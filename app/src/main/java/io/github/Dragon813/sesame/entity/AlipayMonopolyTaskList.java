package io.github.Dragon813.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.Dragon813.sesame.util.Log;
import io.github.Dragon813.sesame.util.idMap.AntOrchardDrawTaskListMap;
import io.github.Dragon813.sesame.util.idMap.AntOrchardTaskListMap;
import io.github.Dragon813.sesame.util.idMap.MonopolyTaskListMap;

public class AlipayMonopolyTaskList extends IdAndName {
    private static List<AlipayMonopolyTaskList> list;

    public AlipayMonopolyTaskList(String i, String n, String iconUrl) {
        id = i;
        name = n;
    icon = iconUrl;
    }

    public static List<AlipayMonopolyTaskList> getList() {
        if (list == null) {
            list = new ArrayList<>();
            for (Map.Entry<String, String> entry : MonopolyTaskListMap.getMap().entrySet()) {
                list.add(new AlipayMonopolyTaskList(entry.getKey(), entry.getKey(), entry.getValue()));
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
