package io.github.lazyimmortal.sesame.model.task.antForest;

import static io.github.lazyimmortal.sesame.util.RandomUtil.getRandomString;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.github.lazyimmortal.sesame.entity.AlipayVersion;
import io.github.lazyimmortal.sesame.entity.RpcEntity;
import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.RandomUtil;
import io.github.lazyimmortal.sesame.util.StringUtil;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.Random;

public class AntForestRpcCall {
    
    private static String VERSION = "";
    
    public static void init() {
        AlipayVersion alipayVersion = ApplicationHook.getAlipayVersion();
        if (alipayVersion.compareTo(new AlipayVersion("10.6.10.8000")) > 0) {
            VERSION = "20250818";
        }
        else if (alipayVersion.compareTo(new AlipayVersion("10.5.88.8000")) > 0) {
            VERSION = "20240403";
        }
        else if (alipayVersion.compareTo(new AlipayVersion("10.3.96.8100")) > 0) {
            VERSION = "20230501";
        }
        else {
            VERSION = "20230501";
        }
    }
    
    private static final Random RANDOM = new Random();
    
    private static String getUniqueId() {
        return String.valueOf(System.currentTimeMillis()) + RandomUtil.nextLong();
    }
    
    public static String queryEnergyRanking() {
        return ApplicationHook.requestString("alipay.antmember.forest.h5.queryEnergyRanking", "[{\"periodType" + "\":\"total\",\"rankType\":\"energyRank\",\"source\":\"chInfo_ch_appcenter__chsub_9patch\"," + "\"version\":\"" + VERSION + "\"}]", "{\"pathList\":[\"friendRanking\",\"myself\"," +
                                                                                                                                                                                                                                                     "\"totalDatas\"]}");
    }
    
    public static String fillUserRobFlag(String userIdList) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.fillUserRobFlag", "[{\"userIdList\":" + userIdList + "}]", "{\"pathList\":[\"friendRanking\"]}");
    }
    
