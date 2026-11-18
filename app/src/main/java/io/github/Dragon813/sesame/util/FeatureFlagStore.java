package io.github.Dragon813.sesame.util;

import io.github.Dragon813.sesame.data.AccountSwitchConfig;

/**
 * 全局功能开关。
 * 值为所有账号共用，统一记录在 config/config_common.json 中，
 * 读取方式与切号配置一致（1 秒缓存 + 文件修改时间校验）。
 */
public final class FeatureFlagStore {

    private FeatureFlagStore() {
    }

    public static boolean isSlideCaptchaEnabled() {
        return AccountSwitchConfig.isAutoSlideCaptchaEnabled();
    }

    public static boolean isUnlockAccountLimitEnabled() {
        return AccountSwitchConfig.isUnlockAccountLimitEnabled();
    }

    /**
     * 是否需要保护本地账号登录记录。
     * 解除账号数量限制或自动切号任一开启时都必须保护，
     * 否则账号超过 5 个会被账号管理页清理，导致切号列表失效。
     */
    public static boolean isAccountListProtected() {
        return isUnlockAccountLimitEnabled() || AccountSwitchConfig.isAccountSwitchEnabled();
    }

    public static void setSlideCaptchaEnabled(boolean enabled) {
        AccountSwitchConfig.setAutoSlideCaptchaEnabled(enabled);
    }

    public static void setUnlockAccountLimitEnabled(boolean enabled) {
        AccountSwitchConfig.setUnlockAccountLimitEnabled(enabled);
    }
}
