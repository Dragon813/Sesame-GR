package io.github.lazyimmortal.sesame.model.task.antSports;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import io.github.lazyimmortal.sesame.data.ConfigV2;
import io.github.lazyimmortal.sesame.data.ModelFields;
import io.github.lazyimmortal.sesame.data.ModelGroup;
import io.github.lazyimmortal.sesame.data.TokenConfig;
import io.github.lazyimmortal.sesame.data.modelFieldExt.BooleanModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.IntegerModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectModelField;
import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.entity.AlipayAntSportsTaskList;
import io.github.lazyimmortal.sesame.entity.WalkPathThemeMapList;
import io.github.lazyimmortal.sesame.entity.AlipayUser;
import io.github.lazyimmortal.sesame.entity.WalkPath;
import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.hook.Toast;
import io.github.lazyimmortal.sesame.model.base.TaskCommon;
import io.github.lazyimmortal.sesame.model.extensions.ExtensionsHandle;
import io.github.lazyimmortal.sesame.model.task.antStall.AntStall;
import io.github.lazyimmortal.sesame.model.task.antStall.AntStallRpcCall;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.RandomUtil;
import io.github.lazyimmortal.sesame.util.Status;
import io.github.lazyimmortal.sesame.util.StringUtil;
import io.github.lazyimmortal.sesame.util.TimeUtil;
import io.github.lazyimmortal.sesame.util.idMap.AntSportsTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.AntStallTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.PathThemeMapListMap;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

public class AntSports extends ModelTask {
    
    private static final String TAG = AntSports.class.getSimpleName();
    
    private int tmpStepCount = -1;
    private BooleanModelField walk;
    private ChoiceModelField PathThemeMapList;
    private BooleanModelField receiveCoinAsset;
    private ChoiceModelField donateCharityCoinType;
    private IntegerModelField donateCharityCoinAmount;
    private BooleanModelField coinExchangeDoubleCard;
    private IntegerModelField minExchangeCount;
    private IntegerModelField earliestSyncStepTime;
    private IntegerModelField latestExchangeTime;
    private IntegerModelField syncStepCount;
    private BooleanModelField tiyubiz;
    private BooleanModelField club;
    private ChoiceModelField clubTrainItemType;
    private ChoiceModelField clubTradeMemberType;
    private SelectModelField clubTradeMemberList;
    private BooleanModelField sportsTasks;
    private BooleanModelField AutoAntSportsTaskList;
    private SelectModelField AntSportsTaskList;
    private BooleanModelField neverLand;
    
    // 处理签到
    private BooleanModelField QUERY_SIGN;
    // 处理任务中心
    
    private BooleanModelField QUERY_TASK_CENTER;
    
    // 处理气泡任务
    private BooleanModelField QUERY_BUBBLE_TASK;
    
    // 兑换权益
    private BooleanModelField QUERY_ITEM_LIST;
    
    //能量泵
    private BooleanModelField WALK_GRID;
    
    private IntegerModelField WALK_GRID_LIMIT;
    
    private IntegerModelField WALK_GRID_MAX;
    
    private BooleanModelField MapListSwitch;
    
    private BooleanModelField awardspecialActivityReceive;
    
    //private SelectModelField neverLandOptions;
    private SelectModelField neverLandBenefitList;
    private ChoiceModelField energyStrategy;
    
    @Override
    public String getName() {
        return "运动";
    }
    
