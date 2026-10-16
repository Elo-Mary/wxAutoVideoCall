package com.elomary.wxautovideocall.system;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import com.elomary.wxautovideocall.data.DebugLog;
import com.elomary.wxautovideocall.service.CallAccessibilityService;

/**
 * 无障碍与厂商后台限制的检查 / 引导。
 *
 * <p>这个类存在的唯一原因是：本应用必须开启无障碍服务才能工作，而
 * 「开启无障碍」这件事在每台手机上都是手动的、藏在几层菜单里。
 * 输出是给「帮奶奶装手机的人」看的，所以宁可啰嗦也要说清楚。
 *
 * <p>厂商适配（ColorOS / OriginOS 等）只能尽力而为：各 ROM 的设置页
 * 组件名不公开、且随版本变化，所以每个入口都用 try/catch 包住，
 * 失败就退回到「应用详情页」，绝不因为跳转失败而崩。
 */
public final class Permissions {

    private static final String TAG = "Permissions";

    private Permissions() {
    }

    /** 本应用的无障碍服务是否已在系统中启用。 */
    public static boolean isAccessibilityEnabled(Context context) {
        return CallAccessibilityService.isRunning() || isAccessibilityEnabledInSettings(context);
    }

    /**
     * 直接读系统设置判断，不依赖服务是否已连接。
     *
     * <p>服务进程刚启动或刚被系统重连时，静态实例可能还是空的，
     * 但设置里其实已经开了 —— 这种情况必须判为「已开启」，
     * 否则会误导使用者反复去开。
     */
    public static boolean isAccessibilityEnabledInSettings(Context context) {
        try {
            String enabled = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(enabled)) {
                return false;
            }
            ComponentName component = new ComponentName(context, CallAccessibilityService.class);
            String flattened = component.flattenToString();
            String flattenedShort = component.flattenToShortString();
            for (String item : enabled.split(":")) {
                if (item.equalsIgnoreCase(flattened) || item.equalsIgnoreCase(flattenedShort)) {
                    return true;
                }
            }
        } catch (Throwable t) {
            DebugLog.w(TAG, "读取无障碍设置失败：" + t);
        }
        return false;
    }

    /** 跳转到系统无障碍设置页，并尽量直接定位到本应用。 */
    public static void openAccessibilitySettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            DebugLog.i(TAG, "已跳转到无障碍设置页");
        } catch (Throwable t) {
            DebugLog.e(TAG, "跳转无障碍设置页失败：" + t);
        }
    }

    /** 跳转到应用详情页（电池、自启动等的通用退路）。 */
    public static void openAppDetails(Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Throwable t) {
            DebugLog.e(TAG, "跳转应用详情页失败：" + t);
        }
    }

    /** 是否忽略了电池优化（很多 ROM 上这是后台被杀的主因）。 */
    public static void openBatteryOptimizationSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Throwable t) {
            DebugLog.w(TAG, "跳转电池优化设置失败，退回应用详情页：" + t);
            openAppDetails(context);
        }
    }

    /** 当前设备厂商，用于决定给哪一套引导文案。 */
    public static String manufacturer() {
        // 明确用 Locale.ROOT：这里比较的是品牌标识符，不是给人看的文字，
        // 用默认 locale 会让土耳其语等地区的 i/İ 大小写转换出问题（lint 也会报）。
        String brand = Build.BRAND == null ? "" : Build.BRAND.toLowerCase(java.util.Locale.ROOT);
        String manufacturer = Build.MANUFACTURER == null
                ? "" : Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT);
        String combined = brand + "/" + manufacturer;
        if (combined.contains("oppo") || combined.contains("realme") || combined.contains("oneplus")) {
            return "oppo";
        }
        if (combined.contains("vivo") || combined.contains("iqoo")) {
            return "vivo";
        }
        if (combined.contains("xiaomi") || combined.contains("redmi") || combined.contains("poco")) {
            return "xiaomi";
        }
        if (combined.contains("huawei") || combined.contains("honor")) {
            return "huawei";
        }
        return "other";
    }
}
