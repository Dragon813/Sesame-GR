package io.github.lazyimmortal.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.lazyimmortal.sesame.util.IconRegistry;
import io.github.lazyimmortal.sesame.util.idMap.MarathonIdMap;

public class AlipayMarathon extends IdAndName {
    private static List<AlipayMarathon> list;

    public AlipayMarathon(String i, String n) {
        id = i;
        name = n;
    }

    public static List<AlipayMarathon> getList() {
        if (list == null) {
            list = new ArrayList<>();
            Set<Map.Entry<String, String>> idSet = MarathonIdMap.getMap().entrySet();
            for (Map.Entry<String, String> entry : idSet) {
                list.add(new AlipayMarathon(entry.getKey(), entry.getValue()));
            }
        }
        for (AlipayMarathon item : list) {
            item.icon = IconRegistry.get(IconRegistry.SCOPE_MARATHON, item.id);
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
