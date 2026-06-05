package io.github.lazyimmortal.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.idMap.PathThemeMapListMap;
import io.github.lazyimmortal.sesame.util.idMap.ForestHuntIdMap;
import io.github.lazyimmortal.sesame.util.idMap.MemberCreditSesameTaskListMap;

public class WalkPathThemeMapList extends IdAndName {
    private static List<WalkPathThemeMapList> list;
    public static String[] nickNames;
    public static String[] values;

    public WalkPathThemeMapList(String i, String n) {
        id = i;
        name = n;
    }

    public static List<WalkPathThemeMapList> getList() {
        if (list == null) {
            list = new ArrayList<>();
            // 确保加载 JSON 数据
            PathThemeMapListMap.load();
            for (Map.Entry<String, String> entry : PathThemeMapListMap.getMap().entrySet()) {
                list.add(new WalkPathThemeMapList(entry.getKey(), entry.getValue()));
            }
            // 初始化 ChoiceModelField 需要的数组
            nickNames = new String[list.size()];
            values = new String[list.size()];
            for (int i = 0; i < list.size(); i++) {
                nickNames[i] = list.get(i).name;
                values[i] = list.get(i).id;
            }
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
