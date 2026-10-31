package io.github.lazyimmortal.sesame.model.base;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;
import java.util.regex.Pattern;

import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.model.normal.base.BaseModel;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.Status;
import io.github.lazyimmortal.sesame.util.idMap.MiniGameTaskListMap;

/**
 * 完成任务的另一种实现方案：完成接口被 400000040「不支持rpc调用」拒绝时，换
 * {@code com.alipay.antfarm.doFarmTask} 再试。
 *
 * <p>该接口的响应不可信（常回 102「服务器正在开小差」而任务其实已生效），所以只发不判，
 * 成败一律以任务列表为准（下一轮列表扫描是否仍为 TODO）。
 */
public final class TaskAlternative {

    /** 服务端不校验 version，各模块可沿用本家那份；这里用庄园验证过的值。 */
    public static final String DEFAULT_VERSION = "1.8.2302070202.46";

    /**
     * 兜底伪申报的默认场景码（庄园）。**其它模组必须传自己的**：农场 {@code ORCHARD}、森林
     * {@code ANTFOREST}…冒用别的模组的场景会被服务端判"场景不合法"。
     */
    public static final String DEFAULT_SCENE_CODE = "ANTFARM";

    /** 兜底伪申报的默认来源。**各模组可传本家 RPC 的 source**：农场 {@code ANTFARM_ORCHARD}、
     * 新村 {@code AST}、神奇物种 {@code af-biodiversity}、海洋 {@code ANTFOCEAN}… */
    public static final String DEFAULT_SOURCE = "H5";

    /**
     * 交易/履约类任务关键词：**只认 bizKey**（服务端稳定字段），这类任务只能靠真实交易完成，
     * 用 doFarmTask 伪申报会被判风险操作（服务端回 1009 风控），一律不发，交自动黑名单。
     *
     * <p><b>匹配方式按词的歧义度分档，不能一律用 {@code contains}</b>：短词/缩写（{@code pay}、
     * {@code charge}、{@code _cz}）作为自由子串会命中任意夹带该串的 bizKey。
     * <ul>
     *   <li>{@link #LONG_KEYWORDS}：长拼音词（&ge;6 字母），夹带概率极低，用子串匹配；</li>
     *   <li>{@link #SHORT_KEYWORDS}：英文短词（&le;5 字母），要求两侧不得紧邻字母；</li>
     *   <li>{@code cz} 特判：仅认 {@code game_cz}，或 {@code _cz} 后跟数字（充值 N 元，如
     *       {@code 2026cc_cz6ylyb_fz}）。裸 {@code _cz} 结尾 / 被字母夹带的 {@code cz} 一律不认。</li>
     * </ul>
     */
    private static final String[] LONG_KEYWORDS = {
            // 支付 / 下单 / 缴费 / 充值（拼音连写，长度足够区分）
            "xiadan", "zhifu", "goumai", "jiaofei", "huankuan", "chongzhi",
            // 履约：寄件 / 回收
            "kuaidi", "huishou",
            // 酒店
            "jiudian",
            // 捐款/筹款：需真实付款，伪申报会被判风险操作（choukuan=抽抽乐筹款类）
            "juankuan", "choukuan",
    };

    /** 英文短词：两侧不得紧邻字母（数字/下划线/连字符/首尾都算边界）。 */
    private static final String[] SHORT_KEYWORDS = {
            "pay", "charge",
            // 租赁 / 出行 / 机票 / 余额宝
            "zulin", "zuche", "dache", "jipiao", "yuebao",
    };

    /** {@code _cz} 后跟数字（充值 N 元）的判据，如 {@code 2026cc_cz6ylyb_fz}。 */
    private static final Pattern CZ_AMOUNT = Pattern.compile("_cz[0-9]");

    private static final String TAG = "TaskAlternative";

    private TaskAlternative() {
    }

    /** bizKey 命中交易/履约类关键词，即视为交易/履约类。 */
    public static boolean isTransactionTask(String bizKey) {
        if (bizKey == null || bizKey.isEmpty()) {
            return false;
        }
        String key = bizKey.toLowerCase();
        for (String keyword : LONG_KEYWORDS) {
            if (key.contains(keyword)) {
                return true;
            }
        }
        for (String keyword : SHORT_KEYWORDS) {
            // 两侧不得紧邻字母：避免 pay/charge/dache 等短词被任意编码串夹带误命中
            if (Pattern.compile("(?<![a-z])" + keyword + "(?![a-z])").matcher(key).find()) {
                return true;
            }
        }
        // 游戏内充值：game_cz 明确，或 _cz 后跟数字（裸 _cz 结尾不算，避免误伤含 cz 的普通任务）
        return key.contains("game_cz") || CZ_AMOUNT.matcher(key).find();
    }

