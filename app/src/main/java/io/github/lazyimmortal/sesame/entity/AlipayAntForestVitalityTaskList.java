package io.github.lazyimmortal.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.idMap.AntForestVitalityTaskListMap;

public class AlipayAntForestVitalityTaskList extends IdAndName {
    private static List<AlipayAntForestVitalityTaskList> list;

    public AlipayAntForestVitalityTaskList(String i, String n) {
        id = i;
        name = n;
    }

    public static List<AlipayAntForestVitalityTaskList> getList() {
        if (list == null) {
            list = new ArrayList<>();
            for (Map.Entry<String, String> entry : AntForestVitalityTaskListMap.getMap().entrySet()) {
                list.add(new AlipayAntForestVitalityTaskList(entry.getKey(), entry.getValue()));
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
