package io.github.lazyimmortal.sesame.hook;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import io.github.lazyimmortal.sesame.model.normal.base.BaseModel;
import io.github.lazyimmortal.sesame.util.Log;

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
            //uiHookUnhook = hookCaptchaDialogShow(classLoader);
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
    
    /*
     * 第一层拦截：阻止验证码对话框显示
     * <p>
     * Hook点: CaptchaDialog.show()
     * 作用: 阻止对话框显示，用户看不到验证码
     *
     * @param classLoader 类加载器
     * @return Hook卸载器，失败时返回null
     
    private static XC_MethodHook.Unhook hookCaptchaDialogShow(ClassLoader classLoader) {
        try {
            Class<?> captchaDialogClass = XposedHelpers.findClass(CLASS_CAPTCHA_DIALOG, classLoader);
            
            XC_MethodHook.Unhook unhook = XposedHelpers.findAndHookMethod(captchaDialogClass, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    // 1. 基础信息初始化（避免空指针）
                    Object dialogObj = param.thisObject;
                    StringBuilder dialogAllInfo = new StringBuilder();
                    String className = dialogObj.getClass().getName();
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
                        }
                        catch (Exception e) {
                            dialogAllInfo.append("所属上下文：获取失败 - ").append(e.getMessage()).append("\n");
                        }
                        
                        // 4. 强转为Dialog（兼容非AlertDialog的自定义Dialog）
                        Dialog dialog = null;
                        if (dialogObj instanceof Dialog) {
                            dialog = (Dialog) dialogObj;
                        }
                        else {
                            // 若不是Dialog子类，尝试反射获取Dialog实例（极端场景）
                            try {
                                Field dialogField = dialogObj.getClass().getDeclaredField("mDialog");
                                dialogField.setAccessible(true);
                                dialog = (Dialog) dialogField.get(dialogObj);
                            }
                            catch (Exception e) {
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
                        }
                        else {
                            dialogAllInfo.append("Dialog实例为空，无法获取控件信息\n");
                        }
                        
                        // 7. 打印完整日志（核心：所有元素内容）
                        Log.record(TAG + "\n" + dialogAllInfo.toString());
                        
                        // 8. 阻止对话框显示（无论内容是什么，都拦截；也可加文本判断）
                        if (dialogAllInfo.length() > 0) { // 先判断是否有内容
                            //if(dialogAllInfo.toString().contains("请检查是否使用了代理软件或VPN")||dialogAllInfo.toString().contains("访问被拒绝")){
                            if (dialogAllInfo.toString().contains("请检查是否使用了代理软件或VPN")) {
                                Log.record("包含指定字符");
                                //param.setResult(null);
                            }
                        }
                        Log.record("执行了hookCaptchaDialogShow");
                    }
                    // 阻止验证码对话框显示
                    //Log.other("hook+阻止验证码对话框显示param:" + param);
                    //param.setResult(null);
                    //Log.record(TAG + "✅ [UI层拦截] 已阻止验证码对话框显示");
                    //Log.record(TAG + "  对话框: " + param.thisObject.getClass().getSimpleName());
                }
            });
            
            Log.record(TAG + "✅ Hook CaptchaDialog.show() 成功");
            return unhook;
        }
        catch (Throwable e) {
            Log.record("❌ Hook CaptchaDialog.show() 失败");
            Log.printStackTrace(TAG, e);
            return null;
        }
    }
    
    public static void collectAllTextViewText(View rootView, StringBuilder info) {
        if (rootView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) rootView;
            // 遍历ViewGroup的所有子View
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                collectAllTextViewText(child, info); // 递归
            }
        }
        else if (rootView instanceof TextView) {
            // 是TextView，记录ID（若有）和文本
            TextView textView = (TextView) rootView;
            String viewId = textView.getId() != View.NO_ID ? rootView.getResources().getResourceEntryName(textView.getId()) : "无ID";
            String text = textView.getText().toString().trim();
            if (!text.isEmpty()) { // 只记录非空文本
                info.append("TextView(ID: ").append(viewId).append(")：").append(text).append("\n");
            }
        }
    }*/
    
}