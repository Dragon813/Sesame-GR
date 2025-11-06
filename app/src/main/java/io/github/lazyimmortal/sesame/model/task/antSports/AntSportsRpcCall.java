package io.github.lazyimmortal.sesame.model.task.antSports;

import org.json.JSONObject;

import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;

public class AntSportsRpcCall {
    private static final String chInfo = "ch_appcenter__chsub_9patch",
            timeZone = "Asia/Shanghai", version = "3.0.1.2", alipayAppVersion = "0.0.852",
            cityCode = "330100", appId = "2021002116659397";

    // 运动任务查询
    //{"apiVersion":"energy","canAddHome":false,"chInfo":"medical_health","clientAuthStatus":"not_support","clientOS":"android","features":["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH","STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_AI","SUPPORT_TAB3","SUPPORT_FLYRABBIT","SUPPORT_NEW_MATCH","EXTERNAL_ADVERTISEMENT_TASK","PROP","PROPV2","ASIAN_GAMES"],"topTaskId":""}
    public static String queryCoinTaskPanel() {
        String args = "[{\"apiVersion\":\"energy\",\"canAddHome\":false,\"chInfo\":\"medical_health\",\"clientAuthStatus\":\"not_support\",\"clientOS\":\"android\",\"features\":[\"DAILY_STEPS_RANK_V2\",\"STEP_BATTLE\",\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\",\"CLOUD_SDK_AUTH\",\"STAY_ON_COMPLETE\",\"EXTRA_TREASURE_BOX\",\"NEW_HOME_PAGE_STATIC\",\"SUPPORT_AI\",\"SUPPORT_TAB3\",\"SUPPORT_FLYRABBIT\",\"SUPPORT_NEW_MATCH\",\"EXTERNAL_ADVERTISEMENT_TASK\",\"PROP\",\"PROPV2\",\"ASIAN_GAMES\"],\"topTaskId\":\"\"}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.queryCoinTaskPanel", args);
    }

    public static String signUpTask(String taskId) {
        String args = "[{\"taskId\":\"" + taskId + "\"}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.signUpTask", args);
    }

    //{"apiVersion":"energy","bizNo":"1760803446912-b3612450-6d3b-485e-a8d1-167f8372940e","taskAction":"SHOW_AD","taskId":"AP19300697","taskType":"AD_TASK"}
    public static String completeTask(String taskAction, String taskId) {
        String args = "[{\"taskAction\":\"" + taskAction + "\",\"taskId\":\"" + taskId + "\"}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.completeTask", args);
    }

    public static String duublecompleteTask(String bizNo, String taskAction, String taskId) {
        String args = "[{\"apiVersion\":\"energy\",\"bizNo\":\"" + bizNo + "\",\"taskAction\":\"" + taskAction + "\",\"taskId\":\"" + taskId + "\",\"taskType\":\"AD_TASK\"}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.completeTask", args);
    }

    public static String signInCoinTask() {
        String args = "[{\"operatorType\":\"signIn\"}]";
        //String args = "[{\"apiVersion\":\"energy\",\"operatorType\":\"query\"}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.signInCoinTask", args);
    }


    //{"apiVersion":"energy","bubbleId":"","canAddHome":false,"chInfo":"ch_shouquan_shouye","clientAuthStatus":"not_support","clientOS":"android","distributionChannel":"","features":["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH","STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_AI","SUPPORT_TAB3","SUPPORT_FLYRABBIT","SUPPORT_NEW_MATCH","EXTERNAL_ADVERTISEMENT_TASK","PROP","PROPV2","ASIAN_GAMES"],"outBizNo":""}
    public static String queryCoinBubbleModule() {
        String args = "[{\"apiVersion\":\"energy\",\"bubbleId\":\"\",\"canAddHome\":false,\"chInfo\":\"ch_shouquan_shouye\",\"clientAuthStatus\":\"not_support\",\"clientOS\":\"android\",\"distributionChannel\":\"\",\"features\":[\"DAILY_STEPS_RANK_V2\",\"STEP_BATTLE\",\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\",\"CLOUD_SDK_AUTH\",\"STAY_ON_COMPLETE\",\"EXTRA_TREASURE_BOX\",\"NEW_HOME_PAGE_STATIC\",\"SUPPORT_AI\",\"SUPPORT_TAB3\",\"SUPPORT_FLYRABBIT\",\"SUPPORT_NEW_MATCH\",\"EXTERNAL_ADVERTISEMENT_TASK\",\"PROP\",\"PROPV2\",\"ASIAN_GAMES\"],\"outBizNo\":\"\"}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.sportsHealthHomeRpc.queryEnergyBubbleModule", args);
    }
