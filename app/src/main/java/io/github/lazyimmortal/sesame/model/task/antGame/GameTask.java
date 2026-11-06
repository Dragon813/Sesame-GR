package io.github.lazyimmortal.sesame.model.task.antGame;

import static io.github.lazyimmortal.sesame.hook.AlipayMiniMarkHelper.getAlipayMiniMark;

import io.github.lazyimmortal.sesame.hook.AlipayMiniMarkHelper;
import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.hook.AuthCodeHelper;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

import android.content.Intent;
import android.net.Uri;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 游戏任务上报工具类
 * 对应原Kotlin的GameTask枚举类
 */
public enum GameTask {

    Orchard_ncscc("农场上车车", "2060170000356601", "zfb_ncscc", "ncscc_game_kaiche_every_10", "nongchangleyuan", "1.0.2", 2, SdkType.GAMESAPI2),
    Farm_ddply("对对碰乐园", "2021004149679303", "zfb_ddply", "ddply_game_xiaochu_every_5", "zhuangyuan", "1.0.14", 2, SdkType.GAMESAPI2),
    Forest_slxcc("森林小车车", "2060170000363691", "zfb_slxcc", "slxcc_game_kaiche_every_10", "lianyun_senlin_leyuan", "1.0.1", 3, SdkType.GAMESAPI2),
    Forest_sljyd("森林救援队(能量雨)", "2021005113684028", "zfb_sljydx", "sljyd_game_xiaochu_every_10", "lianyun_senlin_leyuan", "1.0.1", 3, SdkType.GAMESAPI2),
    // 金豆乐园：复用农场/庄园同款游戏，channel=goldenbean，用于刷金豆砸蛋机会
    GoldenBean_ncscc("农场上车车", "2060170000356601", "zfb_ncscc", "ncscc_game_kaiche_every_10", "goldenbean", "1.0.2", 2, SdkType.GAMESAPI2),
    GoldenBean_ddply("对对碰乐园", "2021004149679303", "zfb_ddply", "ddply_game_xiaochu_every_5", "goldenbean", "1.0.14", 4, SdkType.GAMESAPI2),
    GoldenBean_slxcc("森林小车车", "2060170000363691", "zfb_slxcc", "slxcc_game_kaiche_every_10", "goldenbean", "1.0.1", 2, SdkType.GAMESAPI2),
    GoldenBean_sljyd("森林救援队", "2021005113684028", "zfb_sljydx", "sljyd_game_xiaochu_every_10", "goldenbean", "1.0.1", 3, SdkType.GAMESAPI2),

    // ===== 以下游戏：统一走 gamesapi2 平台的 login/taskReport 方式提交 =====
    // 三国冰河时代（任务: 招募3次得2次机会）gid 取自 SDK 域名 sanguo2
    Forest_sgbhsd("三国冰河时代", "2021004173661702", "sanguo2", "SGBHSD_QDRW_CCL_new", "lianyun_senlin_leyuan", "1.10.0", 3, SdkType.GAMESAPI2),
    // 解忧小食光（任务: 招待顾客5次得2次机会）
    Forest_jyxsg("解忧小食光", "2021004147641532", "jyxsg", "FOREST_NORMAL_DRAW_JYXSG_ZH", "lianyun_senlin_leyuan", "1.36.1", 5, SdkType.GAMESAPI2),
    // 主宰世界炼器副本（任务: 通关1次副本得2次机会）
    Forest_zzsj("主宰世界炼器副本", "2021004199682148", "zzsj", "FOREST_ACTIVITY_DRAW_ZZSJ_ZH", "lianyun_senlin_leyuan", "0.0.510", 1, SdkType.GAMESAPI2);

    // 不良人：破局 —— 抓包确认 action_code=blrpj_game_zhaomu_every_10（每10次1个），channel=lianyun_senlin_leyuan
    // 该游戏抓包走 huanyou7 平台，gamesapi2 侧的 appId/gid/version 未知，确认后取消注释
    //Forest_blrpj("不良人：破局", "TODO_APPID", "zfb_blrpj", "blrpj_game_zhaomu_every_10", "lianyun_senlin_leyuan", "TODO_VERSION", 10, SdkType.GAMESAPI2);

