package io.github.lazyimmortal.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.lazyimmortal.sesame.util.idMap.GameCenterMallItemMap;

public class GameCenterMallItem extends IdAndName {

    public GameCenterMallItem(String i, String n) {
        id = i;
        name = n;
    }

    public static List<GameCenterMallItem> getList() {
        List<GameCenterMallItem> list = new ArrayList<>();
        Set<Map.Entry<String, String>> idSet = GameCenterMallItemMap.getMap().entrySet();
        for (Map.Entry<String, String> entry: idSet) {
            list.add(new GameCenterMallItem(entry.getKey(), entry.getValue()));
        }
        return list;
    }
}
