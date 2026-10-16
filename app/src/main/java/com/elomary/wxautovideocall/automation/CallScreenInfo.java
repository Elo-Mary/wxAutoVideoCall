package com.elomary.wxautovideocall.automation;

import com.elomary.wxautovideocall.data.DebugLog;

import java.util.List;

/**
 * 判断微信是否已经进入通话界面。
 *
 * <p>这是整个流程里唯一能给出「到底拨出去了没有」依据的地方，所以单独成类，
 * 方便真机验证时集中调整这里的关键词。
 *
 * <p><b>TODO(真机)：</b>下面这些文案是按微信通话界面的通用说法收集的，
 * 需要真机上确认。确认方法：手动打一次视频通话，看日志里
 * {@code CallFlow} 标签打印的「等待通话界面，当前文案」。
 */
public final class CallScreenInfo {

    private static final String TAG = "CallScreenInfo";

    /**
     * 通话界面上的标志性文案。
     *
     * <p>「正在等待对方接受邀请」是微信视频/语音通话发起后的典型提示。
     * 「取消」是通话界面右下角的按钮（呼叫中时显示）。
     */
    private static final String[] CALL_SCREEN_KEYWORDS = {
            "正在等待对方接受邀请",
            "等待对方接受邀请",
            "正在呼叫",
            "正在等待对方",
            "对方无应答",
    };

    private CallScreenInfo() {
    }

    public static boolean isOnCallScreen(NodeUi ui) {
        List<String> texts;
        try {
            texts = UiNodes.allTexts(ui.root());
        } catch (Throwable t) {
            DebugLog.w(TAG, "读取界面文案失败：" + t);
            return false;
        }
        for (String text : texts) {
            for (String keyword : CALL_SCREEN_KEYWORDS) {
                if (text.contains(keyword)) {
                    DebugLog.i(TAG, "命中通话界面标志：「" + text + "」");
                    return true;
                }
            }
        }
        return false;
    }
}
