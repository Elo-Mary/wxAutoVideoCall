package com.elomary.wxautovideocall.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.elomary.wxautovideocall.R;
import com.elomary.wxautovideocall.data.Contact;
import com.elomary.wxautovideocall.data.DebugLog;
import com.elomary.wxautovideocall.data.Prefs;
import com.elomary.wxautovideocall.system.ContactShortcuts;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;

/**
 * 设置页。由「帮奶奶装手机的人」使用，不是奶奶本人用的，所以这里可以正常密度地放控件。
 *
 * <p>关键约束：这里填的「微信里的名字」必须和微信里显示的一模一样（含备注名），
 * 因为拨号流程是拿这个名字去微信搜索框里搜的。这一点在界面上要反复提醒，
 * 否则最容易出的问题就是「名字写错了，搜不到人」。
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    private LinearLayout contactContainer;
    private EditText nameInput;
    private TextView accessibilityStatus;

    public static Intent createIntent(Context context) {
        return new Intent(context, SettingsActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Prefs.restoreLog(this);

        contactContainer = findViewById(R.id.container_contact_list);
        nameInput = findViewById(R.id.input_contact_name);
        accessibilityStatus = findViewById(R.id.text_accessibility_status);

        findViewById(R.id.button_add_contact).setOnClickListener(view -> addContact());
        findViewById(R.id.button_save).setOnClickListener(view -> {
            Toast.makeText(this, R.string.msg_saved, Toast.LENGTH_SHORT).show();
            finish();
        });
        findViewById(R.id.button_permission_guide).setOnClickListener(
                view -> startActivity(PermissionGuideActivity.createIntent(this)));
        findViewById(R.id.button_view_log).setOnClickListener(
                view -> startActivity(LogActivity.createIntent(this)));
        findViewById(R.id.button_clear_log).setOnClickListener(view -> {
            DebugLog.clear();
            Prefs.persistLog(this);
            Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show();
        });

        setupDebugLogSwitch();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAccessibilityStatus();
        refreshContactList();
    }

    // ------------------------------------------------------------------ 控件初始化

    private void setupDebugLogSwitch() {
        SwitchMaterial debugLog = findViewById(R.id.switch_debug_log);
        debugLog.setChecked(Prefs.debugLogEnabled(this));
        debugLog.setOnCheckedChangeListener((buttonView, checked) -> {
            Prefs.setDebugLogEnabled(this, checked);
            DebugLog.i(TAG, "调试日志 = " + checked);
        });
    }

    private void refreshAccessibilityStatus() {
        boolean enabled = com.elomary.wxautovideocall.system.Permissions.isAccessibilityEnabled(this);
        accessibilityStatus.setText(enabled ? R.string.guide_status_ok : R.string.guide_status_missing);
        accessibilityStatus.setTextColor(getColor(enabled ? R.color.accent_video : R.color.accent_voice));
    }

    // ------------------------------------------------------------------ 联系人列表

    private void refreshContactList() {
        contactContainer.removeAllViews();
        List<Contact> contacts = Prefs.contacts(this);
        // 每次刷新都同步一次快捷方式：联系人改名后桌面图标上的名字也要跟着变。
        ContactShortcuts.sync(this, contacts);
        Contact defaultContact = Prefs.defaultContact(this);
        String defaultId = defaultContact == null ? "" : defaultContact.getId();

        for (Contact contact : contacts) {
            contactContainer.addView(createContactEditorRow(contact, contact.getId().equals(defaultId)));
        }
        if (contacts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.msg_need_one_contact);
            empty.setTextSize(16f);
            empty.setPadding(0, 16, 0, 16);
            contactContainer.addView(empty);
        }
    }

    private View createContactEditorRow(Contact contact, boolean isDefault) {
        View row = getLayoutInflater().inflate(R.layout.item_contact_edit, contactContainer, false);

        EditText nameField = row.findViewById(R.id.input_row_name);
        nameField.setText(contact.getDisplayName());

        RadioButton defaultRadio = row.findViewById(R.id.radio_row_default);
        defaultRadio.setChecked(isDefault);
        defaultRadio.setOnClickListener(view -> {
            // 改名后 id 不变，所以默认联系人不会因为改名而丢失。
            Prefs.setDefaultContactId(this, contact.getId());
            refreshContactList();
        });

        // 「放到桌面」：请系统弹确认框，把这个人固定成桌面快捷方式。
        // 这是奶奶真正要点的那个图标，所以按钮放在最显眼的位置。
        Button shortcut = row.findViewById(R.id.button_row_shortcut);
        if (ContactShortcuts.isPinned(this, contact)) {
            shortcut.setText(R.string.label_shortcut_pinned);
        } else {
            shortcut.setText(R.string.label_create_shortcut);
        }
        shortcut.setOnClickListener(view -> requestShortcut(contact));

        Button save = row.findViewById(R.id.button_row_save);
        save.setOnClickListener(view -> {
            String newName = nameField.getText().toString().trim();
            if (TextUtils.isEmpty(newName)) {
                Toast.makeText(this, R.string.label_contact_name, Toast.LENGTH_SHORT).show();
                return;
            }
            List<Contact> contacts = Prefs.contacts(this);
            for (int i = 0; i < contacts.size(); i++) {
                if (contacts.get(i).getId().equals(contact.getId())) {
                    contacts.set(i, contact.withDisplayName(newName));
                    break;
                }
            }
            Prefs.saveContacts(this, contacts);
            DebugLog.i(TAG, "联系人改名：" + contact.getDisplayName() + " -> " + newName);
            refreshContactList();
            Toast.makeText(this, R.string.msg_saved, Toast.LENGTH_SHORT).show();
        });

        Button delete = row.findViewById(R.id.button_row_delete);
        delete.setOnClickListener(view -> confirmDelete(contact));

        return row;
    }

    /**
     * 请系统把某个联系人固定成桌面快捷方式。
     *
     * <p>注意这里只能「请求」：系统会弹一个确认框让用户在桌面上点「添加」，
     * 应用无法静默添加。所以这个方法的反馈分两层 ——
     * 受理成功时说「请在弹框里点添加」，设备不支持时直接说明原因。
     */
    private void requestShortcut(Contact contact) {
        if (!ContactShortcuts.isSupported(this)) {
            Toast.makeText(this, R.string.msg_shortcut_unsupported, Toast.LENGTH_LONG).show();
            DebugLog.w(TAG, "设备不支持固定快捷方式");
            return;
        }
        // 回调 Intent 用同一个快捷方式 Intent，用户点「添加」后会落到主页。
        boolean accepted = ContactShortcuts.requestPin(this, contact,
                MainActivity.createShortcutIntent(this, contact));
        if (accepted) {
            Toast.makeText(this, R.string.msg_shortcut_requested, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.msg_shortcut_unsupported, Toast.LENGTH_LONG).show();
        }
    }

    /** 删除是不可逆操作，必须二次确认，并且要说清后果。 */
    private void confirmDelete(Contact contact) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.label_delete)
                .setMessage("确定要删掉「" + contact.getDisplayName() + "」吗？删掉后桌面上就不能一键打给他了。")
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.label_delete, (dialog, which) -> {
                    List<Contact> contacts = Prefs.contacts(this);
                    for (int i = 0; i < contacts.size(); i++) {
                        if (contacts.get(i).getId().equals(contact.getId())) {
                            contacts.remove(i);
                            break;
                        }
                    }
                    Prefs.saveContacts(this, contacts);
                    if (contact.getId().equals(Prefs.defaultContactId(this))) {
                        Prefs.setDefaultContactId(this, "");
                    }
                    // 桌面上的图标要立刻失效，否则奶奶还会去点一个打不通的图标。
                    ContactShortcuts.disable(this, contact);
                    DebugLog.i(TAG, "已删除联系人：" + contact.getDisplayName());
                    refreshContactList();
                })
                .show();
    }

    private void addContact() {
        String name = nameInput.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "请先填写微信里的名字", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Contact> contacts = Prefs.contacts(this);
        for (Contact existing : contacts) {
            if (existing.getDisplayName().equals(name)) {
                Toast.makeText(this, "已经有这个名字了：" + name, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Contact contact = Contact.create(name);
        contacts.add(contact);
        Prefs.saveContacts(this, contacts);
        // 第一个联系人自动成为默认联系人，省得还要多点一次。
        if (TextUtils.isEmpty(Prefs.defaultContactId(this))) {
            Prefs.setDefaultContactId(this, contact.getId());
        }
        nameInput.setText("");
        DebugLog.i(TAG, "新增联系人：" + name);
        refreshContactList();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 页面一离开就落盘，避免进程被杀导致日志丢失。
        Prefs.persistLog(this);
        DebugLog.d(TAG, "离开设置页，当前联系人数量=" + Prefs.contacts(this).size());
    }
}
