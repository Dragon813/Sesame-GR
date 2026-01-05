package io.github.lazyimmortal.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.idMap.AntOceanAntiepTaskListMap;

public class AlipayAntOceanAntiepTaskList extends IdAndName {
    private static List<AlipayAntOceanAntiepTaskList> list;

    public AlipayAntOceanAntiepTaskList(String i, String n) {
        id = i;
        name = n;
    }

    public static List<AlipayAntOceanAntiepTaskList> getList() {
        if (list == null) {
            list = new ArrayList<>();
            for (Map.Entry<String, String> entry : AntOceanAntiepTaskListMap.getMap().entrySet()) {
                list.add(new AlipayAntOceanAntiepTaskList(entry.getKey(), entry.getValue()));
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
