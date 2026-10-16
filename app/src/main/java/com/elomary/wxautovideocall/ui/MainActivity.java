package com.elomary.wxautovideocall.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.elomary.wxautovideocall.R;
import com.elomary.wxautovideocall.automation.WeChatLauncher;
import com.elomary.wxautovideocall.data.CallRequest;
import com.elomary.wxautovideocall.data.CallType;
import com.elomary.wxautovideocall.data.Contact;
import com.elomary.wxautovideocall.data.DebugLog;
import com.elomary.wxautovideocall.data.Prefs;
import com.elomary.wxautovideocall.service.CallAccessibilityService;
import com.elomary.wxautovideocall.system.ContactShortcuts;
import com.elomary.wxautovideocall.system.Permissions;

import java.util.List;

/**
 * 桌面快捷方式点进来看到的卡片。
 *
 * <p>这个 Activity 有两个入口，行为不同：
 * <ul>
 *   <li><b>联系人快捷方式</b>（推荐用法）：Intent 里带联系人 id，
 *       卡片上只显示这一个人，奶奶只需要选「视频通话」还是「语音通话」。
 *       快捷方式的名字就是联系人的名字，所以她在桌面上看到的是
 *       「给儿子」「给孙女」这样的图标，不需要在列表里找人。</li>
 *   <li><b>应用图标</b>：不带联系人，卡片上列出所有联系人，
 *       并且提示去设置页给每个人创建桌面快捷方式。</li>
 * </ul>
 *
 * <p>为什么不能做到「点一下就直接拨视频」：微信没有公开接口可以拨出通话，
 * 必须靠无障碍服务替使用者点微信里的按钮，而要定位到「视频通话」这个按钮，
 * 就必须先进入某个联系人的聊天页 —— 所以至少要有一次交互来确认「打给谁」和
 * 「打视频还是语音」。参见
 * {@link com.elomary.wxautovideocall.automation.CallFlowController}。
 *
 * <p>交互上针对老人做的取舍：
 * <ul>
 *   <li>按钮特大（96dp 高、30sp 字），两个并排也不会点偏；</li>
 *   <li>点卡片外面直接关掉，不用找「取消」；</li>
 *   <li>卡片不占满全屏，留出明显的可点空白；</li>
 *   <li>设置入口在卡片底部一行小字，不会误触。</li>
 * </ul>
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    /** 防止一次点击触发多次拨号（快速连点）。 */
    private boolean callRequested;

    /** 从快捷方式进来时带的联系人；为 null 表示是从应用图标进来的。 */
    private Contact shortcutContact;

    /** 给某个联系人构造快捷方式要用的 Intent。 */
    public static Intent createShortcutIntent(Context context, Contact contact) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(ContactShortcuts.EXTRA_CONTACT_ID, contact.getId());
        // 让每个联系人的快捷方式各自独立成一个任务，避免互相复用实例后
        // 显示成上一个人的名字。
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 卡片形式出现，点外面即退出。
        setFinishOnTouchOutside(true);
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = android.view.Gravity.CENTER;
        getWindow().setAttributes(params);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main_card);
        Prefs.restoreLog(this);

        shortcutContact = resolveShortcutContact();
        findViewById(R.id.button_settings_entry).setOnClickListener(view -> openSettings());

        TextView status = findViewById(R.id.text_status);
        status.setText(describeEnvironment());
        status.setVisibility(status.getText().length() == 0 ? View.GONE : View.VISIBLE);

        buildCard();
    }

    /**
     * 取出快捷方式里带的联系人。
     *
     * <p>名字也随 Intent 带了一份：联系人如果已经被删掉，
     * 至少还能把「你想打给谁」显示出来，而不是给一个空界面。
     */
    private Contact resolveShortcutContact() {
        Intent intent = getIntent();
        if (intent == null) {
            return null;
        }
        String contactId = intent.getStringExtra(ContactShortcuts.EXTRA_CONTACT_ID);
        if (contactId == null || contactId.isEmpty()) {
            return null;
        }
        for (Contact contact : Prefs.contacts(this)) {
            if (contactId.equals(contact.getId())) {
                return contact;
            }
        }
        DebugLog.w(TAG, "快捷方式指向的联系人已不存在：" + contactId);
        return null;
    }

    // ------------------------------------------------------------------ 卡片内容

    /** 环境不满足时给一句人话，让人知道该去修什么。 */
    private String describeEnvironment() {
        if (!WeChatLauncher.isInstalled(this)) {
            return getString(R.string.msg_wechat_not_installed);
        }
        if (!Permissions.isAccessibilityEnabled(this)) {
            return getString(R.string.msg_accessibility_off);
        }
        return "";
    }

    private void buildCard() {
        LinearLayout container = findViewById(R.id.container_contacts);
        TextView title = findViewById(R.id.text_title);
        TextView empty = findViewById(R.id.text_empty_hint);
        container.removeAllViews();

        if (shortcutContact != null) {
            // 从联系人快捷方式进来：只显示这一个人。
            title.setText(shortcutContact.getDisplayName());
            empty.setVisibility(View.GONE);
            container.addView(createContactRow(shortcutContact));
            return;
        }

        List<Contact> contacts = Prefs.contacts(this);
        if (contacts.isEmpty()) {
            title.setText(R.string.hint_choose_contact);
            empty.setVisibility(View.VISIBLE);
            empty.setText(R.string.msg_need_one_contact);
            empty.setOnClickListener(view -> openSettings());
            return;
        }

        // 从应用图标进来：列出所有人，并引导去创建快捷方式。
        title.setText(R.string.hint_choose_contact);
        empty.setVisibility(View.VISIBLE);
        empty.setText(R.string.hint_create_shortcuts);
        empty.setOnClickListener(view -> openSettings());

        Contact defaultContact = Prefs.defaultContact(this);
        if (defaultContact != null) {
            contacts.remove(defaultContact);
            contacts.add(0, defaultContact);
        }
        for (Contact contact : contacts) {
            container.addView(createContactRow(contact));
        }
    }

    private View createContactRow(Contact contact) {
        View row = getLayoutInflater().inflate(R.layout.item_contact_row,
                findViewById(R.id.container_contacts), false);
        TextView name = row.findViewById(R.id.text_contact_name);
        name.setText(contact.getDisplayName());

        Button video = row.findViewById(R.id.button_row_video);
        video.setOnClickListener(view -> requestCall(contact, CallType.VIDEO));

        Button voice = row.findViewById(R.id.button_row_voice);
        voice.setOnClickListener(view -> requestCall(contact, CallType.VOICE));

        return row;
    }

    // ------------------------------------------------------------------ 拨号

    private void requestCall(Contact contact, CallType type) {
        if (callRequested) {
            DebugLog.d(TAG, "忽略重复的拨号请求");
            return;
        }
        callRequested = true;

        if (!WeChatLauncher.isInstalled(this)) {
            showBlocked(getString(R.string.msg_wechat_not_installed));
            return;
        }
        if (!Permissions.isAccessibilityEnabled(this)) {
            // 无障碍没开的话点了不会有任何反应，先把人引到引导页。
            showBlocked(getString(R.string.msg_accessibility_off));
            startActivity(PermissionGuideActivity.createIntent(this));
            finish();
            return;
        }

        CallRequest request = new CallRequest(contact.getDisplayName(), type);
        DebugLog.i(TAG, "使用者请求：" + request);
        String[] reason = new String[1];
        if (CallAccessibilityService.requestCall(this, request, reason)) {
            // 拨号由无障碍服务接手，本页立刻退出，不要挡住微信的画面。
            finish();
        } else {
            callRequested = false;
            showBlocked(reason[0]);
        }
    }

    private void showBlocked(String message) {
        TextView status = findViewById(R.id.text_status);
        status.setText(message);
        status.setVisibility(View.VISIBLE);
        DebugLog.w(TAG, "拨号被拦下：" + message);
    }

    private void openSettings() {
        startActivity(SettingsActivity.createIntent(this));
        finish();
    }
}
