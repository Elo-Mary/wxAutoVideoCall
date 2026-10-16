package com.elomary.wxautovideocall.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

import com.elomary.wxautovideocall.automation.CallFlowController;
import com.elomary.wxautovideocall.automation.ForegroundApps;
import com.elomary.wxautovideocall.automation.NodeUi;
import com.elomary.wxautovideocall.automation.UiNodes;
import com.elomary.wxautovideocall.automation.WeChatLauncher;
import com.elomary.wxautovideocall.automation.WeChatSelectors;
import com.elomary.wxautovideocall.data.CallRequest;
import com.elomary.wxautovideocall.data.DebugLog;
import com.elomary.wxautovideocall.data.Prefs;

import java.lang.ref.WeakReference;

/**
 * 真正干活的组件：接收微信的界面事件，驱动 {@link CallFlowController} 一步步点过去。
 *
 * <p>为什么要有这个服务：微信没有任何公开接口可以「拨出视频通话」，
 * 免 root 的前提下唯一的办法就是模拟人的点击，而模拟点击必须有无障碍权限。
 *
 * <p>线程模型：无障碍事件的回调发生在主线程，{@link CallFlowController} 的所有
 * 节点操作也必须在主线程，所以这里统一用主线程 Handler 驱动，不引入后台线程。
 * 定时兜底（每 {@link #TICK_INTERVAL_MS} 毫秒推一次）是为了应对
 * 「页面已经变了但没发事件」的情况。
 */
public class CallAccessibilityService extends AccessibilityService {

    private static final String TAG = "CallService";

    /** 事件节流：微信会短时间抛大量事件，全部转发会做很多无用功。 */
    private static final long EVENT_THROTTLE_MS = 120L;
    /** 定时兜底推进间隔。 */
    private static final long TICK_INTERVAL_MS = 250L;

    /** 当前实例。Activity 通过它请求拨号，并查询服务是否可用。 */
    private static WeakReference<CallAccessibilityService> instance = new WeakReference<>(null);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final NodeUi ui = new NodeUi(this);

    private CallFlowController controller;
    private long lastEventAt;

    /** 定时兜底任务：只有流程在跑时才续期，平时不耗电。 */
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (controller == null || controller.isFinished()) {
                return;
            }
            // 使用者中途把微信切走了，就当这次拨号失败，别在后台瞎点。
            String foreground = ForegroundApps.currentPackage(CallAccessibilityService.this);
            if (foreground != null && !WeChatSelectors.PACKAGE.equals(foreground)
                    && !getPackageName().equals(foreground)) {
                controller.cancel("微信被切到后台（当前前台：" + foreground + "）");
                return;
            }
            controller.tick();
            if (!controller.isFinished()) {
                handler.postDelayed(this, TICK_INTERVAL_MS);
            }
        }
    };

    // ------------------------------------------------------------------ 生命周期

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = new WeakReference<>(this);
        DebugLog.i(TAG, "无障碍服务已连接，微信已安装=" + WeChatLauncher.isInstalled(this));
        if (Prefs.debugLogEnabled(this)) {
            DebugLog.i(TAG, "已开启调试日志");
        }
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        DebugLog.w(TAG, "无障碍服务被解绑（多半是使用者或系统把它关了）");
        instance = new WeakReference<>(null);
        stopFlow("无障碍服务已被关闭");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        DebugLog.w(TAG, "无障碍服务被销毁");
        instance = new WeakReference<>(null);
        stopFlow("无障碍服务已被销毁");
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onInterrupt() {
        DebugLog.w(TAG, "无障碍服务被系统打断");
        stopFlow("无障碍服务被打断");
    }

    // ------------------------------------------------------------------ 对外入口

    /** 无障碍服务是否已连接（等价于使用者已在系统设置里开启了本应用）。 */
    public static boolean isRunning() {
        return instance.get() != null;
    }

    /**
     * 请求拨打一次。
     *
     * @return 是否成功受理；未受理时 {@code reasonOut} 里是给使用者看的中文原因。
     */
    public static boolean requestCall(Context context, CallRequest request, String[] reasonOut) {
        CallAccessibilityService service = instance.get();
        if (service == null) {
            reasonOut[0] = "无障碍服务没开启，请先在设置里开启";
            DebugLog.w(TAG, "拨号被拒：无障碍服务未连接");
            return false;
        }
        if (request == null || !request.isValid()) {
            reasonOut[0] = "没有配置联系人，请先在设置里添加";
            DebugLog.w(TAG, "拨号被拒：联系人无效");
            return false;
        }
        if (ForegroundApps.isLocked(context)) {
            reasonOut[0] = "手机现在是锁屏的，请先解锁再点一次";
            DebugLog.w(TAG, "拨号被拒：设备处于锁屏状态");
            return false;
        }
        service.startFlow(request);
        return true;
    }

    // ------------------------------------------------------------------ 流程驱动

    private void startFlow(CallRequest request) {
        stopFlow("新的拨号请求覆盖了上一次");
        DebugLog.i(TAG, "受理拨号请求：" + request);
        controller = new CallFlowController(ui, request, new CallFlowController.Listener() {
            @Override
            public void onProgress(String message) {
                DebugLog.i(TAG, "进度：" + message);
                FlowNotifier.progress(CallAccessibilityService.this, message);
            }

            @Override
            public void onFinished(boolean success, String reason) {
                handler.removeCallbacks(ticker);
                if (success) {
                    FlowNotifier.success(CallAccessibilityService.this);
                } else {
                    FlowNotifier.failure(CallAccessibilityService.this, reason);
                }
                // 现场留着给开发排查，但不要把日志无脑刷爆。
                Prefs.persistLog(CallAccessibilityService.this);
                controller = null;
            }
        });
        controller.start();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, TICK_INTERVAL_MS);
    }

    private void stopFlow(String reason) {
        handler.removeCallbacks(ticker);
        if (controller != null && !controller.isFinished()) {
            controller.cancel(reason);
        }
        controller = null;
    }

    // ------------------------------------------------------------------ 事件

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        CharSequence packageName = event.getPackageName();
        if (packageName == null || !WeChatSelectors.PACKAGE.contentEquals(packageName)) {
            return;
        }
        if (controller == null || controller.isFinished()) {
            // 没有拨号任务时不做事，避免无谓耗电。
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastEventAt < EVENT_THROTTLE_MS) {
            return;
        }
        lastEventAt = now;
        if (Prefs.debugLogEnabled(this)) {
            DebugLog.d(TAG, "事件 " + AccessibilityEvent.eventTypeToString(event.getEventType())
                    + " class=" + event.getClassName());
        }
        controller.tick();
        if (!controller.isFinished()) {
            handler.removeCallbacks(ticker);
            handler.postDelayed(ticker, TICK_INTERVAL_MS);
        }
    }

    /** 供调试用：把当前微信界面的节点树写进日志。 */
    public static void dumpCurrentTree() {
        CallAccessibilityService service = instance.get();
        if (service == null) {
            DebugLog.w(TAG, "无法导出节点树：无障碍服务未连接");
            return;
        }
        DebugLog.i(TAG, "当前界面节点树：\n" + UiNodes.dumpTree(service.getRootInActiveWindow()));
    }
}
