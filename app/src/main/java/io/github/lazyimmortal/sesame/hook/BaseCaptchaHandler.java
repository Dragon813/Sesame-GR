package io.github.lazyimmortal.sesame.hook;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.RandomUtil;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

/**
 * 验证码处理程序的基类，提供处理滑动验证码的通用逻辑。
 * 该类专门用于处理目标应用验证页面上的滑动验证码。
 */
public abstract class BaseCaptchaHandler {
    private static final String TAG = "CaptchaHandler";
    
    // 滑动参数配置
    private static final int SLIDE_START_OFFSET = 25; // 滑动起始位置偏移量（像素）
    private static final int SLIDE_END_MARGIN = 20;   // 滑动结束位置距离右侧的边距（像素）
    private static final long SLIDE_DURATION_MIN = 500L; // 最小滑动持续时间
    private static final long SLIDE_DURATION_MAX = 600L; // 最大滑动持续时间
    
    // 滑动后延迟检查是否成功
    private static final long POST_SLIDE_CHECK_DELAY_MS = 500L;
    
    // 查找滑动验证文本的 XPath
    private static final String SLIDE_VERIFY_TEXT_XPATH = "//TextView[contains(@text,'向右滑动验证')]";
    
    // 并发控制，防止多个处理程序同时运行
    private static final Lock captchaProcessingMutex = new ReentrantLock();
    private static final Random random = new Random();
    
    /**
     * 获取在 DataStore 中存储滑动路径的键。
     * @return 用于存储滑动路径的键。
     */
    protected abstract String getSlidePathKey();
    
    /**
     * 处理当前 Activity 中的验证码。
     * @param activity 当前 Activity 实例。
     * @param root 根视图图像。
     * @return 如果验证码处理成功返回 true，否则返回 false。
     */
    public boolean handleActivity(Activity activity, SimpleViewImage root) {
        try {
            return handleSlideCaptcha(activity);
        } catch (Exception e) {
            Log.record("滑动验证🆘处理验证码页面时发生异常: " + e);
            return false;
        }
    }
    
