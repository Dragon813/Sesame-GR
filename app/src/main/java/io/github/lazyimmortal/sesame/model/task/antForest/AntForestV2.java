package io.github.lazyimmortal.sesame.model.task.antForest;

import static io.github.lazyimmortal.sesame.model.normal.base.BaseModel.taskRpcRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.XposedHelpers;
import io.github.lazyimmortal.sesame.data.ConfigV2;
import io.github.lazyimmortal.sesame.data.ModelFields;
import io.github.lazyimmortal.sesame.data.ModelGroup;
import io.github.lazyimmortal.sesame.data.RuntimeInfo;
import io.github.lazyimmortal.sesame.data.TokenConfig;
import io.github.lazyimmortal.sesame.data.modelFieldExt.BooleanModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.EmptyModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.IntegerModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.ListModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectAndCountModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.StringModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.TextModelField;
import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.entity.AlipayAntForestHuntTaskList;
import io.github.lazyimmortal.sesame.entity.AlipayAntForestVitalityTaskList;
import io.github.lazyimmortal.sesame.entity.AlipayMonopolyTaskList;
import io.github.lazyimmortal.sesame.entity.AlipayUser;
import io.github.lazyimmortal.sesame.entity.CollectEnergyEntity;
import io.github.lazyimmortal.sesame.entity.CustomOption;
import io.github.lazyimmortal.sesame.entity.FriendWatch;
import io.github.lazyimmortal.sesame.entity.KVNode;
import io.github.lazyimmortal.sesame.entity.RpcEntity;
import io.github.lazyimmortal.sesame.entity.VitalityBenefit;
import io.github.lazyimmortal.sesame.entity.AlipayForestHunt;
import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.hook.Toast;
import io.github.lazyimmortal.sesame.model.base.TaskCommon;
import io.github.lazyimmortal.sesame.model.extensions.ExtensionsHandle;
import io.github.lazyimmortal.sesame.model.normal.base.BaseModel;
import io.github.lazyimmortal.sesame.model.task.antFarm.AntFarm.TaskStatus;
import io.github.lazyimmortal.sesame.model.task.antFarm.AntFarmRpcCall;
import io.github.lazyimmortal.sesame.model.task.antGame.GameTask;
import io.github.lazyimmortal.sesame.rpc.intervallimit.FixedOrRangeIntervalLimit;
import io.github.lazyimmortal.sesame.rpc.intervallimit.RpcIntervalLimit;
import io.github.lazyimmortal.sesame.ui.ObjReference;
import io.github.lazyimmortal.sesame.util.AverageMath;
import io.github.lazyimmortal.sesame.util.FileUtil;
import io.github.lazyimmortal.sesame.util.JsonUtil;
import io.github.lazyimmortal.sesame.util.ListUtil;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.NotificationUtil;
import io.github.lazyimmortal.sesame.util.RandomUtil;
import io.github.lazyimmortal.sesame.util.Statistics;
import io.github.lazyimmortal.sesame.util.Status;
import io.github.lazyimmortal.sesame.util.StringUtil;
import io.github.lazyimmortal.sesame.util.TimeUtil;
import io.github.lazyimmortal.sesame.util.idMap.AntForestHuntTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.AntForestVitalityTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.MonopolyTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.PathThemeMapListMap;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;
import io.github.lazyimmortal.sesame.util.idMap.VitalityBenefitIdMap;
import lombok.Getter;

/**
 * 蚂蚁森林V2
 */
public class AntForestV2 extends ModelTask {

    private static final String TAG = AntForestV2.class.getSimpleName();

    /**
     * 新版保护地任务：只有洪山动物园区域下发了任务场景，其余区域没有任务列表。
     */
    private static String MONOPOLY_REGION_HSDWY = "hongshandongwuyuan";
    private static final String MONOPOLY_TASK_SCENE_HSDWY = "ANTFOREST_MONOPOLY_TASK_HSDWY";

    /**
     * 新版保护地任务的浏览时长上限（秒）：服务端下发 timeCount 后要等这么久才能提交完成，
     * 超出上限视为异常数据，不阻塞本轮任务。
     */
    private static final long MONOPOLY_MAX_TASK_SECONDS = 300L;

    private static final AverageMath offsetTimeMath = new AverageMath(5);

    private static final Map<String, Long> usingProps = new ConcurrentHashMap<>();

    private static final Map<String, String> dressMap;

    private static final Set<String> AntForestTaskTypeSet;

    static {
        dressMap = new HashMap<>();
        // position To positionType
        dressMap.put("tree__main", "treeMain");
        dressMap.put("bg__sky_0", "bgSky0");
        dressMap.put("bg__sky_cloud", "bgSkyCloud");
        dressMap.put("bg__ground_a", "bgGroundA");
        dressMap.put("bg__ground_b", "bgGroundB");
        dressMap.put("bg__ground_c", "bgGroundC");
        // positionType To position
        dressMap.put("treeMain", "tree__main");
        dressMap.put("bgSky0", "bg__sky_0");
        dressMap.put("bgSkyCloud", "bg__sky_cloud");
        dressMap.put("bgGroundA", "bg__ground_a");
        dressMap.put("bgGroundB", "bg__ground_b");
        dressMap.put("bgGroundC", "bg__ground_c");

        AntForestTaskTypeSet = new HashSet<>();
        AntForestTaskTypeSet.add("VITALITYQIANDAOPUSH"); //
        AntForestTaskTypeSet.add("ONE_CLICK_WATERING_V1"); // 给随机好友一键浇水
        AntForestTaskTypeSet.add("GYG_YUEDU_2"); // 去森林图书馆逛15s
        AntForestTaskTypeSet.add("GYG_TBRS"); // 逛一逛淘宝人生
        AntForestTaskTypeSet.add("TAOBAO_tab2_2023"); // 去淘宝看科普视频
        AntForestTaskTypeSet.add("GYG_diantao"); // 逛一逛点淘得红包
        AntForestTaskTypeSet.add("GYG-taote"); // 逛一逛淘宝特价版
        AntForestTaskTypeSet.add("NONGCHANG_20230818"); // 逛一逛淘宝芭芭农场
        // AntForestTaskTypeSet.add("GYG_haoyangmao_20240103");//逛一逛淘宝薅羊毛
        // AntForestTaskTypeSet.add("YAOYIYAO_0815");//去淘宝摇一摇领奖励
        // AntForestTaskTypeSet.add("GYG-TAOCAICAI");//逛一逛淘宝买菜
    }

    private final AtomicInteger taskCount = new AtomicInteger(0);

    private String selfId;

    private Integer tryCountInt;

    private Integer retryIntervalInt;

    private Integer advanceTimeInt;

    private Integer checkIntervalInt;

    private FixedOrRangeIntervalLimit collectIntervalEntity;

    private FixedOrRangeIntervalLimit doubleCollectIntervalEntity;

    private final AverageMath delayTimeMath = new AverageMath(5);

    private final ObjReference<Long> collectEnergyLockLimit = new ObjReference<>(0L);

    private final Object usePropLockObj = new Object();

    private BooleanModelField collectEnergy;
    private BooleanModelField expiredEnergy;
    private BooleanModelField energyRain;
    private IntegerModelField advanceTime;
    private IntegerModelField tryCount;
    private IntegerModelField retryInterval;
    private SelectModelField dontCollectList;

    private BooleanModelField drawGameCenterAward;
    private ChoiceModelField CollectSelfEnergyType;

    private IntegerModelField CollectSelfEnergyThreshold;
    private IntegerModelField collectRobExpandEnergy;
    private BooleanModelField collectWateringBubble;
    private BooleanModelField batchRobEnergy;
    private BooleanModelField balanceNetworkDelay;
    //PK能量
    private BooleanModelField pkEnergy;
    private ChoiceModelField whackModeName;
    private IntegerModelField whackModeGames;
    private IntegerModelField whackModeCount;
    private IntegerModelField earliestwhackMoleTime;

    // 定义运行模式名称数组（需提前声明，与原 Kotlin 中的 whackMoleModeNames 对应）

    private BooleanModelField collectProp;
    private StringModelField queryInterval;
    private StringModelField collectInterval;
    private StringModelField doubleCollectInterval;
    private ChoiceModelField doubleClickType;
    private ListModelField.ListJoinCommaToStringModelField doubleCardTime;
    @Getter
    private IntegerModelField doubleCountLimit;
    private IntegerModelField CollectBombEnergyLimit;
    private BooleanModelField useEnergyRainLimit;
    private BooleanModelField doubleCardConstant;
    private ChoiceModelField helpFriendCollectType;
    private SelectModelField helpFriendCollectList;

    private IntegerModelField helpFriendCollectListLimit;
    private IntegerModelField returnWater33;
    private IntegerModelField returnWater18;
    private IntegerModelField returnWater10;
    private BooleanModelField receiveForestTaskAward;

    private BooleanModelField AutoAntForestVitalityTaskList;
    private SelectModelField AntForestVitalityTaskList;
    private ChoiceModelField waterFriendType;
    private BooleanModelField waterFriendEnergySendChat;

    private BooleanModelField waterFriendEnergyFirst;
    private SelectAndCountModelField waterFriendList;

    private SelectAndCountModelField wateredFriendList;

    private BooleanModelField doubleWaterFriendEnergy;
    private SelectModelField giveEnergyRainList;
    private BooleanModelField vitalityExchangeBenefit;
    private SelectAndCountModelField vitality_ExchangeBenefitList;
    private BooleanModelField userPatrol;
    private BooleanModelField monopolyPatrol;
    private BooleanModelField monopolyTasks;
    private BooleanModelField newMonopolyExchangeTree;

    private BooleanModelField preNewMonopolyExchangeTree;

    private BooleanModelField switchNewMonopolyMap;
    private BooleanModelField monopolyAnimalEnergy;
    private BooleanModelField monopolyDispatchAnimal;
    private ChoiceModelField monopolyDispatchPriority;
    private BooleanModelField AutoMonopolyTaskList;
    private SelectModelField MonopolyTaskList;
    private BooleanModelField collectGiftBox;
    private BooleanModelField medicalHealth;
    private BooleanModelField greenLife;

    private BooleanModelField greenRent;
    private BooleanModelField combineAnimalPiece;
    private ChoiceModelField consumeAnimalPropType;
    private SelectModelField whoYouWantToGiveTo;
    private BooleanModelField ecoLife;
    private BooleanModelField youthPrivilege;
    private SelectModelField ecoLifeOptions;
    private BooleanModelField dress;
    private TextModelField dressDetailList;

    private static int totalCollected = 0;
    private static int totalHelpCollected = 0;
    private static boolean hasErrorWait = false;

    @Getter
    private Set<String> dontCollectMap = new HashSet<>();

    @Override
    public String getName() {
        return "森林";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.FOREST;
    }

    private BooleanModelField loveteamWater;
    private IntegerModelField loveteamWaterNum;

    private BooleanModelField partnerteamWater;
    private IntegerModelField partnerteamWaterNum;
    private IntegerModelField partnerteamWaterMax;
    private BooleanModelField ForestHunt;
    private BooleanModelField AutoAntForestHuntTaskList;
    private SelectModelField AntForestHuntTaskList;
    private BooleanModelField ForestHuntDraw;
    private BooleanModelField ForestHuntHelp;
    private SelectModelField ForestHuntHelpList;

    private SelectModelField continuousUseCardOptions;

    private IntegerModelField continuousUseShieldHour;
    private BooleanModelField NORMALForestHuntHelp;
    private BooleanModelField ACTIVITYForestHuntHelp;

