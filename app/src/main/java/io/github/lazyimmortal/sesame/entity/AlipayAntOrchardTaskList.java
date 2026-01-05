package io.github.lazyimmortal.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.idMap.AntOrchardTaskListMap;

public class AlipayAntOrchardTaskList extends IdAndName {
    private static List<AlipayAntOrchardTaskList> list;

    public AlipayAntOrchardTaskList(String i, String n) {
        id = i;
        name = n;
    }

    public static List<AlipayAntOrchardTaskList> getList() {
        if (list == null) {
            list = new ArrayList<>();
            for (Map.Entry<String, String> entry : AntOrchardTaskListMap.getMap().entrySet()) {
                list.add(new AlipayAntOrchardTaskList(entry.getKey(), entry.getValue()));
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
