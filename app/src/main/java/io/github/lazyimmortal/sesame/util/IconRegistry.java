package io.github.lazyimmortal.sesame.util;

import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPC 列表项图标注册表：供"从响应拉取、供用户选择"的各类列表（树种、保护地、海滩、马拉松、
 * 古树、活力值权益、乐园兑奖等）保存条目图标。这些 idMap 的 value 是展示文本（含能量/次数），
 * 不能像任务清单那样直接放图标，因此用 scope+id 独立登记，单文件持久化到 iconRegistry.json。
 */
public class IconRegistry {

    public static final String SCOPE_TREE = "tree";
    public static final String SCOPE_RESERVE = "reserve";
    public static final String SCOPE_ANIMAL = "animal";
    public static final String SCOPE_BEACH = "beach";
    public static final String SCOPE_MARATHON = "marathon";
    public static final String SCOPE_ANCIENT_TREE = "ancientTree";
    public static final String SCOPE_VITALITY_BENEFIT = "vitalityBenefit";
    public static final String SCOPE_MALL_ITEM = "mallItem";
    public static final String SCOPE_COOPERATE = "cooperate";

    private static final Map<String, String> iconMap = new ConcurrentHashMap<>();

    /* 惰性加载标记: 任务运行时进程不会走设置界面的load(), 读写前必须先把磁盘数据装入内存, 否则saveAndPrefetch会把历史图标整体覆盖丢失 */
    private static volatile boolean loaded = false;

    private static void ensureLoaded() {
        if (!loaded) {
            synchronized (IconRegistry.class) {
                if (!loaded) {
                    load();
                    loaded = true;
                }
            }
        }
    }

    private static File file;

    private static File file() {
        if (file == null) {
            file = new File(FileUtil.MAIN_DIRECTORY_FILE, "iconRegistry.json");
        }
        return file;
    }

    public static synchronized void load() {
        iconMap.clear();
        try {
            iconMap.putAll(readDisk());
        } catch (Throwable t) {
            Log.printStackTrace(t);
        } finally {
            loaded = true;
        }
    }

    /* 读取磁盘现有登记, 供加载与保存时合并 */
    private static Map<String, String> readDisk() throws Exception {
        Map<String, String> map = new ConcurrentHashMap<>();
        String body = FileUtil.readFromFile(file());
        if (body.isEmpty()) {
            return map;
        }
        Map<String, String> parsed = JsonUtil.parseObject(body, new TypeReference<Map<String, String>>() {
        });
        if (parsed != null) {
            map.putAll(parsed);
        }
        return map;
    }

    public static String get(String scope, String id) {
        if (id == null) {
            return "";
        }
        ensureLoaded();
        String url = iconMap.get(scope + ":" + id);
        return url == null ? "" : url;
    }

    /** 记录图标，无效地址不覆盖已有值 */
    public static void put(String scope, String id, String iconUrl) {
        if (id == null || !TaskIconUtil.isIconUrl(iconUrl)) {
            return;
        }
        ensureLoaded();
        iconMap.put(scope + ":" + id, iconUrl);
    }

    /** 落盘并异步预下载全部已登记图标。只追加/修改, 不删历史: 先以磁盘现有数据为底再叠加内存,
     *  避免多进程各自基于过期快照保存时互相覆盖丢掉别的进程刚登记的图标 */
    public static synchronized void saveAndPrefetch() {
        ensureLoaded();
        try {
            Map<String, String> merged = readDisk();
            merged.putAll(iconMap);
            iconMap.clear();
            iconMap.putAll(merged);
            FileUtil.write2File(JsonUtil.toJsonString(merged), file());
        } catch (Throwable t) {
            Log.printStackTrace(t);
        }
        prefetch();
    }

    /** 异步预下载全部已登记图标（打开配置界面时调用，确保图标已入 icon.zip） */
    public static void prefetch() {
        TaskIconUtil.predownload(new HashSet<>(iconMap.values()));
    }
}
