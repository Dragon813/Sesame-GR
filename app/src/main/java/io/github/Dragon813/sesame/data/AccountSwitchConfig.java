package io.github.Dragon813.sesame.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import io.github.Dragon813.sesame.util.FileUtil;
import io.github.Dragon813.sesame.util.Log;
import io.github.Dragon813.sesame.util.StringUtil;

/**
 * 所有账号共用的全局配置，统一保存在 config/config_common.json。
 *
 * 该文件不区分账号，模块 App 与支付宝注入进程共用同一份：
 * accountSwitchEnabled                    是否开启自动切换登录账号
 * accountSwitchIntervalMinutes            切号间隔(分钟，0=任务完成立即切)
 * accountSwitchRoundIntervalMinutes       切号轮次间隔(分钟)
 * accountSwitchAccounts                   账号列表，顺序即切号顺序
 * accountSwitchFinalAccountUid            切号最终保持账号（一轮结束后切换到该账号等待轮次间隔）
 * accountSwitchScheduleTimes              切换账号定时执行时间点，如 ["0800","2000"]
 * autoSlideCaptchaEnabled                 自动验证(滑动和拼图)
 * unlockAccountLimitEnabled               解除登录账号数限制
 *
 * 任意一项保存时都会把全部配置一起写回，避免多个字段分别落盘互相覆盖。
 */
public final class AccountSwitchConfig {

    public static final String FILE_NAME = "config_common.json";

    private static final String TAG = "AccountSwitchConfig";
    private static final int MAX_INTERVAL_MINUTES = 24 * 60;
    private static final long CACHE_TTL_MILLIS = 1000L;

    private static final Object LOCK = new Object();

    private static volatile Snapshot cache;
    private static volatile long cacheModified = Long.MIN_VALUE;
    private static volatile long cacheTime;

    private AccountSwitchConfig() {
    }

    public static File getFile() {
        return new File(FileUtil.CONFIG_DIRECTORY_FILE, FILE_NAME);
    }

    /** 读取配置（1 秒内复用缓存，避免任务/界面高频读取文件）。 */
    public static Snapshot read() {
        File file = getFile();
        long modified = file.exists() ? file.lastModified() : -1;
        long now = System.currentTimeMillis();
        Snapshot value = cache;
        if (value != null && modified == cacheModified && now - cacheTime < CACHE_TTL_MILLIS) {
            return value.copy();
        }
        synchronized (LOCK) {
            value = cache;
            if (value != null && modified == cacheModified
                    && System.currentTimeMillis() - cacheTime < CACHE_TTL_MILLIS) {
                return value.copy();
            }
            Snapshot loaded = load(file);
            cache = loaded;
            cacheModified = file.exists() ? file.lastModified() : -1;
            cacheTime = System.currentTimeMillis();
            return loaded.copy();
        }
    }

    /** 开关类高频读取：命中缓存时不复制账号列表。 */
    private static Snapshot peek() {
        Snapshot value = cache;
        if (value != null) {
            File file = getFile();
            long modified = file.exists() ? file.lastModified() : -1;
            if (modified == cacheModified && System.currentTimeMillis() - cacheTime < CACHE_TTL_MILLIS) {
                return value;
            }
        }
        return read();
    }

    /** 绕过缓存读取磁盘最新内容，用于跨进程写入前的读取。 */
    public static Snapshot readFresh() {
        synchronized (LOCK) {
            File file = getFile();
            Snapshot loaded = load(file);
            cache = loaded;
            cacheModified = file.exists() ? file.lastModified() : -1;
            cacheTime = System.currentTimeMillis();
            return loaded.copy();
        }
    }

