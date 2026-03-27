package io.github.dragon813.sesame.model.task.antOcean;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.dragon813.sesame.data.ConfigV2;
import io.github.dragon813.sesame.data.ModelFields;
import io.github.dragon813.sesame.data.ModelGroup;
import io.github.dragon813.sesame.data.modelFieldExt.BooleanModelField;
import io.github.dragon813.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.dragon813.sesame.data.modelFieldExt.SelectModelField;
import io.github.dragon813.sesame.data.task.ModelTask;
import io.github.dragon813.sesame.entity.AlipayAntOceanAntiepTaskList;
import io.github.dragon813.sesame.entity.AlipayUser;
import io.github.dragon813.sesame.hook.ApplicationHook;
import io.github.dragon813.sesame.model.base.TaskCommon;
import io.github.dragon813.sesame.model.task.antFarm.AntFarm.TaskStatus;
import io.github.dragon813.sesame.model.task.antForest.AntForestRpcCall;
import io.github.dragon813.sesame.util.Log;
import io.github.dragon813.sesame.util.MessageUtil;
import io.github.dragon813.sesame.util.Statistics;
import io.github.dragon813.sesame.util.Status;
import io.github.dragon813.sesame.util.StringUtil;
import io.github.dragon813.sesame.util.TimeUtil;
import io.github.dragon813.sesame.util.idMap.AntOceanAntiepTaskListMap;
import io.github.dragon813.sesame.util.idMap.UserIdMap;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Constanline
 * @since 2023/08/01
 */
public class AntOcean extends ModelTask {
    private static final String TAG = AntOcean.class.getSimpleName();
    
    /**
     * 获取任务名称
     *
     * @return 海洋任务名称
     */
    @Override
    public String getName() {
        return "海洋";
    }
    
    /**
     * 获取任务分组
     *
     * @return 森林分组
     */
    @Override
    public ModelGroup getGroup() {
        return ModelGroup.FOREST;
    }
    
