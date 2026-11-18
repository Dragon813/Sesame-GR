package io.github.Dragon813.sesame.hook;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.Dragon813.sesame.util.FeatureFlagStore;
import io.github.Dragon813.sesame.util.Log;
import io.github.Dragon813.sesame.util.RandomUtil;

/**
 * 自动处理「对准图片」的拼图滑块验证码（H5 WebView 渲染）。
 * <p>流程：验证被要求时 {@link #arm} → 主线程每秒扫描一次窗口（最多 60 次）找 WebView → 对 WebView 截图（PixelCopy）
 * → 工作线程识别滑块按钮/轨道终点并做图像匹配得到缺口位移 → 主线程用触摸事件把滑块拖过去。
 * <p>约束（来自 GR 的经验，也是为了不加重风控）：每个验证码窗口最多自动拖动 {@link #DEFAULT_ATTEMPTS} 次
 * （拖错后等页面刷新出新图再重试）；识别置信度不够就不动手；
 * 只在验证被要求后的窗口期内扫描，不会在任意 H5 页面上乱点。
 * <p>拼图方案失败（没有 H5 拼图窗口 / 自动尝试次数用完）时回调调用方传入的兜底逻辑，
 * 由调用方退回项目原有的滑动到底方案。
 * <p>坐标常量按 GR 记录的设备布局（参考宽度 1264）缩放，其它布局可能识别不到滑块——这时只会记日志并保存截图，
 * 不会拖动；截图收在 sesame/puzzle.zip（最多 20 张），用来校准。
 */
public final class PuzzleCaptchaSolver {
    private static final String TAG = "PuzzleCaptchaSolver";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final long POLL_MS = 1000L;
    private static final int MAX_POLLS = 60;
    /**
     * 被动扫描的窗口期（秒）。XRiver/Nebula 页面打开后，用户往往过几秒甚至一两分钟才点“森林动态”之类的入口，
     * 验证码才弹出来；窗口太短会刚好错过（实测 8 秒、30 秒都出现过窗口到期前一秒才点到的情况）。
     * 页面重新回到前台时窗口会重新计时，所以这个值只决定“停在页面上不动时最多等多久”。
     */
    private static final int PASSIVE_POLLS = 300;
    /**
     * 同一个窗口最多截图分析多少次。被动扫描要覆盖“页面打开后用户过几秒才点入口”的情况，
     * 窗口期五分钟、每秒一帧，这里必须够用，否则后弹出的验证码会被这个上限挡住。
     */
    private static final int MAX_CAPTURES_PER_WINDOW = 200;
    /** 被动扫描时连续多少次找不到 WebView 就安静退出（用户已经离开验证页，不用继续耗电）。 */
    private static final int PASSIVE_NO_WEBVIEW_POLLS = 5;
    private static final int DEFAULT_ATTEMPTS = 4;
    private static final int RETRY_POLLS = 30;
    private static final long MATCH_BUDGET_MS = 3500L;
    private static final long SLIDE_MIN_MS = 850L;
    private static final long SLIDE_MAX_MS = 950L;
    /** 连续扫描多少次都没有找到 WebView 就判定"不是 H5 拼图"，立即退回本地滑动方案。 */
    private static final int NO_WEBVIEW_POLLS = 2;

    private static final float REFERENCE_WIDTH = 1264f;
    private static final float START_X = 236f;
    private static final float START_Y = 1787f;
    // 同一验证码在不同设备有不同的水平留白：固定坐标只作宽松候选提示，真正的按钮靠颜色连通块识别
    private static final float START_TOLERANCE = 130f;
    private static final float VERTICAL_TOLERANCE = 700f;

