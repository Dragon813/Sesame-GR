package io.github.Dragon813.sesame.model.task.antOrchard;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.github.Dragon813.sesame.model.task.antForest.AntForestRpcCall;
import io.github.Dragon813.sesame.model.task.antGame.GameTask;
import io.github.Dragon813.sesame.util.Log;
import io.github.Dragon813.sesame.util.MessageUtil;
import io.github.Dragon813.sesame.util.Status;
import io.github.Dragon813.sesame.util.TimeUtil;
import io.github.Dragon813.sesame.util.idMap.UserIdMap;
import io.github.Dragon813.sesame.model.task.antGame.TaskAlternative;
import io.github.Dragon813.sesame.model.normal.base.BaseModel;
import io.github.Dragon813.sesame.util.idMap.MiniGameTaskListMap;
import io.github.Dragon813.sesame.model.task.antGame.MiniGameVendorReporter;
import io.github.Dragon813.sesame.model.task.antGame.MiniGameTaskTable;
import io.github.Dragon813.sesame.model.task.antMember.GameCenterMiniGameReporter;

/**
 * 支付宝-金豆夺宝（芭芭农场下的新玩法）业务逻辑。
 * <p>
 * 已实现（接口均已确认）：
 * <ul>
 *     <li>领支金豆/金豆苗收取（调 index 时服务端自动收取，读 sproutCollectedDelta 记录）</li>
 *     <li>每日签到领金豆（sign）</li>
 *     <li>每日求签夺宝（fortuneDraw）</li>
 *     <li>金猫矿工挖矿（miner.index + miner.grab 循环下钩抓豆）</li>
 *     <li>金豆乐园自动砸蛋（drawGameCenterAward，288豆/次）</li>
 *     <li>金豆乐园自动玩游戏刷砸蛋机会（GameTask channel=goldenbean，上报农场上车车/对对碰乐园）</li>
 *     <li>完成任务领金豆（finishTask + receiveTaskAward，带黑名单与当日去重，尊重风控）</li>
 *     <li>浮动金豆收取（floatingBeans，限时豆过期作废，借鉴参考实现）</li>
 *     <li>限时加量弹窗任务（marketingPopupTask，模拟点击弹窗后领奖，借鉴参考实现）</li>
 *     <li>常用来访任务（用首页入口source重访index触发自动发奖，借鉴参考实现）</li>
 *     <li>游戏任务上报（对对碰乐园/森林小车车/森林救援队走gamesapi2真实上报，借鉴参考实现）</li>
 *     <li>肥料换金豆（manureExchange，支持当日兑换肥料上限与剩余肥料下限，上限与服务器取低者，均0=不限制）</li>
 * </ul>
 * 风控：每日去重/服务端状态判断/循环限速/死循环保护；结果写 farm.log，诊断写 debug.log。
 */
public class GoldenBean {

    private static final String TAG = "GoldenBean";

    /** 只领奖模式：手动运行「农场金豆领奖」时用，只走第 1 步领奖，不做任务。 */
    private static volatile boolean claimOnly = false;

    /** 抽抽乐「攒 N 金豆」三档的阈值（固定写死，不再做开关）。 */
    private static final int ZJD_TIER_1 = 5000;
    private static final int ZJD_TIER_2 = 8000;
    private static final int ZJD_TIER_3 = 20000;

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
     * 农场金豆夺宝里出现过的 GAME_ZH 小游戏：appId → 厂商/上报方式。
     * <p>
     * 2026-11-18 抓包与包体取证结论：这类游戏的进度都是「厂商服务器 → 支付宝开放平台记账」，
     * 客户端（H5/Unity/wasm）身上没有「明文 action_code + 开放平台提交」的调用
     * （保卫向日葵 Unity 数据包里 incrementGameAction/action_code/goldenbean 全 0 命中；
     * 梦幻消除战 kuaigames 包里同样 0 命中；保卫向日葵真正的对局走 wss）。
     * 因此这些任务只能「真人玩一次 → 模块自动领奖」，模块不要盲发 finishTask。
     * <p>
     * 2026-11-19 修正：适配表（MiniGameTaskTable）里已登记 SDK 族+动作码的游戏是例外——
     * 快乐拼拼豆(2021006148677438,gid=zfb_klppd) 用 /v2/zfb/taskReport(action=klppd_game_pass_every_1,
     * action_finish_channel=goldenbean) 直接上报后，金豆任务当场变「领取」，600豆到手。
     */
    private static final java.util.Map<String, String> GAME_ZH_VENDOR = new java.util.HashMap<>();

    static {
        GAME_ZH_VENDOR.put("2060170000380982", "kuaigames(服务端上报)");
        GAME_ZH_VENDOR.put("2060170000362587", "bhjuxian/yqmengyou(WS 服务端上报)");
        GAME_ZH_VENDOR.put("2021005122634802", "灵画师(未适配)");
        // 2026-11-19 抓包新增：这两款在静态适配表里有 SDK 族与动作码，可直接厂商上报
        GAME_ZH_VENDOR.put("2021006148677438", "aslk2018/gamesapi2(已适配-拼拼豆)");
        GAME_ZH_VENDOR.put("2060170000399540", "dianwanshidai(已适配-眼力拼图)");
        GAME_ZH_VENDOR.put("2021004184696771", "d3games(已适配-森林环游记)");
        GAME_ZH_VENDOR.put("2021004123689742", "mihuangame(已适配-躺平发育)");
        GAME_ZH_VENDOR.put("2060170000365575", "igame58(点点消不停)");
    }

    /** 从 targetUrl 里取 appId（形如 appId=2060170000362587） */
    private static String appIdOf(String targetUrl) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[?&]appId=(\\d+)")
                    .matcher(targetUrl == null ? "" : targetUrl);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static String vendorOf(String appId) {
        if (appId == null || appId.isEmpty()) {
            return "";
        }
        String v = GAME_ZH_VENDOR.get(appId);
        return v == null ? "" : v;
    }

