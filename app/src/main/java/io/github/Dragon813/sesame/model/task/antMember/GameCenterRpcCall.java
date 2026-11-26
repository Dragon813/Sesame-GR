package io.github.Dragon813.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

import io.github.Dragon813.sesame.hook.ApplicationHook;
import io.github.Dragon813.sesame.util.RandomUtil;

/**
 * 游戏中心「赚现金（P2E）」相关 RPC。
 * <p>
 * 全部来自 Sesame-AG 的实测实现，`__git` 与 source 参数按服务端校验要求带上。
 */
public class GameCenterRpcCall {

    /** 游戏中心通用身份串，服务端会校验。 */
    public static final String GAME_CENTER_GIT = "9e159d58cce04c13a";
    /** 首页/签到/平台任务用的来源。 */
    public static final String GAME_CENTER_SOURCE = "ch_appcollect__chsub_my-recentlyUsed";
    /** 赚现金任务页用的来源。 */
    /**
     * 2026-11-19：手点抓包与 AG 成功请求用的都是 ch_appcenter__chsub_9patch；
     * recentUSE 是模块早前凭猜测填的回落值，浮球链路（LIGHT_TRAN_TASK）用它会因子场景不匹配被
     * 判成 CONFIGURATION_ERROR，任务永远停在 SIGNUP_COMPLETED。
     */
    public static final String GAME_CENTER_P2E_SOURCE = "ch_appcenter__chsub_9patch";
    /** 赚现金平台任务活动号。 */
    public static final String ACTIVITY_ID = "P2E_PLATFORM_TASK";

    private static String request(String method, JSONObject args) {
        return ApplicationHook.requestString(method, new JSONArray().put(args).toString());
    }

