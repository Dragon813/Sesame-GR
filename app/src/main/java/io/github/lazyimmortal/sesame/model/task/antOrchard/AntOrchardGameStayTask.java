package io.github.lazyimmortal.sesame.model.task.antOrchard;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.Status;
import io.github.lazyimmortal.sesame.util.TimeUtil;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

/**
 * 农场完成游戏时长任务: 扫描农场乐园悬浮球游戏任务, 极速模拟游玩时长后提交结项领肥料
 */
public final class AntOrchardGameStayTask {
    private static final String DONE_FLAG_PREFIX = "Orchard::gameStay_";
    private static final Random RANDOM = new Random();
    private static final String SOURCE = "lianyun_nc_sydb";
    private static final String TAG = "农场游戏🎮";

    private AntOrchardGameStayTask() {
    }

    public static void execute() {
        try {
            List<StayTaskInfo> taskList = new ArrayList<>();
            Log.i(TAG, "正在扫描农场游戏时长任务");
            scanIndexFeeds(taskList);
            scanIndexDelivery(taskList);
            if (taskList.isEmpty()) {
                Log.i(TAG, "未发现待执行的农场游戏时长任务");
                return;
            }
            int finishedCount = 0;
            int totalManure = 0;
            for (StayTaskInfo taskInfo : taskList) {
                if (Status.hasFlagToday(DONE_FLAG_PREFIX + taskInfo.appId)) {
                    continue;
                }
                Log.i(TAG, "发现任务: [" + taskInfo.name + "] (" + taskInfo.appId + ") | 时长: " + taskInfo.duration + "s");
                int manure = runSingleGameTask(taskInfo);
                if (manure >= 0) {
                    Status.flagToday(DONE_FLAG_PREFIX + taskInfo.appId, UserIdMap.getCurrentUid());
                    finishedCount++;
                    totalManure += manure;
                }
                TimeUtil.sleep(1000);
            }
            if (finishedCount > 0) {
                Log.farm(TAG + "本次共完成" + finishedCount + "个任务共获得肥料" + totalManure + "g");
            } else {
                Log.i(TAG, "本次扫描的所有任务均已在之前完成");
            }
        } catch (Throwable t) {
            Log.i(TAG, "execute err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /* 扫描农场首页信息流中的悬浮球游戏任务 */
    private static void scanIndexFeeds(List<StayTaskInfo> taskList) {
        try {
            String response = requestString("com.alipay.antorchard.indexFeeds", "[" + buildIndexFeedsBody(1) + "]");
            JSONArray feedsList = new JSONObject(response).optJSONArray("feedsInfoList");
            if (feedsList == null || feedsList.length() == 0) {
                return;
            }
            for (int i = 0; i < feedsList.length(); i++) {
                JSONObject feedJo = feedsList.optJSONObject(i);
                if (feedJo == null || !"game".equals(feedJo.optString("itemType"))) {
                    continue;
                }
                JSONObject gameItem = feedJo.optJSONObject("gameItem");
                JSONObject itemOutExtMap = feedJo.optJSONObject("itemOutExtMap");
                if (gameItem == null || itemOutExtMap == null) {
                    continue;
                }
                String benefitInfo = itemOutExtMap.optString("orchardBenefitInfo");
                if (benefitInfo.length() == 0) {
                    continue;
                }
                JSONObject benefitJo = new JSONObject(benefitInfo);
                if ("FLOAT_BALL_TASK".equals(benefitJo.optString("benefitType"))) {
                    JSONObject bizInfo = benefitJo.optJSONObject("bizInfo");
                    addTask(taskList, gameItem.optString("gameItemId"), gameItem.optString("gameName", "未知游戏"),
                            benefitJo.optString("iepTaskId"), benefitJo.optString("iepSceneCode"),
                            bizInfo == null ? 60 : bizInfo.optInt("floatBallDuration", 60));
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "scanIndexFeeds err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /* 扫描农场首页投放位中的悬浮球游戏任务 */
    private static void scanIndexDelivery(List<StayTaskInfo> taskList) {
        try {
            String response = requestString("com.alipay.antorchard.orchardIndexDelivery", "[{\"commonDegradeResult\":{\"deviceLevel\":\"high\",\"resultReason\":0,\"resultType\":0},\"enableTeamType\":[\"team\"],\"inTeam\":true,\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"teamPositionSwitch\":true,\"teamType\":\"help\",\"version\":\"20260721.01\"}]");
            JSONArray deliveryList = new JSONObject(response).optJSONArray("indexDeliveryList");
            if (deliveryList == null) {
                return;
            }
            for (int i = 0; i < deliveryList.length(); i++) {
                JSONObject deliveryJo = deliveryList.optJSONObject(i);
                if (deliveryJo == null) {
                    continue;
                }
                JSONObject benefitJo = deliveryJo.optJSONObject("deliveryBenefitInfo");
                if (benefitJo == null || !"FLOAT_BALL_TASK".equals(benefitJo.optString("benefitType"))) {
                    continue;
                }
                JSONObject displayInfo = deliveryJo.optJSONObject("deliveryDisplayInfo");
                String name = displayInfo != null ? displayInfo.optString("btnText", "未知游戏") : "未知游戏";
                JSONObject bizInfo = benefitJo.optJSONObject("bizInfo");
                addTask(taskList, deliveryJo.optString("contentId"), name,
                        benefitJo.optString("iepTaskId"), benefitJo.optString("iepSceneCode"),
                        bizInfo == null ? 60 : bizInfo.optInt("floatBallDuration", 60));
            }
        } catch (Throwable t) {
            Log.i(TAG, "scanIndexDelivery err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static String buildIndexFeedsBody(int pageNum) throws Exception {
        JSONObject body = new JSONObject();
        body.put("pageNum", pageNum);
        body.put("requestType", "NORMAL");
        body.put("sceneCode", "ORCHARD");
        body.put("source", "ch_appcenter__chsub_9patch");
        body.put("version", "20260721.01");
        JSONArray filterItemList = new JSONArray();
        JSONObject qianyiFilter = new JSONObject();
        qianyiFilter.put("itemType", "qianyi");
        qianyiFilter.put("itemIdList", new JSONArray());
        filterItemList.put(qianyiFilter);
        JSONObject gameFilter = new JSONObject();
        gameFilter.put("itemType", "game");
        gameFilter.put("itemIdList", new JSONArray());
        filterItemList.put(gameFilter);
        body.put("filterItemList", filterItemList);
        JSONObject degradeResult = new JSONObject();
        degradeResult.put("deviceLevel", "high");
        degradeResult.put("resultReason", 0);
        degradeResult.put("resultType", 0);
        body.put("commonDegradeResultVO", degradeResult);
        return body.toString();
    }

    /* 去重添加任务: 缺taskId/sceneCode或当天已完成或已加入列表的跳过, 时长最少30秒 */
    private static void addTask(List<StayTaskInfo> taskList, String appId, String name, String taskId, String sceneCode, int duration) {
        if (appId == null || appId.length() == 0 || taskId == null || taskId.length() == 0
                || Status.hasFlagToday(DONE_FLAG_PREFIX + appId)) {
            return;
        }
        for (StayTaskInfo existing : taskList) {
            if (appId.equals(existing.appId)) {
                return;
            }
        }
        taskList.add(new StayTaskInfo(appId, name, taskId, sceneCode, Math.max(duration, 30)));
    }

    /* 单个游戏任务: 进游戏 -> 上报事件 -> 极速模拟时长 -> 提交结项, 返回肥料g数, 失败返回-1 */
    private static int runSingleGameTask(StayTaskInfo taskInfo) {
        try {
            requestString("com.alipay.antorchard.noticeGame", "[{\"appId\":\"" + taskInfo.appId + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"20260721.01\"}]");
            submitUserAction(taskInfo.appId, "enterGame");
            TimeUtil.sleep(500);
            submitEvent(taskInfo.appId, "GAME_FIRST_FRAME", 0, "paladinx_auto");
            TimeUtil.sleep(500);
            submitEvent(taskInfo.appId, "loading_completed", 0, null);
            int duration = Math.max(taskInfo.duration, 30);
            int waitSeconds = RANDOM.nextInt(2) + 3;
            Log.i(TAG, "[" + taskInfo.name + "]正在极速模拟游玩 (预计等待" + waitSeconds + "s)");
            long waitMillis = waitSeconds * 1000L;
            TimeUtil.sleep(waitMillis);
            submitDuration(taskInfo.appId, duration);
            submitEvent(taskInfo.appId, "game_play", waitMillis, null);
            Log.i(TAG, "[" + taskInfo.name + "]极速游玩已完成，正在提交结项");
            JSONObject finishBody = new JSONObject();
            finishBody.put("outBizNo", taskInfo.taskId + "_" + System.currentTimeMillis() + "_" + RANDOM.nextInt(1000));
            finishBody.put("requestType", "NORMAL");
            finishBody.put("sceneCode", taskInfo.sceneCode);
            finishBody.put("source", "ch_appcenter__chsub_9patch");
            finishBody.put("taskType", taskInfo.taskId);
            finishBody.put("userId", "");
            finishBody.put("version", "0.1.2411251623.29");
            String finishResponse = requestString("com.alipay.antiep.finishTask", "[" + finishBody + "]");
            if (finishResponse == null || finishResponse.length() == 0) {
                Log.record(TAG + "[" + taskInfo.name + "]任务结项返回为空");
                return -1;
            }
            JSONObject finishJo = new JSONObject(finishResponse);
            if (!finishJo.optBoolean("success") && !"100000000".equals(finishJo.optString("code"))) {
                Log.record(TAG + "[" + taskInfo.name + "]任务结项失败: " + finishJo.optString("desc"));
                return -1;
            }
            JSONObject awardVo = finishJo.optJSONObject("finishAwardResultVO");
            int manure = awardVo != null ? awardVo.optInt("deltaAwardCount", 0) : 0;
            Log.farm(TAG + "完成[" + taskInfo.name + "]任务获得肥料" + manure + "g");
            return manure;
        } catch (Throwable t) {
            Log.i(TAG, "[" + taskInfo.name + "]执行异常:");
            Log.printStackTrace(TAG, t);
            return -1;
        }
    }

    /* 上报游戏中心用户行为(enterGame等) */
    private static void submitUserAction(String appId, String actionCode) {
        try {
            JSONObject body = new JSONObject();
            body.put("actionCode", actionCode);
            body.put("gameId", appId);
            body.put("paladinxVersion", "2.1.95");
            body.put("source", "gameFramework");
            requestString("com.alipay.gamecenteruprod.biz.rpc.v3.submitUserAction", "[" + body + "]");
        } catch (Throwable t) {
            Log.i(TAG, "submitUserAction err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /* 上报游戏时长(极速模拟游玩) */
    private static void submitDuration(String appId, int playTime) {
        try {
            JSONObject body = new JSONObject();
            body.put("gameAppId", appId);
            body.put("playTime", playTime);
            body.put("source", SOURCE);
            body.put("statisticTag", "");
            requestString("com.alipay.gamecenteruprod.biz.rpc.v3.submitUserPlayDurationAction", "[" + body + "]");
        } catch (Throwable t) {
            Log.i(TAG, "submitDuration err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /* 上报游戏事件(首帧/加载完成/游玩), 补充农场乐园场景属性 */
    private static void submitEvent(String appId, String eventId, long elapsedTime, String source) {
        try {
            JSONObject eventAttrMap = new JSONObject();
            eventAttrMap.put("CH_INFO", SOURCE);
            eventAttrMap.put("CPS_ID", "unknown");
            eventAttrMap.put("GAME_VERSION", "6.5.72");
            eventAttrMap.put("PALADINX_VERSION", "2.1.95");
            eventAttrMap.put("PLAY_SCENE", "NORMAL");
            eventAttrMap.put("SCENE_ID", "nongchangleyuan");
            if ("GAME_FIRST_FRAME".equals(eventId)) {
                eventAttrMap.put("ALIVE_ENTER", "0");
            }
            if (elapsedTime > 0) {
                eventAttrMap.put("GAME_ELAPASED_TIME", elapsedTime);
            }
            JSONObject body = new JSONObject();
            body.put("appId", appId);
            body.put("eventAttrMap", eventAttrMap);
            body.put("eventId", eventId);
            body.put("idempotentNo", "platform_" + System.currentTimeMillis() + "_" + RANDOM.nextInt(100000) + "_" + RANDOM.nextInt(10));
            if (source == null) {
                source = "loading_completed".equals(eventId) ? "my.reportLoadingCompleted" : "my.reportGamePlay";
            }
            body.put("source", source);
            requestString("com.alipay.gameevent.biz.rpc.submitEvent", "[" + body + "]");
        } catch (Throwable t) {
            Log.i(TAG, "submitEvent err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static String requestString(String method, String data) {
        String response = ApplicationHook.requestString(method, data);
        return response == null ? "" : response;
    }

    private static final class StayTaskInfo {
        final String appId;
        final int duration;
        final String name;
        final String sceneCode;
        final String taskId;

        StayTaskInfo(String appId, String name, String taskId, String sceneCode, int duration) {
            this.appId = appId;
            this.name = name;
            this.taskId = taskId;
            this.sceneCode = sceneCode;
            this.duration = duration;
        }
    }
}
