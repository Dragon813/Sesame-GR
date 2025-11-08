package io.github.lazyimmortal.sesame.model.task.antForest;

import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.util.RandomUtil;

public class GreenLifeRpcCall {

    /* 森林集市 */
    public static String consultForSendEnergyByAction(String sourceType) {
        String args = "[{\"sourceType\":\"" + sourceType + "\"}]";
        return ApplicationHook.requestString("alipay.bizfmcg.greenlife.consultForSendEnergyByAction", args);
    }

    public static String sendEnergyByAction(String sourceType) {
        String args = "[{\"actionType\":\"GOODS_BROWSE\",\"requestId\":\"" + RandomUtil.getRandomString(8) + "\"," +
                "\"sourceType\":\"" + sourceType + "\"}]";
        return ApplicationHook.requestString("alipay.bizfmcg.greenlife.sendEnergyByAction", args);
    }

    // 打卡兑好礼
    public static String retrieveCurrentActivity() {
        String args = "[{}]";
        return ApplicationHook.requestString("alipay.bizfmcg.greenlife.retrieveCurrentActivity", args);
    }

    public static String retrieveHotActivityPrize(String activityId) {
        String args = "[{\"activityId\":\"" + activityId + "\"}]";
        return ApplicationHook.requestString("alipay.bizfmcg.greenlife.retrieveHotActivityPrize", args);
    }

    public static String finishCurrentTask(String taskTemplateId) {
        String args = "[{\"taskTemplateId\":\"" + taskTemplateId + "\"}]";
        return ApplicationHook.requestString("alipay.bizfmcg.greenlife.finishCurrentTask", args);
    }
}