    /**
     * 目前实测只有庄园两个列表的任务能通过备用接口（doFarmTask）执行成功，
     * 其余列表先只收集不提交，后续按实测结果再放开。
     */
    public static boolean isSubmitEnabledList(String listTitle) {
        return "AntFarmDoFarmTaskList".equals(listTitle)
                || "AntFarmDrawMachineTaskList".equals(listTitle);
    }

    /** 日志出口（{@code Log.farm/forest/other}）。 */
    public interface LogSink {
        void log(String message);
    }

    /** 是否该换另一种实现方案：400000040 且 taskSceneCode 非空。 */
    public static boolean hit(JSONObject failJo, String taskSceneCode) {
        return taskSceneCode != null && !taskSceneCode.trim().isEmpty()
                && MessageUtil.isUnsupportedRpc(failJo);
    }

    /** 唯一的 doFarmTask payload，返回原始响应（场景码与来源用默认值：庄园 + H5）。 */
    public static String request(String bizKey, String taskSceneCode, String version) {
        return request(bizKey, taskSceneCode, version, DEFAULT_SCENE_CODE, DEFAULT_SOURCE);
    }

    /** 唯一的 doFarmTask payload，返回原始响应；{@code sceneCode} 需与调用方模组一致。 */
    public static String request(String bizKey, String taskSceneCode, String version, String sceneCode) {
        return request(bizKey, taskSceneCode, version, sceneCode, DEFAULT_SOURCE);
    }

    /** 完整形态：{@code sceneCode} 与 {@code source} 都按调用方模组给（混用会被判"场景不合法"）。 */
    public static String request(String bizKey, String taskSceneCode, String version, String sceneCode,
                                 String source) {
        if (isTransactionTask(bizKey)) {
            Log.i("⏭️跳过交易/履约类任务#bizKey=" + bizKey + "，不自动申报");
            return "{}";
        }
        String scene = (sceneCode == null || sceneCode.isEmpty()) ? DEFAULT_SCENE_CODE : sceneCode;
        String src = (source == null || source.isEmpty()) ? DEFAULT_SOURCE : source;
        String args = "[{\"bizKey\":\"" + bizKey + "\",\"requestType\":\"RPC\",\"sceneCode\":\"" + scene + "\","
                + "\"source\":\"" + src + "\",\"taskSceneCode\":\"" + taskSceneCode + "\",\"version\":\""
                + version + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.doFarmTask", args);
    }

    public static JSONObject doFarmTask(String bizKey, String taskSceneCode, String version) throws JSONException {
        return new JSONObject(request(bizKey, taskSceneCode, version));
    }

    public static JSONObject doFarmTask(String bizKey, String taskSceneCode, String version, String sceneCode)
            throws JSONException {
        return new JSONObject(request(bizKey, taskSceneCode, version, sceneCode));
    }

    public static JSONObject doFarmTask(String bizKey, String taskSceneCode, String version, String sceneCode,
                                        String source) throws JSONException {
        return new JSONObject(request(bizKey, taskSceneCode, version, sceneCode, source));
    }

    /** 日志片段 {@code resultCode/memo}（{@code code}、{@code desc}、{@code resultDesc} 兜底）。 */
    public static String describe(JSONObject jo) {
        if (jo == null) {
            return "无响应";
        }
        String code = jo.optString("resultCode", jo.optString("code", ""));
        String memo = jo.optString("memo", jo.optString("desc", jo.optString("resultDesc", "")));
        if (code.isEmpty() && memo.isEmpty()) {
            return "无响应";
        }
        return code + "/" + memo;
    }

    /** version 取 {@link #DEFAULT_VERSION}。 */
    public static JSONObject trigger(Map<String, String> pending, String taskId, String taskTitle,
                                     String bizKey, String taskSceneCode, String logPrefix, LogSink sink) {
        return trigger(pending, taskId, taskTitle, bizKey, taskSceneCode, DEFAULT_VERSION, logPrefix, sink);
    }

