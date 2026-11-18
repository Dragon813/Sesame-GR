package io.github.Dragon813.sesame.hook;

import android.os.Bundle;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import de.robv.android.xposed.XposedHelpers;
import io.github.Dragon813.sesame.data.AccountSwitchConfig;
import io.github.Dragon813.sesame.util.Log;
import io.github.Dragon813.sesame.util.StringUtil;

/**
 * 直接调用支付宝账号服务读取/切换登录账号。
 *
 * 读取走 AccountService.getLoginedAlipayUser，不经过账号管理页面，
 * 因此不受「登录账号超过 5 个就清除」页面逻辑影响；
 * 切换走 LoginService 的 7 参 login(type=switchAccount)。
 */
public final class AlipayAccountSwitchBridge {

    private static final String TAG = "AccountSwitchBridge";
    private static final String SERVICE_PREFIX =
            "com.alipay.mobile.framework.service.ext.security.";
    private static final int STATUS_SUCCESS = 1000;
    /** 目标账号登录凭证失效，支付宝要求重新输入账号密码。 */
    private static final int STATUS_NEED_RELOGIN = 1014;

    /** 上一次切号是否被支付宝要求重新输入账号密码（ResultStatus=1014）。 */
    private static volatile boolean manualLoginRequired;

    public static boolean isManualLoginRequired() {
        return manualLoginRequired;
    }

    private AlipayAccountSwitchBridge() {
    }

