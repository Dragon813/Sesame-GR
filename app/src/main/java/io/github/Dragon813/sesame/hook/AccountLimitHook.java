package io.github.Dragon813.sesame.hook;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListAdapter;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import dalvik.system.DexFile;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import io.github.Dragon813.sesame.util.FeatureFlagStore;
import io.github.Dragon813.sesame.util.Log;

/**
 * 账号限制 Hook 实现：
 *
 * 1. 列表入口 a(BaseAccountManagerActivity, List)：按字段组清空待删除集合并写入完整列表，setResult(null)；
 * 2. processAccountList(List)：清空 mNeedDeleteAccountList 并重写 queryAccountList，setResult(null)；
 * 3. getCount：读取 d / c / queryAccountList，取不到列表时返回 0；
 * 4. deleteMoreUserInfo：整方法置空；
 * 5. queryAccountList 查询入口与 V2 d() 作为额外保护保留。
 *
 * 字段组按版本三选一，不再同时清空所有候选字段：
 * (mNeedDeleteAccountList, queryAccountList)、(m, l)、(u, t)。
 * c 是 BaseAdapterA 的真实列表字段，禁止当作待删除字段清空。
 *
 * 仅保留本地账号登录记录，不解除服务端、设备或风控限制。
 */
public final class AccountLimitHook {

    private static final String TAG = AccountLimitHook.class.getSimpleName();
    private static final String BASE_ACTIVITY =
            "com.alipay.mobile.security.accountmanager.ui.BaseAccountManagerActivity";
    private static final String V2_ACTIVITY =
            "com.alipay.mobile.security.accountmanager.ui.AccountManagerV2Activity";

    /**
     * getCount 所在类。
     * 旧版：BaseAdapter 为 com.alipay.mobile.security.accountmanager.b.a，
     *       账号页实际使用其子类 b.b，列表字段 d。
     * 新版：BaseAdapter 为 securityappbiz.obf.l.a，账号页实际使用其子类 l.c，
     *       列表字段 c。
     * 类名随版本混淆，这里先用已确认的类名（含继承链基类），再扫描宿主 dex 补充。
     */
    private static final String[] KNOWN_COUNT_CLASSES = {
            "com.alipay.mobile.security.accountmanager.b.a",
            "com.alipay.mobile.security.accountmanager.b.b",
            "securityappbiz.obf.l.a",
            "securityappbiz.obf.l.c",
            "com.alipay.mobile.security.accountmanager.adapter.BaseAdapterA",
            "com.alipay.mobile.security.accountmanager.adapter.AccountManagerListAdapter",
            "com.alipay.mobile.security.accountmanager.AccountManagerBA",
            "com.alipay.mobile.security.accountmanager.widget.AccountManagerListAdapter"
    };
    private static final String ACCOUNT_PACKAGE_PREFIX =
            "com.alipay.mobile.security.accountmanager";
    /** getCount 类上可能承载账号列表的字段。 */
    private static final String[] COUNT_LIST_FIELDS = {
            "d", "c", "queryAccountList"
    };

    /** 待删除集合字段；c 不在此列。 */
    private static final String[] PENDING_FIELDS = {
            "mNeedDeleteAccountList", "m", "u"
    };
    /** 当前账号列表字段，包含三个 getCount 类的读取字段。 */
    private static final String[] CURRENT_LIST_FIELDS = {
            "queryAccountList", "d", "c", "l", "t"
    };
    /** 版本字段组：待删除字段 + 当前列表字段。 */
    private static final String[][] FIELD_PAIRS = {
            {"mNeedDeleteAccountList", "queryAccountList"},
            {"m", "l"},
            {"u", "t"}
    };

    private static final Map<Object, List<?>> CAPTURED_LISTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** 最近一次重写得到的完整账号列表，供运行时适配器接管。 */
    private static volatile List<?> sLastFullList;
    private static volatile long sLastFullListTime;
    /** 最近一次挂到账号页 ListView 上的适配器。 */
    private static volatile WeakReference<Object> sLastAdapter = new WeakReference<>(null);
    /** 已在运行时挂过 getCount 的适配器类，避免重复挂载。 */
    private static final Set<String> sAdapterClasses =
            Collections.synchronizedSet(new HashSet<String>());

