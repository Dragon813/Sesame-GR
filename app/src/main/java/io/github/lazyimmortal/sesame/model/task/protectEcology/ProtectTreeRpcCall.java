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

    /* 能量PK赛季: 查询古树地图(activityId固定pkSeason4, 返回resData.actionData.resultData.protectProgress) */
    public static String pkQueryTreeMap(String seasonActivityId) {
        String args =
                "[{\"actionCode\":\"queryTreeMap\",\"activityId\":\"" + seasonActivityId + "\",\"source\":\"forest\"}]";
        return ApplicationHook.requestString("alipay.antforest.forest.h5.activity.doRubickActivity", args);
    }

    /* 能量PK赛季: 查询关卡首页(actionCode=pkSeason2Home, activityId=pk4Level{N}) */
    public static String pkSeason2Home(String levelActivityId) {
        return doRubickActivity("pkSeason2Home", levelActivityId, new JSONObject());
    }

    /* 能量PK赛季: 浇水保护古树(actionCode=pkSeason2Water, paramMap={"donateNum":N,"incrNum":N}) */
    public static String pkSeason2Water(String levelActivityId, int donateNum) {
        try {
            JSONObject paramMap = new JSONObject();
            paramMap.put("donateNum", donateNum);
            paramMap.put("incrNum", donateNum);
            return doRubickActivity("pkSeason2Water", levelActivityId, paramMap);
        }
        catch (Throwable t) {
            return null;
        }
    }
}
