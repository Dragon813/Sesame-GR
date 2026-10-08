package io.github.lazyimmortal.sesame.model.task.antOrchard;

import java.util.UUID;

import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

/**
 * 支付宝-金豆夺宝（芭芭农场下的新玩法）RPC 封装
 * <p>
 * 接口命名空间 com.alipay.goldenbean.*，source 固定为 babafarm。
 * 通过抓包 debug.2026-08-18 分析得到，接口版本随支付宝更新可能变化。
 */
public class GoldenBeanRpcCall {

    // 抓包中固定的版本号与来源
    private static final String VERSION = "20260803.01";
    private static final String SOURCE = "babafarm";
    private static final String BIZ_TYPE = "MASTER";

    /**
     * 金豆夺宝首页数据
     * 返回 signInfo（签到）/manureExchangeInfo（肥料换豆）/taskList（任务）/jarInfo（罐子进度）等
     */
    public static String index() {
        return ApplicationHook.requestString("com.alipay.goldenbean.index",
                "[{\"bizType\":\"" + BIZ_TYPE + "\",\"darwinSceneList\":[\"indexLayoutTwo\",\"indexPreRequestCacheAB\",\"taskFlowHandGuide\"],\"source\":\"" + SOURCE + "\",\"version\":\"" + VERSION + "\"}]");
    }

    /**
     * 每日签到领金豆
     *
     * @param signKey 当日签到 key，取自 index 响应 signInfo.currentSignKey（形如 2026-08-18）
     */
    public static String sign(String signKey) {
        return ApplicationHook.requestString("com.alipay.goldenbean.sign",
                "[{\"bizType\":\"" + BIZ_TYPE + "\",\"signKey\":\"" + signKey + "\",\"source\":\"" + SOURCE + "\",\"version\":\"" + VERSION + "\"}]");
    }

    /**
     * 数据刷新（按需刷新首页各板块）
     *
     * @param syncTypeList 例如 JAR_INFO,SIGN,TASK_LIST,EXCHANGE_MANURE 等，以逗号分隔的裸值
     */
    public static String sync(String syncTypeList) {
        return ApplicationHook.requestString("com.alipay.goldenbean.sync",
                "[{\"bizType\":\"" + BIZ_TYPE + "\",\"source\":\"" + SOURCE + "\",\"syncTypeList\":[" + syncTypeList + "],\"version\":\"" + VERSION + "\"}]");
    }

    /**
     * 肥料换金豆（1肥料=1金豆，每日上限10w，最低800）
     *
     * @param exchangeBeanAmount 本次要兑换的金豆数量（等于消耗的肥料数量）
     */
    public static String manureExchange(int exchangeBeanAmount) {
        return ApplicationHook.requestString("com.alipay.goldenbean.manureExchange",
                "[{\"bizType\":\"" + BIZ_TYPE + "\",\"exchangeBeanAmount\":" + exchangeBeanAmount + ",\"source\":\"" + SOURCE + "\",\"version\":\"" + VERSION + "\"}]");
    }

    /**
     * 金猫矿工挖矿首页（读取棋盘、剩余下钩次数）
     */
    public static String minerIndex() {
        return ApplicationHook.requestString("com.alipay.goldenbean.miner.index",
                "[{\"bizType\":\"" + BIZ_TYPE + "\",\"source\":\"ch_url-https://render.alipay.com/p/yuyan/180020010001291350/index.html\",\"version\":\"" + VERSION + "\"}]");
    }

    /**
     * 触发类任务（挖矿/逛乐园/弹窗奖励等）
     *
     * @param taskId      任务 id
     * @param triggerType 触发类型，如 MARKETING_POPUP_CLICKED
     */
    public static String trigger(String taskId, String triggerType) {
        return ApplicationHook.requestString("com.alipay.goldenbean.trigger",
                "[{\"bizType\":\"" + BIZ_TYPE + "\",\"source\":\"" + SOURCE + "\",\"taskId\":\"" + taskId + "\",\"triggerType\":\"" + triggerType + "\",\"version\":\"" + VERSION + "\"}]");
    }

