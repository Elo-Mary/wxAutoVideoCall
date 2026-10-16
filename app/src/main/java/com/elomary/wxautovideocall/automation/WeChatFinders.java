package com.elomary.wxautovideocall.automation;

import android.view.accessibility.AccessibilityNodeInfo;

import com.elomary.wxautovideocall.data.CallType;
import com.elomary.wxautovideocall.data.DebugLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 在微信界面里找具体控件。
 *
 * <p>统一策略：<b>多套候选，按可信度从高到低依次尝试</b>，第一个命中的就用。
 * 因为微信的 resource-id 是混淆的、且随版本变化，只靠一种定位方式必然短命。
 *
 * <p>每个方法的返回节点都保证「自身或祖先可点击」，可以直接交给
 * {@link NodeUi#clickNode} 使用。
 */
public final class WeChatFinders {

    private static final String TAG = "WeChatFinders";

    private WeChatFinders() {
    }

    private interface NodePredicate {
        boolean matches(AccessibilityNodeInfo node);
    }

    // ------------------------------------------------------------------ 通用查找

    private static List<AccessibilityNodeInfo> collectByViewId(NodeUi ui, String[] idSuffixes) {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        AccessibilityNodeInfo root = ui.root();
        if (root == null || idSuffixes == null) {
            return result;
        }
        for (AccessibilityNodeInfo node : UiNodes.flatten(root)) {
            if (!UiNodes.isVisible(node)) {
                continue;
            }
            for (String suffix : idSuffixes) {
                if (UiNodes.viewIdEndsWith(node, suffix) || UiNodes.hasViewId(node, suffix)) {
                    result.add(node);
                    break;
                }
            }
        }
        return result;
    }

    private static List<AccessibilityNodeInfo> collect(NodeUi ui, NodePredicate predicate) {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        AccessibilityNodeInfo root = ui.root();
        if (root == null) {
            return result;
        }
        for (AccessibilityNodeInfo node : UiNodes.flatten(root)) {
            if (UiNodes.isVisible(node) && predicate.matches(node)) {
                result.add(node);
            }
        }
        return result;
    }

    /** 从候选里挑一个可点击的；找不到可点击的则返回 null（宁可失败也不乱点）。 */
    private static AccessibilityNodeInfo firstClickable(List<AccessibilityNodeInfo> candidates, String why) {
        for (AccessibilityNodeInfo candidate : candidates) {
            if (UiNodes.isClickableInHierarchy(candidate)) {
                DebugLog.d(TAG, "命中「" + why + "」-> " + UiNodes.describe(candidate));
                return candidate;
            }
        }
        if (!candidates.isEmpty()) {
            DebugLog.d(TAG, "找到「" + why + "」的候选但都不可点击，共 " + candidates.size() + " 个");
        }
        return null;
    }

    private static AccessibilityNodeInfo findByViewIdThenText(NodeUi ui,
                                                             String[] idSuffixes,
                                                             String[] texts,
                                                             String[] descriptions,
                                                             String why) {
        AccessibilityNodeInfo byId = firstClickable(collectByViewId(ui, idSuffixes), why + "（按资源 id）");
        if (byId != null) {
            return byId;
        }
        if (texts != null) {
            for (String text : texts) {
                AccessibilityNodeInfo byText = firstClickable(
                        collect(ui, node -> UiNodes.containsText(node, text)), why + "（按文本「" + text + "」）");
                if (byText != null) {
                    return byText;
                }
            }
        }
        if (descriptions != null) {
            for (String description : descriptions) {
                AccessibilityNodeInfo byDesc = firstClickable(
                        collect(ui, node -> UiNodes.contentDescriptionContains(node, description)),
                        why + "（按描述「" + description + "」）");
                if (byDesc != null) {
                    return byDesc;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 具体控件

    /** 微信首页顶部的搜索框。 */
    public static AccessibilityNodeInfo findHomeSearch(NodeUi ui) {
        return findByViewIdThenText(ui,
                WeChatSelectors.HOME_SEARCH_ID_SUFFIXES,
                WeChatSelectors.HOME_SEARCH_TEXTS,
                WeChatSelectors.HOME_SEARCH_DESCRIPTIONS,
                "首页搜索框");
    }

    /**
     * 搜索页的输入框。
     *
     * <p>判据是「可编辑」：微信搜索页的输入框一定支持 ACTION_SET_TEXT，
     * 用它比猜 resource-id 稳得多，所以这里优先按 editable 找，id 只作为兜底。
     */
    public static AccessibilityNodeInfo findSearchInput(NodeUi ui) {
        AccessibilityNodeInfo editable = firstClickable(
                collect(ui, node -> node.isEditable() && node.isEnabled()),
                "搜索输入框（按可编辑性）");
        if (editable != null) {
            return editable;
        }
        // 兜底：可编辑但被判定为不可点的节点，直接返回它本身（输入不需要点击）。
        for (AccessibilityNodeInfo node : collect(ui, n -> n.isEditable() && n.isEnabled())) {
            return node;
        }
        return findByViewIdThenText(ui,
                WeChatSelectors.SEARCH_INPUT_ID_SUFFIXES,
                WeChatSelectors.SEARCH_INPUT_HINTS,
                null,
                "搜索输入框（按资源 id）");
    }

    /**
     * 搜索结果列表里的目标联系人。
     *
     * <p>用「文本完全相等」而不是包含，避免搜「小明」时命中「小明明」。
     * 结果列表里同一个名字可能出现多次（联系人分类 + 聊天记录分类），
     * 取树序最靠前的那个 —— 微信把「联系人」分类排在前面。
     */
    public static AccessibilityNodeInfo findSearchResult(NodeUi ui, String contactName) {
        if (contactName == null || contactName.isEmpty()) {
            return null;
        }
        List<AccessibilityNodeInfo> candidates = collect(ui,
                node -> UiNodes.hasText(node, contactName)
                        || UiNodes.hasContentDescription(node, contactName));
        return firstClickable(candidates, "搜索结果「" + contactName + "」");
    }

    /** 聊天输入栏右侧的「+」。 */
    public static AccessibilityNodeInfo findChatPlus(NodeUi ui) {
        return findByViewIdThenText(ui,
                WeChatSelectors.CHAT_PLUS_ID_SUFFIXES,
                null,
                WeChatSelectors.CHAT_PLUS_DESCRIPTIONS,
                "聊天输入栏「+」");
    }

    /**
     * 展开面板里的「视频通话」入口。
     *
     * <p>注意：二选一面板里的「视频通话」按钮文案和这里一样，
     * 所以调用方要按流程顺序使用 —— 第 5、6 步找的是面板入口，
     * 第 7 步找的是二选一按钮（见 {@link #findCallDialogButton}）。
     * 两者的区分靠界面上下文，不靠文案，这是微信的现状，不是这里的疏漏。
     */
    public static AccessibilityNodeInfo findPanelEntry(NodeUi ui, String label) {
        return findByViewIdThenText(ui,
                WeChatSelectors.VIDEO_CALL_ENTRY_ID_SUFFIXES,
                new String[] {label},
                null,
                "面板入口「" + label + "」");
    }

    /**
     * 二选一面板里的「语音通话 / 视频通话」按钮。
     *
     * <p>关键判据：<b>「语音通话」这个文案只在二选一面板里出现</b>。
     * 聊天「+」面板里的入口叫「视频通话」，不叫「语音通话」，所以
     * 一旦屏幕上出现「语音通话」，就可以确定二选一面板已经弹出来了。
     * 先做这个门禁，再去挑目标按钮，能有效避免把「+」面板里的入口
     * 误当成拨号按钮。
     */
    public static AccessibilityNodeInfo findCallDialogButton(NodeUi ui, CallType type) {
        List<AccessibilityNodeInfo> voiceNodes = collect(ui,
                node -> UiNodes.hasText(node, WeChatSelectors.DIALOG_VOICE));
        if (voiceNodes.isEmpty()) {
            // 二选一面板没出现：可能是点入口后直接拨了视频，也可能是还没弹出来。
            return null;
        }
        List<AccessibilityNodeInfo> wanted = voiceNodes;
        if (type == CallType.VIDEO) {
            List<AccessibilityNodeInfo> videoNodes = collect(ui,
                    node -> UiNodes.hasText(node, WeChatSelectors.DIALOG_VIDEO));
            if (videoNodes.isEmpty()) {
                DebugLog.d(TAG, "二选一面板里没找到「视频通话」按钮");
                return null;
            }
            wanted = videoNodes;
        }
        return firstClickable(wanted, "二选一按钮「" + type.wechatLabel() + "」");
    }

    /** 统计当前界面里出现的所有文案，真机排查时用来确认微信暴露了什么。 */
    public static String describeTexts(NodeUi ui) {
        List<String> texts = UiNodes.allTexts(ui.root());
        Set<String> unique = new HashSet<>(texts);
        return unique.toString();
    }
}
