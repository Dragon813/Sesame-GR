package io.github.Dragon813.sesame.hook;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;

import io.github.Dragon813.sesame.util.Log;
import io.github.Dragon813.sesame.util.RandomUtil;

/**
 * 拼图滑块的触摸序列：按下、按时间推进的移动、精确停在终点、抬起，全程主线程 postDelayed 排队，不阻塞。
 * 坐标传屏幕绝对值，内部按目标视图当前位置换算成视图内坐标（WebView 不在屏幕原点，直接传绝对坐标会偏）。
 * <p>
 * 轨迹不是匀速直线：真手指按下后会先停一下、起步快、临近终点减速、末尾有轻微过冲再回正、抬起前也会停顿，
 * 采样间隔和纵向抖动也带随机。匀速直线加等间隔事件是这类验证码最容易识别的特征
 * （实测位移误差只有 1px 却始终不通过，说明卡的是轨迹判定而不是位置）。
 * <p>
 * 中途窗口消失/移动/被取消会立即结束并回调 false；只有 DOWN、MOVE、UP 全部被视图接收才回调 true。
 * 按下就被拒绝时回调的原因单独标成 {@code DOWN_REJECTED}，调用方据此不把这次算作一次尝试。
 */
final class PuzzleSwipe {
    interface Completion {
        void onDone(boolean sent, String reason);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Swipe active;

    private PuzzleSwipe() {
    }

    /** 只读诊断脚本：记录页面收到的触摸要点（trusted/force/radius），并给当前条数打一个读取标记。 */
    private static final String JS_WATCH =
            "(function(){try{window.__sxT=window.__sxT||[];"
                    + "if(!window.__sxW){window.__sxW=1;var f=function(e){try{var t=e.changedTouches||[];for(var i=0;i<t.length;i++){var c=t[i];"
                    + "window.__sxT.push(e.type.charAt(5)+'|'+c.clientX.toFixed(1)+','+c.clientY.toFixed(1)+'|'+e.timeStamp.toFixed(0)"
                    + "+'|'+(e.isTrusted?1:0)+'|'+(c.force||0).toFixed(2)+'|'+(c.radiusX||0).toFixed(1)+'|'+(c.radiusY||0).toFixed(1));}"
                    + "if(window.__sxT.length>600)window.__sxT.splice(0,300);}catch(x){}};"
                    + "['touchstart','touchmove','touchend','touchcancel'].forEach(function(n){document.addEventListener(n,f,true);});}"
                    + "window.__sxM=(window.__sxT||[]).length;}catch(x){}})()";

    /** 只读诊断脚本：只汇总标记之后新增的触摸（多半就是用户手动那一次）。 */
    private static final String JS_TAIL =
            "(function(){try{var a=window.__sxT||[];var m=window.__sxM||0;var b=a.slice(m);window.__sxM=a.length;"
                    + "var g=[],xs=[],pv=null,tr=0;for(var i=0;i<b.length;i++){var p=b[i].split('|');if(p[3]==='1')tr++;"
                    + "if(p[0]!=='m')continue;var t=+p[2];if(pv!==null){var d=t-pv;if(d>=0)g.push(Math.round(d));}pv=t;xs.push(p[1]);}"
                    + "var mn=9999,mx=0;for(var i=0;i<g.length;i++){if(g[i]<mn)mn=g[i];if(g[i]>mx)mx=g[i];}"
                    + "if(!b.length)return '';"
                    + "return JSON.stringify({n:b.length,moves:xs.length,trusted:tr,dtMin:mn,dtMax:mx,dt:g.slice(0,12),"
                    + "first:b.slice(0,3),last:b.slice(-3)});}catch(x){return 'ERR '+x}})()";

