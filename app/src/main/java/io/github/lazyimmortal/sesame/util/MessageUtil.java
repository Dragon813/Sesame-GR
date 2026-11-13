package io.github.lazyimmortal.sesame.util;

import org.json.JSONObject;

import io.github.lazyimmortal.sesame.data.ConfigV2;
import io.github.lazyimmortal.sesame.data.ModelFields;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectModelField;
import io.github.lazyimmortal.sesame.model.base.TaskAlternative;
import io.github.lazyimmortal.sesame.model.task.antMember.AntMember;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

public class MessageUtil {
    private static final String TAG = MessageUtil.class.getSimpleName();
    private static final String UNKNOWN_TAG = "Unknown TAG";

    /** 400000040「不支持rpc调用」；它不等于任务做不了，见 {@link TaskAlternative}。 */
    public static final String CODE_UNSUPPORTED_RPC = "400000040";

    /** 失败响应是否命中"接口不支持调用"（按错误码优先，文案兜底）。 */
    public static boolean isUnsupportedRpc(JSONObject jo) {
        if (jo == null) {
            return false;
        }
        if (CODE_UNSUPPORTED_RPC.equals(jo.optString("code", "").trim())
                || CODE_UNSUPPORTED_RPC.equals(jo.optString("errorCode", "").trim())) {
            return true;
        }
        String desc = jo.optString("desc", "");
        return desc.contains("不支持rpc调用") || desc.contains("不支持RPC调用");
    }

    /**
     * 是否命中安全验证（风控）：服务端返回 error=1009「为了保障您的操作安全，请进行验证后继续。」。
     * 该类失败不代表任务逻辑完成，不输出完成日志，也不计入每日执行次数。
     */
    public static boolean isSecurityVerify(JSONObject jo) {
        if (jo == null) {
            return false;
        }
        return jo.optInt("error") == 1009 || "1009".equals(jo.optString("errorTip", "").trim());
    }

    public static JSONObject newJSONObject(String str) {
        try {
            return new JSONObject(str);
        } catch (Throwable t) {
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
        } catch (Throwable t) {
            Log.i(TAG, "printErrorMessage err:");
            Log.printStackTrace(TAG, t);
        }
    }

    public static Boolean checkMemo(JSONObject jo) {
        return checkMemo(UNKNOWN_TAG, jo);
    }

