package io.github.lazyimmortal.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Set;

import io.github.lazyimmortal.sesame.model.task.antOrchard.GoldenBeanRpcCall;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.Status;
import io.github.lazyimmortal.sesame.util.TimeUtil;
import io.github.lazyimmortal.sesame.util.idMap.GoldenBeansTaskListMap;

/**
 * 支付宝-芝麻炼金（芝麻信用-攒芝麻分-芝麻炼金）+ 炼金入口金豆夺宝业务逻辑。
 * <p>
 * 已实现（接口均由抓包 RPC.txt 2026-10-19 确认）：
 * <ul>
 *     <li>芝麻炼金：每日签到（CheckInTaskRpcManager sceneCode=alchemy）</li>
 *     <li>芝麻炼金：自动炼金（每轮检查剩余次数即炼金，体力不足/满级时停止，无当日标记，循环限速+死循环保护）</li>
 *     <li>芝麻炼金：限时补贴任务（早/中/晚饭时段内自动完成）</li>
 *     <li>芝麻炼金：阶段奖励领取（炼金次数达标后的次日礼包，awardAvailable 时 claimAward）</li>
 *     <li>炼金金豆夺宝：金豆苗收取（调 index 服务端自动结算，读 sproutCollectedDelta）</li>
 *     <li>炼金金豆夺宝：每日签到领金豆（goldenbean.sign，bizType=ZHIMA）</li>
 *     <li>炼金金豆夺宝：完成任务领奖（finishTask + receiveTaskAward，黑名单与农场金豆夺宝共用）</li>
 *     <li>炼金金豆夺宝：芝麻粒换金豆（manureExchange，1芝麻=80金豆，支持当日兑换上限与剩余芝麻粒下限）</li>
 * </ul>
 * 风控：每日去重/服务端状态判断/循环限速/死循环保护；结果写 other.log（会员），诊断写 runtime 日志。
 * 任务黑名单直接复用芭芭农场「金豆任务 | 黑名单列表」（GoldenBeansTaskList），两处共用一套配置。
 */
public class SesameAlchemy {

    private static final String TAG = "SesameAlchemy";

    /**
     * 芝麻炼金主入口，由 AntMember.run() 调用
     *
     * @param doAlchemy      自动炼金（消耗芝麻粒）
     * @param doSign         炼金每日签到（sceneCode=alchemy）
     * @param doGoldenBean   炼金金豆夺宝任务签到领奖（bizType=ZHIMA）
     * @param goldenBeanList 金豆任务黑名单（与农场金豆夺宝共用 GoldenBeansTaskList）
     * @param doExchange     芝麻粒换金豆（消耗芝麻粒）
     * @param dailyLimit     当日兑换芝麻粒上限（0=不限制）
     * @param reserveZml     剩余芝麻粒下限（0=不限制）
     * @param doPointBox     芝麻积分主页宝箱
     */
    public static void run(boolean doAlchemy, boolean doSign,
                           boolean doGoldenBean, Set<String> goldenBeanList,
                           boolean doExchange, int dailyLimit, int reserveZml,
                           boolean doPointBox) {
        try {
            if (doAlchemy || doSign) {
                alchemy(doSign, doAlchemy);
            }
            if (doGoldenBean || doExchange) {
                goldenBean(doGoldenBean, goldenBeanList, doExchange, dailyLimit, reserveZml);
            }
            if (doPointBox) {
                pointTreasureBox();
            }
        } catch (Throwable t) {
            Log.i(TAG, "run err:");
            Log.printStackTrace(TAG, t);
        }
    }

    // ==================== 芝麻积分宝箱 ====================

