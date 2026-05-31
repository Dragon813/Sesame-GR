package io.github.lazyimmortal.sesame.entity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.lazyimmortal.sesame.model.task.antSports.AntSportsRpcCall;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.idMap.PathThemeMapListMap;

public class WalkPath extends IdAndName {
    private static List<WalkPath> list;

    public WalkPath(String i, String n) {
        id = i;
        name = n;
    }

    public static List<WalkPath> getList() {
        list = new ArrayList<>();
        Set<Map.Entry<String, String>> idSet = PathThemeMapListMap.getMap().entrySet();
        for (Map.Entry<String, String> entry : idSet) {
            list.add(new WalkPath(entry.getKey(), entry.getValue()));
        }
        return list;
    }

    public static List<WalkPath> getThemeListFromRpc() {
        list = new ArrayList<>();
        try {
            String result = AntSportsRpcCall.queryThemeList();
            JSONObject jo = new JSONObject(result);
            JSONObject data = jo.optJSONObject("data");
            if (data != null) {
                JSONArray themeList = data.optJSONArray("themeList");
                if (themeList != null) {
                    for (int i = 0; i < themeList.length(); i++) {
                        JSONObject theme = themeList.optJSONObject(i);
                        String themeId = theme.optString("themeId");
                        String themeName = theme.optString("themeName");
                        if (themeId != null && !themeId.isEmpty() && themeName != null && !themeName.isEmpty()) {
                            list.add(new WalkPath(themeId, themeName));
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.i("WalkPath", "获取主题列表失败:");
            Log.printStackTrace("WalkPath", t);
        }
        return list;
    }

    public static void remove(String id) {
        getList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
    }
}