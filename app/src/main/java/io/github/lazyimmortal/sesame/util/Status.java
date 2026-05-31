package io.github.lazyimmortal.sesame.util;

import com.fasterxml.jackson.databind.JsonMappingException;

import lombok.Data;

import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.model.task.antFarm.AntFarm;
import io.github.lazyimmortal.sesame.model.task.antForest.AntForestV2;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;
import io.github.lazyimmortal.sesame.data.ConfigV2;
import io.github.lazyimmortal.sesame.data.ModelFields;
import io.github.lazyimmortal.sesame.data.ModelField;

import java.io.File;
import java.util.*;

@Data
public class Status {
    
    private static final String TAG = Status.class.getSimpleName();
    
    public static final Status INSTANCE = new Status();
    
    // forest
    private final Map<String, Integer> waterFriendLogList = new HashMap<>();
    private final Map<String, Integer> wateredFriendLogList = new HashMap<>();
    private final Map<String, Integer> wateringFriendLogList = new HashMap<>();
    private final Map<String, Integer> forestHuntHelpLogList = new HashMap<>();
    private final Map<String, Integer> vitality_ExchangeBenefitLogList = new HashMap<>();
    private final Map<Integer, Integer> exchangeReserveLogList = new HashMap<>();
    private final Set<String> ancientTreeCityCodeList = new HashSet<>();
    
    private int doubleTimes = 0;
    
