package io.github.lazyimmortal.sesame.util;

import android.content.Context;
import android.content.pm.PackageManager;

import org.json.JSONObject;

import io.github.lazyimmortal.sesame.BuildConfig;

public class LibraryUtil {
    private static final String TAG = LibraryUtil.class.getSimpleName();

    public static String getLibSesamePath(Context context) {
        String libSesamePath = null;
        try {
            libSesamePath = context.getPackageManager()
                    .getApplicationInfo(BuildConfig.APPLICATION_ID, 0)
                    .nativeLibraryDir + "/" + System.mapLibraryName("sesame");
        } catch (PackageManager.NameNotFoundException e) {
            ToastUtil.show(context, "请授予支付宝读取芝麻粒的权限");
            Log.record("请授予支付宝读取芝麻粒的权限");
        }
        return libSesamePath;
    }

    public static Boolean loadLibrary(String libraryName) {
        try {
            System.loadLibrary(libraryName);
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }


    // native code
    private static native boolean libraryCheckFarmTaskStatus(JSONObject task);

    public static Boolean checkFarmTaskStatus(JSONObject task) {
        return libraryCheckFarmTaskStatus(task); // 注释此行，重写实现
    }

    private static native boolean libraryDoFarmTask(JSONObject task);

    public static Boolean doFarmTask(JSONObject task) {
        return libraryDoFarmTask(task);
    }

    private static native boolean libraryDoFarmDrawTimesTask(JSONObject task);

    public static Boolean doFarmDrawTimesTask(JSONObject task) {
        return libraryDoFarmDrawTimesTask(task);
    }
}
