package io.github.dragon813.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.dragon813.sesame.util.idMap.PromiseSimpleTemplateIdMap;

public class PromiseSimpleTemplate extends IdAndName {

    public PromiseSimpleTemplate(String i, String n) {
        id = i;
        name = n;
    }

    public static List<PromiseSimpleTemplate> getList() {
        List<PromiseSimpleTemplate> list = new ArrayList<>();
        Set<Map.Entry<String, String>> idSet = PromiseSimpleTemplateIdMap.getMap().entrySet();
        for (Map.Entry<String, String> entry: idSet) {
            list.add(new PromiseSimpleTemplate(entry.getKey(), entry.getValue()));
        }
        return list;
    }
}