    /**
     * 发请求 + 打日志 + 登记 {@code taskId -> taskTitle}（{@code pending} 可为 null）；不做成败判定。
     *
     * @return doFarmTask 的响应；调用异常返回 null（按未触发处理）
     */
    public static JSONObject trigger(Map<String, String> pending, String taskId, String taskTitle,
                                     String bizKey, String taskSceneCode, String version,
                                     String logPrefix, LogSink sink) {
        return trigger(pending, taskId, taskTitle, bizKey, taskSceneCode, version, DEFAULT_SCENE_CODE,
                logPrefix, sink);
    }

    /** 指定伪申报场景码（各模组不同，见 {@link #DEFAULT_SCENE_CODE}）。 */
    public static JSONObject trigger(Map<String, String> pending, String taskId, String taskTitle,
                                     String bizKey, String taskSceneCode, String version, String sceneCode,
                                     String logPrefix, LogSink sink) {
        return trigger(pending, taskId, taskTitle, bizKey, taskSceneCode, version, sceneCode, DEFAULT_SOURCE,
                logPrefix, sink);
    }

    /** 指定伪申报场景码 + 来源。 */
    public static JSONObject trigger(Map<String, String> pending, String taskId, String taskTitle,
                                     String bizKey, String taskSceneCode, String version, String sceneCode,
                                     String source, String logPrefix, LogSink sink) {
        try {
            if (isTransactionTask(bizKey)) {
                Log.i(logPrefix + "⏭️跳过[" + taskTitle + "]#bizKey=" + bizKey + "，交易/履约类不自动申报");
                if (pending != null && taskId != null && !taskId.isEmpty()) {
                    pending.put(taskId, taskTitle);
                }
                return null;
            }
            JSONObject doFarmJo = doFarmTask(bizKey, taskSceneCode, version, sceneCode, source);
            if (pending != null && taskId != null && !taskId.isEmpty()) {
                pending.put(taskId, taskTitle);
            }
            String message = logPrefix + "🕓已触发[" + taskTitle + "]#响应=" + describe(doFarmJo)
                    + "，结果以任务列表为准";
            if (sink != null) {
                sink.log(message);
            } else {
                Log.other(message);
            }
            return doFarmJo;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return null;
        }
    }

    /**
     * 任务列表处理统一前置第1步：独立游戏任务（仅「待完成」状态）。
     *
     * <p>判定顺序（与 {@link MiniGameTaskListMap} 白/黑名单语义一致）：
     * <ol>
     *     <li><b>分项黑名单优先</b>：标题命中该列表黑名单关键字的，<b>仅不作为游戏任务</b>
     *         （不走小程序申报 doFarmTask），本方法返回 false，任务继续按各模块原有正常步骤处理；</li>
     *     <li><b>总白名单</b>：未命中黑名单且标题命中总白名单（「玩游戏得 / 去玩」）时，
     *         记录到共用的游戏任务收集表；状态为 {@code TODO} 且未被本账号加入
     *         「游戏任务 | 不允许执行列表」时，直接调用备用接口完成，不再走完成接口。</li>
     * </ol>
     *
     * <p>自动拉黑流程保持不变：各模块完成接口失败时由
     * {@code MessageUtil.checkResultCodeAndMarkTaskBlackList} 把任务名称写入对应列表黑名单；
     * 用户手动勾选的「游戏任务 | 不允许执行列表」优先级最高，命中坚决不做；
     * 每个任务的每日提交次数受「游戏任务每日最大提交次数」限制，该值是单个任务的上限；
     * 配置为 0 时仅收集不执行。
     *
     * <p>目前实测仅庄园两个列表（见 {@link #isSubmitEnabledList}）能通过备用接口执行成功，
     * 其余列表只收集、不提交。
     *
     * @return true 表示该任务已由本流程处理（已提交或跳过），调用方应跳过原有逻辑；
     *         false 表示不是游戏任务（含命中分项黑名单）、或状态不是待完成，继续原有逻辑
     */
    public static boolean handleGameTask(String listTitle, String taskTitle, String taskStatus,
                                         String bizKey, String taskSceneCode, String sceneCode, String source) {
        return handleGameTask(listTitle, taskTitle, taskStatus, bizKey, taskSceneCode, sceneCode, source,
                DEFAULT_VERSION);
    }

