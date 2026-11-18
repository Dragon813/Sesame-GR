package io.github.Dragon813.sesame.util;

/**
 * 水印工具类
 * 纯 Java 实现，返回固定水印配置
 */
public class WatermarkUtil {

    private static final String WATERMARK_TEXT = "免费模块 交流QQ群:694474777";
    private static final int WATERMARK_ALPHA = 30;
    private static final int WATERMARK_TEXT_SIZE = 16;
    private static final float WATERMARK_ROTATION = -30.0f;

    /**
     * 获取水印文本内容
     * @return 水印文本
     */
    public static String getWatermarkText() {
        return WATERMARK_TEXT;
    }

    /**
     * 获取水印透明度
     * @return 透明度值 0-255
     */
    public static int getWatermarkAlpha() {
        return WATERMARK_ALPHA;
    }

    /**
     * 获取水印文字大小
     * @return 文字大小 sp
     */
    public static int getWatermarkTextSize() {
        return WATERMARK_TEXT_SIZE;
    }

    /**
     * 获取水印旋转角度
     * @return 旋转角度
     */
    public static float getWatermarkRotation() {
        return WATERMARK_ROTATION;
    }
}
