package com.elomary.wxautovideocall.automation;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import com.elomary.wxautovideocall.data.DebugLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 无障碍节点树的小工具集。
 *
 * <p>微信的类名与 id 是混淆过的、且各版本不同，所以这里的匹配一律走
 * 「类名 + 可见文本 + contentDescription」这些跨版本相对稳定的信息，
 * 并且按「最具体的条件优先」排序，避免误点。
 *
 * <p>节点回收策略：Android 12 起 AccessibilityNodeInfo.recycle() 已废弃且是空实现，
 * 但 Android 8~11 上不回收会触发系统告警。因此统一走 {@link #recycle} 做版本判断。
 */
public final class UiNodes {

    private static final String TAG = "UiNodes";

    private UiNodes() {
    }

    /** Android 13（API 33）：从这一版起 recycle() 变成空实现并已废弃。 */
    private static final int ANDROID_13 = 33;

    public static void recycle(AccessibilityNodeInfo node) {
        if (node == null) {
            return;
        }
        // 用字面量而不是 Build.VERSION_CODES.TIRAMISU：后者要求 compileSdk >= 33，
        // 写死成常量可以让这段代码在更低的 compileSdk 下也能编过。
        if (android.os.Build.VERSION.SDK_INT < ANDROID_13) {
            try {
                node.recycle();
            } catch (Throwable ignored) {
                // 某些厂商 ROM 上重复回收会抛异常，忽略即可。
            }
        }
    }

    /** 深度优先收集整棵树，便于做统计分析（节点树日志）。 */
    public static List<AccessibilityNodeInfo> flatten(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        if (root == null) {
            return result;
        }
        Deque<AccessibilityNodeInfo> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            AccessibilityNodeInfo node = stack.pop();
            result.add(node);
            for (int i = node.getChildCount() - 1; i >= 0; i--) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    stack.push(child);
                }
            }
        }
        return result;
    }

    public static boolean isVisible(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        if (!node.isVisibleToUser()) {
            return false;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        return bounds.width() > 0 && bounds.height() > 0;
    }

    public static boolean hasText(AccessibilityNodeInfo node, String text) {
        if (node == null || text == null) {
            return false;
        }
        CharSequence nodeText = node.getText();
        return nodeText != null && text.contentEquals(nodeText.toString().trim());
    }

    /** 文本包含匹配（微信里很多按钮文案前后带空格或不可见字符）。 */
    public static boolean containsText(AccessibilityNodeInfo node, String text) {
        if (node == null || text == null) {
            return false;
        }
        CharSequence nodeText = node.getText();
        if (nodeText == null) {
            return false;
        }
        return nodeText.toString().replace("\u200b", "").trim().contains(text);
    }

    public static boolean hasContentDescription(AccessibilityNodeInfo node, String text) {
        if (node == null || text == null) {
            return false;
        }
        CharSequence description = node.getContentDescription();
        return description != null && text.contentEquals(description.toString().trim());
    }

    public static boolean contentDescriptionContains(AccessibilityNodeInfo node, String text) {
        if (node == null || text == null) {
            return false;
        }
        CharSequence description = node.getContentDescription();
        if (description == null) {
            return false;
        }
        return description.toString().replace("\u200b", "").trim().contains(text);
    }

    public static boolean hasViewId(AccessibilityNodeInfo node, String viewId) {
        if (node == null || viewId == null) {
            return false;
        }
        String nodeId = node.getViewIdResourceName();
        return nodeId != null && nodeId.equals(viewId);
    }

    public static boolean viewIdEndsWith(AccessibilityNodeInfo node, String suffix) {
        if (node == null || suffix == null) {
            return false;
        }
        String nodeId = node.getViewIdResourceName();
        return nodeId != null && nodeId.endsWith(suffix);
    }

    /**
     * 判断节点是否可以被点击。
     *
     * <p>微信大量使用自绘控件：按钮本身 clickable=false，能点的是它的某个祖先。
     * 所以这里只判断「节点自身或任一祖先 clickable」，真正点击时再往上找。
     */
    public static boolean isClickableInHierarchy(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        int guard = 0;
        while (current != null && guard++ < 24) {
            if (current.isClickable() && current.isEnabled()) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /** 从节点自身往上找第一个真正可点击的祖先（含自身）。 */
    public static AccessibilityNodeInfo findClickableSelfOrAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        int guard = 0;
        while (current != null && guard++ < 24) {
            if (current.isClickable() && current.isEnabled()) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    public static Rect boundsOf(AccessibilityNodeInfo node) {
        Rect bounds = new Rect();
        if (node != null) {
            node.getBoundsInScreen(bounds);
        }
        return bounds;
    }

    /** 节点中心点的屏幕坐标，供手势点击使用。 */
    public static float[] centerOf(AccessibilityNodeInfo node) {
        Rect bounds = boundsOf(node);
        return new float[] {bounds.exactCenterX(), bounds.exactCenterY()};
    }

    public static String describe(AccessibilityNodeInfo node) {
        if (node == null) {
            return "<null>";
        }
        Rect bounds = boundsOf(node);
        String text = node.getText() == null ? "" : node.getText().toString();
        String description = node.getContentDescription() == null
                ? "" : node.getContentDescription().toString();
        return "class=" + node.getClassName()
                + " id=" + node.getViewIdResourceName()
                + " text=" + trim(text)
                + " desc=" + trim(description)
                + " clickable=" + node.isClickable()
                + " enabled=" + node.isEnabled()
                + " bounds=" + bounds.toShortString();
    }

    /** 日志里不要把长文本整段打出来，截断到 60 字符。 */
    private static String trim(String value) {
        String cleaned = value.replace("\n", "\\n").replace("\u200b", "").trim();
        return cleaned.length() <= 60 ? cleaned : cleaned.substring(0, 60) + "…";
    }

    /** 把整棵可见树打出来，用于真机排查微信到底暴露了哪些节点。 */
    public static String dumpTree(AccessibilityNodeInfo root) {
        if (root == null) {
            return "<root 为 null：微信界面可能未就绪，或系统未授予读取窗口内容>";
        }
        StringBuilder builder = new StringBuilder();
        List<AccessibilityNodeInfo> nodes = flatten(root);
        int visible = 0;
        for (AccessibilityNodeInfo node : nodes) {
            if (!isVisible(node)) {
                continue;
            }
            visible++;
            builder.append(describe(node)).append('\n');
        }
        return "共 " + nodes.size() + " 个节点，其中可见 " + visible + " 个：\n" + builder;
    }

    /** 打印点击前的现场，失败时回溯用。 */
    public static void logTree(String tag, AccessibilityNodeInfo root) {
        DebugLog.d(tag, dumpTree(root));
    }

    /** 收录文本统计，用于判断某个文案是否真的出现在节点树里。 */
    public static List<String> allTexts(AccessibilityNodeInfo root) {
        List<String> result = new ArrayList<>();
        for (AccessibilityNodeInfo node : flatten(root)) {
            CharSequence text = node.getText();
            if (text != null && text.length() > 0) {
                result.add(text.toString());
            }
            CharSequence description = node.getContentDescription();
            if (description != null && description.length() > 0) {
                result.add("(desc) " + description);
            }
        }
        Collections.sort(result);
        return result;
    }
}
