package io.github.lazyimmortal.sesame.model.task.antOrchard;

import io.github.lazyimmortal.sesame.entity.AlipayPlantScene;
import io.github.lazyimmortal.sesame.entity.AlipayUser;
import io.github.lazyimmortal.sesame.data.ModelFields;
import io.github.lazyimmortal.sesame.data.ModelGroup;
import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.hook.Toast;
import io.github.lazyimmortal.sesame.model.base.TaskCommon;
import io.github.lazyimmortal.sesame.data.modelFieldExt.BooleanModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.IntegerModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectAndCountModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectModelField;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.Status;
import io.github.lazyimmortal.sesame.util.TimeUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import io.github.lazyimmortal.sesame.util.*;
import io.github.lazyimmortal.sesame.util.idMap.BeachIdMap;
import io.github.lazyimmortal.sesame.util.idMap.PlantSceneIdMap;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

import java.util.*;

public class AntOrchard extends ModelTask {
    private static final String TAG = "AntOrchard";
    private static final String NAME = "农场";
    private static final ModelGroup GROUP = ModelGroup.ORCHARD;
    private String[] wuaList;
    
    // 模型字段定义
    private IntegerModelField executeInterval;
    private BooleanModelField orchardListTask;
    private BooleanModelField orchardSpreadManure;
    private BooleanModelField useBatchSpread;
    private SelectAndCountModelField orchardSpreadManureSceneList;
    private ChoiceModelField driveAnimalType;
    private SelectModelField driveAnimalList;
    private BooleanModelField batchHireAnimal;
    private SelectModelField doNotHireList;
    private SelectModelField doNotWeedingList;
    private BooleanModelField assistFriend;
    private SelectModelField assistFriendList;
    private static int fertilizerProgress = 0;
    private static final ArrayList<String> enableSceneList = new ArrayList<>();
    
    static {
    
    }
    
    @Override
    public String getName() {
        return NAME;
    }
    
