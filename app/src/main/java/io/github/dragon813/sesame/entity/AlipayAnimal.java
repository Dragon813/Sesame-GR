package io.github.dragon813.sesame.entity;

import io.github.dragon813.sesame.util.idMap.AnimalIdMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AlipayAnimal extends IdAndName {
    private static List<AlipayAnimal> list;

    public AlipayAnimal(String i, String n) {
        id = i;
        name = n;
    }

    public static List<AlipayAnimal> getList() {
        if (list == null) {
            list = new ArrayList<>();
            Set<Map.Entry<String, String>> idSet = AnimalIdMap.getMap().entrySet();
            for (Map.Entry<String, String> entry : idSet) {
                list.add(new AlipayAnimal(entry.getKey(), entry.getValue()));
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
