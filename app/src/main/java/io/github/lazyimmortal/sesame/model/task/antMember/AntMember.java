package io.github.lazyimmortal.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.github.lazyimmortal.sesame.data.ConfigV2;
import io.github.lazyimmortal.sesame.data.ModelFields;
import io.github.lazyimmortal.sesame.data.ModelGroup;
import io.github.lazyimmortal.sesame.data.modelFieldExt.BooleanModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectModelField;
import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.entity.AlipayMemberCreditSesameTaskList;
import io.github.lazyimmortal.sesame.entity.MemberBenefit;
import io.github.lazyimmortal.sesame.model.base.TaskCommon;
import io.github.lazyimmortal.sesame.model.extensions.ExtensionsHandle;
import io.github.lazyimmortal.sesame.util.*;
import io.github.lazyimmortal.sesame.util.idMap.AntFarmDoFarmTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.MemberBenefitIdMap;
import io.github.lazyimmortal.sesame.util.idMap.MemberCreditSesameTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.PromiseSimpleTemplateIdMap;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class AntMember extends ModelTask {
    private static final String TAG = AntMember.class.getSimpleName();

    @Override
    public String getName() {
        return "会员";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.MEMBER;
    }

    private BooleanModelField memberSign;
    private BooleanModelField memberPointExchangeBenefit;
    private SelectModelField memberPointExchangeBenefitList;

    private BooleanModelField collectSesame;
    private BooleanModelField AutoMemberCreditSesameTaskList;
    private SelectModelField MemberCreditSesameTaskList;
    private BooleanModelField KuaiDiFuLiJia;
    private BooleanModelField signinCalendar;
    private BooleanModelField enableGoldTicket;
    private BooleanModelField enableGoldTicketConsume;
    private BooleanModelField enableGameCenter;
    private BooleanModelField merchantSignIn;
    private BooleanModelField merchantKMDK;

    private BooleanModelField sesameAlchemyTask;
    private BooleanModelField doSesameAlchemy;

    private BooleanModelField sesameTreeTask;
    private BooleanModelField purifySesameTree;

    // 新增年度回顾字段
    private BooleanModelField AnnualReview;

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(memberSign = new BooleanModelField("memberSign", "会员签到", false));
        modelFields.addField(memberPointExchangeBenefit = new BooleanModelField("memberPointExchangeBenefit", "会员积分 | 兑换权益", false));
        modelFields.addField(memberPointExchangeBenefitList = new SelectModelField("memberPointExchangeBenefitList", "会员积分 | 权益列表", new LinkedHashSet<>(), MemberBenefit::getList));
        modelFields.addField(collectSesame = new BooleanModelField("collectSesame", "芝麻粒 | 领取", false));
        modelFields.addField(AutoMemberCreditSesameTaskList = new BooleanModelField("AutoMemberCreditSesameTaskList", "芝麻粒 | 自动黑白名单", true));
        modelFields.addField(MemberCreditSesameTaskList = new SelectModelField("MemberCreditSesameTaskList", "芝麻粒 | 黑名单任务列表", new LinkedHashSet<>(), AlipayMemberCreditSesameTaskList::getList));
        modelFields.addField(KuaiDiFuLiJia = new BooleanModelField("KuaiDiFuLiJia", "我的快递 | 福利加", false));
        modelFields.addField(signinCalendar = new BooleanModelField("signinCalendar", "消费金 | 签到", false));
        modelFields.addField(enableGoldTicket = new BooleanModelField("enableGoldTicket", "黄金票 | 签到", false));
        modelFields.addField(enableGoldTicketConsume = new BooleanModelField("enableGoldTicketConsume", "黄金票 | 提取(兑换黄金)", false));
        modelFields.addField(enableGameCenter = new BooleanModelField("enableGameCenter", "游戏中心 | 签到", false));
        modelFields.addField(merchantSignIn = new BooleanModelField("merchantSignIn", "商家服务 | 签到", false));
        modelFields.addField(merchantKMDK = new BooleanModelField("merchantKMDK", "商家服务 | 开门打卡", false));

        modelFields.addField(sesameAlchemyTask = new BooleanModelField("sesameAlchemyTask", "芝麻炼金 | 攒粒", false));
        modelFields.addField(doSesameAlchemy = new BooleanModelField("doSesameAlchemy", "芝麻炼金 | 炼金", false));

        modelFields.addField(sesameTreeTask = new BooleanModelField("sesameTreeTask", "芝麻树 | 攒净化值", false));
        modelFields.addField(purifySesameTree = new BooleanModelField("purifySesameTree", "芝麻树 | 净化芝麻树", false));

        // 新增年度回顾字段
        modelFields.addField(AnnualReview = new BooleanModelField("AnnualReview", "年度回顾", false));

        return modelFields;
    }

    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.other("任务暂停⏸️蚂蚁会员:当前为仅收能量时间");
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        try {
            initMemberTaskListMap(AutoMemberCreditSesameTaskList.getValue());
            if (memberSign.getValue()) {
                memberSign();
            }

            if (memberPointExchangeBenefit.getValue()) {
                memberPointExchangeBenefit();
            }
            if (collectSesame.getValue()) {
                CheckInTaskRpcManager();
                collectSesame();
            }

            if (KuaiDiFuLiJia.getValue()) {
                RecommendTask();
                OrdinaryTask();
            }

            boolean shouldRunGoldTicket = (enableGoldTicket != null && enableGoldTicket.getValue()) ||
                    (enableGoldTicketConsume != null && enableGoldTicketConsume.getValue());
            if (shouldRunGoldTicket) {
                Log.record("攒黄金票🎫执行黄金票任务");
                doGoldTicketTask(enableGoldTicket.getValue(), enableGoldTicketConsume.getValue());
            }

            if (sesameAlchemyTask.getValue()) {
                doSesameAlchemyTasks();
                TimeUtil.sleep(500);
                doSesameAlchemyNextDayGift();
            }

            if (doSesameAlchemy.getValue()) {
                doSesameAlchemy();
            }

            if (sesameTreeTask.getValue() || purifySesameTree.getValue()) {
                if (checkSesameCanRun()) {
                    handleSesameTree();
                }
            }

            // 新增年度回顾任务
            if (AnnualReview.getValue()) {
                doAnnualReview();
            }

            if (signinCalendar.getValue()) {
                signinCalendar();
            }
            if (enableGameCenter.getValue()) {
                enableGameCenter();
            }
            if (merchantSignIn.getValue() || merchantKMDK.getValue()) {
                if (MerchantService.transcodeCheck()) {
                    if (merchantSignIn.getValue()) {
                        MerchantService.taskListQueryV2();
                    }
                    if (merchantKMDK.getValue()) {
                        MerchantService.merchantKMDK();
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 年度回顾任务（完整移植自附件）
     */
    private void doAnnualReview() {
        try {
            Log.record("年度回顾\uD83D\uDCC5[开始执行]");

            String resp = AntMemberRpcCall.annualReviewQueryTasks();
            if (resp == null || resp.isEmpty()) {
                Log.record("年度回顾\uD83D\uDCC5[查询返回空]");
                return;
            }

            JSONObject root = new JSONObject(resp);
            if (!root.optBoolean("isSuccess", false)) {
                Log.record("年度回顾\uD83D\uDCC5[查询失败]#" + resp);
                return;
            }

            JSONObject components = root.optJSONObject("components");
            if (components == null || components.length() == 0) {
                Log.record("年度回顾\uD83D\uDCC5[components 为空]");
                return;
            }

            String ANNUAL_REVIEW_QUERY_COMPONENT = "independent_component_task_reward_v2_02888775_independent_component_task_reward_query";
            String ANNUAL_REVIEW_APPLY_COMPONENT = "independent_component_task_reward_v2_02888775_independent_component_task_reward_apply";
            String ANNUAL_REVIEW_PROCESS_COMPONENT = "independent_component_task_reward_v2_02888775_independent_component_task_reward_process";
            String ANNUAL_REVIEW_GET_REWARD_COMPONENT = "independent_component_task_reward_v2_02888775_independent_component_task_reward_get_reward";

            JSONObject queryComp = components.optJSONObject(ANNUAL_REVIEW_QUERY_COMPONENT);
            if (queryComp == null) {
                // 兜底：取第一个组件
                try {
                    java.util.Iterator<String> it = components.keys();
                    if (it.hasNext()) {
                        queryComp = components.optJSONObject(it.next());
                    }
                } catch (Throwable ignored) {
                }
            }
            if (queryComp == null) {
                Log.record("年度回顾\uD83D\uDCC5[未找到查询组件]");
                return;
            }
            if (!queryComp.optBoolean("isSuccess", true)) {
                Log.record("年度回顾\uD83D\uDCC5[查询组件返回失败]");
                return;
            }

            JSONObject content = queryComp.optJSONObject("content");
            if (content == null) {
                Log.record("年度回顾\uD83D\uDCC5[content 为空]");
                return;
            }

            JSONArray taskList = content.optJSONArray("playTaskOrderInfoList");
            if (taskList == null || taskList.length() == 0) {
                Log.record("年度回顾\uD83D\uDCC5[当前无可处理任务]");
                return;
            }

            int candidate = 0;
            int applied = 0;
            int processed = 0;
            int failed = 0;

            for (int i = 0; i < taskList.length(); i++) {
                JSONObject task = taskList.optJSONObject(i);
                if (task == null) {
                    continue;
                }

                String taskStatus = task.optString("taskStatus", "");
                if (!"init".equals(taskStatus)) {
                    continue;
                }
                candidate++;

                String code = task.optString("code", "");
                if (code.isEmpty()) {
                    JSONObject extInfo = task.optJSONObject("extInfo");
                    if (extInfo != null) {
                        code = extInfo.optString("taskId", "");
                    }
                }
                if (code.isEmpty()) {
                    failed++;
                    continue;
                }

                String taskName = code;
                JSONObject displayInfo = task.optJSONObject("displayInfo");
                if (displayInfo != null) {
                    String name = displayInfo.optString("taskName",
                            displayInfo.optString("activityName", code));
                    if (!name.isEmpty()) {
                        taskName = name;
                    }
                }

                // Step 1: 领取任务
                String applyResp = AntMemberRpcCall.annualReviewApplyTask(code);
                if (applyResp == null || applyResp.isEmpty()) {
                    Log.record("年度回顾\uD83D\uDCC5[领任务失败]" + taskName + "#响应为空");
                    failed++;
                    continue;
                }

                JSONObject applyRoot = new JSONObject(applyResp);
                if (!applyRoot.optBoolean("isSuccess", false)) {
                    Log.record("年度回顾\uD83D\uDCC5[领任务失败]" + taskName + "#" + applyResp);
                    failed++;
                    continue;
                }
                JSONObject applyComps = applyRoot.optJSONObject("components");
                if (applyComps == null) {
                    failed++;
                    continue;
                }
                JSONObject applyComp = applyComps.optJSONObject(ANNUAL_REVIEW_APPLY_COMPONENT);
                if (applyComp == null) {
                    try {
                        java.util.Iterator<String> it2 = applyComps.keys();
                        if (it2.hasNext()) {
                            applyComp = applyComps.optJSONObject(it2.next());
                        }
                    } catch (Throwable ignored) {
                    }
                }
                if (applyComp == null || !applyComp.optBoolean("isSuccess", true)) {
                    failed++;
                    continue;
                }
                JSONObject applyContent = applyComp.optJSONObject("content");
                if (applyContent == null) {
                    failed++;
                    continue;
                }
                JSONObject claimedTask = applyContent.optJSONObject("claimedTask");
                if (claimedTask == null) {
                    failed++;
                    continue;
                }
                String recordNo = claimedTask.optString("recordNo", "");
                if (recordNo.isEmpty()) {
                    failed++;
                    continue;
                }
                applied++;

                TimeUtil.sleep(500);

                // Step 2: 提交任务完成
                String processResp = AntMemberRpcCall.annualReviewProcessTask(code, recordNo);
                if (processResp == null || processResp.isEmpty()) {
                    Log.record("年度回顾\uD83D\uDCC5[提交任务失败]" + taskName + "#响应为空");
                    failed++;
                    continue;
                }

                JSONObject processRoot = new JSONObject(processResp);
                if (!processRoot.optBoolean("isSuccess", false)) {
                    Log.record("年度回顾\uD83D\uDCC5[提交任务失败]" + taskName + "#" + processResp);
                    failed++;
                    continue;
                }
                JSONObject processComps = processRoot.optJSONObject("components");
                if (processComps == null) {
                    failed++;
                    continue;
                }
                JSONObject processComp = processComps.optJSONObject(ANNUAL_REVIEW_PROCESS_COMPONENT);
                if (processComp == null) {
                    try {
                        java.util.Iterator<String> it3 = processComps.keys();
                        if (it3.hasNext()) {
                            processComp = processComps.optJSONObject(it3.next());
                        }
                    } catch (Throwable ignored) {
                    }
                }
                if (processComp == null || !processComp.optBoolean("isSuccess", true)) {
                    failed++;
                    continue;
                }
                JSONObject processContent = processComp.optJSONObject("content");
                if (processContent == null) {
                    failed++;
                    continue;
                }
                JSONObject processedTask = processContent.optJSONObject("processedTask");
                if (processedTask == null) {
                    failed++;
                    continue;
                }
                String newStatus = processedTask.optString("taskStatus", "");
                String rewardStatus = processedTask.optString("rewardStatus", "");

                // Step 3: 如仍未发奖，则调用 get_reward 领取奖励
                if (!"success".equalsIgnoreCase(rewardStatus)) {
                    try {
                        String rewardResp = AntMemberRpcCall.annualReviewGetReward(code, recordNo);
                        if (rewardResp != null && !rewardResp.isEmpty()) {
                            JSONObject rewardRoot = new JSONObject(rewardResp);
                            if (rewardRoot.optBoolean("isSuccess", false)) {
                                JSONObject rewardComps = rewardRoot.optJSONObject("components");
                                if (rewardComps != null) {
                                    JSONObject rewardComp = rewardComps.optJSONObject(ANNUAL_REVIEW_GET_REWARD_COMPONENT);
                                    if (rewardComp == null) {
                                        try {
                                            java.util.Iterator<String> it4 = rewardComps.keys();
                                            if (it4.hasNext()) {
                                                rewardComp = rewardComps.optJSONObject(it4.next());
                                            }
                                        } catch (Throwable ignored) {
                                        }
                                    }
                                    if (rewardComp != null && rewardComp.optBoolean("isSuccess", true)) {
                                        JSONObject rewardContent = rewardComp.optJSONObject("content");
                                        if (rewardContent != null) {
                                            JSONObject rewardTask = rewardContent.optJSONObject("processedTask");
                                            if (rewardTask == null) {
                                                rewardTask = rewardContent.optJSONObject("claimedTask");
                                            }
                                            if (rewardTask != null) {
                                                String rs = rewardTask.optString("rewardStatus", "");
                                                if (!rs.isEmpty()) {
                                                    rewardStatus = rs;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable e) {
                        Log.printStackTrace(TAG + ".doAnnualReview.getReward", e);
                    }
                }

                processed++;
                Log.other("年度回顾\uD83D\uDCC5[任务完成]" + taskName + "#状态=" + newStatus + " 奖励状态=" + rewardStatus);
            }

            Log.record("年度回顾\uD83D\uDCC5[执行结束] 待处理=" + candidate + " 已领取=" + applied + " 已提交=" + processed + " 失败=" + failed);
        } catch (Throwable t) {
            Log.printStackTrace(TAG + ".doAnnualReview", t);
        }
    }

    /**
     * 黄金票任务
     */
    private void doGoldTicketTask(boolean doSignIn, boolean doConsume) {
        try {
            if (doSignIn) {
                String homeRes = AntMemberRpcCall.queryWelfareHome();
                if (homeRes != null) {
                    JSONObject homeJson = new JSONObject(homeRes);
                    if (MessageUtil.checkSuccess(TAG, homeJson)) {
                        JSONObject signObj = homeJson.optJSONObject("result").optJSONObject("sign");
                        if (signObj != null && !signObj.optBoolean("todayHasSigned")) {
                            String signRes = AntMemberRpcCall.welfareCenterTrigger("SIGN");
                            JSONObject signJson = new JSONObject(signRes);
                            if (MessageUtil.checkSuccess(TAG, signJson)) {
                                String amount = signJson.optJSONObject("result").optJSONObject("prize").optString("amount");
                                Log.other("攒黄金票🎫[签到成功]#获得: " + amount);
                            }
                        }
                    }
                }
            }

            if (doConsume) {
                String queryRes = AntMemberRpcCall.queryConsumeHome();
                if (queryRes == null) return;
                JSONObject queryJson = new JSONObject(queryRes);
                if (!MessageUtil.checkSuccess(TAG, queryJson)) return;

                JSONObject assetInfo = queryJson.optJSONObject("result").optJSONObject("assetInfo");
                if (assetInfo == null) return;
                int availableAmount = assetInfo.optInt("availableAmount", 0);
                int extractAmount = (availableAmount / 100) * 100;
                if (extractAmount < 100) return;

                JSONObject result = queryJson.optJSONObject("result");
                String productId = result.optJSONObject("product") != null ? result.optJSONObject("product").optString("productId") : "";
                if (productId.isEmpty() && result.optJSONArray("productList") != null) {
                    productId = result.optJSONArray("productList").optJSONObject(0).optString("productId");
                }
                int bonusAmount = result.optJSONObject("bonusInfo") != null ? result.optJSONObject("bonusInfo").optInt("bonusAmount", 0) : 0;

                String submitRes = AntMemberRpcCall.submitConsume(extractAmount, productId, bonusAmount);
                JSONObject submitJson = new JSONObject(submitRes);
                if (MessageUtil.checkSuccess(TAG, submitJson)) {
                    Log.other("攒黄金票🎫[提取成功]#消耗: " + extractAmount + " 份");
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG + ".doGoldTicketTask", t);
        }
    }

    /**
     * 检查是否满足运行芝麻信用任务的条件
     */
    private static Boolean checkSesameCanRun() {
        try {
            String s = AntMemberRpcCall.queryHome();
            JSONObject jo = new JSONObject(s);
            if (!jo.optBoolean("success")) {
                Log.other("芝麻信用💳[首页响应失败]#" + jo.optString("errorMsg"));
                return false;
            }
            JSONObject entrance = jo.getJSONObject("entrance");
            if (!entrance.optBoolean("openApp")) {
                Log.other("芝麻信用💳[未开通芝麻信用]");
                return false;
            }
            return true;
        } catch (Throwable t) {
            Log.printStackTrace(TAG + ".checkSesameCanRun", t);
            return false;
        }
    }

    /**
     * 芝麻树主逻辑
     */
    private void handleSesameTree() {
        if (sesameTreeTask != null && sesameTreeTask.getValue()) {
            doSesameTreeTasks();
        }
        if (purifySesameTree != null && purifySesameTree.getValue()) {
            purifySesameTree();
        }
    }

    /**
     * 执行芝麻树任务以获取净化值
     */
    private void doSesameTreeTasks() {
        try {
            Log.record("芝麻树🌳开始攒净化值任务");
            String taskListStr = AntMemberRpcCall.getSesameTreeTaskList();
            JSONObject taskListJo = new JSONObject(taskListStr);
            if (!taskListJo.optBoolean("success") || !taskListJo.has("extInfo")) {
                Log.record("获取芝麻树任务列表失败或结构不符: " + taskListJo.toString());
                return;
            }

            JSONArray tasks = taskListJo.getJSONObject("extInfo").getJSONObject("taskDetailList").getJSONArray("taskDetailList");
            Log.record("芝麻树🌳获取到[" + tasks.length() + "个]任务");
            int unfinishedCount = 0;

            for (int i = 0; i < tasks.length(); i++) {
                JSONObject task = tasks.getJSONObject(i);
                String taskProcessStatus = task.getString("taskProcessStatus");
                if (!"NOT_DONE".equals(taskProcessStatus)) {
                    continue;
                }

                JSONObject taskMaterial = task.getJSONObject("taskMaterial");
                String title = taskMaterial.getString("title");
                String innerTaskType = task.getJSONObject("taskExtProps").getString("TASK_TYPE");

                if ("COMMON_COUNT_DOWN_VIEW".equals(innerTaskType)) {
                    unfinishedCount++;
                    Log.record("芝麻树🌳[发现可做任务: " + title + "]");
                    String taskId = task.getString("taskId");

                    String browseTimeStr = taskMaterial.optString("browseTime", "0");
                    int browseTime = 0;
                    if (!browseTimeStr.isEmpty()) {
                        try {
                            browseTime = Integer.parseInt(browseTimeStr);
                        } catch (NumberFormatException e) { }
                    }

                    if (browseTime > 0) {
                        Log.record("芝麻树🌳#模拟浏览 " + browseTime + " 秒...");
                        TimeUtil.sleep(browseTime * 1000L);
                    } else {
                        Log.record("芝麻树🌳#模拟点击...");
                        TimeUtil.sleep(2000);
                    }

                    String finishResultStr = AntMemberRpcCall.finishSesameTreeTask(taskId);
                    JSONObject finishResultJo = new JSONObject(finishResultStr);
                    if (finishResultJo.optBoolean("success")) {
                        Log.record("任务'" + title + "'已完成, 准备领取奖励");
                    } else {
                        Log.record("完成芝麻树任务'" + title + "'失败: " + finishResultJo.toString());
                    }
                    TimeUtil.sleep(3000);
                }
            }

            TimeUtil.sleep(3000);
            taskListStr = AntMemberRpcCall.getSesameTreeTaskList();
            taskListJo = new JSONObject(taskListStr);
            if (!taskListJo.optBoolean("success") || !taskListJo.has("extInfo")) return;

            tasks = taskListJo.getJSONObject("extInfo").getJSONObject("taskDetailList").getJSONArray("taskDetailList");
            boolean hasUnclaimed = false;
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject task = tasks.getJSONObject(i);
                String taskProcessStatus = task.getString("taskProcessStatus");
                if ("TO_RECEIVE".equals(taskProcessStatus)) {
                    hasUnclaimed = true;
                    String taskId = task.getString("taskId");
                    String title = task.getJSONObject("taskMaterial").getString("title");
                    String reward = task.getJSONObject("taskMaterial").optString("finishOneTaskGetPurificationValue", "未知");

                    Log.record("芝麻树🌳[发现可领取奖励的任务: " + title + "]");
                    String receiveResultStr = AntMemberRpcCall.receiveSesameTreeTaskReward(taskId);
                    JSONObject receiveResultJo = new JSONObject(receiveResultStr);
                    if (receiveResultJo.optBoolean("success")) {
                        Log.other("芝麻树🌳[领取奖励: " + title + "]#获得净化值+" + reward);
                    } else {
                        Log.record("芝麻树🌳领取芝'" + title + "'失败: " + receiveResultJo.toString());
                    }
                    TimeUtil.sleep(2000);
                }
            }

        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 净化芝麻树（根据剩余净化次数）
     */
    private void purifySesameTree() {
        try {
            Log.record("芝麻树🌳开始净化");
            String s = AntMemberRpcCall.getSesameTreeHomePage();
            JSONObject jo = new JSONObject(s);

            if (!jo.optBoolean("success") || !jo.has("extInfo")) {
                Log.record("获取芝麻树主页信息失败或结构不符：" + jo.toString());
                return;
            }

            JSONObject result = jo.getJSONObject("extInfo").getJSONObject("zhimaTreeHomePageQueryResult");
            JSONArray trees = result.optJSONArray("trees");
            if (trees == null || trees.length() == 0) {
                Log.record("芝麻树-未找到tree信息");
                return;
            }

            JSONObject tree = trees.getJSONObject(0);
            int remainClick = tree.optInt("remainPurificationClickNum", 0);

            if (remainClick <= 0) {
                Log.record("芝麻树🌳[今日净化次数已用完]");
                return;
            }

            Log.record("芝麻树🌳剩余次数["+ remainClick +"]，开始净化");

            for (int i = 0; i < remainClick; i++) {
                String cleanResultStr = AntMemberRpcCall.cleanSesameTreeByClick();
                TimeUtil.sleep(2000);
                JSONObject cleanResultJo = new JSONObject(cleanResultStr);

                if (cleanResultJo.optBoolean("success") && cleanResultJo.has("extInfo")) {
                    JSONObject cleanResult = cleanResultJo.getJSONObject("extInfo")
                            .getJSONObject("zhimaTreeCleanAndPushResult");
                    int newScore = cleanResult.getJSONObject("currentTreeInfo").getInt("scoreSummary");
                    int purificationScore = cleanResult.getInt("purificationScore");
                    Log.other("净化芝麻树🗑️[成功净化1次]#剩余净化值" + purificationScore + ", 当前成长值:" + newScore);
                } else {
                    Log.record("净化失败: " + cleanResultJo.toString());
                    break;
                }
            }

        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 芝麻炼金 - 攒粒任务
     */
    private void doSesameAlchemyTasks() {
        try {
            Log.record("芝麻炼金🔮开始执行攒粒任务...");

            String checkInListStr = AntMemberRpcCall.alchemyQueryCheckInTasks();
            JSONObject checkInListJo = new JSONObject(checkInListStr);
            if (checkInListJo.optBoolean("success")) {
                JSONObject taskData = checkInListJo.getJSONObject("data");
                if (taskData.has("currentDateCheckInTaskVO")) {
                    JSONObject checkInTask = taskData.getJSONObject("currentDateCheckInTaskVO");
                    if ("CAN_COMPLETE".equals(checkInTask.getString("status"))) {
                        String currentDate = checkInTask.getString("checkInDate");
                        String completeStr = AntMemberRpcCall.completeAlchemyCheckIn(currentDate);
                        JSONObject completeJo = new JSONObject(completeStr);
                        if (completeJo.optBoolean("success")) {
                            String zmlNum = completeJo.getJSONObject("data").optString("zmlNum", "?");
                            Log.other("芝麻炼金🔮攒粒✨[签到成功] #" + zmlNum + "粒");
                        } else {
                            Log.record("芝麻炼金🔮攒粒✨[签到失败]: " + completeJo.optString("resultView"));
                        }
                    } else {
                        Log.record("芝麻炼金🔮攒粒✨[今日已签到]");
                    }
                }
            }
            TimeUtil.sleep(2000);

            String timeLimitedTaskStr = AntMemberRpcCall.alchemyQueryTimeLimitedTask();
            JSONObject timeLimitedJo = new JSONObject(timeLimitedTaskStr);
            if (timeLimitedJo.optBoolean("success")) {
                JSONObject taskVo = timeLimitedJo.getJSONObject("data").getJSONObject("timeLimitedTaskVO");
                if (taskVo.getInt("state") == 1) {
                    String templateId = taskVo.getString("templateId");
                    String title = taskVo.getString("longTitle");
                    String completeStr = AntMemberRpcCall.alchemyCompleteTimeLimitedTask(templateId);
                    JSONObject completeJo = new JSONObject(completeStr);
                    if (completeJo.optBoolean("success")) {
                        String zmlNum = completeJo.getJSONObject("data").optString("zmlNum", "?");
                        Log.other("芝麻炼金🔮攒粒✨[领取 " + title + " 成功] #" + zmlNum + "粒");
                    } else {
                        Log.record("芝麻炼金🔮攒粒✨[领取 " + title + " 失败]: " + completeJo.optString("resultView"));
                    }
                } else {
                    String title = taskVo.getString("longTitle");
                    Log.record("芝麻炼金🔮攒粒✨[" + title + " 不可领取]");
                }
            }
            TimeUtil.sleep(2000);

            Log.record("芝麻炼金🔮攒粒✨[开始处理其他日常任务]");
            String s = AntMemberRpcCall.alchemyQueryTasks();
            JSONObject jo = new JSONObject(s);
            if (!jo.optBoolean("success")) {
                Log.record("芝麻炼金🔮攒粒✨[查询日常任务失败]: " + jo.optString("resultView"));
                return;
            }
            JSONArray toCompleteTasks = jo.getJSONObject("data").optJSONArray("toCompleteVOS");
            if (toCompleteTasks == null || toCompleteTasks.length() == 0) {
                Log.record("芝麻炼金🔮攒粒✨[没有可做的日常任务]");
                return;
            }

            Log.record("芝麻炼金🔮攒粒✨[发现 " + toCompleteTasks.length() + " 个日常任务]");

            for (int i = 0; i < toCompleteTasks.length(); i++) {
                JSONObject task = toCompleteTasks.getJSONObject(i);
                String taskTitle = task.optString("title", "未知任务");
                boolean finishFlag = task.optBoolean("finishFlag", false);
                String actionText = task.optString("actionText", "");

                if (finishFlag || "已完成".equals(actionText)) {
                    continue;
                }

                if (!task.has("templateId")) {
                    continue;
                }

                String taskTemplateId = task.getString("templateId");
                int needCompleteNum = task.has("needCompleteNum") ? task.getInt("needCompleteNum") : 1;
                int completedNum = task.optInt("completedNum", 0);

                Log.record("芝麻炼金🔮[处理任务: " + taskTitle + "]");
            }

            if (toCompleteTasks.length() > 0) {
                TimeUtil.sleep(3000);
                s = AntMemberRpcCall.alchemyQueryTasks();
                jo = new JSONObject(s);
                toCompleteTasks = jo.optJSONObject("data").optJSONArray("toCompleteVOS");
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 芝麻炼金 - 领取次日礼包
     */
    private void doSesameAlchemyNextDayGift() {
        try {
            Log.record("芝麻炼金🔮开始尝试领取次日礼包...");

            Log.record("芝麻炼金🔮领取次日礼包✨");
            String claimStr = AntMemberRpcCall.alchemyClaimAward();
            JSONObject claimJo = new JSONObject(claimStr);

            if (claimJo.optBoolean("success")) {
                JSONObject data = claimJo.getJSONObject("data");
                if (data != null) {
                    JSONArray awards = data.optJSONArray("alchemyAwardSendResultVOS");
                    if (awards != null && awards.length() > 0) {
                        JSONObject firstAward = awards.getJSONObject(0);
                        String pointNum = firstAward.optString("pointNum", "?");
                        Log.other("芝麻炼金🔮领取次日礼包✨[领取成功] #获得" + pointNum + "芝麻粒");
                    }
                }
            } else {
                Log.record("芝麻炼金🔮领取次日礼包✨[领取失败]: " + claimJo.optString("resultView", "无详细错误信息"));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 芝麻炼金 - 炼金
     */
    private void doSesameAlchemy() {
        try {
            Log.record("芝麻炼金-开始执行炼金...");
            String homeStr = AntMemberRpcCall.alchemyQueryHome();
            JSONObject homeJo = new JSONObject(homeStr);

            if (!homeJo.optBoolean("success")) {
                Log.record("芝麻炼金🔮[获取炼金主页信息失败]: " + homeJo.optString("resultView"));
                return;
            }

            JSONObject data = homeJo.getJSONObject("data");
            int zmlBalance = data.getInt("zmlBalance");
            int alchemyCost = data.getInt("alchemyCostZml");
            int dailyCap = data.getInt("alchemyDailyCap");
            int finishedCount = data.getInt("finishAlchemyCount");

            if (finishedCount >= dailyCap) {
                Log.record("芝麻炼金🔮[今日炼金次数已达上限(" + finishedCount + "/" + dailyCap + ")]");
                return;
            }

            if (zmlBalance < alchemyCost) {
                Log.record("芝麻炼金🔮[芝麻粒不足]: 需要 " + alchemyCost + ", 当前 " + zmlBalance);
                return;
            }

            int remainingAttempts = dailyCap - finishedCount;
            Log.record("芝麻炼金🔮[开始炼金], 剩余次数: " + remainingAttempts);

            for (int i = 0; i < remainingAttempts; i++) {
                if (zmlBalance < alchemyCost) {
                    Log.record("芝麻炼金🔮[芝麻粒不足]: 需要 " + alchemyCost + ", 当前 " + zmlBalance);
                    break;
                }

                String alchemyResultStr = AntMemberRpcCall.doAlchemy();
                JSONObject resultJo = new JSONObject(alchemyResultStr);

                if (resultJo.optBoolean("success") && resultJo.getJSONObject("data").optBoolean("success")) {
                    JSONObject resultData = resultJo.getJSONObject("data");
                    String goldNum = resultData.optString("goldNum", "未知");
                    zmlBalance -= alchemyCost;
                    Log.other("芝麻炼金🔮[成功" + (finishedCount + i + 1) +"次]#消耗" + alchemyCost +" 粒, 获得黄金 " + goldNum);
                } else {
                    Log.record("芝麻炼金🔮[第 " + (finishedCount + i + 1) + " 次失败]: " + resultJo.optString("resultView"));
                    break;
                }
                TimeUtil.sleep(3000);
            }

        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    // 以下为原有代码，保持不变
    public static void initMemberTaskListMap(boolean AutoMemberCreditSesameTaskList) {
        try {
            MemberCreditSesameTaskListMap.load();
            Set<String> blackList = new HashSet<>();
            blackList.add("去淘金币逛一逛");
            blackList.add("坚持逛裹酱领福利");
            blackList.add("坚持签到领奖励");
            blackList.add("坚持看直播领福利");
            blackList.add("去雇佣芝麻大表鸽");
            blackList.add("完成旧衣回收得现金");
            blackList.add("0.1元起租会员攒粒");
            blackList.add("每日施肥领水果");
            blackList.add("去玩小游戏");

            Set<String> whiteList = new HashSet<>();
            whiteList.add("逛一逛芝麻树");
            whiteList.add("浏览15秒视频广告");
            whiteList.add("逛15秒商品橱窗");
            whiteList.add("逛一逛集汗滴找现金");
            whiteList.add("去体验先用后付");
            whiteList.add("去抛竿钓鱼");
            whiteList.add("去参与花呗活动");
            whiteList.add("坚持攒保障金");
            whiteList.add("去领支付宝积分");
            whiteList.add("去浏览租赁大促会场");
            for (String task : blackList) {
                MemberCreditSesameTaskListMap.add(task, task);
            }
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryHome());
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONObject entrance = jo.getJSONObject("entrance");
                if (entrance.optBoolean("openApp")) {
                    jo = new JSONObject(AntMemberRpcCall.CreditAccumulateStrategyRpcManager());
                    TimeUtil.sleep(300);
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        if (jo.has("data")) {
                            JSONObject data = jo.getJSONObject("data");
                            if (data.has("completeVOS")) {
                                JSONArray completeVOS = data.getJSONArray("completeVOS");
                                for (int i = 0; i < completeVOS.length(); i++) {
                                    JSONObject toCompleteVO = completeVOS.getJSONObject(i);
                                    String title = toCompleteVO.optString("title");
                                    if (title.isEmpty()) {
                                        continue;
                                    }
                                    MemberCreditSesameTaskListMap.add(title, title);
                                }
                            }
                            if (data.has("toCompleteVOS")) {
                                JSONArray toCompleteVOS = data.getJSONArray("toCompleteVOS");
                                for (int i = 0; i < toCompleteVOS.length(); i++) {
                                    JSONObject toCompleteVO = toCompleteVOS.getJSONObject(i);
                                    String title = toCompleteVO.optString("title");
                                    if (title.isEmpty()) {
                                        continue;
                                    }
                                    MemberCreditSesameTaskListMap.add(title, title);
                                }
                            }
                        }
                    }
                }
            }
            MemberCreditSesameTaskListMap.save();
            Log.record("同步任务：会员芝麻信用芝麻粒任务列表");

            if(AutoMemberCreditSesameTaskList){
                ConfigV2 config = ConfigV2.INSTANCE;
                ModelFields antMember = config.getModelFieldsMap().get( "AntMember");
                SelectModelField MemberCreditSesameTaskList = (SelectModelField) antMember.get("MemberCreditSesameTaskList");
                if (MemberCreditSesameTaskList == null) {
                    return;
                }

                Set<String> currentValues = MemberCreditSesameTaskList.getValue();
                if (currentValues != null) {
                    for (String task : blackList) {
                        if (!currentValues.contains(task)) {
                            MemberCreditSesameTaskList.add(task, 0);
                        }
                    }

                    for (String task : whiteList) {
                        currentValues.remove(task);
                    }
                }
                if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
                    Log.record("会员芝麻信用任务芝麻粒黑白名单自动设置: " + MemberCreditSesameTaskList.getValue());
                }
                else {
                    Log.record("会员芝麻信用任务芝麻粒黑白名单设置失败");
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "initMemberTaskListMap err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void memberSign() {
        try {
            if (!Status.hasFlagToday("member::sign")) {
                JSONObject jo = new JSONObject(AntMemberRpcCall.queryMemberSigninCalendar());
                TimeUtil.sleep(500);
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    if (jo.getBoolean("autoSignInSuccess")) {
                        Log.other("会员任务📅签到[坚持" + jo.getString("signinSumDay") + "天]#获得[" + jo.getString("signinPoint") + "积分]");
                    }
                    Status.flagToday("member::sign");
                }
            }

            queryPointCert(1, 8);

            signPageTaskList();

            queryAllStatusTaskList();
        }
        catch (Throwable t) {
            Log.i(TAG, "memberSign err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void queryPointCert(int page, int pageSize) {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryPointCert(page, pageSize));
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            boolean hasNextPage = jo.getBoolean("hasNextPage");
            JSONArray jaCertList = jo.getJSONArray("certList");
            for (int i = 0; i < jaCertList.length(); i++) {
                jo = jaCertList.getJSONObject(i);
                String bizTitle = jo.getString("bizTitle");
                String id = jo.getString("id");
                int pointAmount = jo.getInt("pointAmount");
                jo = new JSONObject(AntMemberRpcCall.receivePointByUser(id));
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    Log.other("会员任务🎖️领取[" + bizTitle + "]奖励#获得[" + pointAmount + "积分]");
                }
            }
            if (hasNextPage) {
                queryPointCert(page + 1, pageSize);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryPointCert err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void signPageTaskList() {
        try {
            do {
                JSONObject jo = new JSONObject(AntMemberRpcCall.signPageTaskList());
                TimeUtil.sleep(500);
                boolean doubleCheck = false;
                if (!MessageUtil.checkResultCode(TAG + " signPageTaskList", jo)) {
                    return;
                }
                if (!jo.has("categoryTaskList")) {
                    return;
                }
                JSONArray categoryTaskList = jo.getJSONArray("categoryTaskList");
                for (int i = 0; i < categoryTaskList.length(); i++) {
                    jo = categoryTaskList.getJSONObject(i);
                    JSONArray taskList = jo.getJSONArray("taskList");
                    String type = jo.getString("type");
                    if (Objects.equals("BROWSE", type)) {
                        doubleCheck = doBrowseTask(taskList);
                    }
                    else {
                        ExtensionsHandle.handleAlphaRequest("antMember", "doMoreTask", jo);
                    }
                }
                if (doubleCheck) {
                    continue;
                }
                break;
            }
            while (true);
        }
        catch (Throwable t) {
            Log.i(TAG, "signPageTaskList err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void queryAllStatusTaskList() {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryAllStatusTaskList());
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray availableTaskList = jo.getJSONArray("availableTaskList");
            if (doBrowseTask(availableTaskList)) {
                queryAllStatusTaskList();
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryAllStatusTaskList err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static Boolean doBrowseTask(JSONArray taskList) {
        boolean doubleCheck = false;
        try {
            for (int i = 0; i < taskList.length(); i++) {
                JSONObject task = taskList.getJSONObject(i);
                if (task.getBoolean("hybrid")) {
                    int periodCurrentCount = Integer.parseInt(task.getJSONObject("extInfo").getString("PERIOD_CURRENT_COUNT"));
                    int periodTargetCount = Integer.parseInt(task.getJSONObject("extInfo").getString("PERIOD_TARGET_COUNT"));
                    int count = periodTargetCount > periodCurrentCount ? periodTargetCount - periodCurrentCount : 0;
                    if (count > 0) {
                        doubleCheck = doubleCheck || doBrowseTask(task, periodTargetCount, periodTargetCount);
                    }
                }
                else {
                    doubleCheck = doubleCheck || doBrowseTask(task, 1, 1);
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "doBrowseTask err:");
            Log.printStackTrace(TAG, t);
        }
        return doubleCheck;
    }

    private static Boolean doBrowseTask(JSONObject task, int left, int right) {
        boolean doubleCheck = false;
        try {
            JSONObject taskConfigInfo = task.getJSONObject("taskConfigInfo");
            String name = taskConfigInfo.getString("name");
            Long id = taskConfigInfo.getLong("id");
            String awardParamPoint = taskConfigInfo.getJSONObject("awardParam").getString("awardParamPoint");
            String targetBusiness = taskConfigInfo.getJSONArray("targetBusiness").getString(0);
            for (int i = left; i <= right; i++) {
                JSONObject jo = new JSONObject(AntMemberRpcCall.applyTask(name, id));
                TimeUtil.sleep(300);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    continue;
                }
                String[] targetBusinessArray = targetBusiness.split("#");
                String bizParam;
                String bizSubType;
                if (targetBusinessArray.length > 2) {
                    bizParam = targetBusinessArray[2];
                    bizSubType = targetBusinessArray[1];
                }
                else {
                    bizParam = targetBusinessArray[1];
                    bizSubType = targetBusinessArray[0];
                }
                jo = new JSONObject(AntMemberRpcCall.executeTask(bizParam, bizSubType));
                TimeUtil.sleep(300);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    continue;
                }
                String ex = left == right && left == 1 ? "" : "(" + (i + 1) + "/" + right + ")";
                Log.other("会员任务🎖️完成[" + name + ex + "]#获得[" + awardParamPoint + "积分]");
                doubleCheck = true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "doBrowseTask err:");
            Log.printStackTrace(TAG, t);
        }
        return doubleCheck;
    }

    private void enableGameCenter() {
        try {
            try {
                String str = AntMemberRpcCall.querySignInBall();
                JSONObject jsonObject = new JSONObject(str);
                if (!jsonObject.optBoolean("success")) {
                    Log.i(TAG + ".signIn.querySignInBall", jsonObject.optString("resultDesc"));
                    return;
                }
                str = JsonUtil.getValueByPath(jsonObject, "data.signInBallModule.signInStatus");
                if (String.valueOf(true).equals(str)) {
                    return;
                }
                str = AntMemberRpcCall.continueSignIn();
                TimeUtil.sleep(300);
                jsonObject = new JSONObject(str);
                if (!jsonObject.optBoolean("success")) {
                    Log.i(TAG + ".signIn.continueSignIn", jsonObject.optString("resultDesc"));
                    return;
                }
                Log.record("游戏中心🎮签到成功");
            }
            catch (Throwable th) {
                Log.i(TAG, "signIn err:");
                Log.printStackTrace(TAG, th);
            }
            try {
                String str = AntMemberRpcCall.queryPointBallList();
                JSONObject jsonObject = new JSONObject(str);
                if (!jsonObject.optBoolean("success")) {
                    Log.i(TAG + ".batchReceive.queryPointBallList", jsonObject.optString("resultDesc"));
                    return;
                }
                JSONArray jsonArray = (JSONArray) JsonUtil.getValueByPathObject(jsonObject, "data.pointBallList");
                if (jsonArray == null || jsonArray.length() == 0) {
                    return;
                }
                str = AntMemberRpcCall.batchReceivePointBall();
                TimeUtil.sleep(300);
                jsonObject = new JSONObject(str);
                if (jsonObject.optBoolean("success")) {
                    Log.other("游戏中心🎮全部领取成功[" + JsonUtil.getValueByPath(jsonObject, "data.totalAmount") + "]乐豆");
                }
                else {
                    Log.i(TAG + ".batchReceive.batchReceivePointBall", jsonObject.optString("resultDesc"));
                }
            }
            catch (Throwable th) {
                Log.i(TAG, "batchReceive err:");
                Log.printStackTrace(TAG, th);
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    private void memberPointExchangeBenefit() {
        try {
            String userId = UserIdMap.getCurrentUid();
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryDeliveryZoneDetail(userId, "94000SR2024011106752003"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("entityInfoList")) {
                Log.record("会员积分[未实名账号无可兑换权益]");
                return;
            }
            JSONArray entityInfoList = jo.getJSONArray("entityInfoList");
            for (int i = 0; i < entityInfoList.length(); i++) {
                JSONObject entityInfo = entityInfoList.getJSONObject(i);
                JSONObject benefitInfo = entityInfo.getJSONObject("benefitInfo");
                JSONObject pricePresentation = benefitInfo.getJSONObject("pricePresentation");
                if (!"POINT_PAY".equals(pricePresentation.optString("strategyType"))) {
                    continue;
                }
                String name = benefitInfo.getString("name");
                String benefitId = benefitInfo.getString("benefitId");
                MemberBenefitIdMap.add(benefitId, name);
                if (!Status.canMemberPointExchangeBenefitToday(benefitId) || !memberPointExchangeBenefitList.getValue().contains(benefitId)) {
                    continue;
                }
                String itemId = benefitInfo.getString("itemId");
                if (exchangeBenefit(benefitId, itemId)) {
                    String point = pricePresentation.getString("point");
                    Log.other("会员积分🎐兑换[" + name + "]#花费[" + point + "积分]");
                }
            }
            MemberBenefitIdMap.save(userId);
        }
        catch (Throwable t) {
            Log.i(TAG, "memberPointExchangeBenefit err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private Boolean exchangeBenefit(String benefitId, String itemId) {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.exchangeBenefit(benefitId, itemId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Status.memberPointExchangeBenefitToday(benefitId);
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "exchangeBenefit err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private void collectSesame() {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject entrance = jo.getJSONObject("entrance");
            if (!entrance.optBoolean("openApp")) {
                Log.other("芝麻信用💌未开通");
                return;
            }

            jo = new JSONObject(AntMemberRpcCall.CreditAccumulateStrategyRpcManager());
            TimeUtil.sleep(300);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("data")) {
                return;
            }
            JSONObject data = jo.getJSONObject("data");
            if (!data.has("toCompleteVOS")) {
                return;
            }
            JSONArray toCompleteVOS = data.getJSONArray("toCompleteVOS");
            for (int i = 0; i < toCompleteVOS.length(); i++) {
                JSONObject toCompleteVO = toCompleteVOS.getJSONObject(i);
                String taskTitle = toCompleteVO.has("title") ? toCompleteVO.getString("title") : "未知任务";
                if (MemberCreditSesameTaskList.getValue().contains(taskTitle)) {
                    continue;
                }

                boolean finishFlag = toCompleteVO.optBoolean("finishFlag", false);
                String actionText = toCompleteVO.optString("actionText", "");

                if (finishFlag || "已完成".equals(actionText)) {
                    continue;
                }

                if (!toCompleteVO.has("templateId")) {
                    continue;
                }

                String taskTemplateId = toCompleteVO.getString("templateId");
                int needCompleteNum = toCompleteVO.has("needCompleteNum") ? toCompleteVO.getInt("needCompleteNum") : 1;
                int completedNum = toCompleteVO.optInt("completedNum", 0);
                String s = null;
                String recordId = null;
                JSONObject responseObj = null;

                if (!toCompleteVO.has("todayFinish")) {
                    s = AntMemberRpcCall.joinSesameTask(taskTemplateId);
                    TimeUtil.sleep(200);
                    responseObj = new JSONObject(s);
                    if (!MessageUtil.checkResultCode(TAG, responseObj)) {
                        Log.error(TAG + "芝麻信用💳领取任务[" + taskTitle + "]失败#" + s);
                        continue;
                    }
                    recordId = responseObj.getJSONObject("data").getString("recordId");
                }
                else {
                    if (!toCompleteVO.has("recordId")) {
                        Log.error(TAG + "芝麻信用💳任务[" + taskTitle + "未获取到]recordId#" + toCompleteVO);
                        continue;
                    }
                    recordId = toCompleteVO.getString("recordId");
                }

                for (int j = completedNum; j < needCompleteNum; j++) {
                    s = AntMemberRpcCall.finishSesameTask(recordId);
                    TimeUtil.sleep(2000);
                    responseObj = new JSONObject(s);
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("MemberCreditSesameTaskList", taskTitle,responseObj);

                    if (MessageUtil.checkResultCode(TAG, responseObj)) {
                        Log.record("芝麻信用💳完成任务[" + taskTitle + "]#(" + (j + 1) + "/" + needCompleteNum + "天)");
                    }
                    else {
                        Log.error("芝麻信用💳完成任务[" + taskTitle + "]失败#" + s);
                    }
                }

                jo = new JSONObject(AntMemberRpcCall.queryCreditFeedback());
                TimeUtil.sleep(300);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                JSONArray ja = jo.getJSONArray("creditFeedbackVOS");
                for (int j = 0; j < ja.length(); j++) {
                    jo = ja.getJSONObject(j);
                    if (!"UNCLAIMED".equals(jo.getString("status"))) {
                        continue;
                    }
                    String creditFeedbackId = jo.getString("creditFeedbackId");
                    String potentialSize = jo.getString("potentialSize");
                    jo = new JSONObject(AntMemberRpcCall.collectCreditFeedback(creditFeedbackId));
                    TimeUtil.sleep(300);
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.other("收芝麻粒🙇🏻‍♂️领取[" + taskTitle + "]奖励[芝麻粒*" + potentialSize + "]");
                    }
                }
            }
            jo = new JSONObject(AntMemberRpcCall.queryCreditFeedback());
            TimeUtil.sleep(300);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray creditFeedbackVOS = jo.getJSONArray("creditFeedbackVOS");
            if (creditFeedbackVOS.length() != 0) {
                jo = new JSONObject(AntMemberRpcCall.collectAllCreditFeedback());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    String resultCode = jo.optString("resultCode");
                    Log.other("收芝麻粒🙇🏻‍♂️[一键收取]" + resultCode);
                }
            }

        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    private void CheckInTaskRpcManager() {
        if (Status.hasFlagToday("AntMember::zmlCheckIn")) {
            return;
        }
        try {

            String checkInRes = AntMemberRpcCall.alchemyQueryCheckIn("zml");
            JSONObject checkInJo = new JSONObject(checkInRes);
            if (MessageUtil.checkResultCode(TAG, checkInJo)) {
                JSONObject data = checkInJo.optJSONObject("data");
                if (data != null) {
                    JSONObject currentDay = data.optJSONObject("currentDateCheckInTaskVO");
                    if (currentDay != null) {
                        String status = currentDay.optString("status");
                        String checkInDate = currentDay.optString("checkInDate");
                        if ("CAN_COMPLETE".equals(status) && !checkInDate.isEmpty()) {
                            String completeRes = AntMemberRpcCall.zmCheckInCompleteTask(checkInDate, "zml");
                            try {
                                JSONObject completeJo = new JSONObject(completeRes);
                                if (MessageUtil.checkResultCode(TAG, completeJo)) {
                                    JSONObject prize = completeJo.optJSONObject("data");
                                    int num = 0;
                                    if (prize != null) {
                                        num = prize.optInt("zmlNum", prize.optJSONObject("prize") != null ? prize.optJSONObject("prize").optInt("num", 0) : 0);
                                    }
                                    Log.other("收芝麻粒🙇🏻‍♂️领取[每日签到成功]#获得" + num + "粒");
                                }
                                else {
                                    Log.error(".doSesameAlchemy#" + "签到失败:" + completeRes);
                                }
                            }
                            catch (Throwable e) {
                                Log.printStackTrace(TAG + ".doSesameAlchemy.alchemyCheckInComplete", e);
                            }
                        }
                    }
                }
            }
            Status.flagToday("AntMember::zmlCheckIn");
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG + ".doSesameZmlCheckIn", t);
        }
    }

    private void RecommendTask() {
        try {
            String response = AntMemberRpcCall.queryRecommendTask();
            JSONObject jsonResponse = new JSONObject(response);
            JSONArray taskDetailList = jsonResponse.getJSONArray("taskDetailList");
            for (int i = 0; i < taskDetailList.length(); i++) {
                JSONObject taskDetail = taskDetailList.getJSONObject(i);
                boolean canAccess = taskDetail.optBoolean("canAccess", false);
                if (!canAccess) {
                    continue;
                }
                JSONObject taskMaterial = taskDetail.optJSONObject("taskMaterial");
                JSONObject taskBaseInfo = taskDetail.optJSONObject("taskBaseInfo");
                String taskCode = taskMaterial.optString("taskCode", "");
                if ("WELFARE_PLUS_ANT_FOREST".equals(taskCode) || "WELFARE_PLUS_ANT_OCEAN".equals(taskCode)) {
                    if ("WELFARE_PLUS_ANT_FOREST".equals(taskCode)) {
                        String forestTaskResponse = AntMemberRpcCall.forestTask();
                        TimeUtil.sleep(500);
                        String forestreceiveTaskAward = AntMemberRpcCall.forestreceiveTaskAward();
                    }
                    else if ("WELFARE_PLUS_ANT_OCEAN".equals(taskCode)) {
                        String oceanTaskResponse = AntMemberRpcCall.oceanTask();
                        TimeUtil.sleep(500);
                        String oceanreceiveTaskAward = AntMemberRpcCall.oceanreceiveTaskAward();
                    }
                    if (taskBaseInfo != null) {
                        String appletName = taskBaseInfo.optString("appletName", "Unknown Applet");
                        Log.other("我的快递💌完成[" + appletName + "]");
                    }
                }
                if (taskMaterial == null || !taskMaterial.has("taskId")) {
                    continue;
                }
                String taskId = taskMaterial.getString("taskId");
                String triggerResponse = AntMemberRpcCall.trigger(taskId);
                JSONObject triggerResult = new JSONObject(triggerResponse);
                boolean success = triggerResult.getBoolean("success");
                if (success) {
                    JSONArray prizeSendInfo = triggerResult.getJSONArray("prizeSendInfo");
                    if (prizeSendInfo.length() > 0) {
                        JSONObject prizeInfo = prizeSendInfo.getJSONObject(0);
                        JSONObject extInfo = prizeInfo.getJSONObject("extInfo");
                        String promoCampName = extInfo.optString("promoCampName", "Unknown Promo Campaign");
                        Log.other("我的快递💌完成[" + promoCampName + "]");
                    }
                }
            }
        }
        catch (Throwable th) {
            Log.i(TAG, "RecommendTask err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private void OrdinaryTask() {
        try {
            String response = AntMemberRpcCall.queryOrdinaryTask();
            JSONObject jsonResponse = new JSONObject(response);
            if (jsonResponse.getBoolean("success")) {
                JSONArray taskDetailList = jsonResponse.getJSONArray("taskDetailList");
                for (int i = 0; i < taskDetailList.length(); i++) {
                    JSONObject task = taskDetailList.getJSONObject(i);
                    String taskId = task.optString("taskId");
                    String taskProcessStatus = task.optString("taskProcessStatus");
                    String sendCampTriggerType = task.optString("sendCampTriggerType");
                    if (!"RECEIVE_SUCCESS".equals(taskProcessStatus) && !"EVENT_TRIGGER".equals(sendCampTriggerType)) {
                        String signuptriggerResponse = AntMemberRpcCall.signuptrigger(taskId);
                        String sendtriggerResponse = AntMemberRpcCall.sendtrigger(taskId);
                        JSONObject sendTriggerJson = new JSONObject(sendtriggerResponse);
                        if (sendTriggerJson.getBoolean("success")) {
                            JSONArray prizeSendInfo = sendTriggerJson.getJSONArray("prizeSendInfo");
                            String prizeName = prizeSendInfo.getJSONObject(0).getString("prizeName");
                            Log.other("我的快递💌完成[" + prizeName + "]");
                        }
                        else {
                            Log.i(TAG, "sendtrigger failed for taskId: " + taskId);
                        }
                        TimeUtil.sleep(1000);
                    }
                }
            }
        }
        catch (Throwable th) {
            Log.i(TAG, "OrdinaryTask err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private void signinCalendar() {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.signinCalendar());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            boolean signed = jo.optBoolean("isSignInToday");
            if (!signed) {
                jo = new JSONObject(AntMemberRpcCall.openBoxAward());
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    int amount = jo.getInt("amount");
                    int consecutiveSignInDays = jo.getInt("consecutiveSignInDays");
                    Log.other("攒消费金💰签到[坚持" + consecutiveSignInDays + "天]#获得[" + amount + "消费金]");
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "signinCalendar err:");
            Log.printStackTrace(TAG, t);
        }
    }
}