package io.github.lazyimmortal.sesame.hook;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * A simplified PageManager - only keeps Activity monitoring and Dialog tracking.
 */
@SuppressLint("StaticFieldLeak")
public class SimplePageManager {
    private static final String TAG = "SimplePageManager";
    
    private static WeakReference<Context> mContextRef;
    private static ClassLoader mClassLoader;
    private static Activity topActivity;
    
    private static final ConcurrentHashMap<String, ActivityFocusHandler> activityFocusHandlerMap = new ConcurrentHashMap<>();
    
    // 主线程 Handler（复用项目原生调度方式，替代协程 Dispatchers.Main）
    public static final Handler handler = new Handler(Looper.getMainLooper());
    
    private static int taskDuration = 500;
    // 加 volatile 保证多线程可见性（参考 BaseTask.java 并发设计）
    private static volatile boolean hasPendingActivityTask = false;
    private static boolean disable = false;
    
    private static final ArrayList<WeakReference<Dialog>> dialogs = new ArrayList<>();
    private static boolean windowMonitorEnabled = false;
    
    /**
     * Activity焦点处理器接口
     */
    public interface ActivityFocusHandler {
        boolean handleActivity(Activity activity, io.github.lazyimmortal.sesame.hook.SimpleViewImage root);
    }
    
    static {
        enablePageMonitor();
    }
    
    public static Context getContext() {
        return mContextRef != null ? mContextRef.get() : null;
    }
    
    public static ClassLoader getClassLoader() {
        return mClassLoader;
    }
    
    public static Activity getTopActivity() {
        return topActivity;
    }
    
    public static void setTaskDuration(int duration) {
        taskDuration = duration;
    }
    
    public static void setDisable(boolean disabled) {
        disable = disabled;
    }
    
    public static void addHandler(String activityClassName, ActivityFocusHandler handler) {
        activityFocusHandlerMap.put(activityClassName, handler);
    }
    
    public static void removeHandler(String activityClassName) {
        activityFocusHandlerMap.remove(activityClassName);
    }
    
    public static ArrayList<WeakReference<Dialog>> getDialogs() {
        return dialogs;
    }
    
    public static void enableWindowMonitoring(ClassLoader classLoader) {
        if (classLoader != null) {
            mClassLoader = classLoader;
        }
        Log.i(
                TAG,
                "启用窗口监控被调用，窗口监控已启用: " + windowMonitorEnabled + ", 类加载器: " + (mClassLoader != null ? mClassLoader.getClass().getName() : "null")
        );
        if (!windowMonitorEnabled) {
            enableWindowMonitor();
            windowMonitorEnabled = true;
        }
    }
    
    /**
     * 重载方法：兼容无参调用
     */
    public static void enableWindowMonitoring() {
        enableWindowMonitoring(null);
    }
    
