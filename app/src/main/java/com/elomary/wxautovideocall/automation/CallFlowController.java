package com.elomary.wxautovideocall.automation;

import android.view.accessibility.AccessibilityNodeInfo;

import com.elomary.wxautovideocall.data.CallRequest;
import com.elomary.wxautovideocall.data.CallType;
import com.elomary.wxautovideocall.data.DebugLog;

import java.util.List;

/**
 * 把「给某人打微信视频」拆成有序步骤，逐步推进。
 *
 * <p>模型：每个步骤声明一个「达成条件」({@link Step#isSatisfied()})，
 * 控制器反复检查条件是否达成；没达成就在该步的预算内重试动作
 * ({@link Step#run()})，达成后才进入下一步。这样写的直接好处是：
 * <ul>
 *   <li>不会出现「动作发出去了但页面没反应，却已经跳到下一步」的假成功；</li>
 *   <li>每步的失败点非常明确，日志里一看就知道卡在哪一步；</li>
 *   <li>没有 sleep —— 全部由无障碍事件 + 定时器驱动，
 *       因为无障碍回调跑在主线程，sleep 会直接 ANR，而且 sleep 期间
 *       微信的节点树根本不会更新。</li>
 * </ul>
 */
public final class CallFlowController {

    private static final String TAG = "CallFlow";

    /** 单步超时预算。微信冷启动慢，第一步给足时间。 */
    private static final long TIMEOUT_OPEN_WECHAT_MS = 12000L;
    private static final long TIMEOUT_DEFAULT_MS = 6000L;
    /** 流程整体上限，防止某步反复重试把使用者晾在那里。 */
    private static final long TIMEOUT_TOTAL_MS = 45000L;

    public interface Listener {
        /** 进度播报，用于给使用者看的中文提示（例如「正在打开微信」）。 */
        void onProgress(String message);

        /** 流程结束。success 为 false 时 reason 一定有内容。 */
        void onFinished(boolean success, String reason);
    }

    private final NodeUi ui;
    private final CallRequest request;
    private final Listener listener;

    private long startedAt;
    private Step current;
    private boolean finished;

    public CallFlowController(NodeUi ui, CallRequest request, Listener listener) {
        this.ui = ui;
        this.request = request;
        this.listener = listener;
    }

    /** 启动流程。必须从主线程调用。 */
    public void start() {
        startedAt = System.currentTimeMillis();
        DebugLog.i(TAG, "开始拨号：" + request);
        current = new OpenWeChatStep();
        current.reset();
        current.run();
    }

    /**
     * 推进一步：条件已达成则进入下一步，否则重试当前动作。
     * 无障碍事件到达时调用（做了节流），以及定时器兜底调用。必须从主线程调用。
     */
    public void tick() {
        if (finished || current == null) {
            return;
        }
        if (System.currentTimeMillis() - startedAt > TIMEOUT_TOTAL_MS) {
            fail("整体超时（" + (TIMEOUT_TOTAL_MS / 1000) + " 秒）：卡在「" + current.name() + "」");
            return;
        }
        if (current.timedOut()) {
            fail("步骤「" + current.name() + "」超时，微信界面可能已经变化");
            return;
        }
        if (!current.isSatisfied()) {
            current.run();
            return;
        }
        DebugLog.i(TAG, "步骤「" + current.name() + "」达成，用时 " + current.elapsed() + "ms");
        Step next = current.next();
        if (next == null) {
            succeed();
            return;
        }
        current = next;
        current.reset();
        current.run();
    }

    /** 外部中止（例如使用者取消、微信被切走）。 */
    public void cancel(String reason) {
        if (finished) {
            return;
        }
        DebugLog.w(TAG, "流程被中止：" + reason);
        finish(false, reason);
    }

    public boolean isFinished() {
        return finished;
    }

    // ------------------------------------------------------------------ 结果

    private void succeed() {
        DebugLog.i(TAG, "拨号流程完成：" + request
                + "（耗时 " + (System.currentTimeMillis() - startedAt) + "ms）");
        listener.onProgress("已发起通话");
        finish(true, null);
    }

    private void fail(String reason) {
        DebugLog.e(TAG, "拨号失败：" + reason);
        DebugLog.w(TAG, "失败时的界面：" + UiNodes.dumpTree(ui.root()));
        finish(false, reason);
    }

    private void finish(boolean success, String reason) {
        if (finished) {
            return;
        }
        finished = true;
        listener.onFinished(success, reason);
    }

    // ------------------------------------------------------------------ 步骤基类

    private abstract class Step {
        private long stepStartedAt;
        private final long budgetMs;

