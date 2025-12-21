package io.github.lazyimmortal.sesame.model.task.antSports;

import org.json.JSONObject;

import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;

public class AntSportsRpcCall {
    private static final String chInfo = "ch_appcenter__chsub_9patch", timeZone = "Asia/Shanghai", version = "3.0.1" + ".2", alipayAppVersion = "0.0.852", cityCode = "330100", appId = "2021002116659397";
    
    // 运动任务查询
    // {"apiVersion":"energy","canAddHome":false,"chInfo":"medical_health","clientAuthStatus":"not_support",
    // "clientOS":"android","features":["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC",
    // "CLOUD_SDK_AUTH","STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_AI","SUPPORT_TAB3",
    // "SUPPORT_FLYRABBIT","SUPPORT_NEW_MATCH","EXTERNAL_ADVERTISEMENT_TASK","PROP","PROPV2","ASIAN_GAMES"],
    // "topTaskId":""}
    public static String queryCoinTaskPanel() {
        String args = "[{\"apiVersion\":\"energy\",\"canAddHome\":false,\"chInfo\":\"medical_health\"," + "\"clientAuthStatus\":\"not_support\",\"clientOS\":\"android\",\"features\":[\"DAILY_STEPS_RANK_V2\"," + "\"STEP_BATTLE\",\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\",\"CLOUD_SDK_AUTH\"," +
                      "\"STAY_ON_COMPLETE\"," + "\"EXTRA_TREASURE_BOX\",\"NEW_HOME_PAGE_STATIC\",\"SUPPORT_AI\"," + "\"SUPPORT_TAB3\"," + "\"SUPPORT_FLYRABBIT\",\"SUPPORT_NEW_MATCH\",\"EXTERNAL_ADVERTISEMENT_TASK\"," + "\"PROP\",\"PROPV2\"," + "\"ASIAN_GAMES\"],\"topTaskId\":\"\"}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc" + ".queryCoinTaskPanel", args);
    }
    
    public static String signUpTask(String taskId) {
        String args = "[{\"taskId\":\"" + taskId + "\"}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.signUpTask", args);
    }
    
    // {"apiVersion":"energy","bizNo":"1760803446912-b3612450-6d3b-485e-a8d1-167f8372940e","taskAction":"SHOW_AD",
    // "taskId":"AP19300697","taskType":"AD_TASK"}
    public static String completeTask(String taskAction, String taskId) {
        String args = "[{\"taskAction\":\"" + taskAction + "\",\"taskId\":\"" + taskId + "\"}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.completeTask", args);
    }
    
    public static String duublecompleteTask(String bizNo, String taskAction, String taskId) {
        String args = "[{\"apiVersion\":\"energy\",\"bizNo\":\"" + bizNo + "\",\"taskAction\":\"" + taskAction + "\"," + "\"taskId\":\"" + taskId + "\",\"taskType\":\"AD_TASK\"}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.completeTask", args);
    }
    
    public static String signInCoinTask() {
        String args = "[{\"operatorType\":\"signIn\"}]";
        // String args = "[{\"apiVersion\":\"energy\",\"operatorType\":\"query\"}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.signInCoinTask", args);
    }
    
    // {"apiVersion":"energy","bubbleId":"","canAddHome":false,"chInfo":"ch_shouquan_shouye",
    // "clientAuthStatus":"not_support","clientOS":"android","distributionChannel":"",
    // "features":["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH",
    // "STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_AI","SUPPORT_TAB3",
    // "SUPPORT_FLYRABBIT","SUPPORT_NEW_MATCH","EXTERNAL_ADVERTISEMENT_TASK","PROP","PROPV2","ASIAN_GAMES"],
    // "outBizNo":""}
    public static String queryCoinBubbleModule() {
        String args = "[{\"apiVersion\":\"energy\",\"bubbleId\":\"\",\"canAddHome\":false," + "\"chInfo" + "\":\"ch_shouquan_shouye\",\"clientAuthStatus\":\"not_support\",\"clientOS\":\"android\"," + "\"distributionChannel\":\"\",\"features\":[\"DAILY_STEPS_RANK_V2\",\"STEP_BATTLE\"," +
                      "\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\",\"CLOUD_SDK_AUTH\",\"STAY_ON_COMPLETE\"," + "\"EXTRA_TREASURE_BOX\",\"NEW_HOME_PAGE_STATIC\",\"SUPPORT_AI\",\"SUPPORT_TAB3\"," + "\"SUPPORT_FLYRABBIT\",\"SUPPORT_NEW_MATCH\",\"EXTERNAL_ADVERTISEMENT_TASK\",\"PROP\",\"PROPV2\"," +
                      "\"ASIAN_GAMES\"],\"outBizNo\":\"\"}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.sportsHealthHomeRpc" + ".queryEnergyBubbleModule", args);
    }
    
    /*
        public static String receiveCoinAsset(String assetId, int coinAmount) {
            // "tracertPos": "首页金币收集" "任务面板"
            String args = "[{\"assetId\":\"" + assetId + "\",\"coinAmount\":" + coinAmount + "}]";
            return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinCenterRpc
            .receiveCoinAsset", args);
        }
    */
    public static String receiveCoinAsset(String assetId) {
        
        String args = "[{\"apiVersion\":\"energy\",\"chInfo\":\"medical_health\",\"clientOS\":\"android\"," + "\"features\":[\"DAILY_STEPS_RANK_V2\",\"STEP_BATTLE\",\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\"," + "\"CLOUD_SDK_AUTH\",\"STAY_ON_COMPLETE\",\"EXTRA_TREASURE_BOX\"," +
                      "\"NEW_HOME_PAGE_STATIC\"," + "\"SUPPORT_AI\",\"SUPPORT_TAB3\",\"SUPPORT_FLYRABBIT\",\"SUPPORT_NEW_MATCH\"," + "\"EXTERNAL_ADVERTISEMENT_TASK\",\"PROP\",\"PROPV2\",\"ASIAN_GAMES\"]," + "\"medEnergyBallInfoRecordIds\":[\"" + assetId + "\"],\"pickAllEnergyBall\":false," +
                      "\"source\":\"SPORT\"}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy", args);
    }
    
    public static String pickAllEnergyBall() {
        String args = "[{\"apiVersion\":\"energy\",\"chInfo\":\"ch_shouquan_shouye\",\"clientOS\":\"android\"," + "\"features\":[\"DAILY_STEPS_RANK_V2\",\"STEP_BATTLE\",\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\"," + "\"CLOUD_SDK_AUTH\",\"STAY_ON_COMPLETE\",\"EXTRA_TREASURE_BOX\"," +
                      "\"NEW_HOME_PAGE_STATIC\"," + "\"SUPPORT_AI\",\"SUPPORT_TAB3\",\"SUPPORT_FLYRABBIT\",\"SUPPORT_NEW_MATCH\"," + "\"EXTERNAL_ADVERTISEMENT_TASK\",\"PROP\",\"PROPV2\",\"ASIAN_GAMES\"]," + "\"medEnergyBallInfoRecordIds\":[],\"pickAllEnergyBall\":true,\"source\":\"SPORT\"}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy", args);
    }
    
    public static String queryDonateRecord() {
        String args = "[{\"pageIndex\":1,\"pageSize\":10}]";
        return ApplicationHook.requestString("alipay.antsports.walk.charity.queryDonateRecord", args);
    }
    
    public static String queryProjectList(int index) {
        String args = "[{\"index\":" + index + ",\"projectListUseVertical\":true}]";
        return ApplicationHook.requestString("alipay.antsports.walk.charity.queryProjectList", args);
    }
    
    public static String queryProjectDetail(String projectId) {
        String args = "[{\"projectId\": \"" + projectId + "\"}]";
        return ApplicationHook.requestString("alipay.antsports.walk.charity.queryProjectDetail", args);
    }
    
    public static String donate(int donateCharityCoin, String projectId) {
        String args = "[{\"donateCharityCoin\":" + donateCharityCoin + ",\"projectId\":\"" + projectId + "\"}]";
        return ApplicationHook.requestString("alipay.antsports.walk.charity.donate", args);
    }
    
    //public static String queryWalkStep() {
    //    String args = "[{}]";
    //    return ApplicationHook.requestString("alipay.antsports.walk.user.queryWalkStep", args);
    //}
    
    public static String queryWalkStep() {
        String args = "[{\"appId\":\"healthstep\",\"bizId\":\"donation\",\"chInfo\":\"h5_donation_healthstep\",\"timeZone\":\"Asia/Shanghai\"}]";
        return ApplicationHook.requestString("alipay.antsports.steps.query", args);
    }

    public static String walkDonateSignInfo(int count) {
        return ApplicationHook.requestString("alipay.charity.mobile.donate.walk.walkDonateSignInfo", "[{\"needDonateAction\":false,\"source\":\"walkDonateHome\",\"steps\":" + count + ",\"timezoneId" + "\":\"" + timeZone + "\"}]");
    }
    
    public static String donateWalkHome(int steps) {
        String args = "[{\"module\":\"3\",\"steps\":" + steps + ",\"timezoneId\":\"" + timeZone + "\"}]";
        return ApplicationHook.requestString("alipay.charity.mobile.donate.walk.home", args);
    }
    
    public static String donateExchangeRecord() {
        String args = "[{\"page\":1,\"pageSize\":10}]";
        return ApplicationHook.requestString("alipay.charity.mobile.donate.exchange.record", args);
    }
    
    
    //{"actId":"20160524001110000000000000001002","count":100000,"donateToken":"2088002795595507_1766240522284","timezoneId":"Asia/Shanghai","ver":"0"}]}
    public static String donateWalkExchange(String actId, int count, String donateToken) {
        return ApplicationHook.requestString("alipay.charity.mobile.donate.walk.exchange", "[{\"actId\":\"" + actId + "\",\"count\":" + count + ",\"donateToken\":\"" + donateToken + "\"," + "\"timezoneId\":\"" + timeZone + "\",\"ver\":0}]");
    }
    
    /*
     * 新版 走路线
     */
    
    // 查询用户
    public static String queryUser() {
        return ApplicationHook.requestString("com.alipay.sportsplay.biz.rpc.walk.queryUser", "[{}]");
    }
    
    // 查询主题列表
    public static String queryThemeList() {
        return ApplicationHook.requestString("com.alipay.sportsplay.biz.rpc.walk.theme.queryThemeList", "[{}]");
    }
    
    // 查询世界地图
    public static String queryWorldMap(String themeId) {
        String args = "[{\"themeId\":\"" + themeId + "\"}]";
        return ApplicationHook.requestString("com.alipay.sportsplay.biz.rpc.walk.queryWorldMap", args);
    }
    
    // 查询城市路线
    public static String queryCityPath(String cityId) {
        String args = "[{\"cityId\":\"" + cityId + "\"}]";
        return ApplicationHook.requestString("com.alipay.sportsplay.biz.rpc.walk.queryCityPath", args);
    }
    
    // 查询路线
    public static String queryPath(String date, String pathId) {
        String args = "[{\"date\":\"" + date + "\",\"pathId\":\"" + pathId + "\"}]";
        return ApplicationHook.requestString("com.alipay.sportsplay.biz.rpc.walk.queryPath", args);
    }
    
    public static String queryPathName(String pathId) {
        try {
            JSONObject jo = new JSONObject(queryPath(Log.getFormatDate(), pathId));
            if (MessageUtil.checkSuccess("queryPathName", jo)) {
                jo = jo.getJSONObject("data").getJSONObject("path");
                return jo.getString("name");
            }
        }
        catch (Throwable t) {
            Log.record("查询路线:[" + pathId + "]失败！");
        }
        return null;
    }
    
    // 加入路线
    public static String joinPath(String pathId) {
        String args = "[{\"pathId\":\"" + pathId + "\"}]";
        return ApplicationHook.requestString("com.alipay.sportsplay.biz.rpc.walk.joinPath", args);
    }
    
    // 行走路线
    public static String walkGo(String date, String pathId, int useStepCount) {
        String args = "[{\"date\":\"" + date + "\",\"pathId\":\"" + pathId + "\",\"useStepCount\":\"" + useStepCount + "\"}]";
        return ApplicationHook.requestString("com.alipay.sportsplay.biz.rpc.walk.go", args);
    }
    
    public static String queryMailList() {
        String args = "[{\"mailType\":\"SYSTEM\",\"pageSize\":50,\"userMailStartIndex\":1}]";
        return ApplicationHook.requestString("alipay.antsports.walk.mail.queryMailList", args);
    }
    
    // 开启宝箱
    // eventBillNo = boxNo(WalkGo)
    public static String receiveEvent(String eventBillNo) {
        return ApplicationHook.requestString("com.alipay.sportsplay.biz.rpc.walk.receiveEvent", "[{\"eventBillNo" + "\":\"" + eventBillNo + "\"}]");
    }
    
    // 查询路线奖励
    public static String queryPathReward(String pathId) {
        String args = "[{\"pathId\":\"" + pathId + "\"}]";
        return ApplicationHook.requestString("com.alipay.sportsplay.biz.rpc.walk.queryPathReward", args);
    }
    
    /* 这个好像没用 */
    public static String exchangeSuccess(String exchangeId) {
        String args1 = "[{\"exchangeId\":\"" + exchangeId + "\",\"timezone\":\"GMT+08:00\",\"version\":\"" + version + "\"}]";
        return ApplicationHook.requestString("alipay.charity.mobile.donate.exchange.success", args1);
    }
    
    /* 文体中心 */
    public static String userTaskGroupQuery(String groupId) {
        return ApplicationHook.requestString("alipay.tiyubiz.sports.userTaskGroup.query", "[{\"cityCode\":\"" + cityCode + "\",\"groupId\":\"" + groupId + "\"}]");
    }
    
    public static String userTaskComplete(String bizType, String taskId) {
        return ApplicationHook.requestString("alipay.tiyubiz.sports.userTask.complete", "[{\"bizType\":\"" + bizType + "\",\"cityCode\":\"" + cityCode + "\",\"completedTime\":" + System.currentTimeMillis() + ",\"taskId\":\"" + taskId + "\"}]");
    }
    
    public static String userTaskRightsReceive(String taskId, String userTaskId) {
        return ApplicationHook.requestString("alipay.tiyubiz.sports.userTaskRights.receive", "[{\"taskId\":\"" + taskId + "\",\"userTaskId\":\"" + userTaskId + "\"}]");
    }
    
    public static String queryAccount() {
        return ApplicationHook.requestString("alipay.tiyubiz.user.asset.query.account", "[{\"accountType" + "\":\"TIYU_SEED\"}]");
    }
    
    public static String queryRoundList() {
        return ApplicationHook.requestString("alipay.tiyubiz.wenti.walk.queryRoundList", "[{}]");
    }
    
    public static String participate(int bettingPoints, String InstanceId, String ResultId, String roundId) {
        return ApplicationHook.requestString("alipay.tiyubiz.wenti.walk.participate", "[{\"bettingPoints\":" + bettingPoints + ",\"guessInstanceId\":\"" + InstanceId + "\",\"guessResultId" + "\":\"" + ResultId + "\",\"newParticipant\":false,\"roundId\":\"" + roundId + "\"," + "\"stepTimeZone" +
                                                                                      "\":\"" + timeZone + "\"}]");
    }
    
    public static String pathFeatureQuery() {
        return ApplicationHook.requestString("alipay.tiyubiz.path.feature.query", "[{\"appId\":\"" + appId + "\"," + "\"features\":[\"USER_CURRENT_PATH_SIMPLE\"],\"sceneCode\":\"wenti_shijiebei\"}]");
    }
    
    public static String pathMapJoin(String pathId) {
        return ApplicationHook.requestString("alipay.tiyubiz.path.map.join", "[{\"appId\":\"" + appId + "\",\"pathId" + "\":\"" + pathId + "\"}]");
    }
    
    public static String pathMapHomepage(String pathId) {
        return ApplicationHook.requestString("alipay.tiyubiz.path.map.homepage", "[{\"appId\":\"" + appId + "\"," + "\"pathId\":\"" + pathId + "\"}]");
    }
    
    public static String stepQuery(String countDate, String pathId) {
        return ApplicationHook.requestString("alipay.tiyubiz.path.map.step.query", "[{\"appId\":\"" + appId + "\"," + "\"countDate\":\"" + countDate + "\",\"pathId\":\"" + pathId + "\",\"timeZone\":\"" + timeZone + "\"}]");
    }
    
    public static String tiyubizGo(String countDate, int goStepCount, String pathId, String userPathRecordId) {
        return ApplicationHook.requestString("alipay.tiyubiz.path.map.go",
                "[{\"appId\":\"" + appId + "\",\"countDate" + "\":\"" + countDate + "\",\"goStepCount\":" + goStepCount + ",\"pathId\":\"" + pathId + "\"," + "\"timeZone\":\"" + timeZone + "\",\"userPathRecordId\":\"" + userPathRecordId + "\"}]");
    }
    
    public static String rewardReceive(String pathId, String userPathRewardId) {
        return ApplicationHook.requestString("alipay.tiyubiz.path.map.reward.receive", "[{\"appId\":\"" + appId + "\",\"pathId\":\"" + pathId + "\",\"userPathRewardId\":\"" + userPathRewardId + "\"}]");
    }
    
    /* 抢好友大战 */
    public static String queryClubHome() {
        return ApplicationHook.requestString("alipay.antsports.club.home.queryClubHome", "[{\"apiVersion\":\"energy" + "\",\"chInfo\":\"healthstep\",\"timeZone\":\"" + timeZone + "\"}]");
    }
    
    public static String queryClubRoom(String roomId) {
        String args = "[{\"apiVersion\":\"energy\",\"chInfo\":\"healthstep\",\"roomId\":\"" + roomId + "\"}]";
        return ApplicationHook.requestString("alipay.antsports.club.trade.queryClubRoom", args);
    }
    
    // 方法: com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy
    // 芝麻粒：{"apiVersion":"energy","medEnergyBallInfoRecordIds":["a52eddef2cdb6e7242d285ddcde9c6c9"],
    // "pickAllEnergyBall":false,"source":"SPORT"}
    // {"apiVersion":"energy","chInfo":"medical_health","clientOS":"android","features":["DAILY_STEPS_RANK_V2",
    // "STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH","STAY_ON_COMPLETE",
    // "EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_AI","SUPPORT_TAB3","SUPPORT_FLYRABBIT",
    // "SUPPORT_NEW_MATCH","EXTERNAL_ADVERTISEMENT_TASK","PROP","PROPV2","ASIAN_GAMES"],
    // "medEnergyBallInfoRecordIds":["b28ecff68be22424bacaab41a5803706"],"pickAllEnergyBall":false,"source":"SPORT"}
    public static String collectBubble(String bubbleId) {
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy", "[{\"apiVersion" + "\":\"energy\",\"medEnergyBallInfoRecordIds\":[\"" + bubbleId + "\"],\"pickAllEnergyBall\":false," + "\"source\":\"SPORT\"}]");
    }
    
    // [{"apiVersion":"energy","chInfo":"healthstep"}]
    public static String queryTrainItem() {
        return ApplicationHook.requestString("alipay.antsports.club.train.queryTrainItem", "[{\"apiVersion\":\"energy" + "\",\"chInfo\":\"healthstep\"}]");
    }
    
    // 方法: alipay.antsports.club.train.trainMember
    // 参数:[{"apiVersion":"energy","chInfo":"healthstep","itemType":"yangko","memberId":"cm0000002088842214318540",
    // "originBossId":"2088842214318540"}]}
    public static String DoubletrainMember(String itemType, String bizId, String memberId, String originBossId) {
        return ApplicationHook.requestString("alipay.antsports.club.train.trainMember", "[{\"apiVersion\":\"energy\"," + "\"bizId\":\"" + bizId + "\",\"chInfo\":\"healthstep\",\"itemType\":\"" + itemType + "\",\"memberId" + "\":\"" + memberId + "\",\"originBossId\":\"" + originBossId + "\"}]");
    }
    
    public static String trainMember(String itemType, String memberId, String originBossId) {
        return ApplicationHook.requestString("alipay.antsports.club.train.trainMember", "[{\"apiVersion\":\"energy\"," + "\"chInfo\":\"healthstep\",\"itemType\":\"" + itemType + "\",\"memberId\":\"" + memberId + "\"," + "\"originBossId\":\"" + originBossId + "\"}]");
    }
    
    // {"apiVersion":"energy","bizNo":"67e538d7d031542ddcbfcda7dcb9effc","taskAction":"SHOW_AD",
    // "taskId":"AP16235854","taskType":"AD_TASK"}
    public static String completeTask(String bizId) {
        return ApplicationHook.requestString("alipay.antsports.club.train.trainMember", "[{\"apiVersion\":\"energy\"," + "\"bizNo\":\"" + bizId + "\",\"taskAction\":\"SHOW_AD\",\"taskId\":\"AP16235854\"," + "\"taskType" + "\":\"AD_TASK\"}]");
    }
    
    public static String queryMemberPriceRankingEnergy(int coinBalance) {
        String args = "[{\"apiVersion\":\"energy\",\"buyMember\":true,\"chInfo\":\"healthstep\",\"coinBalance\":\"" + coinBalance + "\"}]";
        return ApplicationHook.requestString("alipay.antsports.club.ranking.queryMemberPriceRanking", args);
    }
    
    // 方法: alipay.antsports.club.ranking.queryMemberPriceRanking
    // 参数: [{"apiVersion":"energy","buyMember":true,"chInfo":"healthstep","coinBalance":31662}]}
    // 数据:
    // public static String queryMemberPriceRanking() {
    //   String args = "[{\"apiVersion\":\"energy\",\"buyMember\":true,\"chInfo\":\"healthstep\"}]";
    //  return ApplicationHook.requestString("alipay.antsports.club.ranking.queryMemberPriceRanking",
    // args);
    // }
    
    // alipay.antsports.club.trade.queryClubMember
    // [{"apiVersion":"energy","chInfo":"healthstep","memberId":"cm0000002088842214318540",
    // "originBossId":"2088842214318540"}]}
    public static String queryClubMember(String memberId, String originBossId) {
        return ApplicationHook.requestString("alipay.antsports.club.trade.queryClubMember", "[{\"apiVersion" + "\":\"energy\",\"chInfo\":\"healthstep\",\"memberId\":\"" + memberId + "\",\"originBossId\":\"" + originBossId + "\"}]");
    }
    
    public static String buyMember(String currentBossId, String memberId, String originBossId, JSONObject priceInfo, String roomId) {
        String requestData = "[{\"apiVersion\":\"energy\",\"chInfo\":\"healthstep\",\"currentBossId\":\"" + currentBossId + "\"," + "\"memberId\":\"" + memberId + "\",\"originBossId\":\"" + originBossId + "\",\"priceInfo\":" + priceInfo + ",\"roomId\":\"" + roomId + "\"}]";
        return ApplicationHook.requestString("alipay.antsports.club.trade.buyMember", requestData);
    }
    
    // 运动币兑好礼
    public static String queryItemDetail(String itemId) {
        String arg = "[{\"itemId\":\"" + itemId + "\"}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthItemCenterRpc" + ".queryItemDetail", arg);
    }
    
    public static String exchangeItem(String itemId, int coinAmount) {
        String arg = "[{\"coinAmount\":" + coinAmount + ",\"itemId\":\"" + itemId + "\"}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthItemCenterRpc.exchangeItem", arg);
    }
    
    public static String queryExchangeRecordPage(String exchangeRecordId) {
        String arg = "[{\"exchangeRecordId\":\"" + exchangeRecordId + "\"}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthItemCenterRpc" + ".queryExchangeRecordPage", arg);
    }
    
    //悦动健康岛
    public static String receiveSpecialPrize(String sceneType) {
        String arg = "[{\"sceneType\":\"" + sceneType + "\"}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.receiveSpecialPrize", arg);
    }
    
    public static String takeSign() {
        String arg = "[{\"signType\":3,\"source\":\"jkdprizesign\"}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.takeSign", arg);
    }
    
    public static String neverlandtaskReceive(String arg) {
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.taskReceive", arg);
    }
    
    public static String neverlandtaskSend(String arg) {
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.taskSend", arg);
    }
    
    public static String neverlandwalkGrid(String branchId, String mapId) {
        String arg = "[{\"branchId\":\"" + branchId + "\",\"drilling\":false,\"mapId\":\"" + mapId + "\"}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.walkGrid", arg);
    }
    
    public static String neverlandenergyReceive(String arg) {
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.energyReceive", arg);
    }
    
    public static String offlineAward() {
        String arg = "[{\"isAdvertisement\":true}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.offlineAward", arg);
    }
    
    //方法有重名
    public static String neverlandpickBubbleTaskEnergy(String recordId) {
        String arg = "[{\"medEnergyBallInfoRecordIds\":[\"" + recordId + "\"],\"pickAllEnergyBall\":false}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy", arg);
    }
    
    public static String queryBaseinfo() {
        String arg = "[{}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.queryBaseinfo", arg);
    }
    
    public static String queryBubbleTask() {
        String arg = "[{\"sportsAuthed\":true}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.queryBubbleTask", arg);
    }
    
    public static String queryItemList(int page) {
        String arg = "[{\"page\":" + page + "}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.queryItemList", arg);
    }
    
    public static String createOrder(String benefitId, String itemId) {
        String arg = "[{\"benefitId\":\"" + benefitId + "\",\"itemId\":\"" + itemId + "\"}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.createOrder", arg);
    }
    
    public static String queryMapInfo(String branchId, String mapId) {
        String arg = "[{\"branchId\":\"" + branchId + "\",\"drilling\":false,\"mapId\":\"" + mapId + "\"}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.queryMapInfo", arg);
    }
    
    public static String querySign() {
        String arg = "[{\"source\":\"jkdprizesign\"}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.querySign", arg);
    }
    
    public static String queryTaskCenter() {
        String arg = "[{\"isAdvertisement\":true}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.queryTaskCenter", arg);
    }
    
    public static String queryTaskInfo() {
        String arg = "[{\"source\":\"health-island\",\"type\":\"LIGHT_FEEDS_TASK\"}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.queryTaskInfo", arg);
    }
    
    public static String queryUserAccount() {
        String arg = "[{}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.queryUserAccount", arg);
    }
    
    public static String checkAuth() {
        String arg = "[{\"assetSceneList\":[\"AS_106240429002783499\"]}]";
        return ApplicationHook.requestString("alipay.antsigncenter.checkAuth", arg);
    }
    
    //requestData":[{"source":"jkdsportcard"}]
    public static String queryMapList() {
        String arg = "[{\"source\":\"jkdsportcard\"}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.queryMapList", arg);
    }
    
    //{"branchId":"MASTER","mapId":"MM120","source":"jkdsportcard"}
    public static String mapChooseFree(String branchId,String mapId) {
        String arg = "[{\"branchId\":\""+branchId+"\",\"mapId\":\""+mapId+"\",\"source\":\"jkdsportcard\"}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.mapChooseFree", arg);
    }
    
    //{"branchId":"MASTER","mapId":"MM125","multiNum":10,"source":"jkdsportcard"}
    public static String build(String branchId,String mapId,int multiNum) {
        String arg = "[{\"branchId\":\""+branchId+"\",\"mapId\":\""+mapId+"\",\"multiNum\":"+multiNum+",\"source\":\"jkdsportcard\"}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.build", arg);
    }
    
    //{"branchId":"MASTER","mapId":"MM125","multiNum":10,"source":"jkdsportcard"}
    public static String queryMapDetail(String mapId) {
        String arg = "[{\"mapId\":\""+mapId+"\",\"source\":\"jkdsportcard\"}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.queryMapDetail", arg);
    }
    
    //{"branchId":"MASTER","channel":"jkdsportcard","mapId":"MM125","rewardId":"GAME_BUILD_MM125_3_3_1","source":"jkdsportcard"}
    public static String mapChooseReward(String branchId,String mapId,String rewardId) {
        String arg = "[{\"branchId\":\""+branchId+"\",\"channel\":\"jkdsportcard\",\"mapId\":\""+mapId+"\",\"rewardId\":\""+rewardId+"\",\"source\":\"jkdsportcard\"}]";
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.mapChooseReward", arg);
    }
}
