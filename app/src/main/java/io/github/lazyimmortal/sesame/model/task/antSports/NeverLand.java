package io.github.lazyimmortal.sesame.model.task.antSports;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

import io.github.lazyimmortal.sesame.data.ModelFields;
import io.github.lazyimmortal.sesame.data.ModelGroup;
import io.github.lazyimmortal.sesame.data.modelFieldExt.BooleanModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectModelField;
import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.entity.AlipayUser;
import io.github.lazyimmortal.sesame.entity.WalkPath;
import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.model.base.TaskCommon;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.Status;
import io.github.lazyimmortal.sesame.util.TimeUtil;

/**
 * 悦动健康岛任务模块
 */
public class NeverLand extends ModelTask {
    // 模块常量
    public static final NeverLand INSTANCE = new NeverLand();
    public static final String MODULE_NAME = "NeverLand";
    public static final String DISPLAY_NAME = "悦动健康岛";
    public static final ModelGroup MODULE_GROUP = ModelGroup.SPORTS;
    
    
    private BooleanModelField neverLand;
    private SelectModelField neverLandOptions;
    private SelectModelField neverLandBenefitList;
    private ChoiceModelField energyStrategy;
    
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(neverLand = new BooleanModelField("neverLand", "健康岛 | 开启", false));
        modelFields.addField(energyStrategy = new ChoiceModelField("energyStrategy", "能量策略", EnergyStrategy.NONE, EnergyStrategy.nickNames));
        //需要修改AlipayUser::getList
        modelFields.addField(neverLandOptions = new SelectModelField("neverLandOptions", "健康岛 | 选项", new LinkedHashSet<>(), AlipayUser::getList));
        //需要修改AlipayUser::getList
        modelFields.addField(neverLandBenefitList = new SelectModelField("neverLandBenefitList", "健康岛 | 权益列表", new LinkedHashSet<>(), AlipayUser::getList));
        return modelFields;
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
            if (MessageUtil.checkSuccess(MODULE_NAME, jsonResult)) {
                JSONObject data = jsonResult.getJSONObject("data");
                int energy = data.getInt("modifyCount");
                if (energy > 0) {
                    Log.other("悦动健康🗺️领取奖励[" + rewardName + "]#获得[" + energy + "g健康能量]");
                }
            }
        }
        catch (Exception e) {
            Log.i(MODULE_NAME, "receiveSpecialPrize err:");
            Log.printStackTrace(MODULE_NAME, e);
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
            if (MessageUtil.checkSuccess(MODULE_NAME, jsonResult)) {
                JSONObject data = jsonResult.getJSONObject("data");
                int continuousDay = data.getJSONObject("continuousSignInfo").getInt("continuitySignedDayCount");
                int reward = data.getJSONObject("continuousDoSignInVO").getInt("rewardAmount");
                Log.other("悦动健康🗺️连续签到[第" + continuousDay + "天]#获得[" + reward + "g健康能量]");
                return true;
            }
        }
        catch (Exception e) {
            Log.i(MODULE_NAME, "takeSign err:");
            Log.printStackTrace(MODULE_NAME, e);
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
            
            if (MessageUtil.checkSuccess(MODULE_NAME, jsonResult)) {
                String taskName = task.getString("title");
                JSONObject data = jsonResult.getJSONObject("data");
                JSONArray rewards = data.getJSONArray("userItems");
                ArrayList<String> rewardList = parseRewards(rewards);
                Log.other("悦动健康🗺️领取奖励[" + taskName + "]#获得" + rewardList);
                return true;
            }
        }
        catch (Exception e) {
            Log.i(MODULE_NAME, "taskReceive err:");
            Log.printStackTrace(MODULE_NAME, e);
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
            if (MessageUtil.checkSuccess(MODULE_NAME, jsonResult)) {
                String taskName = task.getString("title");
                Log.other("悦动健康🗺️完成任务[" + taskName + "]");
                return true;
            }
        }
        catch (Exception e) {
            Log.i(MODULE_NAME, "taskSend err:");
            Log.printStackTrace(MODULE_NAME, e);
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
            JSONObject jsonResult =new JSONObject(AntSportsRpcCall.neverlandwalkGrid(branchId,mapId));
            if (MessageUtil.checkSuccess(MODULE_NAME, jsonResult)) {
                JSONObject data = jsonResult.getJSONObject("data");
                int step = data.getJSONArray("mapAwards").getJSONObject(0).getInt("step");
                int leftCount = data.getInt("leftCount");
                Log.other("悦动健康🗺️能量泵[" + mapName + "]#前进[" + step + "步]");
                
                JSONArray rewards = data.getJSONArray("userItems");
                ArrayList<String> rewardList = parseRewards(rewards);
                if (!rewardList.isEmpty()) {
                    Log.other("悦动健康🗺️能量泵[" + mapName + "]#获得" + rewardList);
                }
                
                int currentStar = data.getJSONObject("starData").getInt("curr");
                int totalStar = data.getJSONObject("starData").getInt("count");
                return leftCount >= 5 && currentStar < totalStar;
            }
        }
        catch (Exception e) {
            Log.i(MODULE_NAME, "walkGrid err:");
            Log.printStackTrace(MODULE_NAME, e);
        }
        return false;
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
            
            if (MessageUtil.checkSuccess(MODULE_NAME, jsonResult)) {
                JSONObject data = jsonResult.getJSONObject("data");
                JSONArray prizes = data.getJSONArray("prizes");
                int totalEnergy = 0;
                for (int i = 0; i < prizes.length(); i++) {
                    totalEnergy += prizes.getJSONObject(i).getInt("prizeCount");
                }
                
                String taskName = task.optString("title", "浏览商品15s得健康能量");
                Log.other("悦动健康🗺️完成任务[" + taskName + "]#获得[" + totalEnergy + "g健康能量]");
                return true;
            }
        }
        catch (Exception e) {
            Log.i(MODULE_NAME, "energyReceive err:");
            Log.printStackTrace(MODULE_NAME, e);
        }
        return false;
    }
    
    /**
     * 领取离线奖励
     */
    public static void receiveOfflineReward() {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.offlineAward());
            if (MessageUtil.checkSuccess(MODULE_NAME, jsonResult)) {
                JSONObject data = jsonResult.getJSONObject("data");
                JSONArray rewards = data.getJSONArray("userItems");
                ArrayList<String> rewardList = parseRewards(rewards);
                
                if (!rewardList.isEmpty()) {
                    Log.other("悦动健康🗺️领取奖励[离线奖励]#获得" + rewardList);
                }
            }
        }
        catch (Exception e) {
            Log.i(MODULE_NAME, "offlineAward err:");
            Log.printStackTrace(MODULE_NAME, e);
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
            Log.i(MODULE_NAME, "parseRewards err:");
            Log.printStackTrace(MODULE_NAME, e);
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
            if (MessageUtil.checkSuccess(MODULE_NAME, jsonResult)) {
                JSONObject data = jsonResult.getJSONObject("data");
                String energy = data.getString("changeAmount");
                Log.other("悦动健康🗺️领取奖励[" + rewardName + "]#获得[" + energy + "g健康能量]");
            }
        }
        catch (Exception e) {
            Log.i(MODULE_NAME, "pickBubbleTaskEnergy err:");
            Log.printStackTrace(MODULE_NAME, e);
        }
    }
    
    /**
     * 查询基础信息并处理相关任务
     */
    public void queryBaseInfoAndProcess() {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.queryBaseinfo());
            if (!MessageUtil.checkSuccess(MODULE_NAME, jsonResult)) {
                return;
            }
            JSONObject data = jsonResult.getJSONObject("data");
            // 处理离线奖励
            if (data.getJSONArray("offlineAwards").length() > 0) {
                receiveOfflineReward();
            }
            
            // 处理能量泵任务
            if (!data.optBoolean("newGame") && neverLandOptions.contains("WALK_GRID")) {
                String branchId = data.getString("branchId");
                String mapId = data.getString("mapId");
                String mapName = data.getString("mapName");
                
                if (canWalkGrid(branchId, mapId) && queryUserEnergy() >= 5) {
                    while (walkGrid(branchId, mapId, mapName)) {
                        TimeUtil.sleep(2000);
                    }
                }
            }
        }
        catch (Exception e) {
            Log.i(MODULE_NAME, "queryBaseInfo err:");
            Log.printStackTrace(MODULE_NAME, e);
        }
    }
    
    /**
     * 查询气泡任务并处理
     */
    public static void queryAndProcessBubbleTasks() {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.queryBubbleTask());
            if (!MessageUtil.checkSuccess(MODULE_NAME, jsonResult)) {
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
                String status = task.getString("bubbleTaskStatus");
                
                switch (TaskStatus.valueOf(status)) {
                    case TODO:
                        // 待完成任务（如广告气泡）
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
                        break;
                    case FINISHED:
                        // 已完成任务，领取奖励
                        receiveBubbleReward(task.getString("medEnergyBallInfoRecordId"), title);
                        break;
                    default:
                        break;
                }
            }
            
            // 如果有任务触发了状态变更，重试一次
            if (needRetry) {
                queryAndProcessBubbleTasks();
            }
        }
        catch (Exception e) {
            Log.i(MODULE_NAME, "queryBubbleTask err:");
            Log.printStackTrace(MODULE_NAME, e);
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
                if (!MessageUtil.checkSuccess(MODULE_NAME, jsonResult)) {
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
                            String exchangeResult = AntSportsRpcCall.createOrder(benefitId,itemId);
                            if (MessageUtil.checkSuccess(MODULE_NAME, new JSONObject(exchangeResult))) {
                                Log.other("悦动健康🗺️兑换权益[" + itemName + "]#消耗[" + cost + "g健康能量]");
                                currentEnergy -= cost;
                            }
                        }
                    }
                }
                page++;
            }
        }
        catch (Exception e) {
            Log.i(MODULE_NAME, "exchangeBenefits err:");
            Log.printStackTrace(MODULE_NAME, e);
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
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.queryMapInfo(branchId,mapId));
            if (MessageUtil.checkSuccess(MODULE_NAME, jsonResult)) {
                JSONObject data = jsonResult.getJSONObject("data");
                JSONObject starData = data.getJSONObject("starData");
                return data.getBoolean("canWalk") && starData.getInt("curr") < starData.getInt("count");
            }
        }
        catch (Exception e) {
            Log.i(MODULE_NAME, "canWalkGrid err:");
            Log.printStackTrace(MODULE_NAME, e);
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
            if (!MessageUtil.checkSuccess(MODULE_NAME, jsonResult)) {
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
            Log.i(MODULE_NAME, "processSignIn err:");
            Log.printStackTrace(MODULE_NAME, e);
        }
    }
    
    /**
     * 处理任务中心任务
     */
    public static void processTaskCenter() {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.queryTaskCenter());
            if (!MessageUtil.checkSuccess(MODULE_NAME, jsonResult)) {
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
                        JSONObject logExtMap = task.getJSONObject("logExtMap");
                        //if (TaskHelper.checkTaskCompleted(logExtMap.getString("taskType"), logExtMap.getString("bizId"))) {
                        //
                         //    TimeUtil.sleep(1000);
                        //    needRetry = true;
                        //}
                    }
                    else if ("PROMOKERNEL_TASK".equals(taskType)) {
                        if (completeTask(task)) {
                            task.put("taskStatus", "TO_RECEIVE");
                            TimeUtil.sleep(1000);
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
            Log.i(MODULE_NAME, "processTaskCenter err:");
            Log.printStackTrace(MODULE_NAME, e);
        }
    }
    
    /**
     * 处理浏览任务
     */
    public static void processBrowseTasks() {
        try {
            JSONObject jsonResult = new JSONObject(AntSportsRpcCall.queryTaskInfo());
            if (!MessageUtil.checkSuccess(MODULE_NAME, jsonResult)) {
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
            Log.i(MODULE_NAME, "processBrowseTasks err:");
            Log.printStackTrace(MODULE_NAME, e);
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
            if (MessageUtil.checkSuccess(MODULE_NAME, jsonResult)) {
                JSONObject data = jsonResult.getJSONObject("data");
                return Integer.parseInt(data.getString("balance"));
            }
        }
        catch (Exception e) {
            Log.i(MODULE_NAME, "queryUserEnergy err:");
            Log.printStackTrace(MODULE_NAME, e);
        }
        return 0;
    }
    
    // 任务状态枚举
    public enum TaskStatus {
        TODO, FINISHED, EXPIRED, DISABLED
    }
    
    // 能量策略枚举
    public interface EnergyStrategy {
        int NONE=0;
        int CONSERVE=1;
        int MAXIMIZE=2;
        String[] nickNames = {"不操作", "保守策略", "最大化收益"};
        }
    
    
    // 任务选项接口
    public interface NeverLandOption {
    }
    
    @Override
    public String getName() {
        return DISPLAY_NAME;
    }
    
    @Override
    public ModelGroup getGroup() {
        return MODULE_GROUP;
    }
    
    
    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.other("任务暂停⏸️悦动健康岛:当前为仅收能量时间");
            return false;
        }
        return true;
    }
    
    @Override
    public void run() {
        if (!neverLand.getValue() || !checkAuth()) {
            return;
        }
        
        try {
            Log.other("开始执行悦动健康岛任务...");
            
            // 处理签到
            if (neverLandOptions.contains("QUERY_SIGN")) {
                processSignIn();
            }
            
            // 处理任务中心
            if (neverLandOptions.contains("QUERY_TASK_CENTER")) {
                processTaskCenter();
            }
            
            // 处理浏览任务
            processBrowseTasks();
            
            // 处理气泡任务
            if (neverLandOptions.contains("QUERY_BUBBLE_TASK")) {
                queryAndProcessBubbleTasks();
            }
            
            // 处理基础信息相关任务
            queryBaseInfoAndProcess();
            
            // 兑换权益
            if (neverLandOptions.contains("QUERY_ITEM_LIST")) {
                exchangeBenefits();
            }
            
            Log.other("悦动健康岛任务执行完成");
        }
        catch (Exception e) {
            Log.i(MODULE_NAME, "run err:");
            Log.printStackTrace(MODULE_NAME, e);
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
            Log.i(MODULE_NAME, "checkAuth err:");
            Log.printStackTrace(MODULE_NAME, e);
        }
        return false;
    }
}