    /**
     * 带伪申报版本号的完整形态：{@code version} 会进入 doFarmTask 提交载荷，
     * 各模块有本家验证过的版本号时传本家的（如金豆 {@code 20260803.01}），否则用 {@link #DEFAULT_VERSION}。
     */
    public static boolean handleGameTask(String listTitle, String taskTitle, String taskStatus,
                                         String bizKey, String taskSceneCode, String sceneCode, String source,
                                         String version) {
        //第1步：分项黑名单优先——命中则不作为游戏任务，不走小程序申报，返回 false 让任务继续走原有正常步骤
        if (MiniGameTaskListMap.isBlacklisted(listTitle, taskTitle)) {
            return false;
        }
        //第2步：总白名单——命中才走游戏任务流程
        if (!MiniGameTaskListMap.isGameTask(listTitle, taskTitle)) {
            return false;
        }
        //仅用任务标题作为收集键，描述不参与
        MiniGameTaskListMap.add(taskTitle, listTitle);
        if (!"TODO".equals(taskStatus)) {
            return false;
        }
        //第3步：目前仅庄园列表实测能执行成功，其余列表只收集、不提交
        if (!isSubmitEnabledList(listTitle)) {
            Log.i("游戏任务⏭️跳过[" + taskTitle + "]场景[" + MessageUtil.getTaskListName(listTitle) + "]#该列表暂不支持备用接口执行");
            return true;
        }
        //提交次数由「每日最大提交次数」控制：0=仅自动收集，不执行
        if (BaseModel.isMiniGameTaskDisallowed(taskTitle, listTitle)) {
            Log.i("游戏任务🎮跳过[" + taskTitle + "]场景[" + MessageUtil.getTaskListName(listTitle) + "]#已加入不允许执行列表");
            return true;
        }
        //交易/履约类只能靠真实交易完成，伪申报会被判风险操作（1009），一律不发
        if (isTransactionTask(bizKey)) {
            Log.i("游戏任务⏭️跳过[" + taskTitle + "]#bizKey=" + bizKey + "，交易/履约类不自动申报");
            return true;
        }
        submitGameTask(listTitle, taskTitle, bizKey, taskSceneCode, sceneCode, source, version);
        return true;
    }

    /**
     * 发请求 + 打日志；不做成败判定（响应不可信）。
     *
     * <p>所有任务共用「游戏任务每日最大提交次数」这一个上限值，但按任务（任务名称+任务列表）
     * 分别计数记录在 Status，跨天自动重置；每个任务各自达到上限后都不再提交，配置为 0 即只收集不执行。
     *
     * @return doFarmTask 的响应；达到上限或调用异常返回 null
     */
    private static JSONObject submitGameTask(String listTitle, String taskTitle, String bizKey,
                                             String taskSceneCode, String sceneCode, String source, String version) {
        Integer limit = BaseModel.getMaxGameTaskSubmitPerDay().getValue();
        int max = (limit == null) ? 0 : limit;
        if (max <= 0) {
            return null;
        }
        String taskKey = MiniGameTaskListMap.key(taskTitle, listTitle);
        int submitted = Status.getGameTaskSubmitCountToday(taskKey);
        if (submitted >= max) {
            Log.i("游戏任务⏭️今日提交已达上限[" + submitted + "/" + max + "]，跳过["
                    + MessageUtil.getTaskListName(listTitle) + "][" + taskTitle + "]");
            return null;
        }
        //保守执行：不管接口是否异常都计入次数
        Status.incrGameTaskSubmitToday(taskKey);
        try {
            JSONObject doFarmJo = doFarmTask(bizKey, taskSceneCode, version, sceneCode, source);
            Log.other("游戏任务🕓提交[" + MessageUtil.getTaskListName(listTitle) + "][" + taskTitle + "]"
                    + "#今日第" + (submitted + 1) + "/" + max + "次");
                   // + "#doFarmTask=" + describe(doFarmJo)
                   // + "#bizKey=" + bizKey + "#taskSceneCode=" + taskSceneCode);
            return doFarmJo;
        } catch (Throwable t) {
            Log.i(TAG, "submitGameTask err:");
            Log.printStackTrace(TAG, t);
            return null;
        }
    }
}
