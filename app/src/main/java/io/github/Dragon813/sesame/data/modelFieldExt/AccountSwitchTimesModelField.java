package io.github.Dragon813.sesame.data.modelFieldExt;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

import io.github.Dragon813.sesame.data.AccountSwitchConfig;

/**
 * 切换账号定时执行时间点配置（全局配置）。
 *
 * 界面上是逗号分隔的时间列表（如 0800,2000），实际读写 config/config_common.json，
 * 不区分登录账号，所有账号共用同一份。
 */
public class AccountSwitchTimesModelField extends ListModelField.ListJoinCommaToStringModelField {

    public AccountSwitchTimesModelField(String code, String name, List<String> value) {
        super(code, name, value);
    }

    @Override
    public List<String> getValue() {
        return AccountSwitchConfig.getAccountSwitchScheduleTimes();
    }

    @Override
    @JsonIgnore
    public String getConfigValue() {
        return String.join(",", AccountSwitchConfig.getAccountSwitchScheduleTimes());
    }

    @Override
    @JsonIgnore
    public void setConfigValue(String configValue) {
        List<String> times = new ArrayList<>();
        if (configValue != null) {
            for (String item : configValue.split(",")) {
                if (item != null && !item.trim().isEmpty()) {
                    times.add(item.trim());
                }
            }
        }
        AccountSwitchConfig.setAccountSwitchScheduleTimes(times);
    }
}