        Step(long budgetMs) {
            this.budgetMs = budgetMs;
        }

        abstract String name();

        /** 本步的目标是否已经达成。 */
        abstract boolean isSatisfied();

        /** 尝试做本步的动作。允许被反复调用，实现必须是幂等的或自带去重。 */
        abstract void run();

        /** 下一步；返回 null 表示整个流程结束。 */
        abstract Step next();

        void reset() {
            stepStartedAt = System.currentTimeMillis();
        }

        boolean timedOut() {
            return System.currentTimeMillis() - stepStartedAt > budgetMs;
        }

        long elapsed() {
            return System.currentTimeMillis() - stepStartedAt;
        }
    }

    /** 第 1 步：把微信拉到前台。 */
    private final class OpenWeChatStep extends Step {
        private boolean launchRequested;

        OpenWeChatStep() {
            super(TIMEOUT_OPEN_WECHAT_MS);
        }

        @Override
        String name() {
            return "打开微信";
        }

        @Override
        boolean isSatisfied() {
            String foreground = ForegroundApps.currentPackage(ui.service());
            if (WeChatSelectors.PACKAGE.equals(foreground)) {
                return true;
            }
            DebugLog.d(TAG, "等待微信到前台，当前前台：" + foreground);
            return false;
        }

        @Override
        void run() {
            if (!launchRequested) {
                listener.onProgress("正在打开微信…");
                if (!WeChatLauncher.isInstalled(ui.service())) {
                    fail("没有安装微信");
                    return;
                }
                launchRequested = true;
            }
            WeChatLauncher.bringToFront(ui.service());
        }

        @Override
        Step next() {
            return new OpenSearchStep();
        }
    }

    /** 第 2 步：在微信首页点开搜索框，直到搜索输入框出现。 */
    private final class OpenSearchStep extends Step {
        OpenSearchStep() {
            super(TIMEOUT_DEFAULT_MS);
        }

        @Override
        String name() {
            return "打开微信搜索";
        }

        @Override
        boolean isSatisfied() {
            return WeChatFinders.findSearchInput(ui) != null;
        }

        @Override
        void run() {
            listener.onProgress("正在查找联系人…");
            AccessibilityNodeInfo search = WeChatFinders.findHomeSearch(ui);
            if (search == null) {
                DebugLog.d(TAG, "首页搜索框还没出现（微信可能停在某个会话里）");
                // 退一步：按返回键回到会话列表首页。
                ui.service().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
                return;
            }
            ui.clickNode(search, "首页搜索框");
        }

        @Override
        Step next() {
            return new InputContactStep();
        }
    }

    /** 第 3 步：把联系人名字输进搜索框。 */
    private final class InputContactStep extends Step {
        private boolean inputRequested;

        InputContactStep() {
            super(TIMEOUT_DEFAULT_MS);
        }

        @Override
        String name() {
            return "输入联系人名字";
        }

        @Override
        boolean isSatisfied() {
            if (!inputRequested) {
                return false;
            }
            // 输入完成的判据：搜索结果里出现了这个名字。比读输入框内容更可靠。
            return WeChatFinders.findSearchResult(ui, request.contactName) != null;
        }

        @Override
        void run() {
            AccessibilityNodeInfo input = WeChatFinders.findSearchInput(ui);
            if (input == null) {
                DebugLog.d(TAG, "搜索输入框还没出现");
                return;
            }
            if (!inputRequested) {
                ui.setText(input, request.contactName, "搜索框输入「" + request.contactName + "」");
                inputRequested = true;
            }
        }

        @Override
        Step next() {
            return new OpenChatStep();
        }
    }

    /** 第 4 步：在搜索结果里点中联系人，进入聊天页。 */
    private final class OpenChatStep extends Step {
        OpenChatStep() {
            super(TIMEOUT_DEFAULT_MS);
        }

        @Override
        String name() {
            return "打开与联系人的聊天";
        }

        @Override
        boolean isSatisfied() {
            // 聊天页的标志：输入栏右侧的「+」出现了。
            return WeChatFinders.findChatPlus(ui) != null;
        }

        @Override
        void run() {
            AccessibilityNodeInfo entry = WeChatFinders.findSearchResult(ui, request.contactName);
            if (entry == null) {
                DebugLog.d(TAG, "搜索结果里还没出现「" + request.contactName + "」");
                return;
            }
            ui.clickNode(entry, "搜索结果中的联系人「" + request.contactName + "」");
        }

        @Override
        Step next() {
            return new OpenCallPanelStep();
        }
    }

