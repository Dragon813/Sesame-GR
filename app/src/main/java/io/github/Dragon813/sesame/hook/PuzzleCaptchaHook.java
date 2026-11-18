package io.github.Dragon813.sesame.hook;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import io.github.Dragon813.sesame.util.FeatureFlagStore;
import io.github.Dragon813.sesame.util.Log;

/**
 * 拼图验证处理：
 *
 * 1. CaptchaDialog.show()：识别“请进行验证”提示，并在弹窗显示后按能力驱动滑块：
 *    - 模式 A：存在 u/t/C + k()，直接写字段并调用 k() 提交，不产生任何手势；
 *    - 模式 B：存在 n/m/v，按旧版滑动驱动的取值写字段，并把 r0 Runnable 交给 ThreadPoolFrame 执行；
 *    - 兜底：两种字段都不匹配时，复用旧版 Captcha1/2Handler 的 MotionEvent 滑动；
 * 2. CaptchaDialogUtil.a(Context, Map, View)：替换为后台验证入口；
 * 3. CaptchaManager.isNeedUseH5Captcha(Map)：返回 false，强制原生分支；
 * 4. CaptchaVerifyModule.onCaptchaVerify(Map, X)：移除 type 参数；
 * 5. CaptchaManager.waitPageResult()：结果非 true 时执行解锁恢复；
 * 6. RpcRdsUtilImpl.rdsCaptchaHandle(7 参数)：结果 5/6 或异常时执行解锁恢复；
 * 7. <前缀>.RdsHandler.openRdsCaptchaPage(Map, X, boolean)：
 *    actionType 为 captcha_repeat/captcha_no_repeat、tips 为“请进行验证”且当前无前台 Activity 时，
 *    阻断页面并转后台处理。
 *
 * 说明：类名按候选列表和 CaptchaDialog 的包名前缀推导；
 * 视图字段 e、方法 u/t/k/w/o 及提示文案随版本变化，找不到时保留原流程。
 */
public final class PuzzleCaptchaHook {

    private static final String TAG = PuzzleCaptchaHook.class.getSimpleName();

    private static final String[] CAPTCHA_DIALOG_CLASSES = {
            "com.alipay.rdssecuritysdk.v3.captcha.view.CaptchaDialog",
            "com.alipay.mobile.security.captcha.CaptchaDialog"
    };
    private static final String[] CAPTCHA_MANAGER_CLASSES = {
            "com.alipay.apmobilesecuritysdk.captcha.CaptchaManager",
            "com.alipay.mobile.security.captcha.CaptchaManager",
            "com.alipay.rdssecuritysdk.v3.captcha.CaptchaManager"
    };
    private static final String[] VERIFY_MODULE_CLASSES = {
            "com.alipay.blueshield.CaptchaVerifyModule",
            "com.alipay.mobile.security.captcha.CaptchaVerifyModule",
            "com.alipay.rdssecuritysdk.v3.captcha.CaptchaVerifyModule"
    };
    private static final String[] RPC_RDS_UTIL_CLASSES = {
            "com.alipay.edge.observer.rpc.RpcRdsUtilImpl",
            "com.alipay.rdssecuritysdk.v3.captcha.RpcRdsUtilImpl"
    };
    /** CaptchaDialogUtil 完整类名随版本变化，这里按包名候选探测。 */
    private static final String[] DIALOG_UTIL_CLASSES = {
            "com.alipay.rdssecuritysdk.v3.captcha.activity.CaptchaDialogUtil",
            "com.alipay.rdssecuritysdk.v3.captcha.CaptchaDialogUtil",
            "com.alipay.mobile.security.captcha.activity.CaptchaDialogUtil",
            "com.alipay.mobile.security.captcha.CaptchaDialogUtil"
    };
    private static final String[] RDS_HANDLER_CLASSES = {
            "com.alipay.edge.observer.rpc.RdsHandler",
            "com.alipay.rdssecuritysdk.v3.captcha.RdsHandler",
            "com.alipay.mobile.security.captcha.RdsHandler"
    };

    /** 模式 B 的滑块 Runnable：mobilesecuritysdk 中固定的混淆类。 */
    private static final String[] SLIDE_RUNNABLE_CLASSES = {
            "com.alipay.android.phone.thirdparty.mobilesecuritysdk.obfuscated.r0"
    };

    /** 模式 B 的线程池：ThreadPoolFrame.a() 取单例，g(Runnable) 投递任务。 */
    private static final String[] THREAD_POOL_FRAME_CLASSES = {
            "com.alipay.apmobilesecuritysdk.tool.thread.ThreadPoolFrame"
    };

    /** 默认的异常提示关键词，命中后自动关闭提示栏。 */
    private static final String[] TIP_KEYWORDS = {
            "访问被拒绝", "人气太旺", "设置功能存在风险"
    };
    /** 未知版本的兜底文案；实际字面量通过运行时学习得到。 */
    private static final String TIP_VERIFY = "请进行验证";
    private static final String PREFS_NAME = "captcha_tips";
    private static final String KEY_TIPS = "verify_tips";

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static volatile String sVerifyTips;
    private static volatile boolean sTipsLoaded;
    /** 页面路径的“请手动完成”提示节流，避免被动扫描反复失败时刷屏。 */
    private static long sLastPageManualAt;
    private static final long PAGE_MANUAL_REPORT_INTERVAL_MS = 60_000L;

    private static boolean installed;
    private static ClassLoader sClassLoader;
    private static Class<?> sDialogUtilClass;
    /** 以 CaptchaDialog 为锚点解析出的同族类包前缀，用于推导同包其它类名。 */
    private static volatile String sCaptchaRootPackage;
    private static final AtomicBoolean sViewDumped = new AtomicBoolean(false);
    /**
     * 每个弹窗最多自动尝试次数：首次提交成功弹窗即关闭，不会再有下次；
     * 只有首次确实失败、验证码刷新后才允许第二次，避免同一验证被连续提交。
     */
    private static final Map<Object, Integer> sDirectSubmitAttempts =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final int MAX_DIRECT_SUBMIT_ATTEMPTS = 2;
    /** 同一弹窗两次自动提交的最小间隔，避免重复提交产生多次成功回执。 */
    private static final long DIRECT_SUBMIT_DEBOUNCE_MS = 1200L;
    /**
     * show() 返回时验证视图通常还没完成布局、回调与验证数据也未注入，
     * 此时直接提交必然失败，表现为“第一次要手动完成”。
     */
    private static final long SLIDER_READY_POLL_INTERVAL_MS = 200L;
    /** 中间版弹窗要等内部异步 show 完成，5 秒内都可提交。 */
    private static final int SLIDER_READY_MAX_POLLS = 25;
    private static final Map<Object, Long> sLastDirectSubmitAt =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** 正在等待视图就绪的弹窗，防止 show() 多次回调时重复排队。 */
    private static final Map<Object, Boolean> sDirectSubmitScheduled =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** 每个弹窗只提示一次“自动验证失败，请手动完成”，避免重复弹 Toast。 */
    private static final Map<Object, Boolean> sSuccessReported =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** 每个弹窗只提示一次“无自动验证能力，请手动完成”。 */
    private static final Map<Object, Boolean> sManualNeededReported =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** 已处理过的弹窗，避免 show() 与 Dialog.onStart 重复处理。 */
    private static final Map<Object, Boolean> sShowHandled =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static Class<?> sRdsHandlerClass;
    private static volatile WeakReference<Activity> sResumedActivity = new WeakReference<>(null);
    private static volatile boolean sLifecycleRegistered;

    private PuzzleCaptchaHook() {
    }

    public static synchronized void install(ClassLoader classLoader) {
        if (installed || classLoader == null) {
            return;
        }
        installed = true;
        sClassLoader = classLoader;

        hookClassGroup(candidates(CAPTCHA_DIALOG_CLASSES,
                "view.CaptchaDialog", "CaptchaDialog"), PuzzleCaptchaHook::hookDialogShow);
        hookDialogShowFallback();
        hookActivityDiagnostics();
        hookH5RiskUrl();
        hookClassGroup(candidates(CAPTCHA_MANAGER_CLASSES,
                "CaptchaManager"), PuzzleCaptchaHook::hookCaptchaManager);
        hookClassGroup(candidates(VERIFY_MODULE_CLASSES,
                "CaptchaVerifyModule"), PuzzleCaptchaHook::hookVerifyModule);
        hookClassGroup(candidates(RPC_RDS_UTIL_CLASSES,
                "RpcRdsUtilImpl"), PuzzleCaptchaHook::hookRpcRdsUtil);
        hookSubmitDiagnostics();

        sDialogUtilClass = resolveFirst(candidates(DIALOG_UTIL_CLASSES,
                "CaptchaDialogUtil", "activity.CaptchaDialogUtil"));
        if (sDialogUtilClass != null) {
            hookDialogUtil(sDialogUtilClass);
        } else {
            Log.i(TAG, "未找到 CaptchaDialogUtil，后台验证入口跳过");
        }

        sRdsHandlerClass = resolveRdsHandler();
        if (sRdsHandlerClass != null) {
            hookOpenRdsCaptchaPage(sRdsHandlerClass);
        } else {
            Log.i(TAG, "未解析到 RdsHandler，页面阻断跳过");
        }
        // 启动后自动做一次 dry-run：只验证构造与字段，不触发验证、不回报结果。
        // dry-run 只在支付宝主进程执行，避免子进程重复输出结构日志。
        if (ApplicationHook.processName == null
                || !ApplicationHook.processName.contains(":")) {
            MAIN_HANDLER.postDelayed(PuzzleCaptchaHook::dryRun, 3000L);
        }
    }

