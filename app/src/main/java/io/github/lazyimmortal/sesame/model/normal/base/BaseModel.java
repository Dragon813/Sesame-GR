package io.github.lazyimmortal.sesame.model.normal.base;

import io.github.lazyimmortal.sesame.hook.CaptchaHook;
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
    private static final BooleanModelField stayAwake = new BooleanModelField("stayAwake", "保持唤醒", true);
    @Getter
    private static final IntegerModelField.MultiplyIntegerModelField checkInterval = new IntegerModelField.MultiplyIntegerModelField("checkInterval", "执行间隔(分钟)", 50, 1, 12 * 60, 60_000);
    @Getter
    private static final ListModelField.ListJoinCommaToStringModelField execAtTimeList = new ListModelField.ListJoinCommaToStringModelField("execAtTimeList", "定时执行(关闭:-1)", ListUtil.newArrayList("065530", "2359", "24"));
    @Getter
    private static final ListModelField.ListJoinCommaToStringModelField wakenAtTimeList = new ListModelField.ListJoinCommaToStringModelField("wakenAtTimeList", "定时唤醒(关闭:-1)", ListUtil.newArrayList("0650", "2350"));
    @Getter
    private static final ListModelField.ListJoinCommaToStringModelField energyTime = new ListModelField.ListJoinCommaToStringModelField("energyTime", "只收能量时间(范围)", ListUtil.newArrayList("0700-0731"));
    @Getter
    private static final ChoiceModelField timedTaskModel = new ChoiceModelField("timedTaskModel", "定时任务模式", TimedTaskModel.SYSTEM, TimedTaskModel.nickNames);
    @Getter
    private static final BooleanModelField timeoutRestart = new BooleanModelField("timeoutRestart", "超时重启", true);
    @Getter
    private static final IntegerModelField.MultiplyIntegerModelField waitWhenException = new IntegerModelField.MultiplyIntegerModelField("waitWhenException", "异常等待时间(分钟)", 60, 0, 24 * 60, 60_000);
    @Getter
    private static final BooleanModelField newRpc = new BooleanModelField("newRpc", "使用新接口(最低支持v10.3.96.8100)", true);
    @Getter
    private static final BooleanModelField debugMode = new BooleanModelField("debugMode", "开启抓包(基于新接口)", false);
    @Getter
    private static final BooleanModelField batteryPerm = new BooleanModelField("batteryPerm", "为支付宝申请后台运行权限", true);
    @Getter
    private static final BooleanModelField recordLog = new BooleanModelField("recordLog", "记录日志", true);
    @Getter
    private static final BooleanModelField showToast = new BooleanModelField("showToast", "气泡提示", true);
    //public static final BooleanModelField closeCaptchaDialogVPN = new BooleanModelField("closeCaptchaDialogVPN", "关闭请检查是否使用了代理软件或VPN", false);
    @Getter
    private static final IntegerModelField toastOffsetY = new IntegerModelField("toastOffsetY", "气泡纵向偏移", 0);
    @Getter
    private static final BooleanModelField enableOnGoing = new BooleanModelField("enableOnGoing", "开启状态栏禁删", false);

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
        modelFields.addField(stayAwake);
        modelFields.addField(checkInterval);
        modelFields.addField(execAtTimeList);
        modelFields.addField(wakenAtTimeList);
        modelFields.addField(energyTime);
        modelFields.addField(timedTaskModel);
        modelFields.addField(timeoutRestart);
        modelFields.addField(waitWhenException);
        modelFields.addField(newRpc);
        modelFields.addField(debugMode);
        modelFields.addField(batteryPerm);
        modelFields.addField(recordLog);
        modelFields.addField(showToast);
        //modelFields.addField(closeCaptchaDialogVPN);
        modelFields.addField(enableOnGoing);
        modelFields.addField(toastOffsetY);
        return modelFields;
    }

    public static void initData() {
        new Thread(() -> {
            try {
                TimeUtil.sleep(5000);
                ProtectEcology.initForest();
                ProtectEcology.initOcean();
            } catch (Exception e) {
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
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    public interface TimedTaskModel {

        int SYSTEM = 0;

        int PROGRAM = 1;

        String[] nickNames = {"系统计时", "程序计时"};

    }

}
