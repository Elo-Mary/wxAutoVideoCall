package com.elomary.wxautovideocall.data;

/**
 * 一次拨号请求。
 *
 * <p>参数少到可以做成不可变对象，方便在 Activity 与无障碍服务之间传递并留证。
 */
public final class CallRequest {

    public final String contactName;
    public final CallType callType;

    public CallRequest(String contactName, CallType callType) {
        this.contactName = contactName == null ? "" : contactName.trim();
        this.callType = callType == null ? CallType.VIDEO : callType;
    }

    public boolean isValid() {
        return !contactName.isEmpty();
    }

    @Override
    public String toString() {
        return "给「" + contactName + "」拨打" + callType.wechatLabel();
    }
}
