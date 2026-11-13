package io.github.lazyimmortal.sesame.util;

import android.graphics.Bitmap;

import org.json.JSONObject;

import java.io.File;
import java.util.Collection;

/**
 * 任务图标缓存：统一收进 MAIN_DIRECTORY_FILE/taskicon.zip 单文件（与 avatar 平级），
 * 条目名为图标 URL 的 md5 + 真实后缀，zip 包不会被相册索引，目录下不再散落碎文件。
 * initXXXTaskListMap 同步任务列表时调用 {@link #predownload(Collection)} 下载保存。
 */
public class TaskIconUtil {

    /** 提取任务图标：主字段优先，取不到再用奖励图标兜底 */
    private static final String[] ICON_KEYS = {"icon", "taskIcon", "iconUrl", "logo",
            "skuHeadImg", "spuHeadImg", "templateIconNew"};
    private static final String[] FALLBACK_ICON_KEYS = {"awardIcon"};

    public static String extractIcon(JSONObject... candidates) {
        String icon = pickIcon(candidates, ICON_KEYS);
        if (icon.isEmpty()) {
            icon = pickIcon(candidates, FALLBACK_ICON_KEYS);
        }
        return icon;
    }

    /** 依次在候选对象及其嵌套的 taskDisplayConfig/bizInfo/extendInfo(可能是JSON字符串) 中查找图标字段 */
    private static final String[] NESTED_CONTAINERS = {"taskDisplayConfig", "bizInfo", "extendInfo"};

    private static String pickIcon(JSONObject[] candidates, String[] keys) {
        if (candidates == null) {
            return "";
        }
        for (JSONObject jo : candidates) {
            String value = pickIconDirect(jo, keys);
            if (!value.isEmpty()) {
                return value;
            }
        }
        //嵌套容器: 对象形式或 JSON 字符串形式
        for (JSONObject jo : candidates) {
            if (jo == null) {
                continue;
            }
            for (String container : NESTED_CONTAINERS) {
                String value = pickIconDirect(jo.optJSONObject(container), keys);
                if (!value.isEmpty()) {
                    return value;
                }
                String raw = jo.optString(container, "");
                if (raw.startsWith("{")) {
                    try {
                        value = pickIconDirect(new JSONObject(raw), keys);
                        if (!value.isEmpty()) {
                            return value;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return "";
    }

    private static String pickIconDirect(JSONObject jo, String[] keys) {
        if (jo == null) {
            return "";
        }
        for (String key : keys) {
            String value = jo.optString(key, "");
            if (value.startsWith("http")) {
                return value;
            }
        }
        return "";
    }

    /** 是否为有效的图标地址（idMap 中 value 即图标地址，空串表示无图标） */
    public static boolean isIconUrl(String value) {
        return value != null && value.startsWith("http");
    }

    /** 图标统一收进 sesame/icon.zip 单文件（条目 md5(url)+真实后缀） */
    private static ImageZipCache cache;

    private static synchronized ImageZipCache cache() {
        if (cache == null) {
            cache = new ImageZipCache(new File(FileUtil.MAIN_DIRECTORY_FILE, "icon.zip"));
        }
        return cache;
    }

    /** 同步读取已缓存的图标（不联网，未缓存返回 null） */
    public static Bitmap loadCached(String iconUrl) {
        return cache().load(iconUrl);
    }

    /** 批量下载保存任务图标（异步），已缓存的跳过，全部完成后一次性写入 zip */
    public static void predownload(Collection<String> iconUrls) {
        cache().predownload(iconUrls);
    }

    /** 同步下载（列表项滚动到可视区时兜底），返回 Bitmap */
    public static Bitmap downloadAndGet(String iconUrl) {
        return cache().loadOrDownload(iconUrl);
    }
}
