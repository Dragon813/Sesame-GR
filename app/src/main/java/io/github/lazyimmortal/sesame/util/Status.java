package io.github.lazyimmortal.sesame.util;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.databind.JsonMappingException;

import org.jetbrains.annotations.Contract;

import lombok.Data;

import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.model.task.antFarm.AntFarm;
import io.github.lazyimmortal.sesame.model.task.antForest.AntForestV2;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

import java.io.File;
import java.util.*;

@Data
public class Status {
    
    private static final String TAG = Status.class.getSimpleName();
    
    public static final Status INSTANCE = new Status();
    
    // forest
    private final Map<String, Integer> waterFriendLogList = new HashMap<>();
    private final Map<String, Integer> forestHuntHelpLogList = new HashMap<>();
    private final Map<String, Integer> vitalityExchangeBenefitLogList = new HashMap<>();
    private final Map<Integer, Integer> exchangeReserveLogList = new HashMap<>();
    private final Set<String> ForestHuntHelpList = new HashSet<>();
    private final Set<String> ancientTreeCityCodeList = new HashSet<>();
    
    private int doubleTimes = 0;
    
    // farm
    private final Map<String, Integer> feedFriendLogList = new HashMap<>();
    private final Map<String, Integer> visitFriendLogList = new HashMap<>();
    private int useAccelerateToolCount = 0;
    private int useSpecialFoodCount = 0;
    
    // orchard
    private final Set<String> orchardShareP2PLogList = new HashSet<>();
    
    // stall
    private final Map<String, Integer> stallHelpedCountLogList = new HashMap<>();
    private final Set<String> stallShareP2PLogList = new HashSet<>();
    
    // member
    private final Set<String> memberPointExchangeBenefitLogList = new HashSet<>();
    
    // other
    private final Set<String> flagLogList = new HashSet<>();
    
    // 保存时间
    private Long saveTime = 0L;
    
    /**
     * 绿色经营，收取好友金币已完成用户
     */
    private boolean greenFinancePointFriend = false;
    
    /**
     * 绿色经营，评级领奖已完成用户
     */
    private final Set<Integer> greenFinancePrizesSet = new HashSet<>();
    
    public static Boolean hasFlagToday(String tag) {
        return INSTANCE.flagLogList.contains(tag);
    }
    
    public static void flagToday(String tag) {
        if (!hasFlagToday(tag)) {
            INSTANCE.flagLogList.add(tag);
            save();
        }
    }
    
    //在写入status中时，重要数据提前记录Uid,一定程度上避免因支付宝账号切换导致标记到下一个账号的少数情况
    public static void flagToday(String tag,String taskUid) {
        if (!hasFlagToday(tag)) {
            if(taskUid.equals(UserIdMap.getCurrentUid())){
                INSTANCE.flagLogList.add(tag);
                save();
            }
        }
    }
    
    // 清除单个指定Flag
    public static void clearFlag(String tag) {
        if (INSTANCE.flagLogList.contains(tag)) {
            INSTANCE.flagLogList.remove(tag);
            save(); // 清除后需保存状态，避免下次加载时恢复
        }
    }
    
    //根据助力场景记录助力次数
    public static void forestHuntHelpToday(String taskType, int count,String taskUid) {
        if(taskUid.equals(UserIdMap.getCurrentUid())){
            INSTANCE.forestHuntHelpLogList.put(taskType, count);
            save();
        }
    }
    
    public static Integer getforestHuntHelpToday(String taskType) {
        Integer count = INSTANCE.forestHuntHelpLogList.get(taskType);
        if (count == null) {
            return 0;
        }
        else return count;
    }
    
    public static Boolean canWaterFriendToday(String id, int newCount) {
        Integer count = INSTANCE.waterFriendLogList.get(id);
        if (count == null) {
            return true;
        }
        return count < newCount;
    }
    
    public static void waterFriendToday(String id, int count,String taskUid) {
        if(taskUid.equals(UserIdMap.getCurrentUid())){
            INSTANCE.waterFriendLogList.put(id, count);
            save();
        }
    }
    
    public static int getVitalityExchangeBenefitCountToday(String skuId) {
        Integer exchangedCount = INSTANCE.vitalityExchangeBenefitLogList.get(skuId);
        if (exchangedCount == null) {
            exchangedCount = 0;
        }
        return exchangedCount;
    }
    
