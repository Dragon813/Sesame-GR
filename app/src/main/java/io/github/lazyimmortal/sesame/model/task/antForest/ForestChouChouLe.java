package io.github.lazyimmortal.sesame.model.task.antForest;




import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.TimeUtil;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

public class ForestChouChouLe {

    private static final String TAG = ForestChouChouLe.class.getSimpleName();

    void chouChouLe(Boolean ForestHuntDraw) {
        try {
            boolean doublecheck;
            String source = "task_entry";
            //String source = "guide";


            // ==================== 手动屏蔽任务集合 ====================
            Set<String> presetBad = new LinkedHashSet<>();
            presetBad.add("FOREST_NORMAL_DRAW_SHARE");  // 邀请好友任务（屏蔽）
            // 你可以在这里继续添加更多要屏蔽的任务
            // presetBad.add("xxx");
            // =====================================================

            JSONObject jo = new JSONObject(AntForestRpcCall.enterDrawActivityopengreen(source));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }

            JSONObject drawScene = jo.getJSONObject("drawScene");
            JSONObject drawActivity = drawScene.getJSONObject("drawActivity");
            String activityId = drawActivity.getString("activityId");
            String sceneCode = drawActivity.getString("sceneCode"); // ANTFOREST_NORMAL_DRAW
            String listSceneCode = sceneCode + "_TASK";

            long startTime = drawActivity.getLong("startTime");
            long endTime = drawActivity.getLong("endTime");

            int loopCount = 0;           // 循环次数计数
            final int MAX_LOOP = 7;      // 最大循环次数，避免死循环

            do {
                doublecheck = false;
                if (System.currentTimeMillis() > startTime && System.currentTimeMillis() < endTime) {
                    TimeUtil.sleep(1000);

                    JSONObject listTaskopengreen = new JSONObject(AntForestRpcCall.listTaskopengreen(activityId, listSceneCode, source));
                    if (MessageUtil.checkSuccess(TAG, listTaskopengreen)) {
                        JSONArray taskList = listTaskopengreen.getJSONArray("taskInfoList");
                        for (int i = 0; i < taskList.length(); i++) {
                            JSONObject taskInfo = taskList.getJSONObject(i);
                            JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo");
                            JSONObject bizInfo = new JSONObject(taskBaseInfo.getString("bizInfo"));
                            String taskName = bizInfo.getString("title");
                            String taskSceneCode = taskBaseInfo.getString("sceneCode");
                            String taskStatus = taskBaseInfo.getString("taskStatus");
                            String taskType = taskBaseInfo.getString("taskType");

                            JSONObject taskRights = taskInfo.getJSONObject("taskRights");
                            int rightsTimes = taskRights.getInt("rightsTimes");
                            int rightsTimesLimit = taskRights.getInt("rightsTimesLimit");

                            // ==================== 活力值兑换任务 =====================
                            if (taskType.equals("NORMAL_DRAW_EXCHANGE_VITALITY") && taskStatus.equals("TODO")) {
                                JSONObject sginRes =  new JSONObject(AntForestRpcCall.exchangeTimesFromTaskopengreen(activityId, sceneCode, source, taskSceneCode, taskType));
                                if (MessageUtil.checkSuccess(TAG, sginRes)) {
                                    int times=sginRes.getInt("times");
                                    Log.forest("森林寻宝🏆"+ taskName+"，获得抽奖次数：" + times);
                                    doublecheck = true;
                                }
                                continue; // 防止进入下面的 FOREST_NORMAL_DRAW 分支
                            }

                            // 统一处理 FOREST_NORMAL_DRAW 开头任务
                            if (taskType.startsWith("FOREST_NORMAL_DRAW") && taskStatus.equals("TODO")) {
                                // ==================== 屏蔽逻辑 ====================
                                if (presetBad.contains(taskType)) {
                                    Log.record("已屏蔽任务，跳过：" + taskName);
                                }
                                else{
                                    TimeUtil.sleep(1000);

                                    // 调用对应完成接口
                                    JSONObject result;
                                    if (taskType.contains("XLIGHT")) {
                                        result = new JSONObject(AntForestRpcCall.finishTask4Chouchoule(taskType, taskSceneCode));
                                    } else {
                                        result = new JSONObject(AntForestRpcCall.finishTaskopengreen(taskType, taskSceneCode));
                                    }

                                    if (MessageUtil.checkSuccess(TAG, result)) {
                                        Log.forest("森林寻宝🧾完成任务：" + taskName);
                                        doublecheck = true;
                                    }
                                }
                            }

                            // 已完成任务领取奖励
                            if (taskStatus.equals("FINISHED")) {
                                TimeUtil.sleep(3000);
                                JSONObject sginRes = new JSONObject(AntForestRpcCall.receiveTaskAwardopengreen(source, taskSceneCode, taskType));
                                if (MessageUtil.checkSuccess(TAG, sginRes)) {
                                    int incAwardCount=sginRes.getInt("incAwardCount");
                                    Log.forest("森林寻宝🏆"+ taskName+"，获得抽奖次数：" + incAwardCount);
                                    if (rightsTimesLimit - rightsTimes > 0) {
                                        doublecheck = true;
                                    }
                                }
                            }
                        }
                    }
                }
            } while (doublecheck && ++loopCount < MAX_LOOP);

            // ==================== 执行抽奖 ====================
            if(ForestHuntDraw){
                jo = new JSONObject(AntForestRpcCall.enterDrawActivityopengreen(source));
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    drawScene = jo.getJSONObject("drawScene");
                    drawActivity = drawScene.getJSONObject("drawActivity");
                    activityId = drawActivity.getString("activityId");
                    sceneCode = drawActivity.getString("sceneCode");

                    JSONObject drawAsset = jo.getJSONObject("drawAsset");
                    int blance = drawAsset.optInt("blance", 0);

                    while (blance > 0) {
                        jo = new JSONObject(AntForestRpcCall.drawopengreen(activityId, sceneCode, source, UserIdMap.getCurrentUid()));
                        if (MessageUtil.checkSuccess(TAG, jo)) {
                            drawAsset = jo.getJSONObject("drawAsset");
                            blance = drawAsset.getInt("blance");
                            JSONObject prizeVO = jo.getJSONObject("prizeVO");
                            String prizeName = prizeVO.getString("prizeName");
                            int prizeNum = prizeVO.getInt("prizeNum");
                            Log.forest("森林寻宝🎁[领取: " + prizeName + "*" + prizeNum + "]"+"#"+UserIdMap.getMaskName(UserIdMap.getCurrentUid()));
                        }
                    }
                }
            }

            // ==============================================

        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }
}