    // 37SDK游戏（区服:少年时6区，37平台gid=1021669）—— 抓包走 8rn4u.com，gamesapi2 参数未知
    //Forest_37xs("少年时", "TODO_APPID", "TODO_GID", "TODO_ACTION", "lianyun_senlin_leyuan", "TODO_VERSION", 3, SdkType.GAMESAPI2);

    //Farm_lhs("灵画师", "2021005122634802", "lhs", "lhs", "lianyun_zhuangyuan_v2", "0.0.89", 3, SdkType.GAMESAPI2);

    /**
     * SDK 平台类型
     */
    public enum SdkType {
        GAMESAPI2,  // 统一平台 gamesapi2.aslk2018.com（现有 login/taskReport 机制）
        BJXUEJING,  // 三国冰河时代 bjxuejing.cn
        HAOTIANHUYU,// 解忧小食光 haotianhuyu.com + chuxinhd.com
        AIYSM       // 主宰世界炼器副本 4399/aiysm.com
    }

    private final String title;
    private final String appId;
    private final String gid;
    private final String action;
    private final String channel;
    private final String version;
    private final int requestsPerEgg; // 完成1个🥚要多少次 为了防止网络崩溃 多加1次
    private final SdkType sdkType;
    private String cachedToken; // 缓存登录Token

    /**
     * 枚举构造方法
     */
    GameTask(String title, String appId, String gid, String action, String channel, String version, int requestsPerEgg, SdkType sdkType) {
        this.title = title;
        this.appId = appId;
        this.gid = gid;
        this.action = action;
        this.channel = channel;
        this.version = version;
        this.requestsPerEgg = requestsPerEgg;
        this.sdkType = sdkType;
    }

    public SdkType getSdkType() {
        return sdkType;
    }

    public String getTitle() {
        return title;
    }

    public String getAction() {
        return action;
    }

    /**
     * 第一步：登录获取 Token 并缓存
     * 根据 sdkType 分发到不同的登录实现
     */
    private String login() {
        switch (sdkType) {
            case BJXUEJING:
                return loginBjxuejing();
            case HAOTIANHUYU:
                return loginHaotianhuyu();
            case AIYSM:
                return loginAiySM();
            case GAMESAPI2:
            default:
                return loginGamesApi2();
        }
    }

    /**
     * gamesapi2 统一平台登录（原有逻辑）
     */
    private String loginGamesApi2() {
        try {
            String authCode = AuthCodeHelper.getAuthCode(appId);
            String mark = getAlipayMiniMark(appId, version);
            String reqId = System.currentTimeMillis() + "_" + new Random().nextInt(350) + 1;

            JSONObject bodyJson = new JSONObject();
            bodyJson.put("v", version);
            bodyJson.put("code", authCode);
            bodyJson.put("pf", "zfb");
            bodyJson.put("reqId", reqId);
            bodyJson.put("gid", gid);
            bodyJson.put("version", version);
            String body = bodyJson.toString();

            //Log.other("login 请求体 -> " + body);

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

            //Log.other("login 响应 -> HTTP " + respCode + " " + responseText);

            // 解析响应JSON
            JSONObject resJson = new JSONObject(responseText.toString());
            if (resJson.optInt("code") == 1) {
                JSONObject data = resJson.optJSONObject("data");
                if (data != null) {
                    this.cachedToken = data.optString("token");
                    Log.record("登录成功✅Token已获取");
                    return this.cachedToken;
                }
            } else {
                Log.error("登录接口❌报错(Code" + respCode + "):" + responseText);
            }
        } catch (Exception e) {
            Log.error("登录过程🚨抛出异常:" + e.getMessage());
        }
        return null;
    }

