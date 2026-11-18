package io.github.Dragon813.sesame.data.modelFieldExt;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;

import io.github.Dragon813.sesame.data.AccountSwitchConfig;

/**
 * 全局开关（所有账号共用）。
 * 值统一读写 config/config_common.json，不跟随当前账号的 config_v2 保存。
 */
public class AccountSwitchBooleanModelField extends BooleanModelField {

    /** 自动切换登录账号。 */
    public static final int KIND_AUTO_SWITCH = 0;
    /** 自动验证(滑动和拼图)。 */
    public static final int KIND_SLIDE_CAPTCHA = 1;
    /** 解除登录账号数限制。 */
    public static final int KIND_UNLOCK_ACCOUNT_LIMIT = 2;

    private final int kind;

    public AccountSwitchBooleanModelField(String code, String name, Boolean value, int kind) {
        super(code, name, value);
        this.kind = kind;
    }

    @Override
    public Boolean getValue() {
        switch (kind) {
            case KIND_SLIDE_CAPTCHA:
                return AccountSwitchConfig.isAutoSlideCaptchaEnabled();
            case KIND_UNLOCK_ACCOUNT_LIMIT:
                return AccountSwitchConfig.isUnlockAccountLimitEnabled();
            default:
                return AccountSwitchConfig.isAccountSwitchEnabled();
        }
    }

    @Override
    public String getConfigValue() {
        return String.valueOf(getValue());
    }

    @Override
    public void setConfigValue(String configValue) {
        boolean enabled = Boolean.parseBoolean(configValue);
        value = enabled;
        switch (kind) {
            case KIND_SLIDE_CAPTCHA:
                AccountSwitchConfig.setAutoSlideCaptchaEnabled(enabled);
                break;
            case KIND_UNLOCK_ACCOUNT_LIMIT:
                AccountSwitchConfig.setUnlockAccountLimitEnabled(enabled);
                break;
            default:
                AccountSwitchConfig.setAccountSwitchEnabled(enabled);
                break;
        }
    }

    @Override
    public View getView(Context context) {
        Switch sw = new Switch(context);
        sw.setText(getName());
        sw.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sw.setMinHeight(150);
        sw.setMaxHeight(180);
        sw.setPaddingRelative(40, 0, 40, 0);
        sw.setChecked(Boolean.TRUE.equals(getValue()));
        sw.setOnClickListener(v -> setConfigValue(String.valueOf(((Switch) v).isChecked())));
        return sw;
    }
}