    // farm
    private final Map<String, Integer> feedFriendLogList = new HashMap<>();
    private final Map<String, Integer> visitFriendLogList = new HashMap<>();
    private final Map<String, Integer> gameCenterBuyMallItemList = new HashMap<>();
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
    public static void flagToday(String tag, String taskUid) {
        if (!hasFlagToday(tag)) {
            if (taskUid.equals(UserIdMap.getCurrentUid())) {
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
    public static void forestHuntHelpToday(String taskType, int count, String taskUid) {
        if (taskUid.equals(UserIdMap.getCurrentUid())) {
            INSTANCE.forestHuntHelpLogList.put(taskType, count);
            save();
        }
    }
    
    public static Integer getforestHuntHelpToday(String taskType) {
        Integer count = INSTANCE.forestHuntHelpLogList.get(taskType);
        if (count == null) {
            return 0;
        }
        else {
            return count;
        }
    }
    
    //记录完成任务次数
    public static void rpcRequestListToday(String taskName, int count) {
        INSTANCE.forestHuntHelpLogList.put(taskName, count);
        save();
    }
    
    public static Integer getrpcRequestListToday(String taskName) {
        Integer count = INSTANCE.forestHuntHelpLogList.get(taskName);
        if (count == null) {
            return 0;
        }
        else {
            return count;
        }
    }
    
    public static void wateredFriendToday(String id) {
        Integer count = INSTANCE.wateredFriendLogList.get(id);
        if (count == null) {
            count = 0; // 首次被浇水，次数初始化为0
        }
        INSTANCE.wateredFriendLogList.put(id, count + 1);
        save();
    }
    //Log.forest("统计被水🍯
    //Log.forest("统计浇水🚿
    public static void getWateredFriendToday() {
        // 1. 基础统计：浇水好友数量（Map的key数量）
        int friendCount = INSTANCE.wateredFriendLogList.size();
        // 2. 统计总浇水量（遍历Map累加所有value）
        int totalWaterAmount = 0;
        
        // 3. 遍历Map的键值对并输出详细信息
        for (Map.Entry<String, Integer> entry : INSTANCE.wateredFriendLogList.entrySet()) {
            String friendId = entry.getKey();       // 好友ID
            Integer waterAmount = entry.getValue(); // 给该好友的浇水量（避免空指针）
            if (waterAmount == null) {
                waterAmount = 0;
            }
            
            // 可选：通过UserIdMap获取好友昵称（如果需要显示名称而非ID）
            String friendName = UserIdMap.getShowName(friendId);
            
            // 输出单条明细（日志/控制台）
            Log.forest("统计被水🍯被["+friendName+"]浇水"+waterAmount+"次");
            
            // 累加总浇水量
            totalWaterAmount += waterAmount;
        }
        
        // 4. 输出汇总统计信息
        Log.forest("统计被水🍯共计被"+friendCount+"个好友浇水"+ totalWaterAmount+"次#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
    
        // 5. 从ConfigV2读取wateredFriendList配置值
        Map<String, Integer> configWateredFriendList = null;
        try {
            ModelFields antForestFields = ConfigV2.INSTANCE.getModelFieldsMap().get("AntForestV2");
            if (antForestFields != null) {
                ModelField<?> wateredFriendField = antForestFields.get("wateredFriendList");
                if (wateredFriendField != null && wateredFriendField.getValue() instanceof Map) {
                    configWateredFriendList = (Map<String, Integer>) wateredFriendField.getValue();
                }
            }
        } catch (Exception e) {
            Log.i(TAG, "获取wateredFriendList配置失败:");
            Log.printStackTrace(TAG, e);
        }
        
        // 6. 如果配置存在且不为空，输出预计统计和差别
        if (configWateredFriendList != null && !configWateredFriendList.isEmpty()) {
            // 6.1 输出预计统计信息
            int configFriendCount = 0;
            int configTotalWaterAmount = 0;
            for (Map.Entry<String, Integer> entry : configWateredFriendList.entrySet()) {
                Integer configWaterAmount = entry.getValue();
                if (configWaterAmount != null && configWaterAmount > 0) {
                    configFriendCount++;
                    configTotalWaterAmount += configWaterAmount;
                }
            }
            Log.forest("统计被水🍯预计被"+configFriendCount+"个好友浇水"+ configTotalWaterAmount+"次#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");

            // 6.2 对比配置和实际浇水情况，输出差别
            for (Map.Entry<String, Integer> entry : configWateredFriendList.entrySet()) {
                String friendId = entry.getKey();
                Integer configWaterAmount = entry.getValue();
                if (configWaterAmount != null && configWaterAmount > 0) {
                    Integer actualWaterAmount = INSTANCE.wateredFriendLogList.get(friendId);
                    String friendName = UserIdMap.getShowName(friendId);
                    int actual = actualWaterAmount == null ? 0 : actualWaterAmount;
                    int diff = configWaterAmount - actual;
                    if (diff > 0) {
                        Log.forest("统计被水🍯被[" + friendName + "]少浇" + diff + "次");
                    } else if (diff < 0) {
                        Log.forest("统计被水🍯被[" + friendName + "]多浇" + (-diff) + "次");
                    }
                }
            }
            // 6.3 检查不在预计列表中但实际被浇了水的好友
            for (Map.Entry<String, Integer> entry : INSTANCE.wateredFriendLogList.entrySet()) {
                String friendId = entry.getKey();
                if (!configWateredFriendList.containsKey(friendId)) {
                    Integer actualWaterAmount = entry.getValue();
                    if (actualWaterAmount != null && actualWaterAmount > 0) {
                        String friendName = UserIdMap.getShowName(friendId);
                        Log.forest("统计被水🍯被[" + friendName + "]多浇" + actualWaterAmount + "次");
                    }
                }
            }
        }
    }

    public static void fillWateredFriendList() {
        // 1. 获取当前被浇水的好友数据
        Map<String, Integer> currentData = INSTANCE.wateredFriendLogList;
        if (currentData == null || currentData.isEmpty()) {
            Log.forest("填入被水🍯[当前没有被浇水数据]");
            return;
        }

        // 2. 从ConfigV2读取wateredFriendList配置值
        Map<String, Integer> configWateredFriendList = null;
        try {
            ModelFields antForestFields = ConfigV2.INSTANCE.getModelFieldsMap().get("AntForestV2");
            if (antForestFields != null) {
                ModelField<?> wateredFriendField = antForestFields.get("wateredFriendList");
                if (wateredFriendField != null && wateredFriendField.getValue() instanceof Map) {
                    configWateredFriendList = (Map<String, Integer>) wateredFriendField.getValue();
                }
            }
        } catch (Exception e) {
            Log.i(TAG, "获取wateredFriendList配置失败:");
            Log.printStackTrace(TAG, e);
        }

        // 3. 如果配置为空，创建一个新的LinkedHashMap
        if (configWateredFriendList == null) {
            configWateredFriendList = new LinkedHashMap<>();
        }

        // 4. 将当前被浇水数据填入配置
        int addedCount = 0;
        int updatedCount = 0;
        for (Map.Entry<String, Integer> entry : currentData.entrySet()) {
            String friendId = entry.getKey();
            Integer waterAmount = entry.getValue();
            if (waterAmount != null && waterAmount > 0) {
                if (configWateredFriendList.containsKey(friendId)) {
                    // 更新已有记录
                    configWateredFriendList.put(friendId, waterAmount);
                    updatedCount++;
                    String friendName = UserIdMap.getShowName(friendId);
                    Log.forest("填入被水🍯更新[" + friendName + "]为" + waterAmount + "次");
                } else {
                    // 新增记录
                    configWateredFriendList.put(friendId, waterAmount);
                    addedCount++;
                    String friendName = UserIdMap.getShowName(friendId);
                    Log.forest("填入被水🍯新增[" + friendName + "]" + waterAmount + "次");
                }
            }
        }

        // 5. 清除配置中不在实际被浇水列表里的项
        int removedCount = 0;
        Iterator<Map.Entry<String, Integer>> iterator = configWateredFriendList.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            String friendId = entry.getKey();
            if (!currentData.containsKey(friendId)) {
                    Integer waterAmount = entry.getValue();
                    iterator.remove();
                    removedCount++;
                    String friendName = UserIdMap.getShowName(friendId);
                    Log.forest("填入被水🍯清除[" + friendName + "]" + (waterAmount != null ? waterAmount : 0) + "次");
                }
        }

        // 6. 保存配置到ConfigV2
        try {
            ModelFields antForestFields = ConfigV2.INSTANCE.getModelFieldsMap().get("AntForestV2");
            if (antForestFields != null) {
                ModelField<?> wateredFriendField = antForestFields.get("wateredFriendList");
                if (wateredFriendField != null) {
                    wateredFriendField.setObjectValue(configWateredFriendList);
                    String currentUid = UserIdMap.getCurrentUid();
                    ConfigV2.save(currentUid, true);
                    Log.forest("填入被水🍯完成新增" + addedCount + "个更新" + updatedCount + "个清除" + removedCount + "个#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                }
            }
        } catch (Exception e) {
            Log.i(TAG, "保存wateredFriendList配置失败:");
            Log.printStackTrace(TAG, e);
        }
    }
    
    public static void wateringFriendToday(String id) {
        Integer count = INSTANCE.wateringFriendLogList.get(id);
        if (count == null) {
            count = 0; // 首次被浇水，次数初始化为0
        }
        INSTANCE.wateringFriendLogList.put(id, count + 1);
    }
    
    public static void getWateringFriendToday() {
        // 1. 基础统计：浇水好友数量（Map的key数量）
        int friendCount = INSTANCE.wateringFriendLogList.size();
        // 2. 统计总浇水量（遍历Map累加所有value）
        int totalWaterAmount = 0;
        
        // 3. 遍历Map的键值对并输出详细信息
        for (Map.Entry<String, Integer> entry : INSTANCE.wateringFriendLogList.entrySet()) {
            String friendId = entry.getKey();       // 好友ID
            Integer waterAmount = entry.getValue(); // 给该好友的浇水量（避免空指针）
            if (waterAmount == null) {
                waterAmount = 0;
            }
            
            // 可选：通过UserIdMap获取好友昵称（如果需要显示名称而非ID）
            String friendName = UserIdMap.getShowName(friendId);
            
            // 输出单条明细（日志/控制台）
            Log.forest("统计浇水🚿给["+friendName+"]浇水"+waterAmount+"次");
            
            // 累加总浇水量
            totalWaterAmount += waterAmount;
        }
        
        // 4. 输出汇总统计信息
        Log.forest("统计浇水🚿共计给"+friendCount+"个好友浇水"+ totalWaterAmount+"次#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
        
        // 5. 从ConfigV2读取waterFriendList配置值
        Map<String, Integer> configWaterFriendList = null;
        try {
            ModelFields antForestFields = ConfigV2.INSTANCE.getModelFieldsMap().get("AntForestV2");
            if (antForestFields != null) {
                ModelField<?> waterFriendField = antForestFields.get("waterFriendList");
                if (waterFriendField != null && waterFriendField.getValue() instanceof Map) {
                    configWaterFriendList = (Map<String, Integer>) waterFriendField.getValue();
                }
            }
        } catch (Exception e) {
            Log.i(TAG, "获取waterFriendList配置失败:");
            Log.printStackTrace(TAG, e);
        }
        
        // 6. 如果配置存在且不为空，输出预计统计和差别
        if (configWaterFriendList != null && !configWaterFriendList.isEmpty()) {
            // 6.1 输出预计统计信息
            int configFriendCount = 0;
            int configTotalWaterAmount = 0;
            for (Map.Entry<String, Integer> entry : configWaterFriendList.entrySet()) {
                Integer configWaterAmount = entry.getValue();
                if (configWaterAmount != null && configWaterAmount > 0) {
                    configFriendCount++;
                    configTotalWaterAmount += configWaterAmount;
                }
            }
            Log.forest("统计浇水🚿预计给"+configFriendCount+"个好友浇水"+ configTotalWaterAmount+"次#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");

            // 6.2 对比配置和实际浇水情况，输出差别
            for (Map.Entry<String, Integer> entry : configWaterFriendList.entrySet()) {
                String friendId = entry.getKey();
                Integer configWaterAmount = entry.getValue();
                if (configWaterAmount != null && configWaterAmount > 0) {
                    Integer actualWaterAmount = INSTANCE.wateringFriendLogList.get(friendId);
                    String friendName = UserIdMap.getShowName(friendId);
                    int actual = actualWaterAmount == null ? 0 : actualWaterAmount;
                    int diff = configWaterAmount - actual;
                    if (diff != 0) {
                        Log.forest("统计浇水🚿给[" + friendName + "]缺少" + diff + "次");       
                    }
                }
            }
        }
    }
    
    public static Boolean canWaterFriendToday(String id, int newCount) {
        Integer count = INSTANCE.waterFriendLogList.get(id);
        if (count == null) {
            return true;
        }
        return count < newCount;
    }
    
    public static void waterFriendToday(String id, int count, String taskUid) {
        if (taskUid.equals(UserIdMap.getCurrentUid())) {
            INSTANCE.waterFriendLogList.put(id, count);
            save();
        }
    }
    
    public static int getVitalityExchangeBenefitCountToday(String skuId) {
        Integer exchangedCount = INSTANCE.vitality_ExchangeBenefitLogList.get(skuId);
        if (exchangedCount == null) {
            exchangedCount = 0;
        }
        return exchangedCount;
    }
    
    public static Boolean canVitalityExchangeBenefitToday(String skuId, int count) {
        return !hasFlagToday("forest::exchangeLimit::" + skuId) && getVitalityExchangeBenefitCountToday(skuId) < count;
    }
    
    public static void vitalityExchangeBenefitToday(String skuId) {
        int count = getVitalityExchangeBenefitCountToday(skuId) + 1;
        INSTANCE.vitality_ExchangeBenefitLogList.put(skuId, count);
        save();
    }
    
    public static int getGameCenterBuyMallItemCountToday(String skuId) {
        Integer buyedCount = INSTANCE.gameCenterBuyMallItemList.get(skuId);
        if (buyedCount == null) {
            buyedCount = 0;
        }
        return buyedCount;
    }
    
    public static Boolean canGameCenterBuyMallItemToday(String skuId, int count) {
        return !hasFlagToday("farm::buyLimit::" + skuId) && getGameCenterBuyMallItemCountToday(skuId) < count;
    }
    
    public static void gameCenterBuyMallItemToday(String skuId) {
        int count = getGameCenterBuyMallItemCountToday(skuId) + 1;
        INSTANCE.gameCenterBuyMallItemList.put(skuId, count);
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
        return !hasFlagToday("farm::visitFriendLimit::" + id) && getVisitFriendCountToday(id) < countLimit;
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
        }
        else {
            count += 1;
        }
        INSTANCE.stallHelpedCountLogList.put(id, count);
        save();
    }
    
    public static Boolean canUseAccelerateToolToday() {
        return !hasFlagToday("farm::useFarmToolLimit::" + "ACCELERATE" + "TOOL") && INSTANCE.useAccelerateToolCount < 8;
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
        return !hasFlagToday("orchard::shareP2PLimit") && !hasFlagToday("orchard::shareP2PLimit::" + friendUserId) && !INSTANCE.orchardShareP2PLogList.contains(friendUserId);
    }
    
    public static void orchardShareP2PToday(String friendUserId) {
        if (canOrchardShareP2PToday(friendUserId)) {
            INSTANCE.orchardShareP2PLogList.add(friendUserId);
            save();
        }
    }
    
    public static Boolean canStallShareP2PToday(String friendUserId) {
        return !hasFlagToday("stall::shareP2PLimit") && !hasFlagToday("stall::shareP2PLimit::" + friendUserId) && !INSTANCE.stallShareP2PLogList.contains(friendUserId);
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
            }
            else {
                JsonUtil.copyMapper().updateValue(INSTANCE, new Status());
                Log.i(TAG, "初始化 status.json");
                Log.system(TAG, "初始化 status.json");
                FileUtil.write2File(JsonUtil.toFormatJsonString(INSTANCE), FileUtil.getStatusFile(currentUid));
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            Log.i(TAG, "状态文件格式有误，已重置");
            Log.system(TAG, "状态文件格式有误，已重置");
            try {
                JsonUtil.copyMapper().updateValue(INSTANCE, new Status());
                FileUtil.write2File(JsonUtil.toFormatJsonString(INSTANCE), FileUtil.getStatusFile(currentUid));
            }
            catch (JsonMappingException e) {
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
        }
        catch (JsonMappingException e) {
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
        }
        else {
            Log.system(TAG, "保存 status.json");
        }
        long lastSaveTime = INSTANCE.saveTime;
        try {
            INSTANCE.saveTime = System.currentTimeMillis();
            FileUtil.write2File(JsonUtil.toFormatJsonString(INSTANCE), FileUtil.getStatusFile(currentUid));
        }
        catch (Exception e) {
            INSTANCE.saveTime = lastSaveTime;
            throw e;
        }
    }
    
    public static Boolean updateDay(Calendar nowCalendar) {
        if (TimeUtil.isLessThanSecondOfDays(INSTANCE.saveTime, nowCalendar.getTimeInMillis())) {
            Status.unload();
            return true;
        }
        else {
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