    /**
     * 三国冰河时代登录（bjxuejing SDK）
     * POST https://138-sanguo2-sdk-online01.bjxuejing.cn/open/v6/user/LoginBySnsIdNoVerify
     * 需要 snsId（来自支付宝授权），返回 userId/token/jwttoken
     */
    private String loginBjxuejing() {
        try {
            // snsId 格式: ali.cn.minigame:{authCode}
            String authCode = AuthCodeHelper.getAuthCode(appId);
            String snsId = "ali.cn.minigame:" + authCode;
            String mark = getAlipayMiniMark(appId, version);

            JSONObject bodyJson = new JSONObject();
            bodyJson.put("snsId", snsId);
            String body = bodyJson.toString();

            URL url = new URL("https://138-sanguo2-sdk-online01.bjxuejing.cn/open/v6/user/LoginBySnsIdNoVerify");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("alipayMiniMark", mark);
            conn.setRequestProperty("User-Agent", getDynamicUA());

            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(body);
            }

            int respCode = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    respCode >= 200 && respCode <= 299 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8));
            StringBuilder responseText = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) responseText.append(line);
            reader.close();
            conn.disconnect();

            JSONObject resJson = new JSONObject(responseText.toString());
            if (resJson.optInt("code") == 200 || resJson.has("userId")) {
                JSONObject data = resJson.optJSONObject("data");
                if (data != null) {
                    this.cachedToken = data.optString("token");
                    Log.record("三国冰河时代登录成功✅");
                    return this.cachedToken;
                }
            }
            Log.error("三国冰河时代登录❌:" + responseText);
        } catch (Exception e) {
            Log.error("三国冰河时代登录🚨异常:" + e.getMessage());
        }
        return null;
    }

    /**
     * 解忧小食光登录（haotianhuyu SDK）
     * POST https://sdkapi.haotianhuyu.com/api/user/login  {appId, timestamp}
     * 返回 token/user_id，然后用 token 调游戏服 login 获取 WS 地址
     */
    private String loginHaotianhuyu() {
        try {
            String mark = getAlipayMiniMark(appId, version);
            // haotianhuyu SDK 的 appId 是固定的 "250820649130"
            String sdkAppId = "250820649130";

            JSONObject bodyJson = new JSONObject();
            bodyJson.put("appId", sdkAppId);
            bodyJson.put("timestamp", System.currentTimeMillis() / 1000);
            String body = bodyJson.toString();

            URL url = new URL("https://sdkapi.haotianhuyu.com/api/user/login");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("alipayMiniMark", mark);
            conn.setRequestProperty("User-Agent", getDynamicUA());

            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(body);
            }

            int respCode = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    respCode >= 200 && respCode <= 299 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8));
            StringBuilder responseText = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) responseText.append(line);
            reader.close();
            conn.disconnect();

            JSONObject resJson = new JSONObject(responseText.toString());
            JSONObject data = resJson.optJSONObject("data");
            if (data != null && data.has("token")) {
                this.cachedToken = data.optString("token");
                Log.record("解忧小食光SDK登录成功✅ token=" + this.cachedToken.substring(0, 8) + "...");
                return this.cachedToken;
            }
            Log.error("解忧小食光SDK登录❌:" + responseText);
        } catch (Exception e) {
            Log.error("解忧小食光SDK登录🚨异常:" + e.getMessage());
        }
        return null;
    }

    /**
     * 主宰世界炼器副本登录（4399/aiysm SDK）
     * GET https://fnapi.aiysm.com/sdk/api/login_zfb.php?ext={js_code:authCode,...}
     * 返回 openid/verifyToken/uid/username
     */
    private String loginAiySM() {
        try {
            String authCode = AuthCodeHelper.getAuthCode(appId);
            String mark = getAlipayMiniMark(appId, version);

            // 构造 ext 参数
            JSONObject extJson = new JSONObject();
            extJson.put("fngid", "1732090583435280");
            extJson.put("fnpid", "1478");
            extJson.put("type", "code2Session");
            extJson.put("js_code", authCode);
            extJson.put("os", "android");
            extJson.put("osid", "2");
            extJson.put("os_id", "2");
            extJson.put("deviceType", "Android");
            String ext = extJson.toString();

            String urlStr = "https://fnapi.aiysm.com/sdk/api/login_zfb.php?uid=&name=&ext="
                    + java.net.URLEncoder.encode(ext, "UTF-8");

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("alipayMiniMark", mark);
            conn.setRequestProperty("User-Agent", getDynamicUA());

            int respCode = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    respCode >= 200 && respCode <= 299 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8));
            StringBuilder responseText = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) responseText.append(line);
            reader.close();
            conn.disconnect();

            JSONObject resJson = new JSONObject(responseText.toString());
            if (resJson.optInt("code") == 1) {
                JSONObject content = resJson.optJSONObject("content");
                if (content != null) {
                    this.cachedToken = content.optString("verifyToken");
                    Log.record("主宰世界登录成功✅ uid=" + content.optString("uid"));
                    return this.cachedToken;
                }
            }
            Log.error("主宰世界登录❌:" + responseText);
        } catch (Exception e) {
            Log.error("主宰世界登录🚨异常:" + e.getMessage());
        }
        return null;
    }

    /**
     * 启动小程序（自动登录）
     * 通过 alipays://platformapi/startapp?appId=... 启动小程序
     * 小程序内部会自动使用支付宝授权凭证完成登录
     */
    public void launchMiniProgram() {
        try {
            String url = "alipays://platformapi/startapp?appId=" + appId
                    + "&query=channel%3D" + channel;
            Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ApplicationHook.getContext().startActivity(it);
            Log.record("启动小程序🚀" + title + " (appId=" + appId + ")");
        } catch (Exception e) {
            Log.error("启动小程序🚨失败:" + title + " " + e.getMessage());
        }
    }

    /**
     * 外部调用：执行上报任务
     * @param eggCount 目标蛋数量
     */
    public void report(String gameType,int eggCount) {
        // 非 gamesapi2 平台暂不支持直接上报，需实现自定义游戏操作
        if (sdkType != SdkType.GAMESAPI2) {
            Log.record("⚠️ " + title + " 使用 " + sdkType + " SDK，暂不支持直接 taskReport，"
                    + "需通过游戏内操作(WebSocket/HTTP)完成任务。当前仅支持启动小程序自动登录。");
            return;
        }
        int totalNeeded = eggCount * (this.requestsPerEgg + 1); // 多1次确保网络请求不会错误
        new Thread(() -> {
            this.cachedToken = login();
            if (this.cachedToken == null || this.cachedToken.isEmpty()) {
                 Log.error("无法获取⚠️有效的Token，放弃上报任务");
                return;
            }

            Log.record("开始执行🚀"+gameType+"游戏任务:目标" + eggCount + "个蛋，需请求" + totalNeeded + "次");
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
     * 外部调用：按指定精确次数执行上报任务（不做 eggCount 倍数换算）
     * 混合模式：登录走各自 SDK（login() 按 sdkType 分发），上报统一走 gamesapi2 taskReport
     * 用于森林抽抽乐等场景：任务要求几次就提交几次
     * @param gameType 游戏类型标识（用于日志区分）
     * @param times 需要提交的精确次数
     */
    public void reportExact(String gameType, int times) {
        if (times <= 0) {
            return;
        }
        new Thread(() -> {
            this.cachedToken = login();
            if (this.cachedToken == null || this.cachedToken.isEmpty()) {
                Log.error("无法获取⚠️有效的Token，放弃上报任务");
                return;
            }

            Log.record("开始执行🚀" + gameType + "游戏任务:精确提交" + times + "次(登录SDK=" + sdkType + ",上报=gamesapi2)");
            for (int i = 1; i <= times; i++) {
                if (!executeSingleReport(gameType, i, times)) {
                    break;
                }
                if (i < times) {
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
            String mark = getAlipayMiniMark(appId, version);
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

            //Log.other("taskReport 请求体 -> " + body);

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

            //Log.debug("taskReport 响应 -> HTTP " + respCode + " " + responseText);

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