package io.github.lazyimmortal.sesame.model.normal.base;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectAndCountModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectModelField;
import io.github.lazyimmortal.sesame.entity.AlipayrpcRequest;
import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.model.task.antForest.AntForestRpcCall;
import lombok.Getter;

import io.github.lazyimmortal.sesame.data.Model;
import io.github.lazyimmortal.sesame.data.ModelFields;
import io.github.lazyimmortal.sesame.data.ModelGroup;
import io.github.lazyimmortal.sesame.data.modelFieldExt.BooleanModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.IntegerModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.ListModelField;
import io.github.lazyimmortal.sesame.model.task.protectEcology.ProtectEcology;
import io.github.lazyimmortal.sesame.util.*;
import io.github.lazyimmortal.sesame.util.idMap.*;

/**
 * 基础配置模块
 */
public class BaseModel extends Model {
    @Getter
    private static BooleanModelField stayAwake;
    @Getter
    private static IntegerModelField.MultiplyIntegerModelField checkInterval;
    @Getter
    private static ListModelField.ListJoinCommaToStringModelField execAtTimeList;
    @Getter
    private static ListModelField.ListJoinCommaToStringModelField wakenAtTimeList;
    @Getter
    private static ListModelField.ListJoinCommaToStringModelField energyTime;
    @Getter
    private static ChoiceModelField timedTaskModel;
    @Getter
    private static BooleanModelField timeoutRestart;
    @Getter
    private static IntegerModelField.MultiplyIntegerModelField waitWhenException;
    @Getter
    public static IntegerModelField backupConfigDays;
    @Getter
    public static IntegerModelField backupLogDays;
    @Getter
    private static BooleanModelField newRpc;
    @Getter
    private static BooleanModelField debugMode;

    /**
     * 带跳转链接的游戏任务，单个任务每天最多执行多少次（按「任务名[描述] + 列表」独立计数）。
     * 计数的是任务执行次数：一次执行内部提交几次接口都只算 1 次；不同任务互不影响；0=不执行，仅自动收集。
     */
    @Getter
    private static IntegerModelField maxGameTaskSubmitPerDay;

    /** 每个账号独立配置：勾选的「游戏任务 + 任务列表」不执行，默认空=全部执行。 */
    @Getter
    private static SelectModelField miniGameTaskDisallowList;

    @Getter
    private static SelectAndCountModelField rpcRequestList;
    @Getter
    private static SelectModelField rpcRequestTaskList;
    @Getter
    private static BooleanModelField batteryPerm;
    @Getter
    private static BooleanModelField recordLog;
    @Getter
    private static BooleanModelField showToast;
    @Getter
    private static IntegerModelField toastOffsetY;
    @Getter
    private static BooleanModelField enableOnGoing;
    
    @Override
    public String getName() {
        return "基础";
    }
    
    @Override
    public ModelGroup getGroup() {
        return ModelGroup.BASE;
    }
    
    @Override
    public String getEnableFieldName() {
        return "启用模块";
    }
    
    /** 该「任务名称 + 任务列表」是否被当前账号设为不允许执行。 */
    public static boolean isMiniGameTaskDisallowed(String taskTitle, String listTitle) {
        return isMiniGameTaskDisallowed(taskTitle, "", listTitle);
    }

    /** 带描述的判定：新键为「任务名[描述] + 任务列表类」，同时兼容历史只存任务名的旧键。 */
    public static boolean isMiniGameTaskDisallowed(String taskTitle, String desc, String listTitle) {
        Set<String> value = miniGameTaskDisallowList.getValue();
        if (value == null) {
            return false;
        }
        return value.contains(MiniGameTaskListMap.key(MiniGameTaskListMap.name(taskTitle, desc), listTitle))
                || value.contains(MiniGameTaskListMap.key(taskTitle, listTitle));
    }

