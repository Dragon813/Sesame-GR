package io.github.Dragon813.sesame.data.modelFieldExt;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

import io.github.Dragon813.sesame.R;
import io.github.Dragon813.sesame.data.AccountSwitchConfig;
import io.github.Dragon813.sesame.data.ModelField;
import io.github.Dragon813.sesame.util.Log;
import io.github.Dragon813.sesame.util.StringUtil;
import io.github.Dragon813.sesame.util.ToastUtil;

/**
 * 切号最终保持账号（单选）：
 * 选项动态取自 config/config_common.json 的账号列表，选中项也保存到该文件。
 * 一轮账号全部切完后，切换到该账号等待「切号轮次间隔」，再开始运行。
 */
public class AccountSwitchFinalAccountModelField extends ModelField<Integer> {

    /** 界面加载时的账号 uid 顺序，用于把单选下标还原成 uid（避免保存时列表顺序刚被调整而选错账号）。 */
    private volatile List<String> optionUids = java.util.Collections.emptyList();

    public AccountSwitchFinalAccountModelField(String code, String name) {
        super(code, name, 0);
    }

    @Override
    @JsonIgnore
    public String getType() {
        return "CHOICE";
    }

    @Override
    @JsonIgnore
    public Object getExpandKey() {
        List<AccountSwitchConfig.Account> accounts = AccountSwitchConfig.getAccountSwitchAccounts();
        if (accounts.isEmpty()) {
            optionUids = java.util.Collections.emptyList();
            return new String[]{"暂无账号"};
        }
        String[] keys = new String[accounts.size()];
        List<String> uids = new java.util.ArrayList<>(accounts.size());
        for (int i = 0; i < accounts.size(); i++) {
            keys[i] = displayName(accounts.get(i));
            uids.add(accounts.get(i).getUid());
        }
        optionUids = uids;
        return keys;
    }

    @Override
    public Integer getValue() {
        return selectedIndex();
    }

    @Override
    @JsonIgnore
    public String getConfigValue() {
        return String.valueOf(selectedIndex());
    }

    @Override
    @JsonIgnore
    public void setConfigValue(String configValue) {
        if (configValue == null) {
            return;
        }
        try {
            int index = Integer.parseInt(configValue.trim());
            String uid = null;
            List<String> uids = optionUids;
            if (index >= 0 && index < uids.size()) {
                uid = uids.get(index);
            }
            if (StringUtil.isEmpty(uid)) {
                List<AccountSwitchConfig.Account> accounts = AccountSwitchConfig.getAccountSwitchAccounts();
                if (index >= 0 && index < accounts.size()) {
                    uid = accounts.get(index).getUid();
                }
            }
            if (!StringUtil.isEmpty(uid)) {
                AccountSwitchConfig.setAccountSwitchFinalAccountUid(uid);
            }
        } catch (Throwable t) {
            Log.printStackTrace("AccountSwitchFinalAccountModelField#保存最终保持账号失败", t);
        }
    }

    @Override
    public View getView(Context context) {
        Button btn = new Button(context);
        btn.setText(getName());
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btn.setTextColor(ContextCompat.getColor(context, R.color.button));
        btn.setBackground(ContextCompat.getDrawable(context, R.drawable.button));
        btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        btn.setMinHeight(150);
        btn.setMaxHeight(180);
        btn.setPaddingRelative(40, 0, 40, 0);
        btn.setAllCaps(false);
        btn.setOnClickListener(v -> showChoiceDialog(v.getContext()));
        return btn;
    }

    private void showChoiceDialog(Context context) {
        List<AccountSwitchConfig.Account> accounts = AccountSwitchConfig.getAccountSwitchAccounts();
        if (accounts.isEmpty()) {
            ToastUtil.show(context, "暂无支付宝登录账号");
            return;
        }
        String[] keys = new String[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) {
            keys[i] = displayName(accounts.get(i));
        }
        final int[] checked = {selectedIndex()};
        new AlertDialog.Builder(context)
                .setTitle(getName())
                .setSingleChoiceItems(keys, checked[0], (dialog, which) -> checked[0] = which)
                .setPositiveButton(R.string.ok,
                        (dialog, which) -> setConfigValue(String.valueOf(checked[0])))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private int selectedIndex() {
        List<AccountSwitchConfig.Account> accounts = AccountSwitchConfig.getAccountSwitchAccounts();
        if (accounts.isEmpty()) {
            return 0;
        }
        String uid = AccountSwitchConfig.getAccountSwitchFinalAccountUid();
        if (!StringUtil.isEmpty(uid)) {
            for (int i = 0; i < accounts.size(); i++) {
                AccountSwitchConfig.Account account = accounts.get(i);
                if (account != null && uid.equals(account.getUid())) {
                    return i;
                }
            }
        }
        return 0;
    }

    private static String displayName(AccountSwitchConfig.Account account) {
        if (account == null) {
            return "未知";
        }
        // 与账号列表保持一致，用登录名(loginId)显示
        if (!StringUtil.isEmpty(account.getLoginId())) {
            return account.getLoginId();
        }
        if (!StringUtil.isEmpty(account.getName())) {
            return account.getName();
        }
        return StringUtil.isEmpty(account.getUid()) ? "未知" : account.getUid();
    }
}