    @SuppressLint("SuspiciousIndentation")
    private boolean handleSlideCaptcha(Activity activity) {
        if (!captchaProcessingMutex.tryLock()) {
            return true; // 返回 true 告知上层已处理，避免重试
        }
        try {
            SimpleViewImage slideTextInDialog = findSlideTextInDialog();
            if (slideTextInDialog == null) {
                // Log.captcha(TAG, "未找到滑动验证文本，跳过处理");
                return false; // 未找到关键视图，返回 false 让其他处理器尝试
            }
            Log.record("滑动验证🆘发现滑动验证文本:" + slideTextInDialog.getText()+"[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
            try {
                Thread.sleep(500L); // 等待界面稳定
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.record("滑动验证🆘等待界面稳定时被中断: " + e);
                return false;
            }
            // 执行滑动验证
            return performSlideAndVerify(activity, slideTextInDialog);
        } catch (Exception e) {
            Log.record("滑动验证🆘处理滑动验证码时发生错误: " + e);
            return false;
        } finally {
            captchaProcessingMutex.unlock();
        }
    }
    
    /**
     * 执行滑动操作并验证结果。
     * @param activity 当前的 Activity。
     * @param slideTextView "向右滑动验证"文本的视图图像，作为查找滑块的锚点。
     * @return 如果验证码成功解除返回 true，否则返回 false。
     */
    private boolean performSlideAndVerify(Activity activity, SimpleViewImage slideTextView) {
        View sliderView = ViewHierarchyAnalyzer.findActualSliderView(slideTextView);
        if (sliderView == null) {
            Log.record("滑动验证🆘未能找到可操作的滑块视图，滑动无法执行。");
            return false;
        }
        
        // 计算滑动坐标
        SlideCoordinates coordinates = calculateSlideCoordinates(activity, sliderView);
        if (coordinates == null) {
            Log.record("滑动验证🆘计算滑动坐标失败，滑动无法执行。");
            return false;
        }
        
        // 随机化滑动持续时间，模拟更自然的行为
        long slideDuration = SLIDE_DURATION_MIN + RandomUtil.nextLong(SLIDE_DURATION_MAX,SLIDE_DURATION_MIN + 1);
        
        // 执行滑动
        MotionEventSimulator.simulateSwipe(
                sliderView,
                coordinates.getStartX(),
                coordinates.getStartY(),
                coordinates.getEndX(),
                coordinates.getEndY(),
                slideDuration
        );
        
        try {
            Thread.sleep(POST_SLIDE_CHECK_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.record("滑动验证🆘滑动后等待检查时被中断:" + e);
            return false;
        }
        
        if (checkCaptchaTextGone()) {
            //Log.record("滑动验证🆘验证码文本已消失，滑动成功。");
            return true;
        } else {
            //Log.record("滑动验证🆘验证码文本仍然存在，滑动可能失败。");
            return false;
        }
    }
    
    /**
     * 计算滑动验证码的坐标参数。
     *
     * @param activity 当前Activity，用于获取屏幕信息
     * @param sliderView 滑块视图
     * @return 包含(startX, startY, endX, endY)的四元组，如果计算失败返回null
     */
    private SlideCoordinates calculateSlideCoordinates(Activity activity, View sliderView) {
        // 获取滑动区域的整体容器（滑块的父容器）
        ViewGroup slideContainer = (sliderView.getParent() instanceof ViewGroup) ? (ViewGroup) sliderView.getParent() : null;
        if (slideContainer == null) {
            // Log.captcha(TAG, "未能找到滑块容器");
            return null;
        }
        
        // 获取屏幕尺寸信息
        Resources resources = activity.getResources();
        DisplayMetrics displayMetrics = resources.getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;
        
        // 计算滑动区域的边界
        int[] containerLocation = new int[2];
        slideContainer.getLocationOnScreen(containerLocation);
        int containerX = containerLocation[0];
        int containerY = containerLocation[1];
        int containerWidth = slideContainer.getWidth();
        int containerHeight = slideContainer.getHeight();
        
        // 计算滑块位置
        int[] sliderLocation = new int[2];
        sliderView.getLocationOnScreen(sliderLocation);
        int sliderX = sliderLocation[0];
        int sliderY = sliderLocation[1];
        int sliderWidth = sliderView.getWidth();
        int sliderHeight = sliderView.getHeight();
        
        // 计算滑动起点（滑块中心稍微偏右，模拟手指按住滑块）
        int startXOffset = random.nextInt(7) - 3; // -3 到 3
        float startX = sliderX + sliderWidth / 2f + SLIDE_START_OFFSET + startXOffset;
        int startYOffset = random.nextInt(5) - 2; // -2 到 2
        float startY = sliderY + sliderHeight / 2f + startYOffset;
        
        // 计算滑动终点
        int containerRightEdge = containerX + containerWidth;
        float maxEndX = screenWidth - 50f; // 距离屏幕右边缘50像素
        
        // 计算理想的滑动终点（容器右端减去边距）
        int endXOffset = random.nextInt(11) - 5; // -5 到 5
        float endX = containerRightEdge - SLIDE_END_MARGIN + endXOffset;
        
        // 确保滑动终点不超过屏幕边界
        if (endX > maxEndX) {
            endX = maxEndX;
            Log.record("滑动验证🆘调整滑动终点以适配屏幕边界");
        }
        
        // 确保滑动距离足够（至少滑块宽度的1.5倍）
        float minSlideDistance = sliderWidth * 1.5f;
        float actualSlideDistance = endX - startX;
        if (actualSlideDistance < minSlideDistance) {
            int minDistanceOffset = random.nextInt(7) - 3; // -3 到 3
            endX = startX + minSlideDistance + minDistanceOffset;
            Log.record("滑动验证🆘调整滑动距离至最小要求:" + minSlideDistance + "px");
        }
        
        float endY = startY; // 保持水平滑动
        /*
        // 输出详细的调试信息
        Log.record("屏幕信息🆘尺寸=" + screenWidth + "x" + screenHeight);
        Log.record("滑动区域信息: 容器位置=[" + containerX + "," + containerY + "], 尺寸=" + containerWidth + "x" + containerHeight);
        Log.record("滑块信息: 位置=[" + sliderX + "," + sliderY + "], 尺寸=" + sliderWidth + "x" + sliderHeight);
        Log.record("计算结果: 起点=[" + startX + "," + startY + "], 终点=[" + endX + "," + endY + "], 滑动距离=" + (endX - startX) + "px");
        */
        Log.record("滑动验证🆘屏幕信息:尺寸=" + screenWidth + "x" + screenHeight + ";" + "滑动区域信息:容器位置=[" + containerX + "," + containerY + "],尺寸=" + containerWidth + "x" + containerHeight + ";" + "滑块信息:位置=[" + sliderX + "," + sliderY + "],尺寸=" + sliderWidth + "x" + sliderHeight + ";" + "计算结果:起点=[" + startX + "," + startY + "],终点=[" + endX + "," + endY + "],滑动距离=" + (endX - startX) + "px.[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
        // 生成滑动指令并发送广播
        long slideDuration = SLIDE_DURATION_MIN + RandomUtil.nextLong(SLIDE_DURATION_MAX,SLIDE_DURATION_MIN + 1);
        String swipeCmd = String.format("input swipe %d %d %d %d %d",
                (int) startX, (int) startY,
                (int) endX, (int) endY,
                slideDuration);
        //ApplicationHook.sendBroadcastShell(getSlidePathKey(), swipeCmd);
        
        return new SlideCoordinates(startX, startY, endX, endY);
    }
    
    /**
     * 检查验证码验证文本是否已从视图中消失。
     * @return 如果文本已消失返回 true，如果仍然存在返回 false。
     */
    private boolean checkCaptchaTextGone() {
        SimpleViewImage slideTextInDialog = findSlideTextInDialog();
        if (slideTextInDialog == null) {
            //Log.record("滑动验证🆘验证码文本已消失(在对话框中未找到)。");
            return true;
        } else {
            //Log.record("滑动验证🆘验证码文本仍然存在(在对话框中找到)。");
            return false;
        }
    }
    
    /**
     * 在对话框视图中查找滑动验证文本。
     * @return 如果找到则返回文本视图的 SimpleViewImage，否则返回 null。
     */
    private SimpleViewImage findSlideTextInDialog() {
        try {
            // Log.captcha(TAG, "尝试通过 XPath 查找滑动验证文本: " + SLIDE_VERIFY_TEXT_XPATH);
            return SimplePageManager.tryGetTopView(SLIDE_VERIFY_TEXT_XPATH);
        } catch (Exception e) {
            Log.record("滑动验证🆘由于异常导致查找验证码文本失败:"+e);
            return null;
        }
    }
}