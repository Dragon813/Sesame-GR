package io.github.lazyimmortal.sesame.model.base;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.model.normal.base.BaseModel;
import io.github.lazyimmortal.sesame.model.task.antGame.MiniGameVendorReporter;
import io.github.lazyimmortal.sesame.mtop.bridge.MtopCaller;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.Status;
import io.github.lazyimmortal.sesame.util.TimeUtil;
import io.github.lazyimmortal.sesame.util.idMap.MiniGameTaskListMap;

/**
 * 游戏任务 / 第三方跳转任务的统一完成入口。
 *
 * <p>有跳转链接的按链接类型完成：
 * <ul>
 *     <li>小程序/游戏链接（带 {@code appId}）→ 游戏中心「无 UI 进入游戏」
 *     <li>第三方 App deeplink（{@code taskPlayType=CALL_APP_OUT_TASK}，如淘金币、美团）
 *         → 淘宝任务完成上报
 * </ul>
 * 带链接的任务受 {@code maxGameTaskSubmitPerDay} 限制：按「任务名[描述] + 列表」单个任务独立计数，
 * 限制的是<b>同一个任务每天被执行的次数</b>（一次执行内部提交几次都只算 1 次），不同任务互不影响。
 *
 * <p>本类只接管<b>带跳转链接</b>的任务；没有跳转链接的任务一律返回 false，交给各模块原有逻辑
 * （如庄园饲料任务的 {@code doFarmTask}、装扮抽抽乐的 {@code doFarmTaskPseudo/finishTask}）。
 * 模块侧的前置伪申报可通过 {@link #takeExecuteQuota} 复用同一份「每日执行次数」计数。
 */
public final class TaskAlternative {

    /** 游戏中心 paladinx 版本号。 */
    //private static final String PALADINX_VERSION = "2.2.13";
    private static final String PALADINX_VERSION = "2.19.50";

    /** 游戏中心默认渠道号：链接里不带 chInfo 时使用。 */
    private static final String DEFAULT_CH_INFO = "zfbhy_mc_wkenoz66";

    /** 游戏中心首页/推荐位请求来源（。 */
    private static final String HOME_SOURCE = "zmxy_mc_xctsup08";

    /** 淘宝侧固定 ttid（两个 mtop 都要带，缺了会被当作非 App 来源）。 */
    //private static final String TB_TTID = "1568860058617@taobao_android_10.68.10";
      private static final String TB_TTID = "1588718881807@taobao_android_10.68.10";
    /** 首帧事件幂等号里的随机数。 */
    private static final java.util.Random RANDOM = new java.util.Random();

    /** 淘宝人生 antFinishTask 的 referer。 */
    private static final String TAOBAO_REFERER =
            "https://pages-fast.m.taobao.com/wow/z/tblife/achievement-i/JX4ezmspfN8txPMWyawe";

    /** 淘宝农场 notifyAlipayTaskFinish 的 referer。 */
    private static final String FARM_REFERER =
            "https://pages-fast.m.taobao.com/wow/z/hdwk/farm-ssr/tmfarm";

    /**
     * 归zfb自己管的场景码：
     * 这类场景不能再拿去 callApp 查落地链接，只能用链接里现成的 token 走 antFinishTask。
     */
    private static final String[] TAOBAO_SELF_SCENES = {
            "ANTORCHARD_DRAW_TIMES", "ANTFOREST_ACTIVITY_DRAW_TASK", "FUGUO", "ORCHARD",
            "ANTFOREST_VITALITY_TASK", "ANTFARM_ORCHARD_TASK_V2", "ANTORCHARD_DRAW_TIMES_TASK",
            "ANTFOREST_NORMAL_DRAW_TASK",
            // 走链接 token 而不是 callApp 查落地链接
            "FOREST_CONTINUOUS_COLLECT_ENERGY_7", "2026cc_1000lyb_A&h", "ANTFARM_pay_6",
            "20260525_chouchoulechoukuan2", "IP_chouchoule_juankuan", "HEART_DONATE",
            "HEART_DONATION_ADVANCED_FOOD_V2", "MYZY_pay_xjsl_xddq", "monthlyCard", "freeze",
    };

    /** callApp 落地链接里 token 的候选参数名。 */
    private static final String[] USER_TOKEN_KEYS = {"userToken", "token", "user_token", "tToken", "Token"};

    /**
     * 交易/履约类任务关键词：**只认 bizKey**（服务端稳定字段），这类任务只能靠真实交易完成，
     * 模拟上报会被判风险操作（服务端回 1009 风控），一律不做，交自动黑名单。
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

    /**
     * 真实行为类任务标题/描述关键词：服务端按真实支付、缴费、出行、回收等行为判定，
     * 伪申报与游戏中心模拟都不可能完成（实测晚间残留率 90% 以上），一律不模拟。
     */
    private static final String[] REAL_BEHAVIOR_KEYWORDS = {
            "支付", "缴费", "月卡", "捐款", "捐赠", "回收", "下单", "充值", "外卖",
            "火车票", "购票", "出行", "打车", "寄件", "点单", "退税", "发票",
    };

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

    /** 普通浏览类标题关键词：这类任务常规流程能完成，TaskAlternative 不接管。 */
    private static final String[] BROWSE_TITLE_KEYWORDS = {"逛", "浏览", "签到", "参观", "看一看"};

