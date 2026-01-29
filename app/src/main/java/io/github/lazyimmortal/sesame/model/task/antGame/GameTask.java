package io.github.lazyimmortal.sesame.model.task.antGame;

import io.github.lazyimmortal.sesame.hook.AlipayMiniMarkHelper;
import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.hook.AuthCodeHelper;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * 游戏任务上报工具类
 * 对应原Kotlin的GameTask枚举类
 */
public enum GameTask {
    Orchard_ncscc("农场上车车", "2060170000356601", "zfb_ncscc", "ncscc_game_kaiche_every_10", "nongchangleyuan", "1.0.2", 2),
    Farm_ddply("对对碰乐园", "2021004149679303", "zfb_ddply", "ddply_game_xiaochu_every_5", "zhuangyuan", "1.0.14", 2),
    Forest_slxcc("森林小车车", "2060170000363691", "zfb_slxcc", "slxcc_game_kaiche_every_10", "lianyun_senlin_leyuan", "1.0.1", 3),
    Forest_sljyd("森林救援队(能量雨)", "2021005113684028", "zfb_sljydx", "sljyd_game_xiaochu_every_10", "lianyun_senlin_leyuan", "1.0.1", 3);

    private final String title;
    private final String appId;
    private final String gid;
    private final String action;
    private final String channel;
    private final String version;
    private final int requestsPerEgg; // 完成1个🥚要多少次 为了防止网络崩溃 多加1次
    private String cachedToken; // 缓存登录Token

    /**
     * 枚举构造方法
     */
    GameTask(String title, String appId, String gid, String action, String channel, String version, int requestsPerEgg) {
        this.title = title;
        this.appId = appId;
        this.gid = gid;
        this.action = action;
        this.channel = channel;
        this.version = version;
        this.requestsPerEgg = requestsPerEgg;
    }

    /**
     * 第一步：登录获取 Token 并缓存
     */
    private String login() {
        try {
            String authCode = AuthCodeHelper.getAuthCode(appId);
            String mark = AlipayMiniMarkHelper.getAlipayMiniMark(appId, version);
            String reqId = System.currentTimeMillis() + "_" + new Random().nextInt(350) + 1;

            JSONObject bodyJson = new JSONObject();
            bodyJson.put("v", version);
            bodyJson.put("code", authCode);
            bodyJson.put("pf", "zfb");
            bodyJson.put("reqId", reqId);
            bodyJson.put("gid", gid);
            bodyJson.put("version", version);
            String body = bodyJson.toString();

            // 建立HTTP连接
            URL url = new URL("https://gamesapi2.aslk2018.com/v2/game/login");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("alipayMiniMark", mark);
            conn.setRequestProperty("User-Agent", getDynamicUA());
            conn.setRequestProperty("x-release-type", "ONLINE");

            // 写入请求体
            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(body);
            }

            // 处理响应（包含错误流）
            int respCode = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    respCode >= 200 && respCode <= 299 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8
            ));
            StringBuilder responseText = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseText.append(line);
            }
            reader.close();
            conn.disconnect();

            // 解析响应JSON
            JSONObject resJson = new JSONObject(responseText.toString());
            if (resJson.optInt("code") == 1) {
                JSONObject data = resJson.optJSONObject("data");
                if (data != null) {
                    this.cachedToken = data.optString("token");
                    Log.record("登录成功✅Token 已获取");
                    return this.cachedToken;
                }
            } else {
                Log.error("❌ 登录接口报错 (Code " + respCode + "): " + responseText);
            }
        } catch (Exception e) {
            Log.error("🚨 登录过程抛出异常: " + e.getMessage());
        }
        return null;
    }

    /**
     * 外部调用：执行上报任务
     * @param eggCount 目标蛋数量
     */
    public void report(String gameType,int eggCount) {
        int totalNeeded = eggCount * (this.requestsPerEgg + 1); // 多1次确保网络请求不会错误
        new Thread(() -> {
            this.cachedToken = login();
            if (this.cachedToken == null || this.cachedToken.isEmpty()) {
                 Log.error("⚠️ 无法获取有效的 Token，放弃上报任务");
                return;
            }

            Log.record("开始执行🚀"+gameType+"游戏任务：目标 " + eggCount + " 个蛋，需请求 " + totalNeeded + " 次");
            for (int i = 1; i <= totalNeeded; i++) {
                if (!executeSingleReport(gameType,i, totalNeeded)) {
                    // 具体的错误原因已在 executeSingleReport 中详细输出
                    break;
                }
                if (i < totalNeeded) {
                    try {
                        Thread.sleep(new Random().nextInt(2001) + 1000); // 1000-3000ms随机休眠
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            Log.record("任务流程🏁运行结束");
        }).start();
    }

    /**
     * 执行单次上报请求
     * @param current 当前请求次数
     * @param total 总请求次数
     * @return 是否上报成功
     */
    private boolean executeSingleReport(String gameType,int current, int total) {
        try {
            String mark = AlipayMiniMarkHelper.getAlipayMiniMark(appId, version);
            String reqId = System.currentTimeMillis() + "_" + (new Random().nextInt(90) + 10); // 10-99随机数

            // 构建请求体
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("v", version);
            bodyJson.put("version", version);
            bodyJson.put("reqId", reqId);
            bodyJson.put("gid", gid);
            bodyJson.put("action_code", action);
            bodyJson.put("action_finish_channel", channel);
            String body = bodyJson.toString();

            // 建立HTTP连接
            URL url = new URL("https://gamesapi2.aslk2018.com/v2/zfb/taskReport");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("authorization", this.cachedToken);
            conn.setRequestProperty("alipayMiniMark", mark);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", getDynamicUA());
            conn.setRequestProperty("x-release-type", "ONLINE");
            conn.setRequestProperty("referer", "https://" + appId + ".hybrid.alipay-eco.com/" + appId + "/" + version + "/index.html");

            // 写入请求体
            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(body);
            }

            // 处理响应
            int respCode = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    respCode >= 200 && respCode <= 299 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8
            ));
            StringBuilder responseText = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseText.append(line);
            }
            reader.close();
            conn.disconnect();

            // 解析响应
            JSONObject resJson = new JSONObject(responseText.toString());
            if (resJson.optInt("code") == 1) {
                if (current % this.requestsPerEgg == 0) {
                    Log.other("游戏进度📈"+ gameType +"[" + current + "/" + total + "](达成" + (current/this.requestsPerEgg) + "个)");
                }
                return true;
            } else {
                Log.error("⚠️ 第 " + current + " 次上报业务失败 (HTTP " + respCode + "): " + responseText);
                return false;
            }
        } catch (IOException e) {
            Log.error("🚨 第 " + current + " 次请求发生网络崩溃:"+ e);
            return false;
        } catch (Exception e) {
            Log.error("🚨 第 " + current + " 次请求发生异常:"+ e);
            return false;
        }
    }

    /**
     * 获取动态User-Agent
     * @return 拼接后的UA字符串
     */
    private String getDynamicUA() {
        String systemUa = System.getProperty("http.agent");
        if (systemUa == null || systemUa.isEmpty()) {
            systemUa = "Mozilla/5.0 (Linux; Android 11)";
        }
        String alipayVer = String.valueOf(ApplicationHook.getAlipayVersion());
        return systemUa + " NebulaSDK/1.8.100112 Nebula AliApp(AP/" + alipayVer + ") AlipayClient/" + alipayVer;
    }
}