package io.github.lazyimmortal.sesame.hook;

import static io.github.lazyimmortal.sesame.hook.SimplePageManager.addHandler;
import static io.github.lazyimmortal.sesame.hook.SimplePageManager.enableWindowMonitoring;

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
import java.net.URLDecoder;
import java.util.Objects;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

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
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
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

public class ApplicationHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static final String TAG = ApplicationHook.class.getSimpleName();

    /** zygote 入口（无临时 hook 需求时留空实现） */
    @Override
    public void initZygote(StartupParam startupParam) {
    }

    @Getter
    private static final String modelVersion = BuildConfig.VERSION_NAME;

    private static final Map<Object, Object[]> rpcHookMap = new ConcurrentHashMap<>();

    private static final Map<String, PendingIntent> wakenAtTimeAlarmMap = new ConcurrentHashMap<>();

    @Getter
    private static ClassLoader classLoader = null;

    @Getter
    private static Object microApplicationContextObject = null;

    // 新增：全局静态变量，存储当前进程名
    public static String processName; // 供其他方法（如 startIfNeeded）调用

    @Getter
    private static Context context = null; // 全局上下文，对应 Kotlin 的 appContext
    @SuppressLint("StaticFieldLeak")
    private static Service service; // 目标 Service 实例，也是 Context 子类

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

    private static BroadcastReceiver broadcastReceiver = null;

    private static volatile boolean broadcastReceiverRegistered = false;

    public static void setOffline(boolean offline) {
        ApplicationHook.offline = offline;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // 先提取进程名并赋值给全局变量
        processName = lpparam.processName; // 新增：将 Xposed 提供的进程名赋值给全局变量
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
            } catch (ClassNotFoundException e) {
                Log.printStackTrace(e);
            }
        } else if (ClassUtil.PACKAGE_NAME.equals(lpparam.packageName) && ClassUtil.PACKAGE_NAME.equals(lpparam.processName)) {
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
                    alipayVersion = new AlipayVersion(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
                    // 各初始化独立try-catch, 互不影响, 避免一个失败导致AuthCodeHelper未初始化
                    try {
                        AlipayMiniMarkHelper.init(classLoader);
                    } catch (Throwable t) {
                        Log.printStackTrace(t);
                    }
                    try {
                        AuthCodeHelper.init(classLoader);
                        AuthCodeHelper.getAuthCode("2021005114632037");
                    } catch (Throwable t) {
                        Log.printStackTrace(t);
                    }
                    try {
                        // ========== 关键改动：异步执行 initSimplePageManager，不阻塞 ==========
                        // 用线程直接执行（项目中大量使用 Thread 方式，贴合风格）
                        //new Thread(() -> {
                        //    try {
                        initSimplePageManager();
                        //     } catch (Throwable t) {
                        // 复用项目日志风格，捕获异步执行异常
                        //         Log.i(TAG, "initSimplePageManager async err:");
                        //         Log.printStackTrace(TAG, t);
                        //     }
                        // }, "InitSimplePageManager-Thread").start();
                    } catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                    super.afterHookedMethod(param);
                }
            });
            try {
                XposedHelpers.findAndHookMethod("com.alipay.mobile.nebulaappproxy.api.rpc.H5AppRpcUpdate", classLoader, "matchVersion", classLoader.loadClass(ClassUtil.H5PAGE_NAME), Map.class, String.class, XC_MethodReplacement.returnConstant(false));
                Log.i(TAG, "hook matchVersion successfully");
            } catch (Throwable t) {
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
            } catch (Throwable t) {
                Log.i(TAG, "hook login err:");
                Log.printStackTrace(TAG, t);
            }
            try {
                XposedHelpers.findAndHookMethod("android.app.Service", classLoader, "onCreate", new XC_MethodHook() {

                    @SuppressLint({"WakelockTimeout", "UnsafeDynamicallyLoadedCode"})
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        // 1. 获取目标 Service 实例（appService 是 Service 子类，也是 Context 类型）
                        Service appService = (Service) param.thisObject;
                        if (!ClassUtil.CURRENT_USING_SERVICE.equals(appService.getClass().getCanonicalName())) {
                            return;// 非目标 Service，直接返回，保障只处理支付宝前台服务
                        }

                        // 2. 兜底赋值全局 context（对应 Kotlin appContext 的二次赋值）
                        context = appService.getApplicationContext(); // 获取应用全局上下文，更新全局变量
                        service = appService; // 存储 Service 实例，供后续复用

                        // 3. 调用 registerBroadcastReceiver，传入参数 Context（appService）
                        // 这里的 appService 就是对应 Kotlin registerBroadcastReceiver(appContext!!) 的参数
                        registerBroadcastReceiver(appService);

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
                                Log.record("模块版本：" + modelVersion + "（交流更新QQ群：694474777）");
                                Log.record("开始执行");
                                // 每轮信息日志空行分隔(扩展功能按钮的森林日志输出保持不变)
                                Log.infoNewLine();
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
                                    // 自动统计被浇水与浇水情况(必须在账号一致性校验之后: 多账号切换时UserIdMap好友表已确认是当前账号的,getShowName才能正确转昵称)
                                    try {
                                        Status.getWateredFriendToday(true);
                                        Status.getWateringFriendToday(true);
                                    } catch (Throwable t) {
                                        Log.i(TAG, "自动统计浇水情况失败:");
                                        Log.printStackTrace(TAG, t);
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
                                    } catch (InterruptedException | ExecutionException |
                                             TimeoutException e) {
                                        Log.record("执行失败：检查中断");
                                        reLogin();
                                        return;
                                    } catch (Exception e) {
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
                                    } catch (Exception e) {
                                        Log.i(TAG, "execAtTime err:");
                                        Log.printStackTrace(TAG, e);
                                    }

                                    execDelayedHandler(checkInterval);
                                    FileUtil.clearLog();
                                } catch (Exception e) {
                                    Log.record("执行异常:");
                                    Log.printStackTrace(e);
                                }
                            }
                        });
                        dayCalendar = Calendar.getInstance();
                        Statistics.load();
                        FriendWatch.load();
                        if (initHandler(true)) {
                            init = true;
                        }
                    }
                });
                Log.i(TAG, "hook service onCreate successfully");
            } catch (Throwable t) {
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
            } catch (Throwable t) {
                Log.i(TAG, "hook service onDestroy err:");
                Log.printStackTrace(TAG, t);
            }
            try {
                XposedHelpers.findAndHookMethod("com.alipay.mobile.common.fgbg.FgBgMonitorImpl", classLoader, "isInBackground", XC_MethodReplacement.returnConstant(false));
            } catch (Throwable t) {
                Log.i(TAG, "hook FgBgMonitorImpl method 1 err:");
                Log.printStackTrace(TAG, t);
            }
            try {
                XposedHelpers.findAndHookMethod("com.alipay.mobile.common.fgbg.FgBgMonitorImpl", classLoader, "isInBackground", boolean.class, XC_MethodReplacement.returnConstant(false));
            } catch (Throwable t) {
                Log.i(TAG, "hook FgBgMonitorImpl method 2 err:");
                Log.printStackTrace(TAG, t);
            }
            try {
                XposedHelpers.findAndHookMethod("com.alipay.mobile.common.fgbg.FgBgMonitorImpl", classLoader, "isInBackgroundV2", XC_MethodReplacement.returnConstant(false));
            } catch (Throwable t) {
                Log.i(TAG, "hook FgBgMonitorImpl method 3 err:");
                Log.printStackTrace(TAG, t);
            }
            try {
                XposedHelpers.findAndHookMethod("com.alipay.mobile.common.transport.utils.MiscUtils", classLoader, "isAtFrontDesk", classLoader.loadClass("android.content.Context"), XC_MethodReplacement.returnConstant(true));
                Log.i(TAG, "hook MiscUtils successfully");
            } catch (Throwable t) {
                Log.i(TAG, "hook MiscUtils err:");
                Log.printStackTrace(TAG, t);
            }
            hooked = true;
            Log.i(TAG, "load success: " + lpparam.packageName);
        } else if (ClassUtil.PACKAGE_NAME.equals(lpparam.packageName)) {
            // 支付宝非主进程（如小程序独立进程 :nebula/:tinyapp 等）：仅安装小程序网络抓包hook，不动其他逻辑
            final ClassLoader remoteClassLoader = lpparam.classLoader;
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        context = (Context) param.args[0];
                        classLoader = remoteClassLoader;
                        // 子进程启动早期配置可能尚未加载，延迟轮询开关，确认打开后才安装
                        //（未开抓包的用户不会安装，避免 1.8.9 的启动卡死闪退问题）
                        scheduleMiniHookInstall();
                    } catch (Throwable t) {
                        if (miniDebugEnabled()) {
                            Log.debug("小程序抓包进程[" + processName + "]启动失败: " + t.getMessage());
                        }
                    }
                }
            });
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
                            Log.record("页面内容：" + dialogAllInfo.toString());
                            if (dialogAllInfo.toString().contains("请检查是否使用了代理软件或VPN") || dialogAllInfo.toString().contains("访问被拒绝")) {
                                Log.record("包含指定字符");
                                param.setResult(null);
                            }
                        }
                        Log.i(TAG, "已拦截CaptchaDialog显示");
                        Log.record("已拦截CaptchaDialog显示");
                    }
                }
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
     *
     * @param rootView 根布局
     * @param info     拼接日志的StringBuilder
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
            String viewId = textView.getId() != View.NO_ID ? rootView.getResources().getResourceEntryName(textView.getId()) : "无ID";
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
            } catch (Exception e) {
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
                    } catch (Exception e) {
                        Log.i(TAG, "setWakenAtTime err:");
                        Log.printStackTrace(TAG, e);
                    }
                }
            }
        } catch (Exception e) {
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
                } catch (Exception e) {
                    Log.i(TAG, "unsetWakenAtTime err:");
                    Log.printStackTrace(TAG, e);
                }
            }
            try {
                if (unsetAlarmTask(alarm0Pi)) {
                    alarm0Pi = null;
                    Log.record("取消定时唤醒:0|000000");
                }
            } catch (Exception e) {
                Log.i(TAG, "unsetWakenAt0 err:");
                Log.printStackTrace(TAG, e);
            }
        } catch (Exception e) {
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

                //调用 startIfNeeded 方法，参数与 Kotlin 保持一致
                ModuleHttpServerManager.getInstance().startIfNeeded(8080, "ET3vB^#td87sQqKaY*eMUJXP", processName, "com.eg.android.AlipayGphone");

                UserIdMap.initUser(userId);
                Model.initAllModel();
                Log.record("模块版本：" + modelVersion);
                Log.record("开始加载");
                ConfigV2.load(userId);

                boolean enableModule = Model.getModel(BaseModel.class).getEnableField().getValue();
                if (!enableModule) {
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
                } else {
                    rpcBridge = new OldRpcBridge();
                }
                rpcBridge.load();
                rpcVersion = rpcBridge.getVersion();
                // 抓取支付宝小程序网络请求/响应数据
                if (miniDebugEnabled()) {
                    hookMiniProgramNetwork();
                }
                if (BaseModel.getStayAwake().getValue()) {
                    try {
                        PowerManager pm = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
                        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, service.getClass().getName());
                        wakeLock.acquire();
                    } catch (Throwable t) {
                        Log.printStackTrace(t);
                    }
                }
                setWakenAtTimeAlarm();
                if (BaseModel.getNewRpc().getValue() && BaseModel.getDebugMode().getValue()) {
                    try {
                        rpcRequestUnhook = XposedHelpers.findAndHookMethod("com.alibaba.ariver.commonability.network.rpc.RpcBridgeExtension", classLoader, "rpc", String.class, boolean.class, boolean.class, String.class, classLoader.loadClass(ClassUtil.JSON_OBJECT_NAME), String.class, classLoader.loadClass(ClassUtil.JSON_OBJECT_NAME), boolean.class, boolean.class, int.class, boolean.class, String.class, classLoader.loadClass("com.alibaba.ariver.app.api.App"), classLoader.loadClass("com.alibaba.ariver.app.api.Page"), classLoader.loadClass("com.alibaba.ariver.engine.api.bridge.model.ApiContext"), classLoader.loadClass("com.alibaba.ariver.engine.api.bridge.extension" + ".BridgeCallback"), new XC_MethodHook() {

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
                                } else {
                                    Log.debug("删除记录ID: " + object.hashCode());
                                }
                            }

                        });
                        Log.i(TAG, "hook record request successfully");
                    } catch (Throwable t) {
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
                    } catch (Throwable t) {
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
                BaseModel.initRpcRequest();
                Log.record("加载完成");
                Toast.show("芝麻粒加载成功");
            }
            offline = false;
            execHandler();
            return true;
        } catch (Throwable th) {
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
                    } catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                }
                if (rpcRequestUnhook != null) {
                    try {
                        rpcRequestUnhook.unhook();
                    } catch (Exception e) {
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
            } else {
                ModelTask.stopAllTask();
            }
        } catch (Throwable th) {
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
        } catch (Exception e) {
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
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        try {
            Statistics.save(nowCalendar);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        try {
            Status.save(nowCalendar);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        try {
            FriendWatch.updateDay();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    @SuppressLint({"ScheduleExactAlarm", "MissingPermission"})
    private static Boolean setAlarmTask(long triggerAtMillis, PendingIntent operation) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            }
            Log.i("setAlarmTask triggerAtMillis:" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(triggerAtMillis) + " operation:" + (operation == null ? "" : operation.toString()));
            return true;
        } catch (Throwable th) {
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
        } catch (Throwable th) {
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
        } catch (Throwable th) {
            Log.i(TAG, "sesame sendBroadcast reLogin err:");
            Log.printStackTrace(TAG, th);
        }
    }

    public static void restartByBroadcast() {
        try {
            context.sendBroadcast(new Intent("com.eg.android.AlipayGphone.sesame.restart"));
        } catch (Throwable th) {
            Log.i(TAG, "sesame sendBroadcast restart err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private static int getPendingIntentFlag() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        } else {
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
        } catch (Throwable th) {
            Log.i(TAG, "getServiceObject err");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }

    public static Object getUserObject() {
        try {
            return XposedHelpers.callMethod(getServiceObject(XposedHelpers.findClass("com.alipay.mobile.personalbase.service.SocialSdkContactService", classLoader).getName()), "getMyAccountInfoModelByLocal");
        } catch (Throwable th) {
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
        } catch (Throwable th) {
            Log.i(TAG, "getUserId err");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }

    public static void reLogin() {
        mainHandler.post(() -> {
            if (reLoginCount.get() < 5) {
                execDelayedHandler(reLoginCount.getAndIncrement() * 5000L);
            } else {
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
                            BroadcastReceiver.PendingResult r = goAsync();
                            new Thread(() -> {
                                try {
                                    initHandler(true);
                                } catch (Throwable th) {
                                    Log.printStackTrace(TAG, th);
                                }
                                r.finish();
                            }, "Sesame-Restart").start();
                        }
                        break;
                    case "com.eg.android.AlipayGphone.sesame.execute":
                        BroadcastReceiver.PendingResult r2 = goAsync();
                        new Thread(() -> {
                            try {
                                initHandler(false);
                            } catch (Throwable th) {
                                Log.printStackTrace(TAG, th);
                            }
                            r2.finish();
                        }, "Sesame-Execute").start();
                        break;
                    case "com.eg.android.AlipayGphone.sesame.reLogin":
                        reLogin();
                        break;
                    case "com.eg.android.AlipayGphone.sesame.status":
                        try {
                            context.sendBroadcast(new Intent("io.github.lazyimmortal.sesame.status"));
                        } catch (Throwable th) {
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
                        } catch (Throwable th) {
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
            if (broadcastReceiverRegistered && broadcastReceiver != null) {
                try {
                    context.unregisterReceiver(broadcastReceiver);
                    broadcastReceiverRegistered = false;
                    Log.i(TAG, "hook unregisterBroadcastReceiver successfully");
                } catch (Throwable t) {
                    Log.i(TAG, "hook unregisterBroadcastReceiver err:");
                    Log.printStackTrace(TAG, t);
                }
            }

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.restart");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.execute");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.reLogin");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.status");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.rpctest");

            broadcastReceiver = new AlipayBroadcastReceiver();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(broadcastReceiver, intentFilter);
            }
            broadcastReceiverRegistered = true;
            Log.i(TAG, "hook registerBroadcastReceiver successfully");
        } catch (Throwable th) {
            Log.i(TAG, "hook registerBroadcastReceiver err:");
            Log.printStackTrace(TAG, th);
        }
    }

    // 滑块验证hook注册
    private void initSimplePageManager() {
        if (shouldEnableSimplePageManager()) {
            enableWindowMonitoring(classLoader);
            addHandler("com.alipay.mobile.nebulax.xriver.activity.XRiverActivity", new Captcha1Handler());
            addHandler("com.eg.android.AlipayGphone.AlipayLogin", new Captcha2Handler());
        }
    }

    /**
     * 检查目标应用版本是否需要启用SimplePageManager功能
     *
     * @return true表示版本低于等于10.6.58.99999，需要启用；false表示不需要
     */
    private boolean shouldEnableSimplePageManager() {
        if (alipayVersion.toString().isEmpty()) {
            return false;
        }

        AlipayVersion maxSupported = new AlipayVersion("10.6.58.99999");
        if (alipayVersion.compareTo(maxSupported) > 0) {
            // 只有在不支持时才打印警告
            Log.record("目标应用版本[" + alipayVersion.getVersionString() + "]高于[10.6.58.99999]不支持自动过滑块验证");
            return false;
        }

        return true;
    }

    /**
     * 抓取支付宝小程序（含小游戏）的网络请求与响应，只读不写，不影响原有链路。
     * 请求入口：AsyncRequestHandler.processAsyncRequest(ctx,app,page,RequestModel,ParamsModel,...)
     * 响应出口：HttpRequestResponseTrigger.onHandleResponse(url, body, ext, ...)
     * 两个类位于小程序插件 dex 中延迟加载，故先尝试直接加载，同时监听 ClassLoader.loadClass。
     */
    private void hookMiniProgramNetwork() {
        tryHookMiniNetworkClass(MINI_RESP_CLASS);
        tryHookMiniNetworkClass(ASYNC_REQUEST_HANDLER_CLASS);
        // 补齐 ariver bridge 层（my.request/my.call 入口与响应回调）与 nebula RPC 入口，
        // 覆盖不走 AsyncRequestHandler/HttpRequestResponseTrigger 的小游戏
        tryHookMiniNetworkClass(ARIVER_RPC_BRIDGE_CLASS);
        tryHookMiniNetworkClass(ARIVER_BRIDGE_CALLBACK_CLASS);
        tryHookMiniNetworkClass(NEBULA_RPC_CLASS);
        tryHookMiniNetworkClass(ARIVER_HTTP_BRIDGE_CLASS);
        tryHookMiniNetworkClass(ARIVER_WS_BRIDGE_CLASS);
        tryHookMiniNetworkClass(OKHTTP_WEBSOCKET_CLASS);
        tryHookMiniNetworkClass(OKHTTP_WS_LISTENER_CLASS);
        tryHookMiniNetworkClass(XRIVER_WS_CLASS);
        tryHookMiniNetworkClass(JAVA_WS_CLIENT_CLASS);
        tryHookMiniNetworkClass(NV_WS_CLASS);
        installDeferredNetworkHook();
    }



    /** 子进程 hook 延迟安装是否已调度（每个进程只调度一次） */
    private static volatile boolean miniHookInstallScheduled = false;

    /**
     * 子进程启动时配置可能尚未加载完成，后台等待约 30 秒（每 5 秒复查一次），
     * debugMode 开关为打开时才安装 hook。
     * 未开抓包的用户不会安装 hook，避免 1.8.9 无条件安装导致启动卡死闪退的问题。
     */
    private void scheduleMiniHookInstall() {
        if (miniHookInstallScheduled) {
            return;
        }
        miniHookInstallScheduled = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 6; i++) {
                    try {
                        if (miniDebugEnabled()) {
                            hookMiniProgramNetwork();
                            Log.debug("小程序抓包进程启动[" + processName + "]");
                            return;
                        }
                    } catch (Throwable ignored) {
                    }
                    try {
                        Thread.sleep(5000L);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "sesame-mini-hook").start();
    }

    private void tryHookMiniNetworkClass(String className) {
        if (deferredHookedClasses.contains(className)) {
            return;
        }
        try {
            hookMiniNetworkClass(XposedHelpers.findClass(className, classLoader));
        } catch (Throwable ignored) {
            // 类尚未加载，交给 loadClass 延迟监听
        }
    }

    /** 请求暂存：域名白名单过滤后按精确 URL 保存，等待 onHandleResponse 配对（只读，不修改原请求） */
    private void pendingMiniRequest(String url, String method, String data) {
        if (url == null || url.length() == 0 || !isGameApi(url)) {
            return;
        }
        String body = "";
        if (data != null && data.length() > 0) {
            body = data;
            // rantu 走 application/x-www-form-urlencoded：data=<urlencoded JSON>；
            // gamesapi2 等接口直接是裸 JSON，不能 URLDecoder 解码（'+' 会被误转为空格）
            if (body.startsWith("data=")) {
                body = body.substring(5);
                try {
                    body = URLDecoder.decode(body, "UTF-8");
                } catch (Throwable ignored) {
                }
            }
            body = clip(deepPrettyJson(body));
        }
        // timestamp+sign 使每次请求的完整 URL 唯一，天然可作为请求/响应配对键
        pendingMiniRequests.put(url, new String[]{
                String.valueOf(System.currentTimeMillis()),
                "MINI "
                        + (method == null || method.length() == 0 ? "?" : method) + " " + url,
                body
        });
    }

    /** 记录小程序响应：与暂存请求按 URL 配对，按 时间/方法/参数/数据 单条输出，只保留游戏业务接口 */
    private void logMiniResponse(Object[] args, Object thisObject) {
        if (!miniDebugEnabled() || args == null || args.length < 2 || args[0] == null) {
            // 诊断：onHandleResponse 被调用但参数不足，记录原始参数结构便于排查签名变化
            if (miniDebugEnabled() && args != null) {
                StringBuilder sb = new StringBuilder("小程序onHandleResponse参数不足: len=").append(args.length);
                for (int i = 0; i < args.length; i++) {
                    sb.append(" arg[").append(i).append("]=")
                            .append(args[i] == null ? "null" : args[i].getClass().getName());
                }
                Log.debug(sb.toString());
            }
            return;
        }
        String url = args[0].toString();
        // 诊断：记录所有到达 onHandleResponse 的 URL，便于判断游戏流量是否走该 hook 以及是否被黑名单过滤
        if (!isGameApi(url)) {
            Log.debug("小程序onHandleResponse过滤URL: " + url + " 进程=" + processName);
            return;
        }
        ensureAsyncRequestHook(thisObject != null ? thisObject.getClass().getClassLoader() : classLoader);
        String body = args[1] != null ? clip(deepPrettyJson(args[1].toString())) : "";
        String[] record = pendingMiniRequests.remove(url);
        String time;
        String methodLine;
        String params;
        if (record != null) {
            time = record[0];
            methodLine = record[1];
            params = record[2];
        } else {
            // 请求先于 hook 安装等情况下没有暂存，仍单独记录响应
            time = String.valueOf(System.currentTimeMillis());
            methodLine = "MINI ? " + url;
            params = "";
        }
        Log.debug("记录\n时间: " + time + "\n方法: " + methodLine + "\n参数: " + params + "\n数据: " + body + "\n");
    }

    /**
     * 响应首包到达时兜底补装 AsyncRequestHandler hook（正常情况下类加载时已在
     * hookMiniNetworkClass 中装好；此处防止 loadClass 监听漏回调）。
     */
    private void ensureAsyncRequestHook(ClassLoader loader) {
        if (deferredHookedClasses.contains(ASYNC_REQUEST_HANDLER_CLASS)) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(ASYNC_REQUEST_HANDLER_CLASS, false, loader);
            if (!deferredHookedClasses.add(ASYNC_REQUEST_HANDLER_CLASS)) {
                return;
            }
            installAsyncRequestHookOnClass(clazz);
        } catch (Throwable ignored) {
            // 类尚未加载：loadClass 监听命中后会安装，这里不打日志避免噪音
        }
    }

    /**
     * 定点 hook 真实请求入口（由响应调用栈与方法清单确认）：
     *   AsyncRequestHandler.processAsyncRequest(ApiContext,App,Page,RequestModel,ParamsModel,...,String,...)
     * paramsJson 在到达此处前已被拆进 RequestModel/ParamsModel 的字段，故递归收集模型中的字符串。
     * 调用方需已持有 deferredHookedClasses 中的去重标记。
     */
    private void installAsyncRequestHookOnClass(Class<?> clazz) {
        XposedBridge.hookAllMethods(clazz, "processAsyncRequest", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    logRequestFromModels(p.args);
                } catch (Throwable ignored) {
                }
            }
        });
        if (miniDebugEnabled()) {
            Log.debug("小程序抓包hook已安装: AsyncRequestHandler.processAsyncRequest 进程=" + processName);
        }
    }

    /** 从 processAsyncRequest 的模型参数中提取游戏请求（先找完整 paramsJson，再按 url/body 拼装） */
    private void logRequestFromModels(Object[] args) {
        if (!miniDebugEnabled() || args == null) {
            return;
        }
        java.util.List<String> strings = new java.util.ArrayList<>();
        java.util.Set<Object> visited =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
        for (Object arg : args) {
            collectStrings(arg, 3, visited, strings);
            if (strings.size() > 256) {
                break;
            }
        }
        // 1) 模型里若仍保留完整 paramsJson
        for (String s : strings) {
            if (s.length() >= 8 && s.indexOf('"') >= 0) {
                try {
                    JSONObject jo = new JSONObject(s);
                    String u = jo.optString("url");
                    if (u.length() > 0 && isGameApi(u)) {
                        pendingMiniRequest(u, jo.optString("method", "GET"), jo.optString("data"));
                        return;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        // 2) url 与 body 被拆成独立字段：模型里可能有多个 URL（referer/广告配置等），
        //    给所有候选 URL 登记同一份暂存，响应回来时精确 URL 自然命中真实请求
        String method = null;
        for (String s : strings) {
            if ("GET".equals(s) || "POST".equals(s) || "PUT".equals(s) || "DELETE".equals(s)) {
                method = s;
                break;
            }
        }
        String body = null;
        for (String s : strings) {
            if (s.startsWith("data=")) {
                body = s;
                break;
            }
        }
        if (body == null) {
            for (String s : strings) {
                if (s.startsWith("{") && s.length() > 16 && (body == null || s.length() > body.length())) {
                    body = s;
                }
            }
        }
        java.util.Set<String> candidates = new java.util.LinkedHashSet<>();
        for (String s : strings) {
            if (s.startsWith("http") && isGameApi(s)) {
                candidates.add(s.trim());
                if (candidates.size() >= 3) {
                    break;
                }
            }
        }
        for (String url : candidates) {
            pendingMiniRequest(url, method, body);
        }
    }

    /** 各类实例字段缓存（含父类链），避免网络热路径重复反射 */
    private static final java.util.Map<Class<?>, java.util.List<java.lang.reflect.Field>> modelFieldsCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 递归收集对象图中的 String：字段读取含父类链，com.alipay 模型对象继续下钻，
     * Map/Collection 取元素，深度与对象数双重上限，只读。
     */
    private void collectStrings(Object obj, int depth, java.util.Set<Object> visited, java.util.List<String> out) {
        if (obj == null || depth < 0 || visited.size() > 128) {
            return;
        }
        if (obj instanceof String) {
            String s = (String) obj;
            if (s.length() > 0) {
                out.add(s);
            }
            return;
        }
        Class<?> c = obj.getClass();
        if (c.isPrimitive() || c.isArray() || obj instanceof Number || obj instanceof Boolean
                || obj instanceof Character || obj instanceof Enum) {
            return;
        }
        if (obj instanceof java.util.Map) {
            if (!visited.add(obj)) {
                return;
            }
            for (Object v : ((java.util.Map<?, ?>) obj).values()) {
                collectStrings(v, depth - 1, visited, out);
            }
            return;
        }
        if (obj instanceof java.util.Collection) {
            if (!visited.add(obj)) {
                return;
            }
            for (Object v : (java.util.Collection<?>) obj) {
                collectStrings(v, depth - 1, visited, out);
            }
            return;
        }
        if (!c.getName().startsWith("com.alipay") || !visited.add(obj)) {
            return;
        }
        for (java.lang.reflect.Field f : modelFieldsCache.computeIfAbsent(c, this::resolveInstanceFields)) {
            try {
                collectStrings(f.get(obj), depth - 1, visited, out);
            } catch (Throwable ignored) {
            }
        }
    }

    private java.util.List<java.lang.reflect.Field> resolveInstanceFields(Class<?> clazz) {
        java.util.List<java.lang.reflect.Field> list = new java.util.ArrayList<>();
        Class<?> cur = clazz;
        int guard = 0;
        while (cur != null && cur != Object.class && guard++ < 6) {
            for (java.lang.reflect.Field fld : cur.getDeclaredFields()) {
                int mods = fld.getModifiers();
                if (java.lang.reflect.Modifier.isStatic(mods)) {
                    continue;
                }
                Class<?> t = fld.getType();
                if (t == String.class || t.getName().startsWith("com.alipay")
                        || java.util.Map.class.isAssignableFrom(t) || java.util.Collection.class.isAssignableFrom(t)) {
                    try {
                        fld.setAccessible(true);
                        list.add(fld);
                    } catch (Throwable ignored) {
                    }
                }
            }
            cur = cur.getSuperclass();
        }
        return list;
    }

    /** 非业务域名：日志埋点/监控/小程序包静态资源 CDN，命中即不记录 */
    private static final String[] MINI_HOST_BLACKLIST = {
            "hybrid.alipay-eco.com", "xxl-log", "mmstat", "amdc.", "arms",
            "alicdn.com", "tbcdn.cn", "alipayobjects.com", "gtimg.cn"
    };

    /** 静态资源后缀（按 ? 之前的路径判断）：命中即不记录；不包含 .json，避免误过滤返回 JSON 的业务接口 */
    private static final String[] MINI_STATIC_SUFFIX = {
            ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico",
            ".svg", ".woff", ".woff2", ".ttf", ".html", ".htm", ".mp4", ".mp3"
    };

    /**
     * 是否记录该小程序请求：抓包开关打开期间默认记录全部小程序 API 请求，
     * 仅排除埋点/日志/监控域名和静态资源，避免白名单域名导致其他游戏抓不到。
     */
    private boolean isGameApi(String url) {
        if (url == null || url.length() < 8) {
            return false;
        }
        String lower = url.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        for (String host : MINI_HOST_BLACKLIST) {
            if (lower.contains(host)) {
                return false;
            }
        }
        int query = lower.indexOf('?');
        String path = query >= 0 ? lower.substring(0, query) : lower;
        int slash = path.indexOf('/', 8);
        if (slash >= 0) {
            path = path.substring(slash);
        }
        for (String suffix : MINI_STATIC_SUFFIX) {
            if (path.endsWith(suffix)) {
                return false;
            }
        }
        return true;
    }

    /** 把 JSON 中嵌套转义的 JSON 字符串逐层展开为单行紧凑形式；非 JSON 原样返回并折掉换行 */
    private String deepPrettyJson(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        try {
            Object parsed;
            if (s.startsWith("{")) {
                parsed = deepParseJson(new JSONObject(s));
            } else if (s.startsWith("[")) {
                parsed = deepParseJson(new JSONArray(s));
            } else {
                return raw.replaceAll("\\r?\\n", " ");
            }
            // toString() 为单行紧凑输出，保证每条日志只占一行
            String out = (parsed instanceof JSONObject)
                    ? ((JSONObject) parsed).toString()
                    : ((JSONArray) parsed).toString();
            return out.replaceAll("\\r?\\n", " ");
        } catch (Throwable t) {
            return raw.replaceAll("\\r?\\n", " ");
        }
    }

    private Object deepParseJson(Object value) {
        try {
            if (value instanceof JSONObject) {
                JSONObject obj = (JSONObject) value;
                JSONObject out = new JSONObject();
                JSONArray names = obj.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String key = names.optString(i);
                        out.put(key, deepParseJson(obj.opt(key)));
                    }
                }
                return out;
            }
            if (value instanceof JSONArray) {
                JSONArray arr = (JSONArray) value;
                JSONArray out = new JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    out.put(deepParseJson(arr.opt(i)));
                }
                return out;
            }
            if (value instanceof String) {
                String str = ((String) value).trim();
                boolean jsonObj = str.startsWith("{") && str.endsWith("}");
                boolean jsonArr = str.startsWith("[") && str.endsWith("]");
                if (jsonObj || jsonArr) {
                    return jsonObj ? deepParseJson(new JSONObject(str)) : deepParseJson(new JSONArray(str));
                }
            }
        } catch (Throwable ignored) {
        }
        return value;
    }

    private String clip(String s) {
        if (s != null && s.length() > MAX_LOG_LEN) {
            return s.substring(0, MAX_LOG_LEN) + "...(truncated)";
        }
        return s;
    }

    private static final String MINI_RESP_CLASS =
            "com.alipay.android.phone.event.ariver.network.HttpRequestResponseTrigger";
    private static final String ASYNC_REQUEST_HANDLER_CLASS =
            "com.alipay.android.phone.wallet.aompnetwork.request.AsyncRequestHandler";
    /** ariver JS Bridge RPC 入口：my.request / my.call 的底层桥接方法，主进程已 hook，小程序进程补齐 */
    private static final String ARIVER_RPC_BRIDGE_CLASS =
            "com.alibaba.ariver.commonability.network.rpc.RpcBridgeExtension";
    /** ariver Bridge 响应回调：所有通过 bridge 的响应最终经 sendJSONResponse 回传 JS */
    private static final String ARIVER_BRIDGE_CALLBACK_CLASS =
            "com.alibaba.ariver.engine.common.bridge.internal.DefaultBridgeCallback";
    /** nebula 容器 RPC 入口：部分 H5 小游戏走 nebula 网络栈而非 ariver */
    private static final String NEBULA_RPC_CLASS =
            "com.alipay.mobile.nebulaappproxy.api.rpc.H5RpcUtil";
    /** ariver HTTP 请求桥接：my.request 的原生入口，覆盖不走 RPC 的 HTTP 类小游戏请求 */
    private static final String ARIVER_HTTP_BRIDGE_CLASS =
            "com.alibaba.ariver.commonability.network.http.HttpRequestBridgeExtension";
    /** ariver WebSocket 桥接：my.connectSocket / my.sendSocketMessage / my.onSocketMessage 入口，
     *  三国冰河时代等小游戏的招募/战斗等操作走 WebSocket，必须在此层捕获收发数据 */
    private static final String ARIVER_WS_BRIDGE_CLASS =
            "com.alibaba.ariver.commonability.network.websocket.WebSocketBridgeExtension";
    /** OkHttp WebSocket 兜底：部分版本 ariver 底层直接用 okhttp3.RealWebSocket，
     *  桥接类不存在时仍可捕获 send 数据 */
    private static final String OKHTTP_WEBSOCKET_CLASS = "okhttp3.RealWebSocket";
    /** OkHttp WebSocketListener 兜底：捕获服务器推送的消息（onMessage 回调） */
    private static final String OKHTTP_WS_LISTENER_CLASS = "okhttp3.WebSocketListener";
    /** XRiver 引擎 WebSocket 客户端候选类（游戏 WS 接收走 XRiver 内部实现） */
    private static final String XRIVER_WS_CLASS = "com.alibaba.ariver.xriver.websocket.XRWebSocket";
    /** Java-WebSocket 库候选（部分版本 ariver 用此库实现 WS） */
    private static final String JAVA_WS_CLIENT_CLASS = "org.java_websocket.client.WebSocketClient";
    /** NV-WebSocket 库候选 */
    private static final String NV_WS_CLASS = "com.neovisionaries.ws.client.WebSocket";

    /** 单条日志最大长度：过小的上限会把游戏大响应截断（如邮件列表/背包），这里放宽到 20 万字符 */
    private static final int MAX_LOG_LEN = 200000;

    /** 已 hook 过的类名（跨 loadClass 多次回调去重） */
    private static final Set<String> deferredHookedClasses =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /** 已暂存未配对请求：精确 URL -> {时间, 方法行, 请求体}（LRU 上限，防止无响应请求堆积） */
    private static final java.util.Map<String, String[]> pendingMiniRequests =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<String, String[]>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, String[]> eldest) {
                    return size() > 128;
                }
            });

    /** 小程序抓包开关：统一由基础配置 debugMode("开启抓包(含小程序数据)") 控制 */
    private boolean miniDebugEnabled() {
        try {
            return BaseModel.getDebugMode().getValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 监听 ClassLoader.loadClass：小程序插件 dex 中的网络类被加载时动态安装 hook。
     * 同时 hook ClassLoader 和 BaseDexClassLoader（后者重写了 loadClass）。
     */
    private void installDeferredNetworkHook() {
        XC_MethodHook loadClassHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                try {
                    Object result = p.getResult();
                    if (result instanceof Class) {
                        hookMiniNetworkClass((Class<?>) result);
                    }
                } catch (Throwable ignored) {
                }
            }
        };
        try {
            XposedBridge.hookAllMethods(ClassLoader.class, "loadClass", loadClassHook);
        } catch (Throwable ignored) {
        }
        try {
            Class<?> baseDexClassLoader = XposedHelpers.findClass("dalvik.system.BaseDexClassLoader", null);
            XposedBridge.hookAllMethods(baseDexClassLoader, "loadClass", loadClassHook);
        } catch (Throwable ignored) {
        }
    }

    /** 对小程序网络请求/响应类安装定点 hook（只记录，不修改参数和返回值） */
    private void hookMiniNetworkClass(Class<?> clazz) {
        String name = clazz.getName();
        if (!MINI_RESP_CLASS.equals(name)
                && !ASYNC_REQUEST_HANDLER_CLASS.equals(name)
                && !ARIVER_RPC_BRIDGE_CLASS.equals(name)
                && !ARIVER_BRIDGE_CALLBACK_CLASS.equals(name)
                && !NEBULA_RPC_CLASS.equals(name)
                && !ARIVER_HTTP_BRIDGE_CLASS.equals(name)
                && !ARIVER_WS_BRIDGE_CLASS.equals(name)
                && !OKHTTP_WEBSOCKET_CLASS.equals(name)
                && !OKHTTP_WS_LISTENER_CLASS.equals(name)
                && !XRIVER_WS_CLASS.equals(name)
                && !JAVA_WS_CLIENT_CLASS.equals(name)
                && !NV_WS_CLASS.equals(name)) {
            return;
        }
        if (!deferredHookedClasses.add(name)) {
            return;
        }
        try {
            if (MINI_RESP_CLASS.equals(name)) {
                int hooked = hookMethodInHierarchy(clazz, "onHandleResponse", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            logMiniResponse(p.args, p.thisObject);
                        } catch (Throwable ignored) {
                        }
                    }
                });
                if (miniDebugEnabled()) {
                    Log.debug("小程序抓包hook已安装: " + name + " onHandleResponse方法=" + hooked + " 进程=" + processName);
                }
            } else if (ASYNC_REQUEST_HANDLER_CLASS.equals(name)) {
                installAsyncRequestHookOnClass(clazz);
            } else if (ARIVER_RPC_BRIDGE_CLASS.equals(name)) {
                installAriverRpcBridgeHook(clazz);
            } else if (ARIVER_BRIDGE_CALLBACK_CLASS.equals(name)) {
                installAriverBridgeCallbackHook(clazz);
            } else if (NEBULA_RPC_CLASS.equals(name)) {
                installNebulaRpcHook(clazz);
            } else if (ARIVER_HTTP_BRIDGE_CLASS.equals(name)) {
                installAriverHttpBridgeHook(clazz);
            } else if (ARIVER_WS_BRIDGE_CLASS.equals(name)) {
                installAriverWebSocketHook(clazz);
            } else if (OKHTTP_WEBSOCKET_CLASS.equals(name)) {
                installOkHttpWebSocketHook(clazz);
            } else if (OKHTTP_WS_LISTENER_CLASS.equals(name)) {
                installOkHttpWsListenerHook(clazz);
            } else if (XRIVER_WS_CLASS.equals(name)
                    || JAVA_WS_CLIENT_CLASS.equals(name)
                    || NV_WS_CLASS.equals(name)) {
                installWsClientDiagnosticHook(clazz, name);
            }
        } catch (Throwable t) {
            if (miniDebugEnabled()) {
                Log.debug("小程序抓包hook安装失败: " + name + " " + t.getMessage());
            }
        }
    }

    /**
     * ariver bridge RPC 入口 hook：与主进程一致，记录 operationType(请求方法) 与请求参数，
     * 并以 BridgeCallback 对象为 key 暂存，等待 sendJSONResponse 配对响应。
     * 兼容不同版本 rpc 方法签名：按位置取 args[0]=operationType, args[4]=requestData(JSONObject),
     * args[last]=BridgeCallback。
     */
    private void installAriverRpcBridgeHook(Class<?> clazz) {
        try {
            XposedBridge.hookAllMethods(clazz, "rpc", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        Object[] args = p.args;
                        if (args == null || args.length < 5) {
                            return;
                        }
                        Object callback = args[args.length - 1];
                        if (callback == null) {
                            return;
                        }
                        String operation = args[0] != null ? args[0].toString() : "";
                        String requestData = args[4] != null ? args[4].toString() : "";
                        // 第 4 位留空给响应（由 sendJSONResponse 填充），与主进程配对逻辑一致
                        rpcHookMap.put(callback, new Object[]{
                                System.currentTimeMillis(),
                                "MINI " + operation,
                                clip(deepPrettyJson(requestData)),
                                null
                        });
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Object[] args = p.args;
                        if (args == null || args.length < 5) {
                            return;
                        }
                        Object callback = args[args.length - 1];
                        if (callback == null) {
                            return;
                        }
                        Object[] record = rpcHookMap.remove(callback);
                        if (record != null) {
                            // sendJSONResponse 若已回调则 record[3] 有值，否则响应为空
                            String response = record[3] != null ? record[3].toString() : "";
                            Log.debug("记录\n时间: " + record[0] + "\n方法: " + record[1]
                                    + "\n参数: " + record[2] + "\n数据: " + response + "\n");
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            if (miniDebugEnabled()) {
                Log.debug("小程序抓包hook已安装: " + ARIVER_RPC_BRIDGE_CLASS + ".rpc 进程=" + processName);
            }
        } catch (Throwable t) {
            if (miniDebugEnabled()) {
                Log.debug("小程序抓包hook安装失败: " + ARIVER_RPC_BRIDGE_CLASS + " " + t.getMessage());
            }
        }
    }

    /**
     * ariver bridge 响应回调 hook：sendJSONResponse(JSONObject) 将响应回传 JS，
     * 以 thisObject 为 key 从 rpcHookMap 取出暂存请求并填入响应字段，
     * 实际日志输出由 rpc.afterHookedMethod 完成（保证请求/响应同条输出）。
     */
    private void installAriverBridgeCallbackHook(Class<?> clazz) {
        try {
            XposedBridge.hookAllMethods(clazz, "sendJSONResponse", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        Object key = p.thisObject;
                        if (key == null) {
                            return;
                        }
                        Object[] record = rpcHookMap.get(key);
                        if (record == null) {
                            // 无配对请求：检查是否为 WebSocket 事件（onSocketMessage 等），
                            // 小游戏服务器推送数据通过此回调回传 JS
                            if (p.args != null && p.args.length > 0 && p.args[0] != null) {
                                String resp = p.args[0].toString();
                                // WebSocket 事件回调包含 socketTaskID 字段（onSocketMessage/onSocketOpen/onSocketClose 等）
                                if (resp.contains("socketTaskID") || resp.contains("\"type\":\"onSocket")) {
                                    Log.debug("记录\n时间: " + System.currentTimeMillis()
                                            + "\n方法: MINI WS recv"
                                            + "\n参数: \n数据: " + clip(deepPrettyJson(resp)) + "\n");
                                }
                            }
                            return;
                        }
                        if (p.args != null && p.args.length > 0 && p.args[0] != null) {
                            record[3] = clip(deepPrettyJson(p.args[0].toString()));
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            // sendBridgeResponse: 检查是否承载 WS 消息（BridgeResponse 可能包装事件数据）
            try {
                XposedBridge.hookAllMethods(clazz, "sendBridgeResponse", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            if (p.args == null || p.args.length == 0 || p.args[0] == null) {
                                return;
                            }
                            Object resp = p.args[0];
                            String s = resp.toString();
                            // 只记录可能包含 WS 数据的响应（短对象引用说明可能是事件）
                            if (s.contains("socket") || s.contains("Socket") || s.contains("message") || s.contains("Message")) {
                                Log.debug("BridgeCallback.sendBridgeResponse: " + s);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                });
            } catch (Throwable ignored) {
            }
            if (miniDebugEnabled()) {
                Log.debug("小程序抓包hook已安装: " + ARIVER_BRIDGE_CALLBACK_CLASS + ".sendJSONResponse 进程=" + processName);
            }
        } catch (Throwable t) {
            if (miniDebugEnabled()) {
                Log.debug("小程序抓包hook安装失败: " + ARIVER_BRIDGE_CALLBACK_CLASS + " " + t.getMessage());
            }
        }
    }

    /**
     * nebula RPC 入口 hook：H5RpcUtil.rpcCall(operationType, args, ...) 为同步调用，
     * 在 after 阶段直接取返回值（H5Response）的 getResponse 输出，无需配对。
     */
    private void installNebulaRpcHook(Class<?> clazz) {
        try {
            XposedBridge.hookAllMethods(clazz, "rpcCall", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Object[] args = p.args;
                        if (args == null || args.length < 2) {
                            return;
                        }
                        String operation = args[0] != null ? args[0].toString() : "";
                        String requestData = args[1] != null ? args[1].toString() : "";
                        String response = "";
                        Object result = p.getResult();
                        if (result != null) {
                            try {
                                Object resp = XposedHelpers.callMethod(result, "getResponse");
                                if (resp != null) {
                                    response = resp.toString();
                                }
                            } catch (Throwable ignored) {
                                response = result.toString();
                            }
                        }
                        Log.debug("记录\n时间: " + System.currentTimeMillis()
                                + "\n方法: MINI " + operation
                                + "\n参数: " + clip(deepPrettyJson(requestData))
                                + "\n数据: " + clip(deepPrettyJson(response)) + "\n");
                    } catch (Throwable ignored) {
                    }
                }
            });
            if (miniDebugEnabled()) {
                Log.debug("小程序抓包hook已安装: " + NEBULA_RPC_CLASS + ".rpcCall 进程=" + processName);
            }
        } catch (Throwable t) {
            if (miniDebugEnabled()) {
                Log.debug("小程序抓包hook安装失败: " + NEBULA_RPC_CLASS + " " + t.getMessage());
            }
        }
    }

    /**
     * ariver HTTP 请求桥接 hook：my.request 的原生入口。
     * 方法名与签名在不同版本间可能变化，故 hookAllMethods 并从参数中提取 URL/请求体，
     * 以最后一个参数（BridgeCallback）为 key 暂存，响应由 DefaultBridgeCallback.sendJSONResponse 配对。
     */
    private void installAriverHttpBridgeHook(Class<?> clazz) {
        try {
            XposedBridge.hookAllMethods(clazz, "httpRequest", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    stashHttpBridgeRequest(p);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    flushHttpBridgeRequest(p);
                }
            });
            // 兜底：部分版本方法名可能为 request / sendRequest，一并 hook 避免遗漏
            for (String m : new String[]{"request", "sendRequest"}) {
                try {
                    XposedBridge.hookAllMethods(clazz, m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            stashHttpBridgeRequest(p);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            flushHttpBridgeRequest(p);
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
            if (miniDebugEnabled()) {
                Log.debug("小程序抓包hook已安装: " + ARIVER_HTTP_BRIDGE_CLASS + " 进程=" + processName);
            }
        } catch (Throwable t) {
            if (miniDebugEnabled()) {
                Log.debug("小程序抓包hook安装失败: " + ARIVER_HTTP_BRIDGE_CLASS + " " + t.getMessage());
            }
        }
    }

    /** 从 HTTP bridge 方法参数中提取 URL 与请求体并暂存，等待响应配对 */
    private void stashHttpBridgeRequest(XC_MethodHook.MethodHookParam p) {
        try {
            Object[] args = p.args;
            if (args == null || args.length < 2) {
                return;
            }
            Object callback = args[args.length - 1];
            if (callback == null) {
                return;
            }
            String url = "";
            String body = "";
            // 遍历参数找 http(s) URL 与 JSON 请求体
            for (Object arg : args) {
                if (arg == null) {
                    continue;
                }
                String s = arg.toString();
                if (s.startsWith("http://") || s.startsWith("https://")) {
                    url = s;
                } else if (s.startsWith("{") && s.length() > body.length()) {
                    body = s;
                }
            }
            if (url.length() == 0 && body.length() == 0) {
                return;
            }
            String methodLine = url.length() > 0 ? ("MINI " + url) : "MINI httpRequest";
            rpcHookMap.put(callback, new Object[]{
                    System.currentTimeMillis(),
                    methodLine,
                    clip(deepPrettyJson(body)),
                    null
            });
        } catch (Throwable ignored) {
        }
    }

    /** HTTP bridge 请求结束后输出日志（响应由 sendJSONResponse 填入 record[3]） */
    private void flushHttpBridgeRequest(XC_MethodHook.MethodHookParam p) {
        try {
            Object[] args = p.args;
            if (args == null || args.length < 2) {
                return;
            }
            Object callback = args[args.length - 1];
            if (callback == null) {
                return;
            }
            Object[] record = rpcHookMap.remove(callback);
            if (record != null) {
                String response = record[3] != null ? record[3].toString() : "";
                Log.debug("记录\n时间: " + record[0] + "\n方法: " + record[1]
                        + "\n参数: " + record[2] + "\n数据: " + response + "\n");
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * ariver WebSocket 桥接 hook：捕获 connectSocket（连接地址）、sendSocketMessage（发送数据，含招募等操作）、
     * onSocketMessage（接收数据）。方法签名在不同版本间可能变化，故用 hookAllMethods 并按方法名分发处理。
     */
    private void installAriverWebSocketHook(Class<?> clazz) {
        try {
            // connectSocket: 记录 WebSocket 连接地址
            try {
                XposedBridge.hookAllMethods(clazz, "connectSocket", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            String url = extractWsUrl(p.args);
                            Log.debug("记录\n时间: " + System.currentTimeMillis()
                                    + "\n方法: MINI WS connect " + url
                                    + "\n参数: " + clip(deepPrettyJson(argsToJson(p.args)))
                                    + "\n数据: \n");
                        } catch (Throwable ignored) {
                        }
                    }
                });
            } catch (Throwable ignored) {
            }
            // sendSocketMessage: 捕获游戏发送的数据（招募/战斗等核心操作）
            try {
                XposedBridge.hookAllMethods(clazz, "sendSocketMessage", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            String data = extractWsData(p.args);
                            String socketId = extractSocketTaskId(p.args);
                            Log.debug("记录\n时间: " + System.currentTimeMillis()
                                    + "\n方法: MINI WS send socket=" + socketId
                                    + "\n参数: " + clip(deepPrettyJson(argsToJson(p.args)))
                                    + "\n数据: " + clip(data) + "\n");
                        } catch (Throwable ignored) {
                        }
                    }
                });
            } catch (Throwable ignored) {
            }
            // 接收消息：不同版本方法名可能不同，覆盖常见候选
            for (String m : new String[]{"onSocketMessage", "onMessage", "onReceiveMessage",
                    "handleMessage", "dispatchMessage", "notifyMessage", "onSocketTaskMessage"}) {
                try {
                    XposedBridge.hookAllMethods(clazz, m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                String data = extractWsData(p.args);
                                Log.debug("记录\n时间: " + System.currentTimeMillis()
                                        + "\n方法: MINI WS recv socket=" + extractSocketTaskId(p.args)
                                        + "\n参数: " + clip(deepPrettyJson(argsToJson(p.args)))
                                        + "\n数据: " + clip(data) + "\n");
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
            if (miniDebugEnabled()) {
                Log.debug("小程序抓包hook已安装: " + ARIVER_WS_BRIDGE_CLASS + " 进程=" + processName);
            }
        } catch (Throwable t) {
            if (miniDebugEnabled()) {
                Log.debug("小程序抓包hook安装失败: " + ARIVER_WS_BRIDGE_CLASS + " " + t.getMessage());
            }
        }
    }

    /**
     * OkHttp RealWebSocket 兜底 hook：当 ariver WebSocketBridgeExtension 不存在时，
     * 直接 hook 底层 send(String)/send(ByteString) 捕获所有 WebSocket 发送数据。
     */
    private void installOkHttpWebSocketHook(Class<?> clazz) {
        try {
            XposedBridge.hookAllMethods(clazz, "send", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        Object[] args = p.args;
                        if (args == null || args.length == 0 || args[0] == null) {
                            return;
                        }
                        Object arg = args[0];
                        String data;
                        if (arg instanceof String) {
                            data = (String) arg;
                        } else {
                            // ByteString 或 byte[]：转 base64 便于查看二进制协议数据
                            byte[] bytes = null;
                            try {
                                bytes = (byte[]) XposedHelpers.callMethod(arg, "toByteArray");
                            } catch (Throwable ignored) {
                            }
                            if (bytes == null && arg instanceof byte[]) {
                                bytes = (byte[]) arg;
                            }
                            data = bytes != null ? "base64:" + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP) : arg.toString();
                        }
                        Log.debug("记录\n时间: " + System.currentTimeMillis()
                                + "\n方法: MINI WS okhttp-send " + clip(data)
                                + "\n参数: \n数据: \n");
                    } catch (Throwable ignored) {
                    }
                }
            });
            if (miniDebugEnabled()) {
                Log.debug("小程序抓包hook已安装: " + OKHTTP_WEBSOCKET_CLASS + ".send 进程=" + processName);
            }
        } catch (Throwable t) {
            if (miniDebugEnabled()) {
                Log.debug("小程序抓包hook安装失败: " + OKHTTP_WEBSOCKET_CLASS + " " + t.getMessage());
            }
        }
    }

    /**
     * OkHttp WebSocketListener 兜底 hook：捕获服务器推送的消息。
     * onMessage(WebSocket, String) / onMessage(WebSocket, ByteString) 由 RealWebSocket
     * 在收到消息帧时回调，是最底层的接收入口，无论 ariver 如何封装都能命中。
     */
    private void installOkHttpWsListenerHook(Class<?> clazz) {
        try {
            XposedBridge.hookAllMethods(clazz, "onMessage", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        Object[] args = p.args;
                        if (args == null || args.length < 2 || args[1] == null) {
                            return;
                        }
                        Object msg = args[1];
                        String data;
                        if (msg instanceof String) {
                            data = "text:" + msg;
                        } else {
                            byte[] bytes = null;
                            try {
                                bytes = (byte[]) XposedHelpers.callMethod(msg, "toByteArray");
                            } catch (Throwable ignored) {
                            }
                            if (bytes == null && msg instanceof byte[]) {
                                bytes = (byte[]) msg;
                            }
                            data = bytes != null
                                    ? "base64:" + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP) + " len=" + bytes.length
                                    : msg.toString();
                        }
                        Log.debug("记录\n时间: " + System.currentTimeMillis()
                                + "\n方法: MINI WS recv"
                                + "\n参数: \n数据: " + clip(data) + "\n");
                    } catch (Throwable ignored) {
                    }
                }
            });
            if (miniDebugEnabled()) {
                Log.debug("小程序抓包hook已安装: " + OKHTTP_WS_LISTENER_CLASS + ".onMessage 进程=" + processName);
            }
        } catch (Throwable t) {
            if (miniDebugEnabled()) {
                Log.debug("小程序抓包hook安装失败: " + OKHTTP_WS_LISTENER_CLASS + " " + t.getMessage());
            }
        }
    }

    /**
     * WebSocket 客户端诊断 hook：针对 XRiver/Java-WebSocket/NV-WebSocket 等候选类，
     * hook 所有声明方法并记录方法名+参数；同时专门捕获 onMessage/onTextMessage/onBinaryMessage
     * 等接收回调，输出服务器推送数据。
     */
    private void installWsClientDiagnosticHook(Class<?> clazz, String name) {
        try {
            // 1. 诊断：记录所有方法调用
            for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                final String mname = m.getName();
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                int argc = p.args != null ? p.args.length : 0;
                                String firstArg = "";
                                if (p.args != null && p.args.length > 0 && p.args[0] != null) {
                                    firstArg = p.args[0].toString();
                                    if (firstArg.length() > 60) firstArg = firstArg.substring(0, 60);
                                }
                                Log.debug("WSClient方法: " + name + "#" + mname + " argc=" + argc + " arg0=" + firstArg);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
            // 2. 专门 hook 常见接收方法
            for (String recvMethod : new String[]{"onMessage", "onTextMessage", "onBinaryMessage",
                    "onWsMessage", "handleMessage", "dispatchMessage"}) {
                try {
                    XposedBridge.hookAllMethods(clazz, recvMethod, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                String data = extractWsData(p.args);
                                if (data.length() > 0) {
                                    Log.debug("记录\n时间: " + System.currentTimeMillis()
                                            + "\n方法: MINI WS recv"
                                            + "\n参数: " + clip(deepPrettyJson(argsToJson(p.args)))
                                            + "\n数据: " + clip(data) + "\n");
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
            if (miniDebugEnabled()) {
                Log.debug("小程序抓包hook已安装(WS客户端诊断): " + name + " 进程=" + processName);
            }
        } catch (Throwable t) {
            if (miniDebugEnabled()) {
                Log.debug("小程序抓包hook安装失败(WS客户端诊断): " + name + " " + t.getMessage());
            }
        }
    }

    /** 从 WebSocket bridge 方法参数中提取 URL（connectSocket 用） */
    private String extractWsUrl(Object[] args) {
        if (args == null) {
            return "";
        }
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            String s = arg.toString();
            if (s.startsWith("ws://") || s.startsWith("wss://")) {
                return s;
            }
            // 参数可能是 JSONObject，尝试取 url 字段
            if (s.startsWith("{")) {
                try {
                    org.json.JSONObject jo = new org.json.JSONObject(s);
                    if (jo.has("url")) {
                        return jo.getString("url");
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return "";
    }

    /** 从 WebSocket bridge 方法参数中提取消息数据（sendSocketMessage/onSocketMessage 用）。
     *  三国冰河时代等小游戏 sendSocketMessage 的 args[2] 是 byte[]（二进制协议），
     *  需转 base64；同时兼容 JSONObject.data / String 形式。 */
    private String extractWsData(Object[] args) {
        if (args == null) {
            return "";
        }
        // 1. 优先找 byte[]（游戏二进制协议数据）
        for (Object arg : args) {
            if (arg instanceof byte[]) {
                byte[] bytes = (byte[]) arg;
                // 尝试当 UTF-8 文本解码（部分协议用 JSON 文本）
                try {
                    String text = new String(bytes, "UTF-8");
                    if (text.length() > 0 && isPrintableText(text)) {
                        return "text:" + text;
                    }
                } catch (Throwable ignored) {
                }
                return "base64:" + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        + " len=" + bytes.length;
            }
        }
        // 2. 找 JSONObject.data 字段
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            String s = arg.toString();
            if (s.startsWith("{")) {
                try {
                    org.json.JSONObject jo = new org.json.JSONObject(s);
                    if (jo.has("data")) {
                        Object d = jo.get("data");
                        if (d instanceof String) {
                            return (String) d;
                        }
                        return d.toString();
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        // 3. 兜底：返回第一个非空字符串参数（排除纯数字的 socketTaskID）
        for (Object arg : args) {
            if (arg != null && arg instanceof String) {
                String s = (String) arg;
                if (s.length() > 0 && !s.matches("\\d+")) {
                    return s;
                }
            }
        }
        return "";
    }

    /** 从参数中提取 socketTaskID（通常是纯数字字符串） */
    private String extractSocketTaskId(Object[] args) {
        if (args == null) {
            return "";
        }
        // 优先从 JSONObject 取 socketTaskID
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            String s = arg.toString();
            if (s.startsWith("{")) {
                try {
                    org.json.JSONObject jo = new org.json.JSONObject(s);
                    if (jo.has("socketTaskID")) {
                        return jo.getString("socketTaskID");
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        // 兜底：返回第一个纯数字字符串
        for (Object arg : args) {
            if (arg != null && arg instanceof String && ((String) arg).matches("\\d+")) {
                return (String) arg;
            }
        }
        return "";
    }

    /** 判断字符串是否主要由可打印字符组成（用于区分文本协议和二进制协议） */
    private boolean isPrintableText(String s) {
        if (s == null || s.length() == 0) {
            return false;
        }
        int printable = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 32 && c < 127 || c == '\n' || c == '\r' || c == '\t') {
                printable++;
            }
        }
        return printable * 1.0 / s.length() > 0.7;
    }

    /** 将方法参数数组转为 JSON 字符串用于日志 */
    private String argsToJson(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            Object a = args[i];
            if (a == null) {
                sb.append("null");
            } else {
                String s = a.toString();
                // 转义 JSON 字符串中的特殊字符
                sb.append("\"").append(s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).append("\"");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 沿继承链逐层 hook 同名方法并返回 hook 到的方法总数。
     * XposedBridge.hookAllMethods 只覆盖目标类自身声明的方法，
     * onHandleResponse 可能定义在父类上，必须向上追溯。
     */
    private int hookMethodInHierarchy(Class<?> clazz, String methodName, XC_MethodHook callback) {
        int count = 0;
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                count += XposedBridge.hookAllMethods(current, methodName, callback).size();
            } catch (Throwable ignored) {
            }
            current = current.getSuperclass();
        }
        return count;
    }
}
