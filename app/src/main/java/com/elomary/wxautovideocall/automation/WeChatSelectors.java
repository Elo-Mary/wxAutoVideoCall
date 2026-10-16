package com.elomary.wxautovideocall.automation;

/**
 * 微信界面的定位规则（单一维护点）。
 *
 * <p><b>重要：下面带 TODO(真机) 的常量都是按微信通用的界面结构推测的，
 * 尚未在真机上验证。</b>微信的 resource-id 是混淆过的，且不同版本会变，
 * 所以这里的策略是「多套候选 + 文本兜底」，并把真实节点树打进日志。
 *
 * <p>真机调试时的流程：
 * <ol>
 *   <li>在设置里打开「节点树日志」；</li>
 *   <li>点一次一键拨号，让它走到失败；</li>
 *   <li>把日志导出，搜 {@code NodeUi} / {@code CallFlow} 标签；</li>
 *   <li>按日志里真实的 id / 文案，回来补这里的候选值。</li>
 * </ol>
 */
public final class WeChatSelectors {

    /** 微信包名（大陆版微信）。国际版 WeChat 是 com.tencent.wework 之外的另一个包，暂不支持。 */
    public static final String PACKAGE = "com.tencent.mm";

    /**
     * 微信主界面的入口 Activity。
     *
     * <p>用包名的 launch intent 而不是写死 LauncherUI 类名：
     * 类名一旦变化会直接崩，而 launch intent 由系统解析，更稳。
     */
    public static final String MAIN_ACTIVITY = "com.tencent.mm.ui.LauncherUI";

    private WeChatSelectors() {
    }

    // ------------------------------------------------------------ 首页（聊天列表）

    /** 首页顶部搜索框的 resource-id 候选。TODO(真机)：核实并补齐。 */
    public static final String[] HOME_SEARCH_ID_SUFFIXES = {
            ":id/gy",       // 历史版本里搜索框的短 id
            ":id/fs",
            ":id/cwq",
            ":id/search_bar",
            ":id/action_bar_search",
    };

    /** 首页搜索框的文案候选（微信首页搜索框里长期是「搜索」）。 */
    public static final String[] HOME_SEARCH_TEXTS = {
            "搜索",
    };

    /** 首页搜索框的 contentDescription 候选。 */
    public static final String[] HOME_SEARCH_DESCRIPTIONS = {
            "搜索",
    };

    // ------------------------------------------------------------ 搜索页

    /** 搜索页输入框的 resource-id 候选。TODO(真机)：核实。 */
    public static final String[] SEARCH_INPUT_ID_SUFFIXES = {
            ":id/bhn",
            ":id/search_input",
            ":id/gy",
            ":id/b4k",
    };

    /** 搜索框提示文案（用于把输入框本身找出来）。 */
    public static final String[] SEARCH_INPUT_HINTS = {
            "搜索",
    };

    // ------------------------------------------------------------ 聊天页顶部

    /**
     * 聊天页右上角「…」的 contentDescription 候选。
     * 这是「+」面板与视频通话入口最稳定的入口之一。
     */
    public static final String[] CHAT_MORE_DESCRIPTIONS = {
            "聊天信息",
            "更多功能按钮，已折叠",
            "更多",
    };

    /** 聊天页「…」的 resource-id 候选。TODO(真机)：核实。 */
    public static final String[] CHAT_MORE_ID_SUFFIXES = {
            ":id/gy",
            ":id/aua",
            ":id/chatting_more_btn",
    };

    // ------------------------------------------------------------ 输入栏「+」

    /** 聊天输入栏右侧「+」的 resource-id 候选。TODO(真机)：核实。 */
    public static final String[] CHAT_PLUS_ID_SUFFIXES = {
            ":id/ao5",
            ":id/plus",
            ":id/chatting_plus_btn",
            ":id/b4c",
            ":id/ao_",
    };

    /** 「+」的 contentDescription 候选。 */
    public static final String[] CHAT_PLUS_DESCRIPTIONS = {
            "更多功能按钮",
            "更多",
    };

    // ------------------------------------------------------------ 视频通话入口

    /**
     * 展开面板后「视频通话」的文案。这个文案在微信的「+」面板和「…」面板里
     * 都长期存在，是整套流程里最可信的锚点，优先用文本匹配而不是 id。
     */
    public static final String VIDEO_CALL_ENTRY = "视频通话";

    /**
     * 面板里「视频通话」的 resource-id 候选。文本匹配失败时兜底。TODO(真机)：核实。
     */
    public static final String[] VIDEO_CALL_ENTRY_ID_SUFFIXES = {
            ":id/b4g",
            ":id/au7",
            ":id/video_call_entry",
    };

    // ------------------------------------------------------------ 语音/视频二选一

    /** 二选一面板里「视频通话」按钮的文案（与入口同名，靠层级区分）。 */
    public static final String DIALOG_VIDEO = "视频通话";

    /** 二选一面板里「语音通话」按钮的文案。 */
    public static final String DIALOG_VOICE = "语音通话";
}