    public static Boolean checkMemo(String tag, JSONObject jo) {
        try {
            String memo = jo.optString("memo");
            // 部分RPC成功时memo为"成功"(如orchardLazyIndex)
            if (!"SUCCESS".equals(memo) && !"成功".equals(memo)) {
                if (jo.has("memo")) {
                    printErrorMessage(tag, jo, "memo");
                } else {
                    Log.i(tag, jo.toString());
                }
                return false;
            }
            return true;
        } catch (Throwable t) {
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
            } else if (resultCode instanceof String) {
                return checkResultCodeString(tag, jo);
            }
            Log.i(tag, jo.toString());
            return false;
        } catch (Throwable t) {
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
                } else if (jo.has("resultView")) {
                    printErrorMessage(tag, jo, "resultView");
                } else {
                    Log.i(tag, jo.toString());
                }
                return false;
            }
            return true;
        } catch (Throwable t) {
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
                } else {
                    Log.i(tag, jo.toString());
                }
                return false;
            }
            return true;
        } catch (Throwable t) {
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
                } else if (jo.has("errorMessage")) {
                    printErrorMessage(tag, jo, "errorMessage");
                } else if (jo.has("desc")) {
                    printErrorMessage(tag, jo, "desc");
                } else if (jo.has("resultDesc")) {
                    printErrorMessage(tag, jo, "resultDesc");
                } else if (jo.has("resultView")) {
                    printErrorMessage(tag, jo, "resultView");
                } else {
                    Log.i(tag, jo.toString());
                }
                return false;
            }
            return true;
        } catch (Throwable t) {
            Log.i(TAG, "checkSuccess err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    /**
     * 任务列表（listTitle）的中文名称，用于日志与设置界面显示；未登记的返回原值。
     *
     * <p>与 {@link #checkResultCodeAndMarkTaskBlackList} 里各 case 的备注保持一致。
     */
    public static String getTaskListName(String listTitle) {
        if (listTitle == null || listTitle.isEmpty()) {
            return "";
        }
        switch (listTitle) {
            case "AntForestVitalityTaskList":
                return "活力值";
            case "AntForestHuntTaskList":
                return "森林抽抽乐";
            case "MonopolyTaskList":
                return "保护地";
            case "AntFarmDoFarmTaskList":
                return "饲料";
            case "AntFarmDrawMachineTaskList":
                return "装扮抽抽乐";
            case "AntFarmChickenChefTaskList":
                return "小鸡大厨";
            case "AntDodoTaskList":
                return "神奇物种";
            case "AntOceanAntiepTaskList":
                return "海洋普通任务";
            case "AntOceanFishBlackList":
                return "海洋去摸鱼";
            case "AntOrchardTaskList":
                return "肥料";
            case "AntOrchardDrawTaskList":
                return "农场抽抽乐";
            case "GoldenBeansTaskList":
                return "金豆";
            case "AntStallTaskList":
                return "新村";
            case "AntSportsTaskList":
                return "运动";
            case "AntMemberTaskList":
                return "会员";
            case "MemberCreditSesameTaskList":
                return "芝麻信用芝麻粒";
            default:
                return listTitle;
        }
    }

    /**
     * 按任务列表来源输出日志：森林类写森林日志，庄园/农场类写庄园日志，其余写其他日志。
     */
    public static void logByTaskList(String listTitle, String message) {
        switch (listTitle == null ? "" : listTitle) {
            case "AntForestVitalityTaskList":
            case "AntForestHuntTaskList":
            case "MonopolyTaskList":
            case "AntDodoTaskList":
            case "AntOceanAntiepTaskList":
            case "AntOceanFishBlackList":
                Log.forest(message);
                break;
            case "AntFarmDoFarmTaskList":
            case "AntFarmDrawMachineTaskList":
            case "AntFarmChickenChefTaskList":
            case "AntOrchardTaskList":
            case "AntOrchardDrawTaskList":
            case "GoldenBeansTaskList":
                Log.farm(message);
                break;
            default:
                Log.other(message);
        }
    }

    public static void checkResultCodeAndMarkTaskBlackList(String listTitle, String taskTitle, JSONObject jo) {
        try {
            if (jo == null) {
                Log.i(listTitle, "JSON对象为空");
                return;
            }
            //标记是否加黑
            boolean canAddBlackList = false;

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

                //蚂蚁森林保护地任务
                case "MonopolyTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntForestV2", listTitle, "蚂蚁森林保护地任务", taskTitle);
                    }
                    break;

                //庄园饲料任务AntFarm
                case "AntFarmDoFarmTaskList":
                    if (jo.has("memo")) {
                        String memo = jo.optString("memo");
                        if (memo.contains("服务器正在开小差，请稍后再试～")) {
                            canAddBlackList = true;
                        }
                    }
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntFarm", listTitle, "庄园饲料任务", taskTitle);
                    }
                    break;

                //庄园装扮抽抽乐任务AntFarm
                case "AntFarmDrawMachineTaskList":
                    if (jo.has("memo")) {
                        String memo = jo.optString("memo");
                        if (memo.contains("服务器正在开小差，请稍后再试～")) {
                            canAddBlackList = true;
                        }
                    }
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntFarm", listTitle, "庄园装扮抽抽乐任务", taskTitle);
                    }
                    break;

                //庄园小鸡大厨任务AntFarm
                case "AntFarmChickenChefTaskList":
                    if (jo.has("memo")) {
                        String memo = jo.optString("memo");
                        if (memo.contains("服务器正在开小差，请稍后再试～")) {
                            canAddBlackList = true;
                        }
                    }
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntFarm", listTitle, "庄园小鸡大厨任务", taskTitle);
                    }
                    break;

                //神奇物种任务AntDodo
                case "AntDodoTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntDodo", listTitle, "神奇物种任务", taskTitle);
                    }
                    break;

                //神奇海洋普通任务AntOcean
                case "AntOceanAntiepTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntOcean", listTitle, "神奇海洋普通任务", taskTitle);
                    }
                    break;

                //神奇海洋去摸鱼任务AntOcean
                case "AntOceanFishBlackList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntOcean", listTitle, "神奇海洋去摸鱼任务", taskTitle);
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

                //农场抽抽乐任务AntOrchardDrawTime
                case "AntOrchardDrawTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntOrchard", listTitle, "农场抽抽乐任务", taskTitle);
                    }
                    break;

                //农场金豆任务GoldenBeansTaskList
                case "GoldenBeansTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntOrchard", listTitle, "农场金豆任务", taskTitle);
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
                    if (jo.has("errorMsg")) {
                        String errorMsg = jo.optString("errorMsg");
                        if (errorMsg.contains("海豚活动触发不可重试错误")) {
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
                        if (resultView.contains("不是有效的入参") || resultView.contains("存在进行中的生活记录")|| resultView.contains("生活记录模板不存在")) {
                            canAddBlackList = true;
                        }
                    }
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntMember", listTitle, "会员芝麻信用任务芝麻粒", taskTitle);
                    }
                    break;

            }
        } catch (Throwable t) {
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
            Log.record("自动拉黑🏴在[" + TaskListName + "]中添加[" + taskTitle + "]黑名单:" + TaskSelectModelField.getValue());
        } else {
            Log.record("添加" + TaskListName + "黑名单失败：" + taskTitle);
        }
    }

}