    /**
     * 芝麻积分主页宝箱：hasBox 且 status=WAIT_CLAIM 时开启，开启后进入倒计时（HANGING）
     */
    private static void pointTreasureBox() {
        try {
            JSONObject jo = new JSONObject(AlchemyRpcCall.queryPointTreasureBox());
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject data = jo.optJSONObject("data");
            if (data == null || !data.optBoolean("hasBox", false)) {
                return;
            }
            if (!"WAIT_CLAIM".equals(data.optString("status"))) {
                return;
            }
            JSONObject openJo = new JSONObject(AlchemyRpcCall.openPointTreasureBox());
            TimeUtil.sleep(500);
            if (MessageUtil.checkResultCode(TAG, openJo)) {
                JSONObject openData = openJo.optJSONObject("data");
                Log.other("芝麻积分🎁宝箱#获得[" + (openData != null ? openData.optInt("rewardAmount", data.optInt("rewardAmount", 0)) : data.optInt("rewardAmount", 0)) + "芝麻粒]");
            }
        } catch (Throwable t) {
            Log.i(TAG, "pointTreasureBox err:");
            Log.printStackTrace(TAG, t);
        }
    }

    // ==================== 芝麻炼金 ====================

    /**
     * 芝麻炼金：签到 + 自动炼金（内含限时补贴） + 阶段奖励
     */
    private static void alchemy(boolean doSign, boolean doAlchemy) {
        try {
            // 每日签到（场景码alchemy，复用会员签到RPC，独立去重防重复请求）
            if (doSign && !Status.hasFlagToday("SesameAlchemy::alchemySign")) {
                if (alchemySign()) {
                    Status.flagToday("SesameAlchemy::alchemySign");
                }
                TimeUtil.sleep(500);
            }

            // 自动炼金（每轮检查，无当日标记）
            if (doAlchemy) {
                // 限时补贴任务（早/中/晚饭，时段内完成，跟随自动炼金执行）
                if (!Status.hasFlagToday("SesameAlchemy::timeLimited")) {
                    if (timeLimitedTask()) {
                        Status.flagToday("SesameAlchemy::timeLimited");
                    }
                    TimeUtil.sleep(500);
                }
                doAlchemyLoop();
            }

            // 阶段奖励（炼金次数达标后的次日礼包）
            if (doAlchemy && !Status.hasFlagToday("SesameAlchemy::stageAward")) {
                if (claimStageAward()) {
                    Status.flagToday("SesameAlchemy::stageAward");
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "alchemy err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 炼金每日签到：queryTaskLists(sceneCode=alchemy) 当日 CAN_COMPLETE 时 completeTask
     *
     * @return true=已处理完（成功或无可签），false=本轮失败待重试
     */
    private static boolean alchemySign() {
        try {
            JSONObject checkInJo = new JSONObject(AntMemberRpcCall.alchemyQueryCheckIn("alchemy"));
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, checkInJo)) {
                return false;
            }
            JSONObject data = checkInJo.optJSONObject("data");
            JSONObject currentDay = data == null ? null : data.optJSONObject("currentDateCheckInTaskVO");
            if (currentDay == null) {
                return true;
            }
            String status = currentDay.optString("status");
            if (!"CAN_COMPLETE".equals(status)) {
                return true;
            }
            String checkInDate = currentDay.optString("checkInDate");
            if (checkInDate.isEmpty()) {
                return true;
            }
            JSONObject completeJo = new JSONObject(AntMemberRpcCall.zmCheckInCompleteTask(checkInDate, "alchemy"));
            TimeUtil.sleep(500);
            if (MessageUtil.checkResultCode(TAG, completeJo)) {
                int num = 0;
                JSONObject prize = completeJo.optJSONObject("data");
                if (prize != null) {
                    num = prize.optInt("zmlNum", prize.optJSONObject("prize") != null ? prize.optJSONObject("prize").optInt("num", 0) : 0);
                    if (num == 0) {
                        num = prize.optInt("awardPoint", 0);
                    }
                }
                Log.other("芝麻炼金📅签到#获得[" + num + "]");
                return true;
            }
            return false;
        } catch (Throwable t) {
            Log.i(TAG, "alchemySign err:");
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    /**
     * 限时补贴任务（早/中/晚饭）：当天时段内 state=1 时 completeTask
     *
     * @return true=已处理完（成功或无任务），false=本轮失败待重试
     */
    private static boolean timeLimitedTask() {
        try {
            JSONObject jo = new JSONObject(AlchemyRpcCall.queryTimeLimitedTask());
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONObject data = jo.optJSONObject("data");
            JSONObject taskVO = data == null ? null : data.optJSONObject("timeLimitedTaskVO");
            if (taskVO == null || taskVO.optBoolean("tomorrow", false)) {
                return true;
            }
            int startHour = taskVO.optInt("startHour", -1);
            int endHour = taskVO.optInt("endHour", -1);
            int state = taskVO.optInt("state", 0);
            String templateId = taskVO.optString("templateId");
            if (templateId.isEmpty() || state != 1) {
                return true;
            }
            // 仅在任务时段内提交（如晚饭补贴19~21时）
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (startHour >= 0 && endHour > startHour && (hour < startHour || hour >= endHour)) {
                return true;
            }
            String title = taskVO.optString("longTitle", taskVO.optString("shortTitle", "限时补贴"));
            JSONObject completeJo = new JSONObject(AlchemyRpcCall.completeTimeLimitedTask(templateId));
            TimeUtil.sleep(500);
            if (MessageUtil.checkResultCode(TAG, completeJo)) {
                JSONObject completeData = completeJo.optJSONObject("data");
                // 奖励以completeTask响应data.zmlNum为准（抓包验证），缺失回退queryTask的rewardAmount
                int award = completeData != null ? completeData.optInt("zmlNum", taskVO.optInt("rewardAmount", 0)) : taskVO.optInt("rewardAmount", 0);
                Log.other("芝麻炼金⏰限时补贴[" + title + "]#获得[" + award + "芝麻粒]");
            }
            return true;
        } catch (Throwable t) {
            Log.i(TAG, "timeLimitedTask err:");
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    /**
     * 自动炼金：每轮检查（无当日标记），有剩余次数即循环炼金；
     * 体力不足（staminaExhausted/staminaCurrent不足）或满级（levelFull）时停止
     */
    private static void doAlchemyLoop() {
        try {
            JSONObject home = new JSONObject(AlchemyRpcCall.queryHome());
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, home)) {
                return;
            }
            JSONObject data = home.optJSONObject("data");
            if (data == null) {
                return;
            }
            int remaining = data.optInt("alchemyNum", 0);
            if (data.optBoolean("capReached", false)) {
                remaining = 0;
            }
            if (remaining <= 0) {
                // 今日次数已用完，无操作
                return;
            }
            // 体力不够不炼金（staminaExhausted为服务端体力耗尽标志）
            int staminaCurrent = data.optInt("staminaCurrent", -1);
            if (data.optBoolean("staminaExhausted", false) || staminaCurrent == 0) {
                Log.i(TAG, "体力不足#剩余体力[" + staminaCurrent + "]，本轮跳过炼金");
                return;
            }
            // 炼金前使用可用道具（如BOTTLE瓶子）
            useAlchemyItems();

            long goldGot = 0;
            int done = 0;
            int levelUpCount = 0;
            boolean levelFullStopped = false;
            int dailyCap = data.optInt("alchemyDailyCap", 0);
            int staminaCost = -1; // 单次炼金体力消耗，首次响应校准（观测值5）
            // 死循环保护：剩余次数+富余量
            int guard = remaining + 5;
            while (remaining > 0 && guard-- > 0) {
                // 体力不够下一次炼金时停止
                if (staminaCost > 0 && staminaCurrent >= 0 && staminaCurrent < staminaCost) {
                    Log.i(TAG, "体力不足#剩余体力[" + staminaCurrent + "]#本轮已炼[" + done + "次]，停止");
                    break;
                }
                JSONObject alchemyJo = new JSONObject(AlchemyRpcCall.alchemy());
                TimeUtil.sleep(500);
                if (!MessageUtil.checkResultCode(TAG, alchemyJo)) {
                    break;
                }
                JSONObject alchemyData = alchemyJo.optJSONObject("data");
                if (alchemyData == null || !alchemyData.optBoolean("success", true)) {
                    break;
                }
                // 用响应的累计已炼次数回校剩余次数（炼金响应.alchemyNum=累计，与queryHome语义相反），
                // 防止本地计数漂移（如用户在App内手动炼金插队消耗次数导致多发一次失败请求）
                if (dailyCap > 0 && alchemyData.has("alchemyNum")) {
                    int left = dailyCap - alchemyData.optInt("alchemyNum", 0);
                    if (left >= 0 && left < remaining) {
                        remaining = left;
                    }
                }
                // 满级不再炼金（服务端可能仍下发剩余次数，继续炼无效）
                if (alchemyData.optBoolean("levelFull", false)) {
                    levelFullStopped = true;
                    break;
                }
                goldGot += alchemyData.optLong("goldNum", 0);
                done++;
                if (alchemyData.optBoolean("levelUp", false)) {
                    levelUpCount++;
                }
                remaining--;
                // 校准体力消耗并更新剩余体力（响应staminaCurrent为炼金后剩余值）
                staminaCost = alchemyData.optInt("staminaUsedV2", staminaCost > 0 ? staminaCost : 5);
                staminaCurrent = alchemyData.optInt("staminaCurrent", staminaCurrent);
            }
            // 炼金后重新查询主页拿最新状态（金币余额/等级/芝麻粒余额）
            JSONObject newHome = new JSONObject(AlchemyRpcCall.queryHome());
            TimeUtil.sleep(500);
            if (MessageUtil.checkResultCode(TAG, newHome) && newHome.optJSONObject("data") != null) {
                data = newHome.optJSONObject("data");
            }
            if (done > 0) {
                summaryAlchemy(data, done, goldGot, levelUpCount);
            }
            if (levelFullStopped) {
                Log.i(TAG, "已满级#本轮炼金[" + done + "次]后停止");
            }
        } catch (Throwable t) {
            Log.i(TAG, "doAlchemyLoop err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 使用炼金可用道具（queryAvailableItems bizScene=ALCHEMY，逐个useItem）
     */
    private static void useAlchemyItems() {
        try {
            JSONObject jo = new JSONObject(AlchemyRpcCall.queryAvailableItems());
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject data = jo.optJSONObject("data");
            JSONArray items = data == null ? null : data.optJSONArray("items");
            if (items == null) {
                return;
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String itemId = item.optString("itemId");
                String itemType = item.optString("itemType");
                if (itemId.isEmpty()) {
                    continue;
                }
                JSONObject useJo = new JSONObject(AlchemyRpcCall.useItem(itemId, itemType));
                TimeUtil.sleep(500);
                if (MessageUtil.checkResultCode(TAG, useJo)) {
                    Log.other("芝麻炼金🧪使用道具[" + itemType + "]");
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "useAlchemyItems err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 炼金概况输出：一条日志带炼金次数/产出/等级/余额
     */
    private static void summaryAlchemy(JSONObject data, int done, long goldGot, int levelUpCount) {
        try {
            if (data == null) {
                return;
            }
            long goldBalance = data.optLong("goldBalance", 0);
            int level = data.optInt("currentLevel", 0);
            long zmlBalance = data.optLong("zmlBalance", 0);
            int finishCount = data.optInt("finishAlchemyCount", 0);
            Log.other("芝麻炼金⚗️炼金[" + done + "次]#获得[" + goldGot + "金币]#今日累计[" + finishCount + "次]"
                    + (levelUpCount > 0 ? "#升级[" + levelUpCount + "级→lv" + level + "]" : "")
                    + "#金币余额[" + goldBalance + "]#芝麻粒余额[" + zmlBalance + "]");
        } catch (Throwable t) {
            Log.i(TAG, "summaryAlchemy err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 炼金阶段奖励：queryEntryList 中 ALCHEMY_STAGE_REWARD.awardAvailable 时 claimAward
     *
     * @return true=已处理完（成功或无可领），false=本轮失败待重试
     */
    private static boolean claimStageAward() {
        try {
            JSONObject jo = new JSONObject(AlchemyRpcCall.queryEntryList());
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONObject data = jo.optJSONObject("data");
            JSONArray entryList = data == null ? null : data.optJSONArray("entryList");
            if (entryList == null) {
                return true;
            }
            for (int i = 0; i < entryList.length(); i++) {
                JSONObject entry = entryList.optJSONObject(i);
                if (entry == null || !"ALCHEMY_STAGE_REWARD".equals(entry.optString("entryCode"))) {
                    continue;
                }
                JSONObject nextDayAwardDTO = entry.optJSONObject("nextDayAwardDTO");
                if (nextDayAwardDTO == null || !nextDayAwardDTO.optBoolean("awardAvailable", false)) {
                    return true;
                }
                JSONObject claimJo = new JSONObject(AlchemyRpcCall.claimAward());
                TimeUtil.sleep(500);
                if (MessageUtil.checkResultCode(TAG, claimJo)) {
                    Log.other("芝麻炼金🏆阶段奖励#获得[" + nextDayAwardDTO.optInt("pointValue", 0) + "]");
                }
                return true;
            }
            return true;
        } catch (Throwable t) {
            Log.i(TAG, "claimStageAward err:");
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    // ==================== 炼金金豆夺宝（bizType=ZHIMA） ====================

    /**
     * 炼金入口金豆夺宝：金豆苗收取 + 签到 + 任务（黑名单与农场金豆夺宝共用） + 芝麻粒换金豆
     */
    private static void goldenBean(boolean doTask, Set<String> goldenBeanList, boolean doExchange, int dailyLimit, int reserveZml) {
        try {
            JSONObject index = new JSONObject(GoldenBeanRpcCall.index(GoldenBeanRpcCall.BIZ_TYPE_ZHIMA, GoldenBeanRpcCall.SOURCE_ZHIMA, GoldenBeanRpcCall.VERSION_ZHIMA));
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, index)) {
                return;
            }

            // 金豆苗收取：调 index 时服务端自动结算，sproutCollectedDelta 为本轮收取量
            int sproutDelta = index.optInt("sproutCollectedDelta", 0);
            if (sproutDelta > 0) {
                Log.other("炼金金豆🌱金豆苗收取#获得[" + sproutDelta + "金豆]");
            }

            if (doTask) {
                // 同步炼金金豆任务标题到黑名单选择列表（与农场金豆夺宝共用一套任务黑名单）
                syncGoldenBeanTaskList(index);
                // 每日签到
                if (!Status.hasFlagToday("SesameAlchemy::zhimaSign")) {
                    if (zhimaSign(index.optJSONObject("signInfo"))) {
                        Status.flagToday("SesameAlchemy::zhimaSign");
                    }
                    TimeUtil.sleep(500);
                }
                // 任务列表（scene=GOLDEN_BEAN_ZHIMA_LIST）
                if (index.has("taskList")) {
                    doTaskList(index.optJSONArray("taskList"), goldenBeanList);
                }
            }

            // 芝麻粒换金豆
            if (doExchange) {
                exchangeManure(index.optJSONObject("manureExchangeInfo"), dailyLimit, reserveZml);
            }
        } catch (Throwable t) {
            Log.i(TAG, "goldenBean err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 把炼金金豆任务标题同步进 GoldenBeansTaskListMap（农场金豆夺宝共用选择列表），每日一次
     */
    private static void syncGoldenBeanTaskList(JSONObject index) {
        try {
            if (Status.hasFlagToday("BlackList::initAlchemyGB")) {
                return;
            }
            GoldenBeansTaskListMap.load();
            JSONArray taskList = index.optJSONArray("taskList");
            if (taskList != null) {
                for (int i = 0; i < taskList.length(); i++) {
                    JSONObject task = taskList.optJSONObject(i);
                    JSONObject taskDisplayConfig = task == null ? null : task.optJSONObject("taskDisplayConfig");
                    String title = taskDisplayConfig == null ? "" : taskDisplayConfig.optString("title");
                    if (!title.isEmpty()) {
                        GoldenBeansTaskListMap.add(title, title);
                    }
                }
            }
            GoldenBeansTaskListMap.save();
            Status.flagToday("BlackList::initAlchemyGB");
            Log.record("同步任务🉑炼金金豆任务列表(与农场金豆共用黑名单)");
        } catch (Throwable t) {
            Log.i(TAG, "syncGoldenBeanTaskList err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 炼金金豆每日签到
     *
     * @return true=已处理完（成功或无可签），false=本轮失败待重试
     */
    private static boolean zhimaSign(JSONObject signInfo) {
        try {
            if (signInfo == null) {
                return true;
            }
            if (signInfo.optBoolean("todaySigned", false)) {
                return true;
            }
            String signKey = signInfo.optString("currentSignKey");
            if (signKey.isEmpty()) {
                return true;
            }
            JSONObject jo = new JSONObject(GoldenBeanRpcCall.sign(GoldenBeanRpcCall.BIZ_TYPE_ZHIMA, GoldenBeanRpcCall.SOURCE_ZHIMA, GoldenBeanRpcCall.VERSION_ZHIMA, signKey));
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            int awardCount = 100;
            JSONArray signList = signInfo.optJSONArray("signList");
            if (signList != null) {
                for (int i = 0; i < signList.length(); i++) {
                    JSONObject item = signList.optJSONObject(i);
                    if (item != null && item.optBoolean("today", false)) {
                        awardCount = item.optInt("awardCount", 100);
                        break;
                    }
                }
            }
            Log.other("炼金金豆🎁每日签到#获得[" + awardCount + "金豆]");
            return true;
        } catch (Throwable t) {
            Log.i(TAG, "zhimaSign err:");
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    /**
     * 炼金金豆任务列表：遍历任务，黑名单任务仅领取，TODO 任务先 finishTask 再 receiveTaskAward。
     * 当日已尝试的任务打标防重复请求（二方换量任务无法RPC完成，避免每轮报错刷屏）。
     */
    private static void doTaskList(JSONArray taskList, Set<String> goldenBeanList) {
        if (taskList == null) {
            return;
        }
        for (int i = 0; i < taskList.length(); i++) {
            try {
                JSONObject task = taskList.optJSONObject(i);
                if (task == null) {
                    continue;
                }
                String sceneCode = task.optString("sceneCode");
                String taskType = task.optString("taskId");
                String taskStatus = task.optString("taskStatus");
                JSONObject taskDisplayConfig = task.optJSONObject("taskDisplayConfig");
                String title = taskDisplayConfig == null ? "" : taskDisplayConfig.optString("title");
                if (title.isEmpty()) {
                    title = taskType;
                }

                // 黑名单任务跳过，仅领取
                if (goldenBeanList != null && goldenBeanList.contains(title)) {
                    if ("FINISHED".equals(taskStatus) || "COMPLETED".equals(taskStatus) || "TO_RECEIVE".equals(taskStatus)) {
                        receiveTaskAward(sceneCode, taskType, title);
                    }
                    continue;
                }
                if ("RECEIVED".equals(taskStatus)) {
                    continue;
                }
                if ("FINISHED".equals(taskStatus)) {
                    receiveTaskAward(sceneCode, taskType, title);
                    continue;
                }
                if ("TODO".equals(taskStatus)) {
                    /*// 交易/支付类任务不申报：RPC无法完成且会反复失败，直接加入黑名单（配置无解禁周期，等同永久拉黑）
                    if (isTransactionTask(title)) {
                        MessageUtil.MarkTaskBlackList("AntOrchard", "GoldenBeansTaskList", "金豆任务", title);
                        Log.other("炼金金豆⏭️[" + title + "]交易/支付类#不申报，已拉黑");
                        continue;
                    }*/
                    // 多次任务（rightsTimesLimit>1）一次补齐：逐档finishTask，否则只完成第一档
                    int rightsTimes = task.optInt("rightsTimes", 0);
                    int rightsTimesLimit = task.optInt("rightsTimesLimit", 0);
                    int remain = rightsTimesLimit > rightsTimes ? rightsTimesLimit - rightsTimes : 1;
                    boolean anyDone = false;
                    for (int cnt = 0; cnt < remain; cnt++) {
                        if (!finishTask(sceneCode, taskType, title)) {
                            break; // 做不了就收手，不再空转剩余次数
                        }
                        anyDone = true;
                        TimeUtil.sleep(500);
                    }
                    if (anyDone) {
                        receiveTaskAward(sceneCode, taskType, title);
                    }
                }
                TimeUtil.sleep(500);
            } catch (Throwable t) {
                Log.i(TAG, "doTaskList err:");
                Log.printStackTrace(TAG, t);
            }
        }
    }

    /**
     * 交易/支付类任务判断（标题关键词）：此类任务需真实下单/付款，RPC申报无法完成且会反复失败
     */
    private static boolean isTransactionTask(String title) {
        if (title == null || title.isEmpty()) {
            return false;
        }
        String[] keywords = {"付款", "下单", "购买", "消费", "交易", "租赁", "租车", "租借", "信用购",
                "花呗", "分期", "借款", "贷款", "缴费", "充值", "下单立减", "购物金", "还贷"};
        for (String keyword : keywords) {
            if (title.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 完成炼金金豆任务
     */
    private static boolean finishTask(String sceneCode, String taskType, String title) {
        try {
            JSONObject jo = new JSONObject(GoldenBeanRpcCall.finishTask(GoldenBeanRpcCall.BIZ_TYPE_ZHIMA, GoldenBeanRpcCall.SOURCE_ZHIMA, GoldenBeanRpcCall.VERSION_ZHIMA, sceneCode, taskType));
            TimeUtil.sleep(500);
            MessageUtil.checkResultCodeAndMarkTaskBlackList("GoldenBeansTaskList", title, jo);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.other("炼金金豆🧾完成任务[" + title + "]");
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "finishTask err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    /**
     * 领取炼金金豆任务奖励
     */
    private static boolean receiveTaskAward(String sceneCode, String taskType, String title) {
        try {
            JSONObject jo = new JSONObject(GoldenBeanRpcCall.receiveTaskAward(GoldenBeanRpcCall.BIZ_TYPE_ZHIMA, GoldenBeanRpcCall.SOURCE_ZHIMA, GoldenBeanRpcCall.VERSION_ZHIMA, sceneCode, taskType));
            TimeUtil.sleep(500);
            MessageUtil.checkResultCodeAndMarkTaskBlackList("GoldenBeansTaskList", title, jo);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                int recvGot = jo.optInt("incAwardCount", 0);
                Log.other("炼金金豆🎁领取奖励[" + title + "]#获得[" + recvGot + "金豆]");
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "receiveTaskAward err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    /**
     * 芝麻粒换金豆（消耗芝麻粒，1芝麻=beanReward金豆）：三重约束——
     * 1. 剩余芝麻粒下限(reserveZml, 0=不限制)：兑换后剩余芝麻粒不得低于该值
     * 2. 当日兑换芝麻粒上限(dailyLimit, 0=不限制)：与服务器今日可兑芝麻粒(effectiveExchangeManure)看谁低取谁
     * 3. 服务端最低兑换额(minExchangeAmount，单位金豆)，低于则跳过
     * <p>
     * 抓包口径：currentManure=芝麻粒总余额；effectiveExchangeManure=今日还可兑的芝麻粒数(=remainQuota/beanReward)；
     * exchangeBeanAmount按金豆提交，服务端折算manureCost(芝麻粒)，响应beanDelta为实得金豆、remainQuota为兑换后剩余额度。
     */
    private static void exchangeManure(JSONObject exchangeInfo, int dailyLimit, int reserveZml) {
        try {
            if (exchangeInfo == null || !exchangeInfo.optBoolean("pageOpened", true)) {
                return;
            }
            int currentManure = exchangeInfo.optInt("currentManure", 0);
            int effective = exchangeInfo.optInt("effectiveExchangeManure", 0);
            int beanReward = exchangeInfo.optInt("beanReward", 0);
            int minExchange = exchangeInfo.optInt("minExchangeAmount", 0);
            int remainQuota = exchangeInfo.optInt("remainQuota", 0);
            // 服务端字段异常防御：折算比例/最低兑换额非法时不兑换（勿用默认值掩盖，避免超额消耗芝麻粒）
            if (beanReward <= 0 || minExchange <= 0) {
                Log.i(TAG, "换金豆服务端字段异常#beanReward=" + beanReward + "#minExchangeAmount=" + minExchange + "，跳过");
                return;
            }
            if (currentManure <= 0 || remainQuota <= 0) {
                return;
            }
            // 剩余芝麻粒下限(0=不限制)：只兑超出下限的部分
            if (reserveZml < 0) {
                reserveZml = 0;
            }
            int maxGrain = currentManure - reserveZml;
            if (maxGrain <= 0) {
                return;
            }
            // 当日兑换芝麻粒上限(0=不限制)：扣除当日已兑粒数（跨轮累计，Status跨天自动重置）
            if (dailyLimit > 0) {
                int exchangedGrain = Status.getGoldenBeanExchangeCountToday("ZHIMA") / beanReward;
                int remainLimit = dailyLimit - exchangedGrain;
                if (remainLimit <= 0) {
                    Log.i(TAG, "今日已兑" + exchangedGrain + "粒达上限" + dailyLimit + "，跳过换金豆");
                    return;
                }
                if (maxGrain > remainLimit) {
                    maxGrain = remainLimit;
                }
            }
            if (effective > 0 && maxGrain > effective) {
                maxGrain = effective;
            }
            int beans = maxGrain * beanReward;
            // 服务器剩余额度兜底，向下取整保持整粒兑换
            if (beans > remainQuota) {
                beans = (remainQuota / beanReward) * beanReward;
            }
            if (beans < minExchange) {
                return;
            }
            JSONObject jo = new JSONObject(GoldenBeanRpcCall.manureExchange(GoldenBeanRpcCall.BIZ_TYPE_ZHIMA, GoldenBeanRpcCall.SOURCE_ZHIMA, GoldenBeanRpcCall.VERSION_ZHIMA, beans));
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            // 按实际到账记账（服务端折算异常时虚记额度会导致后续轮超额兑换），未到账不记
            long beanDelta = jo.optLong("beanDelta", 0);
            if (beanDelta > 0) {
                Status.goldenBeanExchangeToday("ZHIMA", (int) Math.min(beanDelta, Integer.MAX_VALUE));
            }
            int manureCost = jo.optInt("manureCost", 0);
            int newRemainQuota = jo.optInt("remainQuota", -1);
            Log.other("炼金金豆💰芝麻粒换金豆#消耗[" + manureCost + "芝麻粒]获得[" + beanDelta + "金豆]"
                    + (newRemainQuota >= 0 ? "#今日剩余额度[" + newRemainQuota + "]" : ""));
        } catch (Throwable t) {
            Log.i(TAG, "exchangeManure err:");
            Log.printStackTrace(TAG, t);
        }
    }
}
