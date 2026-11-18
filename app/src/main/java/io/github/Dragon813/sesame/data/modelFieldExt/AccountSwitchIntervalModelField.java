package io.github.Dragon813.sesame.data.modelFieldExt;

import io.github.Dragon813.sesame.data.AccountSwitchConfig;

/**
 * 切号间隔/切号轮次间隔（分钟）。
 * 值统一读写 config/config_common.json，不跟随当前账号的 config_v2 保存。
 */
public class AccountSwitchIntervalModelField extends IntegerModelField {

    /** 当前账号任务完成后等待多久切号。 */
    public static final int KIND_SWITCH = 0;
    /** 全部账号轮完回到第一个账号后的等待时间。 */
    public static final int KIND_ROUND = 1;

    private final int kind;

    public AccountSwitchIntervalModelField(String code, String name, int kind) {
        super(code, name, 0, 0, 24 * 60);
        this.kind = kind;
    }

    @Override
    public Integer getValue() {
        return kind == KIND_ROUND
                ? AccountSwitchConfig.getAccountSwitchRoundIntervalMinutes()
                : AccountSwitchConfig.getAccountSwitchIntervalMinutes();
    }

    @Override
    public String getConfigValue() {
        return String.valueOf(getValue());
    }

    @Override
    public void setConfigValue(String configValue) {
        int minutes = 0;
        if (configValue != null && !configValue.isEmpty()) {
            try {
                minutes = Integer.parseInt(configValue.trim());
            } catch (Exception ignored) {
                minutes = 0;
            }
        }
        minutes = Math.max(0, Math.min(minutes, 24 * 60));
        value = minutes;
        if (kind == KIND_ROUND) {
            AccountSwitchConfig.setAccountSwitchRoundIntervalMinutes(minutes);
        } else {
            AccountSwitchConfig.setAccountSwitchIntervalMinutes(minutes);
        }
    }
}
