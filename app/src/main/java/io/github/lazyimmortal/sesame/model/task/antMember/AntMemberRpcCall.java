package io.github.lazyimmortal.sesame.model.task.antMember;

import org.json.JSONObject;

import io.github.lazyimmortal.sesame.entity.RpcEntity;
import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.util.RandomUtil;

public class AntMemberRpcCall {
    
    private static String getUniqueId() {
        return String.valueOf(System.currentTimeMillis()) + RandomUtil.nextLong();
    }
    
    public static Boolean check() {
        RpcEntity rpcEntity = ApplicationHook.requestObject("alipay.antmember.biz.rpc.member.h5.queryPointCert", "[{\"page\":" + 1 + ",\"pageSize\":" + 8 + "}]", 1, 0);
        return rpcEntity != null && !rpcEntity.getHasError();
    }
    
    /* ant member point */
    public static String queryPointCert(int page, int pageSize) {
        String args1 = "[{\"page\":" + page + ",\"pageSize\":" + pageSize + "}]";
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.member.h5.queryPointCert", args1);
    }
    
    public static String receivePointByUser(String certId) {
        String args1 = "[{\"certId\":" + certId + "}]";
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.member.h5.receivePointByUser", args1);
    }
    
    public static String queryMemberSigninCalendar() {
        return ApplicationHook.requestString("com.alipay.amic.biz.rpc.signin.h5.queryMemberSigninCalendar", "[{\"autoSignIn\":true,\"invitorUserId\":\"\",\"sceneCode\":\"QUERY\"}]");
    }
    
