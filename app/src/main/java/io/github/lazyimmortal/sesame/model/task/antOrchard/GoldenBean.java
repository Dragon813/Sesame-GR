package io.github.lazyimmortal.sesame.model.task.antOrchard;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.github.lazyimmortal.sesame.model.task.antForest.AntForestRpcCall;
import io.github.lazyimmortal.sesame.model.task.antGame.GameTask;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.Status;
import io.github.lazyimmortal.sesame.util.TimeUtil;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

/**
 * 支付宝-金豆夺宝（芭芭农场下的新玩法）业务逻辑。
 * <p>
 * 已实现（接口均由抓包确认）：
 * <ul>
 *     <li>领支金豆/金豆苗收取（调 index 时服务端自动收取，读 sproutCollectedDelta 记录）</li>
 *     <li>每日签到领金豆（sign）</li>
 *     <li>每日求签夺宝（fortuneDraw）</li>
 *     <li>金猫矿工挖矿（miner.index + miner.grab 循环下钩抓豆）</li>
 *     <li>金豆乐园自动砸蛋（drawGameCenterAward，288豆/次）</li>
 *     <li>金豆乐园自动玩游戏刷砸蛋机会（GameTask channel=goldenbean，上报农场上车车/对对碰乐园）</li>
 *     <li>完成任务领金豆（finishTask + receiveTaskAward，带黑名单与当日去重，尊重风控）</li>
 *     <li>肥料换金豆（manureExchange，支持保留数量策略，避免耗尽农场肥料）</li>
 * </ul>
 * 风控：每日去重/服务端状态判断/循环限速/死循环保护；结果写 farm.log，诊断写 debug.log。
 */
public class GoldenBean {

    private static final String TAG = "GoldenBean";

    /**
     * 完成任务黑名单：这些任务需要真实交互（玩游戏/支付/外跳/订阅），
     * 不主动通过 RPC 完成，避免异常请求触发风控。按 taskDisplayConfig.type 匹配。
     */
    private static final Set<String> TASK_TYPE_BLACKLIST = new HashSet<>();

    /**
     * 按 taskType(spmExtend里的类型)黑名单：这些任务不支持 finishTask 直接完成，
     * 靠对应功能的实际动作完成，避免"不支持rpc调用"等无效请求。
     */
    private static final Set<String> TASK_ID_BLACKLIST = new HashSet<>();

    static {
        TASK_TYPE_BLACKLIST.add("GAME_ZH");          // 玩游戏类（寻道大千/消消消/对对碰/逛金豆乐园…）
        TASK_TYPE_BLACKLIST.add("KUAISHOU");         // 逛一逛快手（外跳）
        TASK_TYPE_BLACKLIST.add("XIANSHANGZHIFU");   // 线上支付
        TASK_TYPE_BLACKLIST.add("XIANXIAZHIFU");     // 到店支付
        TASK_TYPE_BLACKLIST.add("YUEBAO");           // 余额宝攒钱
        TASK_TYPE_BLACKLIST.add("XIAOXIDINGYUE");    // 消息订阅（涉及用户设置，不主动订阅）
        TASK_TYPE_BLACKLIST.add("WAKUANG");          // 挖矿（由金猫矿工功能完成）

        TASK_ID_BLACKLIST.add("MANURE_EXCHANGE");    // 肥料兑换支金豆（靠实际换肥料完成，finishTask 返回"不支持rpc调用"）
    }

