package io.github.lazyimmortal.sesame.model.task.antForest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.lazyimmortal.sesame.hook.Toast;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.Statistics;
import io.github.lazyimmortal.sesame.util.Status;
import io.github.lazyimmortal.sesame.util.TimeUtil;
import io.github.lazyimmortal.sesame.util.idMap.ForestHuntIdMap;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

public class ForestChouChouLe {

  private static final String TAG = ForestChouChouLe.class.getSimpleName();

  void chouChouLe(
      Boolean ForestHuntDraw,
      Boolean ForestHuntHelp,
      Set<String> shareIds,
      Boolean ACTIVITYForestHuntHelp) {
    try {
      ForestHuntIdMap.load();
      // String source = "task_entry";
      // String source = "guide";
      // String source = "forestchouchoule";
      JSONObject resData =
          new JSONObject(
              AntForestRpcCall.enterDrawActivityopengreen(
                  "", "ANTFOREST_NORMAL_DRAW", "task_entry"));
      if (!MessageUtil.checkSuccess(TAG, resData)) {
        return;
      }
      // 提取drawSceneGroups数组
      JSONArray drawSceneGroups = resData.getJSONArray("drawSceneGroups");
      for (int i = 0; i < drawSceneGroups.length(); i++) {
        JSONObject drawScene = drawSceneGroups.getJSONObject(i);
        JSONObject drawActivity = drawScene.getJSONObject("drawActivity");

        String activityId = drawActivity.getString("activityId");
        String drawScenename = drawActivity.getString("name");
        String sceneCode = drawActivity.getString("sceneCode");

        chouChouLescene(
            ForestHuntDraw,
            activityId,
            drawScenename,
            sceneCode,
            ForestHuntHelp,
            shareIds,
                ACTIVITYForestHuntHelp);
      }
    } catch (Exception e) {
      Log.printStackTrace(e);
    }
  }

