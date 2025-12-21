package io.github.lazyimmortal.sesame.model.task.antOrchard;

import android.util.Base64;

import java.util.List;

import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.util.RandomUtil;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

public class AntOrchardRpcCall {
    
    private static final String VERSION = "20250812.01";
    
    public static String orchardIndex() {
        return ApplicationHook.requestString("com.alipay.antfarm.orchardIndex", "[{\"inHomepage\":\"true\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String mowGrassInfo() {
        return ApplicationHook.requestString("com.alipay.antorchard.mowGrassInfo", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"showRanking\":true,\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String batchHireAnimalRecommend(String orchardUserId) {
        return ApplicationHook.requestString("com.alipay.antorchard.batchHireAnimalRecommend", "[{\"orchardUserId\":\"" + orchardUserId + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"sceneType\":\"weed\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String batchHireAnimal(List<String> recommendGroupList) {
        return ApplicationHook.requestString("com.alipay.antorchard.batchHireAnimal",
                "[{\"recommendGroupList\":[" + String.join(",", recommendGroupList) + "],\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"sceneType\":\"weed\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String extraInfoGet() {
        return ApplicationHook.requestString("com.alipay.antorchard.extraInfoGet", "[{\"from\":\"entry\",\"requestType\":\"NORMAL\",\"sceneCode\":\"FUGUO\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String extraInfoSet() {
        return ApplicationHook.requestString("com.alipay.antorchard.extraInfoSet",
                "[{\"bizCode\":\"fertilizerPacket\",\"bizParam\":{\"action\":\"queryCollectFertilizerPacket\"},\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String querySubplotsActivity(String treeLevel) {
        return ApplicationHook.requestString("com.alipay.antorchard.querySubplotsActivity", "[{\"activityType\":[\"WISH\",\"BATTLE\",\"HELP_FARMER\",\"DEFOLIATION\",\"CAMP_TAKEOVER\"],\"inHomepage\":false,\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\"," + "\"source" +
                                                                                            "\":\"ch_appcenter__chsub_9patch\",\"treeLevel\":\"" + treeLevel + "\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String triggerSubplotsActivity(String activityId, String activityType, String optionKey) {
        return ApplicationHook.requestString("com.alipay.antorchard.triggerSubplotsActivity", "[{\"activityId\":\"" + activityId + "\",\"activityType\":\"" + activityType + "\",\"optionKey\":\"" + optionKey + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\"," + "\"source" +
                                                                                              "\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String receiveOrchardRights(String activityId, String activityType) {
        return ApplicationHook.requestString("com.alipay.antorchard.receiveOrchardRights", "[{\"activityId\":\"" + activityId + "\",\"activityType\":\"" + activityType + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION +
                                                                                           "\"}]");
    }
    
    /* 七日礼包 */
    public static String drawLottery() {
        return ApplicationHook.requestString("com.alipay.antorchard.drawLottery", "[{\"lotteryScene\":\"receiveLotteryPlus\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String orchardSyncIndex() {
        return ApplicationHook.requestString("com.alipay.antorchard.orchardSyncIndex", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"syncIndexTypes\":\"QUERY_MAIN_ACCOUNT_INFO\",\"version\":\"" + VERSION + "\"}]");
    }
    
    // 主要修复：统一方法签名，只保留一个orchardSpreadManure方法
    public static String orchardSpreadManure(Boolean useBatchSpread, String wua) {
        // 修复：正确格式化布尔值
        String useBatchSpreadStr = Boolean.TRUE.equals(useBatchSpread) ? "true" : "false";
        return ApplicationHook.requestString("com.alipay.antfarm.orchardSpreadManure",
                "[{\"plantScene\":\"main\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"useBatchSpread\":" + useBatchSpreadStr + ",\"version\":\"" + VERSION + "\",\"wua\":\"" + (wua != null ? wua : "") + "\"}]");
    }
    
    public static String receiveTaskAward(String sceneCode, String taskType) {
        return ApplicationHook.requestString("com.alipay.antiep.receiveTaskAward", "[{\"ignoreLimit\":false,\"requestType\":\"NORMAL\",\"sceneCode\":\"" + sceneCode + "\",\"source\":\"ch_appcenter__chsub_9patch\",\"taskType\":\"" + taskType + "\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String orchardListTask() {
        return ApplicationHook.requestString("com.alipay.antfarm.orchardListTask", "[{\"plantHiddenMMC\":\"false\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String orchardSign() {
        return ApplicationHook.requestString("com.alipay.antfarm.orchardSign", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"signScene\":\"ANTFARM_ORCHARD_SIGN_V2\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String finishTask(String sceneCode, String taskType) {
        String userId = UserIdMap.getCurrentUid();
        String args = "[{\"outBizNo\":\"" + userId + System.currentTimeMillis() + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"" + sceneCode + "\",\"source\":\"ANTFARM_ORCHARD\",\"taskType\":\"" + taskType + "\",\"userId\":\"" + userId + "\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antiep.finishTask", args);
    }
    
    public static String triggerTbTask(String taskId, String taskPlantType) {
        return ApplicationHook.requestString("com.alipay.antfarm.triggerTbTask", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"taskId\":\"" + taskId + "\",\"taskPlantType\":\"" + taskPlantType + "\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String orchardSelectSeed() {
        return ApplicationHook.requestString("com.alipay.antfarm.orchardSelectSeed", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"seedCode\":\"rp\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }
    
    /* 砸金蛋 */
    public static String queryGameCenter() {
        return ApplicationHook.requestString("com.alipay.antorchard.queryGameCenter", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String noticeGame(String appId) {
        return ApplicationHook.requestString("com.alipay.antorchard.noticeGame", "[{\"appId\":\"" + appId + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String submitUserAction(String gameId) {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.submitUserAction", "[{\"actionCode\":\"enterGame\",\"gameId\":\"" + gameId + "\",\"paladinxVersion\":\"2.0.13\",\"source\":\"gameFramework\"}]");
    }
    
    public static String submitUserPlayDurationAction(String gameAppId, String source) {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.submitUserPlayDurationAction", "[{\"gameAppId\":\"" + gameAppId + "\",\"playTime\":32,\"source\":\"" + source + "\",\"statisticTag\":\"\"}]");
    }
    
    public static String smashedGoldenEgg() {
        return ApplicationHook.requestString("com.alipay.antorchard.smashedGoldenEgg", "[{\"requestType\":\"NORMAL\",\"seneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }
    
    /* 助力好友 */
    public static String achieveBeShareP2P(String friendUserId) {
        String shareId = Base64.encodeToString((friendUserId + "-" + RandomUtil.getRandom(5) + "ANTFARM_ORCHARD_SHARE_P2P").getBytes(), Base64.NO_WRAP);
        String args = "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM_ORCHARD_SHARE_P2P\",\"shareId\":\"" + shareId + "\",\"source\":\"share\"}]";
        return ApplicationHook.requestString("com.alipay.antiep.achieveBeShareP2P", args);
    }
    
    public static String switchPlantScene(String sceneName) {
        return ApplicationHook.requestString("com.alipay.antorchard.switchPlantScene", "[{\"plantScene\":\"" + sceneName + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ANTFARM_ORCHARD\"}]");
    }
    
    public static String choosePrize(String orderId) {
        return ApplicationHook.requestString("com.alipay.antfarmlite.choosePrize", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"sendOrderId\":\"" + orderId + "\",\"source\":\"ANTFARM_ORCHARD\"}]");
    }
    
    public static String yebPlantSceneRevenuePage() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.yebPlantSceneRevenuePage", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ANTFARM_ORCHARD\"}]");
    }
    
    public static String triggerYebMoneyTree() {
        return ApplicationHook.requestString("com.alipay.yebbffweb.needle.yebHome.moneyTree.trigger", "[{\"sceneType\":\"default\",\"type\":\"trigger\"}]");
    }
    
    /**
     * 领取回访奖励
     */
    public static String receiveOrchardVisitAward() {
        return ApplicationHook.requestString("com.alipay.antorchard.receiveOrchardVisitAward",
                "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }
    
    /**
     * 带参数的 orchardSyncIndex（适配第二个文件中的调用）
     * 注意：这里参数被忽略，调用无参版本
     */
    public static String orchardSyncIndex(String param) {
        return orchardSyncIndex();
    }
    
    /*
     * 适配施肥调用（两个参数）- 已删除，使用统一版本
     */
    // public static String orchardSpreadManure(String wua, String source) {
    //     return orchardSpreadManure(false, wua);
    // }
}