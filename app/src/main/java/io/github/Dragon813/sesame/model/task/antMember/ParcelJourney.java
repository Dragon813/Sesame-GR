package io.github.Dragon813.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.github.Dragon813.sesame.data.ModelFields;
import io.github.Dragon813.sesame.data.ModelGroup;
import io.github.Dragon813.sesame.data.RuntimeInfo;
import io.github.Dragon813.sesame.data.task.ModelTask;
import io.github.Dragon813.sesame.hook.ApplicationHook;
import io.github.Dragon813.sesame.util.Log;
import io.github.Dragon813.sesame.util.Status;
import io.github.Dragon813.sesame.util.TimeUtil;
import io.github.Dragon813.sesame.util.idMap.UserIdMap;

/**
 * 包裹游历（我的快递 → 包裹游历）自动化。
 * <p>
 * 2026-11-17 真机抓包得到的三类动作，这里实现了其中两个：
 * 1) {@code alipay.mobile.logisticsinteraction.game.parcelMap.receiveAllRewards}
 *    —— 一键领完全部包裹奖励，body 为空对象；
 * 2) {@code alipay.promoprod.task.listQuery} + {@code alipay.promoprod.applet.trigger}
 *    —— 「攒芝麻粒」任务：报名(signup) + 发奖(send) 两段式，奖励当场到账
 *    （抓到的即时发奖标志：{@code TASK_MORPHO_DETAIL.immediateClaim=true}），
 *    实测完全不需要真的去逛。
 * <p>
 * 未实现：棋局（board.signup + 走格/抽奖）。棋局要靠包裹碎片推进，
 * 碎片来自包裹与任务，抓包时碎片为 0，先不写死这块逻辑。
 */
public class ParcelJourney extends ModelTask {

    private static final String TAG = "ParcelJourney";

    /** 抓包得到的任务中心串（listQuery / trigger 都要求带），按用户维度固定 */
    private static final String TASK_CEN_INFO = "MZVPQ0DScvD6NjaPJzk8iE31OtnKddQY";

    private static final String RPC_RECEIVE_ALL = "alipay.mobile.logisticsinteraction.game.parcelMap.receiveAllRewards";
    private static final String RPC_LIST = "alipay.promoprod.task.listQuery";
    private static final String RPC_TRIGGER = "alipay.promoprod.applet.trigger";

    private static final String FLAG_DONE = "ParcelJourney::done";

    /** 单个任务两段式之间的等待，抓包里真实点击间隔 0.3~11 秒 */
    private static final long STAGE_INTERVAL = 900L;

    /** 信息记录（info 日志）快照：每轮只输出一行，从 listQuery 这一条概况响应里提炼。 */
    private static final String INFO_KEY = "ParcelJourney::info";
    private static int infoScanned;
    private static int infoFinished;
    private static int infoCanAccess;
    private static int infoNeedSignUp;
    private static int infoDirect;
    private static int infoAttempted;
    private static int infoDone;
    private static int infoBeans;


    @Override
    public String getName() {
        return "包裹游历";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }

    @Override
    public ModelFields getFields() {
        // 字段已全部搬到「扩展任务」里，这里不再显示任何开关
        return new ModelFields();
    }

    @Override
    public Boolean check() {
        return true;
    }

    @Override
    public void run() {
        runFromExtension();
    }

    /** 由「扩展任务」调用：不再检查父开关，只看两个包裹游历子开关 */
    public static void runFromExtension() {
        new ParcelJourney().runInternal();
    }

