package io.github.dragon813.sesame.data;

import android.annotation.SuppressLint;
import android.app.Application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonMappingException;
import lombok.Data;
import io.github.dragon813.sesame.util.FileUtil;
import io.github.dragon813.sesame.util.JsonUtil;
import io.github.dragon813.sesame.util.Log;

import java.io.File;

@Data
public class AppConfig {

    private static final String TAG = AppConfig.class.getSimpleName();

    private static final File APP_CONFIG_DIRECTORY_FILE = getExternalFilesDir();

    public static final AppConfig INSTANCE = new AppConfig();

    @JsonIgnore
    private boolean init;

    private Boolean newUI = true;
    private Boolean languageSimplifiedChinese = true;

    private static File getExternalFilesDir() {
        try {
            @SuppressLint("PrivateApi")
            Application application = (Application) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (application != null) {
                return application.getExternalFilesDir("Sesame");
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
        return null;
    }

    public static Boolean save() {
        return FileUtil.write2File(toSaveStr(), new File(APP_CONFIG_DIRECTORY_FILE, "appConfig.json"));
    }

    public static synchronized AppConfig load() {
        File appConfigFile = new File(APP_CONFIG_DIRECTORY_FILE, "appConfig.json");
        try {
            if (appConfigFile.exists()) {
                Log.i("加载APP配置");
                String json = FileUtil.readFromFile(appConfigFile);
                JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                String formatted = toSaveStr();
                if (formatted != null && !formatted.equals(json)) {
                    Log.i(TAG, "格式化APP配置");
                    Log.system(TAG, "格式化APP配置");
                    FileUtil.write2File(formatted, appConfigFile);
                }
            } else {
                unload();
                Log.i(TAG, "初始APP配置");
                Log.system(TAG, "初始APP配置");
                FileUtil.write2File(toSaveStr(), appConfigFile);
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            Log.i(TAG, "重置APP配置");
            Log.system(TAG, "重置APP配置");
            try {
                unload();
                FileUtil.write2File(toSaveStr(), appConfigFile);
            } catch (Exception e) {
                Log.printStackTrace(TAG, t);
            }
        }
        INSTANCE.setInit(true);
        return INSTANCE;
    }

    public static synchronized void unload() {
        try {
            JsonUtil.copyMapper().updateValue(INSTANCE, new AppConfig());
        } catch (JsonMappingException e) {
            Log.printStackTrace(TAG, e);
        }
    }

    public static String toSaveStr() {
        return JsonUtil.toFormatJsonString(INSTANCE);
    }

}