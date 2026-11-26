package io.github.Dragon813.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

import io.github.Dragon813.sesame.data.RuntimeInfo;
import io.github.Dragon813.sesame.model.task.antGame.MiniGameTaskTable;
import io.github.Dragon813.sesame.model.task.antGame.MiniGameVendorReporter;
import io.github.Dragon813.sesame.util.Log;
import io.github.Dragon813.sesame.util.Status;
import io.github.Dragon813.sesame.util.TimeUtil;
import io.github.Dragon813.sesame.util.idMap.UserIdMap;

/**
 * 支付宝「游戏中心」一键处理：签到 + 平台任务 + 乐豆 + 赚现金任务（含小游戏进度上报）+ 赚现金签到/抽金币/提现。
 * <p>
 * 移植自 Sesame-AG 的 AntMember.enableGameCenter（Kotlin），改为同步 Java 实现，
 * 沿用同一套 RPC、同一套日志文案与同一套「全部完成才置当日标记」的语义。
 */
public class GameCenter {

    private static final String TAG = "AntMember";
    /** 当日标记：全部步骤都完成才置位，未置位则下次进会员任务时整轮重跑。 */
    private static final String FLAG_GAME_CENTER_DONE = "AntMember::gameCenterDone";
    // 2026-11-19：对齐 AG（最多 64 轮）。浏览类任务的「受理 → 回查确认 → 领取」要跨 2–3 轮才能走完，
    // 6 轮不够；这里取 64 并按「本轮是否还有进展」提前退出，避免空转。
    private static final int MAX_ROUND = 24;

    private static final Set<String> TERMINAL_STATUS = new HashSet<>();
    private static final Set<String> REWARD_READY_STATUS = new HashSet<>();

    static {
        TERMINAL_STATUS.add("RECEIVED");
        TERMINAL_STATUS.add("DONE");
        TERMINAL_STATUS.add("SUCCESS");
        REWARD_READY_STATUS.add("COMPLETED");
        REWARD_READY_STATUS.add("COMPLETE");
        REWARD_READY_STATUS.add("FINISHED");
    }

    private static int waitMs = 600;

    /** 信息记录（info 日志）快照：每天/每轮只输出一行，综合金币、现金、签到、抽奖、任务等现状。 */
    private static final String INFO_KEY = "GameCenter::info";
    /** 最近一次 p2e.queryHomePage 成功的 data，info 日志从这一条概况响应里提炼。 */
    private static JSONObject lastHome;
    private static String infoSignIn = "";
    private static int infoPointBallReceive;
    private static int infoPointBallTotal;
    private static int infoP2eCompleted;
    private static int infoP2eTotal;
    /** 本轮未完成的步骤，拼进 info 日志。 */
    private static final List<String> infoStepFailed = new ArrayList<>();

    public static void run() {
        // 信息记录跟随「游戏中心」开关：本方法只在 AntMember.enableGameCenter 开启时调用
        if (Boolean.TRUE.equals(Status.hasFlagToday(FLAG_GAME_CENTER_DONE))) {
            Log.i("游戏中心🎮[今日已处理，跳过]");
            Log.info(loadInfoLine());
            return;
        }
        infoSignIn = "";
        infoPointBallReceive = 0;
        infoPointBallTotal = 0;
        infoP2eCompleted = 0;
        infoP2eTotal = 0;
        lastHome = null;
        infoStepFailed.clear();
        boolean allHandled = true;
        allHandled &= step("签到", new Step() {
            public boolean run() {
                return doSignIn();
            }
        });
        allHandled &= step("平台任务", new Step() {
            public boolean run() {
                return doPlatformTasks();
            }
        });
        allHandled &= step("乐豆", new Step() {
            public boolean run() {
                return doPointBall();
            }
        });
        allHandled &= step("赚现金任务", new Step() {
            public boolean run() {
                return doP2eTasks(false);
            }
        });
        allHandled &= step("赚现金签到", new Step() {
            public boolean run() {
                return doP2eSignIn();
            }
        });
        allHandled &= step("抽金币", new Step() {
            public boolean run() {
                return doP2eDrawGold();
            }
        });
        // 赚现金提现：按需求暂停，不自动提交
        // allHandled &= step("p2eCashExchange", new Step() {
        //     public boolean run() {
        //         return doP2eCashExchange();
        //     }
        // });
        if (allHandled) {
            Status.flagToday(FLAG_GAME_CENTER_DONE);
            Log.i("游戏中心🎮[全部处理完成]");
        } else {
            Log.i("游戏中心🎮[本轮未全部完成，稍后重试]");
        }
        String info = buildInfoLine(allHandled);
        RuntimeInfo.getInstance().put(INFO_KEY, info);
        Log.info(info);
    }

    /** 当天已处理时，输出当天最后一次的概况快照（不额外发请求）。 */
    private static String loadInfoLine() {
        String info = RuntimeInfo.getInstance().getString(INFO_KEY);
        return info.isEmpty()
                ? "游戏中心🎮[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]今日已处理;"
                : info;
    }

    /** 把 lastHome 等本轮状态汇总成一行 info 日志。 */
    private static String buildInfoLine(boolean allHandled) {
        StringBuilder sb = new StringBuilder(240);
        sb.append("游戏中心🎮[").append(UserIdMap.getShowName(UserIdMap.getCurrentUid())).append(']');
        JSONObject home = lastHome;
        JSONObject asset = home == null ? null : home.optJSONObject("assetModuleVO");
        if (asset != null) {
            sb.append("金币:").append(Log.n(asset.optInt("goldCoinAmount", 0)));
            String cash = asset.optString("afterCashAmount", asset.optString("beforeCashAmount", ""));
            String threshold = asset.optString("goldExgCashThreshold", "");
            if (!cash.isEmpty()) {
                sb.append(";现金:").append(cash).append("元");
                if (!threshold.isEmpty()) {
                    sb.append("/满").append(threshold).append("元");
                }
            }
        }
        JSONObject signUp = home == null ? null : home.optJSONObject("signUpModuleVO");
        if (signUp != null) {
            JSONObject today = todaySignRecord(signUp);
            boolean signed = today != null && "SIGNED".equalsIgnoreCase(today.optString("signUpStatus", ""));
            sb.append(";今日签到:").append(signed ? "已签" : "未签");
            int todayGold = today == null ? 0 : today.optInt("todayGoldCoinAmount", 0);
            if (todayGold > 0) {
                sb.append("(得").append(Log.n(todayGold)).append("金币)");
            }
            sb.append(";连签:").append(Log.n(signUp.optInt("completedSignUpDays", 0))).append("天");
            JSONObject card = signUp.optJSONObject("signInPrizeSummaryCardVO");
            String tier = card == null ? "" : card.optString("goldExgCashAmount", "");
            if (!tier.isEmpty()) {
                sb.append(";提现档:").append(tier).append("元");
            }
        }
        JSONObject draw = home == null ? null : home.optJSONObject("drawGoldCoinModuleVO");
        if (draw != null) {
            String status = upper(draw.optString("status", ""));
            sb.append(";抽金币:").append("DRAWN".equals(status) ? "已抽"
                    : ("NOT_DRAWN".equals(status) || "FULFILL_FAILED".equals(status) ? "未抽" : status));
            String tips = draw.optString("tipsText", "");
            if (!tips.isEmpty()) {
                sb.append('(').append(tips).append(')');
            }
        }
        if (home != null) {
            boolean risk = home.optBoolean("hitRiskControl", false) || home.optBoolean("hitFourControlLimit", false);
            sb.append(";风控:").append(risk ? "受限" : "正常");
            sb.append(";实名:").append(home.optBoolean("isRealName", false) ? "是" : "否");
        }
        sb.append(";赚现金任务:已完成").append(Log.n(infoP2eCompleted)).append('/').append(Log.n(infoP2eTotal));
        if (infoPointBallReceive > 0 || infoPointBallTotal > 0) {
            sb.append(";乐豆:本次").append(Log.n(infoPointBallReceive)).append("/累计").append(Log.n(infoPointBallTotal));
        }
        if (!infoSignIn.isEmpty()) {
            sb.append(";签到:").append(infoSignIn);
        }
        if (allHandled) {
            sb.append(";全部完成;");
        } else {
            sb.append(";未完成:");
            for (int i = 0; i < infoStepFailed.size(); i++) {
                if (i > 0) {
                    sb.append('、');
                }
                sb.append(infoStepFailed.get(i));
            }
            sb.append(';');
        }
        return sb.toString();
    }

