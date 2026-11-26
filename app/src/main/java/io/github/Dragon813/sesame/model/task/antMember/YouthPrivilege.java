package io.github.Dragon813.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.Dragon813.sesame.data.ModelFields;
import io.github.Dragon813.sesame.data.ModelGroup;
import io.github.Dragon813.sesame.data.task.ModelTask;
import io.github.Dragon813.sesame.hook.ApplicationHook;
import io.github.Dragon813.sesame.model.base.TaskCommon;
import io.github.Dragon813.sesame.model.task.antForest.Privilege;
import io.github.Dragon813.sesame.util.Log;
import io.github.Dragon813.sesame.util.Status;
import io.github.Dragon813.sesame.util.TimeUtil;

/**
 * 青春特权（移植自 Sesame-AG 的 task/youthPrivilege/YouthPrivilege.kt）。
 * <p>
 * 四个开关一一对应 AG 的同名开关：
 * 青春特权 | 签到青春豆 / 森林道具 / 青春任务 / 青春豆十连抽
 * <p>
 * 说明：
 * 1. 森林道具直接复用 GR 已有的 {@link Privilege#youthPrivilege()}（双击卡 / 保护罩 / 加速器）。
 * 2. 签到、青春任务、十连抽按 AG 抓到的 RPC 原样实现，全部“动作后回查”。
 * 3. AG 里挂在“青春任务”后面的「青春体验金」一并移植；「青春每月理财福利（YOUTH100）」
 * 依赖城市定位参数，GR 没有定位能力，这里只记一条日志跳过。
 */
public class YouthPrivilege extends ModelTask {

    private static final String TAG = "YouthPrivilege";

    /** AG 侧固定频道号 */
    private static final String CH_INFO = "searchxsth";
    private static final String PREFIX_YOUTH = "com.alipay.mobileopl.youthprivilege.rpc.mgw.";
    private static final String PREFIX_STUDENT = "alipay.membertangram.biz.rpc.student.";
    private static final String PREFIX_YEB = "com.alipay.yebpromobff.promosdk2024.prize.";

    private static final String SCENE_CODE = "STUDENT_MONEY_CHECK_IN";
    private static final String SUCCESS = "SUCCESS";

    private static final String ACTION_CHECK_IN = "CHECK_IN";
    private static final String ACTION_CHECKED_IN = "DO_TASK";

    private static final String STATUS_COMPLETE = "COMPLETE";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_TO_APPLY = "TO_APPLY";

    private static final String TASK_ACTION_SIGNUP = "SIGNUP";
    private static final String TASK_ACTION_COMPLETE = "COMPLETE";
    private static final String TASK_ACTION_DO_NOTHING = "DO_NOTHING";
    private static final String TASK_TYPE_BROWSER = "BROWSER";
    private static final String TASK_TYPE_FEEDS = "FEEDS";

    private static final String FLAG_CHECK_IN = "YouthPrivilege::checkIn";
    private static final String FLAG_TASKS = "YouthPrivilege::tasks";
    private static final String FLAG_MULTI_DRAWS = "YouthPrivilege::multiDrawsCalled";


    @Override
    public String getName() {
        return "青春特权";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.MEMBER;
    }

