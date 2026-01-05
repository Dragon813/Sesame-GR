package io.github.lazyimmortal.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.idMap.AntFarmDrawMachineTaskListMap;

public class AlipayAntFarmDrawMachineTaskList extends IdAndName {
    private static List<AlipayAntFarmDrawMachineTaskList> list;

    public AlipayAntFarmDrawMachineTaskList(String i, String n) {
        id = i;
        name = n;
    }

    public static List<AlipayAntFarmDrawMachineTaskList> getList() {
        if (list == null) {
            list = new ArrayList<>();
            for (Map.Entry<String, String> entry : AntFarmDrawMachineTaskListMap.getMap().entrySet()) {
                list.add(new AlipayAntFarmDrawMachineTaskList(entry.getKey(), entry.getValue()));
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
