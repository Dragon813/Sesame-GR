package io.github.dragon813.sesame.model.task.protectEcology;

import io.github.dragon813.sesame.hook.ApplicationHook;
import io.github.dragon813.sesame.util.RandomUtil;

public class ProtectOceanRpcCall {

    private static String getUniqueId() {
        return String.valueOf(System.currentTimeMillis()) + RandomUtil.nextLong();
    }

    /* 保护海洋净滩行动 */
    public static String queryCultivationList() {
        String args = "[{\"source\":\"ANT_FOREST\"}]";
        return ApplicationHook.requestString("alipay.antocean.ocean.h5.queryCultivationList", args);
    }

    public static String queryCultivationDetail(String cultivationCode, String projectCode) {
        // source: ANT_FOREST ANT_FOREST_listExchange
        String args = "[{\"cultivationCode\":\"" + cultivationCode + "\",\"projectCode\":\"" + projectCode + "\",\"source\":\"ANT_FOREST\",\"uniqueId\":\"" + getUniqueId() + "\"}]";
        return ApplicationHook.requestString("alipay.antocean.ocean.h5.queryCultivationDetail", args);
    }

    public static String oceanExchangeTree(String cultivationCode, String projectCode) {
        String args = "[{\"cultivationCode\":\"" + cultivationCode + "\",\"projectCode\":\"" + projectCode + "\",\"source\":\"ANT_FOREST\",\"uniqueId\":\"" + getUniqueId() + "\"}]";
        return ApplicationHook.requestString("alipay.antocean.ocean.h5.exchangeTree", args);
    }
}
