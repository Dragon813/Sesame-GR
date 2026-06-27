package io.github.lazyimmortal.sesame.model.task.antOcean;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.lazyimmortal.sesame.data.ConfigV2;
import io.github.lazyimmortal.sesame.data.ModelFields;
import io.github.lazyimmortal.sesame.data.ModelGroup;
import io.github.lazyimmortal.sesame.data.modelFieldExt.BooleanModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectModelField;
import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.entity.AlipayAntOceanAntiepTaskList;
import io.github.lazyimmortal.sesame.entity.AlipayAntOceanFishBlackList;
import io.github.lazyimmortal.sesame.entity.AlipayUser;
import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.hook.Toast;
import io.github.lazyimmortal.sesame.model.base.TaskCommon;
import io.github.lazyimmortal.sesame.model.task.antFarm.AntFarm.TaskStatus;
import io.github.lazyimmortal.sesame.model.task.antForest.AntForestRpcCall;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.Statistics;
import io.github.lazyimmortal.sesame.util.Status;
import io.github.lazyimmortal.sesame.util.StringUtil;
import io.github.lazyimmortal.sesame.util.TimeUtil;
import io.github.lazyimmortal.sesame.util.idMap.AntOceanAntiepTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.AntOceanFishBlackListMap;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

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
    private BooleanModelField AutoAntOceanFishBlackList;  // 自动添加摸鱼黑名单
    private SelectModelField AntOceanFishBlackList;  // 摸鱼黑名单列表
    private ChoiceModelField cleanOceanType;
    private SelectModelField cleanOceanList;
    private BooleanModelField exchangeUniversalPiece;
    private BooleanModelField useUniversalPiece;
    private BooleanModelField replica;
    private BooleanModelField antfishEnable;  // 开启摸鱼功能
    private BooleanModelField antfishAutoTask;  // 自动完成任务获得次数


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
        modelFields.addField(antfishEnable = new BooleanModelField("antfishEnable", "海洋摸鱼 | 开启摸鱼", false));
        modelFields.addField(antfishAutoTask = new BooleanModelField("antfishAutoTask", "海洋摸鱼 | 摸鱼任务", false));
        modelFields.addField(AutoAntOceanFishBlackList = new BooleanModelField("AutoAntOceanFishBlackList", "海洋摸鱼 | 自动黑名单", true));
        modelFields.addField(AntOceanFishBlackList = new SelectModelField("AntOceanFishBlackList", "摸鱼任务 | 黑名单列表", new LinkedHashSet<>(), AlipayAntOceanFishBlackList::getList));
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
                initAntOceanAntiepTaskListMap(AutoAntOceanAntiepTaskList.getValue(), queryTaskList.getValue(),AutoAntOceanFishBlackList.getValue(),antfishAutoTask.getValue());
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

            // 神秘海洋（摸鱼）功能
            if (antfishEnable.getValue()) {
                antfishRun();
            }

        } catch (Throwable t) {
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
        } catch (Throwable t) {
            Log.i(TAG, "queryOceanStatus err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    public static void initAntOceanAntiepTaskListMap(boolean AutoAntOceanAntiepTaskList, boolean queryTaskList,boolean AutoAntOceanFishBlackList,boolean antfishAutoTask) {
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
                    } else {
                        Log.record("神奇海洋普通任务黑白名单设置失败");
                    }
                }
            }

            //初始化AntOceanFishBlackListMap
            AntOceanFishBlackListMap.load();
            // 1. 定义黑名单（需要添加的任务）和白名单（需要移除的任务）
            blackList = new HashSet<>();
            blackList.add("玩一玩向僵尸开炮");
            // 可继续添加更多黑名单任务
            whiteList = new HashSet<>();// 从黑名单中移除该任务
            //whiteList.add("逛一芝麻树");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                AntOceanFishBlackListMap.add(task, task);
            }

            //初始化AntOceanFishBlackListMap
            if (antfishAutoTask) {
                JSONObject jo = new JSONObject(AntOceanRpcCall.antfishListTask());
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        return;
                    }
                    JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
                    if (taskInfoList == null || taskInfoList.length() == 0) {
                        return;
                    }
                    for (int i = 0; i < taskInfoList.length(); i++) {
                        JSONObject taskInfo = taskInfoList.optJSONObject(i);
                        if (taskInfo == null) continue;
                        JSONObject taskBaseInfo = taskInfo.optJSONObject("taskBaseInfo");
                        if (taskBaseInfo == null) continue;
                        JSONObject bizInfo = new JSONObject(taskBaseInfo.optString("bizInfo", "{}"));
                        String taskTitle = bizInfo.getString("taskTitle");
                        AntOceanFishBlackListMap.add(taskTitle, taskTitle);
                    }

                AntOceanFishBlackListMap.save();
                Log.record("同步任务🉑海洋去摸鱼任务列表");
                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntOceanFishBlackList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields AntForestV2 = config.getModelFieldsMap().get("AntOcean");
                    SelectModelField AntOceanFishBlackList = (SelectModelField) AntForestV2.get("AntOceanFishBlackList");
                    if (AntOceanFishBlackList == null) {
                        return;
                    }

                    // 2. 批量添加黑名单任务（确保存在）
                    Set<String> currentValues = AntOceanFishBlackList.getValue();//该处直接返回列表地址
                    if (currentValues != null) {
                        for (String task : blackList) {
                            if (!currentValues.contains(task)) {
                                AntOceanFishBlackList.add(task, 0);
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
                        Log.record("黑白名单🈲海洋去摸鱼任务自动设置: " + AntOceanFishBlackList.getValue());
                    } else {
                        Log.record("海洋去摸鱼任务黑白名单设置失败");
                    }
                }
            }
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
                } else {
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
        } catch (Throwable t) {
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
                    pos++;
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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

    // ========== 神秘海洋（摸鱼）相关方法 ==========

    /**
     * 神秘海洋主方法
     */
    private void antfishRun() {
        try {
            // 1. 查询海洋状态
            if (!antfishQueryStatus()) {
                return;
            }

            // 2. 查询主页信息
            antfishQueryHomePage();

            // 3. 查询并处理任务
            if (antfishAutoTask.getValue()) {
                antfishHandleTasks();
            }

            // 4. 执行摸鱼
            touchfish();


        } catch (Throwable t) {
            Log.i(TAG, "antfishRun err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 查询海洋状态
     */
    private boolean antfishQueryStatus() {
        try {
            String result = AntOceanRpcCall.antfishStatus();
            JSONObject jo = new JSONObject(result);

            if (MessageUtil.checkResultCode(TAG, jo)) {
                String fishStatus = jo.optString("fishStatus", "DEFAULT_AI_FISH");
                int level = jo.optInt("level", 0);
                if("DEFAULT_AI_FISH".equals(fishStatus)){
                    drawFish();
                }
                //Log.record("海洋摸鱼🐟状态[" + fishStatus + "]等级" + level);
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "antfishQueryStatus err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    /**
     * 查询主页信息
     */
    private void antfishQueryHomePage() {
        try {
            String result = AntOceanRpcCall.antfishHomepage();
            JSONObject jo = new JSONObject(result);

            if (MessageUtil.checkResultCode(TAG, jo)) {
                // 获取当前赛季信息
                String seasonTitle = "";
                String seasonId = "";
                String projectName = "";
                String region = "";
                String fishLevelName = "";
                String fishInteractStatus= "";
                String nickName = "";
                int touchEnergy = 0;
                int touchTotal = 0;
                int remainTouchChance = 0;
                JSONObject currentSeasonInfo = jo.optJSONObject("currentSeasonInfo");
                if (currentSeasonInfo != null) {
                    seasonId = currentSeasonInfo.optString("seasonId", "");
                    seasonTitle = "";
                    JSONObject extInfo = currentSeasonInfo.optJSONObject("extInfo");
                    if (extInfo != null) {
                        seasonTitle = extInfo.optString("seasonTitle", "");
                    }
                }

                // 获取能量
                long energy = jo.optLong("energy", 0);
                //Log.record("海洋摸鱼🐟当前能量" + energy);

                // 获取项目信息
                JSONObject project = jo.optJSONObject("project");
                if (project != null) {
                    projectName = project.optString("projectName", "");
                    region = project.optString("region", "");
                }
                // 获取摸鱼信息
                JSONObject myFish = jo.optJSONObject("myFish");
                if (myFish != null) {
                    nickName = myFish.optString("nickName", "");
                    touchEnergy = myFish.optInt("touchEnergy", 0);

                    // 获取鱼等级信息
                    JSONObject currentLevel = myFish.optJSONObject("currentLevel");
                    if (currentLevel != null) {
                        fishLevelName = currentLevel.optString("name", "");
                    }
                    JSONObject interactVO = myFish.optJSONObject("interactVO");
                    if (interactVO != null) {
                        touchTotal = interactVO.optInt("touchTotal", 0);
                        fishInteractStatus = interactVO.optString("fishInteractStatus", "");
                        remainTouchChance = interactVO.optInt("remainTouchChance", 0);
                        
                        // 解析鱼主人信息
                        JSONObject owner = interactVO.optJSONObject("owner");
                        if (owner != null) {
                            String ownerNickName = owner.optString("nickName", "");
                            String ownerUserId = owner.optString("userId", "");
                            if (!ownerNickName.isEmpty()) {
                                Log.record("海洋摸鱼🐟当前鱼状态["+ fishInteractStatus +"]主人[" + ownerNickName + "](" + ownerUserId + ")");
                            }
                        }
                    }
                }
                Log.record("海洋摸鱼🐟当前赛季[" + seasonTitle + "](" + seasonId + ")项目[" + projectName + "](" + region + ")用户[" + nickName + "]鱼状态["+ fishInteractStatus +"]等级[" + fishLevelName + "]可摸鱼"+remainTouchChance+"累计摸" + touchTotal + "累计获得能量" + touchEnergy + "g(总"+energy+"g)");
                //鱼被困了，解救鱼
                if("CAPTURED".equals(fishInteractStatus)){
                    rescueFish();
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "antfishQueryHomePage err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private boolean drawFish() {
        try {
            // 鱼图片URL列表
            String[] fishImgUrls = {
                "https://mdn.alipayobjects.com/afts/img/JNVwTJAPzqMAAAAAQOAAAAgA9KBtAQJr/original?bz=ai_fish",
                "https://mdn.alipayobjects.com/afts/img/ZK3FTK8eQ-IAAAAAQIAAAAgA9KBtAQJr/original?bz=ai_fish",
                "https://mdn.alipayobjects.com/afts/img/Se_RQ7215tsAAAAAQQAAAAgA9KBtAQJr/original?bz=ai_fish",
                "https://mdn.alipayobjects.com/afts/img/n8yQSYH-lvgAAAAAQGAAAAgA9KBtAQJr/original?bz=ai_fish",
                "https://mdn.alipayobjects.com/afts/img/_rKKT6IJRWcAAAAAQKAAAAgA9KBtAQJr/original?bz=ai_fish",
                "https://mdn.alipayobjects.com/afts/img/_zy6QaSffRMAAAAAQGAAAAgA9KBtAQJr/original?bz=ai_fish",
                "https://mdn.alipayobjects.com/afts/img/3tFWT43StnAAAAAAQHAAAAgA9KBtAQJr/original?bz=ai_fish"
            };
            
            // 随机选择一张图片
            int randomIndex = (int) (Math.random() * fishImgUrls.length);
            String imgUrl = fishImgUrls[randomIndex];
            
            // 从URL中提取imgId（img/和/original之间的部分）
            String imgId = imgUrl.substring(imgUrl.indexOf("img/") + 4, imgUrl.indexOf("/original"));
            
            String result = AntOceanRpcCall.drawFish(imgId, imgUrl);
            JSONObject jo = new JSONObject(result);

            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.forest("开通摸鱼🐟提交画鱼成功");
                Toast.show("开通摸鱼🐟提交画鱼成功");
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "antfishFinishTask err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    //解救鱼
    private boolean rescueFish() {
        try {
            String result = AntOceanRpcCall.rescueFish();
            JSONObject jo = new JSONObject(result);
            if (MessageUtil.checkResultCode(TAG, jo)) {
                // 解析解救后的状态
                JSONObject myFish = jo.optJSONObject("myFish");
                if (myFish != null) {
                    JSONObject interactVO = myFish.optJSONObject("interactVO");
                    if (interactVO != null) {
                        String fishInteractStatus = interactVO.optString("fishInteractStatus", "");
                        int remainChance = interactVO.optInt("remainTouchChance", 0);
                        int touchTotal = interactVO.optInt("touchTotal", 0);
                        
                        Log.forest("摸鱼解救🐟解救成功[" + fishInteractStatus + "]可摸鱼次数" + remainChance + "累计摸鱼" + touchTotal);
                        Toast.show("摸鱼解救🐟解救成功[" + fishInteractStatus + "]");
                    } else {
                        Log.forest("摸鱼任务🐟解救成功");
                    }
                } else {
                    Log.forest("摸鱼任务🐟解救成功");
                }
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "rescueFish err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    /**
     * 处理任务（查询并完成任务获取摸鱼次数）
     */
    private void antfishHandleTasks() {
        try {
            String result = AntOceanRpcCall.antfishListTask();
            JSONObject jo = new JSONObject(result);

            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }

            JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
            if (taskInfoList == null || taskInfoList.length() == 0) {
                Log.forest("海洋摸鱼🐟暂无任务");
                return;
            }

            //Log.forest("海洋摸鱼🐟发现 " + taskInfoList.length() + " 个任务");

            for (int i = 0; i < taskInfoList.length(); i++) {
                JSONObject taskInfo = taskInfoList.optJSONObject(i);
                if (taskInfo == null) continue;

                // 解析任务基础信息
                JSONObject taskBaseInfo = taskInfo.optJSONObject("taskBaseInfo");
                if (taskBaseInfo == null) continue;

                String taskType = taskBaseInfo.optString("taskType", "");
                String taskStatus = taskBaseInfo.optString("taskStatus", "");
                String taskMode = taskBaseInfo.optString("taskMode", "");

                // 解析 bizInfo
                JSONObject bizInfo = new JSONObject(taskBaseInfo.optString("bizInfo", "{}"));
                String taskTitle = bizInfo.optString("taskTitle", "未知任务");

                // 解析任务奖励信息
                JSONObject taskRights = taskInfo.optJSONObject("taskRights");
                int awardCount = taskRights != null ? taskRights.optInt("awardCount", 0) : 0;

                // 已完成任务领取奖励
                if ("FINISHED".equals(taskStatus)) {
                    if (antfishReceiveTaskAward(taskType)) {
                        Log.forest("摸鱼任务🎖️领取[" + taskTitle + "]获得摸鱼次数*" + awardCount);
                    }
                    continue;
                }
                //黑名单任务跳过
                if (AntOceanFishBlackList.getValue().contains(taskTitle)) {
                    continue;
                }
                // 处理其他 TODO 状态任务
                if ("TODO".equals(taskStatus)) {
                    if (antfishFinishTask(taskTitle, taskType)) {
                        if (antfishReceiveTaskAward(taskType)) {
                            Log.forest("摸鱼任务🎖️领取[" + taskTitle + "]获得摸鱼次数*" + awardCount);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "antfishHandleTasks err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 完成任务
     */
    private boolean antfishFinishTask(String taskTitle, String taskType) {
        try {
            String result = AntOceanRpcCall.antfishFinishTask(taskType);
            JSONObject jo = new JSONObject(result);
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntOceanFishBlackList", taskTitle, jo);
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.forest("摸鱼任务🧾完成[" + taskTitle + "]");
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "antfishFinishTask err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    /**
     * 领取任务奖励
     */
    private boolean antfishReceiveTaskAward(String taskType) {
        try {
            if (Status.hasFlagToday("Ocean::ANTAIFISH_TaskAward")) {
                return false;
            }
            String result = AntOceanRpcCall.antfishReceiveTaskAward(taskType);
            JSONObject jo = new JSONObject(result);
            if (!jo.optBoolean("success", false)) {
                Log.record("领取海洋摸鱼[" + jo.optString("desc", "无返回结果") + "]今天不再执行");
                Status.flagToday("Ocean::ANTAIFISH_TaskAward_Limitid");
            }
            if (MessageUtil.checkResultCode(TAG, jo)) {
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "antfishReceiveTaskAward err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    /**
     * 执行摸鱼
     */
    private void touchfish() {
        try {
            // 先查询主页获取剩余摸鱼次数
            String homeResult = AntOceanRpcCall.antfishHomepage();
            JSONObject homeJo = new JSONObject(homeResult);

            if (!MessageUtil.checkResultCode(TAG, homeJo)) {
                return;
            }

            // 从主页响应中获取摸鱼相关信息
            JSONObject myFish = homeJo.optJSONObject("myFish");
            if (myFish == null) {
                Log.record("海洋摸鱼未获取到[我的鱼信息]");
                return;
            }

            // 获取交互状态
            JSONObject interactVO = myFish.optJSONObject("interactVO");
            if (interactVO == null) {
                Log.forest("海洋摸鱼🐟未获取到交互状态信息");
                return;
            }

            int remainTouchChance = interactVO.optInt("remainTouchChance", 0);

            //Log.forest("海洋摸鱼🐟剩余摸鱼次数" + remainTouchChance);

            if (remainTouchChance <= 0) {
                //Log.forest("海洋摸鱼🐟今日摸鱼次数已用完");
                return;
            }

            // 执行摸鱼循环
            int touchCount = 0;
            int totalEnergy = 0;
            while (remainTouchChance > 0) {
                String touchResult = AntOceanRpcCall.antfishTouchfish();
                JSONObject touchJo = new JSONObject(touchResult);

                if (!MessageUtil.checkResultCode(TAG, touchJo)) {
                    Log.record("海洋摸鱼🐟摸鱼失败");
                    break;
                }

                touchCount++;

                // 解析摸鱼结果
                String touchType = touchJo.optString("touchType", "");
                JSONArray touchRewardList = touchJo.optJSONArray("touchRewardList");

                int energyGain = 0;
                String rewardDesc = "";
                String rewardType = "";

                // 循环处理奖励列表
                for (int i = 0; touchRewardList != null && i < touchRewardList.length(); i++) {
                    JSONObject reward = touchRewardList.optJSONObject(i);
                    if (reward == null) continue;

                    JSONObject extInfo = reward.optJSONObject("extInfo");
                    if (extInfo == null) continue;

                    JSONObject popup = extInfo.optJSONObject("popup");
                    if (popup == null) continue;

                    energyGain += popup.optInt("rightsNums", 0);
                    if (rewardDesc.isEmpty()) {
                        rewardDesc = popup.optString("name", "");
                    }
                    if (rewardType.isEmpty()) {
                        rewardType = reward.optString("rewardType", "");
                    }

                    // 检查是否摸到鱼
                    JSONObject captureInfoVO = touchJo.optJSONObject("captureInfoVO");
                    if (captureInfoVO != null && "touchFish".equals(touchType)) {
                        String captureNickName = captureInfoVO.optString("nickName", "");
                        String captureUserId = captureInfoVO.optString("userId", "未知ID");
                        String displayName = captureNickName.isEmpty() ? captureUserId : captureNickName;
                        Log.forest("海洋摸鱼🐟摸到了[" + displayName + "]的鱼获得" + popup.optInt("rightsNums", 0) + "g能量");
                        Toast.show("海洋摸鱼🐟获得" + popup.optInt("rightsNums", 0) + "g能量");
                    } else {
                        Log.forest("海洋摸鱼🐟[" + popup.optString("name", "") + "]" + popup.optInt("rightsNums", 0) + "g");
                        Toast.show("海洋摸鱼🐟获得" + popup.optInt("rightsNums", 0) + "g能量");
                    }

                    totalEnergy += energyGain;
                    Statistics.addData(Statistics.DataType.COLLECTED, energyGain);
                }

                // 更新剩余次数
                JSONObject myFishResult = touchJo.optJSONObject("myFish");
                if (myFishResult != null) {
                    JSONObject interactResult = myFishResult.optJSONObject("interactVO");
                    if (interactResult != null) {
                        remainTouchChance = interactResult.optInt("remainTouchChance", 0);
                    }
                }
            }

            if (touchCount > 0) {
                Log.forest("海洋摸鱼🐟本次共摸鱼" + touchCount + "次获得" + totalEnergy + "g能量");
                Toast.show("海洋摸鱼🐟获得" + totalEnergy + "g能量");
            }

        } catch (Throwable t) {
            Log.i(TAG, "antfishDrawFish err:");
            Log.printStackTrace(TAG, t);
        }
    }

}
