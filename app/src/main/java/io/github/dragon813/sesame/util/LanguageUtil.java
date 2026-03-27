package io.github.dragon813.sesame.util;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

import io.github.dragon813.sesame.data.AppConfig;


public class LanguageUtil {
    public static Context setLocal(Context context) {
        AppConfig.load();
        if (AppConfig.INSTANCE.getLanguageSimplifiedChinese()) {
            // 忽略系统语言，强制使用简体中文
            Locale locale = new Locale("zh", "CN"); // 简体中文的区域代码
            return setLocal(context, locale);
        } else {
            return setLocal(context, Locale.getDefault());
        }
    }

    public static Context setLocal(Context context, Locale locale) {
        Configuration configuration = new Configuration();
        configuration.setLocale(locale);
        return context.createConfigurationContext(configuration);
    }
}