    public static String queryHomePage() {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryHomePage", "[{\"configVersionMap" + "\":{\"wateringBubbleConfig\":\"10\"},\"skipWhackMole\":true," + "\"source" + "\":\"chInfo_ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]", 3, 1000);
    }
    
    public static String queryDynamicsIndex() {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryDynamicsIndex", "[{\"autoRefresh" + "\":false,\"source\":\"chInfo_ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }
    
    //{"periodType":"day","rankType":"robRank","source":"chInfo_ch_appcenter__chsub_9patch","version":"20221001"}被偷
    //{"periodType":"day","rankType":"energyRank","source":"chInfo_ch_appcenter__chsub_9patch","version":"20221001"}获取
    public static String queryTopEnergyRanking(String rankType, String periodType) {
        return ApplicationHook.requestString("alipay.antmember.forest.h5.queryTopEnergyRanking", "[{\"periodType\":\"" + periodType + "\",\"rankType\":\"" + rankType + "\",\"source\":\"chInfo_ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }
    
    //PK榜单
    public static String queryTopEnergyChallengeRanking() {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryTopEnergyChallengeRanking", "[{\"source\":\"chInfo_ch_appcenter__chsub_9patch\"}]");
    }
    
    //{"activityParam":{},"canRobFlags":"T,F,F,F,F","configVersionMap":{"wateringBubbleConfig":"0"},"currentEnergy":0,"currentVitalityAmount":0,"fromAct":"rankNew","skipWhackMole":false,"source":"chInfo_ch_appcenter__chsub_9patch","userId":"2088842735970535","version":"20250818"}
    public static String queryFriendHomePage(String userId) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryFriendHomePage",
                "[{\"canRobFlags" + "\":\"F,F,F,F,F\",\"configVersionMap\":{\"redPacketConfig\":0,\"wateringBubbleConfig\":\"10\"}," + "\"source\":\"chInfo_ch_appcenter__chsub_9patch\",\"userId\":\"" + userId + "\"," + "\"version\":\"" + VERSION + "\"}]", 3, 1000);
    }
    
    public static RpcEntity getCollectEnergyRpcEntity(String bizType, String userId, long bubbleId) {
        String args1;
        if (StringUtil.isEmpty(bizType)) {
            args1 = "[{\"bizType\":\"\",\"bubbleIds\":[" + bubbleId + "],\"source" + "\":\"chInfo_ch_appcenter__chsub_9patch\",\"userId\":\"" + userId + "\",\"version\":\"" + VERSION + "\"}]";
        }
        else {
            args1 = "[{\"bizType\":\"" + bizType + "\",\"bubbleIds\":[" + bubbleId + "],\"source" + "\":\"chInfo_ch_appcenter__chsub_9patch\",\"userId\":\"" + userId + "\"}]";
        }
        return new RpcEntity("alipay.antmember.forest.h5.collectEnergy", args1, null);
    }
    
    public static String collectEnergy(String bizType, String userId, Long bubbleId) {
        return ApplicationHook.requestString(getCollectEnergyRpcEntity(bizType, userId, bubbleId));
    }
    
    //一键收取
    public static RpcEntity getCollectBatchEnergyRpcEntity(String userId, List<Long> bubbleIdList) {
        return getCollectBatchEnergyRpcEntity(userId, StringUtil.collectionJoinString(",", bubbleIdList));
    }
    
    public static RpcEntity getCollectBatchEnergyRpcEntity(String userId, String bubbleIds) {
        return new RpcEntity("alipay.antmember.forest.h5.collectEnergy", "[{\"bizType\":\"\",\"bubbleIds\":[" + bubbleIds + "],\"fromAct\":\"BATCH_ROB_ENERGY\"," + "\"source" + "\":\"chInfo_ch_appcenter__chsub_9patch\",\"userId\":\"" + userId + "\",\"version" + "\":\"" + VERSION + "\"}]");
    }
    
    public static String collectBatchEnergy(String userId, List<Long> bubbleId) {
        return ApplicationHook.requestString(getCollectBatchEnergyRpcEntity(userId, bubbleId));
    }
    
    public static String collectRebornEnergy() {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.collectRebornEnergy", "[{\"source" + "\":\"chInfo_ch_appcenter__chsub_9patch\"}]");
    }
    
    public static String transferEnergy(String targetUser, String bizNo, int energyId) {
        return ApplicationHook.requestString("alipay.antmember.forest.h5.transferEnergy", "[{\"bizNo\":\"" + bizNo + UUID.randomUUID().toString() + "\",\"energyId\":" + energyId + ",\"extInfo" + "\":{\"sendChat\":\"N\"},\"from\":\"friendIndex\"," + "\"source" +
                                                                                          "\":\"chInfo_ch_appcenter__chsub_9patch\",\"targetUser\":\"" + targetUser + "\"," + "\"transferType\":\"WATERING\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String forFriendCollectEnergy(String targetUserId, long bubbleId) {
        String args1 = "[{\"bubbleIds\":[" + bubbleId + "],\"targetUserId\":\"" + targetUserId + "\"}]";
        return ApplicationHook.requestString("alipay.antmember.forest.h5.forFriendCollectEnergy", args1);
    }
    
    public static String vitalitySign() {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.vitalitySign", "[{\"source" + "\":\"chInfo_ch_appcenter__chsub_9patch\"}]");
    }
    
    public static String antiepSign(String entityId, String userId, String sceneCode) {
        String args = "[{\"entityId\":\"" + entityId + "\",\"requestType\":\"rpc\",\"sceneCode\":\"" + sceneCode + "\",\"source\":\"ANTFOREST\",\"userId\":\"" + userId + "\"}]";
        return ApplicationHook.requestString("com.alipay.antiep.sign", args);
    }
    
    public static String queryTaskList() {
        return queryTaskList(new JSONObject());
    }
    
    public static String queryTaskList(JSONObject extend) {
        String args = "[{\"extend\":" + extend + ",\"fromAct\":\"home_task_list\"," + "\"source" + "\":\"chInfo_ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryTaskList", args);
    }
    
    public static String queryEnergyRainHome() {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryEnergyRainHome", "[{\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String queryEnergyRainCanGrantList() {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryEnergyRainCanGrantList", "[{}]");
    }
    
    public static String grantEnergyRainChance(String targetUserId) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.grantEnergyRainChance", "[{\"targetUserId" + "\":" + targetUserId + "}]");
    }
    
    public static String startEnergyRain() {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.startEnergyRain", "[{\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String energyRainSettlement(int saveEnergy, String token) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.energyRainSettlement", "[{\"activityPropNums" + "\":0,\"saveEnergy\":" + saveEnergy + ",\"token\":\"" + token + "\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String receiveTaskAward(String sceneCode, String taskType) {
        return ApplicationHook.requestString("com.alipay.antiep.receiveTaskAward", "[{\"ignoreLimit\":false," + "\"requestType\":\"H5\",\"sceneCode\":\"" + sceneCode + "\",\"source\":\"ANTFOREST\",\"taskType\":\"" + taskType + "\"}]");
    }
    
    public static String finishTask(String sceneCode, String taskType) {
        String outBizNo = taskType + "_" + RandomUtil.nextDouble();
        return ApplicationHook.requestString("com.alipay.antiep.finishTask", "[{\"outBizNo\":\"" + outBizNo + "\"," + "\"requestType\":\"H5\",\"sceneCode\":\"" + sceneCode + "\"," + "\"source\":\"ANTFOREST\",\"taskType\":\"" + taskType + "\"}]");
    }
    
    public static String popupTask() {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.popupTask", "[{\"fromAct\":\"pop_task\"," + "\"needInitSign\":false,\"source\":\"chInfo_ch_appcenter__chsub_9patch\",\"statusList\":[\"TODO\"," + "\"FINISHED\"],\"version\":\"" + VERSION + "\"}]");
    }
    
    /*public static String antiepSign(String entityId, String userId) {
        return ApplicationHook.requestString("com.alipay.antiep.sign",
                "[{\"entityId\":\"" + entityId
                        + "\",\"requestType\":\"rpc\",\"sceneCode\":\"ANTFOREST_ENERGY_SIGN\",
                        \"source\":\"ANTFOREST\",\"userId\":\""
                        + userId + "\"}]");
    }
*/
    public static String queryPropList(boolean onlyGive) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryPropList", "[{\"onlyGive\":\"" + (onlyGive ? "Y" : "") + "\",\"source\":\"chInfo_ch_appcenter__chsub_9patch\"," + "\"version\":\"" + VERSION + "\"}]");
    }
    
    // 查询可派遣伙伴
    public static String queryAnimalPropList() {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryAnimalPropList", "[{\"source" + "\":\"chInfo_ch_appcenter__chsub_9patch\"}]");
    }
    
    // 派遣动物伙伴
    public static String consumeProp(String propGroup, String propType, Boolean replace) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.consumeProp", "[{\"propGroup\":\"" + propGroup + "\",\"propType\":\"" + propType + "\",\"replace\":\"" + (replace ? "true" : "false") + "\",\"sToken\":\"" + System.currentTimeMillis() + "_" + RandomUtil.getRandomString(8) +
                                                                                       "\",\"source\":\"chInfo_ch_appcenter__chsub_9patch\"}]");
    }
    
    public static String giveProp(String giveConfigId, String propId, String targetUserId) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.giveProp", "[{\"giveConfigId\":\"" + giveConfigId + "\",\"propId\":\"" + propId + "\",\"source\":\"self_corner" + "\",\"targetUserId\":\"" + targetUserId + "\"}]");
    }
    
    public static String collectProp(String giveConfigId, String giveId) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.collectProp", "[{\"giveConfigId\":\"" + giveConfigId + "\",\"giveId\":\"" + giveId + "\",\"source" + "\":\"chInfo_ch_appcenter__chsub_9patch\"}]");
    }
    
    //{"propGroup":"doubleClick","propId":"0fa28fd0eqb61b1615bib0gy1i8b2130","propType":"LIMIT_TIME_ENERGY_DOUBLE_CLICK","sToken":"1765587286732_b5409fdd","secondConfirm":false,"source":"chInfo_ch_appcenter__chsub_9patch","timezoneId":"Asia/Shanghai","version":"20250813"}]}
    public static String consumeProp(String propGroup, String propId, String propType, Boolean secondConfirm) {
        String sToken = System.currentTimeMillis() + "_" + getRandomString(8);
        return ApplicationHook.requestString("alipay.antforest.forest.h5.consumeProp", "[{\"propGroup\":\"" + propGroup + "\",\"propId\":\"" + propId + "\",\"propType\":\"" + propType + "\",\"sToken\":\"" + sToken + "\",\"secondConfirm\":" + secondConfirm + ",\"source" +
                                                                                       "\":\"chInfo_ch_appcenter__chsub_9patch\",\"timezoneId\":\"Asia/Shanghai\",\"version\":\"" + VERSION + "\"}]");
        
        //return ApplicationHook.requestString("alipay.antforest.forest.h5.consumeProp", "[{\"propId\":\"" + propId + "\",\"propType\":\"" + propType + "\",\"source\":\"chInfo_ch_appcenter__chsub_9patch\"," + "\"timezoneId\":\"Asia/Shanghai\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String consumeProp(String propGroup, String propId, String propType) {
        String sToken = System.currentTimeMillis() + "_" + getRandomString(8);
        return ApplicationHook.requestString("alipay.antforest.forest.h5.consumeProp", "[{\"propGroup\":\"" + propGroup + "\",\"propId\":\"" + propId + "\",\"propType\":\"" + propType + "\",\"sToken\":\"" + sToken + "\",\"source\":\"chInfo_ch_appcenter__chsub_9patch\"," + "\"timezoneId\":\"Asia" +
                                                                                       "/Shanghai\",\"version\":\"" + VERSION + "\"}]");
        
        //return ApplicationHook.requestString("alipay.antforest.forest.h5.consumeProp", "[{\"propId\":\"" + propId + "\",\"propType\":\"" + propType + "\",\"source\":\"chInfo_ch_appcenter__chsub_9patch\"," + "\"timezoneId\":\"Asia/Shanghai\",\"version\":\"" + VERSION + "\"}]");
    }
    
    //{"queryBizType":"usingProp","source":"SELF_HOME","version":"20240201"}]}
    public static String queryMiscInfo() {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryMiscInfo", "[{\"queryBizType\":\"usingProp\",\"source\":\"SELF_HOME\",\"version\":\"20240201\"}]");
    }
    
    public static String itemList(String labelType) {
        return ApplicationHook.requestString("com.alipay.antiep.itemList", "[{\"extendInfo\":\"{}\",\"labelType\":\"" + labelType + "\",\"pageSize\":20,\"requestType\":\"rpc\"," + "\"sceneCode\":\"ANTFOREST_VITALITY\",\"source\":\"afEntry\",\"startIndex\":0}]");
    }
    
    public static String itemDetail(String spuId) {
        return ApplicationHook.requestString("com.alipay.antiep.itemDetail", "[{\"requestType\":\"rpc\"," + "\"sceneCode\":\"ANTFOREST_VITALITY\",\"source\":\"afEntry\",\"spuId\":\"" + spuId + "\"}]");
    }
    
    public static String queryVitalityStoreIndex() {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryVitalityStoreIndex", "[{\"source" + "\":\"afEntry\"}]");
    }
    
    public static String exchangeBenefit(String spuId, String skuId) {
        return ApplicationHook.requestString("com.alipay.antcommonweal.exchange.h5.exchangeBenefit",
                "[{\"sceneCode" + "\":\"ANTFOREST_VITALITY\",\"requestId\":\"" + System.currentTimeMillis() + "_" + RandomUtil.getRandom(17) + "\",\"spuId\":\"" + spuId + "\",\"skuId\":\"" + skuId + "\",\"source" + "\":\"GOOD_DETAIL\"}]");
    }
    
    public static String testH5Rpc(String operationTpye, String requestDate) {
        return ApplicationHook.requestString(operationTpye, requestDate);
    }
    
    /* 巡护保护地 */
    public static String queryUserPatrol() {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryUserPatrol", "[{\"source\":\"ant_forest" + "\",\"timezoneId\":\"Asia/Shanghai\"}]");
    }
    
    public static String queryMyPatrolRecord() {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryMyPatrolRecord", "[{\"source" + "\":\"ant_forest\",\"timezoneId\":\"Asia/Shanghai\"}]");
    }
    
    public static String switchUserPatrol(String targetPatrolId) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.switchUserPatrol", "[{\"source" + "\":\"ant_forest\",\"targetPatrolId\":" + targetPatrolId + ",\"timezoneId\":\"Asia/Shanghai\"}]");
    }
    
    public static String patrolGo(int nodeIndex, int patrolId) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.patrolGo", "[{\"nodeIndex\":" + nodeIndex + ",\"patrolId\":" + patrolId + ",\"source\":\"ant_forest\",\"timezoneId\":\"Asia/Shanghai\"}]");
    }
    
    public static String patrolKeepGoing(int nodeIndex, int patrolId, String eventType) {
        String args = null;
        switch (eventType) {
            case "video":
                args = "[{\"nodeIndex\":" + nodeIndex + ",\"patrolId\":" + patrolId + ",\"reactParam\":{\"viewed" + "\":\"Y\"},\"source\":\"ant_forest\",\"timezoneId\":\"Asia/Shanghai\"}]";
                break;
            case "chase":
                args = "[{\"nodeIndex\":" + nodeIndex + ",\"patrolId\":" + patrolId + ",\"reactParam\":{\"sendChat" + "\":\"Y\"},\"source\":\"ant_forest\",\"timezoneId\":\"Asia/Shanghai\"}]";
                break;
            case "quiz":
                args = "[{\"nodeIndex\":" + nodeIndex + ",\"patrolId\":" + patrolId + ",\"reactParam\":{\"answer" + "\":\"correct\"},\"source\":\"ant_forest\",\"timezoneId\":\"Asia/Shanghai\"}]";
                break;
            default:
                args = "[{\"nodeIndex\":" + nodeIndex + ",\"patrolId\":" + patrolId + ",\"reactParam\":{}," + "\"source\":\"ant_forest\",\"timezoneId\":\"Asia/Shanghai\"}]";
                break;
        }
        return ApplicationHook.requestString("alipay.antforest.forest.h5.patrolKeepGoing", args);
    }
    
    public static String exchangePatrolChance(int costStep) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.exchangePatrolChance", "[{\"costStep\":" + costStep + ",\"source\":\"ant_forest\",\"timezoneId\":\"Asia/Shanghai\"}]");
    }
    
    public static String queryAnimalAndPiece(int animalId) {
        String args = null;
        if (animalId != 0) {
            args = "[{\"animalId\":" + animalId + ",\"source\":\"ant_forest\",\"timezoneId\":\"Asia/Shanghai\"}]";
        }
        else {
            args = "[{\"source\":\"ant_forest\",\"timezoneId\":\"Asia/Shanghai\",\"withDetail\":\"N\"," + "\"withGift" + "\":true}]";
        }
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryAnimalAndPiece", args);
    }
    
    public static String combineAnimalPiece(int animalId, String piecePropIds) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.combineAnimalPiece", "[{\"animalId\":" + animalId + ",\"piecePropIds\":" + piecePropIds + ",\"timezoneId\":\"Asia/Shanghai" + "\",\"source\":\"ant_forest\"}]");
    }
    
    public static String AnimalConsumeProp(String propGroup, String propId, String propType) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.consumeProp", "[{\"propGroup\":\"" + propGroup + "\",\"propId\":\"" + propId + "\",\"propType\":\"" + propType + "\",\"source\":\"ant_forest\",\"timezoneId\":\"Asia/Shanghai\"}]");
    }
    
    public static String collectAnimalRobEnergy(String propId, String propType, String shortDay) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.collectAnimalRobEnergy", "[{\"propId\":\"" + propId + "\",\"propType\":\"" + propType + "\",\"shortDay\":\"" + shortDay + "\"," + "\"source\":\"chInfo_ch_appcenter__chsub_9patch\"}]");
    }
    
    /* 复活能量 */
    public static String protectBubble(String targetUserId) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.protectBubble", "[{\"source" + "\":\"ANT_FOREST_H5\",\"targetUserId\":\"" + targetUserId + "\",\"version\":\"" + VERSION + "\"}]");
    }
    
    /* 森林礼盒 */
    public static String collectFriendGiftBox(String targetId, String targetUserId) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.collectFriendGiftBox", "[{\"source" + "\":\"chInfo_ch_appcenter__chsub_9patch\",\"targetId\":\"" + targetId + "\",\"targetUserId\":\"" + targetUserId + "\"}]");
    }
    
    /**
     * 6秒拼手速 打地鼠
     */
    public static String startWhackMole(String source) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.startWhackMole", "[{\"source\":\"" + source + "\"}]");
    }
    
    /**
     * 打单个地鼠
     */
    public static String whackMole(long moleId, String token, String source) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.whackMole", "[{\"moleId\":" + moleId + ",\"source\":\"" + source + "\",\"token\":\"" + token + "\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String settlementWhackMole(String token, List<String> moleIdList, String source) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.settlementWhackMole", "[{\"moleIdList\":[" + String.join(",", moleIdList) + "],\"settlementScene\":\"NORMAL\",\"source\":\"" + source + "\",\"token\":\"" + token + "\",\"version\":\"" + VERSION + "\"}]");
    }
    
    /*青春特权道具任务状态查询🔍*/
    public static String queryTaskListV2(String firstTaskType) {
        String source;
        if ("DNHZ_SL_college".equals(firstTaskType)) {
            source = firstTaskType;
        }
        else if ("DXS_BHZ".equals(firstTaskType) || "DXS_JSQ".equals(firstTaskType)) {
            source = "202212TJBRW";
        }
        else {
            // 处理未匹配的情况，可根据实际需求设置默认值或抛出异常
            source = "";
        }
        String args = "[{\"extend\":{\"firstTaskType\":\"" + firstTaskType + "\"}," + "\"fromAct\":\"home_task_list\"," + "\"source\":\"" + source + "\"," + "\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryTaskList", args);
    }
    
    /**
     * 领取青春特权道具
     */
    public static String receiveTaskAwardV2(String taskType) {
        String args = "[{\"ignoreLimit\":false,\"requestType\":\"H5\",\"sceneCode\":\"ANTFOREST_VITALITY_TASK\",\"taskType\":\"" + taskType + "\",\"source\":\"ANTFOREST\"}]";
        // DAXUESHENG_SJK,NENGLIANGZHAO_20230807,JIASUQI_20230808
        //[{\"ignoreLimit\":false," + "\"requestType\":\"H5\",\"sceneCode\":\"" + sceneCode + "\",\"source\":\"ANTFOREST\",\"taskType\":\"" + taskType + "\"}]");
        return ApplicationHook.requestString("com.alipay.antiep.receiveTaskAward", args);
    }
    
    public static String studentQqueryCheckInModel() {
        String args = "[{\"chInfo\":\"ch_appcollect__chsub_my-recentlyUsed\",\"skipTaskModule\":false}]";
        return ApplicationHook.requestString("alipay.membertangram.biz.rpc.student.queryCheckInModel", args);
    }
    
    /*青春特权领红包*/
    public static String studentCheckin() {
        String args = "[{\"source\":\"chInfo_ch_appcenter__chsub_9patch\"}]";
        return ApplicationHook.requestString("alipay.membertangram.biz.rpc.student.checkIn", args);
    }
    
    public static String closeWhackMole() {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.updateUserConfig", "[{\"configMap" + "\":{\"whackMole\":\"N\"},\"source\":\"chInfo_ch_appcenter__chsub_9patch\"}]");
    }
    
    /* 翻倍额外能量收取 */
    public static String collectRobExpandEnergy(String propId, String propType) {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.collectRobExpandEnergy", "[{\"propId\":\"" + propId + "\",\"propType\":\"" + propType + "\",\"source" + "\":\"chInfo_ch_appcenter__chsub_9patch\"}]");
    }
    
    /* 医疗健康 */
    public static String medical_health_feeds_query() {
        return ApplicationHook.requestString("alipay.iblib.channel.build.query", "[{\"activityCode" + "\":\"medical_health_feeds_query\",\"activityId\":\"2023072600001207\",\"body\":{\"apiVersion\":\"3" + ".1" + ".0\",\"bizId\":\"B213\",\"businessCode\":\"JKhealth\"," + "\"businessId" +
                                                                                 "\":\"O2023071900061804\"," + "\"cityCode\":\"330100\",\"cityName\":\"杭州\",\"exclContentIds\":[],\"filterItems\":[]," + "\"latitude\":\"\"," + "\"longitude\":\"\"," + "\"moduleParam" +
                                                                                 "\":{\"COMMON_FEEDS_BLOCK_2024041200243259\":{}}," + "\"pageCode\":\"YM2024041200137150\",\"pageNo\":1,\"pageSize\":10," + "\"pid\":\"BC_PD_20230713000008526\"," + "\"queryQuizActivityFeed\":1," + "\"scenceCode" +
                                                                                 "\":\"HEALTH_CHANNEL\"," + "\"schemeParams\":{},\"scope\":\"PARTIAL\",\"selectedTabCode\":\"\",\"sourceType\":\"miniApp\"," + "\"specialItemId\":\"\",\"specialItemType\":\"\"," + "\"tenantCode" +
                                                                                 "\":\"2021003141652419\"," + "\"underTakeContentId\":\"\"},\"version\":\"2.0\"}]");
    }
    
    /*
     * public static String medical_health_feeds_query() {
     * return ApplicationHook.requestString("alipay.iblib.channel.build.query",
     * "[{\"activityCode\":\"medical_health_feeds_query\",\"activityId\":\"2023072600001207\",
     * \"body\":{\"apiVersion\":\"3.1.0\",\"bizId\":\"B213\",\"businessCode\":\"JKhealth\",
     * \"businessId\":\"O2023071900061804\",\"cityCode\":\"330100\",\"cityName\":\"杭州\",
     * \"exclContentIds\":[\"20240611OB020010036515121805\",\"20240618OB020010036519694606\",
     * \"20240531OB020010039908594289\",\"20240618OB020010031219943466\",\"20240130OB020010034821821452\",
     * \"20240531OB020010039908610960\",\"20240520OB020010035100844933\",\"20230926OB020010033829802408\",
     * \"20240612OB020010039916083635\",\"20240510OB020010031294655966\",\"20240520OB020010030300850750\",
     * \"20230928OB020010030332233578\",\"20220519OB020010035308350001\",\"20240104OB020010032288343993\",
     * \"20220517OB020010038708340106\",\"20240606OB020010039912316758\",\"20240529OB020010033806968404\",
     * \"20240614OB020010039917386188\",\"20230830OB020010039920939091\",\"20231124OB020010036561478030\"],
     * \"filterItems\":[],\"latitude\":\"\",\"longitude\":\"\",
     * \"moduleParam\":{\"COMMON_FEEDS_BLOCK_2024041200243259\":{}},\"pageCode\":\"YM2024041200137150\",\"pageNo\":1,
     * \"pageSize\":10,\"pid\":\"BC_PD_20230713000008526\",\"queryQuizActivityFeed\":1,
     * \"scenceCode\":\"HEALTH_CHANNEL\",\"schemeParams\":{},\"scope\":\"PARTIAL\",
     * \"selectedTabCode\":\"MD2024042300000013\",\"sourceType\":\"miniApp\",\"specialItemId\":\"\",
     * \"specialItemType\":\"\",\"tenantCode\":\"2021003141652419\",\"underTakeContentId\":\"\"},\"version\":\"2.0\"}]"
     * );
     * }
     */
    
    public static String query_forest_energy() {
        return ApplicationHook.requestString("alipay.iblib.channel.data", "[{\"activityCode\":\"query_forest_energy" + "\",\"activityId\":\"2024052300762675\"," + "\"appId\":\"2021003141652419\"," + "\"body\":{\"scene\":\"FEEDS\"},\"version\":\"2.0\"}]");
    }
    
    public static String produce_forest_energy(String uniqueId) {
        return ApplicationHook.requestString("alipay.iblib.channel.data", "[{\"activityCode\":\"produce_forest_energy" + "\",\"activityId\":\"2024052300762674\",\"appId\":\"2021003141652419\"," + "\"body\":{\"scene\":\"FEEDS\"," + "\"uniqueId\":\"" + uniqueId + "\"},\"version\":\"2.0\"}]");
    }
    
    public static String harvest_forest_energy(int energy, String id) {
        return ApplicationHook.requestString("alipay.iblib.channel.data", "[{\"activityCode\":\"harvest_forest_energy" + "\",\"activityId\":\"2024052300762676\",\"appId\":\"2021003141652419\"," + "\"body\":{\"bubbles" + "\":[{\"energy\":" + energy + ",\"id\":\"" + id + "\"}],\"scene\":\"FEEDS\"},"
                                                                          + "\"version\":\"2" + ".0\"}]");
    }
    
    // 查询森林能量
    public static String queryForestEnergy(String scene) {
        String args = "[{\"activityCode\":\"query_forest_energy\",\"activityId\":\"2024052300762675\"," + "\"body" + "\":{\"scene\":\"" + scene + "\"},\"version\":\"2.0\"}]";
        return ApplicationHook.requestString("alipay.iblib.channel.data", args);
    }
    
    // 生成森林能量
    public static String produceForestEnergy(String scene) {
        long uniqueId = System.currentTimeMillis();
        String args = "[{\"activityCode\":\"produce_forest_energy\",\"activityId\":\"2024052300762674\"," + "\"body" + "\":{\"scene\":\"" + scene + "\",\"uniqueId\":\"" + uniqueId + "\"},\"version\":\"2.0\"}]";
        return ApplicationHook.requestString("alipay.iblib.channel.data", args);
    }
    
    // 领取森林能量
    public static String harvestForestEnergy(String scene, JSONArray bubbles) {
        String args = "[{\"activityCode\":\"harvest_forest_energy\",\"activityId\":\"2024052300762676\"," + "\"body" + "\":{\"bubbles\":" + bubbles + ",\"scene\":\"" + scene + "\"},\"version\":\"2.0\"}]";
        return ApplicationHook.requestString("alipay.iblib.channel.data", args);
    }
    
    // 森林皮肤
    public static String listUserDressForBackpack(String positionType) {
        String args = "[{\"positionType\":\"" + positionType + "\"," + "\"source" + "\":\"chInfo_ch_appcenter__chsub_9patch\"," + "\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("alipay.antforest.forest.h5.listUserDressForBackpack", args);
    }
    
    public static String wearDress(String dressType) {
        String args = "[{\"dressType\":\"" + dressType + "\"," + "\"outBizNo\":\"" + dressType + RandomUtil.nextDouble() + "\"," + "\"source\":\"chInfo_ch_appcenter__chsub_9patch\"}]";
        return ApplicationHook.requestString("alipay.antforest.forest.h5.wearDress", args);
    }
    
    public static String takeOffDress(String dressType, String position) {
        String args = "[{\"dressType\":\"" + dressType + "\"," + "\"outBizNo\":\"" + dressType + RandomUtil.nextDouble() + "\"," + "\"position\":\"" + position + "\"," + "\"source" + "\":\"chInfo_ch_appcenter__chsub_9patch\"}]";
        return ApplicationHook.requestString("alipay.antforest.forest.h5.takeOffDress", args);
    }
    
    //绿色租赁
    public static String checkUserSecondSceneChance() {
        String requestData = "[{\"requestSceneCode\":\"rentBrowse\"}]";
        return ApplicationHook.requestString("com.alipay.creditapollon.biz.rpc.api.rent.green" + ".checkUserSecondSceneChance", requestData);
    }
    
    public static String generateEnergy() {
        String requestData = "[{\"requestSceneCode\":\"rentBrowse\"}]";
        return ApplicationHook.requestString("com.alipay.creditapollon.biz.rpc.api.rent.green.generateEnergy", requestData);
    }
    
    //切换到组队版
    public static String flowHubEntrance() {
        String requestData = "[{\"bizType\":\"ANTFOREST\",\"flowEntranceId\":\"FOREST_PLAY_GROUND\",\"source\":\"ANTFOREST\"}]";
        return ApplicationHook.requestString("com.alipay.antpwgrowth.flowHubEntrance", requestData);
    }
    
    //组队合种浇水
    //{"energyCount":128,"sToken":"1764761409764_57219282","source":"chInfo_ch_appcenter__chsub_9patch","teamId":"0ar6zza141pa1x11ghiy01bkiwtb5500"}
    public static String partnerteamWater(String partnerteamWater, int partnerteamWaterNum) {
        //随机一个8位16进制数
        SecureRandom sr = new SecureRandom();
        String hex8 = String.format("%08x", sr.nextInt());
        String sToken = System.currentTimeMillis() + "_" + hex8;
        String requestData = "[{\"energyCount\":" + partnerteamWaterNum + ",\"sToken\":\"" + sToken + "\",\"source\":\"chInfo_ch_appcenter__chsub_9patch\",\"teamId\":\"" + partnerteamWater + "\"}]";
        return ApplicationHook.requestString("alipay.antforest.forest.h5.teamWater", requestData);
    }
    
    //真爱合种查询
    public static String loveteamHome() {
        String requestData = "[{\"source\":\"chInfo_ch_appcenter__chsub_9patch\"}]";
        return ApplicationHook.requestString("alipay.greenmatrix.rpc.h5.love.loveHome", requestData);
    }
    
    public static String loveteamWater(String loveteamWater, int loveteamWaterNum) {
        String requestData = "[{\"donateNum\":" + loveteamWaterNum + ",\"source\":\"chInfo_ch_appcenter__chsub_9patch" + "\",\"teamId\":\"" + loveteamWater + "\"}]";
        return ApplicationHook.requestString("alipay.greenmatrix.rpc.h5.love.teamWater", requestData);
    }
    
    //森林寻宝助力
    //
    //{"iepShareChannelType":"qrcode","requestType":"RPC","sceneCode":"FOREST_NORMAL_20251024_SHARE",
    // "shareId":"hhJxgLczlae8wY4uIrOdutR4O7FEYDgn0xx0OehP5jt9bxgpIW643h4FnWRjs9uZzng77VUJcjlcZsjGio6MsAtmwxkxkx",
    // "source":"chouchoule"}
    public static String shareComponentRecall(String sceneCode, String shareId) {
        String requestData = "[{\"iepShareChannelType\":\"qrcode\",\"requestType\":\"RPC\",\"sceneCode\":\"" + sceneCode + "\"," + "\"shareId\":\"" + shareId + "\",\"source\":\"chouchoule\"}]";
        return ApplicationHook.requestString("com.alipay.antiep.shareComponentRecall", requestData);
        
    }
    
    //[{"beSharedBizExtInfo":{"drawActivityId":"20251024","inviterUid":"2088942477411601"},"requestType":"RPC",
    // "sceneCode":"FOREST_NORMAL_20251024_SHARE",
    // "shareId":"hhJxgLczlae8wY4uIrOdutR4O7FEYDgn0xx0OehP5jt9bxgpIW643h4FnWRjs9uZzng77VUJcjlcZsjGio6MsAtmwxkxkx",
    // "source":"chouchoule","userId":"2088942477411601"}]}
    public static String confirmShareRecall(String activityId, String p2pSceneCode, String shareId, String userId) {
        String requestData = "[{\"beSharedBizExtInfo\":{\"drawActivityId\":\"" + activityId + "\",\"inviterUid\":\"" + userId + "\"},\"requestType\":\"RPC\",\"sceneCode\":\"" + p2pSceneCode + "\",\"shareId\":\"" + shareId + "\",\"source\":\"chouchoule\",\"userId\":\"" + userId + "\"}]";
        return ApplicationHook.requestString("com.alipay.antiep.confirmShareRecall", requestData);
    }
    
    /**
     * 森林抽抽乐-活动列表
     */
    public static String enterDrawActivityopengreen(String activityId, String sceneCode, String source) {
        //JSONObject params = new JSONObject();
        //params.put("activityId", "2025060301");
        //params.put("requestType", "RPC");
        //params.put("sceneCode", "ANTFOREST_NORMAL_DRAW");
        //params.put("source", source);
        //String args = "[" + params + "]";
        String requestData = "[{\"activityId\":\"" + activityId + "\",\"requestType\":\"RPC\",\"sceneCode\":\"" + sceneCode + "\"," + "\"source\":\"" + source + "\"}]";
        return ApplicationHook.requestString("com.alipay.antiepdrawprod.enterDrawActivityopengreen", requestData);
    }
    
    /**
     * 森林抽抽乐-请求任务列表
     */
    public static String listTaskopengreen(String sceneCode, String source) {
        //        [{"requestType":"RPC","sceneCode":"ANTFOREST_NORMAL_DRAW_TASK","source":"task_entry"}]
        //JSONObject params = new JSONObject();
        //params.put("requestType", "RPC");
        //params.put("sceneCode", sceneCode);
        //params.put("source", source);
        //String args = "[" + params + "]";
        String requestData = "[{\"requestType\":\"RPC\",\"sceneCode\":\"" + sceneCode + "\",\"source\":\"" + source + "\"}]";
        return ApplicationHook.requestString("com.alipay.antieptask.listTaskopengreen", requestData);
    }
    
    /**
     * 森林抽抽乐-签到领取次数-访问即算签到，所以直接领取？？
     */
    public static String receiveTaskAwardopengreen(String source, String sceneCode, String taskType) {
        //JSONObject params = new JSONObject();
        //params.put("ignoreLimit", true);
        //params.put("requestType", "RPC");
        //params.put("sceneCode", sceneCode);
        //params.put("source", source);
        //params.put("taskType", taskType);
        //String args = "[" + params + "]";
        String requestData = "[{\"ignoreLimit\":true,\"requestType\":\"RPC\",\"sceneCode\":\"" + sceneCode + "\"," + "\"source\":\"" + source + "\",\"taskType\":\"" + taskType + "\"}]";
        return ApplicationHook.requestString("com.alipay.antieptask.receiveTaskAwardopengreen", requestData);
    }
    
    /**
     * 森林抽抽乐-任务-活力值兑换抽奖次数
     */
    public static String exchangeTimesFromTaskopengreen(String activityId, String sceneCode, String source, String taskSceneCode, String taskType) {
        //{"activityId":"2025060301","requestType":"RPC","sceneCode":"ANTFOREST_NORMAL_DRAW","source":"task_entry",
        // "taskSceneCode":"ANTFOREST_NORMAL_DRAW_TASK","taskType":"NORMAL_DRAW_EXCHANGE_VITALITY"}
        //        [{"activityId":"2025060301","requestType":"RPC","sceneCode":"ANTFOREST_NORMAL_DRAW",
        //        "source":"task_entry","taskSceneCode":"ANTFOREST_NORMAL_DRAW_TASK",
        //        "taskType":"NORMAL_DRAW_EXCHANGE_VITALITY"}]
       /* JSONObject params = new JSONObject();
        params.put("activityId", activityId);
        params.put("requestType", "RPC");
        params.put("sceneCode", sceneCode);
        params.put("source", source);
        params.put("taskSceneCode", taskSceneCode);
        params.put("taskType", taskType);
        String args = "[" + params + "]";*/
        //String requestData = "[{\"activityId\":\""+activityId+"\",\"requestType\":\"RPC\",
        // \"sceneCode\":\""+sceneCode+"\",\"source\":\""+source+"\",\"taskSceneCode\":\""+taskSceneCode+"\",
        // \"taskType\":\""+taskType+"\"}]";
        String requestData = "[{\"activityId\":\"" + activityId + "\",\"requestType\":\"RPC\",\"sceneCode\":\"" + sceneCode + "\"," + "\"source\":\"" + source + "\",\"taskSceneCode\":\"" + taskSceneCode + "\",\"taskType\":\"" + taskType + "\"}]";
        return ApplicationHook.requestString("com.alipay.antiepdrawprod.exchangeTimesFromTaskopengreen", requestData);
    }
    
    /**
     * 森林抽抽乐-任务-广告
     */
    public static String finishTask4Chouchoule(String taskType, String sceneCode) {
        //[{"outBizNo":"FOREST_NORMAL_DRAW_XLIGHT_1_1749288736354_ffba6daf","requestType":"RPC",
        // "sceneCode":"ANTFOREST_NORMAL_DRAW_TASK","source":"ADBASICLIB","taskType":"FOREST_NORMAL_DRAW_XLIGHT_1"}]
        //"_" + System.currentTimeMillis() + "_" + RandomUtil.getRandomString(8)
        String taskTypeRandom = taskType + "_" + System.currentTimeMillis() + "_" + RandomUtil.getRandomString(8);
        /*JSONObject params = new JSONObject();
        params.put("outBizNo", taskType + RandomUtil.getRandomTag());
        params.put("requestType", "RPC");
        params.put("sceneCode", sceneCode);
        params.put("source", "ADBASICLIB");
        params.put("taskType", taskType);
        String args = "[" + params + "]";*/
        String requestData = "[{\"outBizNo\":\"" + taskTypeRandom + "\",\"requestType\":\"RPC\",\"sceneCode\":\"" + sceneCode + "\",\"source\":\"ADBASICLIB\",\"taskType\":\"" + taskType + "\"}]";
        return ApplicationHook.requestString("com.alipay.antiep.finishTask", requestData);
    }
    
    /**
     * 森林抽抽乐-抽奖
     */
    // 2025101301
    // "activityId":"20251024","requestType":"RPC","sceneCode":"ANTFOREST_ACTIVITY_DRAW","source":"chouchoule",
    // "userId":"2088842736213752"
    public static String drawopengreen(String activityId, String sceneCode, String source, String userId) {
        /*JSONObject params = new JSONObject();
        params.put("activityId", activityId);
        params.put("requestType", "RPC");
        params.put("sceneCode", sceneCode);
        params.put("source", source);
        params.put("userId", userId);
        String args = "[" + params + "]";*/
        String requestData = "[{\"activityId\":\"" + activityId + "\",\"requestType\":\"RPC\",\"sceneCode\":\"" + sceneCode + "\"," + "\"source\":\"" + source + "\",\"userId\":\"" + userId + "\"}]";
        return ApplicationHook.requestString("com.alipay.antiepdrawprod.drawopengreen", requestData);
    }
    
    /**
     * 完成森林抽抽乐 任务
     *
     * @param taskType  任务类型
     * @param sceneCode 场景Code
     * @return s
     */
    public static String finishTaskopengreen(String taskType, String sceneCode) {
        // [{"outBizNo":"FOREST_NORMAL_DRAW_ANTTODO_1749481064943_2dd9971d","requestType":"RPC",
        // "sceneCode":"ANTFOREST_NORMAL_DRAW_TASK","source":"task_entry","taskType":"FOREST_NORMAL_DRAW_ANTTODO"}]
        String taskTypeRandom = taskType + "_" + System.currentTimeMillis() + "_" + RandomUtil.getRandomString(8);

        /*JSONObject params = new JSONObject();
        params.put("outBizNo", taskType + RandomUtil.getRandomTag());
        params.put("requestType", "RPC");
        params.put("sceneCode", sceneCode);
        params.put("source", "task_entry");
        params.put("taskType", taskType);
        String args = "[" + params + "]";*/
        String requestData = "[{\"outBizNo\":\"" + taskTypeRandom + "\",\"requestType\":\"RPC\",\"sceneCode\":\"" + sceneCode + "\",\"source\":\"task_entry\",\"taskType\":\"" + taskType + "\"}]";
        return ApplicationHook.requestString("com.alipay.antieptask.finishTaskopengreen", requestData);
    }
    
    /**
     * 更新用户配置（是否处于队伍中）
     * <p>
     * 示例请求体：
     * [
     * {
     * "configMap": {
     * "inTeam": "Y"
     * },
     * "source": "chInfo_ch_appcenter__chsub_9patch"
     * }
     * ]
     * <p>
     * 说明：
     * - inTeam = "Y" 表示用户在队伍中
     * - inTeam = "N" 表示用户不在队伍中
     *
     * @param inTeam 是否在队伍中（true = Y，false = N）
     * @return 返回 RPC 响应字符串
     */
    public static String updateUserConfig(boolean inTeam) {
        String inTeamValue = inTeam ? "Y" : "N";
        String args = "[{" + "\"configMap\":{\"inTeam\":\"" + inTeamValue + "\"}," + "\"source\":\"chInfo_ch_appcenter__chsub_9patch\"" + "}]";
        
        return ApplicationHook.requestString("alipay.antforest.forest.h5.updateUserConfig", args);
    }
    
    /**
     * 组队版浇水
     * 修复了 sToken 生成逻辑，必须是 时间戳_8位
     *
     * @param teamId      队伍ID
     * @param energyCount 浇水克数
     * @return 响应字符串
     */
    public static String teamWater(String teamId, int energyCount) {
        // 1. 生成毫秒级时间戳
        long ts = System.currentTimeMillis();
        
        // 2. 生成 8 位随机数字字符
        String rand = RandomUtil.getRandomString(8);
        
        // 3. 拼接 sToken：时间戳_8位数字字符
        String sToken = ts + "_" + rand;
        
        // 4. 构造参数 JSON 字符串
        String args = "[{" + "\"energyCount\":" + energyCount + "," + "\"sToken\":\"" + sToken + "\"," + "\"source\":\"chInfo_ch_appcenter__chsub_9patch\"," + "\"teamId\":\"" + teamId + "\"" + "}]";
        
        // 5. RPC 调用
        return ApplicationHook.requestString("alipay.antforest.forest.h5.teamWater", args);
    }
    
    /**
     * 查询 MiscInfo（teamFlagTreeCount 等）
     * <p>
     * 示例请求体：
     * {
     * "configVersionMap": {},
     * "extInfo": {},
     * "queryBizType": "teamFlagTreeCount",
     * "source": "SELF_HOME",
     * "version": "20240201"
     * }
     *
     * @param queryBizType 查询的业务类型，例如 "teamFlagTreeCount"
     * @param teamId       队伍ID
     * @return 返回 RPC 响应字符串
     */
    public static String queryMiscInfo(String queryBizType, String teamId) {
        // 构造 H5 RPC 参数
        String args = "[{\"queryBizType\":\"" + queryBizType + "\",\"source\":\"SELF_HOME\",\"targetUserId\":\"" + teamId + "\",\"version\":\""+VERSION+"\"}]";
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryMiscInfo", args);
    }
}