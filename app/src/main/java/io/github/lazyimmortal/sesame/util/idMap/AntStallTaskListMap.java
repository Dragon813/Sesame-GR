package io.github.lazyimmortal.sesame.util.idMap;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.lazyimmortal.sesame.util.FileUtil;
import io.github.lazyimmortal.sesame.util.JsonUtil;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.TaskIconUtil;

public class AntStallTaskListMap {

    private static final Map<String, String> idMap = new ConcurrentHashMap<>();

    private static final Map<String, String> readOnlyIdMap = Collections.unmodifiableMap(idMap);

    public static Map<String, String> getMap() {
        return readOnlyIdMap;
    }

    public static String get(String key) {
        return idMap.get(key);
    }

    public synchronized static void add(String key, String value) {
        //value 语义为图标地址: 新值无效时保留已缓存的旧图标, 没有则为空串
        if (!TaskIconUtil.isIconUrl(value)) {
            String old = idMap.get(key);
            value = TaskIconUtil.isIconUrl(old) ? old : "";
        }
        idMap.put(key, value);
    }

    public synchronized static void remove(String key) {
        idMap.remove(key);
    }

    public synchronized static void load() {
        idMap.clear();
        try {
            String body = FileUtil.readFromFile(FileUtil.getAntStallTaskListMapFile());
            if (!body.isEmpty()) {
                Map<String, String> newMap = JsonUtil.parseObject(body, new TypeReference<Map<String, String>>() {
                });
                //旧格式 value 存任务名, 新格式 value 存图标地址, 加载时归一化
                for (Map.Entry<String, String> entry : newMap.entrySet()) {
                    String value = entry.getValue();
                    idMap.put(entry.getKey(), TaskIconUtil.isIconUrl(value) ? value : "");
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    public synchronized static boolean save() {
        boolean success = FileUtil.write2File(JsonUtil.toJsonString(idMap), FileUtil.getAntStallTaskListMapFile());

        TaskIconUtil.predownload(idMap.values());

        return success;
    }

    public synchronized static void clear() {
        idMap.clear();
    }

}