    /**
     * 每个验证码 WebView 已经自动拖动的次数；失败后允许重试，但总数有上限（连续失败太多会加重风控）。
     * 按 WebView 而不是窗口计数：每次重新弹出验证码都是新的 WebView，从 0 开始；验证成功、
     * 重启支付宝（进程重建）也都会归零。仅主线程访问。
     */
    private static final Map<View, Integer> ATTEMPTS = new WeakHashMap<>();
    private static final Map<View, Integer> CAPTURES = new WeakHashMap<>();
    /** 同一窗口的 no-track 截图存档节流，避免被动扫描 30 秒里把普通页面反复写进 puzzle.zip。 */
    private static final Map<View, Long> NO_TRACK_SAVED = new WeakHashMap<>();
    private static final Map<View, String> LAST_DIAG = new WeakHashMap<>();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SesamePuzzleSolver");
        thread.setDaemon(true);
        return thread;
    });

    // 以下仅主线程访问
    private static boolean polling;
    private static boolean busy;
    private static int polls;
    private static int maxPolls = MAX_POLLS;
    /** 连续多少次扫描没有发现 WebView，用于尽快退回本地滑动方案。 */
    private static int noWebPolls;
    /** 扫描循环的令牌：每次（重新）开始换一个新值，旧循环下一次触发时发现令牌不符就自己退出，避免同时跑两条循环。 */
    private static int pollToken;
    private static WeakReference<Dialog> hintDialog = new WeakReference<>(null);
    /** 被动扫描（页面恢复触发）时不刷验证记录，直到真的识别到拼图滑块。工作线程也会读，所以 volatile。 */
    private static volatile boolean quiet;
    private static boolean disabledLogged;
    /** 拼图方案失败时执行的兜底逻辑（项目原有的滑动到底方案），只会执行一次。 */
    private static Runnable fallback;
    private static boolean fallbackFired;
    /** 拼图方案是否正在接管验证（供页面处理器判断是否要先让路）。 */
    private static volatile boolean handling;
    /** 本轮是否真的识别到并拖动过拼图：页面兜底的“请手动完成”只在这种情况下提示。 */
    private static volatile boolean sawPuzzle;

    private PuzzleCaptchaSolver() {
    }

    /** 本轮是否真的识别到拼图并拖动过（用于决定要不要提示用户手动完成）。 */
    public static boolean sawPuzzle() {
        return sawPuzzle;
    }

    /**
     * 验证被要求时调用（任意线程）：接口返回“请验证”、或验证码弹窗出现。重复调用只会把 60 秒窗口期重新计时。
     */
    public static void arm(String source) {
        arm(source, null, null);
    }

    /** dialog 是验证码弹窗对象时传进来，扫描时直接用它，不依赖窗口跟踪列表。 */
    public static void arm(String source, Dialog dialog) {
        arm(source, dialog, null);
    }

    /**
     * @param onFallback 拼图方案失败（没有 H5 拼图窗口，或自动尝试次数用完）时执行；传 null 表示不兜底
     * @return true 表示已开始监视（拼图方案接管），false 表示开关关闭
     */
    public static boolean arm(String source, Dialog dialog, Runnable onFallback) {
        return arm(source, dialog, onFallback, MAX_POLLS);
    }

    /**
     * @param maxPolls 监视窗口期（秒）；弹窗场景可传小一点，尽早退回原有逻辑
     * @param onFallback 拼图方案失败（没有 H5 拼图窗口，或自动尝试次数用完）时执行；传 null 表示不兜底
     * @return true 表示已开始监视（拼图方案接管），false 表示开关关闭
     */
    public static boolean arm(String source, Dialog dialog, Runnable onFallback, int maxPolls) {
        if (!isEnabled()) {
            if (!disabledLogged) {
                disabledLogged = true;
                Log.i(TAG, "自动图片滑块开关已关闭，不处理（来源[" + source + "]）");
            }
            return false;
        }
        handling = true;
        final int window = Math.max(3, maxPolls);
        MAIN.post(() -> {
            if (dialog != null) {
                hintDialog = new WeakReference<>(dialog);
            }
            quiet = false;
            PuzzleCaptchaSolver.maxPolls = window;
            polls = 0;
            noWebPolls = 0;
            fallback = onFallback;
            fallbackFired = false;
            sawPuzzle = false;
            if (polling) {
                return;
            }
            cleanupNoSlider(); // 上次残留的没拖动过的截图
            Log.i(TAG, "开始监视验证窗口，最长 " + window + " 秒（来源[" + source + "]）");
            startPolling();
        });
        return true;
    }

    /** 拼图方案是否正在接管验证。 */
    public static boolean isHandling() {
        return handling;
    }

    /** 指定视图中是否包含（支付宝的）WebView，用于判断验证是否由 H5 渲染。 */
    public static boolean containsWebView(View root) {
        return findWebView(root) != null;
    }

    /**
     * 被动触发（XRiver/首页页面恢复，GR 也在这两个页面挂验证码处理器）：静默扫描，平时不写验证记录，
     * 识别到拼图滑块才转为正常流程。已经在静默扫描时只把窗口期重新计时（用户可能反复进出页面），
     * 真正接管验证期间不打断也不延长。
     */
    public static void armPassive(String source) {
        armPassive(source, null);
    }

    /** @param onFallback 被动扫描真的识别到拼图、但自动尝试次数用完时执行的兜底逻辑 */
    public static void armPassive(String source, Runnable onFallback) {
        MAIN.post(() -> {
            if (!isEnabled()) {
                return;
            }
            if (polling) {
                // 用户常常先打开页面逛一会儿、再点入口，中途还可能返回首页再进来。
                // 已经在自己扫描时不要把窗口期卡在第一次打开页面的时间点，否则验证码弹出时窗口刚好到期
                // （实测 09:31 那次只剩 1 秒，只截到一张还没加载完的图，一次都没拖动）。
                if (quiet) {
                    polls = 0;
                    maxPolls = PASSIVE_POLLS;
                }
                return;
            }
            quiet = true;
            maxPolls = PASSIVE_POLLS;
            polls = 0;
            noWebPolls = 0;
            fallback = onFallback;
            fallbackFired = false;
            sawPuzzle = false;
            Log.i(TAG, "被动扫描开始(" + source + ")，最长 " + PASSIVE_POLLS + " 秒");
            startPolling();
        });
    }

    /** 开始一条新的扫描循环（主线程）：令牌加一，之前排着队的旧循环不再执行。 */
    private static void startPolling() {
        polling = true;
        pollToken++;
        schedulePoll(pollToken);
    }

    private static void schedulePoll(int token) {
        MAIN.postDelayed(() -> poll(token), POLL_MS);
    }

    private static boolean isEnabled() {
        return FeatureFlagStore.isSlideCaptchaEnabled();
    }

    private static void poll(int token) {
        // 拖动完成/窗口期结束/重新开始后，旧循环排着队的这一次不能再跑（否则会和新循环同时扫描、重复打日志）
        if (token != pollToken || !polling) {
            return;
        }
        try {
            polls++;
            boolean webFound = false;
            if (!busy) {
                webFound = scanOnce();
            }
            if (webFound) {
                noWebPolls = 0;
            } else if (!busy) {
                noWebPolls++;
            }
            // 被动扫描时页面已经没有 WebView（用户离开验证页）：安静退出，不必扫满整个窗口期
            if (quiet && noWebPolls >= PASSIVE_NO_WEBVIEW_POLLS) {
                polling = false;
                Log.i(TAG, "被动扫描结束(" + polls + " 秒)，页面已无 WebView");
                return;
            }
            // 连续几次都没有 WebView：当前弹窗/页面不是 H5 拼图，立刻退回项目原有的滑动逻辑，不用等满窗口期
            if (noWebPolls >= NO_WEBVIEW_POLLS && !quiet && fallback != null) {
                polling = false;
                fireFallback("未发现H5拼图窗口");
                return;
            }
            if (polls >= maxPolls) {
                polling = false;
                if (quiet) {
                    Log.i(TAG, "被动扫描结束(" + polls + " 秒)，未发现拼图");
                } else {
                    Log.i(TAG, "监视窗口期结束，没有可处理的拼图窗口");
                }
                if (!quiet) {
                    // 最后一次扫描刚发起截图时分析还在进行，稍后才会存图：等它做完再清理，否则会漏掉最后一张
                    if (busy) {
                        MAIN.postDelayed(PuzzleCaptchaSolver::cleanupNoSlider, 6000L);
                    } else {
                        cleanupNoSlider();
                    }
                }
                if (!quiet) {
                    fireFallback("监视窗口期结束未识别到拼图");
                }
                return;
            }
            schedulePoll(token);
        } catch (Throwable t) {
            polling = false;
            Log.printStackTrace(TAG, t);
        }
    }

    /** 拼图方案失败时执行一次兜底逻辑（项目原有的滑动到底方案）。 */
    private static void fireFallback(String reason) {
        handling = false;
        Runnable action = fallback;
        fallback = null;
        if (action == null || fallbackFired) {
            return;
        }
        fallbackFired = true;
        Log.i(TAG, "拼图方案未完成(" + reason + ")，执行兜底逻辑");
        try {
            action.run();
        } catch (Throwable t) {
            Log.printStackTrace(TAG + "#兜底滑动失败", t);
        }
    }

    private static final class Target {
        final View root;
        final Window window;

        Target(View root, Window window) {
            this.root = root;
            this.window = window;
        }
    }

    /** 当前可见窗口：栈顶 Activity + 被监控到的显示中对话框（对话框靠后，优先扫描）。 */
    private static List<Target> windows() {
        List<Target> targets = new ArrayList<>();
        Activity top = SimplePageManager.getTopActivity();
        if (top != null && !top.isFinishing() && top.getWindow() != null) {
            targets.add(new Target(top.getWindow().getDecorView(), top.getWindow()));
        }
        for (WeakReference<Dialog> ref : new ArrayList<>(SimplePageManager.getDialogs())) {
            Dialog dialog = ref.get();
            if (dialog != null && dialog.isShowing() && dialog.getWindow() != null) {
                targets.add(new Target(dialog.getWindow().getDecorView(), dialog.getWindow()));
            }
        }
        Dialog hinted = hintDialog.get();
        if (hinted != null && hinted.isShowing() && hinted.getWindow() != null) {
            View decor = hinted.getWindow().getDecorView();
            boolean present = false;
            for (Target target : targets) {
                if (target.root == decor) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                targets.add(new Target(decor, hinted.getWindow()));
            }
        }
        return targets;
    }

    /** @return 本次扫描是否找到了 WebView（找到但暂不截图也算，用于判断当前页面是不是 H5） */
    private static boolean scanOnce() {
        List<Target> targets = windows();
        for (int i = targets.size() - 1; i >= 0; i--) {
            Target target = targets.get(i);
            View root = target.root;
            if (root == null || !root.isShown()) {
                continue;
            }
            View web = findWebView(root);
            if (web == null) {
                continue;
            }
            if (!web.isShown() || web.getWidth() < 400 || web.getHeight() < 400
                    || attemptsOf(web) >= maxAttempts()) {
                return true;
            }
            int captures = CAPTURES.containsKey(root) ? CAPTURES.get(root) : 0;
            if (captures >= MAX_CAPTURES_PER_WINDOW) {
                return true;
            }
            CAPTURES.put(root, captures + 1);
            busy = true;
            capture(target, web, (bitmap, error) -> {
                if (bitmap == null) {
                    diag(root, "截图失败:" + error);
                    busy = false;
                    return;
                }
                submit(target, web, bitmap);
            });
            return true;
        }
        return false;
    }

    private static View findWebView(View view) {
        if (view == null) {
            return null;
        }
        View nested = null;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View candidate = findWebView(group.getChildAt(i));
                if (candidate != null) {
                    nested = candidate;
                }
            }
        }
        if (nested != null) {
            return nested;
        }
        return isWebView(view) ? view : null;
    }

    /** 支付宝的验证码在 com.alipay.mywebview.sdk.WebView 里渲染，它不一定继承 android.webkit.WebView。 */
    private static boolean isWebView(View view) {
        if (view instanceof android.webkit.WebView) {
            return true;
        }
        String name = view.getClass().getName();
        if ("android.webkit.WebView".equals(name) || "com.alipay.mywebview.sdk.WebView".equals(name)) {
            return true;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        CharSequence accessibility = view.getAccessibilityClassName();
        return accessibility != null && ("android.webkit.WebView".contentEquals(accessibility)
                || "com.alipay.mywebview.sdk.WebView".contentEquals(accessibility));
    }

    private interface CaptureCallback {
        void onCaptured(Bitmap bitmap, String error);
    }

    /** 主线程：对 WebView 实际渲染画面截图（PixelCopy；WebView 走硬件渲染，软件 draw 可能是空白）。 */
    private static void capture(Target target, View web, CaptureCallback callback) {
        if (web.getWidth() <= 0 || web.getHeight() <= 0) {
            callback.onCaptured(null, "invalid size");
            return;
        }
        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(web.getWidth(), web.getHeight(), Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
            callback.onCaptured(null, "bitmap allocation failed: " + t);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && target.window != null) {
            int[] inWindow = new int[2];
            web.getLocationInWindow(inWindow);
            Rect source = new Rect(inWindow[0], inWindow[1], inWindow[0] + web.getWidth(), inWindow[1] + web.getHeight());
            try {
                PixelCopy.request(target.window, source, bitmap, result -> {
                    if (result == PixelCopy.SUCCESS) {
                        callback.onCaptured(bitmap, null);
                    } else {
                        bitmap.recycle();
                        callback.onCaptured(null, "PixelCopy result=" + result);
                    }
                }, MAIN);
                return;
            } catch (Throwable t) {
                bitmap.recycle();
                callback.onCaptured(null, "PixelCopy failed: " + t);
                return;
            }
        }
        try {
            web.draw(new Canvas(bitmap));
            callback.onCaptured(bitmap, null);
        } catch (Throwable t) {
            bitmap.recycle();
            callback.onCaptured(null, "draw failed: " + t);
        }
    }

    /** 主线程：把截图交给工作线程做识别与匹配。 */
    private static void submit(Target target, View web, Bitmap bitmap) {
        int[] location = new int[2];
        web.getLocationOnScreen(location);
        final float scale = web.getWidth() / REFERENCE_WIDTH;
        try {
            WORKER.execute(() -> {
                Runnable release = () -> busy = false;
                try {
                    analyze(target, web, bitmap, location, scale, release);
                } catch (Throwable t) {
                    Log.printStackTrace(TAG, t);
                    if (!bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                    MAIN.post(release);
                }
            });
        } catch (Throwable t) {
            bitmap.recycle();
            busy = false;
            Log.printStackTrace(TAG, t);
        }
    }

    /** 工作线程：识别滑块 → 图像匹配 → 结果回主线程执行拖动。 */
    private static void analyze(Target target, View web, Bitmap bitmap, int[] location, float scale, Runnable release) {
        View root = target.root;
        Slider slider = detectSlider(bitmap, scale);
        if (slider != null && quiet && slider.hasTrackEnd()) {
            quiet = false; // 按钮和轨道都识别到才像验证码；只有按钮多半是普通页面上的蓝/红按钮
            handling = true; // 拼图方案接管，页面处理器不要再执行“滑动到底”
            Log.i(TAG, "被动扫描发现拼图滑块（未经接口/弹窗触发）：" + slider.describe());
        }
        if (slider == null) {
            diag(root, "未识别到拼图滑块按钮（图片可能还在加载，或布局与参考设备不同）");
            // 没识别到滑块的图（图片还没加载、窗口是别的页面…）先都存着，方便看过程；验证结束后
            // cleanupNoSlider() 统一删掉，只留包含验证码（识别到滑块）的截图
            if (!quiet) {
                saveSample(bitmap, "no-slider");
            }
            bitmap.recycle();
            MAIN.post(release);
            return;
        }
        if (!slider.atStart(scale)) {
            diag(root, "滑块不在轨道左端，跳过：" + slider.describe());
            bitmap.recycle();
            MAIN.post(release);
            return;
        }
        if (!slider.hasTrackEnd()) {
            diag(root, "未识别到轨道终点：" + slider.describe());
            saveNoTrackThrottled(root, bitmap);
            bitmap.recycle();
            MAIN.post(release);
            return;
        }
        float startX = location[0] + slider.centerX;
        float startY = location[1] + slider.centerY;
        float trackEndX = location[0] + slider.trackEndX;
        int sourceLeft = Math.round(slider.centerX - slider.buttonWidth / 2f);
        PuzzleSliderMatcher.Result match = PuzzleSliderMatcher.estimate(
                bitmap, startY, location[1], MATCH_BUDGET_MS, sourceLeft);
        if (!match.success) {
            diag(root, "缺口位移识别失败（不拖动）：" + match.error);
            saveSample(bitmap, "match-failed");
            bitmap.recycle();
            MAIN.post(release);
            return;
        }
        // 先把拖动排到主线程，再做整屏截图存档：PNG 压缩一张 1080x2400 的图要 1~2 秒，
        // 放在拖动前会把验证页等没（实测 08:39 那次就是存档期间页面已被关掉）。
        MAIN.post(() -> swipe(target, web, slider, match, startX, startY, trackEndX, "异步保存", release));
        try {
            String sampleName = saveSample(bitmap, "matched-d" + match.displacement);
            Log.i(TAG, "拖动前截图已保存: " + sampleName + "（位移=" + match.displacement
                    + " 方法=" + match.method + " 匹配耗时=" + match.elapsedMs + "ms）");
        } finally {
            bitmap.recycle();
        }
    }

    /** 主线程：窗口仍然有效才拖动，并只拖一次。 */
    private static void swipe(Target target, View web, Slider slider, PuzzleSliderMatcher.Result match,
                              float startX, float startY, float trackEndX, String sampleName, Runnable release) {
        View root = target.root;
        if (!handling || fallbackFired || !web.isShown() || !web.isAttachedToWindow()
                || attemptsOf(web) >= maxAttempts()) {
            Log.i(TAG, "已算出位移(" + match.displacement + "px)，但放弃拖动：handling=" + handling
                    + " fallback=" + fallbackFired + " shown=" + web.isShown() + " attached=" + web.isAttachedToWindow()
                    + " attempts=" + attemptsOf(web) + "/" + maxAttempts()
                    + " 匹配方法=" + match.method + " 匹配耗时=" + match.elapsedMs + "ms");
            release.run();
            return;
        }
        PuzzleSliderGeometry.Mapping mapping = PuzzleSliderGeometry.map(match.displacement, startX, trackEndX);
        if (!mapping.success || mapping.touchDistance <= 0f) {
            diag(root, "轨道换算失败：" + mapping.error);
            release.run();
            return;
        }
        final int attempt = attemptsOf(web) + 1;
        ATTEMPTS.put(web, attempt);
        long duration = SLIDE_MIN_MS + RandomUtil.nextInt(0, (int) (SLIDE_MAX_MS - SLIDE_MIN_MS + 1));
        Log.i(TAG, String.format(java.util.Locale.ROOT,
                "第%d/" + maxAttempts() + "次识别成功，开始拖动：缺口位移=%dpx 触摸距离=%.0fpx 终点=%.0f 时长=%dms 轨道截断=%s 方法=%s 分数=%.3f 耗时=%dms 滑块=%s 截图=%s",
                attempt, match.displacement, mapping.touchDistance, mapping.endX, duration, mapping.clamped,
                match.method, match.bestScore, match.elapsedMs, slider.describe(), sampleName));
        sawPuzzle = true;
        PuzzleSwipe.start(web, startX, startY, mapping.endX, startY, duration, 4f,
                (sent, reason) -> {
                    // 按下就被视图拒绝：手指相当于没碰到控件，这次不该占用尝试次数
                    boolean downRejected = !sent && "DOWN_REJECTED".equals(reason);
                    if (downRejected) {
                        ATTEMPTS.put(web, attempt - 1);
                        Log.i(TAG, "按下未生效（页面没有接收触摸的控件），本次不计入尝试，稍后重试");
                    } else {
                        Log.i(TAG, "拖动" + (sent ? "已完成" : "未完成") + "（" + reason + "）");
                    }
                    polling = false; // 拖完先停止扫描，稍后按页面结果决定是结束还是重试
                    if (sent) {
                        // 抬起后 260ms 补一张“落点”截图：页面通常 1 秒左右才刷新，
                        // 这张能看到拼图块实际停在哪，用来判断是位移算偏还是轨迹被判掉。
                        MAIN.postDelayed(() -> saveShot(target, web,
                                "matched-land-d" + match.displacement + "-a" + attempt), 260L);
                    }
                    // 验证成功会先显示“验证通过”几秒、窗口随后才关闭，固定 1.5 秒只判一次会把成功误判成失败，
                    // 进而多拖几次并记一条错误的失败。这里改成轮询等窗口真正关闭（最长 6 秒）。
                    awaitWindowClosed(web, downRejected ? 800L : 6000L, () -> {
                        handling = false;
                        ATTEMPTS.remove(web); // 验证成功：尝试次数归零
                        Log.record("拼图验证✅成功（拖动后验证窗口已关闭）");
                        cleanupNoSlider(); // 验证结束：只留包含验证码的截图
                    }, () -> {
                        if (downRejected) {
                            retry(root);
                        } else if (attempt < maxAttempts()) {
                            Log.i(TAG, "6 秒内验证窗口仍未关闭，第 " + attempt + " 次没通过，等页面刷新出新图后重试");
                            saveAfterShot(target, web, match.displacement, attempt);
                            retry(root);
                        } else {
                            Log.i(TAG, "6 秒内验证窗口仍未关闭，已自动尝试 " + attempt + " 次，交给兜底逻辑");
                            saveAfterShot(target, web, match.displacement, attempt);
                            cleanupNoSlider();
                            // 诊断：记录用户随后手动完成的那一次触摸特征，用来和自动轨迹对比。
                            PuzzleSwipe.captureAfterFailure(web);
                            fireFallback("自动尝试 " + attempt + " 次仍未通过");
                        }
                    });
                    release.run();
                });
    }

    /**
     * 轮询等待验证窗口关闭：成功态会先显示“验证通过”，窗口过几秒才消失，所以不能只判一次。
     * 关掉（脱离窗口或不可见）回调 onClosed，超时回调 onTimeout。
     */
    private static void awaitWindowClosed(View web, long budgetMs, Runnable onClosed, Runnable onTimeout) {
        final long start = SystemClock.uptimeMillis();
        Runnable[] step = new Runnable[1];
        step[0] = () -> {
            if (!web.isAttachedToWindow() || !web.isShown()) {
                onClosed.run();
                return;
            }
            if (SystemClock.uptimeMillis() - start >= budgetMs) {
                onTimeout.run();
                return;
            }
            MAIN.postDelayed(step[0], 300L);
        };
        MAIN.postDelayed(step[0], 300L);
    }

    /** 每个窗口最多自动尝试几次（连续失败太多会加重风控，所以有上限）。 */
    private static int maxAttempts() {
        return DEFAULT_ATTEMPTS;
    }

    /**
     * 拖完 1.5 秒窗口还在（多半没对准）时再截一张“拖动之后”的图，存成 matched-after-d<位移>-a<第几次>（属于 matched，会保留）：
     * 对照拖动前的 matched-d<位移> 就能看出滑块到底停在缺口的哪里，是位移算偏了还是页面进了别的状态。
     */
    private static void saveAfterShot(Target target, View web, int displacement, int attempt) {
        saveShot(target, web, "matched-after-d" + displacement + "-a" + attempt);
    }

    /** 截图并按名字存进 puzzle.zip（matched 前缀的会保留）。 */
    private static void saveShot(Target target, View web, String name) {
        try {
            capture(target, web, (bitmap, error) -> {
                if (bitmap == null) {
                    return;
                }
                try {
                    WORKER.execute(() -> {
                        try {
                            saveSample(bitmap, name);
                        } finally {
                            bitmap.recycle();
                        }
                    });
                } catch (Throwable t) {
                    bitmap.recycle();
                }
            });
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    private static int attemptsOf(View web) {
        Integer count = ATTEMPTS.get(web);
        return count == null ? 0 : count;
    }

    /**
     * 拖错后页面会刷新出新图：清掉这个窗口的截图计数和诊断去重，重新开始一轮监视（最多 RETRY_POLLS 秒）。
     * 新一轮仍要求滑块回到轨道左端、匹配可信才动手，所以页面还在刷新时不会乱拖。
     */
    private static void retry(View root) {
        if (!handling || fallbackFired) {
            return; // 已经兜底交给本地滑动，拼图方案不再抢回来
        }
        CAPTURES.remove(root);
        LAST_DIAG.remove(root);
        quiet = false;
        maxPolls = RETRY_POLLS;
        polls = 0;
        startPolling(); // 总是换新令牌：拖动完成时 polling 已置 false，旧循环会在下一次触发时自己退出
    }

    /** 同一窗口同一原因只记一次，避免每秒刷屏。 */
    private static void diag(View root, String message) {
        if (quiet) {
            return;
        }
        String last = LAST_DIAG.get(root);
        if (message.equals(last)) {
            return;
        }
        LAST_DIAG.put(root, message);
        Log.i(TAG, message);
    }

    /** 同一窗口 10 秒内只存一张 no-track，避免普通页面被误判后反复写盘。 */
    private static void saveNoTrackThrottled(View root, Bitmap bitmap) {
        long now = SystemClock.uptimeMillis();
        Long last = NO_TRACK_SAVED.get(root);
        if (last != null && now - last < 10_000L) {
            return;
        }
        NO_TRACK_SAVED.put(root, now);
        saveSample(bitmap, "no-track");
    }

    /**
     * 保存截图到 sesame/puzzle.zip（最多保留 20 张，超出丢最旧的）：条目名以 matched 开头的保留，
     * 其余在验证结束时删掉。返回条目名（保存失败返回 "未保存"）。
     */
    private static String saveSample(Bitmap bitmap, String tag) {
        String name = PuzzleSampleArchive.save(bitmap, tag);
        return name == null ? "未保存" : "puzzle.zip/" + name;
    }

    /**
     * 验证结束（拖动完成/监视窗口期结束）后：只留拖动过的 matched 截图。开始新一轮监视前也会调用一次，清上次的残留。
     * 工作线程执行，避免主线程做文件 IO；删除了才写日志，方便确认清理真的执行了。
     */
    private static void cleanupNoSlider() {
        try {
            WORKER.execute(() -> {
                try {
                    int deleted = PuzzleSampleArchive.deleteNonMatched();
                    if (deleted > 0) {
                        Log.i(TAG, "已清理 " + deleted + " 张没有拖动过的截图，puzzle.zip 只保留 matched");
                    }
                } catch (Throwable t) {
                    Log.printStackTrace(TAG, t);
                }
            });
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    // ===================== 滑块按钮与轨道识别（移植自 GR BaseCaptchaHandler） =====================

    private static final class Slider {
        final float centerX;
        final float centerY;
        final float trackEndX;
        final int buttonWidth;

        Slider(float centerX, float centerY, float trackEndX, int buttonWidth) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.trackEndX = trackEndX;
            this.buttonWidth = buttonWidth;
        }

        boolean hasTrackEnd() {
            return !Float.isNaN(trackEndX) && !Float.isInfinite(trackEndX) && trackEndX > centerX;
        }

        boolean atStart(float scale) {
            return Math.abs(centerX - START_X * scale) <= START_TOLERANCE * scale;
        }

        String describe() {
            return "center=(" + Math.round(centerX) + "," + Math.round(centerY) + ") buttonWidth=" + buttonWidth
                    + " trackEnd=" + (hasTrackEnd() ? String.valueOf(Math.round(trackEndX)) : "无");
        }
    }

    private static Slider detectSlider(Bitmap bitmap, float scale) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return null;
        }
        try {
            int scanTop = Math.max(0, Math.round((START_Y - VERTICAL_TOLERANCE) * scale));
            int scanBottom = Math.min(bitmap.getHeight(), Math.round((START_Y + VERTICAL_TOLERANCE) * scale));
            int scanLeft = Math.max(0, Math.round((START_X - 180f) * scale));
            int scanRight = Math.min(bitmap.getWidth(), Math.round((START_X + 220f) * scale));
            int[] c = findButtonComponent(bitmap, scanLeft, scanTop, scanRight, scanBottom, scale);
            if (c == null) {
                return null;
            }
            float trackEnd = detectTrackEnd(bitmap, c[0], c[1], c[2], c[3], scale);
            return new Slider((c[0] + c[2]) / 2f, (c[1] + c[3]) / 2f, trackEnd, c[2] - c[0] + 1);
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return null;
        }
    }

    /**
     * 取一个连通的彩色块（蓝/红按钮）而不是所有蓝/红像素的包围盒：错误页常带另一块彩色插图，
     * 合并会让坐标漂移。返回 {left, top, right, bottom}，找不到返回 null。
     */
    private static int[] findButtonComponent(Bitmap bitmap, int left, int top, int right, int bottom, float scale) {
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) {
            return null;
        }
        boolean[] visited = new boolean[width * height];
        int[] queue = new int[width * height];
        int minWidth = Math.max(50, Math.round(60f * scale));
        int maxWidth = Math.max(minWidth, Math.round(180f * scale));
        int minHeight = Math.max(45, Math.round(55f * scale));
        int maxHeight = Math.max(minHeight, Math.round(180f * scale));
        int minPixels = Math.max(400, Math.round(1800f * scale * scale));
        float expectedX = START_X * scale;
        float expectedY = START_Y * scale;
        int[] best = null;
        float bestDistance = Float.MAX_VALUE;
        for (int index = 0; index < visited.length; index++) {
            if (visited[index]) {
                continue;
            }
            int x = left + index % width;
            int y = top + index / width;
            if (!isButtonColor(bitmap.getPixel(x, y))) {
                visited[index] = true;
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = index;
            visited[index] = true;
            int minX = x, maxX = x, minY = y, maxY = y;
            while (head < tail) {
                int point = queue[head++];
                int px = point % width;
                int py = point / width;
                minX = Math.min(minX, left + px);
                maxX = Math.max(maxX, left + px);
                minY = Math.min(minY, top + py);
                maxY = Math.max(maxY, top + py);
                if (px > 0 && !visited[point - 1] && isButtonColor(bitmap.getPixel(left + px - 1, top + py))) {
                    visited[point - 1] = true;
                    queue[tail++] = point - 1;
                }
                if (px + 1 < width && !visited[point + 1] && isButtonColor(bitmap.getPixel(left + px + 1, top + py))) {
                    visited[point + 1] = true;
                    queue[tail++] = point + 1;
                }
                if (py > 0 && !visited[point - width] && isButtonColor(bitmap.getPixel(left + px, top + py - 1))) {
                    visited[point - width] = true;
                    queue[tail++] = point - width;
                }
                if (py + 1 < height && !visited[point + width] && isButtonColor(bitmap.getPixel(left + px, top + py + 1))) {
                    visited[point + width] = true;
                    queue[tail++] = point + width;
                }
            }
            int componentWidth = maxX - minX + 1;
            int componentHeight = maxY - minY + 1;
            float centerX = (minX + maxX) / 2f;
            float centerY = (minY + maxY) / 2f;
            if (tail < minPixels || componentWidth < minWidth || componentWidth > maxWidth
                    || componentHeight < minHeight || componentHeight > maxHeight
                    || Math.abs(centerX - expectedX) > START_TOLERANCE * scale
                    || Math.abs(centerY - expectedY) > VERTICAL_TOLERANCE * scale) {
                continue;
            }
            float distance = Math.abs(centerX - expectedX) + Math.abs(centerY - expectedY) * 0.5f;
            if (best == null || distance < bestDistance) {
                best = new int[]{minX, minY, maxX, maxY};
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean isButtonColor(int color) {
        int red = (color >>> 16) & 0xff;
        int green = (color >>> 8) & 0xff;
        int blue = color & 0xff;
        return (blue >= 170 && green >= 60 && red <= 120 && blue - red >= 70)
                || (red >= 160 && green <= 140 && blue <= 140 && red - blue >= 70);
    }

    /** 轨道是紧挨按钮右侧的浅色低饱和矩形，右端减去半个按钮宽度就是按钮中心能到的终点。 */
    private static float detectTrackEnd(Bitmap bitmap, int buttonLeft, int buttonTop, int buttonRight,
                                        int buttonBottom, float scale) {
        int inferredWidth = buttonRight - buttonLeft + 1 + 2 * Math.max(2, Math.round(3f * scale));
        int minRail = Math.max(inferredWidth * 2, Math.round(300f * scale));
        int bestEnd = -1;
        int inset = Math.max(4, Math.round(16f * scale));
        for (int y = buttonTop + inset; y <= buttonBottom - inset; y += Math.max(2, Math.round(4f * scale))) {
            int x = buttonRight + 1;
            int searchLimit = Math.min(bitmap.getWidth() - 1, x + Math.max(12, Math.round(12f * scale)));
            while (x <= searchLimit && !isLightNeutral(bitmap.getPixel(x, y))) {
                x++;
            }
            int runStart = x;
            while (x < bitmap.getWidth() && isLightNeutral(bitmap.getPixel(x, y))) {
                x++;
            }
            int runEnd = x - 1;
            if (runEnd - runStart + 1 >= minRail) {
                bestEnd = Math.max(bestEnd, runEnd);
            }
        }
        return bestEnd < 0 ? Float.NaN : bestEnd + 1f - inferredWidth / 2f;
    }

    private static boolean isLightNeutral(int color) {
        int red = (color >>> 16) & 0xff;
        int green = (color >>> 8) & 0xff;
        int blue = color & 0xff;
        int min = Math.min(red, Math.min(green, blue));
        int max = Math.max(red, Math.max(green, blue));
        return min >= 225 && max <= 253 && max - min <= 5;
    }
}
