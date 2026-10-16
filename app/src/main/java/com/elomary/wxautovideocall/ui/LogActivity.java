package com.elomary.wxautovideocall.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.elomary.wxautovideocall.R;
import com.elomary.wxautovideocall.data.DebugLog;
import com.elomary.wxautovideocall.data.Prefs;

/**
 * 日志页。
 *
 * <p>存在的理由很具体：出问题的现场是奶奶的手机，开发者（本项目的作者）
 * 未必能马上拿到那台设备。所以必须有一条「一键复制日志发给我」的通道。
 *
 * <p>另外提供「导出当前微信界面节点树」，用来在真机上补齐
 * {@link com.elomary.wxautovideocall.automation.WeChatSelectors} 里
 * 那些尚未验证的 resource-id 与文案。
 */
public class LogActivity extends AppCompatActivity {

    private TextView logView;

    public static Intent createIntent(Context context) {
        return new Intent(context, LogActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        logView = findViewById(R.id.text_log);
        findViewById(R.id.button_copy_log).setOnClickListener(view -> copyLog());
        findViewById(R.id.button_dump_tree).setOnClickListener(view -> dumpTree());
        findViewById(R.id.button_refresh_log).setOnClickListener(view -> render());

        render();
    }

    private void render() {
        Prefs.restoreLog(this);
        String text = DebugLog.dump();
        if (text.isEmpty()) {
            text = "（还没有日志。先在设置里打开「记录调试日志」，然后点一次桌面图标拨号。）";
        }
        logView.setText(text);
        ScrollView scroll = findViewById(R.id.scroll_log);
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void copyLog() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "这台手机不支持复制", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("一键通话日志", DebugLog.dump()));
        Toast.makeText(this, "日志已复制，可以发出来了", Toast.LENGTH_LONG).show();
    }

    private void dumpTree() {
        com.elomary.wxautovideocall.service.CallAccessibilityService.dumpCurrentTree();
        render();
        Toast.makeText(this, "已把当前界面节点树写进日志末尾", Toast.LENGTH_LONG).show();
    }
}