    /**
     * 每日求签夺宝（得金豆），taskId 固定 FORTUNE_DRAW
     */
    public static String fortuneDraw() {
        return ApplicationHook.requestString("com.alipay.goldenbean.fortuneDraw",
                "[{\"bizType\":\"" + BIZ_TYPE + "\",\"source\":\"" + SOURCE + "\",\"taskId\":\"FORTUNE_DRAW\",\"version\":\"" + VERSION + "\"}]");
    }

    /**
     * 金猫矿工下钩抓取一个物品
     *
     * @param grabResult 抓取结果类型，通常为 BEAN
     * @param itemId     要抓取的物品 id（取自 miner.index / 上一次 grab 响应的 currentLevel.items）
     */
    public static String minerGrab(String grabResult, String itemId) {
        String grabId = UUID.randomUUID().toString();
        return ApplicationHook.requestString("com.alipay.goldenbean.miner.grab",
                "[{\"bizType\":\"" + BIZ_TYPE + "\",\"grabId\":\"" + grabId + "\",\"grabResult\":\"" + grabResult + "\",\"itemId\":\"" + itemId + "\",\"source\":\"ch_url-https://render.alipay.com/p/yuyan/180020010001291350/index.html\",\"version\":\"" + VERSION + "\"}]");
    }

    /**
     * 完成金豆夺宝任务（scene 固定 GOLDEN_BEAN_MASTER_TASK）
     *
     * @param taskType 任务类型，取自 taskList 的 spmExtend.iepTaskTracer（通常等于 taskId）
     */
    public static String finishTask(String sceneCode,String taskType) {
        String userId = UserIdMap.getCurrentUid();
        String outBizNo = userId + System.currentTimeMillis();
        return ApplicationHook.requestString("com.alipay.antieptask.finishTaskantorchard",
                "[{\"bizType\":\"" + BIZ_TYPE + "\",\"finishBusinessInfo\":{\"bizType\":\"" + BIZ_TYPE + "\"},\"outBizNo\":\"" + outBizNo + "\",\"sceneCode\":\""+sceneCode+"\",\"source\":\"" + SOURCE + "\",\"taskType\":\"" + taskType + "\",\"version\":\"" + VERSION + "\"}]");
    }

    /**
     * 领取金豆夺宝任务奖励
     *
     * @param taskType 任务类型
     */
    public static String receiveTaskAward(String sceneCode,String taskType) {
        return ApplicationHook.requestString("com.alipay.antieptask.receiveTaskAwardantorchard",
                "[{\"bizInfo\":{\"bizType\":\"" + BIZ_TYPE + "\"},\"bizType\":\"" + BIZ_TYPE + "\",\"ignoreLimit\":true,\"sceneCode\":\""+sceneCode+"\",\"source\":\"" + SOURCE + "\",\"taskType\":\"" + taskType + "\",\"version\":\"" + VERSION + "\"}]");
    }

    /**
     * 金豆乐园游戏列表（含 gameCenterDrawRights：usedQuota/quotaLimit/quotaCanUse），用于读取今日已得砸蛋机会
     */
    public static String queryGameCenterList() {
        return ApplicationHook.requestString("com.alipay.charitygamecenter.queryGameList",
                "[{\"bizType\":\"GOLDENBEAN\",\"commonDegradeFilterRequest\":{\"deviceLevel\":\"high\",\"platform\":\"Android\",\"unityDeviceLevel\":\"high\"},\"recentAppRecordList\":[]," + "\"requestType\":\"NORMAL\"," + "\"sceneCode\":\"GOLDENBEAN\"," + "\"source\":\"H5\"," + "\"version\":\"" + VERSION + "\"" + "}]");
    }
    /**
     * 金豆乐园砸蛋领奖（把攒到的砸蛋机会换成金豆）
     *
     * @param batchDrawCount 本次砸蛋次数，通常 1
     */
    public static String drawGameCenterAward(int batchDrawCount) {
        return ApplicationHook.requestString("com.alipay.charitygamecenter.drawGameCenterAward",
                "[{\"batchDrawCount\":" + batchDrawCount + ",\"bizType\":\"GOLDENBEAN\",\"requestType\":\"RPC\",\"sceneCode\":\"GOLDENBEAN\",\"source\":\"" + SOURCE + "\",\"version\":\"" + VERSION + "\"}]");
    }
}