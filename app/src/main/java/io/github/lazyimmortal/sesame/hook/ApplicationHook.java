package io.github.lazyimmortal.sesame.hook;



import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.app.AlertDialog;

import de.robv.android.xposed.XC_MethodHook;

import androidx.fragment.app.Fragment;

import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Field;
import java.util.Objects;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.lazyimmortal.sesame.BuildConfig;
import io.github.lazyimmortal.sesame.data.ConfigV2;
import io.github.lazyimmortal.sesame.data.Model;
import io.github.lazyimmortal.sesame.data.RunType;
import io.github.lazyimmortal.sesame.data.TokenConfig;
import io.github.lazyimmortal.sesame.data.ViewAppInfo;
import io.github.lazyimmortal.sesame.data.task.BaseTask;
import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.entity.AlipayVersion;
import io.github.lazyimmortal.sesame.entity.FriendWatch;
import io.github.lazyimmortal.sesame.entity.RpcEntity;
import io.github.lazyimmortal.sesame.model.base.TaskCommon;
import io.github.lazyimmortal.sesame.model.extensions.TestRpc;
import io.github.lazyimmortal.sesame.model.normal.base.BaseModel;
import io.github.lazyimmortal.sesame.model.task.antMember.AntMemberRpcCall;
import io.github.lazyimmortal.sesame.rpc.bridge.NewRpcBridge;
import io.github.lazyimmortal.sesame.rpc.bridge.OldRpcBridge;
import io.github.lazyimmortal.sesame.rpc.bridge.RpcBridge;
import io.github.lazyimmortal.sesame.rpc.bridge.RpcVersion;
import io.github.lazyimmortal.sesame.rpc.intervallimit.RpcIntervalLimit;
import io.github.lazyimmortal.sesame.util.ClassUtil;
import io.github.lazyimmortal.sesame.util.FileUtil;
import io.github.lazyimmortal.sesame.util.LibraryUtil;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.NotificationUtil;
import io.github.lazyimmortal.sesame.util.PermissionUtil;
import io.github.lazyimmortal.sesame.util.Statistics;
import io.github.lazyimmortal.sesame.util.Status;
import io.github.lazyimmortal.sesame.util.StringUtil;
import io.github.lazyimmortal.sesame.util.TimeUtil;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;
import lombok.Getter;

public class ApplicationHook implements IXposedHookLoadPackage {
    
    private static final String TAG = ApplicationHook.class.getSimpleName();
    
    @Getter
    private static final String modelVersion = BuildConfig.VERSION_NAME;
    
    private static final Map<Object, Object[]> rpcHookMap = new ConcurrentHashMap<>();
    
    private static final Map<String, PendingIntent> wakenAtTimeAlarmMap = new ConcurrentHashMap<>();
    
    @Getter
    private static ClassLoader classLoader = null;
    
    @Getter
    private static Object microApplicationContextObject = null;
    
    @Getter
    @SuppressLint("StaticFieldLeak")
    private static Context context = null;
    
    @Getter
    private static AlipayVersion alipayVersion = new AlipayVersion("");
    
    @Getter
    private static volatile boolean hooked = false;
    
    private static volatile boolean init = false;
    
    private static volatile Calendar dayCalendar;
    
    @Getter
    private static volatile boolean offline = false;
    
    @Getter
    private static final AtomicInteger reLoginCount = new AtomicInteger(0);
    
    @SuppressLint("StaticFieldLeak")
    private static Service service;
    
    @Getter
    private static Handler mainHandler;
    
    private static BaseTask mainTask;
    
    private static RpcBridge rpcBridge;
    
    @Getter
    private static RpcVersion rpcVersion;
    
    private static PowerManager.WakeLock wakeLock;
    
    private static PendingIntent alarm0Pi;
    
    private static XC_MethodHook.Unhook rpcRequestUnhook;
    
    private static XC_MethodHook.Unhook rpcResponseUnhook;
    
