package io.github.Dragon813.sesame.model.task.antForest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.Dragon813.sesame.util.Log;
import io.github.Dragon813.sesame.util.MessageUtil;
import io.github.Dragon813.sesame.util.Status;
import io.github.Dragon813.sesame.util.TimeUtil;
import io.github.Dragon813.sesame.util.idMap.UserIdMap;

/**
 * 翻倍卡路线计算与自动使用。
 *
 * <p>规则复刻自开源项目
 * <a href="https://github.com/Notatat/multiplier-card-calculator">multiplier-card-calculator</a>
 * (MIT)：
 * <ul>
 *   <li>每天最多替换 10 次；</li>
 *   <li>使用下一张卡前，当前倍率时长须不超过该倍率的接替前限制
 *       （1.1倍770天 / 1.2倍390天 / 1.3倍263天 / 1.5倍162天 / 2倍45天）；</li>
 *   <li>相同倍率时长超过 45 天不能再叠加同倍率卡；</li>
 *   <li>2 倍卡单张最多 7 天，2 倍终点默认最多 88 天；</li>
 *   <li>换倍率时能量守恒：新时长 = 原时长 × (原倍率−1) ÷ (新倍率−1) + 卡片时长。</li>
 * </ul>
 *
 * <p>内部用整数能量表示时长：energy = minutes × 10 × (倍率−1)，
 * 换倍率只改变 rate，能量保持不变。
 */
final class MultiplierCardPlanner {

    private static final String TAG = MultiplierCardPlanner.class.getSimpleName();

    private static final int DAY = 1440;
    private static final int[] ALLOWED_RATES = {1, 2, 3, 5, 10};
    private static final int[] ALLOWED_MINUTES = {5, DAY, 3 * DAY, 7 * DAY, 14 * DAY};

    public static final int MODE_FINAL_DOUBLE = 0;
    public static final int MODE_DAILY_DOUBLE = 1;
    public static final int MODE_LONGEST = 2;

    /** 工具预置的 2 倍卡最长 7 天 */
    private static final int DOUBLE_CARD_MAX_MINUTES = 7 * DAY;
    /** 同倍率卡叠加上限（天），工具规则固定值 */
    private static final int SAME_RATE_MAX_DAYS = 45;

    private static final int MAX_VISITED = 300000;
    private static final long MAX_SEARCH_MILLIS = 6000L;

    /**
     * consumeProp 的 resData.usePropStatus。
     * 先以 secondConfirm=false 预检：NORM_CONSUME_PROP 表示已直接生效；
     * NEED_CONFIRM_* 表示需要再次以 secondConfirm=true 确认，确认后返回 REPLACE / PROLONG。
     */
    private static final String USE_STATUS_NORM = "NORM_CONSUME_PROP";
    private static final String USE_STATUS_NEED_REPLACE = "NEED_CONFIRM_CAN_REPLACE";
    private static final String USE_STATUS_NEED_PROLONG = "NEED_CONFIRM_CAN_PROLONG";
    private static final String USE_STATUS_REPLACE = "REPLACE";
    private static final String USE_STATUS_PROLONG = "PROLONG";

    /** resData.resultCode：当日使用次数已达到上限 */
    private static final String RESULT_DAILY_LIMIT = "DAILY_USE_LIMIT";

    /**
     * 命中 1009 安全验证时，等待自动验证完成后再重发次数。
     * 抓包中验证完成后重新提交即可成功，失败的那次不会消耗卡片和次数。
     */
    private static final int VERIFY_RETRY_TIMES = 2;
    private static final long VERIFY_WAIT_MILLIS = 8000L;

    private static final Pattern PROP_TYPE_FACTOR = Pattern.compile("ROB_EXPAND_CARD_([0-9.]+)");
    private static final Pattern PROP_TYPE_DAYS = Pattern.compile("_(\\d+)DAYS?");
    private static final Pattern PROP_TYPE_MINUTES = Pattern.compile("_(\\d+)MIN");

    private MultiplierCardPlanner() {
    }

    /** 小工具要求的用户配置 */
    static final class Config {
        final int mode;
        /** 0 表示不限 */
        final int maxRouteDays;
        final int dailyLimit;
        final int rate11Days;
        final int rate12Days;
        final int rate13Days;
        final int rate15Days;
        final int doubleBeforeDays;
        final int doubleEndDays;

        Config(int mode, int maxRouteDays, int dailyLimit,
               int rate11Days, int rate12Days, int rate13Days, int rate15Days,
               int doubleBeforeDays, int doubleEndDays) {
            this.mode = mode;
            this.maxRouteDays = maxRouteDays;
            // 小于10按设置的实际次数；大于10按服务端上限10
            this.dailyLimit = dailyLimit <= 0 ? 10 : Math.min(dailyLimit, 10);
            this.rate11Days = rate11Days;
            this.rate12Days = rate12Days;
            this.rate13Days = rate13Days;
            this.rate15Days = rate15Days;
            this.doubleBeforeDays = doubleBeforeDays;
            this.doubleEndDays = doubleEndDays;
        }
    }

