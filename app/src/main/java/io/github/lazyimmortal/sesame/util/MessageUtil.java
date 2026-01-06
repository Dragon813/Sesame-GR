package io.github.lazyimmortal.sesame.util;

import org.json.JSONObject;

import io.github.lazyimmortal.sesame.data.ConfigV2;
import io.github.lazyimmortal.sesame.data.ModelFields;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectModelField;
import io.github.lazyimmortal.sesame.model.task.antMember.AntMember;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

public class MessageUtil {
    private static final String TAG = MessageUtil.class.getSimpleName();
    private static final String UNKNOWN_TAG = "Unknown TAG";
    
    public static JSONObject newJSONObject(String str) {
        try {
            return new JSONObject(str);
        }
        catch (Throwable t) {
            Log.i(TAG, "newJSONObject err:");
            Log.printStackTrace(TAG, t);
        }
        return null;
    }
    
    public static void printErrorMessage(String tag, JSONObject jo, String errorMessageField) {
        try {
            String errMsg = tag + " error:";
            Log.record(errMsg + jo.getString(errorMessageField));
            Log.i(jo.getString(errorMessageField), jo.toString());
        }
        catch (Throwable t) {
            Log.i(TAG, "printErrorMessage err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    public static Boolean checkMemo(JSONObject jo) {
        return checkMemo(UNKNOWN_TAG, jo);
    }
    
    public static Boolean checkMemo(String tag, JSONObject jo) {
        try {
            if (!"SUCCESS".equals(jo.optString("memo"))) {
                if (jo.has("memo")) {
                    printErrorMessage(tag, jo, "memo");
                }
                else {
                    Log.i(tag, jo.toString());
                }
                return false;
            }
            return true;
        }
        catch (Throwable t) {
            Log.i(TAG, "checkMemo err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    public static Boolean checkResultCode(JSONObject jo) {
        return checkResultCode(UNKNOWN_TAG, jo);
    }
    
    public static Boolean checkResultCode(String tag, JSONObject jo) {
        try {
            /// 添加空值检查
            if (jo == null) {
                Log.i(tag, "JSON对象为空");
                return false;
            }
            
            if (jo.optBoolean("success") && jo.optString("desc").equals("处理成功")) {
                return true;
            }
            
            Object resultCode = jo.opt("resultCode");
            if (resultCode == null) {
                Log.i(tag, jo.toString());
                return false;
            }
            if (resultCode instanceof Integer) {
                return checkResultCodeInteger(tag, jo);
            }
            else if (resultCode instanceof String) {
                return checkResultCodeString(tag, jo);
            }
            Log.i(tag, jo.toString());
            return false;
        }
        catch (Throwable t) {
            Log.i(TAG, "checkResultCode err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    public static Boolean checkResultCodeString(String tag, JSONObject jo) {
        try {
            String resultCode = jo.optString("resultCode");
            if (!resultCode.equalsIgnoreCase("SUCCESS") && !resultCode.equals("100")) {
                if (jo.has("resultDesc")) {
                    printErrorMessage(tag, jo, "resultDesc");
                }
                else if (jo.has("resultView")) {
                    printErrorMessage(tag, jo, "resultView");
                }
                else {
                    Log.i(tag, jo.toString());
                }
                return false;
            }
            return true;
        }
        catch (Throwable t) {
            Log.i(TAG, "checkResultCodeString err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    public static Boolean checkResultCodeInteger(String tag, JSONObject jo) {
        try {
            int resultCode = jo.optInt("resultCode");
            if (resultCode != 200) {
                if (jo.has("resultMsg")) {
                    printErrorMessage(tag, jo, "resultMsg");
                }
                else {
                    Log.i(tag, jo.toString());
                }
                return false;
            }
            return true;
        }
        catch (Throwable t) {
            Log.i(TAG, "checkResultCodeInteger err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    public static Boolean checkSuccess(JSONObject jo) {
        return checkSuccess(UNKNOWN_TAG, jo);
    }
    
    public static Boolean checkSuccess(String tag, JSONObject jo) {
        try {
            if (!jo.optBoolean("success") && !jo.optBoolean("isSuccess")) {
                if (jo.has("errorMsg")) {
                    printErrorMessage(tag, jo, "errorMsg");
                }
                else if (jo.has("errorMessage")) {
                    printErrorMessage(tag, jo, "errorMessage");
                }
                else if (jo.has("desc")) {
                    printErrorMessage(tag, jo, "desc");
                }
                else if (jo.has("resultDesc")) {
                    printErrorMessage(tag, jo, "resultDesc");
                }
                else if (jo.has("resultView")) {
                    printErrorMessage(tag, jo, "resultView");
                }
                else {
                    Log.i(tag, jo.toString());
                }
                return false;
            }
            return true;
        }
        catch (Throwable t) {
            Log.i(TAG, "checkSuccess err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    public static void checkResultCodeAndMarkTaskBlackList(String listTitle, String taskTitle, JSONObject jo) {
        try {
            if (jo == null) {
                Log.i(listTitle, "JSON对象为空");
                return;
            }
            //标记是否加黑
            Boolean canAddBlackList = false;
            
            //共性返回失败关键字
            if (jo.has("desc")) {
                String desc = jo.optString("desc");
                if (desc.contains("不支持rpc调用") || desc.contains("不支持RPC调用")) {
                    canAddBlackList = true;
                }
            }
            
            //这里根据对应任务返回异常的值精准设置拉黑条件
            switch (listTitle) {
                //蚂蚁森林活力值任务AntForestV2
                case "AntForestVitalityTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntForestV2", listTitle, "蚂蚁森林活力值任务", taskTitle);
                    }
                    break;
                    
                //蚂蚁森林抽抽乐任务AntForestV2
                case "AntForestHuntTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntForestV2", listTitle, "蚂蚁森林抽抽乐任务", taskTitle);
                    }
                    break;
                    
                //庄园饲料任务AntFarm
                case "AntFarmDoFarmTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntFarm", listTitle, "庄园饲料任务", taskTitle);
                    }
                    break;
                    
                //庄园装扮抽抽乐任务AntFarm
                case "AntFarmDrawMachineTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntFarm", listTitle, "庄园装扮抽抽乐任务", taskTitle);
                    }
                    break;
                    
                //神奇海洋普通任务AntOcean
                case "AntOceanAntiepTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntOcean", listTitle, "神奇海洋普通任务", taskTitle);
                    }
                    break;
                    
                //农场肥料任务AntOrchard
                case "AntOrchardTaskList":
                    if (jo.has("desc")) {
                        String desc = jo.optString("desc");
                        if (desc.contains("任务全局配置不存在")) {
                            canAddBlackList = true;
                        }
                    }
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntOrchard", listTitle, "农场肥料任务", taskTitle);
                    }
                    break;
                    
                //新村任务AntStall
                case "AntStallTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntStall", listTitle, "新村任务", taskTitle);
                    }
                    break;
                    
                //运动任务AntSports
                case "AntSportsTaskList":
                    if (jo.has("errorCode")) {
                        String errorCode = jo.optString("errorCode");
                        // {"ariverRpcTraceId":"21a4804717677001946607240e1734","errorCode":"TASK_ID_INVALID","errorMsg":"海豚任务id非法","retryable":false,"success":false}
                        if (errorCode.contains("TASK_ID_INVALID")) {
                            canAddBlackList = true;
                        }
                    }
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntSports", listTitle, "运动任务", taskTitle);
                    }
                    break;
                    
                //会员任务AntMember
                case "AntMemberTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntMember", listTitle, "会员任务", taskTitle);
                    }
                    break;
                    
                //会员芝麻信用任务芝麻粒AntMember
                case "MemberCreditSesameTaskList":
                    if (jo.has("resultView")) {
                        String resultView = jo.optString("resultView");
                        if (resultView.contains("不是有效的入参")) {
                            canAddBlackList = true;
                        }
                    }
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntMember", listTitle, "会员芝麻信用任务芝麻粒", taskTitle);
                    }
                    break;

            }
        }
        catch (Throwable t) {
            Log.i(TAG, "checkSuccess err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    public static void MarkTaskBlackList(String ModelFieldsType, String listTitle, String TaskListName, String taskTitle) {
        ConfigV2 config = ConfigV2.INSTANCE;
        ModelFields TaskModelFields = config.getModelFieldsMap().get(ModelFieldsType);
        SelectModelField TaskSelectModelField = (SelectModelField) TaskModelFields.get(listTitle);
        if (TaskSelectModelField == null) {
            Log.record("添加" + TaskListName + "黑名单失败：" + taskTitle);
            return;
        }
        if (!TaskSelectModelField.contains(taskTitle)) {
            TaskSelectModelField.add(taskTitle, 0); // 数组类型忽略count，传0
        }
        if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
            Log.record("添加" + TaskListName + "黑名单: " + TaskSelectModelField.getValue());
        }
        else {
            Log.record("添加" + TaskListName + "黑名单失败：" + taskTitle);
        }
    }
    
}
