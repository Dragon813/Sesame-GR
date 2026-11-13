package io.github.lazyimmortal.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.idMap.AntDodoTaskListMap;

public class AlipayAntDodoTaskList extends IdAndName {
    private static List<AlipayAntDodoTaskList> list;

    public AlipayAntDodoTaskList(String i, String n, String iconUrl) {
        id = i;
        name = n;
    icon = iconUrl;
    }

    public static List<AlipayAntDodoTaskList> getList() {
        if (list == null) {
            list = new ArrayList<>();
            for (Map.Entry<String, String> entry : AntDodoTaskListMap.getMap().entrySet()) {
                list.add(new AlipayAntDodoTaskList(entry.getKey(), entry.getKey(), entry.getValue()));
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