  void chouChouLescene(
      Boolean ForestHuntDraw,
      String activityId,
      String drawScenename,
      String sceneCode,
      Boolean ForestHuntHelp,
      Set<String> shareIds,
      Boolean ACTIVITYForestHuntHelp) {
    String taskUid = UserIdMap.getCurrentUid();
    try {
      boolean doublecheck;
      // ==================== 手动屏蔽任务集合 ====================
      Set<String> presetBad = new LinkedHashSet<>();
      presetBad.add("FOREST_NORMAL_DRAW_SHARE"); // 邀请好友任务（屏蔽）
      // 你可以在这里继续添加更多要屏蔽的任务
      presetBad.add("FOREST_ACTIVITY_DRAW_SHARE");
      // =====================================================

      int loopCount = 0; // 循环次数计数
      final int MAX_LOOP = 7; // 最大循环次数，避免死循环

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
        JSONObject listTaskopengreen =
            new JSONObject(AntForestRpcCall.listTaskopengreen(sceneCode + "_TASK", "task_entry"));
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


            if (taskType.contains("_DRAW_SHARE") && ForestHuntHelp) {
              // if (!Status.hasFlagToday("Forest::" + sceneCode)) {
                 int forestHuntHelpTodayCount = Status.getforestHuntHelpToday(taskType);
              if (forestHuntHelpTodayCount < shareIds.size()) {
                JSONObject prodPlayParam = new JSONObject(taskBaseInfo.getString("prodPlayParam"));
                String p2pSceneCode = prodPlayParam.getString("p2pSceneCode");
                Log.forest(
                    "森林寻宝🎰️执行["
                        + UserIdMap.getShowName(UserIdMap.getCurrentUid())
                        + "]助力好友["
                        + drawScenename
                        + "]");
                DoForestHuntHelp(shareIds, activityId, p2pSceneCode, taskType);
                // Status.flagToday("Forest::" + sceneCode,taskUid);
              }
            }
            // 在最后一个任务时强制开启活动场景助力
            if ((i == (taskList.length() - 1)) && (!taskType.equals("FOREST_ACTIVITY_DRAW_SHARE")))
              if (ACTIVITYForestHuntHelp && sceneCode.equals("ANTFOREST_ACTIVITY_DRAW")) {
                  int forestHuntHelpTodayCount = Status.getforestHuntHelpToday("FOREST_ACTIVITY_DRAW_SHARE");
                if (forestHuntHelpTodayCount < shareIds.size()) {
                  // if (!Status.hasFlagToday("Forest::" + sceneCode)) {
                  Log.forest(
                      "森林寻宝🎰️执行["
                          + UserIdMap.getShowName(UserIdMap.getCurrentUid())
                          + "]助力好友[活动场景](薅羊毛，如果服务器接口存在，失效后关闭配置选项)");
                  DoForestHuntHelp(
                      shareIds,
                      "20251024",
                      "FOREST_NORMAL_20251024_SHARE",
                      "FOREST_ACTIVITY_DRAW_SHARE");
                  // Status.flagToday("Forest::" + sceneCode,taskUid);
                }
              }
            // ==================== 活力值兑换任务 =====================
            if (taskType.equals("NORMAL_DRAW_EXCHANGE_VITALITY") && taskStatus.equals("TODO")) {
              JSONObject sginRes =
                  new JSONObject(
                      AntForestRpcCall.exchangeTimesFromTaskopengreen(
                          activityId, sceneCode, "task_entry", taskSceneCode, taskType));
              if (MessageUtil.checkSuccess(TAG, sginRes)) {
                int times = sginRes.getInt("times");
                Log.forest("森林寻宝🏆[" + taskName + "]获得抽奖*" + times);
                doublecheck = true;
              }
              continue; // 防止进入下面的 FOREST_NORMAL_DRAW 分支
            }

            // 统一处理 FOREST_NORMAL_DRAW 和 FOREST_ACTIVITY_DRAW开头任务
            if ((taskType.startsWith("FOREST_NORMAL_DRAW")
                    || taskType.startsWith("FOREST_ACTIVITY_DRAW"))
                && taskStatus.equals("TODO")) {
              // ==================== 屏蔽逻辑 ====================
              if (presetBad.contains(taskType)) {
                Log.record("已屏蔽任务，跳过：" + taskName);
              } else {
                TimeUtil.sleep(1000);
                // 调用对应完成接口
                JSONObject result;
                if (taskType.contains("XLIGHT")) {
                  result =
                      new JSONObject(
                          AntForestRpcCall.finishTask4Chouchoule(taskType, taskSceneCode));
                } else {
                  result =
                      new JSONObject(AntForestRpcCall.finishTaskopengreen(taskType, taskSceneCode));
                }

                if (MessageUtil.checkSuccess(TAG, result)) {
                  Log.forest("森林寻宝🧾完成[" + taskName + "]");
                  doublecheck = true;
                }
              }
            }

            // 已完成任务领取奖励
            if (taskStatus.equals("FINISHED")) {
              TimeUtil.sleep(2000);
              JSONObject sginRes =
                  new JSONObject(
                      AntForestRpcCall.receiveTaskAwardopengreen(
                          "task_entry", taskSceneCode, taskType));
              if (MessageUtil.checkSuccess(TAG, sginRes)) {
                int incAwardCount = sginRes.getInt("incAwardCount");
                Log.forest("森林寻宝🏆[" + taskName + "]获得抽奖*" + incAwardCount);
                if (rightsTimesLimit - rightsTimes > 0) {
                  doublecheck = true;
                }
              }
            }
          }
        }
      } while (doublecheck && ++loopCount < MAX_LOOP);

      // ==================== 执行抽奖 ====================
      if (ForestHuntDraw) {
        JSONObject jo =
            new JSONObject(
                AntForestRpcCall.enterDrawActivityopengreen(activityId, sceneCode, "task_entry"));
        if (MessageUtil.checkSuccess(TAG, jo)) {
          JSONObject drawAsset = jo.getJSONObject("drawAsset");
          int blance = drawAsset.getInt("blance");

          while (blance > 0) {
            jo =
                new JSONObject(
                    AntForestRpcCall.drawopengreen(
                        activityId, sceneCode, "task_entry", UserIdMap.getCurrentUid()));
            if (MessageUtil.checkSuccess(TAG, jo)) {
              drawAsset = jo.getJSONObject("drawAsset");
              blance = drawAsset.getInt("blance");
              JSONObject prizeVO = jo.getJSONObject("prizeVO");
              String prizeName = prizeVO.getString("prizeName");
              int prizeNum = prizeVO.getInt("prizeNum");
              Log.forest(
                  "森林寻宝🎁领取["
                      + prizeName
                      + "*"
                      + prizeNum
                      + "]"
                      + "#["
                      + UserIdMap.getShowName(UserIdMap.getCurrentUid())
                      + "]");
              Toast.show("森林寻宝🎁领取[" + prizeName + "*" + prizeNum + "]");
              if (prizeName.contains("g能量")) {
                Statistics.addData(Statistics.DataType.COLLECTED, prizeNum);
              }
            }
          }
        }
      }

      // ==============================================

    } catch (Exception e) {
      Log.printStackTrace(e);
    }
  }

  // kuzVe2lrSrXFdacxxi3KWjxx-4O7FEYDgn0xx0OehP5jt9-YINZOkxgPDkvWvkwkQXSDbZ-77VUJcjlcZsjGio6MsAtmwxkxkx(FOREST_NORMAL_DRAW_SHARE)
  // kuzVe2lrSrXFdacxxi3KWjxx-4O7FEYDgn0xx0OehP5jt9-bxgpIW643h4FnWRjs9uZzng-77VUJcjlcZsjGio6MsAtmwxkxkx(FOREST_ACTIVITY_DRAW_SHARE)
  void DoForestHuntHelp(
      Set<String> shareIds, String activityId, String p2pSceneCode, String taskType) {
    String taskUid = UserIdMap.getCurrentUid();
    try {
      Integer forestHuntHelpTodayCount;

      for (String shareUserId : shareIds) {
        forestHuntHelpTodayCount = Status.getforestHuntHelpToday(taskType);
        // if (!Status.canForestHuntHelpToday(taskType + "::" + shareUserId)) {
        // 判断当天是否助力过
        if (Status.hasFlagToday(taskType + "::" + shareUserId)) {
          continue;
        }
        String shareId;
        if ((shareUserId.length() > 20 && shareUserId.length() < 27)
            && taskType.equals("FOREST_NORMAL_DRAW_SHARE")) {
          shareId =
              shareUserId
                  + "4O7FEYDgn0xx0OehP5jt9"
                  + "YINZOkxgPDkvWvkwkQXSDbZ"
                  + "77VUJcjlcZsjGio6MsAtmwxkxkx";
        } else if ((shareUserId.length() > 20 && shareUserId.length() < 27)
            && taskType.equals("FOREST_ACTIVITY_DRAW_SHARE")) {
          shareId =
              shareUserId
                  + "4O7FEYDgn0xx0OehP5jt9"
                  + "bxgpIW643h4FnWRjs9uZzng"
                  + "77VUJcjlcZsjGio6MsAtmwxkxkx";
        } else {
          Log.forest("森林寻宝🎰️存在错误usershareUserId:" + shareUserId);
          continue;
        }
        String userId = shareComponentRecall(p2pSceneCode, shareId);
        Log.forest("森林寻宝🎰️尝试助力#" + ForestHuntIdMap.get(shareUserId));
        if (userId.equals("解析userID失败")) {
          continue;
        }
        TimeUtil.sleep(1500);
        String resconfirmShareRecall =
            confirmShareRecall(activityId, p2pSceneCode, shareId, userId);
        TimeUtil.sleep(1500);
        Log.forest("森林寻宝🎰️助力[" + userId + "]" + resconfirmShareRecall);
        // 标记助力成功
        Status.flagToday(taskType + "::" + shareUserId, taskUid);
        // Status.ForestHuntHelpToday(taskType + "::" + shareUserId, taskUid);
        forestHuntHelpTodayCount++;
        // 统计场景助力次数
        Status.forestHuntHelpToday(taskType, forestHuntHelpTodayCount, taskUid);
      }
    } catch (Throwable t) {
      Log.printStackTrace(TAG, t);
    }
  }

  private String shareComponentRecall(String sceneCode, String shareId) {
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

  private String confirmShareRecall(
      String activityId, String p2pSceneCode, String shareId, String userId) {
    try {
      JSONObject jo =
          new JSONObject(
              AntForestRpcCall.confirmShareRecall(activityId, p2pSceneCode, shareId, userId));
      return jo.getString("desc");
    } catch (Throwable t) {
      Log.i(TAG, "confirmShareRecall err:");
      Log.printStackTrace(TAG, t);
    }
    return "FALSE end";
  }
}