    @Override
    public ModelGroup getGroup() {
        return ModelGroup.SPORTS;
    }
    
    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(walk = new BooleanModelField("walk", "行走路线 | 开启", false));
        // modelFields.addField(PathThemeMapList = new SelectModelField("PathThemeMapList", "行走路线 | 路线主题", new LinkedHashSet<>(), WalkPathThemeMapList::getList, "请选择要行走的主题，选择多条则随机走其中一个主题下的路线"));
        // 确保 nickNames 和 values 已初始化
        WalkPathThemeMapList.getList();
        modelFields.addField(PathThemeMapList = new ChoiceModelField("PathThemeMapList", "行走路线 | 路线主题", 0, WalkPathThemeMapList.nickNames));
        //modelFields.addField(walkCustomPathIdList = new SelectModelField("walkCustomPathIdList", "行走路线 | 自定义路线列表", new LinkedHashSet<>(), WalkPath::getThemeListFromRpc, "请选择要行走的路线，选择多条则随机走其中一条"));
        modelFields.addField(sportsTasks = new BooleanModelField("sportsTasks", "运动任务", false));
        modelFields.addField(AutoAntSportsTaskList = new BooleanModelField("AutoAntSportsTaskList", "运动任务 | 自动黑白名单", true));
        modelFields.addField(AntSportsTaskList = new SelectModelField("AntSportsTaskList", "运动任务 | 黑名单列表", new LinkedHashSet<>(), AlipayAntSportsTaskList::getList));
        modelFields.addField(receiveCoinAsset = new BooleanModelField("receiveCoinAsset", "收运动币", false));
        modelFields.addField(donateCharityCoinType = new ChoiceModelField("donateCharityCoinType", "捐运动币 | 方式", DonateCharityCoinType.ZERO, DonateCharityCoinType.nickNames));
        modelFields.addField(donateCharityCoinAmount = new IntegerModelField("donateCharityCoinAmount", "捐运动币 | 数量" + "(每次)", 100));
        modelFields.addField(coinExchangeDoubleCard = new BooleanModelField("coinExchangeDoubleCard", "运动币兑换限时能量双击卡", false));
        modelFields.addField(club = new BooleanModelField("club", "抢好友 | 开启", false));
        modelFields.addField(clubTrainItemType = new ChoiceModelField("clubTrainItemType", "抢好友 | 训练动作", TrainItemType.NONE, TrainItemType.nickNames));
        modelFields.addField(clubTradeMemberType = new ChoiceModelField("clubTradeMemberType", "抢好友 | 抢购动作", TradeMemberType.NONE, TradeMemberType.nickNames));
        modelFields.addField(clubTradeMemberList = new SelectModelField("clubTradeMemberList", "抢好友 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(tiyubiz = new BooleanModelField("tiyubiz", "文体中心", false));
        modelFields.addField(syncStepCount = new IntegerModelField("syncStepCount", "同步步数 | 自定义", 22000));
        modelFields.addField(earliestSyncStepTime = new IntegerModelField("earliestSyncStepTime", "同步步数 | 最早同步时间(24小时制)", 0, 0, 23));
        modelFields.addField(latestExchangeTime = new IntegerModelField("latestExchangeTime", "行走捐 | 最晚捐步时间(24小时制)", 22));
        modelFields.addField(minExchangeCount = new IntegerModelField("minExchangeCount", "行走捐 | 最小捐步步数", 10));
        modelFields.addField(neverLand = new BooleanModelField("neverLand", "健康岛 | 开启", false));
        modelFields.addField(QUERY_SIGN = new BooleanModelField("QUERY_SIGN", "健康岛 | 每日签到", false));
        modelFields.addField(QUERY_TASK_CENTER = new BooleanModelField("QUERY_TASK_CENTER", "健康岛 | 做任务 加能量", false));
        modelFields.addField(QUERY_BUBBLE_TASK = new BooleanModelField("QUERY_BUBBLE_TASK", "健康岛 | 领取能量球奖励", false));
        modelFields.addField(QUERY_ITEM_LIST = new BooleanModelField("QUERY_ITEM_LIST", "健康岛 | 健康能量兑好礼", false));
        modelFields.addField(WALK_GRID = new BooleanModelField("WALK_GRID", "健康岛 | 能量泵", false));
        modelFields.addField(WALK_GRID_MAX = new IntegerModelField("WALK_GRID_MAX", "健康岛 | 单次执行能量泵最大次数(不限:0)", 5));
        modelFields.addField(WALK_GRID_LIMIT = new IntegerModelField("WALK_GRID_LIMIT", "健康岛 | 使用能量泵剩余能量值(低于该值停止使用)", 10000));
        modelFields.addField(MapListSwitch = new BooleanModelField("MapListSwitch", "健康岛 | 自动切岛", false));
        modelFields.addField(awardspecialActivityReceive = new BooleanModelField("awardspecialActivityReceive", "健康岛 | 领取活动岛奖励", false));
        return modelFields;
    }
    
    public static final String DISPLAY_NAME = "悦动健康岛";
    public static final ModelGroup MODULE_GROUP = ModelGroup.SPORTS;
    
    @Override
    public void boot(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod("com.alibaba.health.pedometer.core.datasource.PedometerAgent", classLoader, "readDailyStep", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    int originStep = (Integer) param.getResult();
                    int step = tmpStepCount();
                    if (TaskCommon.IS_AFTER_6AM && originStep < step) {
                        param.setResult(step);
                    }
                }
            });
            Log.i(TAG, "hook readDailyStep successfully");
        }
        catch (Throwable t) {
            Log.i(TAG, "hook readDailyStep err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.other("任务暂停⏸️支付宝运动:当前为仅收能量时间");
            return false;
        }
        return true;
    }
    
    @Override
    public void run() {
        try {
            int hour = Integer.parseInt(Log.getFormatTime().split(":")[0]);
            if (!Status.hasFlagToday("sport::syncStep") && hour >= earliestSyncStepTime.getValue()) {
                //if (!Status.hasFlagToday("sport::syncStep")) {
                addChildTask(new ChildModelTask("syncStep", () -> {
                    int step = tmpStepCount();
                    try {
                        ClassLoader classLoader = ApplicationHook.getClassLoader();
                        if ((Boolean) XposedHelpers.callMethod(XposedHelpers.callStaticMethod(classLoader.loadClass("com.alibaba.health.pedometer.intergation.rpc.RpcManager"), "a"), "a", new Object[]{step, Boolean.FALSE, "system"})) {
                            Toast.show("同步步数🏃🏻‍♂️[" + step + "步]");
                            Log.other("同步步数🏃🏻‍♂️[" + step + "步]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                            Status.flagToday("sport::syncStep");
                        }
                        else {
                            Log.record("同步运动步数失败:" + step);
                        }
                    }
                    catch (Throwable t) {
                        Log.printStackTrace(TAG, t);
                    }
                }));
            }
            
            if (walk.getValue()) {
                walk(syncStepCount.getValue());
            }
            
            //初始任务列表
            if (!Status.hasFlagToday("BlackList::initAntSports")) {
                initAntSportsTaskListMap(AutoAntSportsTaskList.getValue(), sportsTasks.getValue());
                Status.flagToday("BlackList::initAntSports");
            }

            //初始化行走主题列表
            if (!Status.hasFlagToday("WalkPathTheme::init")) {
                initWalkPathThemeMap();
                Status.flagToday("WalkPathTheme::init");
            }
            
            if (donateCharityCoinType.getValue() != DonateCharityCoinType.ZERO) {
                queryProjectList();
            }
            
            if (coinExchangeDoubleCard.getValue()) {
                coinExchangeItem("AMS2024032927086104");
            }
            
            if (minExchangeCount.getValue() > 0) {
                queryWalkStep();
            }
            
            if (tiyubiz.getValue()) {
                userTaskGroupQuery("SPORTS_DAILY_SIGN_GROUP");
                userTaskGroupQuery("SPORTS_DAILY_GROUP");
                userTaskRightsReceive();
                pathFeatureQuery();
                //{"error":3000,"errorMessage":"系统出错，正在排查","errorNo":3,"errorTip":"3000"}
                //participate();
            }
            
            if (club.getValue()) {
                queryClubHome();
            }
            
            if (sportsTasks.getValue()) {
                sportsTasks();
            }
            
            if (receiveCoinAsset.getValue()) {
                receiveCoinAsset();
                AntSportsRpcCall.pickAllEnergyBall();
            }
            
            //执行悦动健康岛
            //if (neverLand.getValue() && checkAuth()) {
            if (neverLand.getValue()) {
                neverlandrun();
            }
            
        }
        catch (Throwable t) {
            Log.i(TAG, "start.run err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    public int tmpStepCount() {
        if (tmpStepCount >= 0) {
            return tmpStepCount;
        }
        tmpStepCount = syncStepCount.getValue();
        if (tmpStepCount > 0) {
            tmpStepCount = RandomUtil.nextInt(tmpStepCount, tmpStepCount + 2000);
            if (tmpStepCount > 100000) {
                tmpStepCount = 100000;
            }
        }
        return tmpStepCount;
    }
    
    public static void initAntSportsTaskListMap(boolean AutoAntSportsTaskList, boolean sportsTasks) {
        try {
            //初始化AntSportsTaskListMap
            AntSportsTaskListMap.load();
            Set<String> blackList = new HashSet<>();
            blackList.add("下载登录AI健康管家");
            
            Set<String> whiteList = new HashSet<>();// 从黑名单中移除该任务
            //whiteList.add("逛一逛树");
            for (String task : blackList) {
                AntSportsTaskListMap.add(task, task);
            }
            
            if (sportsTasks) {
                JSONObject jo = new JSONObject(AntSportsRpcCall.queryCoinTaskPanel());
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    jo = jo.getJSONObject("data");
                    if (jo.has("taskList")) {
                        JSONArray taskLists = jo.getJSONArray("taskList");
                        for (int i = 0; i < taskLists.length(); i++) {
                            JSONObject taskList = taskLists.getJSONObject(i);
                            String taskName = taskList.getString("taskName");
                            AntSportsTaskListMap.add(taskName, taskName);
                        }
                    }
                }
                
                //保存任务到配置文件
                AntSportsTaskListMap.save();
                Log.record("同步任务🉑运动任务列表");
                
                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntSportsTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields AntSports = config.getModelFieldsMap().get("AntSports");
                    SelectModelField AntSportsTaskList = (SelectModelField) AntSports.get("AntSportsTaskList");
                    if (AntSportsTaskList == null) {
                        return;
                    }
                    // 2. 批量添加黑名单任务（确保存在）
                    Set<String> currentValues = AntSportsTaskList.getValue();//该处直接返回列表地址
                    if (currentValues != null) {
                        for (String task : blackList) {
                            if (!currentValues.contains(task)) {
                                AntSportsTaskList.add(task, 0);
                            }
                        }
                    }
                    currentValues = AntSportsTaskList.getValue();//该处直接返回列表地址
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
                        Log.record("黑白名单🈲运动任务自动设置: " + AntSportsTaskList.getValue());
                    }
                    else {
                        Log.record("运动任务黑白名单设置失败");
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "initSportsTaskListMap err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void initWalkPathThemeMap() {
        //初始化PathThemeMapListMap
        PathThemeMapListMap.load();
        try {
            String result = AntSportsRpcCall.queryThemeList();
            JSONObject jo = new JSONObject(result);
            JSONObject data = jo.optJSONObject("data");
            if (data != null) {
                JSONArray themeList = data.optJSONArray("themeList");
                if (themeList != null) {
                    for (int i = 0; i < themeList.length(); i++) {
                        JSONObject theme = themeList.optJSONObject(i);
                        String themeId = theme.optString("themeId");
                        String themeName = theme.optString("themeName");
                        if (themeId != null && !themeId.isEmpty() && themeName != null && !themeName.isEmpty()) {
                            PathThemeMapListMap.add(themeId, themeName);
                        }
                    }
                }
            }
            PathThemeMapListMap.save();
            Log.record("同步路线主题" + PathThemeMapListMap.getMap());

        } catch (Throwable t) {
            Log.i(TAG, "initWalkPathThemeMap err:");
            Log.printStackTrace(TAG, t);
        }

    }
    
    // 运动
    private void sportsTasks() {
        try {
            signInCoinTask();
            JSONObject jo = new JSONObject(AntSportsRpcCall.queryCoinTaskPanel());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("data");
            if (!jo.has("taskList")) {
                return;
            }
            JSONArray taskList = jo.getJSONArray("taskList");
            for (int i = 0; i < taskList.length(); i++) {
                jo = taskList.getJSONObject(i);
                String taskName = jo.getString("taskName");
                String taskStatus = jo.getString("taskStatus");
                if (TaskStatus.HAS_RECEIVED.name().equals(taskStatus)) {
                    return;
                }
                
                if (TaskStatus.WAIT_RECEIVE.name().equals(taskStatus)) {
                    String assetId = jo.getString("assetId");
                    int prizeAmount = jo.getInt("prizeAmount");
                    if (receiveCoinAsset(assetId, prizeAmount, taskName)) {
                        TimeUtil.sleep(1000);
                    }
                    continue;
                }
                //黑名单任务跳过
                if (AntSportsTaskList.getValue().contains(taskName)) {
                    continue;
                }
                if (!jo.has("taskAction")) {
                    continue;
                }
                if (TaskStatus.WAIT_COMPLETE.name().equals(taskStatus)) {
                    String taskAction = jo.getString("taskAction");
                    String taskId = jo.getString("taskId");
                    if (jo.optBoolean("multiTask")) {
                        int currentNum = jo.getInt("currentNum") + 1;
                        int limitConfigNum = jo.getInt("limitConfigNum");
                        taskName = taskName.replaceAll("（.*/.*）", "(" + currentNum + "/" + limitConfigNum + ")");
                    }
                    if (jo.optBoolean("needSignUp") && !signUpTask(taskId)) {
                        continue;
                    }
                    if (completeTask(taskAction, taskId, taskName)) {
                        TimeUtil.sleep(2000);
                    }
                    continue;
                }
                
                Log.record("Found New Sport TaskStatus:" + taskStatus);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "sportsTasks err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private Boolean signUpTask(String taskId) {
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.signUpTask(taskId));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "signUpTask err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private Boolean completeTask(String taskAction, String taskId, String taskName) {
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.completeTask(taskAction, taskId));
            //检查并标记黑名单任务
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntSportsTaskList", taskName, jo);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.other("运动任务🧾完成[得运动币:" + taskName + "]");
                TimeUtil.sleep(1000);
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "completeTask err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private void signInCoinTask() {
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.signInCoinTask());
            
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            JSONObject data = jo.getJSONObject("data");
            if (!data.getBoolean("signed")) {
                JSONObject subscribeConfig;
                if (data.has("subscribeConfig")) {
                    subscribeConfig = data.getJSONObject("subscribeConfig");
                    Log.other("运动任务🧾[做任务得运动币:签到" + subscribeConfig.getString("subscribeExpireDays") + "天]奖励" + data.getString("toast") + "运动币");
                }
                else {
                    //                        Log.record("没有签到");
                }
            }
            else {
                Log.record("运动签到今日已签到");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "signInCoinTask err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void receiveCoinAsset() {
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.queryCoinBubbleModule());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            JSONObject data = jo.getJSONObject("data");
            if (!data.has("recBubbleList")) {
                return;
            }
            JSONArray ja = data.getJSONArray("recBubbleList");
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                if (!data.has("assetId")) {
                    return;
                }
                String assetId = jo.getString("assetId");
                int coinAmount = jo.getInt("coinAmount");
                String simpleSourceName = jo.optString("simpleSourceName");
                if (receiveCoinAsset(assetId, coinAmount, simpleSourceName)) {
                    TimeUtil.sleep(500);
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "receiveCoinAsset err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private Boolean receiveCoinAsset(String assetId, int coinAmount, String title) {
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.receiveCoinAsset(assetId));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.other("运动中心🧊领取[" + title + "]奖励[" + coinAmount + "运动能量]");
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "receiveCoinAsset err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    /*
     * 新版行走路线 -- begin
     */
    private void walk(int syncStepCount) {
        String goingPathId = queryGoingPathId();
        do {
            String tempPathId = (String) ExtensionsHandle.handleAlphaRequest("antSports", "walk", null);
            if (tempPathId != null) {
                goingPathId = tempPathId;
            }
            TimeUtil.sleep(1000);
            if (isNeedJoinNewPath(goingPathId)) {
                String joinPathId = queryJoinPathId();
                if (checkJoinPathId(joinPathId)) {
                    if (!joinPath(joinPathId)) {
                        return;
                    }
                    goingPathId = joinPathId;
                }
            }
        }
        while (walkGo(queryPath(goingPathId), syncStepCount));
    }
    
    private Boolean isNeedJoinNewPath(String goingPathId) {
        if (goingPathId.isEmpty()) {
            return true;
        }
        try {
            JSONObject jo = queryPath(goingPathId);
            jo = jo.getJSONObject("userPathStep");
            if (jo.optBoolean("dayLimit")) {
                return true;
            }
            String pathCompleteStatus = jo.getString("pathCompleteStatus");
            if (PathCompleteStatus.COMPLETED.name().equals(pathCompleteStatus)) {
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "isNeedJoinNewPath err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private Boolean hasTreasureBox() {
        if (Status.hasFlagToday("sport::treasureBoxLimit")) {
            return false;
        }
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.queryMailList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray ja = jo.getJSONArray("userMailList");
            int count = 0;
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                if (!"SPORTSPROD_GOPATH_AWARD_BOX".equals(jo.getString("templateId"))) {
                    continue;
                }
                if (!TimeUtil.isToday(jo.getLong("receiveTime"))) {
                    break;
                }
                count++;
            }
            if (count < 20) {
                return true;
            }
            Status.flagToday("sport::treasureBoxLimit");
        }
        catch (Throwable t) {
            Log.i(TAG, "hasTreasureBox err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private Boolean walkGo(JSONObject pathData, int syncStepCount) {
        //按照每天走路20次收获宝箱奖励得健康能量
        int MIN_STEP_FOR_TREASURE = 500;
        int MAX_STEP_FOR_TREASURE = 1000;
        if (syncStepCount > 20000) {
            int walkcountmax = syncStepCount / 20;
            int walkcountmin = (syncStepCount - 10000) / 20;
            MAX_STEP_FOR_TREASURE = walkcountmax;
            MIN_STEP_FOR_TREASURE = walkcountmin;
        }
        try {
            if (pathData == null || !pathData.has("path")) {
                return false;
            }
            JSONObject path = pathData.getJSONObject("path");
            JSONObject userPathStep = pathData.getJSONObject("userPathStep");
            int minGoStepCount = path.getInt("minGoStepCount");
            int pathStepCount = path.getInt("pathStepCount");
            if (path.has("dailyMaxGoStepCount")) {
                pathStepCount = path.getInt("dailyMaxGoStepCount");
            }
            int forwardStepCount = userPathStep.getInt("forwardStepCount");
            int remainStepCount = userPathStep.getInt("remainStepCount");
            boolean dayLimit = userPathStep.getBoolean("dayLimit");
            int useStepCount = Math.min(Math.min(remainStepCount, hasTreasureBox() ? RandomUtil.nextInt(MIN_STEP_FOR_TREASURE, MAX_STEP_FOR_TREASURE) : remainStepCount), Math.max(pathStepCount - forwardStepCount % pathStepCount, minGoStepCount));
            if (useStepCount < minGoStepCount || dayLimit) {
                return false;
            }
            String pathId = path.getString("pathId");
            String pathName = path.getString("name");
            return walkGo(pathName, pathId, useStepCount);
        }
        catch (Throwable t) {
            Log.i(TAG, "walkGo err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private Boolean walkGo(String pathName, String pathId, int useStepCount) {
        boolean result = false;
        try {
            String date = Log.getFormatDate();
            JSONObject jo = new JSONObject(AntSportsRpcCall.walkGo(date, pathId, useStepCount));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                result = true;
                Log.other("行走路线🚶🏻‍♂️行走[" + pathName + "]#前进了" + useStepCount + "步");
                jo = jo.getJSONObject("data");
                if (jo.has("completeInfo")) {
                    Log.other("行走路线🚶🏻‍♂️完成[" + pathName + "]");
                }
                parseRewardsByJSONObjectData(jo);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "walkGo err:");
            Log.printStackTrace(TAG, t);
        }
        return result;
    }
    
    private JSONObject queryWorldMap(String themeId) {
        JSONObject theme = null;
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.queryWorldMap(themeId));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                theme = jo.getJSONObject("data");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryWorldMap err:");
            Log.printStackTrace(TAG, t);
        }
        return theme;
    }
    
    private JSONObject queryCityPath(String cityId) {
        JSONObject city = null;
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.queryCityPath(cityId));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                city = jo.getJSONObject("data");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryCityPath err:");
            Log.printStackTrace(TAG, t);
        }
        return city;
    }
    
    private static JSONObject queryPath(String pathId) {
        JSONObject path = null;
        try {
            String date = Log.getFormatDate();
            JSONObject jo = new JSONObject(AntSportsRpcCall.queryPath(date, pathId));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                path = jo.getJSONObject("data");
                parseRewardsByJSONObjectData(path);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryPath err:");
            Log.printStackTrace(TAG, t);
        }
        return path;
    }
    
    private static void openTreasureBox(JSONArray treasureBoxList) {
        try {
            for (int i = 0; i < treasureBoxList.length(); i++) {
                JSONObject treasureBox = treasureBoxList.getJSONObject(i);
                receiveEvent(treasureBox.getString("boxNo"));
                TimeUtil.sleep(1000);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "openTreasureBox err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void receiveEvent(String eventBillNo) {
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.receiveEvent(eventBillNo));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                jo = jo.getJSONObject("data");
                parseRewardsByJSONArrayRewards(jo.getJSONArray("rewards"), 0);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "receiveEvent err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void parseRewardsByJSONArrayRewards(JSONArray rewards, int rewardsType) {
        String rewardsTypeName;
        switch (rewardsType) {
            case 0:
                rewardsTypeName = "宝箱奖励";
                break;
            case 1:
                rewardsTypeName = "中奖奖励";
                break;
            case 2:
                rewardsTypeName = "终点奖励";
                break;
            default:
                rewardsTypeName = "未知奖励";
                break;
        }
        try {
            for (int i = 0; i < rewards.length(); i++) {
                JSONObject jo = rewards.getJSONObject(i);
                if (jo.has("rewardStatus") && !"SUCCESS".equals(jo.getString("rewardStatus"))) {
                    // rewardStatus : SUCCESS NOT_HIT
                    continue;
                }
                Log.other("行走路线🚶🏻‍♂️收获" + rewardsTypeName + "[" + jo.getString("rewardName") + "*" + jo.getInt("count") + "]");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "parseRewardsByJSONArrayRewards err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void parseRewardsByJSONObjectData(JSONObject data) {
        try {
            JSONArray treasureBoxList = data.getJSONArray("treasureBoxList");
            openTreasureBox(treasureBoxList);
            if (data.has("brandRewardVOs")) {
                JSONArray brandRewardVOs = data.getJSONArray("brandRewardVOs");
                parseRewardsByJSONArrayRewards(brandRewardVOs, 1);
            }
            if (data.has("completeInfo")) {
                data = data.getJSONObject("completeInfo");
                JSONArray completeRewards = data.getJSONArray("completeRewards");
                parseRewardsByJSONArrayRewards(completeRewards, 2);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "parseRewardsByJSONObjectData err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private String queryGoingPathId() {
        String goingPathId = "";
        try {
            String date = Log.getFormatDate();
            JSONObject jo = new JSONObject(AntSportsRpcCall.queryPath(date, ""));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                jo = jo.getJSONObject("data");
                goingPathId = jo.optString("goingPathId");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryGoingPathId err:");
            Log.printStackTrace(TAG, t);
        }
        return goingPathId;
    }
    
    private String queryJoinPathId() {
        String pathId = null;

        try {
            int index = PathThemeMapList.getValue();
            if (index < 0 || index >= WalkPathThemeMapList.values.length) {
                return pathId;
            }
            String themeId = WalkPathThemeMapList.values[index];
            JSONObject theme = queryWorldMap(themeId);
            if (theme == null) {
                return pathId;
            }
            JSONArray cityList = theme.getJSONArray("cityList");
            for (int i = 0; i < cityList.length(); i++) {
                String cityId = cityList.getJSONObject(i).getString("cityId");
                if (cityId.equals("000000") || cityId.equals("232700") || cityId.equals("620900") || cityId.equals("653100") || cityId.equals("710100")) {
                    continue;
                }
                JSONObject city = queryCityPath(cityId);
                if (city == null) {
                    continue;
                }
                JSONArray cityPathList = city.getJSONArray("cityPathList");
                for (int j = 0; j < cityPathList.length(); j++) {
                    JSONObject cityPath = cityPathList.getJSONObject(j);
                    pathId = cityPath.getString("pathId");
                    String pathCompleteStatus = cityPath.optString("pathCompleteStatus");
                    if (!PathCompleteStatus.COMPLETED.name().equals(pathCompleteStatus)) {
                        return pathId;
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryJoinPathId err:");
            Log.printStackTrace(TAG, t);
        }
        return pathId;
    }
    
    public static Boolean checkJoinPathId(String joinPathId) {
        try {
            JSONObject jo = queryPath(joinPathId);
            String goingPathId = jo.optString("goingPathId");
            if (Objects.equals(goingPathId, joinPathId)) {
                return false;
            }
            jo = jo.getJSONObject("userPathStep");
            return !jo.optBoolean("dayLimit");
        }
        catch (Throwable t) {
            Log.i(TAG, "checkJoinPathId err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    public static Boolean joinPath(String pathId) {
        if (pathId == null) {
            // 守护体育梦
            pathId = "p000202408231708";
        }
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.joinPath(pathId));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                JSONObject pathData = queryPath(pathId);
                String pathName = pathData.getJSONObject("path").getString("name");
                Log.other("行走路线🚶🏻‍♂️加入[" + pathName + "]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "joinPath err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    /*
     * 新版行走路线 -- end
     */
    private Boolean canDonateCharityCoinToday() {
        if (Status.hasFlagToday("sport::donateCharityCoin")) {
            return false;
        }
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.queryDonateRecord());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray footballFieldLongModel = jo.getJSONArray("footballFieldLongModel");
            if (footballFieldLongModel.length() == 0) {
                return true;
            }
            jo = footballFieldLongModel.getJSONObject(0);
            jo = jo.getJSONObject("personStatModel");
            long lastDonationTime = jo.getLong("lastDonationTime");
            if (TimeUtil.isLessThanNowOfDays(lastDonationTime)) {
                return true;
            }
            Status.flagToday("sport::donateCharityCoin");
        }
        catch (Throwable t) {
            Log.i(TAG, "canDonateCharityCoinToday err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private void queryProjectList() {
        if (!canDonateCharityCoinToday()) {
            return;
        }
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.queryProjectList(0));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            int charityCoinCount = jo.getInt("charityCoinCount");
            int donateCharityCoin = donateCharityCoinAmount.getValue();
            if (charityCoinCount < donateCharityCoin) {
                return;
            }
            JSONArray ja = jo.getJSONObject("projectPage").getJSONArray("data");
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i).getJSONObject("basicModel");
                if (jo.optInt("acwProjectStatus") == 0) {
                    // acwProjectStatus: 0 1
                    continue;
                }
                // footballFieldStatus: OPENING_DONATE DONATE_COMPLETED
                if ("DONATE_COMPLETED".equals(jo.getString("footballFieldStatus"))) {
                    break;
                }
                if (donate(donateCharityCoin, jo.getString("projectId"), jo.getString("title"))) {
                    charityCoinCount -= donateCharityCoin;
                    if (donateCharityCoinType.getValue() != DonateCharityCoinType.ALL) {
                        break;
                    }
                    if (charityCoinCount < donateCharityCoin) {
                        break;
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryProjectList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private Boolean donate(int donateCharityCoin, String projectId, String title) {
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.donate(donateCharityCoin, projectId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.other("公益捐赠❤️[捐赠运动币:" + title + "]捐赠" + donateCharityCoin + "运动币");
                
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "donate err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private Boolean canDonateWalkExchangeToday() {
        if (Status.hasFlagToday("sport::donateWalk")) {
            return false;
        }
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.donateExchangeRecord());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray userExchangeRecords = jo.getJSONArray("userExchangeRecords");
            if (userExchangeRecords.length() == 0) {
                return true;
            }
            jo = userExchangeRecords.getJSONObject(0);
            long gmtCreate = jo.getLong("gmtCreate");
            if (TimeUtil.isLessThanNowOfDays(gmtCreate)) {
                return true;
            }
            Status.flagToday("sport::donateWalk");
        }
        catch (Throwable t) {
            Log.i(TAG, "canDonateWalkExchangeToday err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private void queryWalkStep() {
        if (!canDonateWalkExchangeToday()) {
            return;
        }
        if (Status.hasFlagToday("sport::donateWalk")) {
            return;
        }
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.queryWalkStep());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            //jo = jo.getJSONObject("dailyStepModel");
            //long stepLastTime = jo.getLong("stepLastTime");
            int hour = Integer.parseInt(Log.getFormatTime().split(":")[0]);
            
            int stepCount = jo.optInt("stepCount");
            if (stepCount < minExchangeCount.getValue() && hour < latestExchangeTime.getValue()) {
                return;
            }
            AntSportsRpcCall.walkDonateSignInfo(stepCount);
            jo = new JSONObject(AntSportsRpcCall.donateWalkHome(stepCount));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject walkDonateHomeModel = jo.getJSONObject("walkDonateHomeModel");
            JSONObject walkUserInfoModel = walkDonateHomeModel.getJSONObject("walkUserInfoModel");
            if (!walkUserInfoModel.has("exchangeFlag")) {
                return;
            }
            
            String donateToken = walkDonateHomeModel.getString("donateToken");
            JSONObject walkCharityActivityModel = walkDonateHomeModel.getJSONObject("walkCharityActivityModel");
            String activityId = walkCharityActivityModel.getString("activityId");
            
            jo = new JSONObject(AntSportsRpcCall.donateWalkExchange(activityId, stepCount, donateToken));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject donateExchangeResultModel = jo.getJSONObject("donateExchangeResultModel");
            int userCount = donateExchangeResultModel.getInt("userCount");
            double amount = donateExchangeResultModel.getJSONObject("userAmount").getDouble("amount");
            String donateTitle = donateExchangeResultModel.getString("donateTitle");
            Log.other("公益捐赠❤️[捐步做公益:" + donateTitle + "]捐赠" + userCount + "步,兑换" + amount + "元公益金");
            Status.flagToday("sport::donateWalk");
            
        }
        catch (Throwable t) {
            Log.i(TAG, "queryWalkStep err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /* 文体中心 */
    // SPORTS_DAILY_SIGN_GROUP SPORTS_DAILY_GROUP
    private void userTaskGroupQuery(String groupId) {
        try {
            String s = AntSportsRpcCall.userTaskGroupQuery(groupId);
            JSONObject jo = new JSONObject(s);
            if (jo.optBoolean("success")) {
                jo = jo.getJSONObject("group");
                JSONArray userTaskList = jo.getJSONArray("userTaskList");
                for (int i = 0; i < userTaskList.length(); i++) {
                    jo = userTaskList.getJSONObject(i);
                    if (!"TODO".equals(jo.getString("status"))) {
                        continue;
                    }
                    JSONObject taskInfo = jo.getJSONObject("taskInfo");
                    String bizType = taskInfo.getString("bizType");
                    String taskId = taskInfo.getString("taskId");
                    jo = new JSONObject(AntSportsRpcCall.userTaskComplete(bizType, taskId));
                    if (jo.optBoolean("success")) {
                        String taskName = taskInfo.optString("taskName", taskId);
                        Log.other("文体中心🧾完成任务[" + taskName + "]");
                    }
                    else {
                        Log.record("文体每日任务" + " " + jo);
                    }
                }
            }
            else {
                Log.record("文体每日任务" + " " + s);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "userTaskGroupQuery err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void participate() {
        try {
            String s = AntSportsRpcCall.queryAccount();
            JSONObject jo = new JSONObject(s);
            if (jo.optBoolean("success")) {
                double balance = jo.getDouble("balance");
                if (balance < 100) {
                    return;
                }
                jo = new JSONObject(AntSportsRpcCall.queryRoundList());
                if (jo.optBoolean("success")) {
                    JSONArray dataList = jo.getJSONArray("dataList");
                    for (int i = 0; i < dataList.length(); i++) {
                        jo = dataList.getJSONObject(i);
                        if (!"P".equals(jo.getString("status"))) {
                            continue;
                        }
                        if (jo.has("userRecord")) {
                            continue;
                        }
                        JSONArray instanceList = jo.getJSONArray("instanceList");
                        int pointOptions = 0;
                        String roundId = jo.getString("id");
                        String InstanceId = null;
                        String ResultId = null;
                        for (int j = instanceList.length() - 1; j >= 0; j--) {
                            jo = instanceList.getJSONObject(j);
                            if (jo.getInt("pointOptions") < pointOptions) {
                                continue;
                            }
                            pointOptions = jo.getInt("pointOptions");
                            InstanceId = jo.getString("id");
                            ResultId = jo.getString("instanceResultId");
                        }
                        jo = new JSONObject(AntSportsRpcCall.participate(pointOptions, InstanceId, ResultId, roundId));
                        if (jo.optBoolean("success")) {
                            jo = jo.getJSONObject("data");
                            String roundDescription = jo.getString("roundDescription");
                            int targetStepCount = jo.getInt("targetStepCount");
                            Log.other("走路挑战🚶🏻‍♂️[" + roundDescription + "]#" + targetStepCount);
                        }
                        else {
                            Log.record("走路挑战赛" + " " + jo);
                        }
                    }
                }
                else {
                    Log.record("queryRoundList" + " " + jo);
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "participate err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void userTaskRightsReceive() {
        try {
            String s = AntSportsRpcCall.userTaskGroupQuery("SPORTS_DAILY_GROUP");
            JSONObject jo = new JSONObject(s);
            if (jo.optBoolean("success")) {
                jo = jo.getJSONObject("group");
                JSONArray userTaskList = jo.getJSONArray("userTaskList");
                for (int i = 0; i < userTaskList.length(); i++) {
                    jo = userTaskList.getJSONObject(i);
                    if (!"COMPLETED".equals(jo.getString("status"))) {
                        continue;
                    }
                    String userTaskId = jo.getString("userTaskId");
                    JSONObject taskInfo = jo.getJSONObject("taskInfo");
                    String taskId = taskInfo.getString("taskId");
                    jo = new JSONObject(AntSportsRpcCall.userTaskRightsReceive(taskId, userTaskId));
                    if (jo.optBoolean("success")) {
                        String taskName = taskInfo.optString("taskName", taskId);
                        JSONArray rightsRuleList = taskInfo.getJSONArray("rightsRuleList");
                        StringBuilder award = new StringBuilder();
                        for (int j = 0; j < rightsRuleList.length(); j++) {
                            jo = rightsRuleList.getJSONObject(j);
                            award.append(jo.getString("rightsName")).append("*").append(jo.getInt("baseAwardCount"));
                        }
                        Log.other("领取奖励🎖️[" + taskName + "]#" + award);
                    }
                    else {
                        Log.record("文体中心领取奖励");
                        Log.i(jo.toString());
                    }
                }
            }
            else {
                Log.record("文体中心领取奖励");
                Log.i(s);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "userTaskRightsReceive err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void pathFeatureQuery() {
        try {
            String s = AntSportsRpcCall.pathFeatureQuery();
            JSONObject jo = new JSONObject(s);
            if (jo.optBoolean("success")) {
                JSONObject path = jo.getJSONObject("path");
                String pathId = path.getString("pathId");
                String title = path.getString("title");
                int minGoStepCount = path.getInt("minGoStepCount");
                if (jo.has("userPath")) {
                    JSONObject userPath = jo.getJSONObject("userPath");
                    String userPathRecordStatus = userPath.getString("userPathRecordStatus");
                    if ("COMPLETED".equals(userPathRecordStatus)) {
                        pathMapHomepage(pathId);
                        pathMapJoin(title, pathId);
                    }
                    else if ("GOING".equals(userPathRecordStatus)) {
                        pathMapHomepage(pathId);
                        String countDate = Log.getFormatDate();
                        jo = new JSONObject(AntSportsRpcCall.stepQuery(countDate, pathId));
                        if (jo.optBoolean("success")) {
                            int canGoStepCount = jo.getInt("canGoStepCount");
                            if (canGoStepCount >= minGoStepCount) {
                                String userPathRecordId = userPath.getString("userPathRecordId");
                                tiyubizGo(countDate, title, canGoStepCount, pathId, userPathRecordId);
                            }
                        }
                    }
                }
                else {
                    pathMapJoin(title, pathId);
                }
            }
            else {
                Log.i(TAG, jo.getString("resultDesc"));
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "pathFeatureQuery err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void pathMapHomepage(String pathId) {
        try {
            String s = AntSportsRpcCall.pathMapHomepage(pathId);
            JSONObject jo = new JSONObject(s);
            if (jo.optBoolean("success")) {
                if (!jo.has("userPathGoRewardList")) {
                    return;
                }
                JSONArray userPathGoRewardList = jo.getJSONArray("userPathGoRewardList");
                for (int i = 0; i < userPathGoRewardList.length(); i++) {
                    jo = userPathGoRewardList.getJSONObject(i);
                    if (!"UNRECEIVED".equals(jo.getString("status"))) {
                        continue;
                    }
                    String userPathRewardId = jo.getString("userPathRewardId");
                    jo = new JSONObject(AntSportsRpcCall.rewardReceive(pathId, userPathRewardId));
                    if (jo.optBoolean("success")) {
                        jo = jo.getJSONObject("userPathRewardDetail");
                        JSONArray rightsRuleList = jo.getJSONArray("userPathRewardRightsList");
                        StringBuilder award = new StringBuilder();
                        for (int j = 0; j < rightsRuleList.length(); j++) {
                            jo = rightsRuleList.getJSONObject(j).getJSONObject("rightsContent");
                            award.append(jo.getString("name")).append("*").append(jo.getInt("count"));
                        }
                        Log.other("文体宝箱🎁[" + award + "]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                    }
                    else {
                        Log.record("文体中心开宝箱");
                        Log.i(jo.toString());
                    }
                }
            }
            else {
                Log.record("文体中心开宝箱");
                Log.i(s);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "pathMapHomepage err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void pathMapJoin(String title, String pathId) {
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.pathMapJoin(pathId));
            if (jo.optBoolean("success")) {
                Log.other("加入线路🚶🏻‍♂️[" + title + "]");
                pathFeatureQuery();
            }
            else {
                Log.i(TAG, jo.toString());
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "pathMapJoin err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void tiyubizGo(String countDate, String title, int goStepCount, String pathId, String userPathRecordId) {
        try {
            String s = AntSportsRpcCall.tiyubizGo(countDate, goStepCount, pathId, userPathRecordId);
            JSONObject jo = new JSONObject(s);
            if (jo.optBoolean("success")) {
                jo = jo.getJSONObject("userPath");
                Log.other("行走线路🚶🏻‍♂️[" + title + "]#前进了" + jo.getInt("userPathRecordForwardStepCount") + "步");
                pathMapHomepage(pathId);
                boolean completed = "COMPLETED".equals(jo.getString("userPathRecordStatus"));
                if (completed) {
                    Log.other("完成线路🚶🏻‍♂️[" + title + "]");
                    pathFeatureQuery();
                }
            }
            else {
                Log.i(TAG, s);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "tiyubizGo err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    // 抢好友大战
    // 俱乐部首页，执行抢好友、训练动作、抢购等操作（具体逻辑依赖配置的 clubTrainItemType、clubTradeMemberType 等）
    private void queryClubHome() {
        try {
            // 收运动能量
            JSONObject joBubble = new JSONObject(AntSportsRpcCall.queryClubHome());
            if (!MessageUtil.checkResultCode(TAG, joBubble)) {
                return;
            }
            JSONObject mainRoom = joBubble.getJSONObject("mainRoom");
            if (mainRoom.has("bubbleList")) {
                JSONArray bubbleList = mainRoom.getJSONArray("bubbleList");
                for (int k = 0; k < bubbleList.length(); k++) {
                    String bubbleId = bubbleList.getJSONObject(k).getString("bubbleId");
                    collectBubble(bubbleId, "[买卖]");
                    TimeUtil.sleep(200);
                }
            }
            
            JSONObject jo = new JSONObject(AntSportsRpcCall.queryClubHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray roomList = jo.getJSONArray("roomList");
            for (int i = 0; i < roomList.length(); i++) {
                // 检查可以购买好友的房号i
                JSONObject room = roomList.getJSONObject(i);
                String roomId = room.getString("roomId");
                
                // 收取训练好友能量
                if (room.has("bubbleList")) {
                    JSONArray roombubbleList = room.getJSONArray("bubbleList");
                    for (int l = 0; l < roombubbleList.length(); l++) {
                        String bubbleId = roombubbleList.getJSONObject(l).getString("bubbleId");
                        // 收取第i号房间需要收取训练好友的第l个能量球
                        collectBubble(bubbleId, "[训练]");
                        TimeUtil.sleep(200);
                    }
                }
                
                if (room.getJSONArray("memberList").length() != 0) {
                    continue;
                }
                
                // 购买好友
                if (clubTradeMemberType.getValue() != TradeMemberType.NONE) {
                    queryMemberPriceRanking(roomId);
                    TimeUtil.sleep(200);
                }
            }
            TimeUtil.sleep(200);
            
            // 训练好友
            JSONObject joTrain = new JSONObject(AntSportsRpcCall.queryClubHome());
            if (!MessageUtil.checkResultCode(TAG, joTrain)) {
                return;
            }
            JSONArray roomListTrain = joTrain.getJSONArray("roomList");
            for (int j = 0; j < roomListTrain.length(); j++) {
                JSONObject roomTrain = roomListTrain.getJSONObject(j);
                if (roomTrain.getJSONArray("memberList").length() != 0) {
                    JSONObject member = roomTrain.getJSONArray("memberList").getJSONObject(0);
                    trainMember(member);
                    TimeUtil.sleep(1000);
                }
            }
            
            //蹲点训练好友
            JSONObject autoTrain = new JSONObject(AntSportsRpcCall.queryClubHome());
            if (!MessageUtil.checkResultCode(TAG, autoTrain)) {
                return;
            }
            roomListTrain = autoTrain.getJSONArray("roomList");
            for (int j = 0; j < roomListTrain.length(); j++) {
                JSONObject roomTrain = roomListTrain.getJSONObject(j);
                String roomId = roomTrain.getString("roomId");
                if (roomTrain.getJSONArray("memberList").length() != 0) {
                    JSONObject member = roomTrain.getJSONArray("memberList").getJSONObject(0);
                    JSONObject trainInfo = member.getJSONObject("trainInfo");
                    if (trainInfo.has("gmtEnd")) {
                        Long gmtEnd = trainInfo.getLong("gmtEnd");
                        long updateTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
                        addChildTask(new ChildModelTask(roomId, "", () -> {
                            autoTrainMember(roomId, gmtEnd);
                        }, updateTime));
                    }
                    TimeUtil.sleep(200);
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryClubHome err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    // 抢好友大战-收集运动能量
    private void collectBubble(String bubbleId, String bubbleType) {
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.collectBubble(bubbleId));
            if (jo.optBoolean("success")) {
                JSONObject ja = jo.getJSONObject("data");
                String collectCoin = ja.getString("changeAmount");
                Log.other("好友大战🧊收取" + bubbleType + "获得[" + collectCoin + "运动能量]" + "#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "collectBubble err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    // 抢好友大战-训练好友
    private void trainMember(JSONObject member) {
        try {
            String memberId = member.getString("memberId");
            String originBossId = member.getString("originBossId");
            JSONObject trainInfo = member.getJSONObject("trainInfo");
            
            String userName = UserIdMap.getShowName(originBossId);
            if (!trainInfo.getBoolean("training")) {
                String itemType = TrainItemType.itemTypes[clubTrainItemType.getValue()];
                if (StringUtil.isEmpty(itemType)) {
                    return;
                }
                
                String name = TrainItemType.nickNames[clubTrainItemType.getValue()];
                JSONObject queryTrainItemjo = new JSONObject(AntSportsRpcCall.queryTrainItem());
                if (!MessageUtil.checkResultCode(TAG, queryTrainItemjo)) {
                    return;
                }
                
                // 可以翻倍训练
                if (queryTrainItemjo.has("bizId")) {
                    String bizId = queryTrainItemjo.getString("bizId");
                    String taskAction = "SHOW_AD";
                    queryTrainItemjo = queryTrainItemjo.getJSONObject("taskDetail");
                    String taskId = queryTrainItemjo.getString("taskId");
                    JSONObject jo = new JSONObject(AntSportsRpcCall.DoubletrainMember(itemType, bizId, memberId, originBossId));
                    Log.other("好友大战💪训练[" + userName + "]" + name + "[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        return;
                    }
                    TimeUtil.sleep(7000);
                    jo = new JSONObject(AntSportsRpcCall.duublecompleteTask(bizId, taskAction, taskId));
                    if (!MessageUtil.checkSuccess(TAG, jo)) {
                        return;
                    }
                    Log.other("好友大战💪翻倍训练[" + userName + "]" + name + "[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                }
                else {
                    JSONObject jo = new JSONObject(AntSportsRpcCall.trainMember(itemType, memberId, originBossId));
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        return;
                    }
                    Log.other("好友大战💪训练[" + userName + "]" + name + "[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                }
            }
            
        }
        catch (Throwable t) {
            Log.i(TAG, "trainMember err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    // 抢好友大战-蹲点训练
    private void autoTrainMember(String roomId, Long gmtEnd) {
        String taskId = "TRAIN|" + roomId;
        if (!hasChildTask(taskId)) {
            addChildTask(new ChildModelTask(taskId, "TRAIN", () -> {
                AntSportsRpcCall.queryClubRoom(roomId);
            }, gmtEnd));
            int roomIdInt = Integer.parseInt(roomId.substring(2, 8));
            Log.record("蹲点训练💪添加[" + roomIdInt + "号房]在[" + TimeUtil.getCommonDate(gmtEnd) + "]执行");
        }
    }
    
    // 抢好友大战-抢购好友
    private void queryMemberPriceRanking(String roomId) {
        int energyBalance;
        try {
            JSONObject jo1 = new JSONObject(AntSportsRpcCall.queryClubHome());
            if (!MessageUtil.checkResultCode(TAG, jo1)) {
                return;
            }
            JSONObject assetsInfo = jo1.getJSONObject("assetsInfo");
            energyBalance = assetsInfo.getInt("energyBalance");
            TimeUtil.sleep(200);
            JSONObject jo = new JSONObject(AntSportsRpcCall.queryMemberPriceRankingEnergy(energyBalance));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            energyBalance = jo.getInt("energyBalance");
            jo = jo.getJSONObject("rank");
            JSONArray ja = jo.getJSONArray("data");
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                int price = jo.getInt("price");
                if (price > energyBalance) {
                    continue;
                }
                String originBossId = jo.getString("originBossId");
                String currentBossId = jo.getString("currentBossId");
                
                // 判断如果老板是当前账号则查找下一个
                if (currentBossId.equals(UserIdMap.getCurrentUid())) {
                    continue;
                }
                
                // 判断是否为购买列表中的好友
                boolean isTradeMember = clubTradeMemberList.getValue().contains(originBossId);
                // 判断是选中购买还是未选中购买
                if (clubTradeMemberType.getValue() != TradeMemberType.TRADE) {
                    isTradeMember = !isTradeMember;
                }
                if (!isTradeMember) {
                    continue;
                }
                
                // 标识为可购买的好友，如果在当前账户的训练房间中则标识为false
                boolean canbuyMember = true;
                JSONObject joTrain = new JSONObject(AntSportsRpcCall.queryClubHome());
                if (!MessageUtil.checkResultCode(TAG, joTrain)) {
                    return;
                }
                JSONArray roomListTrain = joTrain.getJSONArray("roomList");
                for (int j = 0; j < roomListTrain.length(); j++) {
                    JSONObject roomTrain = roomListTrain.getJSONObject(j);
                    if (roomTrain.getJSONArray("memberList").length() != 0) {
                        JSONObject member = roomTrain.getJSONArray("memberList").getJSONObject(0);
                        if (originBossId.equals(member.getString("originBossId"))) {
                            canbuyMember = false;
                        }
                    }
                }
                // 不管是否购买好友成功，都返回继续检测下一个房间
                if (canbuyMember) {
                    buyMember(roomId, queryClubMember(jo));
                    return;
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryMemberPriceRanking err:");
            Log.printStackTrace(TAG, t);
        }
        return;
    }
    
    private JSONObject queryClubMember(JSONObject member) {
        try {
            String memberId = member.getString("memberId");
            String originBossId = member.getString("originBossId");
            JSONObject jo = new JSONObject(AntSportsRpcCall.queryClubMember(memberId, originBossId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONObject priceInfo = jo.getJSONObject("member").getJSONObject("priceInfo");
                member.put("priceInfo", priceInfo);
                
                return member;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryClubMember err:");
            Log.printStackTrace(TAG, t);
        }
        return null;
    }
    
    private Boolean buyMember(String roomId, JSONObject member) {
        if (member == null) {
            return false;
        }
        try {
            String currentBossId = member.getString("currentBossId");
            String currentBossShowName = UserIdMap.getShowName(currentBossId) != null ? UserIdMap.getShowName(currentBossId) : currentBossId;
            String memberId = member.getString("memberId");
            String originBossId = member.getString("originBossId");
            JSONObject priceInfo = member.getJSONObject("priceInfo");
            JSONObject jo = new JSONObject(AntSportsRpcCall.buyMember(currentBossId, memberId, originBossId, priceInfo, roomId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                String userName = UserIdMap.getShowName(originBossId);
                int price = member.getInt("price");
                Log.other("好友大战🉐抢购[" + userName + "]来自[" + currentBossShowName + "]花费[" + price + "健康能量]" + "#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                Toast.show("好友大战🉐抢购[" + userName + "]来自[" + currentBossShowName + "]花费[" + price + "健康能量]");
                return true;
            }
            else {
                return false;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "buyMember err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private void coinExchangeItem(String itemId) {
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.queryItemDetail(itemId));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("data");
            if (!"OK".equals(jo.optString("exchangeBtnStatus"))) {
                return;
            }
            jo = jo.getJSONObject("itemBaseInfo");
            String itemTitle = jo.getString("itemTitle");
            int valueCoinCount = jo.getInt("valueCoinCount");
            jo = new JSONObject(AntSportsRpcCall.exchangeItem(itemId, valueCoinCount));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("data");
            if (jo.optBoolean("exgSuccess")) {
                Log.other("运动好礼🎐兑换[" + itemTitle + "]花费" + valueCoinCount + "运动币");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "trainMember err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 领取特殊奖励
     *
     * @param sceneType  场景类型
     * @param rewardName 奖励名称
     */
    public static void receiveSpecialPrize(String sceneType, String rewardName) {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.receiveSpecialPrize(sceneType));
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.getJSONObject("data");
                int energy = data.getInt("modifyCount");
                if (energy > 0) {
                    Log.other("悦动健康🚑️领取奖励[" + rewardName + "]#获得[" + energy + "g健康能量]");
                }
            }
        }
        catch (Exception e) {
            Log.i(TAG, "receiveSpecialPrize err:");
            Log.printStackTrace(TAG, e);
        }
    }
    
    /**
     * 签到
     *
     * @return 是否签到成功
     */
    public static boolean signIn() {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.takeSign());
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.getJSONObject("data");
                int continuousDay = data.getJSONObject("continuousSignInfo").getInt("continuitySignedDayCount");
                int reward = data.getJSONObject("continuousDoSignInVO").getInt("rewardAmount");
                Log.other("悦动健康🚑️连续签到[第" + continuousDay + "天]#获得[" + reward + "g健康能量]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                return true;
            }
        }
        catch (Exception e) {
            Log.i(TAG, "takeSign err:");
            Log.printStackTrace(TAG, e);
        }
        return false;
    }
    
    /**
     * 领取任务奖励
     *
     * @param task 任务JSON对象
     * @return 是否领取成功
     */
    public static boolean receiveTaskReward(JSONObject task) {
        try {
            task.put("scene", "MED_TASK_HALL").put("source", "jkdprizesign");
            String arg = "[" + task.toString() + "]";
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.neverlandtaskReceive(arg));
            
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                String taskName = task.getString("title");
                JSONObject data = jsonResult.getJSONObject("data");
                JSONArray rewards = data.getJSONArray("userItems");
                ArrayList<String> rewardList = parseRewards(rewards);
                Log.other("悦动健康🚑️领取奖励[" + taskName + "]#获得" + rewardList);
                return true;
            }
        }
        catch (Exception e) {
            Log.i(TAG, "taskReceive err:");
            Log.printStackTrace(TAG, e);
        }
        return false;
    }
    
    /**
     * 完成任务
     *
     * @param task 任务JSON对象
     * @return 是否完成成功
     */
    public static boolean completeTask(JSONObject task) {
        try {
            task.put("scene", "MED_TASK_HALL");
            String arg = "[" + task.toString() + "]";
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.neverlandtaskSend(arg));
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                String taskName = task.getString("title");
                Log.other("悦动健康🚑️完成任务[" + taskName + "]");
                TimeUtil.sleep(1000);
                return true;
            }
        }
        catch (Exception e) {
            Log.i(TAG, "taskSend err:");
            Log.printStackTrace(TAG, e);
        }
        return false;
    }
    
    /**
     * 能量泵前进
     *
     * @param branchId 分支ID
     * @param mapId    地图ID
     * @param mapName  地图名称
     * @return 是否继续前进
     */
    public static boolean walkGrid(String branchId, String mapId, String mapName) {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.neverlandwalkGrid(branchId, mapId));
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.getJSONObject("data");
                int step = data.getJSONArray("mapAwards").getJSONObject(0).getInt("step");
                int leftCount = data.getInt("leftCount");
                Log.other("悦动健康🚑️能量泵[" + mapName + "]#前进[" + step + "步]");
                
                JSONArray rewards = data.getJSONArray("userItems");
                ArrayList<String> rewardList = parseRewards(rewards);
                if (!rewardList.isEmpty()) {
                    Log.other("悦动健康🚑️能量泵[" + mapName + "]#获得" + rewardList);
                }
                
                int currentStar = data.getJSONObject("starData").getInt("curr");
                int totalStar = data.getJSONObject("starData").getInt("count");
                return leftCount >= 5 && currentStar < totalStar;
            }
        }
        catch (Exception e) {
            Log.i(TAG, "walkGrid err:");
            Log.printStackTrace(TAG, e);
        }
        return false;
    }
    
    public static int build(String branchId, String mapId, String mapName, int multiNum) {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.build(branchId, mapId, multiNum));
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.getJSONObject("data");
                JSONObject endStageInfo = data.getJSONObject("endStageInfo");
                int buildingEnergyFinal = endStageInfo.optInt("buildingEnergyFinal");
                String buildingId = endStageInfo.optString("buildingId");
                int endbuildingEnergyProcess = endStageInfo.optInt("buildingEnergyProcess");
                Log.other("悦动健康🚑️能量泵[" + mapName + "]建造[" + buildingId + "]进度(" + endbuildingEnergyProcess + "/" + buildingEnergyFinal + ")#消耗" + multiNum * 5 + "g能量");
                JSONArray rewards = data.getJSONArray("rewards");
                ArrayList<String> rewardList = parseRewards(rewards);
                if (!rewardList.isEmpty()) {
                    Log.other("悦动健康🚑️能量泵[" + mapName + "]#获得" + rewardList);
                }
                return buildingEnergyFinal - endbuildingEnergyProcess;
            }
        }
        catch (Exception e) {
            Log.i(TAG, "build err:");
            Log.printStackTrace(TAG, e);
        }
        return 0;
    }
    
    /**
     * 领取浏览任务奖励
     *
     * @param task 任务JSON对象
     * @return 是否领取成功
     */
    public static boolean receiveBrowseReward(JSONObject task) {
        if (!task.has("encryptValue") || !task.has("energyNum")) {
            return false;
        }
        
        try {
            task.put("type", "LIGHT_FEEDS_TASK");
            String arg = "[" + task.toString() + "]";
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.neverlandenergyReceive(arg));
            
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.getJSONObject("data");
                JSONArray prizes = data.getJSONArray("prizes");
                int totalEnergy = 0;
                for (int i = 0; i < prizes.length(); i++) {
                    totalEnergy += prizes.getJSONObject(i).getInt("prizeCount");
                }
                
                String taskName = task.optString("title", "浏览商品15s得健康能量");
                Log.other("悦动健康🚑️完成任务[" + taskName + "]#获得[" + totalEnergy + "g健康能量]");
                return true;
            }
        }
        catch (Exception e) {
            Log.i(TAG, "energyReceive err:");
            Log.printStackTrace(TAG, e);
        }
        return false;
    }
    
    /**
     * 领取离线奖励
     */
    public static void receiveOfflineReward() {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.offlineAward());
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.getJSONObject("data");
                JSONArray rewards = data.getJSONArray("userItems");
                ArrayList<String> rewardList = parseRewards(rewards);
                
                if (!rewardList.isEmpty()) {
                    Log.other("悦动健康🚑️领取奖励[离线奖励]#获得" + rewardList);
                }
            }
        }
        catch (Exception e) {
            Log.i(TAG, "offlineAward err:");
            Log.printStackTrace(TAG, e);
        }
    }
    
    /**
     * 解析奖励列表
     *
     * @param rewards 奖励JSON数组
     * @return 格式化后的奖励列表
     */
    public static ArrayList<String> parseRewards(JSONArray rewards) {
        ArrayList<String> rewardList = new ArrayList<>();
        try {
            for (int i = 0; i < rewards.length(); i++) {
                JSONObject reward = rewards.getJSONObject(i);
                int count = reward.optInt("modifyCount");
                if (count <= 0) {
                    continue;
                }
                
                String unit = "H1".equals(reward.getString("itemId")) ? "g" : "";
                String name = reward.getString("name");
                rewardList.add(count + unit + name);
            }
        }
        catch (Exception e) {
            Log.i(TAG, "parseRewards err:");
            Log.printStackTrace(TAG, e);
        }
        return rewardList;
    }
    
    /**
     * 领取气泡任务奖励
     *
     * @param recordId   记录ID
     * @param rewardName 奖励名称
     */
    public static void receiveBubbleReward(String recordId, String rewardName) {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.neverlandpickBubbleTaskEnergy(recordId));
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.getJSONObject("data");
                String energy = data.getString("changeAmount");
                Log.other("悦动健康🚑️领取奖励[" + rewardName + "]#获得[" + energy + "g健康能量]");
            }
        }
        catch (Exception e) {
            Log.i(TAG, "pickBubbleTaskEnergy err:");
            Log.printStackTrace(TAG, e);
        }
    }
    
    /**
     * 查询基础信息并处理相关任务
     */
    public void queryBaseInfoAndProcess() {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.queryBaseinfo());
            if (!MessageUtil.checkSuccess(TAG, jsonResult)) {
                return;
            }
            JSONObject data = jsonResult.getJSONObject("data");
            // 处理离线奖励
            if (data.getJSONArray("offlineAwards").length() > 0) {
                receiveOfflineReward();
            }
            
            // 处理普通岛能量泵任务
            if (!data.optBoolean("newGame") && WALK_GRID.getValue()) {
                String branchId = data.getString("branchId");
                String mapId = data.getString("mapId");
                String mapName = data.getString("mapName");
                int walkGridcount = 0;
                if (canWalkGrid(branchId, mapId) && queryUserEnergy() >= 5 && queryUserEnergy() >= WALK_GRID_LIMIT.getValue()) {
                    while (walkGrid(branchId, mapId, mapName)) {
                        TimeUtil.sleep(2000);
                        if (WALK_GRID_MAX.getValue() == 0) {
                            continue;
                        }
                        walkGridcount++;
                        if (walkGridcount >= WALK_GRID_MAX.getValue() || queryUserEnergy() < 5 || queryUserEnergy() <= WALK_GRID_LIMIT.getValue()) {
                            break;
                        }
                    }
                }
            }
            // 处理活动岛能量泵任务
            if (data.optBoolean("newGame") && WALK_GRID.getValue()) {
                String branchId = data.getString("branchId");
                String mapId = data.getString("mapId");
                String mapName = data.getString("mapName");
                int buildcount = 0;
                if (canBuild(mapId) && queryUserEnergy() >= 5 && queryUserEnergy() >= WALK_GRID_LIMIT.getValue()) {
                    int remainBuildingEnergyProcess = build(branchId, mapId, mapName, 1);
                    buildcount++;
                    if (buildcount >= WALK_GRID_MAX.getValue() && WALK_GRID_MAX.getValue() != 0) {
                        return;
                    }
                    while (remainBuildingEnergyProcess > 0 && canBuild(mapId)) {
                        TimeUtil.sleep(2000);
                        if (remainBuildingEnergyProcess >= 50 && ((WALK_GRID_MAX.getValue() - buildcount) >= 10 || WALK_GRID_MAX.getValue() == 0) && queryUserEnergy() >= 50) {
                            remainBuildingEnergyProcess = build(branchId, mapId, mapName, 10);
                            buildcount = buildcount + 10;
                        }
                        else if (remainBuildingEnergyProcess >= 25 && ((WALK_GRID_MAX.getValue() - buildcount) >= 5 || WALK_GRID_MAX.getValue() == 0) && queryUserEnergy() >= 25) {
                            remainBuildingEnergyProcess = build(branchId, mapId, mapName, 5);
                            buildcount = buildcount + 5;
                        }
                        else {
                            remainBuildingEnergyProcess = build(branchId, mapId, mapName, 1);
                            buildcount++;
                        }
                        if (WALK_GRID_MAX.getValue() == 0) {
                            continue;
                        }
                        if (buildcount >= WALK_GRID_MAX.getValue() || queryUserEnergy() < 5 || queryUserEnergy() <= WALK_GRID_LIMIT.getValue()) {
                            break;
                        }
                    }
                }
            }
            if (awardspecialActivityReceive.getValue()) {
                //领取活动岛奖励
                if (data.optBoolean("newGame")) {
                    String branchId = data.getString("branchId");
                    String mapId = data.getString("mapId");
                    String mapName = data.getString("mapName");
                    jsonResult = new JSONObject(AntSportsRpcCall.queryMapDetail(mapId));
                    if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                        JSONObject dataMapDetail = jsonResult.getJSONObject("data");
                        JSONObject baseMapInfo = dataMapDetail.getJSONObject("baseMapInfo");
                        if (baseMapInfo.getInt("currentPercent") == 100 && baseMapInfo.optString("status").equals("FINISH_NOT_REWARD")) {
                            JSONArray rewards = baseMapInfo.getJSONArray("rewards");
                            for (int i = 0; i < rewards.length(); i++) {
                                JSONObject reward = rewards.getJSONObject(i);
                                if (reward.optString("prizeStatus").equals("待领取")) {
                                    String itemId = reward.optString("itemId");
                                    JSONObject mapChooseRewardjo = new JSONObject(AntSportsRpcCall.mapChooseReward(branchId, mapId, itemId));
                                    if (MessageUtil.checkSuccess(TAG, mapChooseRewardjo)) {
                                        data = mapChooseRewardjo.getJSONObject("data");
                                        JSONObject specialActivityReceiveResult = data.getJSONObject("specialActivityReceiveResult");
                                        JSONArray prizes = specialActivityReceiveResult.getJSONArray("prizes");
                                        JSONObject prize = prizes.getJSONObject(0);
                                        String subTitle = prize.optString("subTitle");
                                        String title = prize.optString("title");
                                        Log.other("悦动健康🚑️领取奖励[" + subTitle + "]#获得[" + title + "]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            Log.i(TAG, "queryBaseInfo err:");
            Log.printStackTrace(TAG, e);
        }
    }
    
    /**
     * 查询气泡任务并处理
     */
    public static void queryAndProcessBubbleTasks() {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.queryBubbleTask());
            if (!MessageUtil.checkSuccess(TAG, jsonResult)) {
                return;
            }
            JSONObject data = jsonResult.getJSONObject("data");
            JSONArray tasks = data.getJSONArray("bubbleTaskVOS");
            boolean needRetry = false;
            
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject task = tasks.getJSONObject(i);
                if (!task.has("bubbleTaskStatus")) {
                    continue;
                }
                String title = task.getString("title");
                String bubbleTaskStatus = task.getString("bubbleTaskStatus");
                
                if (bubbleTaskStatus.equals("INIT")) {
                    if ("AD_BALL".equals(task.getString("taskId"))) {
                        task.put("lightTaskId", "adBubble");
                        if (receiveBrowseReward(task)) {
                            TimeUtil.sleep(1000);
                            needRetry = true;
                        }
                    }
                    else if ("STRATEGY_BALL".equals(task.getString("taskId"))) {
                        receiveSpecialPrize(task.getString("taskId") + "_ACTIVITY", title);
                    }
                    else if ("SIGN_BALL".equals(task.getString("taskId"))) {
                        signIn();
                    }
                    break;
                }
                if (bubbleTaskStatus.equals("TO_RECEIVE")) {
                    // 已完成任务，领取奖励
                    receiveBubbleReward(task.getString("medEnergyBallInfoRecordId"), title);
                    break;
                }
            }
            // 如果有任务触发了状态变更，重试一次
            if (needRetry) {
                queryAndProcessBubbleTasks();
            }
        }
        catch (Exception e) {
            Log.i(TAG, "queryBubbleTask err:");
            Log.printStackTrace(TAG, e);
        }
    }
    
    /**
     * 兑换权益
     */
    public void exchangeBenefits() {
        int currentEnergy = queryUserEnergy();
        int page = 1;
        boolean hasMore = true;
        
        try {
            while (hasMore) {
                JSONObject jsonResult = new JSONObject(AntSportsRpcCall.queryItemList(page));
                if (!MessageUtil.checkSuccess(TAG, jsonResult)) {
                    break;
                }
                
                JSONObject data = jsonResult.getJSONObject("data");
                hasMore = data.optBoolean("hasMore");
                if (!data.has("itemVOList")) {
                    break;
                }
                
                JSONArray items = data.getJSONArray("itemVOList");
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    if (!"benefitItem".equals(item.getString("materialType"))) {
                        continue;
                    }
                    
                    String benefitId = item.getString("benefitId");
                    String itemId = item.getString("itemId");
                    String itemName = item.getString("itemName");
                    int remainCount = item.getInt("remainCount");
                    int cost = Integer.parseInt(item.getString("salePoint"));
                    
                    // 检查是否可兑换
                    if (remainCount >= 1 && neverLandBenefitList.contains(itemId) && currentEnergy >= cost) {
                        if (item.getString("status").equals("ITEM_SALE")) {
                            String exchangeResult = AntSportsRpcCall.createOrder(benefitId, itemId);
                            if (MessageUtil.checkSuccess(TAG, new JSONObject(exchangeResult))) {
                                Log.other("悦动健康🚑️兑换权益[" + itemName + "]#消耗[" + cost + "g健康能量]");
                                currentEnergy -= cost;
                            }
                        }
                    }
                }
                page++;
            }
        }
        catch (Exception e) {
            Log.i(TAG, "exchangeBenefits err:");
            Log.printStackTrace(TAG, e);
        }
    }
    
    /**
     * 检查是否可进行能量泵前进
     *
     * @param branchId 分支ID
     * @param mapId    地图ID
     * @return 是否可前进
     */
    public static boolean canWalkGrid(String branchId, String mapId) {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.queryMapInfo(branchId, mapId));
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.getJSONObject("data");
                JSONObject starData = data.getJSONObject("starData");
                return data.getBoolean("canWalk") && starData.getInt("curr") < starData.getInt("count");
            }
        }
        catch (Exception e) {
            Log.i(TAG, "canWalkGrid err:");
            Log.printStackTrace(TAG, e);
        }
        return false;
    }
    
    public static boolean canBuild(String mapId) {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.queryMapDetail(mapId));
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.getJSONObject("data");
                JSONObject baseMapInfo = data.getJSONObject("baseMapInfo");
                return baseMapInfo.getBoolean("newIsLandFlg") && baseMapInfo.getInt("currentPercent") < 100;
            }
        }
        catch (Exception e) {
            Log.i(TAG, "canBuild err:");
            Log.printStackTrace(TAG, e);
        }
        return false;
    }
    
    /**
     * 处理签到逻辑
     */
    public static void processSignIn() {
        if (Status.hasFlagToday("NeverLand::SIGN")) {
            return;
        }
        
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.querySign());
            if (!MessageUtil.checkSuccess(TAG, jsonResult)) {
                return;
            }
            
            JSONObject data = jsonResult.getJSONObject("data");
            if (!data.has("days")) {
                return;
            }
            
            JSONArray days = data.getJSONArray("days");
            for (int i = 0; i < days.length(); i++) {
                JSONObject day = days.getJSONObject(i);
                if (day.optBoolean("current") && !day.optBoolean("signIn")) {
                    if (signIn()) {
                        Status.flagToday("NeverLand::SIGN");
                        return;
                    }
                }
            }
            
            // 检查连续签到状态
            if (data.has("continuousSignInfo")) {
                JSONObject continuousInfo = data.getJSONObject("continuousSignInfo");
                if (continuousInfo.optBoolean("signedToday") || signIn()) {
                    Status.flagToday("NeverLand::SIGN");
                }
            }
        }
        catch (Exception e) {
            Log.i(TAG, "processSignIn err:");
            Log.printStackTrace(TAG, e);
        }
    }
    
    /**
     * 处理任务中心任务
     */
    public static void processTaskCenter() {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.queryTaskCenter());
            if (!MessageUtil.checkSuccess(TAG, jsonResult)) {
                return;
            }
            
            JSONObject data = jsonResult.getJSONObject("data");
            JSONArray tasks = data.getJSONArray("taskCenterTaskVOS");
            boolean needRetry = false;
            
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject task = tasks.getJSONObject(i);
                String status = task.getString("taskStatus");
                
                if ("SIGNUP_COMPLETE".equals(status)) {
                    String taskType = task.getString("taskType");
                    if ("LIGHT_TASK".equals(taskType)) {
                        if (task.has("logExtMap")) {
                            JSONObject logExtMap = task.getJSONObject("logExtMap");
                            //if (TaskHelper.checkTaskCompleted(logExtMap.getString("taskType"), logExtMap.getString("bizId"))) {
                            //
                            //    TimeUtil.sleep(1000);
                            //    needRetry = true;
                            //}
                        }
                    }
                    else if ("PROMOKERNEL_TASK".equals(taskType)) {
                        if (completeTask(task)) {
                            task.put("taskStatus", "TO_RECEIVE");
                            TimeUtil.sleep(2000);
                            needRetry = true;
                        }
                    }
                }
                else if ("TO_RECEIVE".equals(status)) {
                    if (receiveTaskReward(task)) {
                        TimeUtil.sleep(1000);
                        needRetry = true;
                    }
                }
            }
            
            if (needRetry) {
                processTaskCenter();
            }
        }
        catch (Exception e) {
            Log.i(TAG, "processTaskCenter err:");
            Log.printStackTrace(TAG, e);
        }
    }
    
    /**
     * 处理浏览任务
     */
    public static void processBrowseTasks() {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.queryTaskInfo());
            if (!MessageUtil.checkSuccess(TAG, jsonResult)) {
                return;
            }
            
            JSONObject data = jsonResult.getJSONObject("data");
            if (!data.has("taskInfos")) {
                return;
            }
            
            JSONArray tasks = data.getJSONArray("taskInfos");
            boolean hasNewTask = false;
            
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject task = tasks.getJSONObject(i);
                TimeUtil.sleep(TimeUnit.SECONDS.toMillis(task.getInt("viewSec")));
                if (receiveBrowseReward(task)) {
                    hasNewTask = true;
                }
            }
            
            if (hasNewTask) {
                processBrowseTasks();
            }
        }
        catch (Exception e) {
            Log.i(TAG, "processBrowseTasks err:");
            Log.printStackTrace(TAG, e);
        }
    }
    
    /**
     * 查询用户能量值
     *
     * @return 能量值
     */
    public static int queryUserEnergy() {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.queryUserAccount());
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.getJSONObject("data");
                return Integer.parseInt(data.getString("balance"));
            }
        }
        catch (Exception e) {
            Log.i(TAG, "queryUserEnergy err:");
            Log.printStackTrace(TAG, e);
        }
        return 0;
    }
    
    public void neverlandrun() {
        try {
            Log.record("悦动健康🚑️开始执行#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
            // 处理签到
            if (QUERY_SIGN.getValue()) {
                processSignIn();
            }
            // 处理任务中心
            if (QUERY_TASK_CENTER.getValue()) {
                processTaskCenter();
            }
            // 处理浏览任务
            processBrowseTasks();
            // 处理气泡任务
            if (QUERY_BUBBLE_TASK.getValue()) {
                queryAndProcessBubbleTasks();
            }
            // 处理基础信息相关任务
            queryBaseInfoAndProcess();
            // 兑换权益
            if (QUERY_ITEM_LIST.getValue()) {
                exchangeBenefits();
            }
            if (MapListSwitch.getValue()) {
                queryMapListSwitch();
            }
            
            Log.record("悦动健康🚑️执行完成#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
        }
        catch (Exception e) {
            Log.i(TAG, "run err:");
            Log.printStackTrace(TAG, e);
        }
    }
    
    private void queryMapListSwitch() {
        try {
            //获取当前岛名字
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.queryBaseinfo());
            if (!MessageUtil.checkSuccess(TAG, jsonResult)) {
                return;
            }
            JSONObject thisdata = jsonResult.getJSONObject("data");
            String thismapName = thisdata.optString("mapName");
            
            //获取岛地图
            JSONObject jsonLandMap = new JSONObject(AntSportsRpcCall.queryMapList());
            if (MessageUtil.checkSuccess("queryMapList", jsonLandMap)) {
                JSONObject data = jsonLandMap.getJSONObject("data");
                
                JSONArray mapList = data.getJSONArray("mapList");
                boolean needSwitch = false;
                
                for (int i = 0; i < mapList.length(); i++) {
                    JSONObject map = mapList.getJSONObject(i);
                    String mapName = map.getString("mapName");
                    String status = map.getString("status");
                    
                    if (mapName.equals(thismapName) && status.contains("FINISH")) {
                        needSwitch = true;
                    }
                }
                if (needSwitch) {
                    for (int i = 0; i < mapList.length(); i++) {
                        JSONObject map = mapList.getJSONObject(i);
                        String mapName = map.getString("mapName");
                        String mapId = map.getString("mapId");
                        String status = map.getString("status");
                        String branchId = map.getString("branchId");
                        //boolean newIsLandFlg = map.optBoolean("newIsLandFlg");
                        
                        if (!mapName.equals(thismapName)) {
                            //if (!status.contains("FINISH") && !newIsLandFlg) {
                            if (!status.contains("FINISH")) {
                                JSONObject jo = new JSONObject(AntSportsRpcCall.mapChooseFree(branchId, mapId));
                                if (MessageUtil.checkSuccess("mapChooseFree", jo)) {
                                    Log.other("悦动健康🚑️切换到[" + mapName + "](" + mapId + ")#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                                    break;
                                }
                            }
                        }
                    }
                    queryBaseInfoAndProcess();
                }
                
            }
        }
        catch (Exception e) {
            Log.i(TAG, "queryMapListSwitch err:");
            Log.printStackTrace(TAG, e);
        }
    }
    
    /**
     * 检查权限
     *
     * @return 是否有权限
     */
    private boolean checkAuth() {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.checkAuth());
            if (MessageUtil.checkSuccess("NeverLandAuth", jsonResult)) {
                return jsonResult.getJSONObject("resultObj").optBoolean("authStatus");
            }
        }
        catch (Exception e) {
            Log.i(TAG, "checkAuth err:");
            Log.printStackTrace(TAG, e);
        }
        return false;
    }
    
    // 任务状态枚举
    public enum neverlandTaskStatus {
        TODO, FINISHED, EXPIRED, DISABLED
    }
    
    // 能量策略枚举
    public interface EnergyStrategy {
        int NONE = 0;
        int CONSERVE = 1;
        int MAXIMIZE = 2;
        String[] nickNames = {"不操作", "保守策略", "最大化收益"};
    }
    
    // 任务选项接口
    public interface NeverLandOption {
    }
    
    public enum PathCompleteStatus {
        NOT_JOIN, JOIN, NOT_COMPLETED, COMPLETED, INTERRUPT;
    }

    public enum TaskStatus {
        WAIT_COMPLETE, WAIT_RECEIVE, HAS_RECEIVED;
    }




    public interface WalkPathTheme {
        int DA_MEI_ZHONG_GUO = 0;
        int GONG_YI_YI_XIAO_BU = 1;
        int DENG_DING_ZHI_MA_SHAN = 2;
        int WEI_C_DA_TIAO_ZHAN = 3;
        int LONG_NIAN_QI_FU = 4;
        int SHOU_HU_TI_YU_MENG = 5;
        
        String[] nickNames = {"大美中国", "公益一小步", "登顶芝麻山", "维C大挑战", "龙年祈福", "守护体育梦"};
        String[] walkPathThemeIds = {"M202308082226", "M202401042147", "V202405271625", "202404221422", "WF202312050200", "V202409061650"};
    }
    
    public interface DonateCharityCoinType {
        
        int ZERO = 0;
        int ONE = 1;
        int ALL = 2;
        
        String[] nickNames = {"不捐赠", "捐赠一个项目", "捐赠所有项目"};
    }
    
    public interface TradeMemberType {
        
        int NONE = 0;
        int TRADE = 1;
        int NOT_TRADE = 2;
        
        String[] nickNames = {"不抢购", "抢购已选好友", "抢购未选好友"};
    }
    
    public interface TrainItemType {
        
        int NONE = 0;
        int BALLET = 1;
        int SANDBAG = 2;
        int BARBELL = 3;
        int YANGKO = 4;
        int SKATE = 5;
        int MUD = 6;
        
        String[] nickNames = {"不训练", "跳芭蕾", "打沙包", "举杠铃", "扭秧歌", "玩滑板", "踩泥坑"};
        String[] itemTypes = {"", "ballet", "sandbag", "barbell", "yangko", "skate", "mud"};
    }
}
