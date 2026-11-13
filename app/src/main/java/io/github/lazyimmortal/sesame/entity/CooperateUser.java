package io.github.lazyimmortal.sesame.entity;

import io.github.lazyimmortal.sesame.util.IconRegistry;
import io.github.lazyimmortal.sesame.util.idMap.CooperationIdMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CooperateUser extends IdAndName {

    public CooperateUser(String i, String n) {
        id = i;
        name = n;
    }

    public static List<CooperateUser> getList() {
        List<CooperateUser> list = new ArrayList<>();
        Set<Map.Entry<String, String>> idSet = CooperationIdMap.getMap().entrySet();
        for (Map.Entry<String, String> entry : idSet) {
            CooperateUser user = new CooperateUser(entry.getKey(), entry.getValue());
            user.icon = IconRegistry.get(IconRegistry.SCOPE_COOPERATE, entry.getKey());
            list.add(user);
        }
        return list;
    }

}
