package com.elomary.wxautovideocall.automation;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Context;
import android.view.accessibility.AccessibilityWindowInfo;

import com.elomary.wxautovideocall.data.DebugLog;

import java.util.List;

/**
 * 判断当前哪个应用在前台。
 *
 * <p>刻意不申请 {@code PACKAGE_USAGE_STATS}（那是「有权查看使用情况」权限，
 * 需要引导使用者去系统设置里单独开启，对老人机场景太重）。改用无障碍服务
 * 自己的窗口列表，够用且零额外授权。
 */
public final class ForegroundApps {

    private static final String TAG = "ForegroundApps";

    private ForegroundApps() {
    }

    /** 返回当前前台应用的包名；判断不出来时返回 null。 */
    public static String currentPackage(AccessibilityService service) {
        try {
            List<AccessibilityWindowInfo> windows = service.getWindows();
            AccessibilityWindowInfo best = null;
            for (AccessibilityWindowInfo window : windows) {
                if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) {
                    continue;
                }
                if (window.isActive()) {
                    best = window;
                    break;
                }
                if (best == null) {
                    best = window;
                }
            }
            if (best != null && best.getRoot() != null) {
                CharSequence packageName = best.getRoot().getPackageName();
                if (packageName != null) {
                    return packageName.toString();
                }
            }
        } catch (Throwable t) {
            DebugLog.w(TAG, "读取窗口列表失败：" + t);
        }
        try {
            android.view.accessibility.AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) {
                return root.getPackageName().toString();
            }
        } catch (Throwable t) {
            DebugLog.w(TAG, "读取活动窗口根节点失败：" + t);
        }
        return null;
    }

    /** 屏幕是否处于锁屏状态。锁屏下无障碍点击会被系统拦掉，必须先提示使用者解锁。 */
    public static boolean isLocked(Context context) {
        try {
            KeyguardManager manager =
                    (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            return manager != null && manager.isKeyguardLocked();
        } catch (Throwable t) {
            return false;
        }
    }
}