    /**
     * 按指定 source 与 sessionId 查赚现金任务列表（用于诊断不同 source 下任务容器的差异）。
     * 参数与 Sesame-AG 的 queryGameCenterP2eTaskList 完全一致。
     */
    public static String queryP2eTaskListWithSource(String source, String sessionId) {
        JSONObject args = new JSONObject();
        try {
            args.put("__git", GAME_CENTER_GIT);
            args.put("appId", "2021003125685383");
            args.put("deviceLevel", "high");
            args.put("frontEndVersion", "20260820");
            args.put("p2eVersion", "");
            args.put("sessionId", sessionId);
            args.put("setHeadPanelCheck", true);
            args.put("subscribePanelCheck", true);
            args.put("panelLaunchableCheckMap", new JSONObject().put("SET_HEAD_TASK", true));
            args.put("unityDeviceLevel", "high");
            args.put("source", source);
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.p2e.queryTaskList", args);
    }

    public static String querySignInBall() {
        JSONObject args = new JSONObject();
        try {
            args.put("__git", GAME_CENTER_GIT);
            args.put("source", GAME_CENTER_SOURCE);
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.v3.querySignInBall", args);
    }

    public static String continueSignIn() {
        JSONObject args = new JSONObject();
        try {
            args.put("__git", GAME_CENTER_GIT);
            args.put("sceneId", "GAME_CENTER");
            args.put("signType", "NORMAL_SIGN");
            args.put("source", GAME_CENTER_SOURCE);
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.continueSignIn", args);
    }

    public static String queryGameCenterTaskList() {
        JSONObject args = new JSONObject();
        try {
            args.put("__git", GAME_CENTER_GIT);
            args.put("source", GAME_CENTER_SOURCE);
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.v4.queryTaskList", args);
    }

    public static String doTaskSignup(String taskId) {
        JSONObject args = new JSONObject();
        try {
            args.put("__git", GAME_CENTER_GIT);
            args.put("source", GAME_CENTER_SOURCE);
            args.put("taskId", taskId);
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.v3.doTaskSignup", args);
    }

    public static String doTaskSend(String taskId) {
        JSONObject args = new JSONObject();
        try {
            args.put("__git", GAME_CENTER_GIT);
            args.put("taskId", taskId);
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.v3.doTaskSend", args);
    }

    public static String queryPointBallList() {
        JSONObject args = new JSONObject();
        try {
            args.put("__git", GAME_CENTER_GIT);
            args.put("source", GAME_CENTER_SOURCE);
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.v3.queryPointBallList", args);
    }

    public static String batchReceivePointBall() {
        JSONObject args = new JSONObject();
        try {
            args.put("__git", GAME_CENTER_GIT);
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.v3.batchReceivePointBall", args);
    }

    public static String queryP2eHomePage() {
        return queryP2eHomePage(GAME_CENTER_SOURCE);
    }

    public static String queryP2eHomePage(String source) {
        JSONObject args = new JSONObject();
        try {
            args.put("canAddHome", true);
            args.put("deviceLevel", "high");
            args.put("screenType", 10);
            args.put("source", source);
            args.put("subscribePanelCheck", true);
            args.put("__git", GAME_CENTER_GIT);
            args.put("unityDeviceLevel", "high");
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.p2e.queryHomePage", args);
    }

    public static String queryP2eTaskList(String sessionId) {
        JSONObject args = new JSONObject();
        try {
            JSONObject panel = new JSONObject();
            panel.put("SET_HEAD_TASK", true);
            args.put("deviceLevel", "high");
            args.put("panelLaunchableCheckMap", panel);
            args.put("sessionId", sessionId);
            args.put("setHeadPanelCheck", true);
            args.put("source", GAME_CENTER_P2E_SOURCE);
            args.put("subscribePanelCheck", true);
            args.put("__git", GAME_CENTER_GIT);
            args.put("unityDeviceLevel", "high");
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.p2e.queryTaskList", args);
    }

    public static String reportP2eExposedTasks(JSONArray exposedTaskList) {
        JSONObject args = new JSONObject();
        try {
            args.put("exposedTaskList", exposedTaskList == null ? new JSONArray() : exposedTaskList);
            args.put("__git", GAME_CENTER_GIT);
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.p2e.reportExposedTasks", args);
    }

    public static String p2ePlatformTaskSignUp(String taskId, String taskToken, String actionChannel, String source) {
        return p2ePlatformTask(taskId, taskToken, actionChannel, source,
                "com.alipay.gamecenteruprod.biz.rpc.platformTaskSignUp");
    }

    public static String p2ePlatformTaskComplete(String taskId, String taskToken, String actionChannel, String source) {
        return p2ePlatformTask(taskId, taskToken, actionChannel, source,
                "com.alipay.gamecenteruprod.biz.rpc.platformTaskComplete");
    }

    private static String p2ePlatformTask(String taskId, String taskToken, String actionChannel,
                                          String source, String method) {
        // 2026-11-19（gc17）：改用「手点同款信封」原样发送。
        //
        // 实测对照（同一天、同一 taskId/taskToken、同一 source）：
        //   A. 走 request() → NewRpcBridge 的完整信封（带 apiCallLink/execEngine/appName/
        //      __apiNativeCallId/methodName/wua/useWua/disableLimitView）
        //      → 服务端回 success:true，但任务永远停在 UN_SIGNUP，不记账。
        //   B. 原样回放手点抓到的 4 字段信封（只有 __apiCallStartTime / __apiNativeCallId /
        //      operationType / requestData）
        //      → signup 后任务变 SIGNUP_COMPLETED，complete 后变 COMPLETED，正常记账。
        //
        // 差异只有信封本身：完整信封里的 wua/methodName/appName/apiCallLink/execEngine 等字段
        // 会让这条「改状态」的 RPC 走另一条（不记账的）分支，所以这两个方法固定走原样信封。
        StringBuilder env = new StringBuilder(320);
        env.append("{\"__apiCallStartTime\":").append(System.currentTimeMillis());
        env.append(",\"__apiNativeCallId\":\"native_").append(100 + RandomUtil.nextInt(0, 1900)).append('"');
        env.append(",\"operationType\":\"").append(method).append('"');
        env.append(",\"requestData\":[{");
        env.append("\"actionChannel\":\"").append(actionChannel).append('"');
        env.append(",\"activityId\":\"").append(ACTIVITY_ID).append('"');
        env.append(",\"source\":\"").append(source).append('"');
        env.append(",\"taskId\":\"").append(taskId).append('"');
        env.append(",\"taskToken\":\"").append(taskToken).append('"');
        env.append("}]}");
        String resp = ApplicationHook.requestRawEnvelope(method, env.toString());
        if (resp != null) {
            return resp;
        }
        // 原样信封不可用（旧版支付宝/桥接不支持）时退回完整信封，至少保留受理回执。
        JSONObject args = new JSONObject();
        try {
            args.put("actionChannel", actionChannel);
            args.put("activityId", ACTIVITY_ID);
            args.put("source", source);
            args.put("taskId", taskId);
            args.put("taskToken", taskToken);
        } catch (Throwable ignored) {
        }
        return request(method, args);
    }

    public static String p2eTaskReceive(JSONObject task, String actionChannel, String source, String oriChInfo) {
        JSONObject args = new JSONObject();
        try {
            String activityId = task == null ? "" : task.optString("activityId", "");
            args.put("actionChannel", actionChannel);
            args.put("activityId", activityId.isEmpty() ? ACTIVITY_ID : activityId);
            args.put("__git", GAME_CENTER_GIT);
            args.put("oriChInfo", oriChInfo);
            args.put("source", source);
            args.put("taskId", task == null ? "" : task.optString("taskId", ""));
            args.put("taskToken", task == null ? "" : task.optString("taskToken", ""));
            args.put("taskType", task == null ? "" : task.optString("taskType", ""));
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.p2e.gameP2eTaskReceive", args);
    }

    public static String p2eMilestoneReceive(JSONObject task, String source, String oriChInfo) {
        JSONObject args = new JSONObject();
        try {
            args.put("taskMileStoneId", task == null ? "" : task.optString("taskMileStoneId", ""));
            args.put("outBizNo", task == null ? "" : task.optString("outBizNo", ""));
            args.put("sign", task == null ? "" : task.optString("sign", ""));
            args.put("source", source);
            args.put("oriChInfo", oriChInfo);
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.p2e.receiveTaskMileStoneReward", args);
    }

    public static String p2eSignIn(String date, int index, String signSequenceId, String source) {
        JSONObject args = new JSONObject();
        try {
            args.put("date", date);
            args.put("index", index);
            args.put("signSequenceId", signSequenceId);
            args.put("source", source);
            args.put("__git", GAME_CENTER_GIT);
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.p2e.signIn", args);
    }

    public static String p2eDrawGold() {
        JSONObject args = new JSONObject();
        try {
            args.put("__git", GAME_CENTER_GIT);
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.p2e.drawGold", args);
    }

    public static String queryP2eGoldExchangePrizePage() {
        JSONObject args = new JSONObject();
        try {
            args.put("__git", GAME_CENTER_GIT);
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.p2e.queryGoldExgPrizePage", args);
    }

    public static String doP2eGoldExchangePrize(JSONObject prize) {
        JSONObject args = new JSONObject();
        try {
            args.put("__git", GAME_CENTER_GIT);
            args.put("outBizNo", prize == null ? "" : prize.optString("outBizNo", ""));
            args.put("prizeConfigId", prize == null ? "" : prize.optString("prizeConfigId", ""));
            args.put("sendSign", prize == null ? "" : prize.optString("sendSign", ""));
            args.put("sendSignNew", prize == null ? "" : prize.optString("sendSignNew", ""));
            args.put("withdrawalTierType", prize == null ? "" : prize.optString("withdrawalTierType", ""));
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.p2e.doGoldExchangePrize", args);
    }

    // ------------------------------------------------------------- 浮动球（浏览 15 秒类任务）

    /**
     * 提交浏览时长（分段，服务端按段累计）。AG 实测：首段 31 秒，之后每段 30 秒。
     *
     * @param gameAppId 任务里的 appId
     * @param playTime  本段秒数
     * @param source    任务来源（sourceOf(task)）
     */
    public static String submitUserPlayDurationAction(String gameAppId, int playTime, String source) {
        JSONObject args = new JSONObject();
        try {
            args.put("gameAppId", gameAppId);
            args.put("playTime", playTime);
            args.put("source", source);
            args.put("statisticTag", "");
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.v3.submitUserPlayDurationAction", args);
    }

    /** 查询浮动球可完成的时长与类型（服务端下发 timeSeconds / floatingBallTypeList）。 */
    public static String p2eFloatingBallConsult(String gameId, String gameModuleId,
                                                String source, String trafficDriverId) {
        JSONObject args = new JSONObject();
        try {
            args.put("__git", GAME_CENTER_GIT);
            args.put("gameId", gameId);
            args.put("gameModuleId", gameModuleId);
            args.put("source", source);
            args.put("trafficDriverId", trafficDriverId);
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.p2e.gameP2eFloatingBallConsult", args);
    }

    /** 完成浮动球（时长已上报后调用，服务端据此把任务置为完成）。 */
    public static String p2eFloatingBallComplete(String gameId, String gameModuleId, String oriChInfo,
                                                 String source, String trafficDriverId, List<String> types) {
        JSONObject args = new JSONObject();
        try {
            JSONArray typeArray = new JSONArray();
            if (types != null) {
                for (String type : types) {
                    if (type != null && !type.isEmpty()) {
                        typeArray.put(type);
                    }
                }
            }
            args.put("floatingBallTypeList", typeArray);
            args.put("gameId", gameId);
            args.put("gameModuleId", gameModuleId);
            args.put("oriChInfo", oriChInfo);
            args.put("source", source);
            args.put("trafficDriverId", trafficDriverId);
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.p2e.gameP2eFloatingBallComplete", args);
    }

    /** 浮球查询（渠道任务族）：部分机型/版本上走这一族接口，返回体与 p2e 族结构一致。 */
    public static String channelTaskFloatingBallConsult(String gameId, String gameModuleId,
                                                        String source, String trafficDriverId) {
        JSONObject args = new JSONObject();
        try {
            args.put("gameId", gameId);
            args.put("gameModuleId", gameModuleId);
            args.put("source", source);
            args.put("trafficDriverId", trafficDriverId);
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.channeltask.floatingball.consult", args);
    }

    /** 浮球完成（渠道任务族）。 */
    public static String channelTaskFloatingBallComplete(String gameId, String gameModuleId, String oriChInfo,
                                                         String source, String trafficDriverId, List<String> types) {
        JSONObject args = new JSONObject();
        try {
            JSONArray typeArray = new JSONArray();
            if (types != null) {
                for (String type : types) {
                    if (type != null && !type.isEmpty()) {
                        typeArray.put(type);
                    }
                }
            }
            args.put("floatingBallTypeList", typeArray);
            args.put("gameId", gameId);
            args.put("gameModuleId", gameModuleId);
            args.put("oriChInfo", oriChInfo);
            args.put("source", source);
            args.put("trafficDriverId", trafficDriverId);
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.channeltask.floatingball.complete", args);
    }

    /** 旧族浮球查询（无 p2e 前缀）：作为诊断兜底。 */
    public static String floatingBallConsultLegacy(String gameId, String gameModuleId,
                                                   String source, String trafficDriverId) {
        JSONObject args = new JSONObject();
        try {
            args.put("gameId", gameId);
            args.put("gameModuleId", gameModuleId);
            args.put("source", source);
            args.put("trafficDriverId", trafficDriverId);
        } catch (Throwable ignored) {
        }
        return request("com.alipay.gamecenteruprod.biz.rpc.floatingball.consult", args);
    }

    /**
     * 直传数组的通用 RPC：部分新接口（如 floatingball.frontEnd.consult.answer）要求
     * requestData 本身就是数组而不是 [{...}]，这里直接用数组字符串发起。
     */
    public static String requestArray(String method, String requestDataJson) {
        return ApplicationHook.requestString(method, requestDataJson);
    }

    /** 浮球前端咨询答案（App 端真实调用，fieldTypeList 由前端从页面配置里取）。 */
    public static String floatingBallFrontEndConsultAnswer(String sceneId, String taskId, String source,
                                                           List<String> fieldTypeList) {
        JSONObject item = new JSONObject();
        try {
            JSONArray types = new JSONArray();
            if (fieldTypeList != null) {
                for (String type : fieldTypeList) {
                    if (type != null && !type.isEmpty()) {
                        types.put(type);
                    }
                }
            }
            item.put("fieldTypeList", types);
            item.put("sceneId", sceneId);
            item.put("source", source);
            item.put("taskId", taskId);
        } catch (Throwable ignored) {
        }
        return requestArray("com.alipay.gamecenteruprod.biz.rpc.floatingball.frontEnd.consult.answer",
                "[" + item + "]");
    }
}