    public static Boolean canVitalityExchangeBenefitToday(String skuId, int count) {
        return !hasFlagToday("forest::exchangeLimit::" + skuId)
               && getVitalityExchangeBenefitCountToday(skuId) < count;
    }
    
    public static void vitalityExchangeBenefitToday(String skuId) {
        int count = getVitalityExchangeBenefitCountToday(skuId) + 1;
        INSTANCE.vitalityExchangeBenefitLogList.put(skuId, count);
        save();
    }
    
    public static int getExchangeReserveCountToday(int id) {
        Integer count = INSTANCE.exchangeReserveLogList.get(id);
        return count == null ? 0 : count;
    }
    
    public static Boolean canExchangeReserveToday(int id, int count) {
        return getExchangeReserveCountToday(id) < count;
    }
    
    public static void exchangeReserveToday(int id) {
        int count = getExchangeReserveCountToday(id) + 1;
        INSTANCE.exchangeReserveLogList.put(id, count);
        save();
    }
    
    public static Boolean canMemberPointExchangeBenefitToday(String benefitId) {
        return !INSTANCE.memberPointExchangeBenefitLogList.contains(benefitId);
    }
    
    public static void memberPointExchangeBenefitToday(String benefitId) {
        if (canMemberPointExchangeBenefitToday(benefitId)) {
            INSTANCE.memberPointExchangeBenefitLogList.add(benefitId);
            save();
        }
    }
    
    public static Boolean canForestHuntHelpToday(String shareUserId) {
        //Log.other("canForestHuntHelpToday:" +INSTANCE.toString());
        return !INSTANCE.ForestHuntHelpList.contains(shareUserId);
    }
    
    public static void ForestHuntHelpToday(String shareUserId,String taskUid) {
        Status stat = INSTANCE;
        if (!stat.ForestHuntHelpList.contains(shareUserId)) {
            if(taskUid.equals(UserIdMap.getCurrentUid())){
                stat.ForestHuntHelpList.add(shareUserId);
                //Log.other("ForestHuntHelpList:" +stat.ForestHuntHelpList);
                save();
            }
        }
    }
    
    public static Boolean canAncientTreeToday(String cityCode) {
        return !INSTANCE.ancientTreeCityCodeList.contains(cityCode);
    }
    
    public static void ancientTreeToday(String cityCode) {
        Status stat = INSTANCE;
        if (!stat.ancientTreeCityCodeList.contains(cityCode)) {
            stat.ancientTreeCityCodeList.add(cityCode);
            save();
        }
    }
    
    private static int getFeedFriendCountToday(String id) {
        Integer count = INSTANCE.feedFriendLogList.get(id);
        return count == null ? 0 : count;
    }
    
    public static Boolean canFeedFriendToday(String id, int countLimit) {
        return !hasFlagToday("farm::feedFriendAnimalLimit") && getFeedFriendCountToday(id) < countLimit;
    }
    
    public static void feedFriendToday(String id) {
        int count = getFeedFriendCountToday(id) + 1;
        INSTANCE.feedFriendLogList.put(id, count);
        save();
    }
    
    private static int getVisitFriendCountToday(String id) {
        Integer count = INSTANCE.visitFriendLogList.get(id);
        return count == null ? 0 : count;
    }
    
    public static Boolean canVisitFriendToday(String id, int countLimit) {
        countLimit = Math.max(countLimit, 0);
        countLimit = Math.min(countLimit, 3);
        return !hasFlagToday("farm::visitFriendLimit::" + id)
               && getVisitFriendCountToday(id) < countLimit;
    }
    
    public static void visitFriendToday(String id) {
        int count = getVisitFriendCountToday(id) + 1;
        INSTANCE.visitFriendLogList.put(id, count);
        save();
    }
    
    public static void visitFriendToday(String id, int count) {
        INSTANCE.visitFriendLogList.put(id, count);
        save();
    }
    
    public static boolean canStallHelpToday(String id) {
        Integer count = INSTANCE.stallHelpedCountLogList.get(id);
        if (count == null) {
            return true;
        }
        return count < 3;
    }
    