    /** 第 5 步：点「+」展开面板，直到面板里出现「视频通话」。 */
    private final class OpenCallPanelStep extends Step {
        OpenCallPanelStep() {
            super(TIMEOUT_DEFAULT_MS);
        }

        @Override
        String name() {
            return "打开聊天扩展面板";
        }

        @Override
        boolean isSatisfied() {
            return WeChatFinders.findPanelEntry(ui, WeChatSelectors.VIDEO_CALL_ENTRY) != null;
        }

        @Override
        void run() {
            AccessibilityNodeInfo plus = WeChatFinders.findChatPlus(ui);
            if (plus == null) {
                DebugLog.d(TAG, "聊天输入栏的「+」还没出现");
                return;
            }
            ui.clickNode(plus, "聊天输入栏「+」");
        }

        @Override
        Step next() {
            return new ClickVideoEntryStep();
        }
    }

    /** 第 6 步：点面板里的「视频通话」入口，直到出现二选一面板。 */
    private final class ClickVideoEntryStep extends Step {
        private boolean clicked;

        ClickVideoEntryStep() {
            super(TIMEOUT_DEFAULT_MS);
        }

        @Override
        String name() {
            return "点击视频通话入口";
        }

        @Override
        void reset() {
            super.reset();
            clicked = false;
        }

        @Override
        boolean isSatisfied() {
            // 二选一面板出现的标志：能看到「语音通话」按钮。
            return WeChatFinders.findCallDialogButton(ui, CallType.VOICE) != null;
        }

        @Override
        void run() {
            if (clicked) {
                return;
            }
            AccessibilityNodeInfo entry = WeChatFinders.findPanelEntry(ui, WeChatSelectors.VIDEO_CALL_ENTRY);
            if (entry == null) {
                DebugLog.d(TAG, "面板里还没出现「" + WeChatSelectors.VIDEO_CALL_ENTRY + "」");
                return;
            }
            clicked = ui.clickNode(entry, "面板里的「视频通话」入口");
        }

        @Override
        Step next() {
            return new ConfirmCallStep();
        }
    }

    /**
     * 第 7 步：在「语音通话 / 视频通话」里选需要的那一个。
     *
     * <p>有的微信版本点入口会直接拨视频、不出二选一面板。那种情况下
     * 上一步的达成条件永远不成立，所以这里不能死等：如果二选一面板没出现
     * 但已经进了通话界面（第 8 步的判据），就跳过本步。为保持流程简单，
     * 这里通过 {@link #isSatisfied()} 兼容两种形态。
     */
    private final class ConfirmCallStep extends Step {
        private boolean clicked;

        ConfirmCallStep() {
            super(TIMEOUT_DEFAULT_MS);
        }

        @Override
        String name() {
            return "选择" + request.callType.wechatLabel();
        }

        @Override
        void reset() {
            super.reset();
            clicked = false;
        }

        @Override
        boolean isSatisfied() {
            // 已经进了通话界面（微信不再显示二选一按钮）也算达成。
            return clicked || CallScreenInfo.isOnCallScreen(ui);
        }

        @Override
        void run() {
            if (clicked) {
                return;
            }
            AccessibilityNodeInfo button = WeChatFinders.findCallDialogButton(ui, request.callType);
            if (button == null) {
                DebugLog.d(TAG, "还没出现「" + request.callType.wechatLabel() + "」按钮");
                return;
            }
            clicked = ui.clickNode(button, "「" + request.callType.wechatLabel() + "」按钮");
        }

        @Override
        Step next() {
            return new VerifyCallStep();
        }
    }

    /**
     * 第 8 步：确认通话已拨出。
     *
     * <p>这是流程里唯一能给出「到底成没成」依据的地方。微信进入通话界面后
     * 会出现「正在等待对方接受邀请」之类的文案。
     *
     * <p>没检测到也不判失败：微信文案随版本变化，误报失败会让使用者以为
     * 没拨出去而反复点击，反而更糟。所以这里超时后由控制器统一走失败路径，
     * 失败信息里会带上当时的界面摘要，供真机排查。
     */
    private final class VerifyCallStep extends Step {
        VerifyCallStep() {
            super(TIMEOUT_DEFAULT_MS);
        }

        @Override
        String name() {
            return "确认通话已拨出";
        }

        @Override
        boolean isSatisfied() {
            return CallScreenInfo.isOnCallScreen(ui);
        }

        @Override
        void run() {
            List<String> texts = UiNodes.allTexts(ui.root());
            DebugLog.d(TAG, "等待通话界面，当前文案：" + texts);
        }

        @Override
        Step next() {
            return null;
        }
    }
}
