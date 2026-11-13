package io.github.lazyimmortal.sesame.entity;

import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AlipayUser extends IdAndName {

    public AlipayUser(String i, String n) {
        id = i;
        name = n;
    }

    public static List<AlipayUser> getList() {
        return getList(user -> true);
    }

    public static List<AlipayUser> getList(Filter filterFunc) {
        List<AlipayUser> list = new ArrayList<>();
        Map<String, UserEntity> userIdMap = UserIdMap.getUserMap();
        for (Map.Entry<String, UserEntity> entry : userIdMap.entrySet()) {
            UserEntity userEntity = entry.getValue();
            try {
                if (filterFunc.apply(userEntity)) {
                    AlipayUser user = new AlipayUser(entry.getKey(), userEntity.getFullName());
                    //头像地址交给 ListAdapter 按好友头像缓存（avatar 目录）显示
                    user.icon = userEntity.getHeadImageUrl();
                    list.add(user);
                }
            } catch (Throwable t) {
                Log.printStackTrace(t);
            }
        }
        return list;
    }

    public interface Filter {

        Boolean apply(UserEntity user);

    }

}
