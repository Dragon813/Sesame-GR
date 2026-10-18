package io.github.lazyimmortal.sesame.model.task.antForest;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * 森林无纸阅读 HTTP 客户端: 直连 https://m.zhangwenwh.com/api (小程序自有网关, 不走支付宝RPC)
 */
public final class ReadForestRpcCall {
    private static final String APP_ID = "2021003114652763";
    private static final String DEFAULT_VERSION = "0.2.2410251510.53";
    private static final String HOST = "https://m.zhangwenwh.com/api";
    private static final Random RANDOM = new Random();
    private static volatile String currentMiniMark = "";

    private ReadForestRpcCall() {
    }

    public static void setMiniMark(String str) {
        currentMiniMark = str == null ? "" : str;
    }

    /* 登录: 用authCode换取token */
    public static String login(String authCode, String miniMark, String version) throws Exception {
        setMiniMark(miniMark);
        JSONObject body = new JSONObject();
        body.put("auth_code", authCode);
        body.put("app_id", APP_ID);
        body.put("cid", "7001");
        return post("/authorization", body, null, getReferer(version, null, null));
    }

    /* 首页书城: 获取免费书列表 */
    public static String getIndex(String token, String version) throws Exception {
        return get("/index/index?type=1", token, getReferer(version, null, null));
    }

    /* 章节内容: chapterId为空取第一章, 否则取下一章 */
    public static String getChapter(String bookId, String chapterId, String token, String version) throws Exception {
        String path;
        if (chapterId == null || chapterId.length() == 0) {
            path = "/book/chapter?book_id=" + bookId;
        } else {
            path = "/book/chapter?book_id=" + bookId + "&chapter_id=" + chapterId;
        }
        return get(path, token, getReferer(version, bookId, chapterId));
    }

    /* 查询阅读绿色能量进度 current/total */
    public static String checkEnergy(String token, String version, String bookId, String chapterId) throws Exception {
        return get("/user/forestPro", token, getReferer(version, bookId, chapterId));
    }

    /* 上报阅读时长(秒) */
    public static String updateReadingTime(String bookId, String chapterId, int seconds, String token, String version) throws Exception {
        JSONObject body = new JSONObject();
        body.put("time", seconds);
        putChapterId(body, chapterId);
        body.put("book_id", bookId);
        return post("/user/updateWel", body, token, getReferer(version, bookId, chapterId));
    }

    /* 上报最近阅读(书名/章节名) */
    public static String setReadRecently(String bookId, String chapterId, String chapterName, String token, String version) throws Exception {
        JSONObject body = new JSONObject();
        body.put("book_id", bookId);
        putChapterId(body, chapterId);
        body.put("chapter_name", chapterName);
        return post("/book/set_read_recently", body, token, getReferer(version, bookId, chapterId));
    }

    private static void putChapterId(JSONObject body, String chapterId) throws Exception {
        try {
            body.put("chapter_id", Long.parseLong(chapterId));
        } catch (Exception unused) {
            body.put("chapter_id", chapterId);
        }
    }

    /* 构造referer: 模拟小程序页面路径, 章节页带bid/cid */
    public static String getReferer(String version, String bookId, String chapterId) {
        if (version == null || version.length() == 0) {
            version = DEFAULT_VERSION;
        }
        if (version.startsWith("http")) {
            return version;
        }
        String base = "https://" + APP_ID + ".hybrid.alipay-eco.com/" + APP_ID + "/" + version + "/index.html";
        if (bookId != null && bookId.length() != 0 && chapterId != null && chapterId.length() != 0) {
            return base + "#moduleA/pages/chapter/index?__appxPageId=" + (RANDOM.nextInt(20) + 1) + "&bid=" + bookId + "&cid=" + chapterId;
        }
        if (bookId != null && bookId.length() != 0) {
            return base + "#moduleA/pages/chapter/index?__appxPageId=" + (RANDOM.nextInt(20) + 1) + "&bid=" + bookId;
        }
        return base + "#pages/index/index";
    }

    private static String getBaseUserAgent() {
        return "Mozilla/5.0 (Linux; Android 11; M2006J10C Build/RP1A.200720.011; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/105.0.5195.148 MYWeb/0.11.0.241014163223 UWS/3.22.2.9999 UCBS/3.22.2.9999_220000000000 Mobile Safari/537.36 NebulaSDK/1.8.100112 Nebula AlipayDefined(nt:WIFI,ws:393|0|2.75) AliApp(AP/10.6.58.8000) AlipayClient/10.6.58.8000 Language/zh-Hans isConcaveScreen/false Region/CNAriver/1.0.0 ChannelId(" + (RANDOM.nextInt(10) + 1) + ")";
    }

    private static Map<String, String> getHeaders(String token, String referer) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("alipayminimark", currentMiniMark == null ? "" : currentMiniMark);
        headers.put("content-type", "application/json;charset=UTF-8");
        headers.put("referer", referer == null ? "" : referer);
        headers.put("user-agent", getBaseUserAgent());
        headers.put("x-release-type", "ONLINE");
        if (token != null) {
            headers.put("authorization", "Bearer " + token);
        }
        return headers;
    }

    private static String get(String path, String token, String referer) throws Exception {
        return request("GET", path, null, token, referer);
    }

    private static String post(String path, JSONObject body, String token, String referer) throws Exception {
        return request("POST", path, body == null ? "{}" : body.toString(), token, referer);
    }

    private static String request(String method, String path, String body, String token, String referer) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(HOST + path).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod(method);
            conn.setUseCaches(false);
            for (Map.Entry<String, String> entry : getHeaders(token, referer).entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
            if ("POST".equals(method)) {
                byte[] bytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(bytes.length);
                OutputStream os = conn.getOutputStream();
                os.write(bytes);
                os.close();
            }
            InputStream is = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (is == null) {
                return "";
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            is.close();
            return baos.toString("UTF-8");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