    /**
     * 普通浏览类任务：{@code taskMode=NORMAL/VIEW/LABEL/FREE} 且标题为浏览语义，
     * 实测这类 43/45 由常规流程完成，交回模块原有逻辑。
     */
    private static boolean isBrowseTask(GameTask task) {
        for (String keyword : BROWSE_TITLE_KEYWORDS) {
            if (!task.title.contains(keyword)) {
                continue;
            }
            String mode = task.taskMode;
            if ("NORMAL".equals(mode) || "VIEW".equals(mode) || "LABEL".equals(mode) || "FREE".equals(mode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 第三方跳转任务：以平台字段 {@code taskPlayType=CALL_APP_OUT_TASK} 为准；
     * 平台未标注播放类型时才回退到链接特征，避免「有链接」就被误判。
     *
     * <p>平台明确标注了其它类型（{@code VISIT_FLOAT_BALL / OTHER / MINI_GAME_MULTI_STAGE_VISIT_FLOAT_BALL …}）
     * 时不做链接猜测，交回各模块常规流程。
     */
    private static boolean isThirdPartyJumpTask(GameTask task) {
        if (task.isCallAppOutTask()) {
            return true;
        }
        //显式跳转链接优先：实测「去淘宝芭芭农场逛一逛」taskPlayType=OTHER，
        //但链接带 jumpAction=userGrowth，按第三方跳转处理才能走 callAppSchema 上报
        if (isExplicitJumpLink(task.targetUrl)) {
            return true;
        }
        if (!task.taskPlayType.isEmpty()) {
            return false;
        }
        return isThirdPartyJump(task.targetUrl);
    }

    /** 链接是否带显式第三方跳转参数（jumpAction / external_task_param）。 */
    private static boolean isExplicitJumpLink(String url) {
        return url != null
                && (url.contains("jumpAction=") || url.contains("external_task_param="));
    }

    /**
     * 需要「无 UI 进入游戏」的游戏任务（替代标题白名单，只看平台字段与链接结构）。
     *
     * <p>判定依据（不依赖任务名）：
     * <ul>
     *     <li>有 {@code appId} 跳转链接；</li>
     *     <li>{@code taskProdPlayType}/taskPlayType 不含 {@code VISIT_FLOAT_BALL}
     *         （玩一玩 / 乐消消这类浮球浏览任务，常规提交即可完成，排除）；</li>
     *     <li>不属于第三方跳转（{@code CALL_APP_OUT_TASK}）。</li>
     * </ul>
     * 注：{@code taskMode} 不作为区分条件——实测「通过1关向僵尸开炮」等闯关任务
     * 的 taskMode 也是 {@code NORMAL}，但 taskProdPlayType=OTHER，需要进游戏。
     */
    private static boolean isGameTaskLike(GameTask task) {
        if (task.targetUrl.isEmpty() || task.isCallAppOutTask()) {
            return false;
        }
        if (task.taskPlayType.contains("VISIT_FLOAT_BALL")) {
            return false;
        }
        return !urlParam(task.targetUrl, "appId").isEmpty();
    }

    /** 标题/描述命中真实行为类关键词，即视为只能靠真实操作完成的任务。 */
    private static boolean isRealBehaviorTask(GameTask task) {
        String text = task.title + "|" + task.desc;
        for (String keyword : REAL_BEHAVIOR_KEYWORDS) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 单个游戏任务的信息载体：避免 {@code handleGameTask} 出现十个位置参数，
     * 必填项走构造器，可选项链式设置。
     */
    public static final class GameTask {
        private final String listTitle;
        private final String title;
        private final String desc;
        private final String targetUrl;
        private final String status;
        private String bizKey = "";
        private String taskSceneCode = "";
        private String sceneCode = "";
        private String source = "";
        private String version = "";
        private String taskMode = "";
        private String taskPlayType = "";
        private int rightsTimes = -1;
        private int rightsTimesLimit = -1;
        private int taskRemain = -1;
        private Runnable beforeGameLink;

        /**
         * @param listTitle 任务列表类（如 {@code AntFarmDoFarmTaskList}）
         * @param title     任务名
         * @param desc      任务描述，参与收集键 {@code 任务名[描述]}，空串时只用任务名
         * @param targetUrl 任务跳转链接，可为空；为空时只能收集、无法模拟完成
         * @param status    任务状态（只有 {@code TODO} 会被本流程处理）
         */
        public GameTask(String listTitle, String title, String desc, String targetUrl, String status) {
            this.listTitle = listTitle;
            this.title = title == null ? "" : title;
            this.desc = desc == null ? "" : desc;
            this.targetUrl = targetUrl == null ? "" : targetUrl;
            this.status = status == null ? "" : status;
        }

        public GameTask bizKey(String value) {
            this.bizKey = value == null ? "" : value;
            return this;
        }

        public GameTask taskSceneCode(String value) {
            this.taskSceneCode = value == null ? "" : value;
            return this;
        }

        public GameTask scene(String sceneCode, String source) {
            if (sceneCode != null && !sceneCode.isEmpty()) {
                this.sceneCode = sceneCode;
            }
            if (source != null && !source.isEmpty()) {
                this.source = source;
            }
            return this;
        }

        public GameTask version(String value) {
            if (value != null && !value.isEmpty()) {
                this.version = value;
            }
            return this;
        }

        /** 任务模式（{@code NORMAL/VIEW/LABEL/FREE/ACC_ANTIEP}…），用于识别普通浏览类。 */
        public GameTask taskMode(String value) {
            this.taskMode = value == null ? "" : value;
            return this;
        }

        /** 任务播放类型（{@code taskPlayType/taskProdPlayType}），{@code CALL_APP_OUT_TASK}=第三方 App 跳转。 */
        public GameTask taskPlayType(String value) {
            this.taskPlayType = value == null ? "" : value;
            return this;
        }

        /** 是否第三方 App 跳转任务（{@code taskPlayType/taskProdPlayType=CALL_APP_OUT_TASK}）。 */
        public boolean isCallAppOutTask() {
            return taskPlayType.contains("CALL_APP_OUT_TASK");
        }

        /**
         * 多档任务：{@code rightsTimes/rightsTimesLimit}（已完成档数/总档数）。
         * 用于放行「当前档已完成但还有剩余档位」的任务，一次进入把剩余档位推满。
         */
        public GameTask rights(int times, int limit) {
            this.rightsTimes = times;
            this.rightsTimesLimit = limit;
            return this;
        }

        /** 任务列表下发的剩余量 {@code taskRemain}，用于判断是否还有未完成进度。 */
        public GameTask taskRemain(int value) {
            this.taskRemain = value;
            return this;
        }

        /** 游戏小程序链接模拟前的模块侧申报；第三方跳转不会执行。 */
        public GameTask beforeGameLink(Runnable value) {
            this.beforeGameLink = value;
            return this;
        }

        /** 还有几档/多少剩余进度没完成；没有 rights/taskRemain 信息时返回 0。 */
        public int pendingRounds() {
            int rights = rightsTimesLimit > 0
                    ? Math.max(0, rightsTimesLimit - Math.max(0, rightsTimes)) : 0;
            return Math.max(rights, Math.max(0, taskRemain));
        }

        /** 任务显示名 {@code 任务名[描述]}。 */
        public String name() {
            return MiniGameTaskListMap.name(title, desc);
        }
    }

    /**
     * 取第一个非空值：不同接口的链接字段名不同（targetUrl / taskJumpUrl / link），
     * 且字段存在但值为空串时 {@code optString(key, fallback)} 不会回落，统一走这里保证兼容。
     */
    public static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    /**
     * 黑名单任务的游戏/跳转第三方补做入口：各列表在「黑名单跳过」分支中调用一次，
     * 对本方法可识别的任务执行（游戏中心/第三方跳转上报），随后由调用方直接跳过，
     * 不再走原有完成流程；不在黑名单中的任务一律走常规流程，不经由本方法。
     *
     * 本方法只接管<b>带跳转链接</b>的任务，无链接的返回 false：
     * <ol>
     *     <li><b>分项黑名单优先</b>：标题命中该列表黑名单关键字的，本方法返回 false 不接管；</li>
     *     <li><b>游戏任务</b>：未命中黑名单且标题命中总白名单（「玩游戏得 / 去玩」）或链接结构判定为需进游戏；
     *         <b>第三方跳转任务</b>：链接带 {@code jumpAction / userToken / 淘宝 starlink 落地页} 特征；
     *         普通浏览任务（只在链接里带 {@code taskType}、指向通用容器）不接管；</li>
     *     <li>命中后<b>仅当状态为 {@code TODO} 且带跳转链接</b>时，
     *         才以 {@code 任务名[描述]} 记录到共用收集表（值=任务列表类）——
     *         历史已完成/无链接的任务不收集；</li>
     * </ol>
     *
     * <p>自动拉黑流程保持不变：各模块完成接口失败时由
     * {@code MessageUtil.checkResultCodeAndMarkTaskBlackList} 把任务名称写入对应列表黑名单；
     * 用户手动勾选的「游戏任务 | 不允许执行列表」优先级最高，命中坚决不做。
     *
     * @return true 表示该任务已由本流程处理（已上报或跳过），调用方应跳过原有逻辑；
     *         false 表示不是本流程任务（含命中分项黑名单）、或状态不是待完成，继续原有逻辑
     */
    public static boolean handleGameTask(GameTask task) {
        //第1步：分项黑名单优先——命中则返回 false，让任务继续走原有正常步骤
        if (MiniGameTaskListMap.isBlacklisted(task.listTitle, task.title)) {
            return false;
        }
        //第2步：第三方跳转任务（平台字段优先，链接特征兜底）或需进游戏的游戏任务（结构化判断）
        boolean thirdParty = isThirdPartyJumpTask(task);
        boolean gameTask = isGameTaskLike(task) || MiniGameTaskListMap.isGameTask(task.listTitle, task.title);
        if (!gameTask && !thirdParty) {
            return false;
        }
        //普通浏览类常规流程就能完成，不接管（第三方跳转/游戏任务不适用此排除）
        if (!gameTask && !thirdParty && isBrowseTask(task)) {
            return false;
        }
        //只收集「待完成 + 带跳转链接」的第三方跳转/游戏小程序任务；历史已完成或无链接的不收集
        //多档任务（rightsTimes<rightsTimesLimit）或 taskRemain>0 时，即使当前档已 FINISHED 也继续推进
        int pendingRounds = task.pendingRounds();
        if ((!"TODO".equals(task.status) && pendingRounds <= 0) || task.targetUrl.isEmpty()) {
            return false;
        }
        if (!"TODO".equals(task.status)) {
            Log.i("游戏任务▶️[" + task.name() + "]场景["
                    + MessageUtil.getTaskListName(task.listTitle) + "]#状态=" + task.status
                    + "，剩余档位/进度=" + pendingRounds);
        }
        MiniGameTaskListMap.add(task.title, task.desc, task.listTitle);
        //用户勾选的「不允许执行列表」优先
        if (BaseModel.isMiniGameTaskDisallowed(task.title, task.desc, task.listTitle)) {
            Log.i("游戏任务⏭️跳过[" + task.name() + "]场景["
                    + MessageUtil.getTaskListName(task.listTitle) + "]#已加入不允许执行列表");
            return true;
        }
        //交易/履约/真实行为类只能靠真实操作完成，一律不模拟
        if (isTransactionTask(task.bizKey)) {
            Log.i("游戏任务⏭️跳过[" + task.name() + "]#bizKey=" + task.bizKey + "，交易/履约类不模拟");
            return true;
        }
        if (isRealBehaviorTask(task)) {
            Log.i("游戏任务⏭️跳过[" + task.name() + "]#标题或描述属真实支付、缴费、出行、回收等行为类，不模拟");
            return true;
        }
        //按链接类型完成（游戏中心 / 第三方跳转上报），受单任务每日执行次数限制。
        //先检查额度，真正发起上报后才计次：阶梯已完成或无待推进进度时不上报、不占次数。
        if (!hasExecuteQuota(task.listTitle, task.title, task.desc)) {
            return true;
        }
        if (completeByLink(task)) {
            recordExecuteQuota(task.listTitle, task.title, task.desc);
        }
        return true;
    }

    /**
     * 单个任务的每日执行次数检查；返回 false 表示本次不执行（配置为 0 或该任务已达上限）。
     *
     * <p>计数单位是「任务名[描述] + 所在任务列表类」，每个任务各自独立计数、互不影响。
     * 限制的是<b>任务被执行的次数</b>：一次执行内部为了完成任务提交几次接口，都只算 1 次；
     * 同一天最多执行 {@code maxGameTaskSubmitPerDay} 次，次日自然重置。
     */
    private static boolean takeSubmitQuota(GameTask task) {
        Integer limit = BaseModel.getMaxGameTaskSubmitPerDay().getValue();
        int max = (limit == null) ? 0 : limit;
        if (max <= 0) {
            Log.i("游戏任务⏭️[" + task.name() + "]#每日最大执行次数为0，仅收集不执行");
            return false;
        }
        String taskKey = "task|" + MiniGameTaskListMap.key(task.name(), task.listTitle);
        int used = Status.getGameTaskSubmitCountToday(taskKey);
        if (used >= max) {
            Log.i("游戏任务⏭️[" + task.name() + "]场景[" + MessageUtil.getTaskListName(task.listTitle)
                    + "]#今日执行已达上限[" + used + "/" + max + "]");
            return false;
        }
        //保守执行：不管接口是否异常都计入次数（一次执行只计 1 次）
        Status.incrGameTaskSubmitToday(taskKey);
        return true;
    }

    /**
     * 模块侧的前置执行（如庄园装扮抽抽乐的伪申报）也走同一套计数：
     * 占用该任务当天的执行次数；返回 false 表示已达上限、本次不执行。
     */
    public static boolean takeExecuteQuota(String listTitle, String title, String desc) {
        return takeSubmitQuota(new GameTask(listTitle, title, desc, "", "TODO"));
    }

    /**
     * 只检查该任务当天是否还有执行次数，不计数。
     * 供伪申报使用：先检查、发请求，未被安全验证拦截时再调用 {@link #recordExecuteQuota} 计数。
     */
    public static boolean hasExecuteQuota(String listTitle, String title, String desc) {
        Integer limit = BaseModel.getMaxGameTaskSubmitPerDay().getValue();
        int max = (limit == null) ? 0 : limit;
        String name = MiniGameTaskListMap.name(title, desc);
        if (max <= 0) {
            Log.i("游戏任务⏭️[" + name + "]#每日最大执行次数为0，仅收集不执行");
            return false;
        }
        String taskKey = "task|" + MiniGameTaskListMap.key(name, listTitle);
        int used = Status.getGameTaskSubmitCountToday(taskKey);
        if (used >= max) {
            Log.i("游戏任务⏭️[" + name + "]场景[" + MessageUtil.getTaskListName(listTitle)
                    + "]#今日执行已达上限[" + used + "/" + max + "]");
            return false;
        }
        return true;
    }

    /** 记录一次执行次数；伪申报返回安全验证时不调用，避免白白占用当天次数。 */
    public static void recordExecuteQuota(String listTitle, String title, String desc) {
        Status.incrGameTaskSubmitToday("task|" + MiniGameTaskListMap.key(
                MiniGameTaskListMap.name(title, desc), listTitle));
    }

    /**
     * 跳转链接模拟完成。
     *
     * <p>链接带 {@code appId} 时走游戏中心「无 UI 进入游戏」：
     * {@code queryGameComponent(START_APP_FIRST_REQ)} → {@code submitUserAction(enterGame)}
     * → {@code submitEvent(GAME_FIRST_FRAME)}；
     * 带第三方跳转特征（{@code jumpAction} / {@code userToken} / 淘宝 starlink 落地页，
     * 即 {@code taskPlayType=CALL_APP_OUT_TASK}，如淘金币、美团）的转
     * {@link #completeThirdPartyJump}。响应一律不判成败，以下一轮任务列表为准。
     *
     * <p>注意第三方链接也可能带 {@code appId}（支付宝的跳转容器 {@code 20002133}），
     * 所以不能只看 appId 判断。
     *
     * @return true 表示本次已实际进入游戏/第三方跳转（用于占用一次每日执行次数），
     *         无论阶梯是否推进、厂商是否可上报；false 表示链接不完整、无法进入
     */
    public static boolean completeByLink(GameTask task) {
        try {
            if (isThirdPartyJump(task.targetUrl)) {
                return completeThirdPartyJump(task);
            }
            String appId = urlParam(task.targetUrl, "appId");
            if (appId.isEmpty()) {
                Log.i("游戏任务⏭️[" + task.name() + "]#跳转链接无 appId，无法模拟完成");
                return false;
            }
            if (task.beforeGameLink != null && !isThirdPartyJumpTask(task)) {
                try {
                    task.beforeGameLink.run();
                } catch (Throwable t) {
                    Log.i(TAG, "游戏任务🔗前置伪申报异常[" + task.name() + "]");
                    Log.printStackTrace(TAG, t);
                }
            }
            String chInfo = urlParam(task.targetUrl, "chInfo");
            if (chInfo.isEmpty()) {
                chInfo = DEFAULT_CH_INFO;
            }
            String channel = channelOf(task.targetUrl);
            String sceneId = urlParam(task.targetUrl, "scene");
            if (sceneId.isEmpty()) {
                sceneId = task.sceneCode;
            }
            String taskId = gameParam(task.targetUrl, "taskId");
            String subScene = gameParam(task.targetUrl, "subScene");
            String passThrough = urlParam(task.targetUrl, "channelTaskPassThrough");
            //1. 组件信息：按「首次启动」请求
            JSONObject query = new JSONObject();
            query.put("appId", appId);
            query.put("chInfo", chInfo);
            if (!channel.isEmpty()) {
                query.put("channel", channel);
            }
            if (!taskId.isEmpty()) {
                query.put("taskId", taskId);
            }
            if (!subScene.isEmpty()) {
                query.put("subScene", subScene);
            }
            if (!passThrough.isEmpty()) {
                query.put("channelTaskPassThrough", passThrough);
            }
            query.put("reqType", "START_APP_FIRST_REQ");
            query.put("scene", sceneId);
            query.put("closeAd", false);
            query.put("deviceLevel", "high");
            query.put("unityDeviceLevel", "high");
            String componentResp = requestString("com.alipay.gamecenteruprod.biz.rpc.queryGameComponent", "[" + query + "]");
            List<LadderAction> ladderActions = ladderActionsOf(componentResp, appId);
            //2/3. 进入游戏 + 首帧事件（报文结构与客户端一致）
            String[] submit = submitEnterAndFirstFrame(appId, chInfo, sceneId);
            TimeUtil.sleep(500);
            //4. 厂商侧登录+行为上报：游戏中心只记录「进入游戏」，游戏内进度必须由厂商服务器接收
            boolean vendorSupported = MiniGameVendorReporter.supports(appId);
            boolean vendorOk = false;
            if (vendorSupported) {
                if (ladderActions == null) {
                    // 没有阶梯数据：用任务列表下发的剩余量（taskRemain/rights）兜底，至少按一次完整任务
                    int times = Math.max(1, Math.min(MAX_TIMES_PER_ACTION, task.pendingRounds()));
                    ladderActions = java.util.Collections.singletonList(new LadderAction("", times));
                } else if (task.pendingRounds() > 0) {
                    // 阶梯里程碑都完成、但任务列表说还有剩余档位（rights/taskRemain）：
                    // 用标题匹配到的动作码补齐剩余量，保证一次进入推满
                    String titleAction = MiniGameVendorReporter.resolveAction(appId, task.name());
                    if (!titleAction.isEmpty()) {
                        Map<String, Integer> merged = new LinkedHashMap<>();
                        for (LadderAction action : ladderActions) {
                            merged.put(action.code, action.times);
                        }
                        int need = Math.max(1, Math.min(MAX_TIMES_PER_ACTION, task.pendingRounds()));
                        Integer old = merged.get(titleAction);
                        if (old == null || need > old) {
                            merged.put(titleAction, need);
                        }
                        List<LadderAction> list = new ArrayList<>();
                        for (Map.Entry<String, Integer> entry : merged.entrySet()) {
                            list.add(new LadderAction(entry.getKey(), entry.getValue()));
                        }
                        ladderActions = list;
                    }
                }
                if (ladderActions.isEmpty()) {
                    MessageUtil.logByTaskList(task.listTitle, "游戏任务⏭️跳过[" + task.name() + "]任务列表["
                            + MessageUtil.getTaskListName(task.listTitle) + "]#阶梯任务已完成，无待推进进度");
                } else {
                    MessageUtil.logByTaskList(task.listTitle, "游戏任务🔗开始完成[" + task.name() + "]任务列表["
                            + MessageUtil.getTaskListName(task.listTitle) + "]#登录厂商并上报行为，待上报动作="
                            + ladderActions.size());
                    // 一轮内把该游戏所有未完成阶梯按各自差值报满：
                    // 同动作码的累计任务共用一次上报（取最大差值），不同动作码分别上报
                    for (LadderAction ladderAction : ladderActions) {
                        vendorOk |= MiniGameVendorReporter.report(appId, task.name(), channel,
                                ladderAction.code, ladderAction.times, task.listTitle);
                    }
                }
            }
            Log.i("游戏任务🔗链接模拟[" + task.name() + "]来源["
                    + MessageUtil.getTaskListName(task.listTitle) + "]#appId=" + appId + " 提交数据"
                    + " | queryGameComponent: [" + query + "] => " + componentResp
                    + " | submitUserAction: " + submit[0] + " => " + submit[1]
                    + " | submitEvent: " + submit[2] + " => " + submit[3]
                    + " | 厂商上报: " + (vendorSupported
                            ? "已执行(vendor=" + MiniGameVendorReporter.vendorName(appId)
                                    + ")，结果=" + (vendorOk ? "成功" : "失败")
                            : "无适配(sdk=" + MiniGameVendorReporter.sdkName(appId) + "，未实现协议)"));
            MessageUtil.logByTaskList(task.listTitle, "游戏任务🔗链接模拟[" + task.name() + "]来源["
                    + MessageUtil.getTaskListName(task.listTitle) + "]#appId=" + appId);
            //5. 游戏中心补量：首屏浏览任务 / 推荐游戏（每个场景每天一次）
            completeGameCenterExtras(sceneId, chInfo, task.listTitle);
            //进一次就计 1 次：推满、没进度可推、厂商未适配不能上报，都算一次执行
            return true;
        } catch (Throwable t) {
            Log.i(TAG, "completeByLink err:");
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    /** 场景补量每天只做一次，避免每个任务都重打一遍 queryHomePage / queryRecommendGames。 */
    private static final java.util.Set<String> DONE_EXTRA_SCENES = new java.util.HashSet<>();
    private static final java.util.Set<String> DONE_RECOMMEND_APPS = new java.util.HashSet<>();
    private static String extraSceneDay = "";

    /**
     * 游戏中心补量：
     * <ol>
     *   <li>{@code queryHomePage} 的首屏浏览任务 {@code browseTaskVO} → {@code completeBrowseTask}，
     *       顺手领每日任务凭证 {@code batchReceiveTaskPrize}；</li>
     *   <li>{@code queryRecommendGames} 的推荐游戏 → 逐个补进入事件；
     *       带 {@code trafficDriverId} 的再走 {@code channeltask.floatingball}。</li>
     * </ol>
     */
    private static void completeGameCenterExtras(String sceneId, String chInfo, String listTitle) {
        if (sceneId.isEmpty()) {
            return;
        }
        String today = TimeUtil.getDateStr();
        synchronized (DONE_EXTRA_SCENES) {
            if (!today.equals(extraSceneDay)) {
                extraSceneDay = today;
                DONE_EXTRA_SCENES.clear();
                DONE_RECOMMEND_APPS.clear();
            }
            if (!DONE_EXTRA_SCENES.add(sceneId)) {
                return;
            }
        }
        completeBrowseTasks(sceneId);
        drawGameCenterAwards(sceneId, listTitle);
        completeRecommendGames(sceneId, chInfo, listTitle);
    }

    /**
     * 领取游戏中心已累积的奖励次数（完成游戏后的收尾步骤）。
     *
     * <p>接口报文：
     * <pre>
     * com.alipay.charitygamecenter.drawGameCenterAward
     * [{"batchDrawCount":10,"bizType":"ANTFOREST","requestType":"H5",
     *   "sceneCode":"ANTFOREST","source":"hyiaatest","version":"20250821"}]
     * → {"desc":"SUCCESS","drawRightsTimes":0,
     *    "gameCenterDrawAwardList":[{"awardCount":70,"awardName":"能量","awardType":"ENERGY"},…]}
     * </pre>
     * {@code bizType} 取场景码首段（{@code ANTFOREST_ENERGY_RAIN_TASK → ANTFOREST}）。
     */
    private static void drawGameCenterAwards(String sceneId, String listTitle) {
        try {
            JSONObject body = new JSONObject();
            body.put("batchDrawCount", 10);
            body.put("bizType", bizTypeOf(sceneId));
            body.put("requestType", "H5");
            body.put("sceneCode", sceneId);
            body.put("source", "H5");
            body.put("version", "20250821");
            String response = requestString(
                    "com.alipay.charitygamecenter.drawGameCenterAward", "[" + body + "]");
            if (response.isEmpty()) {
                return;
            }
            JSONObject jo = new JSONObject(response);
            JSONArray awards = jo.optJSONArray("gameCenterDrawAwardList");
            if (awards != null && awards.length() > 0) {
                MessageUtil.logByTaskList(listTitle, "游戏任务🎁领奖[" + sceneId + "]#共 " + awards.length()
                        + " 份，剩余次数=" + jo.optInt("drawRightsTimes", 0));
            } else {
                Log.i("游戏任务🎁领奖[" + sceneId + "]#暂无可领奖励("
                        + jo.optString("desc") + ")");
            }
        } catch (Throwable t) {
            Log.i(TAG, "drawGameCenterAward err: " + t.getMessage());
        }
    }

    /** 场景码首段作为 bizType：{@code ANTFOREST_ENERGY_RAIN_TASK → ANTFOREST}。 */
    private static String bizTypeOf(String sceneId) {
        int index = sceneId.indexOf('_');
        return index > 0 ? sceneId.substring(0, index) : sceneId;
    }

    /** 首屏浏览任务与每日凭证。 */
    private static void completeBrowseTasks(String sceneId) {
        try {
            JSONObject body = new JSONObject();
            body.put("alreadyDisplayedGeneralDeliveryPopups", new JSONArray());
            body.put("channelTaskPassThrough", "");
            body.put("cantShowGeneralDeliveryPopups", new JSONArray().put("VOUCHER"));
            body.put("deviceLevel", "high");
            body.put("guideType", "");
            body.put("moduleId", "");
            body.put("sceneId", sceneId);
            body.put("source", HOME_SOURCE);
            body.put("unityDeviceLevel", "high");
            String response = requestString(
                    "com.alipay.gamecenteruprod.biz.rpc.external.gamecenter.queryHomePage",
                    "[" + body + "]");
            if (response.isEmpty()) {
                return;
            }
            JSONObject module = findObject(response, "taskModule");
            if (module == null) {
                return;
            }
            JSONArray browse = module.optJSONArray("browseTaskVO");
            if (browse == null) {
                JSONObject browseObj = module.optJSONObject("browseTaskVO");
                browse = browseObj == null ? null : browseObj.optJSONArray("taskList");
            }
            if (browse != null) {
                for (int i = 0; i < browse.length(); i++) {
                    JSONObject item = browse.optJSONObject(i);
                    if (item == null || item.optInt("targetProgress", 1) <= 0) {
                        continue;
                    }
                    JSONObject complete = new JSONObject();
                    complete.put("sceneId", sceneId);
                    JSONObject sceneExt = item.optJSONObject("sceneExtInfo");
                    complete.put("sceneExtInfo", sceneExt == null ? item : sceneExt);
                    requestString("com.alipay.gamecenteruprod.biz.rpc.external.gamecenter.completeBrowseTask",
                            "[" + complete + "]");
                }
            }
            if (module.optInt("needReceiveTaskCertCnt", 0) > 0) {
                JSONObject prize = new JSONObject();
                prize.put("sceneId", sceneId);
                prize.put("source", HOME_SOURCE);
                requestString("com.alipay.gamecenteruprod.biz.rpc.external.gamecenter.batchReceiveTaskPrize",
                        "[" + prize + "]");
            }
        } catch (Throwable t) {
            Log.i(TAG, "queryHomePage err: " + t.getMessage());
        }
    }

    /** 推荐游戏补进入事件；带 trafficDriverId 的顺带走频道浮动球。 */
    private static void completeRecommendGames(String sceneId, String chInfo, String listTitle) {
        try {
            JSONObject body = new JSONObject();
            body.put("sceneId", sceneId);
            body.put("source", HOME_SOURCE);
            body.put("creativeId", "");
            body.put("topGameId", "");
            String response = requestString(
                    "com.alipay.gamecenteruprod.biz.rpc.external.gamecenter.queryRecommendGames",
                    "[" + body + "]");
            if (response.isEmpty()) {
                return;
            }
            JSONObject recommend = findObject(response, "todayRecommendVO");
            JSONArray games = recommend == null ? null : recommend.optJSONArray("gameList");
            if (games == null) {
                return;
            }
            for (int i = 0; i < games.length(); i++) {
                JSONObject item = games.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String jumpUrl = item.optString("jumpUrl");
                String appId = urlParam(jumpUrl, "appId");
                if (appId.isEmpty()) {
                    continue;
                }
                String trafficDriverId = item.optString("trafficDriverId",
                        item.optString("driverId"));
                if (!trafficDriverId.isEmpty()) {
                    completeChannelFloatingBall(sceneId, appId, trafficDriverId,
                            item.optString("gameModuleId", item.optString("moduleId")), listTitle);
                    continue;
                }
                synchronized (DONE_RECOMMEND_APPS) {
                    if (!DONE_RECOMMEND_APPS.add(appId)) {
                        continue;
                    }
                }
                enterGameOnly(appId, chInfo, sceneId);
            }
        } catch (Throwable t) {
            Log.i(TAG, "queryRecommendGames err: " + t.getMessage());
        }
    }

    /** 频道浮动球：consult → complete（推荐位任务的「进频道即完成」）。 */
    private static void completeChannelFloatingBall(String sceneId, String appId,
                                                    String trafficDriverId, String gameModuleId,
                                                    String listTitle) {
        try {
            JSONObject consult = new JSONObject();
            consult.put("sceneId", sceneId);
            consult.put("taskId", trafficDriverId);
            consult.put("passThrough", "");
            consult.put("source", HOME_SOURCE);
            requestString("com.alipay.gamecenteruprod.biz.rpc.channeltask.floatingball.consult",
                    "[" + consult + "]");
            JSONObject complete = new JSONObject();
            complete.put("floatingBallTypeList", new JSONArray().put("CHANNEL_FLOATING_BALL"));
            complete.put("gameId", appId);
            complete.put("gameModuleId", gameModuleId);
            complete.put("source", HOME_SOURCE);
            complete.put("trafficDriverId", trafficDriverId);
            String response = requestString(
                    "com.alipay.gamecenteruprod.biz.rpc.channeltask.floatingball.complete",
                    "[" + complete + "]");
            MessageUtil.logByTaskList(listTitle, "游戏任务▶️频道浮动球[" + appId + "]#trafficDriverId=" + trafficDriverId
                    + "，响应=" + response);
        } catch (Throwable t) {
            Log.i(TAG, "channeltask.floatingball err: " + t.getMessage());
        }
    }

    /** 推荐游戏只补「进入 + 首帧」，不再重复补量。 */
    private static void enterGameOnly(String appId, String chInfo, String sceneId) {
        try {
            JSONObject query = new JSONObject();
            query.put("appId", appId);
            query.put("chInfo", chInfo);
            query.put("reqType", "START_APP_FIRST_REQ");
            query.put("scene", sceneId);
            query.put("closeAd", false);
            requestString("com.alipay.gamecenteruprod.biz.rpc.queryGameComponent", "[" + query + "]");
            TimeUtil.sleep(300);
            submitEnterAndFirstFrame(appId, chInfo, sceneId);
        } catch (Throwable t) {
            Log.i(TAG, "enterGameOnly err: " + t.getMessage());
        }
    }

    /**
     * 平台侧申报：submitUserAction(enterGame) + submitEvent(GAME_FIRST_FRAME)。
     * 报文结构（2026-11-09 森林救援队）：
     * idempotentNo = platform_<毫秒时间戳>_<随机数>_0，eventAttrMap 常量与真机一致，
     * 不带 GAME_VERSION（真机 GAME_FIRST_FRAME 无此字段）。
     *
     * @return [submitUserAction请求体, submitUserAction响应, submitEvent请求体, submitEvent响应]
     */
    private static String[] submitEnterAndFirstFrame(String appId, String chInfo, String sceneId)
            throws Throwable {
        JSONObject enter = new JSONObject();
        enter.put("actionCode", "enterGame");
        enter.put("gameId", appId);
        enter.put("paladinxVersion", PALADINX_VERSION);
        enter.put("source", "gameFramework");
        String enterBody = "[" + enter + "]";
        String enterResp = requestString("com.alipay.gamecenteruprod.biz.rpc.v3.submitUserAction", enterBody);
        JSONObject attr = new JSONObject();
        attr.put("ALIVE_ENTER", "0");
        attr.put("CH_INFO", chInfo);
        attr.put("CPS_ID", "unknown");
        attr.put("PALADINX_VERSION", PALADINX_VERSION);
        attr.put("PLAY_SCENE", "NORMAL");
        attr.put("SCENE_ID", sceneId);
        JSONObject event = new JSONObject();
        event.put("appId", appId);
        event.put("eventAttrMap", attr);
        event.put("eventId", "GAME_FIRST_FRAME");
        event.put("idempotentNo", "platform_" + System.currentTimeMillis() + "_"
                + RANDOM.nextInt(100000) + "_0");
        event.put("source", "paladinx_auto");
        String eventBody = "[" + event + "]";
        String eventResp = requestString("com.alipay.gameevent.biz.rpc.submitEvent", eventBody);
        return new String[]{enterBody, enterResp, eventBody, eventResp};
    }

    /** 游戏任务参数：先取直连参数，再解析链接里的 {@code originalMap} JSON。 */
    private static String gameParam(String url, String key) {
        String value = urlParam(url, key);
        if (!value.isEmpty()) {
            return value;
        }
        String original = urlParam(url, "originalMap");
        if (original.isEmpty()) {
            return "";
        }
        try {
            return new JSONObject(original).optString(key);
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** 在响应里递归找指定 key 的对象（兼容 taskModule 在 data/result 下的情况）。 */
    private static JSONObject findObject(String response, String key) {
        try {
            return findObject(new JSONObject(response), key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static JSONObject findObject(JSONObject jo, String key) {
        JSONObject direct = jo.optJSONObject(key);
        if (direct != null) {
            return direct;
        }
        JSONArray names = jo.names();
        if (names == null) {
            return null;
        }
        for (int i = 0; i < names.length(); i++) {
            Object value = jo.opt(names.optString(i));
            if (value instanceof JSONObject) {
                JSONObject found = findObject((JSONObject) value, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * 第三方 App 跳转任务模拟完成：
     *
     * <p>两条分支：
     * <ol>
     *   <li>链接里没有 sceneCode，或 sceneCode 归本模块自己管 → 用链接里现成的
     *       {@code userToken + taskType + sceneCode} 调
     *       {@code mtop.aplatform.taobaolife.get} 的 {@code achievement.antFinishTask}；</li>
     *   <li>其它 sceneCode（如淘金币的 {@code ANTFARM_FOOD_TASK}）→
     *       {@code alipay.antmember.callApp.queryCallAppSchema} 查落地链接，
     *       取 {@code userToken} 后调 {@code mtop.tmall.farm.orchard.task.notifyAlipayTaskFinish}。</li>
     * </ol>
     *
     * <p>真实样本（runtime.2026-10-31）：
     * <ul>
     *   <li>{@code 双11来淘金币狂享20亿}：{@code market.m.taobao.com/...?targetUrl=<内层 pages-fast>}，
     *       内层才有 {@code userToken/sceneCode=ANTFARM_FOOD_TASK/taskType=2026TB1111}；</li>
     *   <li>{@code 去美团逛一逛领金币}：{@code alipays://...&sceneCode=ANTFARM_FOOD_TASK&userToken=...}，
     *       参数在顶层。</li>
     * </ul>
     *
     * @return true 表示已发起上报；false 表示链接里没有可用参数
     */
    public static boolean completeThirdPartyJump(GameTask task) {
        try {
            String sceneCode = urlParamDeep(task.targetUrl, "iepTaskSceneCode");
            if (sceneCode.isEmpty()) {
                sceneCode = urlParamDeep(task.targetUrl, "sceneCode");
            }
            if (sceneCode.isEmpty()) {
                sceneCode = urlParamDeep(task.targetUrl, "third_scene_code");
            }
            //本模块自身场景：callApp 查不到外部落地链接，只能用链接 token
            if (sceneCode.isEmpty() || isSelfScene(sceneCode)) {
                return finishByTaobaoLife(task);
            }
            String userToken = queryUserToken(sceneCode);
            if (!userToken.isEmpty()) {
                String response = MtopCaller.call(ApplicationHook.getClassLoader(),
                        "mtop.tmall.farm.orchard.task.notifyAlipayTaskFinish",
                        "{\"sceneCode\":\"FUGUO\",\"convertTag\":\"" + sceneCode
                                + "\",\"userToken\":\"" + userToken
                                + "\",\"ttid\":\"" + TB_TTID + "\"}",
                        "{\"referer\":\"" + FARM_REFERER + "\"}", false, false, 15000);
                MessageUtil.logByTaskList(task.listTitle, "跳转任务↗️任务跳转第三方[" + task.name() + "]" + retMessage(response));
                return true;
            }
            Log.i("跳转任务↗️[" + task.name() + "]#sceneCode=" + sceneCode + " 未取到 userToken，改用链接 token");
            return finishByTaobaoLife(task);
        } catch (Throwable t) {
            Log.i(TAG, "completeThirdPartyJump err:");
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    /**
     * 链接 token 分支：{@code mtop.aplatform.taobaolife.get} + {@code achievement.antFinishTask}。
     *
     * <p>参数优先取链接顶层/内层 targetUrl 的 query；取不到时再解析
     * {@code external_task_param} 里编码的 JSON（「逛一逛淘宝拍照」等淘宝落地页）。
     */
    private static boolean finishByTaobaoLife(GameTask task) throws JSONException {
        String userToken = urlParamDeep(task.targetUrl, "userToken");
        if (userToken.isEmpty()) {
            userToken = urlParamDeep(task.targetUrl, "third_token");
        }
        if (userToken.isEmpty()) {
            userToken = urlParamDeep(task.targetUrl, "token");
        }
        if (userToken.isEmpty()) {
            userToken = externalTaskParam(task.targetUrl, "userToken");
        }
        String sceneCode = urlParamDeep(task.targetUrl, "iepTaskSceneCode");
        if (sceneCode.isEmpty()) {
            sceneCode = urlParamDeep(task.targetUrl, "sceneCode");
        }
        if (sceneCode.isEmpty()) {
            sceneCode = urlParamDeep(task.targetUrl, "third_scene_code");
        }
        if (sceneCode.isEmpty()) {
            sceneCode = externalTaskParam(task.targetUrl, "iepTaskSceneCode");
        }
        String taskType = urlParamDeep(task.targetUrl, "iepTaskType");
        if (taskType.isEmpty()) {
            taskType = urlParamDeep(task.targetUrl, "taskType");
        }
        if (taskType.isEmpty()) {
            taskType = urlParamDeep(task.targetUrl, "third_task_type");
        }
        if (taskType.isEmpty()) {
            taskType = externalTaskParam(task.targetUrl, "iepTaskType");
        }
        if (userToken.isEmpty() || sceneCode.isEmpty() || taskType.isEmpty()) {
            Log.i("跳转任务↗️[" + task.name() + "]#链接缺少 userToken/sceneCode/taskType，无法模拟第三方跳转");
            return false;
        }
        JSONObject bizParam = new JSONObject();
        bizParam.put("source", "taobao");
        bizParam.put("token", userToken);
        bizParam.put("taskType", taskType);
        bizParam.put("sceneCode", sceneCode);
        JSONObject data = new JSONObject();
        data.put("bizType", "achievement.antFinishTask");
        data.put("bizParam", bizParam.toString());
        data.put("ttid", TB_TTID);
        String response = MtopCaller.call(ApplicationHook.getClassLoader(),
                "mtop.aplatform.taobaolife.get", data.toString(),
                "{\"referer\":\"" + TAOBAO_REFERER + "\"}", false, false, 15000);
        MessageUtil.logByTaskList(task.listTitle, "跳转任务↗️任务跳转第三方[" + task.name() + "]" + retMessage(response));
        return true;
    }

    /**
     * 淘宝任务链接里经过 URL 编码的 {@code external_task_param} 参数（JSON），
     * 例如「逛一逛淘宝拍照」：
     * <pre>
     * ...&amp;targetUrl=https%3A%2F%2Fh5.m.taobao.com%2F...%26external_task_param%3D
     * %257B%2522source%2522...%2522params%2522...%2522iepTaskType%2522...%2522userToken%2522...
     * </pre>
     * 结构为 {@code {"source":"...","params":{"iepTaskType":"...","iepTaskSceneCode":"...","userToken":"..."}}}。
     *
     * @return params 对象；参数不存在或无法解析时返回 null
     */
    private static JSONObject externalTaskParams(String url) {
        String raw = urlParamDeep(url, "external_task_param");
        if (raw.isEmpty()) {
            return null;
        }
        for (int i = 0; i < 2; i++) {
            try {
                JSONObject jo = new JSONObject(raw);
                JSONObject params = jo.optJSONObject("params");
                return params == null ? jo : params;
            } catch (Throwable t) {
                //可能还残留一层 URL 编码，解码后再试
                try {
                    raw = java.net.URLDecoder.decode(raw, "UTF-8");
                } catch (Throwable ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /** 从 {@code external_task_param.params} 取字段，取不到返回空串。 */
    private static String externalTaskParam(String url, String key) {
        JSONObject params = externalTaskParams(url);
        if (params == null) {
            return "";
        }
        return cleanToken(params.optString(key));
    }

    /** 去掉 token 值两侧多余的引号/空白（部分链接把 token 值本身又包了一层引号）。 */
    private static String cleanToken(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        while (v.length() >= 2
                && ((v.startsWith("\"") && v.endsWith("\""))
                || (v.startsWith("'") && v.endsWith("'")))) {
            v = v.substring(1, v.length() - 1).trim();
        }
        return v;
    }

    /** {@code alipay.antmember.callApp.queryCallAppSchema}：从落地链接里取 userToken。 */
    private static String queryUserToken(String sceneCode) throws JSONException {
        String response = requestString("alipay.antmember.callApp.queryCallAppSchema",
                "[{\"sceneCode\":\"" + sceneCode + "\"}]");
        if (response.isEmpty()) {
            Log.i("跳转任务🔎userToken[sceneCode=" + sceneCode + "]#queryCallAppSchema 返回空");
            return "";
        }
        JSONObject jo = new JSONObject(response);
        if (!jo.optBoolean("success")) {
            Log.i("跳转任务🔎userToken[sceneCode=" + sceneCode + "]#success=false，响应=" + brief(response));
            return "";
        }
        String userToken = urlParamDeep(jo.optString("channelLandingUrlLink"), "userToken");
        if (!userToken.isEmpty()) {
            Log.i("跳转任务🔎userToken[sceneCode=" + sceneCode + "]#取自 channelLandingUrlLink");
            return userToken;
        }
        JSONArray configs = jo.optJSONArray("schemaConfigList");
        if (configs == null) {
            Log.i("跳转任务🔎userToken[sceneCode=" + sceneCode + "]#无 schemaConfigList，响应=" + brief(response));
            return "";
        }
        for (int i = 0; i < configs.length(); i++) {
            JSONObject config = configs.optJSONObject(i);
            if (config == null) {
                continue;
            }
            for (String key : USER_TOKEN_KEYS) {
                userToken = urlParamDeep(config.optString("channelDeepLink"), key);
                if (userToken.isEmpty()) {
                    userToken = urlParamDeep(config.optString("channelMarketLink"), key);
                }
                if (!userToken.isEmpty()) {
                    Log.i("游戏任务🔎userToken[sceneCode=" + sceneCode + "]#取自 schemaConfigList[" + i + "]");
                    return userToken;
                }
            }
        }
        Log.i("跳转任务🔎userToken[sceneCode=" + sceneCode + "]#链接与 schema 均无 token，响应=" + brief(response));
        return "";
    }

    /** 诊断日志用截断，避免超长响应刷屏。 */
    private static String brief(String s) {
        return s.length() > 800 ? s.substring(0, 800) + "…" : s;
    }

    /** 从响应 {@code ret}（如 {@code ["SUCCESS::接口调用成功"]}）里取 {@code ::} 后的描述文案。 */
    private static String retMessage(String response) {
        if (response == null || response.isEmpty()) {
            return "响应为空";
        }
        try {
            JSONArray ret = new JSONObject(response).optJSONArray("ret");
            if (ret != null && ret.length() > 0) {
                String desc = ret.optString(0);
                int idx = desc.indexOf("::");
                return idx >= 0 ? desc.substring(idx + 2) : desc;
            }
        } catch (Throwable ignored) {
            //解析失败时退回原始响应
        }
        return brief(response);
    }

    /**
     * 第三方跳转任务判定：只认能对得上「外部 App / 淘宝落地页」的链接特征。
     *
     * <p>不能把任意 {@code taskType} 参数都当第三方：实测「去杂货铺逛一逛」这类普通浏览任务的
     * 链接也带 {@code taskType=iepTaskType}，但它由常规流程正常完成；误抓进来反而走错流程。
     * 因此只认：
     * <ul>
     *     <li>{@code jumpAction}：唤起外部 App（美团等）；</li>
     *     <li>{@code userToken / third_token / tToken}：淘宝任务回传凭证（含内层 targetUrl）；</li>
     *     <li>{@code external_task_param}：淘宝落地页里编码的 JSON，内含 {@code userToken / iepTaskType / iepTaskSceneCode}；</li>
     *     <li>淘宝 starlink 落地页（{@code market.m.taobao.com/.../wakeup-transit}，淘金币、拍立淘等）。</li>
     * </ul>
     */
    private static boolean isThirdPartyJump(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return url.contains("jumpAction=")
                || url.contains("wakeup-transit")
                || url.contains("market.m.taobao.com")
                || url.contains("external_task_param=")
                || !urlParamDeep(url, "userToken").isEmpty()
                || !urlParamDeep(url, "third_token").isEmpty()
                || !urlParamDeep(url, "tToken").isEmpty();
    }

    private static boolean isSelfScene(String sceneCode) {
        for (String scene : TAOBAO_SELF_SCENES) {
            if (scene.equals(sceneCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 链接参数取值的深度版本：先取本层参数；取不到时再取本层的 {@code targetUrl/url} 参数，
     * 解码后在内层链接里继续找（任务链接常把真实落地页编码在 targetUrl 里）。
     */
    private static String urlParamDeep(String url, String key) {
        String value = urlParam(url, key);
        if (!value.isEmpty()) {
            return value;
        }
        String nested = urlParam(url, "targetUrl");
        if (nested.isEmpty()) {
            nested = urlParam(url, "url");
        }
        return nested.isEmpty() ? "" : urlParam(nested, key);
    }

    /** 阶梯任务下发的动作码 + 还需上报次数（totalProgress-currentProgress）。 */
    private static final class LadderAction {
        final String code;
        final int times;

        LadderAction(String code, int times) {
            this.code = code;
            this.times = times;
        }
    }

    /** 单次执行内单个动作码的上报次数上限，防止异常数据触发海量请求。 */
    private static final int MAX_TIMES_PER_ACTION = 30;

    /** 单任务单次执行最多推进的档数，避免 rightsTimesLimit 是计数上限（如 100000）时海量调用。 */
    private static final int MAX_RIGHTS_PER_TASK = 20;

    /** 本次应推进的档数：rights 未满时取差值；无 rights 或明显是计数上限（如 100000）时按 1 次。 */
    public static int rightsTimesToDo(int rightsTimes, int rightsTimesLimit) {
        if (rightsTimesLimit <= 0 || rightsTimesLimit <= rightsTimes) {
            return 1;
        }
        if (rightsTimesLimit > MAX_RIGHTS_PER_TASK) {
            // 这类是使用次数/累计次数上限，不是可循环完成的档位，避免重复提交
            return 1;
        }
        return rightsTimesLimit - rightsTimes;
    }

    /** 多档任务完成日志的进度后缀，如（1/3）、（2/3）、（3/3）；单档、无 rights 或计数上限返回空串。 */
    public static String rightsProgressText(int rightsTimes, int rightsTimesLimit, int doneIndex) {
        if (rightsTimesLimit <= 1 || rightsTimesLimit > MAX_RIGHTS_PER_TASK) {
            return "";
        }
        int done = Math.min(rightsTimesLimit, Math.max(1, rightsTimes + doneIndex + 1));
        return "（" + done + "/" + rightsTimesLimit + "）";
    }

    /**
     * 阶梯任务组件（queryGameComponent 响应）里直接带厂商动作码与次数：
     * taskId 形如 {@code qyj_game_pass_level_every_3#3}（动作码#总次数），
     * taskProgressVO 带 currentProgress/totalProgress，还需上报次数 = 两者的差；
     * 厂商一次上报推进 1 个进度（动作码 every_N 只是该进度的等价行为次数，不参与计数）。
     * 同一动作码对应多个累计任务时一次上报会同时累加，按动作码分组取最大差值；不同动作码分别上报。
     * @return null 表示没有该 appId 的阶梯数据（调用方按标题兜底）；空列表表示阶梯任务都已完成。
     */
    private static List<LadderAction> ladderActionsOf(String componentResp, String appId) {
        try {
            if (componentResp == null || componentResp.isEmpty()) {
                return null;
            }
            JSONObject data = new JSONObject(componentResp).optJSONObject("data");
            JSONObject ladder = data == null ? null : data.optJSONObject("ladderTaskComponentVO");
            JSONArray list = ladder == null ? null : ladder.optJSONArray("mainTaskList");
            if (list == null) {
                return null;
            }
            Map<String, Integer> timesByAction = new LinkedHashMap<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null || !appId.equals(item.optString("appId"))) {
                    continue;
                }
                String status = item.optString("taskStatus");
                if ("FINISHED".equals(status) || "RECEIVED".equals(status)
                        || "COMPLETED".equals(status)) {
                    continue;
                }
                String taskId = item.optString("taskId");
                int hash = taskId.indexOf('#');
                if (hash <= 0) {
                    continue;
                }
                JSONObject progress = item.optJSONObject("taskProgressVO");
                int total = progress == null ? 0 : progress.optInt("totalProgress");
                int current = progress == null ? 0 : progress.optInt("currentProgress");
                if (total > 0 && current >= total) {
                    continue;
                }
                int times = Math.max(1, Math.min(MAX_TIMES_PER_ACTION, total - current));
                Log.i("游戏任务▶️阶梯动作码[" + appId + "]#" + taskId + " 状态=" + status
                        + " 进度=" + current + "/" + total + " 需上报=" + times);
                String code = taskId.substring(0, hash);
                Integer old = timesByAction.get(code);
                if (old == null || times > old) {
                    timesByAction.put(code, times);
                }
            }
            List<LadderAction> actions = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : timesByAction.entrySet()) {
                actions.add(new LadderAction(entry.getKey(), entry.getValue()));
            }
            return actions;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 从跳转链接里取 channel：游戏中心链接把它编码在 query 参数里（query=channel%3Dxxx），需要解一层。 */
    private static String channelOf(String url) {
        String channel = urlParam(url, "channel");
        if (!channel.isEmpty()) {
            return channel;
        }
        String query = urlParam(url, "query");
        return query.isEmpty() ? "" : urlParam("?" + query, "channel");
    }

    /** 从跳转链接中取参数值（兼容 URL 编码）；取不到返回空串。 */
    private static String urlParam(String url, String key) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        Pattern p = Pattern.compile("[?&]" + Pattern.quote(key) + "=([^&#]*)");
        java.util.regex.Matcher m = p.matcher(url);
        if (!m.find()) {
            return "";
        }
        String value = m.group(1);
        try {
            return java.net.URLDecoder.decode(value, "UTF-8");
        } catch (Throwable ignored) {
            return value;
        }
    }

    private static String requestString(String method, String data) {
        String response = ApplicationHook.requestString(method, data);
        return response == null ? "" : response;
    }
}