    /**
     * 尝试在对话框中查找视图
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    public static io.github.lazyimmortal.sesame.hook.SimpleViewImage tryGetTopView(String xpath) {
        // 清理空引用
        Iterator<WeakReference<Dialog>> iterator = dialogs.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().get() == null) {
                iterator.remove();
            }
        }
        
        for (WeakReference<Dialog> dialogWeakReference : dialogs) {
            Dialog dialog = dialogWeakReference.get();
            if (dialog == null || !dialog.isShowing()) {
                continue;
            }
            View decorView = dialog.getWindow() != null ? dialog.getWindow().getDecorView() : null;
            if (decorView == null) {
                continue;
            }
            Log.d(TAG, "  - 对话框: " + dialog.getClass().getName() + ", 正在显示: " + dialog.isShowing());
            debugPrintAllTextViews(decorView, 0);
            
            io.github.lazyimmortal.sesame.hook.SimpleViewImage viewImage = new io.github.lazyimmortal.sesame.hook.SimpleViewImage(decorView);
            ArrayList<io.github.lazyimmortal.sesame.hook.SimpleViewImage> results = (ArrayList<SimpleViewImage>) SimpleXpathParser.evaluate(viewImage, xpath);
            if (!results.isEmpty()) {
                return results.get(0);
            }
        }
        return null;
    }
    
    /**
     * 打印所有 TextView 的文本内容（用于调试）
     */
    private static void debugPrintAllTextViews(View view, int depth) {
        String indent = "  ".repeat(depth);
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            String text = textView.getText() != null ? textView.getText().toString() : "";
            String contentDesc = textView.getContentDescription() != null ? textView.getContentDescription().toString() : "";
            
            if (!text.isEmpty() || !contentDesc.isEmpty()) {
                Log.d(
                        TAG,
                        indent + "文本视图[" + view.getClass().getSimpleName() + "] 文本='" + text + "' 内容描述='" + contentDesc + "'"
                );
            }
        }
        
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                debugPrintAllTextViews(viewGroup.getChildAt(i), depth + 1);
            }
        }
    }
    
    /**
     * 启用 Activity 监控
     */
    private static void enablePageMonitor() {
        try {
            CompatHelpers.findAndHookMethod(
                    Application.class,
                    "dispatchActivityResumed",
                    Activity.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            topActivity = (Activity) param.args[0];
                            if (mContextRef == null || mContextRef.get() == null) {
                                mContextRef = new WeakReference<>(topActivity.getApplicationContext());
                            }
                            mClassLoader = topActivity.getClassLoader();
                            triggerActivity();
                        }
                    }
            );
        } catch (Throwable e) {
            Log.e(TAG, "挂钩 Activity->dispatchActivityResumed 错误: ", e);
        }
    }
    
    /**
     * 如果对话框不存在则添加到监控列表
     */
    private static void addDialogIfNotExists(Dialog dialog, String source) {
        boolean exists = false;
        for (WeakReference<Dialog> ref : dialogs) {
            if (ref.get() == dialog) {
                exists = true;
                break;
            }
        }
        
        if (!exists) {
            dialogs.add(new WeakReference<>(dialog));
            Log.d(TAG, "对话框已从 " + source + " 添加，总数: " + dialogs.size());
            triggerDialogProcessing();
        } else {
            Log.d(TAG, "对话框从 " + source + " 已存在于列表中");
        }
    }
    
    /**
     * 挂钩对话框构造函数
     */
    private static void hookDialogConstructor(Class<?>... parameterTypes) {
        StringBuilder paramStr = new StringBuilder();
        for (Class<?> clazz : parameterTypes) {
            if (paramStr.length() > 0) {
                paramStr.append(",");
            }
            paramStr.append(clazz.getSimpleName());
        }
        
        try {
            CompatHelpers.findAndHookConstructor(
                    "android.app.Dialog",
                    getClassLoader(),
                    parameterTypes,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Dialog dialog = (Dialog) param.thisObject;
                            addDialogIfNotExists(dialog, "构造函数(" + paramStr + ")");
                        }
                    }
            );
        } catch (Throwable e) {
            Log.e(TAG, "挂钩对话框构造函数(" + paramStr + ") 错误: ", e);
        }
    }
    
    /**
     * 启用对话框监控
     */
    private static void enableWindowMonitor() {
        Log.i(TAG, "启用窗口监控被调用，类加载器: " + (mClassLoader != null ? mClassLoader.getClass().getName() : "null"));
        
        // Hook Dialog 不同构造函数
        hookDialogConstructor(Context.class);
        hookDialogConstructor(Context.class, int.class);
        try {
            // 兼容带OnCancelListener的构造函数
            Class<?> onCancelListenerClass = Class.forName("android.content.DialogInterface$OnCancelListener");
            hookDialogConstructor(Context.class, boolean.class, onCancelListenerClass);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "找不到DialogInterface.OnCancelListener类", e);
        }
        
        // Hook 支付宝验证码对话框
        try {
            Class<?> captchaDialogClass = XposedHelpers.findClass(
                    "com.alipay.rdssecuritysdk.v3.captcha.view.CaptchaDialog",
                    getClassLoader()
            );
            CompatHelpers.findAndHookMethod(
                    captchaDialogClass,
                    "show",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Dialog dialog = (Dialog) param.thisObject;
                            addDialogIfNotExists(dialog, "CaptchaDialog.show()");
                        }
                    }
            );
        } catch (Throwable e) {
            Log.e(TAG, "挂钩 CaptchaDialog.show() 错误: ", e);
        }
    }
    
    /**
     * 触发 Activity 处理
     */
    private static void triggerActivity() {
        triggerPendingActivityHandler("Activity 已恢复");
    }
    
    /**
     * 触发 Dialog 处理
     */
    private static void triggerDialogProcessing() {
        triggerPendingActivityHandler("Dialog 已创建");
    }
    
    /**
     * 触发待处理的 Activity 处理器
     */
    private static void triggerPendingActivityHandler(String source) {
        final Activity activity = topActivity;
        if (activity == null) {
            Log.i(TAG, "无法从 " + source + " 触发处理器，未找到顶层 Activity");
            return;
        }
        
        final ActivityFocusHandler handler = activityFocusHandlerMap.get(activity.getClass().getName());
        if (handler == null) {
            Log.d(TAG, "未找到 " + activity.getClass().getName() + " 的处理器，来源: " + source);
            return;
        }
        
        if (hasPendingActivityTask) {
            Log.d(TAG, "跳过从 " + source + " 触发，已有待处理任务");
            return;
        }
        
        hasPendingActivityTask = true;
        Log.i(TAG, "从 " + source + " 触发 " + activity.getClass().getName() + " 的处理器");
        triggerActivityActive(activity, handler, 0);
    }
    
    /**
     * 延迟触发 Activity 处理（替换原 Kotlin 协程逻辑，纯 Java 实现）
     */
    private static void triggerActivityActive(
            final Activity activity,
            final ActivityFocusHandler activityFocusHandler,
            final int triggerCount
    ) {
        if (disable) {
            Log.i(TAG, "页面触发管理器已禁用");
            return;
        }
        
        // 替代协程：主线程延迟执行（复用类内已定义的主线程 Handler）
        handler.postDelayed(() -> {
            try {
                hasPendingActivityTask = false;
                
                // 执行处理器逻辑（与原逻辑完全一致）
                View decorView = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
                if (decorView != null && activityFocusHandler.handleActivity(activity, new SimpleViewImage(decorView))) {
                    return; // 处理成功直接返回，终止重试
                }
            } catch (Throwable throwable) {
                Log.e(TAG, "处理 Activity 出错: " + activity.getClass().getName(), throwable);
            }
            
            // 递归重试（最多10次，与原逻辑一致）
            if (triggerCount <= 10) {
                triggerActivityActive(activity, activityFocusHandler, triggerCount + 1);
            } else {
                Log.w(TAG, "Activity 事件触发失败次数过多: " + activityFocusHandler.getClass().getName());
            }
        }, taskDuration); // 替代协程 delay()，延迟时长保持 taskDuration
    }
}