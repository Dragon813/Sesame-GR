package io.github.lazyimmortal.sesame.model.task.antForest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.Status;

import io.github.lazyimmortal.sesame.util.Status;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class Privilege {
    private static final String TAG = "Privilege";
    
    // 标记 & 前缀
    private static final String FLAG_RECEIVED = "youth_privilege_forest_received";
    private static final String FLAG_STUDENT_TASK = "youth_privilege_student_task";
    private static final String PREFIX_PRIVILEGE = "青春特权🌸";
    private static final String PREFIX_SIGN = "青春特权🧧";
    
    // 任务状态
    private static final String TASK_RECEIVED = "RECEIVED";
    private static final String TASK_FINISHED = "FINISHED";
    private static final String RPC_SUCCESS = "SUCCESS";
    
    // 时间范围
    private static final int SIGN_START_HOUR = 5;
    private static final int SIGN_END_HOUR = 10;
    
    // 青春特权任务配置
    private static final List<YouthTask> YOUTH_TASKS;
    
    static {
        YOUTH_TASKS = new ArrayList<>();
        YOUTH_TASKS.add(new YouthTask("DNHZ_SL_college", "DAXUESHENG_SJK", "双击卡"));
        YOUTH_TASKS.add(new YouthTask("DXS_BHZ", "NENGLIANGZHAO_20230807", "保护罩"));
        YOUTH_TASKS.add(new YouthTask("DXS_JSQ", "JIASUQI_20230808", "加速器"));
    }
    
    private Privilege() {
        // 私有构造函数，防止实例化
    }
    
    public static boolean youthPrivilege() {
        if (Status.hasFlagToday(FLAG_RECEIVED)) {
            return false;
        }
        
        List<String> results = new ArrayList<>();
        for (YouthTask task : YOUTH_TASKS) {
            results.addAll(processYouthTask(task));
        }
        
        boolean allSuccess = true;
        for (String result : results) {
            if (!"处理成功".equals(result)) {
                allSuccess = false;
                break;
            }
        }
        
        if (allSuccess) {
            Status.flagToday(FLAG_RECEIVED);
        }
        return allSuccess;
    }
    
    private static List<String> processYouthTask(YouthTask task) {
        JSONArray forestTasksNew = getForestTasks(task.queryParam);
        return handleForestTasks(forestTasksNew, task.receiveParam, task.name);
    }
    
    private static JSONArray getForestTasks(String queryParam) {
        String response = AntForestRpcCall.queryTaskListV2(queryParam);
        try {
            return new JSONObject(response).getJSONArray("forestTasksNew");
        } catch (JSONException e) {
            Log.error("获取任务列表失败" + e);
            return null;
        }
    }
    
    private static List<String> handleForestTasks(JSONArray forestTasks, String taskType, String taskName) {
        List<String> results = new ArrayList<>();
        
        try {
            if (forestTasks != null && forestTasks.length() > 0) {
                for (int i = 0; i < forestTasks.length(); i++) {
                    JSONObject taskGroup = forestTasks.optJSONObject(i);
                    if (taskGroup == null) {
                        continue;
                    }
                    
                    JSONArray taskInfoList = taskGroup.optJSONArray("taskInfoList");
                    if (taskInfoList == null) {
                        continue;
                    }
                    
                    for (int j = 0; j < taskInfoList.length(); j++) {
                        JSONObject task = taskInfoList.optJSONObject(j);
                        if (task == null) {
                            continue;
                        }
                        
                        JSONObject baseInfo = task.optJSONObject("taskBaseInfo");
                        if (baseInfo == null) {
                            continue;
                        }
                        
                        if (!taskType.equals(baseInfo.optString("taskType"))) {
                            continue;
                        }
                        
                        processSingleYouthTask(baseInfo, taskType, taskName, results);
                    }
                }
            }
        } catch (Throwable e) {
            Log.error( "任务列表解析失败" + e);
            results.add("处理异常");
        }
        
        return results;
    }
    
    private static void processSingleYouthTask(JSONObject baseInfo, String taskType, String taskName, List<String> results) {
        String status = baseInfo.optString("taskStatus");
        
        switch (status) {
            case TASK_RECEIVED:
                Log.forest(PREFIX_PRIVILEGE + "[" + taskName + "]已领取[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
                break;
            case TASK_FINISHED:
                handleYouthTaskAward(taskType, taskName, results);
                break;
        }
    }
    
    private static void handleYouthTaskAward(String taskType, String taskName, List<String> results) {
        try {
            String response = AntForestRpcCall.receiveTaskAwardV2(taskType);
            JSONObject jsonResponse = new JSONObject(response);
            String resultDesc = jsonResponse.optString("desc");
            results.add(resultDesc);
            
            String logMessage = "处理成功".equals(resultDesc) ? "领取成功" : "领取结果：" + resultDesc;
            Log.forest(PREFIX_PRIVILEGE + "[" + taskName + "]" + logMessage+"[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
        } catch (JSONException e) {
            Log.error("奖励领取结果解析失败" + e);
            results.add("处理异常");
        }
    }
    
    public static void studentSignInRedEnvelope() {
        if (!isSignInTimeValid()) {
            Log.record(PREFIX_SIGN + "5点前不执行签到");
            return;
        }
        
        if (Status.hasFlagToday(FLAG_STUDENT_TASK)) {
            Log.record(PREFIX_SIGN + "今日已完成签到");
            return;
        }
        
        try {
            processStudentSignIn();
        } catch (Exception e) {
            Log.error("学生签到异常" + e);
        }
    }
    
    private static boolean isSignInTimeValid() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour >= SIGN_START_HOUR;
    }
    
    private static void processStudentSignIn() {
        String response = AntForestRpcCall.studentQqueryCheckInModel();
        JSONObject result;
        try {
            result = new JSONObject(response);
        } catch (JSONException e) {
            Log.error("学生签到模型解析失败" + e);
            return;
        }
        
        if (!RPC_SUCCESS.equals(result.optString("resultCode"))) {
            Log.record(PREFIX_SIGN + " 查询失败：" + result.optString("resultDesc"));
            return;
        }
        
        JSONObject checkInInfo = result.optJSONObject("studentCheckInInfo");
        if (checkInInfo == null || "DO_TASK".equals(checkInInfo.optString("action"))) {
            Status.flagToday(FLAG_STUDENT_TASK);
            return;
        }
        
        executeStudentSignIn();
    }
    
    private static void executeStudentSignIn() {
        try {
            String tag = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < SIGN_END_HOUR ? "double" : "single";
            String response = AntForestRpcCall.studentCheckin();
            JSONObject result = new JSONObject(response);
            handleSignInResult(result, tag);
        } catch (JSONException e) {
            Log.error("学生签到失败：" + e.getMessage());
        }
    }
    
    private static void handleSignInResult(JSONObject result, String tag) {
        String code = result.optString("resultCode");
        String desc = result.optString("resultDesc");
        
        if (RPC_SUCCESS.equals(code)) {
            Status.flagToday(FLAG_STUDENT_TASK);
            Log.forest(PREFIX_SIGN + tag + desc);
        } else {
            String errorMsg = desc;
            if (desc.contains("不匹配")) {
                errorMsg += "可能账户不符合条件";
            }
            Log.error( PREFIX_SIGN + tag + " 失败：" + errorMsg);
        }
    }
    
    public static class YouthTask {
        public final String queryParam;
        public final String receiveParam;
        public final String name;
        
        public YouthTask(String queryParam, String receiveParam, String name) {
            this.queryParam = queryParam;
            this.receiveParam = receiveParam;
            this.name = name;
        }
    }
}