    @Override
    public ModelGroup getGroup() {
        return GROUP;
    }
    
    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(executeInterval = new IntegerModelField("executeInterval", "执行间隔(毫秒)", 500, 500, null));
        modelFields.addField(orchardListTask = new BooleanModelField("orchardListTask", "农场任务", false));
        modelFields.addField(orchardSpreadManure = new BooleanModelField("orchardSpreadManure", "农场施肥 | 开启", false));
        modelFields.addField(useBatchSpread = new BooleanModelField("useBatchSpread", "一键施肥5次", false));
        modelFields.addField(orchardSpreadManureSceneList = new SelectAndCountModelField("orchardSpreadManureSceneList", "农场施肥 | 场景列表", new LinkedHashMap<>(), AlipayPlantScene::getList, "请填写每日施肥次数"));
        //modelFields.addField(driveAnimalType = new ChoiceModelField("driveAnimalType", "驱赶小鸡 | 动作", DriveAnimalType.NONE, DriveAnimalType.nickNames));
        //modelFields.addField(driveAnimalList = new SelectModelField("driveAnimalList", "驱赶小鸡 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        //modelFields.addField(batchHireAnimal = new BooleanModelField("batchHireAnimal", "捉鸡除草 | 开启", false));
        //modelFields.addField(doNotHireList = new SelectModelField("doNotHireList", "捉鸡除草 | 不捉鸡列表", new LinkedHashSet<>(), AlipayUser::getList));
        //modelFields.addField(doNotWeedingList = new SelectModelField("doNotWeedingList", "捉鸡除草 | 不除草列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(assistFriend = new BooleanModelField("assistFriend", "分享助力 | 开启", false));
        modelFields.addField(assistFriendList = new SelectModelField("assistFriendList", "分享助力 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        return modelFields;
    }
    
    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.farm("任务暂停⏸️芭芭农场:当前为只收能量时间");
            return false;
        }
        return true;
    }
    
    @Override
    public void run() {
        try {
            super.startTask();
            if (!checkOrchardOpen()) {
                return;
            }
            
            // 额外信息获取（每日肥料包）
            extraInfoGet();
            
            // 执行农场任务
            if (orchardListTask.getValue()) {
                orchardListTask();
            }
            
            // 执行施肥逻辑
            if (orchardSpreadManure.getValue()) {
                orchardSpreadManure();
            }
            
            // 好友助力
            if (assistFriend.getValue()) {
                orchardAssistFriend();
            }
            
        }
        
        catch (Throwable t) {
            Log.i(TAG, "start.run err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 检查农场是否已开启
     */
    private boolean checkOrchardOpen() {
        try {
            JSONObject jo = new JSONObject(AntOrchardRpcCall.orchardIndex());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            
            if (!jo.optBoolean("userOpenOrchard")) {
                getEnableField().setValue(false);
                Log.record("请先开启芭芭农场！");
                return false;
            }
            
            // 处理七日礼包
            if (jo.has("lotteryPlusInfo")) {
                drawLotteryPlus(jo.getJSONObject("lotteryPlusInfo"));
            }
            
            //获取场景列表
            initPlantScene(jo);
            
            // 处理可用场景列表
            handleEnableScenes(jo);
            
            // 处理淘宝数据（果树状态）
            handleTaobaoData(jo.getString("taobaoData"));
            
            return true;
        }
        catch (Throwable t) {
            Log.i(TAG, "orchardIndex err:");
            Log.printStackTrace(TAG, t);
            return false;
        }
    }
    
    /**
     * 处理可用场景列表
     */
    
    public static void initPlantScene(JSONObject jo) {
        try {
            JSONArray sceneArray = jo.getJSONArray("enableSwitchSceneList");
            if (sceneArray == null) {
                return;
            }
            PlantSceneIdMap.load();
            for (int i = 0; i < sceneArray.length(); i++) {
                String scene = sceneArray.getString(i);
                PlantSceneIdMap.add(scene, scene);
            }
            PlantSceneIdMap.save();
        }
        catch (Throwable t) {
            Log.i(TAG, "initPlantScene err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void handleEnableScenes(JSONObject jo) {
        try {
            
            JSONArray sceneArray = jo.getJSONArray("enableSwitchSceneList");
            enableSceneList.clear();
            for (int i = 0; i < sceneArray.length(); i++) {
                String scene = sceneArray.getString(i);
                enableSceneList.add(scene);
                
                // 主场景处理
                if ("main".equals(scene)) {
                    if (jo.getString("currentPlantScene").equals(scene) || switchPlantScene(PlantScene.main)) {
                        //querySubplotsActivity("WISH");
                        //querySubplotsActivity("CAMP_TAKEOVER");
                    }
                }
                
                // 余额宝场景处理
                if ("yeb".equals(scene)) {
                    JSONObject yebInfo = jo.getJSONObject("yebSceneActivityInfo");
                    if ("NOT_PLANTED".equals(yebInfo.getString("yebSceneStatus"))) {
                        enableSceneList.remove(scene);
                    }
                    else if (yebInfo.optBoolean("revenueNotReceived")) {
                        queryYebRevenueDetail();
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "handleEnableScenes err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 处理淘宝数据（果树生长状态）
     */
    private void handleTaobaoData(String taobaoData) {
        try {
            JSONObject jo = new JSONObject(taobaoData);
            JSONObject plantInfo = jo.getJSONObject("gameInfo").getJSONObject("plantInfo");
            JSONObject seedStage = plantInfo.getJSONObject("seedStage");
            
            // 检查是否可兑换
            if (plantInfo.getBoolean("canExchange")) {
                Log.farm("农场果树似乎可以兑换了！");
                Toast.show("芭芭农场果树似乎可以兑换了！");
            }
            // 更新施肥进度
            fertilizerProgress = seedStage.getInt("totalValue");
        }
        catch (Throwable t) {
            Log.i(TAG, "handleTaoBaoData err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 农场施肥逻辑
     */
    private void orchardSpreadManure() {
        try {
            while (true) {
                boolean hasSpread = false;
                // 遍历可用场景进行施肥
                for (PlantScene scene : PlantScene.getEntries()) {
                    if (enableSceneList.contains(scene.name()) && orchardSpreadManureSceneList.contains(scene.name())) {
                        // 切换场景
                        if (!switchPlantScene(scene)) {
                            continue;
                        }
                        // 检查是否可施肥
                        if (!canSpreadManure(scene)) {
                            continue;
                        }
                        // 执行施肥
                        if (doSpreadManure(scene)) {
                            hasSpread = true;
                            break;
                        }
                    }
                }
                
                // 查询施肥活动奖励
                querySpreadManureActivity();
                
                // 等待间隔时间
                int interval = executeInterval.getValue() != null ? executeInterval.getValue() : 500;
                TimeUtil.sleep(interval);
                
                if (!hasSpread) {
                    break;
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "orchardSpreadManure err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 执行施肥操作
     */
    private boolean doSpreadManure(PlantScene scene) {
        try {
            String sceneName = scene.name();
            String result = AntOrchardRpcCall.orchardSpreadManure(useBatchSpread.getValue(),getWua());
            JSONObject jo = new JSONObject(result);
            
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            
            JSONObject taobaoData = new JSONObject(jo.getString("taobaoData"));
            int cost = taobaoData.getInt("currentCost");
            Log.farm("芭芭农场🌳" + scene.nickname() + "施肥#消耗[" + cost + "g肥料]");
            
            // 检查施肥进度
            if (taobaoData.has("currentStage")) {
                JSONObject stage = taobaoData.getJSONObject("currentStage");
                int newProgress = stage.optInt("totalValue", fertilizerProgress);
                if (newProgress - fertilizerProgress <= 1) {
                    Log.record("施肥只加0.01%进度今日停止施肥！");
                    Status.flagToday("spreadManureLimit:" + sceneName);
                }
                fertilizerProgress = newProgress;
            }
            
            return true;
        }
        catch (Throwable t) {
            Log.i(TAG, "doSpreadManure err:");
            Log.printStackTrace(TAG, t);
            return false;
        }
    }
    
    private String getWua() {
        if (wuaList == null) {
            try {
                String content = FileUtil.readFromFile(FileUtil.getWuaFile());
                wuaList = content.split("\n");
            }
            catch (Throwable ignored) {
                wuaList = new String[0];
            }
        }
        if (wuaList.length > 0) {
            return wuaList[RandomUtil.nextInt(0, wuaList.length - 1)];
        }
        return "null";
    }
    
    /**
     * 检查是否可以施肥
     */
    private boolean canSpreadManure(PlantScene scene) {
        // 检查是否达到今日限制
        if (Status.hasFlagToday("spreadManureLimit:" + scene.name())) {
            return false;
        }
        
        Integer limit = orchardSpreadManureSceneList.get(scene.name());
        if (limit == null) {
            return false;
        }
        
        try {
            switch (scene) {
                case main:
                    // 主场景施肥检查
                    JSONObject mainAccount = new JSONObject(AntOrchardRpcCall.orchardSyncIndex());
                    if (!MessageUtil.checkResultCode(TAG, mainAccount)) {
                        return false;
                    }
                    JSONObject accountInfo = mainAccount.getJSONObject("farmMainAccountInfo");
                    int happyPoint = Integer.parseInt(accountInfo.getString("happyPoint"));
                    int wateringCost = accountInfo.getInt("wateringCost");
                    int leftTimes = accountInfo.getInt("wateringLeftTimes");
                    
                    return happyPoint >= wateringCost && (200 - leftTimes) < limit;
                
                case yeb:
                    // 余额宝场景施肥检查
                    JSONObject yebProgress = new JSONObject(AntOrchardRpcCall.orchardIndex());
                    if (!MessageUtil.checkResultCode(TAG, yebProgress) || !yebProgress.has("yebScenePlantInfo")) {
                        return false;
                    }
                    JSONObject progressInfo = yebProgress.getJSONObject("yebScenePlantInfo").getJSONObject("plantProgressInfo");
                    int currentProgress = progressInfo.getInt("spreadProgress");
                    int dailyLimit = progressInfo.getInt("dailySpreadLimit");
                    
                    return currentProgress < limit && limit < dailyLimit;
                
                default:
                    return false;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "canSpreadManure err:");
            Log.printStackTrace(TAG, t);
            return false;
        }
    }
    
    /**
     * 切换种植场景
     */
    private boolean switchPlantScene(PlantScene scene) {
        try {
            String sceneName = scene.name();
            String result = AntOrchardRpcCall.switchPlantScene(sceneName);
            return MessageUtil.checkResultCode(TAG, new JSONObject(result));
        }
        catch (Throwable t) {
            Log.i(TAG, "switchPlantScene err:");
            Log.printStackTrace(TAG, t);
            return false;
        }
    }
    
    /**
     * 查询施肥活动奖励
     */
    private void querySpreadManureActivity() {
        try {
            JSONObject jo = new JSONObject(AntOrchardRpcCall.orchardIndex());
            if (MessageUtil.checkResultCode(TAG, jo) && jo.has("spreadManureActivity")) {
                JSONObject activity = jo.getJSONObject("spreadManureActivity");
                JSONObject stage = activity.getJSONObject("spreadManureStage");
                if ("FINISHED".equals(stage.getString("status"))) {
                    String result = AntOrchardRpcCall.receiveTaskAward(stage.getString("sceneCode"), stage.getString("taskType"));
                    JSONObject awardJo = new JSONObject(result);
                    if (MessageUtil.checkResultCode(TAG, awardJo)) {
                        int awardCount = awardJo.getInt("incAwardCount");
                        Log.farm("芭芭农场🎁丰收礼包#获得[" + awardCount + "g肥料]");
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "querySpreadManureActivity err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 农场任务列表处理
     */
    private void orchardListTask() {
        try {
            String result = AntOrchardRpcCall.orchardListTask();
            JSONObject jo = new JSONObject(result);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            
            // 处理签到任务
            if (jo.has("signTaskInfo")) {
                handleSignTask(jo.getJSONObject("signTaskInfo"));
            }
            
            // 处理任务列表
            JSONArray taskArray = jo.getJSONArray("taskList");
            handleTaskList(taskArray);
        }
        catch (Throwable t) {
            Log.i(TAG, "orchardListTask err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 处理签到任务
     */
    private void handleSignTask(JSONObject signInfo) {
        if (Status.hasFlagToday("orchardSign")) {
            return;
        }
        
        try {
            JSONObject currentSign = signInfo.getJSONObject("currentSignItem");
            if (currentSign.getBoolean("signed")) {
                Log.record("农场今日已签到");
                Status.flagToday("orchardSign");
                return;
            }
            
            // 执行签到
            String result = AntOrchardRpcCall.orchardSign();
            JSONObject signJo = new JSONObject(result);
            if (MessageUtil.checkResultCode(TAG, signJo)) {
                JSONObject newSignInfo = signJo.getJSONObject("signTaskInfo").getJSONObject("currentSignItem");
                int continuousDays = newSignInfo.getInt("currentContinuousCount");
                int award = newSignInfo.getInt("awardCount");
                Log.farm("农场任务📅七天签到[第" + continuousDays + "天]#获得[" + award + "g肥料]");
                Status.flagToday("orchardSign");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "handleSignTask err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 处理任务列表
     */
    private void handleTaskList(JSONArray taskArray) {
        try {
            for (int i = 0; i < taskArray.length(); i++) {
                JSONObject jo = taskArray.getJSONObject(i);
                String taskStatus = jo.getString("taskStatus");
                if (TaskStatus.RECEIVED.name().equals(taskStatus)) {
                    continue;
                }
                if (TaskStatus.TODO.name().equals(taskStatus)) {
                    if (!finishOrchardTask(jo)) {
                        continue;
                    }
                    TimeUtil.sleep(500);
                }
                String taskId = jo.getString("taskId");
                String taskPlantType = jo.getString("taskPlantType");
                JSONObject taskDisplayConfig=jo.getJSONObject("taskDisplayConfig");
                if(!taskDisplayConfig.has("title")){
                    continue;
                }
                String title = taskDisplayConfig.getString("title");
                if (TaskStatus.FINISHED.name().equals(taskStatus) && !taskPlantType.equals("TAOBAO")) {
                    receiveTaskReward(taskId, taskPlantType, title);
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "handleTaskList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 完成农场任务
     */
    private boolean finishOrchardTask(JSONObject task) {
        try {
            String title = task.getJSONObject("taskDisplayConfig").getString("title");
            String actionType = task.getString("actionType");
            
            // 处理触发型任务
            if ("TRIGGER".equals(actionType) || "ADD_HOME".equals(actionType) || "PUSH_SUBSCRIBE".equals(actionType)) {
                String sceneCode = task.getString("sceneCode");
                String taskId = task.getString("taskId");
                String result = AntOrchardRpcCall.finishTask(sceneCode, taskId);
                if (MessageUtil.checkResultCode(TAG, new JSONObject(result))) {
                    Log.farm("农场任务🧾完成任务[" + title + "]");
                }
                return true;
            }
            return true;
        }
        catch (Throwable t) {
            Log.i(TAG, "finishOrchardTask err:");
            Log.printStackTrace(TAG, t);
            return false;
        }
    }
    
    /**
     * 领取任务奖励
     */
    private void receiveTaskReward(String taskId, String taskType, String title) {
        try {
            String result = AntOrchardRpcCall.triggerTbTask(taskId, taskType);
            JSONObject jo = new JSONObject(result);
            if (MessageUtil.checkResultCode(TAG, jo)) {
                int award = jo.getInt("incAwardCount");
                Log.farm("农场任务🎖️领取奖励[" + title + "]#获得[" + award + "g肥料]");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "receiveTaskReward err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 领取七日礼包
     */
    private void drawLotteryPlus(JSONObject lotteryInfo) {
        if (Status.hasFlagToday("orchardLotteryPlus")) {
            return;
        }
        
        try {
            if (!lotteryInfo.has("userSevenDaysGiftsItem")) {
                return;
            }
            
            JSONObject giftItem = lotteryInfo.getJSONObject("userSevenDaysGiftsItem");
            JSONArray dailyGifts = giftItem.getJSONArray("userEverydayGiftItems");
            String itemId = lotteryInfo.getString("itemId");
            
            // 检查今日是否已领取
            for (int i = 0; i < dailyGifts.length(); i++) {
                JSONObject daily = dailyGifts.getJSONObject(i);
                if (daily.getString("itemId").equals(itemId) && daily.getBoolean("received")) {
                    Log.record("芭芭农场七日礼包当日奖励已领取");
                    Status.flagToday("orchardLotteryPlus");
                    return;
                }
            }
            
            // 领取礼包
            String result = AntOrchardRpcCall.drawLottery();
            JSONObject drawJo = new JSONObject(result);
            if (MessageUtil.checkResultCode(TAG, drawJo)) {
                JSONArray awardArray = drawJo.getJSONObject("lotteryPlusInfo").getJSONObject("userSevenDaysGiftsItem").getJSONArray("userEverydayGiftItems");
                
                for (int i = 0; i < awardArray.length(); i++) {
                    JSONObject award = awardArray.getJSONObject(i);
                    if (award.getString("itemId").equals(itemId)) {
                        int count = award.optInt("awardCount", 1);
                        Log.farm("芭芭农场🎁七日礼包#获得[" + count + "g肥料]");
                        Status.flagToday("orchardLotteryPlus");
                        return;
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "drawLotteryPlus err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 获取额外信息（每日肥料包）
     */
    private void extraInfoGet() {
        try {
            String result = AntOrchardRpcCall.extraInfoGet();
            JSONObject jo = new JSONObject(result);
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONObject fertilizerPacket = jo.getJSONObject("data").getJSONObject("extraData").getJSONObject("fertilizerPacket");
                
                if ("todayFertilizerWaitTake".equals(fertilizerPacket.getString("status"))) {
                    int fertilizerNum = fertilizerPacket.getInt("todayFertilizerNum");
                    String takeResult = AntOrchardRpcCall.extraInfoSet();
                    if (MessageUtil.checkResultCode(TAG, new JSONObject(takeResult))) {
                        Log.farm("每日肥料💩[" + fertilizerNum + "g]");
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "extraInfoGet err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 好友助力
     */
    private void orchardAssistFriend() {
        if (Status.hasFlagToday("orchardAssistLimit")) {
            return;
        }
        
        Set<String> friendList = assistFriendList.getValue();
        if (friendList == null || friendList.isEmpty()) {
            return;
        }
        
        try {
            for (String friendId : friendList) {
                if (Status.hasFlagToday("orchardAssist:" + friendId)) {
                    continue;
                }
                
                String result = AntOrchardRpcCall.achieveBeShareP2P(friendId);
                JSONObject jo = new JSONObject(result);
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    Log.farm("芭芭农场🌳助力好友[" + UserIdMap.getShowName(friendId) + "]");
                }
                else if ("600000027".equals(jo.optString("code"))) {
                    Status.flagToday("orchardAssistLimit");
                    return;
                }
                
                Status.flagToday("orchardAssist:" + friendId);
                TimeUtil.sleep(5000);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "orchardAssistFriend err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 查询子场景活动（许愿、营地接管等）
     */
    private void querySubplotsActivity(String activityType) {
        try {
            String result = AntOrchardRpcCall.querySubplotsActivity(activityType);
            JSONObject jo = new JSONObject(result);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            
            JSONArray activityList = jo.getJSONArray("subplotsActivityList");
            for (int i = 0; i < activityList.length(); i++) {
                JSONObject activity = activityList.getJSONObject(i);
                if (!activityType.equals(activity.getString("activityType"))) {
                    continue;
                }
                
                if ("WISH".equals(activityType)) {
                    handleWishActivity(activity);
                }
                else if ("CAMP_TAKEOVER".equals(activityType)) {
                    handleCampTakeoverActivity(activity);
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "querySubplotsActivity err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 处理许愿活动
     */
    private void handleWishActivity(JSONObject activity) {
        try {
            String activityId = activity.getString("activityId");
            String status = activity.getString("status");
            
            // 已完成则领取奖励
            if ("FINISHED".equals(status)) {
                String result = AntOrchardRpcCall.receiveOrchardRights(activityId, "WISH");
                JSONObject jo = new JSONObject(result);
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    int amount = jo.getInt("amount");
                    Log.farm("农场许愿✨完成承诺#获得[" + amount + "g肥料]");
                    querySubplotsActivity("WISH"); // 重新查询状态
                }
                return;
            }
            
            // 未开始则许下承诺
            if ("NOT_STARTED".equals(status)) {
                Integer mainCount = orchardSpreadManureSceneList.get("main");
                int targetCount = mainCount != null && mainCount >= 10 ? 10 : (mainCount != null && mainCount >= 3 ? 3 : 0);
                
                if (targetCount > 0) {
                    JSONObject extend = new JSONObject(activity.getString("extend"));
                    JSONArray options = extend.getJSONArray("wishActivityOptionList");
                    
                    for (int i = 0; i < options.length(); i++) {
                        JSONObject option = options.getJSONObject(i);
                        if (option.getInt("taskRequire") == targetCount) {
                            String result = AntOrchardRpcCall.triggerSubplotsActivity(activityId, "WISH", option.getString("optionKey"));
                            if (MessageUtil.checkResultCode(TAG, new JSONObject(result))) {
                                Log.farm("农场许愿✨许下承诺[每日施肥" + targetCount + "次]");
                            }
                            break;
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "handleWishActivity err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 处理营地接管活动
     */
    private void handleCampTakeoverActivity(JSONObject activity) {
        try {
            JSONObject extend = new JSONObject(activity.getString("extend"));
            JSONObject currentInfo = extend.getJSONObject("currentActivityInfo");
            String status = currentInfo.getString("activityStatus");
            
            // 待选择奖励
            if ("TO_CHOOSE_PRIZE".equals(status)) {
                JSONArray prizes = currentInfo.getJSONArray("recommendPrizeList");
                for (int i = 0; i < prizes.length(); i++) {
                    JSONObject prize = prizes.getJSONObject(i);
                    if ("FEILIAO".equals(prize.getString("prizeType"))) {
                        String result = AntOrchardRpcCall.choosePrize(prize.getString("sendOrderId"));
                        JSONObject jo = new JSONObject(result);
                        if (MessageUtil.checkResultCode(TAG, jo)) {
                            String prizeName = jo.getJSONObject("currentActivityInfo").getJSONObject("currentPrize").getString("prizeName");
                            Log.farm("速成奖励✨接受挑战#选择[" + prizeName + "]");
                        }
                        break;
                    }
                }
            }
            
            // 待完成任务
            if ("TO_DO_TASK".equals(status)) {
                JSONArray tasks = currentInfo.getJSONArray("taskList");
                handleTaskList(tasks);
                querySubplotsActivity("CAMP_TAKEOVER"); // 重新查询状态
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "handleCampTakeoverActivity err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 查询余额宝收益
     */
    private void queryYebRevenueDetail() {
        try {
            String result = AntOrchardRpcCall.yebPlantSceneRevenuePage();
            JSONObject jo = new JSONObject(result);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            
            JSONArray revenueList = jo.getJSONArray("yebRevenueDetailList");
            for (int i = 0; i < revenueList.length(); i++) {
                JSONObject revenue = revenueList.getJSONObject(i);
                if ("I".equals(revenue.getString("orderStatus"))) {
                    String triggerResult = AntOrchardRpcCall.triggerYebMoneyTree();
                    JSONObject triggerJo = new JSONObject(triggerResult);
                    if (MessageUtil.checkResultCode(TAG, triggerJo)) {
                        JSONObject awardInfo = triggerJo.getJSONObject("result").optJSONObject("awardInfo");
                        if (awardInfo != null) {
                            String amount = awardInfo.getString("totalAmount");
                            Log.farm("芭芭农场🌳领取奖励[摇钱树]#获得[" + amount + "元余额宝收益]");
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryYebRevenueDetail err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    // 内部枚举定义
    public enum PlantScene {
        main("主场景"), yeb("余额宝场景");
        
        private final String nickname;
        
        PlantScene(String nickname) {
            this.nickname = nickname;
        }
        
        public String nickname() {
            return nickname;
        }
        
        public static PlantScene[] getEntries() {
            return values();
        }
        
        // 用于获取选项列表的静态方法
        public static List<String> getList() {
            List<String> list = new ArrayList<>();
            for (PlantScene scene : values()) {
                list.add(scene.name());
            }
            return list;
        }
    }
    
    public interface DriveAnimalType {
        int NONE = 0;
        int ALL = 1;
        String[] nickNames = {"不操作", "驱赶所有"};
    }
    
    public enum TaskStatus {
        TODO, FINISHED, RECEIVED
    }
}