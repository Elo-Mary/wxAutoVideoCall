package com.elomary.wxautovideocall.automation;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityNodeInfo;

import com.elomary.wxautovideocall.data.DebugLog;

/**
 * 对无障碍能力的一层薄封装。
 *
 * <p>存在的意义是让上层流程代码不直接依赖 {@link AccessibilityService}，
 * 一来方便把「点击」换成别的手段（比如坐标脚本），二来方便对流程做纯逻辑测试。
 */
public final class NodeUi {

    private static final String TAG = "NodeUi";

    private final AccessibilityService service;

    public NodeUi(AccessibilityService service) {
        this.service = service;
    }

    public AccessibilityService service() {
        return service;
    }

    /** 当前活动窗口的根节点，可能为 null（微信正在切换页面时很常见）。 */
    public AccessibilityNodeInfo root() {
        try {
            return service.getRootInActiveWindow();
        } catch (Throwable t) {
            DebugLog.w(TAG, "读取根节点失败：" + t);
            return null;
        }
    }

    /**
     * 点击一个节点。
     *
     * <p>优先用 ACTION_CLICK；节点自己不可点时往上找可点的祖先
     * （微信自绘控件常见）；都没有时退化为坐标手势 —— 这是最后手段，
     * 因为坐标会随分辨率与界面微调而失效，所以一定要记日志，方便回溯。
     */
    public boolean clickNode(AccessibilityNodeInfo node, String why) {
        if (node == null) {
            DebugLog.w(TAG, "点击失败（节点为空）：" + why);
            return false;
        }
        AccessibilityNodeInfo target = node.isClickable() ? node : UiNodes.findClickableSelfOrAncestor(node);
        if (target != null) {
            if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                DebugLog.i(TAG, "ACTION_CLICK 成功：" + why + " -> " + UiNodes.describe(target));
                return true;
            }
            DebugLog.w(TAG, "ACTION_CLICK 返回 false：" + why + " -> " + UiNodes.describe(target));
        } else {
            DebugLog.w(TAG, "节点及祖先都不可点击：" + why + " -> " + UiNodes.describe(node));
        }
        float[] center = UiNodes.centerOf(node);
        return tap(center[0], center[1], why + "（退化为坐标点击）");
    }

    /** 在屏幕坐标上模拟一次点击。 */
    public boolean tap(float x, float y, String why) {
        // 不需要判断系统版本：dispatchGesture 从 API 24 起可用，而 minSdk 是 26。
        if (x <= 0 || y <= 0) {
            DebugLog.w(TAG, "坐标非法，放弃点击：" + why + " x=" + x + " y=" + y);
            return false;
        }
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 60))
                .build();
        boolean dispatched = service.dispatchGesture(gesture, null, null);
        DebugLog.i(TAG, "坐标点击 dispatched=" + dispatched + " (" + x + "," + y + ")：" + why);
        return dispatched;
    }

    /** 在节点里输入文字（微信搜索框用）。 */
    public boolean setText(AccessibilityNodeInfo node, String text, String why) {
        if (node == null) {
            DebugLog.w(TAG, "输入失败（节点为空）：" + why);
            return false;
        }
        android.os.Bundle arguments = new android.os.Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        boolean ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        DebugLog.i(TAG, "ACTION_SET_TEXT ok=" + ok + " 内容「" + text + "」：" + why);
        return ok;
    }
}
