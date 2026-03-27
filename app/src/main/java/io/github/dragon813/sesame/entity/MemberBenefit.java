package io.github.dragon813.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.dragon813.sesame.util.idMap.MemberBenefitIdMap;

public class MemberBenefit extends IdAndName {

    public MemberBenefit(String i, String n) {
        id = i;
        name = n;
    }

    public static List<MemberBenefit> getList() {
        List<MemberBenefit> list = new ArrayList<>();
        Set<Map.Entry<String, String>> idSet = MemberBenefitIdMap.getMap().entrySet();
        for (Map.Entry<String, String> entry: idSet) {
            list.add(new MemberBenefit(entry.getKey(), entry.getValue()));
        }
        return list;
    }
}
