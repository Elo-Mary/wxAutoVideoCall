package com.elomary.wxautovideocall.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.elomary.wxautovideocall.R;
import com.elomary.wxautovideocall.automation.WeChatLauncher;
import com.elomary.wxautovideocall.data.DebugLog;
import com.elomary.wxautovideocall.system.Permissions;

/**
 * 运行环境自检页。
 *
 * <p>面向的是「帮奶奶把手机设好的人」（也就是项目作者本人），
 * 所以这里可以把技术原因讲清楚，但要给出可以照做的步骤。
 *
 * <p>为什么需要这一页：本应用依赖无障碍服务，而它在每台手机上都要手动开启；
 * 很多国产 ROM 还会把后台应用杀掉，导致「点了图标没反应」。
 * 把这两件事集中到一个页面里，比让使用者在系统设置里乱找要好得多。
 */
public class PermissionGuideActivity extends AppCompatActivity {

    private static final String TAG = "PermissionGuide";

    private TextView accessibilityStatus;
    private TextView weChatStatus;
    private TextView overlayStatus;
    private TextView vendorHint;

    public static Intent createIntent(Context context) {
        return new Intent(context, PermissionGuideActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_guide);

        accessibilityStatus = findViewById(R.id.text_guide_accessibility_status);
        weChatStatus = findViewById(R.id.text_guide_wechat_status);
        overlayStatus = findViewById(R.id.text_guide_overlay_status);
        vendorHint = findViewById(R.id.text_guide_vendor_hint);

        findViewById(R.id.button_guide_accessibility).setOnClickListener(view -> {
            DebugLog.i(TAG, "跳转无障碍设置");
            Permissions.openAccessibilitySettings(this);
        });
        findViewById(R.id.button_guide_overlay).setOnClickListener(view -> {
            DebugLog.i(TAG, "跳转悬浮窗授权页");
            WeChatLauncher.requestOverlayPermission(this);
        });
        findViewById(R.id.button_guide_background).setOnClickListener(view -> {
            DebugLog.i(TAG, "跳转后台运行设置（厂商=" + Permissions.manufacturer() + "）");
            openBackgroundSettings();
        });
        findViewById(R.id.button_guide_details).setOnClickListener(view -> Permissions.openAppDetails(this));
        findViewById(R.id.button_guide_done).setOnClickListener(view -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        boolean accessibilityOk = Permissions.isAccessibilityEnabled(this);
        setStatus(accessibilityStatus, accessibilityOk);

        boolean weChatOk = WeChatLauncher.isInstalled(this);
        setStatus(weChatStatus, weChatOk);

        // 悬浮窗权限不是必需品，但在部分 ROM 上是「无障碍服务去启动微信」能否成功的
        // 关键变量，所以单独列一项，并说清它是可选的。
        boolean overlayOk = WeChatLauncher.hasOverlayPermission(this);
        setStatus(overlayStatus, overlayOk);

        vendorHint.setText(vendorHintText(Permissions.manufacturer()));
    }

    private void setStatus(TextView view, boolean ok) {
        view.setText(ok ? R.string.guide_status_ok : R.string.guide_status_missing);
        view.setTextColor(getColor(ok ? R.color.accent_video : R.color.accent_voice));
    }

    /**
     * 按厂商给具体路径。
     *
     * <p>这里只写「去哪里找」，不直接跳转具体组件：各 ROM 的设置页组件名不公开
     * 且随版本变化，硬跳转失败反而让人困惑。跳到电池优化页 / 应用详情页
     * 是各版本都存在的稳定落点。
     */
    private String vendorHintText(String manufacturer) {
        switch (manufacturer) {
            case "oppo":
                return "ColorOS / realme UI：设置 → 应用管理 → 找到本应用 → 允许「自启动」和「关联启动」，"
                        + "再到「耗电管理」里选「允许后台运行」。把本应用加进「最近任务」的锁定列表也有帮助。";
            case "vivo":
                return "OriginOS / Funtouch：设置 → 电池 → 后台耗电管理 → 本应用选「允许后台高耗电」；"
                        + "再到 i 管家 → 自启动管理里允许本应用自启动。";
            case "xiaomi":
                return "MIUI / HyperOS：设置 → 应用设置 → 本应用 → 允许「自启动」，"
                        + "省电策略选「无限制」，并在最近任务里给本应用加锁。";
            case "huawei":
                return "EMUI / HarmonyOS：设置 → 应用 → 应用启动管理 → 本应用改为「手动管理」，"
                        + "三项（自启动、关联启动、后台活动）全部允许。";
            default:
                return "如果点桌面图标后没反应，通常是系统限制了后台启动。"
                        + "请到「设置 → 应用 → 本应用」里允许后台运行和自启动，"
                        + "并把省电策略设为「不限制」。";
        }
    }

    private void openBackgroundSettings() {
        Permissions.openBatteryOptimizationSettings(this);
    }
}