    public void boot(ClassLoader classLoader) {
        /*// 配置已加载，更新验证码Hook状态
        try {
            CaptchaHook.updateHooks(closeCaptchaDialogVPN.getValue());
            Log.record("✅ 验证码Hook配置已同步");
        } catch (Throwable t) {
            Log.printStackTrace("❌ 验证码Hook配置同步失败", t);
        }*/
    }
    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(stayAwake = new BooleanModelField("stayAwake", "保持唤醒", true));
        modelFields.addField(checkInterval = new IntegerModelField.MultiplyIntegerModelField("checkInterval", "执行间隔(分钟)", 50, 1, 12 * 60, 60_000));
        modelFields.addField(execAtTimeList = new ListModelField.ListJoinCommaToStringModelField("execAtTimeList", "定时执行(关闭:-1)", ListUtil.newArrayList("065530", "2359", "24")));
        modelFields.addField(wakenAtTimeList = new ListModelField.ListJoinCommaToStringModelField("wakenAtTimeList", "定时唤醒(关闭:-1)", ListUtil.newArrayList("0650", "2350")));
        modelFields.addField(energyTime = new ListModelField.ListJoinCommaToStringModelField("energyTime", "只收能量时间(范围)", ListUtil.newArrayList("0700-0731")));
        modelFields.addField(timedTaskModel = new ChoiceModelField("timedTaskModel", "定时任务模式", TimedTaskModel.SYSTEM, TimedTaskModel.nickNames));
        modelFields.addField(timeoutRestart = new BooleanModelField("timeoutRestart", "超时重启", true));
        modelFields.addField(waitWhenException = new IntegerModelField.MultiplyIntegerModelField("waitWhenException", "异常等待时间(分钟)", 60, 0, 24 * 60, 60_000));
        modelFields.addField(backupConfigDays = new IntegerModelField("backupConfigDays", "按天和修改备份配置保存数(滚动覆盖)", 5));
        modelFields.addField(backupLogDays = new IntegerModelField("backupLogDays", "保留之前日志天数", 1));
        modelFields.addField(newRpc = new BooleanModelField("newRpc", "使用新接口(最低支持v10.3.96.8100)", true));
        modelFields.addField(debugMode = new BooleanModelField("debugMode", "开启抓包(含小程序数据)", false));
        modelFields.addField(maxGameTaskSubmitPerDay = new IntegerModelField("maxGameTaskSubmitPerDay", "链接任务单个每日最大执行次数(0=仅收集不执行)", 1, 0, 10));
        modelFields.addField(miniGameTaskDisallowList = new SelectModelField("miniGameTaskDisallowList", "跳转任务 | 不允许执行列表(游戏、第三方)", new LinkedHashSet<>(), MiniGameTaskListMap::getOptions));
        modelFields.addField(rpcRequestList = new SelectAndCountModelField("rpcRequestList", "RPC请求列表及每日执行数(慎用)", new LinkedHashMap<>(), AlipayrpcRequest::getList, "请填写每日执行次数"));
        modelFields.addField(rpcRequestTaskList = new SelectModelField("rpcRequestTaskList", "RPC可选任务列表(长按列表中的项仅移除用，内容需打开rpcResquest.json文件配置)", new LinkedHashSet<>(), AlipayrpcRequest::getList, "长按删除RPC列表项用"));
        modelFields.addField(batteryPerm = new BooleanModelField("batteryPerm", "为支付宝申请后台运行权限", true));
        modelFields.addField(recordLog = new BooleanModelField("recordLog", "记录日志", true));
        modelFields.addField(showToast = new BooleanModelField("showToast", "气泡提示", true));
        modelFields.addField(toastOffsetY = new IntegerModelField("toastOffsetY", "气泡纵向偏移", 0));
        modelFields.addField(enableOnGoing = new BooleanModelField("enableOnGoing", "开启状态栏禁删", false));
        return modelFields;
    }
    
    public static void initData() {
        new Thread(() -> {
            try {
                TimeUtil.sleep(5000);
                ProtectEcology.initForest();
                ProtectEcology.initOcean();
            }
            catch (Exception e) {
                Log.printStackTrace(e);
            }
        }).start();
    }
    
    //public static boolean getcloseCaptchaDialogVPN() {
    //    return closeCaptchaDialogVPN.getValue();
    //}
    
    public static void destroyData() {
        try {
            TreeIdMap.clear();
            ReserveIdMap.clear();
            AnimalIdMap.clear();
            MarathonIdMap.clear();
            NewAncientTreeIdMap.clear();
            BeachIdMap.clear();
            PlantSceneIdMap.clear();
            ForestHuntIdMap.clear();
            MemberCreditSesameTaskListMap.clear();
            AntForestVitalityTaskListMap.clear();
            AntForestHuntTaskListMap.clear();
            AntFarmDoFarmTaskListMap.clear();
            AntFarmDrawMachineTaskListMap.clear();
            AntFarmChickenChefTaskListMap.clear();
            AntDodoTaskListMap.clear();
            AntOceanAntiepTaskListMap.clear();
            AntOceanFishBlackListMap.clear();
            AntOrchardTaskListMap.clear();
            AntOrchardDrawTaskListMap.clear();
            GoldenBeansTaskListMap.clear();
            MonopolyTaskListMap.clear();
            AntStallTaskListMap.clear();
            AntSportsTaskListMap.clear();
            AntMemberTaskListMap.clear();
        }
        catch (Exception e) {
            Log.printStackTrace(e);
        }
    }
    
    public interface TimedTaskModel {
        
        int SYSTEM = 0;
        
        int PROGRAM = 1;
        
        String[] nickNames = {"系统计时", "程序计时"};
        
    }
    
    public static void initRpcRequest() {
        rpcRequestMap.load();
        rpcRequestMap.add("{\"methodName\":\"alipay.antforest.forest.h5.queryMiscInfo\",\"requestData\":[{\"queryBizType\":\"usingProp\",\"source\":\"SELF_HOME\",\"version\":\"20240201\"}]}", "查询森林使用道具(示例)");
        rpcRequestMap.add("{\"methodName\":\"alipay.antforest.forest.h5.updateUserConfig\",\"requestData\":[{\"configMap\":{\"inTeam\":\"Y\"},\"source\":\"chInfo_ch_appcenter__chsub_9patch\"}]}", "切换到组队浇水(示例)");
        rpcRequestMap.add("{\"methodName\":\"alipay.antforest.forest.h5.updateUserConfig\",\"requestData\":[{\"configMap\":{\"inTeam\":\"N\"},\"source\":\"chInfo_ch_appcenter__chsub_9patch\"}]}", "切换到个人主页(示例)");
        
        rpcRequestMap.save();
        
    }
    public static void taskRpcRequest() {
        
        // 1. 获取Map集合，增加空判断避免NPE
        Map<String, Integer> taskRpcList = rpcRequestList.getValue();
        if (taskRpcList == null || taskRpcList.isEmpty()) {
            // 集合为空时直接返回，避免无效遍历
            return;
        }
        // 2. 遍历Map的键值对
        rpcRequestMap.load();
        for (Map.Entry<String, Integer> taskRpc : taskRpcList.entrySet()) {
            // 获取键（待解析的JSON字符串）和值（计数）
            String taskRpcRequestMethodAndData = taskRpc.getKey();
            Integer taskRpcCount = taskRpc.getValue();
            String taskRpcName = rpcRequestMap.get(taskRpcRequestMethodAndData);
            int taskRpcNameTodayCount = Status.getrpcRequestListToday(taskRpcName);
            if (taskRpcNameTodayCount >= taskRpcCount) {
                continue;
            }
            // 3. 解析JSON字符串，处理异常避免崩溃
            JSONObject taskRpcJo = null;
            try {
                //保守执行，不管是否异常均认为执行
                Status.rpcRequestListToday(taskRpcName, taskRpcNameTodayCount+1);
                // 先判空，再解析JSON
                if (taskRpcRequestMethodAndData == null || taskRpcRequestMethodAndData.isEmpty()) {
                    continue; // 跳过空字符串，继续下一次遍历
                }
                taskRpcJo = new JSONObject(taskRpcRequestMethodAndData);
                // 【可选】这里添加解析后的业务逻辑，比如获取JSON中的字段
                String methodName = taskRpcJo.getString("methodName"); // 假设JSON中有method字段
                String requestData = taskRpcJo.getString("requestData");     // 假设JSON中有data字段
                Log.debug("自主调用🈸RPC["+taskRpcName+"]第" + (taskRpcNameTodayCount+1)+"["+taskRpcCount+"]次\n方法：" + methodName + "\n参数：" + requestData);
                //调用接口执行请求
                String taskRpcResult = ApplicationHook.requestString(methodName, requestData);
                Log.debug("自主调用🈸RPC["+taskRpcName+"]返回\n数据：" + taskRpcResult);
            }
            catch (JSONException e) {
                // 捕获JSON解析异常，打印日志而不是崩溃
                e.printStackTrace();
                // 可选：记录错误日志，或跳过当前无效的JSON字符串
                Log.debug("JSON解析失败，字符串内容：" + taskRpcRequestMethodAndData);
            }
        }
    }
}