package io.github.lazyimmortal.sesame.model.task.antForest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.TimeUtil;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

/**
 * 6秒拼手速打地鼠（多线程+间隔控制版）
 * 同时开启6局游戏，结算能量最高的一局
 *
 * @author Ghostxx
 * @date 2025/12/14
 */
public class WhackMole {
    private static final String TAG = WhackMole.class.getSimpleName();
    
    // 总游戏局数，同时开启3局游戏
    //private static int TOTAL_ROUNDS = 3;
    
    // 单个游戏任务的超时时间（秒），防止任务卡住
    private static final long TASK_TIMEOUT_SECONDS = 30;
    
    // 游戏会话信息
    private static class GameSession {
        final String token;
        final List<String> remainingMoleIds;
        final int whackedEnergy;
        final int roundNumber;
        
        GameSession(String token, List<String> remainingMoleIds, int whackedEnergy, int roundNumber) {
            this.token = token;
            this.remainingMoleIds = remainingMoleIds;
            this.whackedEnergy = whackedEnergy;
            this.roundNumber = roundNumber;
        }
    }
    
    /**
     * 开6局游戏打地鼠（并发执行）
     * 每局游戏独立计时，严格控制在6秒内完成
     */
    public static void startWhackMole(int WhackMoleRoundNum) {
        String source = "senlinguangchangdadishu";
        List<Future<GameSession>> futures = new ArrayList<>();
        
        // 创建自定义线程池
        ThreadPoolExecutor executor = new ThreadPoolExecutor(WhackMoleRoundNum, WhackMoleRoundNum, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "WhackMole-" + threadNumber.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
        
        try {
            // 1. 并发提交6局游戏任务
            Log.i(TAG, "启动" + WhackMoleRoundNum + "局打地鼠游戏（并发模式）");
            for (int round = 1; round <= WhackMoleRoundNum; round++) {
                final int currentRound = round;
                Callable<GameSession> task = () -> playRound(currentRound, source);
                futures.add(executor.submit(task));
            }
            
            // 2. 等待所有任务完成
            List<GameSession> sessions = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                Future<GameSession> future = futures.get(i);
                try {
                    GameSession session = future.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (session != null) {
                        sessions.add(session);
                        Log.record(String.format("第"+session.roundNumber+"局完成，打地鼠能量"+session.whackedEnergy+"g"));
                    }
                }
                catch (TimeoutException e) {
                    Log.i(TAG, "第" + (i + 1) + "局任务超时");
                    future.cancel(true);
                }
                catch (Exception e) {
                    Log.i(TAG, "第" + (i + 1) + "局任务异常: " + e.getMessage());
                }
            }
            
            if (sessions.isEmpty()) {
                Log.i(TAG, "没有成功进行任何游戏");
                return;
            }
            
            // 3. 找出能量最高的局
            GameSession bestSession = sessions.get(0);
            for (GameSession session : sessions) {
                if (session.whackedEnergy > bestSession.whackedEnergy) {
                    bestSession = session;
                }
            }
            
            Log.i(TAG, String.format("最佳局为第"+bestSession.roundNumber+"局，打地鼠能量"+bestSession.whackedEnergy+"g"));
            
            // 4. 结算能量最高的那一局
            settleBestRound(bestSession, source);
            
        }
        catch (Exception e) {
            Log.i(TAG, "whackMole 任务调度失败: " + e.getMessage());
            Log.printStackTrace(TAG, e);
        }
        finally {
            shutdownExecutor(executor);
        }
    }
    