    private interface ClassHook {
        void hook(Class<?> clazz);
    }

    private static void hookClassGroup(String[] classNames, ClassHook hook) {
        for (String className : classNames) {
            Class<?> clazz = findClassQuietly(className);
            if (clazz == null) {
                continue;
            }
            try {
                hook.hook(clazz);
            } catch (Throwable t) {
                Log.printStackTrace(TAG + "#处理目标类失败: " + className, t);
            }
        }
    }

    /** 1. CaptchaDialog.show()：提示识别与视图预处理。 */
    private static void hookDialogShow(Class<?> clazz) {
        Method show;
        try {
            show = clazz.getDeclaredMethod("show");
        } catch (NoSuchMethodException e) {
            Log.i(TAG, "未找到 show 方法，跳过: " + clazz.getName());
            return;
        }
        XpCompatEngine.hookMethod(show, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!FeatureFlagStore.isSlideCaptchaEnabled()) {
                    return;
                }
                Object dialog = param.thisObject;
                Map<?, ?> map = readMapField(dialog);
                if (map == null) {
                    return;
                }
                String tips = asString(map.get("tips"));
                if (!isVerifyTips(tips)) {
                    return;
                }
                View view = readViewField(dialog);
                if (view == null) {
                    return;
                }
                learnVerifyTips(tips);
                Log.i(TAG, "检测到验证提示，模式A准备字段直提: " + tips);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                handleDialogShown(param.thisObject);
            }
        });
        hookValidateResult(clazz);
        Log.i(TAG, "CaptchaDialog.show 已挂载: " + clazz.getName());
    }

    /**
     * 弹窗显示后的统一处理：先记录结构与提示，便于日志定位；
     * 结构可识别时自动提交，不可识别时保留弹窗并提示手动完成。
     */
    private static void handleDialogShown(Object dialog) {
        if (dialog == null) {
            return;
        }
        // show()/Dialog.onStart 可能对同一个弹窗多次回调，这里只处理一次。
        if (sShowHandled.put(dialog, Boolean.TRUE) != null) {
            return;
        }
        if (!FeatureFlagStore.isSlideCaptchaEnabled()) {
            Log.i(TAG, "滑动验证开关未开启，跳过: " + dialog.getClass().getName());
            return;
        }
        Map<?, ?> map = readMapField(dialog);
        View view = readViewField(dialog);
        String tips = map == null ? null : asString(map.get("tips"));
        SliderMode mode = detectSliderMode(view);
        Log.i(TAG, "弹窗show: " + dialog.getClass().getName()
                + " map=" + (map == null ? "null" : map.keySet())
                + " view=" + (view == null ? "null" : view.getClass().getName())
                + " tips=" + tips
                + " 模式=" + mode);
        if (map == null && view == null) {
            return;
        }
        if (view != null && sViewDumped.compareAndSet(false, true)) {
            dumpStructure(view.getClass());
        }
        // 保重点：模式A（字段直提）优先；不可用或没通过再走拼图手势方案；最后才提示手动完成。
        if (handleDialogShownLegacy(dialog, tips, view)) {
            return;
        }
        if (!armPuzzle(dialog, view, tips)) {
            if (map != null) {
                handleTips(dialog, map, view);
            } else {
                reportManualNeeded(dialog, tips);
            }
        }
    }

    /** 项目原有的验证弹窗逻辑：字段直提 / 滑动驱动。返回 true 表示已接管，不用再往下走。 */
    private static boolean handleDialogShownLegacy(Object dialog, String tips, View view) {
        if (view != null && (isVerifyTips(tips) || detectSliderMode(view) != SliderMode.UNKNOWN)) {
            // 提示文案变化时，只要视图结构可识别（u/t/C 或 n/m/v）同样自动提交。
            // show() 返回时视图往往尚未布局完成，先等待就绪再提交。
            return scheduleDirectSubmit(dialog, view);
        }
        return false;
    }

    /** 拼图手势方案：验证内容确实在 WebView 里才接管，失败回调里提示手动完成。 */
    private static boolean armPuzzle(Object dialog, View view, String tips) {
        if (dialog instanceof Dialog && view != null && PuzzleCaptchaSolver.containsWebView(view)) {
            final Dialog d = (Dialog) dialog;
            return PuzzleCaptchaSolver.arm("验证弹窗", d,
                    () -> reportManualNeeded(d, tips), 15);
        }
        return false;
    }

    /**
     * 诊断用：记录每次前台 Activity 的类名；XRiver（Nebula/H5）页面出现时启动拼图自动验证。
     */
    private static void hookActivityDiagnostics() {
        try {
            XposedHelpers.findAndHookMethod(android.app.Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!FeatureFlagStore.isSlideCaptchaEnabled()) {
                        return;
                    }
                    Object activity = param.thisObject;
                    String name = activity.getClass().getName();
                    Log.i(TAG, "Activity 显示: " + name);
                    String lower = name.toLowerCase(Locale.ROOT);
                    if ((lower.contains("xriver") || lower.contains("nebula") || lower.contains("h5activity"))
                            && activity instanceof Activity) {
                        armPuzzleForPage((Activity) activity);
                    }
                }
            });
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#挂载 Activity 诊断失败", t);
        }
    }

    /**
     * XRiver/Nebula 页面：先静默扫描，识别到 H5 拼图滑块后按拼图方案（截图+缺口识别+拖动）自动过验证；
     * 拼图自动尝试失败时再跑项目原有的“滑动到底”页面处理器。
     * <p>
     * 兜底要跑完整的旧逻辑：{@link Captcha1Handler} 和 {@link Captcha2Handler} 分别对应历史保存的两条
     * 滑动路径（slide_path_Sheng_Huo / slide_path_API），哪个能识别到页面上的“向右滑动验证”就用哪个。
     * 只跑其中一个时，另一条路径的页面会表现为“未发现滑动验证”，等于没兜底。
     * 弹窗场景的“模式A/B 直接写字段提交”在 {@link #handleDialogShownLegacy} 里，页面路径拿不到 CaptchaDialog
     * 对象，这里只负责视图层的滑动兜底。
     */
    private static void armPuzzleForPage(final Activity activity) {
        final View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (decor == null) {
            return;
        }
        Runnable legacy = () -> new Thread(() -> {
            try {
                boolean handled = new Captcha1Handler()
                        .handleActivity(activity, new SimpleViewImage(decor));
                if (!handled) {
                    handled = new Captcha2Handler()
                            .handleActivity(activity, new SimpleViewImage(decor));
                }
                Log.i(TAG, "本地滑动验证(页面): " + (handled ? "已触发" : "未发现滑动验证"));
                // 顺序：拼图手势 → 本地滑动 → 提示手动。只有真的拖过拼图（说明页面上确实有验证）
                // 才提示，避免普通页面被动扫描后误报。
                if (!handled && PuzzleCaptchaSolver.sawPuzzle()) {
                    reportManualForPage();
                }
            } catch (Throwable t) {
                Log.printStackTrace(TAG + "#页面滑动验证失败", t);
            }
        }, "CaptchaLegacyPageSwipe").start();
        PuzzleCaptchaSolver.armPassive("XRiver页面", legacy);
    }

    /** H5 风险页 URL 关键词（与 Sesame-M 一致），命中即启动拼图自动验证。 */
    private static final String[] H5_RISK_KEYWORDS = {
            "captcha", "180020010001270421", "x-dispose-trace",
            "disposeapplication", "disposedname", "disposename"
    };
    /** 支付宝 H5 可能用自带 WebView 渲染，两个类都尝试挂钩。 */
    private static final String[] WEBVIEW_CLASSES = {
            "android.webkit.WebView", "com.alipay.mywebview.sdk.WebView"
    };
    /** 避免同一个 H5 页面重复 loadUrl 时反复启动监视、刷日志。 */
    private static volatile String sLastH5RiskUrl;
    private static volatile long sLastH5ArmAt;

    /**
     * H5 验证页触发：Hook WebView.loadUrl，URL 命中验证/处置页关键词时启动拼图自动验证。
     * 这样验证页在 onResume 之前就加载出来的场景也能被接管。
     */
    private static void hookH5RiskUrl() {
        for (String className : WEBVIEW_CLASSES) {
            Class<?> clazz = findClassQuietly(className);
            if (clazz == null) {
                continue;
            }
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!FeatureFlagStore.isSlideCaptchaEnabled() || param.args.length == 0) {
                        return;
                    }
                    inspectRiskUrl(param.args[0]);
                }
            };
            try {
                XposedHelpers.findAndHookMethod(clazz, "loadUrl", String.class, hook);
                XposedHelpers.findAndHookMethod(clazz, "loadUrl", String.class, Map.class, hook);
                Log.i(TAG, "WebView.loadUrl Hook 已挂载: " + className);
            } catch (Throwable t) {
                Log.printStackTrace(TAG + "#挂载 WebView.loadUrl 失败: " + className, t);
            }
        }
    }

    private static void inspectRiskUrl(Object value) {
        if (!(value instanceof String)) {
            return;
        }
        String url = (String) value;
        String lower = url.toLowerCase(Locale.ROOT);
        for (String keyword : H5_RISK_KEYWORDS) {
            if (!lower.contains(keyword)) {
                continue;
            }
            long now = SystemClock.uptimeMillis();
            if (url.equals(sLastH5RiskUrl) && now - sLastH5ArmAt < 10_000L) {
                return;
            }
            sLastH5RiskUrl = url;
            sLastH5ArmAt = now;
            Log.i(TAG, "H5 风险页命中(" + keyword + ")，启动拼图自动验证: " + shorten(url));
            PuzzleCaptchaSolver.arm("H5风险页");
            return;
        }
    }

    private static String shorten(String url) {
        return url.length() <= 160 ? url : url.substring(0, 160) + "...";
    }

    /**
     * 兜底：候选列表未覆盖的验证弹窗（类名含 captcha/verify）也走同一处理逻辑，
     * 避免版本更名后弹窗既不自动验证、也无法提示手动完成。
     */
    private static void hookDialogShowFallback() {
        try {
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!FeatureFlagStore.isSlideCaptchaEnabled()) {
                        return;
                    }
                    Object dialog = param.thisObject;
                    if (dialog == null) {
                        return;
                    }
                    Class<?> clazz = dialog.getClass();
                    if (!isKnownCaptchaDialog(clazz)) {
                        String name = clazz.getName().toLowerCase(Locale.ROOT);
                        if (!name.contains("captcha") && !name.contains("verify")) {
                            return;
                        }
                    }
                    handleDialogShown(dialog);
                }
            };
            // show() 被子类重写且不回调 super 时不会触发；onStart 由所有弹窗显示时统一调用。
            XposedHelpers.findAndHookMethod(Dialog.class, "show", hook);
            XposedHelpers.findAndHookMethod(Dialog.class, "onStart", hook);
            Log.i(TAG, "兜底 Dialog.show/onStart Hook 已挂载");
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#挂载兜底 Dialog Hook 失败", t);
        }
    }

    private static boolean isKnownCaptchaDialog(Class<?> clazz) {
        for (String name : CAPTCHA_DIALOG_CLASSES) {
            if (name.equals(clazz.getName())) {
                return true;
            }
        }
        return false;
    }

    /** CaptchaDialog.b(ValidateResult)：记录真实验证结果，可判定时同时提示。 */
    private static void hookValidateResult(Class<?> clazz) {
        int count = 0;
        Class<?> current = clazz;
        while (current != null && current != Object.class && count == 0) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!"b".equals(method.getName()) || parameters.length != 1
                        || !"ValidateResult".equals(parameters[0].getSimpleName())) {
                    continue;
                }
                XpCompatEngine.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!FeatureFlagStore.isSlideCaptchaEnabled()) {
                            return;
                        }
                        Object result = param.args[0];
                        Boolean success = parseValidateSuccess(result);
                        String detail = describeValidateResult(result)
                                + (success == null ? "" : (success ? " => 成功" : " => 失败"));
                        Log.i(TAG, "CaptchaDialog.b(ValidateResult): " + detail);
                        if (success != null) {
                            notifyValidateResult(param.thisObject, success);
                        }
                    }
                });
                count++;
            }
            current = current.getSuperclass();
        }
        Log.i(TAG, "CaptchaDialog.b(ValidateResult) Hook 数量: " + count);
    }

    /** 只读诊断：记录真实提交前的字段与提交请求体，不修改任何参数。 */
    private static void hookSubmitDiagnostics() {
        Class<?> dialogClass = findClassQuietly(CAPTCHA_DIALOG_CLASSES[0]);
        if (dialogClass != null) {
            for (Method method : dialogClass.getDeclaredMethods()) {
                if (!"c".equals(method.getName()) || method.getParameterTypes().length != 6) {
                    continue;
                }
                XpCompatEngine.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!FeatureFlagStore.isSlideCaptchaEnabled()) {
                            return;
                        }
                        Object[] args = param.args;
                        Log.i(TAG, "提交请求: encryptVersion=" + args[1]
                                + "，encryptData长度=" + lengthOf(args[0])
                                + "，extra长度=" + lengthOf(args[2])
                                + "，B=" + args[3] + "，elapsed=" + args[4] + "，C=" + args[5]);
                    }
                });
            }
        }
        Class<?> barClass = resolveFirst(candidates(new String[]{
                "com.alipay.rdssecuritysdk.v3.captcha.view.CaptchaProcessBar"},
                "view.CaptchaProcessBar"));
        if (barClass == null) {
            return;
        }
        for (Method method : barClass.getDeclaredMethods()) {
            if (!"k".equals(method.getName()) || method.getParameterTypes().length != 0) {
                continue;
            }
            XpCompatEngine.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!FeatureFlagStore.isSlideCaptchaEnabled()) {
                        return;
                    }
                    Object bar = param.thisObject;
                    Log.i(TAG, "提交前参数: u=" + readObjectField(bar, "u")
                            + "，t=" + readObjectField(bar, "t")
                            + "，B=" + readObjectField(bar, "B")
                            + "，C=" + readObjectField(bar, "C")
                            + "，回调非空=" + (readObjectField(bar, "w") != null)
                            + "，RDSClient非空=" + (readObjectField(bar, "x") != null));
                }
            });
        }
    }

    private static int lengthOf(Object value) {
        return value instanceof String ? ((String) value).length() : -1;
    }

    private static Object readObjectField(Object target, String name) {
        if (target == null) {
            return null;
        }
        Field field = findField(target.getClass(), name);
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 汇总 ValidateResult 的简单字段，用于观察真实返回结构。 */
    private static String describeValidateResult(Object result) {
        if (result == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(result.getClass().getSimpleName());
        Class<?> current = result.getClass();
        int printed = 0;
        while (current != null && current != Object.class && printed < 8) {
            for (Field field : current.getDeclaredFields()) {
                if (printed >= 8) {
                    break;
                }
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(result);
                    if (value == null || value instanceof Map || value instanceof Collection) {
                        continue;
                    }
                    builder.append(", ").append(field.getName()).append('=').append(value);
                    printed++;
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return builder.toString();
    }

    /** 尽力从 ValidateResult 中取成功标志；结构未知时返回 null。 */
    private static Boolean parseValidateSuccess(Object result) {
        if (result == null) {
            return null;
        }
        Class<?> current = result.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType() != boolean.class && field.getType() != Boolean.class) {
                    continue;
                }
                String name = field.getName().toLowerCase();
                if (!name.contains("success") && !name.contains("valid")
                        && !"pass".equals(name)) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(result);
                    if (value instanceof Boolean) {
                        return (Boolean) value;
                    }
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /** 2. CaptchaDialogUtil.a(Context, Map, View)：替换为后台验证。 */
    private static void hookDialogUtil(Class<?> clazz) {
        int count = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (!"a".equals(method.getName()) || parameters.length != 3
                    || !Map.class.isAssignableFrom(parameters[1])) {
                continue;
            }
            XpCompatEngine.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!FeatureFlagStore.isSlideCaptchaEnabled()) {
                        return;
                    }
                    Map<?, ?> map = param.args[1] instanceof Map ? (Map<?, ?>) param.args[1] : null;
                    if (Modifier.isAbstract(clazz.getModifiers())) {
                        // 12.12.50.8000 的 CaptchaDialogUtil 是抽象类，无构造器，
                        // 无法通过实例化走后台验证，改由 CaptchaDialog.show 接管。
                        Log.i(TAG, "CaptchaDialogUtil 为抽象类，跳过构造式后台验证"
                                + (map == null ? "" : "，keys: " + map.keySet()));
                        return;
                    }
                    Context context = param.args[0] instanceof Context
                            ? (Context) param.args[0] : ApplicationHook.getContext();
                    if (context == null || map == null) {
                        return;
                    }
                    boolean ok = runBackgroundVerification(context, map, param.args[2], null);
                    if (!ok) {
                        // 后台验证不可用时放行原流程：弹窗里的自动滑动会继续处理。
                        // 不能在这里提示“验证失败”，否则用户会先看到失败再被自动验证成功。
                        Log.i(TAG, "后台验证不可用，保留原验证流程，交由弹窗内自动滑动");
                        return;
                    }
                    // 替换语义：后台流程已启动，不再执行原弹窗流程。
                    param.setResult(null);
                    Log.i(TAG, "自动验证已触发，等待验证结果");
                    Toast.show("自动验证已触发", true);
                }
            });
            count++;
        }
        Log.i(TAG, "CaptchaDialogUtil.a Hook 数量: " + count + "，类: " + clazz.getName());
    }

    /** 3/5. CaptchaManager：H5 分支与 waitPageResult 恢复。 */
    private static void hookCaptchaManager(Class<?> clazz) {
        int h5 = 0;
        int wait = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if ("isNeedUseH5Captcha".equals(method.getName()) && parameters.length == 1
                    && Map.class.isAssignableFrom(parameters[0])) {
                XpCompatEngine.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (FeatureFlagStore.isSlideCaptchaEnabled()) {
                            Log.i(TAG, "isNeedUseH5Captcha 强制 false");
                            param.setResult(false);
                        }
                    }
                });
                h5++;
            } else if ("waitPageResult".equals(method.getName()) && parameters.length == 0) {
                XpCompatEngine.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!FeatureFlagStore.isSlideCaptchaEnabled()) {
                            return;
                        }
                        Object result = param.getResult();
                        if (param.getThrowable() != null || !Boolean.TRUE.equals(result)) {
                            Log.i(TAG, "waitPageResult 未成功，执行解锁恢复");
                            recoverCaptchaState();
                        }
                    }
                });
                wait++;
            }
        }
        Log.i(TAG, "CaptchaManager Hook: H5=" + h5 + ", waitPageResult=" + wait
                + "，类: " + clazz.getName());
    }

    /** 4. CaptchaVerifyModule.onCaptchaVerify(Map, X)：移除 type。 */
    private static void hookVerifyModule(Class<?> clazz) {
        int count = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (!"onCaptchaVerify".equals(method.getName()) || parameters.length != 2
                    || !Map.class.isAssignableFrom(parameters[0])) {
                continue;
            }
            XpCompatEngine.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!FeatureFlagStore.isSlideCaptchaEnabled()) {
                        return;
                    }
                    if (param.args[0] instanceof Map) {
                        try {
                            Map<?, ?> map = (Map<?, ?>) param.args[0];
                            Object type = map.remove("type");
                            Log.i(TAG, "onCaptchaVerify 命中，移除 type=" + type
                                    + "，keys=" + map.keySet());
                        } catch (Throwable t) {
                            Log.printStackTrace(TAG + "#onCaptchaVerify 移除 type 失败", t);
                        }
                    }
                }
            });
            count++;
        }
        Log.i(TAG, "onCaptchaVerify Hook 数量: " + count + "，类: " + clazz.getName());
    }

    /** 6. RpcRdsUtilImpl.rdsCaptchaHandle(7 参数)：失败码恢复。 */
    private static void hookRpcRdsUtil(Class<?> clazz) {
        int count = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            if (!"rdsCaptchaHandle".equals(method.getName())
                    || method.getParameterTypes().length != 7) {
                continue;
            }
            XpCompatEngine.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!FeatureFlagStore.isSlideCaptchaEnabled()) {
                        return;
                    }
                    Object result = param.getResult();
                    int code = result instanceof Number ? ((Number) result).intValue() : -1;
                    if (param.getThrowable() != null || code == 5 || code == 6) {
                        Log.i(TAG, "rdsCaptchaHandle 返回 " + code + "，执行解锁恢复");
                        recoverCaptchaState();
                    }
                }
            });
            count++;
        }
        Log.i(TAG, "rdsCaptchaHandle Hook 数量: " + count + "，类: " + clazz.getName());
    }

    /** 7. RdsHandler.openRdsCaptchaPage(Map, X, boolean)：重复验证转后台。 */
    private static void hookOpenRdsCaptchaPage(Class<?> clazz) {
        int count = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (!"openRdsCaptchaPage".equals(method.getName()) || parameters.length != 3
                    || !Map.class.isAssignableFrom(parameters[0])) {
                continue;
            }
            XpCompatEngine.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!FeatureFlagStore.isSlideCaptchaEnabled()
                            || !(param.args[0] instanceof Map)) {
                        return;
                    }
                    Map<?, ?> map = (Map<?, ?>) param.args[0];
                    // 仅 actionType 命中重复验证且 tips 为“请进行验证”时才考虑接管。
                    if (!isBackgroundCaptcha(map)) {
                        // 普通验证页保留原流程，不能提前回报失败，否则会先失败再被弹窗自动验证成功。
                        Log.i(TAG, "非重复验证页，保留原流程");
                        return;
                    }
                    // 前台存在 Activity 时保留原验证页，由 CaptchaDialog#show 内的
                    // 自动滑动处理；只有后台场景才掐掉页面转后台验证，避免前台页面被吞后毫无反馈。
                    if (hasForegroundActivity()) {
                        Log.i(TAG, "前台存在 Activity，保留原验证页面，交由弹窗内自动滑动");
                        return;
                    }
                    Context context = ApplicationHook.getContext();
                    if (!hasUsableCreation(sDialogUtilClass)) {
                        // 保留原验证页面时同样不能提前回报失败。
                        Log.i(TAG, "CaptchaDialogUtil 无可构造方式，保留原验证页面");
                        return;
                    }
                    param.setResult(null);
                    MAIN_HANDLER.post(() -> {
                        boolean ok = runBackgroundVerification(
                                context, map, param.args[1], new ResultCallback() {
                                    private final AtomicBoolean finished = new AtomicBoolean(false);

                                    @Override
                                    public void onResult(boolean success) {
                                        if (finished.compareAndSet(false, true)) {
                                            notifyPageResult(success);
                                            showResultToast(success);
                                        }
                                    }
                                });
                        if (!ok) {
                            notifyPageResult(false);
                            showResultToast(false);
                        }
                    });
                    Log.i(TAG, "openRdsCaptchaPage 命中后台验证条件，转后台处理");
                }
            });
            count++;
        }
        Log.i(TAG, "openRdsCaptchaPage Hook 数量: " + count + "，类: " + clazz.getName());
    }

    /** 结果回调：只回报一次页面结果。 */
    private interface ResultCallback {
        void onResult(boolean success);
    }

    /**
     * 动态构造 CaptchaDialogUtil。
     * 不同支付宝版本构造器不一致，按 (Context...) → 无参 → 静态工厂 的顺序尝试，
     * 全部失败时打印可用构造器，便于按真实签名继续适配。
     */
    private static Object createDialogUtil(Class<?> utilClass, Context context) throws Throwable {
        Throwable last = null;
        Object[] contextCandidates = {context, sResumedActivity.get()};
        for (Constructor<?> constructor : utilClass.getDeclaredConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            if (types.length == 0) {
                continue;
            }
            Object firstArg = matchContextArg(types[0], contextCandidates);
            if (firstArg == null) {
                continue;
            }
            Object[] args = new Object[types.length];
            args[0] = firstArg;
            for (int i = 1; i < types.length; i++) {
                args[i] = defaultValue(types[i]);
            }
            try {
                constructor.setAccessible(true);
                return constructor.newInstance(args);
            } catch (Throwable t) {
                last = t;
            }
        }
        for (Constructor<?> constructor : utilClass.getDeclaredConstructors()) {
            if (constructor.getParameterTypes().length != 0) {
                continue;
            }
            try {
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (Throwable t) {
                last = t;
            }
        }
        for (Method method : utilClass.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())
                    || !utilClass.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            try {
                method.setAccessible(true);
                if (types.length == 0) {
                    return method.invoke(null);
                }
                if (types.length == 1) {
                    Object firstArg = matchContextArg(types[0], contextCandidates);
                    if (firstArg != null) {
                        return method.invoke(null, firstArg);
                    }
                }
            } catch (Throwable t) {
                last = t;
            }
        }
        logConstructors(utilClass);
        if (last != null) {
            throw last;
        }
        throw new NoSuchMethodException(utilClass.getName() + " 无可用的构造器或静态工厂");
    }

    /** 预判是否存在可满足的构造方式，避免先阻断页面再失败。 */
    private static boolean hasUsableCreation(Class<?> utilClass) {
        if (utilClass == null) {
            return false;
        }
        for (Constructor<?> constructor : utilClass.getDeclaredConstructors()) {
            if (constructor.getParameterTypes().length == 0) {
                return true;
            }
        }
        Object[] contextCandidates = {ApplicationHook.getContext(), sResumedActivity.get()};
        for (Constructor<?> constructor : utilClass.getDeclaredConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            if (types.length >= 1 && matchContextArg(types[0], contextCandidates) != null) {
                return true;
            }
        }
        for (Method method : utilClass.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())
                    || !utilClass.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 0) {
                return true;
            }
            if (types.length == 1 && matchContextArg(types[0], contextCandidates) != null) {
                return true;
            }
        }
        return false;
    }

    private static Object matchContextArg(Class<?> type, Object[] candidates) {
        for (Object candidate : candidates) {
            if (candidate != null && type.isInstance(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == Boolean.TYPE) {
            return false;
        }
        if (type == Character.TYPE) {
            return '\0';
        }
        if (type == Byte.TYPE) {
            return (byte) 0;
        }
        if (type == Short.TYPE) {
            return (short) 0;
        }
        if (type == Integer.TYPE) {
            return 0;
        }
        if (type == Long.TYPE) {
            return 0L;
        }
        if (type == Float.TYPE) {
            return 0f;
        }
        if (type == Double.TYPE) {
            return 0d;
        }
        return null;
    }

    private static void logConstructors(Class<?> clazz) {
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            StringBuilder builder = new StringBuilder(clazz.getName()).append("(");
            Class<?>[] types = constructor.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(types[i].getName());
            }
            builder.append(")");
            Log.i(TAG, "可用构造器: " + builder);
        }
    }

    /**
     * dry-run 测试入口：
     * 只构造 CaptchaDialogUtil、读取视图字段、检查方法是否存在，
     * 不调用视图 k()，不写视图标签，也不调用 notifyPageResult。
     */
    public static void dryRun() {
        if (!FeatureFlagStore.isSlideCaptchaEnabled()) {
            Log.i(TAG, "dry-run 跳过：滑动验证开关未开启");
            return;
        }
        Class<?> utilClass = sDialogUtilClass != null
                ? sDialogUtilClass : resolveFirst(DIALOG_UTIL_CLASSES);
        if (utilClass == null) {
            Log.i(TAG, "dry-run 跳过：未找到 CaptchaDialogUtil");
            return;
        }
        Context context = ApplicationHook.getContext();
        if (context == null) {
            Log.i(TAG, "dry-run 跳过：context 为空");
            return;
        }
        Log.i(TAG, "dry-run 开始: " + utilClass.getName());
        Log.i(TAG, "dry-run 类型: interface=" + utilClass.isInterface()
                + ", abstract=" + Modifier.isAbstract(utilClass.getModifiers()));
        dumpStructure(utilClass);
        Class<?> dialogClass = resolveFirst(CAPTCHA_DIALOG_CLASSES);
        if (dialogClass != null) {
            dumpStructure(dialogClass);
        }
        if (utilClass.isInterface() || Modifier.isAbstract(utilClass.getModifiers())) {
            Log.i(TAG, "dry-run 结束: CaptchaDialogUtil 为抽象类/接口，改用 CaptchaDialog.show 接管");
            return;
        }
        logConstructors(utilClass);
        try {
            Object util = createDialogUtil(utilClass, context);
            View view = readViewField(util);
            StringBuilder builder = new StringBuilder("dry-run 成功: util=")
                    .append(util.getClass().getSimpleName())
                    .append(", 视图e=")
                    .append(view == null ? "null" : view.getClass().getSimpleName())
                    .append(", util方法[u=").append(hasMethod(utilClass, "u"))
                    .append(", t=").append(hasMethod(utilClass, "t"))
                    .append(", w=").append(hasMethod(utilClass, "w"))
                    .append(", o=").append(hasMethod(utilClass, "o"))
                    .append("]");
            if (view != null) {
                Class<?> viewClass = view.getClass();
                builder.append(", view方法[k=").append(hasMethod(viewClass, "k"))
                        .append(", w=").append(hasMethod(viewClass, "w"))
                        .append(", o=").append(hasMethod(viewClass, "o"))
                        .append("]");
            }
            Log.i(TAG, builder.toString());
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#dry-run 失败", t);
        }
    }

    private static boolean hasMethod(Class<?> clazz, String name) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (name.equals(method.getName())) {
                return true;
            }
        }
        return false;
    }

    /** dry-run 用：打印类字段与方法，便于确认真实结构。 */
    private static void dumpStructure(Class<?> clazz) {
        try {
            StringBuilder fields = new StringBuilder();
            int fieldCount = 0;
            Class<?> current = clazz;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    if (field.isSynthetic() || fieldCount >= 40) {
                        continue;
                    }
                    if (fieldCount > 0) {
                        fields.append(", ");
                    }
                    fields.append(field.getName()).append(':')
                            .append(field.getType().getSimpleName());
                    fieldCount++;
                }
                current = current.getSuperclass();
            }
            Log.i(TAG, clazz.getSimpleName() + " 字段: " + fields);

            StringBuilder methods = new StringBuilder();
            int methodCount = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isSynthetic() || methodCount >= 60) {
                    continue;
                }
                if (methodCount > 0) {
                    methods.append(", ");
                }
                methods.append(method.getName()).append('(');
                Class<?>[] types = method.getParameterTypes();
                for (int i = 0; i < types.length; i++) {
                    if (i > 0) {
                        methods.append(',');
                    }
                    methods.append(types[i].getSimpleName());
                }
                methods.append(")->").append(method.getReturnType().getSimpleName());
                methodCount++;
            }
            Log.i(TAG, clazz.getSimpleName() + " 方法: " + methods);
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#打印类结构失败", t);
        }
    }

    /**
     * 后台验证主流程：
     * 反射构造 CaptchaDialogUtil -> 设置 Map 与回调 -> 读取视图 -> 触发 k()。
     */
    private static boolean runBackgroundVerification(
            Context context, Map<?, ?> map, Object extra, ResultCallback callback) {
        if (context == null || map == null) {
            return false;
        }
        Class<?> utilClass = sDialogUtilClass != null ? sDialogUtilClass : resolveFirst(DIALOG_UTIL_CLASSES);
        if (utilClass == null) {
            Log.i(TAG, "CaptchaDialogUtil 不可用，保留原流程");
            return false;
        }
        try {
            Object util = createDialogUtil(utilClass, context);

            invokeWithMap(util, "u", map);
            if (extra != null) {
                invokeWithObject(util, "t", extra);
            }

            View view = readViewField(util);
            if (view == null) {
                Log.i(TAG, "CaptchaDialogUtil 视图字段 e 不存在，保留原流程");
                return false;
            }
            if (!handleTips(util, map, view)) {
                return false;
            }

            Log.i(TAG, "自动验证: 触发方法" + map.get("operationType"));
            if (!driveSlider(view)) {
                // 当前结构无法自动驱动：返回 false，调用方会保留原弹窗供手动验证。
                Log.i(TAG, "后台自动验证未触发，保留原验证流程");
                return false;
            }

            final Object utilRef = util;
            MAIN_HANDLER.postDelayed(() -> {
                if (callback == null) {
                    setObjectField(view, "l", Boolean.TRUE);
                    invokeIfExists(view, "__onDetachedFromWindow_stub_private");
                } else {
                    try {
                        callback.onResult(true);
                        invokeIfExists(utilRef, "w");
                        invokeIfExists(utilRef, "o");
                    } catch (Throwable t) {
                        callback.onResult(false);
                    }
                }
            }, 2500L);
            return true;
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#后台验证失败", t);
            return false;
        }
    }

    /**
     * 提示语判定：
     * 提示为“请进行验证”时只做学习，不关闭弹窗；
     * 视图结构无法自动驱动时保留弹窗并提示用户手动完成；
     * 仅命中异常关键词时自动关闭提示栏。
     */
    private static boolean handleTips(Object target, Map<?, ?> map, View view) {
        String tips = asString(map.get("tips"));
        if (isVerifyTips(tips)) {
            if (view != null && view.getVisibility() == View.VISIBLE) {
                learnVerifyTips(tips);
                return true;
            }
            // 视图结构与该版本自动验证逻辑不匹配：保留弹窗，交给用户手动完成验证。
            // 不能再 closeTipBar，否则弹窗会被直接 dismiss，自动和手动都无法验证。
            reportManualNeeded(target, tips);
            return false;
        }
        if (tips != null) {
            for (String keyword : TIP_KEYWORDS) {
                if (tips.contains(keyword)) {
                    Log.i(TAG, "自动关闭异常提示栏: " + tips
                            + " 触发方法" + map.get("operationType"));
                    closeTipBar(target);
                    return false;
                }
            }
        }
        return false;
    }

    /** 页面路径（XRiver/H5）自动验证失败：提示手动，并只记一条失败。 */
    private static void reportManualForPage() {
        long now = SystemClock.uptimeMillis();
        if (now - sLastPageManualAt < PAGE_MANUAL_REPORT_INTERVAL_MS) {
            return;
        }
        sLastPageManualAt = now;
        Log.record("拼图验证❌失败（页面拼图与本地滑动都未通过），请手动完成验证");
        Toast.show("自动验证未通过，请手动完成", true);
    }

    private static void reportManualNeeded(Object target, String tips) {
        if (sManualNeededReported.put(target, Boolean.TRUE) != null) {
            return;
        }
        Log.i(TAG, "自动验证不支持当前弹窗结构，保留弹窗等待手动验证: " + tips);
        Log.record("拼图验证⚠️ 无自动验证能力，需手动完成");
        Toast.show("自动验证不支持此验证类型，请手动完成", true);
    }

    private static void closeTipBar(Object target) {
        invokeIfExists(target, "__onDetachedFromWindow_stub_private");
        MAIN_HANDLER.postDelayed(() -> dismissQuietly(target), 10L);
    }

    private enum SliderMode {
        /** 新版 u/t/C + k()，可直接写状态提交。 */
        NEW,
        /** 旧版滑动驱动的 n/m/v 字段，需要原生 Runnable 驱动。 */
        LEGACY,
        UNKNOWN
    }

    private static SliderMode detectSliderMode(View view) {
        if (view == null) {
            return SliderMode.UNKNOWN;
        }
        Class<?> clazz = view.getClass();
        boolean newMode = matchesType(findField(clazz, "u"), Boolean.TYPE, Boolean.class)
                && matchesType(findField(clazz, "t"), Integer.TYPE, Integer.class)
                && matchesType(findField(clazz, "C"), Long.TYPE, Long.class)
                && findNoArgMethod(clazz, "k") != null;
        if (newMode) {
            return SliderMode.NEW;
        }
        boolean legacyMode = matchesType(findField(clazz, "n"), Boolean.TYPE, Boolean.class)
                && matchesType(findField(clazz, "m"), Integer.TYPE, Integer.class)
                && matchesType(findField(clazz, "v"), Long.TYPE, Long.class);
        return legacyMode ? SliderMode.LEGACY : SliderMode.UNKNOWN;
    }

    /**
     * 能力优先（而非版本优先）选择驱动方式：
     * A. u/t/C + k()  -> 写字段后直接调用 k()；
     * B. n/m/v        -> 按旧版滑动驱动的取值写字段，并把 r0 Runnable 交给 ThreadPoolFrame；
     * C. 都不匹配     -> 复用旧版 Captcha1/2Handler 的 MotionEvent 滑动。
     * 这样 10.6.59~10.8.x 之间字段结构变化的版本不会静默失效。
     */
    private static boolean driveSlider(View view) {
        SliderMode mode = detectSliderMode(view);
        switch (mode) {
            case NEW:
                prepareView(view);
                invokeIfExists(view, "k");
                Log.i(TAG, "模式A: 已写入 u=true, t=2050, C=随机时长并触发 k()");
                return true;
            case LEGACY:
                return driveLegacy(view);
            default:
                return fallbackSwipe(view, "未匹配 u/t/C 或 n/m/v");
        }
    }

    /**
     * 模式 B：旧版滑动驱动的字段写入：
     * n=true, m=2050, v=random(1800,3000)，
     * 然后 new r0(view) 并提交到 ThreadPoolFrame.a().g(Runnable)；
     * ThreadPoolFrame 不可用时退回静态入口 a(view)。
     */
    private static boolean driveLegacy(View view) {
        Runnable runnable = createSlideRunnable(view);
        Object pool = runnable == null ? null : resolveThreadPoolFrame();
        if (runnable != null && pool != null) {
            Method submit = findRunnableSink(pool.getClass());
            if (submit != null) {
                long duration = prepareLegacyFields(view);
                try {
                    submit.setAccessible(true);
                    submit.invoke(pool, runnable);
                    Log.i(TAG, "模式B: 已写入 n=true, m=2050, v=" + duration
                            + "ms 并交由 ThreadPoolFrame 执行");
                    return true;
                } catch (Throwable t) {
                    Log.printStackTrace(TAG + "#ThreadPoolFrame 投递失败", t);
                }
            }
        }
        Method staticSlide = findLegacyStaticSlide(view);
        if (staticSlide != null) {
            long duration = prepareLegacyFields(view);
            try {
                staticSlide.setAccessible(true);
                staticSlide.invoke(null, view);
                Log.i(TAG, "模式B: ThreadPoolFrame 不可用，已调用静态滑动入口 a(view), v="
                        + duration + "ms");
                return true;
            } catch (Throwable t) {
                Log.printStackTrace(TAG + "#模式B 静态滑动入口调用失败", t);
            }
        }
        return fallbackSwipe(view, "ThreadPoolFrame/Runnable 不可用");
    }

    /** 按旧版滑动驱动的取值写入 n/m/v，返回本次随机拖动时长。 */
    private static long prepareLegacyFields(View view) {
        long duration = ThreadLocalRandom.current().nextLong(1800L, 3000L);
        setObjectField(view, "n", Boolean.TRUE);
        setObjectField(view, "m", 2050);
        setObjectField(view, "v", duration);
        return duration;
    }

    private static Runnable createSlideRunnable(View view) {
        for (String className : SLIDE_RUNNABLE_CLASSES) {
            Class<?> clazz = findClassQuietly(className);
            if (clazz == null || !Runnable.class.isAssignableFrom(clazz)) {
                continue;
            }
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length != 1 || !types[0].isInstance(view)) {
                    continue;
                }
                try {
                    constructor.setAccessible(true);
                    return (Runnable) constructor.newInstance(view);
                } catch (Throwable t) {
                    Log.printStackTrace(TAG + "#构造模式B Runnable 失败: " + className, t);
                }
            }
        }
        return null;
    }

    private static Object resolveThreadPoolFrame() {
        for (String className : THREAD_POOL_FRAME_CLASSES) {
            Class<?> clazz = findClassQuietly(className);
            if (clazz == null) {
                continue;
            }
            Object pool = invokeStaticFactory(clazz);
            if (pool != null) {
                return pool;
            }
        }
        return null;
    }

    /** ThreadPoolFrame.a()：优先无参静态方法 a()，否则取任意返回自身类型的无参静态工厂。 */
    private static Object invokeStaticFactory(Class<?> clazz) {
        Object fallback = null;
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())
                        || method.getParameterTypes().length != 0
                        || !clazz.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    Object instance = method.invoke(null);
                    if (instance == null) {
                        continue;
                    }
                    if ("a".equals(method.getName())) {
                        return instance;
                    }
                    if (fallback == null) {
                        fallback = instance;
                    }
                } catch (Throwable ignored) {
                    // 继续尝试其它候选，全部失败后由调用方降级。
                }
            }
            current = current.getSuperclass();
        }
        return fallback;
    }

    /** 实例方法 g(Runnable)：优先同名方法，否则取返回 void 且只接收 Runnable/Object 的实例方法。 */
    private static Method findRunnableSink(Class<?> clazz) {
        Method fallback = null;
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        || method.getParameterTypes().length != 1) {
                    continue;
                }
                Class<?> param = method.getParameterTypes()[0];
                if (param != Runnable.class && param != Object.class) {
                    continue;
                }
                if ("g".equals(method.getName())) {
                    return method;
                }
                if (fallback == null && method.getReturnType() == Void.TYPE) {
                    fallback = method;
                }
            }
            current = current.getSuperclass();
        }
        return fallback;
    }

    /** 10.6.66 之前的静态入口：视图类上的 a(视图) 方法。 */
    private static Method findLegacyStaticSlide(View view) {
        Class<?> current = view.getClass();
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (!"a".equals(method.getName())
                        || !Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 1 && types[0].isInstance(view)) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static void prepareView(View view) {
        // 新版 CaptchaProcessBar: u=boolean 完成标记, t=int eventFlag, C=long 拖动时长。
        setObjectField(view, "u", Boolean.TRUE);
        setObjectField(view, "t", 2050);
        setObjectField(view, "C", ThreadLocalRandom.current().nextLong(1800L, 5000L));
    }

    private static boolean fallbackSwipe(View view, String reason) {
        boolean swiped = swipeView(view);
        Log.i(TAG, "降级 MotionEvent 滑动: " + reason + " -> "
                + (swiped ? "已触发" : "失败"));
        return swiped;
    }

    /**
     * 兜底：复用旧版 Captcha1/2Handler 的 MotionEventSimulator；
     * 视图尚未 attached 时退回直接 dispatchTouchEvent 的同步实现。
     */
    private static boolean swipeView(View view) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return false;
        }
        float startX = view.getWidth() * 0.05f;
        float endX = view.getWidth() * 0.95f;
        float y = view.getHeight() / 2f;
        long duration = ThreadLocalRandom.current().nextLong(300L, 600L);
        if (view.isShown() && view.isEnabled()) {
            try {
                MotionEventSimulator.simulateSwipe(view, startX, y, endX, y, duration);
                return true;
            } catch (Throwable t) {
                Log.printStackTrace(TAG + "#MotionEventSimulator 滑动失败", t);
            }
        }
        return dispatchSwipeDirect(view, startX, y, endX, y, duration);
    }

    private static boolean dispatchSwipeDirect(View view, float startX, float startY,
                                               float endX, float endY, long duration) {
        try {
            long downTime = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(downTime, downTime,
                    MotionEvent.ACTION_DOWN, startX, startY, 0);
            view.dispatchTouchEvent(down);
            down.recycle();
            int steps = 12;
            for (int i = 1; i <= steps; i++) {
                long eventTime = downTime + duration * i / steps;
                float x = startX + (endX - startX) * i / steps;
                float y = startY + (endY - startY) * i / steps;
                MotionEvent move = MotionEvent.obtain(downTime, eventTime,
                        MotionEvent.ACTION_MOVE, x, y, 0);
                view.dispatchTouchEvent(move);
                move.recycle();
            }
            MotionEvent up = MotionEvent.obtain(downTime, downTime + duration,
                    MotionEvent.ACTION_UP, endX, endY, 0);
            view.dispatchTouchEvent(up);
            up.recycle();
            return true;
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#手势滑动失败", t);
            return false;
        }
    }

    /**
     * 安排一次自动提交：先做去重与次数限制，再等待验证视图就绪后提交。
     *
     * @return true 表示本次调用已接管（等待中或已提交），false 表示保留原提示处理
     */
    private static boolean scheduleDirectSubmit(Object dialog, View view) {
        if (dialog == null || view == null) {
            return false;
        }
        Integer attempts = sDirectSubmitAttempts.get(dialog);
        int count = attempts == null ? 0 : attempts;
        if (count >= MAX_DIRECT_SUBMIT_ATTEMPTS) {
            Log.i(TAG, "自动提交已达上限 " + MAX_DIRECT_SUBMIT_ATTEMPTS + " 次，保留手动验证");
            return false;
        }
        long now = SystemClock.uptimeMillis();
        Long last = sLastDirectSubmitAt.get(dialog);
        if (last != null && now - last < DIRECT_SUBMIT_DEBOUNCE_MS) {
            Log.i(TAG, "距上次自动提交 " + (now - last) + "ms，跳过重复提交");
            return true;
        }
        if (sDirectSubmitScheduled.put(dialog, Boolean.TRUE) != null) {
            return true;
        }
        waitForSliderReady(dialog, view, 0);
        return true;
    }

    private static void waitForSliderReady(Object dialog, View view, int poll) {
        // 中间版 CaptchaDialog.show() 返回时窗口还没真正显示（内部异步 show），
        // 此时立即放弃会让模式A永远不提交，所以未显示也继续轮询到超时。
        boolean showing = !(dialog instanceof Dialog) || ((Dialog) dialog).isShowing();
        if (showing && isSliderReady(view)) {
            sDirectSubmitScheduled.remove(dialog);
            if (markSubmitted(dialog)) {
                Log.i(TAG, "验证视图已就绪（等待 " + (poll * SLIDER_READY_POLL_INTERVAL_MS) + "ms），开始提交");
                tryDirectSubmit(dialog, view);
            }
            return;
        }
        if (poll >= SLIDER_READY_MAX_POLLS) {
            sDirectSubmitScheduled.remove(dialog);
            if (!showing) {
                Log.i(TAG, "验证弹窗始终未显示，放弃自动提交（等待 "
                        + (poll * SLIDER_READY_POLL_INTERVAL_MS) + "ms）");
                return;
            }
            // 超时后仅在视图已有宽高时按现状提交，避免彻底放弃自动验证。
            boolean usable = view.getWidth() > 0 && view.getHeight() > 0;
            Log.i(TAG, "等待验证视图就绪超时" + (usable ? "，按当前状态提交" : "，放弃自动提交"));
            if (usable && markSubmitted(dialog)) {
                tryDirectSubmit(dialog, view);
            }
            return;
        }
        MAIN_HANDLER.postDelayed(() -> waitForSliderReady(dialog, view, poll + 1),
                SLIDER_READY_POLL_INTERVAL_MS);
    }

    /** 记一次提交尝试并刷新时间戳；已达上限时返回 false。 */
    private static boolean markSubmitted(Object dialog) {
        Integer attempts = sDirectSubmitAttempts.get(dialog);
        int count = attempts == null ? 0 : attempts;
        if (count >= MAX_DIRECT_SUBMIT_ATTEMPTS) {
            return false;
        }
        sDirectSubmitAttempts.put(dialog, count + 1);
        sLastDirectSubmitAt.put(dialog, SystemClock.uptimeMillis());
        return true;
    }

    /**
     * 视图就绪判定：必须已附着、可见且有尺寸；
     * 若存在回调/验证数据字段但为空，说明验证数据尚未注入，继续等待。
     */
    private static boolean isSliderReady(View view) {
        if (view == null || !view.isAttachedToWindow() || !view.isShown()
                || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return false;
        }
        return fieldLoaded(view, "w", "x", "B");
    }

    private static boolean fieldLoaded(View view, String... names) {
        for (String name : names) {
            if (findField(view.getClass(), name) == null) {
                continue;
            }
            Object value = readObjectField(view, name);
            if (value == null
                    || (value instanceof CharSequence && ((CharSequence) value).length() == 0)) {
                return false;
            }
        }
        return true;
    }

    /** 同一弹窗的成功回执只提示一次，失败提示不受影响。 */
    private static void notifyValidateResult(Object dialog, boolean success) {
        if (success && sSuccessReported.put(dialog, Boolean.TRUE) != null) {
            Log.i(TAG, "该弹窗已提示过验证成功，忽略重复回执");
            return;
        }
        showResultToast(success);
    }

    /**
     * 按能力驱动滑块并提交（模式 A / B / 兜底），调用前需保证视图已就绪。
     */
    private static boolean tryDirectSubmit(Object dialog, View view) {
        Integer attempts = sDirectSubmitAttempts.get(dialog);
        int count = attempts == null ? 0 : attempts;

        if (view == null) {
            return false;
        }
        SliderMode mode = detectSliderMode(view);
        boolean submitted = driveSlider(view);
        Log.i(TAG, "自动提交: 模式=" + mode + (submitted ? "，已触发" : "，失败")
                + "，第 " + count + " 次"
                + "（u=" + fieldType(view.getClass(), "u")
                + ", t=" + fieldType(view.getClass(), "t")
                + ", C=" + fieldType(view.getClass(), "C")
                + ", n=" + fieldType(view.getClass(), "n")
                + ", m=" + fieldType(view.getClass(), "m")
                + ", v=" + fieldType(view.getClass(), "v") + "）");
        if (!submitted) {
            // 字段结构和兜底手势都不支持：不关闭弹窗，先交给拼图手势方案，做不了再提示手动完成。
            if (!armPuzzle(dialog, view, "自动提交失败")) {
                reportManualNeeded(dialog, "自动提交失败");
            }
        } else {
            // 服务端拒绝时弹窗会保留并刷新出新图：几秒后仍在说明模式A没通过，按顺序转拼图手势方案。
            MAIN_HANDLER.postDelayed(() -> {
                if (dialog instanceof Dialog && ((Dialog) dialog).isShowing()) {
                    Log.i(TAG, "模式A提交后验证窗口仍在，转拼图手势方案");
                    if (!armPuzzle(dialog, view, "模式A未通过")) {
                        reportManualNeeded(dialog, "模式A未通过");
                    }
                }
            }, 3000L);
        }
        return submitted;
    }

    private static boolean matchesType(Field field, Class<?> primitive, Class<?> boxed) {
        return field != null && (field.getType() == primitive || field.getType() == boxed);
    }

    private static String fieldType(Class<?> clazz, String name) {
        Field field = findField(clazz, name);
        return field == null ? "缺失" : field.getType().getSimpleName();
    }

    private static Method findNoArgMethod(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (name.equals(method.getName()) && method.getParameterTypes().length == 0) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static void setObjectField(Object target, String name, Object value) {
        if (target == null) {
            return;
        }
        Field field = findField(target.getClass(), name);
        if (field == null) {
            return;
        }
        try {
            field.setAccessible(true);
            Class<?> type = field.getType();
            if (value instanceof Number) {
                Number number = (Number) value;
                if (type == Integer.TYPE || type == Integer.class) {
                    value = number.intValue();
                } else if (type == Long.TYPE || type == Long.class) {
                    value = number.longValue();
                } else if (type == Float.TYPE || type == Float.class) {
                    value = number.floatValue();
                } else if (type == Double.TYPE || type == Double.class) {
                    value = number.doubleValue();
                }
            }
            field.set(target, value);
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#写入视图字段失败: " + name, t);
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /** 验证结果统一日志 + Toast 提示。 */
    private static void showResultToast(boolean success) {
        String message = success ? "自动验证完成" : "自动验证失败，请手动完成";
        Log.i(TAG, message);
        Log.record("拼图验证" + (success ? "✅成功" : "❌失败"));
        Toast.show(message, true);
    }

    /** 恢复动作：解除验证页与 RPC 拦截状态。 */
    private static void recoverCaptchaState() {
        Object manager = getCaptchaManagerInstance();
        if (manager == null) {
            return;
        }
        invokeIfExists(manager, "unLockIsCaptchaPageOn");
        invokeIfExists(manager, "unLockIsRpcInterceptOn");
    }

    /** 对应 ck4.m1498f：向 CaptchaManager 回报页面结果。 */
    private static void notifyPageResult(boolean success) {
        Object manager = getCaptchaManagerInstance();
        if (manager == null) {
            return;
        }
        try {
            XposedHelpers.callMethod(manager, "notifyPageResult", success);
            if (success) {
                invokeIfExists(manager, "unLockIsCaptchaPageOn");
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#notifyPageResult 调用失败", t);
        }
    }

    private static Object getCaptchaManagerInstance() {
        for (String className : CAPTCHA_MANAGER_CLASSES) {
            Class<?> clazz = findClassQuietly(className);
            if (clazz == null) {
                continue;
            }
            try {
                Object instance = XposedHelpers.callStaticMethod(clazz, "getInstance");
                if (instance != null) {
                    return instance;
                }
            } catch (Throwable ignored) {
                // 继续尝试下一个包名。
            }
        }
        return null;
    }

    private static boolean isBackgroundCaptcha(Map<?, ?> map) {
        String actionType = asString(map.get("actionType"));
        String tips = asString(map.get("tips"));
        boolean repeat = "captcha_repeat".equals(actionType) || "captcha_no_repeat".equals(actionType);
        if (repeat && tips != null) {
            learnVerifyTips(tips);
        }
        return repeat && isVerifyTips(tips);
    }

    /**
     * 动态还原 tips 字面量：
     * 验证文案随版本变化且可能被混淆，这里从宿主真实验证页学习文案并持久化。
     */
    private static boolean isVerifyTips(String tips) {
        if (tips == null || tips.isEmpty()) {
            return false;
        }
        String learned = getLearnedVerifyTips();
        if (learned != null && !learned.isEmpty()
                && (learned.equals(tips) || tips.contains(learned))) {
            return true;
        }
        // 学习值未命中时继续按“包含验证”判断，兼容文案变更并触发重新学习。
        return tips.contains(TIP_VERIFY) || tips.contains("验证");
    }

    private static void learnVerifyTips(String tips) {
        if (tips == null || tips.isEmpty() || !tips.contains("验证")) {
            return;
        }
        if (tips.equals(getLearnedVerifyTips())) {
            return;
        }
        sVerifyTips = tips;
        Context context = ApplicationHook.getContext();
        if (context != null) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_TIPS, tips)
                    .apply();
        }
        Log.i(TAG, "动态学习验证提示文案: " + tips);
    }

    private static String getLearnedVerifyTips() {
        if (!sTipsLoaded) {
            synchronized (PuzzleCaptchaHook.class) {
                if (!sTipsLoaded) {
                    Context context = ApplicationHook.getContext();
                    if (context != null) {
                        sVerifyTips = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .getString(KEY_TIPS, null);
                    }
                    sTipsLoaded = true;
                }
            }
        }
        return sVerifyTips;
    }

    /** 前台判定：判断当前是否存在前台 Activity。 */
    private static boolean hasForegroundActivity() {
        ensureActivityTracking();
        Activity activity = sResumedActivity.get();
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private static void ensureActivityTracking() {
        if (sLifecycleRegistered) {
            return;
        }
        Context context = ApplicationHook.getContext();
        if (!(context instanceof Application)) {
            return;
        }
        ((Application) context).registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                    }

                    @Override
                    public void onActivityStarted(Activity activity) {
                    }

                    @Override
                    public void onActivityResumed(Activity activity) {
                        sResumedActivity = new WeakReference<>(activity);
                    }

                    @Override
                    public void onActivityPaused(Activity activity) {
                        clearIfCurrent(activity);
                    }

                    @Override
                    public void onActivityStopped(Activity activity) {
                    }

                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                    }

                    @Override
                    public void onActivityDestroyed(Activity activity) {
                        clearIfCurrent(activity);
                    }
                });
        sLifecycleRegistered = true;
    }

    private static void clearIfCurrent(Activity activity) {
        if (sResumedActivity.get() == activity) {
            sResumedActivity.clear();
        }
    }

    private static void dismissQuietly(Object target) {
        if (target == null) {
            return;
        }
        try {
            if (target instanceof Dialog) {
                ((Dialog) target).dismiss();
            } else {
                XposedHelpers.callMethod(target, "dismiss");
            }
        } catch (Throwable ignored) {
            // 没有 dismiss 时忽略。
        }
    }

    private static void invokeIfExists(Object target, String methodName) {
        if (target == null) {
            return;
        }
        try {
            XposedHelpers.callMethod(target, methodName);
        } catch (Throwable ignored) {
            // 方法不存在或签名不符时保留原状态。
        }
    }

    private static void invokeWithMap(Object target, String methodName, Map<?, ?> map) {
        try {
            XposedHelpers.callMethod(target, methodName, map);
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#调用 " + methodName + "(Map) 失败", t);
        }
    }

    private static void invokeWithObject(Object target, String methodName, Object argument) {
        try {
            XposedHelpers.callMethod(target, methodName, argument);
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#调用 " + methodName + "(Object) 失败", t);
        }
    }

    private static Map<?, ?> readMapField(Object target) {
        if (target == null) {
            return null;
        }
        Object value = readField(target, "k");
        if (value instanceof Map) {
            return (Map<?, ?>) value;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        Object fieldValue = field.get(target);
                        if (fieldValue instanceof Map) {
                            return (Map<?, ?>) fieldValue;
                        }
                    } catch (Throwable ignored) {
                        // 继续查找。
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static View readViewField(Object target) {
        if (target == null) {
            return null;
        }
        Object value = readField(target, "e");
        if (value instanceof View) {
            return (View) value;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (View.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        Object fieldValue = field.get(target);
                        if (fieldValue instanceof View) {
                            return (View) fieldValue;
                        }
                    } catch (Throwable ignored) {
                        // 继续查找。
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object readField(Object target, String name) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static Class<?> resolveFirst(String[] classNames) {
        for (String className : classNames) {
            Class<?> clazz = findClassQuietly(className);
            if (clazz != null) {
                return clazz;
            }
        }
        return null;
    }

    /** 按包名前缀从 CaptchaDialog 推导 RdsHandler。 */
    private static Class<?> resolveRdsHandler() {
        return resolveFirst(candidates(RDS_HANDLER_CLASSES, "RdsHandler"));
    }

    /**
     * 以已定位的 CaptchaDialog 为锚点推导同族类名：
     * 用包前缀 + 类是否存在探测，结果放在候选数组之前，
     * 找不到时仍回退到硬编码候选。
     */
    private static String[] candidates(String[] base, String... relativeNames) {
        String root = captchaRootPackage();
        if (root == null) {
            return base;
        }
        List<String> result = new ArrayList<>();
        for (String relative : relativeNames) {
            String className = root + "." + relative;
            if (!result.contains(className) && findClassQuietly(className) != null) {
                result.add(className);
            }
        }
        for (String className : base) {
            if (!result.contains(className)) {
                result.add(className);
            }
        }
        return result.toArray(new String[0]);
    }

    private static String captchaRootPackage() {
        String cached = sCaptchaRootPackage;
        if (cached != null) {
            return cached;
        }
        Class<?> dialogClass = resolveFirst(CAPTCHA_DIALOG_CLASSES);
        if (dialogClass == null) {
            return null;
        }
        String name = dialogClass.getName();
        int index = name.lastIndexOf('.');
        if (index <= 0) {
            return null;
        }
        String pkg = name.substring(0, index);
        if (pkg.endsWith(".view")) {
            pkg = pkg.substring(0, pkg.length() - ".view".length());
        }
        sCaptchaRootPackage = pkg;
        Log.i(TAG, "验证类包前缀: " + pkg);
        return pkg;
    }

    private static Class<?> findClassQuietly(String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }
        ClassLoader classLoader = sClassLoader != null ? sClassLoader : ApplicationHook.getClassLoader();
        if (classLoader == null) {
            return null;
        }
        try {
            return XposedHelpers.findClass(className, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
