package io.github.lazyimmortal.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.lazyimmortal.sesame.util.IconRegistry;
import io.github.lazyimmortal.sesame.util.idMap.VitalityBenefitIdMap;

public class VitalityBenefit extends IdAndName {

    public VitalityBenefit(String i, String n) {
        id = i;
        name = n;
    }

    public static List<VitalityBenefit> getList() {
        List<VitalityBenefit> list = new ArrayList<>();
        Set<Map.Entry<String, String>> idSet = VitalityBenefitIdMap.getMap().entrySet();
        for (Map.Entry<String, String> entry: idSet) {
            VitalityBenefit item = new VitalityBenefit(entry.getKey(), entry.getValue());
            item.icon = IconRegistry.get(IconRegistry.SCOPE_VITALITY_BENEFIT, entry.getKey());
            list.add(item);
        }
        return list;
    }
}