    /**
     * 诊断用（自动方案失败后调用）：把后续触摸事件延时读回来，用于和自动轨迹对比。
     * 只读页面事件，不产生任何触摸，也不参与验证。
     */
    static void captureAfterFailure(View target) {
        if (target == null) {
            return;
        }
        Runnable run = () -> {
            if (!callJs(target, JS_WATCH, null)) {
                return;
            }
            for (long delay : new long[]{6000L, 20000L, 45000L}) {
                MAIN.postDelayed(() -> callJs(target, JS_TAIL, "拼图验证🖐️后续(手动)触摸: "), delay);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            run.run();
        } else {
            MAIN.post(run);
        }
    }

    /**
     * 调页面的 evaluateJavascript：触摸目标是 WebViewInternalForM（转发用的 View），
     * 真正执行 JS 的是它 getImpl() 返回的 IWebView，所以两处都要找。
     */
    private static boolean callJs(View target, String js, String prefix) {
        try {
            Object evaluator = target;
            Method method = findJsMethod(evaluator.getClass());
            if (method == null) {
                Object impl = invokeNoArg(target, "getImpl");
                if (impl != null) {
                    Method implMethod = findJsMethod(impl.getClass());
                    if (implMethod != null) {
                        evaluator = impl;
                        method = implMethod;
                    }
                }
            }
            if (method == null) {
                return false;
            }
            Object callback = null;
            Class<?>[] types = method.getParameterTypes();
            if (types.length > 1 && types[1].isInterface()) {
                callback = Proxy.newProxyInstance(types[1].getClassLoader(),
                        new Class<?>[]{types[1]}, (proxy, invoked, args) -> {
                            String name = invoked.getName();
                            if ("equals".equals(name)) {
                                return args != null && args.length == 1 && proxy == args[0];
                            }
                            if ("hashCode".equals(name)) {
                                return System.identityHashCode(proxy);
                            }
                            if ("toString".equals(name)) {
                                return "SesameJs";
                            }
                            if ("onReceiveValue".equals(name) && args != null && args.length == 1
                                    && args[0] != null && !"\"\"".equals(args[0])) {
                                Log.i("PuzzleSwipe", (prefix == null ? "拼图验证🖐️: " : prefix) + unquote(args[0]));
                            }
                            return null;
                        });
            }
            method.invoke(evaluator, js, callback);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** evaluateJavascript 返回的是 JSON 字符串字面量，去掉外层引号并反转义，日志更好读。 */
    private static String unquote(Object value) {
        String s = String.valueOf(value);
        if (s.length() > 1 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            s = s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return s;
    }

    private static Object invokeNoArg(Object owner, String name) {
        for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(owner);
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Method findJsMethod(Class<?> clazz) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if ("evaluateJavascript".equals(method.getName())
                        && types.length == 2 && types[0] == String.class) {
                    try {
                        method.setAccessible(true);
                    } catch (Throwable ignored) {
                    }
                    return method;
                }
            }
        }
        return null;
    }

    /** 任意线程可调；实际在主线程开始。 */
    static void start(View target, float sx, float sy, float ex, float ey, long duration, float arc,
                      Completion completion) {
        Runnable begin = () -> {
            if (active != null) {
                active.finish(false, "CANCELLED");
            }
            Swipe swipe = new Swipe(target, sx, sy, ex, ey, Math.max(500L, duration), arc, completion);
            active = swipe;
            swipe.begin();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            begin.run();
        } else {
            MAIN.post(begin);
        }
    }

    private static final class Swipe {
        final View target;
        final float sx, sy, ex, ey;
        final long duration;
        final Completion completion;
        final int[] origin = new int[2];
        final int[] location = new int[2];
        /** 依次为 {相对按下时刻的延迟ms, 屏幕x, 屏幕y}。 */
        final float[][] steps;
        /** 最后一帧移动之后、抬起之前还要停多久。 */
        final long tail;
        long downTime;
        boolean down;
        boolean done;

        Swipe(View target, float sx, float sy, float ex, float ey, long duration, float arc,
              Completion completion) {
            this.target = target;
            this.sx = sx;
            this.sy = sy;
            this.ex = ex;
            this.ey = ey;
            this.duration = duration;
            this.completion = completion;
            long hold = 60L + RandomUtil.nextInt(0, 80);
            this.tail = 45L + RandomUtil.nextInt(0, 70);
            this.steps = buildPath(sx, sy, ex, ey, duration, arc, hold, tail);
        }

        boolean available() {
            if (done || active != this || target == null || !target.isAttachedToWindow()
                    || !target.isShown() || !target.isEnabled()) {
                return false;
            }
            target.getLocationOnScreen(location);
            return !down || (origin[0] == location[0] && origin[1] == location[1]);
        }

        void begin() {
            if (!available()) {
                finish(false, "WINDOW_UNAVAILABLE");
                return;
            }
            target.getLocationOnScreen(origin);
            MAIN.postDelayed(this::startDown, 60L);
        }

        private void startDown() {
            if (!available()) {
                finish(false, "WINDOW_UNAVAILABLE");
                return;
            }
            downTime = SystemClock.uptimeMillis();
            down = true;
            if (!send(MotionEvent.ACTION_DOWN, sx - origin[0], sy - origin[1], 0f)) {
                finish(false, "DOWN_REJECTED");
                return;
            }
            for (float[] step : steps) {
                MAIN.postDelayed(() -> moveTo(step), (long) step[0]);
            }
            MAIN.postDelayed(this::up, (long) steps[steps.length - 1][0] + tail);
        }

        void moveTo(float[] step) {
            if (done) {
                return;
            }
            if (!available()) {
                finish(false, "WINDOW_UNAVAILABLE");
                return;
            }
            long now = SystemClock.uptimeMillis();
            float progress = Math.min(1f, (now - downTime) / (float) duration);
            if (!send(MotionEvent.ACTION_MOVE, step[1] - origin[0], step[2] - origin[1], progress)) {
                finish(false, "INPUT_REJECTED");
            }
        }

        void up() {
            if (done) {
                return;
            }
            if (!available()) {
                finish(false, "WINDOW_UNAVAILABLE");
                return;
            }
            boolean accepted = send(MotionEvent.ACTION_UP, ex - origin[0], ey - origin[1], 1f);
            down = false;
            finish(accepted, accepted ? "INPUT_FINISHED" : "INPUT_REJECTED");
        }

        boolean send(int action, float localX, float localY, float progress) {
            long eventTime = SystemClock.uptimeMillis();
            MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
            props.id = 0;
            props.toolType = MotionEvent.TOOL_TYPE_FINGER;
            MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
            coords.x = localX;
            coords.y = localY;
            boolean released = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
            // 按实机抓到的真手指样本对齐：按下到抬起前压力恒为 1.00，抬起为 0。
            // 之前用 0.55~0.85 的起伏值，页面收到的 forward(force) 与真机样本明显不同。
            coords.pressure = released ? 0f : 1f;
            coords.size = 0.8f;
            coords.toolMajor = 36f;
            coords.toolMinor = 36f;
            MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, 1,
                    new MotionEvent.PointerProperties[]{props}, new MotionEvent.PointerCoords[]{coords},
                    0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
            try {
                return target.dispatchTouchEvent(event);
            } catch (Throwable failed) {
                return false;
            } finally {
                event.recycle();
            }
        }

        void finish(boolean success, String reason) {
            if (done) {
                return;
            }
            done = true;
            if (down) {
                // 手指还按着就结束：补一个 CANCEL，避免宿主以为一直按着
                send(MotionEvent.ACTION_CANCEL, ex - origin[0], ey - origin[1], 1f);
                down = false;
            }
            if (active == this) {
                active = null;
            }
            if (completion != null) {
                completion.onDone(success, reason);
            }
        }

        /**
         * 生成一条“像人拖出来”的轨迹：采样间隔本身就不均匀（8–21ms，偶尔掉一帧），
         * 速度按“起步加速→中段匀速→临近终点减速→最后几乎停下再松手”随真实经过时间累加，
         * 纵向是带随机游走的轻微抖动而非固定弧线，x 单调推进不回退，最后一帧精确落在终点。
         */
        private static float[][] buildPath(float sx, float sy, float ex, float ey, long duration,
                                           float arc, long hold, long tail) {
            long moveMs = Math.max(260L, duration - hold - tail);
            ArrayList<Long> times = new ArrayList<>();
            long at = 0L;
            while (at < moveMs && times.size() < 120) {
                at += RandomUtil.nextInt(8, 21);
                if (RandomUtil.nextInt(0, 9) == 0) {
                    at += RandomUtil.nextInt(6, 14); // 偶尔掉一帧
                }
                if (at > moveMs) {
                    at = moveMs;
                }
                times.add(at);
            }
            if (times.isEmpty()) {
                times.add(moveMs);
            }
            int count = times.size();
            float[] ratio = motionProfile(times, moveMs);
            float[][] steps = new float[count][];
            float drift = 0f;
            for (int i = 0; i < count; i++) {
                float p = (i + 1) / (float) count;
                float x = sx + (ex - sx) * ratio[i];
                drift += (float) (RandomUtil.nextDouble() - 0.5) * 1.1f;
                if (drift > 2.6f) {
                    drift = 2.6f;
                } else if (drift < -2.6f) {
                    drift = -2.6f;
                }
                float y = sy + drift + (float) Math.sin(Math.PI * p) * arc * 0.15f;
                if (i == count - 1) {
                    x = ex;
                    y = ey;
                }
                steps[i] = new float[]{hold + times.get(i), x, y};
            }
            return steps;
        }

        /**
         * 位移比例曲线：按每段“实际经过的时间 × 该时刻的速度”累加，保证 x 随时间单调推进到终点。
         * 前 16% 加速、16–58% 匀速、58–93% 减速，最后 7% 几乎停下（松手前的自然停顿）。
         */
        private static float[] motionProfile(ArrayList<Long> times, long moveMs) {
            int count = times.size();
            float[] weight = new float[count];
            float total = 0f;
            long prev = 0L;
            for (int i = 0; i < count; i++) {
                long now = times.get(i);
                float mid = ((prev + now) * 0.5f) / (float) moveMs;
                float speed;
                if (mid < 0.16f) {
                    speed = 0.25f + 0.75f * (mid / 0.16f);          // 起步加速
                } else if (mid < 0.58f) {
                    speed = 1f;                                      // 中段匀速
                } else if (mid < 0.93f) {
                    float r = (mid - 0.58f) / 0.35f;
                    speed = 1f - 0.78f * (float) Math.pow(r, 1.35);  // 临近终点减速
                } else {
                    speed = 0.12f;                                   // 松手前几乎停下
                }
                weight[i] = speed * (now - prev);
                total += weight[i];
                prev = now;
            }
            float[] ratio = new float[count];
            float sum = 0f;
            for (int i = 0; i < count; i++) {
                sum += weight[i];
                ratio[i] = total <= 0f ? 1f : Math.min(1f, sum / total);
            }
            ratio[count - 1] = 1f;
            return ratio;
        }
    }
}
