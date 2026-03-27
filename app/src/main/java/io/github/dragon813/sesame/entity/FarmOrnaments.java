package io.github.dragon813.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.dragon813.sesame.util.idMap.FarmOrnamentsIdMap;

public class FarmOrnaments extends IdAndName{

    public FarmOrnaments(String i, String n) {
        id = i;
        name = n;
    }

    public static List<FarmOrnaments> getList() {
        List<FarmOrnaments> list = new ArrayList<>();
        Set<Map.Entry<String, String>> idSet = FarmOrnamentsIdMap.getMap().entrySet();
        for (Map.Entry<String, String> entry: idSet) {
            list.add(new FarmOrnaments(entry.getKey(), entry.getValue()));
        }
        return list;
    }
}