/*
    public static String receiveCoinAsset(String assetId, int coinAmount) {
        // "tracertPos": "首页金币收集" "任务面板"
        String args = "[{\"assetId\":\"" + assetId + "\",\"coinAmount\":" + coinAmount + "}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinCenterRpc.receiveCoinAsset", args);
    }
*/
public static String receiveCoinAsset(String assetId) {

    String args = "[{\"apiVersion\":\"energy\",\"chInfo\":\"medical_health\",\"clientOS\":\"android\",\"features\":[\"DAILY_STEPS_RANK_V2\",\"STEP_BATTLE\",\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\",\"CLOUD_SDK_AUTH\",\"STAY_ON_COMPLETE\",\"EXTRA_TREASURE_BOX\",\"NEW_HOME_PAGE_STATIC\",\"SUPPORT_AI\",\"SUPPORT_TAB3\",\"SUPPORT_FLYRABBIT\",\"SUPPORT_NEW_MATCH\",\"EXTERNAL_ADVERTISEMENT_TASK\",\"PROP\",\"PROPV2\",\"ASIAN_GAMES\"],\"medEnergyBallInfoRecordIds\":[\""+assetId+"\"],\"pickAllEnergyBall\":false,\"source\":\"SPORT\"}]";
    return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy", args);
}

    public static String pickAllEnergyBall() {
        String args = "[{\"apiVersion\":\"energy\",\"chInfo\":\"ch_shouquan_shouye\",\"clientOS\":\"android\",\"features\":[\"DAILY_STEPS_RANK_V2\",\"STEP_BATTLE\",\"CLUB_HOME_CARD\",\"NEW_HOME_PAGE_STATIC\",\"CLOUD_SDK_AUTH\",\"STAY_ON_COMPLETE\",\"EXTRA_TREASURE_BOX\",\"NEW_HOME_PAGE_STATIC\",\"SUPPORT_AI\",\"SUPPORT_TAB3\",\"SUPPORT_FLYRABBIT\",\"SUPPORT_NEW_MATCH\",\"EXTERNAL_ADVERTISEMENT_TASK\",\"PROP\",\"PROPV2\",\"ASIAN_GAMES\"],\"medEnergyBallInfoRecordIds\":[],\"pickAllEnergyBall\":true,\"source\":\"SPORT\"}]";
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

    public static String queryWalkStep() {
        String args = "[{}]";
        return ApplicationHook.requestString("alipay.antsports.walk.user.queryWalkStep", args);
    }

    public static String walkDonateSignInfo(int count) {
        return ApplicationHook.requestString("alipay.charity.mobile.donate.walk.walkDonateSignInfo",
                "[{\"needDonateAction\":false,\"source\":\"walkDonateHome\",\"steps\":" + count
                        + ",\"timezoneId\":\""
                        + timeZone + "\"}]");
    }

    public static String donateWalkHome(int steps) {
        String args = "[{\"module\":\"3\",\"steps\":" + steps + ",\"timezoneId\":\"" + timeZone + "\"}]";
        return ApplicationHook.requestString("alipay.charity.mobile.donate.walk.home", args);
    }

    public static String donateExchangeRecord() {
        String args = "[{\"page\":1,\"pageSize\":10}]";
        return ApplicationHook.requestString("alipay.charity.mobile.donate.exchange.record", args);
    }

    public static String donateWalkExchange(String actId, int count, String donateToken) {
        return ApplicationHook.requestString("alipay.charity.mobile.donate.walk.exchange",
                "[{\"actId\":\"" + actId + "\",\"count\":"
                        + count + ",\"donateToken\":\"" + donateToken + "\",\"timezoneId\":\""
                        + timeZone + "\",\"ver\":0}]");
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
        } catch (Throwable t) {
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
        return ApplicationHook.requestString("com.alipay.sportsplay.biz.rpc.walk.receiveEvent",
                "[{\"eventBillNo\":\"" + eventBillNo + "\"}]");
    }

    // 查询路线奖励
    public static String queryPathReward(String pathId) {
        String args = "[{\"pathId\":\"" + pathId + "\"}]";
        return ApplicationHook.requestString("com.alipay.sportsplay.biz.rpc.walk.queryPathReward", args);
    }

    /* 这个好像没用 */
    public static String exchangeSuccess(String exchangeId) {
        String args1 = "[{\"exchangeId\":\"" + exchangeId
                + "\",\"timezone\":\"GMT+08:00\",\"version\":\"" + version + "\"}]";
        return ApplicationHook.requestString("alipay.charity.mobile.donate.exchange.success", args1);
    }

    /* 文体中心 */
    public static String userTaskGroupQuery(String groupId) {
        return ApplicationHook.requestString("alipay.tiyubiz.sports.userTaskGroup.query",
                "[{\"cityCode\":\"" + cityCode + "\",\"groupId\":\"" + groupId + "\"}]");
    }

    public static String userTaskComplete(String bizType, String taskId) {
        return ApplicationHook.requestString("alipay.tiyubiz.sports.userTask.complete",
                "[{\"bizType\":\"" + bizType + "\",\"cityCode\":\"" + cityCode + "\",\"completedTime\":"
                        + System.currentTimeMillis() + ",\"taskId\":\"" + taskId + "\"}]");
    }

    public static String userTaskRightsReceive(String taskId, String userTaskId) {
        return ApplicationHook.requestString("alipay.tiyubiz.sports.userTaskRights.receive",
                "[{\"taskId\":\"" + taskId + "\",\"userTaskId\":\"" + userTaskId + "\"}]");
    }

    public static String queryAccount() {
        return ApplicationHook.requestString("alipay.tiyubiz.user.asset.query.account",
                "[{\"accountType\":\"TIYU_SEED\"}]");
    }

    public static String queryRoundList() {
        return ApplicationHook.requestString("alipay.tiyubiz.wenti.walk.queryRoundList",
                "[{}]");
    }

    public static String participate(int bettingPoints, String InstanceId, String ResultId, String roundId) {
        return ApplicationHook.requestString("alipay.tiyubiz.wenti.walk.participate",
                "[{\"bettingPoints\":" + bettingPoints + ",\"guessInstanceId\":\"" + InstanceId
                        + "\",\"guessResultId\":\"" + ResultId
                        + "\",\"newParticipant\":false,\"roundId\":\"" + roundId
                        + "\",\"stepTimeZone\":\"" + timeZone + "\"}]");
    }

    public static String pathFeatureQuery() {
        return ApplicationHook.requestString("alipay.tiyubiz.path.feature.query",
                "[{\"appId\":\"" + appId
                        + "\",\"features\":[\"USER_CURRENT_PATH_SIMPLE\"],\"sceneCode\":\"wenti_shijiebei\"}]");
    }

    public static String pathMapJoin(String pathId) {
        return ApplicationHook.requestString("alipay.tiyubiz.path.map.join",
                "[{\"appId\":\"" + appId + "\",\"pathId\":\"" + pathId + "\"}]");
    }

    public static String pathMapHomepage(String pathId) {
        return ApplicationHook.requestString("alipay.tiyubiz.path.map.homepage",
                "[{\"appId\":\"" + appId + "\",\"pathId\":\"" + pathId + "\"}]");
    }

    public static String stepQuery(String countDate, String pathId) {
        return ApplicationHook.requestString("alipay.tiyubiz.path.map.step.query",
                "[{\"appId\":\"" + appId + "\",\"countDate\":\"" + countDate
                        + "\",\"pathId\":\""
                        + pathId + "\",\"timeZone\":\"" + timeZone + "\"}]");
    }

    public static String tiyubizGo(String countDate, int goStepCount, String pathId, String userPathRecordId) {
        return ApplicationHook.requestString("alipay.tiyubiz.path.map.go",
                "[{\"appId\":\"" + appId + "\",\"countDate\":\"" + countDate
                        + "\",\"goStepCount\":"
                        + goStepCount + ",\"pathId\":\"" + pathId
                        + "\",\"timeZone\":\"" + timeZone + "\",\"userPathRecordId\":\""
                        + userPathRecordId + "\"}]");
    }

    public static String rewardReceive(String pathId, String userPathRewardId) {
        return ApplicationHook.requestString("alipay.tiyubiz.path.map.reward.receive",
                "[{\"appId\":\"" + appId + "\",\"pathId\":\"" + pathId + "\",\"userPathRewardId\":\""
                        + userPathRewardId + "\"}]");
    }

    /* 抢好友大战 */
    public static String queryClubHome() {
        return ApplicationHook.requestString("alipay.antsports.club.home.queryClubHome",
                "[{\"apiVersion\":\"energy\",\"chInfo\":\"healthstep\",\"timeZone\":\"" + timeZone + "\"}]");
    }

    public static String queryClubRoom(String roomId) {
        String args = "[{\"apiVersion\":\"energy\",\"chInfo\":\"healthstep\",\"roomId\":\"" + roomId + "\"}]";
        return ApplicationHook.requestString("alipay.antsports.club.trade.queryClubRoom", args);
    }

    //方法: com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy
    //芝麻粒：{"apiVersion":"energy","medEnergyBallInfoRecordIds":["a52eddef2cdb6e7242d285ddcde9c6c9"],"pickAllEnergyBall":false,"source":"SPORT"}
    //{"apiVersion":"energy","chInfo":"medical_health","clientOS":"android","features":["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH","STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_AI","SUPPORT_TAB3","SUPPORT_FLYRABBIT","SUPPORT_NEW_MATCH","EXTERNAL_ADVERTISEMENT_TASK","PROP","PROPV2","ASIAN_GAMES"],"medEnergyBallInfoRecordIds":["b28ecff68be22424bacaab41a5803706"],"pickAllEnergyBall":false,"source":"SPORT"}
    public static String collectBubble(String bubbleId) {
        return ApplicationHook.requestString("com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy",
                "[{\"apiVersion\":\"energy\",\"medEnergyBallInfoRecordIds\":[\""+bubbleId+"\"],\"pickAllEnergyBall\":false,\"source\":\"SPORT\"}]");
    }


    //[{"apiVersion":"energy","chInfo":"healthstep"}]
    public static String queryTrainItem() {
        return ApplicationHook.requestString("alipay.antsports.club.train.queryTrainItem",
                "[{\"apiVersion\":\"energy\",\"chInfo\":\"healthstep\"}]");
    }

    //方法: alipay.antsports.club.train.trainMember
    //参数:[{"apiVersion":"energy","chInfo":"healthstep","itemType":"yangko","memberId":"cm0000002088842214318540","originBossId":"2088842214318540"}]}
    public static String DoubletrainMember(String itemType, String bizId, String memberId, String originBossId) {
        return ApplicationHook.requestString("alipay.antsports.club.train.trainMember",
                "[{\"apiVersion\":\"energy\",\"bizId\":\"" + bizId + "\",\"chInfo\":\"healthstep\",\"itemType\":\"" + itemType + "\",\"memberId\":\"" + memberId + "\",\"originBossId\":\"" + originBossId + "\"}]");
    }

    public static String trainMember(String itemType, String memberId, String originBossId) {
        return ApplicationHook.requestString("alipay.antsports.club.train.trainMember",
                "[{\"apiVersion\":\"energy\",\"chInfo\":\"healthstep\",\"itemType\":\"" + itemType + "\",\"memberId\":\"" + memberId + "\",\"originBossId\":\"" + originBossId + "\"}]");
    }

    //{"apiVersion":"energy","bizNo":"67e538d7d031542ddcbfcda7dcb9effc","taskAction":"SHOW_AD","taskId":"AP16235854","taskType":"AD_TASK"}
    public static String completeTask(String bizId) {
        return ApplicationHook.requestString("alipay.antsports.club.train.trainMember",
                "[{\"apiVersion\":\"energy\",\"bizNo\":\"" + bizId + "\",\"taskAction\":\"SHOW_AD\",\"taskId\":\"AP16235854\",\"taskType\":\"AD_TASK\"}]");
    }

    public static String queryMemberPriceRankingEnergy(int coinBalance) {
        String args = "[{\"apiVersion\":\"energy\",\"buyMember\":true,\"chInfo\":\"healthstep\",\"coinBalance\":\"" + coinBalance + "\"}]";
        return ApplicationHook.requestString("alipay.antsports.club.ranking.queryMemberPriceRanking", args);
    }

    //方法: alipay.antsports.club.ranking.queryMemberPriceRanking
    //参数: [{"apiVersion":"energy","buyMember":true,"chInfo":"healthstep","coinBalance":31662}]}
    //数据: {"apiVersion":"energy","ariverRpcTraceId":"client`Zw5d08vJ7jUDAO3EgYXsleOePb5OX2j_5321681","energyBalance":31662,"fakePageSize":20,"inviteOpenSportsCachedDays":30,"rank":{"data":[{"currentBossId":"2088942406581892","gmtAllowTrade":1754064000000,"memberId":"cm0000002088542419380143","memberStatus":"SOLD","open":true,"originBossId":"2088542419380143","price":6267,"training":false},{"currentBossId":"2088942405652872","gmtAllowTrade":1757234127713,"memberId":"cm0000002088842214318540","memberStatus":"SOLD","open":true,"originBossId":"2088842214318540","price":5833,"training":false},{"currentBossId":"2088942406581892","gmtAllowTrade":1753979715921,"memberId":"cm0000002088942404841202","memberStatus":"SOLD","open":true,"originBossId":"2088942404841202","price":5441,"training":false},{"currentBossId":"2088942406287310","gmtAllowTrade":1755917948558,"memberId":"cm0000002088942406581892","memberStatus":"SOLD","open":true,"originBossId":"2088942406581892","price":5441,"training":false},{"currentBossId":"2088942405652872","gmtAllowTrade":1753977600000,"memberId":"cm0000002088942406287310","memberStatus":"SOLD","open":true,"originBossId":"2088942406287310","price":5427,"training":false},{"currentBossId":"2088942406287310","gmtAllowTrade":1755918378831,"memberId":"cm0000002088942405652872","memberStatus":"SOLD","open":true,"originBossId":"2088942405652872","price":5413,"training":false},{"currentBossId":"2088942406581892","gmtAllowTrade":1753977600000,"memberId":"cm0000002088942403520113","memberStatus":"SOLD","open":true,"originBossId":"2088942403520113","price":5371,"training":false},{"currentBossId":"2088942403520113","gmtAllowTrade":1753977600000,"memberId":"cm0000002088942405113719","memberStatus":"SOLD","open":true,"originBossId":"2088942405113719","price":5049,"training":false},{"currentBossId":"2088342529334159","gmtAllowTrade":1756107022001,"memberId":"cm0000002088802442155991","memberStatus":"SOLD","open":true,"originBossId":"2088802442155991","price":5021,"training":false},{"currentBossId":"2088942477411601","gmtAllowTrade":1756644907837,"memberId":"cm0000002088002862725396","memberStatus":"SOLD","open":true,"originBossId":"2088002862725396","price":2837,"training":false},{"currentBossId":"2088432804871476","gmtAllowTrade":1751608168270,"memberId":"cm0000002088612279861073","memberStatus":"SOLD","open":true,"originBossId":"2088612279861073","price":149,"training":false},{"currentBossId":"2088022813616245","gmtAllowTrade":1755122797861,"memberId":"cm0000002088022813616245","memberStatus":"FREE","open":true,"originBossId":"2088022813616245","price":126,"training":false},{"currentBossId":"2088002062903510","gmtAllowTrade":1755300583535,"memberId":"cm0000002088002062903510","memberStatus":"FREE","open":true,"originBossId":"2088002062903510","price":66,"training":false},{"currentBossId":"2088702045389136","gmtAllowTrade":1675666586113,"memberId":"cm0000002088702117350932","memberStatus":"SOLD","open":true,"originBossId":"2088702117350932","price":35,"training":false},{"currentBossId":"2088302043927855","gmtAllowTrade":1721947333108,"memberId":"cm0000002088302043927855","memberStatus":"FREE","open":true,"originBossId":"2088302043927855","price":35,"training":false},{"currentBossId":"2088702653747595","gmtAllowTrade":1638460800000,"memberId":"cm0000002088002306745388","memberStatus":"SOLD","open":true,"originBossId":"2088002306745388","price":25,"training":false},{"currentBossId":"2088002344874998","gmtAllowTrade":1733960205539,"memberId":"cm0000002088002344874998","memberStatus":"FREE","open":true,"originBossId":"2088002344874998","price":20,"training":false},{"currentBossId":"2088002564045327","gmtAllowTrade":1660859459612,"memberId":"cm0000002088002092679102","memberStatus":"SOLD","open":true,"originBossId":"2088002092679102","price":19,"training":false},{"currentBossId":"2088842214318540","gmtAllowTrade":1735833600000,"memberId":"cm0000002088842624008325","memberStatus":"SOLD","open":true,"originBossId":"2088842624008325","price":15,"training":true},{"currentBossId":"2088342654219820","gmtAllowTrade":1733997140092,"memberId":"cm0000002088842623617162","memberStatus":"SOLD","open":true,"originBossId":"2088842623617162","price":15,"training":true},{"currentBossId":"2088342654219820","gmtAllowTrade":1734105600000,"memberId":"cm0000002088842623176649","memberStatus":"SOLD","open":true,"originBossId":"2088842623176649","price":14,"training":true},{"currentBossId":"2088842214318540","gmtAllowTrade":1735827327441,"memberId":"cm0000002088842625233401","memberStatus":"SOLD","open":true,"originBossId":"2088842625233401","price":14,"training":true},{"currentBossId":"2088842214318540","gmtAllowTrade":1734017714055,"memberId":"cm0000002088842736213752","memberStatus":"SOLD","open":true,"originBossId":"2088842736213752","price":13,"training":true},{"currentBossId":"2088342654219820","gmtAllowTrade":1734017941283,"memberId":"cm0000002088842626382007","memberStatus":"SOLD","open":true,"originBossId":"2088842626382007","price":13,"training":true},{"currentBossId":"2088542419380143","gmtAllowTrade":1757283779196,"memberId":"cm0000002088842736552071","memberStatus":"SOLD","open":true,"originBossId":"2088842736552071","price":13,"training":true},{"currentBossId":"2088002795595507","gmtAllowTrade":1757779200000,"memberId":"cm0000002088842625343272","memberStatus":"SOLD","open":true,"originBossId":"2088842625343272","price":13,"training":true},{"currentBossId":"2088222768056813","gmtAllowTrade":1638275205422,"memberId":"cm0000002088222768056813","memberStatus":"FREE","open":true,"originBossId":"2088222768056813","price":12,"training":false},{"currentBossId":"2088002981483285","gmtAllowTrade":1640182587694,"memberId":"cm0000002088002024346403","memberStatus":"SOLD","open":true,"originBossId":"2088002024346403","price":12,"training":false},{"currentBossId":"2088002795595507","gmtAllowTrade":1735828272381,"memberId":"cm0000002088842737963962","memberStatus":"SOLD","open":true,"originBossId":"2088842737963962","price":12,"training":true},{"currentBossId":"2088542419380143","gmtAllowTrade":1737043200000,"memberId":"cm0000002088942509019434","memberStatus":"SOLD","open":true,"originBossId":"2088942509019434","price":12,"training":true},{"currentBossId":"2088542419380143","gmtAllowTrade":1738754633438,"memberId":"cm0000002088942477195136","memberStatus":"SOLD","open":true,"originBossId":"2088942477195136","price":12,"training":true},{"currentBossId":"2088542419380143","gmtAllowTrade":1745833944842,"memberId":"cm0000002088942477411601","memberStatus":"SOLD","open":true,"originBossId":"2088942477411601","price":12,"training":true},{"currentBossId":"2088342654219820","gmtAllowTrade":1733997167468,"memberId":"cm0000002088842738284572","memberStatus":"SOLD","open":true,"originBossId":"2088842738284572","price":11,"training":true},{"currentBossId":"2088542419380143","gmtAllowTrade":1737034429738,"memberId":"cm0000002088942507966666","memberStatus":"SOLD","open":true,"originBossId":"2088942507966666","price":11,"training":true},{"currentBossId":"2088542419380143","gmtAllowTrade":1745833933826,"memberId":"cm0000002088942475363915","memberStatus":"SOLD","open":true,"originBossId":"2088942475363915","price":11,"training":true},{"currentBossId":"2088542419380143","gmtAllowTrade":1745833938934,"memberId":"cm0000002088942509795981","memberStatus":"SOLD","open":true,"originBossId":"2088942509795981","price":11,"training":true},{"currentBossId":"2088542419380143","gmtAllowTrade":1757283616008,"memberId":"cm0000002088942442367793","memberStatus":"SOLD","open":true,"originBossId":"2088942442367793","price":11,"training":true},{"currentBossId":"2088002862725396","gmtAllowTrade":1657516420473,"memberId":"cm0000002088302065579423","memberStatus":"SOLD","open":true,"originBossId":"2088302065579423","price":11,"training":false},{"currentBossId":"2088722171632137","gmtAllowTrade":1736718384741,"memberId":"cm0000002088942032405542","memberStatus":"SOLD","open":true,"originBossId":"2088942032405542","price":11,"training":true},{"currentBossId":"2088722171632137","gmtAllowTrade":1736718408618,"memberId":"cm0000002088942032692920","memberStatus":"SOLD","open":true,"originBossId":"2088942032692920","price":11,"training":true},{"currentBossId":"2088002795595507","gmtAllowTrade":1736718482292,"memberId":"cm0000002088942069348663","memberStatus":"SOLD","open":true,"originBossId":"2088942069348663","price":11,"training":true},{"currentBossId":"2088002795595507","gmtAllowTrade":1736718518191,"memberId":"cm0000002088942027062754","memberStatus":"SOLD","open":true,"originBossId":"2088942027062754","price":11,"training":true},{"currentBossId":"2088842214318540","gmtAllowTrade":1737036382144,"memberId":"cm0000002088942472659060","memberStatus":"SOLD","open":true,"originBossId":"2088942472659060","price":11,"training":true},{"currentBossId":"2088842214318540","gmtAllowTrade":1737036397351,"memberId":"cm0000002088942473943614","memberStatus":"SOLD","open":true,"originBossId":"2088942473943614","price":11,"training":true},{"currentBossId":"2088342654219820","gmtAllowTrade":1737036627990,"memberId":"cm0000002088942474727844","memberStatus":"SOLD","open":true,"originBossId":"2088942474727844","price":11,"training":true},{"currentBossId":"2088842214318540","gmtAllowTrade":1737272236744,"memberId":"cm0000002088942474436533","memberStatus":"SOLD","open":true,"originBossId":"2088942474436533","price":11,"training":true},{"currentBossId":"2088122935513522","gmtAllowTrade":1742652601797,"memberId":"cm0000002088412463725127","memberStatus":"SOLD","open":true,"originBossId":"2088412463725127","price":11,"training":true},{"currentBossId":"2088722171632137","gmtAllowTrade":1745752599855,"memberId":"cm0000002088942477367342","memberStatus":"SOLD","open":true,"originBossId":"2088942477367342","price":11,"training":true},{"currentBossId":"2088842214318540","gmtAllowTrade":1745752756756,"memberId":"cm0000002088942477193471","memberStatus":"SOLD","open":true,"originBossId":"2088942477193471","price":11,"training":true},{"currentBossId":"2088342654219820","gmtAllowTrade":1745752830242,"memberId":"cm0000002088942477756872","memberStatus":"SOLD","open":true,"originBossId":"2088942477756872","price":11,"training":true},{"currentBossId":"2088342654219820","gmtAllowTrade":1745834267240,"memberId":"cm0000002088942411422304","memberStatus":"SOLD","open":true,"originBossId":"2088942411422304","price":11,"training":true},{"currentBossId":"2088342654219820","gmtAllowTrade":1745834277026,"memberId":"cm0000002088942412293626","memberStatus":"SOLD","open":true,"originBossId":"2088942412293626","price":11,"training":true},{"currentBossId":"2088722171632137","gmtAllowTrade":1745834339657,"memberId":"cm0000002088942412412160","memberStatus":"SOLD","open":true,"originBossId":"2088942412412160","price":11,"training":true},{"currentBossId":"2088722171632137","gmtAllowTrade":1745834451641,"memberId":"cm0000002088942440970164","memberStatus":"SOLD","open":true,"originBossId":"2088942440970164","price":11,"training":true},{"currentBossId":"2088722171632137","gmtAllowTrade":1745834465853,"memberId":"cm0000002088942442447615","memberStatus":"SOLD","open":true,"originBossId":"2088942442447615","price":11,"training":true},{"currentBossId":"2088842946891403","memberId":"cm0000002088842946891403","memberStatus":"FREE","open":true,"originBossId":"2088842946891403","price":10,"training":false},{"currentBossId":"2088942472624862","memberId":"cm0000002088942472624862","memberStatus":"FREE","open":true,"originBossId":"2088942472624862","price":10,"training":false},{"currentBossId":"2088942473903887","memberId":"cm0000002088942473903887","memberStatus":"FREE","open":true,"originBossId":"2088942473903887","price":10,"training":false},{"currentBossId":"2088942444232903","memberId":"cm0000002088942444232903","memberStatus":"FREE","open":true,"originBossId":"2088942444232903","price":10,"training":false},{"currentBossId":"2088942471964523","memberId":"cm0000002088942471964523","memberStatus":"FREE","open":true,"originBossId":"2088942471964523","price":10,"training":false},{"currentBossId":"2088942473545669","memberId":"cm0000002088942473545669","memberStatus":"FREE","open":true,"originBossId":"2088942473545669","price":10,"training":false},{"currentBossId":"2088122935513522","gmtAllowTrade":1754064000000,"memberId":"cm0000002088802111521371","memberStatus":"SOLD","notAllowBuyReason":"资金不足","notAllowBuyType":"AMOUNT_NOT_ENOUGH","open":true,"originBossId":"2088802111521371","price":36591,"training":true},{"currentBossId":"2088722171632137","gmtAllowTrade":1754151369643,"memberId":"cm0000002088002795595507","memberStatus":"SOLD","open":true,"originBossId":"2088002795595507","price":23137,"training":true},{"currentBossId":"2088942403520113","gmtAllowTrade":1755964800000,"memberId":"cm0000002088722171632137","memberStatus":"SOLD","open":true,"originBossId":"2088722171632137","price":14415,"training":false},{"currentBossId":"2088722171632137","gmtAllowTrade":1754236800000,"memberId":"cm0000002088102087365187","memberStatus":"SOLD","open":true,"originBossId":"2088102087365187","price":14191,"training":true},{"currentBossId":"2088042666275750","gmtAllowTrade":1699545600000,"memberId":"cm0000002088002162376481","memberStatus":"SOLD","open":true,"originBossId":"2088002162376481","price":13561,"training":false},{"currentBossId":"2088002494531259","gmtAllowTrade":1756546944236,"memberId":"cm0000002088702210650205","memberStatus":"SOLD","open":true,"originBossId":"2088702210650205","price":10607,"training":false},{"currentBossId":"2088942406287310","gmtAllowTrade":1754064000000,"memberId":"cm0000002088342654219820","memberStatus":"SOLD","open":true,"originBossId":"2088342654219820","price":7135,"training":false}],"haveMore":false,"totalSize":0},"resultCode":"SUCCESS","resultDesc":"成功","success":true}
    //public static String queryMemberPriceRanking() {
    //   String args = "[{\"apiVersion\":\"energy\",\"buyMember\":true,\"chInfo\":\"healthstep\"}]";
    //  return ApplicationHook.requestString("alipay.antsports.club.ranking.queryMemberPriceRanking", args);
    //}

    //alipay.antsports.club.trade.queryClubMember
    //[{"apiVersion":"energy","chInfo":"healthstep","memberId":"cm0000002088842214318540","originBossId":"2088842214318540"}]}
    public static String queryClubMember(String memberId, String originBossId) {
        return ApplicationHook.requestString("alipay.antsports.club.trade.queryClubMember",
                "[{\"apiVersion\":\"energy\",\"chInfo\":\"healthstep\",\"memberId\":\"" + memberId + "\",\"originBossId\":\"" + originBossId + "\"}]");
    }

    public static String buyMember(String currentBossId, String memberId, String originBossId, JSONObject priceInfo, String roomId) {
        String requestData = "[{\"apiVersion\":\"energy\",\"chInfo\":\"healthstep\",\"currentBossId\":\"" + currentBossId + "\",\"memberId\":\"" + memberId + "\",\"originBossId\":\"" + originBossId + "\",\"priceInfo\":" + priceInfo + ",\"roomId\":\"" + roomId + "\"}]";
        return ApplicationHook.requestString("alipay.antsports.club.trade.buyMember", requestData);
    }

    // 运动币兑好礼
    public static String queryItemDetail(String itemId) {
        String arg = "[{\"itemId\":\"" + itemId + "\"}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthItemCenterRpc.queryItemDetail", arg);
    }

    public static String exchangeItem(String itemId, int coinAmount) {
        String arg = "[{\"coinAmount\":" + coinAmount + ",\"itemId\":\"" + itemId + "\"}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthItemCenterRpc.exchangeItem", arg);
    }

    public static String queryExchangeRecordPage(String exchangeRecordId) {
        String arg = "[{\"exchangeRecordId\":\"" + exchangeRecordId + "\"}]";
        return ApplicationHook.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthItemCenterRpc.queryExchangeRecordPage", arg);
    }
}