    private interface Step {
        boolean run() throws Throwable;
    }

    private static boolean step(String name, Step body) {
        try {
            boolean ok = body.run();
            if (!ok) {
                infoStepFailed.add(name);
            }
            return ok;
        } catch (Throwable t) {
            infoStepFailed.add(name);
            Log.i(TAG, "enableGameCenter." + name + " err:");
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    // ------------------------------------------------------------- 1. 游戏中心签到

    private static boolean doSignIn() {
        JSONObject root = json(GameCenterRpcCall.querySignInBall());
        if (!ok(root)) {
            infoSignIn = "查询失败";
            Log.i("游戏中心🎮[签到查询失败]#" + messageOf(root));
            return false;
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null || data.length() == 0) {
            infoSignIn = "无签到状态";
            Log.i("游戏中心🎮[签到状态为空，跳过签到]");
            return true;
        }
        JSONObject signModule = data.optJSONObject("signInBallModule");
        if (signModule == null) {
            infoSignIn = "无签到模块";
            Log.i("游戏中心🎮[暂无签到模块]");
            return true;
        }
        if (signModule.optBoolean("signInStatus", false)) {
            infoSignIn = "今日已签到";
            Log.i("游戏中心🎮[今日已签到]");
            return true;
        }
        JSONObject sign = json(GameCenterRpcCall.continueSignIn());
        if (!ok(sign)) {
            infoSignIn = "签到失败";
            Log.i("游戏中心🎮[签到失败]#" + messageOf(sign));
            return false;
        }
        JSONObject signData = sign.optJSONObject("data");
        String title = "";
        String desc = "";
        String type = "";
        if (signData != null) {
            JSONObject toast = signData.optJSONObject("autoSignInToastModule");
            if (toast != null) {
                title = toast.optString("title", "");
                desc = toast.optString("desc", "");
                type = toast.optString("type", "");
            }
        }
        boolean toastSuccess = "SUCCESS".equalsIgnoreCase(type)
                && !title.contains("失败") && !desc.contains("失败");
        if (toastSuccess) {
            StringBuilder sb = new StringBuilder("游戏中心🎮[每日签到成功]");
            if (!title.isEmpty()) {
                sb.append('#').append(title);
            }
            if (!desc.isEmpty()) {
                sb.append('#').append(desc);
            }
            Log.i(sb.toString());
            infoSignIn = "签到成功";
            return true;
        }
        infoSignIn = "签到失败";
        Log.i("游戏中心🎮[签到失败]#" + (title.isEmpty() && desc.isEmpty() ? String.valueOf(sign) : title + " " + desc));
        return false;
    }

    // ------------------------------------------------------------- 2. 平台任务

    private static boolean doPlatformTasks() {
        // 平台任务（乐豆任务）由 com.alipay.gamecenteruprod.biz.rpc.v3.queryModularTaskList 下发，
        // 直接复用 GR 原有实现；早期版本误用 v4.queryTaskList 取不到 taskModuleList，
        // 日志只剩「暂无平台任务模块」，导致 AG 能做的那批平台任务全部空转。
        try {
            AntMember.queryAndProcessTaskList();
            return true;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    // ------------------------------------------------------------- 3. 乐豆

    private static boolean doPointBall() {
        JSONObject root = json(GameCenterRpcCall.queryPointBallList());
        if (!ok(root)) {
            Log.i("游戏中心🎮[查询待收乐豆失败]#" + messageOf(root));
            return false;
        }
        JSONObject data = root.optJSONObject("data");
        JSONArray list = data == null ? null : data.optJSONArray("pointBallList");
        if (list == null || list.length() == 0) {
            Log.i("游戏中心🎮[暂无可领取乐豆]");
            return true;
        }
        JSONObject batch = json(GameCenterRpcCall.batchReceivePointBall());
        if (!ok(batch)) {
            Log.i("游戏中心🎮[一键领取乐豆失败]#" + messageOf(batch));
            return false;
        }
        JSONObject batchData = batch.optJSONObject("data");
        int receiveAmount = batchData == null ? 0 : batchData.optInt("receiveAmount", 0);
        int totalAmount = batchData == null ? receiveAmount : batchData.optInt("totalAmount", receiveAmount);
        infoPointBallReceive = receiveAmount;
        infoPointBallTotal = totalAmount;
        if (receiveAmount > 0) {
            Log.other("游戏中心🎮[一键领取乐豆成功]#本次领取" + receiveAmount + " | 当前累计" + totalAmount + "玩乐豆");
        } else {
            Log.i("游戏中心🎮[暂无可领取乐豆]");
        }
        return true;
    }

    /**
     * 「手动运行 → 游戏中心浏览任务」：只跑赚现金平台任务，并且把每一步的原始响应同步显示到屏幕上，
     * 方便与浏览器 F12 的 gameP2eFloatingBallConsult 请求逐字段对照。
     */
    public static void runP2eTasksWithToast() {
        try {
            doP2eTasks(true);
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    // ------------------------------------------------------------- 4. 赚现金任务（核心）

    private static String lastGameModuleId = "";

    private static boolean doP2eTasks(boolean onlineToast) {
        String sessionId = String.valueOf(System.currentTimeMillis());
        Set<String> complained = new HashSet<>();
        // 2026-11-19（gc8）：signup / send 都是「异步受理」，服务端列表要过十几秒才刷新。
        // 用「任务 -> 发出动作的轮次」节流：signup 之后等 SIGNUP_GAP 轮再补 send，
        // send 之后等 SEND_GAP 轮再看是否变成可领取，避免一轮报名一轮发送的死循环。
        Map<String, Integer> signupRound = new HashMap<>();
        Map<String, Integer> sentRound = new HashMap<>();
        // 2026-11-19（gc19）：每个任务累计发过几次 send，用于「最多 2 次」的上限。
        Map<String, Integer> sendCount = new HashMap<>();
        // 厂商受理不等于任务推进：每次执行每个 taskId 只上报一次，后续以任务列表回查为准。
        Set<String> vendorReported = new HashSet<>();
        // 2026-11-19（gc19）：SIGNUP_GAP 由 2 降到 1 —— 报名确认实测只需一轮（gc18 里第 2 轮就该发 send），
        // 原来的 2 会让第 2 轮空转并被误判为「无可执行动作」直接收工。
        final int SIGNUP_GAP = 1;
        final int SEND_GAP = 2;
        int lastCompleted = -1;
        int lastTotal = -1;
        for (int round = 1; round <= MAX_ROUND; round++) {
            if (round > 1) {
                TimeUtil.sleep(500 + (long) (Math.random() * 1500));
            }
            JSONObject home = json(GameCenterRpcCall.queryP2eHomePage());
            JSONObject taskPage = json(GameCenterRpcCall.queryP2eTaskList(sessionId));
            boolean homeOk = ok(home);
            boolean taskOk = ok(taskPage);
            if (homeOk) {
                JSONObject homeData = home.optJSONObject("data");
                if (homeData != null) {
                    lastHome = homeData;
                }
            }
            if (!homeOk && !taskOk) {
                Log.i("游戏中心🎮[赚现金任务查询失败]");
                return round > 1;
            }
            // 2026-11-19（gc22）：浮球完成接口必须要模块级 gameModuleId，任务对象里没有这个字段。
        // 实测（probe_light3）：gameId=jhwg + moduleId 空 = CONFIGURATION_ERROR；
        //                       带上 26NTMYXJBBX_CUSTOM_… = success:true。
        lastGameModuleId = "";
        try {
            JSONObject taskData = taskPage == null ? null : taskPage.optJSONObject("data");
            JSONObject platform = taskData == null ? null : taskData.optJSONObject("platformGameTaskModule");
            if (platform != null) {
                lastGameModuleId = platform.optString("gameModuleId", "");
            }
            if (lastGameModuleId.isEmpty() && home != null) {
                JSONObject homeData = home.optJSONObject("data");
                JSONObject homePlatform = homeData == null ? null : homeData.optJSONObject("platformGameTaskModule");
                if (homePlatform != null) {
                    lastGameModuleId = homePlatform.optString("gameModuleId", "");
                }
            }
        } catch (Throwable ignored) {
        }
        List<JSONObject> tasks = new ArrayList<>();
            if (homeOk) {
                tasks.addAll(collectP2eTasks(home));
            }
            if (taskOk) {
                tasks.addAll(collectP2eTasks(taskPage));
            }
            if (round == 1 && tasks.isEmpty()) {
                Log.i("游戏中心🎮[赚现金暂无任务模块]");
                return true;
            }

            int completed = 0;
            int total = 0;
            boolean progressed = false;
            // 本节对齐 AG：浏览类平台任务不是一次请求就过账的，必须「报名 → 提交完成 → 回查确认 → 领取」，
            // 实测服务端要 2-3 轮才把状态推进到 COMPLETED。为了让回查有间隔，这里把动作按轮次交替：
            // 奇数轮只做 signup / 厂商上报，偶数轮只做 send（platformTaskComplete）与 receive。
            boolean evenRound = (round % 2 == 0);
            int doneBefore = 0;
            // 2026-11-19（gc19）：本轮是否真的发出过动作、有多少任务被节流「押后」。
            // 收工判断必须同时满足「没进展 + 没动作 + 没有押后任务 + 数量没变」，
            // 否则会出现 gc18 那种「第 2 轮被判无可执行动作就收工」的假结束。
            boolean actedThisRound = false;
            int throttled = 0;
            StringBuilder roundActions = new StringBuilder();
            for (JSONObject task : tasks) {
                String taskId = task.optString("taskId", "");
                if (taskId.isEmpty()) {
                    continue;
                }
                String status = upper(task.optString("taskStatus", ""));
                String title = titleOf(task);
                if (TERMINAL_STATUS.contains(status)) {
                    completed++;
                    doneBefore++;
                    continue;
                }
                total++;
                if (REWARD_READY_STATUS.contains(status)) {
                    Integer sent = sentRound.get(taskId);
                    if (sent != null && round - sent < SEND_GAP) {
                        // 刚发过 send，服务端还没落账，先等两轮再领。
                        throttled++;
                        TimeUtil.sleep(waitMs);
                        continue;
                    }
                    // 2026-11-19（gc19）：send 最多发 2 次。服务端若两轮都没推进，就不再重复打，
                    // 避免「send → 没落账 → 再 send」在 24 轮里空转。
                    Integer sentTimes = sendCount.get(taskId);
                    if (sentTimes != null && sentTimes >= 2) {
                        if (complained.add("send2:" + taskId)) {
                            Log.i("游戏中心🎮赚现金任务[" + title + "]#已发送 2 次仍未确认，本轮跳过");
                        }
                        TimeUtil.sleep(waitMs);
                        continue;
                    }
                    if (receive(task)) {
                        progressed = true;
                        completed++;
                        roundActions.append("receive：").append(title).append("；");
                    }
                    TimeUtil.sleep(waitMs);
                    continue;
                }
                String milestoneId = task.optString("taskMileStoneId", "");
                if (!milestoneId.isEmpty() && !"null".equals(milestoneId)) {
                    // 累计奖励里程碑：只有到达 COMPLETED 才领，未到达时服务端会拒绝，直接跳过。
                    continue;
                }
                String taskType = upper(task.optString("taskType", ""));
                String taskToken = task.optString("taskToken", "");
                GameCenterMiniGameReporter.Spec spec = GameCenterMiniGameReporter.specOf(task.optString("appId", ""));
                if (spec != null && !taskToken.isEmpty() && !"LIGHT_TRAN_TASK".equals(taskType)) {
                    if (!evenRound && vendorReported.add(taskId) && reportMiniGame(task, spec)) {
                        actedThisRound = true;
                        roundActions.append("厂商上报：").append(title).append("；");
                    }
                    continue;
                }
                // 2026-11-19（mg1）：GameCenterMiniGameReporter 只有 19 款表，
                // 其余游戏回落到 MiniGameVendorReporter 的大适配表（MiniGameTaskTable，200+ 款，
                // 含 d3games / tomato / devourad / cpchxapi 等完整签名实现）。
                String vendorAppId = task.optString("appId", "");
                if (spec == null && !taskToken.isEmpty() && !vendorAppId.isEmpty()
                        && !"LIGHT_TRAN_TASK".equals(taskType)
                        && MiniGameVendorReporter.supports(vendorAppId)) {
                    if (!evenRound && vendorReported.add(taskId)
                            && reportVendorMiniGame(task, vendorAppId, title)) {
                        actedThisRound = true;
                        roundActions.append("厂商上报(通用表)：").append(title).append("；");
                    }
                    continue;
                }
                if ("LIGHT_TRAN_TASK".equals(taskType)) {
                    // 2026-11-19（gc20）：AG 日志里「玩寻道大千」等 12 条是
                    // status=SIGNUP_COMPLETED action=send rpc=send msg=浮球完成已提交，
                    // 也就是「时长上报 + 浮球完成」而不是 platformTaskComplete。
                    // 这类任务的 task JSON 自带 gameId/appId/jumpLink，浮球接口认得。
                    Integer sentLt = sentRound.get(taskId);
                    if (sentLt != null && round - sentLt < SEND_GAP) {
                        throttled++;
                        TimeUtil.sleep(waitMs);
                        continue;
                    }
                    Integer ltTimes = sendCount.get(taskId);
                    if (ltTimes != null && ltTimes >= 2) {
                        if (complained.add("lt2:" + taskId)) {
                            Log.i("游戏中心🎮赚现金任务[" + title + "]#浮球已提交 2 次仍未确认，本轮跳过");
                        }
                        TimeUtil.sleep(waitMs);
                        continue;
                    }
                    sentRound.put(taskId, round);
                    sendCount.put(taskId, ltTimes == null ? 1 : ltTimes + 1);
                    if (completeFloatingBallTask(task, title, floatingBallSeconds(task))) {
                        roundActions.append("浮球：").append(title).append("；");
                        actedThisRound = true;
                        Log.other("游戏中心🎮赚现金任务[" + title
                                + "]#提交浮球完成");
                    } else {
                        Log.i("游戏中心🎮赚现金任务[" + title + "]#浮球链路失败，保留待办");
                    }
                    TimeUtil.sleep(waitMs);
                    continue;
                }
                if (isAutoP2eTask(task) && !taskToken.isEmpty()) {
                    // 2026-11-19 三次修复（gc6）：AG 日志证明浏览类任务只用「signup + platformTaskComplete」，
                    // 浮球链路是给 LIGHT_TRAN_TASK 用的（浏览类任务的 task JSON 里根本没有 gameId，
                    // 浮球接口必然回「gameId为空」）。这里恢复 AG 的两步循环。
                    boolean needSignUp = task.optBoolean("needSignUp", false);
                    boolean notSigned = "UN_SIGNUP".equals(status) || "SIGNUP_REQUIRED".equals(status);
                    Integer signedAt = signupRound.get(taskId);
                    if (notSigned && signedAt != null) {
                        // 已经发过报名，服务端列表还没刷新；按「已报名」处理，够了间隔再补 send。
                        notSigned = false;
                    }
                    if (needSignUp && notSigned) {
                        if (!evenRound) {
                            // 2026-11-19（gc18）：报名必须走 platformTaskSignUp（手点抓到的就是它），
                            // 原来错用 v3.doTaskSignup —— 那是另一套任务体系的方法，
                            // 对本页的 P2E 平台任务只会回 success 而不改状态。
                            JSONObject signResp = json(GameCenterRpcCall.p2ePlatformTaskSignUp(taskId, taskToken,
                                    actionChannelOf(task), sourceOf(task)));
                            if (!ok(signResp)) {
                                Log.i("游戏中心🎮赚现金任务[" + title + "]#报名失败#" + messageOf(signResp)
                                        + "#原始=" + clip(String.valueOf(signResp)));
                                TimeUtil.sleep(waitMs);
                                continue;
                            }
                            Log.i("游戏中心🎮赚现金任务[" + title + "]#报名完成");
                            roundActions.append("signup：").append(title).append("；");
                            actedThisRound = true;
                            // 报名已受理：记下轮次，等 SIGNUP_GAP 轮后补发 send。
                            signupRound.put(taskId, round);
                            // 2026-11-19（gc9）：报名/发送只代表「受理」，不算进展 ——
                            // 否则服务端一直不刷新状态时会一轮报名一轮发送地死循环。
                            // 只有「服务端状态真的推进」（completed/total 变化）或领取成功才计为进展；
                            // 厂商上报仅表示受理，记入 actedThisRound，不再直接计为进展。
                        }
                        TimeUtil.sleep(waitMs);
                        continue;
                    }
                    if (signedAt != null && round - signedAt < SIGNUP_GAP) {
                        // 刚报名，给服务端留出确认时间。
                        throttled++;
                        TimeUtil.sleep(waitMs);
                        continue;
                    }
                    Integer alreadySent = sentRound.get(taskId);
                    if (alreadySent != null && round - alreadySent < SEND_GAP) {
                        // send 已受理，等它落账（AG 里 send 后一轮就能领取）。
                        TimeUtil.sleep(waitMs);
                        continue;
                    }
                    JSONObject jo = json(GameCenterRpcCall.p2ePlatformTaskComplete(taskId, taskToken,
                            actionChannelOf(task), sourceOf(task)));
                    sentRound.put(taskId, round);
                    Integer prevCount = sendCount.get(taskId);
                    sendCount.put(taskId, prevCount == null ? 1 : prevCount + 1);
                    if (ok(jo)) {
                        Log.i("游戏中心🎮赚现金任务[" + title + "]#完成请求已受理，等待列表确认");
                        roundActions.append("send：").append(title).append("；");
                        actedThisRound = true;
                        // 同上：受理不算进展。
                    } else {
                        Log.i("游戏中心🎮赚现金任务[" + title + "]#完成失败#" + messageOf(jo));
                    }
                    TimeUtil.sleep(waitMs);
                    continue;
                }
                if (complained.add(taskId)) {
                    Log.i("游戏中心🎮赚现金任务[" + title + "]#" + unsupportedReason(task));
                }
            }
            infoP2eCompleted = doneBefore;
            infoP2eTotal = doneBefore + total;
            if (total > 0 || completed > 0) {
                Log.i("游戏中心🎮赚现金任务刷新进度[轮次:" + round + "/" + MAX_ROUND
                        + "][处理前已完成:" + doneBefore + "/" + (doneBefore + total)
                        + "][本轮动作:" + (roundActions.length() == 0 ? "无可执行动作" : roundActions.toString())
                        + "][押后:" + throttled + "]"
                        + "][本轮有进展:" + progressed + "]");
            }
            if (total == 0) {
                break;
            }
            // 2026-11-19（gc19）：收工条件必须排除「本轮还有任务在节流等待」的情况，
            // 否则刚报名的那一轮（无动作、无进展、数量没变）会被误判成「服务端不再推进」而提前结束。
            boolean waiting = throttled > 0;
            if (!progressed && !actedThisRound && !waiting
                    && completed == lastCompleted && total == lastTotal) {
                Log.i("游戏中心🎮赚现金任务[已无可推进动作，结束]");
                break;
            }
            lastCompleted = completed;
            lastTotal = total;
        }
        return true;
    }

    /**
     * 通用厂商适配表（MiniGameTaskTable）兜底上报：适用于 GameCenterMiniGameReporter 未收录、
     * 但 MiniGameVendorReporter 支持的游戏（如 d3games 泡泡龙王国）。
     */
    private static boolean reportVendorMiniGame(JSONObject task, String appId, String title) {
        String actionDesc = task.optString("gameActionDesc", "");
        int need = GameCenterMiniGameReporter.requiredTimes(actionDesc);
        String action = MiniGameVendorReporter.resolveAction(appId, title + " " + actionDesc);
        Log.i("游戏中心🎮赚现金任务[" + title + "]厂商上报开始(通用表)：" + MiniGameVendorReporter.vendorName(appId)
                + " action=" + action + " 次数=" + need + " 描述=" + actionDesc);
        boolean ok = false;
        try {
            ok = MiniGameVendorReporter.report(appId, title, GameCenterMiniGameReporter.CHANNEL, action, need, "GameCenterP2eTaskList");
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
        if (ok) {
            Log.other("游戏中心🎮赚现金任务[" + title + "]#厂商进度已上报");
        } else {
            Log.i("游戏中心🎮赚现金任务[" + title + "]#厂商上报失败(通用表)，保留待办");
        }
        return ok;
    }

    private static String miniGameAction(GameCenterMiniGameReporter.Spec spec, String actionDesc) {
        if (!actionDesc.contains("消除") && !actionDesc.contains("消消")) {
            return spec.action;
        }
        String resolved = MiniGameVendorReporter.resolveAction(spec.appId, actionDesc);
        if (resolved.contains("_xiaochu")) {
            return resolved;
        }
        if (spec.altAction != null && spec.altAction.contains("_xiaochu")) {
            return spec.altAction;
        }
        return "";
    }

    private static boolean reportMiniGame(JSONObject task, GameCenterMiniGameReporter.Spec spec) {
        String title = titleOf(task);
        String actionDesc = task.optString("gameActionDesc", "");
        String action = miniGameAction(spec, actionDesc);
        if (action.isEmpty()) {
            Log.i("游戏中心🎮赚现金任务[" + title + "]#消除任务未配置xiaochu动作码，跳过");
            return false;
        }
        int target = GameCenterMiniGameReporter.requiredTimes(actionDesc);
        int every = MiniGameTaskTable.everyOf(spec.appId, action);
        int need = Math.max(1, (target + every - 1) / every);
        Log.i("游戏中心🎮赚现金任务[" + title + "]厂商上报开始：" + spec.title
                + " action=" + action + " 次数=" + need + " 目标=" + target + " every=" + every
                + " 描述=" + actionDesc);
        int done = 0;
        try {
            done = GameCenterMiniGameReporter.report(spec.withAction(action), need);
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
        if (done >= need) {
            Log.other("游戏中心🎮赚现金任务[" + title + "]#厂商进度已上报 " + done + "/" + need );
            return true;
        }
        if (done == 0) {
            Log.i("游戏中心🎮赚现金任务[" + title + "]#厂商上报未完成(0/" + need + ")，保留待办");
            return false;
        }
        Log.i("游戏中心🎮赚现金任务[" + title + "]#厂商上报未完成(" + done + "/" + need + ")，保留待办等待重试");
        return true;
    }

    private static boolean receive(JSONObject task) {
        String taskId = task.optString("taskId", "");
        String title = titleOf(task);
        String flagKey = "GameCenter::receive_" + taskId;
        if (Boolean.TRUE.equals(Status.hasFlagToday(flagKey))) {
            return false;
        }
        String milestoneId = task.optString("taskMileStoneId", "");
        JSONObject jo;
        if (!milestoneId.isEmpty() && !"null".equals(milestoneId)) {
            jo = json(GameCenterRpcCall.p2eMilestoneReceive(task, sourceOf(task), oriChInfoOf(task)));
        } else {
            jo = json(GameCenterRpcCall.p2eTaskReceive(task, actionChannelOf(task), sourceOf(task), oriChInfoOf(task)));
        }
        if (ok(jo)) {
            int gold = task.optInt("goldCoinAmount", 0);
            StringBuilder sb = new StringBuilder("游戏中心🎮赚现金任务[" + title + "]#领取成功");
            if (gold > 0) {
                sb.append("#金币+").append(gold);
            }
            Log.other(sb.toString());
            Status.flagToday(flagKey);
            return true;
        }
        Log.i("游戏中心🎮赚现金任务[" + title + "]#领取失败#" + messageOf(jo));
        return false;
    }

    /**
     * AG 的 isGameCenterP2eAutoTask：只有这些平台任务能靠 platformTaskComplete 直接闭环，
     * 其余 taskType/actionType 组合（浏览、广告、真实游戏）必须走厂商上报或时长合同。
     */
    /** 任务里的浮动球时长（「浏览 15 秒」这类任务才有）。 */
    private static int floatingBallSeconds(JSONObject task) {
        JSONObject floatingBall = task.optJSONObject("floatingBall");
        if (floatingBall != null) {
            int seconds = floatingBall.optInt("timeSeconds", 0);
            if (seconds > 0) {
                return seconds;
            }
        }
        int seconds = task.optInt("timeSeconds", 0);
        if (seconds > 0) {
            return seconds;
        }
        // LIGHT_TRAN_TASK 的时长只写在副标题里：「玩60秒」「浏览15秒」「观看30秒」。
        String text = task.optString("subTitle", "") + " " + task.optString("title", "");
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+)\\s*秒").matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    /**
     * 完成「浮动球」类浏览任务：查询可完成时长 → 分段上报浏览时长 → 提交完成。
     * <p>
     * 直接发 platformTaskComplete 只会被服务端「受理」但不记账，这就是之前「提交了没反应」的原因。
     */
    private static boolean completeFloatingBallTask(JSONObject task, String title, int seconds) {
        String gameId = task.optString("gameId", "");
        String gameModuleId = task.optString("gameModuleId", "");
        if (gameModuleId.isEmpty() || "null".equals(gameModuleId)) {
            // 任务对象里没有模块 id，用平台模块级的下发值（gc22 实测缺它会被判 CONFIGURATION_ERROR）。
            gameModuleId = lastGameModuleId;
        }
        String trafficDriverId = task.optString("trafficDriverId", "");
        String appId = task.optString("appId", "");
        if (appId.isEmpty()) {
            // 浏览类平台任务的 task JSON 没有 appId 字段，只有 jumpLink，真正的 gameAppId 要从链接里取。
            appId = appIdFromLink(task.optString("jumpLink", ""));
        }
        if (gameId.isEmpty()) {
            gameId = appIdFromLink(task.optString("jumpLink", ""));
        }
        String source = sourceOf(task);
        String oriChInfo = oriChInfoOf(task);
        JSONObject consult = probeFloatingBallConsult(gameId, gameModuleId, source, trafficDriverId, appId);
        List<String> types = new ArrayList<>();
        int duration = seconds;
        JSONObject consultData = consult.optJSONObject("data");
        if (consultData != null) {
            JSONArray typeList = consultData.optJSONArray("floatingBallTypeList");
            if (typeList != null) {
                for (int i = 0; i < typeList.length(); i++) {
                    String type = typeList.optString(i, "");
                    if (!type.isEmpty()) {
                        types.add(type);
                    }
                }
            }
            int serverSeconds = consultData.optInt("timeSeconds", 0);
            if (serverSeconds > 0) {
                duration = serverSeconds;
            }
        }
        if (duration <= 0) {
            duration = seconds;
        }
        if (duration <= 0) {
            duration = 60;
        }
        // AG 实测：总秒数 = 时长 + 1，首段 31 秒，之后每段 30 秒。
        int totalSeconds = Math.max(1, duration + 1);
        int reported = 0;
        boolean ackOk = true;
        while (reported < totalSeconds) {
            int chunk = Math.min(reported == 0 ? 31 : 30, totalSeconds - reported);
            JSONObject ack = json(GameCenterRpcCall.submitUserPlayDurationAction(appId, chunk, source));
            if (ack != null && ack.has("errorCode")) {
                ackOk = false;
                Log.i("游戏中心🎮赚现金任务[" + title + "]#浏览时长上报失败#" + messageOf(ack));
                break;
            }
            reported += chunk;
            TimeUtil.sleep(600);
        }
        if (!ackOk) {
            return false;
        }
        JSONObject complete = json(GameCenterRpcCall.p2eFloatingBallComplete(gameId, gameModuleId, oriChInfo,
                source, trafficDriverId, types));
        if (!ok(complete)) {
            Log.i("游戏中心🎮赚现金任务[" + title + "]#浮球完成失败#" + messageOf(complete));
            return false;
        }
        // 服务端把时长/完成受理后还要回查确认，不能直接当成功（gc19 的教训：受理 ≠ 记账）。
        TimeUtil.sleep(2500);
        return true;
    }


    /** 手动「赚现金任务」时在屏幕上显示一行抓包小结（接口名 <<原始响应，方便与浏览器 F12 对照）。 */
    private static String floatingBallHeadline(JSONObject task) {
        String link = task.optString("jumpLink", "");
        if (link.length() > 160) {
            link = link.substring(0, 160) + "…";
        }
        return "gameP2eFloatingBallConsult<<REQ_PARAM_ILLEGAL\nlink=" + link;
    }


    /**
     * 浮球查询「多族探测」：p2e 族的 gameId 为空时会被判 REQ_PARAM_ILLEGAL，
     * 这里依次尝试 channeltask 族与旧 floatingball 族，并打印每一族的原始响应，
     * 好让手动运行时能直接和浏览器 F12 里的真实请求对照。
     */
    private static JSONObject probeFloatingBallConsult(String gameId, String gameModuleId, String source,
                                                       String trafficDriverId, String appId) {
        JSONObject p2e = json(GameCenterRpcCall.p2eFloatingBallConsult(gameId, gameModuleId, source, trafficDriverId));
        if (p2e.optBoolean("success", false)) {
            return p2e;
        }
        JSONObject chan = json(GameCenterRpcCall.channelTaskFloatingBallConsult(gameId, gameModuleId, source, trafficDriverId));
        if (chan.optBoolean("success", false)) {
            return chan;
        }
        JSONObject legacy = json(GameCenterRpcCall.floatingBallConsultLegacy(gameId, gameModuleId, source, trafficDriverId));
        if (legacy.optBoolean("success", false)) {
            return legacy;
        }
        String frontEnd = GameCenterRpcCall.floatingBallFrontEndConsultAnswer("", "", source, null);
        return p2e;
    }

    private static boolean isAutoP2eTask(JSONObject task) {
        String taskType = upper(task.optString("taskType", ""));
        String actionType = upper(task.optString("actionType", ""));
        if (!"PLATFORM_TRAN_TASK".equals(taskType)) {
            return false;
        }
        return "VIEW_TASK".equals(actionType)
                || "SUBSCRIBE_TASK".equals(actionType)
                || "ADD_DESKTOP_TASK".equals(actionType)
                || "SWITCH_APP_TASK".equals(actionType)
                || "SET_HEAD_TASK".equals(actionType);
    }

    private static String unsupportedReason(JSONObject task) {
        String taskType = upper(task.optString("taskType", ""));
        if ("GAME_TRAN_TASK".equals(taskType)) {
            return "真实游戏通关/订单任务缺少已知厂商上报通道，保留待办等待刷新";
        }
        return "当前暂无稳定自动闭环，保留待办等待刷新[taskType=" + taskType
                + ",actionType=" + upper(task.optString("actionType", ""))
                + ",token=" + (task.optString("taskToken", "").isEmpty() ? "无" : "有") + "]";
    }

    // ------------------------------------------------------------- 5. 赚现金签到

    private static boolean doP2eSignIn() {
        JSONObject home = json(GameCenterRpcCall.queryP2eHomePage());
        if (!ok(home)) {
            return p2eFailure("赚现金签到查询", home);
        }
        JSONObject data = home.optJSONObject("data");
        if (data == null) {
            Log.i("游戏中心🎮[赚现金暂无签到模块]");
            return true;
        }
        lastHome = data;
        if (data.optBoolean("hitRiskControl") || !data.optString("hitRiskControlMsg", "").isEmpty()) {
            Log.i("游戏中心🎮[赚现金签到业务受限，跳过]#" + data.optString("hitRiskControlMsg", ""));
            return true;
        }
        JSONObject signUp = data.optJSONObject("signUpModuleVO");
        if (signUp == null) {
            Log.i("游戏中心🎮[赚现金暂无签到模块]");
            return true;
        }
        JSONObject today = todaySignRecord(signUp);
        if (today != null && "SIGNED".equalsIgnoreCase(today.optString("signUpStatus", ""))) {
            Log.i("游戏中心🎮[赚现金今日已签到]" + goldSuffix(today));
            return true;
        }
        String date = signUp.optString("date", "");
        int index = signUp.optInt("index", 0);
        String sequenceId = signUp.optString("signSequenceId", "");
        if (date.isEmpty() || sequenceId.isEmpty()) {
            Log.i("游戏中心🎮[赚现金签到配置缺失]#date=" + date + " index=" + index + " signSequenceId=" + sequenceId);
            return false;
        }
        JSONObject sign = json(GameCenterRpcCall.p2eSignIn(date, index, sequenceId, GameCenterRpcCall.GAME_CENTER_SOURCE));
        if (!ok(sign)) {
            return p2eFailure("赚现金签到", sign);
        }
        JSONObject signData = sign.optJSONObject("data");
        JSONObject popup = signData == null ? null : signData.optJSONObject("signUpPopupModuleVO");
        JSONObject record = popup == null ? null : todaySignRecord(popup);
        if (record != null && "SIGNED".equalsIgnoreCase(record.optString("signUpStatus", ""))) {
            Log.other("游戏中心🎮[赚现金签到成功]" + goldSuffix(record));
            return true;
        }
        Log.i("游戏中心🎮[赚现金签到已提交，等待状态刷新]");
        return true;
    }

    private static JSONObject todaySignRecord(JSONObject module) {
        JSONArray records = module.optJSONArray("signRecordVOList");
        if (records == null) {
            return null;
        }
        String date = module.optString("date", "");
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) {
                continue;
            }
            if (record.optBoolean("isToday", false)) {
                return record;
            }
        }
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record != null && date.equals(record.optString("signDate", ""))) {
                return record;
            }
        }
        return null;
    }

    private static String goldSuffix(JSONObject record) {
        int amount = record == null ? 0 : record.optInt("todayGoldCoinAmount", 0);
        return amount > 0 ? "#金币+" + amount : "";
    }

    // ------------------------------------------------------------- 6. 赚现金抽金币

    private static boolean doP2eDrawGold() {
        JSONObject home = json(GameCenterRpcCall.queryP2eHomePage());
        if (!ok(home)) {
            return p2eFailure("赚现金抽金币查询", home);
        }
        JSONObject data = home.optJSONObject("data");
        if (data == null) {
            Log.i("游戏中心🎮[赚现金暂无抽金币模块]");
            return true;
        }
        lastHome = data;
        if (data.optBoolean("hitRiskControl") || data.optBoolean("hitFourControlLimit")) {
            Log.i("游戏中心🎮[赚现金抽金币业务受限，跳过]");
            return true;
        }
        JSONObject drawModule = data.optJSONObject("drawGoldCoinModuleVO");
        if (drawModule == null) {
            Log.i("游戏中心🎮[赚现金暂无抽金币模块]");
            return true;
        }
        String status = upper(drawModule.optString("status", ""));
        if ("DRAWN".equals(status)) {
            Log.i("游戏中心🎮[赚现金抽金币已完成]#奖励次日解锁领取");
            return true;
        }
        if (!"NOT_DRAWN".equals(status) && !"FULFILL_FAILED".equals(status)) {
            Log.i("游戏中心🎮[赚现金抽金币状态未识别]#status=" + status);
            return false;
        }
        JSONObject draw = json(GameCenterRpcCall.p2eDrawGold());
        if (!ok(draw)) {
            return p2eFailure("赚现金抽金币", draw);
        }
        JSONObject after = json(GameCenterRpcCall.queryP2eHomePage());
        if (!ok(after)) {
            return p2eFailure("赚现金抽金币回查", after);
        }
        JSONObject afterData = after.optJSONObject("data");
        if (afterData != null) {
            lastHome = afterData;
        }
        JSONObject afterDraw = afterData == null ? null : afterData.optJSONObject("drawGoldCoinModuleVO");
        String afterStatus = afterDraw == null ? "" : upper(afterDraw.optString("status", ""));
        if ("DRAWN".equals(afterStatus)) {
            JSONObject popup = draw.optJSONObject("data") == null ? null
                    : draw.optJSONObject("data").optJSONObject("popupInfoVO");
            String title = popup == null ? "" : popup.optString("mainTitle", "");
            Log.other("游戏中心🎮[赚现金抽金币成功]" + (title.isEmpty() ? "#奖励次日解锁领取" : "#" + title));
            return true;
        }
        Log.i("游戏中心🎮[赚现金抽金币已提交，等待状态刷新]");
        return true;
    }

    // ------------------------------------------------------------- 7. 赚现金提现

    private static boolean doP2eCashExchange() {
        JSONObject home = json(GameCenterRpcCall.queryP2eHomePage());
        if (!ok(home)) {
            return p2eFailure("赚现金提现前状态查询", home);
        }
        JSONObject data = home.optJSONObject("data");
        if (data == null) {
            return true;
        }
        if (data.optBoolean("hitRiskControl") || data.optBoolean("hitFourControlLimit")) {
            Log.i("游戏中心🎮[赚现金提现]#服务端业务受限，本轮不提交");
            return true;
        }
        JSONObject page = json(GameCenterRpcCall.queryP2eGoldExchangePrizePage());
        if (!ok(page)) {
            return p2eFailure("赚现金提现档位查询", page);
        }
        JSONObject pageData = page.optJSONObject("data");
        if (pageData == null) {
            Log.i("游戏中心🎮[赚现金提现]#服务端未返回现金档位");
            return true;
        }
        JSONObject cashModule = pageData.optJSONObject("cashExchangeModule");
        if (cashModule == null) {
            Log.i("游戏中心🎮[赚现金提现]#当前无现金兑换模块");
            return true;
        }
        JSONObject prize = findTenYuanPrize(cashModule.optJSONArray("prizes"));
        if (prize == null) {
            Log.i("游戏中心🎮[赚现金提现]#当前无服务端选中的固定10元档");
            return true;
        }
        String prizeStatus = upper(prize.optString("prizeStatus", ""));
        if (!"CAN_EXG".equals(prizeStatus)) {
            Log.i("游戏中心🎮[赚现金提现]#固定10元档当前状态=" + (prizeStatus.isEmpty() ? "UNKNOWN" : prizeStatus));
            return true;
        }
        JSONObject asset = pageData.optJSONObject("assetModuleVO");
        long balance = asset == null ? -1 : parseLong(asset.optString("goldAmount", ""));
        JSONObject confirm = prize.optJSONObject("confirmPopup");
        long need = confirm == null ? -1 : parseLong(confirm.optString("consumeGold", ""));
        if (balance < 0 || need < 0) {
            Log.i("游戏中心🎮[赚现金提现]#服务端余额或门槛字段缺失");
            return false;
        }
        if (balance < need) {
            Log.i("游戏中心🎮[赚现金提现]#金币未达门槛[当前=" + balance + ",需要=" + need + "]");
            return true;
        }
        if (!hasExchangeContract(prize)) {
            Log.i("游戏中心🎮[赚现金提现]#服务端缺少动态提交参数");
            return false;
        }
        JSONObject exchange = json(GameCenterRpcCall.doP2eGoldExchangePrize(prize));
        JSONObject exchangeData = exchange == null ? null : exchange.optJSONObject("data");
        String exgStatus = exchangeData == null ? "" : upper(exchangeData.optString("exgStatus", ""));
        if (!ok(exchange) || !"SUCCESS".equals(exgStatus)) {
            boolean retryable = exchange != null && exchange.optBoolean("retryable");
            Log.i("游戏中心🎮[赚现金提现]#" + (retryable ? "可重试失败:" : "提交未确认:") + messageOf(exchange));
            return false;
        }
        String amount = exchangeData.optString("amount", prize.optString("prizeAmount", ""));
        String tips = exchangeData.optString("arrivalTips", "");
        Log.i("游戏中心🎮[赚现金提现提交成功]#" + amount + "元" + (tips.isEmpty() ? "" : "#" + tips));

        JSONObject rePage = json(GameCenterRpcCall.queryP2eGoldExchangePrizePage());
        if (!ok(rePage)) {
            Log.i("游戏中心🎮[赚现金提现]#提交成功但回查失败");
            return false;
        }
        JSONObject reData = rePage.optJSONObject("data");
        boolean confirmed = isExchangeConfirmed(pageData, reData, prize);
        if (confirmed) {
            Log.i("游戏中心🎮[赚现金提现已确认]#" + amount + "元");
            return true;
        }
        Log.i("游戏中心🎮[赚现金提现]#提交成功但回查未确认，本轮不重复提交");
        return false;
    }

    private static JSONObject findTenYuanPrize(JSONArray prizes) {
        if (prizes == null) {
            return null;
        }
        for (int i = 0; i < prizes.length(); i++) {
            JSONObject prize = prizes.optJSONObject(i);
            if (prize == null || !prize.optBoolean("selected", false)) {
                continue;
            }
            if (!"FIXED_TIER".equalsIgnoreCase(prize.optString("withdrawalTierType", ""))) {
                continue;
            }
            if (!"元".equals(prize.optString("prizeUnit", ""))) {
                continue;
            }
            double amount = parseDouble(prize.optString("prizeAmount", ""));
            if (Math.abs(amount - 10.0) < 0.001) {
                return prize;
            }
        }
        return null;
    }

    private static boolean hasExchangeContract(JSONObject prize) {
        return !prize.optString("outBizNo", "").isEmpty()
                && !prize.optString("prizeConfigId", "").isEmpty()
                && !prize.optString("withdrawalTierType", "").isEmpty()
                && (!prize.optString("sendSign", "").isEmpty() || !prize.optString("sendSignNew", "").isEmpty());
    }

    private static boolean isExchangeConfirmed(JSONObject before, JSONObject after, JSONObject target) {
        if (after == null) {
            return false;
        }
        JSONObject module = after.optJSONObject("cashExchangeModule");
        JSONArray prizes = module == null ? null : module.optJSONArray("prizes");
        String targetId = target.optString("prizeConfigId", "");
        JSONObject refreshed = null;
        if (prizes != null) {
            for (int i = 0; i < prizes.length(); i++) {
                JSONObject prize = prizes.optJSONObject(i);
                if (prize != null && targetId.equals(prize.optString("prizeConfigId", ""))) {
                    refreshed = prize;
                    break;
                }
            }
        }
        if (refreshed == null) {
            return true;
        }
        if (!"CAN_EXG".equalsIgnoreCase(refreshed.optString("prizeStatus", ""))) {
            return true;
        }
        JSONObject beforeAsset = before.optJSONObject("assetModuleVO");
        JSONObject afterAsset = after.optJSONObject("assetModuleVO");
        long beforeGold = beforeAsset == null ? -1 : parseLong(beforeAsset.optString("goldAmount", ""));
        long afterGold = afterAsset == null ? -1 : parseLong(afterAsset.optString("goldAmount", ""));
        return beforeGold >= 0 && afterGold >= 0 && afterGold < beforeGold;
    }

    private static boolean p2eFailure(String scene, JSONObject response) {
        String message = messageOf(response);
        if (isBusinessLimited(response, message)) {
            Log.i("游戏中心🎮[" + scene + "]#业务受限，本轮跳过:" + message);
            return true;
        }
        if (isDuplicateOrDone(message)) {
            Log.i("游戏中心🎮[" + scene + "]#已处理过，跳过重复处理:" + message);
            return true;
        }
        Log.i("游戏中心🎮[" + scene + "]#失败:" + message);
        return false;
    }

    private static boolean isBusinessLimited(JSONObject response, String message) {
        if (response != null) {
            String code = upper(response.optString("errorCode",
                    response.optString("resultCode", "")));
            if ("PROMO_RISK_ERROR".equals(code)) {
                return true;
            }
        }
        if (message == null) {
            return false;
        }
        return message.contains("不在活动邀请范围") || message.contains("风险")
                || message.contains("风控") || message.contains("受限");
    }

    private static boolean isDuplicateOrDone(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("已签到") || message.contains("已领取")
                || message.contains("重复") || message.toLowerCase(Locale.ROOT).contains("already");
    }

    // ------------------------------------------------------------- 任务列表解析

    private static List<JSONObject> collectTasks(JSONObject root) {
        List<JSONObject> result = new ArrayList<>();
        if (root == null) {
            return result;
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            return result;
        }
        String[] modules = new String[]{"gameTaskModule", "platformTaskModule", "platformGameTaskModule"};
        String[] lists = new String[]{"gameTaskList", "platformTaskList", "taskList"};
        for (String moduleName : modules) {
            JSONObject module = data.optJSONObject(moduleName);
            if (module == null) {
                continue;
            }
            for (String listName : lists) {
                append(result, module.optJSONArray(listName));
            }
        }
        for (String listName : lists) {
            append(result, data.optJSONArray(listName));
        }
        return dedupe(result);
    }

    private static List<JSONObject> collectP2eTasks(JSONObject root) {
        List<JSONObject> result = new ArrayList<>();
        if (root == null) {
            return result;
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            return result;
        }
        JSONObject platformModule = data.optJSONObject("platformGameTaskModule");
        if (platformModule != null) {
            String[] lists = new String[]{"platformGameTaskList", "platformTaskList", "taskList", "gameTaskList"};
            for (String listName : lists) {
                JSONArray array = platformModule.optJSONArray(listName);
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject item = array.optJSONObject(i);
                        if (item != null) {
                            try {
                                item.put("_container", listName);
                            } catch (Throwable ignored) {
                            }
                            result.add(item);
                        }
                    }
                }
            }
        }
        JSONObject milestoneReward = data.optJSONObject("taskMileStoneRewardVO");
        JSONArray milestones = milestoneReward == null ? null : milestoneReward.optJSONArray("taskMileStoneList");
        if (milestones != null) {
            for (int i = 0; i < milestones.length(); i++) {
                JSONObject item = milestones.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                JSONObject copy = new JSONObject();
                try {
                    copy.put("taskId", item.optString("taskMileStoneId", ""));
                    copy.put("taskMileStoneId", item.optString("taskMileStoneId", ""));
                    copy.put("taskStatus", item.optString("status", ""));
                    copy.put("title", "累计任务奖励");
                    copy.put("outBizNo", item.optString("outBizNo", ""));
                    copy.put("sign", item.optString("sign", ""));
                } catch (Throwable ignored) {
                }
                result.add(copy);
            }
        }
        JSONObject exposed = data.optJSONObject("exposedTaskModuleVO");
        JSONArray exposedList = exposed == null ? null : exposed.optJSONArray("exposedTaskList");
        append(result, exposedList);
        if (!result.isEmpty() && exposedList != null && exposedList.length() > 0) {
            JSONArray exposedIds = new JSONArray();
            for (int i = 0; i < exposedList.length(); i++) {
                JSONObject item = exposedList.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String taskId = item.optString("taskId", "");
                String taskType = item.optString("taskType", "");
                if (taskId.isEmpty() || taskType.isEmpty()) {
                    continue;
                }
                JSONObject exposedItem = new JSONObject();
                try {
                    exposedItem.put("taskId", taskId);
                    exposedItem.put("taskType", taskType);
                } catch (Throwable ignored) {
                }
                exposedIds.put(exposedItem);
            }
            if (exposedIds.length() > 0) {
                GameCenterRpcCall.reportP2eExposedTasks(exposedIds);
            }
        }
        return dedupe(result);
    }

    private static void append(List<JSONObject> target, JSONArray array) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                target.add(item);
            }
        }
    }

    private static List<JSONObject> dedupe(List<JSONObject> tasks) {
        List<JSONObject> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JSONObject task : tasks) {
            String taskId = task.optString("taskId", "");
            if (taskId.isEmpty() || "null".equals(taskId)) {
                continue;
            }
            if (seen.add(taskId)) {
                result.add(task);
            }
        }
        return result;
    }

    private static JSONObject findTask(JSONObject root, String taskId) {
        for (JSONObject task : collectTasks(root)) {
            if (taskId.equals(task.optString("taskId", ""))) {
                return task;
            }
        }
        return null;
    }

    // ------------------------------------------------------------- 小工具

    private static String titleOf(JSONObject task) {
        String title = task.optString("title", "");
        if (title.isEmpty() || "null".equals(title)) {
            title = task.optString("subTitle", "");
        }
        if (title.isEmpty() || "null".equals(title)) {
            title = task.optString("appName", "");
        }
        return title;
    }

    private static String pointSuffix(JSONObject task) {
        int amount = task.optInt("pointAmount", 0);
        return amount > 0 ? ",奖励" + amount + "玩乐豆" : "";
    }

    private static String actionChannelOf(JSONObject task) {
        String channel = task.optString("_actionChannel", "");
        if (channel.isEmpty() || "null".equals(channel)) {
            channel = task.optString("actionChannel", "");
        }
        if (channel.isEmpty() || "null".equals(channel)) {
            channel = "taskList";
        }
        return channel.isEmpty() || "null".equals(channel) ? "taskList" : channel;
    }

    private static String sourceOf(JSONObject task) {
        String source = task.optString("_source", "");
        if (source.isEmpty() || "null".equals(source)) {
            source = task.optString("source", "");
        }
        if (!source.isEmpty() && !"null".equals(source)) {
            return source;
        }
        // 2026-11-19：任务对象本身没有 source 字段，只有 jumpLink 里的 oriChInfo（如 yxzx_mc_xasqsr68，
        // 那是厂商渠道），且 query 里带 oriChInfo=ch_appcenter__chsub_9patch —— 手点请求用的就是这个。
        String link = task.optString("jumpLink", "");
        int idx = link.indexOf("oriChInfo%3D");
        if (idx < 0) {
            idx = link.indexOf("oriChInfo=");
        }
        if (idx >= 0) {
            String tail = link.substring(idx);
            tail = tail.substring(tail.indexOf('=') + 1);
            int end = tail.length();
            for (int i = 0; i < tail.length(); i++) {
                char c = tail.charAt(i);
                if (c == '%' || c == '&' || c == '?' || c == '#') {
                    end = i;
                    break;
                }
            }
            String fromLink = tail.substring(0, end);
            if (!fromLink.isEmpty() && fromLink.startsWith("ch_")) {
                return fromLink;
            }
        }
        return GameCenterRpcCall.GAME_CENTER_P2E_SOURCE;
    }

    private static String oriChInfoOf(JSONObject task) {
        String info = task.optString("_oriChInfo", "");
        if (info.isEmpty() || "null".equals(info)) {
            info = task.optString("oriChInfo", "");
        }
        return info.isEmpty() || "null".equals(info) ? "ch_appcenter__chsub_9patch" : info;
    }

    private static JSONObject json(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(text);
        } catch (Throwable t) {
            Log.i(TAG, "json parse err: " + (text.length() > 200 ? text.substring(0, 200) : text));
            return null;
        }
    }

    /** 日志截断，避免把整段任务列表刷进日志。 */
    private static String clip(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 900 ? text : text.substring(0, 900) + "…(" + text.length() + ")";
    }

    /** 从 jumpLink / targetUrl 里取 appId。 */
    private static String appIdFromLink(String link) {
        if (link == null || link.isEmpty()) {
            return "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("appId=(\\d+)").matcher(link);
        return m.find() ? m.group(1) : "";
    }

    private static boolean ok(JSONObject jo) {
        if (jo == null) {
            return false;
        }
        if (jo.has("success")) {
            return jo.optBoolean("success");
        }
        if (jo.has("isSuccess")) {
            return jo.optBoolean("isSuccess");
        }
        return jo.has("data");
    }

    private static String messageOf(JSONObject jo) {
        if (jo == null) {
            return "响应为空";
        }
        String[] keys = new String[]{"errorMsg", "errorMessage", "resultView", "resultDesc", "desc", "memo", "message"};
        for (String key : keys) {
            String value = jo.optString(key, "");
            if (!value.isEmpty() && !"null".equals(value)) {
                return value;
            }
        }
        String text = jo.toString();
        return text.length() > 200 ? text.substring(0, 200) : text;
    }

    private static String upper(String text) {
        return text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
    }

    private static long parseLong(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (Throwable t) {
            return -1;
        }
    }

    private static double parseDouble(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (Throwable t) {
            return -1;
        }
    }
}