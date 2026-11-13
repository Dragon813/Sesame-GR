package io.github.lazyimmortal.sesame.entity;

import io.github.lazyimmortal.sesame.util.IconRegistry;
import io.github.lazyimmortal.sesame.util.idMap.AnimalIdMap;

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
        for (AlipayAnimal item : list) {
            item.icon = IconRegistry.get(IconRegistry.SCOPE_ANIMAL, item.id);
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