    private static boolean installed;
    private static boolean sAdapterHookInstalled;
    private static volatile List<Class<?>> sCountClasses;

    private AccountLimitHook() {
    }

    public static synchronized void install(ClassLoader classLoader) {
        if (installed || classLoader == null) {
            return;
        }
        installed = true;
        hookBaseActivity(classLoader);
        hookCountClasses(classLoader);
        hookV2Activity(classLoader);
        hookListAdapter(classLoader);
        hookPageDiagnostics(classLoader);
    }

    private static void hookBaseActivity(ClassLoader classLoader) {
        try {
            Class<?> activityClass = XposedHelpers.findClass(BASE_ACTIVITY, classLoader);
            int[] counts = hookAccountListMethods(activityClass);
            int queryCount = hookQueryAccountList(activityClass);
            Log.i(TAG, "BaseAccountManagerActivity 列表 Hook: " + counts[0]
                    + ", processAccountList: " + counts[1]
                    + ", queryAccountList: " + queryCount);
            if (counts[0] == 0 && counts[1] == 0 && queryCount == 0) {
                dumpClassStructure(activityClass);
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#当前版本跳过 BaseAccountManagerActivity", t);
        }
    }

    private static void hookCountClasses(ClassLoader classLoader) {
        for (Class<?> clazz : resolveCountClasses(classLoader)) {
            int count = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        || !"getCount".equals(method.getName())
                        || method.getParameterTypes().length != 0
                        || (method.getReturnType() != Integer.TYPE
                        && method.getReturnType() != Integer.class)) {
                    continue;
                }
                hookGetCount(method);
                count++;
            }
            int queryCount = hookQueryAccountList(clazz);
            hookDeleteMoreUserInfo(clazz);
            Log.i(TAG, clazz.getName() + " getCount Hook: " + count
                    + ", queryAccountList: " + queryCount);
        }
    }

    /** 已知候选 + 宿主 dex 扫描，按完整类名去重。 */
    private static List<Class<?>> resolveCountClasses(ClassLoader classLoader) {
        List<Class<?>> cached = sCountClasses;
        if (cached != null) {
            return cached;
        }
        LinkedHashMap<String, Class<?>> resolved = new LinkedHashMap<>();
        for (String className : KNOWN_COUNT_CLASSES) {
            Class<?> clazz = findClassQuietly(className, classLoader);
            if (clazz != null) {
                resolved.put(clazz.getName(), clazz);
            }
        }
        for (Class<?> clazz : scanCountClasses(classLoader)) {
            resolved.put(clazz.getName(), clazz);
        }
        List<Class<?>> result = new ArrayList<>(resolved.values());
        sCountClasses = result;
        return result;
    }

    /**
     * 扫描宿主 APK 与 split APK 的账号包：
     * 只保留同时满足 getCount() 返回 int 且存在 d/c/queryAccountList 列表字段的类。
     */
    private static List<Class<?>> scanCountClasses(ClassLoader classLoader) {
        ArrayList<Class<?>> result = new ArrayList<>();
        Context context = ApplicationHook.getContext();
        if (context == null) {
            return result;
        }
        ApplicationInfo info = context.getApplicationInfo();
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        addPath(paths, info.sourceDir);
        addPath(paths, info.publicSourceDir);
        if (info.splitSourceDirs != null) {
            for (String path : info.splitSourceDirs) {
                addPath(paths, path);
            }
        }
        int scanned = 0;
        for (String path : paths) {
            DexFile dexFile = null;
            try {
                dexFile = new DexFile(new File(path));
                Enumeration<String> entries = dexFile.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement();
                    if (!name.startsWith(ACCOUNT_PACKAGE_PREFIX)) {
                        continue;
                    }
                    scanned++;
                    Class<?> clazz;
                    try {
                        clazz = Class.forName(name, false, classLoader);
                    } catch (Throwable ignored) {
                        continue;
                    }
                    if (isCountClassCandidate(clazz)) {
                        result.add(clazz);
                        Log.i(TAG, "dex 扫描到 getCount 类: " + name);
                    } else if (hasGetCountMethod(clazz)) {
                        Log.i(TAG, "getCount 类(字段未匹配): " + name
                                + "，List字段: " + describeListFields(clazz));
                    }
                }
            } catch (Throwable t) {
                Log.printStackTrace(TAG + "#扫描 dex 失败: " + path, t);
            } finally {
                if (dexFile != null) {
                    try {
                        dexFile.close();
                    } catch (Throwable ignored) {
                        // 关闭失败不影响 Hook。
                    }
                }
            }
        }
        Log.i(TAG, "账号包类扫描: " + scanned + "，候选: " + result.size());
        return result;
    }

    private static boolean isCountClassCandidate(Class<?> clazz) {
        boolean hasGetCount = false;
        for (Method method : clazz.getDeclaredMethods()) {
            if (!"getCount".equals(method.getName())
                    || method.getParameterTypes().length != 0) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (returnType == Integer.TYPE || returnType == Integer.class) {
                hasGetCount = true;
                break;
            }
        }
        if (!hasGetCount) {
            return false;
        }
        for (String fieldName : COUNT_LIST_FIELDS) {
            Field field = findField(clazz, fieldName);
            if (field != null && (List.class.isAssignableFrom(field.getType())
                    || Collection.class.isAssignableFrom(field.getType()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasGetCountMethod(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if ("getCount".equals(method.getName())
                    && method.getParameterTypes().length == 0) {
                Class<?> returnType = method.getReturnType();
                if (returnType == Integer.TYPE || returnType == Integer.class) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String describeListFields(Class<?> clazz) {
        StringBuilder builder = new StringBuilder();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isSynthetic()) {
                    continue;
                }
                Class<?> type = field.getType();
                if (!List.class.isAssignableFrom(type)
                        && !Collection.class.isAssignableFrom(type)) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(field.getName()).append(':').append(type.getSimpleName());
            }
            current = current.getSuperclass();
        }
        return builder.length() == 0 ? "无" : builder.toString();
    }

    /** 打印账号页真实字段与方法，用于适配未知版本。 */
    private static void dumpClassStructure(Class<?> clazz) {
        try {
            StringBuilder fields = new StringBuilder();
            int fieldCount = 0;
            Class<?> current = clazz;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    if (field.isSynthetic() || fieldCount >= 40) {
                        continue;
                    }
                    if (fieldCount > 0) {
                        fields.append(", ");
                    }
                    fields.append(field.getName()).append(':')
                            .append(field.getType().getSimpleName());
                    fieldCount++;
                }
                current = current.getSuperclass();
            }
            Log.i(TAG, clazz.getSimpleName() + " 字段: " + fields);

            StringBuilder methods = new StringBuilder();
            int methodCount = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isSynthetic() || methodCount >= 60) {
                    continue;
                }
                if (methodCount > 0) {
                    methods.append(", ");
                }
                methods.append(method.getName()).append('(');
                Class<?>[] types = method.getParameterTypes();
                for (int i = 0; i < types.length; i++) {
                    if (i > 0) {
                        methods.append(',');
                    }
                    methods.append(types[i].getSimpleName());
                }
                methods.append(")->").append(method.getReturnType().getSimpleName());
                methodCount++;
            }
            Log.i(TAG, clazz.getSimpleName() + " 方法: " + methods);
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#打印类结构失败", t);
        }
    }

    private static void addPath(Set<String> paths, String path) {
        if (path != null && !path.isEmpty()) {
            paths.add(path);
        }
    }

    private static void hookV2Activity(ClassLoader classLoader) {
        try {
            Class<?> activityClass = XposedHelpers.findClass(V2_ACTIVITY, classLoader);
            int[] counts = hookAccountListMethods(activityClass);
            int queryCount = hookQueryAccountList(activityClass);
            Log.i(TAG, "AccountManagerV2Activity 列表 Hook: " + counts[0]
                    + ", processAccountList: " + counts[1]
                    + ", queryAccountList: " + queryCount);
            if (counts[0] == 0 && counts[1] == 0 && queryCount == 0) {
                dumpClassStructure(activityClass);
            }
            // 未接管 V2 的 d()；阻断它会导致页面无法加载，这里只保留列表入口处理。
            Log.i(TAG, "AccountManagerV2Activity.d 不做阻断，仅依赖列表入口");
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#当前版本跳过 AccountManagerV2Activity", t);
        }
    }

    /**
     * 运行时定位账号列表适配器。
     * 已知类名和账号包 dex 扫描都可能拿不到真实混淆类，
     * 这里改为在账号页 ListView.setAdapter 之后直接接管适配器实例：
     * 把适配器自己的列表字段替换成完整账号列表，并 Hook 其 getCount 读取真实长度。
     */
    private static void hookListAdapter(ClassLoader classLoader) {
        if (sAdapterHookInstalled) {
            return;
        }
        sAdapterHookInstalled = true;
        int hooked = 0;
        String[] classNames = {"android.widget.AbsListView", "android.widget.ListView"};
        for (String className : classNames) {
            Class<?> clazz = findClassQuietly(className, classLoader);
            if (clazz == null) {
                continue;
            }
            try {
                Method setAdapter = clazz.getDeclaredMethod("setAdapter", ListAdapter.class);
                XpCompatEngine.hookMethod(setAdapter, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!FeatureFlagStore.isAccountListProtected()
                                || !(param.args[0] instanceof ListAdapter)) {
                            return;
                        }
                        ListAdapter adapter = (ListAdapter) param.args[0];
                        if (!isAccountAdapter(param.thisObject, adapter)) {
                            return;
                        }
                        sLastAdapter = new WeakReference<Object>(adapter);
                        applyFullListToAdapter(adapter);
                    }
                });
                hooked++;
                Log.i(TAG, "适配器定位入口已挂载: " + clazz.getName() + ".setAdapter");
            } catch (NoSuchMethodException ignored) {
                // 该类未声明 setAdapter，继续尝试下一个。
            } catch (Throwable t) {
                Log.printStackTrace(TAG + "#挂载 setAdapter 失败: " + className, t);
            }
        }
        Log.i(TAG, "账号适配器运行时定位 Hook: " + hooked);
    }

    /** 仅处理账号管理页的适配器，避免影响宿主其他 ListView。 */
    private static boolean isAccountAdapter(Object listView, ListAdapter adapter) {
        String adapterName = adapter.getClass().getName();
        if (adapterName.startsWith(ACCOUNT_PACKAGE_PREFIX)
                || adapterName.contains("AccountManager")) {
            return true;
        }
        if (!(listView instanceof View)) {
            return false;
        }
        Context context = ((View) listView).getContext();
        int depth = 0;
        while (context != null && depth < 8) {
            String name = context.getClass().getName();
            if (name.startsWith(ACCOUNT_PACKAGE_PREFIX)
                    || name.contains("AccountManager")) {
                return true;
            }
            if (!(context instanceof ContextWrapper)) {
                break;
            }
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) {
                break;
            }
            context = base;
            depth++;
        }
        return false;
    }

    /**
     * 把完整账号列表写入适配器自己的列表字段，并 Hook 该适配器类的 getCount。
     * 适配器早已用裁剪后的列表创建时，通过替换字段内容补齐到第 6 个及以后。
     */
    private static void applyFullListToAdapter(Object adapter) {
        List<?> full = sLastFullList;
        if (full == null || full.isEmpty()
                || System.currentTimeMillis() - sLastFullListTime > 60_000L) {
            return;
        }
        Field field = findAdapterListField(adapter);
        Object value = field != null ? readFieldValue(adapter, field) : null;
        boolean replaced = false;
        if (field != null) {
            if (value instanceof List && ((List<?>) value).size() >= full.size()) {
                Log.i(TAG, "适配器列表已完整: " + adapter.getClass().getSimpleName()
                        + "，数量: " + ((List<?>) value).size());
            } else {
                replaced = replaceListField(adapter, field, value, full);
            }
        }
        hookAdapterGetCount(adapter, field);
        Log.i(TAG, "账号适配器已接管: " + adapter.getClass().getName()
                + "，列表字段: " + (field == null ? "未找到"
                : field.getName() + ":" + field.getType().getSimpleName())
                + "，替换: " + replaced + "，完整数量: " + full.size());
        if (replaced) {
            try {
                XposedHelpers.callMethod(adapter, "notifyDataSetChanged");
            } catch (Throwable ignored) {
                // 适配器没有该方法时忽略。
            }
        }
    }

    /** 优先取已知列表字段名，其次取适配器上第一个非待删除集合字段。 */
    private static Field findAdapterListField(Object adapter) {
        for (String name : CURRENT_LIST_FIELDS) {
            Field field = findField(adapter.getClass(), name);
            if (isCollectionField(field)) {
                return field;
            }
        }
        Class<?> current = adapter.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || !isCollectionField(field)
                        || isPendingField(field.getName())) {
                    continue;
                }
                return field;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean isCollectionField(Field field) {
        return field != null && (List.class.isAssignableFrom(field.getType())
                || Collection.class.isAssignableFrom(field.getType()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean replaceListField(
            Object target, Field field, Object value, List<?> full) {
        try {
            field.setAccessible(true);
            if (!Modifier.isFinal(field.getModifiers())) {
                field.set(target, new ArrayList<>(full));
                return true;
            }
            if (value instanceof List) {
                List mutable = (List) value;
                mutable.clear();
                mutable.addAll(full);
                return true;
            }
            // final 且尚未初始化时无法替换。
            return false;
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#替换适配器列表失败: " + field.getName(), t);
            return false;
        }
    }

    /** 运行时 Hook 适配器类的 getCount，返回其自身列表的真实长度。 */
    private static void hookAdapterGetCount(Object adapter, Field listField) {
        Class<?> clazz = adapter.getClass();
        if (!sAdapterClasses.add(clazz.getName())) {
            return;
        }
        int count = 0;
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        || Modifier.isAbstract(method.getModifiers())
                        || !"getCount".equals(method.getName())
                        || method.getParameterTypes().length != 0
                        || (method.getReturnType() != Integer.TYPE
                        && method.getReturnType() != Integer.class)) {
                    continue;
                }
                XpCompatEngine.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!FeatureFlagStore.isAccountListProtected()) {
                            return;
                        }
                        // 优先读取接管时确定的列表字段，避免误取适配器上的其他集合字段。
                        List<?> accounts = null;
                        Object value = listField != null
                                ? readFieldValue(param.thisObject, listField) : null;
                        if (value instanceof List) {
                            accounts = (List<?>) value;
                        }
                        if (accounts == null) {
                            accounts = findAccountList(param.thisObject);
                        }
                        if (accounts != null) {
                            param.setResult(accounts.size());
                        }
                    }
                });
                count++;
            }
            current = current.getSuperclass();
        }
        Log.i(TAG, "运行时适配器 getCount Hook: " + clazz.getName() + "，数量: " + count);
    }

    private static Object readFieldValue(Object target, Field field) {
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 只读诊断：页面初始化、适配器创建与溢出清理的时序和数量。 */
    private static void hookPageDiagnostics(ClassLoader classLoader) {
        Class<?> v2 = findClassQuietly(
                "com.alipay.mobile.security.accountmanager.ui.AccountManagerV2Activity", classLoader);
        if (v2 != null) {
            hookNoArgDiagnostic(v2, "t", "视图初始化");
            hookNoArgDiagnostic(v2, "q", "适配器创建");
            hookNoArgDiagnostic(v2, "d", "视图初始化");
            hookNoArgDiagnostic(v2, "g", "溢出清理触发");
        }
        Class<?> task = findClassQuietly("securityappbiz.obf.o.t", classLoader);
        if (task == null) {
            return;
        }
        try {
            Method run = task.getDeclaredMethod("run");
            XpCompatEngine.hookMethod(run, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!FeatureFlagStore.isAccountListProtected()) {
                        return;
                    }
                    Object activity = readFieldValue(param.thisObject,
                            findField(param.thisObject.getClass(), "a"));
                    Log.i(TAG, "账号任务执行: 模式=" + readFieldValue(param.thisObject,
                            findField(param.thisObject.getClass(), "i"))
                            + describeAccountState(activity != null ? activity : param.thisObject));
                }
            });
        } catch (NoSuchMethodException ignored) {
            // 该版本没有 run 方法。
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#挂载账号任务诊断失败", t);
        }
    }

    private static void hookNoArgDiagnostic(Class<?> clazz, String name, String label) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (!name.equals(method.getName()) || method.getParameterTypes().length != 0) {
                continue;
            }
            XpCompatEngine.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!FeatureFlagStore.isAccountListProtected()) {
                        return;
                    }
                    Log.i(TAG, label + ": " + clazz.getSimpleName() + "." + name + "()"
                            + describeAccountState(param.thisObject));
                }
            });
        }
    }

    private static String describeAccountState(Object owner) {
        return "，保留=" + sizeOfList(owner, "l", "t")
                + "，溢出=" + sizeOfList(owner, "m", "u")
                + "，适配器=" + fieldClassName(owner, "A", "r", "h", "z");
    }

    private static int sizeOfList(Object owner, String... names) {
        for (String name : names) {
            Object value = readFieldValue(owner, findField(owner.getClass(), name));
            if (value instanceof Collection) {
                return ((Collection<?>) value).size();
            }
        }
        return -1;
    }

    private static String fieldClassName(Object owner, String... names) {
        for (String name : names) {
            Object value = readFieldValue(owner, findField(owner.getClass(), name));
            if (value != null) {
                return value.getClass().getSimpleName();
            }
        }
        return "null";
    }

    /** 列表入口 a(Activity, List) 与 processAccountList(List)。 */
    private static int[] hookAccountListMethods(Class<?> activityClass) {
        int listCount = 0;
        int processCount = 0;
        for (Method method : activityClass.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getReturnType() == Void.TYPE && "a".equals(method.getName())
                    && parameters.length == 2
                    && activityClass.isAssignableFrom(parameters[0])
                    && List.class.isAssignableFrom(parameters[1])) {
                XpCompatEngine.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!FeatureFlagStore.isAccountListProtected()
                                || !(param.args[1] instanceof List)) {
                            return;
                        }
                        Object target = param.args[0] != null ? param.args[0] : param.thisObject;
                        List<?> fullList = rewriteAccountLists(target, (List<?>) param.args[1]);
                        if (fullList == null) {
                            return;
                        }
                        CAPTURED_LISTS.put(target, fullList);
                        // 跳过原方法，不修改入参。
                        param.setResult(null);
                        Log.i(TAG, "账号列表入口已阻断，数量: " + fullList.size());
                    }
                });
                listCount++;
            } else if (method.getReturnType() == Void.TYPE
                    && !Modifier.isStatic(method.getModifiers())
                    && "processAccountList".equals(method.getName())
                    && parameters.length == 1
                    && List.class.isAssignableFrom(parameters[0])) {
                XpCompatEngine.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!FeatureFlagStore.isAccountListProtected()
                                || !(param.args[0] instanceof List)) {
                            return;
                        }
                        List<?> fullList = rewriteAccountLists(
                                param.thisObject, (List<?>) param.args[0]);
                        if (fullList == null) {
                            return;
                        }
                        CAPTURED_LISTS.put(param.thisObject, fullList);
                        // 跳过原方法。
                        param.setResult(null);
                        Log.i(TAG, "processAccountList 已阻断，数量: " + fullList.size());
                    }
                });
                processCount++;
            }
        }
        hookDeleteMoreUserInfo(activityClass);
        return new int[]{listCount, processCount};
    }

    /** 查询入口直接返回真实列表，作为额外保护保留。 */
    private static int hookQueryAccountList(Class<?> clazz) {
        int count = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    || !"queryAccountList".equals(method.getName())
                    || !List.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            XpCompatEngine.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!FeatureFlagStore.isAccountListProtected()) {
                        return;
                    }
                    List<?> real = findAccountList(param.thisObject);
                    if (real == null) {
                        real = CAPTURED_LISTS.get(param.thisObject);
                    }
                    if (real != null) {
                        param.setResult(real);
                    }
                }
            });
            count++;
        }
        return count;
    }

    /** 取不到列表时返回 0。 */
    private static void hookGetCount(Method method) {
        XpCompatEngine.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!FeatureFlagStore.isAccountListProtected()) {
                    return;
                }
                List<?> accounts = findAccountList(param.thisObject);
                param.setResult(accounts != null ? accounts.size() : 0);
            }
        });
    }

    /** H4：整方法置空，删除成为空操作。 */
    private static void hookDeleteMoreUserInfo(Class<?> clazz) {
        try {
            Method method = clazz.getDeclaredMethod("deleteMoreUserInfo");
            if (Modifier.isStatic(method.getModifiers())) {
                return;
            }
            XpCompatEngine.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (FeatureFlagStore.isAccountListProtected()) {
                        param.setResult(null);
                    }
                }
            });
            Log.i(TAG, "deleteMoreUserInfo 已替换为空操作: " + clazz.getName());
        } catch (NoSuchMethodException ignored) {
            // 该类没有该方法。
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#deleteMoreUserInfo 处理失败: " + clazz.getName(), t);
        }
    }

    /**
     * 按字段组三选一重写：
     * 只有待删除字段和当前列表字段同时存在且类型匹配时才生效，
     * 避免触碰 c/d 等 getCount 专用字段。
     */
    private static List<?> rewriteAccountLists(Object target, List<?> source) {
        if (target == null || source == null) {
            return null;
        }
        for (String[] pair : FIELD_PAIRS) {
            Field pending = findField(target.getClass(), pair[0]);
            Field current = findField(target.getClass(), pair[1]);
            if (pending == null || current == null) {
                continue;
            }
            if (!Collection.class.isAssignableFrom(pending.getType())
                    || !List.class.isAssignableFrom(current.getType())) {
                continue;
            }
            ArrayList<?> fullList = new ArrayList<>(source);
            try {
                Object value = pending.get(target);
                if (value instanceof Collection) {
                    ((Collection<?>) value).clear();
                } else {
                    pending.set(target, new ArrayList<>());
                }
            } catch (Throwable t) {
                Log.printStackTrace(TAG + "#清空待删除字段失败: " + pair[0], t);
            }
            try {
                current.set(target, fullList);
            } catch (Throwable t) {
                Log.printStackTrace(TAG + "#写入账号列表失败: " + pair[1], t);
                return null;
            }
            Log.i(TAG, "应用字段组 " + pair[0] + "/" + pair[1]
                    + "，数量: " + fullList.size());
            sLastFullList = fullList;
            sLastFullListTime = System.currentTimeMillis();
            // 适配器可能先于列表入口创建，捕获到完整列表后回填一次。
            Object adapter = sLastAdapter.get();
            if (adapter instanceof ListAdapter) {
                applyFullListToAdapter(adapter);
            }
            return fullList;
        }
        Log.i(TAG, "未匹配到账号字段组，保留原流程");
        return null;
    }

    private static List<?> findAccountList(Object target) {
        if (target == null) {
            return null;
        }
        for (String name : CURRENT_LIST_FIELDS) {
            try {
                Field field = findField(target.getClass(), name);
                if (field != null && List.class.isAssignableFrom(field.getType())) {
                    Object value = field.get(target);
                    if (value instanceof List) {
                        return (List<?>) value;
                    }
                }
            } catch (Throwable t) {
                Log.printStackTrace(TAG + "#读取账号列表失败: " + name, t);
            }
        }
        // 混淆版本的字段名不可知，退化为按类型查找 List 字段。
        Class<?> current = target.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || !List.class.isAssignableFrom(field.getType())
                        || isPendingField(field.getName())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value instanceof List) {
                        return (List<?>) value;
                    }
                } catch (Throwable ignored) {
                    // 单个字段读取失败时继续查找。
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean isPendingField(String name) {
        for (String pending : PENDING_FIELDS) {
            if (pending.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Class<?> findClassQuietly(String className, ClassLoader classLoader) {
        try {
            return XposedHelpers.findClass(className, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
