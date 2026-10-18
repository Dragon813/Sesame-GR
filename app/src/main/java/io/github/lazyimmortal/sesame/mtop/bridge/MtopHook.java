package io.github.lazyimmortal.sesame.mtop.bridge;

import de.robv.android.xposed.XposedHelpers;
import io.github.lazyimmortal.sesame.util.Log;

/**
 * 主动获取 mtop 服务实例。
 * 走框架服务查询，不依赖任何既有请求。
 * 线程安全；失败会打日志。
 */
public class MtopHook {
    private static final String TAG = "MtopHook";
    private static volatile Object instance;

    public static Object resolveInstance(ClassLoader loader) {
        Object local = instance;
        if (local != null) return local;
        synchronized (MtopHook.class) {
            if (instance != null) return instance;
            try {
                Class<?> laa = XposedHelpers.findClass(
                    "com.alipay.mobile.framework.LauncherApplicationAgent", loader);
                Object app = XposedHelpers.callStaticMethod(laa, "getInstance");
                Object ctx = XposedHelpers.callMethod(app, "getMicroApplicationContext");
                Object svc = XposedHelpers.callMethod(ctx, "findServiceByInterface",
                    "com.alipay.android.phone.mobilesdk.mtop.MtopService");
                if (svc != null) {
                    instance = svc;
                } else {
                    Log.i(TAG, "MtopService 为 null（findServiceByInterface 未返回）");
                }
                return svc;
            } catch (Throwable t) {
                Log.i(TAG, "resolveInstance 失败: " + t);
                Log.printStackTrace(TAG, t);
                return null;
            }
        }
    }
}
