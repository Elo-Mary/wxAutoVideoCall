package com.elomary.wxautovideocall.system;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.elomary.wxautovideocall.R;
import com.elomary.wxautovideocall.data.Contact;
import com.elomary.wxautovideocall.data.DebugLog;
import com.elomary.wxautovideocall.ui.MainActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 每个联系人一个桌面快捷方式。
 *
 * <p>为什么这么做：作者希望奶奶桌面上是「给儿子」「给孙女」这样的图标，
 * 点进去只需要选语音还是视频，不用在一堆亲戚里找人。
 * 快捷方式上的名字就是联系人的名字，对老人来说比任何界面内的列表都直观。
 *
 * <p>这里用的是 **固定快捷方式（pinned shortcut）**，也就是 ShortcutManager 的
 * {@code requestPinShortcut}。两个必须知道的约束：
 * <ul>
 *   <li><b>只有 Activity 能发起固定请求</b>，而且系统会弹一个确认框让用户点「添加」。
 *       所以「加联系人 → 自动弹框 → 用户确认」这个流程只能从设置页走，
 *       没办法做成静默添加。</li>
 *   <li>部分 ROM（尤其老版本国产系统）对固定快捷方式支持不好，
 *       表现为弹框不出现或添加后不生效。这种情况下的退路是应用内
 *       普通图标仍然可用，见 {@link MainActivity}。</li>
 * </ul>
 */
public final class ContactShortcuts {

    private static final String TAG = "ContactShortcuts";

    /** 快捷方式 id 前缀，避免和别的东西撞上。 */
    private static final String ID_PREFIX = "contact_";

    /** 传联系人 id 的 extra。名字只是展示用，真正的依据是 id。 */
    public static final String EXTRA_CONTACT_ID = "com.elomary.wxautovideocall.extra.CONTACT_ID";

    private ContactShortcuts() {
    }

    private static String shortcutId(Contact contact) {
        return ID_PREFIX + contact.getId();
    }

    /** 设备是否支持固定快捷方式。不支持时设置页要给出说明而不是静默失败。 */
    public static boolean isSupported(Context context) {
        return ShortcutManagerCompat.isRequestPinShortcutSupported(context);
    }

    /**
     * 弹出系统确认框，请用户把某个联系人的快捷方式固定到桌面。
     *
     * @param callback 用户在系统弹框上点了「添加」或取消后回调；可以为 null。
     * @return 请求是否被系统受理（false 通常是设备不支持）。
     */
    public static boolean requestPin(Context context, Contact contact, Intent callback) {
        if (!isSupported(context)) {
            DebugLog.w(TAG, "设备不支持固定快捷方式，无法为「" + contact.getDisplayName() + "」创建");
            return false;
        }
        ShortcutInfoCompat info = buildShortcut(context, contact);
        PendingIntent pendingIntent = null;
        if (callback != null) {
            // 系统要求用 PendingIntent 回调，这里包一层。
            pendingIntent = PendingIntent.getActivity(
                    context,
                    shortcutId(contact).hashCode(),
                    callback,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        boolean accepted = ShortcutManagerCompat.requestPinShortcut(context, info,
                pendingIntent == null ? null : pendingIntent.getIntentSender());
        DebugLog.i(TAG, "请求固定快捷方式「" + contact.getDisplayName() + "」受理=" + accepted);
        return accepted;
    }

    private static ShortcutInfoCompat buildShortcut(Context context, Contact contact) {
        Intent intent = MainActivity.createShortcutIntent(context, contact);
        return new ShortcutInfoCompat.Builder(context, shortcutId(contact))
                .setShortLabel(contact.getDisplayName())
                .setLongLabel(contact.getDisplayName())
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(intent)
                // setLongLived：联系人名字改了要能更新，所以不能是只读的。
                .setLongLived(true)
                .build();
    }

    /**
     * 同步所有联系人的快捷方式：名字改了就更新，删了联系人就撤掉。
     *
     * <p>必须在设置页每次改动联系人之后调用，否则桌面上会留着
     * 已经删掉的人（点了会找不到）或者旧名字。
     *
     * <p>注意这里只更新**已经固定过**的快捷方式。没固定过的不会被自动加上 ——
     * 加不加由用户决定，见 {@link #requestPin}。
     */
    public static void sync(Context context, List<Contact> contacts) {
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null) {
            return;
        }
        try {
            List<String> existing = new ArrayList<>();
            for (ShortcutInfo info : manager.getPinnedShortcuts()) {
                existing.add(info.getId());
            }
            if (existing.isEmpty()) {
                DebugLog.d(TAG, "桌面上还没有本应用的快捷方式");
                return;
            }

            List<ShortcutInfo> updates = new ArrayList<>();
            List<String> wanted = new ArrayList<>();
            for (Contact contact : contacts) {
                if (!contact.isUsable()) {
                    continue;
                }
                String id = shortcutId(contact);
                wanted.add(id);
                if (!existing.contains(id)) {
                    continue;
                }
                // 只更新名字（图标与 Intent 不变），避免重建快捷方式时把
                // 用户在桌面上的位置弄丢。
                updates.add(new ShortcutInfo.Builder(context, id)
                        .setShortLabel(contact.getDisplayName())
                        .setLongLabel(contact.getDisplayName())
                        .setIntent(MainActivity.createShortcutIntent(context, contact))
                        .build());
            }
            if (!updates.isEmpty()) {
                manager.updateShortcuts(updates);
                DebugLog.i(TAG, "已更新 " + updates.size() + " 个快捷方式的名字");
            }

            List<String> stale = new ArrayList<>();
            for (String id : existing) {
                if (!wanted.contains(id)) {
                    stale.add(id);
                }
            }
            if (!stale.isEmpty()) {
                // 用 disableShortcuts 而不是 remove：删掉联系人后
                // 桌面图标会变灰并提示，比图标凭空消失更不容易让人困惑。
                manager.disableShortcuts(stale, context.getString(R.string.msg_contact_removed));
                DebugLog.i(TAG, "已停用 " + stale.size() + " 个失效的快捷方式");
            }
        } catch (Throwable t) {
            // 各 ROM 对 ShortcutManager 的实现差异较大，这里绝不能让设置页崩掉。
            DebugLog.e(TAG, "同步快捷方式失败：" + t);
        }
    }

    /** 联系人被删除时立刻停用它的快捷方式。 */
    public static void disable(Context context, Contact contact) {
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null) {
            return;
        }
        try {
            List<String> ids = new ArrayList<>();
            ids.add(shortcutId(contact));
            manager.disableShortcuts(ids, context.getString(R.string.msg_contact_removed));
        } catch (Throwable t) {
            DebugLog.e(TAG, "停用快捷方式失败：" + t);
        }
    }

    /** 某个联系人的快捷方式当前是否已经固定到桌面。 */
    public static boolean isPinned(Context context, Contact contact) {
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null) {
            return false;
        }
        try {
            String id = shortcutId(contact);
            for (ShortcutInfo info : manager.getPinnedShortcuts()) {
                if (id.equals(info.getId())) {
                    return true;
                }
            }
        } catch (Throwable t) {
            DebugLog.w(TAG, "查询快捷方式状态失败：" + t);
        }
        return false;
    }
}
