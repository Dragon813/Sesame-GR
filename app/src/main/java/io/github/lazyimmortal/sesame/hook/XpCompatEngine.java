package io.github.lazyimmortal.sesame.hook;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;

/**
 * 精简版 Xposed 兼容引擎
 */
public class XpCompatEngine {
    private XpCompatEngine() {
        // 私有构造函数，防止实例化工具类
    }
    
    /**
     * Hook 方法
     */
    public static void hookMethod(Member method, XC_MethodHook callback) {
        XposedBridge.hookMethod(method, callback);
    }
    
    /**
     * Hook 所有构造函数
     */
    public static void hookAllConstructors(Class<?> hookClass, XC_MethodHook callback) {
        for (Constructor<?> constructor : hookClass.getDeclaredConstructors()) {
            hookMethod(constructor, callback);
        }
    }
}