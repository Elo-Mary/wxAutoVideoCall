package com.elomary.wxautovideocall.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Toast;

import com.elomary.wxautovideocall.data.DebugLog;

/**
 * 拨号过程中的使用者反馈。
 *
 * <p>面向的使用者是不太会用智能手机的老人，所以：
 * <ul>
 *   <li>提示语必须是短句中文，不带技术词；</li>
 *   <li>失败时要震动一下，因为老人可能没看屏幕，
 *       震动是唯一能即时告知「这次没成功」的通道；</li>
 *   <li>失败提示要停留久一点，看得清。</li>
 * </ul>
 */
public final class FlowNotifier {

    private static final String TAG = "FlowNotifier";

    private FlowNotifier() {
    }

    private static void toast(Context context, String message, boolean longDuration) {
        // 无障碍服务没有 Activity 的 UI 线程归属问题，但 Toast 必须在主线程调用。
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(context.getApplicationContext(), message,
                        longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                DebugLog.w(TAG, "弹出提示失败：" + t);
            }
        });
    }

    public static void progress(Context context, String message) {
        toast(context, message, false);
    }

    public static void success(Context context) {
        toast(context, "已经拨出去了，等对方接听", true);
    }

    public static void failure(Context context, String reason) {
        String text = reason == null || reason.isEmpty() ? "这次没拨出去" : reason;
        toast(context, "没拨出去：" + text, true);
        vibrate(context);
    }

    private static void vibrate(Context context) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }
            // 两短一长：和「成功」区分开，老人靠手感也能分辨。
            long[] pattern = {0, 180, 120, 180, 120, 420};
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } catch (Throwable t) {
            DebugLog.w(TAG, "震动失败：" + t);
        }
    }
}
