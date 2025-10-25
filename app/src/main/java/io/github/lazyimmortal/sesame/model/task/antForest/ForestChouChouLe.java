package io.github.lazyimmortal.sesame.model.task.antForest;




import org.json.JSONArray;
import org.json.JSONObject;


import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.lazyimmortal.sesame.hook.Toast;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.Status;
import io.github.lazyimmortal.sesame.util.TimeUtil;
import io.github.lazyimmortal.sesame.util.idMap.ForestHuntIdMap;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;


public class ForestChouChouLe {

    private static final String TAG = ForestChouChouLe.class.getSimpleName();


    void chouChouLe(Boolean ForestHuntDraw,Boolean ForestHuntHelp,Set<String> shareIds) {
        try {
            ForestHuntIdMap.load();
            //String source = "task_entry";
            //String source = "guide";
            //String source = "forestchouchoule";
            JSONObject resData = new JSONObject(AntForestRpcCall.enterDrawActivityopengreen("","ANTFOREST_NORMAL_DRAW", "task_entry"));
            if (!MessageUtil.checkSuccess(TAG, resData)) {
                return;
            }
            //提取drawSceneGroups数组
            JSONArray drawSceneGroups = resData.getJSONArray("drawSceneGroups");
            for (int i = 0; i < drawSceneGroups.length(); i++) {
                JSONObject drawScene = drawSceneGroups.getJSONObject(i);
                JSONObject drawActivity = drawScene.getJSONObject("drawActivity");

                String activityId = drawActivity.getString("activityId");
                String sceneCode = drawActivity.getString("sceneCode");
                chouChouLescene(ForestHuntDraw, activityId, sceneCode,ForestHuntHelp,shareIds);
            }
        }catch(Exception e){
            Log.printStackTrace(e);
        }
    }



