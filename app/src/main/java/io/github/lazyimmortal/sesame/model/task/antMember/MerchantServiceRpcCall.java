package io.github.lazyimmortal.sesame.model.task.antMember;

import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.util.RandomUtil;

public class MerchantServiceRpcCall {

    /* 商家服务 */
    public static String transcodeCheck() {
        return ApplicationHook.requestString("alipay.mrchservbase.mrchbusiness.sign.transcode.check", "[{}]");
    }

    // 签到
    private static String zcjViewInvoke(String compId) {
        String args = "[{\"compId\":\"" + compId + "\"}]";
        return ApplicationHook.requestString("alipay.mrchservbase.zcj.view.invoke", args);
    }

    public static String zcjSignInQuery() {
        return zcjViewInvoke("ZCJ_SIGN_IN_QUERY");
    }

    public static String zcjSignInExecute() {
        return zcjViewInvoke("ZCJ_SIGN_IN_EXECUTE");
    }

    public static String taskListQueryV2() {
        return taskListQueryV2("");
    }

    // 签到任务
    public static String taskListQuery() {
        String args = "[{\"compId\":\"ZCJ_TASK_LIST\",\"params\":{\"activityCode\":\"ZCJ\",\"clientVersion\":\"10.3.36\",\"extInfo\":{},\"platform\":\"Android\",\"underTakeTaskCode\":\"\"}}]";
        return ApplicationHook.requestString("alipay.mrchservbase.zcj.taskList.query", args);
    }

    public static String taskListQueryV2(String taskItemCode) {
        String args = "[{\"taskItemCode\":\"" + taskItemCode + "\"}]";
        return ApplicationHook.requestString("alipay.mrchservbase.zcj.taskList.query.v2", args);
    }

    public static String taskFinish(String bizId) {
        String args = "[{\"bizId\":\"" + bizId + "\"}]";
        return ApplicationHook.requestString("com.alipay.adtask.biz.mobilegw.service.task.finish", args);
    }

    public static String taskReceive(String taskCode) {
        String args = "[{\"compId\":\"ZTS_TASK_RECEIVE\",\"extInfo\":{\"taskCode\":\"" + taskCode + "\"}}]";
        return ApplicationHook.requestString("alipay.mrchservbase.sqyj.task.receive", args);
    }

    public static String taskQueryByActionCode(String actionCode) {
        String args = "[{\"actionCode\":\"" + actionCode + "\"}]";
        return ApplicationHook.requestString("alipay.mrchservbase.task.query.by.actioncode", args);
    }

    public static String taskActionProduce(String actionCode) {
        String args = "[{\"actionCode\":\"" + actionCode + "\"}]";
        return ApplicationHook.requestString("alipay.mrchservbase.biz.task.action.produce", args);
    }

    public static String ballQueryV1() {
        return ApplicationHook.requestString("alipay.mrchservbase.mrchpoint.ball.query.v1", "[{}]");
    }

    public static String ballReceive(String ballIds) {
        String args = "[{\"ballIds\":[\"" + ballIds + "\"],\"channel\":\"MRCH_SELF\",\"outBizNo\":\"" + RandomUtil.getRandomUUID() + "\"}]";
        return ApplicationHook.requestString("alipay.mrchservbase.mrchpoint.ball.receive", args);
    }

    // 开门打卡
    public static String KMDKQueryActivity() {
        String args = "[{\"scene\":\"activityCenter\"}]";
        return ApplicationHook.requestString("alipay.merchant.kmdk.query.activity", args);
    }

    public static String KMDKSignIn(String activityNo) {
        String args = "[{\"activityNo\":\"" + activityNo + "\"}]";
        return ApplicationHook.requestString("alipay.merchant.kmdk.signIn", args);
    }

    public static String KMDKSignUp(String activityNo) {
        String args = "[{\"activityNo\":\"" + activityNo + "\"}]";
        return ApplicationHook.requestString("alipay.merchant.kmdk.signUp", args);
    }
}