    @Override
    public ModelFields getFields() {
        // 字段已全部搬到「扩展任务」里，这里不再显示任何开关
        return new ModelFields();
    }

    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.i("任务暂停⏸️青春特权:当前为仅收能量时间");
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        runFromExtension();
    }

    /** 由「扩展任务」调用：不再检查父开关，只看 4 个青春特权子开关 */
    public static void runFromExtension() {
        new YouthPrivilege().runInternal();
    }

    private void runInternal() {
        try {
            Log.i("青春特权执行开始");
            doCheckIn();
            doYouthTasks();
            claimTrialPrize();
            doMonthlyPrivilege();
            doMultiDraws();
        } catch (Throwable t) {
            Log.i(TAG, "青春特权执行异常:" + t);
        } finally {
            Log.i("青春特权执行结束");
        }
    }

    // ---------------------------------------------------------------- 签到

    private void doCheckIn() {
        if (Status.hasFlagToday(FLAG_CHECK_IN)) {
            Log.i("青春特权🧧今日签到已确认，跳过");
            return;
        }
        JSONObject model = queryCheckInModel();
        if (model == null) {
            return;
        }
        JSONObject info = model.optJSONObject("studentCheckInInfo");
        String action = info == null ? "" : info.optString("action");
        if (ACTION_CHECKED_IN.equals(action)) {
            Status.flagToday(FLAG_CHECK_IN);
            Log.forest("青春特权🧧签到已完成#action=" + action);
            return;
        }
        if (!ACTION_CHECK_IN.equals(action)) {
            Log.i("青春特权🧧签到暂不处理#action=" + (action.isEmpty() ? "UNKNOWN" : action));
            return;
        }
        JSONObject result = rpc(PREFIX_YOUTH, "checkIn", json("source", CH_INFO));
        if (!isYouthSuccess(result)) {
            Log.i(TAG, "青春特权签到执行失败:" + result);
            return;
        }
        // 动作成功后必须回查服务端状态
        JSONObject confirmation = queryCheckInModel();
        if (confirmation == null) {
            return;
        }
        JSONObject confirmedInfo = confirmation.optJSONObject("studentCheckInInfo");
        String confirmedAction = confirmedInfo == null ? "" : confirmedInfo.optString("action");
        if (ACTION_CHECKED_IN.equals(confirmedAction)) {
            Status.flagToday(FLAG_CHECK_IN);
            Log.i("青春特权🧧签到回查确认完成");
        } else {
            Log.i(TAG, "青春特权签到执行成功但未确认进展#action=" + confirmedAction + " raw=" + confirmation);
        }
    }

    private JSONObject queryCheckInModel() {
        JSONObject payload = new JSONObject();
        try {
            payload.put("chInfo", CH_INFO);
            payload.put("queryAd", true);
            payload.put("skipTaskModule", false);
        } catch (JSONException ignored) {
        }
        JSONObject model = rpc(PREFIX_YOUTH, "queryCheckInModel", payload);
        if (!isYouthSuccess(model)) {
            Log.i(TAG, "青春特权签到模型查询失败:" + model);
            return null;
        }
        return model;
    }

    // 森林道具由 AntForestV2 的「青春特权」开关直接调用 Privilege.youthPrivilege() 完成，
    // 这里不再重复执行。

    // ------------------------------------------------------------ 青春任务

    private void doYouthTasks() {
        if (Status.hasFlagToday(FLAG_TASKS)) {
            Log.i("青春特权🎯青春任务已完成，跳过");
            return;
        }
        for (int round = 1; round <= 3; round++) {
            JSONObject payload = new JSONObject();
            try {
                payload.put("chInfo", CH_INFO);
                payload.put("skipTaskList", false);
            } catch (JSONException ignored) {
            }
            JSONObject response = rpc(PREFIX_YOUTH, "queryTaskModel", payload);
            if (!isYouthSuccess(response)) {
                Log.i(TAG, "青春特权任务查询失败:" + response);
                return;
            }
            boolean progressed = false;
            List<JSONObject> tasks = collectTasks(response);
            for (JSONObject task : tasks) {
                if (handleTask(task)) {
                    progressed = true;
                }
            }
            if (handleFeeds(response)) {
                progressed = true;
            }
            Log.i("青春特权🎯青春任务第" + round + "轮#任务数=" + tasks.size() + "#有进展=" + progressed);
            if (!progressed) {
                Status.flagToday(FLAG_TASKS);
                Log.i("青春特权🎯青春任务服务端已无待处理项");
                return;
            }
            TimeUtil.sleep(800);
        }
    }

    private List<JSONObject> collectTasks(JSONObject response) {
        Map<String, JSONObject> tasks = new LinkedHashMap<>();
        JSONObject module = response.optJSONObject("studentTaskModule");
        if (module == null) {
            module = new JSONObject();
        }
        JSONArray groups = module.optJSONArray("taskGroupList");
        if (groups != null) {
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.optJSONObject(i);
                if (group == null) {
                    continue;
                }
                JSONArray taskList = group.optJSONArray("taskList");
                if (taskList == null) {
                    continue;
                }
                for (int j = 0; j < taskList.length(); j++) {
                    JSONObject task = taskList.optJSONObject(j);
                    if (task == null) {
                        continue;
                    }
                    // 组级的计数偶尔只在组上出现，任务上缺失时补一份
                    for (String key : new String[]{"currentCount", "totalCount"}) {
                        if (!task.has(key) && group.has(key)) {
                            try {
                                task.put(key, group.get(key));
                            } catch (JSONException ignored) {
                            }
                        }
                    }
                    putTask(tasks, task);
                }
            }
        }
        putTask(tasks, module.optJSONObject("checkInRecommendTask"));
        return new ArrayList<>(tasks.values());
    }

    private void putTask(Map<String, JSONObject> tasks, JSONObject task) {
        if (task == null) {
            return;
        }
        String taskCode = task.optString("taskCode");
        String taskSource = task.optString("taskSource");
        String taskType = task.optString("taskType");
        if (taskCode.isEmpty() || taskSource.isEmpty() || taskType.isEmpty()) {
            Log.i(TAG, "青春特权任务缺少服务端执行参数:" + task);
            return;
        }
        if (!tasks.containsKey(taskCode)) {
            tasks.put(taskCode, task);
        }
    }

    /** 返回 true 表示本轮确实提交了动作 */
    private boolean handleTask(JSONObject task) {
        String taskCode = task.optString("taskCode");
        String taskName = task.optString("taskName");
        if (taskName.isEmpty()) {
            taskName = taskCode;
        }
        String taskStatus = task.optString("taskStatus");
        String taskType = task.optString("taskType");
        String taskAction = task.optString("taskAction");

        if (STATUS_COMPLETE.equals(taskStatus) || TASK_ACTION_DO_NOTHING.equals(taskAction)) {
            return false;
        }
        if (!TASK_TYPE_BROWSER.equals(taskType) && !TASK_TYPE_FEEDS.equals(taskType)) {
            Log.i("青春特权🎯[跳过非浏览任务]" + taskName + "#taskType=" + taskType + " status=" + taskStatus);
            return false;
        }
        String method;
        String actionName;
        if (STATUS_TO_APPLY.equals(taskStatus) || TASK_ACTION_SIGNUP.equals(taskAction)) {
            method = "taskSignUp";
            actionName = "报名";
        } else if (STATUS_PROCESSING.equals(taskStatus) || TASK_ACTION_COMPLETE.equals(taskAction)) {
            method = "taskComplete";
            actionName = "完成";
        } else {
            return false;
        }

        JSONObject payload = new JSONObject();
        try {
            payload.put("taskCode", taskCode);
            payload.put("taskSource", task.optString("taskSource"));
            payload.put("taskType", taskType);
        } catch (JSONException ignored) {
        }
        JSONObject result = rpc(PREFIX_YOUTH, method, payload);
        if (isYouthSuccess(result)) {
            Log.i("青春特权🎯[" + taskName + "]" + actionName + "已提交，等待回查");
            TimeUtil.sleep(300);
            return true;
        }
        Log.i(TAG, "青春特权任务[" + taskName + "]" + actionName + "失败:" + result);
        return false;
    }

    private boolean handleFeeds(JSONObject response) {
        JSONObject feeds = response.optJSONObject("feedsTaskVO");
        if (feeds == null) {
            return false;
        }
        String status = feeds.optString("feedsTaskStatus");
        if ("FINISH".equals(status) || STATUS_COMPLETE.equals(status)) {
            return false;
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("bizId", "DO_FEEDS_TASK");
            payload.put("sceneCode", SCENE_CODE);
        } catch (JSONException ignored) {
        }
        JSONObject result = rpc(PREFIX_STUDENT, "triggerPointPrize", payload);
        if (isYouthSuccess(result)) {
            Log.forest("青春特权🎯[滑动浏览15秒]已提交，等待回查");
            return true;
        }
        Log.i(TAG, "青春特权[滑动浏览15秒]提交失败:" + result);
        return false;
    }

    // -------------------------------------------------------- 青春体验金

    private void claimTrialPrize() {
        try {
            String playEntrance = "YEB_YONG_TYJ_PROMO";
            SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            String today = dayFormat.format(new Date());
            JSONObject query = rpc(PREFIX_YEB, "query", new JSONObject()
                    .put("playEntrance", playEntrance)
                    .put("playActionCode", "CAMP_DAY_QUERY")
                    .put("startTime", today + " 00:00:00")
                    .put("endTime", today + " 23:59:59"));
            if (!query.optBoolean("success")) {
                Log.i(TAG, "青春体验金查询失败:" + query);
                return;
            }
            if (hasConfirmedAward(query.optJSONArray("result"))) {
                Log.i("青春特权🎁青春体验金当日发放已确认");
                return;
            }
            if (query.optJSONArray("result") != null && query.optJSONArray("result").length() > 0) {
                Log.i("青春特权🎁青春体验金当日已有订单但券状态未确认，保留后续查询");
                return;
            }
            JSONObject trigger = rpc(PREFIX_YEB, "trigger", new JSONObject()
                    .put("playEntrance", playEntrance)
                    .put("playActionCode", "CAMP_TRIGGER"));
            if (!trigger.optBoolean("success")) {
                Log.i(TAG, "青春体验金领取失败，保留后续查询:" + trigger);
                return;
            }
            JSONObject after = rpc(PREFIX_YEB, "query", new JSONObject()
                    .put("playEntrance", playEntrance)
                    .put("playActionCode", "CAMP_DAY_QUERY")
                    .put("startTime", today + " 00:00:00")
                    .put("endTime", today + " 23:59:59"));
            if (hasConfirmedAward(after.optJSONArray("result"))) {
                Log.forest("青春特权🎁青春体验金领取确认到账");
            } else {
                Log.i("青春特权🎁青春体验金已触发，尚未确认到账");
            }
        } catch (Throwable t) {
            Log.i(TAG, "青春体验金处理异常:" + t);
        }
    }

    private boolean hasConfirmedAward(JSONArray awards) {
        if (awards == null) {
            return false;
        }
        for (int i = 0; i < awards.length(); i++) {
            JSONObject award = awards.optJSONObject(i);
            if (award == null) {
                continue;
            }
            if (!SUCCESS.equals(award.optString("sendStatus")) || award.optString("sendOrderId").isEmpty()) {
                continue;
            }
            JSONArray vouchers = award.optJSONArray("voucherInfo");
            if (vouchers == null) {
                continue;
            }
            for (int j = 0; j < vouchers.length(); j++) {
                JSONObject voucher = vouchers.optJSONObject(j);
                if (voucher != null && !voucher.optString("voucher_id").isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void doMonthlyPrivilege() {
        // AG 用 youth100.homepage.query 拉「青春每月理财福利」，参数里必须有 adCode（城市码）。
        // GR 没有定位能力，这里明确提示跳过，避免用错城市码触发风控。
        Log.i("青春特权💳青春每月理财福利需要城市定位，本移植未启用");
    }

    // ------------------------------------------------------- 青春豆十连抽

    private void doMultiDraws() {
        if (Status.hasFlagToday(FLAG_MULTI_DRAWS)) {
            Log.i("青春特权🎰青春豆十连抽今日已处理，跳过");
            return;
        }
        JSONObject index = rpc(PREFIX_STUDENT, "queryLotteryIndex", json("sceneCode", SCENE_CODE));
        if (!isYouthSuccess(index)) {
            Log.i(TAG, "青春豆十连抽首页查询失败:" + index);
            return;
        }
        String status = index.optString("multiDrawsLotteryStatus");
        if ("LIMIT".equals(status)) {
            Log.i("青春特权🎰青春豆十连抽额度已用完#余额=" + index.optString("totalAmount"));
            return;
        }
        if (!"DRAW".equals(status)) {
            Log.i("青春特权🎰青春豆十连抽当前不可执行#status=" + status);
            return;
        }
        java.math.BigDecimal cost = toDecimal(index.optString("multiDrawsCost"));
        java.math.BigDecimal balance = toDecimal(index.optString("totalAmount"));
        if (cost == null || balance == null || cost.signum() <= 0) {
            Log.i(TAG, "青春豆十连抽缺少有效成本或余额:" + index);
            return;
        }
        if (balance.compareTo(cost) < 0) {
            Log.i("青春特权🎰青春豆十连抽余额不足#余额=" + balance + " 成本=" + cost);
            return;
        }
        // 付费动作没有幂等键：先置标记，避免同一天重复扣青春豆
        Status.flagToday(FLAG_MULTI_DRAWS);
        JSONObject result = rpc(PREFIX_STUDENT, "multiDrawsLottery", json(
                "sceneCode", SCENE_CODE,
                "systemVersion", android.os.Build.VERSION.RELEASE));
        if (isYouthSuccess(result)) {
            JSONArray prizes = result.optJSONArray("lotteryPrizeInfoList");
            if (prizes == null) {
                Log.i(TAG, "青春豆十连抽响应缺少实际奖励列表:" + result);
            } else {
                for (int i = 0; i < prizes.length(); i++) {
                    JSONObject prize = prizes.optJSONObject(i);
                    if (prize == null) {
                        continue;
                    }
                    Log.forest("青春特权🎰十连抽🎁[" + prize.optString("title") + "] " + prize.optString("subTitle"));
                }
            }
        } else {
            Log.i(TAG, "青春豆十连抽失败，不立即重试:" + result);
        }
        JSONObject confirmed = rpc(PREFIX_STUDENT, "queryLotteryIndex", json("sceneCode", SCENE_CODE));
        if (!isYouthSuccess(confirmed)) {
            Log.i(TAG, "青春豆十连抽回查失败:" + confirmed);
            return;
        }
        if ("LIMIT".equals(confirmed.optString("multiDrawsLotteryStatus"))) {
            Log.i("青春特权🎰十连抽额度已确认消耗，剩余余额=" + confirmed.optString("totalAmount"));
        } else {
            Log.i(TAG, "青春豆十连抽额度尚未确认变化，不重复抽取:" + confirmed);
        }
    }

    private java.math.BigDecimal toDecimal(String text) {
        try {
            if (text == null || text.isEmpty() || "null".equals(text)) {
                return null;
            }
            return new java.math.BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 工具

    /** 顺序拼接 k/v 成 JSONObject，避免每处都写 try/catch(JSONException) */
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

    private JSONObject rpc(String prefix, String method, JSONObject payload) {
        try {
            return new JSONObject(ApplicationHook.requestString(prefix + method, new JSONArray().put(payload).toString()));
        } catch (Throwable t) {
            Log.i(TAG, "RPC异常 " + method + ":" + t);
            try {
                return new JSONObject("{\"success\":false}");
            } catch (JSONException ignored) {
                return new JSONObject();
            }
        }
    }

    private boolean isYouthSuccess(JSONObject response) {
        return response != null
                && response.optBoolean("success")
                && SUCCESS.equals(response.optString("resultCode"));
    }
}