    public static Boolean closeWhackMole() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.closeWhackMole());
            return MessageUtil.checkSuccess(TAG, jo);
        }
        catch (Throwable t) {
            Log.printStackTrace(t);
        }
        return false;
    }
    

    
    /**
     * 进行单局游戏（严格控制在6秒内）
     */
    private static GameSession playRound(int round, String source) {
        long startTime = System.currentTimeMillis();
        try {
            // 每局开始前随机小延迟，错开请求时间
            TimeUtil.sleep((long) (Math.random() * 300));
            Log.i(TAG, "第" + round + "局游戏开始");
            // 开始游戏
            JSONObject response = new JSONObject(AntForestRpcCall.startWhackMole(source));
            if (!response.optBoolean("success")) {
                Log.i(TAG, "第" + round + "局开始失败: " + response.optString("resultDesc", "未知错误"));
                return null;
            }
            JSONArray moleInfoArray = response.optJSONArray("moleInfo");
            if (moleInfoArray == null || moleInfoArray.length() == 0) {
                Log.i(TAG, "第" + round + "局没有地鼠信息");
                return null;
            }
            String token = response.optString("token");
            if (token.isEmpty()) {
                Log.i(TAG, "第" + round + "局未获取到游戏token");
                return null;
            }
            // 收集地鼠信息
            List<Long> allMoleIds = new ArrayList<>();
            List<Long> bubbleMoleIds = new ArrayList<>();
            for (int i = 0; i < moleInfoArray.length(); i++) {
                JSONObject mole = moleInfoArray.getJSONObject(i);
                long moleId = mole.getLong("id");
                allMoleIds.add(moleId);
                if (mole.has("bubbleId")) {
                    bubbleMoleIds.add(moleId);
                }
            }
            Log.i(TAG, "第" + round + "局发现" + bubbleMoleIds.size() + "个能量地鼠");
            
            // 打地鼠（带击打间隔）
            int totalEnergy = 0;
            int hitCount = 0;
            for (Long moleId : bubbleMoleIds) {
                try {
                    JSONObject whackResp = new JSONObject(AntForestRpcCall.whackMole(moleId, token, source));
                    if (whackResp.optBoolean("success")) {
                        int energy = whackResp.optInt("energyAmount", 0);
                        totalEnergy += energy;
                        hitCount++;
                        Log.i(TAG, "第" + round + "局击打地鼠" + moleId + " 能量+" + energy + "g");
                    }
                }
                catch (Exception e) {
                    Log.i(TAG, "第" + round + "局打地鼠异常 " + moleId + ": " + e.getMessage());
                }
            }
            
            // 计算剩余未打的地鼠
            List<String> remainingMoleIds = new ArrayList<>();
            for (Long moleId : allMoleIds) {
                if (!bubbleMoleIds.contains(moleId)) {
                    remainingMoleIds.add(String.valueOf(moleId));
                }
            }
            
            // 等待接近6秒总时长（严格控制在6秒内）
            long elapsedTime = System.currentTimeMillis() - startTime;
            long sleepTime = Math.max(0, 6000 - elapsedTime - 100); // 提前100ms结束，确保不超标
            if (sleepTime > 0) {
                TimeUtil.sleep(sleepTime);
            }
            
            // 打印该局能量信息到森林日志
            Log.forest("森林能量⚡️[6秒拼手速第" + round + "局 打地鼠能量+" + totalEnergy + "g]");
            Log.i(TAG, "第" + round + "局完成，总耗时" + (System.currentTimeMillis() - startTime) + "ms");
            
            // 返回会话信息
            return new GameSession(token, remainingMoleIds, totalEnergy, round);
            
        }
        catch (Exception e) {
            Log.i(TAG, "第" + round + "局游戏异常: " + e.getMessage());
            Log.printStackTrace(TAG, e);
            return null;
        }
    }
    
    /**
     * 结算能量最高的那一局
     */
    private static void settleBestRound(GameSession session, String source) {
        try {
            Log.i(TAG, "正在结算第" + session.roundNumber + "局游戏（能量最高）");
            JSONObject response = new JSONObject(AntForestRpcCall.settlementWhackMole(session.token, session.remainingMoleIds, source));
            if (MessageUtil.checkSuccess(TAG, response)) {
                int totalEnergy = response.optInt("totalEnergy", 0);
                int provideEnergy = response.optInt("provideDefaultEnergy", 0);
                int whackedEnergy = totalEnergy - provideEnergy;
                Log.forest("森林能量⚡️[6秒拼手速第" + session.roundNumber + "局结算" + " 打地鼠能量+" + whackedEnergy + "g" + " 默认奖励+" + provideEnergy + "g" + " 总能量+" + totalEnergy + "g#[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]");
            }
        }
        catch (Exception e) {
            Log.i(TAG, "结算第" + session.roundNumber + "局失败: " + e.getMessage());
            Log.printStackTrace(TAG, e);
        }
    }
    
    /**
     * 安全关闭线程池
     */
    private static void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
        catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
