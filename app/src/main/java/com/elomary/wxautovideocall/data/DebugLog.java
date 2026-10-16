package com.elomary.wxautovideocall.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 环形调试日志。
 *
 * <p>问题现场在奶奶的手机上，开发者拿不到现场设备时，这份日志是唯一的排查依据。
 * 所以它必须：1) 一直能写；2) 有上限不会撑爆存储；3) 能给使用者一键复制出来。
 *
 * <p>刻意不用第三方日志库，避免为一个小功能引入依赖。
 */
public final class DebugLog {

    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    /** 最多保留的条数，超过就丢最老的。 */
    private static final int MAX_ENTRIES = 800;

    private static final List<Entry> ENTRIES = Collections.synchronizedList(new ArrayList<>());

    private DebugLog() {
    }

    public static final class Entry {
        final long time;
        final Level level;
        final String tag;
        final String message;

        Entry(long time, Level level, String tag, String message) {
            this.time = time;
            this.level = level;
            this.tag = tag;
            this.message = message;
        }

        public long time() {
            return time;
        }

        public Level level() {
            return level;
        }

        public String tag() {
            return tag;
        }

        public String message() {
            return message;
        }

        @Override
        public String toString() {
            // 用 String.format 而不是 android.util.Log.format：后者是隐藏 API，
            // 编译期拿不到（会报「找不到符号」），只有系统内部能用。
            return String.format(java.util.Locale.US, "%s/%s: %s", shortName(level), tag, message);
        }

        private static String shortName(Level level) {
            switch (level) {
                case DEBUG:
                    return "D";
                case INFO:
                    return "I";
                case WARN:
                    return "W";
                case ERROR:
                    return "E";
                default:
                    return "?";
            }
        }
    }

    public static void d(String tag, String message) {
        add(Level.DEBUG, tag, message);
    }

    public static void i(String tag, String message) {
        add(Level.INFO, tag, message);
    }

    public static void w(String tag, String message) {
        add(Level.WARN, tag, message);
    }

    public static void e(String tag, String message) {
        add(Level.ERROR, tag, message);
    }

    public static void e(String tag, String message, Throwable throwable) {
        add(Level.ERROR, tag, message + " / " + throwable);
    }

    private static void add(Level level, String tag, String message) {
        Entry entry = new Entry(System.currentTimeMillis(), level, tag, message);
        ENTRIES.add(entry);
        // 同步块里做裁剪，避免并发下算错下标。
        synchronized (ENTRIES) {
            while (ENTRIES.size() > MAX_ENTRIES) {
                ENTRIES.remove(0);
            }
        }
        // 同时打到 logcat，方便开发阶段用 adb logcat 直接看。
        switch (level) {
            case DEBUG:
                android.util.Log.d(tag, message);
                break;
            case WARN:
                android.util.Log.w(tag, message);
                break;
            case ERROR:
                android.util.Log.e(tag, message);
                break;
            default:
                android.util.Log.i(tag, message);
                break;
        }
    }

    public static List<Entry> entries() {
        synchronized (ENTRIES) {
            return new ArrayList<>(ENTRIES);
        }
    }

    public static void clear() {
        ENTRIES.clear();
    }

    /** 供使用者复制粘贴发出来的纯文本形式。 */
    public static String dump() {
        java.text.SimpleDateFormat format =
                new java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US);
        StringBuilder builder = new StringBuilder();
        for (Entry entry : entries()) {
            builder.append(String.format(java.util.Locale.US, "%s %s/%s: %s",
                    format.format(new java.util.Date(entry.time)),
                    Entry.shortName(entry.level), entry.tag, entry.message));
            builder.append('\n');
        }
        return builder.toString();
    }

    /** 落盘用的 JSON，进程重启后还能把上一次的现场捞出来。 */
    public static String toJson() {
        JSONArray array = new JSONArray();
        for (Entry entry : entries()) {
            JSONObject object = new JSONObject();
            try {
                object.put("t", entry.time);
                object.put("l", entry.level.name());
                object.put("g", entry.tag);
                object.put("m", entry.message);
            } catch (JSONException ignored) {
                // JSONObject 放基本类型不会失败，这里只是满足编译器。
            }
            array.put(object);
        }
        return array.toString();
    }

    public static void restoreFromJson(String json) {
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            JSONArray array = new JSONArray(json);
            ENTRIES.clear();
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                ENTRIES.add(new Entry(
                        object.optLong("t"),
                        parseLevel(object.optString("l")),
                        object.optString("g"),
                        object.optString("m")));
            }
        } catch (JSONException e) {
            // 日志损坏不值得让程序崩溃，直接丢弃。
            ENTRIES.clear();
        }
    }

    private static Level parseLevel(String name) {
        for (Level level : Level.values()) {
            if (level.name().equals(name)) {
                return level;
            }
        }
        return Level.INFO;
    }
}