    private BooleanModelField energyPvp;

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(collectEnergy = new BooleanModelField("collectEnergy", "收集能量", false));
        modelFields.addField(dontCollectList = new SelectModelField("dontCollectList", "不收取能量列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(batchRobEnergy = new BooleanModelField("batchRobEnergy", "一键收取", false));
        modelFields.addField(CollectSelfEnergyType = new ChoiceModelField("CollectSelfEnergyType", "收单个能量球 | " + "方式", CollectSelfType.ALL, CollectSelfType.nickNames));
        modelFields.addField(CollectSelfEnergyThreshold = new IntegerModelField("CollectSelfEnergyThreshold", "收单个能量球阈值", 0, 0, 10000));
        modelFields.addField(pkEnergy = new BooleanModelField("pkEnergy", "Pk榜收取 | 开关", false));
        modelFields.addField(energyPvp = new BooleanModelField("energyPvp", "1V1能量挑战 | 开关", false));
        modelFields.addField(collectWateringBubble = new BooleanModelField("collectWateringBubble", "收取金球", false));
        modelFields.addField(wateredFriendList = new SelectAndCountModelField("wateredFriendList", "统计 | 应被好友浇水", new LinkedHashMap<>(), AlipayUser::getList, "请填写被浇水次数(用于核对金球)"));
        modelFields.addField(collectRobExpandEnergy = new IntegerModelField("collectRobExpandEnergy", "倍卡额外能量(大于该值收取)", 100, 0, 1000000));
        modelFields.addField(expiredEnergy = new BooleanModelField("expiredEnergy", "收取过期能量", false));
        modelFields.addField(queryInterval = new StringModelField("queryInterval", "查询间隔(毫秒或毫秒范围)", "500-1000"));
        modelFields.addField(collectInterval = new StringModelField("collectInterval", "收取间隔" + "(毫秒或毫秒范围)", "1000" + "-1500"));
        modelFields.addField(doubleCollectInterval = new StringModelField("doubleCollectInterval", "双击间隔(毫秒或毫秒范围)", "50-150"));
        modelFields.addField(balanceNetworkDelay = new BooleanModelField("balanceNetworkDelay", "平衡网络延迟", true));
        modelFields.addField(advanceTime = new IntegerModelField("advanceTime", "提前时间(毫秒)", 0, Integer.MIN_VALUE, 500));
        modelFields.addField(tryCount = new IntegerModelField("tryCount", "尝试收取(次数)", 1, 0, 10));
        modelFields.addField(retryInterval = new IntegerModelField("retryInterval", "重试间隔(毫秒)", 1000, 0, 10000));
        modelFields.addField(drawGameCenterAward = new BooleanModelField("drawGameCenterAward", "森林乐园 | 游戏宝箱", true));
        modelFields.addField(CollectBombEnergyLimit = new IntegerModelField("CollectBombEnergyLimit", "单个炸弹能量大于该值收取", 0, 0, 100000));
        modelFields.addField(continuousUseCardOptions = new SelectModelField("continuousUseCardOptions", "连续兑换使用道具卡片 | 选项", new LinkedHashSet<>(), CustomOption::getContinuousUseCardOptions, "光盘行动需要先手动完成一次"));
        modelFields.addField(continuousUseShieldHour = new IntegerModelField("continuousUseShieldHour", "剩余小时数接续使用保护罩", 24, 1, 168));
        //modelFields.addField(doubleClickType = new ChoiceModelField("doubleClickType", "双击卡 | " + "自动使用", UsePropType.CLOSE, UsePropType.nickNames));
        //modelFields.addField(doubleCountLimit = new IntegerModelField("doubleCountLimit", "双击卡 | " + "使用次数", 6));
        //modelFields.addField(doubleCardTime = new ListModelField.ListJoinCommaToStringModelField("doubleCardTime", "双击卡 | 使用时间(范围)", ListUtil.newArrayList("0700" + "-0730")));
        //modelFields.addField(doubleCardConstant = new BooleanModelField("DoubleCardConstant", "双击卡 | 限时双击永动机", false));
        modelFields.addField(returnWater10 = new IntegerModelField("returnWater10", "返水 | 10克需收能量" + "(关闭:0)", 0));
        modelFields.addField(returnWater18 = new IntegerModelField("returnWater18", "返水 | 18克需收能量" + "(关闭:0)", 0));
        modelFields.addField(returnWater33 = new IntegerModelField("returnWater33", "返水 | 33克需收能量" + "(关闭:0)", 0));
        modelFields.addField(waterFriendType = new ChoiceModelField("waterFriendType", "浇水 | 动作", WaterFriendType.WATER_00, WaterFriendType.nickNames));
        modelFields.addField(waterFriendList = new SelectAndCountModelField("waterFriendList", "浇水 | 好友列表", new LinkedHashMap<>(), AlipayUser::getList, "请填写浇水次数(每日)"));
        modelFields.addField(waterFriendEnergySendChat = new BooleanModelField("waterFriendEnergySendChat", "浇水 | 发送已浇水提醒", false));
        modelFields.addField(waterFriendEnergyFirst = new BooleanModelField("waterFriendEnergyFirst", "浇水 | 每次首先执行", false));
        modelFields.addField(doubleWaterFriendEnergy = new BooleanModelField("doubleWaterFriendEnergy", "浇水 | 强制检查重复一次", false));
        modelFields.addField(helpFriendCollectType = new ChoiceModelField("helpFriendCollectType", "复活能量 | 动作", HelpFriendCollectType.NONE, HelpFriendCollectType.nickNames));
        modelFields.addField(helpFriendCollectList = new SelectModelField("helpFriendCollectList", "复活能量 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(helpFriendCollectListLimit = new IntegerModelField("helpFriendCollectListLimit", "复活好友能量下限(大于该值复活)", 0, 0, 100000));
        modelFields.addField(vitalityExchangeBenefit = new BooleanModelField("vitalityExchangeBenefit", "活力值 | 兑换权益", false));
        modelFields.addField(vitality_ExchangeBenefitList = new SelectAndCountModelField("vitality_ExchangeBenefitList", "活力值 | 权益列表", new LinkedHashMap<>(), VitalityBenefit::getList, "请填写兑换次数(每日)"));
        modelFields.addField(whackModeName = new ChoiceModelField("whackModeName", "6秒拼手速 | 运行模式", whackModeNames.CLOSE, whackModeNames.nickNames));
        modelFields.addField(whackModeGames = new IntegerModelField("whackModeGames", "6秒拼手速 | 激进模式局数", 5));
        modelFields.addField(whackModeCount = new IntegerModelField("whackModeCount", "6秒拼手速 | 兼容模式击打数", 15));
        modelFields.addField(earliestwhackMoleTime = new IntegerModelField("earliestwhackMoleTime", "6秒拼手速 | 最早执行(24小时制)", 8, 0, 23));
        modelFields.addField(collectProp = new BooleanModelField("collectProp", "收集道具", false));
        modelFields.addField(whoYouWantToGiveTo = new SelectModelField("whoYouWantToGiveTo", "赠送道具好友列表", new LinkedHashSet<>(), AlipayUser::getList, "会赠送所有可送道具都给已选择的好友"));
        modelFields.addField(energyRain = new BooleanModelField("energyRain", "收集能量雨", false));
        modelFields.addField(giveEnergyRainList = new SelectModelField("giveEnergyRainList", "赠送能量雨好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(useEnergyRainLimit = new BooleanModelField("useEnergyRainLimit", "兑换使用限时能量雨卡", false));
        modelFields.addField(userPatrol = new BooleanModelField("userPatrol", "旧版保护地巡护", false));
        modelFields.addField(combineAnimalPiece = new BooleanModelField("combineAnimalPiece", "合成动物碎片", false));
        modelFields.addField(consumeAnimalPropType = new ChoiceModelField("consumeAnimalPropType", "派遣动物伙伴", ConsumeAnimalPropType.NONE, ConsumeAnimalPropType.nickNames));
        modelFields.addField(monopolyDispatchAnimal = new BooleanModelField("monopolyDispatchAnimal", "新版动物伙伴 | 自动派遣", false));
        modelFields.addField(monopolyDispatchPriority = new ChoiceModelField("monopolyDispatchPriority", "新版动物伙伴 | 派遣优先级", 0, new String[]{"新版优先", "旧版优先"}));
        modelFields.addField(monopolyPatrol = new BooleanModelField("monopolyPatrol", "新版保护地 | 自动前进", false));
        modelFields.addField(monopolyAnimalEnergy = new BooleanModelField("monopolyAnimalEnergy", "新版动物伙伴 | 领取能量", false));
        modelFields.addField(monopolyTasks = new BooleanModelField("monopolyTasks", "新版保护地 | 自动任务", false));
        modelFields.addField(newMonopolyExchangeTree = new BooleanModelField("newMonopolyExchangeTree", "新版保护地 | 领取证书", false));
        modelFields.addField(preNewMonopolyExchangeTree = new BooleanModelField("preNewMonopolyExchangeTree", "新版保护地 | 提前兑换未开始保护地证书", false));
        modelFields.addField(switchNewMonopolyMap = new BooleanModelField("switchNewMonopolyMap", "新版保护地 | 切换最新可保护地图", false));
        modelFields.addField(AutoMonopolyTaskList = new BooleanModelField("AutoMonopolyTaskList", "新版保护地 | 推荐黑白名单", true));
        modelFields.addField(MonopolyTaskList = new SelectModelField("MonopolyTaskList", "新版保护地 | 黑白名单列表", new LinkedHashSet<>(), AlipayMonopolyTaskList::getList));
        modelFields.addField(receiveForestTaskAward = new BooleanModelField("receiveForestTaskAward", "森林任务", false));
        modelFields.addField(AutoAntForestVitalityTaskList = new BooleanModelField("AutoAntForestVitalityTaskList", "活力值 | 推荐黑白名单", true));
        modelFields.addField(AntForestVitalityTaskList = new SelectModelField("AntForestVitalityTaskList", "活力值 | 黑名单列表", new LinkedHashSet<>(), AlipayAntForestVitalityTaskList::getList));
        modelFields.addField(collectGiftBox = new BooleanModelField("collectGiftBox", "领取礼盒", false));
        modelFields.addField(medicalHealth = new BooleanModelField("medicalHealth", "医疗健康", false));
        modelFields.addField(greenLife = new BooleanModelField("greenLife", "森林集市", false));
        modelFields.addField(greenRent = new BooleanModelField("greenRent", "绿色租赁", false));
        modelFields.addField(youthPrivilege = new BooleanModelField("youthPrivilege", "青春特权 | 森林道具", false));
        modelFields.addField(ecoLife = new BooleanModelField("ecoLife", "绿色行动 | 开启", false));
        modelFields.addField(ecoLifeOptions = new SelectModelField("ecoLifeOptions", "绿色行动 | 选项", new LinkedHashSet<>(), CustomOption::getEcoLifeOptions, "光盘行动需要先手动完成一次"));
        modelFields.addField(partnerteamWater = new BooleanModelField("partnerteamWater", "组队合种浇水", false));
        modelFields.addField(partnerteamWaterNum = new IntegerModelField("partnerteamWaterNum", "组队合种浇水" + "(g)", 10, 10, 5000));
        modelFields.addField(partnerteamWaterMax = new IntegerModelField("partnerteamWaterMax", "组队合种浇水上限（0不限)", 0, 0, 99999999));
        modelFields.addField(loveteamWater = new BooleanModelField("loveteamWater", "真爱合种浇水", false));
        modelFields.addField(loveteamWaterNum = new IntegerModelField("loveteamWaterNum", "真爱合种浇水" + "(g)", 20, 20, 10000));
        modelFields.addField(ForestHunt = new BooleanModelField("ForestHunt", "森林寻宝", false));
        modelFields.addField(AutoAntForestHuntTaskList = new BooleanModelField("AutoAntForestHuntTaskList", "抽抽乐任务 | 推荐黑白名单", true));
        modelFields.addField(AntForestHuntTaskList = new SelectModelField("AntForestHuntTaskList", "抽抽乐任务 | 黑名单列表", new LinkedHashSet<>(), AlipayAntForestHuntTaskList::getList));
        modelFields.addField(ForestHuntDraw = new BooleanModelField("ForestHuntDraw", "森林寻宝抽奖", false));
        modelFields.addField(ForestHuntHelp = new BooleanModelField("ForestHuntHelp", "森林寻宝助力", false));
        modelFields.addField(NORMALForestHuntHelp = new BooleanModelField("NORMALForestHuntHelp", "普通场景强制助力" + "(助力任务不在列表中时使用，如果日志显示失效请关闭)", false));
        modelFields.addField(ACTIVITYForestHuntHelp = new BooleanModelField("ACTIVITYForestHuntHelp", "活动场景强制助力" + "(同上)", false));
        modelFields.addField(ForestHuntHelpList = new SelectModelField("ForestHuntHelpList", "点击配置寻宝助力列表" + "(填写shareId中开头的22-24位字符在\"4O7FEYDgn\"前的)", new LinkedHashSet<>(), AlipayForestHunt::getList));
        modelFields.addField(dress = new BooleanModelField("dress", "装扮保护 | 开启", false));
        modelFields.addField(dressDetailList = new TextModelField("dressDetailList", "装扮保护 | " + "装扮信息", ""));
        modelFields.addField(new EmptyModelField("dressDetailListClear", "装扮保护 | 装扮信息清除", () -> dressDetailList.reset()));
        return modelFields;
    }

    @Override
    public Boolean check() {
        if (RuntimeInfo.getInstance().getLong(RuntimeInfo.RuntimeInfoKey.ForestPauseTime) > System.currentTimeMillis()) {
            Log.record("异常等待中，暂不执行检测！");
            return false;
        }
        return true;
    }

    @Override
    public Boolean isSync() {
        return true;
    }

    @Override
    public void boot(ClassLoader classLoader) {
        super.boot(classLoader);
        FixedOrRangeIntervalLimit queryIntervalLimit = new FixedOrRangeIntervalLimit(queryInterval.getValue(), 10, 10000);
        RpcIntervalLimit.addIntervalLimit("alipay.antforest.forest.h5.queryHomePage", queryIntervalLimit);
        RpcIntervalLimit.addIntervalLimit("alipay.antforest.forest.h5.queryFriendHomePage", queryIntervalLimit);
        RpcIntervalLimit.addIntervalLimit("alipay.antmember.forest.h5.collectEnergy", 0);
        RpcIntervalLimit.addIntervalLimit("alipay.antmember.forest.h5.queryEnergyRanking", 100);
        RpcIntervalLimit.addIntervalLimit("alipay.antforest.forest.h5.fillUserRobFlag", 500);
        tryCountInt = tryCount.getValue();
        retryIntervalInt = retryInterval.getValue();
        advanceTimeInt = advanceTime.getValue();
        checkIntervalInt = BaseModel.getCheckInterval().getValue();
        dontCollectMap = dontCollectList.getValue();
        collectIntervalEntity = new FixedOrRangeIntervalLimit(collectInterval.getValue(), 50, 10000);
        doubleCollectIntervalEntity = new FixedOrRangeIntervalLimit(doubleCollectInterval.getValue(), 10, 5000);
        delayTimeMath.clear();
        AntForestRpcCall.init();
    }

    @Override
    public void run() {
        try {
            Log.record("执行开始-蚂蚁森林");
            NotificationUtil.setStatusTextExec();
            taskCount.set(0);
            selfId = UserIdMap.getCurrentUid();
            hasErrorWait = false;

            //GameTask.Orchard_ncscc.report("农场上车车", 1);
            if (waterFriendEnergyFirst.getValue()) {
                waterFriendEnergy();
            }


            if (useEnergyRainLimit.getValue()) {
                useEnergyRainCard();
            }

            if (energyRain.getValue()) {
                energyRain();
            }

            if (ecoLife.getValue()) {
                ecoLife();
            }

            if (youthPrivilege.getValue()) {
                Privilege.youthPrivilege();
                //Privilege.studentSignInRedEnvelope();
            }
            //连续兑换使用道具卡片
            continuousUseCardOptions();

            if (receiveForestTaskAward.getValue()) {
                vantiepSign();
            }

            JSONObject selfHomeObject = collectSelfEnergy();
            try {
                JSONObject friendsObject = new JSONObject(AntForestRpcCall.queryEnergyRanking());
                if (MessageUtil.checkResultCode(TAG, friendsObject)) {
                    //每天一次: 复用本轮能量榜响应更新好友统计ta的总能量, 不额外发请求; 取不到数据则不打标记下轮重试
                    if (!Status.hasFlagToday("FriendWatch::energySummation")) {
                        int count = FriendWatch.updateEnergySummation(friendsObject);
                        if (count > 0) {
                            Status.flagToday("FriendWatch::energySummation");
                            Log.forest("好友统计🌲好友ta的总能量已更新" + count + "条请移步“好友统计”菜单中查看");
                        }
                    }
                    collectFriendsEnergy(friendsObject, "ordinary");
                    int pos = 20;
                    List<String> idList = new ArrayList<>();
                    JSONArray totalDatas = friendsObject.getJSONArray("totalDatas");
                    while (pos < totalDatas.length()) {
                        JSONObject friend = totalDatas.getJSONObject(pos);
                        idList.add(friend.getString("userId"));
                        pos++;
                        if (pos % 20 == 0) {
                            collectFriendsEnergy(idList, "ordinary");
                            idList.clear();
                        }
                    }
                    if (!idList.isEmpty()) {
                        collectFriendsEnergy(idList, "ordinary");
                    }
                }
                selfHomeObject = collectSelfEnergy();
            } catch (Throwable t) {
                Log.i(TAG, "queryEnergyRanking err:");
                Log.printStackTrace(TAG, t);
            }

            if (!TaskCommon.IS_ENERGY_TIME && selfHomeObject != null) {
                String whackMoleStatus = selfHomeObject.optString("whackMoleStatus");
                if (Objects.equals("CAN_PLAY", whackMoleStatus) || Objects.equals("CAN_INITIATIVE_PLAY", whackMoleStatus) || Objects.equals("NEED_MORE_FRIENDS", whackMoleStatus)) {
                    checkAndHandleWhackMole();
                }
                boolean hasMore = false;
                do {
                    if (hasMore) {
                        hasMore = false;
                        selfHomeObject = querySelfHome();
                    }
                    if (collectWateringBubble.getValue()) {
                        JSONArray wateringBubbles = selfHomeObject.has("wateringBubbles") ? selfHomeObject.getJSONArray("wateringBubbles") : new JSONArray();
                        if (wateringBubbles.length() > 0) {
                            int collected = 0;
                            for (int i = 0; i < wateringBubbles.length(); i++) {
                                JSONObject wateringBubble = wateringBubbles.getJSONObject(i);
                                String bizType = wateringBubble.getString("bizType");
                                String friendShowName = UserIdMap.getShowName(wateringBubble.getString("userId"));
                                switch (bizType) {
                                    case "jiaoshui": {
                                        JSONObject joEnergy = new JSONObject(AntForestRpcCall.collectEnergy(bizType, selfId, wateringBubble.getLong("id")));
                                        if (MessageUtil.checkResultCode("收取[我]的浇水金球", joEnergy)) {
                                            JSONArray bubbles = joEnergy.getJSONArray("bubbles");
                                            for (int j = 0; j < bubbles.length(); j++) {
                                                collected = bubbles.getJSONObject(j).getInt("collectedEnergy");
                                            }

                                            if (collected > 0) {
                                                //记录被浇水次数
                                                Status.wateredFriendToday(wateringBubble.getString("userId"));
                                                FriendWatch.waterFriendWatch(wateringBubble.getString("userId"), true);
                                                Statistics.addData(Statistics.DataType.WATEREDCOUNT, 1);
                                                String msg = "收取金球🍯[" + friendShowName + "]的浇水[" + collected + "g]";
                                                Log.forest(msg + "#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                                                Toast.show(msg);
                                                totalCollected += collected;
                                                Statistics.addData(Statistics.DataType.COLLECTED, collected);
                                            } else {
                                                Log.record("收取[我]的浇水金球失败");
                                            }
                                        }
                                        break;
                                    }
                                    case "fuhuo": {
                                        JSONObject joEnergy = new JSONObject(AntForestRpcCall.collectRebornEnergy());
                                        if (MessageUtil.checkResultCode("收取[我]的复活金球", joEnergy)) {
                                            collected = joEnergy.getInt("energy");
                                            String msg = "收取金球🍯复活[" + collected + "g]";
                                            Log.forest(msg + "#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                                            Toast.show(msg);
                                            totalCollected += collected;
                                            Statistics.addData(Statistics.DataType.COLLECTED, collected);
                                        }
                                        break;
                                    }
                                    case "baohuhuizeng": {
                                        JSONObject joEnergy = new JSONObject(AntForestRpcCall.collectEnergy(bizType, selfId, wateringBubble.getLong("id")));
                                        if (MessageUtil.checkResultCodeString("收取[" + friendShowName + "]的复活回赠金球", joEnergy)) {
                                            JSONArray bubbles = joEnergy.getJSONArray("bubbles");
                                            for (int j = 0; j < bubbles.length(); j++) {
                                                collected = bubbles.getJSONObject(j).getInt("collectedEnergy");
                                            }
                                            if (collected > 0) {
                                                String msg = "收取金球🍯[" + friendShowName + "]复活回赠[" + collected + "g]";
                                                Log.forest(msg + "#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                                                Toast.show(msg);
                                                totalCollected += collected;
                                                Statistics.addData(Statistics.DataType.COLLECTED, collected);
                                            } else {
                                                Log.record("收取[" + friendShowName + "]的复活回赠金球失败");
                                            }
                                        }
                                        break;
                                    }
                                }
                                TimeUtil.sleep(1000L);
                            }
                            if (wateringBubbles.length() >= 20) {
                                hasMore = true;
                            }
                        }
                    }
                    if (collectProp.getValue()) {
                        JSONArray givenProps = selfHomeObject.has("givenProps") ? selfHomeObject.getJSONArray("givenProps") : new JSONArray();
                        if (givenProps.length() > 0) {
                            for (int i = 0; i < givenProps.length(); i++) {
                                JSONObject jo = givenProps.getJSONObject(i);
                                String giveConfigId = jo.getString("giveConfigId");
                                String giveId = jo.getString("giveId");
                                String propName = jo.getJSONObject("propConfig").getString("propName");
                                jo = new JSONObject(AntForestRpcCall.collectProp(giveConfigId, giveId));
                                if (MessageUtil.checkSuccess(TAG, jo)) {
                                    Log.forest("领取道具🎭[" + propName + "]");
                                }
                                TimeUtil.sleep(1000L);
                            }
                            if (givenProps.length() >= 20) {
                                hasMore = true;
                            }
                        }
                    }
                } while (hasMore);
                //JSONArray usingUserProps = selfHomeObject.has("usingUserProps") ? selfHomeObject.getJSONArray("usingUserProps") : new JSONArray();
                //JSONArray usingUserProps = selfHomeObject.has("usingUserPropsNew") ? selfHomeObject.getJSONArray("usingUserPropsNew") : new JSONArray();
                JSONArray usingUserProps;
                if (selfHomeObject.has("usingUserPropsNew")) {
                    usingUserProps = selfHomeObject.getJSONArray("usingUserPropsNew");
                } else {
                    usingUserProps = selfHomeObject.has("usingUserProps") ? selfHomeObject.getJSONArray("usingUserProps") : new JSONArray();
                }
                boolean canConsumeAnimalProp = true;
                if (usingUserProps.length() > 0) {
                    for (int i = 0; i < usingUserProps.length(); i++) {
                        JSONObject jo = usingUserProps.getJSONObject(i);
                        if (!Objects.equals("animal", jo.optString("propGroup"))) {
                            continue;
                        } else {
                            canConsumeAnimalProp = false;
                        }
                        JSONObject extInfo = new JSONObject(jo.getString("extInfo"));
                        int energy = extInfo.optInt("energy", 0);
                        if (energy > 0 && !extInfo.optBoolean("isCollected")) {
                            String propId = jo.getString("propId");
                            String propType = jo.getString("propType");
                            String shortDay = extInfo.getString("shortDay");
                            String animalName = extInfo.getJSONObject("animal").getString("name");
                            jo = new JSONObject(AntForestRpcCall.collectAnimalRobEnergy(propId, propType, shortDay));
                            if (MessageUtil.checkResultCode(TAG, jo)) {
                                Log.forest("动物能量🦩派遣" + animalName + "收取能量[" + energy + "g]");
                            }
                            TimeUtil.sleep(500);
                            break;
                        }
                    }
                }
                //强制重复浇水一次
                if (doubleWaterFriendEnergy.getValue()) {
                    if (!Status.hasFlagToday("Forest::doubleWaterFriendEnergy")) {
                        doubleWaterFriendEnergy();
                    }
                }

                waterFriendEnergy();

                if (pkEnergy.getValue()) {
                    collectPKEnergy();
                }

                // 组队合种浇水
                //if (partnerteamWater.getValue()) {
                //    if (partnerteamWaterNum.getValue() > 0 && partnerteamWaterNum.getValue() <= 5000) {
                //        partnerteamWater(partnerteamWaterNum.getValue());
                //    }
                //}

                //初始任务列表
                if (!Status.hasFlagToday("BlackList::initAntForest")) {
                    initAntForestTaskListMap(AutoAntForestVitalityTaskList.getValue(), AutoAntForestHuntTaskList.getValue(), AutoMonopolyTaskList.getValue(), receiveForestTaskAward.getValue(), ForestHunt.getValue(), monopolyTasks.getValue());
                    Status.flagToday("BlackList::initAntForest");
                }

                // 组队合种浇水
                if (partnerteamWater.getValue()) {
                    teamCooperateWater();
                }
                // 真爱合种浇水
                if (loveteamWater.getValue()) {
                    if (loveteamWaterNum.getValue() >= 20 && loveteamWaterNum.getValue() <= 10000) {
                        loveteam(loveteamWaterNum.getValue());
                    }
                }

                // 森林寻宝
                if (ForestHunt.getValue()) {
                    ForestChouChouLe forestChouChouLe = new ForestChouChouLe();
                    forestChouChouLe.chouChouLe(ForestHuntDraw.getValue(), ForestHuntHelp.getValue(), ForestHuntHelpList.getValue(), NORMALForestHuntHelp.getValue(), ACTIVITYForestHuntHelp.getValue(), AntForestHuntTaskList.getValue());
                }

                if (userPatrol.getValue()) {
                    queryUserPatrol();
                }
                if (combineAnimalPiece.getValue()) {
                    queryAnimalAndPiece();
                }
                if (monopolyPatrol.getValue()) {
                    monopolyPatrol();
                }

                if (newMonopolyExchangeTree.getValue()) {
                    newMonopolyExchangeTree();
                }

                if (monopolyAnimalEnergy.getValue()) {
                    collectMonopolyAnimalEnergy();
                }
                // 动物伙伴自动派遣：新版受独立开关与优先级控制，旧版仍只看"派遣动物伙伴"选择
                Integer monopolyDispatchPriorityValue = monopolyDispatchPriority.getValue();
                boolean newAnimalFirst = monopolyDispatchPriorityValue == null || monopolyDispatchPriorityValue == 0;
                boolean animalDispatched = false;
                if (monopolyDispatchAnimal.getValue() && newAnimalFirst) {
                    animalDispatched = dispatchMonopolyAnimal(canConsumeAnimalProp);
                }
                if (consumeAnimalPropType.getValue() != ConsumeAnimalPropType.NONE) {
                    if (!canConsumeAnimalProp) {
                        Log.record("已经有动物伙伴在巡护森林");
                    } else if (!animalDispatched) {
                        animalDispatched = queryAnimalPropList();
                    }
                }
                if (monopolyDispatchAnimal.getValue() && !animalDispatched && !newAnimalFirst) {
                    dispatchMonopolyAnimal(canConsumeAnimalProp);
                }
                /*
                if (consumeAnimalPropType.getValue() != ConsumeAnimalPropType.NONE) {
                    if (!canConsumeAnimalProp) {
                        Log.record("已经有动物伙伴在巡护森林");
                    } else {
                        queryAnimalPropList();
                    }
                }*/
                if (expiredEnergy.getValue()) {
                    popupTask();
                }

                if (receiveForestTaskAward.getValue()) {
                    queryCommonSign();
                    queryTaskList();
                }

                giveProp();
                if (vitalityExchangeBenefit.getValue()) {
                    vitalityExchangeBenefit();
                }
                /* 森林集市 */
                if (greenLife.getValue()) {
                    greenLife();
                }

                // 绿色租赁
                if (greenRent.getValue()) {
                    if (!Status.hasFlagToday("Forest::greenRent")) {
                        greenRent();
                        Status.flagToday("Forest::greenRent");
                    }
                }

                if (medicalHealth.getValue()) {
                    // 医疗健康 绿色医疗 16g*6能量
                    queryForestEnergy("FEEDS");
                    // 医疗健康 电子小票 4g*10能量
                    queryForestEnergy("BILL");
                }
                if (dress.getValue()) {
                    dress();
                }

                checkAndHandleWhackMole();

                //森林乐园
                if (drawGameCenterAward.getValue()) {
                    doforestgame();
                    queryOptionalPlay();
                }

                //1V1能量挑战
                if (energyPvp.getValue()) {
                    if (updateUserConfigEnergyPvp()) {
                        queryPvpHomeInfo();
                        receivePvpRewards();
                    }

                }
                ForestEnergyInfo();

            }
        } catch (Throwable t) {
            Log.i(TAG, "AntForestV2.run err:");
            Log.printStackTrace(TAG, t);
        } finally {
            try {
                synchronized (AntForestV2.this) {
                    int count = taskCount.get();
                    if (count > 0) {
                        AntForestV2.this.wait(TimeUnit.MINUTES.toMillis(30));
                        count = taskCount.get();
                    }
                    if (count > 0) {
                        Log.record("执行超时-蚂蚁森林");
                    } else if (count == 0) {
                        Log.record("执行结束-蚂蚁森林");
                    } else {
                        Log.record("执行完成-蚂蚁森林");
                    }
                }
            } catch (InterruptedException ie) {
                Log.i(TAG, "执行中断-蚂蚁森林");
            }
            Statistics.save();
            FriendWatch.save();
            NotificationUtil.updateLastExecText("收:" + totalCollected + " 帮:" + totalHelpCollected);
        }
    }

    private void ForestEnergyInfo() {
        //读到的值(含真实0)正常赋值, 读不到保持null, 输出时null用空格代替; 每个RPC独立容错, 最终一定输出一条完整信息
        JSONObject homeJo = null;
        Integer bubblesNumber = null;
        Integer currentEnergy = null;
        Integer totalCertCount = null;
        Integer treeCount = null;
        Integer ancientTreeCount = null;
        Integer reserveCount = null;
        Integer seaPlantCount = null;
        Integer animalCertCount = null;
        Integer totalVitalityAmount = null;
        Integer obtainTotal = null;
        Integer robbedTotal = null;
        String robMost = "";
        Integer dayenergySummation = null;
        Integer dayrank = null;
        Integer weekenergySummation = null;
        Integer weekrank = null;
        Integer totalenergySummation = null;
        Integer totalrank = null;
        String dayenergySummationtop3 = "";
        String weekenergySummationtop3 = "";
        String pkInfo = "";

        //首页: 能量球数量/当前能量/证书明细
        try {
            homeJo = new JSONObject(AntForestRpcCall.queryHomePage());
            if (MessageUtil.checkResultCode(TAG, homeJo)) {
                JSONArray bubbles = homeJo.optJSONArray("bubbles");
                bubblesNumber = bubbles != null ? bubbles.length() : 0;
                JSONObject userBaseInfo = homeJo.optJSONObject("userBaseInfo");
                if (userBaseInfo != null) {
                    currentEnergy = userBaseInfo.optInt("currentEnergy", 0);
                    totalCertCount = userBaseInfo.optInt("totalCertCount", 0);
                    treeCount = userBaseInfo.optInt("treeCount", 0);
                    ancientTreeCount = userBaseInfo.optInt("ancientTreeCount", 0);
                    reserveCount = userBaseInfo.optInt("reserveCount", 0);
                    seaPlantCount = userBaseInfo.optInt("seaPlantCount", 0);
                    animalCertCount = userBaseInfo.optInt("animalCertCount", 0);
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "ForestEnergyInfo 首页查询失败, 相关数据按空格处理:");
            Log.printStackTrace(TAG, th);
        }

        //活力值: 新版queryHomePage不再返回, 改查queryTaskList; 已对照RPC.txt抓包验证真实路径为userVitalityInfo.totalVitalityAmount(另有过期值overDueVitalityAmount)
        try {
            JSONObject vitalityJo = new JSONObject(AntForestRpcCall.queryTaskList());
            if (MessageUtil.checkResultCode(TAG, vitalityJo)) {
                JSONObject userVitalityInfo = vitalityJo.optJSONObject("userVitalityInfo");
                if (userVitalityInfo == null) {
                    userVitalityInfo = vitalityJo.optJSONObject("userVitalityInfoVO");
                }
                if (userVitalityInfo != null && userVitalityInfo.has("totalVitalityAmount")) {
                    totalVitalityAmount = userVitalityInfo.optInt("totalVitalityAmount");
                } else if (vitalityJo.has("totalVitalityAmount")) {
                    totalVitalityAmount = vitalityJo.optInt("totalVitalityAmount");
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "ForestEnergyInfo 活力值查询失败, 按空格处理:");
            Log.printStackTrace(TAG, th);
        }

        //动态首页: 今日收取/被收能量, 今日我收取最多好友
        try {
            JSONObject dynamicsJo = new JSONObject(AntForestRpcCall.queryDynamicsIndex());
            if (MessageUtil.checkSuccess(TAG, dynamicsJo)) {
                JSONObject todayEnergySummary = dynamicsJo.optJSONObject("todayEnergySummary");
                if (todayEnergySummary != null) {
                    obtainTotal = todayEnergySummary.optInt("obtainTotal", 0);
                    robbedTotal = todayEnergySummary.optInt("robbedTotal", 0);
                }
                JSONObject dynamicsRobRank = dynamicsJo.optJSONObject("robRank");
                JSONObject robEntry = dynamicsRobRank != null ? dynamicsRobRank.optJSONObject("ROB") : null;
                if (robEntry != null) {
                    String robContent = robEntry.optString("content", "");
                    if (!robContent.isEmpty()) {
                        robMost = "偷取最多[" + robEntry.optString("displayName", "") + "]" + robContent + ";";
                    }
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "ForestEnergyInfo 动态首页查询失败, 相关数据按空格处理:");
            Log.printStackTrace(TAG, th);
        }

        //能量日榜
        try {
            JSONObject dayRankJo = new JSONObject(AntForestRpcCall.queryTopEnergyRanking("energyRank", "day"));
            if (MessageUtil.checkResultCode(TAG, dayRankJo)) {
                JSONObject myself = dayRankJo.optJSONObject("myself");
                if (myself != null) {
                    dayenergySummation = myself.optInt("energySummation", 0);
                    dayrank = myself.optInt("rank", 0);
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "ForestEnergyInfo 能量日榜查询失败, 按空格处理:");
            Log.printStackTrace(TAG, th);
        }

        //能量周榜
        try {
            JSONObject weekRankJo = new JSONObject(AntForestRpcCall.queryTopEnergyRanking("energyRank", "week"));
            if (MessageUtil.checkResultCode(TAG, weekRankJo)) {
                JSONObject myself = weekRankJo.optJSONObject("myself");
                if (myself != null) {
                    weekenergySummation = myself.optInt("energySummation", 0);
                    weekrank = myself.optInt("rank", 0);
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "ForestEnergyInfo 能量周榜查询失败, 按空格处理:");
            Log.printStackTrace(TAG, th);
        }

        //能量总榜
        try {
            JSONObject totalRankJo = new JSONObject(AntForestRpcCall.queryTopEnergyRanking("energyRank", "total"));
            if (MessageUtil.checkResultCode(TAG, totalRankJo)) {
                JSONObject myself = totalRankJo.optJSONObject("myself");
                if (myself != null) {
                    totalenergySummation = myself.optInt("energySummation", 0);
                    totalrank = myself.optInt("rank", 0);
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "ForestEnergyInfo 能量总榜查询失败, 按空格处理:");
            Log.printStackTrace(TAG, th);
        }

        //偷我日榜top3
        try {
            JSONObject robDayJo = new JSONObject(AntForestRpcCall.queryTopEnergyRanking("robRank", "day"));
            if (MessageUtil.checkResultCode(TAG, robDayJo)) {
                JSONArray friendRankings = robDayJo.optJSONArray("friendRanking");
                if (friendRankings != null) {
                    for (int i = 0; i < Math.min(friendRankings.length(), 3); i++) {
                        JSONObject friendRanking = friendRankings.optJSONObject(i);
                        if (friendRanking == null) {
                            continue;
                        }
                        int energySummation = friendRanking.optInt("energySummation", 0);
                        if (energySummation == 0) {
                            break;
                        }
                        String userId = friendRanking.optString("userId", null);
                        dayenergySummationtop3 = dayenergySummationtop3 + "[" + UserIdMap.getShowName(userId) + "]" + energySummation + "g;";
                    }
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "ForestEnergyInfo 偷我日榜查询失败, 按空处理:");
            Log.printStackTrace(TAG, th);
        }

        //偷我周榜top3
        try {
            JSONObject robWeekJo = new JSONObject(AntForestRpcCall.queryTopEnergyRanking("robRank", "week"));
            if (MessageUtil.checkResultCode(TAG, robWeekJo)) {
                JSONArray friendRankings = robWeekJo.optJSONArray("friendRanking");
                if (friendRankings != null) {
                    for (int i = 0; i < Math.min(friendRankings.length(), 3); i++) {
                        JSONObject friendRanking = friendRankings.optJSONObject(i);
                        if (friendRanking == null) {
                            continue;
                        }
                        int energySummation = friendRanking.optInt("energySummation", 0);
                        if (energySummation == 0) {
                            break;
                        }
                        String userId = friendRanking.optString("userId", null);
                        weekenergySummationtop3 = weekenergySummationtop3 + "[" + UserIdMap.getShowName(userId) + "]" + energySummation + "g;";
                    }
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "ForestEnergyInfo 偷我周榜查询失败, 按空处理:");
            Log.printStackTrace(TAG, th);
        }

        //古树PK赛信息(仅展示): 优先queryTopEnergyChallengeRanking(赛季能量/保护进度/保级线), 失败回退首页energyChallengeRank
        try {
            JSONObject pkJo = new JSONObject(AntForestRpcCall.queryTopEnergyChallengeRanking());
            if (MessageUtil.checkResultCode(TAG, pkJo)) {
                String rankMemberStatus = pkJo.optString("rankMemberStatus", "");
                JSONObject pkMyself = pkJo.optJSONObject("myself");
                if (pkMyself != null && (rankMemberStatus.isEmpty() || "JOIN".equals(rankMemberStatus))) {
                    String pkLevelName = pkMyself.optString("challengeRankLevelName", "");
                    String pkLevel = pkMyself.optString("challengeRankLevel", "");
                    String pkProgress = "";
                    JSONObject ancientTreeProgress = pkMyself.optJSONObject("ancientTreeProgress");
                    if (ancientTreeProgress != null) {
                        pkProgress = "保护" + ancientTreeProgress.optInt("protectCount", 0) + "/" + ancientTreeProgress.optInt("totalCount", 0) + ";";
                    }
                    //预计结算结果: promote晋级/maintain保级/drop降级, 晋级时附下赛季段位名
                    String pkExpect = "";
                    String expect = pkMyself.optString("challengeExpectedSettlementResult", "");
                    if (!expect.isEmpty()) {
                        String expectCN;
                        if ("promote".equals(expect)) {
                            expectCN = "晋级";
                        } else if ("maintain".equals(expect) || "keep".equals(expect)) {
                            expectCN = "保级";
                        } else if ("drop".equals(expect) || "degrade".equals(expect) || "demote".equals(expect)) {
                            expectCN = "降级";
                        } else {
                            expectCN = expect;
                        }
                        pkExpect = "预计" + expectCN;
                    }
                    String pkLines = "";
                    String pkQuota = "";
                    String pkSettle = "";
                    JSONObject rankDisplayParamsVO = pkJo.optJSONObject("rankDisplayParamsVO");
                    if (rankDisplayParamsVO != null) {
                        if ("promote".equals(expect) && rankDisplayParamsVO.has("nextRankLevelName")) {
                            pkExpect += "[" + rankDisplayParamsVO.optString("nextRankLevelName", "") + "]";
                        }
                        if (!pkExpect.isEmpty()) {
                            pkExpect += ";";
                        }
                        if (rankDisplayParamsVO.has("promoteLineEnergy")) {
                            pkLines += "晋级线" + rankDisplayParamsVO.optInt("promoteLineEnergy", 0) + ";";
                        }
                        if (rankDisplayParamsVO.has("safeLineEnergy")) {
                            pkLines += "保级线" + rankDisplayParamsVO.optInt("safeLineEnergy", 0) + ";";
                        }
                        if (rankDisplayParamsVO.has("promoteCount") || rankDisplayParamsVO.has("maintainCount") || rankDisplayParamsVO.has("dropCount")) {
                            pkQuota = "名额晋" + rankDisplayParamsVO.optInt("promoteCount", 0) + "/保" + rankDisplayParamsVO.optInt("maintainCount", 0) + "/降" + rankDisplayParamsVO.optInt("dropCount", 0) + ";";
                        }
                        long settlementStartTime = rankDisplayParamsVO.optLong("settlementStartTime", 0);
                        if (settlementStartTime > 0) {
                            pkSettle = "结算" + new java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault()).format(new java.util.Date(settlementStartTime)) + ";";
                        }
                    }
                    //榜前3: friendRanking[]按排名降序 displayName+energySummation, 含本人时附加(本人)标记
                    StringBuilder pkTop3 = new StringBuilder();
                    JSONArray friendRanking = pkJo.optJSONArray("friendRanking");
                    if (friendRanking != null) {
                        String currentUid = UserIdMap.getCurrentUid();
                        int n = Math.min(3, friendRanking.length());
                        for (int i = 0; i < n; i++) {
                            JSONObject item = friendRanking.optJSONObject(i);
                            if (item == null) {
                                continue;
                            }
                            String displayName = item.optString("displayName", "");
                            if (!currentUid.isEmpty() && currentUid.equals(item.optString("userId", ""))) {
                                displayName += "(本人)";
                            }
                            pkTop3.append("第").append(i + 1).append("[").append(displayName)
                                    .append("]").append(item.optInt("energySummation", 0)).append("g;");
                        }
                    }
                    pkInfo = pkLevelName + "LV" + pkLevel + "第" + pkMyself.optInt("rank", 0) + "名:" + pkMyself.optInt("energySummation", 0) + "g;" + pkProgress + pkExpect + pkLines + pkQuota + pkSettle + pkTop3;
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "ForestEnergyInfo PK榜查询失败, 回退首页段位:");
            Log.printStackTrace(TAG, th);
        }
        if (pkInfo.isEmpty() && homeJo != null) {
            //回退: queryHomePage.combineHandlerVOMap.energyChallengeRank(段位名/等级/排名)
            try {
                JSONObject combineHandlerVOMap = homeJo.optJSONObject("combineHandlerVOMap");
                JSONObject energyChallengeRank = combineHandlerVOMap != null ? combineHandlerVOMap.optJSONObject("energyChallengeRank") : null;
                if (energyChallengeRank != null && "SUCCESS".equals(energyChallengeRank.optString("resultCode", ""))) {
                    String pkLevelName = "";
                    JSONArray rankHeadPortraitVOS = energyChallengeRank.optJSONArray("rankHeadPortraitVOS");
                    JSONObject firstRank = rankHeadPortraitVOS != null ? rankHeadPortraitVOS.optJSONObject(0) : null;
                    JSONObject rankExtInfo = firstRank != null ? firstRank.optJSONObject("extInfo") : null;
                    if (rankExtInfo != null) {
                        pkLevelName = rankExtInfo.optString("name", "");
                    }
                    pkInfo = pkLevelName + "LV" + energyChallengeRank.optString("level", "") + "第" + energyChallengeRank.optInt("rank", 0) + "名;";
                }
            } catch (Throwable th) {
                Log.printStackTrace(TAG, th);
            }
        }

        String ForestInfo = "森林榜单🌳[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "](" + UserIdMap.getCurrentUid() + ")收取" + n(obtainTotal) + "g;被收" + n(robbedTotal) + "g;能量球" + n(bubblesNumber) + "个;活力值" + n(totalVitalityAmount) + ";当前能量" + n(currentEnergy) + "g;证书" + n(totalCertCount) + "(种树" + n(treeCount) + "/古树" + n(ancientTreeCount) + "/保护地" + n(reserveCount) + "/海域" + n(seaPlantCount) + "/动物" + n(animalCertCount) + ");" + (dayenergySummationtop3.isEmpty() ? "" : "偷我日榜top3:" + dayenergySummationtop3) + (weekenergySummationtop3.isEmpty() ? "" : "偷我周榜top3:" + weekenergySummationtop3) + robMost + "日榜第" + n(dayrank) + "名:" + n(dayenergySummation) + "g;周榜第" + n(weekrank) + "名:" + n(weekenergySummation) + "g;总榜第" + n(totalrank) + "名:" + n(totalenergySummation) + "g;";
        Log.info(ForestInfo);
        //PK赛信息单独列条(未查到不输出)
        if (!pkInfo.isEmpty()) {
            Log.info("森林比赛🆚[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "](PK赛)" + pkInfo);
        }
    }

    /**
     * 森林榜单数值展示: 读到null(接口失败/字段缺失)显示空格, 读到真实值(含0)正常显示
     */
    private static String n(Integer value) {
        return value == null ? " " : Integer.toString(value);
    }

    /**
     * 新保护地排行好友前3文本: [昵称]步数步(本人标记), 按服务端返回顺序取前3, 遇无数据条目(步数勋章证书均0)即终止
     */
    private static String monopolyTop3Text(JSONArray ranking) {
        if (ranking == null || ranking.length() == 0) {
            return "";
        }
        String currentUid = UserIdMap.getCurrentUid();
        StringBuilder sb = new StringBuilder();
        int picked = 0;
        for (int i = 0; i < ranking.length() && picked < 3; i++) {
            JSONObject entry = ranking.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            int step = entry.optInt("stepCount", 0);
            int medal = entry.optInt("medalCount", 0);
            int map = entry.optInt("mapCount", 0);
            if (step <= 0 && medal <= 0 && map <= 0) {
                break;
            }
            String uid = entry.optString("userId");
            sb.append("/[").append(UserIdMap.getShowName(uid));
            if (uid.equals(currentUid)) {
                sb.append("(本人)");
            }
            sb.append("]").append(step).append("步");
            picked++;
        }
        return sb.length() == 0 ? "" : sb.substring(1);
    }

    private void collectPKEnergy() {
        try {
            JSONObject pkObject = new JSONObject(AntForestRpcCall.queryTopEnergyChallengeRanking());
            if (!MessageUtil.checkResultCode(TAG + "获取PK排行榜失败:", pkObject)) {
                Log.error("获取PK排行榜失败: " + pkObject.optString("resultDesc"));
            } else {
                if (!pkObject.getString("rankMemberStatus").equals("JOIN")) {
                    Log.record("未加入PK排行榜");
                    return;
                }
                collectFriendsEnergy(pkObject, "PK");
                //继续处理靠后的PK好友
                JSONArray totalData = pkObject.optJSONArray("totalData");
                if (totalData == null || totalData.length() == 0) {
                    Log.record("PK好友排行榜为空，跳过");
                    return;
                }
                List<String> pkIdList = new ArrayList<>();
                for (int pos = 20; pos < totalData.length(); pos++) {
                    JSONObject pkFriend = totalData.getJSONObject(pos);
                    String userId = pkFriend.getString("userId");
                    if (Objects.equals(userId, selfId)) {
                        continue; //如果是自己则跳过
                    }
                    pkIdList.add(userId);
                    if (pkIdList.size() == 20) {
                        collectFriendsEnergy(pkIdList, "PK");
                        pkIdList.clear();
                    }
                }
                if (!pkIdList.isEmpty()) {
                    collectFriendsEnergy(pkIdList, "PK");
                }
                Log.record("收取PK能量完成！");
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
    }

    private void notifyMain() {
        if (taskCount.decrementAndGet() < 1) {
            synchronized (AntForestV2.this) {
                AntForestV2.this.notifyAll();
            }
        }
    }

    private JSONObject querySelfHome() {
        JSONObject userHomeObject = null;
        try {
            long start = System.currentTimeMillis();
            userHomeObject = new JSONObject(AntForestRpcCall.queryHomePage());
            long end = System.currentTimeMillis();
            long serverTime = userHomeObject.getLong("now");
            int offsetTime = offsetTimeMath.nextInteger((int) ((start + end) / 2 - serverTime));
            Log.i("服务器时间：" + serverTime + "，本地与服务器时间差：" + offsetTime);
            //兼容组队模式
            if (isTeam(userHomeObject)) {
                JSONObject teamHomeResult = userHomeObject.optJSONObject("teamHomeResult");
                JSONObject mainMember = teamHomeResult != null ? teamHomeResult.optJSONObject("mainMember") : null;
                //取出组队模式下的selfHomeObject
                if (mainMember != null) {
                    Iterator<String> keyIterator = mainMember.keys();
                    while (keyIterator.hasNext()) {
                        String key = keyIterator.next();
                        Object value = mainMember.get(key);
                        //将道具卡详情存为一般森林主页格式，以便统一解析
                        if (key.equals("usingUserProps")) {
                            key = "usingUserPropsNew";
                        }
                        // 核心方法：put()
                        // 效果：存在该 key 则覆盖原值，不存在则新增键值对
                        userHomeObject.put(key, value);
                    }
                }
                //userHomeObject = teamHomeResult != null ? teamHomeResult.optJSONObject("mainMember") : null;
            }
        } catch (Throwable t) {
            Log.printStackTrace(t);
        }
        return userHomeObject;
    }

    private JSONObject queryFriendHome(String userId) {
        JSONObject userHomeObject = null;
        try {
            long start = System.currentTimeMillis();
            userHomeObject = new JSONObject(AntForestRpcCall.queryFriendHomePage(userId));
            long end = System.currentTimeMillis();
            long serverTime = userHomeObject.getLong("now");
            int offsetTime = offsetTimeMath.nextInteger((int) ((start + end) / 2 - serverTime));
            Log.i("服务器时间：" + serverTime + "，本地与服务器时间差：" + offsetTime);
        } catch (Throwable t) {
            Log.printStackTrace(t);
        }
        return userHomeObject;
    }

    private JSONObject collectSelfEnergy() {
        try {
            JSONObject selfHomeObject = querySelfHome();
            if (selfHomeObject != null) {
                if (whackModeName.getValue() == whackModeNames.CLOSE) {
                    JSONObject propertiesObject = selfHomeObject.optJSONObject("properties");
                    if (propertiesObject != null) {
                        if (Objects.equals("Y", propertiesObject.optString("whackMole"))) {
                            if (io.github.lazyimmortal.sesame.model.task.antForest.WhackMole.closeWhackMole()) {
                                Log.record("6秒拼手速关闭成功");
                            } else {
                                Log.record("6秒拼手速关闭失败");
                            }
                        }
                    }
                }
                String nextAction = selfHomeObject.optString("nextAction");
                if ("WhackMole".equalsIgnoreCase(nextAction)) {
                    Log.record("检测到6秒拼手速强制弹窗，先执行拼手速");
                    checkAndHandleWhackMole();
                }
                return collectUserEnergy(UserIdMap.getCurrentUid(), selfHomeObject, "ordinary");
            }
        } catch (Throwable t) {
            Log.printStackTrace(t);
        }
        return null;
    }

    private JSONObject collectFriendEnergy(String userId, String getType) {
        if (hasErrorWait) {
            return null;
        }
        try {
            JSONObject userHomeObject = queryFriendHome(userId);
            if (userHomeObject != null) {
                return collectUserEnergy(userId, userHomeObject, getType);
            }
        } catch (Throwable t) {
            Log.printStackTrace(t);
        }
        return null;
    }

    private JSONObject collectUserEnergy(String userId, JSONObject userHomeObject, String getType) {
        try {
            if (!MessageUtil.checkResultCode(TAG, userHomeObject)) {
                return userHomeObject;
            }

            long serverTime = userHomeObject.getLong("now");
            boolean isSelf = Objects.equals(userId, selfId);
            String userName;
            boolean isCollectEnergy;
            //默认收炸弹能量
            boolean isBombCollectenergy = true;
            if (getType.equals("PK")) {
                JSONObject userBaseInfo = userHomeObject.getJSONObject("userBaseInfo");
                userName = userBaseInfo.optString("displayName") + "(PK森友)";
                isCollectEnergy = true;
            } else {
                userName = UserIdMap.getMaskName(userId);
                isCollectEnergy = collectEnergy.getValue() && !dontCollectMap.contains(userId);
            }

            if (isSelf) {
                Log.record("进入[" + userName + "]的蚂蚁森林");
                updateUsingPropsEndTime(userHomeObject);
            } else {
                if (isCollectEnergy) {
                    JSONArray jaProps = userHomeObject.getJSONArray("usingUserPropsNew");
                    for (int i = 0; i < jaProps.length(); i++) {
                        JSONObject joProp = jaProps.getJSONObject(i);
                        if (Objects.equals("shield", joProp.getString("propGroup"))) {
                            if (joProp.getLong("endTime") > serverTime) {
                                Log.record("[" + userName + "]能量罩保护到[" + TimeUtil.getCommonDateS(joProp.getLong("endTime")) + "]");
                                isCollectEnergy = false;
                                JSONArray jaBubbles = userHomeObject.getJSONArray("bubbles");
                                for (int ii = 0; ii < jaBubbles.length(); ii++) {
                                    JSONObject canbubble = jaBubbles.getJSONObject(ii);
                                    long bubbleId = canbubble.getLong("id");
                                    switch (CollectStatus.valueOf(canbubble.getString("collectStatus"))) {
                                        case AVAILABLE:
                                            break;
                                        case WAITING:
                                            long produceTime = canbubble.getLong("produceTime");
                                            //如果保护罩不能覆盖能量成熟时间
                                            if (produceTime < joProp.getLong("endTime")) {
                                                break;
                                            }
                                            if (checkIntervalInt + checkIntervalInt / 2 > produceTime - serverTime) {
                                                if (hasChildTask(AntForestV2.getBubbleTimerTid(userId, bubbleId))) {
                                                    break;
                                                }
                                                addChildTask(new BubbleTimerTask(userId, bubbleId, produceTime, userName));
                                                Log.record("[" + userName + "]能量保护罩时间[" + TimeUtil.getCommonDate(joProp.getLong("endTime")) + "]#未覆盖能量球成熟时间[" + TimeUtil.getCommonDate(produceTime) + "]");
                                                Log.record("添加蹲点收取🪂[" + userName + "]在[" + TimeUtil.getCommonDate(produceTime) + "]执行");
                                            } else {
                                                Log.i("用户[" + userName + "]能量成熟时间: " + TimeUtil.getCommonDate(produceTime));
                                            }
                                            break;
                                    }
                                }
                                break;
                            }
                        }
                        if (Objects.equals("energyBombCard", joProp.getString("propGroup"))) {
                            if (joProp.getLong("endTime") > serverTime) {
                                Log.record("[" + userName + "]使用了炸弹卡");
                                if (userHomeObject.has("bubbles")) {
                                    JSONArray jaBubbles = userHomeObject.getJSONArray("bubbles");
                                    for (int ii = 0; ii < jaBubbles.length(); ii++) {
                                        JSONObject Bombubble = jaBubbles.getJSONObject(ii);
                                        int remainEnergy = Bombubble.optInt("remainEnergy");
                                        //存在小于预设值
                                        if (remainEnergy < CollectBombEnergyLimit.getValue()) {
                                            isBombCollectenergy = false;
                                        } else {
                                            Log.record("[" + userName + "]炸弹能量[" + remainEnergy + "g]>设定值[" + CollectBombEnergyLimit.getValue() + "g]");
                                            isBombCollectenergy = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!isBombCollectenergy) {
                isCollectEnergy = false;
            }

            if (isCollectEnergy) {
                JSONArray jaBubbles = userHomeObject.getJSONArray("bubbles");
                List<Long> bubbleIdList = new ArrayList<>();
                for (int i = 0; i < jaBubbles.length(); i++) {
                    JSONObject bubble = jaBubbles.getJSONObject(i);
                    int remainEnergy = bubble.optInt("remainEnergy");
                    long bubbleId = bubble.getLong("id");
                    switch (CollectStatus.valueOf(bubble.getString("collectStatus"))) {
                        case AVAILABLE:
                            //用阈值判断单个能量球需收取情况
                            if (CollectSelfEnergyType.getValue() == CollectSelfType.ALL) {
                                bubbleIdList.add(bubbleId);
                            } else if ((CollectSelfEnergyType.getValue() == CollectSelfType.OVER_THRESHOLD) && (remainEnergy >= CollectSelfEnergyThreshold.getValue())) {
                                bubbleIdList.add(bubbleId);
                            } else if (((CollectSelfEnergyType.getValue() == CollectSelfType.BELOW_THRESHOLD) && (remainEnergy <= CollectSelfEnergyThreshold.getValue()))) {
                                bubbleIdList.add(bubbleId);
                            }
                            break;
                        case WAITING:
                            long produceTime = bubble.getLong("produceTime");
                            if (checkIntervalInt + checkIntervalInt / 2 > produceTime - serverTime) {
                                if (hasChildTask(AntForestV2.getBubbleTimerTid(userId, bubbleId))) {
                                    break;
                                }
                                if (CollectSelfEnergyType.getValue() == CollectSelfType.ALL) {
                                    addChildTask(new BubbleTimerTask(userId, bubbleId, produceTime, userName));
                                    Log.record("添加蹲点收取🪂[" + userName + "]在[" + TimeUtil.getCommonDate(produceTime) + "]执行");
                                } else if ((CollectSelfEnergyType.getValue() == CollectSelfType.OVER_THRESHOLD) && (remainEnergy >= CollectSelfEnergyThreshold.getValue())) {
                                    addChildTask(new BubbleTimerTask(userId, bubbleId, produceTime, userName));
                                    Log.record("添加蹲点收取🪂[" + userName + "]在[" + TimeUtil.getCommonDate(produceTime) + "]执行");
                                } else if (((CollectSelfEnergyType.getValue() == CollectSelfType.BELOW_THRESHOLD) && (remainEnergy <= CollectSelfEnergyThreshold.getValue()))) {
                                    addChildTask(new BubbleTimerTask(userId, bubbleId, produceTime, userName));
                                    Log.record("添加蹲点收取🪂[" + userName + "]在[" + TimeUtil.getCommonDate(produceTime) + "]执行");
                                }
                            } else {
                                Log.i("用户[" + userName + "]能量成熟时间: " + TimeUtil.getCommonDate(produceTime));
                            }
                            break;
                    }
                }
                //兼容组队模式
                JSONObject selfHomeObject = new JSONObject(AntForestRpcCall.queryHomePage());
                //不是自己或者是自己不在组队模式全收的情况
                //if (batchRobEnergy.getValue() && (!isSelf || (CollectSelfEnergyType.getValue() == CollectSelfType.ALL && !isTeam(selfHomeObject)))) {
                //不在组队模式全收的情况
                if (batchRobEnergy.getValue() && (CollectSelfEnergyType.getValue() == CollectSelfType.ALL) && !isTeam(selfHomeObject)) {
                    Iterator<Long> iterator = bubbleIdList.iterator();
                    List<Long> batchBubbleIdList = new ArrayList<>();
                    while (iterator.hasNext()) {
                        batchBubbleIdList.add(iterator.next());
                        if (batchBubbleIdList.size() >= 6) {
                            collectEnergy(new CollectEnergyEntity(userId, userHomeObject, AntForestRpcCall.getCollectBatchEnergyRpcEntity(userId, batchBubbleIdList)), userName);
                            batchBubbleIdList = new ArrayList<>();
                        }
                    }
                    int size = batchBubbleIdList.size();
                    if (size > 0) {
                        if (size == 1) {
                            collectEnergy(new CollectEnergyEntity(userId, userHomeObject, AntForestRpcCall.getCollectEnergyRpcEntity(null, userId, batchBubbleIdList.get(0))), userName);
                        } else {
                            collectEnergy(new CollectEnergyEntity(userId, userHomeObject, AntForestRpcCall.getCollectBatchEnergyRpcEntity(userId, batchBubbleIdList)), userName);
                        }
                    }
                } else {
                    for (Long bubbleId : bubbleIdList) {
                        collectEnergy(new CollectEnergyEntity(userId, userHomeObject, AntForestRpcCall.getCollectEnergyRpcEntity(null, userId, bubbleId)), userName);
                    }
                }
            }

            return userHomeObject;
        } catch (Throwable t) {
            Log.i(TAG, "collectUserEnergy err:");
            Log.printStackTrace(TAG, t);
        }
        return null;
    }

    private void collectFriendsEnergy(List<String> idList, String getType) {
        try {
            if (hasErrorWait) {
                return;
            }
            collectFriendsEnergy(new JSONObject(AntForestRpcCall.fillUserRobFlag(new JSONArray(idList).toString())), getType);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    private void collectFriendsEnergy(JSONObject friendsObject, String getType) {
        if (hasErrorWait) {
            return;
        }
        try {
            JSONArray jaFriendRanking = friendsObject.optJSONArray("friendRanking");
            if (jaFriendRanking == null) {
                return;
            }
            for (int i = 0, len = jaFriendRanking.length(); i < len; i++) {
                try {
                    JSONObject friendObject = jaFriendRanking.getJSONObject(i);
                    String userId = friendObject.getString("userId");
                    if (Objects.equals(userId, selfId)) {
                        continue;
                    }
                    JSONObject userHomeObject = null;
                    if (getType.equals("PK")) {
                        boolean collectEnergy = true;
                        if (!friendObject.optBoolean("canCollectEnergy")) {
                            long canCollectLaterTime = friendObject.getLong("canCollectLaterTime");
                            if (canCollectLaterTime <= 0 || (canCollectLaterTime - System.currentTimeMillis() > checkIntervalInt)) {
                                collectEnergy = false;
                            }
                        }
                        if (collectEnergy) {
                            userHomeObject = collectFriendEnergy(userId, getType);
                        }
                    } else {
                        if (collectEnergy.getValue() && !dontCollectMap.contains(userId)) {
                            boolean collectEnergy = true;
                            if (!friendObject.optBoolean("canCollectEnergy")) {
                                long canCollectLaterTime = friendObject.getLong("canCollectLaterTime");
                                if (canCollectLaterTime <= 0 || (canCollectLaterTime - System.currentTimeMillis() > checkIntervalInt)) {
                                    collectEnergy = false;
                                }
                            }
                            if (collectEnergy) {
                                userHomeObject = collectFriendEnergy(userId, getType);
                            } /* else {
                  Log.i("不收取[" + UserIdMap.getNameById(userId) + "], userId=" + userId);
              }*/
                        }

                        if (helpFriendCollectType.getValue() != HelpFriendCollectType.NONE && friendObject.optBoolean("canProtectBubble") && !Status.hasFlagToday("forest::protectBubble")) {
                            boolean isHelpCollect = helpFriendCollectList.getValue().contains(userId);
                            if (helpFriendCollectType.getValue() != HelpFriendCollectType.HELP) {
                                isHelpCollect = !isHelpCollect;
                            }
                            if (isHelpCollect) {
                                if (userHomeObject == null) {
                                    userHomeObject = queryFriendHome(userId);
                                }
                                if (userHomeObject != null) {
                                    protectFriendEnergy(userHomeObject);
                                }
                            }
                        }
                        if (collectGiftBox.getValue() && friendObject.getBoolean("canCollectGiftBox")) {
                            if (userHomeObject == null) {
                                userHomeObject = queryFriendHome(userId);
                            }
                            if (userHomeObject != null) {
                                collectGiftBox(userHomeObject);
                            }
                        }
                    }
                } catch (Exception t) {
                    Log.i(TAG, "collectFriendEnergy err:");
                    Log.printStackTrace(TAG, t);
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    private void collectGiftBox(JSONObject userHomeObject) {
        try {
            JSONObject giftBoxInfo = userHomeObject.optJSONObject("giftBoxInfo");
            JSONObject userEnergy = userHomeObject.optJSONObject("userEnergy");
            String userId = userEnergy == null ? UserIdMap.getCurrentUid() : userEnergy.optString("userId");
            if (giftBoxInfo != null) {
                JSONArray giftBoxList = giftBoxInfo.optJSONArray("giftBoxList");
                if (giftBoxList != null && giftBoxList.length() > 0) {
                    for (int ii = 0; ii < giftBoxList.length(); ii++) {
                        try {
                            JSONObject giftBox = giftBoxList.getJSONObject(ii);
                            String giftBoxId = giftBox.getString("giftBoxId");
                            String title = giftBox.getString("title");
                            JSONObject giftBoxResult = new JSONObject(AntForestRpcCall.collectFriendGiftBox(giftBoxId, userId));
                            if (!MessageUtil.checkResultCode(TAG, giftBoxResult)) {
                                continue;
                            }
                            int energy = giftBoxResult.optInt("energy", 0);
                            Log.forest("礼盒能量🎁[" + UserIdMap.getMaskName(userId) + "-" + title + "]#" + energy + "g");
                            Statistics.addData(Statistics.DataType.COLLECTED, energy);
                        } catch (Throwable t) {
                            Log.printStackTrace(t);
                            break;
                        } finally {
                            TimeUtil.sleep(500);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    private void protectFriendEnergy(JSONObject userHomeObject) {
        try {
            JSONArray wateringBubbles = userHomeObject.optJSONArray("wateringBubbles");
            JSONObject userEnergy = userHomeObject.optJSONObject("userEnergy");
            String userId = userEnergy == null ? UserIdMap.getCurrentUid() : userEnergy.optString("userId");
            if (wateringBubbles != null && wateringBubbles.length() > 0) {
                for (int j = 0; j < wateringBubbles.length(); j++) {
                    try {
                        JSONObject wateringBubble = wateringBubbles.getJSONObject(j);
                        if (!Objects.equals("fuhuo", wateringBubble.getString("bizType"))) {
                            continue;
                        }
                        if (wateringBubble.getJSONObject("extInfo").optInt("restTimes", 0) == 0) {
                            Status.flagToday("forest::protectBubble");
                        }
                        if (!wateringBubble.getBoolean("canProtect")) {
                            continue;
                        }
                        int fullEnergy = wateringBubble.optInt("fullEnergy", 0);
                        if (fullEnergy < helpFriendCollectListLimit.getValue()) {
                            continue;
                        }
                        JSONObject joProtect = new JSONObject(AntForestRpcCall.protectBubble(userId));
                        if (!MessageUtil.checkResultCode(TAG, joProtect)) {
                            continue;
                        }
                        int vitalityAmount = joProtect.optInt("vitalityAmount", 0);

                        String str = "复活能量🚑[" + UserIdMap.getMaskName(userId) + "-" + fullEnergy + "g]" + (vitalityAmount > 0 ? "#活力值+" + vitalityAmount : "");
                        Log.forest(str);
                        totalHelpCollected += fullEnergy;
                        Statistics.addData(Statistics.DataType.HELPED, fullEnergy);

                        break;
                    } catch (Throwable t) {
                        Log.printStackTrace(t);
                        break;
                    } finally {
                        TimeUtil.sleep(500);
                    }
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    private void collectEnergy(CollectEnergyEntity collectEnergyEntity, String username) {
        collectEnergy(collectEnergyEntity, false, username);
    }

    private void collectEnergy(CollectEnergyEntity collectEnergyEntity, Boolean joinThread, String username) {
        if (hasErrorWait) {
            return;
        }
        Runnable runnable = () -> {
            try {
                String userId = collectEnergyEntity.getUserId();
                //usePropBeforeCollectEnergy(userId);
                RpcEntity rpcEntity = collectEnergyEntity.getRpcEntity();
                boolean needDouble = collectEnergyEntity.getNeedDouble();
                boolean needRetry = collectEnergyEntity.getNeedRetry();
                int tryCount = collectEnergyEntity.addTryCount();
                int collected = 0;
                long startTime;
                synchronized (collectEnergyLockLimit) {
                    long sleep;
                    if (needDouble) {
                        collectEnergyEntity.unsetNeedDouble();
                        sleep = doubleCollectIntervalEntity.getInterval() - System.currentTimeMillis() + collectEnergyLockLimit.get();
                    } else if (needRetry) {
                        collectEnergyEntity.unsetNeedRetry();
                        sleep = retryIntervalInt - System.currentTimeMillis() + collectEnergyLockLimit.get();
                    } else {
                        sleep = collectIntervalEntity.getInterval() - System.currentTimeMillis() + collectEnergyLockLimit.get();
                    }
                    if (sleep > 0) {
                        TimeUtil.sleep(sleep);
                    }
                    startTime = System.currentTimeMillis();
                    collectEnergyLockLimit.setForce(startTime);
                }
                ApplicationHook.requestObject(rpcEntity, 0, 0);
                long spendTime = System.currentTimeMillis() - startTime;
                if (balanceNetworkDelay.getValue()) {
                    delayTimeMath.nextInteger((int) (spendTime / 3));
                }
                if (rpcEntity.getHasError()) {
                    String errorCode = (String) XposedHelpers.callMethod(rpcEntity.getResponseObject(), "getString", "error");
                    if (Objects.equals("1004", errorCode)) {
                        if (BaseModel.getWaitWhenException().getValue() > 0) {
                            long waitTime = System.currentTimeMillis() + BaseModel.getWaitWhenException().getValue();
                            RuntimeInfo.getInstance().put(RuntimeInfo.RuntimeInfoKey.ForestPauseTime, waitTime);
                            NotificationUtil.updateStatusText("异常");
                            Log.record("触发异常,等待至" + TimeUtil.getCommonDate(waitTime));
                            hasErrorWait = true;
                            return;
                        }
                        TimeUtil.sleep(600 + RandomUtil.delay());
                    }
                    if (tryCount < tryCountInt) {
                        collectEnergyEntity.setNeedRetry();
                        collectEnergy(collectEnergyEntity, username);
                    }
                    return;
                }
                JSONObject jo = new JSONObject(rpcEntity.getResponseString());
                String resultCode = jo.getString("resultCode");
                if (!"SUCCESS".equalsIgnoreCase(resultCode)) {
                    if ("PARAM_ILLEGAL2".equals(resultCode)) {
                        Log.record("[" + username + "]" + "能量已被收取,取消重试 错误:" + jo.getString("resultDesc"));
                        return;
                    }
                    Log.record("[" + username + "]" + jo.getString("resultDesc"));
                    if (tryCount < tryCountInt) {
                        collectEnergyEntity.setNeedRetry();
                        collectEnergy(collectEnergyEntity, username);
                    }
                    return;
                }

                JSONArray jaBubbles = jo.getJSONArray("bubbles");

                int jaBubbleLength = jaBubbles.length();
                if (jaBubbleLength > 1) {
                    List<Long> newBubbleIdList = new ArrayList<>();
                    for (int i = 0; i < jaBubbleLength; i++) {
                        JSONObject bubble = jaBubbles.getJSONObject(i);
                        if (bubble.getBoolean("canBeRobbedAgain")) {
                            newBubbleIdList.add(bubble.getLong("id"));
                        }
                        collected += bubble.getInt("collectedEnergy");
                    }
                    if (collected > 0) {
                        //PK森友(名字带(PK森友)后缀)不参与好友统计
                        if (username == null || !username.endsWith("(PK森友)")) {
                            FriendWatch.friendWatch(userId, collected, username);
                        }
                        String str;
                        if (jo.has("bombCardEffect")) {
                            JSONObject bombCardEffect = jo.getJSONObject("bombCardEffect");
                            int explodeEnergy = bombCardEffect.optInt("explodeEnergy", 0);
                            str = "一键收取🪂[" + username + "]#" + collected + "g被炸" + explodeEnergy + "g";
                        } else {
                            str = "一键收取🪂[" + username + "]#" + collected + "g";
                        }
                        if (needDouble) {
                            Log.forest(str + "耗时[" + spendTime + "]ms[双击]");
                            Toast.show(str + "[双击]");
                        } else {
                            Log.forest(str + "耗时[" + spendTime + "]ms");
                            Toast.show(str);
                        }
                        totalCollected += collected;
                        Statistics.addData(Statistics.DataType.COLLECTED, collected);
                    } else {
                        Log.record("一键收取[" + username + "]的能量失败" + " " + "，UserID：" + userId + "，BubbleId：" + newBubbleIdList);
                    }
                    if (!newBubbleIdList.isEmpty()) {
                        collectEnergyEntity.setRpcEntity(AntForestRpcCall.getCollectBatchEnergyRpcEntity(userId, newBubbleIdList));
                        collectEnergyEntity.setNeedDouble();
                        collectEnergyEntity.resetTryCount();
                        collectEnergy(collectEnergyEntity, username);
                    }
                } else if (jaBubbleLength == 1) {
                    JSONObject bubble = jaBubbles.getJSONObject(0);
                    collected += bubble.getInt("collectedEnergy");
                    //PK森友(名字带(PK森友)后缀)不参与好友统计
                    if (username == null || !username.endsWith("(PK森友)")) {
                        FriendWatch.friendWatch(userId, collected, username);
                    }
                    if (collected > 0) {
                        String str;
                        if (jo.has("bombCardEffect")) {
                            JSONObject bombCardEffect = jo.getJSONObject("bombCardEffect");
                            int explodeEnergy = bombCardEffect.optInt("explodeEnergy", 0);
                            str = "收取能量🪂[" + username + "]#" + collected + "g被炸" + explodeEnergy + "g";
                        } else {
                            str = "收取能量🪂[" + username + "]#" + collected + "g";
                        }

                        if (needDouble) {
                            Log.forest(str + "耗时[" + spendTime + "]ms[双击]");
                            Toast.show(str + "[双击]");
                        } else {
                            Log.forest(str + "耗时[" + spendTime + "]ms");
                            Toast.show(str);
                        }
                        totalCollected += collected;
                        Statistics.addData(Statistics.DataType.COLLECTED, collected);
                    } else {
                        Log.record("收取[" + username + "]的能量失败");
                        Log.i("，UserID：" + userId + "，BubbleId：" + bubble.getLong("id"));
                    }
                    if (bubble.getBoolean("canBeRobbedAgain")) {
                        collectEnergyEntity.setNeedDouble();
                        collectEnergyEntity.resetTryCount();
                        collectEnergy(collectEnergyEntity, username);
                        return;
                    }
                    JSONObject userHome = collectEnergyEntity.getUserHome();
                    if (userHome == null) {
                        return;
                    }
                    String bizNo = userHome.optString("bizNo");
                    if (bizNo.isEmpty()) {
                        return;
                    }
                    int returnCount = 0;
                    if (returnWater33.getValue() > 0 && collected >= returnWater33.getValue()) {
                        returnCount = 33;
                    } else if (returnWater18.getValue() > 0 && collected >= returnWater18.getValue()) {
                        returnCount = 18;
                    } else if (returnWater10.getValue() > 0 && collected >= returnWater10.getValue()) {
                        returnCount = 10;
                    }
                    if (returnCount > 0) {
                        returnFriendWater(userId, bizNo, 1, returnCount);
                    }
                }
            } catch (Exception e) {
                Log.i("collectEnergy err:");
                Log.printStackTrace(e);
            } finally {
                Statistics.save();
                NotificationUtil.updateLastExecText("收:" + totalCollected + " 帮:" + totalHelpCollected);
                notifyMain();
            }
        };
        taskCount.incrementAndGet();
        if (joinThread) {
            runnable.run();
        } else {
            addChildTask(new ChildModelTask("CE|" + collectEnergyEntity.getUserId() + "|" + runnable.hashCode(), "CE", runnable));
        }
    }

    private void updateUsingPropsEndTime() throws JSONException {
        JSONObject joHomePage = new JSONObject(AntForestRpcCall.queryHomePage());
        TimeUtil.sleep(100);
        updateUsingPropsEndTime(joHomePage);
    }

    private void updateUsingPropsEndTime(JSONObject joHomePage) {
        try {
            JSONArray ja = joHomePage.getJSONArray("loginUserUsingPropNew");
            if (ja.length() == 0) {
                ja = joHomePage.getJSONArray("usingUserPropsNew");
            }
            for (int i = 0; i < ja.length(); i++) {
                JSONObject jo = ja.getJSONObject(i);
                String propGroup = jo.getString("propGroup");
                Long endTime = jo.getLong("endTime");
                String propId = jo.getString("propId");
                String propType = jo.getString("propType");
                usingProps.put(propGroup, endTime);
                if (PropGroup.robExpandCard.name().equals(propGroup)) {
                    collectRobExpandEnergy(jo.optString("extInfo"), propId, propType);
                }
            }
            forestExtensions();
        } catch (Throwable th) {
            Log.i(TAG, "updateUsingPropsEndTime err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private void collectRobExpandEnergy(String extInfo, String propId, String propType) {
        if (extInfo.isEmpty()) {
            return;
        }
        try {
            JSONObject jo = new JSONObject(extInfo);
            double leftEnergy = Double.parseDouble(jo.optString("leftEnergy", "0"));
            if (leftEnergy > collectRobExpandEnergy.getValue() || (Objects.equals(jo.optString("overLimitToday", "false"), "true") && leftEnergy > 0)) {
                collectRobExpandEnergy(propId, propType);
            }
        } catch (Throwable th) {
            Log.i(TAG, "collectRobExpandEnergy err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private void collectRobExpandEnergy(String propId, String propType) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.collectRobExpandEnergy(propId, propType));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                int collectEnergy = jo.optInt("collectEnergy");
                Log.forest("额外能量🎄收取[" + collectEnergy + "g]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                totalCollected += collectEnergy;
                Statistics.addData(Statistics.DataType.COLLECTED, collectEnergy);
            }
        } catch (Throwable th) {
            Log.i(TAG, "collectRobExpandEnergy err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private void queryForestEnergy(String scene) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryForestEnergy(scene));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("data").getJSONObject("response");
            JSONArray ja = jo.getJSONArray("energyGeneratedList");
            if (ja.length() > 0) {
                harvestForestEnergy(scene, ja);
            }
            int remainBubble = jo.optInt("remainBubble");
            for (int i = 0; i < remainBubble; i++) {
                ja = produceForestEnergy(scene);
                if (ja.length() == 0 || !harvestForestEnergy(scene, ja)) {
                    return;
                }
                TimeUtil.sleep(1000);
            }
        } catch (Throwable th) {
            Log.i(TAG, "queryForestEnergy err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private JSONArray produceForestEnergy(String scene) {
        JSONArray energyGeneratedList = new JSONArray();
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.produceForestEnergy(scene));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                jo = jo.getJSONObject("data").getJSONObject("response");
                energyGeneratedList = jo.getJSONArray("energyGeneratedList");
                if (energyGeneratedList.length() > 0) {
                    String title = scene.equals("FEEDS") ? "绿色医疗" : "电子小票";
                    int cumulativeEnergy = jo.getInt("cumulativeEnergy");
                    Log.forest("医疗健康🚑完成[" + title + "]#产生[" + cumulativeEnergy + "g能量]");
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "produceForestEnergy err:");
            Log.printStackTrace(TAG, th);
        }
        return energyGeneratedList;
    }

    private Boolean harvestForestEnergy(String scene, JSONArray bubbles) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.harvestForestEnergy(scene, bubbles));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            jo = jo.getJSONObject("data").getJSONObject("response");
            int collectedEnergy = jo.getInt("collectedEnergy");
            if (collectedEnergy > 0) {
                String title = scene.equals("FEEDS") ? "绿色医疗" : "电子小票";
                Log.forest("医疗健康🚑收取[" + title + "]#获得[" + collectedEnergy + "g能量]");
                totalCollected += collectedEnergy;
                Statistics.addData(Statistics.DataType.COLLECTED, collectedEnergy);
                return true;
            }
        } catch (Throwable th) {
            Log.i(TAG, "harvestForestEnergy err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    /**
     * 检查并处理6秒拼手速逻辑（每天主动执行一次）
     */
    private void whackMole() {
        try {
            if (whackModeName.getValue() == whackModeNames.CLOSE) {
                // 检查今天是否已执行过打地鼠
                if (Status.hasFlagToday("forest::whackMole::executed")) {
                    Log.record("⏭️ 今天已完成过6秒拼手速，跳过执行");
                } else {
                    // 主动执行打地鼠（今日首次）
                    Log.record("🎮 开始执行6秒拼手速（今日首次）");
                    checkAndHandleWhackMole();
                    Status.flagToday("forest::whackMole::executed");
                    Log.record("✅ 6秒拼手速已完成，今天不再执行");
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "whackMole err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void checkAndHandleWhackMole() {
        try {
            // 获取当前选择的索引 (0, 1, 或 2)
            int modeIndex = (whackModeName != null) ? whackModeName.getValue() : 0;

            // 如果索引为 0 (关闭)，直接返回
            if (modeIndex == 0) {
                return;
            }

            // 检查执行时间
            int hour = Integer.parseInt(Log.getFormatTime().split(":")[0]);
            if (hour >= earliestwhackMoleTime.getValue()) {
                String whackMoleFlag = "forest::whackMole::executed";
                if (Status.hasFlagToday(whackMoleFlag)) {
                    return;
                }

                // 根据索引匹配模式
                switch (modeIndex) {
                    case 1: // 兼容模式
                        Log.record("触发任务🎮拼手速:兼容模式");
                        WhackMole.setTotalGames(1);
                        int defaultMoleCount = (whackModeCount != null) ? whackModeCount.getValue() : 15;
                        WhackMole.setMoleCount(defaultMoleCount);
                        WhackMole.start(WhackMole.Mode.COMPATIBLE);
                        break;

                    case 2: // 激进模式
                        Log.record("触发任务🎮拼手速:激进模式");
                        int configGames = (whackModeGames != null) ? whackModeGames.getValue() : 5;
                        WhackMole.setTotalGames(configGames);
                        WhackMole.start(WhackMole.Mode.AGGRESSIVE);
                        break;
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }


    public void initAntForestTaskListMap(boolean AutoAntForestVitalityTaskList, boolean AutoAntForestHuntTaskList, boolean AutoMonopolyTaskList, boolean receiveForestTaskAward, boolean ForestHunt, boolean monopolyTasks) {
        try {

            //初始化AntForestVitalityTaskListMap
            AntForestVitalityTaskListMap.load();
            // 1. 定义黑名单（需要添加的任务）和白名单（需要移除的任务）
            Set<String> blackList = new HashSet<>();
            blackList.add("去蚂蚁阿福健康问答");
            blackList.add("邀请1位好友助力");
            blackList.add("三国大冒险过1关征战");
            blackList.add("添加组件及时收能量");
            blackList.add("去淘宝花花乐领红包");
            blackList.add("到店支付得50g能量");
            blackList.add("践行绿色行为");
            blackList.add("连续7天收自己能量");

            // 可继续添加更多黑名单任务

            Set<String> whiteList = new HashSet<>();// 从黑名单中移除该任务
            whiteList.add("逛农场得落叶肥料");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                AntForestVitalityTaskListMap.add(task, task);
            }

            if (receiveForestTaskAward) {
                JSONObject jo = new JSONObject(AntForestRpcCall.queryTaskList());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray forestTasksNew = jo.optJSONArray("forestTasksNew");
                    if (forestTasksNew != null && forestTasksNew.length() != 0) {
                        for (int i = 0; i < forestTasksNew.length(); i++) {
                            JSONObject forestTask = forestTasksNew.getJSONObject(i);
                            JSONArray taskInfoList = forestTask.optJSONArray("taskInfoList");
                            if (taskInfoList != null && taskInfoList.length() != 0) {
                                for (int j = 0; j < taskInfoList.length(); j++) {
                                    JSONObject taskInfo = taskInfoList.getJSONObject(j);
                                    JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo");
                                    JSONObject bizInfo = new JSONObject(taskBaseInfo.getString("bizInfo"));
                                    String taskType = taskBaseInfo.getString("taskType");
                                    String taskTitle = bizInfo.optString("taskTitle", taskType);
                                    AntForestVitalityTaskListMap.add(taskTitle, taskTitle);
                                }
                            }
                        }
                    }
                }

                jo = new JSONObject(AntForestRpcCall.listTaskopengreen());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    // 添加安全的空值判断
                    if (jo.has("taskInfoList")) {
                        JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
                        if (taskInfoList != null && taskInfoList.length() != 0) {
                            for (int j = 0; j < taskInfoList.length(); j++) {
                                JSONObject taskInfo = taskInfoList.getJSONObject(j);
                                JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo");
                                JSONObject bizInfo = new JSONObject(taskBaseInfo.getString("bizInfo"));
                                String taskType = taskBaseInfo.getString("taskType");
                                String taskTitle = bizInfo.optString("taskTitle", taskType);
                                AntForestVitalityTaskListMap.add(taskTitle, taskTitle);
                            }
                        }
                    }
                }


                //保存任务到配置文件
                AntForestVitalityTaskListMap.save();
                Log.record("同步任务🉑森林活力值任务列表");

                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntForestVitalityTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields AntForestV2 = config.getModelFieldsMap().get("AntForestV2");
                    SelectModelField AntForestVitalityTaskList = (SelectModelField) AntForestV2.get("AntForestVitalityTaskList");
                    if (AntForestVitalityTaskList == null) {
                        return;
                    }

                    // 2. 批量添加黑名单任务（确保存在）
                    Set<String> currentValues = AntForestVitalityTaskList.getValue();//该处直接返回列表地址
                    if (currentValues != null) {
                        for (String task : blackList) {
                            if (!currentValues.contains(task)) {
                                AntForestVitalityTaskList.add(task, 0);
                            }
                        }

                        // 3. 批量移除白名单任务（从现有列表中删除）
                        for (String task : whiteList) {
                            if (currentValues.contains(task)) {
                                currentValues.remove(task);
                            }
                        }
                    }
                    // 4. 保存配置
                    if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
                        Log.record("黑白名单🈲森林活力值任务自动设置: " + AntForestVitalityTaskList.getValue());
                    } else {
                        Log.record("森林活力值任务黑白名单设置失败");
                    }
                }
            }

            //初始化AntForestHuntTaskListMap
            AntForestHuntTaskListMap.load();
            // 1. 定义黑名单（需要添加的任务）和白名单（需要移除的任务）
            blackList = new HashSet<>();
            blackList.add("【限时】玩游戏得2次机会");
            blackList.add("去乐园开宝箱得机会");
            // 可继续添加更多黑名单任务

            whiteList = new HashSet<>();// 从黑名单中移除该任务
            whiteList.add("消耗活力值得机会");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                AntForestHuntTaskListMap.add(task, task);
            }

            if (ForestHunt) {
                JSONObject resData = new JSONObject(AntForestRpcCall.enterDrawActivityopengreen("", "ANTFOREST_NORMAL_DRAW", "task_entry"));
                if (MessageUtil.checkSuccess(TAG, resData)) {
                    JSONArray drawSceneGroups = resData.getJSONArray("drawSceneGroups");
                    for (int i = 0; i < drawSceneGroups.length(); i++) {
                        JSONObject drawScene = drawSceneGroups.getJSONObject(i);
                        JSONObject drawActivity = drawScene.getJSONObject("drawActivity");
                        String sceneCode = drawActivity.getString("sceneCode");
                        JSONObject listTaskopengreen = new JSONObject(AntForestRpcCall.listTaskopengreen(sceneCode + "_TASK", "task_entry"));
                        if (MessageUtil.checkSuccess(TAG, listTaskopengreen)) {
                            JSONArray taskList = listTaskopengreen.getJSONArray("taskInfoList");
                            for (int j = 0; j < taskList.length(); j++) {
                                JSONObject taskInfo = taskList.getJSONObject(j);
                                JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo");
                                JSONObject bizInfo = new JSONObject(taskBaseInfo.getString("bizInfo"));
                                String taskName = bizInfo.getString("title");
                                AntForestHuntTaskListMap.add(taskName, taskName);
                            }
                        }
                    }
                }
                AntForestHuntTaskListMap.save();
                Log.record("同步任务🉑森林抽抽乐任务列表");
                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntForestHuntTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields AntForestV2 = config.getModelFieldsMap().get("AntForestV2");
                    SelectModelField AntForestHuntTaskList = (SelectModelField) AntForestV2.get("AntForestHuntTaskList");
                    if (AntForestHuntTaskList == null) {
                        return;
                    }

                    // 2. 批量添加黑名单任务（确保存在）
                    Set<String> currentValues = AntForestHuntTaskList.getValue();//该处直接返回列表地址
                    if (currentValues != null) {
                        for (String task : blackList) {
                            if (!currentValues.contains(task)) {
                                AntForestHuntTaskList.add(task, 0);
                            }
                        }

                        // 3. 批量移除白名单任务（从现有列表中删除）
                        for (String task : whiteList) {
                            if (currentValues.contains(task)) {
                                currentValues.remove(task);
                            }
                        }
                    }
                    // 4. 保存配置
                    if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
                        Log.record("黑白名单🈲森林抽抽乐任务自动设置: " + AntForestHuntTaskList.getValue());
                    } else {
                        Log.record("森林抽抽乐任务黑白名单设置失败");
                    }
                }


                //初始化MonopolyTaskListMap
                MonopolyTaskListMap.load();
                // 1. 定义黑名单（需要添加的任务）和白名单（需要移除的任务）
                blackList = new HashSet<>();
                blackList.add("添加森林桌面组件");
                // 可继续添加更多黑名单任务

                whiteList = new HashSet<>();// 从黑名单中移除该任务
                //whiteList.add("消耗活力值得机会");
                // 可继续添加更多白名单任务
                for (String task : blackList) {
                    MonopolyTaskListMap.add(task, task);
                }

                if (monopolyTasks) {
                    JSONObject jo = monopolyResponse(AntForestRpcCall.listMonopolyTasks(MONOPOLY_REGION_HSDWY, MONOPOLY_TASK_SCENE_HSDWY));
                    if (MessageUtil.checkSuccess(TAG, jo)) {
                        JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
                        if (taskInfoList != null) {
                            for (int i = 0; i < taskInfoList.length(); i++) {
                                JSONObject task = taskInfoList.optJSONObject(i);
                                JSONObject taskBaseInfo = task.getJSONObject("taskBaseInfo");
                                JSONObject bizInfo = new JSONObject(taskBaseInfo.getString("bizInfo"));
                                String taskTitle = bizInfo.getString("taskTitle");
                                MonopolyTaskListMap.add(taskTitle, taskTitle);
                            }
                        }
                    }
                    MonopolyTaskListMap.save();
                    Log.record("同步任务🉑森林保护地任务列表");
                    //自动按模块初始化设定调整黑名单和白名单
                    if (AutoMonopolyTaskList) {
                        // 初始化黑白名单（使用集合统一操作）
                        ConfigV2 config = ConfigV2.INSTANCE;
                        ModelFields AntForestV2 = config.getModelFieldsMap().get("AntForestV2");
                        SelectModelField MonopolyTaskList = (SelectModelField) AntForestV2.get("MonopolyTaskList");
                        if (MonopolyTaskList == null) {
                            return;
                        }

                        // 2. 批量添加黑名单任务（确保存在）
                        Set<String> currentValues = MonopolyTaskList.getValue();//该处直接返回列表地址
                        if (currentValues != null) {
                            for (String task : blackList) {
                                if (!currentValues.contains(task)) {
                                    MonopolyTaskList.add(task, 0);
                                }
                            }

                            // 3. 批量移除白名单任务（从现有列表中删除）
                            for (String task : whiteList) {
                                if (currentValues.contains(task)) {
                                    currentValues.remove(task);
                                }
                            }
                        }
                        // 4. 保存配置
                        if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
                            Log.record("黑白名单🈲森林保护地任务自动设置: " + MonopolyTaskList.getValue());
                        } else {
                            Log.record("森林保护地任务黑白名单设置失败");
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "initAntForestTaskListMap err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /* 森林集市 */
    private static void greenLife() {
        sendEnergyByAction("GREEN_LIFE");
        //sendEnergyByAction("ANTFOREST");
        retrieveCurrentActivity();
    }

    // 绿色租赁
    private static void greenRent() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.checkUserSecondSceneChance());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            TimeUtil.sleep(200);
            jo = new JSONObject(AntForestRpcCall.generateEnergy());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }

            JSONObject resultObject = jo.getJSONObject("resultObject");
            JSONObject energyGenerated = resultObject.getJSONObject("energyGenerated");
            int zulinshangpinliulan = energyGenerated.getInt("zulinshangpinliulan");
            Log.forest("绿色租赁🛍️完成[线上逛街]#产生[" + zulinshangpinliulan + "g能量]");
            Toast.show("绿色租赁🛍️完成[线上逛街]#产生[" + zulinshangpinliulan + "g能量]");
        } catch (Throwable t) {
            Log.i(TAG, "greenRent err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void retrieveCurrentActivity() {
        try {
            JSONObject jo = new JSONObject(GreenLifeRpcCall.retrieveCurrentActivity());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }

            jo = jo.getJSONObject("data");
            if (!jo.has("currentActivity")) {
                return;
            }
            JSONObject currentActivity = jo.getJSONObject("currentActivity");
            int numberOfDaysCompleted = currentActivity.getInt("numberOfDaysCompleted") + 1;
            JSONObject currentTask = jo.getJSONObject("currentTask");
            if (currentTask.getBoolean("checkInCompleted")) {
                return;
            }
            String taskTemplateId = currentTask.getString("taskTemplateId");
            jo = new JSONObject(GreenLifeRpcCall.finishCurrentTask(taskTemplateId));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("data");
            JSONArray ja = jo.getJSONArray("prizes");
            StringBuilder award = new StringBuilder();
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                if (i > 0) {
                    award.append(";");
                }
                award.append(jo.getString("name"));
            }
            if (award.length() > 0) {
                award = new StringBuilder("#获得[" + award + "]");
            }
            Log.forest("森林集市🛍️打卡[坚持" + numberOfDaysCompleted + "天]" + award);
        } catch (Throwable t) {
            Log.i(TAG, "retrieveCurrentActivity err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void sendEnergyByAction(String sourceType) {
        try {
            JSONObject jo = new JSONObject(GreenLifeRpcCall.consultForSendEnergyByAction(sourceType));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            JSONObject data = jo.getJSONObject("data");
            if (data.optBoolean("canSendEnergy", false)) {
                jo = new JSONObject(GreenLifeRpcCall.sendEnergyByAction(sourceType));
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    data = jo.getJSONObject("data");
                    if (data.optBoolean("canSendEnergy", false)) {
                        int receivedEnergyAmount = data.getInt("receivedEnergyAmount");
                        Log.forest("森林集市🛍️完成[线上逛街]#产生[" + receivedEnergyAmount + "g能量]");
                        Toast.show("森林集市🛍️完成[线上逛街]#产生[" + receivedEnergyAmount + "g能量]");
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "sendEnergyByAction err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void popupTask() {
        try {
            JSONObject resData = new JSONObject(AntForestRpcCall.popupTask());
            if (!MessageUtil.checkResultCode(TAG, resData)) {
                return;
            }
            JSONArray forestSignVOList = resData.optJSONArray("forestSignVOList");
            if (forestSignVOList != null) {
                for (int i = 0; i < forestSignVOList.length(); i++) {
                    JSONObject forestSignVO = forestSignVOList.getJSONObject(i);
                    String signId = forestSignVO.getString("signId");
                    String currentSignKey = forestSignVO.getString("currentSignKey");
                    JSONArray signRecords = forestSignVO.getJSONArray("signRecords");
                    for (int j = 0; j < signRecords.length(); j++) {
                        JSONObject signRecord = signRecords.getJSONObject(j);
                        String signKey = signRecord.getString("signKey");
                        if (signKey.equals(currentSignKey)) {
                            if (!signRecord.getBoolean("signed")) {
                                JSONObject resData2 = new JSONObject(AntForestRpcCall.antiepSign(signId, "ANTFOREST_ENERGY_SIGN", UserIdMap.getCurrentUid()));
                                if (MessageUtil.checkSuccess(TAG, resData2)) {
                                    Log.forest("过期能量💊[" + signRecord.getInt("awardCount") + "g]");
                                }
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "popupTask err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void waterFriendEnergy() {
        String taskUid = UserIdMap.getCurrentUid();
        int waterEnergy = WaterFriendType.waterEnergy[waterFriendType.getValue()];
        if (waterEnergy == 0) {
            return;
        }
        Map<String, Integer> friendMap = waterFriendList.getValue();
        for (Map.Entry<String, Integer> friendEntry : friendMap.entrySet()) {
            String uid = friendEntry.getKey();
            if (selfId.equals(uid)) {
                continue;
            }
            Integer waterCount = friendEntry.getValue();
            if (waterCount == null || waterCount <= 0) {
                continue;
            }
            if (waterCount > 3) {
                waterCount = 3;
            }
            if (Status.canWaterFriendToday(uid, waterCount)) {
                try {
                    JSONObject jo = new JSONObject(AntForestRpcCall.queryFriendHomePage(uid));
                    TimeUtil.sleep(100);
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        String bizNo = jo.getString("bizNo");
                        KVNode<Integer, Boolean> waterCountKVNode = returnFriendWater(uid, bizNo, waterCount, waterEnergy);
                        waterCount = waterCountKVNode.getKey();
                        if (waterCount > 0) {
                            Status.waterFriendToday(uid, waterCount, taskUid);
                        }
                        if (!waterCountKVNode.getValue()) {
                            break;
                        }
                    }
                } catch (Throwable t) {
                    Log.i(TAG, "waterFriendEnergy err:");
                    Log.printStackTrace(TAG, t);
                }
            }
        }
    }

    private KVNode<Integer, Boolean> returnFriendWater(String userId, String bizNo, int count, int waterEnergy) {
        if (bizNo == null || bizNo.isEmpty()) {
            return new KVNode<>(0, true);
        }
        int wateredTimes = 0;
        boolean isContinue = true;
        try {
            String s;
            JSONObject jo;
            int energyId = getEnergyId(waterEnergy);
            label:
            for (int waterCount = 1; waterCount <= count; waterCount++) {
                s = AntForestRpcCall.transferEnergy(userId, bizNo, energyId, waterFriendEnergySendChat.getValue() ? "Y" : "N");
                TimeUtil.sleep(1500);
                jo = new JSONObject(s);

                String resultCode = jo.getString("resultCode");
                switch (resultCode) {
                    case "SUCCESS":
                        //记录浇水次数
                        Status.wateringFriendToday(userId);
                        FriendWatch.waterFriendWatch(userId, false);
                        Statistics.addData(Statistics.DataType.WATERINGCOUNT, 1);
                        int currentEnergy = jo.getJSONObject("userBaseInfo").getInt("currentEnergy");
                        Log.forest("好友浇水🚿给[" + UserIdMap.getShowName(userId) + "]浇" + waterEnergy + "g#剩余能量[" + currentEnergy + "g]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                        Toast.show("好友浇水🚿给[" + UserIdMap.getShowName(userId) + "]浇" + waterEnergy + "g");
                        wateredTimes++;
                        Statistics.addData(Statistics.DataType.WATERED, waterEnergy);
                        break;
                    case "WATERING_TIMES_LIMIT":
                        Log.record("好友浇水🚿今日给[" + UserIdMap.getMaskName(userId) + "]浇水已达上限");
                        wateredTimes = 3;
                        break label;
                    case "WATERING_USER_LIMIT":
                        Log.record("好友浇水🚿给[" + UserIdMap.getMaskName(userId) + "]浇水，" + jo.getString("resultDesc"));
                        wateredTimes = 3;
                        break label;
                    default:
                        Log.record("好友浇水🚿" + jo.getString("resultDesc"));
                        Log.i(jo.toString());
                        break;
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "returnFriendWater err:");
            Log.printStackTrace(TAG, t);
        }
        return new KVNode<>(wateredTimes, isContinue);
    }

    private int getEnergyId(int waterEnergy) {
        if (waterEnergy <= 0) {
            return 0;
        }
        if (waterEnergy >= 66) {
            return 42;
        }
        if (waterEnergy >= 33) {
            return 41;
        }
        if (waterEnergy >= 18) {
            return 40;
        }
        return 39;
    }

    private void doubleWaterFriendEnergy() {
        String taskUid = UserIdMap.getCurrentUid();
        int waterEnergy = WaterFriendType.waterEnergy[waterFriendType.getValue()];
        if (waterEnergy == 0) {
            return;
        }
        boolean reSet = true;
        Map<String, Integer> friendMap = waterFriendList.getValue();
        for (Map.Entry<String, Integer> friendEntry : friendMap.entrySet()) {
            String uid = friendEntry.getKey();
            if (selfId.equals(uid)) {
                continue;
            }
            Integer waterCount = friendEntry.getValue();
            if (waterCount == null || waterCount <= 0) {
                continue;
            }
            if (Status.canWaterFriendToday(uid, 3)) {
                reSet = false;
            }
        }
        if (reSet) {
            for (Map.Entry<String, Integer> friendEntry : friendMap.entrySet()) {
                String uid = friendEntry.getKey();
                if (selfId.equals(uid)) {
                    continue;
                }
                Integer waterCount = friendEntry.getValue();
                if (waterCount == null || waterCount <= 0) {
                    continue;
                }
                //重置浇水次数
                Status.waterFriendToday(uid, 0, taskUid);
            }
            Log.record("好友浇水🚿今日给好友浇水状态已重置！");
            Status.flagToday("Forest::doubleWaterFriendEnergy");
        }
    }

    private void forestExtensions() {
        try {
            ExtensionsHandle.handleAlphaRequest("antForest", "extensions", usingProps);
        } catch (Throwable t) {
            Log.i(TAG, "forestExtensions err:");
            Log.printStackTrace(TAG, t);
        }
    }

    // skuId, sku
    Map<String, JSONObject> skuInfo = new HashMap<>();

    private void vitalityExchangeBenefit() {
        try {
            getAllSkuInfo();
            Map<String, Integer> exchangeList = vitality_ExchangeBenefitList.getValue();
            for (Map.Entry<String, Integer> entry : exchangeList.entrySet()) {
                String skuId = entry.getKey();
                Integer count = entry.getValue();
                if (count == null || count < 0) {
                    continue;
                }
                while (Status.canVitalityExchangeBenefitToday(skuId, count) && exchangeBenefit(skuId)) {
                    TimeUtil.sleep(3000);
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "vitalityExchangeBenefit err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void vantiepSign() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryTaskList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray forestSignVOList = jo.getJSONArray("forestSignVOList");
            JSONObject forestSignVO = forestSignVOList.getJSONObject(0);
            String currentSignKey = forestSignVO.getString("currentSignKey"); // 当前签到的 key
            String signId = forestSignVO.getString("signId"); // 签到ID
            String sceneCode = forestSignVO.getString("sceneCode"); // 场景代码
            JSONArray signRecords = forestSignVO.getJSONArray("signRecords"); // 签到记录
            for (int i = 0; i < signRecords.length(); i++) { // 遍历签到记录
                JSONObject signRecord = signRecords.getJSONObject(i);
                String signKey = signRecord.getString("signKey");
                int awardCount = signRecord.getInt("awardCount");
                if (signKey.equals(currentSignKey) && !signRecord.getBoolean("signed")) {
                    JSONObject joSign = new JSONObject(AntForestRpcCall.antiepSign(signId, UserIdMap.getCurrentUid(), sceneCode));
                    TimeUtil.sleep(300); // 等待300毫秒
                    if (MessageUtil.checkSuccess(TAG + "森林签到失败:", joSign)) {
                        int continuousCount = joSign.getInt("continuousCount");
                        Log.forest("森林签到📆拯救第" + continuousCount + "天#复活[" + awardCount + "g能量]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                        Statistics.addData(Statistics.DataType.COLLECTED, awardCount);
                        // return awardCount;
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "vitalitySign err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void queryCommonSign() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryCommonSign("ANTFOREST_GIFT7TH_SIGN_202506"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("forestSignVO")) {
                if (!Status.hasFlagToday("forest::CommonSign")) {
                    Status.flagToday("forest::CommonSign");
                    Log.forest("森林签到📆尚未检测到[森林7日签到数据]若出现数据立马为大人领取");
                }
                return;
            }
            JSONObject forestSignVO = jo.getJSONObject("forestSignVO");
            String currentSignKey = forestSignVO.getString("currentSignKey"); // 当前签到的 key
            String signId = forestSignVO.getString("signId"); // 签到ID
            String sceneCode = forestSignVO.getString("sceneCode"); // 场景代码
            JSONArray signRecords = forestSignVO.getJSONArray("signRecords"); // 签到记录
            for (int i = 0; i < signRecords.length(); i++) { // 遍历签到记录
                JSONObject signRecord = signRecords.getJSONObject(i);
                String signKey = signRecord.getString("signKey");
                int awardCount = signRecord.getInt("awardCount");
                String awardType = signRecord.getString("awardType");
                JSONObject extInfo = signRecord.getJSONObject("extInfo");
                String awardName = extInfo.getString("awardName");
                if (signKey.equals(currentSignKey) && !signRecord.getBoolean("signed")) {
                    JSONObject joSign = new JSONObject(AntForestRpcCall.antiepSign(signId, UserIdMap.getCurrentUid(), sceneCode));
                    TimeUtil.sleep(300); // 等待300毫秒
                    if (MessageUtil.checkSuccess(TAG + "森林7日签到:", joSign)) {
                        int continuousCount = joSign.getInt("continuousCount");
                        Log.forest("森林签到📆第" + continuousCount + "天#7日签到[" + awardName + "*" + awardCount + "]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                        if (awardType.equals("ENERGY")) {
                            Statistics.addData(Statistics.DataType.COLLECTED, awardCount);
                        }
                        // return awardCount;
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "vitalitySign err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void vitalitySign() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.vitalitySign());
            TimeUtil.sleep(300);
            if (MessageUtil.checkResultCode(TAG, jo)) {
                int continuousCount = jo.getInt("continuousCount");
                int signAwardCount = jo.getInt("signAwardCount");
                Log.forest("森林任务📆签到[" + continuousCount + "天]奖励[" + signAwardCount + "活力值]");
            }
        } catch (Throwable t) {
            Log.i(TAG, "vitalitySign err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void queryTaskList() {
        queryTaskList("DNHZ_SL_college", "DAXUESHENG_SJK");
        queryTaskList("DXS_BHZ", "NENGLIANGZHAO_20230807");
        queryTaskList("DXS_JSQ", "JIASUQI_20230808");
        try {
            boolean doubleCheck = true;
            while (doubleCheck) {
                JSONObject jo = new JSONObject(AntForestRpcCall.queryTaskList());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    doubleCheck = false;
                    continue;
                }
                // 添加安全的空值判断
                if (!jo.has("forestTasksNew")) {
                    doubleCheck = false;
                    continue;
                }
                JSONArray forestTasksNew = jo.optJSONArray("forestTasksNew");
                if (forestTasksNew == null || forestTasksNew.length() == 0) {
                    doubleCheck = false;
                    continue;
                }
                for (int i = 0; i < forestTasksNew.length(); i++) {
                    JSONObject forestTask = forestTasksNew.optJSONObject(i);
                    if (forestTask == null) {
                        continue;
                    }
                    JSONArray taskInfoList = forestTask.optJSONArray("taskInfoList");
                    if (taskInfoList == null) {
                        continue;
                    }
                    doubleCheck = doForsetTaskList(taskInfoList);
                }
            }
            doubleCheck = true;
            while (doubleCheck) {
                JSONObject jo = new JSONObject(AntForestRpcCall.listTaskopengreen());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                // 添加安全的空值判断
                if (!jo.has("taskInfoList")) {
                    return;
                }
                JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
                if (taskInfoList == null || taskInfoList.length() == 0) {
                    return;
                }
                doubleCheck = doForsetTaskList(taskInfoList);
            }
        } catch (Throwable t) {
            Log.i(TAG, "queryTaskList err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private Boolean doForsetTaskList(JSONArray taskInfoList) {
        boolean doubleCheck = false;
        try {
            if (taskInfoList == null || taskInfoList.length() == 0) {
                return doubleCheck;
            }
            for (int j = 0; j < taskInfoList.length(); j++) {
                JSONObject taskInfo = taskInfoList.getJSONObject(j);
                JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo");
                JSONObject bizInfo = new JSONObject(taskBaseInfo.getString("bizInfo"));
                String taskType = taskBaseInfo.getString("taskType");
                String taskTitle = bizInfo.optString("taskTitle", taskType);
                String sceneCode = taskBaseInfo.getString("sceneCode");
                String taskStatus = taskBaseInfo.getString("taskStatus");
                if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                    if (receiveTaskAward(sceneCode, taskType, taskTitle)) {
                        doubleCheck = true;
                    }
                } else if (TaskStatus.TODO.name().equals(taskStatus)) {
                    //黑名单任务跳过
                    if (AntForestVitalityTaskList.getValue().contains(taskTitle)) {
                        continue;
                    }

                    if ("TEST_LEAF_TASK".equals(taskType)) {
                        JSONArray childTaskTypeList = taskInfo.optJSONArray("childTaskTypeList");
                        if (childTaskTypeList != null && childTaskTypeList.length() > 0) {
                            doChildTask(childTaskTypeList, taskTitle);
                            doubleCheck = true;
                            continue;
                        }
                    }

                    if ("DAKA_GROUP".equals(taskType)) {
                        JSONArray childTaskTypeList = taskInfo.optJSONArray("childTaskTypeList");
                        if (childTaskTypeList != null && childTaskTypeList.length() > 0) {
                            doChildTask(childTaskTypeList, taskTitle);
                            continue;
                        }
                    }

                    doubleCheck = finishTask(sceneCode, taskType, taskTitle);
                }
            }
            //可能是触发限时挑战奖励的
            AntForestRpcCall.batchQueryAndTouchopengreen();
        } catch (Throwable t) {
            Log.i(TAG, "doForsetTaskList err:");
            Log.printStackTrace(TAG, t);
        }
        return doubleCheck;
    }


    private void queryTaskList(String firstTaskType, String taskType) {
        if (Status.hasFlagToday("vitalityTask::" + firstTaskType)) {
            return;
        }
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryTaskList(new JSONObject().put("firstTaskType", firstTaskType)));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            // 添加安全的空值判断
            if (!jo.has("forestTasksNew")) {
                return;
            }
            JSONArray forestTasksNew = jo.optJSONArray("forestTasksNew");
            if (forestTasksNew == null || forestTasksNew.length() == 0) {
                return;
            }
            JSONObject firstTask = forestTasksNew.optJSONObject(0);
            if (firstTask == null || !firstTask.has("taskInfoList")) {
                return;
            }
            JSONArray taskInfoList = firstTask.optJSONArray("taskInfoList");
            if (taskInfoList == null || taskInfoList.length() == 0) {
                return;
            }
            for (int i = 0; i < taskInfoList.length(); i++) {
                jo = taskInfoList.getJSONObject(i).getJSONObject("taskBaseInfo");
                if (!Objects.equals(taskType, jo.getString("taskType"))) {
                    continue;
                }
                boolean isReceived = TaskStatus.RECEIVED.name().equals(jo.getString("taskStatus"));
                if (!isReceived && TaskStatus.FINISHED.name().equals(jo.getString("taskStatus"))) {
                    String sceneCode = jo.getString("sceneCode");
                    String taskTitle = new JSONObject(jo.getString("bizInfo")).getString("taskTitle");
                    isReceived = receiveTaskAward(sceneCode, taskType, taskTitle);
                    TimeUtil.sleep(1000);
                }
                if (isReceived) {
                    Status.flagToday("vitalityTask::" + firstTaskType);
                }
                return;
            }
        } catch (Throwable t) {
            Log.i(TAG, "queryTaskList err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private Boolean receiveTaskAward(String sceneCode, String taskType, String taskTitle) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.receiveTaskAward(sceneCode, taskType));
            TimeUtil.sleep(500);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                int incAwardCount = jo.optInt("incAwardCount", 1);
                if (jo.has("returnData")) {
                    String returnData = jo.optString("returnData");
                    if (returnData.contains("Energy")) {
                        Log.forest("森林任务🎖️领取[" + taskTitle + "]奖励#获得[" + incAwardCount + "g能量]");
                        Statistics.addData(Statistics.DataType.COLLECTED, incAwardCount);
                    } else {
                        Log.forest("森林任务🎖️领取[" + taskTitle + "]奖励#获得[" + incAwardCount + "活力值]");
                    }
                }
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "receiveTaskAward err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private Boolean finishTask(String sceneCode, String taskType, String taskTitle) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.finishTask(sceneCode, taskType));
            //检查并标记黑名单任务
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntForestVitalityTaskList", taskTitle, jo);
            TimeUtil.sleep(500);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.forest("森林任务🧾️完成[" + taskTitle + "]");
                return true;
            }
            Log.record("完成任务[" + taskTitle + "]失败");
        } catch (Throwable t) {
            Log.i(TAG, "finishTask err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private void doChildTask(JSONArray childTaskTypeList, String title) {
        try {
            for (int i = 0; i < childTaskTypeList.length(); i++) {
                JSONObject taskInfo = childTaskTypeList.getJSONObject(i);
                JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo");
                JSONObject bizInfo = new JSONObject(taskBaseInfo.getString("bizInfo"));
                String taskType = taskBaseInfo.getString("taskType");
                String taskTitle = bizInfo.optString("taskTitle", title);
                String sceneCode = taskBaseInfo.getString("sceneCode");
                String taskStatus = taskBaseInfo.getString("taskStatus");
                if (TaskStatus.TODO.name().equals(taskStatus)) {
                    if (bizInfo.optBoolean("autoCompleteTask")) {
                        finishTask(sceneCode, taskType, taskTitle);
                    }
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "doChildTask err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private void startEnergyRain() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.startEnergyRain());
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            String token = jo.getString("token");
            JSONArray bubbleEnergyList = jo.getJSONObject("difficultyInfo").getJSONArray("bubbleEnergyList");
            int sum = 0;
            for (int i = 0; i < bubbleEnergyList.length(); i++) {
                sum += bubbleEnergyList.getInt(i);
            }
            TimeUtil.sleep(5000L);
            if (sum == 50) {
                Status.flagToday("EnergyRain::PlayGame");
            }
            jo = new JSONObject(AntForestRpcCall.energyRainSettlement(sum, token));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Toast.show("获得了[" + sum + "g]能量[能量雨]");
                Log.forest("收能量雨🌧️[" + sum + "g]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                totalCollected += sum;
                Statistics.addData(Statistics.DataType.COLLECTED, sum);
            }
            TimeUtil.sleep(500);
        } catch (Throwable th) {
            Log.i(TAG, "startEnergyRain err:");
            Log.printStackTrace(TAG, th);
        }
    }

    // LIMIT_TIME_ENERGY_DOUBLE_CLICK,CR20230516000363
    // LIMIT_TIME_ENERGY_RAIN_CHANCE,SK20250117005985,VITALITY_ENERGYRAIN_3DAYS，限时3天内使用能量雨次卡
    private void useEnergyRainCard() {
        try {
            // 商店兑换 限时能量雨卡
            exchangeBenefit("SK20250117005985");
            TimeUtil.sleep(2000);
            JSONObject jo;
            do {
                TimeUtil.sleep(1000);
                // 背包查找 能量雨卡
                jo = null;
                List<JSONObject> list = getPropGroup(getForestPropVOList(), PropGroup.energyRain.name());
                if (!list.isEmpty()) {
                    jo = list.get(0);
                }
                if (jo == null) {
                    break;
                }
                // 使用能量雨卡
            } while (consumeProp(jo));
        } catch (Throwable th) {
            Log.i(TAG, "useEnergyRainCard err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private void energyRain() {
        try {
            JSONObject joEnergyRainHome = new JSONObject(AntForestRpcCall.queryEnergyRainHome());
            TimeUtil.sleep(500);
            if (MessageUtil.checkResultCode(TAG, joEnergyRainHome)) {
                if (joEnergyRainHome.getBoolean("canPlayToday")) {
                    startEnergyRain();
                }
                if (joEnergyRainHome.getBoolean("canGrantStatus")) {
                    Log.record("有送能量雨的机会");
                    JSONObject joEnergyRainCanGrantList = new JSONObject(AntForestRpcCall.queryEnergyRainCanGrantList());
                    TimeUtil.sleep(500);
                    JSONArray grantInfos = joEnergyRainCanGrantList.getJSONArray("grantInfos");
                    Set<String> set = giveEnergyRainList.getValue();
                    String userId;
                    boolean granted = false;
                    for (int j = 0; j < grantInfos.length(); j++) {
                        JSONObject grantInfo = grantInfos.getJSONObject(j);
                        if (grantInfo.getBoolean("canGrantedStatus")) {
                            userId = grantInfo.getString("userId");
                            if (set.contains(userId)) {
                                JSONObject joEnergyRainChance = new JSONObject(AntForestRpcCall.grantEnergyRainChance(userId));
                                TimeUtil.sleep(500);
                                Log.record("尝试送能量雨给【" + UserIdMap.getMaskName(userId) + "】");
                                granted = true;
                                // 20230724能量雨调整为列表中没有可赠送的好友则不赠送
                                if (MessageUtil.checkResultCode(TAG, joEnergyRainChance)) {
                                    Log.forest("送能量雨🌧️[" + UserIdMap.getMaskName(userId) + "]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                                    startEnergyRain();
                                }
                                break;
                            }
                        }
                    }
                    if (!granted) {
                        Log.record("没有可以送的用户");
                    }
                }
                boolean canPlayGame = joEnergyRainHome.getBoolean("canPlayGame");

                if (canPlayGame) {
                    // 检查今日是否需要执行
                    if (!Status.hasFlagToday("EnergyRain::PlayGame")) {
                        Log.record("是否可以能量雨游戏: " + canPlayGame);
                        // 检查并处理游戏任务
                        boolean hasTaskToProcess = checkAndDoEndGameTask();
                        TimeUtil.sleep(4000);
                    }
                }
            }
            joEnergyRainHome = new JSONObject(AntForestRpcCall.queryEnergyRainHome());
            TimeUtil.sleep(500);
            if (MessageUtil.checkResultCode(TAG, joEnergyRainHome) && joEnergyRainHome.getBoolean("canPlayToday")) {
                startEnergyRain();
            }
        } catch (Throwable th) {
            Log.i(TAG, "energyRain err:");
            Log.printStackTrace(TAG, th);
        }
    }

    public static boolean checkAndDoEndGameTask() {
        try {
            // 1. 查询游戏任务列表
            String response = AntForestRpcCall.queryEnergyRainEndGameList();
            JSONObject jo = new JSONObject(response);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            if (!jo.has("energyRainEndGameGroupTask")) {
                Status.flagToday("EnergyRain::PlayGame");
                return false;
            }

            // 2. 初始化新任务（需要接入森林救援队）
            if (jo.optBoolean("needInitTask", false)) {
                Log.record("检测到新任务，准备接入[森林救援队]...");
                String initResStr = AntForestRpcCall.initTask("GAME_DONE_SLJYD");
                JSONObject initRes = new JSONObject(initResStr);
                if (!MessageUtil.checkResultCode(TAG, initRes)) {
                    return false;
                }
                /*
                // 3. 遍历任务列表，检查待执行任务
                JSONObject groupTask = jo.optJSONObject("energyRainEndGameGroupTask");
                JSONArray taskInfoList = groupTask != null ? groupTask.optJSONArray("taskInfoList") : null;

                if (taskInfoList != null && taskInfoList.length() > 0) {
                    for (int i = 0; i < taskInfoList.length(); i++) {
                        JSONObject task = taskInfoList.getJSONObject(i);
                        JSONObject baseInfo = task.optJSONObject("taskBaseInfo");
                        if (baseInfo == null) {
                            continue;
                        }
                        String taskType = baseInfo.optString("taskType");
                        String taskStatus = baseInfo.optString("taskStatus");

                        // 处理森林救援队任务（GAME_DONE_SLJYD）
                        if ("GAME_DONE_SLJYD".equals(taskType)) {
                            if ("TODO".equals(taskStatus) || "NOT_TRIGGER".equals(taskStatus)) {
                                // 执行任务上报
                                GameTask.Forest_sljyd.report("森林", 1);
                                return true; // 有任务待处理
                            } else if ("FINISHED".equals(taskStatus) || "DONE".equals(taskStatus)) {
                                return false; // 任务已完成
                            }
                        }
                    }
                } else if (!jo.optBoolean("needInitTask", false)) {
                    return false; // 无任务且无需初始化
                }*/
            }
            GameTask.Forest_sljyd.report("森林", 1);
            return true;

        } catch (Throwable th) {
            Log.printStackTrace("执行能量雨后续任务出错:", th);
            return false;
        }
    }

    public void doforestgame() {
        try {
            String response = AntForestRpcCall.queryGameList();
            JSONObject jo = new JSONObject(response);

            // 验证请求是否成功
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                Log.error("queryGameList 失败: " + jo.optString("desc"));
                return;
            }

            JSONObject drawRights = jo.optJSONObject("gameCenterDrawRights");
            if (drawRights != null) {
                int perTime = drawRights.optInt("quotaPerTime", 100);

                // 换算实际宝箱次数
                int canUseCount = drawRights.optInt("quotaCanUse") / perTime;
                int limitCount = drawRights.optInt("quotaLimit") / perTime;
                int usedCount = drawRights.optInt("usedQuota") / perTime;

                // 1. 处理待开启奖励 (批量开启)
                if (canUseCount > 0) {
                    Log.record("森林乐园正在一次性开启 " + canUseCount + " 个宝箱...");
                    JSONObject drawJo = new JSONObject(AntForestRpcCall.drawGameCenterAward(canUseCount));
                    if (!MessageUtil.checkResultCode(drawJo)) {
                        return;
                    }
                    JSONArray awardList = drawJo.optJSONArray("gameCenterDrawAwardList");
                    int totalEnergy = 0;
                    List<String> otherAwards = new ArrayList<>();

                    if (awardList != null) {
                        for (int i = 0; i < awardList.length(); i++) {
                            JSONObject award = awardList.getJSONObject(i);
                            String type = award.optString("awardType");
                            String name = award.optString("awardName");
                            int count = award.optInt("awardCount");
                            Log.forest("森林乐园🎁开宝箱得[" + name + "*" + count + "]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                            if ("ENERGY".equals(type)) {
                                totalEnergy += count;
                            } else {
                                otherAwards.add(name + "x" + count);
                            }
                        }
                    }
                    Statistics.addData(Statistics.DataType.COLLECTED, totalEnergy);
                    // 输出统计结果
                    StringBuilder logMsg = new StringBuilder("森林乐园🎁[开宝箱]共计");
                    if (totalEnergy > 0) {
                        logMsg.append("获得能量").append(totalEnergy).append("g");
                    }
                    if (!otherAwards.isEmpty()) {
                        if (totalEnergy > 0) {
                            logMsg.append(", ");
                        }
                        logMsg.append("其他: ").append(String.join("/", otherAwards));
                    }
                    Log.forest(logMsg.toString());
                    Toast.show(logMsg.toString());

                }

                // 2. 判断是否需要刷任务
                int remainToTask = limitCount - usedCount;
                if (remainToTask > 0) {
                    GameTask.Forest_slxcc.report("森林", remainToTask);
                } else {
                    Log.record("今日森林乐园游戏任务已满额");
                }
            }

        } catch (CancellationException e) {
            throw e;
        } catch (Throwable t) {
            Log.printStackTrace("doforestgame 流程异常", t);
        }
    }

    //乐园限定活动
    private void queryOptionalPlay() {
        try {
            boolean doubleCheck = true;
            while (doubleCheck) {
                doubleCheck = false;
                JSONObject jo = new JSONObject(AntForestRpcCall.queryOptionalPlay());
                if (!MessageUtil.checkSuccess(TAG, jo)) {
                    return;
                }
                if (!jo.has("taskTriggerPlayInfo")) {
                    return;
                }
                JSONObject taskTriggerPlayInfo = jo.optJSONObject("taskTriggerPlayInfo");
                if (!taskTriggerPlayInfo.has("taskList")) {
                    return;
                }
                JSONArray taskList = taskTriggerPlayInfo.getJSONArray("taskList");
                for (int j = 0; j < taskList.length(); j++) {
                    JSONObject task = taskList.getJSONObject(j);
                    String taskStatus = task.getString("taskStatus");
                    int alreadyReceiveAwardCount = task.optInt("alreadyReceiveAwardCount");
                    int awardCount = task.optInt("awardCount");
                    int awardCountForReceive = awardCount - alreadyReceiveAwardCount;
                    int rightsTimesLimit = task.optInt("rightsTimesLimit");
                    int rightsTimes = task.optInt("rightsTimes");
                    String awardType = task.optString("awardType", "能量");
                    JSONObject bizInfo = task.getJSONObject("bizInfo");
                    String title = bizInfo.getString("title");
                    String source = task.optString("source", "ch_appcenter__chsub_9patch");
                    String sceneCode = task.optString("sceneCode", "");
                    String taskType = task.optString("taskType", "");
                    // 记录任务状态
                    if (taskStatus.equals("FINISHED")) {
                        if (awardCountForReceive > 0) {
                            // 领取奖励
                            JSONObject joReceived = new JSONObject(AntForestRpcCall.receiveTaskAwardopengreen(source, sceneCode, taskType));
                            if (MessageUtil.checkSuccess(TAG, joReceived)) {
                                int incAwardCount = joReceived.optInt("incAwardCount");
                                JSONObject taskConfigResultVO = joReceived.optJSONObject("taskConfigResultVO");
                                if (taskConfigResultVO != null) {
                                    awardType = taskConfigResultVO.optString("awardType", awardType);
                                }
                                // 能量统计
                                if ("能量".equals(awardType) || "ENERGY".equals(awardType)) {
                                    Log.forest("森林乐园🎖️领取[" + title + "]奖励[" + incAwardCount + "g能量]");
                                    Statistics.addData(Statistics.DataType.COLLECTED, incAwardCount);
                                } else {
                                    Log.forest("森林乐园🎖️领取[" + title + "]奖励[" + awardType + "*" + incAwardCount + "]");
                                }
                            }
                        }
                    }
                    if (TaskStatus.TODO.name().equals(taskStatus) || rightsTimes < rightsTimesLimit) {
                        //黑名单任务跳过
                        if (AntForestVitalityTaskList.getValue().contains(title)) {
                            continue;
                        }
                        if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                            if (finishTask(sceneCode, taskType, title + "(" + (rightsTimes + 1) + "/" + rightsTimesLimit + ")")) {
                                doubleCheck = true;
                            }
                        } else {
                            if (finishTask(sceneCode, taskType, title)) {
                                doubleCheck = true;
                            }
                        }
                    }
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "queryOptionalPlay err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private void continuousUseCardOptions() {
        //双击卡
        continuousUseAndExchangeCard("doubleClick", "SK20240805004754");
        //收能量倍卡
        continuousUseAndExchangeCard("robExpandCard", "");
        //保护罩
        continuousUseAndExchangeCard("shield", "CR20230516000370");
        //隐身卡
        continuousUseAndExchangeCard("stealthCard", "SK20230521000206");
        //炸弹卡
        //continuousUseAndExchangeCard("energyBombCard", "SK20250219006517");
    }

    private void continuousUseAndExchangeCard(String propGroupType, String exchangeProp) {
        try {
            if (continuousUseCardOptions.getValue().contains(propGroupType)) {
                long continuousUseCardSecond = continuousUseCardCheak(propGroupType);
                if (continuousUseCardSecond >= 0) {
                    TimeUtil.sleep(500);
                    JSONObject rightCard = chooseContinuousLIMITTIMECard(propGroupType);
                    if (rightCard == null) {
                        if (exchangeProp != null) {
                            exchangeBenefit(exchangeProp);
                            TimeUtil.sleep(500);
                        }
                        rightCard = chooseContinuousLIMITTIMECard(propGroupType);
                        if (rightCard == null) {
                            return;
                        }
                    }
                    int holdsNum = rightCard.optInt("holdsNum");
                    if (holdsNum == 0) {
                        return;
                    }
                    int loopCount = 0; // 循环次数计数
                    final int MAX_LOOP = 10; // 最大循环次数，避免死循环
                    do {
                        rightCard = chooseContinuousLIMITTIMECard(propGroupType);
                        if (rightCard == null) {
                            return;
                        }
                        holdsNum = rightCard.optInt("holdsNum");
                        if (holdsNum == 0) {
                            return;
                        }
                        if (!rightCard.has("propIdList")) {
                            return;
                        }
                        JSONArray propIdList = rightCard.optJSONArray("propIdList");
                        if (propIdList.length() == 0) {
                            return;
                        }
                        String propId = propIdList.optString(0);
                        String propType = rightCard.optString("propType");
                        String propName = rightCard.getJSONObject("propConfigVO").getString("propName");
                        JSONObject joResult;
                        switch (propGroupType) {
                            case "doubleClick":
                            case "shield":
                            case "robExpandCard":
                                if (continuousUseCardSecond > 0) {
                                    joResult = new JSONObject(AntForestRpcCall.consumeProp(propGroupType, propId, propType, true));
                                } else {
                                    joResult = new JSONObject(AntForestRpcCall.consumeProp(propGroupType, propId, propType, false));
                                }
                                holdsNum--;
                                TimeUtil.sleep(500);
                                if (MessageUtil.checkResultCode(TAG, joResult)) {
                                    Log.forest("使用道具🎭[" + propName + "]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                                }
                                break;

                            case "stealthCard":
                                joResult = new JSONObject(AntForestRpcCall.consumeProp(propGroupType, propId, propType));
                                holdsNum--;
                                TimeUtil.sleep(1000);
                                if (MessageUtil.checkResultCode(TAG, joResult)) {
                                    Log.forest("使用道具🎭[" + propName + "]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                                }
                                break;
                            /*case "energyBombCard":
                                joResult = new JSONObject(AntForestRpcCall.consumeProp(propGroupType, propId, propType,false));
                                holdsNum--;
                                TimeUtil.sleep(1000);
                                if (MessageUtil.checkResultCode(TAG, joResult)) {
                                    Log.forest("使用道具🎭[" + propName + "]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                                }
                                break;*/
                        }
                        continuousUseCardSecond = continuousUseCardCheak(propGroupType);
                        if (continuousUseCardSecond < 0) {
                            return;
                        }
                        TimeUtil.sleep(500);
                    } while (holdsNum > 0 && ++loopCount < MAX_LOOP);
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "continuousUseAndExchangeCard err:");
            Log.printStackTrace(TAG, th);
        }
    }

    //判断是否可以使用道具卡片
    //返回值-1为不可用，0为可用，大于0为剩余时间
    private long continuousUseCardCheak(String propGroupType) {
        try {
            JSONObject joMiscHomes = new JSONObject(AntForestRpcCall.queryMiscInfo());
            System.out.println(joMiscHomes);
            if (!MessageUtil.checkResultCode(TAG, joMiscHomes)) {
                return -1;
            }
            if (!joMiscHomes.has("combineHandlerVOMap")) {
                return -1;
            }
            long now = System.currentTimeMillis();
            JSONObject combineHandlerVOMap = joMiscHomes.optJSONObject("combineHandlerVOMap");
            if (!combineHandlerVOMap.has("usingProp")) {
                return -1;
            }
            JSONObject usingProp = combineHandlerVOMap.optJSONObject("usingProp");
            if (!usingProp.has("userPropVOS")) {
                return -1;
            }
            JSONArray userPropVOS = usingProp.getJSONArray("userPropVOS");
            for (int i = 0; i < userPropVOS.length(); i++) {
                JSONObject userPropVO = userPropVOS.getJSONObject(i);
                String propGroup = userPropVO.optString("propGroup");
                if (propGroup.equals(propGroupType)) {
                    long endTime = userPropVO.optLong("endTime");
                    long duringTime = endTime - now;
                    if (duringTime < 0) {
                        return -1;
                    }
                    switch (propGroupType) {
                        case "doubleClick":
                            if (duringTime / (1000 * 60) < 60 * 24 * 31) {
                                return duringTime;
                            } else {
                                return -1;
                            }
                        case "robExpandCard":
                            if (duringTime / (1000 * 60) < 60 * 24 * 30) {
                                return duringTime;
                            } else {
                                return -1;
                            }
                        case "stealthCard":
                            return -1;
                        case "shield":
                            if (duringTime / (1000 * 60) < 60 * continuousUseShieldHour.getValue()) {
                                return duringTime;
                            } else {
                                return -1;
                            }
                        /*case "energyBombCard":
                            if (duringTime / (1000 * 60) < 3*60 * 24) {
                                Log.forest("duringTime");
                                return duringTime;
                            }
                            else {
                                return -1;
                            }*/

                    }
                }
            }
            return 0;
        } catch (Throwable th) {
            Log.i(TAG, "useDoubleCard err:");
            Log.printStackTrace(TAG, th);
        }
        return -1;
    }

    private String useRobExpandCardFactor() {
        try {
            JSONObject joMiscHomes = new JSONObject(AntForestRpcCall.queryMiscInfo());
            System.out.println(joMiscHomes);
            if (!MessageUtil.checkResultCode(TAG, joMiscHomes)) {
                return null;
            }
            if (!joMiscHomes.has("combineHandlerVOMap")) {
                return null;
            }
            long now = System.currentTimeMillis();
            JSONObject combineHandlerVOMap = joMiscHomes.optJSONObject("combineHandlerVOMap");
            if (!combineHandlerVOMap.has("usingProp")) {
                return null;
            }
            JSONObject usingProp = combineHandlerVOMap.optJSONObject("usingProp");
            if (!usingProp.has("userPropVOS")) {
                return null;
            }
            JSONArray userPropVOS = usingProp.getJSONArray("userPropVOS");
            for (int i = 0; i < userPropVOS.length(); i++) {
                JSONObject userPropVO = userPropVOS.getJSONObject(i);
                String propGroup = userPropVO.optString("propGroup");
                if (propGroup.equals("robExpandCard")) {
                    if (!userPropVO.has("detail")) {
                        return null;
                    }
                    return userPropVO.optJSONObject("detail").optString("factor");
                }
            }
            return null;
        } catch (Throwable th) {
            Log.i(TAG, "UserobExpandCardFactor err:");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }


    //选出可以使用的限时道具卡片
    private JSONObject chooseContinuousLIMITTIMECard(String propGroupType) {
        try {
            JSONArray forestPropVOList = getForestPropVOList();
            JSONObject rightCard = null;
            String useFactor = useRobExpandCardFactor();
            for (int i = 0; i < forestPropVOList.length(); i++) {
                JSONObject forestBagProp = forestPropVOList.getJSONObject(i);
                String propGroup = forestBagProp.optString("propGroup");
                if (forestBagProp.has("recentExpireTime") && propGroup.equals(propGroupType)) {
                    switch (propGroup) {
                        case "stealthCard":
                        case "shield":
                        case "doubleClick":
                            //case "energyBombCard":
                            if (rightCard != null) {
                                long recentExpireTimerightCard = rightCard.optLong("recentExpireTime");
                                long recentExpireTimeforestBagProp = forestBagProp.optLong("recentExpireTime");
                                if (recentExpireTimerightCard > recentExpireTimeforestBagProp) {
                                    rightCard = forestBagProp;
                                }
                            } else {
                                rightCard = forestBagProp;
                            }
                            break;
                        case "robExpandCard":
                            //有在用倍卡，选择同倍率快到期卡
                            if (useFactor != null) {
                                String factorforestBagProp = forestBagProp.optJSONObject("propConfigVO").optJSONObject("detail").optString("factor");
                                if (factorforestBagProp.equals(useFactor)) {
                                    if (rightCard != null) {
                                        String factorrightCard = rightCard.optJSONObject("propConfigVO").optJSONObject("detail").optString("factor");
                                        long recentExpireTimerightCard = rightCard.optLong("recentExpireTime");
                                        long recentExpireTimeforestBagProp = forestBagProp.optLong("recentExpireTime");
                                        if (Float.parseFloat(factorrightCard) == Float.parseFloat(factorforestBagProp)) {
                                            if (recentExpireTimerightCard > recentExpireTimeforestBagProp) {
                                                rightCard = forestBagProp;
                                            }
                                        }
                                    } else {
                                        rightCard = forestBagProp;
                                    }
                                }
                            }
                            //没有在用倍卡，选择最高倍率卡
                            else {
                                if (rightCard != null) {
                                    String factorrightCard = rightCard.optJSONObject("propConfigVO").optJSONObject("detail").optString("factor");
                                    String factorforestBagProp = forestBagProp.optJSONObject("propConfigVO").optJSONObject("detail").optString("factor");
                                    if (Float.parseFloat(factorrightCard) < Float.parseFloat(factorforestBagProp)) {
                                        rightCard = forestBagProp;
                                    }
                                } else {
                                    rightCard = forestBagProp;
                                }
                            }
                            /*

                            String factorrightCard = rightCard.optJSONObject("propConfigVO").optJSONObject("detail").optString("factor");
                            String factorforestBagProp = forestBagProp.optJSONObject("propConfigVO").optJSONObject("detail").optString("factor");
                            long recentExpireTimerightCard = rightCard.optLong("recentExpireTime");
                            long recentExpireTimeforestBagProp = forestBagProp.optLong("recentExpireTime");
                            Log.forest("factorrightCard:"+factorrightCard);
                            Log.forest("factorforestBagProp:"+factorforestBagProp);
                            Log.forest("recentExpireTimerightCard:"+recentExpireTimerightCard);
                            Log.forest("recentExpireTimeforestBagProp:"+recentExpireTimeforestBagProp);
                            //有在用倍卡，选择同倍率快到期卡
                            if (useFactor != null) {
                                if (Float.parseFloat(factorrightCard) == Float.parseFloat(factorforestBagProp)) {
                                    if (recentExpireTimerightCard > recentExpireTimeforestBagProp) {
                                        rightCard = forestBagProp;
                                    }
                                }
                            }
                            //没有在用倍卡，选择最高倍率卡
                            else {
                                if (rightCard != null) {
                                    if (Float.parseFloat(factorrightCard) < Float.parseFloat(factorforestBagProp)) {
                                        rightCard = forestBagProp;
                                    }
                                } else {
                                    rightCard = forestBagProp;
                                }
                            }*/
                    }
                }
            }
            return rightCard;
        } catch (Throwable th) {
            Log.i(TAG, "useDoubleCard err:");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }

    private void usePropBeforeCollectEnergy(String userId) {
        if (Objects.equals(selfId, userId)) {
            return;
        }
        if (needDoubleClick()) {
            synchronized (usePropLockObj) {
                if (needDoubleClick()) {
                    useDoubleCard(getForestPropVOList());
                }
            }
        }
    }

    private Boolean needDoubleClick() {
        if (doubleClickType.getValue() == UsePropType.CLOSE) {
            return false;
        }
        Long doubleClickEndTime = usingProps.get(PropGroup.doubleClick.name());
        if (doubleClickEndTime == null) {
            return true;
        }
        return doubleClickEndTime < System.currentTimeMillis();
    }

    private void useDoubleCard(JSONArray forestPropVOList) {
        try {
            if (hasDoubleCardTime() && Status.canDoubleToday()) {
                // 背包查找 能量双击卡
                JSONObject jo = null;
                List<JSONObject> list = getPropGroup(forestPropVOList, PropGroup.doubleClick.name());
                if (!list.isEmpty()) {
                    jo = list.get(0);
                }
                if (jo == null || !jo.has("recentExpireTime")) {
                    if (doubleCardConstant.getValue()) {
                        // 商店兑换 限时能量双击卡
                        if (exchangeBenefit("SK20240805004754")) {
                            jo = getForestPropVO(getForestPropVOList(), "ENERGY_DOUBLE_CLICK_31DAYS");
                        } else if (exchangeBenefit("CR20230516000363")) {
                            jo = getForestPropVO(getForestPropVOList(), "LIMIT_TIME_ENERGY_DOUBLE_CLICK");
                        }
                    }
                }
                if (jo == null) {
                    return;
                }
                if (!jo.has("recentExpireTime") && doubleClickType.getValue() == UsePropType.ONLY_LIMIT_TIME) {
                    return;
                }
                // 使用能量双击卡
                if (consumeProp(jo)) {
                    Long endTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(jo.getJSONObject("propConfigVO").getLong("durationTime"));
                    usingProps.put(PropGroup.doubleClick.name(), endTime);
                    Status.DoubleToday();
                } else {
                    updateUsingPropsEndTime();
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "useDoubleCard err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private boolean hasDoubleCardTime() {
        long currentTimeMillis = System.currentTimeMillis();
        return TimeUtil.checkInTimeRange(currentTimeMillis, doubleCardTime.getValue());
    }

    /* 赠送道具 */
    private void giveProp() {
        Set<String> set = whoYouWantToGiveTo.getValue();
        if (set.isEmpty()) {
            return;
        }
        for (String userId : set) {
            if (UserIdMap.getCurrentUid() == null || Objects.equals(UserIdMap.getCurrentUid(), userId)) {
                continue;
            }
            giveProp(userId);
            break;
        }
    }

    private void giveProp(String targetUserId) {
        try {
            do {
                try {
                    JSONObject jo = new JSONObject(AntForestRpcCall.queryPropList(true));
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        return;
                    }
                    JSONArray forestPropVOList = jo.optJSONArray("forestPropVOList");
                    if (forestPropVOList != null && forestPropVOList.length() > 0) {
                        jo = forestPropVOList.getJSONObject(0);
                        String giveConfigId = jo.getJSONObject("giveConfigVO").getString("giveConfigId");
                        int holdsNum = jo.optInt("holdsNum", 0);
                        String propName = jo.getJSONObject("propConfigVO").getString("propName");
                        String propId = jo.getJSONArray("propIdList").getString(0);
                        jo = new JSONObject(AntForestRpcCall.giveProp(giveConfigId, propId, targetUserId));
                        if (MessageUtil.checkResultCode(TAG, jo)) {
                            Log.forest("赠送道具🎭[" + UserIdMap.getMaskName(targetUserId) + "]#" + propName);
                            if (holdsNum > 1 || forestPropVOList.length() > 1) {
                                continue;
                            }
                        }
                    }
                } finally {
                    TimeUtil.sleep(1500);
                }
                break;
            } while (true);
        } catch (Throwable th) {
            Log.i(TAG, "giveProp err:");
            Log.printStackTrace(TAG, th);
        }
    }

    /**
     * 绿色行动
     */
    private void ecoLife() {
        try {
            JSONObject jo = new JSONObject(EcoLifeRpcCall.queryHomePage());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject data = jo.getJSONObject("data");
            if (!data.getBoolean("openStatus")) {
                Log.forest("绿色任务☘未开通");
                jo = new JSONObject(EcoLifeRpcCall.openEcolife());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                if (!String.valueOf(true).equals(JsonUtil.getValueByPath(jo, "data.opResult"))) {
                    return;
                }
                Log.forest("绿色任务🍀报告大人，开通成功(～￣▽￣)～可以愉快的玩耍了");
                jo = new JSONObject(EcoLifeRpcCall.queryHomePage());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                data = jo.getJSONObject("data");
            }
            String dayPoint = data.getString("dayPoint");
            JSONArray actionListVO = data.getJSONArray("actionListVO");
            if (ecoLifeOptions.getValue().contains("dish")) {
                photoGuangPan(dayPoint);
            }
            if (ecoLifeOptions.getValue().contains("tick")) {
                ecoLifeTick(actionListVO, dayPoint);
            }
        } catch (Throwable th) {
            Log.i(TAG, "ecoLife err:");
            Log.printStackTrace(TAG, th);
        }
    }

    /* 绿色行动打卡 */

    private void ecoLifeTick(JSONArray actionListVO, String dayPoint) {
        try {
            String source = "source";
            for (int i = 0; i < actionListVO.length(); i++) {
                JSONObject actionVO = actionListVO.getJSONObject(i);
                JSONArray actionItemList = actionVO.getJSONArray("actionItemList");
                for (int j = 0; j < actionItemList.length(); j++) {
                    JSONObject actionItem = actionItemList.getJSONObject(j);
                    if (!actionItem.has("actionId")) {
                        continue;
                    }
                    if (actionItem.getBoolean("actionStatus")) {
                        continue;
                    }
                    String actionId = actionItem.getString("actionId");
                    String actionName = actionItem.getString("actionName");
                    if ("photoguangpan".equals(actionId)) {
                        continue;
                    }
                    JSONObject jo = new JSONObject(EcoLifeRpcCall.tick(actionId, dayPoint, source));
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.forest("绿色打卡🍀[" + actionName + "]");
                    }
                    TimeUtil.sleep(500);
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "ecoLifeTick err:");
            Log.printStackTrace(TAG, th);
        }
    }

    /**
     * 光盘行动
     */
    private void photoGuangPan(String dayPoint) {
        // if (!TaskCommon.IS_AFTER_6AM) {
        //    return;
        // }
        try {
            String source = "renwuGD";
            // 检查今日任务状态
            JSONObject jo = new JSONObject(EcoLifeRpcCall.queryDish(source, dayPoint));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            // 更新光盘照片
            Map<String, String> dishImage = new HashMap<>();
            JSONObject data = jo.optJSONObject("data");
            if (data != null) {
                String beforeMealsImageUrl = data.optString("beforeMealsImageUrl");
                String afterMealsImageUrl = data.optString("afterMealsImageUrl");
                if (!StringUtil.isEmpty(beforeMealsImageUrl) && !StringUtil.isEmpty(afterMealsImageUrl)) {
                    Pattern pattern = Pattern.compile("img/(.*)/original");
                    Matcher beforeMatcher = pattern.matcher(beforeMealsImageUrl);
                    if (beforeMatcher.find()) {
                        dishImage.put("BEFORE_MEALS", beforeMatcher.group(1));
                    }
                    Matcher afterMatcher = pattern.matcher(afterMealsImageUrl);
                    if (afterMatcher.find()) {
                        dishImage.put("AFTER_MEALS", afterMatcher.group(1));
                    }
                    TokenConfig.saveDishImage(dishImage);
                }
            }
            if (Objects.equals("SUCCESS", jo.getJSONObject("data").getString("status"))) {
                // Log.forest("光盘行动💿今日打卡已完成");
                return;
            }

            dishImage = TokenConfig.getRandomDishImage();
            if (dishImage == null) {
                Log.forest("光盘行动💿请先完成一次光盘打卡");
                return;
            }
            // 上传餐前照片
            jo = new JSONObject(EcoLifeRpcCall.uploadBeforeMealsDishImage(dishImage.get("BEFORE_MEALS"), dayPoint));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            // 上传餐后照片
            jo = new JSONObject(EcoLifeRpcCall.uploadAfterMealsDishImage(dishImage.get("AFTER_MEALS"), dayPoint));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            // 提交
            jo = new JSONObject(EcoLifeRpcCall.tick("photoguangpan", dayPoint, source));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            String toastMsg = jo.getJSONObject("data").getString("toastMsg");
            Toast.show("光盘行动💿打卡完成#" + toastMsg);
            Log.forest("光盘行动💿打卡完成#" + toastMsg + "[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
        } catch (Throwable t) {
            Log.i(TAG, "photoGuangPan err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void queryUserPatrol() {
        try {
            th:
            do {
                JSONObject jo = new JSONObject(AntForestRpcCall.queryUserPatrol());
                TimeUtil.sleep(500);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                JSONObject resData = new JSONObject(AntForestRpcCall.queryMyPatrolRecord());
                TimeUtil.sleep(500);
                if (resData.optBoolean("canSwitch")) {
                    JSONArray records = resData.getJSONArray("records");
                    for (int i = 0; i < records.length(); i++) {
                        JSONObject record = records.getJSONObject(i);
                        JSONObject userPatrol = record.getJSONObject("userPatrol");
                        if (userPatrol.getInt("unreachedNodeCount") > 0) {
                            if ("silent".equals(userPatrol.getString("mode"))) {
                                JSONObject patrolConfig = record.getJSONObject("patrolConfig");
                                String patrolId = patrolConfig.getString("patrolId");
                                resData = new JSONObject(AntForestRpcCall.switchUserPatrol(patrolId));
                                TimeUtil.sleep(500);
                                if (MessageUtil.checkResultCode(TAG, resData)) {
                                    Log.i("巡护⚖️-切换地图至" + patrolId);
                                }
                                continue th;
                            }
                            break;
                        }
                    }
                }

                JSONObject userPatrol = jo.getJSONObject("userPatrol");
                int currentNode = userPatrol.getInt("currentNode");
                String currentStatus = userPatrol.getString("currentStatus");
                int patrolId = userPatrol.getInt("patrolId");
                JSONObject chance = userPatrol.getJSONObject("chance");
                int leftChance = chance.getInt("leftChance");
                int leftStep = chance.getInt("leftStep");
                int usedStep = chance.getInt("usedStep");
                if ("STANDING".equals(currentStatus)) {
                    if (leftChance > 0) {
                        jo = new JSONObject(AntForestRpcCall.patrolGo(currentNode, patrolId));
                        TimeUtil.sleep(500);
                        patrolKeepGoing(jo.toString(), currentNode, patrolId);
                        continue;
                    } else if (leftStep >= 2000 && usedStep < 10000) {
                        jo = new JSONObject(AntForestRpcCall.exchangePatrolChance(leftStep));
                        TimeUtil.sleep(300);
                        if (MessageUtil.checkResultCode(TAG, jo)) {
                            int addedChance = jo.optInt("addedChance", 0);
                            Log.forest("步数兑换⚖️[巡护次数*" + addedChance + "]");
                            continue;
                        }
                    }
                } else if ("GOING".equals(currentStatus)) {
                    patrolKeepGoing(null, currentNode, patrolId);
                }
                break;
            } while (true);
        } catch (Throwable t) {
            Log.i(TAG, "queryUserPatrol err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void patrolKeepGoing(String s, int nodeIndex, int patrolId) {
        try {
            do {
                if (s == null) {
                    s = AntForestRpcCall.patrolKeepGoing(nodeIndex, patrolId, "image");
                }
                JSONObject jo = new JSONObject(s);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                JSONArray jaEvents = jo.optJSONArray("events");
                if (jaEvents == null || jaEvents.length() == 0) {
                    return;
                }
                JSONObject userPatrol = jo.getJSONObject("userPatrol");
                int currentNode = userPatrol.getInt("currentNode");
                JSONObject events = jo.getJSONArray("events").getJSONObject(0);
                JSONObject rewardInfo = events.optJSONObject("rewardInfo");
                if (rewardInfo != null) {
                    JSONObject animalProp = rewardInfo.optJSONObject("animalProp");
                    if (animalProp != null) {
                        JSONObject animal = animalProp.optJSONObject("animal");
                        if (animal != null) {
                            Log.forest("巡护森林🏇🏻[" + animal.getString("name") + "碎片]");
                        }
                    }
                }
                if (!"GOING".equals(jo.getString("currentStatus"))) {
                    return;
                }
                JSONObject materialInfo = events.getJSONObject("materialInfo");
                String materialType = materialInfo.optString("materialType", "image");
                s = AntForestRpcCall.patrolKeepGoing(currentNode, patrolId, materialType);
                TimeUtil.sleep(100);
            } while (true);
        } catch (Throwable t) {
            Log.i(TAG, "patrolKeepGoing err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /* 新版保护地：自动前进（领取首页机会 + 掷骰推进 + 事件确认） */
    private void monopolyPatrol() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryMonopolyMapPage());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            String currentRegionCode = jo.optString("currentRegionCode", "hongshandongwuyuan");
            String currentMapCode = jo.optString("currentMapCode");
            String regionName = "";
            String regionCode = "";
            String mapNameText = "";
            String mapCode = "";

            JSONArray regionList = jo.optJSONArray("regionList");
            if (regionList != null) {
                for (int i = 0; i < regionList.length(); i++) {
                    JSONObject region = regionList.optJSONObject(i);
                    String regionAvailability = region.optString("regionAvailability");
                    boolean currentRegion = region.optBoolean("currentRegion");
                    if (regionAvailability.equals("AVAILABLE") && currentRegion) {
                        regionName = region.optString("regionName");
                        regionCode = region.optString("regionCode");
                        currentRegionCode = regionCode;
                    }
                }
            }

            JSONArray currentRegionMapList = jo.optJSONArray("currentRegionMapList");
            if (currentRegionMapList != null) {
                for (int i = 0; i < currentRegionMapList.length(); i++) {
                    JSONObject currentRegionMap = currentRegionMapList.optJSONObject(i);
                    boolean currentMap = currentRegionMap.optBoolean("currentMap");
                    String mapAvailability = currentRegionMap.optString("mapAvailability");
                    if (mapAvailability.equals("AVAILABLE") && currentMap) {
                        mapCode = currentRegionMap.optString("mapCode");
                        JSONObject displayInfo = currentRegionMap.optJSONObject("displayInfo");
                        JSONObject mapDisplay = displayInfo.optJSONObject("mapDisplay");
                        mapNameText = mapDisplay.optString("mapNameText");
                    }
                }
            }
            MONOPOLY_REGION_HSDWY = currentRegionCode;
            currentMapCode = mapCode;

            JSONObject state = new JSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
            if (!MessageUtil.checkResultCode(TAG, state)) {
                return;
            }
            JSONObject regionInfo = state.optJSONObject("regionInfo");
            JSONObject mapInfo = state.optJSONObject("mapInfo");
            JSONObject userInfo = state.optJSONObject("userInfo");
            int totalDiceCount = state.optInt("totalDiceCount");
            if (regionInfo == null || mapInfo == null || userInfo == null) {
                Log.record("新保护地🌲入口缺少区域、地图或用户状态，跳过本轮");
                return;
            }
            if (isMonopolyClosed(regionInfo) || isMonopolyClosed(mapInfo)) {
                Log.forest("新保护地🌲当前区域或地图不在开放期，停止本轮");
                return;
            }
            int energyCount = userInfo.optInt("energyCount");
            int stepCount = userInfo.optInt("stepCount");

            // 排行信息(全容错,已对照RPC.txt queryMonopolyRanking抓包验证): 累计排名/证书/勋章取TOTAL榜myself,
            // 今日巡护名次/步数/勋章/证书取DAY榜myself, friendRanking各取前3(跳过无数据条目); 任一失败仅略过对应段不中断主流程
            Integer rank = null;
            Integer mapCount = null;
            Integer medalCount = null;
            String totalTop3 = "";
            try {
                JSONObject joRank = new JSONObject(AntForestRpcCall.queryMonopolyRanking("TOTAL"));
                if (MessageUtil.checkResultCode(TAG, joRank)) {
                    JSONObject myself = joRank.optJSONObject("myself");
                    if (myself != null) {
                        if (myself.has("rank")) {
                            rank = myself.optInt("rank");
                        }
                        if (myself.has("mapCount")) {
                            mapCount = myself.optInt("mapCount");
                        }
                        if (myself.has("medalCount")) {
                            medalCount = myself.optInt("medalCount");
                        }
                    }
                    totalTop3 = monopolyTop3Text(joRank.optJSONArray("friendRanking"));
                }
            } catch (Throwable t) {
                Log.printStackTrace(TAG, t);
            }
            Integer dayRank = null;
            Integer dayStepCount = null;
            Integer dayMedalCount = null;
            Integer dayMapCount = null;
            String dayTop3 = "";
            try {
                JSONObject joDayRank = new JSONObject(AntForestRpcCall.queryMonopolyRanking("DAY"));
                if (MessageUtil.checkResultCode(TAG, joDayRank)) {
                    JSONObject dayMyself = joDayRank.optJSONObject("myself");
                    if (dayMyself != null) {
                        if (dayMyself.has("rank")) {
                            dayRank = dayMyself.optInt("rank");
                        }
                        if (dayMyself.has("stepCount")) {
                            dayStepCount = dayMyself.optInt("stepCount");
                        }
                        if (dayMyself.has("medalCount")) {
                            dayMedalCount = dayMyself.optInt("medalCount");
                        }
                        if (dayMyself.has("mapCount")) {
                            dayMapCount = dayMyself.optInt("mapCount");
                        }
                    }
                    dayTop3 = monopolyTop3Text(joDayRank.optJSONArray("friendRanking"));
                }
            } catch (Throwable t) {
                Log.printStackTrace(TAG, t);
            }

            StringBuilder monopolyInfo = new StringBuilder("新保护地🌲[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]巡护[" + regionName + "]地图[" + mapNameText + "(" + currentMapCode + ")]累计排名" + n(rank) + "证书" + n(mapCount) + "勋章" + n(medalCount) + "总能量" + energyCount + "g总步数" + stepCount + "掷骰子剩余" + totalDiceCount);
            if (dayRank != null || dayStepCount != null || dayMedalCount != null || dayMapCount != null) {
                monopolyInfo.append(";今日巡护第").append(n(dayRank)).append("名").append(n(dayStepCount)).append("步/勋章").append(n(dayMedalCount)).append("/证书").append(n(dayMapCount));
            }
            if (!dayTop3.isEmpty()) {
                monopolyInfo.append(";今日前三").append(dayTop3);
            }
            if (!totalTop3.isEmpty()) {
                monopolyInfo.append(";累计前三").append(totalTop3);
            }
            Log.info(monopolyInfo.toString());

            if (switchNewMonopolyMap.getValue()) {
                // 从第一张地图起依次检测: ownedCreatureCount达到totalCreatureCount且creatureList全部达到maxLevel视为已满级, 找第一张未达成的AVAILABLE地图
                String targetRegionCode = currentRegionCode;
                String targetMapCode = null;
                String targetMapName = "";
                String targetRegionName = "";
                String targetReason = "";
                boolean crossRegion = false;
                StringBuilder detectDetail = new StringBuilder();
                if (currentRegionMapList != null) {
                    for (int i = 0; i < currentRegionMapList.length(); i++) {
                        JSONObject mapItem = currentRegionMapList.optJSONObject(i);
                        // 仅检测可保护(AVAILABLE)的地图, PREPARING等状态直接跳过
                        if (mapItem == null || !"AVAILABLE".equals(mapItem.optString("mapAvailability"))) {
                            continue;
                        }
                        int ownedCreatureCount = mapItem.optInt("ownedCreatureCount");
                        int totalCreatureCount = mapItem.optInt("totalCreatureCount");
                        boolean allCollected = totalCreatureCount > 0 && ownedCreatureCount >= totalCreatureCount;
                        JSONArray creatureList = mapItem.optJSONArray("creatureList");
                        // 必须逐只核对到level达到maxLevel, 动物列表为空/缺失时不能视为满级
                        boolean allMaxLevel = creatureList != null && creatureList.length() > 0;
                        StringBuilder notMaxNames = new StringBuilder();
                        if (creatureList != null) {
                            for (int j = 0; j < creatureList.length(); j++) {
                                JSONObject creature = creatureList.optJSONObject(j);
                                if (creature == null) {
                                    continue;
                                }
                                if (creature.optInt("level") < creature.optInt("maxLevel")) {
                                    allMaxLevel = false;
                                    if (notMaxNames.length() > 0) {
                                        notMaxNames.append(",");
                                    }
                                    notMaxNames.append(creature.optString("creatureName"));
                                }
                            }
                        }
                        detectDetail.append(mapItem.optString("mapCode")).append(":")
                                .append(ownedCreatureCount).append("/").append(totalCreatureCount)
                                .append(allMaxLevel ? "满级" : "未满级[" + notMaxNames + "]");
                        // 收集数达到总数 且 每只动物等级都达到maxLevel, 两个条件同时满足才跳过该图检测下一张
                        if (allCollected && allMaxLevel) {
                            continue;
                        }
                        targetMapCode = mapItem.optString("mapCode");
                        JSONObject targetDisplay = mapItem.optJSONObject("displayInfo");
                        JSONObject targetMapDisplay = targetDisplay == null ? null : targetDisplay.optJSONObject("mapDisplay");
                        targetMapName = targetMapDisplay == null ? targetMapCode : targetMapDisplay.optString("mapNameText", targetMapCode);
                        targetReason = "动物" + ownedCreatureCount + "/" + totalCreatureCount
                                + (allMaxLevel ? "已满级" : "未满级[" + notMaxNames + "]");
                        break;
                    }
                }
                // 调试日志: 输出各图完成度与选中目标, 需要排查时再放开
                //Log.record("新保护地🌲切换检测" + (detectDetail.length() == 0 ? "无地图数据" : detectDetail.toString()
                //        + (targetMapCode == null ? "→无目标" : "→目标" + targetMapCode)));
                // 当前地区所有AVAILABLE地图均已收集满级: 若仍有未开放(PREPARING等)地图则等待, 只有lv1至最后一张地图全部完成才切换到下一个保护区
                boolean hasUnavailableMap = false;
                int lastMapLevel = 0;
                if (targetMapCode == null && currentRegionMapList != null) {
                    for (int i = 0; i < currentRegionMapList.length(); i++) {
                        JSONObject mapItem = currentRegionMapList.optJSONObject(i);
                        if (mapItem == null) {
                            continue;
                        }
                        int mapLevel = mapItem.optInt("level", 0);
                        if (mapLevel > lastMapLevel) {
                            lastMapLevel = mapLevel;
                        }
                        if (!"AVAILABLE".equals(mapItem.optString("mapAvailability"))) {
                            hasUnavailableMap = true;
                        }
                    }
                }
                boolean nextRegionAvailable = false;
                boolean hasOtherRegion = false;
                String nextRegionName = "";
                String nextRegionStatus = "";
                if (targetMapCode == null && !hasUnavailableMap && lastMapLevel > 0 && regionList != null) {
                    // 当前地区lv1至最后一张地图全部收集满级且均已开放, 切换到下一个保护区; 新保护区无地图数据, 首张地图按 regionCode#lv1 规律推算
                    for (int i = 0; i < regionList.length(); i++) {
                        JSONObject region = regionList.optJSONObject(i);
                        if (region == null) {
                            continue;
                        }
                        String nextRegionCode = region.optString("regionCode");
                        if (currentRegionCode.equals(nextRegionCode)) {
                            continue;
                        }
                        // 记录第一个其他保护区的名称和状态(无论是否开放)
                        hasOtherRegion = true;
                        nextRegionName = region.optString("regionName", "");
                        nextRegionStatus = region.optString("regionAvailability");
                        // 仅切换到已开放(AVAILABLE)的保护区, PREPARING等状态跳过
                        if (!"AVAILABLE".equals(nextRegionStatus)) {
                            continue;
                        }
                        nextRegionAvailable = true;
                        targetRegionCode = nextRegionCode;
                        targetMapCode = nextRegionCode + "#lv1";
                        crossRegion = true;
                        targetRegionName = nextRegionName;
                        targetReason = "当前地区lv1至lv" + lastMapLevel + "均已收集满级";
                        break;
                    }
                }
                if (targetMapCode == null) {
                    if (lastMapLevel == 0) {
                        Log.record("新保护地🌲当前地区无地图数据，跳过切换检测");
                    } else if (hasUnavailableMap) {
                        Log.record("新保护地🌲当前地区已开放地图均已收集满级，后续地图未开放，等待不切换");
                    } else if (!hasOtherRegion) {
                        Log.record("新保护地🌲当前地区lv1至lv" + lastMapLevel + "均已收集满级，暂无后续保护区");
                    } else if (!nextRegionAvailable) {
                        Log.record("新保护地🌲当前地区lv1至lv" + lastMapLevel + "均已收集满级，下一保护区["
                                + nextRegionName + "]状态" + nextRegionStatus + "尚未开放，等待不切换");
                    } else {
                        Log.record("新保护地🌲所有保护区地图动物均已收集且满级，无需切换");
                    }
                } else if (targetRegionCode.equals(currentRegionCode) && targetMapCode.equals(currentMapCode)) {
                    //Log.record("新保护地🌲当前地图[" + targetMapName + "(" + targetMapCode + ")]" + targetReason + "，无需切换");
                } else {
                    if (crossRegion) {
                        Log.record("新保护地🌲" + targetReason + "，切换到保护区[" + targetRegionName + "]("
                                + targetRegionCode + ")首张地图" + targetMapCode + "(地图名未知)，开始切换");
                    } else {
                        Log.record("新保护地🌲目标地图[" + targetMapName + "(" + targetMapCode + ")]" + targetReason + "开始切换");
                    }
                    JSONObject switchJo = new JSONObject(AntForestRpcCall.switchMonopolyMap(targetMapCode, targetRegionCode));
                    if (MessageUtil.checkResultCode(TAG, switchJo)) {
                        if (crossRegion) {
                            Log.forest("新保护地🌲已切换保护区[" + targetRegionName + "](" + targetRegionCode + "/" + targetMapCode + ")继续巡护");
                        } else {
                            Log.forest("新保护地🌲已切换到地图[" + targetMapName + "(" + targetMapCode + ")]继续巡护");
                        }
                        // 切换成功后重新查询入口信息, 新state的regionInfo.regionCode即为新区域, 本轮继续按新地图巡护
                        state = new JSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
                        if (!MessageUtil.checkResultCode(TAG, state)) {
                            return;
                        }
                        regionInfo = state.optJSONObject("regionInfo");
                        mapInfo = state.optJSONObject("mapInfo");
                        userInfo = state.optJSONObject("userInfo");
                        if (regionInfo == null || mapInfo == null || userInfo == null) {
                            Log.record("新保护地🌲切换后入口缺少区域、地图或用户状态，跳过本轮");
                            return;
                        }
                        if (isMonopolyClosed(regionInfo) || isMonopolyClosed(mapInfo)) {
                            Log.record("新保护地🌲切换后的区域或地图不在开放期，停止本轮");
                            return;
                        }
                    }
                }
            }

            regionCode = regionInfo.optString("regionCode");
            boolean tasksEnabled = monopolyTasks.getValue();
            // 掷骰会解锁新任务，所以骰子用完后需要再补跑一次任务
            boolean tasksMayHaveChanged = false;
            if (tasksEnabled) {
                monopolyTask(regionCode);
            }
            // 首次进入地图时服务端要求带引导参数掷骰
            boolean guideRoll = userInfo.optBoolean("firstEnterMonopoly");
            JSONObject pendingEvent = state.optJSONObject("eventInfo");

            // 首页道具：领取当日巡护机会（骰子）
            JSONObject props = new JSONObject(AntForestRpcCall.triggerMonopolyHomeProps());
            if (!MessageUtil.checkResultCode(TAG, props)) {
                return;
            }
            state = new JSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
            if (!MessageUtil.checkResultCode(TAG, state)) {
                return;
            }
            if (pendingEvent == null) {
                pendingEvent = state.optJSONObject("eventInfo");
            }

            Set<String> confirmedEvents = new HashSet<>();
            while (!Thread.currentThread().isInterrupted()) {
                JSONObject event = pendingEvent;
                if (event != null && event.optBoolean("needConfirm")) {
                    String eventId = event.optString("eventId");
                    String eventType = event.optString("eventType");
                    String actionKey = "CHARITY".equals(eventType) ? "confirm" : ("SPECIAL".equals(eventType) ? "skip" : null);
                    JSONObject displayInfo = event.optJSONObject("displayInfo");
                    if (eventId.isEmpty() || actionKey == null || displayInfo == null || !"SINGLE_ACTION_CONFIRM".equals(displayInfo.optString("flowType"))) {
                        Log.record("新保护地🌲事件缺少可执行决策，保留当前事件[" + eventId + "]");
                        return;
                    }
                    if (confirmedEvents.contains(eventId)) {
                        Log.record("新保护地🌲事件[" + eventId + "]已提交确认，等待后续调度刷新");
                        return;
                    }
                    JSONObject confirmation = new JSONObject(AntForestRpcCall.confirmMonopolyEvent(eventId, actionKey));
                    if (!MessageUtil.checkResultCode(TAG, confirmation)) {
                        return;
                    }
                    confirmedEvents.add(eventId);
                    Log.record("新保护地🌲事件[" + eventId + "]已确认");
                    if (confirmation.has("eventResult")) {
                        JSONArray eventResult = confirmation.optJSONArray("eventResult");
                        if (eventResult != null) {
                            for (int i = 0; i < eventResult.length(); i++) {
                                JSONObject Result = eventResult.optJSONObject(i);
                                if (Result == null) {
                                    continue;
                                }
                                String rewardCode = Result.optString("rewardCode");
                                JSONObject triggerData = Result.optJSONObject("triggerData");
                                int energy = triggerData.optInt("energy");
                                if (rewardCode.contains("energy")) {
                                    Log.forest("新保护地🌲掷骰事件领取[" + rewardCode + "]" + energy + "g能量");
                                    Toast.show("新保护地🌲掷骰事件领取" + energy + "g能量");
                                    Statistics.addData(Statistics.DataType.COLLECTED, energy);

                                } else {
                                    Log.forest("新保护地🌲掷骰事件领取[" + rewardCode + "]");
                                }
                            }
                        }
                    }
                    state = new JSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
                    if (!MessageUtil.checkResultCode(TAG, state)) {
                        return;
                    }
                    pendingEvent = state.optJSONObject("eventInfo");
                    continue;
                }

                int diceCount = state.optInt("totalDiceCount", -1);
                if (diceCount < 0) {
                    Log.record("新保护地🌲响应缺少可用骰子数量，跳过本轮");
                    return;
                }
                if (diceCount == 0) {
                    if (tasksEnabled && tasksMayHaveChanged) {
                        monopolyTask(regionCode);
                        tasksMayHaveChanged = false;
                        state = new JSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
                        if (!MessageUtil.checkResultCode(TAG, state)) {
                            return;
                        }
                        pendingEvent = state.optJSONObject("eventInfo");
                        continue;
                    }
                    //Log.record("新保护地🌲当前无可用骰子，后续调度继续查询");
                    return;
                }

                JSONObject currentUserInfo = state.optJSONObject("userInfo");
                int previousSteps = currentUserInfo == null ? -1 : currentUserInfo.optInt("stepCount", -1);
                JSONObject roll = new JSONObject(AntForestRpcCall.rollMonopolyDice(guideRoll));
                if (!MessageUtil.checkResultCode(TAG, roll)) {
                    return;
                }
                guideRoll = false;
                pendingEvent = roll.optJSONObject("eventInfo");
                JSONObject rollUserInfo = roll.optJSONObject("userInfo");
                int steps = rollUserInfo == null ? -1 : rollUserInfo.optInt("stepCount", -1);
                // 骰子数、步数、事件三者都没变化，说明服务端没受理，留到下次调度重试
                if (roll.optInt("totalDiceCount", -1) == diceCount && steps == previousSteps && pendingEvent == null) {
                    Log.record("新保护地🌲掷骰后未确认状态变化，保留后续调度");
                    return;
                }
                tasksMayHaveChanged = true;
                Log.forest("新保护地🌲掷骰[" + roll.optInt("diceNumber") + "]剩余" + roll.optInt("totalDiceCount") + "次");
                TimeUtil.sleep(500);
                state = new JSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
                if (!MessageUtil.checkResultCode(TAG, state)) {
                    return;
                }
                if (pendingEvent == null) {
                    pendingEvent = state.optJSONObject("eventInfo");
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "monopolyPatrol err:");
            Log.printStackTrace(TAG, t);
        }
    }


    private void newMonopolyExchangeTree() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryMonopolyMapPage());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            String regionName = "";
            JSONArray regionList = jo.optJSONArray("regionList");
            if (regionList != null) {
                for (int i = 0; i < regionList.length(); i++) {
                    JSONObject region = regionList.optJSONObject(i);
                    if (region.optBoolean("currentRegion")) {
                        regionName = region.optString("regionName");
                    }
                }
            }
            JSONArray currentRegionMapList = jo.optJSONArray("currentRegionMapList");
            if (currentRegionMapList == null) {
                return;
            }
            for (int i = 0; i < currentRegionMapList.length(); i++) {
                JSONObject currentRegionMap = currentRegionMapList.optJSONObject(i);
                if (currentRegionMap == null) {
                    continue;
                }
                // certificateStatus: PENDING=证书未兑换(未保护) OBTAINED=已兑换(已保护)
                if (!"PENDING".equals(currentRegionMap.optString("certificateStatus")) || "OBTAINED".equals(currentRegionMap.optString("certificateStatus"))) {
                    continue;
                }
                if (!"AVAILABLE".equals(currentRegionMap.optString("mapAvailability")) && !preNewMonopolyExchangeTree.getValue()) {
                    continue;
                }
                String mapCode = currentRegionMap.optString("mapCode");
                int projectId = extractMonopolyProjectId(currentRegionMap.optJSONObject("displayInfo"));
                if (projectId <= 0) {
                    //Log.record("新保护地🌲[" + regionName + "-" + mapCode + "]证书未保护但未取到projectId，跳过");
                    continue;
                }
                JSONObject exchangeRes = new JSONObject(AntForestRpcCall.newMonopolyExchangeTree(projectId));
                // exchangeTree响应为header/resData网关包装，resultCode与userCertificate都在resData内
                if (!MessageUtil.checkResultCode(TAG, exchangeRes)) {
                    return;
                }
                JSONObject cert = exchangeRes.optJSONObject("userCertificate");
                String levelName = "";
                String subName = "";
                int level = 0;
                if (cert != null) {
                    JSONObject extendInfo = cert.optJSONObject("extendInfo");
                    if (extendInfo != null) {
                        level = extendInfo.optInt("level");
                        levelName = extendInfo.optString("levelName");
                        subName = extendInfo.optString("subName");
                    }
                }
                if ("SUCCESS".equalsIgnoreCase(exchangeRes.optString("resultCode"))) {
                    Log.forest("新保护地🌲[" + regionName + "-" + mapCode + "]证书兑换[" + levelName + "]LV" + level + "(" + subName + ")");
                } else {
                    Log.record("新保护地🌲[" + regionName + "-" + mapCode + "]证书兑换失败 projectId=" + projectId + " " + exchangeRes.optString("resultDesc"));
                }
                TimeUtil.sleep(500);
            }
        } catch (Throwable t) {
            Log.i(TAG, "newMonopolyExchangeTree err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /* 从新版保护地地图 displayInfo 的跳转链接中提取projectId（数字，兼容URL编码 %3D 形式），取不到返回0 */
    private int extractMonopolyProjectId(JSONObject displayInfo) {
        if (displayInfo == null) {
            return 0;
        }
        JSONObject mapDisplay = displayInfo.optJSONObject("mapDisplay");
        if (mapDisplay == null) {
            return 0;
        }
        String url = mapDisplay.optString("lockedRedirectUrl");
        Matcher matcher = Pattern.compile("projectId(?:%3D|=)(\\d+)").matcher(url);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }


    /* 新版保护地：区域/地图是否未开放或已结束（缺少日期字段视为一直开放） */
    private boolean isMonopolyClosed(JSONObject activity) {
        long now = System.currentTimeMillis();
        long start = parseMonopolyDate(activity.optString("startDate"));
        long end = parseMonopolyDate(activity.optString("endDate"));
        return (start > 0 && now < start) || (end > 0 && now >= end);
    }

    /* 解析新版保护地的 "yyyy-MM-dd HH:mm:ss" 时间串（东八区），解析失败返回 0 */
    private long parseMonopolyDate(String date) {
        if (StringUtil.isEmpty(date)) {
            return 0L;
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
            format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            format.setLenient(false);
            Date parsed = format.parse(date);
            return parsed == null ? 0L : parsed.getTime();
        } catch (ParseException e) {
            return 0L;
        }
    }

    /* 新版保护地部分接口的响应会被包在 resData 里 */
    private JSONObject monopolyResponse(String raw) throws JSONException {
        JSONObject jo = new JSONObject(raw);
        JSONObject resData = jo.optJSONObject("resData");
        return resData == null ? jo : resData;
    }

    /* 新版保护地：完成可自动执行的任务并领取奖励 */
    private boolean monopolyTask(String regionCode) {
        String sceneCode;
        if (MONOPOLY_REGION_HSDWY.equals(regionCode)) {
            sceneCode = MONOPOLY_TASK_SCENE_HSDWY;
        } else {
            Log.forest("新保护地🌲当前区域[" + regionCode + "]没有任务场景，继续地图巡护");
            return false;
        }
        boolean changed = false;
        try {
            // 每轮只处理一个任务，最多 20 轮：服务端状态不变时靠轮次上限收敛
            for (int round = 0; round < 20; round++) {
                JSONObject jo = monopolyResponse(AntForestRpcCall.listMonopolyTasks(regionCode, sceneCode));
                if (!MessageUtil.checkSuccess(TAG, jo)) {
                    return changed;
                }
                JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
                if (taskInfoList == null) {
                    Log.record("新保护地🌲任务列表为空，跳过");
                    return changed;
                }

                for (int i = 0; i < taskInfoList.length(); i++) {
                    JSONObject task = taskInfoList.optJSONObject(i);
                    JSONObject taskBaseInfo = task.getJSONObject("taskBaseInfo");
                    JSONObject bizInfo = new JSONObject(taskBaseInfo.getString("bizInfo"));
                    String taskTitle = bizInfo.getString("taskTitle");
                    String taskStatus = taskBaseInfo.optString("taskStatus");
                    String taskType = taskBaseInfo.optString("taskType");

                    //黑名单任务跳过,仅领取
                    if (MonopolyTaskList.getValue().contains(taskTitle)) {
                        if (taskStatus.equals("FINISHED")) {
                            monopolyReceiveTaskAward(taskType, sceneCode, taskTitle);
                        }
                        continue;
                    }
                    if (taskStatus.equals("RECEIVED")) {
                        continue;
                    }
                    if (taskStatus.equals("FINISHED") && !monopolyReceiveTaskAward(taskType, sceneCode, taskTitle)) {
                        continue;
                    }
                    if (taskStatus.equals("TODO") && monopolyFinishTask(taskBaseInfo, taskType, sceneCode, taskTitle)) {
                        monopolyReceiveTaskAward(taskType, sceneCode, taskTitle);
                        continue;
                    }

                    TimeUtil.sleep(1000);
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "monopolyTask err:");
            Log.printStackTrace(TAG, t);
        }
        return changed;
    }

    /* 新版保护地：按服务端下发的时长等待后提交任务完成 */
    private boolean monopolyFinishTask(JSONObject base, String taskType, String sceneCode, String title) {
        try {
            long seconds = 0L;
            String prodPlayParam = base.optString("prodPlayParam");
            if (!prodPlayParam.isEmpty()) {
                seconds = new JSONObject(prodPlayParam).optLong("timeCount", 0L);
            }
            /*if (seconds <= 0 || seconds > MONOPOLY_MAX_TASK_SECONDS) {
                Log.record("新保护地🌲任务[" + title + "]下发时长异常(" + seconds + "s)，跳过");
                return false;
            }*/
            TimeUtil.sleep(TimeUnit.SECONDS.toMillis(seconds));
            JSONObject jo = monopolyResponse(AntForestRpcCall.finishMonopolyTask(taskType, sceneCode));
            MessageUtil.checkResultCodeAndMarkTaskBlackList("MonopolyTaskList", title, jo);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.forest("新保护地🧾任务完成[" + title + "]");
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "monopolyFinishTask err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    /* 新版保护地：领取任务奖励 */
    private boolean monopolyReceiveTaskAward(String taskType, String sceneCode, String title) {
        try {
            JSONObject jo = monopolyResponse(AntForestRpcCall.receiveMonopolyTask(taskType, sceneCode));
            MessageUtil.checkResultCodeAndMarkTaskBlackList("MonopolyTaskList", title, jo);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                int incAwardCount = jo.optInt("incAwardCount");
                Log.forest("新保护地🎖️任务奖励[" + title + "][巡护次数*" + incAwardCount + "]");
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "monopolyReceiveTaskAward err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    /* 新版动物伙伴：领取已产生的派遣能量 */
    private void collectMonopolyAnimalEnergy() {
        try {
            String uid = UserIdMap.getCurrentUid();
            if (StringUtil.isEmpty(uid)) {
                return;
            }
            JSONObject jo = monopolyResponse(AntForestRpcCall.queryUsingCreatureInfo(uid));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject creature = jo.optJSONObject("userCreatureVO");
            if (creature == null) {
                return;
            }
            JSONObject energy = creature.optJSONObject("robEnergyVO");
            if (energy == null) {
                Log.record("新动物伙伴🦩缺少能量状态，跳过");
                return;
            }
            if (energy.optBoolean("energyIsCollect")) {
                return;
            }
            if (energy.optInt("yesterdayRobEnergy", 0) <= 0) {
                // 昨日没有产出能量，属于正常情况，不打扰用户
                return;
            }
            String creatureCode = creature.optString("creatureCode");
            String shortDay = energy.optString("yesterdayShortDay");
            if (!energy.has("energyIsCollect") || creatureCode.isEmpty() || shortDay.isEmpty()) {
                Log.record("新版动物伙伴🦩能量缺少领取标识，跳过");
                return;
            }
            JSONObject collect = monopolyResponse(AntForestRpcCall.collectMonopolyCreatureEnergy(creatureCode, shortDay));
            if (!MessageUtil.checkResultCode(TAG, collect)) {
                return;
            }
            int collected = collect.optInt("collectedEnergy", -1);
            if (collected < 0) {
                Log.forest("新版动物伙伴🦩领取成功但缺少实际到账量，不计入统计");
                return;
            }
            if (collected > 0) {
                Statistics.addData(Statistics.DataType.COLLECTED, collected);
            }
            Log.forest("新版伙伴🦩[" + creature.optString("creatureName", creatureCode) + "]派遣能量[" + collected + "g]");
        } catch (Throwable t) {
            Log.i(TAG, "collectMonopolyAnimalEnergy err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /* 新版动物伙伴：自动派遣（已有动物在巡护时不替换） */
    private boolean dispatchMonopolyAnimal(boolean canConsumeAnimalProp) {
        try {
            if (!canConsumeAnimalProp) {
                return false;
            }
            JSONObject jo = new JSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            if (jo.optBoolean("usingMonopolyCreature")) {
                Log.record("新版动物伙伴🦩已有动物在巡护，保留当前伙伴");
                return true;
            }
            JSONArray creatureList = jo.optJSONArray("creatureList");
            if (creatureList == null) {
                Log.record("新版动物伙伴🦩入口缺少动物列表，跳过");
                return false;
            }
            List<JSONObject> candidates = new ArrayList<>();
            boolean allHasInitialRobEnergy = true;
            for (int i = 0; i < creatureList.length(); i++) {
                JSONObject creature = creatureList.optJSONObject(i);
                if (creature == null || !isMonopolyCreatureIdle(creature)) {
                    continue;
                }
                candidates.add(creature);
                if (creature.isNull("initialRobEnergy")) {
                    allHasInitialRobEnergy = false;
                }
            }
            if (candidates.isEmpty()) {
                Log.record("新版动物伙伴🦩当前没有可派遣的动物");
                return false;
            }
            // 都能给出预计产出时选最高的，否则按服务端顺序取第一个
            JSONObject selected = candidates.get(0);
            if (allHasInitialRobEnergy) {
                for (JSONObject candidate : candidates) {
                    if (candidate.optInt("initialRobEnergy") > selected.optInt("initialRobEnergy")) {
                        selected = candidate;
                    }
                }
            }
            String creatureCode = selected.getString("creatureCode");
            JSONObject assigned = new JSONObject(AntForestRpcCall.assignMonopolyCreature(creatureCode));
            if (MessageUtil.checkResultCode(TAG, assigned)) {
                Log.forest("新版动物伙伴🦩派遣[" + selected.optString("creatureName", creatureCode) + "]");
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "dispatchMonopolyAnimal err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    /* 新版动物是否空闲可派遣 */
    private boolean isMonopolyCreatureIdle(JSONObject creature) {
        if (StringUtil.isEmpty(creature.optString("creatureCode"))) {
            return false;
        }
        if ("using".equals(creature.optString("status")) || creature.optLong("assignTime", 0L) > 0) {
            return false;
        }
        JSONObject energy = creature.optJSONObject("robEnergyVO");
        if (energy == null) {
            return true;
        }
        if (energy.has("robRemainDays") && energy.optInt("robRemainDays") <= 0) {
            return false;
        }
        int maxWorkDays = energy.optInt("maxWorkDays", 0);
        return maxWorkDays <= 0 || energy.optInt("alreadyWorkDays", 0) < maxWorkDays;
    }

    // 查询可派遣伙伴，返回是否派遣成功
    private boolean queryAnimalPropList() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryAnimalPropList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray animalProps = jo.getJSONArray("animalProps");
            JSONObject animalProp = null;
            for (int i = 0; i < animalProps.length(); i++) {
                jo = animalProps.getJSONObject(i);
                if (animalProp == null) {
                    animalProp = jo;
                    if (consumeAnimalPropType.getValue() == ConsumeAnimalPropType.SEQUENCE) {
                        break;
                    }
                } else if (jo.getJSONObject("main").getInt("holdsNum") > animalProp.getJSONObject("main").getInt("holdsNum")) {
                    animalProp = jo;
                }
            }
            return consumeAnimalProp(animalProp);
        } catch (Throwable t) {
            Log.i(TAG, "queryAnimalPropList err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    // 派遣伙伴，返回是否派遣成功
    private boolean consumeAnimalProp(JSONObject animalProp) {
        if (animalProp == null) {
            return false;
        }
        try {
            String propGroup = animalProp.getJSONObject("main").getString("propGroup");
            String propType = animalProp.getJSONObject("main").getString("propType");
            String name = animalProp.getJSONObject("partner").getString("name");
            JSONObject jo = new JSONObject(AntForestRpcCall.consumeProp(propGroup, propType, false));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.forest("巡护派遣🐆[" + name + "]");
                return true;
            }
        } catch (Throwable t) {
            Log.i(TAG, "consumeAnimalProp err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private void queryAnimalAndPiece() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryAnimalAndPiece(0));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray animalProps = jo.getJSONArray("animalProps");
            for (int i = 0; i < animalProps.length(); i++) {
                boolean canCombineAnimalPiece = true;
                jo = animalProps.getJSONObject(i);
                JSONArray pieces = jo.getJSONArray("pieces");
                int id = jo.getJSONObject("animal").getInt("id");
                for (int j = 0; j < pieces.length(); j++) {
                    jo = pieces.optJSONObject(j);
                    if (jo == null || jo.optInt("holdsNum", 0) <= 0) {
                        canCombineAnimalPiece = false;
                        break;
                    }
                }
                if (canCombineAnimalPiece) {
                    combineAnimalPiece(id);
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "queryAnimalAndPiece err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void combineAnimalPiece(int animalId) {
        try {
            do {
                JSONObject jo = new JSONObject(AntForestRpcCall.queryAnimalAndPiece(animalId));
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                JSONArray animalProps = jo.getJSONArray("animalProps");
                jo = animalProps.getJSONObject(0);
                JSONObject animal = jo.getJSONObject("animal");
                int id = animal.getInt("id");
                String name = animal.getString("name");
                JSONArray pieces = jo.getJSONArray("pieces");
                boolean canCombineAnimalPiece = true;
                JSONArray piecePropIds = new JSONArray();
                for (int j = 0; j < pieces.length(); j++) {
                    jo = pieces.optJSONObject(j);
                    if (jo == null || jo.optInt("holdsNum", 0) <= 0) {
                        canCombineAnimalPiece = false;
                        break;
                    } else {
                        piecePropIds.put(jo.getJSONArray("propIdList").getString(0));
                    }
                }
                if (canCombineAnimalPiece) {
                    jo = new JSONObject(AntForestRpcCall.combineAnimalPiece(id, piecePropIds.toString()));
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.forest("合成动物💡[" + name + "]");
                        animalId = id;
                        TimeUtil.sleep(100);
                        continue;
                    }
                }
                break;
            } while (true);
        } catch (Throwable t) {
            Log.i(TAG, "combineAnimalPiece err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private int forFriendCollectEnergy(String targetUserId, long bubbleId) {
        int helped = 0;
        try {
            String s = AntForestRpcCall.forFriendCollectEnergy(targetUserId, bubbleId);
            JSONObject jo = new JSONObject(s);
            if ("SUCCESS".equals(jo.getString("resultCode"))) {
                JSONArray jaBubbles = jo.getJSONArray("bubbles");
                for (int i = 0; i < jaBubbles.length(); i++) {
                    jo = jaBubbles.getJSONObject(i);
                    helped += jo.getInt("collectedEnergy");
                }
                if (helped > 0) {
                    Log.forest("帮收能量🧺[" + UserIdMap.getMaskName(targetUserId) + "]#" + helped + "g");
                    totalHelpCollected += helped;
                    Statistics.addData(Statistics.DataType.HELPED, helped);
                } else {
                    Log.record("帮[" + UserIdMap.getMaskName(targetUserId) + "]收取失败");
                    Log.i("，UserID：" + targetUserId + "，BubbleId" + bubbleId);
                }
            } else {
                Log.record("[" + UserIdMap.getMaskName(targetUserId) + "]" + jo.getString("resultDesc"));
                Log.i(s);
            }
        } catch (Throwable t) {
            Log.i(TAG, "forFriendCollectEnergy err:");
            Log.printStackTrace(TAG, t);
        }
        return helped;
    }

    public static JSONArray getForestPropVOList() {
        JSONArray forestPropVOList = new JSONArray();
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryPropList(false));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                forestPropVOList = jo.getJSONArray("forestPropVOList");
            }
        } catch (Throwable th) {
            Log.i(TAG, "getForestPropVOList err:");
            Log.printStackTrace(TAG, th);
        }
        return forestPropVOList;
    }

    // 获取道具组全部道具
    public static List<JSONObject> getPropGroup(JSONArray forestPropVOList, String propGroup) {
        List<JSONObject> list = new ArrayList<>();
        try {
            for (int i = 0; i < forestPropVOList.length(); i++) {
                JSONObject forestPropVO = forestPropVOList.getJSONObject(i);
                if (forestPropVO.getString("propGroup").equals(propGroup)) {
                    list.add(forestPropVO);
                }
            }
            Collections.sort(list, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject jsonObject1, JSONObject jsonObject2) {
                    try {
                        int durationTime1 = jsonObject1.getJSONObject("propConfigVO").getInt("durationTime");
                        int durationTime2 = jsonObject2.getJSONObject("propConfigVO").getInt("durationTime");
                        boolean hasExpireTime1 = jsonObject1.has("recentExpireTime");
                        boolean hasExpireTime2 = jsonObject2.has("recentExpireTime");
                        if (hasExpireTime1 && hasExpireTime2) {
                            long endTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(durationTime1);
                            long recentExpireTime = jsonObject2.getLong("recentExpireTime");
                            if (endTime < recentExpireTime) {
                                return -1;
                            } else {
                                return durationTime2 - durationTime1;
                            }
                        } else if (!hasExpireTime1 && !hasExpireTime2) {
                            return durationTime1 - durationTime2;
                        } else {
                            return hasExpireTime1 ? -1 : 1;
                        }
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (Throwable th) {
            Log.i(TAG, "getPropGroup err:");
            Log.printStackTrace(TAG, th);
        }
        return list;
    }

    /*
     * 查找背包道具
     * prop
     * propGroup, propType, holdsNum, propIdList[], propConfigVO[propName]
     */
    private JSONObject getForestPropVO(JSONArray forestPropVOList, String propType) {
        try {
            for (int i = 0; i < forestPropVOList.length(); i++) {
                JSONObject forestPropVO = forestPropVOList.getJSONObject(i);
                if (forestPropVO.getString("propType").equals(propType)) {
                    return forestPropVO;
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "getForestPropVO err:");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }

    /*
     * 使用背包道具
     * prop
     * propGroup, propType, holdsNum, propIdList[], propConfigVO[propName]
     */
    public static Boolean consumeProp(JSONObject prop) {
        try {
            // 使用道具
            String propId = prop.getJSONArray("propIdList").getString(0);
            String propType = prop.getString("propType");
            String propGroup = prop.getString("propGroup");
            String propName = prop.getJSONObject("propConfigVO").getString("propName");
            return consumeProp(propGroup, propId, propType, propName);
        } catch (Throwable th) {
            Log.i(TAG, "consumeProp err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    private static Boolean consumeProp(String propGroup, String propId, String propType, String propName) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.consumeProp(propGroup, propId, propType));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.forest("使用道具🎭[" + propName + "]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                return true;
            }
        } catch (Throwable th) {
            Log.i(TAG, "consumeProp err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    // 获取活力值商店列表
    private JSONArray getVitalityItemList(String labelType) {
        JSONArray itemInfoVOList = null;
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.itemList(labelType));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                itemInfoVOList = jo.optJSONArray("itemInfoVOList");
            }
        } catch (Throwable th) {
            Log.i(TAG, "getVitalityItemList err:");
            Log.printStackTrace(TAG, th);
        }
        return itemInfoVOList;
    }

    // 获取活力值商店所有商品信息
    private void getAllSkuInfo() {
        try {
            JSONArray itemInfoVOList = getVitalityItemList("SC_ASSETS");
            if (itemInfoVOList == null) {
                return;
            }
            for (int i = 0; i < itemInfoVOList.length(); i++) {
                JSONObject itemInfoVO = itemInfoVOList.getJSONObject(i);
                getSkuInfoByItemInfoVO(itemInfoVO);
            }
        } catch (Throwable th) {
            Log.i(TAG, "getAllSkuInfo err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private void getSkuInfoBySpuId(String spuId) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.itemDetail(spuId));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            JSONObject spuItemInfoVo = jo.getJSONObject("spuItemInfoVO");
            getSkuInfoByItemInfoVO(spuItemInfoVo);
        } catch (Throwable th) {
            Log.i(TAG, "getSkuInfoBySpuId err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private void getSkuInfoByItemInfoVO(JSONObject spuItem) {
        try {
            String spuId = spuItem.getString("spuId");
            JSONArray skuModelList = spuItem.getJSONArray("skuModelList");
            for (int i = 0; i < skuModelList.length(); i++) {
                JSONObject skuModel = skuModelList.getJSONObject(i);
                String skuId = skuModel.getString("skuId");
                String skuName = skuModel.getString("skuName");
                if (!skuModel.has("spuId")) {
                    skuModel.put("spuId", spuId);
                }
                skuInfo.put(skuId, skuModel);
                VitalityBenefitIdMap.add(skuId, skuName);
            }
            VitalityBenefitIdMap.save(UserIdMap.getCurrentUid());
        } catch (Throwable th) {
            Log.i(TAG, "getSkuInfoByItemInfoVO err:");
            Log.printStackTrace(TAG, th);
        }
    }

    /*
     * 兑换活力值商品
     * sku
     * spuId, skuId, skuName, exchangedCount, price[amount]
     * exchangedCount == 0......
     */
    private Boolean exchangeBenefit(String skuId) {
        if (skuInfo.isEmpty()) {
            getAllSkuInfo();
        }
        JSONObject sku = skuInfo.get(skuId);
        if (sku == null) {
            Log.record("活力兑换🎐找不到要兑换的权益！");
            return false;
        }
        try {
            String skuName = sku.getString("skuName");
            JSONArray itemStatusList = sku.getJSONArray("itemStatusList");
            for (int i = 0; i < itemStatusList.length(); i++) {
                String itemStatus = itemStatusList.getString(i);
                if (ItemStatus.REACH_LIMIT.name().equals(itemStatus) || ItemStatus.NO_ENOUGH_POINT.name().equals(itemStatus) || ItemStatus.NO_ENOUGH_STOCK.name().equals(itemStatus)) {
                    Log.record("活力兑换🎐[" + skuName + "]停止:" + ItemStatus.valueOf(itemStatus).nickName());
                    if (ItemStatus.REACH_LIMIT.name().equals(itemStatus)) {
                        Status.flagToday("forest::exchangeLimit::" + skuId);
                    }
                    return false;
                }
            }
            String spuId = sku.getString("spuId");
            if (exchangeBenefit(spuId, skuId, skuName)) {
                return true;
            }
            getSkuInfoBySpuId(spuId);
        } catch (Throwable th) {
            Log.i(TAG, "exchangeBenefit err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    public static Boolean exchangeBenefit(String spuId, String skuId, String skuName) {
        try {
            if (exchangeBenefit(spuId, skuId)) {
                Status.vitalityExchangeBenefitToday(skuId);
                int exchangedCount = Status.getVitalityExchangeBenefitCountToday(skuId);
                Log.forest("活力兑换🎐[" + skuName + "]#第" + exchangedCount + "次");
                return true;
            }
        } catch (Throwable th) {
            Log.i(TAG, "exchangeBenefit err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    private static Boolean exchangeBenefit(String spuId, String skuId) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.exchangeBenefit(spuId, skuId));
            if (jo.has("errorMessage")) {
                String errorMessage = jo.optString("errorMessage");
                //如果出错今天停止兑换
                if (errorMessage.equals("系统繁忙，请稍后再试。")) {
                    Status.flagToday("forest::exchangeLimit::" + skuId);
                }
            }
            return MessageUtil.checkResultCode(TAG, jo);
        } catch (Throwable th) {
            Log.i(TAG, "exchangeBenefit err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    private void teamCooperateWater() {
        try {

            int userDailyTarget = Math.min(Math.max(partnerteamWaterNum.getValue(), 10), 5000);
            int userWaterMax = partnerteamWaterMax.getValue();
            int todayUsed = 0;
            int usertotalEnergy = 0;
            //int todayUsed = Status.getforestHuntHelpToday("FLAG_TEAM_WATER_DAILY_COUNT");


            // 获取组队合种基础信息
            String homeStr = AntForestRpcCall.queryHomePage();
            JSONObject homeJo = new JSONObject(homeStr);
            if (!MessageUtil.checkResultCode(TAG, homeJo)) {
                Log.record("queryHomePage 返回异常");
                return;
            }

            String teamId = "";
            Integer treeCountForFlag = null;
            Integer targetPlantNums = null;
            String rankRewardStatus = "";
            JSONObject teamHomeResult = homeJo.optJSONObject("teamHomeResult");
            JSONObject teamBaseInfo = teamHomeResult != null ? teamHomeResult.optJSONObject("teamBaseInfo") : null;
            if (teamBaseInfo != null) {
                teamId = teamBaseInfo.optString("teamId", "");
                if (teamBaseInfo.has("treeCountForFlag")) {
                    treeCountForFlag = teamBaseInfo.optInt("treeCountForFlag");
                }
            }
            if (teamId.isEmpty()) {
                Log.record("未获取到组队合种 TeamID");
                Log.info("组队浇水👥[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]未获取到组队合种TeamID, 我的队伍种树" + n(treeCountForFlag) + "颗/前6万名种树" + n(targetPlantNums) + "颗(" + rankRewardStatus + ");");
                return;
            }

            JSONObject userBaseInfo = homeJo.optJSONObject("userBaseInfo");
            int currentEnergy = userBaseInfo != null ? userBaseInfo.optInt("currentEnergy", 0) : 0;
            if (currentEnergy < 10) {
                Log.record("当前能量不足10g(" + currentEnergy + "g)无法浇水");
                Log.info("组队浇水👥[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]当前能量" + currentEnergy + "g不足10g无法浇水, 我的队伍种树" + n(treeCountForFlag) + "颗/前6万名种树" + n(targetPlantNums) + "颗(" + rankRewardStatus + ");");
                return;
            }

            JSONObject joTeamMemberList = new JSONObject(AntForestRpcCall.queryTeamMemberList(teamId));
            if (!MessageUtil.checkResultCode(TAG, joTeamMemberList)) {
                Log.info("组队浇水👥[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]查询队伍成员失败, 我的队伍种树" + n(treeCountForFlag) + "颗/前6万名种树" + n(targetPlantNums) + "颗(" + rankRewardStatus + ");");
                return;
            }
            JSONObject teamDetail = joTeamMemberList.optJSONObject("teamDetail");
            int teamCurrentEnergy = teamDetail != null ? teamDetail.optInt("currentEnergy") : 0;

            // 组队种树进度: H5主页响应路径为teamHomeResult.teamBaseInfo.treeCountForFlag(我的队伍已种)与
            // teamHomeResult.combineHandlerVOMap.teamPlantRank.targetPlantNums(前6万名门槛), 程序自身queryHomePage响应
            // 实测不下发这两个字段, 缺失时回退queryDessertBattleRankInfo(dessertBattleRankInfoVO.plantedNums/targetPlantNums同值)
            JSONObject combineHandlerVOMap = teamHomeResult != null ? teamHomeResult.optJSONObject("combineHandlerVOMap") : null;
            JSONObject teamPlantRank = combineHandlerVOMap != null ? combineHandlerVOMap.optJSONObject("teamPlantRank") : null;
            if (teamPlantRank != null) {
                if (teamPlantRank.has("targetPlantNums")) {
                    targetPlantNums = teamPlantRank.optInt("targetPlantNums");
                }
                rankRewardStatus = teamPlantRank.optString("rankRewardStatus", "");
            }
            if (treeCountForFlag == null || targetPlantNums == null) {
                try {
                    JSONObject battleJo = new JSONObject(AntForestRpcCall.queryDessertBattleRankInfo());
                    if (MessageUtil.checkResultCode(TAG, battleJo)) {
                        JSONObject battleInfo = battleJo.optJSONObject("dessertBattleRankInfoVO");
                        if (battleInfo != null) {
                            if (treeCountForFlag == null && battleInfo.has("plantedNums")) {
                                treeCountForFlag = battleInfo.optInt("plantedNums");
                            }
                            if (targetPlantNums == null && battleInfo.has("targetPlantNums")) {
                                targetPlantNums = battleInfo.optInt("targetPlantNums");
                            }
                            if (rankRewardStatus.isEmpty() && battleInfo.has("rankRewardStatus")) {
                                rankRewardStatus = battleInfo.optString("rankRewardStatus", "");
                            }
                        }
                    }
                } catch (Throwable t) {
                    Log.printStackTrace(TAG, t);
                }
            }

            // 解析队伍成员：self=true(或userId为当前用户)标记为本人，单行输出
            JSONArray teamMemberList = joTeamMemberList.optJSONArray("memberList");
            if (teamMemberList == null || teamMemberList.length() == 0) {
                Log.info("组队浇水👥[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]成员列表为空, 我的队伍种树" + n(treeCountForFlag) + "颗/前6万名种树" + n(targetPlantNums) + "颗(" + rankRewardStatus + ")小队能量" + teamCurrentEnergy + "g;");
                return;
            }
            String currentUid = UserIdMap.getCurrentUid();
            StringBuilder memberInfo = new StringBuilder("组队浇水👥[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]我的队伍种树")
                    .append(n(treeCountForFlag)).append("颗/前6万名种树").append(n(targetPlantNums)).append("颗");
            // 距前6万名排名差值由 targetPlantNums - treeCountForFlag 推算(响应无直接字段)，达标或字段缺失时不输出
            if (targetPlantNums != null && treeCountForFlag != null && targetPlantNums - treeCountForFlag > 0) {
                memberInfo.append(",距前6万名还差").append(targetPlantNums - treeCountForFlag).append("颗");
            }
            if (!rankRewardStatus.isEmpty()) {
                memberInfo.append("(").append(rankRewardStatus).append(")");
            }
            memberInfo.append(",小队能量").append(teamCurrentEnergy).append("g;");
            for (int i = 0; i < teamMemberList.length(); i++) {
                JSONObject member = teamMemberList.optJSONObject(i);
                if (member == null) {
                    continue;
                }
                String userId = member.optString("userId");
                String displayName = member.optString("displayName");
                if (displayName.isEmpty()) {
                    displayName = UserIdMap.getShowName(userId);
                }
                int totalEnergy = member.optInt("totalEnergy");
                int todayEnergy = member.optInt("todayEnergy");
                int yesterdayEnergy = member.optInt("yesterdayEnergy");
                boolean isSelf = member.optBoolean("self") || userId.equals(currentUid);
                memberInfo.append("[").append(displayName).append("]");
                if (isSelf) {
                    memberInfo.append("(当前账户)");
                    todayUsed = member.optInt("todayEnergy");
                    usertotalEnergy = member.optInt("totalEnergy");
                }
                memberInfo.append("累计浇水").append(totalEnergy).append("g昨").append(yesterdayEnergy).append("g今").append(todayEnergy).append("g;");
            }
            int userRemainingQuota = userDailyTarget - todayUsed;

            // 今日已达标时将达标说明合并进组队浇水👥信息条目，不再单独输出record
            if (userRemainingQuota < 10) {
                memberInfo.append("今日已达标(已浇").append(todayUsed).append("g目标").append(userDailyTarget)
                        .append("g组队合种浇水上限").append(userWaterMax).append("g);");
                Log.info(memberInfo.toString());
                return;
            }
            Log.info(memberInfo.toString());

            // 切换团队模式
            boolean needReturn = false;
            if (!isTeam(homeJo)) {
                Log.record("不在队伍模式,已为您切换至组队浇水");
                updateUserConfiginTeam(!needReturn);
                needReturn = true;
            }

            // 获取服务端限制
            String miscStr = AntForestRpcCall.queryMiscInfo("teamCanWaterCount", teamId);
            JSONObject miscJo = new JSONObject(miscStr);
            if (!MessageUtil.checkResultCode(TAG, miscJo)) {
                Log.record("queryMiscInfo 查询失败");
                if (needReturn) {
                    updateUserConfiginTeam(!needReturn);
                }
                return;
            }

            int serverRemaining = miscJo.optJSONObject("combineHandlerVOMap").optJSONObject("teamCanWaterCount").optInt("waterCount", 0);
            Log.record("组队状态检查:目标剩余" + userRemainingQuota + "g;官方剩余" + serverRemaining + "g;背包能量" + currentEnergy + "g;组队合种浇水上限" + userWaterMax + "g");

            if (serverRemaining < 10) {
                Log.record("官方限制今日无可浇水额度，跳过");
                if (needReturn) {
                    updateUserConfiginTeam(!needReturn);
                }
                return;
            }

            // 计算最终浇水量
            int finalWaterAmount = 0;
            if (userWaterMax > 0) {
                finalWaterAmount = Math.min(Math.min(userRemainingQuota, userWaterMax - usertotalEnergy), Math.min(serverRemaining, currentEnergy));
            } else {
                finalWaterAmount = Math.min(userRemainingQuota, Math.min(serverRemaining, currentEnergy));
            }
            if (finalWaterAmount < 10) {
                Log.record("计算后浇水量(" + finalWaterAmount + "g)低于最小限制10g，不执行");
                if (needReturn) {
                    updateUserConfiginTeam(!needReturn);
                }
                return;
            }

            // 执行浇水
            String waterStr = AntForestRpcCall.teamWater(teamId, finalWaterAmount);
            JSONObject waterJo = new JSONObject(waterStr);
            if (MessageUtil.checkResultCode(TAG, waterJo)) {
                Log.forest("组队合种🚿给合种浇水" + finalWaterAmount + "g#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                Toast.show("组队合种🚿给合种浇水" + finalWaterAmount + "g");
                //Status.forestHuntHelpToday("FLAG_TEAM_WATER_DAILY_COUNT", todayUsed + finalWaterAmount, UserIdMap.getCurrentUid());
                Log.record("组队合种今日浇水累计: " + (todayUsed + finalWaterAmount) + "g / " + userDailyTarget + "g");
            }

            // 切换回个人模式
            if (needReturn) {
                updateUserConfiginTeam(!needReturn);
                Log.record("已返回个人模式");
            }
        } catch (Throwable t) {
            Log.printStackTrace("teamCooperateWater 异常:", t);
        }
    }

    //needReturn:false切回个人模式，true切到组队模式
    private static boolean updateUserConfiginTeam(Boolean needReturn) {
        try {
            String updateStr = AntForestRpcCall.updateUserConfiginTeam(needReturn);
            JSONObject updateJo = new JSONObject(updateStr);
            if (!MessageUtil.checkResultCode(TAG, updateJo)) {
                Log.record("updateUserConfig 返回异常");
                return false;
            } else {
                Log.record("合种浇水切换成功：" + (needReturn ? "切到组队模式" : "切到个人模式"));
                return true;
            }
        } catch (Throwable th) {
            Log.i(TAG, "updateUserConfig err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    private static boolean isTeam(JSONObject homeObj) {
        return "Team".equals(homeObj.optString("nextAction", ""));
    }

    private void loveteam(int loveteamWater) {
        if (loveteamWaterNum.getValue() >= 20) {
            try {
                JSONObject jo = new JSONObject(AntForestRpcCall.loveteamHome());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }

                // 解析真爱合种今日浇水：teamInfo.waterInfo.todayWaterMap={userId:浇水数}，名称从memberInfoList按userId查
                JSONObject teamInfo = jo.optJSONObject("teamInfo");
                int currentEnergy = 0;
                if (teamInfo != null) {
                    JSONObject energyInfo = teamInfo.optJSONObject("energyInfo");
                    if (energyInfo != null) {
                        currentEnergy = energyInfo.optInt("currentEnergy");
                    }
                }
                String loveTeamId = "";
                int usertotalEnergy = 0;
                JSONObject userInfo = jo.optJSONObject("userInfo");
                if (userInfo != null) {
                    loveTeamId = userInfo.optString("teamId");
                }
                if (teamInfo != null) {
                    JSONObject todayWaterMap = null;
                    JSONObject waterInfo = teamInfo.optJSONObject("waterInfo");
                    if (waterInfo != null) {
                        todayWaterMap = waterInfo.optJSONObject("todayWaterMap");
                    }
                    if (todayWaterMap != null && todayWaterMap.length() > 0) {
                        String currentUid = UserIdMap.getCurrentUid();
                        // userId -> displayName
                        Map<String, String> nameMap = new HashMap<>();
                        JSONArray memberInfoList = teamInfo.optJSONArray("memberInfoList");
                        if (memberInfoList != null) {
                            for (int i = 0; i < memberInfoList.length(); i++) {
                                JSONObject member = memberInfoList.optJSONObject(i);
                                if (member == null) {
                                    continue;
                                }
                                String uid = member.optString("userId");
                                String displayName = member.optString("displayName");
                                if (displayName.isEmpty()) {
                                    displayName = UserIdMap.getShowName(uid);
                                }
                                nameMap.put(uid, displayName);
                            }
                        }

                        StringBuilder waterLog = new StringBuilder("真爱合种💧[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "][" + loveTeamId + "]累计攒能量" + currentEnergy + "g;");
                        Iterator<String> it = todayWaterMap.keys();
                        while (it.hasNext()) {
                            String uid = it.next();
                            int waterNum = todayWaterMap.optInt(uid);
                            String name = nameMap.get(uid);
                            if (name == null) {
                                name = UserIdMap.getShowName(uid);
                            }
                            waterLog.append("[").append(name).append("]");
                            if (uid.equals(currentUid)) {
                                waterLog.append("(当前账户)");
                                usertotalEnergy = waterNum;
                            }
                            waterLog.append("当天浇水").append(waterNum).append("g;");
                        }
                        Log.info(waterLog.toString());
                    }
                }

                // 仅真爱合种已激活(teamStatus=ACTIVATED)时才浇水
                String teamStatus = teamInfo == null ? "" : teamInfo.optString("teamStatus");
                if (!"ACTIVATED".equals(teamStatus)) {
                    Log.record("真爱合种💧当前状态[" + teamStatus + "]未激活, 跳过浇水");
                    return;
                }

                // 若配置修改，计算再浇水数
                int waterAgainNum;
                if (loveteamWater <= usertotalEnergy) {
                    waterAgainNum=0;
                }else{
                    //差着一点，多浇一些，凑足保底20
                    waterAgainNum = Math.max((loveteamWater - usertotalEnergy), 20);
                }
                // 计算最终浇水量
                int finalWaterAmount = Math.min(loveteamWater, waterAgainNum);
                if (!loveTeamId.isEmpty() && finalWaterAmount >= 20) {
                    loveteamWater(loveTeamId, finalWaterAmount);
                }
            } catch (Throwable th) {
                Log.i(TAG, "loveteam err:");
                Log.printStackTrace(TAG, th);
            }
        }
    }

    private static void loveteamWater(String loveteamWater, int loveteamWaterNum) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.loveteamWater(loveteamWater, loveteamWaterNum));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.forest("真爱浇水🚿给[" + loveteamWater + "]合种浇水" + loveteamWaterNum + "g#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                Toast.show("真爱浇水🚿给[" + loveteamWater + "]合种浇水" + loveteamWaterNum + "g");
            }
        } catch (Throwable th) {
            Log.i(TAG, "loveteamWater err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private static boolean updateUserConfigEnergyPvp() {
        try {
            String Str = AntForestRpcCall.queryUserTag();
            JSONObject Jo = new JSONObject(Str);
            if (!MessageUtil.checkResultCode(TAG, Jo)) {
                Log.record("queryUserTag 查询1V1状态返回异常");
                return false;
            }
            if (Jo.has("tagMap")) {
                JSONObject tagMap = Jo.getJSONObject("tagMap");
                if (!tagMap.has("energyPvp")) {
                    Log.record("查询1V1无状态返回[无法参加]若出现标识立马为大人开启");
                    return false;
                }
                String energyPvp = tagMap.getString("energyPvp");
                if (energyPvp.equals("Y")) {
                    return true;
                }
            }
            //自动开启挑战
            String updateStr = AntForestRpcCall.updateUserConfigEnergyPvp(true);
            JSONObject updateJo = new JSONObject(updateStr);
            if (!MessageUtil.checkResultCode(TAG, updateJo)) {
                Log.record("updateUserConfigEnergyPvp 返回异常");
            } else {
                //Log.record("1V1能量挑战切换成功：" + (needReturn ? "开启" : "关闭"));
                Log.forest("森林比赛🆚1V1能量挑战成功开启");
                return true;
            }
            return false;
        } catch (Throwable th) {
            Log.i(TAG, "updateUserConfigEnergyPvp err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    private static void queryPvpHomeInfo() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryPvpHomeInfo());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (jo.has("currentEnergyPvpBattleRecord")) {
                JSONObject currentEnergyPvpBattleRecord = jo.getJSONObject("currentEnergyPvpBattleRecord");
                //获取1v1比赛情况
                String attackerDisplayName = currentEnergyPvpBattleRecord.optString("attackerDisplayName");
                int attackerEnergy = currentEnergyPvpBattleRecord.optInt("attackerEnergy");
                int attackerWinCount = currentEnergyPvpBattleRecord.optInt("attackerWinCount");
                String battleType = currentEnergyPvpBattleRecord.optString("battleType");
                String defenderDisplayName = currentEnergyPvpBattleRecord.optString("defenderDisplayName");
                int defenderEnergy = currentEnergyPvpBattleRecord.optInt("defenderEnergy");
                int defenderWinCount = currentEnergyPvpBattleRecord.optInt("defenderWinCount");
                String battleStatus = currentEnergyPvpBattleRecord.optString("battleStatus");
                Log.info("森林比赛🆚[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "](" + battleType + "赛)" + battleStatus + "[" + attackerDisplayName + "]" + attackerWinCount + "胜(" + attackerEnergy + "g)VS[" + defenderDisplayName + "]" + defenderWinCount + "胜(" + defenderEnergy + "g)");
                //领取奖励
                if (currentEnergyPvpBattleRecord.optBoolean("hasReward")) {
                    jo = new JSONObject(AntForestRpcCall.receivePvpRewards());
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        return;
                    }
                    if (jo.has("receivedRewards")) {
                        JSONArray receivedRewards = jo.getJSONArray("receivedRewards");
                        for (int i = 0; i < receivedRewards.length(); i++) {
                            JSONObject reward = receivedRewards.getJSONObject(i);
                            String rewardName = reward.getString("rewardName");
                            String rewardType = reward.getString("rewardType");
                            if ("energy".equals(rewardType)) {
                                int energy = reward.getInt("energy");
                                Log.forest("领取奖励🎖️1V1[" + rewardName + "]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                                Statistics.addData(Statistics.DataType.COLLECTED, energy);
                            } else {
                                Log.forest("领取奖励🎖️1V1[" + rewardName + "]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                            }
                        }
                    }

                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "queryPvpHomeInfo err:");
            Log.printStackTrace(TAG, th);
        }

    }

    private static void receivePvpRewards() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.checkRewardqueryMiscInfo());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (jo.has("combineHandlerVOMap")) {
                JSONObject combineHandlerVOMap = jo.getJSONObject("combineHandlerVOMap");
                if (combineHandlerVOMap.has("energyPvpInfo")) {
                    JSONObject energyPvpInfo = combineHandlerVOMap.getJSONObject("energyPvpInfo");
                    //领取奖励
                    if (energyPvpInfo.optBoolean("hasReward")) {
                        jo = new JSONObject(AntForestRpcCall.receivePvpRewards());
                        if (!MessageUtil.checkResultCode(TAG, jo)) {
                            return;
                        }
                        if (jo.has("receivedRewards")) {
                            JSONArray receivedRewards = jo.getJSONArray("receivedRewards");
                            for (int i = 0; i < receivedRewards.length(); i++) {
                                JSONObject reward = receivedRewards.getJSONObject(i);
                                String rewardName = reward.getString("rewardName");
                                String rewardType = reward.getString("rewardType");
                                if ("energy".equals(rewardType)) {
                                    int energy = reward.getInt("energy");
                                    Log.forest("领取奖励🎖️1V1[" + rewardName + "]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                                    Statistics.addData(Statistics.DataType.COLLECTED, energy);
                                } else {
                                    Log.forest("领取奖励🎖️1V1[" + rewardName + "]#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "receivePvpRewards err:");
            Log.printStackTrace(TAG, th);
        }
    }


    private void dress() {
        String dressDetail = dressDetailList.getValue();
        if (dressDetail.isEmpty()) {
            setDressDetail(getDressDetail().toString());
        } else {
            checkDressDetail(dressDetail);
        }
    }

    private JSONObject getDressDetail() {
        JSONObject dressDetail = new JSONObject();
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryHomePage());
            JSONArray ja = jo.getJSONObject("indexDressVO").getJSONArray("dressDetailList");
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                String position = jo.getString("position");
                String batchType = jo.getString("batchType");
                dressDetail.put(position, batchType);
            }
        } catch (Throwable th) {
            Log.i(TAG, "getDressDetail err:");
            Log.printStackTrace(TAG, th);
        }
        return dressDetail;
    }

    private void setDressDetail(String dressDetail) {
        dressDetailList.setValue(dressDetail);
        if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
            Log.forest("装扮保护🔐皮肤保存,芝麻粒将为你持续保护!");
        }
    }

    private void removeDressDetail(String position) {
        JSONObject jo = getDressDetail();
        jo.remove(position);
        setDressDetail(jo.toString());
    }

    private void checkDressDetail(String dressDetail) {
        String[] positions = {"tree__main", "bg__sky_0", "bg__sky_cloud", "bg__ground_a", "bg__ground_b", "bg__ground_c"};
        try {
            boolean isDressExchanged = false;
            JSONObject jo = new JSONObject(dressDetail);
            for (String position : positions) {
                String batchType = "";
                if (jo.has(position)) {
                    batchType = jo.getString(position);
                }
                if (queryUserDressForBackpack(dressMap.get(position), batchType)) {
                    isDressExchanged = true;
                }
            }
            if (isDressExchanged) {
                Log.forest("装扮保护🔐皮肤修改,芝麻粒已为你自动恢复!");
            }
        } catch (Throwable th) {
            Log.i(TAG, "checkDressDetail err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private Boolean queryUserDressForBackpack(String positionType, String batchType) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.listUserDressForBackpack(positionType));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray userHoldDressVOList = jo.getJSONArray("userHoldDressVOList");
            boolean isTakeOff = false;
            for (int i = 0; i < userHoldDressVOList.length(); i++) {
                jo = userHoldDressVOList.getJSONObject(i);
                if (jo.optInt("remainNum", 1) == 0) {
                    if (batchType.equals(jo.getString("batchType"))) {
                        return false;
                    }
                    String position = jo.getJSONArray("posList").getString(0);
                    isTakeOff = takeOffDress(jo.getString("dressType"), position);
                } else if (batchType.equals(jo.getString("batchType"))) {
                    return wearDress(jo.getString("dressType"));
                }
            }

            if (!batchType.isEmpty()) {
                removeDressDetail(dressMap.get(positionType));
                Log.forest("装扮保护🔐皮肤过期,芝麻粒已为你恢复默认!");
            }
            return isTakeOff;
        } catch (Throwable th) {
            Log.i(TAG, "queryUserDressForBackpack err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    private Boolean wearDress(String dressType) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.wearDress(dressType));
            return MessageUtil.checkResultCode(TAG, jo);
        } catch (Throwable th) {
            Log.i(TAG, "wearDress err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    private Boolean takeOffDress(String dressType, String position) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.takeOffDress(dressType, position));
            return MessageUtil.checkResultCode(TAG, jo);
        } catch (Throwable th) {
            Log.i(TAG, "takeOffDress err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    /**
     * The enum Collect status.
     */
    public enum CollectStatus {
        /**
         * Available collect status.
         */
        AVAILABLE,
        /**
         * Waiting collect status.
         */
        WAITING,
        /**
         * Insufficient collect status.
         */
        INSUFFICIENT,
        /**
         * Robbed collect status.
         */
        ROBBED
    }

    /**
     * The type Bubble timer task.
     */
    private class BubbleTimerTask extends ChildModelTask {

        /**
         * The User id.
         */
        private final String userId;

        /**
         * The Bubble id.
         */
        private final long bubbleId;

        /**
         * The ProduceTime.
         */
        private final long produceTime;
        private final String userName;

        /**
         * Instantiates a new Bubble timer task.
         */
        BubbleTimerTask(String ui, long bi, long pt, String un) {
            super(AntForestV2.getBubbleTimerTid(ui, bi), pt - advanceTimeInt);
            userId = ui;
            bubbleId = bi;
            produceTime = pt;
            userName = un;
        }

        @Override
        public Runnable setRunnable() {
            return () -> {
                //String userName = UserIdMap.getMaskName(userId);
                int averageInteger = offsetTimeMath.getAverageInteger();
                long readyTime = produceTime - advanceTimeInt + averageInteger - delayTimeMath.getAverageInteger() - System.currentTimeMillis() + 70;
                if (readyTime > 0) {
                    try {
                        Thread.sleep(readyTime);
                    } catch (InterruptedException e) {
                        Log.i("终止[" + userName + "]蹲点收取任务, 任务ID[" + getId() + "]");
                        return;
                    }
                }
                Log.record("执行蹲点收取[" + userName + "]" + "时差[" + averageInteger + "]ms" + "提前[" + advanceTimeInt + "]ms");
                collectEnergy(new CollectEnergyEntity(userId, null, AntForestRpcCall.getCollectEnergyRpcEntity(null, userId, bubbleId)), userName);
            };
        }
    }

    public static String getBubbleTimerTid(String ui, long bi) {
        return "BT|" + ui + "|" + bi;
    }

    public enum ItemStatus {
        NO_ENOUGH_POINT, NO_ENOUGH_STOCK, REACH_LIMIT, SECKILL_NOT_BEGIN, SECKILL_HAS_END, HAS_NEVER_EXPIRE_DRESS;

        public static final String[] nickNames = {"活力值不足", "库存量不足", "兑换达上限", "秒杀未开始", "秒杀已结束", "不限时皮肤"};

        public String nickName() {
            return nickNames[ordinal()];
        }
    }

    public enum PropGroup {
        shield, boost, doubleClick, energyRain, vitalitySignDouble, stealthCard, robExpandCard;

        public static final String[] nickNames = {"能量保护罩", "时光加速器", "能量双击卡", "能量雨卡", "活力翻倍卡", "隐身卡", "能量翻倍卡"};

        public String nickName() {
            return nickNames[ordinal()];
        }
    }

    public interface WaterFriendType {

        int WATER_00 = 0;
        int WATER_10 = 1;
        int WATER_18 = 2;
        int WATER_33 = 3;
        int WATER_66 = 4;

        String[] nickNames = {"不浇水", "浇水10克", "浇水18克", "浇水33克", "浇水66克"};
        int[] waterEnergy = {0, 10, 18, 33, 66};
    }

    public interface HelpFriendCollectType {

        int NONE = 0;
        int HELP = 1;
        int NOT_HELP = 2;

        String[] nickNames = {"不复活能量", "复活已选好友", "复活未选好友"};
    }

    public interface ConsumeAnimalPropType {

        int NONE = 0;
        int SEQUENCE = 1;
        int QUANTITY = 2;

        String[] nickNames = {"不派遣动物", "按默认顺序派遣", "按最大数量派遣"};
    }

    public interface UsePropType {

        int CLOSE = 0;
        int ALL = 1;
        int ONLY_LIMIT_TIME = 2;

        String[] nickNames = {"关闭", "所有道具", "限时道具"};
    }

    public interface CollectSelfType {
        int ALL = 0;
        int OVER_THRESHOLD = 1;
        int BELOW_THRESHOLD = 2;

        String[] nickNames = {"所有", "大于阈值", "小于阈值"};
    }

    public interface whackModeNames {
        int CLOSE = 0;
        int WHACK_MODE_COMPATIBLE = 1;
        int WHACK_MODE_AGGRESSIVE = 2;
        String[] nickNames = {"关闭", "兼容模式", "激进模式"};
    }
}
