package com.elomary.wxautovideocall.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 本应用的设置存储。
 *
 * <p>用最朴素的 SharedPreferences + org.json，不引入 Room/DataStore：
 * 配置项就这么几个，引入数据库会让 APK 变大、无障碍服务启动变慢，不划算。
 */
public final class Prefs {

    private static final String FILE = "wx_auto_video_call";
    private static final String LOG_FILE = "debug_log";

    private static final String KEY_CONTACTS = "contacts";
    private static final String KEY_DEFAULT_CONTACT_ID = "default_contact_id";
    private static final String KEY_DEBUG_LOG = "debug_log_enabled";
    private static final String KEY_LAST_LOG_DUMP = "last_log_dump";

    private Prefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ 联系人

    public static List<Contact> contacts(Context context) {
        String raw = prefs(context).getString(KEY_CONTACTS, null);
        List<Contact> result = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                Contact contact = new Contact(object.optString("id"), object.optString("name"));
                if (contact.isUsable()) {
                    result.add(contact);
                }
            }
        } catch (JSONException e) {
            DebugLog.e("Prefs", "联系人配置损坏，已忽略：" + e);
        }
        return result;
    }

    public static void saveContacts(Context context, List<Contact> contacts) {
        JSONArray array = new JSONArray();
        for (Contact contact : contacts) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", contact.getId());
                object.put("name", contact.getDisplayName());
            } catch (JSONException ignored) {
                // 基本类型不会失败。
            }
            array.put(object);
        }
        prefs(context).edit().putString(KEY_CONTACTS, array.toString()).apply();
    }

    public static String defaultContactId(Context context) {
        return prefs(context).getString(KEY_DEFAULT_CONTACT_ID, "");
    }

    public static void setDefaultContactId(Context context, String id) {
        prefs(context).edit().putString(KEY_DEFAULT_CONTACT_ID, id == null ? "" : id).apply();
    }

    /** 取默认联系人；没设过就退化为列表里的第一个。 */
    public static Contact defaultContact(Context context) {
        List<Contact> contacts = contacts(context);
        String wanted = defaultContactId(context);
        if (!TextUtils.isEmpty(wanted)) {
            for (Contact contact : contacts) {
                if (wanted.equals(contact.getId())) {
                    return contact;
                }
            }
        }
        return contacts.isEmpty() ? null : contacts.get(0);
    }

    // ------------------------------------------------------------------ 日志

    public static boolean debugLogEnabled(Context context) {
        return prefs(context).getBoolean(KEY_DEBUG_LOG, true);
    }

    public static void setDebugLogEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_DEBUG_LOG, enabled).apply();
    }

    /** 把内存里的日志落盘，供下次启动或使用者复制。 */
    public static void persistLog(Context context) {
        prefs(context).edit().putString(KEY_LAST_LOG_DUMP, DebugLog.toJson()).apply();
    }

    public static void restoreLog(Context context) {
        DebugLog.restoreFromJson(prefs(context).getString(KEY_LAST_LOG_DUMP, null));
    }
}