    void chouChouLescene(Boolean ForestHuntDraw,String activityId,String sceneCode,Boolean ForestHuntHelp,Set<String> shareIds) {
        try {
            boolean doublecheck;
            // ==================== 手动屏蔽任务集合 ====================
            Set<String> presetBad = new LinkedHashSet<>();
            presetBad.add("FOREST_NORMAL_DRAW_SHARE");  // 邀请好友任务（屏蔽）
            // 你可以在这里继续添加更多要屏蔽的任务
             presetBad.add("FOREST_ACTIVITY_DRAW_SHARE");
            // =====================================================

            int loopCount = 0;           // 循环次数计数
            final int MAX_LOOP = 7;      // 最大循环次数，避免死循环

            /*
            sceneCode
            ANTFOREST_NORMAL_DRAW_TASK
            ANTFOREST_ACTIVITY_DRAW_TASK

            taskType
            FOREST_NORMAL_DRAW_SHARE
            FOREST_ACTIVITY_DRAW_SHARE

            p2pSceneCode
            FOREST_NORMAL_20250829_SHARE
            FOREST_NORMAL_20251024_SHARE
             */
            do {
                doublecheck = false;
                    JSONObject listTaskopengreen = new JSONObject(AntForestRpcCall.listTaskopengreen(sceneCode+"_TASK", "task_entry"));
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

                            //
                            if (taskType.contains("_DRAW_SHARE")&&ForestHuntHelp) {
                                JSONObject prodPlayParam=new JSONObject(taskBaseInfo.getString("prodPlayParam"));
                                String p2pSceneCode=prodPlayParam.getString("p2pSceneCode");
                                if (!Status.hasFlagToday("Forest::" + sceneCode)) {
                                    DoForestHuntHelp(shareIds,activityId,p2pSceneCode);
                                    Status.flagToday("Forest::" + sceneCode);
                                }
                            }




                            // ==================== 活力值兑换任务 =====================
                            if (taskType.equals("NORMAL_DRAW_EXCHANGE_VITALITY") && taskStatus.equals("TODO")) {
                                JSONObject sginRes =  new JSONObject(AntForestRpcCall.exchangeTimesFromTaskopengreen(activityId,sceneCode, "task_entry", taskSceneCode, taskType));
                                if (MessageUtil.checkSuccess(TAG, sginRes)) {
                                    int times=sginRes.getInt("times");
                                    Log.forest("森林寻宝🏆["+ taskName+"]获得抽奖*" + times);
                                    doublecheck = true;
                                }
                                continue; // 防止进入下面的 FOREST_NORMAL_DRAW 分支
                            }

                            // 统一处理 FOREST_NORMAL_DRAW 和 FOREST_ACTIVITY_DRAW开头任务
                            if ((taskType.startsWith("FOREST_NORMAL_DRAW")||taskType.startsWith("FOREST_ACTIVITY_DRAW")) && taskStatus.equals("TODO")) {
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
                                        Log.forest("森林寻宝🧾完成[" + taskName+"]");
                                        doublecheck = true;
                                    }
                                }
                            }

                            // 已完成任务领取奖励
                            if (taskStatus.equals("FINISHED")) {
                                TimeUtil.sleep(2000);
                                JSONObject sginRes = new JSONObject(AntForestRpcCall.receiveTaskAwardopengreen("task_entry", taskSceneCode, taskType));
                                if (MessageUtil.checkSuccess(TAG, sginRes)) {
                                    int incAwardCount=sginRes.getInt("incAwardCount");
                                    Log.forest("森林寻宝🏆["+ taskName+"]获得抽奖*" + incAwardCount);
                                    if (rightsTimesLimit - rightsTimes > 0) {
                                        doublecheck = true;
                                    }
                                }
                            }
                        }
                    }
            } while (doublecheck && ++loopCount < MAX_LOOP);

            // ==================== 执行抽奖 ====================
            if(ForestHuntDraw) {
                JSONObject jo = new JSONObject(AntForestRpcCall.enterDrawActivityopengreen(activityId,sceneCode,"task_entry"));
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    JSONObject drawAsset = jo.getJSONObject("drawAsset");
                    int blance = drawAsset.getInt("blance");

                    while (blance > 0) {
                        jo = new JSONObject(AntForestRpcCall.drawopengreen(activityId,sceneCode, "task_entry", UserIdMap.getCurrentUid()));
                        if (MessageUtil.checkSuccess(TAG, jo)) {
                            drawAsset = jo.getJSONObject("drawAsset");
                            blance = drawAsset.getInt("blance");
                            JSONObject prizeVO = jo.getJSONObject("prizeVO");
                            String prizeName = prizeVO.getString("prizeName");
                            int prizeNum = prizeVO.getInt("prizeNum");
                            Log.forest("森林寻宝🎁领取[" + prizeName + "*" + prizeNum + "]"+"#["+UserIdMap.getShowName(UserIdMap.getCurrentUid())+"]");
                            Toast.show("森林寻宝🎁领取[" + prizeName + "*" + prizeNum + "]");
                        }
                    }
                }
            }

            // ==============================================

        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    void DoForestHuntHelp(Set<String> shareIds,String activityId,String p2pSceneCode) {
            try {
                //Set<String> shareIds = ForestHuntHelpList.getValue();
                for (String shareId : shareIds) {
                    TimeUtil.sleep(2000);
                    if (shareId.length() > 90) {
                        String userId = shareComponentRecall(p2pSceneCode, shareId);
                        Log.forest("森林寻宝🎰️尝试助力#"+ForestHuntIdMap.get(shareId));
                        if(userId.equals("解析userID失败")){
                            continue;
                        }
                        TimeUtil.sleep(2000);
                        String resconfirmShareRecall = confirmShareRecall(activityId, p2pSceneCode, shareId, userId);
                        TimeUtil.sleep(1000);
                        Log.forest("森林寻宝🎰️助力[" + userId + "]" + resconfirmShareRecall + "[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                    }
                }
            } catch (Throwable t) {
                Log.printStackTrace(TAG, t);
            }
        }


    private String shareComponentRecall(String sceneCode,String shareId) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.shareComponentRecall(sceneCode, shareId));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return "解析shareID失败";
            }
            if (jo.has("inviterInfoVo")) {
                jo = jo.getJSONObject("inviterInfoVo");
                String userID = jo.getString("userId");
                return userID;
            }
        } catch (Throwable t) {
            Log.i(TAG, "shareComponentRecall err:");
            Log.printStackTrace(TAG, t);
        }
        return "解析userID失败";
    }

private String confirmShareRecall(String activityId,String p2pSceneCode,String shareId,String userId) {
    try {
        JSONObject jo = new JSONObject(AntForestRpcCall.confirmShareRecall(activityId,p2pSceneCode,shareId,userId));
        //Log.forest(jo.toString());
        return jo.getString("desc");
    } catch (Throwable t) {
        Log.i(TAG, "confirmShareRecall err:");
        Log.printStackTrace(TAG, t);
    }
    return "FALSE end";
}
    }



