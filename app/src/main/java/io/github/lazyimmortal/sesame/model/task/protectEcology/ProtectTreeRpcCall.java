package io.github.lazyimmortal.sesame.model.task.protectEcology;

import org.json.JSONObject;

import io.github.lazyimmortal.sesame.hook.ApplicationHook;

public class ProtectTreeRpcCall {
    private static final String VERSION = "20230501";
    private static final String VERSION2 = "20230522";

    /* 查询地图树苗 */
    public static String queryAreaTrees() {
        return ApplicationHook.requestString("alipay.antmember.forest.h5.queryAreaTrees", "[{}]");
    }

    public static String queryTreeItemsForExchange() {
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryTreeItemsForExchange", "[{}]");
    }

    public static String queryTreeItemsForExchange(String applyActions, String itemTypes) {
        // "applyActions": "LIMITED,NO_STOCK,AVAILABLE,ENERGY_LACK,COMING"
        // "itemTypes": "project,special"
        String args = "[{\"applyActions\":\"" + applyActions + "\",\"itemTypes\":\"" + itemTypes + "\"}]";
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryTreeItemsForExchange", args);
    }

    public static String queryTreeForExchange(Object projectId) {
        String args = "[{\"projectId\":\"" + projectId + "\"}]";
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryTreeForExchange", args);
    }

    public static String exchangeTree(int projectId) {
        String args = "[{\"projectId\":" + projectId + ",\"sToken\":\"" + System.currentTimeMillis() + "\"}]";
        return ApplicationHook.requestString("alipay.antmember.forest.h5.exchangeTree", args);
    }

    public static String applyGoldAnimalCert(int projectId) {
        String args = "[{\"projectId\":\"" + projectId + "\"}]";
        return ApplicationHook.requestString("alipay.antforest.forest.h5.applyGoldAnimalCert", args);
    }

    // 碳中和

    /**
     *
     * @param actionCode actionCode
     *                   marathonHome marathonWater
     *                   carbonHome carbonWater
     * @param activityId activityId
     * @param paramMap   查询时: {"donateQueryActionParam":"marathonWater"}
     *                   捐赠时: {"donateNum":1000,"incrNum":1000}
     * @return String
     */
    public static String doRubickActivity(String actionCode, String activityId, JSONObject paramMap) {
        String args =
                "[{\"actionCode\":\"" + actionCode + "\",\"activityId\":\"" + activityId + "\",\"paramMap\":" + paramMap + ",\"source\":\"forest\"}]";
        return ApplicationHook.requestString("com.alipay.charityactivity.rubick.rpc.h5.doRubickActivity", args);
    }
}
