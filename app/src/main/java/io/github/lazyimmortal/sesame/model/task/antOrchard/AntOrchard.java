package io.github.lazyimmortal.sesame.model.task.antOrchard;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.lazyimmortal.sesame.data.ModelFields;
import io.github.lazyimmortal.sesame.data.ModelGroup;
import io.github.lazyimmortal.sesame.data.modelFieldExt.BooleanModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.IntegerModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectModelField;
import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.entity.AlipayUser;
import io.github.lazyimmortal.sesame.model.base.TaskCommon;
import io.github.lazyimmortal.sesame.model.task.antFarm.AntFarm.TaskStatus;
import io.github.lazyimmortal.sesame.util.*;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

import java.util.*;

public class AntOrchard extends ModelTask {
    private static final String TAG = AntOrchard.class.getSimpleName();

    private String userId;
    private String treeLevel;

    private String[] wuaList;

    private Integer executeIntervalInt;

    private IntegerModelField executeInterval;
    private BooleanModelField orchardListTask;
    private IntegerModelField orchardSpreadManureCount;
    private BooleanModelField batchHireAnimal;
    private SelectModelField dontHireList;
    private SelectModelField dontWeedingList;
    private BooleanModelField assistFriend;
    private SelectModelField assistFriendList;

    @Override
    public String getName() {
        return "农场";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.ORCHARD;
    }

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(executeInterval = new IntegerModelField("executeInterval", "执行间隔(毫秒)", 500));
        modelFields.addField(orchardListTask = new BooleanModelField("orchardListTask", "农场任务", false));
        modelFields.addField(orchardSpreadManureCount = new IntegerModelField("orchardSpreadManureCount", "农场每日施肥次数", 0));
        modelFields.addField(assistFriend = new BooleanModelField("assistFriend", "分享助力 | 开启", false));
        modelFields.addField(assistFriendList = new SelectModelField("assistFriendList", "分享助力 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(batchHireAnimal = new BooleanModelField("batchHireAnimal", "一键捉鸡除草", false));
        modelFields.addField(dontHireList = new SelectModelField("dontHireList", "除草 | 不雇佣好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(dontWeedingList = new SelectModelField("dontWeedingList", "除草 | 不除草好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        return modelFields;
    }

    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.farm("任务暂停⏸️芭芭农场:当前为仅收能量时间");
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        try {
            executeIntervalInt = Math.max(executeInterval.getValue(), 500);
            String s = AntOrchardRpcCall.orchardIndex();
            JSONObject jo = new JSONObject(s);
            if ("100".equals(jo.getString("resultCode"))) {
                if (jo.optBoolean("userOpenOrchard")) {
                    JSONObject taobaoData = new JSONObject(jo.getString("taobaoData")); treeLevel = Integer.toString(taobaoData.getJSONObject("gameInfo").getJSONObject("plantInfo")
                            .getJSONObject("seedStage").optInt("stageLevel",0));
                    JSONObject joo = new JSONObject(AntOrchardRpcCall.mowGrassInfo());
                    if ("100".equals(jo.getString("resultCode"))) {
                        userId = joo.getString("userId");
                        if (jo.has("lotteryPlusInfo"))
                            drawLotteryPlus(jo.getJSONObject("lotteryPlusInfo"));
                        extraInfoGet();
                        if (batchHireAnimal.getValue()) {
                            if (!joo.optBoolean("hireCountOnceLimit", true)
                                    && !joo.optBoolean("hireCountOneDayLimit", true))
                                batchHireAnimalRecommend();
                        }
                        if (orchardListTask.getValue()) {
                            orchardListTask();
                        }
                        Integer orchardSpreadManureCountValue = orchardSpreadManureCount.getValue();
                        if (orchardSpreadManureCountValue > 0 && !Status.hasFlagToday("orchard::spreadManureLimit"))
                            orchardSpreadManure();

                        if (orchardSpreadManureCountValue >= 3
                                && orchardSpreadManureCountValue < 10) {
                            querySubplotsActivity(3);
                        } else if (orchardSpreadManureCountValue >= 10) {
                            querySubplotsActivity(10);
                        }
                        // 助力
                        if (assistFriend.getValue()) {
                            orchardAssistFriend();
                        }
                    } else {
                        Log.record(jo.getString("resultDesc"));
                        Log.i(jo.toString());
                    }
                } else {
                    getEnableField().setValue(false);
                    Log.record("请先开启芭芭农场！");
                }
            } else {
                Log.i(TAG, jo.getString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.i(TAG, "start.run err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private String getWua() {
        if (wuaList == null) {
            try {
                String content = FileUtil.readFromFile(FileUtil.getWuaFile());
                wuaList = content.split("\n");
            } catch (Throwable ignored) {
                wuaList = new String[0];
            }
        }
        if (wuaList.length > 0) {
            return wuaList[RandomUtil.nextInt(0, wuaList.length - 1)];
        }
        return "null";
    }

    private boolean canSpreadManureContinue(int stageBefore, int stageAfter) {
        if (stageAfter - stageBefore > 1) {
            return true;
        }
        Log.record("施肥只加0.01%进度今日停止施肥！");
        return false;
    }

    private void orchardSpreadManure() {
        try {
            do {
                try {
                    JSONObject jo = new JSONObject(AntOrchardRpcCall.orchardIndex());
                    if (!"100".equals(jo.getString("resultCode"))) {
                        Log.i(TAG, jo.getString("resultDesc"));
                        return;
                    }
                    if (jo.has("spreadManureActivity")) {
                        JSONObject spreadManureStage = jo.getJSONObject("spreadManureActivity")
                                .getJSONObject("spreadManureStage");
                        if ("FINISHED".equals(spreadManureStage.getString("status"))) {
                            String sceneCode = spreadManureStage.getString("sceneCode");
                            String taskType = spreadManureStage.getString("taskType");
                            int awardCount = spreadManureStage.getInt("awardCount");
                            JSONObject joo = new JSONObject(AntOrchardRpcCall.receiveTaskAward(sceneCode, taskType));
                            if (joo.optBoolean("success")) {
                                Log.farm("丰收礼包🎁[肥料*" + awardCount + "]");
                            } else {
                                Log.record(joo.getString("desc"));
                                Log.i(joo.toString());
                            }
                        }
                    }
                    String taobaoData = jo.getString("taobaoData");
                    jo = new JSONObject(taobaoData);
                    JSONObject plantInfo = jo.getJSONObject("gameInfo").getJSONObject("plantInfo");
                    boolean canExchange = plantInfo.getBoolean("canExchange");
                    if (canExchange) {
                        Log.farm("农场果树似乎可以兑换了！");
                        return;
                    }
                    JSONObject seedStage = plantInfo.getJSONObject("seedStage");
                    treeLevel = Integer.toString(seedStage.optInt("stageLevel",0));
                    JSONObject accountInfo = jo.getJSONObject("gameInfo").getJSONObject("accountInfo");
                    int happyPoint = Integer.parseInt(accountInfo.getString("happyPoint"));
                    int wateringCost = accountInfo.getInt("wateringCost");
                    int wateringLeftTimes = accountInfo.getInt("wateringLeftTimes");
                    if (happyPoint > wateringCost && wateringLeftTimes > 0
                            && (200 - wateringLeftTimes < orchardSpreadManureCount.getValue())) {
                        jo = new JSONObject(AntOrchardRpcCall.orchardSpreadManure(getWua()));
                        if (!"100".equals(jo.getString("resultCode"))) {
                            Log.record(jo.getString("resultDesc"));
                            Log.i(jo.toString());
                            return;
                        }
                        taobaoData = jo.getString("taobaoData");
                        jo = new JSONObject(taobaoData);
                        String stageText = jo.getJSONObject("currentStage").getString("stageText");
                        Log.farm("农场施肥💩[" + stageText + "]");
                        if (!canSpreadManureContinue(seedStage.getInt("totalValue"), jo.getJSONObject("currentStage").getInt("totalValue"))) {
                            Status.flagToday("orchard::spreadManureLimit");
                            return;
                        }
                        continue;
                    }
                } finally {
                    TimeUtil.sleep(executeIntervalInt);
                }
                break;
            } while (true);
        } catch (Throwable t) {
            Log.i(TAG, "orchardSpreadManure err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void extraInfoGet() {
        try {
            String s = AntOrchardRpcCall.extraInfoGet();
            JSONObject jo = new JSONObject(s);
            if ("100".equals(jo.getString("resultCode"))) {
                JSONObject fertilizerPacket = jo.getJSONObject("data").getJSONObject("extraData")
                        .getJSONObject("fertilizerPacket");
                if (!"todayFertilizerWaitTake".equals(fertilizerPacket.getString("status")))
                    return;
                int todayFertilizerNum = fertilizerPacket.getInt("todayFertilizerNum");
                jo = new JSONObject(AntOrchardRpcCall.extraInfoSet());
                if ("100".equals(jo.getString("resultCode"))) {
                    Log.farm("每日肥料💩[" + todayFertilizerNum + "g]");
                } else {
                    Log.i(jo.getString("resultDesc"), jo.toString());
                }
            } else {
                Log.i(jo.getString("resultDesc"), jo.toString());
            }
        } catch (Throwable t) {
            Log.i(TAG, "extraInfoGet err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void drawLotteryPlus(JSONObject lotteryPlusInfo) {
        try {
            if (!lotteryPlusInfo.has("userSevenDaysGiftsItem"))
                return;
            String itemId = lotteryPlusInfo.getString("itemId");
            JSONObject jo = lotteryPlusInfo.getJSONObject("userSevenDaysGiftsItem");
            JSONArray ja = jo.getJSONArray("userEverydayGiftItems");
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                if (jo.getString("itemId").equals(itemId)) {
                    if (!jo.getBoolean("received")) {
                        jo = new JSONObject(AntOrchardRpcCall.drawLottery());
                        if ("100".equals(jo.getString("resultCode"))) {
                            JSONArray userEverydayGiftItems = jo.getJSONObject("lotteryPlusInfo")
                                    .getJSONObject("userSevenDaysGiftsItem").getJSONArray("userEverydayGiftItems");
                            for (int j = 0; j < userEverydayGiftItems.length(); j++) {
                                jo = userEverydayGiftItems.getJSONObject(j);
                                if (jo.getString("itemId").equals(itemId)) {
                                    int awardCount = jo.optInt("awardCount", 1);
                                    Log.farm("七日礼包🎁[获得肥料]#" + awardCount + "g");
                                    break;
                                }
                            }
                        } else {
                            Log.i(jo.getString("resultDesc"), jo.toString());
                        }
                    } else {
                        Log.record("七日礼包已领取");
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "drawLotteryPlus err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void orchardListTask() {
        try {
            JSONObject jo = new JSONObject(AntOrchardRpcCall.orchardListTask());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (jo.has("signTaskInfo")) {
                orchardSign(jo.getJSONObject("signTaskInfo"));
            }
            JSONArray ja = jo.getJSONArray("taskList");
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
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
                String title = jo.getJSONObject("taskDisplayConfig").getString("title");
                triggerTbTask(taskId, taskPlantType, title);
            }
        } catch (Throwable t) {
            Log.i(TAG, "orchardListTask err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void orchardSign(JSONObject signTaskInfo) {
        if (Status.hasFlagToday("orchard::sign")) {
            return;
        }
        try {
            boolean signed = signTaskInfo.getJSONObject("currentSignItem").getBoolean("signed");
            if (!signed) {
                JSONObject jo = new JSONObject(AntOrchardRpcCall.orchardSign());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    jo = jo.getJSONObject("signTaskInfo").getJSONObject("currentSignItem");
                    int currentContinuousCount = jo.getInt("currentContinuousCount");
                    int awardCount = jo.getInt("awardCount");
                    Log.farm("农场任务📅签到[坚持" + currentContinuousCount + "天]#获得[" + awardCount + "g肥料]");
                    signed = true;
                }
            } else {
                Log.record("农场今日已签到");
            }
            if (signed) {
                Status.flagToday("orchard::sign");
            }
        } catch (Throwable t) {
            Log.i(TAG, "orchardSign err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static Boolean finishOrchardTask(JSONObject task) {
        try {
            String title = task.getJSONObject("taskDisplayConfig").getString("title");
            String actionType = task.getString("actionType");
            if (Objects.equals("TRIGGER", actionType)
                    || Objects.equals("ADD_HOME", actionType)
                    || Objects.equals("PUSH_SUBSCRIBE", actionType)) {
                String taskId = task.getString("taskId");
                String sceneCode = task.getString("sceneCode");
                JSONObject jo = new JSONObject(AntOrchardRpcCall.finishTask(sceneCode, taskId));
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    Log.farm("农场任务🧾完成[" + title + "]");
                    return true;
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "finishOrchardTask err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private static void triggerTbTask(String taskId, String taskPlantType, String title) {
        try {
            JSONObject jo = new JSONObject(AntOrchardRpcCall.triggerTbTask(taskId, taskPlantType));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                int incAwardCount = jo.getInt("incAwardCount");
                Log.farm("农场任务🎖️领取[" + title + "]奖励#获得[" + incAwardCount + "g肥料]");
            }
        } catch (Throwable t) {
            Log.i(TAG, "triggerTbTask err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void querySubplotsActivity(int taskRequire) {
        try {
            if(treeLevel.equals("0")){return;}
            String s = AntOrchardRpcCall.querySubplotsActivity(treeLevel);
            JSONObject jo = new JSONObject(s);
            if ("100".equals(jo.getString("resultCode"))) {
                JSONArray subplotsActivityList = jo.getJSONArray("subplotsActivityList");
                for (int i = 0; i < subplotsActivityList.length(); i++) {
                    jo = subplotsActivityList.getJSONObject(i);
                    if (!"WISH".equals(jo.getString("activityType")))
                        continue;
                    String activityId = jo.getString("activityId");
                    if ("NOT_STARTED".equals(jo.getString("status"))) {
                        String extend = jo.getString("extend");
                        jo = new JSONObject(extend);
                        JSONArray wishActivityOptionList = jo.getJSONArray("wishActivityOptionList");
                        String optionKey = null;
                        for (int j = 0; j < wishActivityOptionList.length(); j++) {
                            jo = wishActivityOptionList.getJSONObject(j);
                            if (taskRequire == jo.getInt("taskRequire")) {
                                optionKey = jo.getString("optionKey");
                                break;
                            }
                        }
                        if (optionKey != null) {
                            jo = new JSONObject(
                                    AntOrchardRpcCall.triggerSubplotsActivity(activityId, "WISH", optionKey));
                            if ("100".equals(jo.getString("resultCode"))) {
                                Log.farm("农场许愿✨[每日施肥" + taskRequire + "次]");
                            } else {
                                Log.record(jo.getString("resultDesc"));
                                Log.i(jo.toString());
                            }
                        }
                    } else if ("FINISHED".equals(jo.getString("status"))) {
                        jo = new JSONObject(AntOrchardRpcCall.receiveOrchardRights(activityId, "WISH"));
                        if ("100".equals(jo.getString("resultCode"))) {
                            Log.farm("许愿奖励✨[肥料" + jo.getInt("amount") + "g]");
                            querySubplotsActivity(taskRequire);
                            return;
                        } else {
                            Log.record(jo.getString("resultDesc"));
                            Log.i(jo.toString());
                        }
                    }
                }
            } else {
                Log.record(jo.getString("resultDesc"));
                Log.i(s);
            }
        } catch (Throwable t) {
            Log.i(TAG, "triggerTbTask err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void batchHireAnimalRecommend() {
        try {
            JSONObject jo = new JSONObject(AntOrchardRpcCall.batchHireAnimalRecommend(UserIdMap.getCurrentUid()));
            if ("100".equals(jo.getString("resultCode"))) {
                JSONArray recommendGroupList = jo.optJSONArray("recommendGroupList");
                if (recommendGroupList != null && recommendGroupList.length() > 0) {
                    List<String> GroupList = new ArrayList<>();
                    for (int i = 0; i < recommendGroupList.length(); i++) {
                        jo = recommendGroupList.getJSONObject(i);
                        String animalUserId = jo.getString("animalUserId");
                        if (dontHireList.getValue().contains(animalUserId)) {
                            continue;
                        }
                        int earnManureCount = jo.getInt("earnManureCount");
                        String groupId = jo.getString("groupId");
                        String orchardUserId = jo.getString("orchardUserId");
                        if (dontWeedingList.getValue().contains(orchardUserId)) {
                            continue;
                        }
                        GroupList.add("{\"animalUserId\":\"" + animalUserId + "\",\"earnManureCount\":"
                                + earnManureCount + ",\"groupId\":\"" + groupId + "\",\"orchardUserId\":\""
                                + orchardUserId + "\"}");
                    }
                    if (!GroupList.isEmpty()) {
                        jo = new JSONObject(AntOrchardRpcCall.batchHireAnimal(GroupList));
                        if ("100".equals(jo.getString("resultCode"))) {
                            Log.farm("一键捉鸡🐣[除草]");
                        }
                    }
                }
            } else {
                Log.record(jo.getString("resultDesc"));
                Log.i(jo.toString());
            }
        } catch (Throwable t) {
            Log.i(TAG, "batchHireAnimalRecommend err:");
            Log.printStackTrace(TAG, t);
        }
    }

    // 助力
    private void orchardAssistFriend() {
        if (Status.hasFlagToday("orchard::shareP2PLimit")) {
            return;
        }
        try {
            Set<String> friendSet = assistFriendList.getValue();
            for (String friendUserId : friendSet) {
                if (!Status.canOrchardShareP2PToday(friendUserId)) {
                    continue;
                }
                JSONObject jo = new JSONObject(AntOrchardRpcCall.achieveBeShareP2P(friendUserId));
                TimeUtil.sleep(5000);
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    Log.farm("农场助力🎉助力[" + UserIdMap.getMaskName(friendUserId) + "]成功");
                    Status.orchardShareP2PToday(friendUserId);
                } else if (Objects.equals("600000027", jo.getString("code"))) {
                    Status.flagToday("orchard::shareP2PLimit");
                    return;
                } else {
                    Status.flagToday("orchard::shareP2PLimit::" + friendUserId);
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "orchardAssistFriend err:");
            Log.printStackTrace(TAG, t);
        }
    }
}