    public static void stallHelpToday(String id, boolean limited) {
        Integer count = INSTANCE.stallHelpedCountLogList.get(id);
        if (count == null) {
            count = 0;
        }
        if (limited) {
            count = 3;
        } else {
            count += 1;
        }
        INSTANCE.stallHelpedCountLogList.put(id, count);
        save();
    }
    
    public static Boolean canUseAccelerateToolToday() {
        return !hasFlagToday("farm::useFarmToolLimit::" + "ACCELERATE" + "TOOL")
               && INSTANCE.useAccelerateToolCount < 8;
    }
    
    public static void useAccelerateToolToday() {
        INSTANCE.useAccelerateToolCount += 1;
        save();
    }
    
    public static Boolean canUseSpecialFoodToday() {
        AntFarm task = ModelTask.getModel(AntFarm.class);
        if (task == null) {
            return false;
        }
        int countLimit = task.getUseSpecialFoodCountLimit().getValue();
        if (countLimit == 0) {
            return true;
        }
        return INSTANCE.useSpecialFoodCount < countLimit;
    }
    
    public static void useSpecialFoodToday() {
        INSTANCE.useSpecialFoodCount += 1;
        save();
    }
    
    public static Boolean canOrchardShareP2PToday(String friendUserId) {
        return !hasFlagToday("orchard::shareP2PLimit")
               && !hasFlagToday("orchard::shareP2PLimit::" + friendUserId)
               && !INSTANCE.orchardShareP2PLogList.contains(friendUserId);
    }
    
    public static void orchardShareP2PToday(String friendUserId) {
        if (canOrchardShareP2PToday(friendUserId)) {
            INSTANCE.orchardShareP2PLogList.add(friendUserId);
            save();
        }
    }
    
    public static Boolean canStallShareP2PToday(String friendUserId) {
        return !hasFlagToday("stall::shareP2PLimit")
               && !hasFlagToday("stall::shareP2PLimit::" + friendUserId)
               && !INSTANCE.stallShareP2PLogList.contains(friendUserId);
    }
    
    public static void stallShareP2PToday(String friendUserId) {
        if (canStallShareP2PToday(friendUserId)) {
            INSTANCE.stallShareP2PLogList.add(friendUserId);
            save();
        }
    }
    
    public static boolean canDoubleToday() {
        AntForestV2 task = ModelTask.getModel(AntForestV2.class);
        if (task == null) {
            return false;
        }
        return INSTANCE.doubleTimes < task.getDoubleCountLimit().getValue();
    }
    
    public static void DoubleToday() {
        INSTANCE.doubleTimes += 1;
        save();
    }
    
    /**
     * 绿色经营-是否可以收好友金币
     *
     * @return true是，false否
     */
    public static boolean canGreenFinancePointFriend() {
        return !INSTANCE.greenFinancePointFriend;
    }
    
    /**
     * 绿色经营-收好友金币完了
     */
    public static void greenFinancePointFriend() {
        Status stat = INSTANCE;
        if (!stat.greenFinancePointFriend) {
            stat.greenFinancePointFriend = true;
            save();
        }
    }
    
    /**
     * 绿色经营-是否可以做评级任务
     *
     * @return true是，false否
     */
    public static boolean canGreenFinancePrizesMap() {
        int week = TimeUtil.getWeekNumber(new Date());
        return !INSTANCE.greenFinancePrizesSet.contains(week);
    }
    
    /**
     * 绿色经营-评级任务完了
     */
    public static void greenFinancePrizesMap() {
        int week = TimeUtil.getWeekNumber(new Date());
        Status stat = INSTANCE;
        if (!stat.greenFinancePrizesSet.contains(week)) {
            stat.greenFinancePrizesSet.add(week);
            save();
        }
    }
    
