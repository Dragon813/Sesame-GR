package io.github.lazyimmortal.sesame.util.idMap;

import com.fasterxml.jackson.core.type.TypeReference;
import de.robv.android.xposed.XposedHelpers;
import lombok.Getter;

import io.github.lazyimmortal.sesame.entity.UserEntity;
import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.util.FileUtil;
import io.github.lazyimmortal.sesame.util.JsonUtil;
import io.github.lazyimmortal.sesame.util.Log;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserIdMap {
    
    private static final Map<String, UserEntity> userMap = new ConcurrentHashMap<>();
    
    private static final Map<String, UserEntity> readOnlyUserMap = Collections.unmodifiableMap(userMap);
    
    @Getter
    private static String currentUid = null;
    
    public static Map<String, UserEntity> getUserMap() {
        return readOnlyUserMap;
    }
    
    public static Set<String> getUserIdSet() {
        return userMap.keySet();
    }
    
    public static Collection<UserEntity> getUserEntityCollection() {
        return userMap.values();
    }
    
    public synchronized static void initUser(String currentUserId) {
        setCurrentUserId(currentUserId);
        // 先同步加载本地缓存好友表(含本人), 保证轮次开始的自动统计/信息日志能立即转换昵称; 再由异步任务刷新为最新数据
        load(currentUserId);
        ApplicationHook.getMainHandler().post(() -> {
            ClassLoader loader;
            try {
                loader = ApplicationHook.getClassLoader();
            } catch (Exception e) {
                Log.i("Error getting classloader");
                return;
            }
            try {
                String selfId = ApplicationHook.getUserId();
                Class<?> clsUserIndependentCache = loader.loadClass("com.alipay.mobile.socialcommonsdk.bizdata.UserIndependentCache");
                Class<?> clsAliAccountDaoOp = loader.loadClass("com.alipay.mobile.socialcommonsdk.bizdata.contact.data.AliAccountDaoOp");
                Object aliAccountDaoOp = XposedHelpers.callStaticMethod(clsUserIndependentCache, "getCacheObj", clsAliAccountDaoOp);
                List<?> allFriends = (List<?>) XposedHelpers.callMethod(aliAccountDaoOp, "getAllFriends", new Object[0]);
                if (!allFriends.isEmpty()) {
                    // 拿到新数据后再清空, 避免查询期间好友表为空导致昵称回退为ID
                    UserIdMap.unload();
                    Class<?> friendClass = allFriends.get(0).getClass();
                    Field userIdField = XposedHelpers.findField(friendClass, "userId");
                    Field accountField = XposedHelpers.findField(friendClass, "account");
                    Field nameField = XposedHelpers.findField(friendClass, "name");
                    Field nickNameField = XposedHelpers.findField(friendClass, "nickName");
                    Field remarkNameField = XposedHelpers.findField(friendClass, "remarkName");
                    Field friendStatusField = XposedHelpers.findField(friendClass, "friendStatus");
                    Field headImageUrlField = XposedHelpers.findField(friendClass, "headImageUrl");
                    Field firstAlphaCharField = XposedHelpers.findField(friendClass, "firstAlphaChar");
                    Field friendTimeField = XposedHelpers.findField(friendClass, "version");
                    Field registerTimeField = XposedHelpers.findField(friendClass, "extVersion");
                    Field isDeleteField = XposedHelpers.findField(friendClass, "isDelete");
                    UserEntity selfEntity = null;
                    for (Object userObject : allFriends) {
                        try {
                            String userId = (String) userIdField.get(userObject);
                            String account = (String) accountField.get(userObject);
                            String name = (String) nameField.get(userObject);
                            String nickName = (String) nickNameField.get(userObject);
                            String remarkName = (String) remarkNameField.get(userObject);
                            Integer friendStatus = (Integer) friendStatusField.get(userObject);
                            String headImageUrl = (String) headImageUrlField.get(userObject);
                            String firstAlphaChar = (String) firstAlphaCharField.get(userObject);
                            Long friendTime = (Long) friendTimeField.get(userObject);
                            Long registerTime = (Long) registerTimeField.get(userObject);
                            Boolean isDelete = (Boolean) isDeleteField.get(userObject);
                            UserEntity userEntity = new UserEntity(userId, account, friendStatus, name, nickName, remarkName,
                                    headImageUrl, firstAlphaChar, friendTime, registerTime, isDelete);
                            if (Objects.equals(selfId, userId)) {
                                selfEntity = userEntity;
                            }
                            UserIdMap.add(userEntity);
                        } catch (Throwable t) {
                            Log.i("addUserObject err:");
                            Log.printStackTrace(t);
                        }
                    }
                    UserIdMap.saveSelf(selfEntity);
                }
                UserIdMap.save(selfId);
                //预下载全部好友头像到磁盘缓存(已缓存的跳过), 打开好友统计时本地秒出图
                try {
                    List<String> avatarUrls = new ArrayList<>();
                    for (UserEntity ue : userMap.values()) {
                        if (ue.getHeadImageUrl() != null && !ue.getHeadImageUrl().isEmpty()) {
                            avatarUrls.add(ue.getHeadImageUrl());
                        }
                    }
                    io.github.lazyimmortal.sesame.ui.ListAdapter.predownloadAvatars(avatarUrls);
                } catch (Throwable ignored) {
                }
            } catch (Throwable t) {
                Log.i("checkUnknownId.run err:");
                Log.printStackTrace(t);
            }
        });
    }
    
    public synchronized static void setCurrentUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            currentUid = null;
            return;
        }
        currentUid = userId;
    }
    
    public static String getCurrentMaskName() {
        return getMaskName(currentUid);
    }
    
    public static String getMaskName(String userId) {
        UserEntity userEntity = userMap.get(userId);
        if (userEntity == null) {
            return null;
        }
        return userEntity.getMaskName();
    }
    public static String getShowName(String userId) {
        if (userId == null || userId.isEmpty()) {
            return "未知用户";
        }
        UserEntity userEntity = userMap.get(userId);
        if (userEntity == null) {
            return userId; // 返回用户ID作为默认值
        }
        return userEntity.getShowName();
    }
    public static String getFullName(String userId) {
        UserEntity userEntity = userMap.get(userId);
        if (userEntity == null) {
            return null;
        }
        return userEntity.getFullName();
    }
    
    public static UserEntity get(String userId) {
        return userMap.get(userId);
    }
    
    public synchronized static void add(UserEntity userEntity) {
        String userId = userEntity.getUserId();
        if (userId == null || userId.isEmpty()) {
            return;
        }
        userMap.put(userId, userEntity);
    }
    
    public synchronized static void remove(String userId) {
        userMap.remove(userId);
    }
    
    public synchronized static void load(String userId) {
        userMap.clear();
        try {
            String body = FileUtil.readFromFile(FileUtil.getFriendIdMapFile(userId));
            if (!body.isEmpty()) {
                Map<String, UserEntity.UserDto> dtoMap = JsonUtil.parseObject(body, new TypeReference<Map<String, UserEntity.UserDto>>() {
                });
                for (UserEntity.UserDto dto : dtoMap.values()) {
                    userMap.put(dto.getUserId(), dto.toEntity());
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }
    
    public synchronized static void unload() {
        userMap.clear();
    }
    
    public synchronized static boolean save(String userId) {
        return FileUtil.write2File(JsonUtil.toJsonString(userMap), FileUtil.getFriendIdMapFile(userId));
    }
    
    public synchronized static void loadSelf(String userId) {
        userMap.clear();
        try {
            String body = FileUtil.readFromFile(FileUtil.getSelfIdFile(userId));
            if (!body.isEmpty()) {
                UserEntity.UserDto dto = JsonUtil.parseObject(body, new TypeReference<UserEntity.UserDto>() {
                });
                userMap.put(dto.getUserId(), dto.toEntity());
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }
    
    public synchronized static boolean saveSelf(UserEntity userEntity) {
        return FileUtil.write2File(JsonUtil.toJsonString(userEntity), FileUtil.getSelfIdFile(userEntity.getUserId()));
    }
    
}
