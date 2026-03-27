package io.github.dragon813.sesame.model.task.antForest;

import android.annotation.SuppressLint;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONArray;
import org.json.JSONObject;
import io.github.dragon813.sesame.util.Status;
import io.github.dragon813.sesame.util.Log;
import io.github.dragon813.sesame.util.MessageUtil;

/**
 * 6秒拼手速打地鼠
 * 整合版本：适配最新 RPC 定义
 */
public class WhackMole {
    private static final String TAG = "WhackMole";
    private static final String SOURCE = "senlinguangchangdadishu";
    private static final String EXEC_FLAG = "forest::whackMole::executed";
    
    private static volatile int totalGames = 5;
    private static volatile int moleCount = 15; // 兼容模式默认击打数
    private static final long GAME_DURATION_MS = 12000L;
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            Runtime.getRuntime().availableProcessors() * 2,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            r -> new Thread(r, "WhackMole-Worker")
    );
    private static final AtomicLong startTime = new AtomicLong(0);
    private static volatile boolean isRunning = false;
    
    /**
     * 运行模式
     */
    public enum Mode {
        COMPATIBLE,  // 兼容模式 (对应 old系列 RPC)
        AGGRESSIVE   // 激进模式 (对应 标准系列 RPC)
    }
    
    /**
     * 游戏会话信息
     */
    public static class GameSession {
        private final String token;
        private final int roundNumber;
        
        public GameSession(String token, int roundNumber) {
            this.token = token;
            this.roundNumber = roundNumber;
        }
        
        public String getToken() {
            return token;
        }
        
        public int getRoundNumber() {
            return roundNumber;
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
     * 游戏间隔计算器
     */
    private static class GameIntervalCalculator {
        public static long calculateDynamicInterval(long totalDuration, int totalGames) {
            return totalDuration / (totalGames * 2);
        }
        
        public static long calculateNextDelay(long dynamicInterval, int currentRound, int totalGames, long remainingTime) {
            long baseDelay = dynamicInterval * (currentRound % 2 + 1);
            return Math.max(100L, Math.min(baseDelay, remainingTime / (totalGames - currentRound + 1)));
        }
    }
    
    /**
     * 设置总游戏局数
     */
    public static void setTotalGames(int games) {
        totalGames = games;
    }
    
    /**
     * 设置击打地鼠数量
     */
    public static void setMoleCount(int count) {
        moleCount = count;
    }
    
    /**
     * 异步启动打地鼠
     */
    public static void start(Mode mode) {
        EXECUTOR.submit(() -> startSuspend(mode));
    }
    
    /**
     * 同步启动打地鼠（对应Kotlin suspend函数）
     */
    public static void startSuspend(Mode mode) {
        if (isRunning) {
            Log.record("正在运行⏭️打地鼠游戏中，跳过重复启动");
            return;
        }
        isRunning = true;
        
        try {
            switch (mode) {
                case COMPATIBLE:
                    runCompatibleMode();
                    break;
                case AGGRESSIVE:
                    runAggressiveMode();
                    break;
            }
            Status.flagToday(EXEC_FLAG);
        } catch (Exception e) {
            Log.printStackTrace("打地鼠异常:", e);
        } finally {
            isRunning = false;
            Log.record("运行状态🎮打地鼠已重置");
        }
    }
    
    // ================= [ 兼容模式：对应 old 系列 RPC ] =================
    private static void runCompatibleMode() {
        try {
            long startTs = System.currentTimeMillis();
            
            // 1. 开始游戏 (使用 oldstartWhackMole)
            String startRespStr = AntForestRpcCall.oldstartWhackMole(SOURCE);
            JSONObject response = new JSONObject(startRespStr);
            if (!response.optBoolean("success")) {
                Log.record(response.optString("resultDesc", "开始失败"));
                return;
            }
            JSONArray moleInfoArray = response.optJSONArray("moleInfo");
            String token = response.optString("token");
            if (moleInfoArray == null || moleInfoArray.length() == 0 || token.isEmpty()) {
                return;
            }
            
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
            
            // 2. 打有能量球的地鼠 (使用 oldwhackMole)
            int hitCount = 0;
            Random random = new Random();
            for (Long moleId : bubbleMoleIds) {
                try {
                    String whackRespStr = AntForestRpcCall.oldwhackMole(moleId, token, SOURCE);
                    JSONObject whackResp = new JSONObject(whackRespStr);
                    if (whackResp.optBoolean("success")) {
                        int energy = whackResp.optInt("energyAmount", 0);
                        hitCount++;
                        Log.forest("森林能量⚡️[兼容打地鼠:" + moleId + "+" + energy + "g]");
                        if (hitCount < bubbleMoleIds.size()) {
                            Thread.sleep(100 + random.nextInt(201)); // 100-300ms 随机延迟
                        }
                    }
                } catch (Throwable t) {
                    // 忽略单个击打异常
                }
            }
            
            // 3. 计算剩余 ID 并结算 (使用 oldsettlementWhackMole)
            List<String> remainingIds = new ArrayList<>();
            for (Long moleId : allMoleIds) {
                if (!bubbleMoleIds.contains(moleId)) {
                    remainingIds.add(String.valueOf(moleId));
                    if (remainingIds.size() >= moleCount) {
                        break; // 限制击打数量
                    }
                }
            }
            
            // 等待至接近6秒时长
            long elapsedTime = System.currentTimeMillis() - startTs;
            long sleepTime = Math.max(0L, 6000L - elapsedTime - 200L);
            if (sleepTime > 0) {
                Thread.sleep(sleepTime);
            }
            
            // 执行结算
            String settleRespStr = AntForestRpcCall.oldsettlementWhackMole(token, remainingIds, SOURCE);
            JSONObject settleResp = new JSONObject(settleRespStr);
            if (MessageUtil.checkSuccess(TAG, settleResp)) {
                int total = settleResp.optInt("totalEnergy", 0);
                Log.forest("森林能量⚡️[兼容模式完成(打" + (remainingIds.size() + hitCount) + "个)总能量+" + total + "g]");
            }
        } catch (Throwable t) {
            Log.record("兼容模式出错:" + (t.getMessage() != null ? t.getMessage() : "未知错误"));
        }
    }
    
    // ================= [ 激进模式：对应 标准系列 RPC ] =================
    @SuppressLint("DefaultLocale")
    private static void runAggressiveMode() {
        startTime.set(System.currentTimeMillis());
        long dynamicInterval = GameIntervalCalculator.calculateDynamicInterval(GAME_DURATION_MS, totalGames);
        
        List<GameSession> sessions = new ArrayList<>();
        try {
            // 批量启动多局游戏
            for (int roundNum = 1; roundNum <= totalGames; roundNum++) {
                GameSession session = startSingleRound(roundNum);
                if (session != null) {
                    sessions.add(session);
                }
                
                // 局间延迟
                if (roundNum < totalGames) {
                    long remaining = GAME_DURATION_MS - (System.currentTimeMillis() - startTime.get());
                    long delay = GameIntervalCalculator.calculateNextDelay(dynamicInterval, roundNum, totalGames, remaining);
                    Thread.sleep(delay);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            Log.printStackTrace("激进模式启动轮次异常:", e);
        }
        
        // 等待结算窗口
        long waitTime = Math.max(0L, GAME_DURATION_MS - (System.currentTimeMillis() - startTime.get()));
        try {
            if (waitTime > 0) {
                Thread.sleep(waitTime);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 批量结算所有有效局
        int totalEnergy = 0;
        for (GameSession session : sessions) {
            try {
                Thread.sleep(200); // 结算间隔
                totalEnergy += settleStandardRound(session);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.printStackTrace("结算第" + session.getRoundNumber() + "局异常:", e);
            }
        }
        Log.forest("森林能量⚡️[激进模式" + sessions.size() + "局#总计" + totalEnergy + "g]");
    }
    
    /**
     * 启动单局游戏（激进模式）
     */
    private static GameSession startSingleRound(int round) {
        try {
            String startRespStr = AntForestRpcCall.startWhackMole();
            JSONObject startResp = new JSONObject(startRespStr);
            if (!MessageUtil.checkSuccess(TAG, startResp)) {
                return null;
            }
            
            // 检查今日是否可玩
            if (!startResp.optBoolean("canPlayToday", true)) {
                Status.flagToday(EXEC_FLAG);
                throw new InterruptedException("今日打地鼠次数已达上限");
            }
            
            String token = startResp.optString("token");
            //Toast.show("打地鼠 第" + round + "局启动\nToken: " + token);
            return new GameSession(token, round);
        } catch (Exception e) {
            Log.printStackTrace("启动第" + round + "局异常:", e);
            return null;
        }
    }
    
    /**
     * 结算单局游戏（激进模式）
     */
    private static int settleStandardRound(GameSession session) {
        try {
            String respStr = AntForestRpcCall.settlementWhackMole(session.getToken());
            JSONObject resp = new JSONObject(respStr);
            if (MessageUtil.checkSuccess(TAG, resp)) {
                return resp.optInt("totalEnergy", 0);
            }
        } catch (Exception e) {
            Log.printStackTrace("结算第" + session.getRoundNumber() + "局失败:", e);
        }
        return 0;
    }
    
    /**
     * 关闭线程池（建议在应用退出时调用）
     */
    public static void shutdown() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}