    private BooleanModelField queryTaskList;
    private BooleanModelField AutoAntOceanAntiepTaskList;
    private SelectModelField AntOceanAntiepTaskList;
    private ChoiceModelField cleanOceanType;
    private SelectModelField cleanOceanList;
    private BooleanModelField exchangeUniversalPiece;
    private BooleanModelField useUniversalPiece;
    private BooleanModelField replica;
    
    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(queryTaskList = new BooleanModelField("queryTaskList", "海洋任务", false));
        modelFields.addField(AutoAntOceanAntiepTaskList = new BooleanModelField("AutoAntOceanAntiepTaskList", "海洋任务 | 自动黑白名单", true));
        modelFields.addField(AntOceanAntiepTaskList = new SelectModelField("AntOceanAntiepTaskList", "海洋任务 | 黑名单列表", new LinkedHashSet<>(), AlipayAntOceanAntiepTaskList::getList));
        modelFields.addField(cleanOceanType = new ChoiceModelField("cleanOceanType", "清理海域 | 动作", CleanOceanType.NONE, CleanOceanType.nickNames));
        modelFields.addField(cleanOceanList = new SelectModelField("cleanOceanList", "清理海域 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(exchangeUniversalPiece = new BooleanModelField("exchangeUniversalPiece", "万能拼图 | 制作", false));
        modelFields.addField(useUniversalPiece = new BooleanModelField("useUniversalPiece", "万能拼图 | 使用", false));
        modelFields.addField(replica = new BooleanModelField("replica", "潘多拉海域", false));
        return modelFields;
    }
    
    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.forest("任务暂停⏸️神奇海洋:当前为仅收能量时间");
            return false;
        }
        return true;
    }
    
    @Override
    public void run() {
        try {
            if (!queryOceanStatus()) {
                return;
            }
            
            //初始任务列表
            if (!Status.hasFlagToday("BlackList::initAntOceanAntiep")) {
                initAntOceanAntiepTaskListMap(AutoAntOceanAntiepTaskList.getValue(), queryTaskList.getValue());
                Status.flagToday("BlackList::initAntOceanAntiep");
            }
            queryHomePage();
            
            if (queryTaskList.getValue()) {
                queryTaskList();
            }
            if (cleanOceanType.getValue() != CleanOceanType.NONE) {
                queryUserRanking();
            }
            if (exchangeUniversalPiece.getValue()) {
                exchangeUniversalPiece();
            }
            if (useUniversalPiece.getValue()) {
                useUniversalPiece();
            }
            
            //开启新海域修复
            openWAIT_FOR_UNLOCK();
            
            if (replica.getValue()) {
                queryReplicaHome();
            }
            
            //添加蹲点清理自己海洋
            autocleanOcean(UserIdMap.getCurrentUid());
            
        }
        catch (Throwable t) {
            Log.i(TAG, "AntOcean.start.run err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private Boolean queryOceanStatus() {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.queryOceanStatus());
            if (MessageUtil.checkResultCode(TAG, jo)) {
                if (!jo.getBoolean("opened")) {
                    getEnableField().setValue(false);
                    Log.record("请先开启神奇海洋，并完成引导教程");
                    return false;
                }
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryOceanStatus err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    public static void initAntOceanAntiepTaskListMap(boolean AutoAntOceanAntiepTaskList, boolean queryTaskList) {
        try {
            //初始化AntOceanAntiepTaskListMap
            AntOceanAntiepTaskListMap.load();
            // 1. 定义黑名单（需要添加的任务）和白名单（需要移除的任务）
            Set<String> blackList = new HashSet<>();
            blackList.add("随机任务：玩一玩得拼图");
            // 可继续添加更多黑名单任务
            
            Set<String> whiteList = new HashSet<>();// 从黑名单中移除该任务
            //whiteList.add("逛一芝麻树");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                AntOceanAntiepTaskListMap.add(task, task);
            }
            
            if (queryTaskList) {
                JSONObject jo = new JSONObject(AntOceanRpcCall.queryTaskList());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    
                    JSONArray ja = jo.getJSONArray("antOceanTaskVOList");
                    for (int i = 0; i < ja.length(); i++) {
                        jo = ja.getJSONObject(i);
                        JSONObject bizInfo = new JSONObject(jo.getString("bizInfo"));
                        String taskTitle = bizInfo.optString("taskTitle");
                        AntOceanAntiepTaskListMap.add(taskTitle, taskTitle);
                    }
                }
                //保存任务到配置文件
                AntOceanAntiepTaskListMap.save();
                Log.record("同步任务🉑海洋普通任务列表");
                
                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntOceanAntiepTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields AntOcean = config.getModelFieldsMap().get("AntOcean");
                    SelectModelField AntOceanAntiepTaskList = (SelectModelField) AntOcean.get("AntOceanAntiepTaskList");
                    if (AntOceanAntiepTaskList == null) {
                        return;
                    }
                    
                    // 2. 批量添加黑名单任务（确保存在）
                    Set<String> currentValues = AntOceanAntiepTaskList.getValue();//该处直接返回列表地址
                    if (currentValues != null) {
                        for (String task : blackList) {
                            if (!currentValues.contains(task)) {
                                AntOceanAntiepTaskList.add(task, 0);
                            }
                        }
                        
                        // 3. 批量移除白名单任务（从现有列表中删除）
                        for (String task : whiteList) {
                            if (currentValues.contains(task)) {
                                currentValues.remove(task);
                            }
                        }
                    }
                    // 4. 保存配置
                    if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
                        Log.record("黑白名单🈲海洋普通任务自动设置: " + AntOceanAntiepTaskList.getValue());
                    }
                    else {
                        Log.record("神奇海洋普通任务黑白名单设置失败");
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "initAntOceanAntiepTaskListMap err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void queryHomePage() {
        try {
            JSONObject joHomePage = new JSONObject(AntOceanRpcCall.queryHomePage());
            if (!MessageUtil.checkResultCode(TAG, joHomePage)) {
                return;
            }
            
            if (joHomePage.has("bubbleVOList")) {
                collectEnergy(joHomePage.getJSONArray("bubbleVOList"));
            }
            
            JSONObject userInfoVO = joHomePage.getJSONObject("userInfoVO");
            int rubbishNumber = userInfoVO.optInt("rubbishNumber", 0);
            String userId = userInfoVO.getString("userId");
            cleanOcean(userId, rubbishNumber);
            
            JSONObject ipVO = userInfoVO.optJSONObject("ipVO");
            if (ipVO != null) {
                int surprisePieceNum = ipVO.optInt("surprisePieceNum", 0);
                if (surprisePieceNum > 0) {
                    ipOpenSurprise();
                }
            }
            
            queryMiscInfo();
        }
        catch (Throwable t) {
            Log.i(TAG, "queryHomePage err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void collectEnergy(JSONArray bubbleVOList) {
        try {
            for (int i = 0; i < bubbleVOList.length(); i++) {
                JSONObject bubble = bubbleVOList.getJSONObject(i);
                if (!"ocean".equals(bubble.getString("channel"))) {
                    continue;
                }
                if ("AVAILABLE".equals(bubble.getString("collectStatus"))) {
                    long bubbleId = bubble.getLong("id");
                    String userId = bubble.getString("userId");
                    JSONObject jo = new JSONObject(AntForestRpcCall.collectEnergy(null, userId, bubbleId));
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        JSONArray retBubbles = jo.optJSONArray("bubbles");
                        if (retBubbles != null) {
                            for (int j = 0; j < retBubbles.length(); j++) {
                                JSONObject retBubble = retBubbles.optJSONObject(j);
                                if (retBubble != null) {
                                    int collectedEnergy = retBubble.getInt("collectedEnergy");
                                    Log.forest("神奇海洋🐳收取[" + UserIdMap.getMaskName(userId) + "]的海洋能量#" + collectedEnergy + "g");
                                    Statistics.addData(Statistics.DataType.COLLECTED, collectedEnergy);
                                }
                            }
                            Statistics.save();
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "collectEnergy err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void cleanOcean(String userId, int rubbishNumber) {
        try {
            for (int i = 0; i < rubbishNumber; i++) {
                JSONObject jo = new JSONObject(AntOceanRpcCall.cleanOcean(userId));
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray cleanRewardVOS = jo.getJSONArray("cleanRewardVOS");
                    checkReward(cleanRewardVOS);
                    Log.forest("神奇海洋🐳清理[" + UserIdMap.getMaskName(userId) + "]海域");
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "cleanOcean err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void autocleanOcean(String UserId) {
        try {
            JSONObject joHomePage = new JSONObject(AntOceanRpcCall.queryHomePage());
            if (!MessageUtil.checkResultCode(TAG, joHomePage)) {
                return;
            }
            JSONObject userInfoVO = joHomePage.getJSONObject("userInfoVO");
            Long canCleanLaterTime = userInfoVO.getLong("canCleanLaterTime");
            long updateTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
            addChildTask(new ChildModelTask(UserId, "Ocean", this::queryHomePage, updateTime));
            String taskId = "Ocean|" + UserId;
            if (!hasChildTask(taskId)) {
                addChildTask(new ChildModelTask(taskId, "Ocean", this::queryHomePage, canCleanLaterTime));
                Log.record("神奇海洋🐳蹲添加蹲点在[" + TimeUtil.getCommonDate(canCleanLaterTime) + "]执行清理海洋#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryHomePage err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void ipOpenSurprise() {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.ipOpenSurprise());
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONArray rewardVOS = jo.getJSONArray("surpriseRewardVOS");
                checkReward(rewardVOS);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "ipOpenSurprise err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void combineFish(String fishId) {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.combineFish(fishId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONObject fishDetailVO = jo.getJSONObject("fishDetailVO");
                String name = fishDetailVO.getString("name");
                Log.forest("神奇海洋🐳迎回[" + name + "]");
            }
            //检测是否能开启限时挑战
            createSeaAreaExtraCollect();
        }
        catch (Throwable t) {
            Log.i(TAG, "combineFish err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void checkReward(JSONArray rewards) {
        try {
            for (int i = 0; i < rewards.length(); i++) {
                JSONObject reward = rewards.getJSONObject(i);
                String name = reward.getString("name");
                JSONArray attachReward = reward.getJSONArray("attachRewardBOList");
                if (attachReward.length() > 0) {
                    Log.forest("神奇海洋🐳获得[" + name + "]拼图");
                    boolean canCombine = true;
                    for (int j = 0; j < attachReward.length(); j++) {
                        JSONObject detail = attachReward.getJSONObject(j);
                        if (detail.optInt("count", 0) == 0) {
                            canCombine = false;
                            break;
                        }
                    }
                    if (canCombine && reward.optBoolean("unlock", false)) {
                        String fishId = reward.getString("id");
                        combineFish(fishId);
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "checkReward err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void queryReplicaHome() {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.queryReplicaHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            
            if (jo.has("userReplicaAssetVO")) {
                JSONObject userReplicaAssetVO = jo.getJSONObject("userReplicaAssetVO");
                int canCollectAssetNum = userReplicaAssetVO.getInt("canCollectAssetNum");
                collectReplicaAsset(canCollectAssetNum);
            }
            
            if (jo.has("userCurrentPhaseVO")) {
                JSONObject userCurrentPhaseVO = jo.getJSONObject("userCurrentPhaseVO");
                String phaseCode = userCurrentPhaseVO.getString("phaseCode");
                String code = jo.getJSONObject("userReplicaInfoVO").getString("code");
                if ("COMPLETED".equals(userCurrentPhaseVO.getString("phaseStatus"))) {
                    unLockReplicaPhase(code, phaseCode);
                }
            }
            
            queryReplicaTaskList();
        }
        catch (Throwable t) {
            Log.i(TAG, "queryReplicaHome err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void collectReplicaAsset(int canCollectAssetNum) {
        try {
            for (int i = 0; i < canCollectAssetNum; i++) {
                JSONObject jo = new JSONObject(AntOceanRpcCall.collectReplicaAsset());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    Log.forest("神奇海洋🐳[学习海洋科普知识]#获得[潘多拉能量*1]");
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "collectReplicaAsset err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void unLockReplicaPhase(String replicaCode, String replicaPhaseCode) {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.unLockReplicaPhase(replicaCode, replicaPhaseCode));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                String name = jo.getJSONObject("currentPhaseInfo").getJSONObject("extInfo").getString("name");
                Log.forest("神奇海洋🐳迎回[" + name + "]");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "unLockReplicaPhase err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void queryReplicaTaskList() {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.queryReplicaTaskList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray ja = jo.getJSONArray("antOceanTaskVOList");
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                String taskStatus = jo.getString("taskStatus");
                if (!TaskStatus.FINISHED.name().equals(taskStatus)) {
                    continue;
                }
                String taskType = jo.getString("taskType");
                JSONObject bizInfo = new JSONObject(jo.getString("bizInfo"));
                String taskTitle = bizInfo.getString("taskTitle");
                receiveReplicaTaskAward(taskType, taskTitle);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryReplicaTaskList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void receiveReplicaTaskAward(String taskType, String taskTitle) {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.receiveReplicaTaskAward(taskType));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                int incAwardCount = jo.getInt("incAwardCount");
                Log.forest("神奇海洋🐳领取[" + taskTitle + "]奖励#获得[潘多拉能量*" + incAwardCount + "]");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "receiveReplicaTaskAward err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void queryMiscInfo() {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.queryMiscInfo());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject miscHandlerVOMap = jo.getJSONObject("miscHandlerVOMap");
            JSONObject homeTipsRefresh = miscHandlerVOMap.getJSONObject("HOME_TIPS_REFRESH");
            if (homeTipsRefresh.optBoolean("fishCanBeCombined") || homeTipsRefresh.optBoolean("canBeRepaired")) {
                querySeaAreaDetailList();
            }
            switchOceanChapter();
        }
        catch (Throwable t) {
            Log.i(TAG, "queryMiscInfo err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void createSeaAreaExtraCollect() {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.querySeaAreaDetailList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            //判断神秘海域
            boolean awardSeaAreaCanCreateExtraCollect = jo.optBoolean("awardSeaAreaCanCreateExtraCollect", false);
            if (awardSeaAreaCanCreateExtraCollect) {
                JSONObject Extrajo = new JSONObject(AntOceanRpcCall.createSeaAreaExtraCollect());
                if (MessageUtil.checkResultCode(TAG, Extrajo)) {
                    if (Extrajo.has("seaAreaExtraCollectVO")) {
                        Log.forest("神奇海洋🐳开启了神秘海域#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "createSeaAreaExtraCollect err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void querySeaAreaDetailList() {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.querySeaAreaDetailList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            //判断神秘海域
            boolean awardSeaAreaCanCreateExtraCollect = jo.optBoolean("awardSeaAreaCanCreateExtraCollect", false);
            if (awardSeaAreaCanCreateExtraCollect) {
                JSONObject Extrajo = new JSONObject(AntOceanRpcCall.createSeaAreaExtraCollect());
                if (MessageUtil.checkResultCode(TAG, Extrajo)) {
                    if (Extrajo.has("seaAreaExtraCollectVO")) {
                        Log.forest("神奇海洋🐳开启了神秘海域#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                    }
                }
            }
            int seaAreaNum = jo.getInt("seaAreaNum");
            int fixSeaAreaNum = jo.getInt("fixSeaAreaNum");
            int currentSeaAreaIndex = jo.getInt("currentSeaAreaIndex");
            if (currentSeaAreaIndex < fixSeaAreaNum && seaAreaNum > fixSeaAreaNum) {
                queryOceanPropList();
            }
            JSONArray seaAreaVOs = jo.getJSONArray("seaAreaVOs");
            for (int i = 0; i < seaAreaVOs.length(); i++) {
                JSONObject seaAreaVO = seaAreaVOs.getJSONObject(i);
                JSONArray fishVOs = seaAreaVO.getJSONArray("fishVO");
                for (int j = 0; j < fishVOs.length(); j++) {
                    JSONObject fishVO = fishVOs.getJSONObject(j);
                    if (!fishVO.getBoolean("unlock") && "COMPLETED".equals(fishVO.getString("status"))) {
                        String fishId = fishVO.getString("id");
                        combineFish(fishId);
                    }
                }
                if (seaAreaVO.has("seaAreaExtraCollectVO")) {
                    JSONObject seaAreaExtraCollectVO = seaAreaVO.getJSONObject("seaAreaExtraCollectVO");
                    String ExtraStatus = seaAreaExtraCollectVO.optString("status");
                    if (!ExtraStatus.equals("FINISHED")) {
                        JSONArray ExtrafishVOs = seaAreaExtraCollectVO.getJSONArray("fishVO");
                        for (int j = 0; j < ExtrafishVOs.length(); j++) {
                            JSONObject ExtrafishVO = ExtrafishVOs.getJSONObject(j);
                            if (!ExtrafishVO.getBoolean("unlock") && "COMPLETED".equals(ExtrafishVO.getString("status"))) {
                                String ExtrafishId = ExtrafishVO.getString("id");
                                combineFish(ExtrafishId);
                            }
                        }
                    }
                }
                seaAreaVO = seaAreaVOs.getJSONObject(seaAreaVOs.length() - 1);
                String LastseaAreaStatus = seaAreaVO.optString("status");
                if (LastseaAreaStatus.equals("WAIT_FOR_UNLOCK")) {
                    AntOceanRpcCall.repairSeaArea();
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "querySeaAreaDetailList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void openWAIT_FOR_UNLOCK() {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.querySeaAreaDetailList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            //判断神秘海域
            boolean awardSeaAreaCanCreateExtraCollect = jo.optBoolean("awardSeaAreaCanCreateExtraCollect", false);
            if (awardSeaAreaCanCreateExtraCollect) {
                
                String args = "[{\"source\":\"chInfo_ch_appcenter__chsub_9patch\",\"uniqueId\":\"" + AntOceanRpcCall.getUniqueId() + "\"}]";
                String Extrastr = ApplicationHook.requestString("alipay.antocean.ocean.h5.createSeaAreaExtraCollect", args);
                JSONObject Extrajo = new JSONObject(Extrastr == null ? "{}" : Extrastr);
                if (MessageUtil.checkResultCode(TAG, Extrajo)) {
                    if (Extrajo.has("seaAreaExtraCollectVO")) {
                        Log.forest("神奇海洋🐳开启了神秘海域#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                    }
                }
            }
            JSONArray seaAreaVOs = jo.getJSONArray("seaAreaVOs");
            JSONObject seaAreaVO = seaAreaVOs.getJSONObject(seaAreaVOs.length() - 1);
            String LastseaAreaStatus = seaAreaVO.optString("status");
            if (LastseaAreaStatus.equals("WAIT_FOR_UNLOCK")) {
                AntOceanRpcCall.repairSeaArea();
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "querySeaAreaDetailList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void queryOceanPropList() {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.queryOceanPropList());
            if (MessageUtil.checkResultCode(TAG, jo)) {
                AntOceanRpcCall.repairSeaArea();
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryOceanPropList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void switchOceanChapter() {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.queryOceanChapterList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            String currentChapterCode = jo.getString("currentChapterCode");
            JSONArray chapterVOs = jo.getJSONArray("userChapterDetailVOList");
            boolean isFinish = false;
            String dstChapterCode = "";
            String dstChapterName = "";
            for (int i = 0; i < chapterVOs.length(); i++) {
                JSONObject chapterVO = chapterVOs.getJSONObject(i);
                int repairedSeaAreaNum = chapterVO.getInt("repairedSeaAreaNum");
                int seaAreaNum = chapterVO.getInt("seaAreaNum");
                if (chapterVO.getString("chapterCode").equals(currentChapterCode)) {
                    isFinish = repairedSeaAreaNum >= seaAreaNum;
                }
                else {
                    if (repairedSeaAreaNum >= seaAreaNum || !chapterVO.getBoolean("chapterOpen")) {
                        continue;
                    }
                    dstChapterName = chapterVO.getString("chapterName");
                    dstChapterCode = chapterVO.getString("chapterCode");
                }
            }
            if (isFinish && !StringUtil.isEmpty(dstChapterCode)) {
                jo = new JSONObject(AntOceanRpcCall.switchOceanChapter(dstChapterCode));
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    Log.forest("神奇海洋🐳切换到[" + dstChapterName + "]系列");
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "switchOceanChapter err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void queryUserRanking() {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.queryUserRanking());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (Status.hasFlagToday("Ocean::HELP_CLEAN_ALL_FRIEND_LIMIT")) {
                return;
            }
            JSONArray fillFlagVOList = jo.getJSONArray("fillFlagVOList");
            for (int i = 0; i < fillFlagVOList.length(); i++) {
                JSONObject fillFlag = fillFlagVOList.getJSONObject(i);
                if (cleanOceanType.getValue() != CleanOceanType.NONE) {
                    cleanFriendOcean(fillFlag);
                }
            }
            int pos = 20;
            List<String> idList = new ArrayList<>();
            JSONArray allRankingList = jo.getJSONArray("allRankingList");
            while (pos < allRankingList.length()) {
                JSONObject friend = allRankingList.getJSONObject(pos);
                String userId = friend.optString("userId", "");
                if (userId.equals(UserIdMap.getCurrentUid()) || userId.isEmpty()) {
                    continue;
                }
                idList.add(userId);
                pos++;
                if (pos % 20 == 0) {
                    jo = new JSONObject(AntOceanRpcCall.fillUserFlag(new JSONArray(idList).toString()));
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        return;
                    }
                    fillFlagVOList = jo.getJSONArray("fillFlagVOList");
                    for (int i = 0; i < fillFlagVOList.length(); i++) {
                        JSONObject fillFlag = fillFlagVOList.getJSONObject(i);
                        if (cleanOceanType.getValue() != CleanOceanType.NONE) {
                            cleanFriendOcean(fillFlag);
                            if (Status.hasFlagToday("Ocean::HELP_CLEAN_ALL_FRIEND_LIMIT")) {
                                return;
                            }
                        }
                    }
                    idList.clear();
                }
            }
            if (!idList.isEmpty()) {
                jo = new JSONObject(AntOceanRpcCall.fillUserFlag(new JSONArray(idList).toString()));
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                fillFlagVOList = jo.getJSONArray("fillFlagVOList");
                for (int i = 0; i < fillFlagVOList.length(); i++) {
                    JSONObject fillFlag = fillFlagVOList.getJSONObject(i);
                    if (cleanOceanType.getValue() != CleanOceanType.NONE) {
                        cleanFriendOcean(fillFlag);
                        if (Status.hasFlagToday("Ocean::HELP_CLEAN_ALL_FRIEND_LIMIT")) {
                            return;
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryUserRanking err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void cleanFriendOcean(JSONObject fillFlag) {
        if (!fillFlag.optBoolean("canClean")) {
            return;
        }
        try {
            String userId = fillFlag.getString("userId");
            boolean isCleanOcean = cleanOceanList.getValue().contains(userId);
            if (cleanOceanType.getValue() != CleanOceanType.CLEAN) {
                isCleanOcean = !isCleanOcean;
            }
            if (!isCleanOcean) {
                return;
            }
            if (cleanFriendOcean(userId)) {
                TimeUtil.sleep(1000);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "cleanFriendOcean err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private Boolean cleanFriendOcean(String userId) {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.queryFriendPage(userId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            if (Status.hasFlagToday("Ocean::HELP_CLEAN_ALL_FRIEND_LIMIT")) {
                return false;
            }
            jo = new JSONObject(AntOceanRpcCall.cleanFriendOcean(userId));
            if (jo.has("resultDesc")) {
                if (jo.getString("resultDesc").contains("上限")) {
                    Log.record("神奇海洋🐳" + jo.getString("resultDesc"));
                    Status.flagToday("Ocean::HELP_CLEAN_ALL_FRIEND_LIMIT");
                }
                return false;
            }
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.forest("神奇海洋🐳帮助[" + UserIdMap.getMaskName(userId) + "]清理海域");
                JSONArray cleanRewardVOS = jo.getJSONArray("cleanRewardVOS");
                checkReward(cleanRewardVOS);
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "cleanFriendOcean err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private static boolean isTargetTask(String taskType) {
        // 在这里添加其他任务类型，以便后续扩展
        return "DAOLIU_TAOJINBI".equals(taskType) // 去逛淘金币看淘金仔
               || "DAOLIU_NNYY".equals(taskType) // 逛余额宝新春活动
               || "ANTOCEAN_TASK#DAOLIU_GUANGHUABEIBANGHAI".equals(taskType) // 逛逛花呗活动会场
               || "BUSINESS_LIGHTS01".equals(taskType) // 逛一逛市集15s
               || "DAOLIU_ELEMEGUOYUAN".equals(taskType) // 去逛饿了么夺宝
               || "ZHUANHUA_NONGCHANGYX".equals(taskType) // 去玩趣味小游戏
               || "ZHUANHUA_HUIYUN_OZB".equals(taskType); // 一键传球欧洲杯
        
    }
    
    private void queryTaskList() {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.queryTaskList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray ja = jo.getJSONArray("antOceanTaskVOList");
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                String taskStatus = jo.optString("taskStatus");
                String sceneCode = jo.getString("sceneCode");
                String taskType = jo.getString("taskType");
                JSONObject bizInfo = new JSONObject(jo.getString("bizInfo"));
                String taskTitle = bizInfo.optString("taskTitle");
                if (TaskStatus.RECEIVED.name().equals(taskStatus)) {
                    continue;
                }
                if (TaskStatus.TODO.name().equals(taskStatus) && !finishOceanTask(jo)) {
                    continue;
                }
                TimeUtil.sleep(500);
                
                receiveTaskAward(sceneCode, taskType, taskTitle);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryTaskList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    //日常任务
    private static void receiveTaskAward(String sceneCode, String taskType, String taskTitle) {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.receiveTaskAward(sceneCode, taskType));
            TimeUtil.sleep(500);
            //检查并标记黑名单任务
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntOceanAntiepTaskList", taskTitle, jo);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                String awardCount = jo.optString("incAwardCount");
                Log.forest("海洋任务🎖️领取[" + taskTitle + "]奖励#获得[" + awardCount + "块拼图]");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "receiveTaskAward err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private Boolean finishOceanTask(JSONObject task) {
        try {
            if (task.has("taskProgress")) {
                return false;
            }
            JSONObject bizInfo = new JSONObject(task.getString("bizInfo"));
            String taskTitle = bizInfo.optString("taskTitle");
            //黑名单任务跳过
            if (AntOceanAntiepTaskList.getValue().contains(taskTitle)) {
                return false;
            }
            if (taskTitle.equals("每日任务：答题学海洋知识")) {
                // 答题操作
                if (answerQuestion()) {
                    Log.forest("海洋任务🧾完成[" + taskTitle + "]");
                    return true;
                }
            }
            //不完成限时任务号容易黑
            //else if (taskTitle.startsWith("随机任务：") || taskTitle.startsWith("绿色任务：")|| taskTitle.startsWith("限时任务：")) {
            else if (taskTitle.startsWith("随机任务：") || taskTitle.startsWith("绿色任务：")) {
                String sceneCode = task.getString("sceneCode");
                String taskType = task.getString("taskType");
                JSONObject jo = new JSONObject(AntOceanRpcCall.finishTask(sceneCode, taskType));
                //检查并标记黑名单任务
                MessageUtil.checkResultCodeAndMarkTaskBlackList("AntOceanAntiepTaskList", taskTitle, jo);
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    Log.forest("海洋任务🧾完成[" + taskTitle + "]");
                    return true;
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "finishOceanTask err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    // 海洋答题任务
    private static Boolean answerQuestion() {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.getQuestion());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            if (jo.getBoolean("answered")) {
                Log.record("问题已经被回答过，跳过答题流程");
                return false;
            }
            String questionId = jo.getString("questionId");
            JSONArray options = jo.getJSONArray("options");
            String answer = options.getString(0);
            TimeUtil.sleep(500);
            jo = new JSONObject(AntOceanRpcCall.submitAnswer(answer, questionId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.record("海洋答题成功");
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "answerQuestion err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    // 制作万能拼图
    private static void exchangeUniversalPiece() {
        try {
            // 获取道具兑换列表的JSON数据
            JSONObject jo = new JSONObject(AntOceanRpcCall.queryOceanPropList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            // 获取重复拼图数量
            int duplicatePieceNum = jo.getInt("duplicatePieceNum");
            while (duplicatePieceNum >= 10) {
                // 如果重复拼图数量大于等于10，则执行道具兑换操作
                int exchangeNum = Math.min(duplicatePieceNum / 10, 50);
                if (!exchangeUniversalPiece(exchangeNum)) {
                    break;
                }
                TimeUtil.sleep(1000);
                duplicatePieceNum -= exchangeNum * 10;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "exchangeUniversalPiece error:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static Boolean exchangeUniversalPiece(int number) {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.exchangeUniversalPiece(number));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                String duplicatePieceNum = jo.getString("duplicatePieceNum");
                String exchangeNum = jo.getString("exchangeNum");
                Log.forest("神奇海洋🐳制作[万能拼图*" + exchangeNum + "]#剩余[重复拼图*" + duplicatePieceNum + "]");
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "exchangeUniversalPiece error:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    // 使用万能拼图
    private static void useUniversalPiece() {
        try {
            // 获取道具使用类型列表的JSON数据
            JSONObject jo = new JSONObject(AntOceanRpcCall.queryOceanPropList("UNIVERSAL_PIECE"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            // 获取道具类型列表中的holdsNum值
            JSONArray oceanPropVOByTypeList = jo.getJSONArray("oceanPropVOByTypeList");
            // 遍历每个道具类型信息
            for (int i = 0; i < oceanPropVOByTypeList.length(); i++) {
                JSONObject oceanPropVO = oceanPropVOByTypeList.getJSONObject(i);
                int holdsNum = oceanPropVO.getInt("holdsNum");
                int pageNum = 0;
                boolean hasMore = true;
                while (holdsNum > 0 && hasMore) {
                    // 查询鱼列表的JSON数据
                    pageNum++;
                    jo = new JSONObject(AntOceanRpcCall.queryFishList(pageNum));
                    // 检查是否成功获取到鱼列表并且 hasMore 为 true
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        // 如果没有成功获取到鱼列表或者 hasMore 为 false，则停止后续操作
                        return;
                    }
                    hasMore = jo.optBoolean("hasMore");
                    // 获取鱼列表中的fishVOS数组
                    if (!jo.has("fishVOS")) {
                        return;
                    }
                    JSONArray fishVOS = jo.getJSONArray("fishVOS");
                    holdsNum -= useUniversalPiece(fishVOS, holdsNum);
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "useUniversalPiece error:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static int useUniversalPiece(JSONArray fishVOS, int holdsNum) {
        int count = 0;
        try {
            for (int i = 0; i < fishVOS.length() && count < holdsNum; i++) {
                JSONObject fishVO = fishVOS.getJSONObject(i);
                if (!fishVO.has("pieces")) {
                    continue;
                }
                count += useUniversalPiece(fishVO, holdsNum - count);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "useUniversalPiece error:");
            Log.printStackTrace(TAG, t);
        }
        return count;
    }
    
    private static int useUniversalPiece(JSONObject fishVO, int holdsNum) {
        JSONArray assetsDetails = new JSONArray();
        try {
            int order = fishVO.getInt("order");
            String name = fishVO.getString("name");
            JSONArray pieces = fishVO.getJSONArray("pieces");
            for (int i = 0; i < pieces.length(); i++) {
                JSONObject piece = pieces.getJSONObject(i);
                if (piece.getInt("num") > 1) {
                    continue;
                }
                JSONObject assetsDetail = new JSONObject();
                assetsDetail.put("assets", order);
                assetsDetail.put("assetsNum", 1);
                assetsDetail.put("attachAssets", Integer.parseInt(piece.getString("id")));
                assetsDetail.put("propCode", "UNIVERSAL_PIECE");
                assetsDetails.put(assetsDetail);
                if (assetsDetails.length() == holdsNum) {
                    break;
                }
            }
            if (useUniversalPiece(assetsDetails, name, holdsNum - assetsDetails.length())) {
                TimeUtil.sleep(1000);
                return assetsDetails.length();
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "useUniversalPiece error:");
            Log.printStackTrace(TAG, t);
        }
        return 0;
    }
    
    private static Boolean useUniversalPiece(JSONArray assetsDetails, String name, int holdsNum) {
        try {
            if (assetsDetails.length() == 0) {
                return false;
            }
            JSONObject jo = new JSONObject(AntOceanRpcCall.useUniversalPiece(assetsDetails));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                int userCount = assetsDetails.length();
                Log.forest("神奇海洋🐳使用[万能拼图*" + userCount + "]迎回[" + name + "]#剩余[万能拼图*" + holdsNum + "]");
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "useUniversalPiece error:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    public interface CleanOceanType {
        
        int NONE = 0;
        int CLEAN = 1;
        int NOT_CLEAN = 2;
        
        String[] nickNames = {"不清理海域", "清理已选好友", "清理未选好友"};
        
    }
    
}
