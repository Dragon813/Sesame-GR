package io.github.Dragon813.sesame.hook;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import io.github.Dragon813.sesame.model.normal.base.BaseModel;
import io.github.Dragon813.sesame.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 支付宝滑块验证码Hook工具类（UI层拦截）
 * <p>
 * 核心策略：
 * Hook CaptchaDialog.show() - 阻止验证码对话框显示（UI层拦截）
 * <p>
 * 独立开关：
 * - enableCaptchaUIHook：UI层拦截开关（阻止对话框显示）
 * <p>
 * 使用方式：
 * CaptchaHook.setupHook(classLoader)
 * CaptchaHook.updateHooks(enableUI)  // 动态更新开关状态
 *
 * @author ghostxx
 * @since 2025-10-23
 */
public class CaptchaHook {
    
    // 定义静态实例
    public static final CaptchaHook INSTANCE = new CaptchaHook();
    
    // 私有构造方法，确保单例
    private CaptchaHook() {
    }
    
    private static final String TAG = "CaptchaHook";
    
    /**
     * 验证码对话框类名
     */
    private static final String CLASS_CAPTCHA_DIALOG = "com.alipay.rdssecuritysdk.v3.captcha.view.CaptchaDialog";
    
    /**
     * UI层Hook卸载器（用于动态控制）
     */
    private static XC_MethodHook.Unhook uiHookUnhook;
    
    /**
     * 保存ClassLoader供后续使用
     */
    private static ClassLoader savedClassLoader;
    
    /**
     * 初始化Hook系统
     *
     * @param classLoader 目标应用的ClassLoader
     */
    public static void setupHook(ClassLoader classLoader) {
        savedClassLoader = classLoader;
        Log.i(TAG + "验证码Hook系统初始化完成");
        Log.i(TAG + "⚠️ Hook配置将在配置文件加载后同步");
        
        // 注意：此时配置文件还未加载，不能立即应用Hook
        // 实际的Hook应用会在BaseModel.boot()中进行
    }
    
    /**
     * 动态更新Hook开关状态
     *
     * @param enableUI 是否启用UI层拦截
     */
    public static void updateHooks(boolean enableUI) {
        ClassLoader classLoader = savedClassLoader;
        if (classLoader == null) {
            Log.i("❌ ClassLoader未初始化，请先调用setupHook()");
            return;
        }
        
        Log.i(TAG + "📝 更新验证码Hook状态:");
        Log.record(TAG + "  UI层拦截: " + (enableUI ? "✅ 开启" : "⛔ 关闭"));
        
        // 先卸载所有现有Hook
        unhookAll();
        
        // 根据开关状态重新Hook
        if (enableUI) {
            Log.i(TAG + "  🔧 设置UI层拦截...");
            uiHookUnhook = hookCaptchaDialogShowAndClose(classLoader);
        }
        else {
            Log.i(TAG + "  ⚠️ 验证码拦截已关闭");
        }
        
        Log.i(TAG + "验证码Hook更新完成 ✅");
    }
    
    /**
     * 卸载所有Hook
     */
    private static void unhookAll() {
        if (uiHookUnhook != null) {
            uiHookUnhook.unhook();
            uiHookUnhook = null;
        }
    }
    
    
    
    /**
     * 拦截逻辑：在show()执行后关闭对话框
     */
    private static XC_MethodHook.Unhook hookCaptchaDialogShowAndClose(ClassLoader classLoader) {
        try {
            Class<?> captchaDialogClass = XposedHelpers.findClass(CLASS_CAPTCHA_DIALOG, classLoader);
            
            return XposedHelpers.findAndHookMethod(captchaDialogClass, "show", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    // show()执行后触发
                    Object dialogObj = param.thisObject;
                    StringBuilder dialogAllInfo = new StringBuilder();
                    dialogAllInfo.append("===== 支付宝CaptchaDialog信息 =====\n");
                    dialogAllInfo.append("对话框类名：").append(dialogObj.getClass().getName()).append("\n");
                    
                    // 获取Dialog实例
                    Dialog dialog = getDialogInstance(dialogObj);
                    if (dialog == null) {
                        Log.i(TAG + "无法获取Dialog实例，关闭失败");
                        return;
                    }
                    
                    // 收集对话框信息（保持原有逻辑）
                    collectDialogInfo(dialog, dialogAllInfo);
                    Log.i(TAG + "\n" + dialogAllInfo.toString());
                    
                    // 关闭对话框
                    if (dialogAllInfo.toString().contains("请检查是否使用了代理软件或VPN")) {
                        Log.record("包含\"请检查是否使用了代理软件或VPN\",关闭对话框");
                        dialog.dismiss(); // 关键：在show()后关闭窗口
                    }
                    Log.record("执行了弹窗检测hookCaptchaDialogShowAndClose()");
                    
                }
            });
        } catch (Throwable e) {
            Log.i("❌ Hook CaptchaDialog.show() 失败");
            Log.printStackTrace(TAG, e);
            return null;
        }
    }
    
    /**
     * 获取Dialog实例（兼容直接实例和反射获取）
     */
    private static Dialog getDialogInstance(Object dialogObj) {
        if (dialogObj instanceof Dialog) {
            return (Dialog) dialogObj;
        }
        // 尝试反射获取内部Dialog实例
        try {
            Field dialogField = dialogObj.getClass().getDeclaredField("mDialog");
            dialogField.setAccessible(true);
            return (Dialog) dialogField.get(dialogObj);
        } catch (Exception e) {
            Log.i("反射获取Dialog实例失败：" + e.getMessage());
            return null;
        }
    }
    
    /**
     * 收集对话框信息（复用原有逻辑）
     */
    private static void collectDialogInfo(Dialog dialog, StringBuilder info) {
        // 获取上下文
        try {
            Field mContextField = dialog.getClass().getSuperclass().getDeclaredField("mContext");
            mContextField.setAccessible(true);
            Context context = (Context) mContextField.get(dialog);
            info.append("所属上下文：").append(context != null ? context.getClass().getName() : "null").append("\n");
        } catch (Exception e) {
            info.append("所属上下文：获取失败 - ").append(e.getMessage()).append("\n");
        }
        
        // 系统标准控件信息
        TextView titleView = dialog.findViewById(android.R.id.title);
        info.append("系统标题：").append(titleView != null ? titleView.getText().toString().trim() : "无").append("\n");
        
        TextView messageView = dialog.findViewById(android.R.id.message);
        info.append("系统消息：").append(messageView != null ? messageView.getText().toString().trim() : "无").append("\n");
        
        // 收集所有TextView内容
        info.append("===== 自定义布局文本 =====\n");
        View rootView = dialog.getWindow().getDecorView().getRootView();
        collectAllTextViewText(rootView, info);
    }
    
    public static void collectAllTextViewText(View rootView, StringBuilder info) {
        if (rootView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) rootView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                collectAllTextViewText(viewGroup.getChildAt(i), info);
            }
        } else if (rootView instanceof TextView) {
            TextView textView = (TextView) rootView;
            String text = textView.getText().toString().trim();
            if (!text.isEmpty()) {
                info.append("TextView：").append(text).append("\n");
            }
        }
    }
    
    
}