package io.github.lazyimmortal.sesame.hook;

import dalvik.system.DexFile;
import de.robv.android.xposed.XposedHelpers;
import io.github.lazyimmortal.sesame.util.Log;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 支付宝小程序游戏获取 alipayminimark。
 *
 * <p>调用方式：每次上报前现取，不缓存（mark 内含时间戳，每条请求的 minimark 都不同）。
 * 经 MicroApplicationContext.findServiceByInterface 拿 Nebula 服务实例后调
 * getAlipayMiniMark(appId, version)；静态工具方法（H5HttpUtils）在页面会话未建立时
 * 只会返回 108 字符的降级值。因此这里按方法名收集所有声明 getAlipayMiniMark(String, String)
 * 的目标：静态方法直接调，实例方法经 findServiceByInterface 取服务后调，只认 88 字符为有效。
 */
public class AlipayMiniMarkHelper {
    /** nebula 在不同支付宝版本里换过包名，先按已知名试，不中再扫 dex */
    private static final String[] KNOWN_CLASSES = {
            "com.alipay.mobile.nebula.util.H5HttpUtils",
            "com.alipay.mobile.nebula.utils.H5HttpUtils",
            "com.alibaba.mobile.nebula.util.H5HttpUtils",
            "com.alibaba.ariver.nebula.util.H5HttpUtils",
    };
    private static ClassLoader classLoader;
    /** 所有声明 getAlipayMiniMark(String, String) 的候选方法（静态/实例都在此列） */
    private static final List<Method> CANDIDATES = new CopyOnWriteArrayList<>();
    private static volatile boolean dexScanStarted;

    /**
     * 初始化 AlipayMiniMarkHelper
     * @param loader 应用类加载器
     */
    public static void init(ClassLoader loader) {
        classLoader = loader;
        // 注意: 此处不能用Log.record, attach阶段BaseModel可能未加载会NPE
        Log.i("AlipayMiniMarkHelper 初始化完成");
        for (String name : KNOWN_CLASSES) {
            if (addCandidate(name)) {
                Log.i("minimark 定位=" + name);
            }
        }
        // 已知类名优先入候选；dex 扫描兜底，防止支付宝改版换类名
        startDexScan(loader);
    }

    /**
     * 获取支付宝小程序标记。每次现取不缓存。
     *
     * <p>长度不是有效性标准：游戏类 appId 的 mark 可能为 108 且仍可用，88 只出现在
     * 阅读/救援队类 appId。mark 是否含用户身份取决于调用时序——必须先 getAuthCode
     * 建立该 appId 的授权上下文，再调本方法。
     *
     * @param appId 游戏appid
     * @param version 游戏版本号
     * @return 小程序标记字符串，失败返回空字符串
     */
    public static String getAlipayMiniMark(String appId, String version) {
        for (Method method : CANDIDATES) {
            try {
                boolean isStatic = Modifier.isStatic(method.getModifiers());
                Object receiver = null;
                if (!isStatic) {
                    receiver = resolveReceiver(method.getDeclaringClass());
                    if (receiver == null) {
                        continue;
                    }
                }
                Object result = method.invoke(receiver, appId, version);
                String mark = result instanceof String ? (String) result : "";
                if (!mark.isEmpty()) {
                    Log.debug("getAlipayMiniMark appId=" + appId + " version=" + version
                            + " via=" + method.getDeclaringClass().getSimpleName()
                            + " len=" + mark.length() + " mark=" + mark);
                    return mark;
                }
            } catch (Throwable t) {
                Log.debug("getAlipayMiniMark 调用失败 " + method.getDeclaringClass().getSimpleName() + ": " + t);
            }
        }
        Log.debug("getAlipayMiniMark 无可用候选 appId=" + appId);
        return "";
    }

    /** 实例方法的接收者：经支付宝框架 findServiceByInterface 取服务实例 */
    private static Object resolveReceiver(Class<?> declaringClass) {
        Object service = ApplicationHook.getServiceObject(declaringClass.getName());
        if (service != null) {
            return service;
        }
        for (Class<?> itf : declaringClass.getInterfaces()) {
            service = ApplicationHook.getServiceObject(itf.getName());
            if (service != null && declaringClass.isInstance(service)) {
                return service;
            }
        }
        return null;
    }

    private static boolean addCandidate(String className) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, classLoader);
            return clazz != null && addCandidate(clazz);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 收集类里所有 getAlipayMiniMark(String, String) 重载 */
    private static boolean addCandidate(Class<?> clazz) {
        boolean added = false;
        try {
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"getAlipayMiniMark".equals(method.getName())) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 2 && params[0] == String.class && params[1] == String.class) {
                    method.setAccessible(true);
                    if (!CANDIDATES.contains(method)) {
                        CANDIDATES.add(method);
                        added = true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return added;
    }

    /**
     * 后台按方法名遍历支付宝 dex，找出所有声明 getAlipayMiniMark 的类。
     * 只扫支付宝系包名，且不初始化类（findClassIfExists 不触发 static 块）。
     */
    private static void startDexScan(final ClassLoader loader) {
        if (dexScanStarted || loader == null) {
            return;
        }
        dexScanStarted = true;
        new Thread(() -> {
            int scanned = 0;
            try {
                Object pathList = XposedHelpers.getObjectField(loader, "pathList");
                Object[] dexElements = (Object[]) XposedHelpers.getObjectField(pathList, "dexElements");
                for (Object element : dexElements) {
                    File file;
                    try {
                        file = (File) XposedHelpers.getObjectField(element, "path");
                    } catch (Throwable ignored) {
                        continue;
                    }
                    if (file == null || !file.isFile()) {
                        continue;
                    }
                    DexFile dex = null;
                    try {
                        dex = new DexFile(file);
                        Enumeration<String> entries = dex.entries();
                        while (entries.hasMoreElements()) {
                            String name = entries.nextElement();
                            if (!isAlipayPackage(name)) {
                                continue;
                            }
                            scanned++;
                            if (addCandidate(name)) {
                                Log.i("minimark 扫描命中=" + name);
                            }
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        if (dex != null) {
                            try {
                                dex.close();
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
                Log.i("minimark 方法名扫描完毕 scanned=" + scanned + " 候选=" + CANDIDATES.size());
            } catch (Throwable t) {
                Log.i("minimark 扫描失败: " + t);
            }
        }, "sesame-minimark-scan").start();
    }

    private static boolean isAlipayPackage(String name) {
        return name.startsWith("com.alipay.") || name.startsWith("com.alibaba.")
                || name.startsWith("com.ali.") || name.startsWith("com.ta.")
                || name.startsWith("com.amap.") || name.startsWith("com.uc.");
    }

    // 私有化构造方法，避免类被实例化（对应Kotlin的object单例）
    private AlipayMiniMarkHelper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