    /**
     * 金豆夺宝主入口，由 AntOrchard.run() 在完成农场逻辑后调用。
     *
     * @param doExchange       肥料换金豆（单独开关，因为会消耗农场肥料）
     * @param reserveManure    剩余肥料下限（兑换后剩余肥料不低于该值，0=不限制）
     * @param dailyManureLimit 当日兑换肥料上限（与服务器今日剩余额度看谁低取谁，0=不限制）
     */
    public void run(boolean goldenBeansTask, Set<String> GoldenBeansTaskList, boolean GoldenBeansGameCenter, boolean doExchange, int reserveManure, int dailyManureLimit) {

        try {
            String indexStr = GoldenBeanRpcCall.index();
            JSONObject index = new JSONObject(indexStr);
            if (!MessageUtil.checkResultCode(TAG, index)) {
                return;
            }
            // 金豆概况 + 领支金豆(金豆苗收取，调 index 时服务端已自动收取)
            summary(index);
            sign(index.optJSONObject("signInfo"));
            fortuneDraw(index.optJSONObject("fortuneDrawInfo"));
            collectFloatingBeans();
            miner();
            // 全局监测：农场金豆任务/浮豆/矿工在砸蛋之前就会产豆，这里先查一次抽抽乐档位
            checkChouchouleGlobal();
            if (goldenBeansTask) {
                //限时加量弹窗任务先处理(参考实现, 弹窗任务不在taskList里)
                marketingPopup();
                //完成两个任务列表
                if (index.has("taskList")) {
                    doTaskList(index.optJSONArray("taskList"), "金豆", GoldenBeansTaskList);
                }
                if (index.has("farmTaskList")) {
                    doTaskList(index.optJSONArray("farmTaskList"), "肥料", GoldenBeansTaskList);
                }
                taskListDigest("金豆", index.optJSONArray("taskList"));
                taskListDigest("肥料", index.optJSONArray("farmTaskList"));
                checkChouchouleGlobal();
            }
            if (GoldenBeansGameCenter) {
                gameCenter(index);
                playGame();
                smashGameCenterAfterPlay();
                checkChouchouleGlobal();
            }
            if (doExchange) {
                exchangeManure(index.optJSONObject("manureExchangeInfo"), reserveManure, dailyManureLimit);
                // 全局监测：肥料换来的金豆也算进抽抽乐进度
                checkChouchouleGlobal();
            }
            // 收尾：把三档的当前进度打进日志
            reportChouchouleTasks();
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 手动运行入口（扩展功能 → 手动运行 → 农场金豆夺宝）。
     * <p>
     * 只跑「金豆概况/签到/求签/浮豆/矿工 + 任务列表」，不砸蛋、不换肥料，
     * 用于「真人把小游戏打通后立刻领奖」，不必等下一轮自动运行。
     */
    public static void runOnce() {
        try {
            new GoldenBean().run(true, new HashSet<String>(), false, false, 0, 0);
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 只领奖入口（扩展功能 → 手动运行 → 农场金豆领奖）。
     * <p>
     * 攒金豆模式下，任务做完但奖励攒着；抽抽乐要冲到下一档金豆时点这里，
     * 只把「已完成未领取」的金豆一次性领回来，不重复做任务、不发 finishTask。
     */
    public static void runClaimOnly() {
        try {
            claimOnly = true;
            new GoldenBean().run(true, new HashSet<String>(), false, false, 0, 0);
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        } finally {
            claimOnly = false;
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
     * 浮动金豆收取(借鉴参考实现)：sync取FLOATING_BEANS列表，逐个goldenbean.collect收取。
     * 浮动豆为限时豆(过期作废)，来源如逛淘工厂/逛淘宝等
     */
    private static void collectFloatingBeans() {
        try {
            String resp = GoldenBeanRpcCall.sync("\"FLOATING_BEANS\"");
            JSONObject jo = new JSONObject(resp);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject data = jo.optJSONObject("data");
            JSONArray floatingBeans = jo.optJSONArray("floatingBeans");
            if (floatingBeans == null && data != null) {
                floatingBeans = data.optJSONArray("floatingBeans");
            }
            if (floatingBeans == null || floatingBeans.length() == 0) {
                return;
            }
            int collected = 0;
            long totalBeans = 0;
            for (int i = 0; i < floatingBeans.length(); i++) {
                JSONObject bean = floatingBeans.optJSONObject(i);
                if (bean == null) {
                    continue;
                }
                String assetId = bean.optString("assetId");
                if (assetId.length() == 0) {
                    continue;
                }
                String source = bean.optString("outBizTypeDesc", "浮动金豆");
                int beanCount = bean.optInt("beanCount", 0);
                JSONObject collectJo = new JSONObject(GoldenBeanRpcCall.collect(assetId));
                if (MessageUtil.checkResultCode(TAG, collectJo)) {
                    collected++;
                    totalBeans += beanCount;
                    Log.farm("金豆夺宝🫘浮动金豆[" + source + "]#获得[" + beanCount + "金豆]");
                }
                sleep(500);
            }
        } catch (Throwable t) {
            Log.i(TAG, "collectFloatingBeans err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 限时加量弹窗任务(借鉴参考实现)：弹窗任务不在taskList中，从sync读marketingPopupTask，
     * TODO时模拟点击弹窗(trigger MARKETING_POPUP_CLICKED)后领取奖励，当日打标防重复
     */
    private static void marketingPopup() {
        try {
            String resp = GoldenBeanRpcCall.sync("\"MARKETING_POPUP\"");
            JSONObject jo = new JSONObject(resp);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject data = jo.optJSONObject("data");
            JSONObject popup = jo.optJSONObject("marketingPopupTask");
            if (popup == null && data != null) {
                popup = data.optJSONObject("marketingPopupTask");
            }
            if (popup == null || !"TODO".equals(popup.optString("taskStatus"))) {
                return;
            }
            String popupTaskId = popup.optString("taskId");
            if (popupTaskId.length() == 0 || Status.hasFlagToday("GoldenBean::popup_" + popupTaskId)) {
                return;
            }
            Status.flagToday("GoldenBean::popup_" + popupTaskId);
            JSONObject pd = popup.optJSONObject("taskDisplayConfig");
            String title = pd == null ? "限时加量" : pd.optString("title", "限时加量");
            //模拟点击弹窗(与页面点击一致)，再领奖
            GoldenBeanRpcCall.trigger(popupTaskId, "MARKETING_POPUP_CLICKED");
            sleep(500);
            String sceneCode = popup.optString("sceneCode", "GOLDEN_BEAN_MASTER_TASK");
            JSONObject award = new JSONObject(GoldenBeanRpcCall.receiveTaskAward(sceneCode, popupTaskId));
            if (MessageUtil.checkSuccess(TAG, award)) {
                Log.farm("金豆夺宝⏫限时加量[" + title + "]#获得[" + award.optInt("incAwardCount", 0) + "金豆]");
            }
        } catch (Throwable t) {
            Log.i(TAG, "marketingPopup err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 常用来访任务(借鉴参考实现)：服务端在用户访问金豆主页瞬间自动发奖(无领取接口)，
     * 用App首页入口source重调index触发，响应receptionAwardCount即本次奖励，当日打标防重复
     */
    private static void handleChangYongFangWen(String title) {
        try {
            if (Status.hasFlagToday("GoldenBean::changYongFangWen")) {
                return;
            }
            Status.flagToday("GoldenBean::changYongFangWen");
            JSONObject jo = new JSONObject(GoldenBeanRpcCall.indexFromHome());
            if (MessageUtil.checkResultCode(TAG, jo)) {
                int awardCount = jo.optInt("receptionAwardCount", 0);
                if (awardCount > 0) {
                    Log.farm("金豆夺宝🏠常用来访[" + title + "]#获得[" + awardCount + "金豆]");
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "handleChangYongFangWen err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * GAME_ZH游戏任务(借鉴参考实现)：已知游戏走gamesapi2真实上报完成任务得砸蛋机会。
     * 农场上车车/金豆乐园由playGame按剩余机会统一上报，此处跳过避免重复；未知游戏返回false保持原逻辑
     */
    private static boolean handleGameTaskByReport(String taskId, JSONObject taskDisplayConfig, String title) {
        try {
            if (Status.hasFlagToday("GoldenBean::game_" + taskId)) {
                return true;
            }
            GameTask game;
            int eggCount;
            if (title.contains("对对碰乐园")) {
                game = GameTask.GoldenBean_ddply;
                eggCount = Math.max(1, extractNumber(title) / 10);
            } else if (title.contains("森林小车车")) {
                game = GameTask.GoldenBean_slxcc;
                eggCount = Math.max(1, extractNumber(title) / 20);
            } else if (title.contains("森林救援队")) {
                game = GameTask.GoldenBean_sljyd;
                eggCount = Math.max(1, extractNumber(title) / 30);
            } else if (title.contains("农场上车车") || title.contains("金豆乐园")) {
                //playGame/gameCenter已覆盖，不重复上报
                return true;
            } else {
                return false;
            }
            game.report(title, eggCount);
            Status.flagToday("GoldenBean::game_" + taskId);
        } catch (Throwable t) {
            Log.i(TAG, "handleGameTaskByReport err:");
            Log.printStackTrace(TAG, t);
            return false;
        }
        return true;
    }

    /**
     * 金豆夺宝 GAME_ZH 通用厂商上报：只要静态适配表里登记了 SDK 族（且 gamesapi2 带 gid），
     * 就按任务标题匹配动作码（如快乐拼拼豆 klppd_game_pass_every_1）直接补报，频道固定 goldenbean。
     * 缺动作码/未适配的游戏返回 false，仍走原来的「需真人玩一次」提示。
     */
    private static boolean handleGameZhVendorReport(String taskId, String appId, String title,
                                                    int done, int limit) {
        if (appId == null || appId.isEmpty()) {
            return false;
        }
        if (!MiniGameVendorReporter.supports(appId)) {
            // 通用厂商表里没有时，回落到游戏中心的厂家表（dianwanshidai / jingyou / ultra / igame58 等）
            GameCenterMiniGameReporter.Spec gcSpec = GameCenterMiniGameReporter.specOf(appId);
            if (gcSpec == null || gcSpec.action == null || gcSpec.action.isEmpty()) {
                return false;
            }
            if (Status.hasFlagToday("GoldenBean::gc_" + taskId)) {
                return true;
            }
            int needGc = Math.max(1, limit - done);
            Log.i("农场金豆🎮[" + title + "]走游戏中心厂商表：" + gcSpec.title
                    + " action=" + gcSpec.action + " 次数=" + needGc + "（进度 " + done + "/" + limit + "）");
            GameCenterMiniGameReporter.setChannelOverride("goldenbean");
            int okGc = GameCenterMiniGameReporter.report(gcSpec, needGc);
            GameCenterMiniGameReporter.setChannelOverride(null);
            if (okGc > 0) {
                Status.flagToday("GoldenBean::gc_" + taskId);
                return true;
            }
            return false;
        }
        if (Status.hasFlagToday("GoldenBean::vendor_" + taskId)) {
            return true;
        }
        String action = MiniGameVendorReporter.resolveAction(appId, title);
        if (action.isEmpty()) {
            Log.i("农场金豆🎮[" + title + "]适配表有厂商但匹配不到动作码，跳过（需补充动作字典）");
            return false;
        }
        int need = Math.max(1, limit - done);
        Log.i("农场金豆🎮[" + title + "]走厂商上报：" + MiniGameVendorReporter.sdkName(appId)
                + " action=" + action + " 次数=" + need + "（进度 " + done + "/" + limit + "）");
        MiniGameVendorReporter.report(appId, title, "goldenbean", action, need, "GoldenBeansTaskList");
        Status.flagToday("GoldenBean::vendor_" + taskId);
        return true;
    }

    /**
     * 从文本提取第一个数字(游戏任务需求量，如"玩2组"取2)
     */
    private static int extractNumber(String text) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /**
     * 金豆概况 + 领支金豆（金豆苗收取）。
     * <p>
     * 调用 goldenbean.index 时服务端会自动收取金豆苗，收取量在 sproutCollectedDelta 里返回，
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
            // 领取为静默处理: 每次轮询都会自动收取2-3金豆, 日志过于频繁, 需要排查时再放开
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
            if (jarFull) {
            }
        } catch (Throwable t) {
        }
    }

    /**
     * 每日签到领金豆
     */
    private static void sign(JSONObject signInfo) {
        try {
            if (signInfo == null) {
                return;
            }
            if (signInfo.optBoolean("todaySigned", false)) {
                return;
            }
            String signKey = signInfo.optString("currentSignKey");
            if (signKey.isEmpty()) {
                return;
            }
            String resp = GoldenBeanRpcCall.sign(signKey);
            JSONObject jo = new JSONObject(resp);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            int awardCount = getTodayAwardCount(signInfo);
            Log.farm("金豆夺宝🎁每日签到#获得" + awardCount + "金豆");
        } catch (Throwable t) {
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
                return;
            }
            // 本地当日标记兜底：index 的 todayDrawn 偶尔不准，避免每轮重复请求
            if (Status.hasFlagToday("GoldenBean::fortuneDraw")) {
                return;
            }
            String resp = GoldenBeanRpcCall.fortuneDraw();
            JSONObject jo = new JSONObject(resp);
            // "今日已抽过签"等属正常已完成，先拦截打标避免重复，不经过通用错误日志
            String drawResultCode = jo.optString("resultCode");
            if (!"SUCCESS".equalsIgnoreCase(drawResultCode) && !"100".equals(drawResultCode)) {
                String desc = jo.optString("resultDesc") + jo.optString("memo");
                if (desc.contains("已抽") || desc.contains("已完成") || desc.contains("已领")) {
                    Status.flagToday("GoldenBean::fortuneDraw");
                    return;
                }
            }
            if (!MessageUtil.checkResultCode(TAG, jo)) {
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
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 肥料换金豆：三重约束——
     * 1. 剩余肥料下限(reserveManure, 0=不限制)：兑换后剩余肥料不得低于该值
     * 2. 当日兑换肥料上限(dailyManureLimit, 0=不限制)：与服务器今日剩余额度(remainQuota)看谁低取谁
     * 3. 服务端最低兑换额(minExchangeAmount)，低于则跳过
     */
    private static void exchangeManure(JSONObject exchangeInfo, int reserveManure, int dailyManureLimit) {
        try {
            if (exchangeInfo == null) {
                return;
            }
            if (!exchangeInfo.optBoolean("farmOpened", true) || !exchangeInfo.optBoolean("pageOpened", true)) {
                return;
            }
            // 当前肥料余额（优先用 effectiveExchangeManure，其次 currentManure）
            int currentManure = exchangeInfo.optInt("effectiveExchangeManure", exchangeInfo.optInt("currentManure", 0));
            int minExchange = exchangeInfo.optInt("minExchangeAmount", 800);
            int remainQuota = exchangeInfo.optInt("remainQuota", 0);

            // 剩余肥料下限(0=不限制): 只兑超出下限的部分
            if (reserveManure < 0) {
                reserveManure = 0;
            }
            int exchangeable = currentManure - reserveManure;
            // 当日兑换肥料上限(0=不限制): 按日累计(跨轮次,Status跨天自动重置)
            if (dailyManureLimit > 0) {
                int exchangedToday = Status.getGoldenBeanExchangeCountToday("FARM");
                int remainLimit = dailyManureLimit - exchangedToday;
                if (remainLimit <= 0) {
                    Log.i(TAG, "今日已兑" + exchangedToday + "肥料达上限" + dailyManureLimit + "，跳过换金豆");
                    return;
                }
                if (exchangeable > remainLimit) {
                    exchangeable = remainLimit;
                }
            }
            if (exchangeable > remainQuota) {
                exchangeable = remainQuota;
            }
            if (exchangeable < minExchange) {
                return;
            }

            String resp = GoldenBeanRpcCall.manureExchange(exchangeable);
            JSONObject jo = new JSONObject(resp);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            int manureCost = jo.optInt("manureCost", exchangeable);
            int beanReward = exchangeInfo.optInt("beanReward", 1);
            // 按实际到账记账(beanDelta优先,缺失回退估算);日累计记实际消耗肥料
            long gotBeans = jo.optLong("beanDelta", (long) manureCost * beanReward);
            if (manureCost > 0) {
                Status.goldenBeanExchangeToday("FARM", manureCost);
            }
            int newRemainQuota = jo.optInt("remainQuota", -1);
            Log.farm("金豆夺宝💰肥料换金豆#消耗[" + manureCost + "肥料]获得[" + gotBeans + "金豆]"
                    + (newRemainQuota >= 0 ? "#今日剩余额度[" + newRemainQuota + "]" : ""));
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 金猫矿工挖矿：循环下钩抓取棋盘上价值最高的金豆；当日次数用尽时，
     * 通过「试玩 30 秒小程序」补次数（该次数由支付宝服务端按容器停留时长记账），
     * 补到次数后立刻继续挖，直到没有次数可补。
     * <p>
     * 重构说明：原单轮 miner() 的抓取循环原样迁入 digMine()，此处改为多轮调度，
     * 原有矿工逻辑（选豆/下钩/换关重置/上限保护）一行未删。
     */
    private static void miner() {
        try {
            if (Status.hasFlagToday("GoldenBean::minerDone")) {
                return;
            }
            for (int round = 0; round < 4; round++) {
                JSONObject index = requestMinerIndex();
                if (index == null) {
                    return;
                }
                JSONObject minerInfo = index.optJSONObject("minerInfo");
                if (minerInfo == null) {
                    return;
                }
                JSONObject taskProgress = minerInfo.optJSONObject("taskProgress");
                int remainingTimes = taskProgress == null ? 0 : taskProgress.optInt("remainingTimes", 0);
                boolean canGrab = taskProgress != null && taskProgress.optBoolean("canGrab", false);
                Log.farm("金豆夺宝⛏️挖一挖状态：剩余次数=" + remainingTimes + " canGrab=" + canGrab);
                if (remainingTimes > 0 && canGrab && minerInfo.optJSONObject("currentLevel") != null) {
                    digMine(minerInfo);
                    continue;
                }
                Status.flagToday("GoldenBean::minerDone");
                return;
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    private static JSONObject requestMinerIndex() {
        try {
            JSONObject index = new JSONObject(GoldenBeanRpcCall.minerIndex());
            if (!MessageUtil.checkResultCode(TAG, index)) {
                return null;
            }
            return index;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return null;
        }
    }

    /**
     * 把当前剩余次数尽量挖完（一次调用内循环，直到没次数 / 没可抓格子）
     */
    private static void digMine(JSONObject minerInfo) {
        try {
            JSONObject currentLevel = minerInfo.optJSONObject("currentLevel");
            JSONObject taskProgress = minerInfo.optJSONObject("taskProgress");
            JSONObject progress = minerInfo.optJSONObject("progress");
            if (currentLevel == null) {
                return;
            }
            int remainingTimes = taskProgress == null ? 0 : taskProgress.optInt("remainingTimes", 0);
            boolean canGrab = taskProgress != null && taskProgress.optBoolean("canGrab", false);
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
            if (grabbed > 0) {
                Log.farm("金豆夺宝⛏️金猫矿工#下钩[" + grabbed + "次]获得[" + totalBeans + "金豆]");
            }
        } catch (Throwable t) {
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
     * 诊断用：打印任务列表构成（type×status 计数 + GAME_ZH 的 taskId 明细）。
     */
    private static void taskListDigest(String listName, JSONArray taskList) {
        try {
            if (taskList == null) {
                Log.i(TAG, listName + "任务列表 无(null)");
                return;
            }
            java.util.Map<String, Integer> counter = new java.util.TreeMap<>();
            StringBuilder games = new StringBuilder();
            for (int i = 0; i < taskList.length(); i++) {
                JSONObject task = taskList.optJSONObject(i);
                if (task == null) {
                    continue;
                }
                JSONObject cfg = task.optJSONObject("taskDisplayConfig");
                String type = cfg == null ? "?" : cfg.optString("type");
                counter.merge(type + "/" + task.optString("taskStatus"), 1, Integer::sum);
                if ("GAME_ZH".equals(type)) {
                    games.append(games.length() == 0 ? "" : "、").append(task.optString("taskId"))
                            .append("(").append(cfg.optString("title")).append(")");
                }
            }
            Log.i(TAG, listName + "任务列表 " + taskList.length() + " 条：" + counter);
            if (games.length() > 0) {
                Log.i(TAG, listName + " GAME_ZH 明细：" + games);
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
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
                int rightsTimes = task.optInt("rightsTimes", 0);
                int rightsTimesLimit = task.optInt("rightsTimesLimit", 0);
                JSONObject taskDisplayConfig = task.optJSONObject("taskDisplayConfig");
                String title = taskDisplayConfig == null ? taskType : taskDisplayConfig.optString("title");

                String desc = taskDisplayConfig == null ? "" : taskDisplayConfig.optString("desc");
                String targetUrl = taskDisplayConfig == null ? "" : taskDisplayConfig.optString("targetUrl");

                boolean claimable = taskStatus.equals("FINISHED") || taskStatus.equals("COMPLETED")
                        || taskStatus.equals("TO_RECEIVE");
                //只领奖模式：非「可领奖」的任务一律跳过，不发 finishTask
                if (claimOnly && !claimable) {
                    continue;
                }

                //第1步：可领奖任务优先领奖
                if (taskStatus.equals("FINISHED") || taskStatus.equals("COMPLETED") || taskStatus.equals("TO_RECEIVE")) {
                    goldenBeansReceiveTaskAward(sceneCode, taskType, title, awardType);
                    // 领奖后立刻监测抽抽乐档位（等 1.2 秒让服务端进度结算，静音）
                    sleep(1200);
                    checkChouchouleQuiet();
                    if (claimOnly) {
                        continue;
                    }
                    //还有剩余档位时继续往下推进，做到一次进入推满
                    if (rightsTimesLimit <= rightsTimes) {
                        continue;
                    }
                }
                //第2步：待完成的游戏任务（不在游戏任务黑名单里的直接调备用接口完成）
                //游戏任务(GAME_ZH): 已知游戏走gamesapi2真实上报, 未知游戏保持原逻辑(借鉴参考实现)
                if (taskDisplayConfig != null && "GAME_ZH".equals(taskDisplayConfig.optString("type")) && "TODO".equals(taskStatus)) {
                    //黑名单任务跳过
                    if (BaseModel.isMiniGameTaskDisallowed(title, desc, "GoldenBeansTaskList")) {
                        continue;
                    }
                    if (handleGameTaskByReport(taskType, taskDisplayConfig, title)) {
                        continue;
                    }
                }
                // 【追加】GAME_ZH 增强：探针 + 通用厂商上报 + 真人提示（不改动上方既有分支，已处理的会 continue 跳过这里）
                if (taskDisplayConfig != null && "GAME_ZH".equals(taskDisplayConfig.optString("type"))
                        && "TODO".equals(taskStatus)) {
                    String appId = appIdOf(targetUrl);
                    String vendor = vendorOf(appId);
                    Log.farm("农场金豆🎮[" + title + "]状态=" + taskStatus + " 进度=" + rightsTimes + "/" + rightsTimesLimit
                            + " 游戏=" + (appId.isEmpty() ? "?" : appId)
                            + (vendor.isEmpty() ? "" : " 厂商=" + vendor));
                    String probeFlag = "GoldenBean::probeFinish_" + taskType;
                    if (!Status.hasFlagToday(probeFlag)) {
                        Status.flagToday(probeFlag);
                        probeGameZhFinish(sceneCode, taskType, title);
                    }
                    if (handleGameZhVendorReport(taskType, appId, title, rightsTimes, rightsTimesLimit)) {
                        continue;
                    }
                    String manualFlag = "GoldenBean::manual_" + taskType;
                    if (!Status.hasFlagToday(manualFlag)) {
                        Log.farm("农场金豆🎮[" + title + "]#需真人玩一次（"
                                + (vendor.isEmpty() ? "厂商未知" : vendor)
                                + "，客户端没有上报通道），玩完点「扩展功能 → 手动运行 → 农场金豆夺宝」立刻领奖");
                        Status.flagToday(manualFlag);
                    }
                    continue;
                }
                //第3步：黑名单任务跳过（游戏/跳转第三方任务由 TaskAlternative 执行一次后跳过）
                if (GoldenBeansTaskList.contains(title)) {
                    TaskAlternative.handleGameTask(new TaskAlternative.GameTask("GoldenBeansTaskList",
                            title, desc, targetUrl, taskStatus)
                            .bizKey(taskType)
                            .rights(rightsTimes, rightsTimesLimit)
                            .taskRemain(Math.max(0, task.optInt("taskRequire", 0) - task.optInt("taskProgress", 0)))
                            .taskPlayType(task.optString("taskPlayType", task.optString("taskProdPlayType")))
                            .taskMode(task.optString("taskMode"))
                            .taskSceneCode(sceneCode)
                            .scene(sceneCode, GoldenBeanRpcCall.SOURCE)
                            .version(GoldenBeanRpcCall.VERSION));
                    continue;
                }
                if (taskStatus.equals("RECEIVED")) {
                    continue;
                }
                //第4步：按各任务方式完成，完成后立即领奖
                //常用来访任务: 服务端在访问瞬间自动发奖, 用首页入口source重访index触发(借鉴参考实现)
                if ("GOLDEN_BEAN_TASK_CHANGYONGFANGWEN".equals(taskType) && "TODO".equals(taskStatus)) {
                    handleChangYongFangWen(title);
                    continue;
                }
                if (taskStatus.equals("TODO") || rightsTimesLimit > rightsTimes) {
                    //有剩余档位时循环推满
                    int timesToDo = TaskAlternative.rightsTimesToDo(rightsTimes, rightsTimesLimit);
                    for (int cnt = 0; cnt < timesToDo; cnt++) {
                        if (!goldenBeansFinishTask(sceneCode, taskType, title,
                                taskDisplayConfig == null ? "" : taskDisplayConfig.optString("desc", ""),
                                TaskAlternative.rightsProgressText(rightsTimes, rightsTimesLimit, cnt))) {
                            break;
                        }
                        goldenBeansReceiveTaskAward(sceneCode, taskType, title, awardType);
                        sleep(500);
                        // 每完成一步就监测一次抽抽乐档位，够了立刻领档（静音，避免刷屏）
                        checkChouchouleQuiet();
                    }
                    continue;
                }
                sleep(500);
            } catch (Throwable t) {
                Log.printStackTrace(TAG, t);
            }
            // 全局监测：本轮任务领到的金豆也要立刻反映到抽抽乐档位
            checkChouchouleGlobal();
        }
    }

    /**
     * 抽抽乐（阿肥寻宝记）里的攒金豆任务：攒 5000 / 8000 / 20000 支金豆换抽奖机会。
     * <p>
     * 2026-11-18 真机取证：这三条任务挂在农场任务体系里
     * （RPC `com.alipay.antieptask.listTaskantorchard`，sceneCode `ANTORCHARD_DRAW_TIMES_TASK`，
     * taskType `CHOUCHOULE_GAME_ZJD5000/ZJD8000/ZJD20000`），
     * `taskBaseInfo.taskProgress` 是「**已领取**的金豆数」，`taskRequire` 是阈值；
     * 进度满了任务变 `FINISHED`，必须再调 `receiveTaskAwardantorchard` 才拿到抽奖机会。
     * <p>
     * 所以这里每轮只做两件事：把进度打进日志；`FINISHED` 就自动领奖。
     */
    private static void claimChouchouleAward(String taskType, String sceneCode, String title) {
        try {
            JSONObject award = new JSONObject(AntOrchardRpcCall.receiveTaskAwardantorchard(taskType, sceneCode));
            if (MessageUtil.checkResultCode(TAG, award)) {
                Log.farm("抽抽乐🎁[" + title + "]#领取成功");
            }
        } catch (Throwable t) {
            Log.i(TAG, "claimChouchouleAward err:");
            Log.printStackTrace(TAG, t);
        }
    }

    // ==================== 抽抽乐限时小游戏任务（2611CCL_GAME_ZH_*） ====================

    private static void handleChouchouleGameTask(JSONObject base, String taskType) {
        String title = taskType, targetUrl = "", desc = "";
        try {
            JSONObject b = new JSONObject(base.optString("bizInfo"));
            title = b.optString("title", taskType);
            targetUrl = b.optString("targetUrl", "");
            desc = b.optString("desc", "");
        } catch (Throwable ignored) {
        }
        String taskStatus = base.optString("taskStatus");
        String sceneCode = base.optString("sceneCode", "ANTORCHARD_DRAW_TIMES_TASK");
        Log.i("抽抽乐🎮[" + title + "]状态=" + taskStatus + " " + desc);
        if ("RECEIVED".equals(taskStatus) || "COMPLETED".equals(taskStatus)) {
            return;
        }
        if ("FINISHED".equals(taskStatus) || "TO_RECEIVE".equals(taskStatus)) {
        claimChouchouleAward(taskType, sceneCode, title);
            return;
        }
        if (Status.hasFlagToday("GoldenBean::chouchoule_game_" + taskType)) {
            Log.i("抽抽乐🎮[" + title + "]今日已上报过，跳过");
            return;
        }
        // 从 targetUrl 提取 appId 与 channel
        String appId = "", channel = "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[?&]appId=(\\d+)").matcher(targetUrl);
        if (m.find()) {
            appId = m.group(1);
        }
        m = java.util.regex.Pattern.compile("channel%3[Dd](\\w+)").matcher(targetUrl);
        if (m.find()) {
            channel = m.group(1);
        }
        if (appId.isEmpty() || !MiniGameVendorReporter.supports(appId)) {
            Log.i("抽抽乐🎮[" + title + "]#厂商未适配或已判死，需真人玩");
            Status.flagToday("GoldenBean::chouchoule_game_" + taskType);
            return;
        }
        String action = MiniGameVendorReporter.resolveAction(appId, desc);
        if (action == null || action.isEmpty()) {
            action = MiniGameVendorReporter.resolveAction(appId, title);
        }
        if (action == null || action.isEmpty()) {
            Log.farm("抽抽乐🎮[" + title + "]#任务描述匹配不到 actionCode");
            Status.flagToday("GoldenBean::chouchoule_game_" + taskType);
            return;
        }
        int every = Math.max(1, MiniGameTaskTable.everyOf(appId, action));
        int times = 1;
        java.util.regex.Matcher dm = java.util.regex.Pattern.compile("(\\d+)次").matcher(desc);
        if (dm.find()) {
            times = Math.max(1, Integer.parseInt(dm.group(1)) / every);
        }
        Log.i("抽抽乐🎮[" + title + "]厂商上报开始 action=" + action + " 次数=" + times
                + " 渠道=" + channel + " appId=" + appId);
        boolean ok = MiniGameVendorReporter.report(appId, title, channel, action, times, "GoldenBeansTaskList");
        if (!ok) {
            Log.i("抽抽乐🎮[" + title + "]#厂商上报失败（详情见 debug 日志）");
            return; // 不打标：下次运行重试
        }
        Status.flagToday("GoldenBean::chouchoule_game_" + taskType);
        // d3games 系 S2S 即时落账；轮询 4 轮，FINISHED 即领奖
        for (int round = 1; round <= 4; round++) {
            TimeUtil.sleep(3000);
            String status = queryChouchouleStatus(taskType);
            Log.farm("抽抽乐🔄[" + title + "]回查第" + round + "轮 状态=" + status);
            if ("FINISHED".equals(status) || "TO_RECEIVE".equals(status) || "COMPLETED".equals(status)) {
            claimChouchouleAward(taskType, sceneCode, title);
                return;
            }
        }
        // 按既定策略不自动打开/拉起游戏小程序，未落账就保留待办等下次刷新
    }

    /** 重查抽抽乐单个任务状态（TODO/FINISHED/RECEIVED/…） */
    private static String queryChouchouleStatus(String taskType) {
        try {
            JSONObject jo = new JSONObject(AntOrchardRpcCall.orchardDrawListTask());
            JSONArray list = jo.optJSONArray("taskInfoList");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.optJSONObject(i);
                    JSONObject base = item == null ? null : item.optJSONObject("taskBaseInfo");
                    if (base != null && taskType.equals(base.optString("taskType"))) {
                        return base.optString("taskStatus");
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "queryChouchouleStatus err:");
            Log.printStackTrace(TAG, t);
        }
        return "";
    }

    // ==================== 抽抽乐限时小游戏结束 ====================

    private static int reportChouchouleTasks() {
        int shown = 0;
        try {
            JSONObject jo = new JSONObject(AntOrchardRpcCall.orchardDrawListTask());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return 0;
            }
            JSONArray list = jo.optJSONArray("taskInfoList");
            if (list == null) {
                return 0;
            }
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                JSONObject base = item.optJSONObject("taskBaseInfo");
                if (base == null) {
                    continue;
                }
                String taskType = base.optString("taskType");
                if (!taskType.startsWith("CHOUCHOULE_GAME_ZJD")) {
                    // 限时小游戏任务（2611CCL_GAME_ZH_*）：厂商上报 + 落账领奖（2026-11-22）
                    if (taskType.startsWith("2611CCL_GAME_ZH_")) {
                        handleChouchouleGameTask(base, taskType);
                    }
                    continue;
                }
                String title = taskType;
                try {
                    title = new JSONObject(base.optString("bizInfo")).optString("title", taskType);
                } catch (Throwable ignored) {
                }
                String sceneCode = base.optString("sceneCode", "ANTORCHARD_DRAW_TIMES_TASK");
                String taskStatus = base.optString("taskStatus");
                // 已领完的档位不再逐档打印（用户要求：领过之后就不要再显示「状态=RECEIVED」）
                if ("RECEIVED".equals(taskStatus) || "COMPLETED".equals(taskStatus)) {
                    continue;
                }
                int progress = base.optInt("taskProgress", 0);
                int require = base.optInt("taskRequire", 0);
                JSONObject rights = item.optJSONObject("taskRights");
                int awardCount = rights == null ? 0 : rights.optInt("awardCount", 0);
                shown++;
                Log.i("抽抽乐🎯[" + title + "]进度=" + progress + "/" + require + " 状态=" + taskStatus
                        + " 机会=" + awardCount);
                if ("FINISHED".equals(taskStatus) || "COMPLETED".equals(taskStatus)
                        || "TO_RECEIVE".equals(taskStatus)) {
                    JSONObject award = new JSONObject(AntOrchardRpcCall.receiveTaskAwardantorchard(taskType, sceneCode));
                    if (MessageUtil.checkResultCode(TAG, award)) {
                        Log.farm("抽抽乐🎁领取[" + title + "]成功，抽奖机会+" + awardCount);
                    } else {
                        Log.farm("抽抽乐🎁领取[" + title + "]失败：" + award.optString("desc", brief(award.toString())));
                    }
                    TimeUtil.sleep(500);
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return shown;
        }
        return shown;
    }

    private boolean goldenBeansFinishTask(String sceneCode, String taskType, String title, String taskDesc,
            String progress) {
        try {
            JSONObject jo = new JSONObject(GoldenBeanRpcCall.finishTask(sceneCode, taskType));
            MessageUtil.checkResultCodeAndMarkTaskBlackList("GoldenBeansTaskList", title, jo);
            // 任务已完结(任务已完成/关闭)属正常态，静默跳过，不走错误日志
            if (isGoldenBeanTaskClosed(jo)) {
                return false;
            }
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.farm("农场金豆🧾完成任务[" + title + "]" + progress);
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "goldenBeansFinishTask err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    /**
     * 探针：直接对 GAME_ZH 任务发一次 finishTaskantOrchard（每天每条只发一次），
     * 只把结论写进 farm 日志（中文一句话，不打原始 JSON），用于确认服务端认不认客户端提交。
     */
    private static void probeGameZhFinish(String sceneCode, String taskType, String title) {
        try {
            String raw = GoldenBeanRpcCall.finishTask(sceneCode, taskType);
            JSONObject jo = new JSONObject(raw == null ? "{}" : raw);
            if (MessageUtil.checkResultCode(TAG, jo) && (jo.optBoolean("success") || jo.optBoolean("isSuccess"))) {
                Log.farm("农场金豆🎮[" + title + "]#探针：服务端接受，本条可全自动");
                return;
            }
            String desc = jo.optString("desc", "");
            String code = jo.optString("code", "");
            if ("400000040".equals(code) || desc.contains("不支持")) {
                Log.farm("农场金豆🎮[" + title + "]#探针：服务端不支持客户端提交，只能真人玩");
            } else {
                Log.farm("农场金豆🎮[" + title + "]#探针：未通过（" + (desc.isEmpty() ? "无描述" : desc) + "）");
            }
        } catch (Throwable t) {
            Log.i(TAG, "probeGameZhFinish err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private boolean goldenBeansReceiveTaskAward(String sceneCode, String taskType, String title, String awardType) {
        try {
            JSONObject jo = new JSONObject(GoldenBeanRpcCall.receiveTaskAward(sceneCode, taskType));
            MessageUtil.checkResultCodeAndMarkTaskBlackList("GoldenBeansTaskList", title, jo);
            // 任务已完结(奖励已领过/任务关闭)属正常态，静默跳过，不走错误日志
            if (isGoldenBeanTaskClosed(jo)) {
                return false;
            }
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

    /* 金豆任务"已完结/已完成/已领"类良性失败：success=false 且文案命中即视为正常完成态 */
    private static boolean isGoldenBeanTaskClosed(JSONObject jo) {
        if (jo.optBoolean("success") || jo.optBoolean("isSuccess")) {
            return false;
        }
        String desc = jo.optString("desc") + jo.optString("errorMsg") + jo.optString("errorMessage")
                + jo.optString("resultDesc") + jo.optString("memo");
        return desc.contains("已完结") || desc.contains("已完成") || desc.contains("已领");
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
            if (rights > 0) {
                smashGameCenter(rights);
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 本次运行补砸：playGame() 刚刷出的机会，回查一次额度后立刻砸掉，避免等到下一轮。
     */
    private static void smashGameCenterAfterPlay() {
        try {
            JSONObject gl = new JSONObject(GoldenBeanRpcCall.queryGameCenterList());
            JSONObject drawRights = gl.optJSONObject("gameCenterDrawRights");
            if (drawRights == null) {
                return;
            }
            int left = drawRights.optInt("quotaCanUse",
                    drawRights.optInt("quotaLimit", 0) - drawRights.optInt("usedQuota", 0));
            if (left > 0) {
                smashGameCenter(left);
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 按给定机会数循环砸蛋，日志口径与 gameCenter(index) 保持一致。
     */
    private static void smashGameCenter(int rights) throws Exception {
        // 轮次上限保护：服务端quotaCanUse不递减时避免死循环
        int guard = rights + 5;
        while (rights > 0 && guard-- > 0) {
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
                int quotaCanUse = drawRights == null ? 0 : drawRights.optInt("quotaCanUse", 0);
                rights = quotaCanUse;
                Log.farm("砸出金豆🎖️" + drawGot + "豆[剩蛋" + quotaCanUse + "个]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                sleep(500);
            } else {
                rights--;
            }
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
            Log.printStackTrace(TAG, t);
        }
    }


    /** 静音版：只打真正领取的行，用于多步任务里逐笔监测。 */
    static void checkChouchouleQuiet() {
        int[] tiers = new int[]{ZJD_TIER_1, ZJD_TIER_2, ZJD_TIER_3};
        for (int i = 0; i < tiers.length; i++) {
            claimChouchouleByRequire(tiers[i], true);
        }
    }

    /**
     * 全局监测：抽抽乐「攒 5000 / 8000 / 20000 金豆」三档。
     * <p>
     * 只要当前进度够（或服务端已置 FINISHED），就立刻领取；已领过则跳过。
     * 该方法可以在流程的任意产豆环节之后调用，不依赖砸蛋是否执行过。
     */
    static void checkChouchouleGlobal() {
        int shown = reportChouchouleTasks();
        if (shown == 0) {
            // 三档都领完了：只提示一次，后面不再逐档刷「状态=RECEIVED」
            if (!Status.hasFlagToday("GoldenBean::chouchouleAllDone")) {
                Status.flagToday("GoldenBean::chouchouleAllDone");
                Log.farm("抽抽乐🎯今日三档都已领取，后续不再逐档打印");
            }
            return;
        }
        int[] tiers = new int[]{ZJD_TIER_1, ZJD_TIER_2, ZJD_TIER_3};
        for (int i = 0; i < tiers.length; i++) {
            claimChouchouleByRequire(tiers[i], true);
        }
    }

    private static void claimChouchouleByRequire(int require) {
        claimChouchouleByRequire(require, false);
    }

    /**
     * 领取抽抽乐里「攒 N 金豆」那一档（N = 5000 / 8000 / 20000）。
     * <p>
     * 够就领，不够就不领：
     * <ul>
     *   <li>状态已是 FINISHED/COMPLETED/TO_RECEIVE → 直接领；</li>
     *   <li>进度 taskProgress 已到 N 但状态还没结算 → 等结算（最多 5 轮 × 1.5 秒）再领；</li>
     *   <li>进度不足 → 打一行「不够，不领」直接返回。</li>
     * </ul>
     */
    private static void claimChouchouleByRequire(int require, boolean quiet) {
        try {
            for (int attempt = 0; attempt < 6; attempt++) {
                JSONObject jo = new JSONObject(AntOrchardRpcCall.orchardDrawListTask());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                JSONArray list = jo.optJSONArray("taskInfoList");
                if (list == null) {
                    return;
                }
                String foundType = null;
                String foundScene = null;
                String foundTitle = null;
                String foundStatus = null;
                int foundProgress = 0;
                int foundAward = 0;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    JSONObject base = item.optJSONObject("taskBaseInfo");
                    if (base == null) {
                        continue;
                    }
                    if (!base.optString("taskType").startsWith("CHOUCHOULE_GAME_ZJD")) {
                        continue;
                    }
                    if (base.optInt("taskRequire", 0) != require) {
                        continue;
                    }
                    foundType = base.optString("taskType");
                    foundScene = base.optString("sceneCode", "ANTORCHARD_DRAW_TIMES_TASK");
                    foundStatus = base.optString("taskStatus");
                    foundProgress = base.optInt("taskProgress", 0);
                    JSONObject rights = item.optJSONObject("taskRights");
                    foundAward = rights == null ? 0 : rights.optInt("awardCount", 0);
                    try {
                        foundTitle = new JSONObject(base.optString("bizInfo")).optString("title", foundType);
                    } catch (Throwable ignored) {
                        foundTitle = foundType;
                    }
                    break;
                }
                if (foundType == null) {
                    if (!quiet) {
                        Log.farm("抽抽乐🎯找不到「攒 " + require + " 金豆」这一档（可能今日未下发或已领完），跳过");
                    }
                    return;
                }
                if ("RECEIVED".equals(foundStatus) || "COMPLETED".equals(foundStatus)) {
                    // 已领完直接静默返回，不再打印「已领过」
                    return;
                }
                boolean claimable = "FINISHED".equals(foundStatus) || "COMPLETED".equals(foundStatus)
                        || "TO_RECEIVE".equals(foundStatus);
                if (!claimable && foundProgress < require) {
                    if (!quiet) {
                        Log.farm("抽抽乐🎯[" + foundTitle + "]进度=" + foundProgress + "/" + require
                                + " 不够，不领");
                    }
                    return;
                }
                JSONObject award = new JSONObject(AntOrchardRpcCall.receiveTaskAwardantorchard(foundType, foundScene));
                if (MessageUtil.checkResultCode(TAG, award) && MessageUtil.checkSuccess(TAG, award)) {
                    Log.farm("抽抽乐🎁领取[" + foundTitle + "]成功，抽奖机会+" + foundAward
                            + "（进度=" + foundProgress + "/" + require + "）");
                    return;
                }
                Log.farm("抽抽乐🎁领取[" + foundTitle + "]未成功（状态=" + foundStatus + "，进度="
                        + foundProgress + "/" + require + "）：" + brief(award.toString()) + "，1.5 秒后重试");
                sleep(1500);
            }
            Log.farm("抽抽乐🎁领取重试 6 次仍未成功，放弃这一档");
        } catch (Throwable t) {
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