    /**
     * 金豆夺宝主入口，由 AntOrchard.run() 在完成农场逻辑后调用。
     *
     * @param doExchange    肥料换金豆（单独开关，因为会消耗农场肥料）
     * @param reserveManure 换豆时保留的肥料数量
     */
    public void run(boolean goldenBeansTask, Set<String> GoldenBeansTaskList, boolean GoldenBeansGameCenter, boolean doExchange, int reserveManure) {

        try {
            String indexStr = GoldenBeanRpcCall.index();
            JSONObject index = new JSONObject(indexStr);
            if (!MessageUtil.checkResultCode(TAG, index)) {
                dbg("首页index校验失败，原始返回=" + brief(indexStr));
                return;
            }
            // 金豆概况 + 领支金豆(金豆苗收取，调 index 时服务端已自动收取)
            summary(index);
            sign(index.optJSONObject("signInfo"));
            fortuneDraw(index.optJSONObject("fortuneDrawInfo"));
            miner();
            if (goldenBeansTask) {
                //完成两个任务列表
                if (index.has("taskList")) {
                    doTaskList(index.optJSONArray("taskList"), "金豆", GoldenBeansTaskList);
                }
                if (index.has("farmTaskList")) {
                    doTaskList(index.optJSONArray("farmTaskList"), "肥料", GoldenBeansTaskList);
                }
            }
            if (GoldenBeansGameCenter) {
                gameCenter(index);
                playGame();
            }
            if (doExchange) {
                exchangeManure(index.optJSONObject("manureExchangeInfo"), reserveManure);
            }
        } catch (Throwable t) {
            dbg("主流程异常: " + t.getMessage());
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 截断超长字符串，避免调试日志过大
     */
    private static String brief(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() > 300 ? s.substring(0, 300) + "...(截断)" : s;
    }

    /**
     * 金豆概况 + 领支金豆（金豆苗收取）。
     * <p>
     * 抓包确认：调用 goldenbean.index 时服务端会自动收取金豆苗，收取量在 sproutCollectedDelta 里返回，
     * 因此"领支金豆"无需额外请求——本方法只负责把已发生的收取记入日志，并打印一条中文金豆概况，便于监控。
     * 全程只读 index 数据，不发任何新请求，不影响原有功能。
     */
    private static void summary(JSONObject index) {
        try {
            // 领支金豆(金豆苗)：调 index/sync 时服务端结算，收取量在 sproutInfo.beanDelta 返回
            int sproutCollected = 0;
            JSONObject si = index.optJSONObject("sproutInfo");
            if (si != null) {
                sproutCollected = si.optInt("beanDelta", 0);
            }
            if (sproutCollected <= 0) {
                JSONObject gsi = index.optJSONObject("goldenBeanSproutInfo");
                if (gsi != null) {
                    sproutCollected = gsi.optInt("beanDelta", 0);
                }
            }
            if (sproutCollected > 0) {
                Log.farm("金豆夺宝🌱领支金豆(金豆苗)#获得[" + sproutCollected + "金豆]");
            }
            // 中文金豆概况（写入 farm.log，便于直接查看余额/进度）
            JSONObject jarInfo = index.optJSONObject("jarInfo");
            int balance = 0, capacity = 99999, jarCount = 0;
            boolean jarFull = false;
            if (jarInfo != null) {
                balance = jarInfo.optInt("currentProgress", 0);
                capacity = jarInfo.optInt("jarCapacity", 99999);
                jarCount = jarInfo.optInt("jarCount", 0);
                jarFull = jarInfo.optBoolean("jarFull", false);
            }
            int pendingBeans = 0;
            JSONObject sproutInfo = index.optJSONObject("sproutInfo");
            if (sproutInfo != null) {
                pendingBeans = sproutInfo.optInt("pendingBeans", 0);
            }
            int taskAward = index.optInt("taskAwardToReceive", 0);
            int needForJar = Math.max(0, capacity - balance);
            dbg("金豆概况: 金豆余额=" + balance + " 金豆罐=" + jarCount + " 距兑罐还需=" + needForJar + " 金豆苗待收=" + pendingBeans + " 待领任务奖励=" + taskAward);
            if (jarFull) {
                dbg("金豆概况: 金豆已满" + capacity + "，可兑换金豆罐(自动兑罐暂未实现，缺兑罐RPC抓包)");
            }
        } catch (Throwable t) {
            dbg("金豆概况解析异常: " + t.getMessage());
        }
    }

    /**
     * 每日签到领金豆
     */
    private static void sign(JSONObject signInfo) {
        try {
            if (signInfo == null) {
                dbg("签到: 无 signInfo，跳过");
                return;
            }
            if (signInfo.optBoolean("todaySigned", false)) {
//                dbg("签到: 今日已签到，跳过");
                return;
            }
            String signKey = signInfo.optString("currentSignKey");
            if (signKey.isEmpty()) {
                dbg("签到: currentSignKey 为空，跳过");
                return;
            }
            String resp = GoldenBeanRpcCall.sign(signKey);
            JSONObject jo = new JSONObject(resp);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                dbg("签到: 提交失败(signKey=" + signKey + ")，返回=" + brief(resp));
                return;
            }
            int awardCount = getTodayAwardCount(signInfo);
            Log.farm("金豆夺宝🎁每日签到#获得" + awardCount + "金豆");
        } catch (Throwable t) {
            dbg("签到异常: " + t.getMessage());
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 从签到列表里取当日的奖励数量，取不到则默认 100
     */
    private static int getTodayAwardCount(JSONObject signInfo) {
        try {
            JSONArray signList = signInfo.optJSONArray("signList");
            if (signList != null) {
                for (int i = 0; i < signList.length(); i++) {
                    JSONObject item = signList.optJSONObject(i);
                    if (item != null && item.optBoolean("today", false)) {
                        return item.optInt("awardCount", 100);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return 100;
    }

    /**
     * 每日求签夺宝
     */
    private static void fortuneDraw(JSONObject fortuneDrawInfo) {
        try {
            if (fortuneDrawInfo != null && fortuneDrawInfo.optBoolean("todayDrawn", false)) {
//                dbg("求签: 今日已求签(index标记)，跳过");
                return;
            }
            // 本地当日标记兜底：index 的 todayDrawn 偶尔不准，避免每轮重复请求
            if (Status.hasFlagToday("GoldenBean::fortuneDraw")) {
//                dbg("求签: 今日已求签(本地标记)，跳过");
                return;
            }
            String resp = GoldenBeanRpcCall.fortuneDraw();
            JSONObject jo = new JSONObject(resp);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                // "今日已抽过签"等属正常已完成，不算失败，打标避免重复
                String desc = jo.optString("resultDesc") + jo.optString("memo");
                if (desc.contains("已抽") || desc.contains("已完成") || desc.contains("已领")) {
                    Status.flagToday("GoldenBean::fortuneDraw");
//                    dbg("求签: 今日已求签(服务端返回:" + desc + ")，打标跳过");
                } else {
                    dbg("求签: 提交失败，返回=" + brief(resp));
                }
                return;
            }
            Status.flagToday("GoldenBean::fortuneDraw");
            int beanDelta = jo.optInt("beanDelta", 0);
            String typeName = "";
            JSONObject fortuneType = jo.optJSONObject("fortuneType");
            if (fortuneType != null) {
                typeName = fortuneType.optString("typeName");
            }
            Log.farm("金豆夺宝🔮求签夺宝#" + (typeName.isEmpty() ? "" : "[" + typeName + "]") + "获得[" + beanDelta + "金豆]");
        } catch (Throwable t) {
            dbg("求签异常: " + t.getMessage());
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 肥料换金豆：仅兑换超出保留数量的部分，受每日剩余额度限制，且不低于最低兑换额。
     */
    private static void exchangeManure(JSONObject exchangeInfo, int reserveManure) {
        try {
            if (exchangeInfo == null) {
                dbg("换豆: 无 manureExchangeInfo，跳过");
                return;
            }
            if (!exchangeInfo.optBoolean("farmOpened", true) || !exchangeInfo.optBoolean("pageOpened", true)) {
                dbg("换豆: farmOpened/pageOpened 未开启，跳过");
                return;
            }
            // 当前肥料余额（优先用 effectiveExchangeManure，其次 currentManure）
            int currentManure = exchangeInfo.optInt("effectiveExchangeManure", exchangeInfo.optInt("currentManure", 0));
            int minExchange = exchangeInfo.optInt("minExchangeAmount", 800);
            int remainQuota = exchangeInfo.optInt("remainQuota", 0);

            if (reserveManure < 0) {
                reserveManure = 0;
            }
            // 可兑换 = 余额 - 保留数量，再受每日剩余额度约束
            int exchangeable = currentManure - reserveManure;
            if (exchangeable > remainQuota) {
                exchangeable = remainQuota;
            }
            dbg("换豆: 当前肥料=" + currentManure + " 保留=" + reserveManure + " 每日剩余额度=" + remainQuota + " 最低兑换=" + minExchange + " 实际可兑换=" + exchangeable);
            if (exchangeable < minExchange) {
                dbg("换豆: 可兑换 " + exchangeable + " < 最低 " + minExchange + "，跳过");
                return;
            }

            String resp = GoldenBeanRpcCall.manureExchange(exchangeable);
            JSONObject jo = new JSONObject(resp);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                dbg("换豆: 提交失败(兑换" + exchangeable + ")，返回=" + brief(resp));
                return;
            }
            int manureCost = jo.optInt("manureCost", exchangeable);
            int beanReward = exchangeInfo.optInt("beanReward", 1);
            long gotBeans = (long) manureCost * beanReward;
            Log.farm("金豆夺宝💰肥料换金豆#消耗[" + manureCost + "肥料]获得[" + gotBeans + "金豆]");
        } catch (Throwable t) {
            dbg("肥料换金豆异常: " + t.getMessage());
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 金猫矿工挖矿：循环下钩抓取棋盘上价值最高的金豆，直至用完当日次数。
     */
    private static void miner() {
        try {
            // 今日次数已用完则不再请求 miner.index，减少空转（挖矿次数服务端每日重置）
            if (Status.hasFlagToday("GoldenBean::minerDone")) {
                return;
            }
            String indexStr = GoldenBeanRpcCall.minerIndex();
            JSONObject index = new JSONObject(indexStr);
            if (!MessageUtil.checkResultCode(TAG, index)) {
                dbg("挖矿: miner.index 校验失败，返回=" + brief(indexStr));
                return;
            }
            JSONObject minerInfo = index.optJSONObject("minerInfo");
            if (minerInfo == null) {
                dbg("挖矿: 无 minerInfo，跳过");
                return;
            }
            JSONObject currentLevel = minerInfo.optJSONObject("currentLevel");
            JSONObject taskProgress = minerInfo.optJSONObject("taskProgress");
            JSONObject progress = minerInfo.optJSONObject("progress");
            int remainingTimes = taskProgress == null ? 0 : taskProgress.optInt("remainingTimes", 0);
            boolean canGrab = taskProgress != null && taskProgress.optBoolean("canGrab", false);
            dbg("挖矿: 剩余次数=" + remainingTimes + " canGrab=" + canGrab + " 棋盘=" + (currentLevel != null));
            if (remainingTimes <= 0 || !canGrab || currentLevel == null) {
                if (remainingTimes <= 0) {
                    Status.flagToday("GoldenBean::minerDone");
                }
                dbg("挖矿: 无可用次数或不可抓取，跳过");
                return;
            }

            long totalBeans = 0;
            int grabbed = 0;
            // 本关已尝试(抓过/失败)的 itemId，避免重复抓同一个导致"操作失败"；换关时清空
            Set<String> usedIds = new HashSet<>();
            int levelIndex = progress == null ? 0 : progress.optInt("currentLevelIndex", 0);
            addGrabbedIds(usedIds, progress);
            // 上限保护，防止服务端异常导致死循环
            int guard = remainingTimes + 10;
            while (remainingTimes > 0 && canGrab && currentLevel != null && guard-- > 0) {
                String itemId = pickBestBean(currentLevel.optJSONArray("items"), usedIds);
                if (itemId == null) {
                    break;
                }
                usedIds.add(itemId); // 先标记，无论成败都不再重复抓
                String grabResp = GoldenBeanRpcCall.minerGrab("BEAN", itemId);
                JSONObject grab = new JSONObject(grabResp);
                if (!MessageUtil.checkResultCode(TAG, grab)) {
                    sleep(500);
                    continue;
                }
                int reward = grab.optInt("baseReward", 0) + grab.optInt("extraReward", 0);
                totalBeans += reward;
                grabbed++;
                // 更新棋盘、进度与剩余次数
                currentLevel = grab.optJSONObject("currentLevel");
                progress = grab.optJSONObject("progress");
                JSONObject tp = grab.optJSONObject("taskProgress");
                if (tp == null) {
                    break;
                }
                remainingTimes = tp.optInt("remainingTimes", 0);
                canGrab = tp.optBoolean("canGrab", false);
                // 换关则重置已抓集合（不同关卡 itemId 可能重名）
                int newLevelIndex = progress == null ? levelIndex : progress.optInt("currentLevelIndex", levelIndex);
                if (newLevelIndex != levelIndex) {
                    levelIndex = newLevelIndex;
                    usedIds.clear();
                    addGrabbedIds(usedIds, progress);
                }
                sleep(500);
            }
            if (remainingTimes <= 0) {
                Status.flagToday("GoldenBean::minerDone");
            }
            if (grabbed > 0) {
                Log.farm("金豆夺宝⛏️金猫矿工#下钩[" + grabbed + "次]获得[" + totalBeans + "金豆]");
            }
        } catch (Throwable t) {
            dbg("金猫矿工异常: " + t.getMessage());
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 把 progress.grabbedItemIds 合并进已用集合
     */
    private static void addGrabbedIds(Set<String> set, JSONObject progress) {
        try {
            if (progress == null) {
                return;
            }
            JSONArray arr = progress.optJSONArray("grabbedItemIds");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String id = arr.optString(i, "");
                    if (!id.isEmpty()) {
                        set.add(id);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 从棋盘物品里挑价值最高、且未抓取过的金豆（跳过石头 STONE / beanValue<=0 / 已抓过的）
     */
    private static String pickBestBean(JSONArray items, Set<String> exclude) {
        if (items == null) {
            return null;
        }
        String bestId = null;
        int bestValue = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (!"BEAN".equalsIgnoreCase(item.optString("type"))) {
                continue;
            }
            String itemId = item.optString("itemId");
            if (itemId.isEmpty() || (exclude != null && exclude.contains(itemId))) {
                continue;
            }
            int value = item.optInt("beanValue", 0);
            if (value > bestValue) {
                bestValue = value;
                bestId = itemId;
            }
        }
        return bestId;
    }

    /**
     * 完成任务领金豆：遍历 TODO 任务，跳过黑名单类型与当日已尝试的任务，
     * 先 finishTask 再 receiveTaskAward。当日无论成功失败都打标，避免重复请求。
     */
    private void doTaskList(JSONArray taskList, String awardType, Set<String> GoldenBeansTaskList) {
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
                String title = taskDisplayConfig.optString("title");

                //黑名单任务跳过,仅领取
                if (GoldenBeansTaskList.contains(title)) {
                    if (taskStatus.equals("FINISHED") || taskStatus.equals("COMPLETED") || taskStatus.equals("TO_RECEIVE")) {
                        goldenBeansReceiveTaskAward(sceneCode, taskType, title, awardType);
                    }
                    continue;
                }
                if (taskStatus.equals("RECEIVED")) {
                    continue;
                }
                if (taskStatus.equals("FINISHED") && !goldenBeansReceiveTaskAward(sceneCode, taskType, title, awardType)) {
                    continue;
                }
                if (taskStatus.equals("TODO") && !goldenBeansFinishTask(sceneCode, taskType, title)) {
                    goldenBeansReceiveTaskAward(sceneCode, taskType, title, awardType);
                    continue;
                }
                sleep(500);
            } catch (Throwable t) {
                Log.printStackTrace(TAG, t);
            }
        }
    }

    private boolean goldenBeansFinishTask(String sceneCode, String taskType, String title) {
        try {
            JSONObject jo = new JSONObject(GoldenBeanRpcCall.finishTask(sceneCode, taskType));
            MessageUtil.checkResultCodeAndMarkTaskBlackList("GoldenBeansTaskList", title, jo);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.farm("农场金豆🧾完成任务[" + title + "]");
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "goldenBeansFinishTask err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private boolean goldenBeansReceiveTaskAward(String sceneCode, String taskType, String title, String awardType) {
        try {
            JSONObject jo = new JSONObject(GoldenBeanRpcCall.receiveTaskAward(sceneCode, taskType));
            MessageUtil.checkResultCodeAndMarkTaskBlackList("GoldenBeansTaskList", title, jo);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                int recvGot = jo.optInt("incAwardCount", 0);
                Log.farm("农场金豆🎁领取奖励[" + title + "]#获得" + recvGot + awardType);
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "goldenBeansReceiveTaskAward err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }


    /**
     * 从 spmExtend.iepTaskTracer 里解析 taskType，取不到则回退用 taskId
     */
    private static String parseTaskType(JSONObject task) {
        try {
            JSONObject spmExtend = task.optJSONObject("spmExtend");
            if (spmExtend != null) {
                String tracer = spmExtend.optString("iepTaskTracer");
                int idx = tracer.indexOf("taskType:");
                if (idx >= 0) {
                    String sub = tracer.substring(idx + "taskType:".length());
                    int end = sub.indexOf('~');
                    return end >= 0 ? sub.substring(0, end) : sub;
                }
            }
        } catch (Throwable ignored) {
        }
        return task.optString("taskId", "");
    }

    /**
     * 金豆乐园自动砸蛋：把已攒到的砸蛋机会（gameCenterDrawRightsCount）全部换成金豆。
     * 机会由玩游戏/新人奖励产生，本功能只负责领取已有机会，不自动玩游戏。
     */
    private static void gameCenter(JSONObject index) {
        try {
            if (!index.optBoolean("showGameCenter", true)) {
                return;
            }
            int rights = index.optInt("gameCenterDrawRightsCount", 0);
            if (rights <= 0) {
                return;
            }
            while (rights > 0) {
                String resp = GoldenBeanRpcCall.drawGameCenterAward(1);
                JSONObject jo = new JSONObject(resp);
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    int drawGot = 0;
                    JSONArray awardList = jo.optJSONArray("gameCenterDrawAwardList");
                    if (awardList != null) {
                        for (int i = 0; i < awardList.length(); i++) {
                            JSONObject award = awardList.optJSONObject(i);
                            if (award != null && "GOLDEN_BEAN".equalsIgnoreCase(award.optString("awardType"))) {
                                drawGot += award.optInt("awardCount", 0);
                            }
                        }
                    }
                    JSONObject drawRights = jo.optJSONObject("gameCenterDrawRights");
                    rights = drawRights == null ? 0 : drawRights.optInt("quotaCanUse", 0);
                    //int usedQuota = drawRights == null ? 0 : drawRights.optInt("usedQuota", 0);
                    int quotaCanUse = drawRights == null ? 0 : drawRights.optInt("quotaCanUse", 0);
                    Log.farm("砸出金豆🎖️" + drawGot + "豆[剩蛋" + quotaCanUse + "个]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                    sleep(500);
                } else {
                    rights--;
                }
            }
        } catch (Throwable t) {
            dbg("金豆乐园砸蛋异常: " + t.getMessage());
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 目标为把今日砸蛋机会刷满(20)。刷到的机会由 gameCenter() 自动砸(288豆/次)。
     * 走第三方 gamesapi2 异步上报，失败最多不加蛋，无风控风险。
     */
    private static void playGame() {
        int usedQuota = 0;
        int quotaLimit = 20;
        try {
            JSONObject gl = new JSONObject(GoldenBeanRpcCall.queryGameCenterList());
            JSONObject rights = gl.optJSONObject("gameCenterDrawRights");
            if (rights != null) {
                usedQuota = rights.optInt("usedQuota", 0);
                quotaLimit = rights.optInt("quotaLimit", 20);
            }
            int remaining = quotaLimit - usedQuota;
            if (remaining > 0) {
                GameTask.GoldenBean_ncscc.report("金豆乐园", remaining);
            }
        } catch (Throwable t) {
            dbg("玩游戏异常: " + t.getMessage());
            Log.printStackTrace(TAG, t);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 调试日志：走 Log.debug() 写入 debug.log，不进用户可见的 farm.log，避免"跳过/概况"等
     * 大量诊断信息刷屏。farm.log 只保留真正的结果（Log.farm）。排查时看 debug.log，grep「金豆调试」。
     * 仅用于排查，不影响主流程，任何异常都被吞掉，绝不阻断原有功能。
     */
    private static void dbg(String s) {
        try {
            Log.debug("金豆调试｜" + s);
        } catch (Throwable ignored) {
        }
    }
}