    /* 会员任务 */
    public static String signPageTaskList() {
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.membertask.h5.signPageTaskList", "[{\"sourceBusiness\":\"antmember\",\"spaceCode\":\"ant_member_xlight_task\"}]");
    }
    
    public static String applyTask(String darwinName, Long taskConfigId) {
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.membertask.h5.applyTask", "[{\"darwinExpParams\":{\"darwinName\":\"" + darwinName + "\"},\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"myTab\",\"unid\":\"\"},\"taskConfigId\":" + taskConfigId + "}]");
    }
    
    public static String executeTask(String bizParam, String bizSubType) {
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.membertask.h5.executeTask", "[{\"bizOutNo\":\"" + (System.currentTimeMillis() - 16000L) + "\",\"bizParam\":\"" + bizParam + "\",\"bizSubType\":\"" + bizSubType + "\",\"bizType\":\"BROWSE\"}]");
    }
    
    public static String queryAllStatusTaskList() {
        String args = "[{\"sourceBusiness\":\"signInAd\"}]";
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.membertask.h5.queryAllStatusTaskList", args);
    }
    
    /**
     * 黄金票收取
     *
     * @param str signInfo
     * @return 结果
     */
    public static String goldBillCollect(String str) {
        return ApplicationHook.requestString("com.alipay.wealthgoldtwa.goldbill.v2.index.collect", "[{" + str + "\"trigger\":\"Y\"}]");
    }
    
    /**
     * 游戏中心签到查询
     */
    public static String querySignInBall() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.querySignInBall", "[{\"source\":\"ch_appcenter__chsub_9patch\"}]");
    }
    
    /**
     * 游戏中心签到
     */
    public static String continueSignIn() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.continueSignIn", "[{\"sceneId\":\"GAME_CENTER\",\"signType\":\"NORMAL_SIGN\",\"source\":\"ch_appcenter__chsub_9patch\"}]");
    }
    
    /**
     * 游戏中心查询待领取乐豆列表
     */
    public static String queryPointBallList() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.queryPointBallList", "[{\"source\":\"ch_appcenter__chsub_9patch\"}]");
    }
    
    /**
     * 游戏中心全部领取
     */
    public static String batchReceivePointBall() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.batchReceivePointBall", "[{}]");
    }
    
    /**
     * 查询可收取的芝麻粒
     *
     * @return 结果
     */
    public static String queryCreditFeedback() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.queryCreditFeedback", "[{\"queryPotential\":false,\"size\":20,\"status\":\"UNCLAIMED\"}]");
    }
    
    /**
     * 芝麻信用首页
     *
     * @return 结果
     */
    public static String queryHome() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.home.api.HomeV6RpcManager.queryHome", "[{\"miniZmGrayInside\":\"\"}]");
    }
    
    /**
     * 收取芝麻粒
     *
     * @param creditFeedbackId creditFeedbackId
     * @return 结果
     */
    //{"chInfo":"ch_zhimahome__chsub_zml_doudi","deliverStatus":"","deliveryTemplateId":"","sceneCode":"DAILY_MUST_DO_CARD","searchAddToHomeTask":true,"searchGuidePopFlag":true,"searchShareAssistTask":true,"searchSubscribeTask":true,"version":"new"}]}
    public static String CreditAccumulateStrategyRpcManager(String creditFeedbackId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3", "[{\"chInfo\":\"ch_zhimahome__chsub_zml_doudi\",\"deliverStatus\":\"\",\"deliveryTemplateId\":\"\",\"sceneCode\":\"DAILY_MUST_DO_CARD\"," +
                                                                                                                                                     "\"searchAddToHomeTask\":true,\"searchGuidePopFlag\":true,\"searchShareAssistTask\":true,\"searchSubscribeTask\":true,\"version\":\"new\"}]");
    }
    
    public static String collectCreditFeedback(String creditFeedbackId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.collectCreditFeedback", "[{\"collectAll\":false,\"creditFeedbackId\":\"" + creditFeedbackId + "\",\"status\":\"UNCLAIMED\"}]");
    }
    
    /**
     * 查询生活记录
     *
     * @return 结果
     */
    public static String promiseQueryHome() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.queryHome", null);
    }
    
    public static String querySingleTemplate(String templateId) {
        String args = "[{\"templateId\":\"" + templateId + "\"}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.querySingleTemplate", args);
    }
    
    public static String promiseJoin(JSONObject data) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.join", "[" + data + "]");
    }
    
    /**
     * 查询生活记录明细
     *
     * @param recordId recordId
     * @return 结果
     */
    public static String promiseQueryDetail(String recordId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.queryDetail", "[{\"recordId\":\"" + recordId + "\"}]");
    }
    
    /**
     * 查询会员积分兑换福利列表方法1
     *
     * @param userId     userId
     * @param deliveryId 分类码
     *                   94000SR2023102305988003: 0元起
     *                   94000SR2024011106752003: 0元起/公益道具
     *                   94000SR2024071108523003: 0元起/皮肤
     *                   94000SR2024071808609003: 皮肤
     * @return 分类下商品列表
     * @ param naviCode 导航分类码
     * 皮肤："bb82b"、0元起："全积分"、影音："13"
     */
    public static String queryDeliveryZoneDetail(String userId, String deliveryId) {
        String uniqueId = System.currentTimeMillis() + "全积分0and99999999INTELLIGENT_SORT" + userId;
        String args = "[{\"cityCode\":\"\",\"deliveryId\":\"" + deliveryId + "\",\"pageNum\":1,\"pageSize\":18,\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"myTab\",\"unid\":\"\"},\"topIdList\":[],\"uniqueId\":\"" + uniqueId + "\"}]";
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.config.h5.queryDeliveryZoneDetail", args);
    }
    
    /**
     * 查询会员积分兑换福利列表方法2
     *
     * @param userId   userId
     * @param naviCode 导航分类码
     *                 特色："14"、出行："1"、美食："11"、日用："12"、上新：""
     * @return 分类下商品列表
     */
    public static String queryIndexNaviBenefitFlowV2(String userId, String naviCode) {
        String sortStrategy = "INTELLIGENT_SORT";
        String upperPoint = "99999999";
        String uniqueId = System.currentTimeMillis() + naviCode + "0and" + upperPoint + sortStrategy + userId;
        String args =
                "[\n" + "        {\n" + "            \"adCopyId\": \"\",\n" + "            \"benefitFlowSource\": \"REC\",\n" + "            \"cityCode\": \"\",\n" + "            \"excludeIds\": \"\",\n" + "            \"exposeChannel\": \"antmember\",\n" + "            \"fastTag\": \"\"," + "\n" + "            \"lowerPoint\": 0,\n" + "            \"naviCode\": \"" + naviCode + "\",\n" + "            \"pageNum\": 1,\n" + "            \"pageSize\": 50,\n" + "            \"requestSourceInfo\": \"-|feeds\",\n" + "            \"sortStrategy\": \"" + sortStrategy + "\",\n" + "            \"sourcePassMap\": {\n" + "                \"innerSource\": \"\",\n" + "                \"source\": \"myTab\",\n" + "                \"unid\": \"\"\n" + "            },\n" + "            \"stickyIdList\": [],\n" + "            \"tagCodeIdx\": -1,\n" + "            \"uniqueId\": \"" + uniqueId + "\",\n" + "            \"upperPoint\": " + upperPoint + ",\n" + "            \"withPointRange\": false\n" + "        }\n" + "    ]";
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.config.h5.queryIndexNaviBenefitFlowV2", args);
    }
    
    /**
     * 会员积分兑换福利
     *
     * @param benefitId benefitId
     * @param itemId    itemId
     * @return 结果
     */
    public static String exchangeBenefit(String benefitId, String itemId) {
        String requestId = "requestId" + System.currentTimeMillis();
        String alipayClientVersion = ApplicationHook.getAlipayVersion().getVersionString();
        String args =
                "[{\"benefitId\":\"" + benefitId + "\",\"cityCode\":\"\",\"exchangeType\":\"POINT_PAY\",\"itemId\":\"" + itemId + "\",\"miniAppId\":\"\",\"orderSource\":\"\",\"requestId\":\"" + requestId + "\",\"requestSourceInfo\":\"\",\"sourcePassMap\":{\"alipayClientVersion\":\"" + alipayClientVersion + "\",\"innerSource\":\"\",\"mobileOsType\":\"Android\",\"source\":\"\",\"unid\":\"\"},\"userOutAccount\":\"\"}]";
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.exchange.h5.exchangeBenefit", args);
    }
    
    // 我的快递任务
    public static String queryRecommendTask() {
        String args1 = "[{\"consultAccessFlag\":true,\"extInfo\":{\"componentCode\":\"musi_test\"},\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iCCWtq%2FRt4kh\"}]";
        return ApplicationHook.requestString("alipay.promoprod.task.listQuery", args1);
    }
    
    // 积分、肥料
    public static String trigger(String appletId) {
        String args1 = "[{\"appletId\":\"" + appletId + "\",\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iNRgSSvWpCuA\",\"stageCode\":\"send\"}]";
        return ApplicationHook.requestString("alipay.promoprod.applet.trigger", args1);
    }
    
    // 森林活力值
    public static String queryforestHomePage() {
        String args1 = "[{\"activityParam\":{},\"configVersionMap\":{\"wateringBubbleConfig\":\"0\"},\"skipWhackMole\":false,\"source\":\"kuaidivitality\",\"version\":\"20240606\"}]";
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryHomePage", args1);
    }
    
    public static String forestTask() {
        String args1 = "[{\"extend\":{\"firstTaskType\":\"KUAIDI_VITALITY\"},\"fromAct\":\"home_task_list\",\"source\":\"kuaidivitality\",\"version\":\"20240105\"}]";
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryTaskList", args1);
    }
    
    public static String forestreceiveTaskAward() {
        String args1 = "[{\"ignoreLimit\":false,\"requestType\":\"H5\",\"sceneCode\":\"ANTFOREST_VITALITY_TASK\",\"source\":\"ANTFOREST\",\"taskType\":\"KUAIDI_VITALITY\"}]";
        return ApplicationHook.requestString("com.alipay.antiep.receiveTaskAward", args1);
    }
    
    // 海洋碎片
    public static String queryoceanHomePage() {
        String args1 = "[{\"firstTaskType\":\"DAOLIU_WODEKUAIDIQUANYI\",\"source\":\"wodekuaidiquanyi\",\"uniqueId\":\"" + getUniqueId() + "\",\"version\":\"20240115\"}]";
        return ApplicationHook.requestString("alipay.antocean.ocean.h5.queryHomePage", args1);
    }
    
    public static String oceanTask() {
        String args1 = "[{\"extend\":{\"firstTaskType\":\"DAOLIU_WODEKUAIDIQUANYI\"},\"fromAct\":\"dynamic_task\",\"sceneCode\":\"ANTOCEAN_TASK\",\"source\":\"wodekuaidiquanyi\",\"uniqueId\":\"" + getUniqueId() + "\",\"version\":\"20240115\"}]";
        return ApplicationHook.requestString("alipay.antocean.ocean.h5.queryTaskList", args1);
    }
    
    public static String oceanreceiveTaskAward() {
        String args1 = "[{\"ignoreLimit\":false,\"requestType\":\"RPC\",\"sceneCode\":\"ANTOCEAN_TASK\",\"source\":\"ANT_FOREST\",\"taskType\":\"DAOLIU_WODEKUAIDIQUANYI\",\"uniqueId\":\"" + getUniqueId() + "\"}]";
        return ApplicationHook.requestString("com.alipay.antiep.receiveTaskAward", args1);
    }
    
    // 普通任务
    public static String queryOrdinaryTask() {
        String args1 = "[{\"consultAccessFlag\":true,\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iNRgSSvWpCuA\"}]";
        return ApplicationHook.requestString("alipay.promoprod.task.listQuery", args1);
    }
    
    public static String signuptrigger(String appletId) {
        String args1 = "[{\"appletId\":\"" + appletId + "\",\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iNRgSSvWpCuA\",\"stageCode\":\"signup\"}]";
        return ApplicationHook.requestString("alipay.promoprod.applet.trigger", args1);
    }
    
    public static String sendtrigger(String appletId) {
        String args1 = "[{\"appletId\":\"" + appletId + "\",\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iNRgSSvWpCuA\",\"stageCode\":\"send\"}]";
        return ApplicationHook.requestString("alipay.promoprod.applet.trigger", args1);
    }
    
    // 消费金签到
    public static String signinCalendar() {
        return ApplicationHook.requestString("alipay.mobile.ipsponsorprod.consume.gold.task.signin.calendar", "[{}]");
    }
    
    public static String openBoxAward() {
        return ApplicationHook.requestString("alipay.mobile.ipsponsorprod.consume.gold.task.openBoxAward", "[{\"actionAwardDetails\":[{\"actionType\":\"date_sign_start\"}],\"bizType\":\"CONSUME_GOLD\",\"boxType\":\"CONSUME_GOLD_SIGN_DATE\",\"clientVersion\":\"6.3.0\",\"timeScaleType\":0," +
                                                                                                           "\"userType\":\"new\"}]");
    }
    
    /**
     * 芝麻签到 - 通用完成接口（芝麻粒/炼金等）
     * 对应: com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.completeTask
     *
     * @param checkInDate yyyyMMdd
     * @param sceneCode   "zml" 对应芝麻粒福利签到, "alchemy" 对应芝麻炼金签到
     */
    public static String zmCheckInCompleteTask(String checkInDate, String sceneCode) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.completeTask", "[{\"checkInDate\":\"" + checkInDate + "\",\"sceneCode\":\"" + sceneCode + "\"}]");
    }
    
    /**
     * 获取芝麻信用任务列表
     */
    public static String CreditAccumulateStrategyRpcManager() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3", "[{}]");
    }
    
    /**
     * 芝麻信用领取任务
     */
    public static String joinSesameTask(String taskTemplateId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.joinActivity", "[{\"chInfo\":\"seasameList\",\"joinFromOuter\":false,\"templateId\":\"" + taskTemplateId + "\"}]");
    }
    
    /**
     * 芝麻信用获取任务回调
     */
    public static String feedBackSesameTask(String taskTemplateId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.taskFeedback", "[{\"actionType\":\"TO_COMPLETE\",\"templateId\":\"" + taskTemplateId + "\"}]");
    }
    
    /**
     * 芝麻信用完成任务
     */
    public static String finishSesameTask(String recordId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.pushActivity", "[{\"recordId\":\"" + recordId + "\"}]");
    }
    
    /**
     * 查询可收取的芝麻粒
     */
    
    /**
     * 一键收取芝麻粒
     */
    public static String collectAllCreditFeedback() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.collectCreditFeedback", "[{\"collectAll\":true,\"status\":\"UNCLAIMED\"}]");
    }
    
    public static String alchemyQueryCheckIn(String scenecode) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.queryTaskLists", "[{\"sceneCode\":\"" + scenecode + "\",\"version\":\"2025-10-22\"}]");
    }
}
