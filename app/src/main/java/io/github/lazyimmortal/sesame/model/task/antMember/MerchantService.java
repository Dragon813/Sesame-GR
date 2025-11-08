package io.github.lazyimmortal.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Objects;

import io.github.lazyimmortal.sesame.model.extensions.ExtensionsHandle;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.Status;
import io.github.lazyimmortal.sesame.util.TimeUtil;

public class MerchantService {
    private static final String TAG = MerchantService.class.getSimpleName();

    public static Boolean transcodeCheck() {
        try {
            JSONObject jo = new JSONObject(MerchantServiceRpcCall.transcodeCheck());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return false;
            } jo = jo.getJSONObject("data"); if (jo.optBoolean("isOpened")) {
                return true;
            } Log.record("商家服务🏪未开通");
        } catch (Throwable t) {
            Log.i(TAG, "transcodeCheck err:"); Log.printStackTrace(TAG, t);
        } return false;
    }

    private static void zcjSignIn() {
        if (Status.hasFlagToday("zcj::signIn")) {
            return;
        } try {
            JSONObject jo = new JSONObject(MerchantServiceRpcCall.zcjSignInQuery());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            } jo = jo.getJSONObject("data").getJSONObject("button");
            // status: UNRECEIVED RECEIVED
            boolean signed = Objects.equals("RECEIVED", jo.getString("status")); if (!signed) {
                jo = new JSONObject(MerchantServiceRpcCall.zcjSignInExecute());
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    jo = jo.getJSONObject("data"); int todayReward = jo.getInt("todayReward");
                    String widgetName = jo.getString("widgetName");
                    Log.other("商家服务🏪完成[" + widgetName + "]#获得[" + todayReward + "商家积分]");
                    signed = true;
                }
            } if (signed) {
                Status.flagToday("zcj::signIn");
            }
        } catch (Throwable t) {
            Log.i(TAG, "zcjSignIn err:"); Log.printStackTrace(TAG, t);
        }
    }

    public static void taskListQueryV2() {
        zcjSignIn(); ExtensionsHandle.handleAlphaRequest("merchantService", "doHideTask", null);
        try {
            JSONObject jo = new JSONObject(MerchantServiceRpcCall.taskListQueryV2());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            } jo = jo.getJSONObject("data"); JSONArray ja = jo.getJSONArray("moduleList");
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i); if (!Objects.equals("MORE", jo.getString("planCode"))) {
                    // planCode: SERVICE MORE
                    continue;
                } taskListProcessing(jo.getJSONArray("taskList"));
            }
        } catch (Throwable t) {
            Log.i(TAG, "taskListQueryV2 err:"); Log.printStackTrace(TAG, t);
        } ballQueryV1();
    }

    private static void taskListProcessing(JSONArray taskList) {
        try {
            for (int i = 0; i < taskList.length(); i++) {
                JSONObject task = taskList.getJSONObject(i);
                String status = task.getString("status");
                // UNRECEIVED PROCESSING NEED_RECEIVE
                if (Objects.equals("NEED_RECEIVE", status)) {
                    continue;
                } if (task.has("extendLog")) {
                    taskFinish(task);
                } else {
                    String actionCode = getActionCode(task);
                    if (!taskQueryByActionCode(actionCode)) {
                        continue;
                    } if (Objects.equals("UNRECEIVED", status)) {
                        if (!taskReceive(task)) {
                            continue;
                        }
                    }
                    // PROCESSING
                    taskActionProduce(task, actionCode);
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "taskListProcessing err:"); Log.printStackTrace(TAG, t);
        }
    }

    private static void taskFinish(JSONObject task) {
        try {
            String bizId = task.getJSONObject("extendLog").getJSONObject("bizExtMap").getString("bizId");
            JSONObject jo = new JSONObject(MerchantServiceRpcCall.taskFinish(bizId));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                String title = task.getString("title"); String reward = task.getString("reward");
                Log.other("商家服务🏪[" + title + "]#获得[" + reward + "商家积分]");
            }
        } catch (Throwable t) {
            Log.i(TAG, "taskListProcessing err:"); Log.printStackTrace(TAG, t);
        }
    }

    private static String getActionCode(JSONObject task) {
        String actionCode = null; try {
            JSONObject extInfo = task.getJSONObject("button").optJSONObject("extInfo");
            if (extInfo != null && extInfo.has("actionCode")) {
                actionCode = extInfo.getString("actionCode") + "_VIEWED"; ;
            } else if (task.has("sendPointImmediately")) {
                actionCode = task.getString("taskCode") + "_VIEWED";
            }
        } catch (Throwable t) {
            Log.i(TAG, "getActionCode err:"); Log.printStackTrace(TAG, t);
        } return actionCode;
    }

    private static Boolean taskQueryByActionCode(String actionCode) {
        if (actionCode == null) {
            return false;
        } try {
            JSONObject jo = new JSONObject(MerchantServiceRpcCall.taskQueryByActionCode(actionCode));
            return MessageUtil.checkSuccess(TAG, jo);
        } catch (Throwable t) {
            Log.i(TAG, "taskQueryByActionCode err:"); Log.printStackTrace(TAG, t);
        } return false;
    }

    private static Boolean taskReceive(JSONObject task) {
        try {
            String taskCode = task.getString("taskCode");
            JSONObject jo = new JSONObject(MerchantServiceRpcCall.taskReceive(taskCode));
            return MessageUtil.checkSuccess(TAG, jo);
        } catch (Throwable t) {
            Log.i(TAG, "taskReceive err:"); Log.printStackTrace(TAG, t);
        } return false;
    }

    private static void taskActionProduce(JSONObject task, String actionCode) {
        try {
            int count = task.getInt("target") - task.getInt("current");
            for (int i = 0; i < count; i++) {
                JSONObject jo = new JSONObject(MerchantServiceRpcCall.taskActionProduce(actionCode));
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    String title = task.getString("title");
                    Log.other("商家服务🏪完成[" + title + "]");
                } TimeUtil.sleep(5000);
            }
        } catch (Throwable t) {
            Log.i(TAG, "taskActionProduce err:"); Log.printStackTrace(TAG, t);
        }
    }

    private static void ballQueryV1() {
        try {
            JSONObject jo = new JSONObject(MerchantServiceRpcCall.ballQueryV1());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            } jo = jo.getJSONObject("data"); if (!jo.has("pointBalls")) {
                return;
            } JSONArray pointBalls = jo.getJSONArray("pointBalls");
            for (int i = 0; i < pointBalls.length(); i++) {
                jo = pointBalls.getJSONObject(i);
                ballReceive(jo.getString("id"), jo.getString("name"));
            }
        } catch (Throwable t) {
            Log.i(TAG, "ballQueryV1 err:"); Log.printStackTrace(TAG, t);
        }
    }

    private static void ballReceive(String ballId, String ballName) {
        try {
            JSONObject jo = new JSONObject(MerchantServiceRpcCall.ballReceive(ballId));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                jo = jo.getJSONObject("data"); String pointReceived = jo.getString("pointReceived");
                Log.other("商家服务🏪领取[" + ballName + "]#获得[" + pointReceived + "商家积分]");
            }
        } catch (Throwable t) {
            Log.i(TAG, "ballReceive err:"); Log.printStackTrace(TAG, t);
        }
    }

    public static void merchantKMDK() {
        if (TimeUtil.isNowAfterTimeStr("0600") && TimeUtil.isNowBeforeTimeStr("1200")) {
            merchantKMDKSignIn();
        } merchantKMDKSignUp();
    }

    private static void merchantKMDKSignIn() {
        try {
            JSONObject jo = new JSONObject(MerchantServiceRpcCall.KMDKQueryActivity());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            } if (Objects.equals("SIGN_IN_ENABLE", jo.getString("signInStatus"))) {
                String activityNo = jo.getString("activityNo");
                jo = new JSONObject(MerchantServiceRpcCall.KMDKSignIn(activityNo));
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    Log.other("商家服务🏪[开门打卡签到成功]");
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "merchantKMDKSignIn err:"); Log.printStackTrace(TAG, t);
        }
    }

    private static void merchantKMDKSignUp() {
        if (Status.hasFlagToday("kmdk::signUp")) {
            return;
        } try {
            boolean hasSignUp = false; for (int i = 0; i < 5; i++) {
                JSONObject jo = new JSONObject(MerchantServiceRpcCall.KMDKQueryActivity());
                if (!MessageUtil.checkSuccess(TAG, jo)) {
                    continue;
                } String activityNo = jo.getString("activityNo");
                if (!Log.getFormatDate().replace("-", "").equals(activityNo.split("_")[2])) {
                    break;
                } else if (Objects.equals("SIGN_UP", jo.getString("signUpStatus"))) {
                    Log.record("开门打卡今日已报名！"); hasSignUp = true; break;
                } else if (Objects.equals("UN_SIGN_UP", jo.getString("signUpStatus"))) {
                    String activityPeriodName = jo.getString("activityPeriodName");
                    jo = new JSONObject(MerchantServiceRpcCall.KMDKSignUp(activityNo));
                    if (MessageUtil.checkSuccess(TAG, jo)) {
                        Log.other("商家服务🏪报名[" + activityPeriodName + "开门打卡]");
                        hasSignUp = true; break;
                    }
                } TimeUtil.sleep(500);
            } if (hasSignUp) {
                Status.flagToday("kmdk::signUp");
            }
        } catch (Throwable t) {
            Log.i(TAG, "merchantKMDKSignUp err:"); Log.printStackTrace(TAG, t);
        }
    }
}
