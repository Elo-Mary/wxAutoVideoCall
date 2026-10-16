package com.elomary.wxautovideocall.automation;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import com.elomary.wxautovideocall.data.DebugLog;

/**
 * 启动微信。
 *
 * <p>这里有一个必须知道的系统限制：Android 10 起，后台应用不能随意启动 Activity。
 * 本应用是通过 {@link AccessibilityService} 去启动微信的，而「正在运行的无障碍服务」
 * 属于系统豁免情形之一，所以这条路通常是通的；但个别厂商 ROM 仍会拦截，
 * 表现为「点了图标没反应」。因此这里除了记录日志，还在失败时给出可执行的提示。
 */
public final class WeChatLauncher {

    private static final String TAG = "WeChatLauncher";

    private WeChatLauncher() {
    }

    public static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(WeChatSelectors.PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Throwable t) {
            DebugLog.w(TAG, "查询微信安装状态失败：" + t);
            return false;
        }
    }

    /** 把微信切到前台（已经在前台则只是切回）。 */
    public static boolean bringToFront(Context context) {
        // 首选：让系统按包名解析启动入口，避免写死混淆过的 Activity 类名。
        Intent launch = context.getPackageManager()
                .getLaunchIntentForPackage(WeChatSelectors.PACKAGE);
        if (launch == null) {
            DebugLog.w(TAG, "系统没有给出微信的启动入口，尝试显式类名");
            launch = new Intent();
            launch.setClassName(WeChatSelectors.PACKAGE, WeChatSelectors.MAIN_ACTIVITY);
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            context.startActivity(launch);
            DebugLog.i(TAG, "已请求启动微信：" + launch);
            return true;
        } catch (Throwable t) {
            DebugLog.e(TAG, "启动微信失败（多半是被后台启动限制拦了）：" + t);
            return false;
        }
    }

    /**
     * 本应用是否拿到了「显示在其他应用上层」权限。
     *
     * <p>为什么关心它：Android 10 起后台启动 Activity 受限。正在运行的无障碍服务
     * 通常已经是系统豁免情形，但个别 ROM 认得更死，此时持有悬浮窗权限往往能让
     * 启动微信这一步走通。所以这是一个**间接指标**，不是「后台能力」本身，
     * 界面上的文案要按这个含义写，不要写成「后台权限已开启」。
     */
    public static boolean hasOverlayPermission(Context context) {
        return Settings.canDrawOverlays(context);
    }

    /** 跳转到本应用的「显示在其他应用上层」授权页。 */
    public static void requestOverlayPermission(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            DebugLog.w(TAG, "无法跳转到悬浮窗授权页：" + t);
        }
    }
}
