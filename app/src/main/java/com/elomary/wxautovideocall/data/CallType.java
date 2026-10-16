package com.elomary.wxautovideocall.data;

/**
 * 拨号方式。
 *
 * <p>微信的「视频通话」面板里是「语音通话 / 视频通话」两个选项，这里对应其中一个。
 */
public enum CallType {
    VIDEO,
    VOICE;

    /** 微信面板里的按钮文案，自动化点击时用它匹配节点。 */
    public String wechatLabel() {
        return this == VIDEO ? "视频通话" : "语音通话";
    }

    public static CallType fromName(String name, CallType fallback) {
        if (name == null) {
            return fallback;
        }
        for (CallType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return fallback;
    }
}