    private void runInternal() {
        try {
            if (Status.hasFlagToday(FLAG_DONE)) {
                Log.i("包裹游历📦[今日已处理，跳过]");
                String info = RuntimeInfo.getInstance().getString(INFO_KEY);
                Log.info(info.isEmpty()
                        ? "包裹游历📦[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]今日已处理;"
                        : info);
                return;
            }
            Log.i("包裹游历📦执行开始");
            infoScanned = 0;
            infoFinished = 0;
            infoCanAccess = 0;
            infoNeedSignUp = 0;
            infoDirect = 0;
            infoAttempted = 0;
            infoDone = 0;
            infoBeans = 0;
            boolean receive = doReceiveAll();
            boolean promo = doPromoTasks();
            // 每天只跑一次：无论是否真的领到，跑完就打当日标记，避免每次启动支付宝都重复请求
            Status.flagToday(FLAG_DONE);
            Log.i("包裹游历📦执行结束" + (receive || promo ? "" : "（本轮无可领取）"));
            // 信息记录跟随「包裹游历」开关：本方法只在开关开启时由 AntMember 调用
            String info = "包裹游历📦[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]一键领取:"
                    + (receive ? "成功" : "未成功")
                    + ";任务:共" + Log.n(infoScanned) + "个/已完成" + Log.n(infoFinished)
                    + "个/可报名" + Log.n(infoCanAccess) + "个/需报名" + Log.n(infoNeedSignUp)
                    + "个/直塞" + Log.n(infoDirect) + "个/可做" + Log.n(infoAttempted) + "个"
                    + ";攒芝麻粒:成功" + Log.n(infoDone) + "个/领到" + Log.n(infoBeans) + "个裹力橙;";
            RuntimeInfo.getInstance().put(INFO_KEY, info);
            Log.info(info);
        } catch (Throwable t) {
            Log.i(TAG + " 包裹游历执行异常:" + t);
        }
    }

    // ------------------------------------------------------------ 一键领包裹奖励

    private boolean doReceiveAll() {
        JSONObject response = rpc(RPC_RECEIVE_ALL, json());
        if (response == null) {
            return false;
        }
        boolean success = response.optBoolean("success");
        String errorCode = response.optString("errorCode");
        if (success || "S200".equals(errorCode)) {
            Log.other("包裹游历📦[一键领包裹奖励]#成功");
            return true;
        }
        Log.i("包裹游历📦[一键领包裹奖励]#未成功:" + response.optString("errorCode") + response.optString("msg"));
        return false;
    }

    // ------------------------------------------------------------ 攒芝麻粒任务

    private boolean doPromoTasks() {
        JSONObject response = rpc(RPC_LIST, json("consultAccessFlag", true, "taskCenInfo", TASK_CEN_INFO));
        if (response == null || !response.optBoolean("success")) {
            Log.i("包裹游历📦[攒芝麻粒]任务列表查询失败");
            return false;
        }
        JSONArray list = response.optJSONArray("taskDetailList");
        if (list == null || list.length() == 0) {
            Log.i("包裹游历📦[攒芝麻粒]没有任务");
            return false;
        }
        int scanned = 0;
        int attempted = 0;
        int done = 0;
        for (int i = 0; i < list.length(); i++) {
            JSONObject task = list.optJSONObject(i);
            if (task == null) {
                continue;
            }
            scanned++;
            String appletId = task.optString("taskId");
            if (appletId.isEmpty()) {
                appletId = task.optString("appletId");
            }
            String title = taskTitle(task);
            boolean canAccess = task.optBoolean("canAccess");
            int current = task.optInt("periodCurrentCompleteNum");
            int total = task.optInt("periodTotalCompleteNum");
            if (canAccess) {
                infoCanAccess++;
            }
            if (task.optBoolean("needSignUp")) {
                infoNeedSignUp++;
            }
            if (total > 0 && current >= total) {
                infoFinished++;
                Log.i("包裹游历📦[攒芝麻粒]跳过[" + title + "] 已完成 " + current + "/" + total);
                continue;
            }
            if (appletId.isEmpty()) {
                continue;
            }
            // 只做「直塞」任务：immediateClaim=true 且 sendCampTriggerType=USER_TRIGGER。
            // 其余（EVENT_TRIGGER / immediateClaim=false）要真实浏览或行为，send 会被服务端拒：
            // [10000005]参数错误, promoprod不允许完成事件规则任务
            if (!"USER_TRIGGER".equals(task.optString("sendCampTriggerType")) || !isImmediateClaim(task)) {
                Log.i("包裹游历📦[攒芝麻粒]跳过[" + title + "] 需真实浏览/行为（事件规则任务）");
                continue;
            }
            infoDirect++;
            attempted++;
            // 先直接发奖（可能之前已报名），发不出去再走 报名 + 发奖
            if (trigger(appletId, "send")) {
                done++;
                infoBeans += rewardOf(task);
                Log.other("包裹游历📦[攒芝麻粒][" + title + "]#成功(补发)");
                continue;
            }
            if (!canAccess) {
                Log.i("包裹游历📦[攒芝麻粒][" + title + "]今日报名次数已用完#未成功");
                continue;
            }
            if (trigger(appletId, "signup")) {
                TimeUtil.sleep(STAGE_INTERVAL);
                if (trigger(appletId, "send")) {
                    done++;
                    infoBeans += rewardOf(task);
                    Log.other("包裹游历📦[攒芝麻粒][" + title + "]#成功");
                    continue;
                }
            }
            Log.i("包裹游历📦[攒芝麻粒][" + title + "]#未成功");
        }
        infoScanned = scanned;
        infoAttempted = attempted;
        infoDone = done;
        Log.i("包裹游历📦[攒芝麻粒]本轮扫描 " + scanned + " 个任务，可做 " + attempted + " 个，成功 " + done + " 个");
        return done > 0;
    }