    /** 供全局配置反射调用；模块 App 进程调用会失败并返回空列表。 */
    public static List<AccountSwitchConfig.Account> fetchAccountsForConfig() {
        List<AccountSwitchConfig.Account> result = new ArrayList<>();
        try {
            for (HostAccount account : accounts()) {
                result.add(new AccountSwitchConfig.Account(
                        account.uid, account.loginId, account.label));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#读取登录账号失败", t);
        }
        return result;
    }

    /** 当前登录账号 uid，读取失败返回 null。 */
    public static String currentUid() {
        try {
            Object userInfo = XposedHelpers.callMethod(service("AuthService"), "getUserInfo");
            if (userInfo != null) {
                String uid = readString(userInfo, "getUserId");
                if (!StringUtil.isEmpty(uid)) {
                    return uid;
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "读取当前账号失败: " + t);
        }
        return ApplicationHook.getUserId();
    }

    /** 支付宝本地保存的全部登录账号。 */
    public static List<HostAccount> accounts() throws Throwable {
        LinkedHashMap<String, HostAccount> result = new LinkedHashMap<>();
        Object accountService = service("AccountService");
        Object raw = XposedHelpers.callMethod(accountService, "getLoginedAlipayUser");
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item == null) {
                    continue;
                }
                String uid = readString(item, "getUserId");
                if (!isValidUid(uid)) {
                    continue;
                }
                String loginId = readString(item, "getLogonId");
                String displayName = readString(item, "getDisplayName");
                HostAccount account = new HostAccount(uid, loginId, buildLabel(displayName, loginId));
                HostAccount existing = result.get(uid);
                if (existing == null) {
                    result.put(uid, account);
                } else if (!StringUtil.isEmpty(account.loginId)
                        && !account.loginId.equals(existing.loginId)) {
                    // 同一 uid 存在多个登录记录时保留第一条可用记录
                    Log.i(TAG, "账号存在多条登录记录，已保留首条: " + uid);
                }
            }
        }
        // 当前账号兜底（部分版本 getLoginedAlipayUser 不含当前账号）
        try {
            String uid = currentUid();
            if (isValidUid(uid) && !result.containsKey(uid)) {
                String loginId = readString(
                        XposedHelpers.callMethod(service("AuthService"), "getUserInfo"),
                        "getLogonId");
                result.put(uid, new HostAccount(uid, loginId, buildLabel(null, loginId)));
            }
        } catch (Throwable ignored) {
            // 当前账号读取失败不影响其它账号
        }
        return new ArrayList<>(result.values());
    }

    /**
     * 发起切号。返回 true 表示请求已被支付宝受理（最终结果仍需轮询 currentUid 确认）。
     */
    public static boolean switchTo(String uid, String loginId) throws Throwable {
        manualLoginRequired = false;
        if (!isValidUid(uid) || StringUtil.isEmpty(loginId)) {
            Log.record("自动切号🔄️ 缺少账号登录标识，无法切换: " + uid);
            return false;
        }
        Object loginService = service("LoginService");
        Method login = findLoginMethod(loginService.getClass());
        if (login == null) {
            Log.record("自动切号🔄️ 当前支付宝版本未找到切换账号方法");
            return false;
        }
        Bundle bundle = new Bundle();
        bundle.putString("targetUid", uid);
        Object result;
        try {
            result = login.invoke(loginService, loginId, null, "switchAccount", null, null, true, bundle);
        } catch (Throwable t) {
            Log.record("自动切号🔄️ 调用切号接口异常: " + t);
            throw t;
        }
        // 切号被拒时没有更多信息，把支付宝返回的状态打出来便于定位（尤其是从主账号切出失败时）
        Log.record("自动切号🔄️ 切号接口返回: " + describeResult(result)
                + "，当前=" + currentUid() + "，目标=" + maskLoginId(loginId) + "(" + uid + ")");
        if (result == null) {
            return false;
        }
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        Integer status = readStatus(result);
        if (status != null) {
            if (status == STATUS_NEED_RELOGIN) {
                manualLoginRequired = true;
                Log.record("自动切号🔄️ 目标账号需要先手动登录一次"
                        + "（支付宝返回 1014：请输入账号密码重新登录）: " + maskLoginId(loginId));
            }
            return status == STATUS_SUCCESS;
        }
        try {
            Object loginFlag = XposedHelpers.callMethod(result, "getLoginFlag");
            if (loginFlag != null) {
                return Boolean.TRUE.equals(loginFlag);
            }
        } catch (Throwable ignored) {
            // 继续尝试字段
        }
        try {
            Object loginFlag = XposedHelpers.getObjectField(result, "loginFlag");
            if (loginFlag != null) {
                return Boolean.TRUE.equals(loginFlag);
            }
        } catch (Throwable ignored) {
            // 无法识别的返回值按已受理处理，交给 currentUid 轮询确认
        }
        return true;
    }

    /** 读取返回对象里的结果状态码，取不到返回 null。 */
    private static Integer readStatus(Object result) {
        try {
            Object status = XposedHelpers.callMethod(result, "getResultStatus");
            if (status instanceof Number) {
                return ((Number) status).intValue();
            }
        } catch (Throwable ignored) {
            // 尝试字段
        }
        try {
            Object status = XposedHelpers.getObjectField(result, "resultStatus");
            if (status instanceof Number) {
                return ((Number) status).intValue();
            }
        } catch (Throwable ignored) {
            // 取不到状态码
        }
        return null;
    }

    /** 把切号接口返回值整理成一行日志，未知结构时至少打印类名。 */
    private static String describeResult(Object result) {
        if (result == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(result.getClass().getName());
        appendValue(sb, result, "getResultStatus");
        appendValue(sb, result, "getMemo");
        appendValue(sb, result, "getLoginFlag");
        appendValue(sb, result, "getSuccess");
        if (result instanceof Boolean) {
            sb.append('=').append(result);
        }
        return sb.toString();
    }

    private static void appendValue(StringBuilder sb, Object target, String methodName) {
        try {
            Object value = XposedHelpers.callMethod(target, methodName);
            if (value != null) {
                sb.append(' ').append(methodName.replace("get", "")).append('=').append(value);
            }
        } catch (Throwable ignored) {
            // 返回对象没有该字段时忽略
        }
    }

    private static Method findLoginMethod(Class<?> clazz) {
        Method fallback = null;
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"login".equals(method.getName()) || types.length != 7) {
                    continue;
                }
                if (types[0] != String.class || types[2] != String.class
                        || types[5] != Boolean.TYPE || types[6] != Bundle.class) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    return method;
                } catch (Throwable ignored) {
                    fallback = method;
                }
            }
            current = current.getSuperclass();
        }
        if (fallback == null) {
            for (Method method : clazz.getMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if ("login".equals(method.getName()) && types.length == 7
                        && types[0] == String.class && types[2] == String.class
                        && types[5] == Boolean.TYPE && types[6] == Bundle.class) {
                    return method;
                }
            }
        }
        return fallback;
    }

    private static Object service(String name) throws Throwable {
        Object context = ApplicationHook.getMicroApplicationContext();
        if (context == null) {
            throw new IllegalStateException("micro application context is null");
        }
        Object service = XposedHelpers.callMethod(
                context, "findServiceByInterface", SERVICE_PREFIX + name);
        if (service == null) {
            throw new IllegalStateException("service not found: " + name);
        }
        return service;
    }

    private static String readString(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Object value = XposedHelpers.callMethod(target, methodName);
            return value == null ? null : String.valueOf(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String buildLabel(String displayName, String loginId) {
        String maskedLoginId = maskLoginId(loginId);
        if (!StringUtil.isEmpty(displayName)) {
            return StringUtil.isEmpty(maskedLoginId)
                    ? displayName : displayName + "(" + maskedLoginId + ")";
        }
        return maskedLoginId;
    }

    private static String maskLoginId(String loginId) {
        if (StringUtil.isEmpty(loginId)) {
            return null;
        }
        if (loginId.length() <= 4) {
            return loginId;
        }
        if (loginId.length() <= 8) {
            return loginId.substring(0, 2) + "****" + loginId.substring(loginId.length() - 2);
        }
        return loginId.substring(0, 3) + "****"
                + loginId.substring(loginId.length() - 4);
    }

    private static boolean isValidUid(String uid) {
        if (StringUtil.isEmpty(uid) || uid.length() > 128) {
            return false;
        }
        for (int i = 0; i < uid.length(); i++) {
            char c = uid.charAt(i);
            boolean valid = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || c == '_' || c == '@' || c == '.' || c == '+' || c == ':' || c == '-';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    public static final class HostAccount {
        public final String uid;
        public final String loginId;
        public final String label;

        public HostAccount(String uid, String loginId, String label) {
            this.uid = uid;
            this.loginId = loginId;
            this.label = label;
        }
    }
}