    public static boolean isAccountSwitchEnabled() {
        try {
            return peek().isAccountSwitchEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    public static int getAccountSwitchIntervalMinutes() {
        return read().getAccountSwitchIntervalMinutes();
    }

    public static int getAccountSwitchRoundIntervalMinutes() {
        return read().getAccountSwitchRoundIntervalMinutes();
    }

    public static boolean isAutoSlideCaptchaEnabled() {
        try {
            return peek().isAutoSlideCaptchaEnabled();
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean isUnlockAccountLimitEnabled() {
        try {
            return peek().isUnlockAccountLimitEnabled();
        } catch (Throwable t) {
            return true;
        }
    }

    public static List<Account> getAccountSwitchAccounts() {
        return read().getAccountSwitchAccounts();
    }

    public static List<String> getAccountSwitchAccountUids() {
        List<String> uids = new ArrayList<>();
        for (Account account : read().getAccountSwitchAccounts()) {
            uids.add(account.getUid());
        }
        return uids;
    }

    public static synchronized boolean setAccountSwitchEnabled(boolean enabled) {
        Snapshot snapshot = readFresh();
        snapshot.setAccountSwitchEnabled(enabled);
        return write(snapshot);
    }

    public static synchronized boolean setAccountSwitchIntervalMinutes(int minutes) {
        Snapshot snapshot = readFresh();
        snapshot.setAccountSwitchIntervalMinutes(clampMinutes(minutes));
        return write(snapshot);
    }

    public static synchronized boolean setAccountSwitchRoundIntervalMinutes(int minutes) {
        Snapshot snapshot = readFresh();
        snapshot.setAccountSwitchRoundIntervalMinutes(clampMinutes(minutes));
        return write(snapshot);
    }

    public static synchronized boolean setAutoSlideCaptchaEnabled(boolean enabled) {
        Snapshot snapshot = readFresh();
        snapshot.setAutoSlideCaptchaEnabled(enabled);
        return write(snapshot);
    }

    public static synchronized boolean setUnlockAccountLimitEnabled(boolean enabled) {
        Snapshot snapshot = readFresh();
        snapshot.setUnlockAccountLimitEnabled(enabled);
        return write(snapshot);
    }

    public static String getAccountSwitchFinalAccountUid() {
        try {
            return read().getAccountSwitchFinalAccountUid();
        } catch (Throwable t) {
            return "";
        }
    }

    public static synchronized boolean setAccountSwitchFinalAccountUid(String uid) {
        Snapshot snapshot = readFresh();
        snapshot.setAccountSwitchFinalAccountUid(uid);
        return write(snapshot);
    }

    public static List<String> getAccountSwitchScheduleTimes() {
        return read().getAccountSwitchScheduleTimes();
    }

    public static synchronized boolean setAccountSwitchScheduleTimes(List<String> times) {
        Snapshot snapshot = readFresh();
        snapshot.setAccountSwitchScheduleTimes(normalizeScheduleTimes(times));
        return write(snapshot);
    }

    /** 按传入顺序整体替换账号列表（界面上下移动后保存）。 */
    public static synchronized boolean setAccountSwitchAccounts(List<Account> accounts) {
        Snapshot snapshot = readFresh();
        snapshot.setAccountSwitchAccounts(normalize(accounts));
        return write(snapshot);
    }

    /**
     * 模块启动时把支付宝已登录账号合并进配置：
     * 已有账号保持原位置，新账号追加到末尾。
     */
    public static synchronized boolean mergeHostAccounts(List<Account> hostAccounts) {
        if (hostAccounts == null || hostAccounts.isEmpty()) {
            return false;
        }
        Snapshot snapshot = readFresh();
        LinkedHashMap<String, Account> merged = new LinkedHashMap<>();
        for (Account account : snapshot.getAccountSwitchAccounts()) {
            if (account != null && isValidUid(account.getUid())) {
                merged.put(account.getUid(), account);
            }
        }
        boolean changed = false;
        for (Account host : hostAccounts) {
            if (host == null || !isValidUid(host.getUid())) {
                continue;
            }
            Account existing = merged.get(host.getUid());
            if (existing == null) {
                merged.put(host.getUid(), new Account(host.getUid(), host.getLoginId(), host.getName()));
                changed = true;
                continue;
            }
            if (!StringUtil.isEmpty(host.getLoginId())
                    && !host.getLoginId().equals(existing.getLoginId())) {
                existing.setLoginId(host.getLoginId());
                changed = true;
            }
            if (!StringUtil.isEmpty(host.getName()) && !host.getName().equals(existing.getName())) {
                existing.setName(host.getName());
                changed = true;
            }
        }
        if (!changed) {
            return true;
        }
        snapshot.setAccountSwitchAccounts(new ArrayList<>(merged.values()));
        return write(snapshot);
    }

    private static Snapshot load(File file) {
        Snapshot snapshot = new Snapshot();
        if (file == null || !file.exists() || !file.isFile()) {
            // 文件不存在：先按空列表处理，支付宝登录账号由调度器在后台线程补全，
            // 避免在主线程/持锁状态下反射调用支付宝服务导致宿主卡死。
            return snapshot;
        }
        String text = FileUtil.readFromFile(file);
        if (StringUtil.isEmpty(text)) {
            return snapshot;
        }
        try {
            JSONObject json = new JSONObject(text);
            snapshot.setAccountSwitchEnabled(json.optBoolean("accountSwitchEnabled", false));
            snapshot.setAccountSwitchIntervalMinutes(
                    clampMinutes(json.optInt("accountSwitchIntervalMinutes", 0)));
            snapshot.setAccountSwitchRoundIntervalMinutes(
                    clampMinutes(json.optInt("accountSwitchRoundIntervalMinutes", 0)));
            snapshot.setAutoSlideCaptchaEnabled(json.optBoolean("autoSlideCaptchaEnabled", true));
            snapshot.setUnlockAccountLimitEnabled(json.optBoolean("unlockAccountLimitEnabled", true));
            snapshot.setAccountSwitchFinalAccountUid(
                    json.optString("accountSwitchFinalAccountUid", ""));
            List<String> scheduleTimes = new ArrayList<>();
            JSONArray scheduleArray = json.optJSONArray("accountSwitchScheduleTimes");
            if (scheduleArray != null) {
                for (int i = 0; i < scheduleArray.length(); i++) {
                    Object item = scheduleArray.opt(i);
                    if (item != null) {
                        scheduleTimes.add(String.valueOf(item));
                    }
                }
            }
            snapshot.setAccountSwitchScheduleTimes(scheduleTimes);
            JSONArray array = json.optJSONArray("accountSwitchAccounts");
            List<Account> accounts = new ArrayList<>();
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    Object item = array.opt(i);
                    if (item instanceof JSONObject) {
                        JSONObject object = (JSONObject) item;
                        accounts.add(new Account(
                                object.optString("uid", null),
                                object.optString("loginId", null),
                                object.optString("name", null)));
                    } else if (item != null) {
                        accounts.add(new Account(String.valueOf(item), null, null));
                    }
                }
            }
            snapshot.setAccountSwitchAccounts(normalize(accounts));
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#解析 config_common.json 失败", t);
            snapshot.setAccountSwitchAccounts(new ArrayList<>());
        }
        return snapshot;
    }

    private static boolean write(Snapshot snapshot) {
        try {
            JSONObject json = new JSONObject();
            json.put("accountSwitchEnabled", snapshot.isAccountSwitchEnabled());
            json.put("accountSwitchIntervalMinutes",
                    clampMinutes(snapshot.getAccountSwitchIntervalMinutes()));
            json.put("accountSwitchRoundIntervalMinutes",
                    clampMinutes(snapshot.getAccountSwitchRoundIntervalMinutes()));
            json.put("autoSlideCaptchaEnabled", snapshot.isAutoSlideCaptchaEnabled());
            json.put("unlockAccountLimitEnabled", snapshot.isUnlockAccountLimitEnabled());
            json.put("accountSwitchFinalAccountUid",
                    snapshot.getAccountSwitchFinalAccountUid());
            JSONArray scheduleArray = new JSONArray();
            for (String time : snapshot.getAccountSwitchScheduleTimes()) {
                scheduleArray.put(time);
            }
            json.put("accountSwitchScheduleTimes", scheduleArray);
            JSONArray array = new JSONArray();
            for (Account account : snapshot.getAccountSwitchAccounts()) {
                JSONObject object = new JSONObject();
                object.put("uid", account.getUid());
                if (!StringUtil.isEmpty(account.getLoginId())) {
                    object.put("loginId", account.getLoginId());
                }
                if (!StringUtil.isEmpty(account.getName())) {
                    object.put("name", account.getName());
                }
                array.put(object);
            }
            json.put("accountSwitchAccounts", array);
            File file = getFile();
            boolean success = FileUtil.write2File(json.toString(2), file);
            if (success) {
                cache = snapshot.copy();
                cacheModified = file.exists() ? file.lastModified() : -1;
                cacheTime = System.currentTimeMillis();
                cleanupLegacyFlagFiles();
            }
            return success;
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#保存 config_common.json 失败", t);
            return false;
        }
    }

    /**
     * 清理旧版本用于保存全局开关的空标识文件（slide_captcha_on/_off 等）。
     * 这些值现在统一记录在 config_common.json 中。
     */
    private static void cleanupLegacyFlagFiles() {
        for (String name : new String[]{
                "slide_captcha_on", "slide_captcha_off",
                "unlock_account_limit_on", "unlock_account_limit_off"}) {
            File legacy = new File(FileUtil.CONFIG_DIRECTORY_FILE, name);
            if (legacy.exists() && !legacy.delete()) {
                Log.error(TAG + "#清理旧标识文件失败: " + legacy.getPath());
            }
        }
    }

    /** 过滤非法时间点并去重，保留用户输入顺序。支持 2/4/6 位：HH、HHmm、HHmmss。 */
    private static List<String> normalizeScheduleTimes(List<String> source) {
        List<String> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (String item : source) {
            if (item == null) {
                continue;
            }
            String time = item.trim();
            if (!isValidTimeStr(time) || result.contains(time)) {
                continue;
            }
            result.add(time);
        }
        return result;
    }

    private static boolean isValidTimeStr(String time) {
        int length = time.length();
        if (length != 2 && length != 4 && length != 6) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (!Character.isDigit(time.charAt(i))) {
                return false;
            }
        }
        try {
            int hour = Integer.parseInt(time.substring(0, 2));
            int minute = length >= 4 ? Integer.parseInt(time.substring(2, 4)) : 0;
            int second = length == 6 ? Integer.parseInt(time.substring(4, 6)) : 0;
            return hour >= 0 && hour <= 23
                    && minute >= 0 && minute <= 59
                    && second >= 0 && second <= 59;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<Account> normalize(List<Account> source) {
        LinkedHashMap<String, Account> unique = new LinkedHashMap<>();
        if (source != null) {
            for (Account account : source) {
                if (account == null) {
                    continue;
                }
                String uid = account.getUid() == null ? null : account.getUid().trim();
                if (!isValidUid(uid) || unique.containsKey(uid)) {
                    continue;
                }
                String loginId = StringUtil.isEmpty(account.getLoginId())
                        ? null : account.getLoginId().trim();
                String name = StringUtil.isEmpty(account.getName())
                        ? null : account.getName().trim();
                unique.put(uid, new Account(uid, loginId, name));
            }
        }
        return new ArrayList<>(unique.values());
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

    private static int clampMinutes(int minutes) {
        if (minutes < 0) {
            return 0;
        }
        return Math.min(minutes, MAX_INTERVAL_MINUTES);
    }

    public static final class Account {
        private String uid;
        private String loginId;
        private String name;

        public Account() {
        }

        public Account(String uid, String loginId, String name) {
            this.uid = uid;
            this.loginId = loginId;
            this.name = name;
        }

        public String getUid() {
            return uid;
        }

        public void setUid(String uid) {
            this.uid = uid;
        }

        public String getLoginId() {
            return loginId;
        }

        public void setLoginId(String loginId) {
            this.loginId = loginId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Account copy() {
            return new Account(uid, loginId, name);
        }
    }

    public static final class Snapshot {
        private boolean accountSwitchEnabled;
        private int accountSwitchIntervalMinutes;
        private int accountSwitchRoundIntervalMinutes;
        private boolean autoSlideCaptchaEnabled = true;
        private boolean unlockAccountLimitEnabled = true;
        private String accountSwitchFinalAccountUid = "";
        private List<String> accountSwitchScheduleTimes = new ArrayList<>();
        private List<Account> accountSwitchAccounts = new ArrayList<>();

        public boolean isAccountSwitchEnabled() {
            return accountSwitchEnabled;
        }

        public void setAccountSwitchEnabled(boolean accountSwitchEnabled) {
            this.accountSwitchEnabled = accountSwitchEnabled;
        }

        public int getAccountSwitchIntervalMinutes() {
            return accountSwitchIntervalMinutes;
        }

        public void setAccountSwitchIntervalMinutes(int accountSwitchIntervalMinutes) {
            this.accountSwitchIntervalMinutes = clampMinutes(accountSwitchIntervalMinutes);
        }

        public int getAccountSwitchRoundIntervalMinutes() {
            return accountSwitchRoundIntervalMinutes;
        }

        public void setAccountSwitchRoundIntervalMinutes(int accountSwitchRoundIntervalMinutes) {
            this.accountSwitchRoundIntervalMinutes =
                    clampMinutes(accountSwitchRoundIntervalMinutes);
        }

        public boolean isAutoSlideCaptchaEnabled() {
            return autoSlideCaptchaEnabled;
        }

        public void setAutoSlideCaptchaEnabled(boolean autoSlideCaptchaEnabled) {
            this.autoSlideCaptchaEnabled = autoSlideCaptchaEnabled;
        }

        public boolean isUnlockAccountLimitEnabled() {
            return unlockAccountLimitEnabled;
        }

        public void setUnlockAccountLimitEnabled(boolean unlockAccountLimitEnabled) {
            this.unlockAccountLimitEnabled = unlockAccountLimitEnabled;
        }

        public String getAccountSwitchFinalAccountUid() {
            return accountSwitchFinalAccountUid;
        }

        public void setAccountSwitchFinalAccountUid(String accountSwitchFinalAccountUid) {
            this.accountSwitchFinalAccountUid =
                    accountSwitchFinalAccountUid == null ? "" : accountSwitchFinalAccountUid;
        }

        public List<String> getAccountSwitchScheduleTimes() {
            return accountSwitchScheduleTimes;
        }

        public void setAccountSwitchScheduleTimes(List<String> accountSwitchScheduleTimes) {
            this.accountSwitchScheduleTimes = accountSwitchScheduleTimes == null
                    ? new ArrayList<>() : accountSwitchScheduleTimes;
        }

        public List<Account> getAccountSwitchAccounts() {
            return accountSwitchAccounts;
        }

        public void setAccountSwitchAccounts(List<Account> accountSwitchAccounts) {
            this.accountSwitchAccounts =
                    accountSwitchAccounts == null ? new ArrayList<>() : accountSwitchAccounts;
        }

        public Snapshot copy() {
            Snapshot copy = new Snapshot();
            copy.accountSwitchEnabled = accountSwitchEnabled;
            copy.accountSwitchIntervalMinutes = accountSwitchIntervalMinutes;
            copy.accountSwitchRoundIntervalMinutes = accountSwitchRoundIntervalMinutes;
            copy.autoSlideCaptchaEnabled = autoSlideCaptchaEnabled;
            copy.unlockAccountLimitEnabled = unlockAccountLimitEnabled;
            copy.accountSwitchFinalAccountUid = accountSwitchFinalAccountUid;
            copy.accountSwitchScheduleTimes = new ArrayList<>(accountSwitchScheduleTimes);
            List<Account> list = new ArrayList<>();
            for (Account account : accountSwitchAccounts) {
                if (account != null) {
                    list.add(account.copy());
                }
            }
            copy.accountSwitchAccounts = list;
            return copy;
        }
    }
}
