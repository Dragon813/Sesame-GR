#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_lazyimmortal_sesame_util_WatermarkUtil_getWatermarkTextNative(
        JNIEnv* env,
        jclass /* clazz */) {
    // 水印文本内容
    std::string watermarkText = "免费模块禁止倒卖 QQ群:123456";
    return env->NewStringUTF(watermarkText.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_lazyimmortal_sesame_util_WatermarkUtil_getWatermarkAlphaNative(
        JNIEnv* env,
        jclass /* clazz */) {
    // 水印透明度 0-255
    return 30;
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_lazyimmortal_sesame_util_WatermarkUtil_getWatermarkTextSizeNative(
        JNIEnv* env,
        jclass /* clazz */) {
    // 水印文字大小 sp
    return 16;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_io_github_lazyimmortal_sesame_util_WatermarkUtil_getWatermarkRotationNative(
        JNIEnv* env,
        jclass /* clazz */) {
    // 水印旋转角度
    return -30.0f;
}
