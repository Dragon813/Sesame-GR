package io.github.lazyimmortal.sesame.model.task.antMember;

import org.json.JSONObject;

import io.github.lazyimmortal.sesame.hook.ApplicationHook;

public class AntInsuranceRpcCall {
    /**
     * 查询待领取的保障金
     *
     * @return 结果
     */
    public static String queryMultiSceneWaitToGainList() {
        String args = "[{\"entrance\":\"jkj_zhima_dairy66\"," + "\"eventToWaitParamDTO\":{\"giftProdCode\":\"GIFT_UNIVERSAL_COVERAGE\",\"rightNoList\":[\"UNIVERSAL_ACCIDENT\",\"UNIVERSAL_HOSPITAL\",\"UNIVERSAL_OUTPATIENT\",\"UNIVERSAL_SERIOUSNESS\",\"UNIVERSAL_WEALTH\",\"UNIVERSAL_TRANS\",\"UNIVERSAL_FRAUD_LIABILITY\"]}," + "\"helpChildParamDTO\":{\"giftProdCode\":\"GIFT_HEALTH_GOLD_CHILD\",\"rightNoList\":[\"UNIVERSAL_ACCIDENT\",\"UNIVERSAL_HOSPITAL\",\"UNIVERSAL_OUTPATIENT\",\"UNIVERSAL_SERIOUSNESS\",\"UNIVERSAL_WEALTH\",\"UNIVERSAL_TRANS\",\"UNIVERSAL_FRAUD_LIABILITY\"]}," + "\"priorityChannelParamDTO\":{\"giftProdCode\":\"GIFT_UNIVERSAL_COVERAGE\",\"rightNoList\":[\"UNIVERSAL_ACCIDENT\",\"UNIVERSAL_HOSPITAL\",\"UNIVERSAL_OUTPATIENT\",\"UNIVERSAL_SERIOUSNESS\",\"UNIVERSAL_WEALTH\",\"UNIVERSAL_TRANS\",\"UNIVERSAL_FRAUD_LIABILITY\"]}," + "\"signInParamDTO\":{\"giftProdCode\":\"GIFT_UNIVERSAL_COVERAGE\",\"rightNoList\":[\"UNIVERSAL_ACCIDENT\",\"UNIVERSAL_HOSPITAL\",\"UNIVERSAL_OUTPATIENT\",\"UNIVERSAL_SERIOUSNESS\",\"UNIVERSAL_WEALTH\",\"UNIVERSAL_TRANS\",\"UNIVERSAL_FRAUD_LIABILITY\"]}}]";
        return ApplicationHook.requestString("com.alipay.insgiftbff.insgiftMain.queryMultiSceneWaitToGainList", args);
    }

    /**
     * 领取保障金
     *
     * @return 结果
     */
    public static String gainMyAndFamilySumInsured(JSONObject data) {
        String args = "[" + data + "]";
        return ApplicationHook.requestString("com.alipay.insgiftbff.insgiftMain.gainMyAndFamilySumInsured", args);
    }

    // 天天领保障福利
    public static String queryAvailableNum() {
        String args = "[{\"planId\":\"INSP10723155\",\"scene\":\"INSIOP_BUILD_SCENE_1000100100192_@alipay/insiop-lottery-image-draw\"}]";
        return ApplicationHook.requestString("com.alipay.insmarketingbff.lottery.queryAvailableNum", args);
    }

    public static String lotteryDraw() {
        String args = "[{\"extParams\":{\"componentType\":\"insiop-lottery-draw-image\"},\"planId\":\"INSP10723155\",\"scene\":\"INSIOP_BUILD_SCENE_1000100100192_@alipay/insiop-lottery-image-draw\"}]";
        return ApplicationHook.requestString("com.alipay.insmarketingbff.lottery.draw", args);
    }

    // 安心豆
    public static String queryUserAccountInfo(String pointProdCode) {
        String args = "[{\"channel\":\"HiChat\",\"pointProdCode\":\"" + pointProdCode + "\",\"pointUnitType\":\"COUNT\"}]";
        return ApplicationHook.requestString("com.alipay.insmarketingbff.point.queryUserAccountInfo", args);
    }

    public static String oneStopPlanTriggerExchangeDetail(String planCode, String itemId) {
        String args = "[{\"extParams\":{\"itemId\":\"" + itemId + "\"},\"planCode\":\"" + planCode + "\",\"planOperateCode\":\"exchangeDetail\"}]";
        return ApplicationHook.requestString("com.alipay.insmarketingbff.onestop.planTrigger", args);
    }

    public static String oneStopPlanTriggerExchange(String planCode, String itemId, int pointAmount) {
        String args = "[{\"extParams\":{\"itemId\":\"" + itemId + "\",\"pointAmount\":\"" + pointAmount + "\"},\"planCode\":\"" + planCode + "\",\"planOperateCode\":\"exchange\"}]";
        return ApplicationHook.requestString("com.alipay.insmarketingbff.onestop.planTrigger", args);
    }

    public static String querySignInProcess(String appletId, String scene) {
        String args = "[{\"appletId\":\"" + appletId + "\",\"scene\":\"" + scene + "\"}]";
        return ApplicationHook.requestString("com.alipay.insmarketingbff.bean.querySignInProcess", args);
    }

    public static String beanQuerySignInProcess() {
        return querySignInProcess("AP16242232", "INS_BLUE_BEAN_SIGN");
    }

    public static String beanSignInTrigger() {
        String args = "[{\"appletId\":\"AP16242232\",\"scene\":\"INS_BLUE_BEAN_SIGN\"}]";
        return ApplicationHook.requestString("com.alipay.insmarketingbff.bean.signInTrigger", args);
    }

    public static String beanExchangeDetail(String itemId) {
        return oneStopPlanTriggerExchangeDetail("bluebean_onestop", itemId);
    }

    public static String beanExchange(String itemId, int pointAmount) {
        return oneStopPlanTriggerExchange("bluebean_onestop", itemId, pointAmount);
    }
}
