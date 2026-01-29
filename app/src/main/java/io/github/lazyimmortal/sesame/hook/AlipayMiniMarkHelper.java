package io.github.lazyimmortal.sesame.hook;

import de.robv.android.xposed.XposedHelpers;
import io.github.lazyimmortal.sesame.util.Log;

/**
 * 支付宝小程序游戏获取alipayminimark
 * 用于调用目标应用的 H5HttpUtils.getAlipayMiniMark 方法
 */
public class AlipayMiniMarkHelper {
    private static final String TAG = "AlipayMiniMarkHelper";
    private static ClassLoader classLoader;
    
    /**
     * 初始化 AlipayMiniMarkHelper
     * @param loader 应用类加载器
     */
    public static void init(ClassLoader loader) {
        classLoader = loader;
        Log.record("AlipayMiniMarkHelper 初始化完成");
    }
    
    /**
     * 获取支付宝小程序标记
     * 通过调用 H5HttpUtils.getAlipayMiniMark 方法获取小程序标记
     *
     * @param str 游戏appid
     * @param str2 游戏版本号
     * @return 小程序标记字符串，失败返回空字符串
     */
    public static String getAlipayMiniMark(String str, String str2) {
        try {
            Class<?> h5HttpUtilsClass = XposedHelpers.findClass("com.alipay.mobile.nebula.util.H5HttpUtils", classLoader);
            Object resultObj = XposedHelpers.callStaticMethod(h5HttpUtilsClass, "getAlipayMiniMark", str, str2);
            String result = (resultObj instanceof String) ? (String) resultObj : "";
            return result;
        } catch (Throwable e) {
            Log.printStackTrace("获取alipayminimark失败: " + e.getMessage(), e);
            return "";
        }
    }
    
    // 私有化构造方法，避免类被实例化（对应Kotlin的object单例）
    private AlipayMiniMarkHelper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}