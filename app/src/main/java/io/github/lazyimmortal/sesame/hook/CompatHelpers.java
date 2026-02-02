package io.github.lazyimmortal.sesame.hook;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 精简版 Xposed 兼容辅助类
 */
public class CompatHelpers {
    private CompatHelpers() {
        // 私有构造函数，防止实例化工具类
    }
    
    /**
     * 查找并 Hook 方法
     */
    public static void findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback.length == 0 || !(parameterTypesAndCallback[parameterTypesAndCallback.length - 1] instanceof XC_MethodHook)) {
            throw new IllegalArgumentException("no callback defined");
        }
        
        XC_MethodHook callback = (XC_MethodHook) parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
        Object[] paramTypes = dropLast(parameterTypesAndCallback);
        
        Method method = findMethodExact(clazz, methodName, paramTypes);
        io.github.lazyimmortal.sesame.hook.XpCompatEngine.hookMethod(method, callback);
    }
    
    /**
     * 查找并 Hook 构造函数（使用 ClassLoader）
     */
    public static void findAndHookConstructor(String className, ClassLoader classLoader, Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback.length == 0 || !(parameterTypesAndCallback[parameterTypesAndCallback.length - 1] instanceof XC_MethodHook)) {
            throw new IllegalArgumentException("no callback defined");
        }
        
        XC_MethodHook callback = (XC_MethodHook) parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
        Object[] paramTypes = dropLast(parameterTypesAndCallback);
        
        Class<?> clazz = XposedHelpers.findClass(className, classLoader);
        Constructor<?> constructor = findConstructorExact(clazz, classLoader, paramTypes);
        io.github.lazyimmortal.sesame.hook.XpCompatEngine.hookMethod(constructor, callback);
    }
    
    /**
     * 精确查找方法
     */
    private static Method findMethodExact(Class<?> clazz, String methodName, Object... parameterTypes) {
        return findMethodExact(clazz, null, methodName, parameterTypes);
    }
    
    /**
     * 精确查找方法（带 ClassLoader）
     */
    private static Method findMethodExact(Class<?> clazz, ClassLoader classLoader, String methodName, Object... parameterTypes) {
        Class<?>[] paramClasses = convertToClassArray(parameterTypes, classLoader);
        return XposedHelpers.findMethodExact(clazz, methodName, paramClasses);
    }
    
    /**
     * 精确查找构造函数（带 ClassLoader）
     */
    private static Constructor<?> findConstructorExact(Class<?> clazz, ClassLoader classLoader, Object... parameterTypes) {
        Class<?>[] paramClasses = convertToClassArray(parameterTypes, classLoader);
        return XposedHelpers.findConstructorExact(clazz, paramClasses);
    }
    
    /**
     * 将参数类型数组转换为 Class 数组（支持 Class/字符串类名/内部类）
     */
    private static Class<?>[] convertToClassArray(Object[] parameterTypes, ClassLoader classLoader) {
        List<Class<?>> paramClassList = new ArrayList<>();
        for (Object param : parameterTypes) {
            if (param instanceof Class<?>) {
                paramClassList.add((Class<?>) param);
            } else if (param instanceof String) {
                String className = (String) param;
                if (className.contains("$")) {
                    // 处理内部类（如 com.example.Outer$Inner）
                    String[] parts = className.split("\\$");
                    Class<?> outerClass = classLoader != null
                                          ? XposedHelpers.findClass(parts[0], classLoader)
                                          : loadClass(parts[0]);
                    
                    Class<?> innerClass = null;
                    for (Class<?> declaredClass : outerClass.getDeclaredClasses()) {
                        if (declaredClass.getSimpleName().equals(parts[1])) {
                            innerClass = declaredClass;
                            break;
                        }
                    }
                    if (innerClass == null) {
                        try {
                            throw new ClassNotFoundException("Inner class " + parts[1] + " not found in " + parts[0]);
                        }
                        catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    paramClassList.add(innerClass);
                } else {
                    // 普通类
                    Class<?> cls = classLoader != null
                                   ? XposedHelpers.findClass(className, classLoader)
                                   : loadClass(className);
                    paramClassList.add(cls);
                }
            } else {
                // 其他类型直接取其 Class
                paramClassList.add(param.getClass());
            }
        }
        return paramClassList.toArray(new Class[0]);
    }
    
    /**
     * 加载类（兜底逻辑）
     */
    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 移除数组最后一个元素（对应 Kotlin 的 dropLast(1)）
     */
    private static Object[] dropLast(Object[] array) {
        if (array.length <= 1) {
            return new Object[0];
        }
        Object[] result = new Object[array.length - 1];
        System.arraycopy(array, 0, result, 0, array.length - 1);
        return result;
    }
}