package com.elomary.wxautovideocall.data;

import java.util.Objects;

/**
 * 一个可以一键拨打的联系人。
 *
 * <p>只存「微信里显示的名字」，因为最终拨号是靠名字去微信搜索框里搜。
 * 名字必须和微信里显示的一致（包括备注名），否则搜不到。
 */
public final class Contact {

    /** 稳定标识，用于列表操作与默认联系人的引用。 */
    private final String id;
    /** 微信里显示的名字（备注名优先）。 */
    private final String displayName;

    public Contact(String id, String displayName) {
        this.id = id == null ? "" : id;
        this.displayName = displayName == null ? "" : displayName.trim();
    }

    public static Contact create(String displayName) {
        // 用时间戳 + 名字做 id，避免引入 UUID 依赖，也方便人工看日志。
        return new Contact(System.currentTimeMillis() + "-" + Math.abs(displayName.hashCode()), displayName);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Contact withDisplayName(String newName) {
        return new Contact(id, newName);
    }

    public boolean isUsable() {
        return !displayName.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Contact)) {
            return false;
        }
        Contact other = (Contact) o;
        return id.equals(other.id) && displayName.equals(other.displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName);
    }

    @Override
    public String toString() {
        return "Contact{" + displayName + "}";
    }
}
