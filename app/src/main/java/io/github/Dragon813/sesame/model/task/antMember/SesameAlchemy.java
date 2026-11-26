package io.github.Dragon813.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Set;

import io.github.Dragon813.sesame.model.task.antGame.TaskAlternative;
import io.github.Dragon813.sesame.model.task.antOrchard.GoldenBeanRpcCall;
import io.github.Dragon813.sesame.util.Log;
import io.github.Dragon813.sesame.util.TaskIconUtil;
import io.github.Dragon813.sesame.util.MessageUtil;
import io.github.Dragon813.sesame.util.Status;
import io.github.Dragon813.sesame.util.TimeUtil;
import io.github.Dragon813.sesame.util.idMap.GoldenBeansTaskListMap;

/**
 * 支付宝-芝麻炼金（芝麻信用-攒芝麻分-芝麻炼金）+ 炼金入口金豆夺宝业务逻辑。
 * <p>
 * 已实现（接口于 2026-10-19 确认）：
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
                // 奖励以completeTask响应data.zmlNum为准，缺失回退queryTask的rewardAmount
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
     * 手动运行入口（扩展功能 → 手动运行 → 炼金金豆小游戏）：只跑金豆夺宝里的任务，
     * 包含 ZHIMA_youxi_* 小游戏的上报与领取，不碰炼金/签到/兑换。
     * 上报渠道优先取任务卡片 targetUrl 里的 channel，取不到时回落到默认值 GOLDENBEAN_ZHIMA。
     */
    public static void runGoldenBeanOnly() {
        try {
            // 渠道留空：上报时会优先用任务卡片 targetUrl 里的 channel（真实客户端就是这么发的），
            // 取不到才回落到默认值 GOLDENBEAN_ZHIMA。
            goldenBean(true, null, false, 0, 0);
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

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
                        GoldenBeansTaskListMap.add(title, TaskIconUtil.extractIcon(taskDisplayConfig));
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

                // 小游戏任务（ZHIMA_youxi_*）：进度由支付宝开放平台记账，finishTask 伪造无效，
                // 必须让真实游戏 SDK 把动作报上去，这里调游戏中心那套厂商上报。
                if (taskType.startsWith("ZHIMA_youxi_")) {
                    // 未开启「芝麻信用 | 金豆夺宝」时跳过，避免对游戏任务发无效 finishTask
                    if (!zhimaGameEnabled) {
                        continue;
                    }
                    final String gameChannel = "GOLDENBEAN_ZHIMA";
                    int rightsTimes = task.optInt("rightsTimes", 0);
                    int rightsTimesLimit = Math.max(1, task.optInt("rightsTimesLimit", 1));
                    Log.other("炼金金豆🎮[" + title + "]状态=" + taskStatus
                            + " 进度=" + rightsTimes + "/" + rightsTimesLimit
                            + " 奖励=" + task.optString("awardCount"));
                    if ("RECEIVED".equals(taskStatus)) {
                        TimeUtil.sleep(300);
                        continue;
                    }
                    // 真实客户端把「拉起时的 channel」原样上报（haligame 的 action_finish_channel、
                    // mmhygame 的 eventChannel 都是 my.getLaunchOptionsSync().query.channel），
                    // 这个值就在任务 targetUrl 的 query=channel%3D<值> 里，优先用它。
                    String launchChannel = channelFromTargetUrl(taskDisplayConfig);
                    reportZhimaGame(taskType, title, launchChannel.isEmpty() ? gameChannel : launchChannel);
                    TimeUtil.sleep(800);
                    // 进度已满但状态仍是 TODO（支付宝只记进度、不自动发奖）→ 主动领取
                    if (rightsTimes >= rightsTimesLimit) {
                        receiveTaskAward(sceneCode, taskType, title);
                        TimeUtil.sleep(500);
                        continue;
                    }
                    // 厂商上报只是把动作存到厂商侧，支付宝侧可能还要等落账；先回查并领取。
                    // 按既定策略不自动打开/拉起游戏小程序，仍未落账就保留待办等下次刷新。
                    pollAndReceiveZhimaGame(sceneCode, taskType, title);
                    continue;
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
                    // 多次任务（rightsTimesLimit>1）一次补齐：逐档finishTask，否则只完成第一档
                    int rightsTimes = task.optInt("rightsTimes", 0);
                    int rightsTimesLimit = task.optInt("rightsTimesLimit", 0);
                    int remain = TaskAlternative.rightsTimesToDo(rightsTimes, rightsTimesLimit);
                    boolean anyDone = false;
                    for (int cnt = 0; cnt < remain; cnt++) {
                        if (!finishTask(sceneCode, taskType, title,
                                TaskAlternative.rightsProgressText(rightsTimes, rightsTimesLimit, cnt))) {
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

    /** 「芝麻信用 | 金豆夺宝」开关：控制 ZHIMA_youxi_* 小游戏任务是否上报/领取。 */
    private static volatile boolean zhimaGameEnabled = false;

    public static void setZhimaGameEnabled(boolean enabled) {
        zhimaGameEnabled = enabled;
    }

    /** 炼金金豆夺宝里的小游戏任务 taskId → 游戏 appId（与游戏中心同一款游戏、同一个 appId） */
    private static final java.util.Map<String, String> ZHIMA_GAME_APP_ID = new java.util.HashMap<>();

    /** 炼金金豆小游戏上报渠道：实测只有这个值会被支付宝侧记账（lianjin / zmxy_jdrwlb 都不会）。 */
    private static final String ZHIMA_GAME_CHANNEL = "GOLDENBEAN_ZHIMA";

    static {
        ZHIMA_GAME_APP_ID.put("ZHIMA_youxi_dageluosi", "2021005139650112");        // 玩打个螺丝（haligame）
        ZHIMA_GAME_APP_ID.put("ZHIMA_youxi_zheguanwohenxing", "2021005132680209");  // 玩这关我很行（dianwanshidai）
        ZHIMA_GAME_APP_ID.put("ZHIMA_youxi_hebuguowoba", "2021005112626489");       // 去玩合不过我吧（mmhygame）
        ZHIMA_GAME_APP_ID.put("ZHIMA_youxi_zhishanglexiaoxiao", "2021006105651938"); // 去玩智商乐消消（devourad，表里有 appKey）
    }

    /**
     * 从任务卡片 taskDisplayConfig.targetUrl 里取出拉起游戏时的 channel，例如
     * alipays://platformapi/startapp?...&query=channel%3Dzmxy_jdrwlb → zmxy_jdrwlb。
     * 游戏 SDK 上报进度时会把这个值放在 action_finish_channel / eventChannel 里，支付宝按它记账。
     */
    private static String channelFromTargetUrl(JSONObject config) {
        if (config == null) {
            return "";
        }
        String url = config.optString("targetUrl");
        if (url == null || url.isEmpty()) {
            return "";
        }
        String marker = "channel%3D";
        int idx = url.indexOf(marker);
        if (idx < 0) {
            marker = "channel%3d";
            idx = url.indexOf(marker);
        }
        if (idx < 0) {
            marker = "channel=";
            idx = url.indexOf(marker);
        }
        if (idx < 0) {
            return "";
        }
        String rest = url.substring(idx + marker.length());
        int end = rest.length();
        int amp = rest.indexOf('&');
        if (amp >= 0 && amp < end) {
            end = amp;
        }
        int enc = rest.indexOf("%26");
        if (enc >= 0 && enc < end) {
            end = enc;
        }
        String ch = rest.substring(0, end);
        try {
            ch = java.net.URLDecoder.decode(ch, "UTF-8");
        }
        catch (Throwable ignored) {
        }
        return ch;
    }

    /**
     * 芝麻炼金金豆夺宝里的小游戏任务：taskId 形如 ZHIMA_youxi_xxx，进度由支付宝开放平台记账，
     * finishTask 伪造无效，只有对应游戏 SDK 把动作报上去才会变 FINISHED。
     * 这里复用小游戏上报通道，并把 channel 临时覆盖成炼金金豆所用的值。
     */
    private static void reportZhimaGame(String taskId, String title, String gameChannel) {
        try {
            // 老配置里这个值可能是 lianjin（早期默认值），但实测只有 GOLDENBEAN_ZHIMA 会被支付宝记账，
            // 换账号时各账号的旧配置也都还留着 lianjin，所以在运行期做一次归一，保证任何账号都能到账。
            if (gameChannel == null || gameChannel.isEmpty() || "lianjin".equals(gameChannel)) {
                gameChannel = ZHIMA_GAME_CHANNEL;
            }
            if (Status.hasFlagToday("SesameAlchemy::game_" + taskId)) {
                return;
            }
            String appId = ZHIMA_GAME_APP_ID.get(taskId);
            if (appId == null) {
                String unknownFlag = "SesameAlchemy::gameUnknown_" + taskId;
                if (!Status.hasFlagToday(unknownFlag)) {
                    Log.other("炼金金豆🎮[" + title + "]#暂无已知上报通道，需人工完成一次后才有记录");
                    Status.flagToday(unknownFlag);
                }
                return;
            }
            GameCenterMiniGameReporter.Spec spec = GameCenterMiniGameReporter.specOf(appId);
            if (spec == null) {
                return;
            }
            int done;
            GameCenterMiniGameReporter.setChannelOverride(gameChannel);
            try {
                done = GameCenterMiniGameReporter.report(spec, 3);
            }
            finally {
                GameCenterMiniGameReporter.setChannelOverride(null);
            }
            if (done <= 0) {
                Log.i("炼金金豆🎮[" + title + "]#厂商上报失败，渠道=" + gameChannel);
                return; // 不打标记：换个渠道或下次启动再试
            }
            Log.other("炼金金豆🎮[" + title + "]#厂商上报 " + done + "/3，渠道=" + gameChannel);
            Status.flagToday("SesameAlchemy::game_" + taskId);
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 上报后回查：重新拉取炼金金豆任务列表（scene=GOLDEN_BEAN_ZHIMA_LIST），找到同 taskId 的任务，
     * 状态变成 FINISHED/COMPLETED/TO_RECEIVE 或进度已满即领奖。最多 3 轮，每轮间隔 2 秒。
     * 说明：支付宝侧是「厂商上报 → 服务端记账 → 任务状态更新」，不是同步返回，所以必须回查。
     */
    private static void pollAndReceiveZhimaGame(String sceneCode, String taskType, String title) {
        for (int round = 1; round <= 3; round++) {
            TimeUtil.sleep(2000);
            JSONObject jo;
            try {
                jo = new JSONObject(GoldenBeanRpcCall.index(GoldenBeanRpcCall.BIZ_TYPE_ZHIMA, GoldenBeanRpcCall.SOURCE_ZHIMA, GoldenBeanRpcCall.VERSION_ZHIMA));
            } catch (Throwable t) {
                Log.i(TAG, "pollAndReceiveZhimaGame err:");
                Log.printStackTrace(TAG, t);
                return;
            }
            JSONArray taskList = jo.optJSONArray("taskList");
            if (taskList == null) {
                // 列表异常时不要静默当成「没进展」，留一行日志便于排查
                Log.other("炼金金豆🔄回查[" + title + "]#任务列表为空，第" + round + "轮");
                continue;
            }
            boolean found = false;
            for (int i = 0; i < taskList.length(); i++) {
                JSONObject task = taskList.optJSONObject(i);
                if (task == null || !taskType.equals(task.optString("taskId"))) {
                    continue;
                }
                found = true;
                String status = task.optString("taskStatus");
                int times = task.optInt("rightsTimes", 0);
                int limit = Math.max(1, task.optInt("rightsTimesLimit", 1));
                Log.other("炼金金豆🔄回查[" + title + "]第" + round + "轮 状态=" + status + " 进度=" + times + "/" + limit);
                if ("RECEIVED".equals(status)) {
                    return;
                }
                if ("FINISHED".equals(status) || "COMPLETED".equals(status) || "TO_RECEIVE".equals(status)
                        || times >= limit) {
                    if (receiveTaskAward(sceneCode, taskType, title)) {
                        return;
                    }
                }
                break;
            }
            if (!found) {
                // 任务已不在列表里（通常代表当天被处理完），没必要继续查
                Log.other("炼金金豆🔄回查[" + title + "]#任务已不在列表，停止回查");
                return;
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
    private static boolean finishTask(String sceneCode, String taskType, String title, String progress) {
        try {
            JSONObject jo = new JSONObject(GoldenBeanRpcCall.finishTask(GoldenBeanRpcCall.BIZ_TYPE_ZHIMA, GoldenBeanRpcCall.SOURCE_ZHIMA, GoldenBeanRpcCall.VERSION_ZHIMA, sceneCode, taskType));
            TimeUtil.sleep(500);
            MessageUtil.checkResultCodeAndMarkTaskBlackList("GoldenBeansTaskList", title, jo);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.other("炼金金豆🧾完成任务[" + title + "]" + progress);
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
     * 字段口径：currentManure=芝麻粒总余额；effectiveExchangeManure=今日还可兑的芝麻粒数(=remainQuota/beanReward)；
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
