package io.github.lazyimmortal.sesame.util;

/**
 * 水印工具类
 * 通过JNI从native层获取水印配置信息
 */
public class WatermarkUtil {
    
    private static boolean isLibraryLoaded = false;
    
    static {
        try {
            System.loadLibrary("watermark");
            isLibraryLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            //Log.printStackTrace(e);
            isLibraryLoaded = false;
        }
    }
    
    /**
     * 获取水印文本内容
     * @return 水印文本
     */
    public static String getWatermarkText() {
        if (!isLibraryLoaded) {
            return "免费模块 交流QQ群:694474777";
        }
        try {
            return getWatermarkTextNative();
        } catch (UnsatisfiedLinkError e) {
            //Log.printStackTrace(e);
            return "免费模块 交流QQ群:694474777";
        }
    }
    
    /**
     * 获取水印透明度
     * @return 透明度值 0-255
     */
    public static int getWatermarkAlpha() {
        if (!isLibraryLoaded) {
            return 30;
        }
        try {
            return getWatermarkAlphaNative();
        } catch (UnsatisfiedLinkError e) {
            //Log.printStackTrace(e);
            return 30;
        }
    }
    
    /**
     * 获取水印文字大小
     * @return 文字大小 sp
     */
    public static int getWatermarkTextSize() {
        if (!isLibraryLoaded) {
            return 16;
        }
        try {
            return getWatermarkTextSizeNative();
        } catch (UnsatisfiedLinkError e) {
            //Log.printStackTrace(e);
            return 16;
        }
    }
    
    /**
     * 获取水印旋转角度
     * @return 旋转角度
     */
    public static float getWatermarkRotation() {
        if (!isLibraryLoaded) {
            return -30.0f;
        }
        try {
            return getWatermarkRotationNative();
        } catch (UnsatisfiedLinkError e) {
            //Log.printStackTrace(e);
            return -30.0f;
        }
    }
    
    // Native方法声明
    private static native String getWatermarkTextNative();
    private static native int getWatermarkAlphaNative();
    private static native int getWatermarkTextSizeNative();
    private static native float getWatermarkRotationNative();
}
