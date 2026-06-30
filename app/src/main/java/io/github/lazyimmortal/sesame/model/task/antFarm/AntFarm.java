package io.github.lazyimmortal.sesame.model.task.antFarm;

import android.os.Build;

import io.github.lazyimmortal.sesame.entity.AlipayAntFarmDoFarmTaskList;
import io.github.lazyimmortal.sesame.entity.AlipayAntFarmDrawMachineTaskList;
import io.github.lazyimmortal.sesame.entity.GameCenterMallItem;
import io.github.lazyimmortal.sesame.model.task.antForest.AntForestRpcCall;
import io.github.lazyimmortal.sesame.model.task.antGame.GameTask;
import io.github.lazyimmortal.sesame.util.idMap.AntFarmDoFarmTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.AntFarmDrawMachineTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.GameCenterMallItemMap;
import lombok.Getter;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.lazyimmortal.sesame.data.*;
import io.github.lazyimmortal.sesame.data.ModelFields;
import io.github.lazyimmortal.sesame.data.ModelGroup;
import io.github.lazyimmortal.sesame.data.TokenConfig;
import io.github.lazyimmortal.sesame.data.modelFieldExt.*;
import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.lazyimmortal.sesame.entity.AlipayUser;
import io.github.lazyimmortal.sesame.entity.CustomOption;
import io.github.lazyimmortal.sesame.entity.FarmOrnaments;
import io.github.lazyimmortal.sesame.model.base.TaskCommon;
import io.github.lazyimmortal.sesame.model.extensions.ExtensionsHandle;
import io.github.lazyimmortal.sesame.model.normal.answerAI.AnswerAI;
import io.github.lazyimmortal.sesame.rpc.intervallimit.RpcIntervalLimit;
import io.github.lazyimmortal.sesame.util.*;
import io.github.lazyimmortal.sesame.util.idMap.FarmOrnamentsIdMap;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class AntFarm extends ModelTask {
    private static final String TAG = AntFarm.class.getSimpleName();

    private String ownerFarmId;
    private String ownerUserId;
    private String ownerGroupId;
    private Animal[] animals;
    private Animal ownerAnimal = new Animal();
    private int foodStock;
    private int foodStockLimit;
    private String rewardProductNum;
    private RewardFriend[] rewardList;
    private double benevolenceScore;
    private double harvestBenevolenceScore;
    private int unReceiveTaskAward = 0;
    private double finalScore = 0d;
    private int foodInTrough = 0;

    private FarmTool[] farmTools;

    @Override
    public String getName() {
        return "庄园";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.FARM;
    }

    private BooleanModelField AutoAntFarmDoFarmTaskList;
    private SelectModelField AntFarmDoFarmTaskList;
    private StringModelField sleepTime;
    private IntegerModelField sleepMinutes;
    private BooleanModelField enableSleep;      // 小鸡睡觉开关
    private BooleanModelField feedAnimal;
    private BooleanModelField rewardFriend;
    private ChoiceModelField sendBackAnimalWay;
    private ChoiceModelField sendBackAnimalType;
    private SelectModelField sendBackAnimalList;
    private ChoiceModelField recallAnimalType;
    private BooleanModelField receiveFarmToolReward;
    private BooleanModelField recordFarmGame;
    private ListModelField.ListJoinCommaToStringModelField farmGameTime;
    private BooleanModelField gameCenterBuyMallItem;
    private SelectAndCountModelField gameCenterBuyMallItemList;
    private BooleanModelField kitchen;
    private BooleanModelField useSpecialFood;
    @Getter
    private IntegerModelField useSpecialFoodCountLimit;
    private BooleanModelField useNewEggTool;
    private BooleanModelField harvestProduce;
    private ChoiceModelField donationType;
    private IntegerModelField donationAmount;
    private BooleanModelField receiveFarmTaskAward;
    private BooleanModelField useAccelerateTool;
    private SelectModelField useAccelerateToolOptions;
    private BooleanModelField feedFriendAnimal;
    private SelectAndCountModelField feedFriendAnimalList;
    private ChoiceModelField notifyFriendType;
    private SelectModelField notifyFriendList;
    private BooleanModelField acceptGift;
    private SelectAndCountModelField visitFriendList;
    private BooleanModelField chickenDiary;
    private BooleanModelField drawMachine;
    private BooleanModelField AutoAntFarmDrawMachineTaskList;
    private SelectModelField AntFarmDrawMachineTaskList;
    private BooleanModelField IPexchangeBenefit;
    private BooleanModelField ornamentsDressUp;
    private SelectModelField ornamentsDressUpList;
    private IntegerModelField ornamentsDressUpDays;
    private ChoiceModelField hireAnimalType;
    private SelectModelField hireAnimalList;
    private BooleanModelField drawGameCenterAward;
    private BooleanModelField competition;
    private IntegerModelField competitionStarNum;
    private IntegerModelField competitionLeadEggs;      // 领先第一名的蛋数
    private IntegerModelField competitionDailyLimit;    // 每日捐蛋上限
    private IntegerModelField competitionStealMinutes;  // 偷榜提前分钟数(0或负数不偷榜)
    private IntegerModelField stealRankMinutes;
    private BooleanModelField useBigEaterTool;
    //private ChoiceModelField getFeedType;
    private SelectModelField getFeedList;
    private BooleanModelField family;
    private SelectModelField familyOptions;
    private SelectModelField notInviteList; // 新增：不邀请列表

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(AutoAntFarmDoFarmTaskList = new BooleanModelField("AutoAntFarmDoFarmTaskList", "庄园饲料 | 自动黑白名单", true));
        modelFields.addField(AntFarmDoFarmTaskList = new SelectModelField("AntFarmDoFarmTaskList", "庄园饲料 | 黑名单列表", new LinkedHashSet<>(), AlipayAntFarmDoFarmTaskList::getList));
        modelFields.addField(useNewEggTool = new BooleanModelField("useNewEggTool", "新蛋卡 | 使用", false));
        modelFields.addField(useAccelerateTool = new BooleanModelField("useAccelerateTool", "加速卡 | 使用", false));
        modelFields.addField(useAccelerateToolOptions = new SelectModelField("useAccelerateToolOptions", "加速卡 | 选项", new LinkedHashSet<>(), CustomOption::getUseAccelerateToolOptions));
        modelFields.addField(useBigEaterTool = new BooleanModelField("useBigEaterTool", "加饭卡 | 使用", false));
        modelFields.addField(useSpecialFood = new BooleanModelField("useSpecialFood", "特殊食品 | 使用", false));
        modelFields.addField(useSpecialFoodCountLimit = new IntegerModelField("useSpecialFoodCountLimit", "特殊食品 | " + "使用上限(无限:0)", 0));
        modelFields.addField(rewardFriend = new BooleanModelField("rewardFriend", "打赏好友", false));
        modelFields.addField(recallAnimalType = new ChoiceModelField("recallAnimalType", "召回小鸡", RecallAnimalType.ALWAYS, RecallAnimalType.nickNames));
        modelFields.addField(feedAnimal = new BooleanModelField("feedAnimal", "投喂小鸡", false));
        modelFields.addField(feedFriendAnimal = new BooleanModelField("feedFriendAnimal", "帮喂小鸡 | 开启", true));
        modelFields.addField(feedFriendAnimalList = new SelectAndCountModelField("feedFriendAnimalList", "帮喂小鸡 | " + "好友列表", new LinkedHashMap<>(), AlipayUser::getList, "请填写帮喂次数(每日)"));
        modelFields.addField(hireAnimalType = new ChoiceModelField("hireAnimalType", "雇佣小鸡 | 动作", HireAnimalType.NONE, HireAnimalType.nickNames));
        modelFields.addField(hireAnimalList = new SelectModelField("hireAnimalList", "雇佣小鸡 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(sendBackAnimalWay = new ChoiceModelField("sendBackAnimalWay", "遣返小鸡 | 方式", SendBackAnimalWay.NORMAL, SendBackAnimalWay.nickNames));
        modelFields.addField(sendBackAnimalType = new ChoiceModelField("sendBackAnimalType", "遣返小鸡 | 动作", SendBackAnimalType.NONE, SendBackAnimalType.nickNames));
        modelFields.addField(sendBackAnimalList = new SelectModelField("sendFriendList", "遣返小鸡 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(notifyFriendType = new ChoiceModelField("notifyFriendType", "通知赶鸡 | 动作", NotifyFriendType.NONE, NotifyFriendType.nickNames));
        modelFields.addField(notifyFriendList = new SelectModelField("notifyFriendList", "通知赶鸡 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(ornamentsDressUp = new BooleanModelField("ornamentsDressUp", "装扮焕新 | 开启", false));
        modelFields.addField(ornamentsDressUpList = new SelectModelField("ornamentsDressUpList", "装扮焕新 | 套装列表", new LinkedHashSet<>(), FarmOrnaments::getList));
        modelFields.addField(ornamentsDressUpDays = new IntegerModelField("ornamentsDressUpDays", "装扮焕新 | 焕新频率(天)", 7));
        modelFields.addField(drawMachine = new BooleanModelField("drawMachine", "装扮抽抽乐", false));
        modelFields.addField(AutoAntFarmDrawMachineTaskList = new BooleanModelField("AutoAntFarmDrawMachineTaskList", "抽抽乐 | 自动黑白名单", true));
        modelFields.addField(AntFarmDrawMachineTaskList = new SelectModelField("AntFarmDrawMachineTaskList", "抽抽乐 | 黑名单列表", new LinkedHashSet<>(), AlipayAntFarmDrawMachineTaskList::getList));
        modelFields.addField(IPexchangeBenefit = new BooleanModelField("IPexchangeBenefit", "抽抽乐兑换 | 开启", false));
        modelFields.addField(donationType = new ChoiceModelField("donationType", "每日捐蛋 | 方式", DonationType.ZERO, DonationType.nickNames));
        modelFields.addField(donationAmount = new IntegerModelField("donationAmount", "每日捐蛋 | 倍数(每项)", 1));
        modelFields.addField(competition = new BooleanModelField("competition", "排位赛 | 自动捐蛋领奖", false));
        modelFields.addField(competitionStarNum = new IntegerModelField("competitionStarNum", "保底模式 | 目标星星数", 2, 0, 5));
        modelFields.addField(competitionDailyLimit = new IntegerModelField("competitionDailyLimit", "自动捐蛋 | 每日捐蛋上限(0不限)", 10, 0, 1000));
        modelFields.addField(competitionLeadEggs = new IntegerModelField("competitionLeadEggs", "激进模式 | 捐至榜首领先蛋数", 1, 0, 1000));
        modelFields.addField(competitionStealMinutes = new IntegerModelField("competitionStealMinutes", "激进模式 | 霸榜提前分钟数(0不霸榜，1200为整天)", 0, 0, 1200));
        modelFields.addField(stealRankMinutes = new IntegerModelField("stealRankMinutes", "激进模式 | 偷榜提前分钟数(0不偷榜)", 0, 0, 1200));
        modelFields.addField(family = new BooleanModelField("family", "亲密家庭 | 开启", false));
        modelFields.addField(familyOptions = new SelectModelField("familyOptions", "亲密家庭 | 选项", new LinkedHashSet<>(), CustomOption::getAntFarmFamilyOptions));
        modelFields.addField(notInviteList = new SelectModelField("notInviteList", "亲密家庭 | 不邀请列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(enableSleep = new BooleanModelField("enableSleep", "小鸡睡觉 | 允许睡觉", false));
        modelFields.addField(sleepTime = new StringModelField("sleepTime", "小鸡睡觉 | 时间", "2001"));
        modelFields.addField(sleepMinutes = new IntegerModelField("sleepMinutes", "小鸡睡觉 | 时长(分钟)", 10 * 59, 1, 10 * 60));
        modelFields.addField(recordFarmGame = new BooleanModelField("recordFarmGame", "小鸡乐园 | 游戏改分(星星球、登山赛、飞行赛、揍小鸡)", false));
        List<String> farmGameTimeList = new ArrayList<>();
        farmGameTimeList.add("2200-2400");
        modelFields.addField(farmGameTime = new ListModelField.ListJoinCommaToStringModelField("farmGameTime", "小鸡乐园 " + "| 游戏时间(范围)", farmGameTimeList));
        modelFields.addField(drawGameCenterAward = new BooleanModelField("drawGameCenterAward", "小鸡乐园 | 游戏宝箱", false));
        modelFields.addField(gameCenterBuyMallItem = new BooleanModelField("gameCenterBuyMallItem", "小鸡乐园 | 乐园集市", false));
        modelFields.addField(gameCenterBuyMallItemList = new SelectAndCountModelField("gameCenterBuyMallItemList", "小鸡乐园 | 兑奖", new LinkedHashMap<>(), GameCenterMallItem::getList, "请填写兑奖次数(每日)"));
        modelFields.addField(kitchen = new BooleanModelField("kitchen", "小鸡厨房", false));
        modelFields.addField(chickenDiary = new BooleanModelField("chickenDiary", "小鸡日记", false));
        modelFields.addField(harvestProduce = new BooleanModelField("harvestProduce", "收取爱心鸡蛋", false));
        modelFields.addField(receiveFarmToolReward = new BooleanModelField("receiveFarmToolReward", "收取道具奖励", false));
        modelFields.addField(receiveFarmTaskAward = new BooleanModelField("receiveFarmTaskAward", "收取饲料奖励", false));
        //modelFields.addField(getFeedType = new ChoiceModelField("getFeedType", "一起拿饲料 | 动作", GetFeedType.NONE, GetFeedType.nickNames));
        //modelFields.addField(getFeedList = new SelectModelField("getFeedList", "一起拿饲料 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(acceptGift = new BooleanModelField("acceptGift", "收麦子", false));
        modelFields.addField(visitFriendList = new SelectAndCountModelField("visitFriendList", "送麦子 | 好友列表", new LinkedHashMap<>(), AlipayUser::getList, "请填写赠送次数(每日)"));
        return modelFields;
    }

    @Override
    public void boot(ClassLoader classLoader) {
        super.boot(classLoader);
        RpcIntervalLimit.addIntervalLimit("com.alipay.antfarm.enterFarm", 2000);
    }

    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.farm("任务暂停⏸️蚂蚁庄园:当前为仅收能量时间");
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        try {
            if (enterFarm() == null) {
                return;
            }

            //初始任务列表
            if (!Status.hasFlagToday("BlackList::initAntFarm")) {
                initAntFarmTaskListMap(AutoAntFarmDoFarmTaskList.getValue(), AutoAntFarmDrawMachineTaskList.getValue(), drawMachine.getValue());
                Status.flagToday("BlackList::initAntFarm");
            }

            if (rewardFriend.getValue()) {
                rewardFriend();
            }

            if (sendBackAnimalType.getValue() != SendBackAnimalType.NONE) {
                sendBackAnimal();
            }

            if (!AnimalInteractStatus.HOME.name().equals(ownerAnimal.animalInteractStatus)) {
                if ("ORCHARD".equals(ownerAnimal.locationType)) {
                    Log.farm("庄园通知📣[你家的小鸡给拉去除草了！]");
                    JSONObject joRecallAnimal = new JSONObject(AntFarmRpcCall.orchardRecallAnimal(ownerAnimal.animalId, ownerAnimal.currentFarmMasterUserId));
                    int manureCount = joRecallAnimal.getInt("manureCount");
                    Log.farm("召回小鸡📣收获[" + manureCount + "g肥料]");
                } else {
                    syncAnimalStatusAtOtherFarm(ownerAnimal.currentFarmId);
                    boolean guest = false;
                    switch (SubAnimalType.valueOf(ownerAnimal.subAnimalType)) {
                        case GUEST:
                            guest = true;
                            Log.record("小鸡到好友家去做客了");
                            break;
                        case NORMAL:
                            Log.record("小鸡太饿，离家出走了");
                            break;
                        case PIRATE:
                            Log.record("小鸡外出探险了");
                            break;
                        case WORK:
                            Log.record("小鸡出去工作啦");
                            break;
                        default:
                            Log.record("小鸡不在庄园" + " " + ownerAnimal.subAnimalType);
                    }

                    boolean hungry = false;
                    String userName = UserIdMap.getMaskName(AntFarmRpcCall.farmId2UserId(ownerAnimal.currentFarmId));
                    switch (AnimalFeedStatus.valueOf(ownerAnimal.animalFeedStatus)) {
                        case HUNGRY:
                            hungry = true;
                            Log.record("小鸡在[" + userName + "]的庄园里挨饿");
                            break;

                        case EATING:
                            Log.record("小鸡在[" + userName + "]的庄园里吃得津津有味");
                            break;
                    }

                    boolean recall = false;
                    switch ((int) recallAnimalType.getValue()) {
                        case RecallAnimalType.ALWAYS:
                            recall = true;
                            break;
                        case RecallAnimalType.WHEN_THIEF:
                            recall = !guest;
                            break;
                        case RecallAnimalType.WHEN_HUNGRY:
                            recall = hungry;
                            break;
                    }
                    if (recall) {
                        recallAnimal(ownerAnimal.animalId, ownerAnimal.currentFarmId, ownerFarmId, userName);
                        syncAnimalStatus(ownerFarmId);
                    }
                }
            }

            if (receiveFarmToolReward.getValue()) {
                listFarmTool();
                receiveToolTaskReward();
            }

            if (recordFarmGame.getValue()) {
                long currentTimeMillis = System.currentTimeMillis();
                for (String time : farmGameTime.getValue()) {
                    if (TimeUtil.checkInTimeRange(currentTimeMillis, time)) {
                        recordFarmGame(GameType.starGame);
                        recordFarmGame(GameType.jumpGame);
                        recordFarmGame(GameType.flyGame);
                        recordFarmGame(GameType.hitGame);
                        break;
                    }
                }
            }

            if (gameCenterBuyMallItem.getValue()) {
                gameCenterBuyMallItem();
            }

            if (kitchen.getValue()) {
                collectDailyFoodMaterial(ownerUserId);
                collectDailyLimitedFoodMaterial();
                // 新增：判断小鸡是否在睡觉，如果在睡觉则跳过厨房操作
                if (AnimalFeedStatus.SLEEPY.name().equals(ownerAnimal.animalFeedStatus)) {
                    Log.record("小鸡正在睡觉🛌，跳过小鸡厨房👨🏻‍🍳制作");
                } else {
                    cook(ownerUserId);
                }
            }

            if (chickenDiary.getValue()) {
                queryChickenDiary("");
                queryChickenDiaryList();
            }

            if (useNewEggTool.getValue()) {
                useFarmTool(ownerFarmId, ToolType.NEWEGGTOOL);
                syncAnimalStatus(ownerFarmId);
            }

            if (harvestProduce.getValue() && benevolenceScore >= 1) {
                Log.record("有可收取的爱心鸡蛋");
                harvestProduce(ownerFarmId);
            }

            if (competition.getValue()) {
                competition();
            } else if (donationType.getValue() != DonationType.ZERO) {
                donation();
            }

            if (receiveFarmTaskAward.getValue()) {
                listFarmTask(TaskStatus.TODO);
                listFarmTask(TaskStatus.FINISHED);
            }

            if (AnimalInteractStatus.HOME.name().equals(ownerAnimal.animalInteractStatus)) {
                if (AnimalFeedStatus.HUNGRY.name().equals(ownerAnimal.animalFeedStatus)) {
                    Log.record("小鸡在挨饿");
                    if (feedAnimal.getValue()) {
                        feedAnimal(ownerFarmId);
                    }
                } else if (AnimalFeedStatus.EATING.name().equals(ownerAnimal.animalFeedStatus)) {
                    if (useAccelerateTool.getValue()) {
                        useAccelerateTool();
                        TimeUtil.sleep(1000);
                    }
                    //使用加饭卡
                    if (useBigEaterTool.getValue()) {
                        useFarmTool(ownerFarmId, AntFarm.ToolType.BIG_EATER_TOOL);
                    }
                    if (feedAnimal.getValue()) {
                        autoFeedAnimal();
                        TimeUtil.sleep(1000);
                    }
                }

                checkUnReceiveTaskAward();
            }

            // 小鸡换装
            if (ornamentsDressUp.getValue()) {
                ornamentsDressUp();
            }

            // 到访小鸡送礼
            visitAnimal();

            // 送麦子
            visitFriend();

            // 帮好友喂鸡
            if (feedFriendAnimal.getValue()) {
                feedFriend();
            }

            // 通知好友赶鸡
            if (notifyFriendType.getValue() != NotifyFriendType.NONE) {
                notifyFriend();
            }

            // 抽抽乐
            if (drawMachine.getValue()) {
                drawMachineGroups();

            }

            // 雇佣小鸡
            if (hireAnimalType.getValue() != HireAnimalType.NONE) {
                hireAnimal();
            }
            
            /*  注释掉有问题的代码
             if (getFeedType.getValue() != GetFeedType.NONE) {
                letsGetChickenFeedTogether();
            }*/

            if (family.getValue()) {
                family();
            }

            // 开宝箱
            if (drawGameCenterAward.getValue()) {
                drawGameCenterAward();
            }

            // 小鸡睡觉&起床
            animalSleepAndWake();

        } catch (Throwable t) {
            Log.i(TAG, "AntFarm.start.run err:");
            Log.printStackTrace(TAG, t);
        }
    }

    public static void initAntFarmTaskListMap(boolean AutoAntFarmDoFarmTaskList, boolean AutoAntFarmDrawMachineTaskList, boolean drawMachine) {
        try {
            //初始化AntFarmDoFarmTaskListMap
            AntFarmDoFarmTaskListMap.load();
            Set<String> blackList = new HashSet<>();
            blackList.add("到店付款");
            blackList.add("线上支付");
            blackList.add("逛闪购外卖1元起吃");
            blackList.add("用花呗完成一笔支付");
            Set<String> whiteList = new HashSet<>();// 从黑名单中移除该任务
            //whiteList.add("逛一逛树");
            for (String task : blackList) {
                AntFarmDoFarmTaskListMap.add(task, task);
            }

            JSONObject jo = new JSONObject(AntFarmRpcCall.listFarmTask());
            if (MessageUtil.checkMemo(TAG, jo)) {
                JSONArray ja = jo.getJSONArray("farmTaskList");
                for (int i = 0; i < ja.length(); i++) {
                    jo = ja.getJSONObject(i);
                    String title = jo.getString("title");
                    AntFarmDoFarmTaskListMap.add(title, title);
                }
            }
            //保存任务到配置文件
            AntFarmDoFarmTaskListMap.save();
            Log.record("同步任务🉑庄园饲料任务列表");

            //自动按模块初始化设定调整黑名单和白名单
            if (AutoAntFarmDoFarmTaskList) {
                // 初始化黑白名单（使用集合统一操作）
                ConfigV2 config = ConfigV2.INSTANCE;
                ModelFields AntFarm = config.getModelFieldsMap().get("AntFarm");
                SelectModelField AntFarmDoFarmTaskList = (SelectModelField) AntFarm.get("AntFarmDoFarmTaskList");
                if (AntFarmDoFarmTaskList == null) {
                    return;
                }
                // 2. 批量添加黑名单任务（确保存在）
                Set<String> currentValues = AntFarmDoFarmTaskList.getValue();//该处直接返回列表地址
                if (currentValues != null) {
                    for (String task : blackList) {
                        if (!currentValues.contains(task)) {
                            AntFarmDoFarmTaskList.add(task, 0);
                        }
                    }
                }
                currentValues = AntFarmDoFarmTaskList.getValue();//该处直接返回列表地址
                if (currentValues != null) {

                    // 3. 批量移除白名单任务（从现有列表中删除）
                    for (String task : whiteList) {
                        if (currentValues.contains(task)) {
                            currentValues.remove(task);
                        }
                    }
                }
                // 4. 保存配置
                if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
                    Log.record("黑白名单🈲庄园饲料任务自动设置: " + AntFarmDoFarmTaskList.getValue());
                } else {
                    Log.record("庄园饲料任务黑白名单设置失败");
                }
            }

            //初始化AntFarmDrawMachineTaskListMap
            AntFarmDrawMachineTaskListMap.load();
            blackList = new HashSet<>();
            blackList.add("【限时】玩游戏得新机会");
            blackList.add("【限时】玩游戏得3次机会");
            blackList.add("伸出援手，点亮希望");
            blackList.add("限时玩游戏得新机会");
            blackList.add("【限时】开宝箱得2次机会");
            blackList.add("【限时】开宝箱得3次机会");

            whiteList = new HashSet<>();// 从黑名单中移除该任务
            //whiteList.add("逛一逛树");
            for (String task : blackList) {
                AntFarmDrawMachineTaskListMap.add(task, task);
            }

            if (drawMachine) {
                jo = new JSONObject(AntFarmRpcCall.queryLoveCabin(UserIdMap.getCurrentUid()));
                if (MessageUtil.checkMemo(TAG, jo)) {
                    jo = new JSONObject(AntFarmRpcCall.listFarmDrawTask("ANTFARM_DAILY_DRAW_TASK"));
                    if (MessageUtil.checkMemo(TAG, jo)) {
                        JSONArray farmTaskList = jo.getJSONArray("farmTaskList");
                        for (int i = 0; i < farmTaskList.length(); i++) {
                            jo = farmTaskList.getJSONObject(i);
                            String title = jo.getString("title");
                            AntFarmDrawMachineTaskListMap.add(title, title);
                        }
                        JSONObject queryDrawMachineActivityjo = new JSONObject(AntFarmRpcCall.queryDrawMachineActivity("ipDrawMachine", "dailyDrawMachine"));
                        if (MessageUtil.checkMemo(TAG, queryDrawMachineActivityjo)) {
                            if (queryDrawMachineActivityjo.has("otherDrawMachineActivityIds")) {
                                if (queryDrawMachineActivityjo.getJSONArray("otherDrawMachineActivityIds").length() > 0) {
                                    jo = new JSONObject(AntFarmRpcCall.listFarmDrawTask("ANTFARM_IP_DRAW_TASK"));
                                    if (MessageUtil.checkMemo(TAG, jo)) {
                                        farmTaskList = jo.getJSONArray("farmTaskList");
                                        for (int i = 0; i < farmTaskList.length(); i++) {
                                            jo = farmTaskList.getJSONObject(i);
                                            String title = jo.getString("title");
                                            AntFarmDrawMachineTaskListMap.add(title, title);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                //保存任务到配置文件
                AntFarmDrawMachineTaskListMap.save();
                Log.record("同步任务🉑庄园装扮抽抽乐任务列表");

                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntFarmDrawMachineTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields AntFarm = config.getModelFieldsMap().get("AntFarm");
                    SelectModelField AntFarmDrawMachineTaskList = (SelectModelField) AntFarm.get("AntFarmDrawMachineTaskList");
                    if (AntFarmDrawMachineTaskList == null) {
                        return;
                    }
                    Set<String> currentValues = AntFarmDrawMachineTaskList.getValue();//该处直接返回列表地址
                    if (currentValues != null) {
                        for (String task : blackList) {
                            if (!currentValues.contains(task)) {
                                AntFarmDrawMachineTaskList.add(task, 0);
                            }
                        }
                        for (String task : whiteList) {
                            if (currentValues.contains(task)) {
                                currentValues.remove(task);
                            }
                        }
                    }
                    // 4. 保存配置
                    if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
                        Log.record("黑白名单🈲庄园装扮抽抽乐任务自动设置: " + AntFarmDrawMachineTaskList.getValue());
                    } else {
                        Log.record("庄园装扮抽抽乐任务黑白名单设置失败");
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "initAntFarmTaskListMap err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void animalSleepAndWake() {
        if (!enableSleep.getValue()) {
            Log.record("小鸡睡觉开关已关闭，跳过睡觉逻辑");
            return;
        }
        String sleepTimeStr = sleepTime.getValue();
        if ("-1".equals(sleepTimeStr)) {
            return;
        }
        animalWakeUpNow();
        Calendar animalSleepTimeCalendar = TimeUtil.getTodayCalendarByTimeStr(sleepTimeStr);
        if (animalSleepTimeCalendar == null) {
            return;
        }
        Integer sleepMinutesInt = sleepMinutes.getValue();
        Calendar animalWakeUpTimeCalendar = (Calendar) animalSleepTimeCalendar.clone();
        animalWakeUpTimeCalendar.add(Calendar.MINUTE, sleepMinutesInt);
        long animalSleepTime = animalSleepTimeCalendar.getTimeInMillis();
        long animalWakeUpTime = animalWakeUpTimeCalendar.getTimeInMillis();
        if (animalSleepTime > animalWakeUpTime) {
            Log.record("小鸡睡觉设置有误，请重新设置");
            return;
        }
        Calendar now = TimeUtil.getNow();
        boolean afterSleepTime = now.compareTo(animalSleepTimeCalendar) > 0;
        boolean afterWakeUpTime = now.compareTo(animalWakeUpTimeCalendar) > 0;
        if (afterSleepTime && afterWakeUpTime) {
            // 睡觉时间后
            if (hasSleepToday()) {
                return;
            }
            Log.record("已错过小鸡今日睡觉时间");
            return;
        }
        if (afterSleepTime) {
            // 睡觉时间内
            if (!hasSleepToday()) {
                animalSleepNow();
            }
            animalWakeUpTime(animalWakeUpTime);
            return;
        }
        // 睡觉时间前
        animalWakeUpTimeCalendar.add(Calendar.HOUR_OF_DAY, -24);
        if (now.compareTo(animalWakeUpTimeCalendar) <= 0) {
            animalWakeUpTime(animalWakeUpTimeCalendar.getTimeInMillis());
        }
        animalSleepTime(animalSleepTime);
        animalWakeUpTime(animalWakeUpTime);
    }

    private JSONObject enterFarm() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.enterFarm("", UserIdMap.getCurrentUid()));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return null;
            }
            rewardProductNum = jo.getJSONObject("dynamicGlobalConfig").getString("rewardProductNum");
            JSONObject joFarmVO = jo.getJSONObject("farmVO");
            foodStock = joFarmVO.getInt("foodStock");
            foodStockLimit = joFarmVO.getInt("foodStockLimit");
            harvestBenevolenceScore = joFarmVO.getDouble("harvestBenevolenceScore");
            parseSyncAnimalStatusResponse(joFarmVO.toString());
            ownerUserId = joFarmVO.getJSONObject("masterUserInfoVO").getString("userId");
            ownerGroupId = getFamilyGroupId(ownerUserId);

            if (jo.has("activityData")) {
                JSONObject activityData = jo.optJSONObject("activityData");
                if (activityData.has("springGifts")) {
                    JSONArray springGifts = activityData.optJSONArray("springGifts");
                    if (springGifts != null) {
                        for (int i = 0; i < springGifts.length(); i++) {
                            JSONObject springGift = springGifts.getJSONObject(i);
                            String foodType = springGift.optString("foodType");
                            int giftIndex = springGift.optInt("giftIndex");
                            String foodSubType = springGift.optString("foodSubType");
                            int foodCount = springGift.optInt("foodCount");
                            AntFarmRpcCall.clickForGiftV2(foodType, giftIndex);
                            if (MessageUtil.checkMemo(TAG, jo)) {
                                Log.farm("惊喜礼包🎁[" + foodSubType + "*" + foodCount + "]");
                            }
                        }
                    }
                }
            }

            if (useSpecialFood.getValue()) {
                if (jo.has("cuisineList")) {
                    JSONArray cuisineList = jo.getJSONArray("cuisineList");
                    if (AnimalInteractStatus.HOME.name().equals(ownerAnimal.animalInteractStatus) && !AnimalFeedStatus.SLEEPY.name().equals(ownerAnimal.animalFeedStatus) && Status.canUseSpecialFoodToday()) {
                        useFarmFood(cuisineList);
                    }
                }
            }

            if (jo.has("lotteryPlusInfo")) {
                drawLotteryPlus(jo.getJSONObject("lotteryPlusInfo"));
            }
            if (acceptGift.getValue() && joFarmVO.getJSONObject("subFarmVO").has("giftRecord") && foodStockLimit - foodStock >= 10) {
                acceptGift();
            }
            return jo;
        } catch (Throwable t) {
            Log.i(TAG, "enterFarm err:");
            Log.printStackTrace(TAG, t);
        }
        return null;
    }

    private void autoFeedAnimal() {
        syncAnimalStatus(ownerFarmId);
        if (!AnimalFeedStatus.EATING.name().equals(ownerAnimal.animalFeedStatus)) {
            return;
        }
        double foodHaveEatten = 0d;
        double consumeSpeed = 0d;
        long nowTime = System.currentTimeMillis();
        for (Animal animal : animals) {
            foodHaveEatten += (nowTime - animal.startEatTime) / 1000 * animal.consumeSpeed;
            consumeSpeed += animal.consumeSpeed;
        }
        long nextFeedTime = nowTime + (long) ((foodInTrough - foodHaveEatten) / consumeSpeed) * 1000;
        String taskId = "FA|" + ownerFarmId;
        if (hasChildTask(taskId)) {
            removeChildTask(taskId);
        }
        addChildTask(new ChildModelTask(taskId, "FA", () -> feedAnimal(ownerFarmId), nextFeedTime));
        Log.record("添加蹲点投喂🥣[" + UserIdMap.getCurrentMaskName() + "]在[" + TimeUtil.getCommonDate(nextFeedTime) + "]执行");
    }

    private void animalSleepTime(long animalSleepTime) {
        String sleepTaskId = "AS|" + animalSleepTime;
        if (!hasChildTask(sleepTaskId)) {
            addChildTask(new ChildModelTask(sleepTaskId, "AS", this::animalSleepNow, animalSleepTime));
            Log.record("添加定时睡觉🛌[" + UserIdMap.getCurrentMaskName() + "]在[" + TimeUtil.getCommonDate(animalSleepTime) + "]执行");
        }
    }

    private void animalWakeUpTime(long animalWakeUpTime) {
        String wakeUpTaskId = "AW|" + animalWakeUpTime;
        if (!hasChildTask(wakeUpTaskId)) {
            addChildTask(new ChildModelTask(wakeUpTaskId, "AW", this::animalWakeUpNow, animalWakeUpTime));
            Log.record("添加定时起床🔆[" + UserIdMap.getCurrentMaskName() + "]在[" + TimeUtil.getCommonDate(animalWakeUpTime) + "]执行");
        }
    }

    private Boolean hasSleepToday() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.queryLoveCabin(ownerUserId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            jo = jo.getJSONObject("sleepNotifyInfo");
            return jo.optBoolean("hasSleepToday", false);
        } catch (Throwable t) {
            Log.i(TAG, "hasSleepToday err:");
            Log.printStackTrace(t);
        }
        return false;
    }

    private Boolean animalSleepNow() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.queryLoveCabin(UserIdMap.getCurrentUid()));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            JSONObject sleepNotifyInfo = jo.getJSONObject("sleepNotifyInfo");
            if (!sleepNotifyInfo.optBoolean("canSleep", false)) {
                Log.record("小鸡无需睡觉🛌");
                return false;
            }
            if (family.getValue() && !StringUtil.isEmpty(ownerGroupId)) {
                return familySleep(ownerGroupId);
            }
            return animalSleep();
        } catch (Throwable t) {
            Log.i(TAG, "animalSleepNow err:");
            Log.printStackTrace(t);
        }
        return false;
    }

    private Boolean animalWakeUpNow() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.queryLoveCabin(UserIdMap.getCurrentUid()));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            JSONObject ownAnimal = jo.getJSONObject("ownAnimal");
            JSONObject sleepInfo = ownAnimal.getJSONObject("sleepInfo");
            if (sleepInfo.getInt("countDown") == 0) {
                return false;
            }
            if (sleepInfo.getLong("sleepBeginTime") + TimeUnit.MINUTES.toMillis(sleepMinutes.getValue()) <= System.currentTimeMillis()) {
                if (jo.has("spaceType")) {
                    return familyWakeUp();
                }
                return animalWakeUp();
            } else {
                Log.record("小鸡无需起床🔆");
            }
        } catch (Throwable t) {
            Log.i(TAG, "animalWakeUpNow err:");
            Log.printStackTrace(t);
        }
        return false;
    }

    private Boolean animalSleep() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.sleep());
            if (MessageUtil.checkMemo(TAG, jo)) {
                Log.farm("小鸡睡觉🛌");
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "animalSleep err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private Boolean animalWakeUp() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.wakeUp());
            if (MessageUtil.checkMemo(TAG, jo)) {
                Log.farm("小鸡起床🔆");
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "animalWakeUp err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private void syncAnimalStatus(String farmId) {
        try {
            String s = AntFarmRpcCall.syncAnimalStatus(farmId);
            parseSyncAnimalStatusResponse(s);
        } catch (Throwable t) {
            Log.i(TAG, "syncAnimalStatus err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void syncAnimalStatusAtOtherFarm(String farmId) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.enterFarm(farmId, ""));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("farmVO").getJSONObject("subFarmVO");
            JSONArray jaAnimals = jo.getJSONArray("animals");
            for (int i = 0; i < jaAnimals.length(); i++) {
                jo = jaAnimals.getJSONObject(i);
                if (jo.getString("masterFarmId").equals(ownerFarmId)) {
                    Animal newOwnerAnimal = new Animal();
                    JSONObject animal = jaAnimals.getJSONObject(i);
                    newOwnerAnimal.animalId = animal.getString("animalId");
                    newOwnerAnimal.currentFarmId = animal.getString("currentFarmId");
                    newOwnerAnimal.currentFarmMasterUserId = animal.getString("currentFarmMasterUserId");
                    newOwnerAnimal.masterFarmId = ownerFarmId;
                    newOwnerAnimal.animalBuff = animal.getString("animalBuff");
                    newOwnerAnimal.locationType = animal.optString("locationType", "");
                    newOwnerAnimal.subAnimalType = animal.getString("subAnimalType");
                    animal = animal.getJSONObject("animalStatusVO");
                    newOwnerAnimal.animalFeedStatus = animal.getString("animalFeedStatus");
                    newOwnerAnimal.animalInteractStatus = animal.getString("animalInteractStatus");
                    ownerAnimal = newOwnerAnimal;
                    break;
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "syncAnimalStatusAtOtherFarm err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void rewardFriend() {
        try {
            if (rewardList != null) {
                for (RewardFriend rewardFriend : rewardList) {
                    JSONObject jo = new JSONObject(AntFarmRpcCall.rewardFriend(rewardFriend.consistencyKey, rewardFriend.friendId, rewardProductNum, rewardFriend.time));
                    if (MessageUtil.checkMemo(TAG, jo)) {
                        double rewardCount = benevolenceScore - jo.getDouble("farmProduct");
                        benevolenceScore -= rewardCount;
                        Log.farm("打赏好友💰[" + UserIdMap.getMaskName(rewardFriend.friendId) + "]#得" + rewardCount + "颗爱心鸡蛋");
                    }
                }
                rewardList = null;
            }
        } catch (Throwable t) {
            Log.i(TAG, "rewardFriend err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void recallAnimal(String animalId, String currentFarmId, String masterFarmId, String user) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.recallAnimal(animalId, currentFarmId, masterFarmId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            double foodHaveStolen = jo.getDouble("foodHaveStolen");
            Log.farm("召回小鸡📣偷吃[" + user + "]饲料" + foodHaveStolen + "g");
            // 这里不需要加
            // add2FoodStock((int)foodHaveStolen);
        } catch (Throwable t) {
            Log.i(TAG, "recallAnimal err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void sendBackAnimal() {
        if (animals == null) {
            return;
        }
        try {
            for (Animal animal : animals) {
                if (AnimalInteractStatus.STEALING.name().equals(animal.animalInteractStatus) && !SubAnimalType.GUEST.name().equals(animal.subAnimalType) && !SubAnimalType.WORK.name().equals(animal.subAnimalType)) {
                    // 赶鸡
                    String user = AntFarmRpcCall.farmId2UserId(animal.masterFarmId);
                    boolean isSendBackAnimal = sendBackAnimalList.getValue().contains(user);
                    if (sendBackAnimalType.getValue() != SendBackAnimalType.BACK) {
                        isSendBackAnimal = !isSendBackAnimal;
                    }
                    if (!isSendBackAnimal) {
                        continue;
                    }
                    int sendTypeInt = sendBackAnimalWay.getValue();
                    user = UserIdMap.getMaskName(user);
                    JSONObject jo = new JSONObject(AntFarmRpcCall.sendBackAnimal(SendBackAnimalWay.nickNames[sendTypeInt], animal.animalId, animal.currentFarmId, animal.masterFarmId));
                    if (MessageUtil.checkMemo(TAG, jo)) {
                        String s;
                        if (sendTypeInt == SendBackAnimalWay.HIT) {
                            if (jo.has("hitLossFood")) {
                                s = "胖揍小鸡🤺[" + user + "]，掉落[" + jo.getInt("hitLossFood") + "g]";
                                if (jo.has("finalFoodStorage")) {
                                    foodStock = jo.getInt("finalFoodStorage");
                                }
                            } else {
                                s = "[" + user + "]的小鸡躲开了攻击";
                            }
                        } else {
                            s = "驱赶小鸡🧶[" + user + "]";
                        }
                        Log.farm(s);
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "sendBackAnimal err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void receiveToolTaskReward() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.listToolTaskDetails());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONArray jaList = jo.getJSONArray("list");
            for (int i = 0; i < jaList.length(); i++) {
                JSONObject joItem = jaList.getJSONObject(i);
                if (!TaskStatus.FINISHED.name().equals(joItem.optString("taskStatus"))) {
                    continue;
                }
                JSONObject bizInfo = new JSONObject(joItem.getString("bizInfo"));
                String awardType = bizInfo.optString("awardType");
                ToolType toolType = ToolType.valueOf(awardType);
                boolean isFull = false;
                for (FarmTool farmTool : farmTools) {
                    if (farmTool.toolType == toolType) {
                        if (farmTool.toolCount == farmTool.toolHoldLimit) {
                            isFull = true;
                        }
                        break;
                    }
                }
                if (isFull) {
                    if (toolType.equals(ToolType.NEWEGGTOOL)) {
                        useFarmTool(ownerFarmId, ToolType.NEWEGGTOOL);
                    } else {
                        Log.record("领取道具[" + toolType.nickName() + "]#已满，暂不领取");
                        continue;
                    }
                }
                int awardCount = bizInfo.getInt("awardCount");
                String taskType = joItem.getString("taskType");
                String taskTitle = bizInfo.getString("taskTitle");
                jo = new JSONObject(AntFarmRpcCall.receiveToolTaskReward(awardType, awardCount, taskType));
                if (MessageUtil.checkMemo(TAG, jo)) {
                    Log.farm("领取道具🎖️[" + taskTitle + "-" + toolType.nickName() + "]#" + awardCount + "张");
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "receiveToolTaskReward err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void harvestProduce(String farmId) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.harvestProduce(farmId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            double harvest = jo.getDouble("harvestBenevolenceScore");
            harvestBenevolenceScore = jo.getDouble("finalBenevolenceScore");
            Log.farm("收取鸡蛋🥚[" + harvest + "颗]#剩余" + harvestBenevolenceScore + "颗");
        } catch (Throwable t) {
            Log.i(TAG, "harvestProduce err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /* 捐赠爱心鸡蛋 */
    private void donation() {
        if (!canDonationToday()) {
            return;
        }
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.listActivityInfo());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONArray activityInfos = jo.getJSONArray("activityInfos");
            for (int i = 0; i < activityInfos.length(); i++) {
                jo = activityInfos.getJSONObject(i);
                int donationTotal = jo.getInt("donationTotal");
                int donationLimit = jo.getInt("donationLimit");

                int donationNum = Math.min(donationAmount.getValue(), donationLimit - donationTotal);
                if (donationNum == 0) {
                    continue;
                }
                String activityId = jo.getString("activityId");
                String projectName = jo.getString("projectName");
                String projectId = jo.getString("projectId");
                int projectDonationNum = getProjectDonationNum(projectId);
                donationNum = Math.min(donationNum, donationAmount.getValue() - projectDonationNum % donationAmount.getValue());
                boolean isDonation;
                if (donationNum == donationAmount.getValue()) {
                    isDonation = donation(activityId, projectName, donationNum, 1);
                } else {
                    isDonation = donation(activityId, projectName, 1, donationNum);
                }
                if (isDonation && donationType.getValue() != DonationType.ALL) {
                    return;
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "donation err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void donation(int donateNum) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.listActivityInfo());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            if (!jo.has("activityInfos")) {
                return;
            }

            JSONArray activityInfos = jo.getJSONArray("activityInfos");

            // 收集可捐蛋项目
            java.util.ArrayList<JSONObject> availableList = new java.util.ArrayList<>();
            for (int i = 0; i < activityInfos.length(); i++) {
                JSONObject activityInfo = activityInfos.getJSONObject(i);
                int available = activityInfo.getInt("donationLimit") - activityInfo.getInt("donationTotal");
                if (available > 0) {
                    availableList.add(activityInfo);
                }
            }

            if (availableList.isEmpty()) {
                return;
            }

            // 平均分配
            int remaining = donateNum;
            int size = availableList.size();
            for (int i = 0; i < size && remaining > 0; i++) {
                JSONObject activityInfo = availableList.get(i);
                int available = activityInfo.getInt("donationLimit") - activityInfo.getInt("donationTotal");

                // 计算分配数量
                int assign;
                if (i == size - 1) {
                    assign = remaining;
                } else {
                    assign = donateNum / size;
                    if (i < donateNum % size) {
                        assign++;
                    }
                }

                // 不超过可捐限额和剩余数量
                assign = Math.min(assign, available);
                assign = Math.min(assign, remaining);

                if (assign > 0) {
                    String activityId = activityInfo.getString("activityId");
                    String projectName = activityInfo.getString("projectName");
                    donation(activityId, projectName, assign);
                    remaining -= assign;
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "donation err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private Boolean donation(String activityId, String activityName, int donationAmount, int count) {
        boolean isDonation = false;
        for (int i = 0; i < count; i++) {
            if (!donation(activityId, activityName, donationAmount)) {
                break;
            }
            isDonation = true;
            TimeUtil.sleep(1000L);
        }
        return isDonation;
    }

    private Boolean donation(String activityId, String activityName, int donationAmount) {
        if (harvestBenevolenceScore < donationAmount) {
            return false;
        }
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.donation(activityId, donationAmount));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            jo = jo.getJSONObject("donation");
            harvestBenevolenceScore = jo.getDouble("harvestBenevolenceScore");
            int donationTimesStat = jo.getInt("donationTimesStat");
            Log.farm("公益捐赠❤️[捐爱心蛋:" + activityName + "]捐赠" + donationAmount + "颗爱心蛋#累计捐赠" + donationTimesStat + "次");
            return true;
        } catch (Throwable t) {
            Log.i(TAG, "donation err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private void competition() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.enterDonationCompetitionRank());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            if (jo.has("exitDonationCompetition")) {
                boolean exitDonationCompetition = jo.optBoolean("exitDonationCompetition");
                //开启排位赛
                if (exitDonationCompetition) {
                    JSONObject joOpen = new JSONObject(AntFarmRpcCall.setDonationCompetitionConf("OPEN"));
                    if (MessageUtil.checkMemo(TAG, joOpen)) {
                        String memo = joOpen.optString("memo");
                        Log.farm("捐蛋排位🥚开启：" + memo);
                    }
                } else {
                    //Log.record("捐蛋排位🥚已在排位赛中，跳过加入操作");
                }
            }

            receiveReward();
            PreviousCompetitionInfo();

            String desUserId = null;
            String desNickName = null;
            int desStarRank = 0;
            int desDonation = 0;
            int[] desDonationSub = new int[4];  // 少1~4颗星的捐蛋数
            String[] desUserIdSub = new String[4];// 少1~4颗星的用户名
            int desStarNum = competitionStarNum.getValue();
            int dailyLimit = competitionDailyLimit.getValue();
            int myDonation = 0;
            int myRank = 0;
            int myStar = 0;
            boolean isNovDonation = false;
            String CurrentUserId = UserIdMap.getCurrentUid();

            if (!jo.has("donationRankHomeInfo")) {
                Log.record("捐蛋排位🥚未查询到捐赠排行信息");
                return;
            }
            JSONObject donationRankHomeInfo = jo.getJSONObject("donationRankHomeInfo");
            if (!donationRankHomeInfo.has("userDonationRankList")) {
                Log.record("捐蛋排位🥚未查询到捐赠排行信息");
                return;
            }
            JSONArray userDonationRankList = donationRankHomeInfo.optJSONArray("userDonationRankList");
            if (userDonationRankList == null || userDonationRankList.length() == 0) {
                Log.record("捐蛋排位🥚奖励列表为空");
                return;
            }
            if (desStarNum == 0) {
                Log.record("捐蛋排位🥚目标星级为0跳过保底捐蛋逻辑");
            } else {
                for (int i = 0; i < userDonationRankList.length(); i++) {
                    JSONObject userDonationRank = userDonationRankList.getJSONObject(i);
                    String userId = userDonationRank.optString("userId");
                    String nickName = userDonationRank.optString("nickName");
                    int rewardStarNum = userDonationRank.optInt("rewardStarNum");
                    int donationNum = userDonationRank.optInt("donationNum");
                    int rankOrder = userDonationRank.optInt("rankOrder");
                    if (CurrentUserId.equals(userId)) {
                        myDonation = donationNum;
                        myRank = rankOrder;
                        myStar = rewardStarNum;
                        Log.record("捐蛋排位🥚当前排名" + myRank + "已捐蛋" + myDonation + "预计星星" + myStar);
                    }
                    if (rewardStarNum == desStarNum) {
                        desDonation = donationNum;
                        desStarRank = rankOrder;
                        desNickName = nickName;
                        desUserId = userId;
                    }
                    // 整合：收集少1~4颗星的捐蛋数
                    for (int j = 0; j < 4; j++) {
                        int targetStar = desStarNum - (j + 1);
                        if (targetStar > 0 && rewardStarNum == targetStar) {
                            desDonationSub[j] = donationNum;
                            desUserIdSub[j] = nickName;
                        }
                    }
                }
                if (!CurrentUserId.equals(desUserId)) {
                    Log.record("捐蛋排位🥚保底模式目标星级" + desStarNum + "[" + desNickName + "]" + "已捐蛋" + desDonation);
                }

                // 每天20:01-23:59不执行
                java.util.Calendar now = java.util.Calendar.getInstance();
                int hour = now.get(java.util.Calendar.HOUR_OF_DAY);
                int minute = now.get(java.util.Calendar.MINUTE);
                if (hour > 20 || (hour == 20 && minute >= 1)) {
                    Log.record("捐蛋排位🥚每日20:01后不执行捐蛋操作");
                    return;
                }


                //根据目标星星排名最后的捐蛋数及每日捐蛋上限捐蛋
                if (myRank > desStarRank && desDonation > 0) {
                    int DonationEggNum = desDonation - myDonation + 1;
                    if (desDonation < dailyLimit) {
                        if (DonationEggNum > 0) {
                            Log.farm("捐蛋排位🥚目标星级" + desStarNum + "捐蛋" + desDonation + "当前捐蛋" + myDonation + "尝试再捐蛋" + DonationEggNum);
                            competitionDonation("养老保底模式", DonationEggNum);
                            isNovDonation = true;
                        }

                    } else if (dailyLimit == 0) {
                        if (DonationEggNum > 0) {
                            Log.farm("捐蛋排位🥚目标星级" + desStarNum + "捐蛋" + desDonation + "当前捐蛋" + myDonation + "(无捐蛋上限)尝试再捐蛋" + DonationEggNum);
                            competitionDonation("养老保底模式", DonationEggNum);
                            isNovDonation = true;
                        }
                    } else {
                        if (dailyLimit > myDonation) {
                            Log.record("捐蛋排位🥚目标星级" + desStarNum + "捐蛋" + desDonation + "当前捐蛋限制" + dailyLimit + "尝试减少目标星级捐蛋");
                            // 整合：遍历少1~4颗星的选项
                            for (int j = 0; j < 4; j++) {
                                if (desDonationSub[j] > 0 && desDonationSub[j] < dailyLimit && (4 - j) > myStar) {
                                    DonationEggNum = desDonationSub[j] - myDonation + 1;
                                    if (DonationEggNum < 1) {
                                        continue;
                                    }
                                    Log.farm("捐蛋排位🥚[在捐蛋上限" + dailyLimit + "范围内]比目标星级" + desStarNum + "少" + (j + 1) + "颗星的[" + desUserIdSub[j] + "]捐了" + desDonationSub[j] + "当前捐蛋" + myDonation + "尝试再捐蛋" + DonationEggNum);
                                    competitionDonation("养老保底模式", DonationEggNum);
                                    isNovDonation = true;
                                    break;
                                }
                            }
                            if (!isNovDonation) {
                                Log.record("捐蛋排位🥚目标星级" + desStarNum + "捐蛋" + desDonation + "捐蛋限制" + dailyLimit + "(停止捐蛋)");
                            }
                        } else {
                            Log.record("捐蛋排位🥚目标星级" + desStarNum + "捐蛋" + desDonation + "您的账号已捐蛋" + myDonation + "捐蛋限制" + dailyLimit + "(停止捐蛋)");

                        }
                    }
                }
                //在每日凌晨目标星级还没有人达到且自己捐蛋也为0
                if (myDonation == 0 && desDonation == 0) {
                    Log.farm("捐蛋排位🥚目标星级" + desStarNum + "捐蛋" + desDonation + "当前捐蛋" + myDonation + "尝试首次捐蛋1");
                    competitionDonation("养老保底模式", 1);
                }
            }
            int stealMinutes = competitionStealMinutes.getValue();
            //霸榜时间
            if (isStealRankTime(stealMinutes)) {
                stealRank(stealMinutes, "霸榜");
            }

            //设置偷榜时间定时执行
            int minutes = stealRankMinutes.getValue();
            if (minutes > 0) {
                // 计算今天 20:00 的时间戳
                java.util.Calendar targetTime = java.util.Calendar.getInstance();
                targetTime.set(java.util.Calendar.HOUR_OF_DAY, 20);
                targetTime.set(java.util.Calendar.MINUTE, 0);
                targetTime.set(java.util.Calendar.SECOND, 0);
                targetTime.set(java.util.Calendar.MILLISECOND, 0);

                // 偷榜时间 = 20:00 - minutes
                long stealRankTime = targetTime.getTimeInMillis() - minutes * 60 * 1000L;
                long now = System.currentTimeMillis();

                // 如果偷榜时间已过，设置为明天
                if (stealRankTime <= now) {
                    targetTime.add(java.util.Calendar.DAY_OF_MONTH, 1);
                    stealRankTime = targetTime.getTimeInMillis() - minutes * 60 * 1000L;
                }

                // 添加定时任务
                String taskId = "stealRank_" + minutes;
                if (!hasChildTask(taskId)) {
                    addChildTask(new ChildModelTask(taskId, "STEALRANK", () -> stealRank(minutes, "偷榜"), stealRankTime));
                    Log.record("捐蛋排位🥚已设置偷榜[定时任务]将在 " + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(stealRankTime) + " 执行");
                }
            }
            // 先解析赛季捐蛋数
            int seasonDonationNum = 0;
            if (jo.has("seasonDonationProgress")) {
                JSONObject seasonDonationProgress = jo.getJSONObject("seasonDonationProgress");
                seasonDonationNum = seasonDonationProgress.optInt("seasonDonationNum");
            }
            
            // 复用前面已获取的 userDonationRankList，解析当前用户的数据
            if (userDonationRankList != null && userDonationRankList.length() > 0) {
                for (int i = 0; i < userDonationRankList.length(); i++) {
                    JSONObject userDonationRank = userDonationRankList.optJSONObject(i);
                    if (userDonationRank != null) {
                        String userId = userDonationRank.optString("userId");
                        if (CurrentUserId.equals(userId)) {
                            String nickName = userDonationRank.optString("nickName");
                            int totalStarNum = userDonationRank.optInt("totalStarNum");
                            String levelName = userDonationRank.optString("levelName");
                            int donationTotal = userDonationRank.optInt("donationTotal");
                            Log.record("捐蛋排位🥚[" + nickName + "]星星数" + totalStarNum + "等级[" + levelName + "]累计捐蛋" + donationTotal + "赛季捐蛋" + seasonDonationNum);
                            break;
                        }
                    }
                }
            }
            
            // 检查领取赛季进度奖励
            if (jo.has("seasonDonationProgress")) {
                JSONObject seasonDonationProgress = jo.getJSONObject("seasonDonationProgress");
                JSONArray nodes = seasonDonationProgress.optJSONArray("nodes");
                boolean hasUnreceived = false;
                if (nodes != null) {
                    for (int i = 0; i < nodes.length(); i++) {
                        JSONObject node = nodes.optJSONObject(i);
                        if (node != null && "UNRECEIVED".equals(node.optString("status"))) {
                            hasUnreceived = true;
                            break;
                        }
                    }
                }
                if (hasUnreceived) {
                    receiveDonationCompetitionProgressAward();
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "competition err:");
            Log.printStackTrace(TAG, t);
        }
    }

    //偷榜捐蛋
    private void stealRank(int stealRankMinutes, String worKType) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.enterDonationCompetitionRank());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            if (jo.has("exitDonationCompetition")) {
                boolean exitDonationCompetition = jo.optBoolean("exitDonationCompetition");
                //开启排位赛
                if (exitDonationCompetition) {
                    JSONObject joOpen = new JSONObject(AntFarmRpcCall.setDonationCompetitionConf("OPEN"));
                    if (MessageUtil.checkMemo(TAG, joOpen)) {
                        String memo = joOpen.optString("memo");
                        Log.farm("捐蛋排位🥚开启：" + memo);
                    }
                } else {
                    Log.record("捐蛋排位🥚已在排位赛中，跳过加入操作");
                }
            }
            int myDonation = 0;
            int myRank = 0;
            int myStar = 0;
            int dailyLimit = competitionDailyLimit.getValue();
            String CurrentUserId = UserIdMap.getCurrentUid();
            int firstDonation = 0;
            int secondDonation = 0;
            jo = new JSONObject(AntFarmRpcCall.enterDonationCompetitionRank());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            if (!jo.has("donationRankHomeInfo")) {
                Log.record("捐蛋排位🥚未查询到捐赠排行信息");
                return;
            }
            JSONObject donationRankHomeInfo = jo.getJSONObject("donationRankHomeInfo");
            if (!donationRankHomeInfo.has("userDonationRankList")) {
                Log.record("捐蛋排位🥚未查询到捐赠排行信息");
                return;
            }
            JSONArray userDonationRankList = donationRankHomeInfo.optJSONArray("userDonationRankList");
            if (userDonationRankList == null || userDonationRankList.length() == 0) {
                Log.record("捐蛋排位🥚奖励列表为空");
                return;
            }
            for (int i = 0; i < userDonationRankList.length(); i++) {
                JSONObject userDonationRank = userDonationRankList.getJSONObject(i);
                String userId = userDonationRank.optString("userId");
                int rewardStarNum = userDonationRank.optInt("rewardStarNum");
                int donationNum = userDonationRank.optInt("donationNum");
                int rankOrder = userDonationRank.optInt("rankOrder");
                if (rankOrder == 1) {
                    firstDonation = donationNum;
                }
                if (rankOrder == 2) {
                    secondDonation = donationNum;
                }
                if (CurrentUserId.equals(userId)) {
                    myDonation = donationNum;
                    myRank = rankOrder;
                    myStar = rewardStarNum;
                }
            }
            int leadEggs = competitionLeadEggs.getValue();
            //第1名时判断领先第2名捐蛋数
            if (myRank == 1) {
                if (myDonation - secondDonation >= leadEggs) {
                    Log.record("捐蛋排位🥚" + worKType + "时间段(提前" + stealRankMinutes + "分钟)目前排名" + myRank + "捐蛋" + myDonation + ",第2名捐蛋" + secondDonation + ",满足领先" + leadEggs + "条件,不用捐蛋");
                } else {
                    int DonationEggNum = secondDonation + leadEggs - myDonation;
                    if (dailyLimit == 0) {
                        Log.record("捐蛋排位🥚" + worKType + "时间段(提前" + stealRankMinutes + "分钟)目前排名" + myRank + "捐蛋" + myDonation + ",第2名捐蛋" + secondDonation + ",满足领先" + leadEggs + "条件且无捐蛋上限尝试再捐蛋" + DonationEggNum);
                        competitionDonation("激进模式", DonationEggNum);
                    } else if (DonationEggNum + myDonation > dailyLimit) {
                        Log.record("捐蛋排位🥚" + worKType + "时间段(提前" + stealRankMinutes + "分钟)目前排名" + myRank + "捐蛋" + myDonation + ",第2名捐蛋" + secondDonation + ",满足领先" + leadEggs + "需再捐蛋" + DonationEggNum + "捐蛋限制" + dailyLimit + "(停止捐蛋)");
                    } else {
                        Log.record("捐蛋排位🥚" + worKType + "时间段(提前" + stealRankMinutes + "分钟)目前排名" + myRank + "捐蛋" + myDonation + ",第2名捐蛋" + secondDonation + ",满足领先" + leadEggs + "条件尝试再捐蛋" + DonationEggNum);
                        competitionDonation("激进模式", DonationEggNum);
                    }
                }
            }
            //非第1名判断领先目前第1名捐蛋数
            else {
                int DonationEggNum = firstDonation + leadEggs - myDonation;
                if (dailyLimit == 0) {
                    Log.record("捐蛋排位🥚" + worKType + "时间段(提前" + stealRankMinutes + "分钟)目前排名" + myRank + "捐蛋" + myDonation + ",第1名捐蛋" + firstDonation + ",满足领先" + leadEggs + "条件且无捐蛋上限尝试再捐蛋" + DonationEggNum);
                    competitionDonation("激进模式", DonationEggNum);
                } else if (DonationEggNum + myDonation > dailyLimit) {
                    Log.record("捐蛋排位🥚" + worKType + "时间段(提前" + stealRankMinutes + "分钟)目前排名" + myRank + "捐蛋" + myDonation + ",第1名捐蛋" + firstDonation + ",满足领先" + leadEggs + "需再捐蛋" + DonationEggNum + "捐蛋限制" + dailyLimit + "(停止捐蛋)");
                } else {
                    Log.record("捐蛋排位🥚" + worKType + "时间段(提前" + stealRankMinutes + "分钟)目前排名" + myRank + "捐蛋" + myDonation + ",第1名捐蛋" + firstDonation + ",满足领先" + leadEggs + "条件还需尝试捐蛋" + DonationEggNum);
                    competitionDonation("激进模式", DonationEggNum);
                }
            }

        } catch (Throwable t) {
            Log.i(TAG, "stealRank err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private boolean isStealRankTime(int stealMinutes) {
        if (stealMinutes <= 0) {
            return false;
        }
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
        int minute = calendar.get(java.util.Calendar.MINUTE);
        int totalMinutes = hour * 60 + minute;
        int targetTime = 20 * 60;
        int startTime = targetTime - stealMinutes;
        return totalMinutes >= startTime && totalMinutes < targetTime;
    }

    private void competitionDonation(String competitionType, int DonationEggNum) {

        if (DonationEggNum > 0) {
            int currentEgg = (int) harvestBenevolenceScore;
            if (currentEgg <= 0) {
                Log.record("捐蛋排位🥚当前无蛋可捐");
            } else {
                if (DonationEggNum > harvestBenevolenceScore) {
                    Log.record("捐蛋排位🥚满足" + competitionType + "需捐蛋" + DonationEggNum + "当前可捐" + harvestBenevolenceScore + "放弃捐蛋");
                } else {
                    Log.farm("捐蛋排位🥚" + competitionType + "开始捐蛋" + DonationEggNum + "枚");
                    donation(DonationEggNum);
                }
            }
        }
    }

    private void receiveReward() {
        try {
            //领取奖励
            JSONObject jo = new JSONObject(AntFarmRpcCall.enterCompetitionAwardPage());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONArray levelAwardInfoList = jo.optJSONArray("levelAwardInfoList");
            if (levelAwardInfoList == null || levelAwardInfoList.length() == 0) {
                Log.record("捐蛋排位🥚奖励列表为空");
                return;
            }
            for (int i = 0; i < levelAwardInfoList.length(); i++) {
                JSONObject award = levelAwardInfoList.getJSONObject(i);
                String status = award.optString("status");
                if (!status.equals("unreceived")) {
                    continue;
                }
                String rightsId = award.optString("rightsId");
                JSONObject result = new JSONObject(AntFarmRpcCall.receiveDonationLevelReward(rightsId));
                if (MessageUtil.checkMemo(TAG, result)) {
                    JSONArray levelAwardList = result.optJSONArray("levelAwardList");
                    if (levelAwardList == null || levelAwardList.length() == 0) {
                        Log.record("捐蛋排位🥚奖励为空");
                        continue;
                    }
                    String levelName = result.optString("levelName");
                    for (int j = 0; j < levelAwardList.length(); j++) {
                        JSONObject levelAward = levelAwardList.getJSONObject(j);
                        String awardName = levelAward.optString("awardName");
                        int awardNum = levelAward.optInt("awardNum");
                        Log.farm("捐蛋排位🥚领取" + levelName + "段位奖励" + awardNum + awardName);
                    }
                }
                TimeUtil.sleep(500);
            }
        } catch (Throwable t) {
            Log.i(TAG, "receiveReward err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void receiveDonationCompetitionProgressAward() {
        try {
            //领取捐蛋对应星星数进度奖励
            JSONObject jo = new JSONObject(AntFarmRpcCall.receiveDonationCompetitionProgressAward());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            if (jo.has("totalAddStarNum")) {
                int totalAddStarNum = jo.optInt("totalAddStarNum");
                Log.farm("捐蛋排位🥚累计领取进度奖励星星" + totalAddStarNum);
            }
            
            // 提取 userStarNum 变化
            JSONObject beforeLevelInfo = jo.optJSONObject("beforeLevelInfo");
            JSONObject afterLevelInfo = jo.optJSONObject("afterLevelInfo");
            if (beforeLevelInfo != null && afterLevelInfo != null) {
                int beforeStarNum = beforeLevelInfo.optInt("userStarNum");
                int afterStarNum = afterLevelInfo.optInt("userStarNum");
                String beforeLevelName = beforeLevelInfo.optString("levelName");
                String afterLevelName = afterLevelInfo.optString("levelName");
                int starChange = afterStarNum - beforeStarNum;
                Log.farm("捐蛋排位🥚排位情况["+beforeLevelName+"]("+beforeStarNum + ")→["+afterLevelName+"](" + afterStarNum + ")(+"+starChange+")");
            }
        } catch (Throwable t) {
            Log.i(TAG, "receiveDonationCompetitionProgressAward err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void PreviousCompetitionInfo() {

        try {
            //查询上期比赛情况
            //JSONObject joPrevious = new JSONObject(AntFarmRpcCall.queryCompetitionEntranceInfo());
            JSONObject joPrevious = new JSONObject(AntFarmRpcCall.enterDonationCompetitionRank());
            if (MessageUtil.checkMemo(TAG, joPrevious)) {
                JSONObject previousRoundSettleAwardInfo = joPrevious.optJSONObject("previousRoundSettleAwardInfo");
                if (previousRoundSettleAwardInfo == null) {
                    Log.record("捐蛋排位🥚无法获取上期排名");
                } else {
                    String levelName = previousRoundSettleAwardInfo.optString("levelName");
                    int previousRankOrder = previousRoundSettleAwardInfo.optInt("rankOrder", 0);
                    int previousRewardStarNum = previousRoundSettleAwardInfo.optInt("rewardStarNum", 0);
                    Log.record("捐蛋排位🥚上期排名" + previousRankOrder + "奖励星星" + previousRewardStarNum + "段位等级[" + levelName + "]");
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "PreviousCompetitionInfo err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private int getProjectDonationNum(String projectId) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.getProjectInfo(projectId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return 0;
            }
            return jo.optInt("userProjectDonationNum");
        } catch (Throwable t) {
            Log.i(TAG, "getProjectDonationNum err:");
            Log.printStackTrace(TAG, t);
        }
        return 0;
    }

    private Boolean canDonationToday() {
        if (Status.hasFlagToday("farm::donation")) {
            return false;
        }
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.getCharityAccount(ownerUserId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            JSONArray charityRecords = jo.getJSONArray("charityRecords");
            if (charityRecords.length() == 0) {
                return true;
            }
            jo = charityRecords.getJSONObject(0);
            long charityTime = jo.optLong("charityTime", System.currentTimeMillis());
            if (TimeUtil.isLessThanNowOfDays(charityTime)) {
                return true;
            }
            Status.flagToday("farm::donation");
        } catch (Throwable t) {
            Log.i(TAG, "canDonationToday err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private void recordFarmGame(GameType gameType) {
        try {
            do {
                try {
                    JSONObject jo = new JSONObject(AntFarmRpcCall.initFarmGame(gameType.name()));
                    if (!MessageUtil.checkMemo(TAG, jo)) {
                        return;
                    }
                    if (jo.getJSONObject("gameAward").getBoolean("level3Get")) {
                        return;
                    }
                    if (jo.optInt("remainingGameCount", 1) == 0) {
                        return;
                    }
                    jo = new JSONObject(AntFarmRpcCall.recordFarmGame(gameType.name()));
                    if (!MessageUtil.checkMemo(TAG, jo)) {
                        return;
                    }
                    JSONArray awardInfos = jo.getJSONArray("awardInfos");
                    StringBuilder award = new StringBuilder();
                    for (int i = 0; i < awardInfos.length(); i++) {
                        JSONObject awardInfo = awardInfos.getJSONObject(i);
                        award.append(awardInfo.getString("awardName")).append("*").append(awardInfo.getInt("awardCount"));
                    }
                    if (jo.has("receiveFoodCount")) {
                        award.append(";肥料*").append(jo.getString("receiveFoodCount"));
                    }
                    Log.farm("小鸡乐园🎮游玩[" + gameType.gameName() + "]#获得[" + award + "]");
                    if (jo.optInt("remainingGameCount", 0) > 0) {
                        continue;
                    }
                    break;
                } finally {
                    TimeUtil.sleep(2000);
                }
            } while (true);
        } catch (Throwable t) {
            Log.i(TAG, "recordFarmGame err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void listFarmTask(TaskStatus Mode) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.listFarmTask());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONObject signList = jo.getJSONObject("signList");
            if (sign(signList)) {
                TimeUtil.sleep(1000);
            }
            JSONArray ja = jo.getJSONArray("farmTaskList");
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                TaskStatus taskStatus = TaskStatus.valueOf(jo.getString("taskStatus"));
                String title = jo.getString("title");
                //黑名单任务跳过
                if (AntFarmDoFarmTaskList.getValue().contains(title)) {
                    if (taskStatus == TaskStatus.FINISHED) {
                        receiveFarmTaskAward(jo);
                    }
                    continue;
                }
                if (taskStatus == TaskStatus.RECEIVED || taskStatus != Mode) {
                    continue;
                }
                if (taskStatus == TaskStatus.TODO && !doFarmTask(jo)) {
                    continue;
                }
                if (taskStatus == TaskStatus.FINISHED && !receiveFarmTaskAward(jo)) {
                    continue;
                }
                TimeUtil.sleep(1000);
            }
        } catch (Throwable t) {
            Log.i(TAG, "listFarmTask err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private Boolean sign(JSONObject SignList) {
        if (Status.hasFlagToday("farm::sign")) {
            return false;
        }
        boolean signed = false;
        try {
            String currentSignKey = SignList.getString("currentSignKey");
            JSONArray signList = SignList.getJSONArray("signList");
            for (int i = 0; i < signList.length(); i++) {
                JSONObject jo = signList.getJSONObject(i);
                if (!currentSignKey.equals(jo.getString("signKey"))) {
                    continue;
                }
                if (jo.optBoolean("signed")) {
                    Log.record("庄园今日已签到");
                    signed = true;
                    return false;
                }
                int awardCount = jo.getInt("awardCount");
                if (awardCount + foodStock > foodStockLimit) {
                    return false;
                }
                int currentContinuousCount = jo.getInt("currentContinuousCount");
                jo = new JSONObject(AntFarmRpcCall.sign());
                if (MessageUtil.checkMemo(TAG, jo)) {
                    foodStock = jo.getInt("foodStock");
                    Log.farm("饲料任务📅签到[坚持" + currentContinuousCount + "天]#获得[" + awardCount + "g饲料]");
                    signed = true;
                    return true;
                }
                return false;
            }
        } catch (Throwable t) {
            Log.i(TAG, "sign err:");
            Log.printStackTrace(TAG, t);
        } finally {
            if (signed) {
                Status.flagToday("farm::sign");
            }
        }
        return false;
    }

    private Boolean doVideoTask() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.queryTabVideoUrl());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            String videoUrl = jo.getString("videoUrl");
            String contentId = videoUrl.substring(videoUrl.indexOf("&contentId=") + 1, videoUrl.indexOf("&refer"));
            jo = new JSONObject(AntFarmRpcCall.videoDeliverModule(contentId));
            if (jo.optBoolean("success")) {
                TimeUtil.sleep(15100);
                jo = new JSONObject(AntFarmRpcCall.videoTrigger(contentId));
                if (jo.optBoolean("success")) {
                    return true;
                } else {
                    Log.record(jo.getString("resultMsg"));
                    Log.i(jo.toString());
                }
            } else {
                Log.record(jo.getString("resultMsg"));
                Log.i(jo.toString());
            }
        } catch (Throwable t) {
            Log.i(TAG, "doVideoTask err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private Boolean doAnswerTask() {
        try {
            JSONObject jo = new JSONObject(DadaDailyRpcCall.home("100"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONObject question = jo.getJSONObject("question");
            long questionId = question.getLong("questionId");
            JSONArray labels = question.getJSONArray("label");
            String answer = AnswerAI.getAnswer(question.getString("title"), JsonUtil.jsonArrayToList(labels));
            if (answer == null || answer.isEmpty()) {
                answer = labels.getString(0);
            }
            jo = new JSONObject(DadaDailyRpcCall.submit("100", answer, questionId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONObject extInfo = jo.getJSONObject("extInfo");
            boolean correct = jo.getBoolean("correct");
            String award = extInfo.getString("award");
            Log.record("庄园答题📝回答" + (correct ? "正确" : "错误") + "#获得[" + award + "g饲料]");
            if (jo.has("operationConfigList")) {
                JSONArray operationConfigList = jo.getJSONArray("operationConfigList");
                savePreviewQuestion(operationConfigList);
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "doAnswerTask err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private void savePreviewQuestion(JSONArray operationConfigList) {
        try {
            for (int i = 0; i < operationConfigList.length(); i++) {
                JSONObject jo = operationConfigList.getJSONObject(i);
                String type = jo.getString("type");
                if (Objects.equals(type, "PREVIEW_QUESTION")) {
                    String question = jo.getString("title");
                    JSONArray ja = new JSONArray(jo.getString("actionTitle"));
                    for (int j = 0; j < ja.length(); j++) {
                        jo = ja.getJSONObject(j);
                        if (jo.getBoolean("correct")) {
                            TokenConfig.saveAnswer(question, jo.getString("title"));
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "saveAnswerList err:");
            Log.printStackTrace(TAG, t);
        }
    }

    // 在 doFarmTask 方法中，修复 libraryDoFarmTask 的调用
    private Boolean doFarmTask(JSONObject task) {
        boolean isDoTask = false;
        try {
            String title = task.getString("title");
            String bizKey = task.getString("bizKey");
            if (bizKey.contains("HEART_DONAT") || bizKey.equals("BAIDUJS_202512") || bizKey.equals("BABAFARM_TB")) {
                return false;
            }
            if (Objects.equals(title, "庄园小视频")) {
                isDoTask = doVideoTask();
            } else if (Objects.equals(title, "庄园小课堂")) {
                isDoTask = doAnswerTask();
            } else {
                // 检查library是否可用
                try {
                    isDoTask = LibraryUtil.doFarmTask(task);
                } catch (UnsatisfiedLinkError e) {
                    Log.record("Native库不可用，跳过任务: " + title);
                    isDoTask = false;
                }
            }

            if (isDoTask) {
                Log.farm("饲料任务🧾完成[" + title + "]");
            } else {
                Log.record("任务执行失败或跳过: " + title);
            }
        } catch (Throwable t) {
            Log.i(TAG, "doFarmTask err:");
            Log.printStackTrace(TAG, t);
        }
        return isDoTask;
    }

    private Boolean receiveFarmTaskAward(JSONObject task) {
        try {
            String taskId = task.getString("taskId");
            String awardType = task.optString("awardType", "");
            int awardCount = task.optInt("awardCount", 0);
            if (Objects.equals(awardType, "ALLPURPOSE")) {
                if (awardCount + foodStock > foodStockLimit) {
                    unReceiveTaskAward++;
                    // Log.record("领取" + awardCount + "克饲料后将超过[" + foodStockLimit + "克]上限，终止领取");
                    return false;
                }
            }
            JSONObject jo = new JSONObject(AntFarmRpcCall.receiveFarmTaskAward(taskId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            if (awardType.equals("ALLPURPOSE")) {
                add2FoodStock(awardCount);
                String title = task.optString("title", "");
                Log.farm("饲料领取🎖️任务[" + title + "]奖励#获得[" + awardCount + "g]");
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "receiveFarmTaskAward err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private void checkUnReceiveTaskAward() {
        if (unReceiveTaskAward > 0) {
            Log.record("还有待领取的饲料");
            unReceiveTaskAward = 0;
            listFarmTask(TaskStatus.FINISHED);
        }
    }

    private void feedAnimal(String farmId) {
        try {
            syncAnimalStatus(ownerFarmId);
            if (foodStock < 180) {
                Log.record("剩余饲料不足以投喂小鸡");
                return;
            }
            JSONObject jo = new JSONObject(AntFarmRpcCall.feedAnimal(farmId));
            if (MessageUtil.checkMemo(TAG, jo)) {
                int feedFood = foodStock - jo.getInt("foodStock");
                add2FoodStock(-feedFood);
                Log.farm("投喂小鸡🥣消耗[" + feedFood + "g]#剩余[" + foodStock + "g饲料]");
                if (useAccelerateTool.getValue()) {
                    TimeUtil.sleep(1000);
                    useAccelerateTool();
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "feedAnimal err:");
            Log.printStackTrace(TAG, t);
        } finally {
            long updateTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
            String taskId = "UPDATE|FA|" + farmId;
            addChildTask(new ChildModelTask(taskId, "UPDATE", this::autoFeedAnimal, updateTime));
        }
    }

    private void listFarmTool() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.listFarmTool());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONArray jaToolList = jo.getJSONArray("toolList");
            farmTools = new FarmTool[jaToolList.length()];
            for (int i = 0; i < jaToolList.length(); i++) {
                jo = jaToolList.getJSONObject(i);
                farmTools[i] = new FarmTool();
                farmTools[i].toolId = jo.optString("toolId", "");
                farmTools[i].toolType = ToolType.valueOf(jo.getString("toolType"));
                farmTools[i].toolCount = jo.getInt("toolCount");
                farmTools[i].toolHoldLimit = jo.optInt("toolHoldLimit", 20);
            }
        } catch (Throwable t) {
            Log.i(TAG, "listFarmTool err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void useAccelerateTool() {
        if (!Status.canUseAccelerateToolToday()) {
            return;
        }
        syncAnimalStatus(ownerFarmId);
        if ((!useAccelerateToolOptions.getValue().contains("useAccelerateToolContinue") && AnimalBuff.ACCELERATING.name().equals(ownerAnimal.animalBuff)) || (useAccelerateToolOptions.getValue().contains("useAccelerateToolWhenMaxEmotion") && finalScore != 100)) {
            return;
        }
        double consumeSpeed = 0d;
        double foodHaveEatten = 0d;
        long nowTime = System.currentTimeMillis() / 1000;
        for (Animal animal : animals) {
            if (animal.masterFarmId.equals(ownerFarmId)) {
                consumeSpeed = animal.consumeSpeed;
            }
            foodHaveEatten += animal.consumeSpeed * (nowTime - animal.startEatTime / 1000);
        }
        // consumeSpeed: g/s
        // AccelerateTool: -1h = -60m = -3600s
        while (foodInTrough - foodHaveEatten >= consumeSpeed * 3600 && useFarmTool(ownerFarmId, ToolType.ACCELERATETOOL)) {
            TimeUtil.sleep(1000);
            foodHaveEatten += consumeSpeed * 3600;
            Status.useAccelerateToolToday();
            if (!Status.canUseAccelerateToolToday()) {
                break;
            }
            if (!useAccelerateToolOptions.getValue().contains("useAccelerateToolContinue")) {
                break;
            }
        }
    }

    private Boolean useFarmTool(String targetFarmId, ToolType toolType) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.listFarmTool());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            JSONArray jaToolList = jo.getJSONArray("toolList");
            for (int i = 0; i < jaToolList.length(); i++) {
                jo = jaToolList.getJSONObject(i);
                if (!toolType.name().equals(jo.getString("toolType"))) {
                    continue;
                }
                int toolCount = jo.optInt("toolCount");
                if (toolCount == 0) {
                    return false;
                }
                String toolId = jo.optString("toolId");
                jo = new JSONObject(AntFarmRpcCall.useFarmTool(targetFarmId, toolId, toolType.name()));
                if (MessageUtil.checkMemo(TAG, jo)) {
                    Log.farm("使用道具🎭[" + toolType.nickName() + "]#剩余" + (toolCount - 1) + "张");
                    return true;
                } else if (Objects.equals("3D16", jo.getString("resultCode"))) {
                    Status.flagToday("farm::useFarmToolLimit::" + toolType);
                }
                break;
            }
        } catch (Throwable t) {
            Log.i(TAG, "useFarmTool err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private void feedFriend() {
        try {
            Map<String, Integer> feedFriendAnimalMap = feedFriendAnimalList.getValue();
            for (Map.Entry<String, Integer> entry : feedFriendAnimalMap.entrySet()) {
                String userId = entry.getKey();
                if (userId.equals(UserIdMap.getCurrentUid())) {
                    continue;
                }
                if (!Status.canFeedFriendToday(userId, entry.getValue())) {
                    continue;
                }
                JSONObject jo = new JSONObject(AntFarmRpcCall.enterFarm("", userId));
                if (!MessageUtil.checkMemo(TAG, jo)) {
                    continue;
                }
                jo = jo.getJSONObject("farmVO").getJSONObject("subFarmVO");
                String friendFarmId = jo.getString("farmId");
                int foodInTrough = jo.optInt("foodInTrough", 0);
                
                // 食槽为空时帮喂
                if (foodInTrough == 0) {
                    JSONArray jaAnimals = jo.getJSONArray("animals");
                    for (int j = 0; j < jaAnimals.length(); j++) {
                        JSONObject animal = jaAnimals.getJSONObject(j);
                        String masterFarmId = animal.getString("masterFarmId");
                        
                        // 只处理好友自己的小鸡
                        if (masterFarmId.equals(friendFarmId)) {
                            // 检查小鸡是否太小
                            if (animal.optBoolean("littleChick", false)) {
                                Log.record("跳过帮喂：好友的小鸡太小");
                                break;
                            }
                            
                            JSONObject animalStatusVO = animal.getJSONObject("animalStatusVO");
                            String animalInteractStatus = animalStatusVO.getString("animalInteractStatus");
                            String animalFeedStatus = animalStatusVO.getString("animalFeedStatus");
                            
                            // 好友自己的小鸡在家且饥饿 → 帮喂
                            if (AnimalInteractStatus.HOME.name().equals(animalInteractStatus) 
                                && AnimalFeedStatus.HUNGRY.name().equals(animalFeedStatus)) {
                                feedFriendAnimal(friendFarmId);
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "feedFriend err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void feedFriendAnimal(String friendFarmId) {
        try {
            String userId = AntFarmRpcCall.farmId2UserId(friendFarmId);
            String maskName = UserIdMap.getMaskName(userId);
            Log.record("[" + maskName + "]的小鸡在挨饿");
            if (foodStock < 180) {
                Log.record("喂鸡饲料不足");
                checkUnReceiveTaskAward();
                if (foodStock < 180) {
                    return;
                }
            }
            String groupId = null;
            if (family.getValue()) {
                groupId = getFamilyGroupId(userId);
                if (StringUtil.isEmpty(groupId) || !Objects.equals(ownerGroupId, groupId)) {
                    groupId = null;
                }
            }
            if (feedFriendAnimal(friendFarmId, groupId)) {
                String s = StringUtil.isEmpty(groupId) ? "帮喂小鸡🥣帮喂好友" : "亲密家庭🏠帮喂成员";
                s = s + "[" + maskName + "]" + "的小鸡#剩余[" + foodStock + "g饲料]";
                Log.farm(s);
                Status.feedFriendToday(AntFarmRpcCall.farmId2UserId(friendFarmId));
            }
        } catch (Throwable t) {
            Log.i(TAG, "feedFriendAnimal err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private Boolean feedFriendAnimal(String friendFarmId, String groupId) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.feedFriendAnimal(friendFarmId, groupId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                if (Objects.equals("391", jo.optString("resultCode"))) {
                    Status.flagToday("farm::feedFriendAnimalLimit");
                }
                return false;
            }
            int feedFood = foodStock - jo.getInt("foodStock");
            if (feedFood > 0) {
                add2FoodStock(-feedFood);
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "feedFriendAnimal err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private void notifyFriend() {
        if (foodStock >= foodStockLimit) {
            return;
        }
        try {
            boolean hasNext = false;
            int pageStartSum = 0;
            String s;
            JSONObject jo;
            do {
                s = AntFarmRpcCall.rankingList(pageStartSum);
                jo = new JSONObject(s);
                if (!MessageUtil.checkMemo(TAG, jo)) {
                    break;
                }
                hasNext = jo.getBoolean("hasNext");
                JSONArray jaRankingList = jo.getJSONArray("rankingList");
                pageStartSum += jaRankingList.length();
                for (int i = 0; i < jaRankingList.length(); i++) {
                    jo = jaRankingList.getJSONObject(i);
                    String userId = jo.getString("userId");
                    String userName = UserIdMap.getMaskName(userId);
                    boolean isNotifyFriend = notifyFriendList.getValue().contains(userId);
                    if (notifyFriendType.getValue() != NotifyFriendType.NOTIFY) {
                        isNotifyFriend = !isNotifyFriend;
                    }
                    if (!isNotifyFriend || userId.equals(UserIdMap.getCurrentUid())) {
                        continue;
                    }
                    boolean starve = jo.has("actionType") && "starve_action".equals(jo.getString("actionType"));
                    if (jo.getBoolean("stealingAnimal") && !starve) {
                        jo = new JSONObject(AntFarmRpcCall.enterFarm("", userId));
                        if (!MessageUtil.checkMemo(TAG, jo)) {
                            continue;
                        }
                        jo = jo.getJSONObject("farmVO").getJSONObject("subFarmVO");
                        String friendFarmId = jo.getString("farmId");
                        JSONArray jaAnimals = jo.getJSONArray("animals");
                        for (int j = 0; j < jaAnimals.length(); j++) {
                            jo = jaAnimals.getJSONObject(j);
                            String animalId = jo.getString("animalId");
                            String masterFarmId = jo.getString("masterFarmId");
                            if (!masterFarmId.equals(friendFarmId) && !masterFarmId.equals(ownerFarmId)) {
                                jo = jo.getJSONObject("animalStatusVO");
                                if (notifyFriend(jo, friendFarmId, animalId, userName)) {
                                    break;
                                }
                            }
                        }
                    }
                }
            } while (hasNext);
            Log.record("饲料剩余[" + foodStock + "g]");
        } catch (Throwable t) {
            Log.i(TAG, "notifyFriend err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private Boolean notifyFriend(JSONObject joAnimalStatusVO, String friendFarmId, String animalId, String user) {
        try {
            if (AnimalInteractStatus.STEALING.name().equals(joAnimalStatusVO.getString("animalInteractStatus")) && AnimalFeedStatus.EATING.name().equals(joAnimalStatusVO.getString("animalFeedStatus"))) {
                JSONObject jo = new JSONObject(AntFarmRpcCall.notifyFriend(animalId, friendFarmId));
                if (!MessageUtil.checkMemo(TAG, jo)) {
                    return false;
                }
                int rewardCount = (int) jo.getDouble("rewardCount");
                if (jo.getBoolean("refreshFoodStock")) {
                    foodStock = (int) jo.getDouble("finalFoodStock");
                } else {
                    add2FoodStock(rewardCount);
                }
                Log.farm("通知赶鸡📧提醒[" + user + "]被偷吃#获得[" + rewardCount + "g饲料]");
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "notifyFriend err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private void parseSyncAnimalStatusResponse(String resp) {
        try {
            JSONObject jo = new JSONObject(resp);
            if (!jo.has("subFarmVO")) {
                return;
            }
            if (jo.has("emotionInfo")) {
                finalScore = jo.getJSONObject("emotionInfo").getDouble("finalScore");
            }
            JSONObject subFarmVO = jo.getJSONObject("subFarmVO");
            if (subFarmVO.has("foodStock")) {
                foodStock = subFarmVO.getInt("foodStock");
            }
            if (subFarmVO.has("foodInTrough")) {
                foodInTrough = subFarmVO.getInt("foodInTrough");
            }
            if (subFarmVO.has("manureVO")) {
                JSONArray manurePotList = subFarmVO.getJSONObject("manureVO").getJSONArray("manurePotList");
                for (int i = 0; i < manurePotList.length(); i++) {
                    JSONObject manurePot = manurePotList.getJSONObject(i);
                    if (manurePot.getInt("manurePotNum") >= 100) {
                        JSONObject joManurePot = new JSONObject(AntFarmRpcCall.collectManurePot(manurePot.getString("manurePotNO")));
                        if (joManurePot.optBoolean("success")) {
                            int collectManurePotNum = joManurePot.getInt("collectManurePotNum");
                            Log.farm("打扫鸡屎🧹获得[" + collectManurePotNum + "g肥料]");
                        }
                    }
                }
            }
            ownerFarmId = subFarmVO.getString("farmId");
            JSONObject farmProduce = subFarmVO.getJSONObject("farmProduce");
            benevolenceScore = farmProduce.getDouble("benevolenceScore");
            if (subFarmVO.has("rewardList")) {
                JSONArray jaRewardList = subFarmVO.getJSONArray("rewardList");
                if (jaRewardList.length() > 0) {
                    rewardList = new RewardFriend[jaRewardList.length()];
                    for (int i = 0; i < rewardList.length; i++) {
                        JSONObject joRewardList = jaRewardList.getJSONObject(i);
                        if (rewardList[i] == null) {
                            rewardList[i] = new RewardFriend();
                        }
                        rewardList[i].consistencyKey = joRewardList.getString("consistencyKey");
                        rewardList[i].friendId = joRewardList.getString("friendId");
                        rewardList[i].time = joRewardList.getString("time");
                    }
                }
            }
            JSONArray jaAnimals = subFarmVO.getJSONArray("animals");
            animals = new Animal[jaAnimals.length()];
            for (int i = 0; i < animals.length; i++) {
                Animal animal = new Animal();
                JSONObject animalJsonObject = jaAnimals.getJSONObject(i);
                animal.animalId = animalJsonObject.getString("animalId");
                animal.currentFarmId = animalJsonObject.getString("currentFarmId");
                animal.masterFarmId = animalJsonObject.getString("masterFarmId");
                animal.animalBuff = animalJsonObject.getString("animalBuff");
                animal.subAnimalType = animalJsonObject.getString("subAnimalType");
                animal.currentFarmMasterUserId = animalJsonObject.getString("currentFarmMasterUserId");
                animal.locationType = animalJsonObject.optString("locationType", "");
                JSONObject animalStatusVO = animalJsonObject.getJSONObject("animalStatusVO");
                animal.animalFeedStatus = animalStatusVO.getString("animalFeedStatus");
                animal.animalInteractStatus = animalStatusVO.getString("animalInteractStatus");
                animal.animalInteractStatus = animalStatusVO.getString("animalInteractStatus");
                animal.startEatTime = animalJsonObject.optLong("startEatTime");
                animal.beHiredEndTime = animalJsonObject.optLong("beHiredEndTime");
                animal.consumeSpeed = animalJsonObject.optDouble("consumeSpeed");
                animal.foodHaveEatten = animalJsonObject.optDouble("foodHaveEatten");
                if (animal.masterFarmId.equals(ownerFarmId)) {
                    ownerAnimal = animal;
                }
                animals[i] = animal;
            }
        } catch (Throwable t) {
            Log.i(TAG, "parseSyncAnimalStatusResponse err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void add2FoodStock(int i) {
        foodStock += i;
        if (foodStock > foodStockLimit) {
            foodStock = foodStockLimit;
        }
        if (foodStock < 0) {
            foodStock = 0;
        }
    }

    private void collectDailyFoodMaterial(String userId) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.enterKitchen(userId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            boolean canCollectDailyFoodMaterial = jo.getBoolean("canCollectDailyFoodMaterial");
            int dailyFoodMaterialAmount = jo.getInt("dailyFoodMaterialAmount");
            int garbageAmount = jo.optInt("garbageAmount", 0);
            if (jo.has("orchardFoodMaterialStatus")) {
                JSONObject orchardFoodMaterialStatus = jo.getJSONObject("orchardFoodMaterialStatus");
                if ("FINISHED".equals(orchardFoodMaterialStatus.optString("foodStatus"))) {
                    jo = new JSONObject(AntFarmRpcCall.farmFoodMaterialCollect());
                    if ("100".equals(jo.getString("resultCode"))) {
                        Log.farm("小鸡厨房👨🏻‍🍳农场食材#领取[" + jo.getInt("foodMaterialAddCount") + "g食材]");
                    } else {
                        Log.i(TAG, jo.toString());
                    }
                }
            }
            if (canCollectDailyFoodMaterial) {
                jo = new JSONObject(AntFarmRpcCall.collectDailyFoodMaterial(dailyFoodMaterialAmount));
                if (MessageUtil.checkMemo(TAG, jo)) {
                    Log.farm("小鸡厨房👨🏻‍🍳今日食材#领取[" + dailyFoodMaterialAmount + "g食材]");
                }
            }
            if (garbageAmount > 0) {
                jo = new JSONObject(AntFarmRpcCall.collectKitchenGarbage());
                if (MessageUtil.checkMemo(TAG, jo)) {
                    Log.farm("小鸡厨房👨🏻‍🍳收集厨余#获得[" + jo.getInt("recievedKitchenGarbageAmount") + "g肥料]");
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "collectDailyFoodMaterial err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void collectDailyLimitedFoodMaterial() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.queryFoodMaterialPack());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            boolean canCollectDailyLimitedFoodMaterial = jo.getBoolean("canCollectDailyLimitedFoodMaterial");
            if (canCollectDailyLimitedFoodMaterial) {
                int dailyLimitedFoodMaterialAmount = jo.getInt("dailyLimitedFoodMaterialAmount");
                jo = new JSONObject(AntFarmRpcCall.collectDailyLimitedFoodMaterial(dailyLimitedFoodMaterialAmount));
                if (MessageUtil.checkMemo(TAG, jo)) {
                    Log.farm("小鸡厨房👨🏻‍🍳领取[爱心食材店食材]#" + dailyLimitedFoodMaterialAmount + "g");
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "collectDailyLimitedFoodMaterial err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void cook(String userId) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.enterKitchen(userId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            int cookTimesAllowed = jo.getInt("cookTimesAllowed");
            if (cookTimesAllowed > 0) {
                for (int i = 0; i < cookTimesAllowed; i++) {
                    jo = new JSONObject(AntFarmRpcCall.cook(userId));
                    if (MessageUtil.checkMemo(TAG, jo)) {
                        JSONObject cuisineVO = jo.getJSONObject("cuisineVO");
                        Log.farm("小鸡厨房👨🏻‍🍳制作[" + cuisineVO.getString("name") + "]");
                    }
                    TimeUtil.sleep(RandomUtil.delay());
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "cook err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private List<JSONObject> getSortedCuisineList(JSONArray cuisineList) {
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < cuisineList.length(); i++) {
            list.add(cuisineList.optJSONObject(i));
        }
        Collections.sort(list, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject jsonObject1, JSONObject jsonObject2) {
                int count1 = jsonObject1.optInt("count");
                int count2 = jsonObject2.optInt("count");
                return count2 - count1;
            }
        });
        return list;
    }

    private void useFarmFood(JSONArray cuisineList) {
        try {
            List<JSONObject> list = getSortedCuisineList(cuisineList);
            for (int i = 0; i < list.size(); i++) {
                if (!useFarmFood(list.get(i))) {
                    return;
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "useFarmFood err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private Boolean useFarmFood(JSONObject cuisine) {
        if (!Status.canUseSpecialFoodToday()) {
            return false;
        }
        try {
            String cookbookId = cuisine.getString("cookbookId");
            String cuisineId = cuisine.getString("cuisineId");
            String name = cuisine.getString("name");
            int count = cuisine.getInt("count");
            for (int j = 0; j < count; j++) {
                JSONObject jo = new JSONObject(AntFarmRpcCall.useFarmFood(cookbookId, cuisineId));
                if (!MessageUtil.checkMemo(TAG, jo)) {
                    return false;
                }
                double deltaProduce = jo.getJSONObject("foodEffect").getDouble("deltaProduce");
                Log.farm("使用美食🍱[" + name + "]#加速" + deltaProduce + "颗爱心鸡蛋");
                Status.useSpecialFoodToday();
                if (!Status.canUseSpecialFoodToday()) {
                    break;
                }
            }
            return true;
        } catch (Throwable t) {
            Log.i(TAG, "useFarmFood err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private void drawLotteryPlus(JSONObject lotteryPlusInfo) {
        try {
            if (!lotteryPlusInfo.has("userSevenDaysGiftsItem")) {
                return;
            }
            String itemId = lotteryPlusInfo.getString("itemId");
            JSONObject jo = lotteryPlusInfo.getJSONObject("userSevenDaysGiftsItem");
            JSONArray ja = jo.getJSONArray("userEverydayGiftItems");
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                if (jo.getString("itemId").equals(itemId)) {
                    if (!jo.getBoolean("received")) {
                        String singleDesc = jo.getString("singleDesc");
                        int awardCount = jo.getInt("awardCount");
                        if (singleDesc.contains("饲料") && awardCount + foodStock > foodStockLimit) {
                            Log.record("暂停领取[" + awardCount + "]克饲料，上限为[" + foodStockLimit + "]克");
                            break;
                        }
                        jo = new JSONObject(AntFarmRpcCall.drawLotteryPlus());
                        if (MessageUtil.checkMemo(TAG, jo)) {
                            Log.farm("惊喜礼包🎁[" + singleDesc + "*" + awardCount + "]");
                        }
                    } else {
                        Log.record("当日奖励已领取");
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "drawLotteryPlus err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void visitFriend() {
        Map<String, Integer> map = visitFriendList.getValue();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            String userId = entry.getKey();
            Integer countLimit = entry.getValue();
            if (userId.equals(UserIdMap.getCurrentUid())) {
                continue;
            }
            if (Status.canVisitFriendToday(userId, countLimit)) {
                visitFriend(userId, countLimit);
            }
        }
    }

    private void visitFriend(String userId, int countLimit) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.enterFarm(userId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONObject farmVO = jo.getJSONObject("farmVO");
            foodStock = farmVO.getInt("foodStock");
            JSONObject subFarmVO = farmVO.getJSONObject("subFarmVO");
            if (subFarmVO.optBoolean("visitedToday", true)) {
                Status.flagToday("farm::visitFriendLimit::" + userId);
                return;
            }
            String farmId = subFarmVO.getString("farmId");
            while (Status.canVisitFriendToday(userId, countLimit) && foodStock >= 10) {
                jo = new JSONObject(AntFarmRpcCall.visitFriend(farmId));
                if (!MessageUtil.checkMemo(TAG, jo)) {
                    break;
                }
                TimeUtil.sleep(1000);
                Status.visitFriendToday(userId);
                foodStock = jo.getInt("foodStock");
                Log.farm("赠送麦子🌾赠送[" + UserIdMap.getMaskName(userId) + "]麦子#消耗[" + jo.getInt("giveFoodNum") + "g饲料]");
                if (jo.optBoolean("isReachLimit")) {
                    Log.record("今日给[" + UserIdMap.getMaskName(userId) + "]送麦子已达上限");
                    Status.flagToday("farm::visitFriendLimit::" + userId);
                    break;
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "visitFriend err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void acceptGift() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.acceptGift());
            if (MessageUtil.checkMemo(TAG, jo)) {
                int receiveFoodNum = jo.getInt("receiveFoodNum");
                Log.farm("收取麦子🌾[" + receiveFoodNum + "g]");
            }
        } catch (Throwable t) {
            Log.i(TAG, "acceptGift err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void queryChickenDiary(String queryDayStr) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.queryChickenDiary(queryDayStr));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject data = jo.getJSONObject("data");
            JSONObject chickenDiary = data.getJSONObject("chickenDiary");
            String diaryDateStr = chickenDiary.getString("diaryDateStr");
            if (data.has("hasTietie")) {
                if (!data.optBoolean("hasTietie", true)) {
                    jo = new JSONObject(AntFarmRpcCall.diaryTietie(diaryDateStr, "NEW"));
                    if (MessageUtil.checkMemo(TAG, jo)) {
                        String prizeType = jo.getString("prizeType");
                        int prizeNum = jo.optInt("prizeNum", 0);
                        Log.farm("贴贴小鸡💞奖励[" + prizeType + "*" + prizeNum + "]");
                    }
                    if (!chickenDiary.has("statisticsList")) {
                        return;
                    }
                    JSONArray statisticsList = chickenDiary.getJSONArray("statisticsList");
                    if (statisticsList.length() > 0) {
                        for (int i = 0; i < statisticsList.length(); i++) {
                            JSONObject tietieStatus = statisticsList.getJSONObject(i);
                            String tietieRoleId = tietieStatus.getString("tietieRoleId");
                            jo = new JSONObject(AntFarmRpcCall.diaryTietie(diaryDateStr, tietieRoleId));
                            if (MessageUtil.checkMemo(TAG, jo)) {
                                String prizeType = jo.getString("prizeType");
                                int prizeNum = jo.optInt("prizeNum", 0);
                                Log.farm("贴贴小鸡💞奖励[" + prizeType + "*" + prizeNum + "]");
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "queryChickenDiary err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void queryChickenDiaryList() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.queryChickenDiaryList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray chickenDiaryBriefList = jo.getJSONObject("data").optJSONArray("chickenDiaryBriefList");
            if (chickenDiaryBriefList != null && chickenDiaryBriefList.length() > 0) {
                for (int i = 0; i < chickenDiaryBriefList.length(); i++) {
                    jo = chickenDiaryBriefList.getJSONObject(i);
                    if (!jo.optBoolean("read", true)) {
                        String dateStr = jo.getString("dateStr");
                        queryChickenDiary(dateStr);
                        TimeUtil.sleep(300);
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "queryChickenDiaryList err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void visitAnimal() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.visitAnimal());
            if (!MessageUtil.checkMemo(TAG, jo) || !jo.has("talkConfigs")) {
                return;
            }

            JSONArray talkNodes = jo.getJSONArray("talkNodes");
            JSONArray talkConfigs = jo.getJSONArray("talkConfigs");
            JSONObject data = talkConfigs.getJSONObject(0);
            String farmId = data.getString("farmId");
            jo = new JSONObject(AntFarmRpcCall.feedFriendAnimalVisit(farmId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONArray actionNodes = null;
            for (int i = 0; i < talkNodes.length(); i++) {
                jo = talkNodes.getJSONObject(i);
                if (jo.has("actionNodes")) {
                    actionNodes = jo.getJSONArray("actionNodes");
                    break;
                }
            }
            if (actionNodes == null) {
                return;
            }
            for (int i = 0; i < actionNodes.length(); i++) {
                jo = actionNodes.getJSONObject(i);
                if (!"FEED".equals(jo.getString("type"))) {
                    continue;
                }
                String consistencyKey = jo.getString("consistencyKey");
                jo = new JSONObject(AntFarmRpcCall.visitAnimalSendPrize(consistencyKey));
                if (MessageUtil.checkMemo(TAG, jo)) {
                    String prizeName = jo.getString("prizeName");
                    String userMaskName = UserIdMap.getMaskName(AntFarmRpcCall.farmId2UserId(farmId));
                    Log.farm("小鸡到访💞投喂[" + userMaskName + "]#获得[" + prizeName + "]");
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "visitAnimal err:");
            Log.printStackTrace(TAG, t);
        }
    }

    //乐园限定活动
    private void queryOptionalPlay() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.queryOptionalPlay());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            if (!jo.has("taskTriggerPlayInfo")) {
                return;
            }
            JSONObject taskTriggerPlayInfo = jo.optJSONObject("taskTriggerPlayInfo");
            if (!taskTriggerPlayInfo.has("taskList")) {
                return;
            }
            JSONArray taskList = taskTriggerPlayInfo.getJSONArray("taskList");
            for (int j = 0; j < taskList.length(); j++) {
                JSONObject task = taskList.getJSONObject(j);
                String taskType = task.getString("taskType");
                String taskStatus = task.getString("taskStatus");
                String sceneCode = task.getString("sceneCode");
                int alreadyReceiveAwardCount = task.optInt("alreadyReceiveAwardCount");
                int awardCount = task.optInt("awardCount");
                int awardCountForReceive = awardCount - alreadyReceiveAwardCount;
                JSONObject bizInfo = task.getJSONObject("bizInfo");
                String title = bizInfo.getString("title");
                if (taskStatus.equals("FINISHED")) {
                    if (awardCountForReceive > 0) {
                        JSONObject joReceived = new JSONObject(AntFarmRpcCall.receiveTaskAwardantfarm(awardCountForReceive, sceneCode, taskType));
                        if (MessageUtil.checkSuccess(TAG, joReceived)) {
                            int incAwardCount = joReceived.optInt("incAwardCount");
                            JSONObject taskConfigResultVO = joReceived.optJSONObject("taskConfigResultVO");
                            String awardType = taskConfigResultVO.optString("awardType");
                            Log.farm("小鸡乐园🎖️领取[" + title + "]奖励[" + awardType + "*" + incAwardCount + "]");
                        }
                    }
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "queryOptionalPlay err:");
            Log.printStackTrace(TAG, th);
        }
    }

    //小鸡乐园兑奖
    // skuId, sku
    Map<String, JSONObject> skuInfo = new HashMap<>();

    private void gameCenterBuyMallItem() {
        try {
            getAllSkuInfo();
            Map<String, Integer> buyList = gameCenterBuyMallItemList.getValue();
            for (Map.Entry<String, Integer> entry : buyList.entrySet()) {
                String skuId = entry.getKey();
                Integer count = entry.getValue();
                if (count == null || count < 0) {
                    continue;
                }
                while (Status.canGameCenterBuyMallItemToday(skuId, count) && BuyMallItem(skuId)) {
                    TimeUtil.sleep(3000);
                }
            }
            queryOptionalPlay();
        } catch (Throwable t) {
            Log.i(TAG, "gameCenterBuyMallItem err:");
            Log.printStackTrace(TAG, t);
        }
    }

    // 获取乐币购买商店列表
    private JSONArray getGameCenterMallItemList(String bizType) {
        JSONArray mallItemSimpleList = null;
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.getMallHome(bizType));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                mallItemSimpleList = jo.optJSONArray("mallItemSimpleList");
            }
        } catch (Throwable th) {
            Log.i(TAG, "getGameCenterMallItemList err:");
            Log.printStackTrace(TAG, th);
        }
        return mallItemSimpleList;
    }

    // 获取乐园商店所有商品信息
    private void getAllSkuInfo() {
        try {
            JSONArray mallItemSimpleList = getGameCenterMallItemList("ANTFARM_GAME_CENTER");
            if (mallItemSimpleList == null) {
                return;
            }
            for (int i = 0; i < mallItemSimpleList.length(); i++) {
                JSONObject itemInfoVO = mallItemSimpleList.getJSONObject(i);
                getSkuInfoByItemInfoVO(itemInfoVO);
            }
        } catch (Throwable th) {
            Log.i(TAG, "getAllSkuInfo err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private void getSkuInfoBySpuId(String spuId) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.getMallItemDetail(spuId));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            if (!jo.has("spuItemInfoVo")) {
                return;
            }
            JSONObject spuItemInfoVo = jo.optJSONObject("spuItemInfoVO");
            getSkuInfoByItemInfoVO(spuItemInfoVo);
        } catch (Throwable th) {
            Log.i(TAG, "getSkuInfoBySpuId err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private void getSkuInfoByItemInfoVO(JSONObject spuItem) {
        try {
            String spuId = spuItem.getString("spuId");
            JSONObject jo = new JSONObject(AntFarmRpcCall.getMallItemDetail(spuId));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            JSONObject mallItemDetail = jo.optJSONObject("mallItemDetail");
            if (!mallItemDetail.has("mallSubItemDetailList")) {
                return;
            }
            JSONArray mallSubItemDetailList = mallItemDetail.getJSONArray("mallSubItemDetailList");
            for (int i = 0; i < mallSubItemDetailList.length(); i++) {
                JSONObject skuModel = mallSubItemDetailList.getJSONObject(i);
                String skuId = skuModel.getString("skuId");
                String skuName = skuModel.getString("skuName");
                if (!skuModel.has("spuId")) {
                    skuModel.put("spuId", spuId);
                }
                skuInfo.put(skuId, skuModel);
                GameCenterMallItemMap.add(skuId, skuName);
            }
            GameCenterMallItemMap.save(UserIdMap.getCurrentUid());
        } catch (Throwable th) {
            Log.i(TAG, "getSkuInfoByItemInfoVO err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private Boolean BuyMallItem(String skuId) {
        if (skuInfo.isEmpty()) {
            getAllSkuInfo();
        }
        JSONObject sku = skuInfo.get(skuId);
        if (sku == null) {
            Log.record("小鸡乐园🎐找不到要兑奖的权益！");
            return false;
        }
        try {
            String skuName = sku.getString("skuName");
            JSONArray itemStatusList = sku.getJSONArray("itemStatusList");
            for (int i = 0; i < itemStatusList.length(); i++) {
                String itemStatus = itemStatusList.getString(i);
                if (ItemStatus.REACH_LIMIT.name().equals(itemStatus) || ItemStatus.REACH_USER_HOLD_LIMIT.name().equals(itemStatus) || ItemStatus.NO_ENOUGH_POINT.name().equals(itemStatus)) {
                    Log.farm("乐币兑奖🎐[" + skuName + "]停止:" + AntFarm.ItemStatus.valueOf(itemStatus).nickName());
                    if (AntFarm.ItemStatus.REACH_LIMIT.name().equals(itemStatus)) {
                        Status.flagToday("farm::buyLimit::" + skuId);
                    }
                    return false;
                }
            }
            String spuId = sku.getString("spuId");
            if (BuyMallItem(spuId, skuId, skuName)) {
                return true;
            }
            getSkuInfoBySpuId(spuId);
        } catch (Throwable th) {
            Log.i(TAG, "BuyMallItem err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    public static Boolean BuyMallItem(String spuId, String skuId, String skuName) {
        try {
            if (BuyMallItem(spuId, skuId)) {
                Status.gameCenterBuyMallItemToday(skuId);
                int buyedCount = Status.getGameCenterBuyMallItemCountToday(skuId);
                Log.farm("乐币兑奖🎐[" + skuName + "]#第" + buyedCount + "次");
                return true;
            } else {
                Status.gameCenterBuyMallItemToday(skuId);
                return false;
            }
        } catch (Throwable th) {
            Log.i(TAG, "BuyMallItem err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    private static Boolean BuyMallItem(String spuId, String skuId) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.buyMallItem(spuId, skuId));
            if (jo.has("errorMessage")) {
                String errorMessage = jo.optString("errorMessage");
                //如果出错今天停止兑换
                if (errorMessage.equals("系统繁忙，请稍后再试。")) {
                    Status.flagToday("farm::buyLimit::" + skuId);
                }
            }
            return MessageUtil.checkResultCode(TAG, jo);
        } catch (Throwable th) {
            Log.i(TAG, "BuyMallItem err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    private void drawMachineGroups() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.queryLoveCabin(UserIdMap.getCurrentUid()));
            if (MessageUtil.checkMemo(TAG, jo)) {
                drawMachine("ANTFARM_DAILY_DRAW_TASK", "dailyDrawMachine", "ipDrawMachine");

                JSONObject queryDrawMachineActivityjo = new JSONObject(AntFarmRpcCall.queryDrawMachineActivity("ipDrawMachine", "dailyDrawMachine"));
                if (MessageUtil.checkMemo(TAG, queryDrawMachineActivityjo)) {
                    if (!queryDrawMachineActivityjo.has("otherDrawMachineActivityIds")) {
                        return;
                    }
                    if (queryDrawMachineActivityjo.getJSONArray("otherDrawMachineActivityIds").length() > 0) {
                        drawMachine("ANTFARM_IP_DRAW_TASK", "ipDrawMachine", "dailyDrawMachine");
                        //自动抽奖
                        if (IPexchangeBenefit.getValue()) {
                            try {
                                jo = new JSONObject(AntFarmRpcCall.queryDrawMachineActivity("dailyDrawMachine", "ipDrawMachine"));
                                if (!MessageUtil.checkResultCode(TAG, jo)) {
                                    return;
                                }
                                JSONObject activity = jo.optJSONObject("drawMachineActivity");
                                if (activity == null) {
                                    return;
                                }
                                String activityId = activity.optString("activityId");
                                if (!activityId.isEmpty()) {
                                    //IPexchangeBenefit选择某种类型商品兑换
                                    //返回false表示有兑换的或碎片不足，返回true表示全部兑换完毕
                                    if (IPexchangeBenefit(activityId, "DRESS")) {
                                        if (IPexchangeBenefit(activityId, "REISSUE_CARD")) {
                                            if (IPexchangeBenefit(activityId, "DELICIOUS_FOOD")) {
                                                IPexchangeBenefit(activityId, "ANTFARM_IP_DRAW_MALL");
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                Log.i(TAG, "drawMachine err:");
                                Log.printStackTrace(TAG, t);
                            }

                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "queryLoveCabin err:");
            Log.printStackTrace(t);
        }
    }

    private void drawMachine(String taskSceneCode, String scene, String otherScenes) {
        doFarmDrawTask(taskSceneCode);
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.queryDrawMachineActivity(otherScenes, scene));
            int drawTimes = jo.optInt("drawTimes", 0);
            for (int i = 0; i < drawTimes; i++) {
                if (!drawMachine(scene)) {
                    return;
                }
                TimeUtil.sleep(5000);
            }
        } catch (Throwable t) {
            Log.i(TAG, "drawMachine err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void doFarmDrawTask(String taskSceneCode) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.listFarmDrawTask(taskSceneCode));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONArray farmTaskList = jo.getJSONArray("farmTaskList");
            for (int i = 0; i < farmTaskList.length(); i++) {
                jo = farmTaskList.getJSONObject(i);
                String taskStatus = jo.getString("taskStatus");
                String title = jo.getString("title");
                if (TaskStatus.RECEIVED.name().equals(taskStatus)) {
                    continue;
                }
                if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                    String taskId = jo.getString("taskId");
                    String awardType = jo.optString("awardType");
                    receiveFarmDrawTaskAward(taskId, title, awardType, taskSceneCode);
                    continue;
                }
                //黑名单任务跳过
                if (AntFarmDrawMachineTaskList.getValue().contains(title)) {
                    continue;
                }

                if (TaskStatus.TODO.name().equals(taskStatus)) {
                    int rightsTimesLimit = jo.optInt("rightsTimesLimit");
                    int rightsTimes = jo.optInt("rightsTimes");

                    if (jo.optString("taskId").contains("EXCHANGE") || jo.optString("taskId").contains("FKDWChuodong") || jo.optString("taskId").contains("GYG2") || jo.optString("taskId").equals("jiatingdongrirongrongwu")) {
                        for (int j = 0; j < (rightsTimesLimit - rightsTimes); j++) {
                            JSONObject jodoFarmTask = new JSONObject(AntFarmRpcCall.doFarmTask(jo.optString("bizKey"), taskSceneCode));
                            //检查并标记黑名单任务
                            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmDrawMachineTaskList", title, jodoFarmTask);
                        }
                        TimeUtil.sleep(1000);
                    }
                    if (jo.optString("taskId").contains("SHANGYEHUA")) {
                        for (int j = 0; j < (rightsTimesLimit - rightsTimes); j++) {
                            JSONObject jofinishTask = new JSONObject(AntFarmRpcCall.finishTask(jo.optString("taskId"), taskSceneCode));
                            //检查并标记黑名单任务
                            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmDrawMachineTaskList", title, jofinishTask);
                        }
                        TimeUtil.sleep(2000);
                    }

                    //完成浏览类游戏任务
                    if ((jo.optString("title").contains("玩"))&&jo.optString("desc").contains("玩") && jo.optString("desc").contains("s")) {
                        for (int j = 0; j < (rightsTimesLimit - rightsTimes); j++) {
                            JSONObject jofinishTask = new JSONObject(AntFarmRpcCall.finishTask(jo.optString("taskId"), taskSceneCode));
                            //检查并标记黑名单任务
                            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmDrawMachineTaskList", title, jofinishTask);
                        }
                        TimeUtil.sleep(2000);
                    }

                    TimeUtil.sleep(1000);
                }
                TimeUtil.sleep(2000);
                String taskId = jo.getString("taskId");
                String awardType = jo.optString("awardType");
                receiveFarmDrawTaskAward(taskId, title, awardType, taskSceneCode);
            }
        } catch (Throwable t) {
            Log.i(TAG, "doFarmDrawActivityTimeTask err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void receiveFarmDrawTaskAward(String taskId, String title, String awardType, String taskSceneCode) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.receiveFarmDrawTimesTaskAward(taskId, awardType, taskSceneCode));
            if (MessageUtil.checkMemo(TAG, jo)) {
                Log.farm("装扮抽奖🎖️领取[" + title + "]奖励");
            }
        } catch (Throwable t) {
            Log.i(TAG, "receiveFarmDrawTimesTaskAward err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private Boolean drawMachine(String scene) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.drawMachine(scene));
            if (MessageUtil.checkMemo(TAG, jo)) {
                if (!jo.has("title")) {
                    jo = jo.optJSONObject("drawMachinePrize");
                }
                String title = jo.optString("title");
                Log.farm("装扮抽奖🎁抽中[" + title + "]");
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "drawMachine err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    //返回false表示有兑换的或碎片不足，返回true表示全部兑换完毕
    public boolean IPexchangeBenefit(String activityId, String labelType) {
        try {
            String response = AntFarmRpcCall.getItemList(activityId, 10, 0);
            JSONObject respJson = new JSONObject(response);

            if (respJson.optBoolean("success", false) || "100000000".equals(respJson.optString("code"))) {
                int totalCent = 0;
                JSONObject mallAccount = respJson.optJSONObject("mallAccountInfoVO");
                if (mallAccount != null) {
                    JSONObject holdingCount = mallAccount.optJSONObject("holdingCount");
                    if (holdingCount != null) {
                        totalCent = holdingCount.optInt("cent", 0);
                    }
                }
                //Log.record("当前持有总碎片:" + (totalCent / 100));
                JSONArray itemVOList = respJson.optJSONArray("itemInfoVOList");
                if (itemVOList == null) {
                    return true;
                }

                List<JSONObject> allSkus = new ArrayList<>();
                for (int i = 0; i < itemVOList.length(); i++) {
                    JSONObject item = itemVOList.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    JSONArray labelTypeList = item.optJSONArray("labelTypeList");
                    if (labelTypeList == null) {
                        continue;
                    }
                    boolean isRightItem = false;
                    for (int j = 0; j < labelTypeList.length(); j++) {
                        String itemLabelType = labelTypeList.optString(j);
                        if (itemLabelType.contains(labelType)) {
                            isRightItem = true;
                        }
                    }
                    if (!isRightItem) {
                        continue;
                    }
                    boolean itemReachedLimit = isReachedLimit(item);
                    JSONObject minPriceObj = item.optJSONObject("minPrice");
                    int cent = minPriceObj != null ? minPriceObj.optInt("cent", 0) : 0;

                    JSONArray skuList = item.optJSONArray("skuModelList");
                    if (skuList == null) {
                        continue;
                    }
                    for (int j = 0; j < skuList.length(); j++) {
                        JSONObject sku = skuList.optJSONObject(j);
                        if (sku == null) {
                            continue;
                        }
                        sku.put("_spuId", item.optString("spuId"));
                        sku.put("_spuName", item.optString("spuName"));
                        sku.put("_isReachLimit", itemReachedLimit || isReachedLimit(sku));
                        sku.put("_cent", cent);
                        allSkus.add(sku);
                    }
                }
                // 按价格从高到低排序
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    allSkus.sort((JSONObject a, JSONObject b) -> Integer.compare(b.optInt("_cent", 0), a.optInt("_cent", 0)));
                } else {
                    // 低版本用 Collections.sort 兼容
                    Collections.sort(allSkus, new Comparator<JSONObject>() {
                        @Override
                        public int compare(JSONObject a, JSONObject b) {
                            return Integer.compare(b.optInt("_cent", 0), a.optInt("_cent", 0));
                        }
                    });
                }

                for (JSONObject sku : allSkus) {
                    if (sku.optBoolean("_isReachLimit")) {
                        continue;
                    }
                    int cent = sku.optInt("_cent", 0);
                    String skuName = sku.optString("skuName");

                    if (isNoEnoughPoint(sku) || (cent > 0 && totalCent < cent)) {
                        Log.record("兑换[" + labelType + "]类最高价值[" + skuName + "]碎片不足(持有" + (totalCent / 100) + "需" + (cent / 100) + ")");
                        return false;
                    }
                    break;
                }

                // 执行顺序兑换，按价格从高到低
                for (JSONObject sku : allSkus) {
                    if (sku.optBoolean("_isReachLimit")) {
                        continue;
                    }

                    String skuName = sku.optString("skuName");
                    int cent = sku.optInt("_cent", 0);
                    String extendInfo = sku.optString("skuExtendInfo");
                    int limitCount = extendInfo.contains("20次") ? 20 : (extendInfo.contains("5次") ? 5 : 1);

                    // 【核心逻辑】：如果当前项买不起，直接 return 停止，不再尝试后续更便宜的项目
                    if (isNoEnoughPoint(sku) || (cent > 0 && totalCent < cent)) {
                        Log.record("剩余碎片不足以兑换[" + labelType + "]类优先级项 [" + skuName + "] (需 " + (cent / 100) + ")，停止后续兑换任务");
                        return false;
                    }

                    int sessionExchangedCount = 0;
                    while (sessionExchangedCount < limitCount) {
                        // 预检查当前余额
                        if (cent > 0 && totalCent < cent) {
                            break;
                        }

                        String result = AntFarmRpcCall.exchangeBenefit(sku.optString("_spuId"), sku.optString("skuId"), activityId, "ANTFARM_IP_DRAW_MALL", "antfarm_villa");

                        JSONObject resObj = new JSONObject(result);
                        String resultCode = resObj.optString("resultCode");

                        if ("SUCCESS".equals(resultCode)) {
                            sessionExchangedCount++;
                            totalCent -= cent; // 减去花费
                            Log.farm("兑换装扮👔[" + labelType + "]类[" + skuName + "]#剩余碎片" + (totalCent / 100));
                            TimeUtil.sleep(800);
                        } else if ("NO_ENOUGH_POINT".equals(resultCode)) {
                            return false;
                        } else if (resultCode.contains("LIMIT") || resultCode.contains("MAX")) {
                            break;
                        } else {
                            break;
                        }
                    }
                }
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            Log.printStackTrace("自动兑换异常", e);
        }
        return false;
    }

    private boolean isReachedLimit(JSONObject jo) {
        if (jo == null) {
            return false;
        }
        if ("REACH_LIMIT".equals(jo.optString("itemStatus"))) {
            return true;
        }
        JSONArray list = jo.optJSONArray("itemStatusList");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                String status = list.optString(i);
                if ("REACH_LIMIT".equals(status) || status.contains("LIMIT")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNoEnoughPoint(JSONObject jo) {
        if (jo == null) {
            return false;
        }
        if ("NO_ENOUGH_POINT".equals(jo.optString("itemStatus"))) {
            return true;
        }
        JSONArray list = jo.optJSONArray("itemStatusList");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                if ("NO_ENOUGH_POINT".equals(list.optString(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    /* 雇佣好友小鸡 */
    private void hireAnimal() {
        try {
            syncAnimalStatus(ownerFarmId);
            if (!AnimalFeedStatus.EATING.name().equals(ownerAnimal.animalFeedStatus)) {
                return;
            }
            int count = 3 - animals.length;
            if (count <= 0) {
                return;
            }
            Log.farm("雇佣小鸡👷[当前可雇佣小鸡数量:" + count + "只]");
            if (foodStock < 50) {
                Log.record("饲料不足，暂不雇佣");
                return;
            }

            boolean hasNext;
            int pageStartSum = 0;
            Set<String> hireAnimalSet = hireAnimalList.getValue();
            do {
                JSONObject jo = new JSONObject(AntFarmRpcCall.rankingList(pageStartSum));
                if (!MessageUtil.checkMemo(TAG, jo)) {
                    return;
                }
                JSONArray rankingList = jo.getJSONArray("rankingList");
                hasNext = jo.getBoolean("hasNext");
                pageStartSum += rankingList.length();
                for (int i = 0; i < rankingList.length() && count > 0; i++) {
                    jo = rankingList.getJSONObject(i);
                    String userId = jo.getString("userId");
                    boolean isHireAnimal = hireAnimalSet.contains(userId);
                    if (hireAnimalType.getValue() != HireAnimalType.HIRE) {
                        isHireAnimal = !isHireAnimal;
                    }
                    if (!isHireAnimal || userId.equals(UserIdMap.getCurrentUid())) {
                        continue;
                    }
                    String actionTypeListStr = jo.getJSONArray("actionTypeList").toString();
                    if (actionTypeListStr.contains("can_hire_action")) {
                        if (hireAnimalAction(userId)) {
                            count--;
                            autoFeedAnimal();
                        }
                    }
                }
            } while (hasNext && count > 0);

            if (count > 0) {
                Log.farm("没有足够的小鸡可以雇佣");
            }
        } catch (Throwable t) {
            Log.i(TAG, "hireAnimal err:");
            Log.printStackTrace(TAG, t);
        } finally {
            long updateTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
            String taskId = "UPDATE|HIRE|" + ownerFarmId;
            addChildTask(new ChildModelTask(taskId, "UPDATE", this::autoHireAnimal, updateTime));
        }
    }

    private void autoHireAnimal() {
        try {
            syncAnimalStatus(ownerFarmId);
            for (Animal animal : animals) {
                if (!SubAnimalType.WORK.name().equals(animal.subAnimalType)) {
                    continue;
                }
                String taskId = "HIRE|" + animal.animalId;
                if (!hasChildTask(taskId)) {
                    long beHiredEndTime = animal.beHiredEndTime;
                    addChildTask(new ChildModelTask(taskId, "HIRE", this::hireAnimal, beHiredEndTime));
                    Log.record("添加蹲点雇佣👷在[" + TimeUtil.getCommonDate(beHiredEndTime) + "]执行");
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "autoHireAnimal err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private Boolean hireAnimalAction(String userId) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.enterFarm("", userId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            jo = jo.getJSONObject("farmVO").getJSONObject("subFarmVO");
            String farmId = jo.getString("farmId");
            JSONArray animals = jo.getJSONArray("animals");
            for (int i = 0, len = animals.length(); i < len; i++) {
                JSONObject animal = animals.getJSONObject(i);
                if (Objects.equals(animal.getJSONObject("masterUserInfoVO").getString("userId"), userId)) {
                    String animalId = animal.getString("animalId");
                    jo = new JSONObject(AntFarmRpcCall.hireAnimal(farmId, animalId));
                    if (MessageUtil.checkMemo(TAG, jo)) {
                        foodStock = jo.getInt("foodStock");
                        int reduceFoodNum = jo.getInt("reduceFoodNum");
                        Log.farm("雇佣小鸡👷雇佣[" + UserIdMap.getMaskName(userId) + "]#消耗[" + reduceFoodNum + "g饲料]");
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "hireAnimalAction err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private void drawGameCenterAward() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.queryGameList());
            if (jo.optBoolean("success")) {
                // 2. 获取宝箱领取权限数据
                JSONObject drawRights = jo.optJSONObject("gameCenterDrawRights");
                if (drawRights != null) {
                    // 3. 处理当前可开启的宝箱
                    int quotaCanUse = drawRights.optInt("quotaCanUse"); // 当前可开宝箱数
                    if (quotaCanUse > 0) {
                        Log.record("当前有 " + quotaCanUse + " 个宝箱待开启...");

                        while (quotaCanUse > 0) {
                            // 调用开启宝箱接口
                            String drawResStr = AntFarmRpcCall.drawGameCenterAward(1);
                            JSONObject drawRes = new JSONObject(drawResStr);

                            if (drawRes.optBoolean("success")) {
                                // 更新剩余可开启次数
                                JSONObject nextRights = drawRes.optJSONObject("gameCenterDrawRights");
                                quotaCanUse = (nextRights != null) ? nextRights.optInt("quotaCanUse") : (quotaCanUse - 1);

                                // 解析奖励列表并拼接日志
                                JSONArray awardList = drawRes.optJSONArray("gameCenterDrawAwardList");
                                List<String> awardStrings = new ArrayList<>();
                                if (awardList != null) {
                                    for (int i = 0; i < awardList.length(); i++) {
                                        JSONObject item = awardList.getJSONObject(i);
                                        String awardName = item.optString("awardName");
                                        int awardCount = item.optInt("awardCount");
                                        awardStrings.add(awardName + "*" + awardCount);
                                    }
                                }
                                String awardLog = String.join(",", awardStrings);
                                Log.farm("小鸡乐园🎁开宝箱得[" + awardLog + "]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                                TimeUtil.sleep(3000);
                            } else {
                                Log.record("小鸡乐园开启宝箱失败: " + drawRes.optString("desc"));
                                break; // 开启失败则退出循环
                            }
                        }
                    }

                    // 4. 处理剩余任务（判断是否需要刷任务）
                    int limit = drawRights.optInt("quotaLimit"); // 每日上限
                    int used = drawRights.optInt("usedQuota");   // 今日已开数量
                    int remainToTask = limit - used;
                    // 已开数量 < 上限 且 无可用次数 → 触发任务刷取
                    if (remainToTask > 0 && quotaCanUse == 0) {
                        GameTask.Farm_ddply.report("庄园", remainToTask);
                    } else if (remainToTask <= 0) {
                        Log.record("今日 " + limit + " 个金蛋任务已全部满额");
                    }
                }

                // 异步任务完成
                TimeUtil.sleep(3000);
                
                
                /*
                JSONObject gameDrawAwardActivity = jo.getJSONObject("gameDrawAwardActivity");
                int canUseTimes = gameDrawAwardActivity.getInt("canUseTimes");
                while (canUseTimes > 0) {
                    try {
                        jo = new JSONObject(AntFarmRpcCall.drawGameCenterAward());
                        if (jo.optBoolean("success")) {
                            canUseTimes = jo.getInt("drawRightsTimes");
                            JSONArray gameCenterDrawAwardList = jo.getJSONArray("gameCenterDrawAwardList");
                            ArrayList<String> awards = new ArrayList<String>();
                            for (int i = 0; i < gameCenterDrawAwardList.length(); i++) {
                                JSONObject gameCenterDrawAward = gameCenterDrawAwardList.getJSONObject(i);
                                int awardCount = gameCenterDrawAward.getInt("awardCount");
                                String awardName = gameCenterDrawAward.getString("awardName");
                                awards.add(awardName + "*" + awardCount);
                            }
                            Log.farm("小鸡乐园🎮开宝箱得[" + StringUtil.collectionJoinString(",", awards) + "]");
                        }
                        else {
                            Log.i(TAG, "drawGameCenterAward falsed result: " + jo.toString());
                        }
                    }
                    catch (Throwable t) {
                        Log.printStackTrace(TAG, t);
                    }
                    finally {
                        TimeUtil.sleep(3000);
                    }
                }*/
            } else {
                Log.i(TAG, "queryGameList falsed result: " + jo.toString());
            }

        } catch (Throwable t) {
            Log.i(TAG, "drawGameCenterAward err:");
            Log.printStackTrace(TAG, t);
        }
    }

    // 装扮焕新
    private void ornamentsDressUp() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.listOrnaments());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            List<JSONObject> list = new ArrayList<>();
            JSONArray achievementOrnaments = jo.getJSONArray("achievementOrnaments");
            long takeOffTime = System.currentTimeMillis();
            for (int i = 0; i < achievementOrnaments.length(); i++) {
                jo = achievementOrnaments.getJSONObject(i);
                if (!jo.optBoolean("acquired")) {
                    continue;
                }
                if (jo.has("takeOffTime")) {
                    takeOffTime = jo.getLong("takeOffTime");
                }
                String resourceKey = jo.getString("resourceKey");
                String name = jo.getString("name");
                if (ornamentsDressUpList.getValue().contains(resourceKey)) {
                    list.add(jo);
                }
                FarmOrnamentsIdMap.add(resourceKey, name);
            }
            FarmOrnamentsIdMap.save(UserIdMap.getCurrentUid());
            if (list.isEmpty() || takeOffTime + TimeUnit.DAYS.toMillis(ornamentsDressUpDays.getValue() - 15) > System.currentTimeMillis()) {
                return;
            }

            jo = list.get(RandomUtil.nextInt(0, list.size() - 1));
            if (saveOrnaments(jo)) {
                Log.farm("装扮焕新✨[" + jo.getString("name") + "]");
            }
        } catch (Throwable t) {
            Log.i(TAG, "ornamentsDressUp err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private Boolean saveOrnaments(JSONObject ornaments) {
        try {
            String animalId = ownerAnimal.animalId;
            String farmId = ownerFarmId;
            String ornamentsSets = getOrnamentsSets(ornaments.getJSONArray("sets"));
            JSONObject jo = new JSONObject(AntFarmRpcCall.saveOrnaments(animalId, farmId, ornamentsSets));
            return MessageUtil.checkMemo(TAG, jo);
        } catch (Throwable t) {
            Log.i(TAG, "saveOrnaments err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private String getOrnamentsSets(JSONArray sets) {
        StringBuilder ornamentsSets = new StringBuilder();
        try {
            for (int i = 0; i < sets.length(); i++) {
                JSONObject set = sets.getJSONObject(i);
                if (i > 0) {
                    ornamentsSets.append(",");
                }
                ornamentsSets.append(set.getString("id"));
            }
        } catch (Throwable t) {
            Log.i(TAG, "getOrnamentsSets err:");
            Log.printStackTrace(TAG, t);
        }
        return ornamentsSets.toString();
    }

    // 一起拿小鸡饲料
  /*  private void letsGetChickenFeedTogether() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.letsGetChickenFeedTogether());
            if (jo.optBoolean("success")) {
                String bizTraceId = jo.getString("bizTraceId");
                JSONArray p2pCanInvitePersonDetailList = jo.getJSONArray("p2pCanInvitePersonDetailList");
                
                int canInviteCount = 0;
                int hasInvitedCount = 0;
                List<String> userIdList = new ArrayList<>(); // 保存 userId
                for (int i = 0; i < p2pCanInvitePersonDetailList.length(); i++) {
                    JSONObject personDetail = p2pCanInvitePersonDetailList.getJSONObject(i);
                    String inviteStatus = personDetail.getString("inviteStatus");
                    String userId = personDetail.getString("userId");
                    
                    if (inviteStatus.equals("CAN_INVITE")) {
                        userIdList.add(userId);
                        canInviteCount++;
                    }
                    else if (inviteStatus.equals("HAS_INVITED")) {
                        hasInvitedCount++;
                    }
                }
                
                int invitedToday = hasInvitedCount;
                
                int remainingInvites = 5 - invitedToday;
                int invitesToSend = Math.min(canInviteCount, remainingInvites);
                
                if (invitesToSend == 0) {
                    return;
                }
                
                Set<String> getFeedSet = getFeedList.getValue();
                
                //if (getFeedType.getValue() == GetFeedType.GIVE) {
                    //for (String userId : userIdList) {
                        //if (invitesToSend <= 0) {
                            //                            Log.record("已达到最大邀请次数限制，停止发送邀请。");
                            //break;
                        //}
                        //if (getFeedSet.contains(userId)) {
                         //   jo = new JSONObject(AntFarmRpcCall.giftOfFeed(bizTraceId, userId));
                            //if (jo.optBoolean("success")) {
                                //Log.record("一起拿小鸡饲料🥡 [送饲料：" + UserIdMap.getMaskName(userId) + "]");
                                //invitesToSend--; // 每成功发送一次邀请，减少一次邀请次数
                            //}
                            //else {
                                //Log.record("邀请失败：" + jo);
                                //break;
                            //}
                        //}
                        //else {
                            //                            Log.record("用户 " + UserIdMap.getMaskName(userId) + "
                            // 不在勾选的好友列表中，不发送邀请。");
                     //   }
                  //  }
             //   }
                else {
                    Random random = new Random();
                    for (int j = 0; j < invitesToSend; j++) {
                        int randomIndex = random.nextInt(userIdList.size());
                        String userId = userIdList.get(randomIndex);
                        
                        jo = new JSONObject(AntFarmRpcCall.giftOfFeed(bizTraceId, userId));
                        if (jo.optBoolean("success")) {
                            Log.record("一起拿小鸡饲料🥡 [送饲料：" + UserIdMap.getMaskName(userId) + "]");
                        }
                        else {
                            Log.record("邀请失败：" + jo);
                            break;
                        }
                        userIdList.remove(randomIndex);
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "letsGetChickenFeedTogether err:");
            Log.printStackTrace(t);
        }
    }  */

    private void family() {
        if (StringUtil.isEmpty(ownerGroupId)) {
            return;
        }
        // 检查 ExtensionsHandle 是否存在
        try {
            Class.forName("io.github.lazyimmortal.sesame.model.extensions.ExtensionsHandle");
            ExtensionsHandle.handleAlphaRequest("antFarm", "doFamilyTask", null);
        } catch (ClassNotFoundException e) {
            Log.record("ExtensionsHandle 类未找到，跳过扩展处理");
        }
        try {
            JSONObject joenterFamily = enterFamily();
            JSONObject jo;
            if (joenterFamily == null) {
                return;
            }
            ownerGroupId = joenterFamily.getString("groupId");
            int familyAwardNum = joenterFamily.getInt("familyAwardNum");
            boolean familySignTips = joenterFamily.getBoolean("familySignTips");
            JSONObject assignFamilyMemberInfo = joenterFamily.optJSONObject("assignFamilyMemberInfo");
            boolean feedFriendLimit = joenterFamily.optBoolean("feedFriendLimit", false);
            JSONArray familyAnimals = joenterFamily.getJSONArray("animals");
            JSONArray EatTogetherUserIds = new JSONArray();
            // 修复：创建新的 JSONArray 副本，避免修改原始 familyAnimals
            JSONArray familyAnimalsExceptUser = new JSONArray();
            for (int i = 0; i < familyAnimals.length(); i++) {
                jo = familyAnimals.getJSONObject(i);
                String userId = jo.getString("userId");
                EatTogetherUserIds.put(userId);
                if (!userId.equals(UserIdMap.getCurrentUid())) {
                    familyAnimalsExceptUser.put(jo);
                }
            }
            // 获取家庭成员ID列表
            List<String> familyUserIds = new ArrayList<>();
            JSONArray friendUserIds = new JSONArray();
            for (int i = 0; i < familyAnimals.length(); i++) {
                jo = familyAnimals.getJSONObject(i);
                String animalId = jo.getString("animalId");
                String userId = jo.getString("userId");
                familyUserIds.add(userId);
                friendUserIds.put(userId);
                if (animalId.equals(ownerAnimal.animalId)) {
                    continue;
                }
                String farmId = jo.getString("farmId");
                JSONObject animalStatusVO = jo.getJSONObject("animalStatusVO");
                String animalFeedStatus = animalStatusVO.getString("animalFeedStatus");
                String animalInteractStatus = animalStatusVO.getString("animalInteractStatus");
                if (AnimalInteractStatus.HOME.name().equals(animalInteractStatus) && AnimalFeedStatus.HUNGRY.name().equals(animalFeedStatus)) {
                    if (familyOptions.getValue().contains("familyFeed")) {
                        feedFriendAnimal(farmId);
                    }
                }
            }

            // 家庭签到
            if (familySignTips && familyOptions.getValue().contains("familySign")) {
                familySign();
            }
            // 顶梁柱功能
            if (assignFamilyMemberInfo != null && familyOptions.getValue().contains("assignRights") && !"USED".equals(assignFamilyMemberInfo.getJSONObject("assignRights").optString("status"))) {
                if (UserIdMap.getCurrentUid().equals(assignFamilyMemberInfo.getJSONObject("assignRights").optString("assignRightsOwner"))) {
                    assignFamilyMember(assignFamilyMemberInfo, familyUserIds);
                }
                /*else {
                    Log.record("家庭任务🏡[使用顶梁柱特权] 不是家里的顶梁柱！");
                     移除选项，避免重复检查
                    familyOptions.getValue().remove("assignRights");
                }*/
            }

            // 领取家庭奖励
            if (familyOptions.getValue().contains("familyClaimReward") && familyAwardNum > 0) {
                familyAwardList();
            }

            // 帮家庭成员喂鸡
            if (familyOptions.getValue().contains("feedFamilyAnimal") && !feedFriendLimit) {
                familyFeedFriendAnimal(familyAnimals);
            }

            JSONArray familyInteractActions = joenterFamily.optJSONArray("familyInteractActions");
            JSONObject eatTogetherConfig = joenterFamily.getJSONObject("eatTogetherConfig");
            //家庭请客吃饭
            boolean canEatTogether = true;
            if (familyInteractActions != null) {
                for (int i = 0; i < familyInteractActions.length(); i++) {
                    JSONObject familyInteractAction = familyInteractActions.getJSONObject(i);
                    if ("EatTogether".equals(familyInteractAction.optString("familyInteractType"))) {
                        canEatTogether = false;
                    }
                }
            }
            // 一起吃饭
            if (canEatTogether && familyOptions.getValue().contains("familyEatTogether") && eatTogetherConfig.has("periodItemList")) {
                familyEatTogether(ownerGroupId, EatTogetherUserIds);
            }

            // 道早安
            if (familyOptions.getValue().contains("deliverMsgSend")) {
                deliverMsgSend(familyAnimalsExceptUser, familyUserIds);
            }

            // 分享给好友
            if (familyOptions.getValue().contains("shareToFriends")) {
                familyShareToFriends(ownerGroupId, familyUserIds, notInviteList);
            }
        } catch (Throwable t) {
            Log.i(TAG, "family err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 顶梁柱功能
     */
    private void assignFamilyMember(JSONObject jsonObject, List<String> userIds) {
        try {
            userIds.remove(UserIdMap.getCurrentUid());
            if (userIds.isEmpty()) {
                return;
            }
            String beAssignUser = userIds.get(RandomUtil.nextInt(0, userIds.size() - 1));
            JSONArray assignConfigList = jsonObject.getJSONArray("assignConfigList");
            JSONObject assignConfig = assignConfigList.getJSONObject(RandomUtil.nextInt(0, assignConfigList.length() - 1));
            JSONObject jo = new JSONObject(AntFarmRpcCall.assignFamilyMember(assignConfig.getString("assignAction"), beAssignUser));
            if (MessageUtil.checkMemo(TAG, jo)) {
                Log.farm("家庭任务🏡[使用顶梁柱特权] " + assignConfig.getString("assignDesc"));
            }
        } catch (Throwable t) {
            Log.i(TAG, "assignFamilyMember err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 帮家庭成员喂鸡
     */
    private void familyFeedFriendAnimal(JSONArray animals) {
        try {
            for (int i = 0; i < animals.length(); i++) {
                JSONObject animal = animals.getJSONObject(i);
                JSONObject status = animal.getJSONObject("animalStatusVO");
                String interactStatus = status.getString("animalInteractStatus");
                String feedStatus = status.getString("animalFeedStatus");

                if (!AnimalInteractStatus.HOME.name().equals(interactStatus) || !AnimalFeedStatus.HUNGRY.name().equals(feedStatus)) {
                    continue;
                }

                String groupId = animal.getString("groupId");
                String farmId = animal.getString("farmId");
                String userId = animal.getString("userId");

                if (!UserIdMap.getUserIdSet().contains(userId)) {
                    Log.record(userId + " 不是你的好友！ 跳过家庭喂食");
                    continue;
                }

                String flagKey = "farm::feedFriendLimit::" + userId;
                if (Status.hasFlagToday(flagKey)) {
                    Log.record("[" + userId + "] 今日喂鸡次数已达上限（已记录）🥣，跳过");
                    continue;
                }

                JSONObject jo = new JSONObject(AntFarmRpcCall.feedFriendAnimal(farmId, groupId));
                if (!jo.optBoolean("success", false)) {
                    String code = jo.optString("resultCode");
                    if ("391".equals(code)) {
                        Status.flagToday(flagKey);
                        Log.record("[" + userId + "] 今日帮喂次数已达上限🥣，已记录为当日限制");
                    } else {
                        Log.record("喂食失败 user=" + userId + " code=" + code + " msg=" + jo.optString("memo"));
                    }
                    continue;
                }

                int foodStockAfter = jo.optInt("foodStock");
                String maskName = UserIdMap.getMaskName(userId);
                Log.farm("家庭任务🏠帮喂好友🥣[" + maskName + "]的小鸡180g #剩余" + foodStockAfter + "g");
            }
        } catch (Throwable t) {
            Log.i(TAG, "familyFeedFriendAnimal err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void familyEatTogether(String groupId, JSONArray EatTogetherUserIds) {
        long currentTime = System.currentTimeMillis();
        String periodName;
        if (TimeUtil.isAfterTimeStr(currentTime, "0600") && TimeUtil.isBeforeTimeStr(currentTime, "1100")) {
            periodName = "早餐";
        } else if (TimeUtil.isAfterTimeStr(currentTime, "1100") && TimeUtil.isBeforeTimeStr(currentTime, "1600")) {
            periodName = "午餐";
        } else if (TimeUtil.isAfterTimeStr(currentTime, "1600") && TimeUtil.isBeforeTimeStr(currentTime, "2000")) {
            periodName = "晚餐";
        } else {
            return;
        }
        try {
            JSONArray cuisines = queryRecentFarmFood(EatTogetherUserIds.length());
            if (cuisines == null) {
                return;
            }
            JSONObject jo = new JSONObject(AntFarmRpcCall.familyEatTogether(groupId, cuisines, EatTogetherUserIds));
            if (MessageUtil.checkMemo(TAG, jo)) {
                Log.farm("亲密家庭🏠" + periodName + "请客#消耗美食" + EatTogetherUserIds.length() + "份#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                syncFamilyStatus(groupId);
            }
        } catch (Throwable t) {
            Log.i(TAG, "familyEatTogether err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 家庭「道早安」任务
     * <p>
     * <p>
     * <p>
     * 1）先通过 familyTaskTips 判断今日是否还有「道早安」任务：
     * - 请求方法：com.alipay.antfarm.familyTaskTips
     * - 请求体关键字段：
     * animals      -> 直接复用 enterFamily 返回的家庭 animals 列表
     * taskSceneCode-> "ANTFARM_FAMILY_TASK"
     * sceneCode    -> "ANTFARM"
     * source       -> "H5"
     * requestType  -> "NORMAL"
     * timeZoneId   -> "Asia/Shanghai"
     * - 响应 familyTaskTips 数组中存在 bizKey="GREETING" 且 taskStatus="TODO" 时，说明可以道早安
     * <p>
     * 2）未完成早安任务时，按顺序调用以下 RPC 获取 AI 文案并发送：
     * a. com.alipay.antfarm.deliverSubjectRecommend
     * -> 入参：friendUserIds（家庭其他成员 userId 列表），sceneCode="ChickFamily"，source="H5"
     * -> 取出：ariverRpcTraceId、eventId、eventName、sceneId、sceneName 等上下文
     * b. com.alipay.antfarm.DeliverContentExpand
     * -> 入参：上一步取到的 ariverRpcTraceId / eventId / eventName / sceneId / sceneName 等 + friendUserIds
     * -> 返回：AI 生成的 content 以及 deliverId
     * c. com.alipay.antfarm.QueryExpandContent
     * -> 入参：deliverId
     * -> 用于再次确认 content 与场景（可选安全校验）
     * d. com.alipay.antfarm.DeliverMsgSend
     * -> 入参：content、deliverId、friendUserIds、groupId（家庭 groupId）、sceneCode="ANTFARM"、spaceType="ChickFamily" 等
     * <p>
     * 额外增加保护：
     * - 仅在每天 06:00~10:00 之间执行
     * - 每日仅发送一次（本地 Status 标记 + 远端 familyTaskTips 双重判断）
     * - 自动从家庭成员列表中移除自己，避免接口报参数错误
     *
     * @param familyUserIds 家庭成员 userId 列表（包含自己，方法内部会移除当前账号）
     */
    private void deliverMsgSend(JSONArray familyAnimalsExceptUser, List<String> familyUserIds) {
        try {
            // 时间窗口控制：仅允许在「早安时间段」内自动发送（06:00 ~ 10:00）
            Calendar now = Calendar.getInstance();
            Calendar startTime = Calendar.getInstance();
            startTime.set(Calendar.HOUR_OF_DAY, 6);
            startTime.set(Calendar.MINUTE, 0);
            startTime.set(Calendar.SECOND, 0);
            startTime.set(Calendar.MILLISECOND, 0);

            Calendar endTime = Calendar.getInstance();
            endTime.set(Calendar.HOUR_OF_DAY, 10);
            endTime.set(Calendar.MINUTE, 0);
            endTime.set(Calendar.SECOND, 0);
            endTime.set(Calendar.MILLISECOND, 0);

            if (now.before(startTime) || now.after(endTime)) {
                //Log.record("家庭任务🏠道早安#当前时间不在 06:00-10:00，跳过");
                return;
            }

            if (StringUtil.isEmpty(ownerGroupId)) {
                Log.record("家庭任务🏠道早安#未检测到家庭 groupId，可能尚未加入家庭，跳过");
                return;
            }

            // 本地去重：一天只发送一次
            if (Status.hasFlagToday("antFarm::deliverMsgSend")) {
                //Log.record("家庭任务🏠道早安#今日已在本地发送过，跳过");
                return;
            }

            // 远端任务状态校验
            try {
                JSONObject taskTipsRes = new JSONObject(AntFarmRpcCall.familyTaskTips(familyAnimalsExceptUser));
                if (!MessageUtil.checkMemo(TAG, taskTipsRes)) {
                    Log.record("家庭任务🏠道早安#familyTaskTips 调用失败，跳过");
                    return;
                }

                JSONArray taskTips = taskTipsRes.optJSONArray("familyTaskTips");
                if (taskTips == null || taskTips.length() == 0) {
                    Log.record("家庭任务🏠道早安#远端无 GREETING 任务，可能今日已完成，跳过");

                    Status.flagToday("antFarm::deliverMsgSend");
                    return;
                }

                boolean hasGreetingTodo = false;
                for (int i = 0; i < taskTips.length(); i++) {
                    JSONObject item = taskTips.getJSONObject(i);
                    String bizKey = item.optString("bizKey");
                    String taskStatus = item.optString("taskStatus");
                    if ("GREETING".equals(bizKey) && "TODO".equals(taskStatus)) {
                        hasGreetingTodo = true;
                        break;
                    }
                }

                if (!hasGreetingTodo) {
                    Log.record("家庭任务🏠道早安#GREETING 任务非 TODO 状态，跳过");
                    Status.flagToday("antFarm::deliverMsgSend");
                    return;
                }
            } catch (Throwable e) {
                Log.printStackTrace("familyTaskTips 解析失败，出于安全考虑跳过道早安：", e);
                return;
            }

            // 构建好友 userId 列表（去掉自己）
            List<String> userIdsCopy = new ArrayList<>(familyUserIds);
            userIdsCopy.remove(UserIdMap.getCurrentUid());
            if (userIdsCopy.isEmpty()) {
                Log.record("家庭任务🏠道早安#家庭成员仅自己一人，跳过");
                return;
            }

            JSONArray userIds = new JSONArray();
            for (String userId : userIdsCopy) {
                userIds.put(userId);
            }

            // 确认 AI 隐私协议
            JSONObject resp0 = new JSONObject(AntFarmRpcCall.OpenAIPrivatePolicy());
            if (!MessageUtil.checkMemo(TAG, resp0)) {
                Log.record("家庭任务🏠道早安#OpenAIPrivatePolicy 调用失败");
                return;
            }

            // 请求推荐早安场景
            JSONObject resp1 = new JSONObject(AntFarmRpcCall.deliverSubjectRecommend(userIds));
            if (!MessageUtil.checkMemo(TAG, resp1)) {
                Log.record("家庭任务🏠道早安#deliverSubjectRecommend 调用失败");
                return;
            }

            String ariverRpcTraceId = resp1.getString("ariverRpcTraceId");
            String eventId = resp1.getString("eventId");
            String eventName = resp1.getString("eventName");
            String memo = resp1.optString("memo");
            String resultCode = resp1.optString("resultCode");
            String sceneId = resp1.getString("sceneId");
            String sceneName = resp1.getString("sceneName");
            boolean success = resp1.optBoolean("success", true);

            // 调用 DeliverContentExpand
            JSONObject resp2 = new JSONObject(AntFarmRpcCall.deliverContentExpand(ariverRpcTraceId, eventId, eventName, memo, resultCode, sceneId, sceneName, success, userIds));
            if (!MessageUtil.checkMemo(TAG, resp2)) {
                Log.record("家庭任务🏠道早安#DeliverContentExpand 调用失败");
                return;
            }

            String deliverId = resp2.getString("deliverId");
            //String deliverId = System.currentTimeMillis()+UserIdMap.getCurrentUid();

            // 使用 deliverId 确认扩展内容
            JSONObject resp3 = new JSONObject(AntFarmRpcCall.QueryExpandContent(deliverId));
            if (!MessageUtil.checkMemo(TAG, resp3)) {
                Log.record("家庭任务🏠道早安#QueryExpandContent 调用失败");
                return;
            }

            String content = resp3.getString("content");

            // 最终发送早安消息
            JSONObject resp4 = new JSONObject(AntFarmRpcCall.deliverMsgSend(ownerGroupId, userIds, content, deliverId));
            if (MessageUtil.checkMemo(TAG, resp4)) {
                Log.farm("家庭任务🌈[道早安]" + content);
                Status.flagToday("antFarm::deliverMsgSend");
            }
        } catch (Throwable t) {
            Log.i(TAG, "deliverMsgSend err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 好友分享家庭
     */
    private void familyShareToFriends(String ownerGroupId, List<String> familyUserIds, SelectModelField notInviteList) {
        try {
            if (Status.hasFlagToday("antFarm::familyShareToFriends")) {
                return;
            }

            Set<String> notInviteSet = notInviteList.getValue();
            List<AlipayUser> allUser = AlipayUser.getList();
            if (allUser.isEmpty()) {
                Log.record("allUser is empty");
                return;
            }

            // 打乱顺序，实现随机选取
            List<AlipayUser> shuffledUsers = new ArrayList<>(allUser);
            Collections.shuffle(shuffledUsers);
            JSONArray inviteList = new JSONArray();
            for (AlipayUser user : shuffledUsers) {
                if (!familyUserIds.contains(user.getId()) && !notInviteSet.contains(user.getId()) && (user.getId() != UserIdMap.getCurrentUid())) {
                    inviteList.put(user.getId());
                    if (inviteList.length() >= 2) {
                        break;
                    }
                }
            }

            if (inviteList.length() == 0) {
                Log.record("没有符合分享条件的好友");
                return;
            }
            Log.record("家庭分享🏠邀请:" + inviteList);

            //JSONObject jo = new JSONObject(AntFarmRpcCall.inviteFriendVisitFamily(inviteList));
            for (int i = 0; i < inviteList.length(); i++) {
                String inviteUID = inviteList.getString(i);
                JSONObject jo = new JSONObject(AntFarmRpcCall.batchInviteP2P(ownerGroupId, inviteUID));
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    Log.farm("家庭任务🏠分享给好友[" + UserIdMap.getShowName(inviteUID) + "]");
                }
            }
            Status.flagToday("antFarm::familyShareToFriends");
        } catch (Throwable t) {
            Log.i(TAG, "familyShareToFriends err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 时间差格式化
     */
    private String formatDuration(long diffMillis) {
        long absSeconds = Math.abs(diffMillis) / 1000;

        long value;
        String unit;
        if (absSeconds < 60) {
            value = absSeconds;
            unit = "秒";
        } else if (absSeconds < 3600) {
            value = absSeconds / 60;
            unit = "分钟";
        } else if (absSeconds < 86400) {
            value = absSeconds / 3600;
            unit = "小时";
        } else if (absSeconds < 2592000) {
            value = absSeconds / 86400;
            unit = "天";
        } else if (absSeconds < 31536000) {
            value = absSeconds / 2592000;
            unit = "个月";
        } else {
            value = absSeconds / 31536000;
            unit = "年";
        }

        if (absSeconds < 1) {
            return "刚刚";
        } else if (diffMillis > 0) {
            return value + unit + "后";
        } else {
            return value + unit + "前";
        }
    }

    private String getFamilyGroupId(String userId) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.queryLoveCabin(userId));
            if (MessageUtil.checkMemo(TAG, jo)) {
                return jo.optString("groupId");
            }
        } catch (Throwable t) {
            Log.i(TAG, "getGroupId err:");
            Log.printStackTrace(t);
        }
        return null;
    }

    private JSONObject enterFamily() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.enterFamily());
            if (MessageUtil.checkMemo(TAG, jo)) {
                return jo;
            }
        } catch (Throwable t) {
            Log.i(TAG, "enterFamily err:");
            Log.printStackTrace(TAG, t);
        }
        return null;
    }

    private Boolean familySleep(String groupId) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.familySleep(groupId));
            if (MessageUtil.checkMemo(TAG, jo)) {
                Log.farm("亲密家庭🏠小鸡睡觉");
                syncFamilyStatus(groupId);
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "familySleep err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private Boolean familyWakeUp() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.familyWakeUp());
            if (MessageUtil.checkMemo(TAG, jo)) {
                Log.farm("亲密家庭🏠小鸡起床");
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "familyWakeUp err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private void familyAwardList() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.familyAwardList());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONArray ja = jo.getJSONArray("familyAwardRecordList");
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                if (jo.optBoolean("expired") || jo.optBoolean("received", true) || jo.has("linkUrl") || (jo.has("operability") && !jo.getBoolean("operability"))) {
                    continue;
                }
                String rightId = jo.getString("rightId");
                String awardName = jo.getString("awardName");
                int count = jo.optInt("count", 1);
                receiveFamilyAward(rightId, awardName, count);
            }
        } catch (Throwable t) {
            Log.i(TAG, "familyAwardList err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void receiveFamilyAward(String rightId, String awardName, int count) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.receiveFamilyAward(rightId));
            if (MessageUtil.checkMemo(TAG, jo)) {
                Log.farm("亲密家庭🏠领取奖励[" + awardName + "*" + count + "]");
            }
        } catch (Throwable t) {
            Log.i(TAG, "familyAwardList err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void familyReceiveFarmTaskAward(String taskId, String title) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.familyReceiveFarmTaskAward(taskId));
            if (MessageUtil.checkMemo(TAG, jo)) {
                Log.farm("亲密家庭🏠提交任务[" + title + "]");
            }
        } catch (Throwable t) {
            Log.i(TAG, "familyReceiveFarmTaskAward err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private JSONArray queryRecentFarmFood(int needCount) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.syncAnimalStatus(ownerFarmId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return null;
            }
            JSONArray cuisineList = jo.getJSONArray("cuisineList");
            if (cuisineList.length() == 0) {
                return null;
            }
            List<JSONObject> list = getSortedCuisineList(cuisineList);
            JSONArray result = new JSONArray();
            int count = 0;
            for (int i = 0; i < list.size() && count < needCount; i++) {
                jo = list.get(i);
                int countTemp = jo.getInt("count");
                if (count + countTemp >= needCount) {
                    countTemp = needCount - count;
                    jo.put("count", countTemp);
                }
                count += countTemp;
                result.put(jo);
            }
            if (count == needCount) {
                return result;
            }
        } catch (Throwable t) {
            Log.i(TAG, "queryRecentFarmFood err:");
            Log.printStackTrace(TAG, t);
        }
        return null;
    }

    private void familySign() {
        familyReceiveFarmTaskAward("FAMILY_SIGN_TASK", "每日签到");
    }

    private void syncFamilyStatus(String groupId) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.syncFamilyStatus(groupId, "INTIMACY_VALUE", ownerUserId));
            MessageUtil.checkMemo(TAG, jo);
        } catch (Throwable t) {
            Log.i(TAG, "syncFamilyStatus err:");
            Log.printStackTrace(TAG, t);
        }
    }

    public interface RecallAnimalType {

        int ALWAYS = 0;
        int WHEN_THIEF = 1;
        int WHEN_HUNGRY = 2;
        int NEVER = 3;

        String[] nickNames = {"始终召回", "偷吃召回", "饥饿召回", "暂不召回"};
    }

    public interface SendBackAnimalWay {

        int HIT = 0;
        int NORMAL = 1;

        String[] nickNames = {"攻击", "常规"};
    }

    public interface SendBackAnimalType {

        int NONE = 0;
        int BACK = 1;
        int NOT_BACK = 2;

        String[] nickNames = {"不遣返小鸡", "遣返已选好友", "遣返未选好友"};
    }

    public enum AnimalBuff {
        ACCELERATING, INJURED, NONE
    }

    public enum AnimalFeedStatus {
        HUNGRY, EATING, SLEEPY
    }

    public enum AnimalInteractStatus {
        HOME, GOTOSTEAL, STEALING
    }

    public enum SubAnimalType {
        NORMAL, GUEST, PIRATE, WORK
    }

    public enum ToolType {
        STEALTOOL, ACCELERATETOOL, SHARETOOL, FENCETOOL, NEWEGGTOOL, DOLLTOOL, BIG_EATER_TOOL, ADVANCE_ORNAMENT_TOOL, ORDINARY_ORNAMENT_TOOL, RARE_ORNAMENT_TOOL;

        public static final CharSequence[] nickNames = {"蹭饭卡", "加速卡", "救济卡", "篱笆卡", "新蛋卡", "公仔补签卡", "加饭卡", "高级装扮补签", "普通装扮补签卡", "稀有装扮补签卡"};

        public CharSequence nickName() {
            return nickNames[ordinal()];
        }
    }

    public enum GameType {
        starGame, jumpGame, flyGame, hitGame;

        public static final CharSequence[] gameNames = {"星星球", "登山赛", "飞行赛", "欢乐揍小鸡"};

        public CharSequence gameName() {
            return gameNames[ordinal()];
        }
    }

    private static class Animal {
        public String animalId, currentFarmId, masterFarmId, animalBuff, subAnimalType, animalFeedStatus, animalInteractStatus;
        public String locationType;

        public String currentFarmMasterUserId;

        public Long startEatTime, beHiredEndTime;

        public Double consumeSpeed;

        public Double foodHaveEatten;
    }

    public enum TaskStatus {
        TODO, FINISHED, RECEIVED
    }

    private static class RewardFriend {
        public String consistencyKey, friendId, time;
    }

    private static class FarmTool {
        public ToolType toolType;
        public String toolId;
        public int toolCount, toolHoldLimit;
    }

    public interface HireAnimalType {

        int NONE = 0;
        int HIRE = 1;
        int NOT_HIRE = 2;

        String[] nickNames = {"不雇佣小鸡", "雇佣已选好友", "雇佣未选好友"};
    }

    //  public interface GetFeedType {

    //      int NONE = 0;
    //      int GIVE = 1;
    //     int RANDOM = 2;

    //    String[] nickNames = {"不赠送饲料", "赠送已选好友", "赠送随机好友"};
    // }

    public interface NotifyFriendType {

        int NONE = 0;
        int NOTIFY = 1;
        int NOT_NOTIFY = 2;

        String[] nickNames = {"不通知赶鸡", "通知已选好友", "通知未选好友"};
    }

    public interface DonationType {

        int ZERO = 0;
        int ONE = 1;
        int ALL = 2;

        String[] nickNames = {"不捐赠", "捐赠一个项目", "捐赠所有项目"};
    }

    public enum ItemStatus {
        NO_ENOUGH_POINT, REACH_LIMIT, REACH_USER_HOLD_LIMIT;

        public static final String[] nickNames = {"乐园币不足", "兑换达到上限", "达到用户持有上限"};

        public String nickName() {
            return nickNames[ordinal()];
        }
    }
}