    public static void setOffline(boolean offline) {
        ApplicationHook.offline = offline;
    }
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook验证码关闭功能
        /*
        try {
            CaptchaHook.INSTANCE.setupHook(classLoader);
            Log.i(TAG+"验证码Hook系统已初始化");
        } catch (Throwable t) {
            Log.printStackTrace(TAG+"验证码Hook初始化失败", t);
        }*/
        if (Objects.equals(BuildConfig.APPLICATION_ID, lpparam.packageName)) {
            try {
                XposedHelpers.callStaticMethod(lpparam.classLoader.loadClass(ViewAppInfo.class.getName()), "setRunTypeByCode", RunType.MODEL.getCode());
            }
            catch (ClassNotFoundException e) {
                Log.printStackTrace(e);
            }
        }
        else if (ClassUtil.PACKAGE_NAME.equals(lpparam.packageName) && ClassUtil.PACKAGE_NAME.equals(lpparam.processName)) {
            if (hooked) {
                return;
            }
            classLoader = lpparam.classLoader;
            //Log.record("closeCaptchaDialogVPN"+BaseModel.getcloseCaptchaDialogVPN());
            //if(BaseModel.getcloseCaptchaDialogVPN()){
            //    hookAlipayAlertDialog(lpparam.classLoader);
                
            //}

            
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    context = (Context) param.args[0];
                    try {
                        alipayVersion = new AlipayVersion(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
                    }
                    catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                    super.afterHookedMethod(param);
                }
            });
            try {
                XposedHelpers.findAndHookMethod("com.alipay.mobile.nebulaappproxy.api.rpc.H5AppRpcUpdate", classLoader, "matchVersion", classLoader.loadClass(ClassUtil.H5PAGE_NAME), Map.class, String.class, XC_MethodReplacement.returnConstant(false));
                Log.i(TAG, "hook matchVersion successfully");
            }
            catch (Throwable t) {
                Log.i(TAG, "hook matchVersion err:");
                Log.printStackTrace(TAG, t);
            }
            try {
                XposedHelpers.findAndHookMethod("com.alipay.mobile.quinox.LauncherActivity", classLoader, "onResume", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Log.i(TAG, "Activity onResume");
                        String targetUid = getUserId();
                        if (targetUid == null) {
                            Log.record("用户未登录");
                            Toast.show("用户未登录");
                            return;
                        }
                        if (!init) {
                            if (initHandler(true)) {
                                init = true;
                            }
                            return;
                        }
                        String currentUid = UserIdMap.getCurrentUid();
                        if (!targetUid.equals(currentUid)) {
                            if (currentUid != null) {
                                initHandler(true);
                                Log.record("用户已切换");
                                Toast.show("用户已切换");
                                return;
                            }
                            UserIdMap.initUser(targetUid);
                        }
                        if (offline) {
                            offline = false;
                            execHandler();
                            ((Activity) param.thisObject).finish();
                            Log.i(TAG, "Activity reLogin");
                        }
                    }
                });
                Log.i(TAG, "hook login successfully");
            }
            catch (Throwable t) {
                Log.i(TAG, "hook login err:");
                Log.printStackTrace(TAG, t);
            }
            try {
                XposedHelpers.findAndHookMethod("android.app.Service", classLoader, "onCreate", new XC_MethodHook() {
                    
                    @SuppressLint({"WakelockTimeout", "UnsafeDynamicallyLoadedCode"})
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Service appService = (Service) param.thisObject;
                        if (!ClassUtil.CURRENT_USING_SERVICE.equals(appService.getClass().getCanonicalName())) {
                            return;
                        }
                        Log.i(TAG, "Service onCreate");
                        context = appService.getApplicationContext();
                        System.load(LibraryUtil.getLibSesamePath(context));
                        service = appService;
                        mainHandler = new Handler(Looper.getMainLooper());
                        mainTask = BaseTask.newInstance("MAIN_TASK", new Runnable() {
                            
                            private volatile long lastExecTime = 0;
                            
                            @Override
                            public void run() {
                                if (!init) {
                                    return;
                                }
                                Log.record("应用版本：" + alipayVersion.getVersionString());
                                Log.record("模块版本：" + modelVersion);
                                Log.record("开始执行");
                                try {
                                    int checkInterval = BaseModel.getCheckInterval().getValue();
                                    if (lastExecTime + 2000 > System.currentTimeMillis()) {
                                        Log.record("执行间隔较短，跳过执行");
                                        execDelayedHandler(checkInterval);
                                        return;
                                    }
                                    updateDay();
                                    String targetUid = getUserId();
                                    String currentUid = UserIdMap.getCurrentUid();
                                    if (targetUid == null || currentUid == null) {
                                        Log.record("用户为空，放弃执行");
                                        reLogin();
                                        return;
                                    }
                                    if (!targetUid.equals(currentUid)) {
                                        Log.record("开始切换用户");
                                        Toast.show("开始切换用户");
                                        reLogin();
                                        return;
                                    }
                                    lastExecTime = System.currentTimeMillis();
                                    try {
                                        FutureTask<Boolean> checkTask = new FutureTask<>(AntMemberRpcCall::check);
                                        Thread checkThread = new Thread(checkTask);
                                        checkThread.start();
                                        if (!checkTask.get(10, TimeUnit.SECONDS)) {
                                            long waitTime = 10000 - System.currentTimeMillis() + lastExecTime;
                                            if (waitTime > 0) {
                                                Thread.sleep(waitTime);
                                            }
                                            Log.record("执行失败：检查超时");
                                            reLogin();
                                            return;
                                        }
                                        reLoginCount.set(0);
                                    }
                                    catch (InterruptedException | ExecutionException | TimeoutException e) {
                                        Log.record("执行失败：检查中断");
                                        reLogin();
                                        return;
                                    }
                                    catch (Exception e) {
                                        Log.record("执行失败：检查异常");
                                        reLogin();
                                        Log.printStackTrace(TAG, e);
                                        return;
                                    }
                                    TaskCommon.update();
                                    ModelTask.startAllTask(false);
                                    lastExecTime = System.currentTimeMillis();
                                    
                                    try {
                                        List<String> execAtTimeList = BaseModel.getExecAtTimeList().getValue();
                                        if (execAtTimeList != null) {
                                            Calendar lastExecTimeCalendar = TimeUtil.getCalendarByTimeMillis(lastExecTime);
                                            Calendar nextExecTimeCalendar = TimeUtil.getCalendarByTimeMillis(lastExecTime + checkInterval);
                                            for (String execAtTime : execAtTimeList) {
                                                Calendar execAtTimeCalendar = TimeUtil.getTodayCalendarByTimeStr(execAtTime);
                                                if (execAtTimeCalendar != null && lastExecTimeCalendar.compareTo(execAtTimeCalendar) < 0 && nextExecTimeCalendar.compareTo(execAtTimeCalendar) > 0) {
                                                    Log.record("设置定时执行:" + execAtTime);
                                                    execDelayedHandler(execAtTimeCalendar.getTimeInMillis() - lastExecTime);
                                                    FileUtil.clearLog();
                                                    return;
                                                }
                                            }
                                        }
                                    }
                                    catch (Exception e) {
                                        Log.i(TAG, "execAtTime err:");
                                        Log.printStackTrace(TAG, e);
                                    }
                                    
                                    execDelayedHandler(checkInterval);
                                    FileUtil.clearLog();
                                }
                                catch (Exception e) {
                                    Log.record("执行异常:");
                                    Log.printStackTrace(e);
                                }
                            }
                        });
                        registerBroadcastReceiver(appService);
                        dayCalendar = Calendar.getInstance();
                        Statistics.load();
                        FriendWatch.load();
                        if (initHandler(true)) {
                            init = true;
                        }
                    }
                });
                Log.i(TAG, "hook service onCreate successfully");
            }
            catch (Throwable t) {
                Log.i(TAG, "hook service onCreate err:");
                Log.printStackTrace(TAG, t);
            }
            try {
                XposedHelpers.findAndHookMethod("android.app.Service", classLoader, "onDestroy", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Service service = (Service) param.thisObject;
                        if (!ClassUtil.CURRENT_USING_SERVICE.equals(service.getClass().getCanonicalName())) {
                            return;
                        }
                        Log.record("支付宝前台服务被销毁");
                        NotificationUtil.updateStatusText("支付宝前台服务被销毁");
                        destroyHandler(true);
                        FriendWatch.unload();
                        Statistics.unload();
                        restartByBroadcast();
                    }
                });
            }
            catch (Throwable t) {
                Log.i(TAG, "hook service onDestroy err:");
                Log.printStackTrace(TAG, t);
            }
            try {
                XposedHelpers.findAndHookMethod("com.alipay.mobile.common.fgbg.FgBgMonitorImpl", classLoader, "isInBackground", XC_MethodReplacement.returnConstant(false));
            }
            catch (Throwable t) {
                Log.i(TAG, "hook FgBgMonitorImpl method 1 err:");
                Log.printStackTrace(TAG, t);
            }
            try {
                XposedHelpers.findAndHookMethod("com.alipay.mobile.common.fgbg.FgBgMonitorImpl", classLoader, "isInBackground", boolean.class, XC_MethodReplacement.returnConstant(false));
            }
            catch (Throwable t) {
                Log.i(TAG, "hook FgBgMonitorImpl method 2 err:");
                Log.printStackTrace(TAG, t);
            }
            try {
                XposedHelpers.findAndHookMethod("com.alipay.mobile.common.fgbg.FgBgMonitorImpl", classLoader, "isInBackgroundV2", XC_MethodReplacement.returnConstant(false));
            }
            catch (Throwable t) {
                Log.i(TAG, "hook FgBgMonitorImpl method 3 err:");
                Log.printStackTrace(TAG, t);
            }
            try {
                XposedHelpers.findAndHookMethod("com.alipay.mobile.common.transport.utils.MiscUtils", classLoader, "isAtFrontDesk", classLoader.loadClass("android.content.Context"), XC_MethodReplacement.returnConstant(true));
                Log.i(TAG, "hook MiscUtils successfully");
            }
            catch (Throwable t) {
                Log.i(TAG, "hook MiscUtils err:");
                Log.printStackTrace(TAG, t);
            }
            hooked = true;
            Log.i(TAG, "load success: " + lpparam.packageName);
        }
    }
    
    private void hookAlipayAlertDialog(ClassLoader classLoader) {
        try {
            // 目标：Hook支付宝验证码/提示类对话框（CaptchaDialog）
            Class<?> alertDialogClass = XposedHelpers.findClass("com.alipay.rdssecuritysdk.v3.captcha.view.CaptchaDialog", classLoader);
            /*
            // 兼容系统AlertDialog和androidx版本
            Class<?> alertDialogClass;
            try {
                // 先尝试系统原生AlertDialog
                alertDialogClass = XposedHelpers.findClass("android.app.AlertDialog", classLoader);
            } catch (Throwable e) {
                // 支付宝可能用androidx兼容包
                alertDialogClass = XposedHelpers.findClass("androidx.appcompat.app.AlertDialog", classLoader);
            }*/
            // 置空，不执行原方法
            //XposedHelpers.findAndHookMethod(alertDialogClass, "show", XC_MethodReplacement.DO_NOTHING );
            XposedHelpers.findAndHookMethod(alertDialogClass, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    
                    
                    // 1. 基础信息初始化（避免空指针）
                    Object dialogObj = param.thisObject;
                    String className = dialogObj.getClass().getName();
                    StringBuilder dialogAllInfo = new StringBuilder();
                    dialogAllInfo.append("===== 支付宝CaptchaDialog完整信息 =====\n");
                    
                    // 2. 打印对话框核心类信息
                    dialogAllInfo.append("对话框类名：").append(dialogObj.getClass().getName()).append("\n");
                    dialogAllInfo.append("父类名：").append(dialogObj.getClass().getSuperclass().getName()).append("\n");
                    if (className.contains("alipay")) {
                    // 3. 获取对话框上下文（反射，兼容自定义Dialog）
                    try {
                        Field mContextField = dialogObj.getClass().getSuperclass().getDeclaredField("mContext");
                        mContextField.setAccessible(true);
                        Context context = (Context) mContextField.get(dialogObj);
                        dialogAllInfo.append("所属上下文：").append(context != null ? context.getClass().getName() : "null").append("\n");
                    } catch (Exception e) {
                        dialogAllInfo.append("所属上下文：获取失败 - ").append(e.getMessage()).append("\n");
                    }
                    
                    // 4. 强转为Dialog（兼容非AlertDialog的自定义Dialog）
                    Dialog dialog = null;
                    if (dialogObj instanceof Dialog) {
                        dialog = (Dialog) dialogObj;
                    } else {
                        // 若不是Dialog子类，尝试反射获取Dialog实例（极端场景）
                        try {
                            Field dialogField = dialogObj.getClass().getDeclaredField("mDialog");
                            dialogField.setAccessible(true);
                            dialog = (Dialog) dialogField.get(dialogObj);
                        } catch (Exception e) {
                            dialogAllInfo.append("转换Dialog失败：").append(e.getMessage()).append("\n");
                        }
                    }
                    
                    if (dialog != null) {
                        // 5. 获取系统标准ID的元素（标题、消息、按钮）
                        // 5.1 标题（android.R.id.title）
                        TextView titleView = dialog.findViewById(android.R.id.title);
                        String title = titleView != null ? titleView.getText().toString().trim() : "无标题/未找到系统标题ID";
                        dialogAllInfo.append("系统标题（title）：").append(title).append("\n");
                        
                        // 5.2 消息文本（android.R.id.message）
                        TextView messageView = dialog.findViewById(android.R.id.message);
                        String message = messageView != null ? messageView.getText().toString().trim() : "无消息/未找到系统消息ID";
                        dialogAllInfo.append("系统消息（message）：").append(message).append("\n");
                        
                        // 5.3 所有按钮（button1=确定、button2=取消、button3=中性）
                        TextView btn1 = dialog.findViewById(android.R.id.button1);
                        String btn1Text = btn1 != null ? btn1.getText().toString().trim() : "无确定按钮";
                        dialogAllInfo.append("确定按钮（button1）：").append(btn1Text).append("\n");
                        
                        TextView btn2 = dialog.findViewById(android.R.id.button2);
                        String btn2Text = btn2 != null ? btn2.getText().toString().trim() : "无取消按钮";
                        dialogAllInfo.append("取消按钮（button2）：").append(btn2Text).append("\n");
                        
                        TextView btn3 = dialog.findViewById(android.R.id.button3);
                        String btn3Text = btn3 != null ? btn3.getText().toString().trim() : "无中性按钮";
                        dialogAllInfo.append("中性按钮（button3）：").append(btn3Text).append("\n");
                        
                        // 6. 遍历Dialog根布局，获取所有TextView文本（适配自定义布局）
                        dialogAllInfo.append("===== 自定义布局所有TextView内容 =====\n");
                        View rootView = dialog.getWindow().getDecorView().getRootView();
                        collectAllTextViewText(rootView, dialogAllInfo);
                    } else {
                        dialogAllInfo.append("Dialog实例为空，无法获取控件信息\n");
                    }
                    
                    // 7. 打印完整日志（核心：所有元素内容）
                    Log.i(TAG, dialogAllInfo.toString());
                    //Log.record(dialogAllInfo.toString());
                    
                    // 8. 阻止对话框显示（无论内容是什么，都拦截；也可加文本判断）
                    if (dialogAllInfo.length() > 0) { // 先判断是否有内容
                        Log.record("页面内容："+dialogAllInfo.toString());
                        if(dialogAllInfo.toString().contains("请检查是否使用了代理软件或VPN")||dialogAllInfo.toString().contains("访问被拒绝")){
                            Log.record("包含指定字符");
                            param.setResult(null);
                        }
                    }
                    Log.i(TAG, "已拦截CaptchaDialog显示");
                    Log.record("已拦截CaptchaDialog显示");
                }}
            });
            
            Log.i(TAG, "支付宝CaptchaDialog Hook成功（类加载完成）");
            Log.record("支付宝CaptchaDialog Hook成功（类加载完成）");
        } catch (Throwable t) {
            Log.error(t.toString());
            Log.record("支付宝CaptchaDialog Hook失败：" + t.getMessage());
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 递归遍历View树，收集所有TextView的文本内容（适配自定义布局）
     * @param rootView 根布局
     * @param info 拼接日志的StringBuilder
     */
    private void collectAllTextViewText(View rootView, StringBuilder info) {
        if (rootView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) rootView;
            // 遍历ViewGroup的所有子View
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                collectAllTextViewText(child, info); // 递归
            }
        } else if (rootView instanceof TextView) {
            // 是TextView，记录ID（若有）和文本
            TextView textView = (TextView) rootView;
            String viewId = textView.getId() != View.NO_ID ?
                            rootView.getResources().getResourceEntryName(textView.getId()) : "无ID";
            String text = textView.getText().toString().trim();
            if (!text.isEmpty()) { // 只记录非空文本
                info.append("TextView(ID: ").append(viewId).append(")：").append(text).append("\n");
            }
        }
    }
    
    private static void setWakenAtTimeAlarm() {
        try {
            unsetWakenAtTimeAlarm();
            try {
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent("com.eg.android.AlipayGphone.sesame.execute"), getPendingIntentFlag());
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                if (setAlarmTask(calendar.getTimeInMillis(), pendingIntent)) {
                    alarm0Pi = pendingIntent;
                    Log.record("设置定时唤醒:0|000000");
                }
            }
            catch (Exception e) {
                Log.i(TAG, "setWakenAt0 err:");
                Log.printStackTrace(TAG, e);
            }
            List<String> wakenAtTimeList = BaseModel.getWakenAtTimeList().getValue();
            if (wakenAtTimeList != null && !wakenAtTimeList.isEmpty()) {
                Calendar nowCalendar = Calendar.getInstance();
                for (int i = 1, len = wakenAtTimeList.size(); i < len; i++) {
                    try {
                        String wakenAtTime = wakenAtTimeList.get(i);
                        Calendar wakenAtTimeCalendar = TimeUtil.getTodayCalendarByTimeStr(wakenAtTime);
                        if (wakenAtTimeCalendar != null) {
                            if (wakenAtTimeCalendar.compareTo(nowCalendar) > 0) {
                                PendingIntent wakenAtTimePendingIntent = PendingIntent.getBroadcast(context, i, new Intent("com.eg.android.AlipayGphone.sesame.execute"), getPendingIntentFlag());
                                if (setAlarmTask(wakenAtTimeCalendar.getTimeInMillis(), wakenAtTimePendingIntent)) {
                                    String wakenAtTimeKey = i + "|" + wakenAtTime;
                                    wakenAtTimeAlarmMap.put(wakenAtTimeKey, wakenAtTimePendingIntent);
                                    Log.record("设置定时唤醒:" + wakenAtTimeKey);
                                }
                            }
                        }
                    }
                    catch (Exception e) {
                        Log.i(TAG, "setWakenAtTime err:");
                        Log.printStackTrace(TAG, e);
                    }
                }
            }
        }
        catch (Exception e) {
            Log.i(TAG, "setWakenAtTimeAlarm err:");
            Log.printStackTrace(TAG, e);
        }
    }
    
    private static void unsetWakenAtTimeAlarm() {
        try {
            for (Map.Entry<String, PendingIntent> entry : wakenAtTimeAlarmMap.entrySet()) {
                try {
                    String wakenAtTimeKey = entry.getKey();
                    PendingIntent wakenAtTimePendingIntent = entry.getValue();
                    if (unsetAlarmTask(wakenAtTimePendingIntent)) {
                        wakenAtTimeAlarmMap.remove(wakenAtTimeKey);
                        Log.record("取消定时唤醒:" + wakenAtTimeKey);
                    }
                }
                catch (Exception e) {
                    Log.i(TAG, "unsetWakenAtTime err:");
                    Log.printStackTrace(TAG, e);
                }
            }
            try {
                if (unsetAlarmTask(alarm0Pi)) {
                    alarm0Pi = null;
                    Log.record("取消定时唤醒:0|000000");
                }
            }
            catch (Exception e) {
                Log.i(TAG, "unsetWakenAt0 err:");
                Log.printStackTrace(TAG, e);
            }
        }
        catch (Exception e) {
            Log.i(TAG, "unsetWakenAtTimeAlarm err:");
            Log.printStackTrace(TAG, e);
        }
    }
    
    @SuppressLint("WakelockTimeout")
    private synchronized Boolean initHandler(Boolean force) {
        if (service == null) {
            return false;
        }
        destroyHandler(force);
        try {
            if (force) {
                String userId = getUserId();
                if (userId == null) {
                    Log.record("用户未登录");
                    Toast.show("用户未登录");
                    return false;
                }
                if (!PermissionUtil.checkAlarmPermissions()) {
                    Log.record("支付宝无闹钟权限");
                    mainHandler.postDelayed(() -> {
                        if (!PermissionUtil.checkOrRequestAlarmPermissions(context)) {
                            android.widget.Toast.makeText(context, "请授予支付宝使用闹钟权限", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }, 2000);
                    return false;
                }
                UserIdMap.initUser(userId);
                Model.initAllModel();
                Log.record("模块版本：" + modelVersion);
                Log.record("开始加载");
                ConfigV2.load(userId);
                if (!Model.getModel(BaseModel.class).getEnableField().getValue()) {
                    Log.record("芝麻粒已禁用");
                    Toast.show("芝麻粒已禁用");
                    return false;
                }
                if (BaseModel.getBatteryPerm().getValue() && !init && !PermissionUtil.checkBatteryPermissions()) {
                    Log.record("支付宝无始终在后台运行权限");
                    mainHandler.postDelayed(() -> {
                        if (!PermissionUtil.checkOrRequestBatteryPermissions(context)) {
                            android.widget.Toast.makeText(context, "请授予支付宝终在后台运行权限", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }, 2000);
                }
                if (BaseModel.getNewRpc().getValue()) {
                    rpcBridge = new NewRpcBridge();
                }
                else {
                    rpcBridge = new OldRpcBridge();
                }
                rpcBridge.load();
                rpcVersion = rpcBridge.getVersion();
                if (BaseModel.getStayAwake().getValue()) {
                    try {
                        PowerManager pm = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
                        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, service.getClass().getName());
                        wakeLock.acquire();
                    }
                    catch (Throwable t) {
                        Log.printStackTrace(t);
                    }
                }
                setWakenAtTimeAlarm();
                if (BaseModel.getNewRpc().getValue() && BaseModel.getDebugMode().getValue()) {
                    try {
                        rpcRequestUnhook = XposedHelpers.findAndHookMethod("com.alibaba.ariver.commonability.network.rpc.RpcBridgeExtension", classLoader, "rpc", String.class, boolean.class, boolean.class, String.class, classLoader.loadClass(ClassUtil.JSON_OBJECT_NAME), String.class,
                                classLoader.loadClass(ClassUtil.JSON_OBJECT_NAME), boolean.class, boolean.class, int.class, boolean.class, String.class, classLoader.loadClass("com.alibaba.ariver.app.api.App"), classLoader.loadClass("com.alibaba.ariver.app.api.Page"), classLoader.loadClass("com.alibaba.ariver.engine.api.bridge.model.ApiContext"), classLoader.loadClass("com.alibaba.ariver.engine.api.bridge.extension" + ".BridgeCallback"), new XC_MethodHook() {
                            
                            @SuppressLint("WakelockTimeout")
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Object[] args = param.args;
                                Object object = args[15];
                                Object[] recordArray = new Object[4];
                                recordArray[0] = System.currentTimeMillis();
                                recordArray[1] = args[0];
                                recordArray[2] = args[4];
                                rpcHookMap.put(object, recordArray);
                            }
                            
                            @SuppressLint("WakelockTimeout")
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                Object object = param.args[15];
                                Object[] recordArray = rpcHookMap.remove(object);
                                if (recordArray != null) {
                                    Log.debug("记录\n时间: " + recordArray[0] + "\n方法: " + recordArray[1] + "\n参数: " + recordArray[2] + "\n数据: " + recordArray[3] + "\n");
                                }
                                else {
                                    Log.debug("删除记录ID: " + object.hashCode());
                                }
                            }
                            
                        });
                        Log.i(TAG, "hook record request successfully");
                    }
                    catch (Throwable t) {
                        Log.i(TAG, "hook record request err:");
                        Log.printStackTrace(TAG, t);
                    }
                    try {
                        rpcResponseUnhook = XposedHelpers.findAndHookMethod("com.alibaba.ariver.engine.common.bridge.internal.DefaultBridgeCallback", classLoader, "sendJSONResponse", classLoader.loadClass(ClassUtil.JSON_OBJECT_NAME), new XC_MethodHook() {
                            
                            @SuppressLint("WakelockTimeout")
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Object object = param.thisObject;
                                Object[] recordArray = rpcHookMap.get(object);
                                if (recordArray != null) {
                                    recordArray[3] = String.valueOf(param.args[0]);
                                }
                            }
                            
                        });
                        Log.i(TAG, "hook record response successfully");
                    }
                    catch (Throwable t) {
                        Log.i(TAG, "hook record response err:");
                        Log.printStackTrace(TAG, t);
                    }
                }
                NotificationUtil.start(service);
                Model.bootAllModel(classLoader);
                Status.load();
                TokenConfig.load();
                updateDay();
                BaseModel.initData();
                Log.record("加载完成");
                Toast.show("芝麻粒加载成功");
            }
            offline = false;
            execHandler();
            return true;
        }
        catch (Throwable th) {
            Log.i(TAG, "startHandler err:");
            Log.printStackTrace(TAG, th);
            Toast.show("芝麻粒加载失败");
            return false;
        }
    }
    
    private synchronized static void destroyHandler(Boolean force) {
        try {
            if (force) {
                if (service != null) {
                    stopHandler();
                    BaseModel.destroyData();
                    Status.unload();
                    NotificationUtil.stop();
                    RpcIntervalLimit.clearIntervalLimit();
                    ConfigV2.unload();
                    Model.destroyAllModel();
                    UserIdMap.unload();
                }
                if (rpcResponseUnhook != null) {
                    try {
                        rpcResponseUnhook.unhook();
                    }
                    catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                }
                if (rpcRequestUnhook != null) {
                    try {
                        rpcRequestUnhook.unhook();
                    }
                    catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                }
                if (wakeLock != null) {
                    wakeLock.release();
                    wakeLock = null;
                }
                if (rpcBridge != null) {
                    rpcVersion = null;
                    rpcBridge.unload();
                    rpcBridge = null;
                }
            }
            else {
                ModelTask.stopAllTask();
            }
        }
        catch (Throwable th) {
            Log.i(TAG, "stopHandler err:");
            Log.printStackTrace(TAG, th);
        }
    }
    
    private static void execHandler() {
        mainTask.startTask(false);
    }
    
    private static void execDelayedHandler(long delayMillis) {
        mainHandler.postDelayed(() -> mainTask.startTask(false), delayMillis);
        try {
            NotificationUtil.updateNextExecText(System.currentTimeMillis() + delayMillis);
        }
        catch (Exception e) {
            Log.printStackTrace(e);
        }
    }
    
    private static void stopHandler() {
        mainTask.stopTask();
        ModelTask.stopAllTask();
    }
    
    public static void updateDay() {
        Calendar nowCalendar = Calendar.getInstance();
        try {
            int nowYear = nowCalendar.get(Calendar.YEAR);
            int nowMonth = nowCalendar.get(Calendar.MONTH);
            int nowDay = nowCalendar.get(Calendar.DAY_OF_MONTH);
            if (dayCalendar.get(Calendar.YEAR) != nowYear || dayCalendar.get(Calendar.MONTH) != nowMonth || dayCalendar.get(Calendar.DAY_OF_MONTH) != nowDay) {
                dayCalendar = (Calendar) nowCalendar.clone();
                dayCalendar.set(Calendar.HOUR_OF_DAY, 0);
                dayCalendar.set(Calendar.MINUTE, 0);
                dayCalendar.set(Calendar.SECOND, 0);
                Log.record("日期更新为：" + nowYear + "-" + (nowMonth + 1) + "-" + nowDay);
                setWakenAtTimeAlarm();
            }
        }
        catch (Exception e) {
            Log.printStackTrace(e);
        }
        try {
            Statistics.save(nowCalendar);
        }
        catch (Exception e) {
            Log.printStackTrace(e);
        }
        try {
            Status.save(nowCalendar);
        }
        catch (Exception e) {
            Log.printStackTrace(e);
        }
        try {
            FriendWatch.updateDay();
        }
        catch (Exception e) {
            Log.printStackTrace(e);
        }
    }
    
    @SuppressLint({"ScheduleExactAlarm", "MissingPermission"})
    private static Boolean setAlarmTask(long triggerAtMillis, PendingIntent operation) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            }
            else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            }
            Log.i("setAlarmTask triggerAtMillis:" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(triggerAtMillis) + " operation:" + (operation == null ? "" : operation.toString()));
            return true;
        }
        catch (Throwable th) {
            Log.i(TAG, "setAlarmTask err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }
    
    private static Boolean unsetAlarmTask(PendingIntent operation) {
        try {
            if (operation != null) {
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                alarmManager.cancel(operation);
            }
            return true;
        }
        catch (Throwable th) {
            Log.i(TAG, "unsetAlarmTask err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }
    
    public static String requestString(RpcEntity rpcEntity) {
        return rpcBridge.requestString(rpcEntity, 3, -1);
    }
    
    public static String requestString(RpcEntity rpcEntity, int tryCount, int retryInterval) {
        return rpcBridge.requestString(rpcEntity, tryCount, retryInterval);
    }
    
    public static String requestString(String method, String data) {
        return rpcBridge.requestString(method, data);
    }
    
    public static String requestString(String method, String data, String relation) {
        return rpcBridge.requestString(method, data, relation);
    }

    /*public static String requestString(String method, String data, String relation, Long time) {
        return rpcBridge.requestString(method, data, relation, time);
    }*/
    
    public static String requestString(String method, String data, int tryCount, int retryInterval) {
        return rpcBridge.requestString(method, data, tryCount, retryInterval);
    }
    
    public static String requestString(String method, String data, String relation, int tryCount, int retryInterval) {
        return rpcBridge.requestString(method, data, relation, tryCount, retryInterval);
    }

    /*public static String requestString(String method, String data, String relation, Long time, int tryCount, int retryInterval) {
        return rpcBridge.requestString(method, data, relation, time, tryCount, retryInterval);
    }*/
    
    public static RpcEntity requestObject(RpcEntity rpcEntity) {
        return rpcBridge.requestObject(rpcEntity, 3, -1);
    }
    
    public static RpcEntity requestObject(RpcEntity rpcEntity, int tryCount, int retryInterval) {
        return rpcBridge.requestObject(rpcEntity, tryCount, retryInterval);
    }
    
    public static RpcEntity requestObject(String method, String data) {
        return rpcBridge.requestObject(method, data);
    }
    
    public static RpcEntity requestObject(String method, String data, String relation) {
        return rpcBridge.requestObject(method, data, relation);
    }

    /*public static RpcEntity requestObject(String method, String data, String relation, Long time) {
        return rpcBridge.requestObject(method, data, relation, time);
    }*/
    
    public static RpcEntity requestObject(String method, String data, int tryCount, int retryInterval) {
        return rpcBridge.requestObject(method, data, tryCount, retryInterval);
    }
    
    public static RpcEntity requestObject(String method, String data, String relation, int tryCount, int retryInterval) {
        return rpcBridge.requestObject(method, data, relation, tryCount, retryInterval);
    }

    /*public static RpcEntity requestObject(String method, String data, String relation, Long time, int tryCount, int retryInterval) {
        return rpcBridge.requestObject(method, data, relation, time, tryCount, retryInterval);
    }*/
    
    public static void reLoginByBroadcast() {
        try {
            context.sendBroadcast(new Intent("com.eg.android.AlipayGphone.sesame.reLogin"));
        }
        catch (Throwable th) {
            Log.i(TAG, "sesame sendBroadcast reLogin err:");
            Log.printStackTrace(TAG, th);
        }
    }
    
    public static void restartByBroadcast() {
        try {
            context.sendBroadcast(new Intent("com.eg.android.AlipayGphone.sesame.restart"));
        }
        catch (Throwable th) {
            Log.i(TAG, "sesame sendBroadcast restart err:");
            Log.printStackTrace(TAG, th);
        }
    }
    
    private static int getPendingIntentFlag() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        }
        else {
            return PendingIntent.FLAG_UPDATE_CURRENT;
        }
    }
    
    public static Object getMicroApplicationContext() {
        if (microApplicationContextObject == null) {
            return microApplicationContextObject = XposedHelpers.callMethod(XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.alipay.mobile.framework.AlipayApplication", classLoader), "getInstance"), "getMicroApplicationContext");
        }
        return microApplicationContextObject;
    }
    
    public static Object getServiceObject(String service) {
        try {
            return XposedHelpers.callMethod(getMicroApplicationContext(), "findServiceByInterface", service);
        }
        catch (Throwable th) {
            Log.i(TAG, "getServiceObject err");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }
    
    public static Object getUserObject() {
        try {
            return XposedHelpers.callMethod(getServiceObject(XposedHelpers.findClass("com.alipay.mobile.personalbase.service.SocialSdkContactService", classLoader).getName()), "getMyAccountInfoModelByLocal");
        }
        catch (Throwable th) {
            Log.i(TAG, "getUserObject err");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }
    
    public static String getUserId() {
        try {
            Object userObject = getUserObject();
            if (userObject != null) {
                return (String) XposedHelpers.getObjectField(userObject, "userId");
            }
        }
        catch (Throwable th) {
            Log.i(TAG, "getUserId err");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }
    
    public static void reLogin() {
        mainHandler.post(() -> {
            if (reLoginCount.get() < 5) {
                execDelayedHandler(reLoginCount.getAndIncrement() * 5000L);
            }
            else {
                execDelayedHandler(Math.max(BaseModel.getCheckInterval().getValue(), 180_000));
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setClassName(ClassUtil.PACKAGE_NAME, ClassUtil.CURRENT_USING_ACTIVITY);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            offline = true;
            context.startActivity(intent);
        });
    }

    /*public static Boolean reLogin() {
        Object authService = getExtServiceByInterface("com.alipay.mobile.framework.service.ext.security.AuthService");
        if ((Boolean) XposedHelpers.callMethod(authService, "rpcAuth")) {
            return true;
        }
        Log.record("重新登录失败");
        return false;
    }*/
    
    private class AlipayBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i("sesame broadcast action:" + action + " intent:" + intent);
            if (action != null) {
                switch (action) {
                    case "com.eg.android.AlipayGphone.sesame.restart":
                        String userId = intent.getStringExtra("userId");
                        if (StringUtil.isEmpty(userId) || Objects.equals(UserIdMap.getCurrentUid(), userId)) {
                            initHandler(true);
                        }
                        break;
                    case "com.eg.android.AlipayGphone.sesame.execute":
                        initHandler(false);
                        break;
                    case "com.eg.android.AlipayGphone.sesame.reLogin":
                        reLogin();
                        break;
                    case "com.eg.android.AlipayGphone.sesame.status":
                        try {
                            context.sendBroadcast(new Intent("io.github.lazyimmortal.sesame.status"));
                        }
                        catch (Throwable th) {
                            Log.i(TAG, "sesame sendBroadcast status err:");
                            Log.printStackTrace(TAG, th);
                        }
                        break;
                    case "com.eg.android.AlipayGphone.sesame.rpctest":
                        try {
                            String method = intent.getStringExtra("method");
                            String data = intent.getStringExtra("data");
                            String type = intent.getStringExtra("type");
                            // Log.record("收到测试消息:\n方法:" + method + "\n数据:" + data + "\n类型:" + type);
                            TestRpc.start(method, data, type);
                        }
                        catch (Throwable th) {
                            Log.i(TAG, "sesame rpctest err:");
                            Log.printStackTrace(TAG, th);
                        }
                        break;
                }
            }
        }
    }
    
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerBroadcastReceiver(Context context) {
        try {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.restart");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.execute");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.reLogin");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.status");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.rpctest");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(new AlipayBroadcastReceiver(), intentFilter, Context.RECEIVER_EXPORTED);
            }
            else {
                context.registerReceiver(new AlipayBroadcastReceiver(), intentFilter);
            }
            Log.i(TAG, "hook registerBroadcastReceiver successfully");
        }
        catch (Throwable th) {
            Log.i(TAG, "hook registerBroadcastReceiver err:");
            Log.printStackTrace(TAG, th);
        }
    }
}