package io.github.Dragon813.sesame.data.modelFieldExt;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.Dragon813.sesame.R;
import io.github.Dragon813.sesame.data.AccountSwitchConfig;
import io.github.Dragon813.sesame.data.ModelField;
import io.github.Dragon813.sesame.util.JsonUtil;
import io.github.Dragon813.sesame.util.Log;
import io.github.Dragon813.sesame.util.StringUtil;
import io.github.Dragon813.sesame.util.idMap.UserIdMap;

/**
 * 账号配置列表控件：
 * 列表内容来自 config/config_common.json，保存时按用户上下移动后的顺序写回。
 */
public class AccountSwitchListModelField extends ModelField<List<String>> {

    public AccountSwitchListModelField(String code, String name, List<String> value) {
        super(code, name, value);
    }

    @Override
    @JsonIgnore
    public String getType() {
        return "ACCOUNT_LIST";
    }

    @Override
    public List<String> getValue() {
        return AccountSwitchConfig.getAccountSwitchAccountUids();
    }

    @Override
    @JsonIgnore
    public String getConfigValue() {
        return JsonUtil.toJsonString(AccountSwitchConfig.getAccountSwitchAccountUids());
    }

    @Override
    @JsonIgnore
    public void setConfigValue(String configValue) {
        if (configValue == null) {
            return;
        }
        try {
            List<AccountSwitchConfig.Account> current =
                    AccountSwitchConfig.readFresh().getAccountSwitchAccounts();
            Map<String, AccountSwitchConfig.Account> byUid = new LinkedHashMap<>();
            for (AccountSwitchConfig.Account account : current) {
                byUid.put(account.getUid(), account);
            }
            List<AccountSwitchConfig.Account> ordered = new ArrayList<>();
            JSONArray array = new JSONArray(configValue);
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                String uid = null;
                Boolean switchEnabled = null;
                // 新UI会带勾选状态提交对象；旧UI/旧配置只有 uid 字符串
                if (item instanceof JSONObject) {
                    JSONObject object = (JSONObject) item;
                    uid = object.optString("uid", null);
                    if (object.has("switchEnabled")) {
                        switchEnabled = object.optBoolean("switchEnabled", true);
                    }
                } else if (item != null) {
                    uid = String.valueOf(item);
                }
                if (StringUtil.isEmpty(uid) || containsUid(ordered, uid)) {
                    continue;
                }
                AccountSwitchConfig.Account account = byUid.get(uid);
                if (account == null) {
                    account = new AccountSwitchConfig.Account(uid, null, null);
                }
                if (switchEnabled != null) {
                    account.setSwitchEnabled(switchEnabled);
                }
                ordered.add(account);
            }
            // 兜底：解析结果缺失的账号保持原顺序追加，避免误删配置
            for (AccountSwitchConfig.Account account : current) {
                if (!containsUid(ordered, account.getUid())) {
                    ordered.add(account);
                }
            }
            AccountSwitchConfig.setAccountSwitchAccounts(ordered);
        } catch (Throwable t) {
            Log.printStackTrace("AccountSwitchListModelField#保存账号列表失败", t);
        }
    }

    @Override
    @JsonIgnore
    public Object getExpandValue() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AccountSwitchConfig.Account account : AccountSwitchConfig.getAccountSwitchAccounts()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", account.getUid());
            item.put("showName", showName(account));
            item.put("name", displayName(account));
            item.put("switchEnabled", account.isSwitchEnabled());
            result.add(item);
        }
        return result;
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
        btn.setOnClickListener(v -> showAccountDialog(v.getContext()));
        return btn;
    }

    private void showAccountDialog(Context context) {
        List<AccountSwitchConfig.Account> accounts = new ArrayList<>();
        for (AccountSwitchConfig.Account account : AccountSwitchConfig.getAccountSwitchAccounts()) {
            accounts.add(account.copy());
        }
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 20, 40, 20);
        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(container);

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> fillRows(context, container, accounts, () -> refresh[0].run());

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(getName())
                .setView(scrollView)
                .setPositiveButton(R.string.ok,
                        (d, which) -> AccountSwitchConfig.setAccountSwitchAccounts(accounts))
                .setNegativeButton(R.string.cancel, null)
                .create();
        refresh[0].run();
        dialog.show();
    }

    private void fillRows(Context context,
                          LinearLayout container,
                          List<AccountSwitchConfig.Account> accounts,
                          Runnable refresh) {
        container.removeAllViews();
        if (accounts.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("暂无账号，启动模块后会自动读取支付宝登录账号");
            empty.setPadding(0, 30, 0, 30);
            container.addView(empty);
            return;
        }
        TextView hint = new TextView(context);
        hint.setText("勾选的账号参与自动切换，顺序即切换顺序；用 ↑ ↓ 调整顺序");
        hint.setTextSize(12);
        hint.setPadding(0, 0, 0, 12);
        container.addView(hint);

        for (int i = 0; i < accounts.size(); i++) {
            AccountSwitchConfig.Account account = accounts.get(i);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 12, 0, 12);

            // 勾选才参与自动切换；上下移动只调整顺序，勾选状态跟随账号
            CheckBox checkBox = new CheckBox(context);
            checkBox.setChecked(account.isSwitchEnabled());
            checkBox.setPadding(0, 0, 0, 0);
            checkBox.setMinWidth(0);
            checkBox.setMinimumWidth(0);
            checkBox.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> account.setSwitchEnabled(isChecked));
            row.addView(checkBox);

            // 两行显示：第一行昵称，第二行原来的账号显示
            LinearLayout info = new LinearLayout(context);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(context, 4), 0, dp(context, 4), 0);
            info.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView showName = new TextView(context);
            showName.setText(showName(account));
            showName.setTextSize(14);
            info.addView(showName);

            TextView accountLabel = new TextView(context);
            accountLabel.setText(displayName(account));
            accountLabel.setTextSize(12);
            accountLabel.setAlpha(0.6f);
            info.addView(accountLabel);

            row.addView(info);

            final int index = i;
            Button up = createSmallButton(context, "↑");
            up.setEnabled(index > 0);
            up.setOnClickListener(v -> {
                if (index <= 0) {
                    return;
                }
                AccountSwitchConfig.Account previous = accounts.get(index - 1);
                accounts.set(index - 1, accounts.get(index));
                accounts.set(index, previous);
                refresh.run();
            });
            row.addView(up);

            Button down = createSmallButton(context, "↓");
            down.setEnabled(index < accounts.size() - 1);
            down.setOnClickListener(v -> {
                if (index >= accounts.size() - 1) {
                    return;
                }
                AccountSwitchConfig.Account next = accounts.get(index + 1);
                accounts.set(index + 1, accounts.get(index));
                accounts.set(index, next);
                refresh.run();
            });
            row.addView(down);

            container.addView(row);
        }
    }

    /**
     * 行内小按钮（上移/下移箭头）：宽度按内容测量，避免默认 Button 的 88dp 最小宽度撑太宽。
     */
    private static Button createSmallButton(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        // 按钮宽度 = 字符宽度 + 左右各半个字符宽度 = 字符宽度的 2 倍
        int textWidth = Math.round(button.getPaint().measureText(text));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.max(dp(context, 16), textWidth * 2), dp(context, 32));
        params.setMarginStart(dp(context, 8));
        button.setLayoutParams(params);
        return button;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static boolean containsUid(List<AccountSwitchConfig.Account> accounts, String uid) {
        for (AccountSwitchConfig.Account account : accounts) {
            if (account.getUid() != null && account.getUid().equals(uid)) {
                return true;
            }
        }
        return false;
    }

    /** 账号昵称：取好友表里的显示名，查不到时退回账号显示。 */
    private static String showName(AccountSwitchConfig.Account account) {
        String name = UserIdMap.getShowName(account == null ? null : account.getUid());
        return StringUtil.isEmpty(name) ? displayName(account) : name;
    }

    private static String displayName(AccountSwitchConfig.Account account) {
        if (account == null) {
            return "未知";
        }
        // 界面统一用登录名(loginId)显示账号，不再使用支付宝返回的显示名
        if (!StringUtil.isEmpty(account.getLoginId())) {
            return account.getLoginId();
        }
        if (!StringUtil.isEmpty(account.getName())) {
            return account.getName();
        }
        return StringUtil.isEmpty(account.getUid()) ? "未知" : account.getUid();
    }
}
