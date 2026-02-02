package io.github.lazyimmortal.sesame.hook;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

import io.github.lazyimmortal.sesame.util.RandomUtil;
import io.github.lazyimmortal.sesame.util.TimeUtil;

/**
 * 模拟系统级 MotionEvent 的工具类.
 * 用于执行如滑动等自动化操作.
 */
public class MotionEventSimulator {
    private static final String TAG = "MotionEventSimulator";
    private static final Random RANDOM = new Random();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    
    /**
     * 异步模拟一个从起点到终点的滑动操作.
     *
     * @param view     要在其上执行滑动操作的视图 (通常是滑块本身).
     * @param startX   滑动的屏幕绝对 X 坐标起点.
     * @param startY   滑动的屏幕绝对 Y 坐标起点.
     * @param endX     滑动的屏幕绝对 X 坐标终点.
     * @param endY     滑动的屏幕绝对 Y 坐标终点.
     * @param duration 滑动动画的总时长 (毫秒).
     */
    public static void simulateSwipe(View view, float startX, float startY, float endX, float endY, long duration) {
        // 确保所有UI操作都在主线程执行（替换协程，适配项目纯Java风格）
        MAIN_HANDLER.post(() -> {
            Log.i(TAG, "准备在视图 " + view.getClass().getSimpleName() + " 上模拟滑动");
            Log.d(TAG, "从 (" + startX + ", " + startY + ") -> (" + endX + ", " + endY + ")，持续时间: " + duration + "ms");
            
            if (!view.isShown() || !view.isEnabled()) {
                Log.e(TAG, "滑动失败: 目标视图不可见或未启用.");
                return;
            }
            
            long downTime = SystemClock.uptimeMillis();
            try {
                // 1. 发送 ACTION_DOWN 事件，标志着手指按下
                dispatchTouchEvent(view, MotionEvent.ACTION_DOWN, startX, startY, downTime, downTime);
                TimeUtil.sleep(RandomUtil.nextLong(30, 80)); // 按下后短暂延迟，更像人操作
                
                // 2. 模拟 ACTION_MOVE 事件序列，构造滑动轨迹
                int steps = 15; // 将滑动轨迹分为 15 步
                long stepDuration = (duration - 100) / steps;
                float xStep = (endX - startX) / steps;
                float yStep = (endY - startY) / steps;
                
                for (int i = 1; i <= steps; i++) {
                    float currentX = startX + xStep * i + RandomUtil.nextInt(-3, 4); // 增加微小随机抖动
                    float currentY = startY + yStep * i + RandomUtil.nextInt(-2, 3);
                    long eventTime = downTime + (stepDuration * i);
                    
                    dispatchTouchEvent(view, MotionEvent.ACTION_MOVE, currentX, currentY, downTime, eventTime);
                    TimeUtil.sleep(stepDuration); // 修复语法错误：补充缺失的右括号
                }
                
                // 3. 发送 ACTION_UP 事件，标志着手指抬起
                long upTime = downTime + duration;
                dispatchTouchEvent(view, MotionEvent.ACTION_UP, endX, endY, downTime, upTime);
                Log.i(TAG, "模拟滑动事件序列发送完毕.");
            } catch (Throwable e) {
                Log.e(TAG, "派发触摸事件时发生异常");
            }
        });
    }
    
    /**
     * 重载方法，使用默认滑动时长
     */
    public static void simulateSwipe(View view, float startX, float startY, float endX, float endY) {
        simulateSwipe(view, startX, startY, endX, endY, 800L);
    }
    
    /**
     * 辅助函数，用于创建和派发 MotionEvent.
     */
    private static void dispatchTouchEvent(View view, int action, float x, float y, long downTime, long eventTime) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[1];
        MotionEvent.PointerProperties pointerProps = new MotionEvent.PointerProperties();
        pointerProps.id = 0;
        pointerProps.toolType = MotionEvent.TOOL_TYPE_FINGER;
        properties[0] = pointerProps;
        
        MotionEvent.PointerCoords[] cords = new MotionEvent.PointerCoords[1];
        MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        pointerCoords.x = x;
        pointerCoords.y = y;
        pointerCoords.pressure = 1f;
        pointerCoords.size = 1f;
        cords[0] = pointerCoords;
        
        MotionEvent event = MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                1,
                properties,
                cords,
                0,
                0,
                1f,
                1f,
                0,
                0,
                0,
                0
        );
        
        view.dispatchTouchEvent(event);
        event.recycle(); // 回收事件，避免内存泄漏
    }
}