    /** 当前正在生效的倍卡状态 */
    private static final class CurrentState {
        final int rate;
        final long minutes;

        CurrentState(int rate, long minutes) {
            this.rate = rate;
            this.minutes = minutes;
        }
    }

    /** 库存里的一张卡（同倍率同时长可能来自多个道具条目） */
    private static final class PropItem {
        final String propId;
        final String propType;
        final String propName;
        /** 0 表示背包里长期有效（不限时） */
        final long expireTime;

        PropItem(String propId, String propType, String propName, long expireTime) {
            this.propId = propId;
            this.propType = propType;
            this.propName = propName;
            this.expireTime = expireTime;
        }
    }

    private static final class CardStock {
        final int minutes;
        final int rate;
        long count;
        final List<PropItem> props = new ArrayList<>();

        CardStock(int minutes, int rate) {
            this.minutes = minutes;
            this.rate = rate;
        }

        String key() {
            return minutes + ":" + rate;
        }

        boolean hasExpiring() {
            for (PropItem item : props) {
                if (item.expireTime > 0) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class Inventory {
        final List<CardStock> cards = new ArrayList<>();
        final Map<String, CardStock> index = new HashMap<>();

        CardStock add(int minutes, int rate, long count, PropItem item) {
            String key = minutes + ":" + rate;
            CardStock stock = index.get(key);
            if (stock == null) {
                stock = new CardStock(minutes, rate);
                index.put(key, stock);
                cards.add(stock);
            }
            stock.count += count;
            if (item != null) {
                stock.props.add(item);
            }
            return stock;
        }

        CardStock find(int minutes, int rate) {
            return index.get(minutes + ":" + rate);
        }

        boolean isEmpty() {
            for (CardStock stock : cards) {
                if (stock.count > 0) {
                    return false;
                }
            }
            return true;
        }

        void sortProps() {
            for (CardStock stock : cards) {
                // 优先使用限时（背包会过期）的卡，最早过期的排最前；不限时卡排最后
                Collections.sort(stock.props, new Comparator<PropItem>() {
                    @Override
                    public int compare(PropItem a, PropItem b) {
                        boolean ae = a.expireTime > 0;
                        boolean be = b.expireTime > 0;
                        if (ae != be) {
                            return ae ? -1 : 1;
                        }
                        if (ae) {
                            return Long.compare(a.expireTime, b.expireTime);
                        }
                        return 0;
                    }
                });
            }
        }
    }

    private static final class Step {
        final int cardIndex;
        final int cardMinutes;
        final int cardRate;
        final long resultMinutes;
        final int day;

        Step(int cardIndex, int cardMinutes, int cardRate, long resultMinutes, int day) {
            this.cardIndex = cardIndex;
            this.cardMinutes = cardMinutes;
            this.cardRate = cardRate;
            this.resultMinutes = resultMinutes;
            this.day = day;
        }
    }

    private static final class Plan {
        final boolean found;
        final boolean complete;
        final String reason;
        final List<Step> route;
        final long finalMinutes;
        final int targetRate;
        final long visited;

        Plan(boolean found, boolean complete, String reason, List<Step> route,
             long finalMinutes, int targetRate, long visited) {
            this.found = found;
            this.complete = complete;
            this.reason = reason;
            this.route = route;
            this.finalMinutes = finalMinutes;
            this.targetRate = targetRate;
            this.visited = visited;
        }
    }

    private static final class Node {
        final int[] used;
        final int rate;
        final long energy;
        final long used15Energy;
        final int usedDoubleCount;
        final Node parent;
        final int card;
        final int depth;
        final int day;
        final int daySteps;
        final int waste;
        final int[] doubleDays;

        Node(int[] used, int rate, long energy, long used15Energy, int usedDoubleCount,
             Node parent, int card, int depth, int day, int daySteps, int waste, int[] doubleDays) {
            this.used = used;
            this.rate = rate;
            this.energy = energy;
            this.used15Energy = used15Energy;
            this.usedDoubleCount = usedDoubleCount;
            this.parent = parent;
            this.card = card;
            this.depth = depth;
            this.day = day;
            this.daySteps = daySteps;
            this.waste = waste;
            this.doubleDays = doubleDays;
        }
    }

    static Config defaultConfig(int mode, int maxRouteDays, int dailyLimit,
                                int rate11Days, int rate12Days, int rate13Days, int rate15Days,
                                int doubleBeforeDays, int doubleEndDays) {
        return new Config(mode, maxRouteDays, dailyLimit,
                rate11Days, rate12Days, rate13Days, rate15Days,
                doubleBeforeDays, doubleEndDays);
    }

    /**
     * 读取 RPC 状态 -> 计算最优路线 -> 按路线自动使用今天的份额。
     */
    static void planAndUse(Config config) {
        try {
            String countTag = "forest::robExpandCardPlan::" + UserIdMap.getCurrentUid();
            // 跨天先触发状态重置并刷新 saveTime，避免首次计数被 unload 清掉
            Status.save();
            int usedToday = Status.getCountToday(countTag);
            int quota = config.dailyLimit - usedToday;
            if (quota <= 0) {
                Log.record("倍卡规划🧮 今日已使用" + usedToday + "次，达到上限" + config.dailyLimit + "次");
                return;
            }

            CurrentState current = queryCurrentState();
            Inventory inventory = queryInventory();
            if (inventory.isEmpty()) {
                Log.record("倍卡规划🧮 背包无可用翻倍卡，跳过");
                return;
            }

            String currentText = current.rate <= 0
                    ? "未使用"
                    : formatFactor(current.rate) + "倍 剩余" + formatMinutes(current.minutes);
            Log.record("倍卡规划🧮 今日已用" + usedToday + "/" + config.dailyLimit
                    + "次 当前[" + currentText + "] 库存[" + describeInventory(inventory) + "]");

            Plan plan = solve(current, inventory, config);
            if (!plan.found) {
                Log.record("倍卡规划🧮 无可行路线(" + reasonText(plan.reason) + ")");
                return;
            }
            if (plan.route.isEmpty()) {
                Log.record("倍卡规划🧮 当前[" + currentText + "]已达到规则上限，保持现状");
                return;
            }

            StringBuilder routeText = new StringBuilder();
            for (Step step : plan.route) {
                if (routeText.length() > 0) {
                    routeText.append(" → ");
                }
                routeText.append(cardLabel(step.cardMinutes, step.cardRate));
            }
            Log.record("倍卡规划🧮 路线[" + routeText + "]#终点["
                    + formatMinutes(plan.finalMinutes) + formatFactor(plan.targetRate) + "倍]"
                    + (plan.complete ? "" : "(未搜完)"));
            Log.record("倍卡规划🧮 搜索状态" + plan.visited);

            int executed = 0;
            String beforeState = currentText;
            for (Step step : plan.route) {
                // 只执行第一天的份额，第二天开始由下一次运行重新计算
                if (step.day != 1) {
                    break;
                }
                if (executed >= quota) {
                    break;
                }
                CardStock fresh = queryInventory().find(step.cardMinutes, step.cardRate);
                if (fresh == null || fresh.props.isEmpty()) {
                    Log.record("倍卡规划🧮 库存已无[" + cardLabel(step.cardMinutes, step.cardRate) + "]，停止");
                    break;
                }
                PropItem item = fresh.props.get(0);
                if (item.propId == null || item.propId.isEmpty()) {
                    Log.record("倍卡规划🧮 卡片缺少propId，停止");
                    break;
                }

                JSONObject result = consumeWithVerifyRetry(item, false);
                if (result == null) {
                    break;
                }
                if (handleDailyLimit(result, countTag, config)) {
                    break;
                }
                if (MessageUtil.isSecurityVerify(result)) {
                    Log.record("倍卡规划🧮 使用[" + item.propName + "]安全验证未完成，今日停止使用");
                    break;
                }
                if (!isSuccess(result)) {
                    Log.record("倍卡规划🧮 使用[" + item.propName + "]失败(" + failureText(result) + ")，停止今日规划");
                    break;
                }

                String status = result.optString("usePropStatus");
                if (USE_STATUS_NEED_REPLACE.equals(status) || USE_STATUS_NEED_PROLONG.equals(status)) {
                    long prolongMillis = result.optLong("prolongTimeSeconds") * 1000L;
                    Log.record("倍卡规划🧮 使用[" + item.propName + "]"
                            + (USE_STATUS_NEED_REPLACE.equals(status)
                            ? "将替换当前倍卡"
                            : "将续期" + formatMinutes(prolongMillis / 60000L))
                            + "，自动确认");
                    result = consumeWithVerifyRetry(item, true);
                    if (result == null) {
                        break;
                    }
                    if (handleDailyLimit(result, countTag, config)) {
                        break;
                    }
                    if (MessageUtil.isSecurityVerify(result)) {
                        Log.record("倍卡规划🧮 确认使用[" + item.propName + "]安全验证未完成，今日停止使用");
                        break;
                    }
                    if (!isSuccess(result)) {
                        Log.record("倍卡规划🧮 确认使用[" + item.propName + "]失败("
                                + failureText(result) + ")，停止今日规划");
                        break;
                    }
                    status = result.optString("usePropStatus");
                }

                if (!USE_STATUS_NORM.equals(status)
                        && !USE_STATUS_REPLACE.equals(status)
                        && !USE_STATUS_PROLONG.equals(status)) {
                    Log.record("倍卡规划🧮 使用[" + item.propName
                            + "]返回未识别状态[" + status + "]，放弃本次使用");
                    break;
                }

                Status.incrCountToday(countTag);
                executed++;
                String afterState = resultStateText(result);
                Log.forest("使用道具🎭[" + item.propName + "]"
                        + "#" + statusText(status)
                        + (afterState.isEmpty() ? "" : "#时长[" + beforeState + "]→[" + afterState + "]"));
                if (!afterState.isEmpty()) {
                    beforeState = afterState;
                }
                TimeUtil.sleep(600);
            }

            if (executed == 0) {
                Log.record("倍卡规划🧮 本次未使用卡片");
            } else {
                Log.record("倍卡规划🧮 本次使用" + executed + "张，今日累计"
                        + Status.getCountToday(countTag) + "/" + config.dailyLimit);
            }
        } catch (Throwable th) {
            Log.i(TAG, "planAndUse err:");
            Log.printStackTrace(TAG, th);
        }
    }

    // ------------------------------------------------------------------
    // RPC 状态读取
    // ------------------------------------------------------------------

    /**
     * 提交用卡 RPC。返回 resData（已去掉外层包裹），失败返回 null。
     *
     * <p>抓包显示官方客户端为两次调用：
     * secondConfirm=false 预检，若返回 NEED_CONFIRM_* 再以 secondConfirm=true 确认。
     */
    private static JSONObject consumeProp(PropItem item, boolean secondConfirm) {
        try {
            return unwrap(new JSONObject(AntForestRpcCall.consumeProp(
                    "robExpandCard", item.propId, item.propType, secondConfirm)));
        } catch (Throwable th) {
            Log.i(TAG, "consumeProp err:");
            Log.printStackTrace(TAG, th);
            return null;
        }
    }

    /**
     * 1009 是弹窗验证（自动验证开启时由 PuzzleCaptchaHook 异步完成），
     * 验证成功后重新提交同一张卡即可生效，失败的那次不计次数。
     */
    private static JSONObject consumeWithVerifyRetry(PropItem item, boolean secondConfirm) {
        JSONObject result = consumeProp(item, secondConfirm);
        for (int retry = 1; retry <= VERIFY_RETRY_TIMES && MessageUtil.isSecurityVerify(result); retry++) {
            Log.record("倍卡规划🧮 使用[" + item.propName + "]需要安全验证，等待验证后重试("
                    + retry + "/" + VERIFY_RETRY_TIMES + ")");
            TimeUtil.sleep(VERIFY_WAIT_MILLIS);
            result = consumeProp(item, secondConfirm);
        }
        return result;
    }

    private static boolean isSuccess(JSONObject result) {
        return result != null
                && (result.optBoolean("success") || "SUCCESS".equals(result.optString("resultCode")));
    }

    /** 服务端当日次数上限：本地计数直接对齐上限，避免当天继续尝试。 */
    private static boolean handleDailyLimit(JSONObject result, String countTag, Config config) {
        if (!RESULT_DAILY_LIMIT.equals(result.optString("resultCode"))
                && !result.optString("resultDesc").contains("次数已达到上限")) {
            return false;
        }
        Status.setCountToday(countTag, config.dailyLimit);
        Log.record("倍卡规划🧮 服务端返回[" + result.optString("resultDesc", "当日使用次数已达到上限")
                + "]，今日停止使用");
        return true;
    }

    private static String statusText(String status) {
        if (USE_STATUS_NORM.equals(status)) {
            return "已生效";
        }
        if (USE_STATUS_REPLACE.equals(status)) {
            return "替换成功";
        }
        if (USE_STATUS_PROLONG.equals(status)) {
            return "续期成功";
        }
        return "成功";
    }

    /** 用卡结果里新倍卡的剩余时长与倍率，例如“2倍 剩余88天” */
    private static String resultStateText(JSONObject result) {
        JSONObject prop = result.optJSONObject("userPropVO");
        if (prop == null) {
            return "";
        }
        long remainMillis = prop.optLong("endTime") - System.currentTimeMillis();
        if (remainMillis <= 0) {
            return "";
        }
        String remain = formatMinutes(remainMillis / 60000L);
        double factor = parseFactor(prop.optJSONObject("detail"), prop.optString("propType"));
        int rate = rateOf(factor);
        return (rate > 0 ? formatFactor(rate) + "倍 " : "") + "剩余" + remain;
    }

    private static String failureText(JSONObject result) {
        String text = result.optString("resultDesc", "");
        if (text.isEmpty()) {
            text = result.optString("errorMessage", "");
        }
        return text.isEmpty() ? result.toString() : text;
    }

    private static CurrentState queryCurrentState() {
        try {
            JSONObject jo = unwrap(new JSONObject(AntForestRpcCall.queryMiscInfo()));
            JSONObject combine = jo.optJSONObject("combineHandlerVOMap");
            if (combine == null) {
                return new CurrentState(0, 0);
            }
            JSONObject usingProp = combine.optJSONObject("usingProp");
            if (usingProp == null) {
                return new CurrentState(0, 0);
            }
            JSONArray userPropVOS = usingProp.optJSONArray("userPropVOS");
            if (userPropVOS == null) {
                return new CurrentState(0, 0);
            }
            long now = System.currentTimeMillis();
            for (int i = 0; i < userPropVOS.length(); i++) {
                JSONObject prop = userPropVOS.optJSONObject(i);
                if (prop == null || !"robExpandCard".equals(prop.optString("propGroup"))) {
                    continue;
                }
                long endTime = prop.optLong("endTime");
                long remainMillis = endTime - now;
                if (remainMillis <= 0) {
                    continue;
                }
                double factor = parseFactor(prop.optJSONObject("detail"), prop.optString("propType"));
                int rate = rateOf(factor);
                if (rate <= 0) {
                    continue;
                }
                return new CurrentState(rate, remainMillis / 60000L);
            }
        } catch (Throwable th) {
            Log.i(TAG, "queryCurrentState err:");
            Log.printStackTrace(TAG, th);
        }
        return new CurrentState(0, 0);
    }

    private static Inventory queryInventory() {
        Inventory inventory = new Inventory();
        try {
            JSONObject jo = unwrap(new JSONObject(AntForestRpcCall.queryPropList(false)));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return inventory;
            }
            JSONArray forestPropVOList = jo.optJSONArray("forestPropVOList");
            if (forestPropVOList == null) {
                return inventory;
            }
            for (int i = 0; i < forestPropVOList.length(); i++) {
                JSONObject entry = forestPropVOList.optJSONObject(i);
                if (entry == null || !"robExpandCard".equals(entry.optString("propGroup"))) {
                    continue;
                }
                int holdsNum = entry.optInt("holdsNum");
                if (holdsNum <= 0) {
                    continue;
                }
                JSONObject config = entry.optJSONObject("propConfigVO");
                String propType = entry.optString("propType");
                double factor = parseFactor(config == null ? null : config.optJSONObject("detail"), propType);
                int rate = rateOf(factor);
                if (rate <= 0) {
                    continue;
                }
                int minutes = parseCardMinutes(propType, config);
                if (!isAllowedMinutes(minutes)) {
                    continue;
                }
                // 工具规则：2倍卡最长7天，不允许14天2倍卡
                if (rate == 10 && minutes > DOUBLE_CARD_MAX_MINUTES) {
                    continue;
                }
                long expireTime = entry.optLong("recentExpireTime", 0L);
                String propName = config == null ? cardLabel(minutes, rate) : config.optString("propName", cardLabel(minutes, rate));
                JSONArray propIdList = entry.optJSONArray("propIdList");
                String propId = "";
                if (propIdList != null && propIdList.length() > 0) {
                    propId = propIdList.optString(0, "");
                }
                inventory.add(minutes, rate, holdsNum, new PropItem(propId, propType, propName, expireTime));
            }
        } catch (Throwable th) {
            Log.i(TAG, "queryInventory err:");
            Log.printStackTrace(TAG, th);
        }
        inventory.sortProps();
        return inventory;
    }

    private static double parseFactor(JSONObject detail, String propType) {
        if (detail != null) {
            try {
                double factor = Double.parseDouble(detail.optString("factor", ""));
                if (factor > 0) {
                    return factor;
                }
            } catch (Throwable ignored) {
                // 回退到 propType
            }
        }
        if (propType != null) {
            Matcher matcher = PROP_TYPE_FACTOR.matcher(propType);
            if (matcher.find()) {
                try {
                    return Double.parseDouble(matcher.group(1));
                } catch (Throwable ignored) {
                    // ignore
                }
            }
        }
        return 0;
    }

    private static int parseCardMinutes(String propType, JSONObject config) {
        // 抓包里 SHAMO_ROB_EXPAND_CARD_1.3_1DAYS 的实际 durationTime 是 300 秒（5分钟），
        // propType 里的天数并不可靠，优先使用服务端下发的 durationTime。
        if (config != null) {
            int durationSeconds = config.optInt("durationTime");
            if (durationSeconds > 0 && durationSeconds % 60 == 0) {
                return durationSeconds / 60;
            }
        }
        if (propType != null) {
            Matcher days = PROP_TYPE_DAYS.matcher(propType);
            if (days.find()) {
                try {
                    return Integer.parseInt(days.group(1)) * DAY;
                } catch (Throwable ignored) {
                    // ignore
                }
            }
            Matcher minutes = PROP_TYPE_MINUTES.matcher(propType);
            if (minutes.find()) {
                try {
                    return Integer.parseInt(minutes.group(1));
                } catch (Throwable ignored) {
                    // ignore
                }
            }
        }
        return -1;
    }

    private static boolean isAllowedMinutes(int minutes) {
        for (int allowed : ALLOWED_MINUTES) {
            if (allowed == minutes) {
                return true;
            }
        }
        return false;
    }

    private static int rateOf(double factor) {
        if (factor <= 0) {
            return 0;
        }
        int rate = (int) Math.round((factor - 1) * 10);
        for (int allowed : ALLOWED_RATES) {
            if (allowed == rate) {
                return rate;
            }
        }
        return 0;
    }

    private static JSONObject unwrap(JSONObject jo) {
        if (jo == null) {
            return null;
        }
        JSONObject resData = jo.optJSONObject("resData");
        return resData == null ? jo : resData;
    }

    // ------------------------------------------------------------------
    // 路线搜索（solver.js 移植）
    // ------------------------------------------------------------------

    private static Plan solve(CurrentState current, Inventory inventory, Config config) {
        int n = inventory.cards.size();
        int[] cardMinutes = new int[n];
        int[] cardRate = new int[n];
        long[] counts = new long[n];
        boolean[] expiring = new boolean[n];
        for (int i = 0; i < n; i++) {
            CardStock stock = inventory.cards.get(i);
            cardMinutes[i] = stock.minutes;
            cardRate[i] = stock.rate;
            counts[i] = stock.count;
            expiring[i] = stock.hasExpiring();
        }

        long totalDoubleStock = 0;
        for (int i = 0; i < n; i++) {
            if (cardRate[i] == 10) {
                totalDoubleStock += counts[i];
            }
        }
        boolean hasDouble = totalDoubleStock > 0;
        int initialRate = current.rate > 0 ? current.rate : 10;
        long initialEnergy = current.minutes * initialRate;
        boolean initialReplacementBlocked = beforeLimit(initialEnergy, initialRate, config);
        boolean initialLocked = initialRate == 10 && initialReplacementBlocked;
        int targetRate = (hasDouble || initialLocked) ? 10 : 5;

        double maxBeforeEnergy = 0;
        for (int rate : ALLOWED_RATES) {
            maxBeforeEnergy = Math.max(maxBeforeEnergy, limitHundredths(rate, config) * (double) DAY * rate / 100.0);
        }
        long lastTargetCardEnergy = 0;
        for (int i = 0; i < n; i++) {
            if (cardRate[i] == targetRate && counts[i] > 0) {
                lastTargetCardEnergy = Math.max(lastTargetCardEnergy, (long) cardMinutes[i] * cardRate[i]);
            }
        }
        double cap;
        if (targetRate == 10) {
            cap = Math.round(config.doubleEndDays * 100.0) * (double) DAY * 10 / 100.0;
        } else {
            cap = Math.max(initialEnergy, maxBeforeEnergy + lastTargetCardEnergy);
        }

        if (initialEnergy > cap) {
            return new Plan(false, true, initialLocked ? "locked-above-limit" : "energy-limit",
                    new ArrayList<Step>(), 0, targetRate, 0);
        }

        final int[] order = buildOrder(cardMinutes, cardRate, expiring);
        final boolean daily = config.mode == MODE_DAILY_DOUBLE;
        final boolean finalDouble = config.mode == MODE_FINAL_DOUBLE;
        final int maxRouteDays = config.maxRouteDays;

        Node initial = new Node(new int[n], initialRate, initialEnergy, 0, 0,
                null, -1, 0, 0, 0, 0, new int[0]);
        ArrayDeque<Node> stack = new ArrayDeque<>();
        stack.push(initial);
        Set<String> seen = new HashSet<>();
        Map<String, int[]> seenDaily = new HashMap<>();
        Node best = null;
        long visited = 0;
        boolean complete = true;
        long start = System.currentTimeMillis();

        while (!stack.isEmpty()) {
            Node state = stack.pop();
            String key = Arrays.toString(state.used) + ":" + state.rate + (daily ? ":" + state.waste : "");
            if (daily) {
                int[] prior = seenDaily.get(key);
                if (prior != null && !laterDoubleDays(state.doubleDays, prior)) {
                    continue;
                }
                seenDaily.put(key, state.doubleDays);
            } else {
                if (seen.contains(key)) {
                    continue;
                }
                seen.add(key);
            }
            visited++;
            if (isEligible(state, targetRate, maxRouteDays, daily, hasDouble, initialLocked,
                    totalDoubleStock, cardRate) && isBetter(state, best, daily)) {
                best = state;
            }
            if (visited % 10000 == 0 && System.currentTimeMillis() - start > MAX_SEARCH_MILLIS) {
                complete = false;
                break;
            }
            if (visited >= MAX_VISITED) {
                complete = false;
                break;
            }

            if (finalDouble && state.usedDoubleCount > 0) {
                continue;
            }
            // 每个接替前限制都针对使用下一张卡之前的当前状态
            if (beforeLimit(state.energy, state.rate, config)) {
                continue;
            }
            if (targetRate == 10 && state.rate != 10 && state.usedDoubleCount == totalDoubleStock) {
                continue;
            }

            long remainingEnergy = 0;
            long remaining15Energy = 0;
            long bestDoubleEnergy = 0;
            for (int i = 0; i < n; i++) {
                long left = counts[i] - state.used[i];
                if (left <= 0) {
                    continue;
                }
                long unitEnergy = (long) cardMinutes[i] * cardRate[i];
                if (finalDouble && cardRate[i] == 10) {
                    if (state.usedDoubleCount == 0) {
                        bestDoubleEnergy = Math.max(bestDoubleEnergy, unitEnergy);
                    }
                } else {
                    remainingEnergy += left * unitEnergy;
                }
                if (cardRate[i] == 5) {
                    remaining15Energy += left * unitEnergy;
                }
            }
            remainingEnergy += bestDoubleEnergy;

            if (best != null) {
                double upper = Math.min(cap, state.energy + remainingEnergy);
                if (upper < best.energy) {
                    continue;
                }
                if (upper == best.energy) {
                    long max15 = state.used15Energy + remaining15Energy;
                    if (max15 < best.used15Energy || (max15 == best.used15Energy && state.depth >= best.depth)) {
                        continue;
                    }
                }
            }

            int nextDay = state.depth / 10 + 1;
            int nextDaySteps = state.depth % 10 + 1;
            int nextWaste = 0;
            boolean justUsedDouble = state.card >= 0 && cardRate[state.card] == 10;
            if (daily) {
                nextWaste = state.waste;
                if (state.depth == 0) {
                    nextDay = 1;
                    nextDaySteps = 1;
                } else if (justUsedDouble || state.daySteps == 10) {
                    if (justUsedDouble) {
                        nextWaste += 10 - state.daySteps;
                    }
                    nextDay = state.day + 1;
                    nextDaySteps = 1;
                } else {
                    nextDay = state.day;
                    nextDaySteps = state.daySteps + 1;
                }
                if (nextWaste >= 10) {
                    continue;
                }
            }
            if (maxRouteDays > 0 && nextDay > maxRouteDays) {
                continue;
            }

            for (int oi = order.length - 1; oi >= 0; oi--) {
                int i = order[oi];
                if (state.used[i] >= counts[i]) {
                    continue;
                }
                if (finalDouble && cardRate[i] == 10 && state.usedDoubleCount >= 1) {
                    continue;
                }
                if (state.rate == cardRate[i]
                        && state.energy > (long) SAME_RATE_MAX_DAYS * DAY * state.rate) {
                    continue;
                }
                long energy = state.energy + (long) cardMinutes[i] * cardRate[i];
                if (energy > cap) {
                    continue;
                }
                int[] used = state.used.clone();
                used[i]++;
                int[] doubleDays = state.doubleDays;
                if (daily && cardRate[i] == 10) {
                    doubleDays = Arrays.copyOf(state.doubleDays, state.doubleDays.length + 1);
                    doubleDays[doubleDays.length - 1] = nextDay;
                }
                stack.push(new Node(used, cardRate[i], energy,
                        state.used15Energy + (cardRate[i] == 5 ? (long) cardMinutes[i] * cardRate[i] : 0),
                        state.usedDoubleCount + (cardRate[i] == 10 ? 1 : 0),
                        state, i, state.depth + 1, nextDay, nextDaySteps, nextWaste, doubleDays));
            }
        }

        List<Step> route = new ArrayList<>();
        for (Node state = best; state != null && state.parent != null; state = state.parent) {
            route.add(new Step(state.card, cardMinutes[state.card], cardRate[state.card],
                    state.energy / state.rate, state.day));
        }
        Collections.reverse(route);
        long finalMinutes = best == null ? 0 : best.energy / targetRate;
        return new Plan(best != null, complete,
                best != null ? null : (initialReplacementBlocked ? "replacement-limit"
                        : maxRouteDays > 0 ? "day-limit" : "no-route"),
                route, finalMinutes, targetRate, visited);
    }

    private static int[] buildOrder(int[] cardMinutes, int[] cardRate, final boolean[] expiring) {
        Integer[] indices = new Integer[cardMinutes.length];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }
        Arrays.sort(indices, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                int groupA = cardGroup(cardRate[a], cardMinutes[a]);
                int groupB = cardGroup(cardRate[b], cardMinutes[b]);
                if (groupA != groupB) {
                    return groupA - groupB;
                }
                // 同组内优先限时卡（即将过期）
                if (expiring[a] != expiring[b]) {
                    return expiring[a] ? -1 : 1;
                }
                int energyA = cardMinutes[a] * cardRate[a];
                int energyB = cardMinutes[b] * cardRate[b];
                if (energyA != energyB) {
                    return energyB - energyA;
                }
                return a - b;
            }
        });
        int[] order = new int[indices.length];
        for (int i = 0; i < indices.length; i++) {
            order[i] = indices[i];
        }
        return order;
    }

    private static int cardGroup(int rate, int minutes) {
        if (rate == 10) {
            return 4;
        }
        // 默认先用天卡，5分钟卡排在所有天卡之后
        if (minutes != 5) {
            return rate == 5 ? 0 : 1;
        }
        return rate == 1 ? 3 : 2;
    }

    private static boolean isEligible(Node state, int targetRate, int maxRouteDays, boolean daily,
                                      boolean hasDouble, boolean initialLocked, long totalDoubleStock,
                                      int[] cardRate) {
        if (state.rate != targetRate || (maxRouteDays > 0 && state.day > maxRouteDays)) {
            return false;
        }
        if (!daily || !hasDouble || initialLocked) {
            return true;
        }
        return state.card >= 0 && cardRate[state.card] == 10
                && state.day == (state.depth + 9) / 10
                && state.usedDoubleCount == Math.min(totalDoubleStock, state.day);
    }

    private static boolean isBetter(Node state, Node best, boolean daily) {
        if (best == null) {
            return true;
        }
        if (state.energy != best.energy) {
            return state.energy > best.energy;
        }
        if (state.used15Energy != best.used15Energy) {
            return state.used15Energy > best.used15Energy;
        }
        if (state.depth != best.depth) {
            return state.depth < best.depth;
        }
        return daily && laterDoubleDays(state.doubleDays, best.doubleDays);
    }

    private static boolean laterDoubleDays(int[] a, int[] b) {
        int length = Math.max(a.length, b.length);
        for (int i = length - 1; i >= 0; i--) {
            int left = i < a.length ? a[i] : -1;
            int right = i < b.length ? b[i] : -1;
            if (left != right) {
                return left > right;
            }
        }
        return false;
    }

    private static long limitHundredths(int rate, Config config) {
        switch (rate) {
            case 1:
                return Math.round(config.rate11Days * 100.0);
            case 2:
                return Math.round(config.rate12Days * 100.0);
            case 3:
                return Math.round(config.rate13Days * 100.0);
            case 5:
                return Math.round(config.rate15Days * 100.0);
            case 10:
                return Math.round(config.doubleBeforeDays * 100.0);
            default:
                return 0;
        }
    }

    /**
     * 使用下一张卡前，当前倍率时长是否已超过该倍率的接替前限制。
     * energy × 100 > 限制(百分之一天) × 1440 × rate
     */
    private static boolean beforeLimit(long energy, int rate, Config config) {
        long limit = limitHundredths(rate, config);
        return rate > 0 && energy * 100L > limit * (long) DAY * rate;
    }

    // ------------------------------------------------------------------
    // 日志辅助
    // ------------------------------------------------------------------

    private static String describeInventory(Inventory inventory) {
        StringBuilder sb = new StringBuilder();
        for (CardStock stock : inventory.cards) {
            if (stock.count <= 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("，");
            }
            sb.append(cardLabel(stock.minutes, stock.rate)).append("×").append(stock.count);
        }
        return sb.toString();
    }

    private static String cardLabel(int minutes, int rate) {
        String duration = minutes == 5 ? "5分钟" : (minutes / DAY) + "天";
        return "+" + duration + formatFactor(rate) + "倍";
    }

    private static String formatFactor(int rate) {
        return rate == 10 ? "2" : ("1." + rate);
    }

    private static String formatMinutes(long minutes) {
        long days = minutes / DAY;
        long rest = minutes % DAY;
        long hours = rest / 60;
        long mins = rest % 60;
        if (days > 0) {
            return days + "天" + (hours > 0 ? hours + "小时" : "");
        }
        if (hours > 0) {
            return hours + "小时" + (mins > 0 ? mins + "分钟" : "");
        }
        return mins + "分钟";
    }

    private static String reasonText(String reason) {
        if ("locked-above-limit".equals(reason)) {
            return "2倍已超45天锁定";
        }
        if ("energy-limit".equals(reason)) {
            return "已超过2倍终点上限";
        }
        if ("replacement-limit".equals(reason)) {
            return "当前时长超过接替前限制";
        }
        if ("day-limit".equals(reason)) {
            return "路线天数受限";
        }
        return "无可替换卡片";
    }
}