    private boolean trigger(String appletId, String stageCode) {
        JSONObject payload = json("appletId", appletId, "retryFlag", "true", "stageCode", stageCode, "taskCenInfo", TASK_CEN_INFO);
        JSONObject response = rpc(RPC_TRIGGER, payload);
        if (response == null) {
            return false;
        }
        JSONObject campTriggerInfo = response.optJSONObject("campTriggerInfo");
        String status = campTriggerInfo == null ? "" : campTriggerInfo.optString("status");
        if (response.optBoolean("success") && "SUCCESS".equals(status)) {
            return true;
        }
        String subMsg = response.optString("sub_msg");
        if (subMsg.isEmpty()) {
            subMsg = response.optString("msg");
        }
        String raw = response.toString();
        if (raw.length() > 400) {
            raw = raw.substring(0, 400);
        }
        Log.i("包裹游历📦[攒芝麻粒]" + appletId + " " + stageCode + " 失败:" + status + subMsg + " 响应=" + raw);
        return false;
    }

    private boolean isImmediateClaim(JSONObject task) {
        JSONObject ext = task.optJSONObject("taskExtProps");
        if (ext == null) {
            return false;
        }
        String detail = ext.optString("TASK_MORPHO_DETAIL");
        if (detail.isEmpty()) {
            return false;
        }
        try {
            return new JSONObject(detail).optBoolean("immediateClaim");
        } catch (JSONException ignored) {
            return false;
        }
    }

    /** 任务奖励数量（裹力橙），用于 info 日志统计。 */
    private static int rewardOf(JSONObject task) {
        JSONObject material = task.optJSONObject("taskMaterial");
        String rewards = material == null ? "" : material.optString("rewards", "");
        try {
            return rewards.isEmpty() ? 0 : Integer.parseInt(rewards.trim());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private String taskTitle(JSONObject task) {
        String name = task.optString("taskName");
        if (!name.isEmpty()) {
            return name;
        }
        JSONArray prizes = task.optJSONArray("prizeDetailDTOList");
        if (prizes != null && prizes.length() > 0) {
            JSONObject prize = prizes.optJSONObject(0);
            if (prize != null) {
                JSONObject base = prize.optJSONObject("prizeBaseInfoDTO");
                if (base != null) {
                    String prizeName = base.optString("prizeName");
                    if (!prizeName.isEmpty()) {
                        return prizeName;
                    }
                }
            }
        }
        return task.optString("taskId");
    }

    // ------------------------------------------------------------ 工具

    private JSONObject json(Object... pairs) {
        JSONObject object = new JSONObject();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            try {
                object.put(String.valueOf(pairs[i]), pairs[i + 1]);
            } catch (JSONException ignored) {
            }
        }
        return object;
    }

    private JSONObject rpc(String method, JSONObject requestData) {
        try {
            return new JSONObject(ApplicationHook.requestString(method, new JSONArray().put(requestData).toString()));
        } catch (Throwable t) {
            Log.i(TAG + " RPC异常 " + method + ":" + t);
            try {
                return new JSONObject("{\"success\":false}");
            } catch (JSONException ignored) {
                return new JSONObject();
            }
        }
    }
}