    public static synchronized Status load() {
        String currentUid = UserIdMap.getCurrentUid();
        try {
            if (StringUtil.isEmpty(currentUid)) {
                Log.i(TAG, "用户为空，状态加载失败");
                throw new RuntimeException("用户为空，状态加载失败");
            }
            File statusFile = FileUtil.getStatusFile(currentUid);
            if (statusFile.exists()) {
                String json = FileUtil.readFromFile(statusFile);
                JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                String formatted = JsonUtil.toFormatJsonString(INSTANCE);
                if (formatted != null && !formatted.equals(json)) {
                    Log.i(TAG, "重新格式化 status.json");
                    Log.system(TAG, "重新格式化 status.json");
                    FileUtil.write2File(formatted, FileUtil.getStatusFile(currentUid));
                }
            } else {
                JsonUtil.copyMapper().updateValue(INSTANCE, new Status());
                Log.i(TAG, "初始化 status.json");
                Log.system(TAG, "初始化 status.json");
                FileUtil.write2File(JsonUtil.toFormatJsonString(INSTANCE), FileUtil.getStatusFile(currentUid));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            Log.i(TAG, "状态文件格式有误，已重置");
            Log.system(TAG, "状态文件格式有误，已重置");
            try {
                JsonUtil.copyMapper().updateValue(INSTANCE, new Status());
                FileUtil.write2File(JsonUtil.toFormatJsonString(INSTANCE), FileUtil.getStatusFile(currentUid));
            } catch (JsonMappingException e) {
                Log.printStackTrace(TAG, e);
            }
        }
        if (INSTANCE.saveTime == 0) {
            INSTANCE.saveTime = System.currentTimeMillis();
        }
        return INSTANCE;
    }
    
    public static synchronized void unload() {
        try {
            JsonUtil.copyMapper().updateValue(INSTANCE, new Status());
        } catch (JsonMappingException e) {
            Log.printStackTrace(TAG, e);
        }
    }
    
    public static synchronized void save() {
        save(Calendar.getInstance());
    }
    
    public static synchronized void save(Calendar nowCalendar) {
        String currentUid = UserIdMap.getCurrentUid();
        if (StringUtil.isEmpty(currentUid)) {
            Log.record("用户为空，状态保存失败");
            throw new RuntimeException("用户为空，状态保存失败");
        }
        if (updateDay(nowCalendar)) {
            Log.system(TAG, "重置 status.json");
        } else {
            Log.system(TAG, "保存 status.json");
        }
        long lastSaveTime = INSTANCE.saveTime;
        try {
            INSTANCE.saveTime = System.currentTimeMillis();
            FileUtil.write2File(JsonUtil.toFormatJsonString(INSTANCE), FileUtil.getStatusFile(currentUid));
        } catch (Exception e) {
            INSTANCE.saveTime = lastSaveTime;
            throw e;
        }
    }
    
    public static Boolean updateDay(Calendar nowCalendar) {
        if (TimeUtil.isLessThanSecondOfDays(INSTANCE.saveTime, nowCalendar.getTimeInMillis())) {
            Status.unload();
            return true;
        } else {
            return false;
        }
    }
    
    
    
    @Data
    private static class WaterFriendLog {
        String userId;
        int waterCount = 0;
        
        public WaterFriendLog() {
        }
        
        public WaterFriendLog(String id) {
            userId = id;
        }
    }
    
    @Data
    private static class ReserveLog {
        String projectId;
        int applyCount = 0;
        
        public ReserveLog() {
        }
        
        public ReserveLog(String id) {
            projectId = id;
        }
    }
    
    @Data
    private static class BeachLog {
        String cultivationCode;
        int applyCount = 0;
        
        public BeachLog() {
        }
        
        public BeachLog(String id) {
            cultivationCode = id;
        }
    }
    
    @Data
    private static class FeedFriendLog {
        String userId;
        int feedCount = 0;
        
        public FeedFriendLog() {
        }
        
        public FeedFriendLog(String id) {
            userId = id;
        }
    }
    
    @Data
    private static class VisitFriendLog {
        String userId;
        int visitCount = 0;
        
        public VisitFriendLog() {
        }
        
        public VisitFriendLog(String id) {
            userId = id;
        }
    }
    
    @Data
    private static class StallShareIdLog {
        String userId;
        String shareId;
        
        public StallShareIdLog() {
        }
        
        public StallShareIdLog(String uid, String sid) {
            userId = uid;
            shareId = sid;
        }
    }
    
    @Data
    private static class StallHelpedCountLog {
        String userId;
        int helpedCount = 0;
        int beHelpedCount = 0;
        
        public StallHelpedCountLog() {
        }
        
        public StallHelpedCountLog(String id) {
            userId = id;
        }
    }
    
}