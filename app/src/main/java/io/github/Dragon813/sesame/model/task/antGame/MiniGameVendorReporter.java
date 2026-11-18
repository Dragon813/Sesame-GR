package io.github.Dragon813.sesame.model.task.antGame;

import android.content.Context;
import android.os.Build;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import io.github.Dragon813.sesame.hook.AlipayMiniMarkHelper;
import io.github.Dragon813.sesame.hook.ApplicationHook;
import io.github.Dragon813.sesame.hook.AuthCodeHelper;
import io.github.Dragon813.sesame.hook.SimplePageManager;
import io.github.Dragon813.sesame.util.Log;
import io.github.Dragon813.sesame.util.MessageUtil;
import io.github.Dragon813.sesame.util.idMap.UserIdMap;

/**
 * 小程序游戏的「厂商侧登录 + 行为上报」适配器。
 *
 * <p>游戏中心的 {@code submitUserAction(enterGame)} / {@code submitEvent(GAME_FIRST_FRAME)}
 * 只能向支付宝上报「已进入游戏、已出首帧」，不会产生游戏内进度；服务端校验的
 * {@code action_code}（通关/消除/升级/道具…）必须由厂商服务器接收。
 * 本类按已验证协议的适配器表，对相应 SDK 族补上「登录 → 行为上报」。
 *
 * <p>各厂商协议实现：
 * <ul>
 *     <li>haligame：{@code get_openid.php} 换 {@code open_id} → {@code user_action_submit.php}</li>
 *     <li>dianwanshidai（你是真厉害等 12 款）：{@code user/login} 换 {@code openid} → {@code ZfbSop/behavior}</li>
 *     <li>yyxxgame：无需登录，直接带支付宝 UID 上报，{@code sign=md5(参数串+"undefined")}</li>
 *     <li>nebula737（一步两步）：{@code account/user/detail} 登录 → {@code behavior/report}，
 *         两者都要求 {@code md5(按 key 排序的 urlencode 参数串 + "&" + appKey)}</li>
 *     <li>cpchxapi（全民打螺丝等 4 款）：{@code auth/login-ali} 换 JWT → {@code analytics/ali-report}，
 *         上报头 {@code authorization: Bearer <token>}</li>
 *     <li>hnycgames（我的花园世界）：{@code Channel/login/yxtGame/...} 换 {@code yxtChannelUserId}
 *         → {@code channel/index/yxtMethod/reportaction/...}</li>
 *     <li>dianli100（农场对对碰等 3 款）：{@code farm/openid} 登录 → {@code farm/action/report}，
 *         每次动作都重新取 {@code authCode}</li>
 *     <li>d3games（森林环游记）：{@code api/login} 换 {@code uid+token} →
 *         {@code ali/incrementgameaction/submit}，签名 {@code md5(按 key 排序拼接 k=v（无分隔符，
 *         排除 ver/sign/空值/0 值）+ appKey)}</li>
 *     <li>tomato（时尚百货城）：{@code initDevices} → {@code merge/v3/login/inland/zfbMiniGame}
 *         换 {@code channelUserId} → {@code init/platform/event/push}，签名
 *         {@code md5(全部字段按 key 排序 k=v 用 & 连接 + secret)}</li>
 *     <li>aslk2018/gamesapi2（农场上车车等 33 款）：{@code /v2/game/login} 换 token →
 *         {@code /v2/zfb/taskReport}，与森林乐园每日鸡蛋任务同源，gid 取自静态适配表</li>
 *     <li>raiyantway（占城大师，MiniGameServer 框架）：{@code minigame/login.do} 换 openId →
 *         {@code alipay/reportGameAction}，上报体 ENCODE_DATA 为 XOR+base64 加密</li>
 * </ul>
 *
 * <p>已实现协议的 SDK 族：haligame、dianwanshidai、cpchxapi、yyxxgame、nebula737、hnycgames、
 * dianli100、d3games、tomato、aslk2018、raiyantway、flourish、devourad、microfun、qufang、
 * qmhd87、lumosfun、mmhygame、trueplay、igame58、soulgame、qxygame；
 * 族内差异参数（{@code bgid / gcid / oid / appKey}）从 {@link MiniGameTaskTable} 取；
 * d3games 其余游戏的密钥虽有静态表但算法只验证过森林环游记，暂不启用；
 * qmhd87 请求体按 GameTask.js 使用 eData 整包加密，请求头按 {@code Sa/W} 补 accept/lang/plat。
 */
public final class MiniGameVendorReporter {

    /** 游戏任务频道（“玩游戏得/去玩”类任务的回传频道）。 */
    private static final String GAME_TASK_CHANNEL = "zmxyzms_game_task";

    /** 森林乐园频道（青云南/一步两步）。 */
    private static final String FOREST_CHANNEL = "lianyun_senlin_leyuan";

    /** 庄园频道（农场对对碰等 dianli100 游戏）。 */
    private static final String ZHUANGYUAN_CHANNEL = "zhuangyuan";

    /** 农场乐园频道（占城大师等 raiyantway 游戏，actionFinishChannel=nongchangleyuan）。 */
    private static final String FARM_CHANNEL = "nongchangleyuan";

    private static final Random RANDOM = new Random();

    private enum Vendor {
        HALIGAME, DIANWANSHIDAI, YYXX, NEBULA, CPCHX, HNYC, DIANLI100, D3GAMES, TOMATO, GAMESAPI2,
        RAYIANTWAY, FLOURISH, DEVOURAD, MICROFUN, QUFANG, QMHD87, LUMOSFUN, MMHYGAME,
        TRUEPLAY, IGAME58, SOULGAME, QXYGAME, BKGAME, TUYOO, YXMNYS, MIHUANGAME, SHRYOU
    }

    /** d3games 仅在同时具备 appKey 与 gameId 的游戏上启用。 */
    private static boolean d3gamesEnabled(String appId) {
        return !MiniGameTaskTable.param(appId, "appKey").isEmpty()
                && !MiniGameTaskTable.param(appId, "gameId").isEmpty();
    }

    /** 单个游戏的厂商适配信息。 */
    private static final class Meta {
        final String name;
        /** 版本号统一取自静态适配表 */
        final String version;
        final Vendor vendor;
        /** 非 final：以 RPC 任务列表下发的 channel 为准，metaOf 只给族级默认 */
        String channel;
        /** {关键词, actionCode}；关键词为空表示默认项。 */
        final List<String[]> actions;
        final String oid;
        final String gcid;
        final String appKey;
        final String nebulaAppId;
        final String companyId;
        final String channelId;

        Meta(String name, String version, Vendor vendor, String channel, List<String[]> actions,
             String oid, String gcid, String appKey, String nebulaAppId, String companyId, String channelId) {
            this.name = name;
            this.version = version;
            this.vendor = vendor;
            this.channel = channel;
            this.actions = actions;
            this.oid = oid;
            this.gcid = gcid;
            this.appKey = appKey;
            this.nebulaAppId = nebulaAppId;
            this.companyId = companyId;
            this.channelId = channelId;
        }
    }

    /**
     * SDK 族 → 已实现协议的厂商。只有验证过协议的族才会补上报；
     * 其余 SDK 族虽然也在 {@link MiniGameTaskTable} 里，但 {@link #supports} 返回 false。
     */
    private static final Map<String, Vendor> SDK_VENDORS = new HashMap<>();

    /** 标题关键词覆盖表由静态表 {@code alias} 参数提供，见 {@link MiniGameTaskTable#aliases}。 */

    static {
        // dianwanshidai 族：12 款共用 platformv2 协议，仅 bgid 不同
        SDK_VENDORS.put("haligame", Vendor.HALIGAME);
        SDK_VENDORS.put("dianwanshidai", Vendor.DIANWANSHIDAI);
        // yyxxgame 族：2 款共用 fumodasapi 协议，gcid/oid 不同
        SDK_VENDORS.put("yyxxgame", Vendor.YYXX);
        // nebula737 族：gate-nebula 登录 + feiyu 上报
        SDK_VENDORS.put("nebula737", Vendor.NEBULA);
        // cpchxapi 族：4 款共用 auth/login-ali + analytics/ali-report
        SDK_VENDORS.put("cpchxapi", Vendor.CPCHX);
        // hnycgames 族：Channel/login/yxtGame 登录 + reportaction
        SDK_VENDORS.put("hnycgames", Vendor.HNYC);
        // dianli100 族：farm/openid 登录 + farm/action/report，每次动作重新取 authCode
        SDK_VENDORS.put("dianli100", Vendor.DIANLI100);
        // d3games 族：api/login + incrementgameaction/submit（仅启用森林环游记）
        SDK_VENDORS.put("d3games", Vendor.D3GAMES);
        // tomato 族：initDevices + zfbMiniGame 登录 + event/push
        SDK_VENDORS.put("tomato", Vendor.TOMATO);
        // aslk2018（gamesapi2 联运平台）：/v2/game/login 换 token → /v2/zfb/taskReport，
        // 与森林乐园每日鸡蛋任务同源，仅启用表里带 gid 的游戏
        SDK_VENDORS.put("aslk2018", Vendor.GAMESAPI2);
        // raiyantway 族（MiniGameServer 框架）：login.do 换 openId → reportGameAction（ENCODE_DATA 加密）
        SDK_VENDORS.put("raiyantway", Vendor.RAYIANTWAY);
        // flourish（斗破苍穹）：QuickGame/login 换 token → callMethod(incrementGameAction)
        SDK_VENDORS.put("flourish", Vendor.FLOURISH);
        // devourad：alipay/gameLogin → gameReport（MD5 直拼签名）
        SDK_VENDORS.put("devourad", Vendor.DEVOURAD);
        // microfun（浪漫餐厅）：getDeviceInfo → uploadActionNew（无签名）
        SDK_VENDORS.put("microfun", Vendor.MICROFUN);
        // qufang（农场纸牌塔等）：account/login → increment/basic（v3_ + SHA1）
        SDK_VENDORS.put("qufang", Vendor.QUFANG);
        // qmhd87（四季物语）：openid → login → report（eData 整包加密）
        SDK_VENDORS.put("qmhd87", Vendor.QMHD87);
        // mmhygame（这关我很行等）：login 换 openid → api/reqZfbGameEvent
        SDK_VENDORS.put("mmhygame", Vendor.MMHYGAME);
        // trueplay（无尽探险队）：biu-ccb → mGame/login → userAction
        SDK_VENDORS.put("trueplay", Vendor.TRUEPLAY);
        // igame58（货柜消消消等 7 款）：getOpenId → ali/action/submit
        SDK_VENDORS.put("igame58", Vendor.IGAME58);
        SDK_VENDORS.put("aaIgame58", Vendor.IGAME58);
        // soulgame（超元火柴人）：ThreeLogin → alipay_qg_report_game_action
        SDK_VENDORS.put("soulgame", Vendor.SOULGAME);
        // qxygame（疯狂旋转模拟器/萌萌闯关达人）：/<gameId>GetUserId.php → /<gameId>UpTask.php
        SDK_VENDORS.put("qxygame", Vendor.QXYGAME);
        // lumosfun/gusspro（弓箭手对决）：MiniGameServer 登录 → event/submit
        SDK_VENDORS.put("lumosfun", Vendor.LUMOSFUN);
        // bkxgame（拳皇98）：厂商 SDK 自带 alipay/login + data_report，但不对接平台阶梯；
        // 仅为 supports() 放行，不实现协议，completeByLink 走完组件链路后平台靠前端自动完成
        SDK_VENDORS.put("bkxgame", Vendor.BKGAME);
        // tuyoo（三国冰河时代/次神：光之觉醒）：只有游戏自身 biLog，无平台行为上报接口，
        // 仅做厂商登录（LoginBySnsIdNoVerify），不提交任何行为
        SDK_VENDORS.put("tuyoo", Vendor.TUYOO);
        // yxmys（英雄没有闪）：gzyxnetwork SDK，仅做 quickAppEnter 登录，无通关上报接口
        SDK_VENDORS.put("yxmys", Vendor.YXMNYS);
        // mihuangame（赵云与阿斗）：user/login 取 authentication → alipay/submit 上报 action_code
        SDK_VENDORS.put("mihuangame", Vendor.MIHUANGAME);
        // shryou（城主别慌）：登录链为 SDK 私有加密无法构造，uid 从游戏自身流量学习，
        // 再用明文接口 docking/lewan/aliReport 补报 action_code
        SDK_VENDORS.put("shryou", Vendor.SHRYOU);
    }

    private MiniGameVendorReporter() {
    }

    /** 是否为已知可补厂商上报的游戏（所属 SDK 族有协议实现）。 */
    public static boolean supports(String appId) {
        Vendor vendor = SDK_VENDORS.get(MiniGameTaskTable.sdk(appId));
        if (vendor == null) {
            return false;
        }
        if (vendor == Vendor.D3GAMES) {
            return d3gamesEnabled(appId);
        }
        // gamesapi2 的 gid 无法从 action 前缀推导，仅启用表里带 gid 的游戏
        if (vendor == Vendor.GAMESAPI2) {
            return !MiniGameTaskTable.param(appId, "gid").isEmpty();
        }
        // mmhygame 每款游戏独立域名，缺 URL 时不启用
        if (vendor == Vendor.MMHYGAME) {
            return !MiniGameTaskTable.param(appId, "loginUrl").isEmpty()
                    && !MiniGameTaskTable.param(appId, "submitUrl").isEmpty();
        }
        // trueplay 需要 siteKey 才能生成 sign，缺参数时保持关闭
        if (vendor == Vendor.TRUEPLAY) {
            return !MiniGameTaskTable.param(appId, "siteKey").isEmpty()
                    && !MiniGameTaskTable.param(appId, "site").isEmpty();
        }
        if (vendor == Vendor.IGAME58 || vendor == Vendor.SOULGAME) {
            return !MiniGameTaskTable.param(appId, "loginUrl").isEmpty()
                    && !MiniGameTaskTable.param(appId, "submitUrl").isEmpty();
        }
        if (vendor == Vendor.QXYGAME) {
            return !MiniGameTaskTable.param(appId, "gameId").isEmpty()
                    && !MiniGameTaskTable.param(appId, "loginUrl").isEmpty()
                    && !MiniGameTaskTable.param(appId, "submitUrl").isEmpty();
        }
        // mihuangame 各游戏上报路径不同，仅启用表里带 channelAppId 的游戏
        if (vendor == Vendor.MIHUANGAME) {
            return !MiniGameTaskTable.param(appId, "channelAppId").isEmpty()
                    && !MiniGameTaskTable.param(appId, "loginUrl").isEmpty()
                    && !MiniGameTaskTable.param(appId, "submitUrl").isEmpty();
        }
        return true;
    }

    /** 该游戏登记的 SDK 族名（日志用：说明无适配原因）。 */
    public static String sdkName(String appId) {
        return MiniGameTaskTable.sdk(appId);
    }

    /** 该游戏适配的厂商枚举名（日志用，未适配返回空串）。 */
    public static String vendorName(String appId) {
        Vendor vendor = SDK_VENDORS.get(MiniGameTaskTable.sdk(appId));
        return vendor == null ? "" : vendor.name();
    }

    /** 从静态适配表 + 手工补充构建游戏适配信息；未支持返回 null。 */
    private static Meta metaOf(String appId) {
        MiniGameTaskTable.Game game = MiniGameTaskTable.get(appId);
        if (game == null) {
            return null;
        }
        Vendor vendor = SDK_VENDORS.get(game.sdk);
        if (vendor == null) {
            return null;
        }
        if (vendor == Vendor.D3GAMES && !d3gamesEnabled(appId)) {
            return null;
        }
        if (vendor == Vendor.GAMESAPI2 && MiniGameTaskTable.param(appId, "gid").isEmpty()) {
            return null;
        }
        if (vendor == Vendor.MIHUANGAME && MiniGameTaskTable.param(appId, "channelAppId").isEmpty()) {
            return null;
        }
        String channel;
        switch (vendor) {
            case YYXX:
            case NEBULA:
            case CPCHX:
            case HNYC:
            case D3GAMES:
            case TOMATO:
            case FLOURISH:
            case QUFANG:
            case LUMOSFUN:
                channel = FOREST_CHANNEL;
                break;
            case DIANLI100:
            case DEVOURAD:
            case QMHD87:
                channel = ZHUANGYUAN_CHANNEL;
                break;
            case RAYIANTWAY:
            case MICROFUN:
                channel = FARM_CHANNEL;
                break;
            default:
                channel = GAME_TASK_CHANNEL;
                break;
        }
        // dianwanshidai 用 bgid 标识游戏组，nebula 用 company_id，两者语义同一位
        String groupId = MiniGameTaskTable.param(appId, "bgid");
        if (groupId.isEmpty()) {
            groupId = MiniGameTaskTable.param(appId, "companyId");
        }
        return new Meta(game.name, game.version, vendor, channel, MiniGameTaskTable.aliases(appId),
                MiniGameTaskTable.param(appId, "oid"),
                MiniGameTaskTable.param(appId, "gcid"),
                firstParam(appId, "nbAppKey", "appKey", "secret"),
                MiniGameTaskTable.param(appId, "nebulaAppId"),
                groupId,
                MiniGameTaskTable.param(appId, "channelId"));
    }

    /** 按顺序取第一个非空的厂商参数（不同厂商的密钥字段名不同）。 */
    private static String firstParam(String appId, String... keys) {
        for (String key : keys) {
            String value = MiniGameTaskTable.param(appId, key);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /**
     * 该游戏的版本号（用作 referer / alipayMiniMark / 事件里的 GAME_VERSION）。
     * 统一取静态适配表。
     */
    public static String appVersion(String appId) {
        return MiniGameTaskTable.version(appId);
    }

    /** 静态表登录/提交 URL（多步以分号分隔）；缺失时回退适配器默认值。 */
    private static String tableUrl(String appId, String phase, int step, String fallback) {
        String raw = MiniGameTaskTable.param(appId, phase + "Url");
        if (raw.isEmpty()) {
            return fallback;
        }
        String[] parts = raw.split(";");
        return step < parts.length && !parts[step].isEmpty() ? parts[step] : fallback;
    }

    private static final String[][] LEARN_ENDPOINTS = {
            // {族名, 登录URL模板, 上报URL模板}；{appId} 会被替换
            {"haligame", "https://{appId}.hybrid.alipay-eco.com/get_openid.php", ""},
            {"dianwanshidai", "https://platformv2.dianwanshidai.com/user/login", "https://platformv2.dianwanshidai.com/ZfbSop/behavior"},
            {"yyxxgame", "", "https://fumodasapi.yyxxgame.com/alipay/alipay_game_report"},
            {"nebula737", "https://gate-nebula737.com/account/user/detail", "https://feiyu.nebula737.com/behavior/report"},
            {"cpchxapi", "https://api.cpchxapi.com/auth/login-ali", "https://api.cpchxapi.com/analytics/ali-report"},
            {"hnycgames", "https://h5sdk.hnycgames.cn/Channel/login/yxtGame/{name}/yxtChannel/myxyx/yxtSubChannel/myxyx", ""},
            {"dianli100", "https://farm.dianli100.com/farm/openid", "https://farm.dianli100.com/farm/action/report"},
            {"d3games", "https://api.d3games.com/api/login", "https://api.d3games.com/ali/incrementgameaction/submit"},
            {"tomato", "https://merge-game.37.com.cn/init/v2/sdk/initDevices", "https://merge-game.37.com.cn/merge/v3/login/inland/zfbMiniGame"},
            {"gamesapi2", "https://gamesapi2.aslk2018.com/v2/game/login", "https://gamesapi2.aslk2018.com/v2/zfb/taskReport"},
            {"raiyantway", "https://cbqta.rayiantway.com:8525/MiniGameServer/minigame/login.do", "https://cbqta.rayiantway.com:8525/MiniGameServer/alipay/reportGameAction"},
    };

    private static void learnMode(String appId, String title, String channel, String action) {
        try {
            String authCode = AuthCodeHelper.getAuthCode(appId);
            if (authCode == null || authCode.isEmpty()) {
                Log.i("游戏任务🔍学习模式[" + title + "]#取不到授权码，跳过探测");
                return;
            }
            // 版本号统一取静态适配表
            String version = MiniGameTaskTable.version(appId);
            // 学习模式没有 Meta，直接拼 headers（与 baseHeaders 同结构）
            Map<String, String> headers = new LinkedHashMap<>();
            String minimark = AlipayMiniMarkHelper.getAlipayMiniMark(appId, version);
            headers.put("alipayminimark", minimark);
            headers.put("x-release-type", "ONLINE");
            headers.put("referer", "https://" + appId + ".hybrid.alipay-eco.com/" + appId + "/"
                    + version + "/index.html");
            headers.put("user-agent", dynamicUa());
            Log.i("游戏任务🔍学习模式[" + title + "]#appId=" + appId + "，version=" + version
                    + "，channel=" + channel + "，action=" + action + "，开始探测已知族端点");
            for (String[] endpoint : LEARN_ENDPOINTS) {
                String vendor = endpoint[0];
                String loginUrl = endpoint[1].replace("{appId}", appId).replace("{name}", title);
                String reportUrl = endpoint[2].replace("{appId}", appId).replace("{name}", title);
                if (!loginUrl.isEmpty()) {
                    probe(vendor, loginUrl, authCode, headers);
                }
                if (!reportUrl.isEmpty()) {
                    probe(vendor, reportUrl, authCode, headers);
                }
            }
            // GameTask.js 静态表已知的登录/提交 URL，覆盖未收录的厂商
            for (String phase : new String[]{"login", "submit"}) {
                String urls = MiniGameTaskTable.param(appId, phase + "Url");
                if (urls.isEmpty()) {
                    continue;
                }
                int step = 0;
                for (String url : urls.split(";")) {
                    if (!url.isEmpty()) {
                        probe("table-" + phase + step, url, authCode, headers);
                    }
                    step++;
                }
            }
        } catch (Throwable t) {
            Log.i("游戏任务🔍学习模式异常[" + title + "]: " + t);
        }
    }

    /** 单端点探测：GET 带 authCode 参数，把 URL/响应码/响应体截断打到 debug。 */
    private static void probe(String vendor, String url, String authCode, Map<String, String> headers) {
        try {
            String sep = url.contains("?") ? "&" : "?";
            String probeUrl = url + sep + "authCode=" + authCode;
            Log.i("游戏任务🔍探测[" + vendor + "] GET " + probeUrl);
            HttpURLConnection conn = (HttpURLConnection) new URL(probeUrl).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            for (Map.Entry<String, String> e : headers.entrySet()) {
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
            int code = conn.getResponseCode();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && sb.length() < 500) {
                    sb.append(line);
                }
            }
            conn.disconnect();
            String body = sb.length() > 500 ? sb.substring(0, 500) + "..." : sb.toString();
            Log.i("游戏任务🔍探测[" + vendor + "] HTTP " + code + "，响应=" + body);
        } catch (Throwable t) {
            Log.i("游戏任务🔍探测[" + vendor + "]失败: " + t.getMessage());
        }
    }

    /**
     * 按任务标题+描述解析所有匹配的动作码（去重保序）。
     * 显式关键词与静态表标签可同时命中多个动作，全部返回，避免只报一项导致遗漏；
     * 只有都没有命中时才走默认项 → 动作语义兜底 → 单一动作兜底。
     */
    public static List<String> resolveActions(String appId, String text) {
        List<String> codes = matchActions(appId, text);
        Meta meta = metaOf(appId);
        if (meta == null) {
            return codes;
        }
        if (!codes.isEmpty()) {
            return codes;
        }
        String content = text == null ? "" : text;
        String extraDefault = "";
        if (meta.actions != null) {
            for (String[] action : meta.actions) {
                if (action[0].isEmpty() && extraDefault.isEmpty()) {
                    extraDefault = action[1];
                }
            }
        }
        if (!extraDefault.isEmpty()) {
            addAction(codes, extraDefault);
            return codes;
        }
        String byBehavior = matchByBehavior(appId, content);
        if (!byBehavior.isEmpty()) {
            addAction(codes, byBehavior);
            return codes;
        }
        // 只有唯一动作可以安全兜底；多动作无法确认时宁可跳过，也不能错报首项。
        List<MiniGameTaskTable.Action> tableActions = MiniGameTaskTable.actions(appId);
        if (tableActions.size() == 1) {
            addAction(codes, tableActions.get(0).code);
        }
        return codes;
    }

    /**
     * 仅按关键词解析动作码（显式关键词 + 静态表中文动作名），命中不了就返回空列表，
     * 不做默认项/动作语义兜底。用于区分「描述里明确写了游戏内行为」与「只能拿标题猜」。
     */
    public static List<String> matchActions(String appId, String text) {
        List<String> codes = new ArrayList<>();
        Meta meta = metaOf(appId);
        if (meta == null) {
            return codes;
        }
        String content = text == null ? "" : text;
        if (meta.actions != null) {
            for (String[] action : meta.actions) {
                if (!action[0].isEmpty() && content.contains(action[0])) {
                    addAction(codes, action[1]);
                }
            }
        }
        for (MiniGameTaskTable.Action action : MiniGameTaskTable.actions(appId)) {
            if (action.matches(content)) {
                addAction(codes, action.code);
            }
        }
        return codes;
    }

    private static void addAction(List<String> codes, String code) {
        if (code != null && !code.isEmpty() && !codes.contains(code)) {
            codes.add(code);
        }
    }

    /**
     * 登录厂商服务器并上报行为；action 优先用阶梯任务下发的动作码，times 为还需上报次数
     * （阶梯进度差，一次上报推进 1 个进度）。
     */
    public static boolean report(String appId, String title, String channel, String action, int times,
                                 String listTitle) {
        Meta meta = metaOf(appId);
        if (meta == null) {
            // 未适配游戏进入学习模式：探活已知族端点，日志用于补充适配
            learnMode(appId, title, channel, action);
            return false;
        }
        if (channel != null && !channel.isEmpty()) {
            meta.channel = channel;
        }
        // 版本号统一取静态适配表（baseHeaders 里的 referer / alipayMiniMark 都用它）
        if (action == null || action.isEmpty()) {
            action = pickAction(meta, appId, title);
        }
        if (action.isEmpty()) {
            Log.i("游戏任务 厂商上报[" + meta.name + "] 跳过：任务标题无法确定动作码，title=" + title);
            return false;
        }
        Log.i("游戏任务 厂商上报[" + meta.name + "]#vendor=" + meta.vendor
                + "，channel=" + meta.channel + "，action=" + action + "，times=" + times
                + "（登录与上报的完整链接/请求体/响应见下方 厂商请求/厂商响应）");
        try {
            // 先取授权码（可能顺带初始化 appId 上下文），再在 baseHeaders 里现取 minimark
            String authCode = AuthCodeHelper.getAuthCode(appId);
            // shryou 的登录链是 SDK 私有加密，拿不到 authCode 也能按学习到的 uid 上报
            if ((authCode == null || authCode.isEmpty()) && meta.vendor != Vendor.SHRYOU) {
                Log.i("游戏任务⏭️[" + meta.name + "]#取不到授权码，跳过厂商上报");
                return false;
            }
            Map<String, String> headers = baseHeaders(meta, appId);
            // 厂商一次上报就推进 1 个阶梯进度；动作码里的 every_N 表示这 1 个进度等价于 N 次游戏行为
            // （如 qyj_game_pass_level_every_3：1 次上报=3 关），因此提交次数就是进度差，不能再乘 N。
            int every = MiniGameTaskTable.everyOf(appId, action);
            int n = Math.max(1, times);
            // 仅有登录、没有行为上报接口的 SDK 族：只登录一次，不进入 times 循环
            if (meta.vendor == Vendor.TUYOO || meta.vendor == Vendor.YXMNYS) {
                return meta.vendor == Vendor.TUYOO
                        ? loginTuyoo(meta, appId, authCode, headers)
                        : loginYxmys(meta, appId, authCode, headers);
            }
            Log.record("游戏任务🎮厂商上报[" + meta.name + "]任务列表["
                    + MessageUtil.getTaskListName(listTitle) + "]，厂商=" + meta.vendor + "，次数=" + n
                    + (every > 1 ? "（每次上报=" + every + "次游戏行为）" : "") + "，开始登录并提交");
            boolean ok = false;
            for (int i = 0; i < n; i++) {
                if (n > 1) {
                }
                boolean once;
                switch (meta.vendor) {
                    case HALIGAME:
                        once = reportHaligame(meta, appId, action, authCode, headers);
                        break;
                    case DIANWANSHIDAI:
                        once = reportDianwanshidai(meta, appId, action, authCode, headers);
                        break;
                    case YYXX:
                        once = reportYyxx(meta, appId, action, headers);
                        break;
                    case NEBULA:
                        once = reportNebula(meta, appId, action, authCode, headers);
                        break;
                    case CPCHX:
                        once = reportCpchx(meta, appId, action, authCode, headers);
                        break;
                    case HNYC:
                        once = reportHnyc(meta, appId, action, authCode, headers);
                        break;
                    case DIANLI100:
                        once = reportDianli100(meta, appId, action, authCode, headers);
                        break;
                    case D3GAMES:
                        once = reportD3games(meta, appId, action, authCode, headers);
                        break;
                    case GAMESAPI2:
                        once = reportGamesapi2(meta, appId, action, authCode, headers);
                        break;
                    case TOMATO:
                        once = reportTomato(meta, appId, action, authCode, headers);
                        break;
                    case RAYIANTWAY:
                        once = reportRaiyantway(meta, appId, action, authCode, headers);
                        break;
                    case FLOURISH:
                        once = reportFlourish(meta, appId, action, authCode, headers);
                        break;
                    case DEVOURAD:
                        once = reportDevourad(meta, appId, action, authCode, headers);
                        break;
                    case MICROFUN:
                        once = reportMicrofun(meta, appId, action, authCode, headers);
                        break;
                    case QUFANG:
                        once = reportQufang(meta, appId, action, authCode, headers);
                        break;
                    case QMHD87:
                        once = reportQmhd87(meta, appId, action, authCode, headers);
                        break;
                    case LUMOSFUN:
                        once = reportLumosfun(meta, appId, action, authCode, headers);
                        break;
                    case MMHYGAME:
                        once = reportMmhygame(meta, appId, action, authCode, headers);
                        break;
                    case TRUEPLAY:
                        once = reportTrueplay(meta, appId, action, authCode, headers);
                        break;
                    case IGAME58:
                        once = reportIgame58(meta, appId, action, authCode, headers);
                        break;
                    case SOULGAME:
                        once = reportSoulgame(meta, appId, action, authCode, headers);
                        break;
                    case QXYGAME:
                        once = reportQxygame(meta, appId, action, authCode, headers);
                        break;
                    case MIHUANGAME:
                        once = reportMihuangame(meta, appId, action, authCode, headers);
                        break;
                    case SHRYOU:
                        once = reportShryou(meta, appId, action, headers);
                        break;
                    default:
                        return ok;
                }
                ok |= once;
                MessageUtil.logByTaskList(listTitle, "游戏任务🎮厂商上报[" + meta.name + "]"
                        + (n > 1 ? " 第" + (i + 1) + "/" + n + "次" : "")
                        + (once ? " 成功" : " 失败"));
                if (!once) {
                    break;
                }
                if (i + 1 < n) {
                    Thread.sleep(500);
                }
            }
            return ok;
        } catch (Throwable t) {
            Log.i("游戏任务 厂商上报异常[" + meta.name + "]: " + t);
            Log.i("游戏任务🎮厂商上报[" + meta.name + "] 异常: " + t);
            return false;
        }
    }

    /** 按标题选 actionCode：显式关键词 → 静态表中文动作名 → 显式默认项
     *  → 动作码语义兜底 → 单一动作唯一兜底；多动作未匹配返回空串。 */
    private static String pickAction(Meta meta, String appId, String title) {
        String text = title == null ? "" : title;
        // 显式关键词优先于表内标签：表里的泛化标签（如「任务/挑战」）会抢走更具体的
        // 「首领/BOSS」等动作；空关键词项只作为默认值，稍后再用。
        String extraDefault = "";
        if (meta.actions != null) {
            for (String[] action : meta.actions) {
                if (action[0].isEmpty()) {
                    if (extraDefault.isEmpty()) {
                        extraDefault = action[1];
                    }
                } else if (text.contains(action[0])) {
                    return action[1];
                }
            }
        }
        List<MiniGameTaskTable.Action> tableActions = MiniGameTaskTable.actions(appId);
        for (MiniGameTaskTable.Action action : tableActions) {
            if (action.matches(text)) {
                return action.code;
            }
        }
        if (!extraDefault.isEmpty()) {
            return extraDefault;
        }
        String byBehavior = matchByBehavior(appId, text);
        if (!byBehavior.isEmpty()) {
            return byBehavior;
        }
        // 无阶梯、无关键词的通用「玩一玩」类任务：按表内默认动作兜底
        // （表里 alias 的空关键词项已在上面的 extraDefault 使用），
        // 仍取不到时用表内首个动作，至少提交一次行为，避免直接跳过。
        if (isGenericPlayTask(text) && !tableActions.isEmpty()) {
            String fallback = tableActions.get(0).code;
            Log.i("游戏任务 无动作关键词，按表内默认动作兜底: " + appId + " → " + fallback);
            return fallback;
        }
        // 只有唯一动作可以安全兜底；多动作无法确认时宁可跳过，也不能错报首项。
        return tableActions.size() == 1 ? tableActions.get(0).code : "";
    }

    /** 通用「玩一玩」类任务：只要求进入游戏玩一段时间，标题/描述里没有具体动作关键词。 */
    private static boolean isGenericPlayTask(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.contains("玩一玩") || text.contains("任意一款游戏") || text.contains("任意游戏")
                || text.contains("玩30s") || text.contains("玩30秒")
                || text.contains("玩60s") || text.contains("玩60秒")
                || text.contains("体验游戏") || text.contains("试玩");
    }

    /** 标题 → 动作语义兜底：任务内容里的行为词映射到 actionCode 的行为段。 */
    private static String matchByBehavior(String appId, String text) {
        String[][] rules = {
                {"闯关|通关|通过|过关|胜利|获胜|击败|挑战", "_pass|_level|_win|_mission|_boss|_tianguan"},
                {"消除|消消", "_xiaochu"},
                {"合成|合并", "_hecheng|_synthetise|_merge"},
                {"订单|居民|顾客", "_dingdan|_order|_gukedan"},
                {"开车|车辆|辆车", "_kaiche"},
                {"对局|对战|比赛", "_duiju|_match"},
                {"宝箱|开箱", "_baoxiang"},
                {"道具", "_daoju"},
                {"技能", "_jineng"},
                {"建造|建筑", "_jianzhu|_jianzao"},
                {"升级|升到", "_shengji"},
                {"体力", "_tili"},
                {"箭头", "_jiantou"},
                {"配对|匹配", "_pipei"},
                {"家具", "_jiaju"},
                {"主线", "_zhuxian"},
                {"日常", "_richang"},
                {"招募", "_zhaomu"},
                {"游历", "_take"},
                {"研发", "_gcyf"},
                {"伙伴", "_hbts"},
                {"任务", "_rw|_renwu"},
        };
        for (String[] rule : rules) {
            if (!containsAny(text, rule[0])) {
                continue;
            }
            // 先按规则里的片段顺序（_pass 在 _mission 前）再按动作匹配，
            // 避免表内 mission 排在 pass 前面时把「胜利/闯关」误判成任务码。
            for (String segment : rule[1].split("\\|")) {
                for (MiniGameTaskTable.Action action : MiniGameTaskTable.actions(appId)) {
                    if (action.code.contains(segment)) {
                        return action.code;
                    }
                }
            }
        }
        return "";
    }

    private static boolean containsAny(String text, String keywords) {
        for (String keyword : keywords.split("\\|")) {
            if (!keyword.isEmpty() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * gamesapi2（aslk2018 联运平台）：{@code /v2/game/login}（authCode 换 token）
     * → {@code /v2/zfb/taskReport}（authorization 头带 token）。
     * 与森林乐园每日鸡蛋任务同源（见 {@code GameTask} 枚举），全族 33 款的 gid
     * 均取自静态适配表（6 款另有独立协议版本 v），每次上报都重新登录。
     */
    private static boolean reportGamesapi2(Meta meta, String appId, String action, String authCode,
                                           Map<String, String> headers) {
        try {
            String gid = MiniGameTaskTable.param(appId, "gid");
            if (gid.isEmpty()) {
                Log.i("游戏任务 厂商上报 缺 gid[" + meta.name + "]，无法走 gamesapi2");
                return false;
            }
            // v 为 SDK 协议版本：静态适配表里 6 款游戏带独立 v（1.0.x），其余用游戏 version
            String v = MiniGameTaskTable.param(appId, "v");
            if (v.isEmpty()) {
                v = meta.version;
            }
            JSONObject loginBody = new JSONObject();
            loginBody.put("v", v);
            loginBody.put("code", authCode);
            loginBody.put("pf", "zfb");
            loginBody.put("reqId", System.currentTimeMillis() + "_" + (RANDOM.nextInt(350) + 1));
            loginBody.put("gid", gid);
            loginBody.put("version", meta.version);
            Map<String, String> loginHeaders = new LinkedHashMap<>(headers);
            loginHeaders.put("content-type", "application/json");
            String loginResp = post(tableUrl(appId, "login", 0,
                            "https://gamesapi2.aslk2018.com/v2/game/login"),
                    loginBody.toString(), loginHeaders);
            JSONObject loginJson = new JSONObject(loginResp);
            JSONObject data = loginJson.optJSONObject("data");
            String token = data == null ? "" : data.optString("token");
            if (loginJson.optInt("code") != 1 || token.isEmpty()) {
                Log.i("游戏任务 gamesapi2 登录失败[" + meta.name + "] " + loginResp);
                return false;
            }
            JSONObject reportBody = new JSONObject();
            reportBody.put("v", v);
            reportBody.put("version", meta.version);
            reportBody.put("reqId", System.currentTimeMillis() + "_" + (RANDOM.nextInt(90) + 10));
            reportBody.put("gid", gid);
            reportBody.put("action_code", action);
            reportBody.put("action_finish_channel", meta.channel);
            Map<String, String> reportHeaders = new LinkedHashMap<>(headers);
            reportHeaders.put("content-type", "application/json");
            reportHeaders.put("authorization", token);
            String resp = post(tableUrl(appId, "submit", 0,
                        "https://gamesapi2.aslk2018.com/v2/zfb/taskReport"),
                    reportBody.toString(), reportHeaders);
            JSONObject json = new JSONObject(resp);
            if (json.optInt("code") == 1) {
                Log.record("游戏任务 厂商上报[" + meta.name + "]成功");
                return true;
            }
            Log.record("游戏任务 厂商上报[" + meta.name + "] gamesapi2 拒绝: " + resp);
        } catch (Throwable t) {
            Log.error("游戏任务 厂商上报 gamesapi2 异常: " + t);
        }
        return false;
    }

    private static Map<String, String> baseHeaders(Meta meta, String appId) {
        Map<String, String> headers = new LinkedHashMap<>();
        String minimark = AlipayMiniMarkHelper.getAlipayMiniMark(appId, meta.version);
        headers.put("alipayminimark", minimark);
        Log.i("游戏任务 厂商上报 alipayminimark appId=" + appId
                + " version=" + meta.version + " len=" + minimark.length()
                + " mark=" + minimark);
        headers.put("x-release-type", "ONLINE");
        headers.put("referer", "https://" + appId + ".hybrid.alipay-eco.com/" + appId + "/"
                + meta.version + "/index.html");
        headers.put("user-agent", dynamicUa());
        return headers;
    }

    private static String dynamicUa() {
        String systemUa = System.getProperty("http.agent");
        if (systemUa == null || systemUa.isEmpty()) {
            systemUa = "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + ")";
        }
        String alipayVer;
        try {
            alipayVer = ApplicationHook.getAlipayVersion().getVersionString();
        } catch (Throwable t) {
            alipayVer = "";
        }
        return systemUa + " NebulaSDK/1.8.100112 Nebula AliApp(AP/" + alipayVer
                + ") AlipayClient/" + alipayVer;
    }

    /** haligame：get_openid → user_action_submit，URL 以静态表为准。 */
    private static boolean reportHaligame(Meta meta, String appId, String action, String authCode,
                                          Map<String, String> headers) throws Exception {
        String loginUrl = tableUrl(appId, "login", 0, "https://zf.haligame.com/ajax/get_openid.php");
        String submitUrl = tableUrl(appId, "submit", 0, "https://zf.haligame.com/ajax/user_action_submit.php");
        JSONObject auth = new JSONObject();
        auth.put("auth_code", authCode);
        Map<String, String> jsonHeaders = new LinkedHashMap<>(headers);
        jsonHeaders.put("content-type", "application/json; charset=utf-8");
        String openIdResp = post(loginUrl, auth.toString(), jsonHeaders);
        String openId = findString(openIdResp, "open_id", "openid", "openId");
        if (openId.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#haligame 未取到 open_id，响应=" + openIdResp);
            return false;
        }
        JSONObject submit = new JSONObject();
        submit.put("action_code", action);
        submit.put("action_finish_channel", meta.channel);
        submit.put("open_id", openId);
        Map<String, String> formHeaders = new LinkedHashMap<>(headers);
        formHeaders.put("content-type", "application/x-www-form-urlencoded");
        String resp = post(submitUrl, submit.toString(), formHeaders);
        // 成功格式 {"code":"10000","msg":"Success"}（code 是字符串）
        boolean ok = resp.contains("\"code\":\"10000\"");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败"));
        return ok;
    }

    /** dianwanshidai：user/login → ZfbSop/behavior。 */
    private static boolean reportDianwanshidai(Meta meta, String appId, String action, String authCode,
                                               Map<String, String> headers) throws Exception {
        JSONObject login = new JSONObject();
        login.put("openid", "");
        login.put("code", authCode);
        JSONObject query = new JSONObject();
        query.put("channel", "");
        login.put("query", query);
        Map<String, String> loginHeaders = new LinkedHashMap<>(headers);
        loginHeaders.put("content-type", "application/json; charset=utf-8");
        loginHeaders.put("appid", appId);
        loginHeaders.put("app-id", appId);
        loginHeaders.put("bgid", meta.companyId);
        String loginResp = post(tableUrl(appId, "login", 0,
                        "https://platformv2.dianwanshidai.com/api_v2/v1/user/login"),
                login.toString(), loginHeaders);
        String openId = findString(loginResp, "openid", "open_id", "openId");
        if (openId.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#dianwanshidai 未取到 openid，响应=" + loginResp);
            return false;
        }
        JSONObject behavior = new JSONObject();
        behavior.put("openid", openId);
        behavior.put("action_code", action);
        behavior.put("channel", meta.channel);
        String resp = post(tableUrl(appId, "submit", 0,
                        "https://platformv2.dianwanshidai.com/api_v2/v1/ZfbSop/behavior"),
                behavior.toString(), loginHeaders);
        // 成功格式 {"code":200,"msg":"上报成功"}
        boolean ok = resp.contains("\"code\":200");
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    /** yyxxgame：无登录，直接带支付宝 UID + md5 签名上报。域名按表 param[reportHost] 取
     *  （源星战域实测走 yxdasapi.yxzygame.com，与 yyxxgame 同模板仅换域名），缺省回落 fumodasapi。 */
    private static boolean reportYyxx(Meta meta, String appId, String action,
                                      Map<String, String> headers) throws Exception {
        // JSONObject.put(key, null) 会移除该键，服务端随即报 field "user_id" is not set，
        // 故先取 hook 的 UID，取不到再回落到模块缓存的当前 UID。
        String userId = ApplicationHook.getUserId();
        if (userId == null || userId.isEmpty()) {
            userId = UserIdMap.getCurrentUid();
        }
        if (userId == null || userId.isEmpty()) {
            Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + "，跳过：未取到支付宝 UID");
            return false;
        }
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#user_id=" + userId
                + "（len=" + userId.length() + "），oid=" + meta.oid + "，gcid=" + meta.gcid
                + "，channel=" + meta.channel);
        long time = System.currentTimeMillis();
        String signSrc = "action_code=" + action
                + "&action_finish_channel=" + meta.channel
                + "&gcid=" + meta.gcid
                + "&oid=" + meta.oid
                + "&user_id=" + userId
                + "&time=" + time
                + "undefined";
        // 服务端只按表单字段解析：发 JSON 会 400 field "user_id" is not set
        // （见 11-12 19:37 实测，JSON 提交两条全部失败，form 表单全部成功）
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("user_id", userId);
        fields.put("action_code", action);
        fields.put("action_finish_channel", meta.channel);
        fields.put("oid", meta.oid);
        fields.put("gcid", meta.gcid);
        fields.put("sign", md5(signSrc));
        fields.put("time", String.valueOf(time));
        // 同族换域名（yxzygame 与 yyxxgame 同模板），差异走表 param，不硬编码
        String host = MiniGameTaskTable.param(appId, "reportHost");
        if (host.isEmpty()) {
            host = "fumodasapi.yyxxgame.com";
        }
        String reportUrl = "https://" + host + "/alipay/alipay_game_report";
        String resp = postForm(reportUrl, fields, headers);
        // 成功格式 {"msg":"success","code":1}
        boolean ok = resp.contains("\"code\":1");
        if (ok) {
            Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=成功");
        } else {
            Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=失败"
                    + "\n请求: POST " + reportUrl
                    + "\n参数: " + fields
                    + "\n响应: " + resp);
        }
        return ok;
    }

    /** nebula737（一步两步）：account/user/detail 登录 → behavior/report。 */
    private static boolean reportNebula(Meta meta, String appId, String action, String authCode,
                                        Map<String, String> headers) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", meta.nebulaAppId);
        params.put("channel_id", meta.channelId);
        params.put("code", authCode);
        params.put("company_id", meta.companyId);
        params.put("data_version", "1");
        params.put("device_id", randomHex(32));
        params.put("device_type", Build.MODEL);
        params.put("extend", "");
        params.put("game_version", "1.6.13");
        params.put("is_debug", "0");
        params.put("network_mode", "0");
        params.put("open_id", "");
        params.put("os", "1");
        params.put("os_version", Build.VERSION.RELEASE);
        params.put("passport_code", "alipayminigame");
        params.put("payment_code", "alipayminigame");
        params.put("sdk_version", "3.4.35");
        params.put("sign_nonce", randomHexUpper(8));
        params.put("sign_type", "md5");
        params.put("sign_version", "1.0");
        params.put("startup_id", randomHex(32));
        params.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("union_id", "");
        params.put("user_id", "0");
        JSONObject login = new JSONObject();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            login.put(entry.getKey(), entry.getValue());
        }
        login.put("sign", nebulaSign(params, meta.appKey));
        Map<String, String> jsonHeaders = new LinkedHashMap<>(headers);
        jsonHeaders.put("content-type", "application/json; charset=utf-8");
        String loginUrl = tableUrl(appId, "login", 0, "https://gate-nebula.737.com/account/user/detail");
        String submitUrl = tableUrl(appId, "submit", 0,
                "https://api-combine.feiyu.com/v1/alipayMiniGame/behavior/report");
        String loginResp = post(loginUrl, login.toString(), jsonHeaders);
        String openId = findString(loginResp, "open_id", "openId");
        String userId = findString(loginResp, "user_id", "userId");
        if (openId.isEmpty() || userId.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#nebula 登录未取到 open_id/user_id，响应=" + loginResp);
            return false;
        }
        Map<String, String> report = new LinkedHashMap<>();
        report.put("action_code", action);
        report.put("action_finish_channel", meta.channel);
        report.put("action_finish_date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date()));
        report.put("app_id", meta.nebulaAppId);
        report.put("channel_id", meta.channelId);
        report.put("open_id", openId);
        report.put("out_biz_no", randomHex(32));
        report.put("user_id", userId);
        JSONObject body = new JSONObject();
        for (Map.Entry<String, String> entry : report.entrySet()) {
            body.put(entry.getKey(), entry.getValue());
            if ("channel_id".equals(entry.getKey())) {
                body.put(entry.getKey(), Integer.parseInt(entry.getValue()));
            }
        }
        body.put("sign", nebulaSign(report, meta.appKey));
        String resp = post(submitUrl, body.toString(), jsonHeaders);
        // 成功格式 {"status":0,"message":"OK","data":null}（兼容旧格式 {"msg":"success","code":2000,"data":[]}）
        boolean ok = resp.contains("\"status\":0") || resp.contains("\"code\":2000");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败"));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#"+(ok ? "成功" : "失败"));
        return ok;
    }

    /** cpchxapi：auth/login-ali 换 JWT → analytics/ali-report（Bearer 鉴权）。 */
    private static boolean reportCpchx(Meta meta, String appId, String action, String authCode,
                                       Map<String, String> headers) throws Exception {
        JSONObject login = new JSONObject();
        login.put("appId", appId);
        login.put("code", authCode);
        login.put("anonymousCode", JSONObject.NULL);
        login.put("channel", 3);
        login.put("inviteId", "");
        Map<String, String> jsonHeaders = new LinkedHashMap<>(headers);
        jsonHeaders.put("content-type", "application/json; charset=utf-8");
        String loginResp = post(tableUrl(appId, "login", 0,
                        "https://cpchxapi.dianwanshidai.com/app-api/game/auth/login-ali"),
                login.toString(), jsonHeaders);
        String token = findString(loginResp, "token");
        if (token.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#cpchx 未取到 token，响应=" + loginResp);
            return false;
        }
        JSONObject report = new JSONObject();
        report.put("eventName", action);
        report.put("channel", meta.channel);
        Map<String, String> reportHeaders = new LinkedHashMap<>(jsonHeaders);
        reportHeaders.put("authorization", "Bearer " + token);
        String resp = post(tableUrl(appId, "submit", 0,
                        "https://cpchxapi.dianwanshidai.com/app-api/game/analytics/ali-report"),
                report.toString(), reportHeaders);
        // 成功格式 {"code":0,"msg":"","data":null}
        boolean ok = resp.contains("\"code\":0");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败"));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    /**
     * hnycgames（我的花园世界）：最简模板——Channel/login 换 yxtChannelUserId → reportaction。
     * 本协议：全程无 sign、无时间戳、无设备指纹、无 clientSendBI（clientSendBI 由
     * 游戏自身 JS 生成，不属于本类提报链路）。登录只换 authCode，上报只换 yxtChannelUserId。
     */
    private static boolean reportHnyc(Meta meta, String appId, String action, String authCode,
                                      Map<String, String> headers) throws Exception {
        // action 形如 wdhysj_game_dingdan_every_2，前缀即 yxtGame 目录名
        int index = action.indexOf("_game_");
        String game = index > 0 ? action.substring(0, index) : "wdhysj";
        String segments = "yxtGame/" + game + "/yxtChannel/myxyx/yxtSubChannel/myxyx";
        String hnycBase = "https://h5sdk.hnycgames.cn";
        Map<String, String> formHeaders = new LinkedHashMap<>(headers);
        formHeaders.put("content-type", "application/x-www-form-urlencoded");

        // 1. 渠道登录：body 只有 scene + authCode 两个字段
        String loginBody = "authCode=" + URLEncoder.encode(authCode, "UTF-8")
                + "&scene=" + URLEncoder.encode(meta.channel, "UTF-8");
        String loginUrl = tableUrl(appId, "login", 0, hnycBase + "/Channel/login/" + segments);
        String loginResp = post(loginUrl, loginBody, formHeaders);
        // 上报唯一要用的字段是 yxtChannelUserId（即支付宝 openId）
        String yxtChannelUserId = findString(loginResp, "yxtChannelUserId");
        if (yxtChannelUserId.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#hnyc 未取到 yxtChannelUserId，响应=" + loginResp);
            return false;
        }

        // 2. 行为上报：body 只有 action_code + channel + yxtChannelUserId 三个字段
        String reportBody = "action_code=" + URLEncoder.encode(action, "UTF-8")
                + "&channel=" + URLEncoder.encode(meta.channel, "UTF-8")
                + "&yxtChannelUserId=" + URLEncoder.encode(yxtChannelUserId, "UTF-8");
        String submitUrl = tableUrl(appId, "submit", 0,
                hnycBase + "/channel/index/yxtMethod/reportaction/" + segments);
        String resp = post(submitUrl, reportBody, formHeaders);
        // 成功格式 {"errorCode":0,"errorMessage":"成功"}
        boolean ok = resp.contains("\"errorCode\":0");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败")
                + (ok ? "" : "，响应=" + resp));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    /**
     * mmhygame（这关我很行等）：login 换 openid → api/reqZfbGameEvent。
     * GameTask.js：登录体 {@code {type:7, code:authCode, version, packageType:2}}；
     * 上报体 {@code {eventId:action, eventChannel:channel}}。
     */
    private static boolean reportMmhygame(Meta meta, String appId, String action, String authCode,
                                          Map<String, String> headers) throws Exception {
        String gameName = MiniGameTaskTable.param(appId, "gameName");
        String loginUrl = gameName.isEmpty() ? tableUrl(appId, "login", 0, "")
                : "https://" + gameName + ".xyx.mmhygame.com/" + gameName + "/login";
        String submitUrl = gameName.isEmpty() ? tableUrl(appId, "submit", 0, "")
                : "https://" + gameName + ".xyx.mmhygame.com/" + gameName + "/api/reqZfbGameEvent";
        if (loginUrl.isEmpty() || submitUrl.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#mmhygame 静态表缺少登录/提交 URL");
            return false;
        }
        Map<String, String> jsonHeaders = new LinkedHashMap<>(headers);
        jsonHeaders.put("content-type", "application/json; charset=utf-8");
        JSONObject login = new JSONObject();
        login.put("type", 7);
        login.put("code", authCode);
        login.put("version", meta.version);
        login.put("packageType", 2);
        String loginResp = post(loginUrl, login.toString(), jsonHeaders);
        String openId = findString(loginResp, "openid", "open_id", "uid");
        if (openId.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#mmhygame 未取到 openid，响应=" + loginResp);

            return false;
        }
        JSONObject report = new JSONObject();
        report.put("eventId", action);
        report.put("eventChannel", meta.channel);
        String resp = post(submitUrl, report.toString(), jsonHeaders);
        boolean ok = resp.contains("\"code\":0") || resp.contains("\"success\":true");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败")
                + (ok ? "" : "，响应=" + resp));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    /**
     * trueplay（无尽探险队）：biu-ccb 换 token → mGame/login 换 uid → userAction(GET)。
     * 签名 na[24]：去 sign、排序 k=v&amp;，{@code md5(site + 串 + siteKey)} 小写。
     */
    private static boolean reportTrueplay(Meta meta, String appId, String action, String authCode,
                                          Map<String, String> headers) throws Exception {
        String site = MiniGameTaskTable.param(appId, "site");
        String siteKey = MiniGameTaskTable.param(appId, "siteKey");
        String aid = MiniGameTaskTable.param(appId, "aid");
        String sdkAppId = MiniGameTaskTable.param(appId, "sdkAppId");
        if (site.isEmpty() || siteKey.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#trueplay 静态表缺少 site/siteKey");
            return false;
        }
        Map<String, String> jsonHeaders = new LinkedHashMap<>(headers);
        jsonHeaders.put("accept-charset", "UTF-8");
        jsonHeaders.put("content-type", "application/json");

        String channelId = MiniGameTaskTable.param(appId, "channelId");
        String biuTarget = sdkAppId.isEmpty()
                ? tableUrl(appId, "login", 0, "https://minigame-cli.youwongames.com/biu-ccb/")
                + "alipay/login?biuopen=%7C%7C%7C%7C%7C"
                : "https://minigame-cli.youwongames.com/biu-ccb/" + sdkAppId
                + "/alipay/login?biuopen=%7C%7C%7C%7C%7C";
        JSONObject biu = new JSONObject();
        biu.put("auth_code", authCode);
        String biuResp = post(biuTarget, biu.toString(), jsonHeaders);
        String token = findString(biuResp, "token", "biuToken");
        if (token.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#trueplay biu-ccb 未取到 token，响应=" + biuResp);
            return false;
        }

        Map<String, String> login = new LinkedHashMap<>();
        login.put("site", site);
        login.put("appid", sdkAppId);
        login.put("js_code", token);
        login.put("aid", aid);
        login.put("code", "");
        login.put("channel", meta.channel);
        login.put("channel_parame", "{\"channel\":\"other\"}");
        long seconds = System.currentTimeMillis() / 1000;
        login.put("time", String.valueOf(seconds));
        login.put("sign", trueplaySign(login, site, siteKey));
        String loginUrl2 = channelId.isEmpty()
                ? tableUrl(appId, "login", 1, "https://center.trueplay.cn/mGame/login/")
                : "https://center.trueplay.cn/mGame/login/" + channelId;
        String loginResp = post(loginUrl2, new JSONObject(login).toString(), jsonHeaders);
        String uid = findString(loginResp, "uid", "userId", "user_id");
        if (uid.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#trueplay 未取到 uid，响应=" + loginResp);
            return false;
        }

        Map<String, String> query = new LinkedHashMap<>();
        query.put("site", site);
        query.put("aid", aid);
        query.put("uid", uid);
        query.put("device", java.util.UUID.randomUUID().toString().replace("-", ""));
        query.put("action_code", action);
        query.put("action_finish_channel", meta.channel);
        query.put("time", String.valueOf(System.currentTimeMillis() / 1000));
        query.put("sign", trueplaySign(query, site, siteKey));
        StringBuilder qs = new StringBuilder();
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (qs.length() > 0) {
                qs.append('&');
            }
            qs.append(URLEncoder.encode(entry.getKey(), "UTF-8")).append('=')
                    .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        String submitUrl = channelId.isEmpty()
                ? tableUrl(appId, "submit", 0, "https://center.trueplay.cn/mGame/userAction/")
                : "https://center.trueplay.cn/mGame/userAction/" + channelId;
        String resp = post(submitUrl + "?" + qs, "", headers);
        boolean ok = resp.contains("\"code\":0") || resp.contains("\"success\":true");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败")
                + (ok ? "" : "，响应=" + resp));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    /** trueplay 签名：排序 k=v&amp; 拼接，md5(site + 串 + siteKey) 小写。 */
    private static String trueplaySign(Map<String, String> body, String site, String siteKey) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (String key : new java.util.TreeSet<>(body.keySet())) {
            if ("sign".equals(key)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            String value = body.get(key);
            sb.append(key).append('=').append(value == null ? "" : value);
        }
        return md5(site + sb + siteKey).toLowerCase();
    }

    /** igame58（货柜消消消等）：GET getOpenId 换 openid → POST ali/action/submit。 */
    private static boolean reportIgame58(Meta meta, String appId, String action, String authCode,
                                         Map<String, String> headers) throws Exception {
        String loginUrl = tableUrl(appId, "login", 0, "");
        String submitUrl = tableUrl(appId, "submit", 0, "");
        if (loginUrl.isEmpty() || submitUrl.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#igame58 静态表缺少登录/提交 URL");
            return false;
        }
        String umid = MiniGameTaskTable.param(appId, "umid");
        StringBuilder query = new StringBuilder("?code=")
                .append(URLEncoder.encode(authCode, "UTF-8"))
                .append("&channel=zhifubao&platform=0");
        if (!umid.isEmpty()) {
            query.append("&umid=").append(URLEncoder.encode(umid, "UTF-8"));
        }
        String loginResp = post(loginUrl + query, "", headers);
        String openId = findString(loginResp, "openId", "openid", "open_id", "data");
        if (openId.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#igame58 未取到 openid，响应=" + loginResp);
            return false;
        }
        Map<String, String> jsonHeaders = new LinkedHashMap<>(headers);
        jsonHeaders.put("content-type", "application/json; charset=utf-8");
        jsonHeaders.put("device", "{}");
        jsonHeaders.put("openid", openId);
        jsonHeaders.put("usertag", "");
        jsonHeaders.put("uuid", java.util.UUID.randomUUID().toString().replace("-", ""));
        jsonHeaders.put("versioncode", "0");
        JSONObject report = new JSONObject();
        report.put("actionCode", action);
        report.put("actionFinishChannel", meta.channel);
        report.put("channel", "zhifubao");
        report.put("platform", 0);
        if (!umid.isEmpty()) {
            report.put("umid", umid);
        }
        String resp = post(submitUrl, report.toString(), jsonHeaders);
        boolean ok = resp.contains("\"code\":0") || resp.contains("\"success\":true");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败")
                + (ok ? "" : "，响应=" + resp));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    /**
     * soulgame（超元火柴人）：ThreeLogin 换 userId → alipay_qg_report_game_action。
     * 签名 oa[26]：{@code md5("S1G20A4I" + 排序后各值拼接).toUpperCase()}，排除 sig/ext。
     */
    private static boolean reportSoulgame(Meta meta, String appId, String action, String authCode,
                                          Map<String, String> headers) throws Exception {
        String loginUrl = tableUrl(appId, "login", 0, "");
        String submitUrl = tableUrl(appId, "submit", 0, "");
        if (loginUrl.isEmpty() || submitUrl.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#soulgame 静态表缺少登录/提交 URL");
            return false;
        }
        Map<String, String> formHeaders = new LinkedHashMap<>(headers);
        formHeaders.put("content-type", "application/x-www-form-urlencoded");
        String deviceId = md5(java.util.UUID.randomUUID().toString().replace("-", ""));
        Map<String, String> login = new LinkedHashMap<>();
        login.put("appkey", meta.appKey);
        login.put("channel", "zhifubao");
        login.put("device_id", deviceId);
        login.put("distinct_id", deviceId);
        login.put("logintype", "qg_alipay");
        login.put("openid", "");
        login.put("token", authCode);
        login.put("ext", "");
        login.put("client_ver", "1.0.0");
        login.put("client_type", "4");
        login.put("sdkLogin", "1");
        login.put("sig", soulgameSig(login));
        String loginResp = postForm(loginUrl, login, formHeaders);
        String userId = findString(loginResp, "userId", "user_id", "uid");
        if (userId.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#soulgame 未取到 userId，响应=" + loginResp);
            return false;
        }
        Map<String, String> report = new LinkedHashMap<>();
        report.put("userId", userId);
        report.put("openId", "");
        report.put("action_code", action);
        report.put("action_finish_channel", meta.channel);
        report.put("action_finish_date", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.CHINA).format(new java.util.Date()));
        report.put("out_biz_no", "");
        report.put("sig", soulgameSig(report));
        String resp = postForm(submitUrl, report, formHeaders);
        boolean ok = resp.contains("\"code\":0") || resp.contains("\"success\":true");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败")
                + (ok ? "" : "，响应=" + resp));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    /** soulgame 签名：常量前缀 + 排序后各值（排除 sig/ext），md5 后转大写。 */
    private static String soulgameSig(Map<String, String> body) throws Exception {
        StringBuilder sb = new StringBuilder("S1G20A4I");
        for (String key : new java.util.TreeSet<>(body.keySet())) {
            if ("sig".equals(key) || "ext".equals(key)) {
                continue;
            }
            String value = body.get(key);
            sb.append(value == null ? "" : value);
        }
        return md5(sb.toString()).toUpperCase();
    }

    /**
     * qxygame（疯狂旋转模拟器/萌萌闯关达人）：URL 由 GameTask.js 的拼接公式复现，
     * {@code <base><gameId>GetUserId.php} 换 userId → {@code <base><gameId>UpTask.php}。
     * 基础域名（loginUrl/submitUrl）与 gameId 均取自静态表（表值兜底）。
     */
    private static boolean reportQxygame(Meta meta, String appId, String action, String authCode,
                                         Map<String, String> headers) throws Exception {
        String gameId = MiniGameTaskTable.param(appId, "gameId");
        String base = tableUrl(appId, "login", 0, "https://qxygame.zhangle.fun/");
        String submitBase = tableUrl(appId, "submit", 0, "https://qxygame.zhangle.fun/");
        if (gameId.isEmpty() || base.isEmpty() || submitBase.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#qxygame 静态表缺少 gameId/登录域名");
            return false;
        }
        if (!base.endsWith("/")) {
            base += "/";
        }
        if (!submitBase.endsWith("/")) {
            submitBase += "/";
        }
        Map<String, String> jsonHeaders = new LinkedHashMap<>(headers);
        jsonHeaders.put("content-type", "application/json; charset=utf-8");
        JSONObject login = new JSONObject();
        login.put("code", authCode);
        String loginResp = post(base + gameId + "GetUserId.php", login.toString(), jsonHeaders);
        String userId = findString(loginResp, "userId", "uid", "user_id", "uuid", "openid", "open_id", "data");
        if (userId.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#qxygame 未取到 userId，响应=" + loginResp);
            return false;
        }
        String alipayUserId = ApplicationHook.getUserId();
        if (alipayUserId == null || alipayUserId.isEmpty()) {
            alipayUserId = UserIdMap.getCurrentUid();
        }
        JSONObject report = new JSONObject();
        report.put("action_finish_date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                Locale.CHINA).format(new Date()));
        report.put("uuid", userId);
        report.put("alipay_user_id", alipayUserId == null ? "" : alipayUserId);
        report.put("action_code", action);
        report.put("channel", meta.channel);
        String resp = post(submitBase + gameId + "UpTask.php", report.toString(), jsonHeaders);
        boolean ok = resp.contains("\"code\":0") || resp.contains("\"success\":true");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败")
                + (ok ? "" : "，响应=" + resp));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    /**
     * mihuangame（赵云与阿斗）：user/login 取 authentication →
     * alipay/submit 上报 action_code（token 放请求头 authentication）。
     */
    private static boolean reportMihuangame(Meta meta, String appId, String action, String authCode,
                                            Map<String, String> headers) throws Exception {
        String loginUrl = MiniGameTaskTable.param(appId, "loginUrl");
        String submitUrl = MiniGameTaskTable.param(appId, "submitUrl");
        String channelAppId = MiniGameTaskTable.param(appId, "channelAppId");
        if (loginUrl.isEmpty() || submitUrl.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#mihuangame 静态表缺少 loginUrl/submitUrl");
            return false;
        }
        Map<String, String> jsonHeaders = new LinkedHashMap<>(headers);
        jsonHeaders.put("content-type", "application/json; charset=utf-8");
        JSONObject login = new JSONObject();
        if (!channelAppId.isEmpty()) {
            try {
                login.put("channelAppId", Integer.parseInt(channelAppId));
            } catch (NumberFormatException e) {
                login.put("channelAppId", channelAppId);
            }
        }
        login.put("code", authCode);
        String loginResp = post(loginUrl, login.toString(), jsonHeaders);
        String token = findString(loginResp, "authentication");
        if (token.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#mihuangame 未取到 authentication，响应=" + loginResp);
            return false;
        }
        Map<String, String> submitHeaders = new LinkedHashMap<>(jsonHeaders);
        submitHeaders.put("authentication", token);
        JSONObject submit = new JSONObject();
        submit.put("action_code", action);
        submit.put("action_finish_date", System.currentTimeMillis());
        submit.put("action_finish_channel", meta.channel);
        String resp = post(submitUrl, submit.toString(), submitHeaders);
        String trimmed = resp == null ? "" : resp.trim();
        boolean ok = trimmed.isEmpty() || trimmed.equals("{}") || trimmed.contains("\"code\":0");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败")
                + (ok ? "" : "，响应=" + resp));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    /**
     * tuyoo（三国冰河时代/次神：光之觉醒）：只做厂商登录，不做行为上报。
     * 该 SDK 无「厂商→平台」的行为上报接口，进度只在游戏自身 biLog/WebSocket 内。
     */
    private static boolean loginTuyoo(Meta meta, String appId, String authCode,
                                      Map<String, String> headers) throws Exception {
        String loginUrl = MiniGameTaskTable.param(appId, "loginUrl");
        String sdkAppId = MiniGameTaskTable.param(appId, "sdkAppId");
        if (loginUrl.isEmpty() || sdkAppId.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#tuyoo 静态表缺少 loginUrl/sdkAppId");
            return false;
        }
        String clientId = MiniGameTaskTable.param(appId, "clientId");
        if (clientId.isEmpty()) {
            clientId = "H5_5.0_tyGuest,tyAccount.alipay.0-hall" + sdkAppId + ".aliminigame";
        }
        String deviceId = randomHex(32).toUpperCase(Locale.ROOT);
        JSONObject sceneParam = new JSONObject();
        sceneParam.put("channel", meta.channel);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("ali_appId", appId);
        params.put("ali_authcode", authCode);
        params.put("appId", sdkAppId);
        params.put("gameId", sdkAppId);
        params.put("clientId", clientId);
        params.put("snsId", "ali.cn.minigame:" + authCode);
        params.put("uuid", deviceId);
        params.put("original_deviceid", deviceId);
        params.put("osName", "Android");
        params.put("os_version", Build.VERSION.RELEASE);
        params.put("phone_model", Build.MODEL);
        params.put("k1", "v1");
        params.put("sdk_device_brand", Build.BRAND);
        params.put("sdk_origin_device_level_info", "high");
        params.put("sdk_session_id", randomHex(32).toUpperCase(Locale.ROOT));
        params.put("sdk_login_track_id", randomHex(32).toUpperCase(Locale.ROOT));
        params.put("scene_param", Base64.encodeToString(
                sceneParam.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        String resp = postForm(loginUrl, params, headers);
        boolean ok = resp.contains("\"code\":0") || resp.contains("authorCode");
        Log.i("游戏任务🎮厂商登录[" + meta.name + "]#tuyoo 结果=" + (ok ? "成功" : "失败")
                + (ok ? "" : "，响应=" + resp));
        Log.record("游戏任务🎮厂商登录[" + meta.name + "]#tuyoo " + (ok ? "成功" : "失败"));
        return ok;
    }

    /**
     * yxmys（英雄没有闪）：gzyxnetwork SDK 的 quickAppEnter 登录（js_code 为支付宝授权码）。
     * 进游/登录上报走 ca=game_login_report，无通关类上报接口。
     */
    private static boolean loginYxmys(Meta meta, String appId, String authCode,
                                      Map<String, String> headers) throws Exception {
        String loginUrl = MiniGameTaskTable.param(appId, "loginUrl");
        String cgameId = MiniGameTaskTable.param(appId, "cgame_id");
        if (loginUrl.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#yxmys 静态表缺少 loginUrl");
            return false;
        }
        JSONObject wxParam = new JSONObject();
        wxParam.put("channel", meta.channel);
        Map<String, String> params = new LinkedHashMap<>();
        if (!cgameId.isEmpty()) {
            params.put("c_game_id", cgameId);
        }
        params.put("guid", UUID.randomUUID().toString());
        params.put("js_code", authCode);
        params.put("wx_param", wxParam.toString());
        String resp = postForm(loginUrl, params, headers);
        boolean ok = resp.contains("\"code\":1") || resp.contains("app_ext");
        Log.i("游戏任务🎮厂商登录[" + meta.name + "]#yxmys 结果=" + (ok ? "成功" : "失败")
                + (ok ? "" : "，响应=" + resp));
        Log.record("游戏任务🎮厂商登录[" + meta.name + "]#yxmys " + (ok ? "成功" : "失败"));
        return ok;
    }

    /** dianli100（农场对对碰）：farm/openid 登录 → farm/action/report，上报重新取 authCode。 */
    private static boolean reportDianli100(Meta meta, String appId, String action, String authCode,
                                           Map<String, String> headers) throws Exception {
        String gameId = MiniGameTaskTable.param(appId, "gameId");
        String base = tableUrl(appId, "login", 0, "https://alifunc.dianli100.com/majiang/alipay/")
                + (gameId.isEmpty() ? "farm" : gameId);
        Map<String, String> jsonHeaders = new LinkedHashMap<>(headers);
        jsonHeaders.put("content-type", "application/json; charset=utf-8");
        JSONObject login = new JSONObject();
        login.put("authCode", authCode);
        String loginResp = post(base + "/openid", login.toString(), jsonHeaders);
        String openId = findString(loginResp, "data");
        if (openId.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#dianli100 未取到 openid，响应=" + loginResp);
            return false;
        }
        // 每个动作上报都带一个刚生成的新 authCode，与登录时不同
        String reportCode = AuthCodeHelper.getAuthCode(appId);
        if (reportCode == null || reportCode.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#dianli100 上报取不到授权码");
            return false;
        }
        JSONObject report = new JSONObject();
        report.put("authCode", reportCode);
        report.put("channel", meta.channel);
        report.put("actionCode", action);
        String submitBase = tableUrl(appId, "submit", 0, "https://alifunc.dianli100.com/majiang/alipay/")
                + (gameId.isEmpty() ? "farm" : gameId);
        String resp = post(submitBase + "/action/report", report.toString(), jsonHeaders);
        // 成功格式 {"code":200,"msg":"success","data":...}
        boolean ok = resp.contains("\"code\":200");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败")
                + " openid=" + openId);
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    /**
     * d3games（森林环游记）：api/login 换 uid+token → ali/incrementgameaction/submit。
     * 签名 md5(按 key 排序拼接 k=v（无分隔符，排除 ver/sign/空值/"0" 值）+ appKey)，
     * 密钥即静态表 appKey（森林环游记=465d2fe840abb7bfdc2d69cf1f39be28）。
     */
    private static boolean reportD3games(Meta meta, String appId, String action, String authCode,
                                         Map<String, String> headers) throws Exception {
        String host = MiniGameTaskTable.param(appId, "reportHost");
        if (host.isEmpty()) {
            host = "api-slhyj.d3games.com";
        }
        String base = "https://" + host + "/api";
        String loginUrl = tableUrl(appId, "login", 0, base + "/login");
        String submitUrl = tableUrl(appId, "submit", 0, base + "/ali/incrementgameaction/submit");
        String gameId = MiniGameTaskTable.param(appId, "gameId");
        Map<String, String> formHeaders = new LinkedHashMap<>(headers);
        formHeaders.put("content-type", "application/x-www-form-urlencoded");
        // 登录：body 是 JSON 字符串，但 content-type 为 form-urlencoded
        Map<String, String> loginParams = new LinkedHashMap<>();
        loginParams.put("ver", "2.0.1");
        loginParams.put("gameid", gameId);
        loginParams.put("appid", appId);
        loginParams.put("sign_type", "md5");
        loginParams.put("time_stamp", String.valueOf(System.currentTimeMillis() / 1000));
        loginParams.put("channel", meta.channel);
        loginParams.put("model", Build.MODEL);
        loginParams.put("version", alipayVersion());
        loginParams.put("system", Build.VERSION.RELEASE);
        loginParams.put("platform", "Android");
        loginParams.put("shareuid", "0");
        loginParams.put("scene", "0");
        loginParams.put("sharekey", "");
        loginParams.put("shareid", "0");
        loginParams.put("clue_token", "");
        loginParams.put("creative_id", "");
        loginParams.put("advertister_id", "");
        loginParams.put("request_id", "");
        loginParams.put("code", authCode);
        JSONObject login = new JSONObject();
        for (Map.Entry<String, String> entry : loginParams.entrySet()) {
            if ("shareuid".equals(entry.getKey())) {
                login.put(entry.getKey(), 0);
            } else {
                login.put(entry.getKey(), entry.getValue());
            }
        }
        login.put("sign", d3Sign(loginParams, meta.appKey));
        String loginResp = post(loginUrl, login.toString(), formHeaders);
        String uid = findString(loginResp, "uid");
        String openId = findString(loginResp, "openid");
        String token = findString(loginResp, "token");
        if (uid.isEmpty() || token.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#d3games 登录未取到 uid/token，响应=" + loginResp);
            return false;
        }
        // 上报：uid 是数字，openid 是支付宝 UID，time_stamp 单位秒
        Map<String, String> reportParams = new LinkedHashMap<>();
        reportParams.put("ver", "2.0.1");
        reportParams.put("gameid", gameId);
        reportParams.put("appid", appId);
        reportParams.put("sign_type", "md5");
        reportParams.put("time_stamp", String.valueOf(System.currentTimeMillis() / 1000));
        reportParams.put("action_code", action);
        reportParams.put("action_finish_channel", meta.channel);
        reportParams.put("uid", uid);
        reportParams.put("openid", openId);
        reportParams.put("token", token);
        JSONObject report = new JSONObject();
        for (Map.Entry<String, String> entry : reportParams.entrySet()) {
            if ("uid".equals(entry.getKey())) {
                report.put(entry.getKey(), Long.parseLong(entry.getValue()));
            } else {
                report.put(entry.getKey(), entry.getValue());
            }
        }
        report.put("sign", d3Sign(reportParams, meta.appKey));
        String resp = post(submitUrl, report.toString(), formHeaders);
        // 成功格式 {"code":"0","msg":"成功","servertime":...}（code 是字符串）
        boolean ok = resp.contains("\"code\":\"0\"");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败"));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    /**
     * tomato（时尚百货城）：initDevices → merge/v3/login/inland/zfbMiniGame 换 channelUserId
     * → init/platform/event/push。
     * 签名 md5(全部字段（除 sign）按 key 排序 k=v 用 & 连接（含空值，值不编码）+ secret)。
     */
    private static boolean reportTomato(Meta meta, String appId, String action, String authCode,
                                        Map<String, String> headers) throws Exception {
        String base = "https://ssbhc-minigame-sdk.tomatogames.com";
        String initUrl = tableUrl(appId, "login", 0, base + "/init/v2/sdk/initDevices");
        String pushUrl = tableUrl(appId, "submit", 0, base + "/init/platform/event/push");
        String secret = MiniGameTaskTable.param(appId, "secret");
        String gameId = MiniGameTaskTable.param(appId, "gameId");
        String platformKey = MiniGameTaskTable.param(appId, "platformKey");
        String sdkChannel = MiniGameTaskTable.param(appId, "sdkChannel");
        String cid = MiniGameTaskTable.param(appId, "cid");
        String uuid = deviceUuid();
        String gid = String.format(Locale.ROOT, "%010d-%d",
                (RANDOM.nextLong() & Long.MAX_VALUE) % 10000000000L, System.currentTimeMillis());
        // 设备/渠道基础字段（initDevices / login / push 共用）
        Map<String, String> base2 = new LinkedHashMap<>();
        base2.put("advertisingId", uuid);
        base2.put("androidId", uuid);
        base2.put("channel", sdkChannel);
        base2.put("cid", cid);
        base2.put("device_no", uuid);
        base2.put("gameId", gameId);
        base2.put("gid", gid);
        base2.put("idfa", uuid);
        base2.put("idfv", uuid);
        base2.put("imei", uuid);
        base2.put("ip", "");
        base2.put("mac", "");
        base2.put("model", Build.MODEL);
        base2.put("network_type", "4g");
        base2.put("oaid", uuid);
        base2.put("sdk_version", "1.0.0");
        base2.put("type", "0");
        base2.put("udid", alipayVersion());
        base2.put("version", "1.0.171");
        // 1) initDevices
        Map<String, String> initParams = new LinkedHashMap<>(base2);
        initParams.put("afContent", "");
        initParams.put("channelType", "1");
        initParams.put("extension", new JSONObject()
                .put("miniprogram", new JSONObject().put("zfb_uid", uuid)).toString());
        initParams.put("sign", tomatoSign(initParams, secret));
        String initResp = postForm(initUrl, initParams, headers);
        if (!initResp.contains("\"code\":\"1\"")) {
            Log.i("游戏任务⏭️[" + meta.name + "]#tomato initDevices 失败，响应=" + initResp);
            return false;
        }
        // 2) zfbMiniGame 登录（extension 里塞 authCode）
        Map<String, String> loginParams = new LinkedHashMap<>(base2);
        loginParams.put("channelType", "1");
        loginParams.put("extSdkData", new JSONObject().put("source", new JSONObject()
                .put("platform", "my").put("ext", new JSONObject())).toString());
        loginParams.put("extension", new JSONObject().put("loginMode", "99")
                .put("extension", new JSONObject()
                        .put("miniprogram", new JSONObject().put("zfb_uid", uuid)))
                .put("code", authCode).toString());
        loginParams.put("method", "");
        loginParams.put("sign", tomatoSign(loginParams, secret));
        String loginResp = postForm(base + "/merge/v3/login/inland/zfbMiniGame", loginParams, headers);
        String channelUserId = findString(loginResp, "channelUserId");
        if (channelUserId.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#tomato 登录未取到 channelUserId，响应=" + loginResp);
            return false;
        }
        // 3) event/push（JSON 请求体；pushParams 是 JSON 字符串，openId=channelUserId）
        String pushParams = new JSONObject()
                .put("eventId", action)
                .put("eventFinishChannel", meta.channel)
                .put("isCustom", true)
                .put("eventFinishDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                        .format(new Date()))
                .put("openId", channelUserId)
                .toString();
        Map<String, String> pushSign = new LinkedHashMap<>(base2);
        pushSign.put("platformKey", platformKey);
        pushSign.put("platformType", "ali");
        pushSign.put("pushParams", pushParams);
        JSONObject push = new JSONObject();
        for (Map.Entry<String, String> entry : pushSign.entrySet()) {
            push.put(entry.getKey(), entry.getValue());
        }
        push.put("sign", tomatoSign(pushSign, secret));
        Map<String, String> jsonHeaders = new LinkedHashMap<>(headers);
        jsonHeaders.put("content-type", "application/json; charset=utf-8");
        String resp = post(pushUrl, push.toString(), jsonHeaders);
        // 成功格式 {"code":"1","msg":"成功","data":null}（code 是字符串）
        boolean ok = resp.contains("\"code\":\"1\"");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败"));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    /**
     * flourish（斗破苍穹）：QuickGame/login 换 openid/token → callMethod(incrementGameAction)。
     * 签名为变体 A：排除嵌套字段后按 key 排序，k=v 用 & 连接，末尾直拼 appKey 再 MD5。
     */
    private static boolean reportFlourish(Meta meta, String appId, String action, String authCode,
                                          Map<String, String> headers) throws Exception {
        String loginUrl = tableUrl(appId, "login", 0, "https://sdk.flourish-fs.com/QuickGame/login");
        String submitUrl = tableUrl(appId, "submit", 0, "https://sdk.flourish-fs.com/QuickGame/callMethod");
        String appKey = MiniGameTaskTable.param(appId, "appKey");
        if (appKey.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#flourish 缺 appKey（表 param），跳过");
            return false;
        }
        String userId = ApplicationHook.getUserId();
        if (userId == null || userId.isEmpty()) {
            userId = UserIdMap.getCurrentUid();
        }
        if (userId == null || userId.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#flourish 未取到支付宝 UID，跳过");
            return false;
        }
        String launchOptions = new JSONObject().put("channel", meta.channel).toString();
        Map<String, String> loginParams = new LinkedHashMap<>();
        loginParams.put("c_id", "1");
        loginParams.put("game_id", "192");
        loginParams.put("game_version", "1.0.0");
        loginParams.put("sdk_version", "1.1.2");
        loginParams.put("agent_id", "1001");
        loginParams.put("site_id", "10001");
        loginParams.put("sdk_channel", "qg_alipay");
        loginParams.put("brand", "");
        loginParams.put("model", Build.MANUFACTURER + " " + Build.MODEL);
        loginParams.put("os_type", "android");
        loginParams.put("is_quick_game", "1");
        loginParams.put("device", "qg_" + md5(userId));
        loginParams.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        loginParams.put("timezone", "Asia/Shanghai");
        loginParams.put("utctime", "UTC+8");
        loginParams.put("launch_options", launchOptions);
        loginParams.put("channel_params", new JSONObject().put("code", authCode).toString());
        loginParams.put("sign", flourishSign(loginParams, appKey));
        Map<String, String> jsonHeaders = new LinkedHashMap<>(headers);
        jsonHeaders.put("content-type", "application/json; charset=utf-8");
        String loginResp = post(loginUrl, flourishJson(loginParams).toString(), jsonHeaders);
        String openId = findString(loginResp, "openid");
        String accessToken = findString(loginResp, "access_token");
        String refreshToken = findString(loginResp, "refresh_token");
        String uid = findString(loginResp, "uid");
        String uname = findString(loginResp, "uname");
        if (!loginResp.contains("\"code\":0") || openId.isEmpty() || accessToken.isEmpty() || uid.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#flourish 登录未取到 openid/token，响应=" + loginResp);
            return false;
        }

        JSONObject event = new JSONObject();
        event.put("launch_options", new JSONObject().put("channel", meta.channel));
        event.put("event", "alipay_game_action");
        event.put("role_id", "21045803606873136");
        event.put("role_name", "铁匠铺霜归墟");
        event.put("power", "1669");
        event.put("action_code", action);
        event.put("level", 1);
        event.put("server_id", "4900108");
        event.put("server_name", "冰爽一区70服");
        event.put("role_level", 1);
        event.put("combat_power", "1669");

        Map<String, String> reportParams = new LinkedHashMap<>(loginParams);
        reportParams.remove("sign");
        reportParams.remove("launch_options");
        reportParams.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        reportParams.put("access_token", accessToken);
        reportParams.put("refresh_token", refreshToken);
        reportParams.put("openid", openId);
        reportParams.put("uid", uid);
        reportParams.put("uname", uname);
        reportParams.put("method", "incrementGameAction");
        reportParams.put("channel_params", event.toString());
        reportParams.put("sign", flourishSign(reportParams, appKey));
        Map<String, String> reportHeaders = baseHeaders(meta, appId);
        reportHeaders.put("content-type", "application/json; charset=utf-8");
        String resp = post(submitUrl, flourishJson(reportParams).toString(), reportHeaders);
        boolean ok = resp.contains("\"code\":0");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败")
                + (ok ? "" : "，响应=" + resp));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    /** devourad：alipay/gameLogin → gameReport，MD5(appid + appSecret + 参数直拼)。 */
    private static boolean reportDevourad(Meta meta, String appId, String action, String authCode,
                                          Map<String, String> headers) throws Exception {
        String secret = MiniGameTaskTable.param(appId, "appKey");
        if (secret.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#devourad 缺 appKey（表 param），跳过");
            return false;
        }
        String loginUrl = tableUrl(appId, "login", 0, "https://api.devourad.com/alipay/gameLogin");
        String submitUrl = tableUrl(appId, "submit", 0, "https://api.devourad.com/alipay/gameReport");
        Map<String, String> jsonHeaders = new LinkedHashMap<>(headers);
        jsonHeaders.put("content-type", "application/json; charset=utf-8");
        JSONObject login = new JSONObject();
        login.put("appid", appId);
        login.put("code", authCode);
        login.put("sign", md5(appId + secret + authCode));
        String loginResp = post(loginUrl, login.toString(), jsonHeaders);
        String openId = findString(loginResp, "open_id", "openid");
        if (openId.isEmpty() || !loginResp.contains("\"code\":1")) {
            Log.i("游戏任务⏭️[" + meta.name + "]#devourad 登录失败，响应=" + loginResp);
            return false;
        }
        String outBizNo = md5(UUID.randomUUID().toString());
        JSONObject report = new JSONObject();
        report.put("appid", appId);
        report.put("action_code", action);
        report.put("action_finish_channel", meta.channel);
        report.put("open_id", openId);
        report.put("out_biz_no", outBizNo);
        report.put("sign", md5(appId + secret + action + meta.channel + outBizNo + openId));
        String resp = post(submitUrl, report.toString(), jsonHeaders);
        boolean ok = resp.contains("\"code\":1");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败")
                + (ok ? "" : "，响应=" + resp));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    /** microfun（浪漫餐厅）：getDeviceInfo 换 openid → uploadActionNew，无签名。
     *  静态表标注 loginMethod=GET（code 走 query），实测服务端对 POST JSON 体不敏感，
     *  故按表优先 GET ?code=，未取到 openid 再回退 POST JSON（同一 code，失败的兑换不消耗它）。 */
    private static boolean reportMicrofun(Meta meta, String appId, String action, String authCode,
                                          Map<String, String> headers) throws Exception {
        String path = MiniGameTaskTable.param(appId, "path");
        if (path.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#microfun 缺 path（表 param），跳过");
            return false;
        }
        String loginUrl = tableUrl(appId, "login", 0,
                "https://xyx-txeo.cliapi.microfun.cn/zhifubao/getDeviceInfo/" + path);
        String submitUrl = tableUrl(appId, "submit", 0,
                "https://xyx-txeo.cliapi.microfun.cn/zhifubao/uploadActionNew/" + path);
        Map<String, String> jsonHeaders = new LinkedHashMap<>(headers);
        jsonHeaders.put("content-type", "application/json; charset=utf-8");
        String loginResp = "";
        String openId = "";
        if ("GET".equalsIgnoreCase(MiniGameTaskTable.param(appId, "loginMethod"))) {
            String sep = loginUrl.contains("?") ? "&" : "?";
            try {
                loginResp = request("GET", loginUrl + sep + "code="
                        + URLEncoder.encode(authCode, "UTF-8"), null, headers);
                openId = findString(loginResp, "openid", "open_id");
            } catch (Throwable t) {
                Log.i("游戏任务⏭️[" + meta.name + "]#microfun GET 登录异常: " + t);
            }
        }
        if (openId.isEmpty()) {
            JSONObject login = new JSONObject();
            login.put("code", authCode);
            loginResp = post(loginUrl, login.toString(), jsonHeaders);
            openId = findString(loginResp, "openid", "open_id");
        }
        if (openId.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#microfun 登录未取到 openid，响应=" + loginResp);
            return false;
        }
        Map<String, String> report = new LinkedHashMap<>();
        report.put("action_finish_date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date()));
        report.put("open_id", openId);
        report.put("action_code", action);
        report.put("action_finish_channel", meta.channel);
        String resp = postForm(submitUrl, report, headers);
        boolean ok = resp.contains("\"code\":\"10000\"");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败")
                + (ok ? "" : "，响应=" + resp));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    /**
     * qufang（农场纸牌塔等）：account/login 换 uid/token → increment/basic。
     * signature = "v3_" + SHA1("qyqf-cocos" + "3" + appid + versionApp + uid + token + t + message)。
     */
    private static boolean reportQufang(Meta meta, String appId, String action, String authCode,
                                        Map<String, String> headers) throws Exception {
        String loginHost = MiniGameTaskTable.param(appId, "loginHost");
        if (loginHost.isEmpty()) {
            loginHost = "login-01.qufang.top";
        }
        String reportHost = MiniGameTaskTable.param(appId, "reportHost");
        if (reportHost.isEmpty()) {
            reportHost = "game-factory-jail-001.qufang.top";
        }
        String versionApp = MiniGameTaskTable.param(appId, "versionApp");
        if (versionApp.isEmpty()) {
            versionApp = "4.4.5";
        }
        Map<String, String> loginHeaders = new LinkedHashMap<>(headers);
        loginHeaders.put("content-type", "application/json; charset=utf-8");
        loginHeaders.put("qy-appid", appId);
        loginHeaders.put("qy-rid", UUID.randomUUID().toString());
        JSONObject systemInfo = new JSONObject();
        systemInfo.put("screenWidth", 1080);
        systemInfo.put("screenHeight", 2400);
        systemInfo.put("windowWidth", 411);
        systemInfo.put("windowHeight", 914);
        systemInfo.put("pixelRatio", 2.625);
        systemInfo.put("model", Build.MODEL);
        systemInfo.put("system", Build.VERSION.RELEASE);
        systemInfo.put("version", alipayVersion());
        systemInfo.put("language", "zh-Hans");
        systemInfo.put("isIphoneXSeries", false);
        JSONObject launchOptions = new JSONObject();
        launchOptions.put("path", "");
        launchOptions.put("query", new JSONObject().put("channel", meta.channel));
        launchOptions.put("referrerInfo", new JSONObject());
        JSONObject hostName = new JSONObject();
        hostName.put("login", loginHost);
        hostName.put("game_factory", reportHost);
        hostName.put("event", "event-jail-001.qufang.top");
        hostName.put("more_game", "moregame.qufang.top");
        hostName.put("static_resources_public", "static-resources-public.qufang.top");
        JSONObject message = new JSONObject();
        message.put("version", 3);
        message.put("versionApp", versionApp);
        message.put("type", "alipay");
        message.put("code", authCode);
        message.put("idx", 0);
        message.put("launchOptions", launchOptions);
        message.put("systemInfo", systemInfo);
        message.put("stored_part", new JSONObject().put("part", new JSONObject().put("syncVersion", 0)));
        message.put("hostName", hostName);
        message.put("res", new JSONObject().put("code", authCode));
        JSONObject login = new JSONObject();
        login.put("version", 3);
        login.put("versionApp", versionApp);
        login.put("platformType", "alipay");
        login.put("appid", appId);
        login.put("t", System.currentTimeMillis());
        login.put("message", message);
        String loginUrl = tableUrl(appId, "login", 0, "https://" + loginHost + "/account/login");
        String submitUrl = tableUrl(appId, "submit", 0,
                "https://" + reportHost + "/platform_service/increment/basic");
        String loginResp = post(loginUrl, login.toString(), loginHeaders);
        String uid = findString(loginResp, "uid");
        String token = findString(loginResp, "token");
        if (!loginResp.contains("\"ok\":1") || uid.isEmpty() || token.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#qufang 登录未取到 uid/token，响应=" + loginResp);
            return false;
        }
        long reportTs = System.currentTimeMillis();
        JSONObject actionMessage = new JSONObject();
        actionMessage.put("action_code", action);
        actionMessage.put("action_finish_channel", meta.channel);
        actionMessage.put("action_finish_time", reportTs);
        String signature = "v3_" + sha1("qyqf-cocos" + "3" + appId + versionApp
                + uid + token + reportTs + qufangMessage(actionMessage));
        JSONObject report = new JSONObject();
        report.put("version", 3);
        report.put("versionApp", versionApp);
        report.put("platformType", "alipay");
        report.put("appid", appId);
        report.put("uid", uid);
        report.put("token", token);
        report.put("t", reportTs);
        report.put("message", actionMessage);
        report.put("signature", signature);
        Map<String, String> reportHeaders = new LinkedHashMap<>(headers);
        reportHeaders.put("content-type", "application/json; charset=utf-8");
        reportHeaders.put("qy-appid", appId);
        reportHeaders.put("qy-rid", UUID.randomUUID().toString());
        reportHeaders.put("qy-uid", uid);
        String resp = post(submitUrl, report.toString(), reportHeaders);
        boolean ok = resp.contains("\"ok\":1");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败")
                + (ok ? "" : "，响应=" + resp));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    /** qufang 的 message 签名串：key 字典序、过滤空值、值 URL 编码后用 & 连接。 */
    private static String qufangMessage(JSONObject message) throws Exception {
        JSONArray names = message.names();
        List<String> keys = new ArrayList<>();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                keys.add(names.optString(i));
            }
        }
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            String value = message.optString(key);
            if (value.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(key).append('=')
                    .append(URLEncoder.encode(value, "UTF-8").replace("+", "%20"));
        }
        return sb.toString();
    }

    /**
     * qmhd87（四季物语）：openid → login → report，body 只有 eData。
     * eData = Base64(DES-ECB("cTawb7/c", PKCS#7(Base64(gzip(JSON)))))。
     */
    private static boolean reportQmhd87(Meta meta, String appId, String action, String authCode,
                                        Map<String, String> headers) throws Exception {
        String host = MiniGameTaskTable.param(appId, "reportHost");
        if (host.isEmpty()) {
            host = "game2-sjwy.qmhd87.com";
        }
        String channelCode = MiniGameTaskTable.param(appId, "channelCode");
        if (channelCode.isEmpty()) {
            channelCode = "PA0306";
        }
        String packageName = MiniGameTaskTable.param(appId, "packageName");
        if (packageName.isEmpty()) {
            packageName = "projectA";
        }
        String sdkVersion = MiniGameTaskTable.param(appId, "sdkVersion");
        if (sdkVersion.isEmpty()) {
            sdkVersion = "6.9.7";
        }
        String base = "https://" + host;
        long installId = System.currentTimeMillis();
        String did = "rc" + installId;
        String noise = String.valueOf(100000000 + RANDOM.nextInt(900000000));
        Map<String, String> openidHeaders = qmhdHeaders(headers, did, installId, noise,
                channelCode, packageName, sdkVersion);
        JSONObject openidBody = new JSONObject();
        openidBody.put("eData", qmhdEncrypt(new JSONObject().put("code", authCode).toString()));
        String openidResp = post(tableUrl(appId, "login", 0, base + "/api/v1/ap/openid/"),
                openidBody.toString(), openidHeaders);
        JSONObject openidJson = qmhdJson(openidResp);
        String openId = openidJson == null ? "" : findString(openidJson, "openid", "open_id");
        String token = openidJson == null ? ""
                : findString(openidJson, "token", "authorization", "accessToken", "access_token");
        if (openId.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#qmhd87 未取到 openid，响应=" + openidResp);
            return false;
        }
        Map<String, String> loginHeaders = qmhdHeaders(headers, openId, installId, noise,
                channelCode, packageName, sdkVersion);
        if (!token.isEmpty()) {
            loginHeaders.put("authorization", "Bearer " + token);
        }
        JSONObject loginPlain = new JSONObject();
        loginPlain.put("type", 6);
        loginPlain.put("account", openId);
        loginPlain.put("extData", new JSONObject());
        JSONObject loginBody = new JSONObject();
        loginBody.put("eData", qmhdEncrypt(loginPlain.toString()));
        String loginResp = post(tableUrl(appId, "login", 1, base + "/api/v1/login/"),
                loginBody.toString(), loginHeaders);
        JSONObject loginJson = qmhdJson(loginResp);
        String loginToken = loginJson == null ? ""
                : findString(loginJson, "token", "authorization", "accessToken", "access_token");
        Map<String, String> reportHeaders = qmhdHeaders(headers, openId, installId, noise,
                channelCode, packageName, sdkVersion);
        String bearer = loginToken.isEmpty() ? token : loginToken;
        if (!bearer.isEmpty()) {
            reportHeaders.put("authorization", "Bearer " + bearer);
        }
        JSONObject reportPlain = new JSONObject();
        reportPlain.put("actionCode", action);
        reportPlain.put("actionFinishAt", System.currentTimeMillis() / 1000);
        reportPlain.put("actionFinishChannel", meta.channel);
        JSONObject reportBody = new JSONObject();
        reportBody.put("eData", qmhdEncrypt(reportPlain.toString()));
        String resp = post(tableUrl(appId, "submit", 0, base + "/api/v1/ap/report/"),
                reportBody.toString(), reportHeaders);
        JSONObject json = qmhdJson(resp);
        boolean ok = json != null && json.optInt("code", -1) == 0;
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败")
                + (ok ? "" : "，响应=" + resp));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    private static Map<String, String> qmhdHeaders(Map<String, String> base, String did, long installId,
                                                   String noise, String channelCode, String packageName,
                                                   String sdkVersion) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        Map<String, String> headers = new LinkedHashMap<>(base);
        headers.put("accept-charset", "UTF-8");
        headers.put("accept", "*/*");
        headers.put("content-type", "application/json; charset=utf-8");
        headers.put("channel", channelCode);
        headers.put("did", did);
        headers.put("install-id", String.valueOf(installId));
        headers.put("lang", "");
        headers.put("noise", noise);
        headers.put("package-name", packageName);
        headers.put("plat", "android");
        headers.put("run-id", String.valueOf(installId));
        headers.put("timestamp", String.valueOf(timestamp));
        headers.put("version", sdkVersion);
        headers.put("sign", md5(channelCode + did + installId + noise + packageName
                + installId + timestamp + sdkVersion + "6KscZatA"));
        return headers;
    }

    /** qmhd87 响应解析：可能是明文 JSON，也可能是 {@code eData} 密文。 */
    private static JSONObject qmhdJson(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }
        try {
            JSONObject jo = new JSONObject(response);
            String encrypted = jo.optString("eData");
            return encrypted.isEmpty() ? jo : new JSONObject(qmhdDecrypt(encrypted));
        } catch (Throwable ignored) {
        }
        try {
            return new JSONObject(qmhdDecrypt(response));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final byte[] QMHD_DES_KEY = "cTawb7/c".getBytes(StandardCharsets.UTF_8);

    private static String qmhdEncrypt(String plain) throws Exception {
        String gzipBase64 = Base64.encodeToString(gzip(plain.getBytes(StandardCharsets.UTF_8)),
                Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(QMHD_DES_KEY, "DES"));
        return Base64.encodeToString(cipher.doFinal(gzipBase64.getBytes(StandardCharsets.UTF_8)),
                Base64.NO_WRAP);
    }

    private static String qmhdDecrypt(String data) throws Exception {
        Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(QMHD_DES_KEY, "DES"));
        byte[] inner = cipher.doFinal(Base64.decode(data, Base64.DEFAULT));
        String gzipBase64 = new String(inner, StandardCharsets.UTF_8).trim();
        return gunzip(Base64.decode(gzipBase64, Base64.DEFAULT));
    }

    /** lumosfun/gusspro（弓箭手对决）：MiniGameServer 登录 → mini-alipay event/submit。 */
    private static boolean reportLumosfun(Meta meta, String appId, String action, String authCode,
                                          Map<String, String> headers) throws Exception {
        String loginHost = MiniGameTaskTable.param(appId, "loginHost");
        if (loginHost.isEmpty()) {
            loginHost = "azory.lumosfun.com:8525";
        }
        String reportHost = MiniGameTaskTable.param(appId, "reportHost");
        if (reportHost.isEmpty()) {
            reportHost = "gjdz-zfb-def.gusspro.com:15087";
        }
        String loginBase = tableUrl(appId, "login", 0,
                "https://" + loginHost + "/MiniGameServer/minigame/login.do");
        String loginUrl = loginBase + (loginBase.contains("?") ? "&" : "?")
                + "userType=31&appId=" + URLEncoder.encode(appId, "UTF-8")
                + "&code=" + URLEncoder.encode(authCode, "UTF-8");
        String loginResp = request("GET", loginUrl, "", headers);
        String openId = findString(loginResp, "openId", "openid", "open_id");
        if (!loginResp.contains("\"code\":\"0\"") || openId.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#lumosfun 登录未取到 openId，响应=" + loginResp);
            return false;
        }
        JSONObject report = new JSONObject();
        report.put("openId", openId);
        report.put("eventId", action);
        report.put("EventFinishChannel", meta.channel);
        report.put("AlipayUserId", "");
        report.put("EventFinishDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date()));
        report.put("OutBizNo", "SB_" + (System.currentTimeMillis() / 1000));
        Map<String, String> jsonHeaders = new LinkedHashMap<>(headers);
        jsonHeaders.put("content-type", "application/json; charset=utf-8");
        String submitUrl = tableUrl(appId, "submit", 0,
                "https://" + reportHost + "/mini-alipay/event/submit");
        String resp = post(submitUrl, report.toString(), jsonHeaders);
        boolean ok = resp.contains("\"code\":\"0\"");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败")
                + (ok ? "" : "，响应=" + resp));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    private static byte[] gzip(byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        }
        return out.toByteArray();
    }

    private static String gunzip(byte[] data) throws Exception {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String sha1(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        return hex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    /** flourish 请求体：数字字段保持 JSON number，其余保持字符串。 */
    private static JSONObject flourishJson(Map<String, String> params) throws Exception {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if ("c_id".equals(key) || "game_id".equals(key) || "agent_id".equals(key)
                    || "site_id".equals(key) || "is_quick_game".equals(key)) {
                json.put(key, Integer.parseInt(value));
            } else if ("timestamp".equals(key) || "uid".equals(key)) {
                json.put(key, Long.parseLong(value));
            } else {
                json.put(key, value);
            }
        }
        return json;
    }

    /** flourish 变体 A：嵌套 JSON 字符串整体不参与签名，空值字段仍保留。 */
    private static String flourishSign(Map<String, String> params, String appKey) throws Exception {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            if ("sign".equals(key) || "channel_params".equals(key) || "launch_options".equals(key)
                    || "role_name".equals(key) || "server_name".equals(key) || "goods_name".equals(key)
                    || "extension".equals(key) || "event_params".equals(key)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(key).append('=').append(params.get(key));
        }
        sb.append(appKey);
        return md5(sb.toString());
    }

    /**
     * raiyantway（占城大师，MiniGameServer 框架）：login.do 换 openId → reportGameAction。
     * 上报体为 ENCODE_DATA 加密：明文 {appId,openId,actionCode,actionFinishChannel,outBizNo,
     * actionFinishDate}（算法来自 GameTask.js 的 Ya 函数）。
     * 域名/端口与 userType 走 {@link MiniGameTaskTable} param（loginHost/userType），不硬编码。
     */
    private static boolean reportRaiyantway(Meta meta, String appId, String action, String authCode,
                                            Map<String, String> headers) throws Exception {
        String host = MiniGameTaskTable.param(appId, "loginHost");
        if (host.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#raiyantway 缺 loginHost（表 param），跳过");
            return false;
        }
        String userType = MiniGameTaskTable.param(appId, "userType");
        if (userType.isEmpty()) {
            userType = "31";
        }
        String base = "https://" + host + "/MiniGameServer";
        // 实际为 GET + JSON body（HttpURLConnection 不支持 GET 带 body），按同 content-type 的 POST 发送
        JSONObject login = new JSONObject();
        login.put("userType", userType);
        login.put("appId", appId);
        login.put("code", authCode);
        Map<String, String> jsonHeaders = new LinkedHashMap<>(headers);
        jsonHeaders.put("content-type", "application/json; charset=utf-8");
        String loginUrl = tableUrl(appId, "login", 0, base + "/minigame/login.do");
        String submitUrl = tableUrl(appId, "submit", 0, base + "/alipay/reportGameAction");
        String loginResp = post(loginUrl, login.toString(), jsonHeaders);
        String openId = findString(loginResp, "openId");
        if (!loginResp.contains("\"code\":\"0\"") || openId.isEmpty()) {
            Log.i("游戏任务⏭️[" + meta.name + "]#raiyantway 登录未取到 openId，响应=" + loginResp);
            return false;
        }
        JSONObject plain = new JSONObject();
        plain.put("appId", appId);
        plain.put("openId", openId);
        plain.put("actionCode", action);
        plain.put("actionFinishChannel", meta.channel);
        plain.put("outBizNo", UUID.randomUUID().toString());
        plain.put("actionFinishDate", System.currentTimeMillis());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ENCODE_DATA", raiyantwayEncode(plain.toString()));
        String resp = postForm(submitUrl, fields, headers);
        // 成功格式 {"code":"0","msg":"success","data":null}（code 是字符串）
        boolean ok = resp.contains("\"code\":\"0\"");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#" + action + " 结果=" + (ok ? "成功" : "失败")
                + (ok ? "" : "\n明文: " + plain + "\n响应: " + resp));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    /**
     * raiyantway ENCODE_DATA 加密（GameTask.js Ya 函数）：
     * UTF-8 字节 → 补零至 4 的倍数 → 逐字节 XOR 随机 4 字节 key → 密文拼接 key → base64。
     */
    private static String raiyantwayEncode(String plainJson) {
        byte[] data = plainJson.getBytes(StandardCharsets.UTF_8);
        int pad = (4 - data.length % 4) % 4;
        byte[] key = new byte[4];
        RANDOM.nextBytes(key);
        byte[] out = new byte[data.length + pad + 4];
        for (int i = 0; i < data.length + pad; i++) {
            // 补零段 0 ^ key = key，与显式补零后统一 XOR 等价
            byte b = i < data.length ? data[i] : 0;
            out[i] = (byte) (b ^ key[i % 4]);
        }
        System.arraycopy(key, 0, out, data.length + pad, 4);
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }

    /** tomato 设备标识：androidId/idfa/oaid 等字段同值，进程内生成一次复用。 */
    private static String deviceUuid() {
        if (DEVICE_UUID == null) {
            DEVICE_UUID = java.util.UUID.randomUUID().toString();
        }
        return DEVICE_UUID;
    }

    private static volatile String DEVICE_UUID;

    private static String alipayVersion() {
        try {
            return ApplicationHook.getAlipayVersion().getVersionString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** d3games 签名：按 key 排序拼接 k=v（无分隔符，排除 ver/sign/空值/"0" 值），末尾追加密钥。 */
    private static String d3Sign(Map<String, String> params, String secret) throws Exception {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            if ("ver".equals(key) || "sign".equals(key)) {
                continue;
            }
            String value = params.get(key);
            if (value == null || value.isEmpty() || "0".equals(value)) {
                continue;
            }
            sb.append(key).append('=').append(value);
        }
        sb.append(secret);
        return md5(sb.toString());
    }

    /** tomato 签名：全部字段（除 sign）按 key 排序 k=v 用 & 连接（含空值），末尾追加密钥。 */
    private static String tomatoSign(Map<String, String> params, String secret) throws Exception {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            if ("sign".equals(key)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(key).append('=').append(params.get(key));
        }
        sb.append(secret);
        return md5(sb.toString());
    }

    /** 流量学习到的 shryou 账号 uid（key=游戏 code，如 chengzhubiehuangzhang-zfb）。 */
    private static final Map<String, String> SHRYOU_UIDS = new HashMap<>();
    private static final String SHRYOU_UID_PREF = "sesame_shryou_uid";

    /**
     * 从游戏流量里学习 shryou 账号 uid。
     *
     * <p>shryou 的登录链（{@code graph.llewan.com/v5/AliGame/UserReg} →
     * {@code exc.llewan.com/v5/GameUser/Auth}）请求体是 SDK 私有加密（{@code St_EgxMV_} 前缀），
     * 无法自行构造；但游戏自己上报 {@code docking/lewan/aliReport} 时带的是明文 uid，
     * 这里把 game→uid 记下来，同一账号之后即可自动补报。
     */
    public static void observeTraffic(String url, String body) {
        try {
            if (url == null || body == null || !url.contains("shryou.com") || !url.contains("lewan")) {
                return;
            }
            String uid = findString(body, "uid");
            String game = findString(body, "game");
            if (uid.isEmpty() || game.isEmpty() || uid.equals(SHRYOU_UIDS.get(game))) {
                return;
            }
            SHRYOU_UIDS.put(game, uid);
            Context context = SimplePageManager.getContext();
            if (context != null) {
                context.getSharedPreferences(SHRYOU_UID_PREF, Context.MODE_PRIVATE)
                        .edit().putString(game, uid).apply();
            }
            Log.i("游戏任务 学习shryou uid[" + game + "]=" + uid);
        } catch (Throwable ignored) {
        }
    }

    private static String shryouUid(String game) {
        if (game == null || game.isEmpty()) {
            return "";
        }
        String uid = SHRYOU_UIDS.get(game);
        if (uid != null && !uid.isEmpty()) {
            return uid;
        }
        Context context = SimplePageManager.getContext();
        if (context == null) {
            return "";
        }
        uid = context.getSharedPreferences(SHRYOU_UID_PREF, Context.MODE_PRIVATE)
                .getString(game, "");
        if (uid != null && !uid.isEmpty()) {
            SHRYOU_UIDS.put(game, uid);
            return uid;
        }
        return "";
    }

    /**
     * shryou（城主别慌）：登录链加密无法自行构造，改用流量学习到的 uid 直接调用明文
     * 上报接口 {@code docking/lewan/aliReport}（字段与游戏自身上报一致）。
     */
    private static boolean reportShryou(Meta meta, String appId, String action,
                                        Map<String, String> headers) throws Exception {
        String game = MiniGameTaskTable.param(appId, "game");
        String uid = shryouUid(game);
        if (uid.isEmpty()) {
            Log.i("游戏任务 厂商上报[" + meta.name + "] 跳过：还没有学到shryou uid，"
                    + "先手动打开一次游戏即可自动学习，game=" + game);
            return false;
        }
        String submitUrl = tableUrl(appId, "submit", 0,
                "https://tf-api.shryou.com/docking/lewan/aliReport");
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
        JSONObject body = new JSONObject();
        body.put("game", game);
        body.put("uid", uid);
        body.put("action_code", action);
        body.put("action_finish_date", now);
        body.put("action_finish_channel", meta.channel);
        // 同一次补报会连发多次（times 次），加 nanoTime 保证 out_biz_no 唯一，避免被服务端幂等去重
        body.put("out_biz_no", uid + "_" + action + "_" + now + "_" + System.nanoTime());
        Map<String, String> jsonHeaders = new LinkedHashMap<>(headers);
        jsonHeaders.put("content-type", "application/json; charset=utf-8");
        String resp = post(submitUrl, body.toString(), jsonHeaders);
        boolean ok = resp.contains("\"code\":0");
        Log.i("游戏任务🎮厂商上报[" + meta.name + "]#shryou action=" + action
                + " uid=" + uid + " 结果=" + (ok ? "成功" : "失败，响应=" + resp));
        Log.record("游戏任务🎮厂商上报[" + meta.name + "]#" + (ok ? "成功" : "失败"));
        return ok;
    }

    /** 以 application/x-www-form-urlencoded 提交表单字段（值逐个 urlencode）。 */
    private static String postForm(String url, Map<String, String> params,
                                   Map<String, String> headers) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(entry.getKey(), "UTF-8")).append('=')
                    .append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(), "UTF-8"));
        }
        Map<String, String> formHeaders = new LinkedHashMap<>(headers);
        formHeaders.put("content-type", "application/x-www-form-urlencoded");
        return post(url, sb.toString(), formHeaders);
    }

    /**
     * nebula737（一步两步）签名，对齐原版 QuickJS 闭包 Ea[58]：
     * <pre>
     *   c = {...a}; delete c.sign
     *   s = Object.keys(c).sort().map(k => k + "=" + String(c[k])).join("&amp;")
     *   s = encodeURIComponent(s).replace(/[!'()*]/g, d => "%" + d.charCodeAt(0).toString(16).toUpperCase())
     *   return md5(s + "&amp;" + appKey).toLowerCase()
     * </pre>
     * 核心：先拼原始 k=v&amp; 整串，再对整串做 URL 编码（=、&amp; 也会被编码为 %3D、%26）。
     */
    private static String nebulaSign(Map<String, String> params, String appKey) throws Exception {
        // 1. 复制并移除 sign
        Map<String, String> c = new LinkedHashMap<>(params);
        c.remove("sign");
        // 2. 按 key 排序，拼接原始 k=v&k=v
        List<String> keys = new ArrayList<>(c.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            if (sb.length() > 0) sb.append('&');
            sb.append(k).append('=').append(c.get(k));
        }
        // 3. 对整串做 encodeURIComponent 等价编码
        //    URLEncoder 会把空格变 +、* 不编码，需修正；= & 会被编码为 %3D %26（正是目标）
        String s = URLEncoder.encode(sb.toString(), "UTF-8")
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
        // 4. ! ' ( )  URLEncoder 已编码为大写 %21 %27 %28 %29，与原版 replace 结果一致
        // 5. 拼接 appKey，md5（Java md5 已小写，对应原版 .toLowerCase()）
        return md5(s + "&" + appKey);
    }

    private static String md5(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        return hex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String randomHex(int length) {
        String hex = "0123456789abcdef";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(hex.charAt(RANDOM.nextInt(hex.length())));
        }
        return sb.toString();
    }

    private static String randomHexUpper(int length) {
        return randomHex(length).toUpperCase(Locale.ROOT);
    }

    /** 在 JSON 里递归找第一个命中 key 的非空字符串（兼容 data/result 包装）。 */
    private static String findString(String json, String... keys) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        try {
            return findString(new JSONObject(json), keys);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String findString(Object node, String... keys) {
        if (node instanceof JSONObject) {
            JSONObject jo = (JSONObject) node;
            for (String key : keys) {
                String value = jo.optString(key);
                if (!value.isEmpty()) {
                    return value;
                }
            }
            JSONArray names = jo.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String found = findString(jo.opt(names.optString(i)), keys);
                    if (!found.isEmpty()) {
                        return found;
                    }
                }
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                String found = findString(array.opt(i), keys);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return "";
    }

    private static String post(String url, String body, Map<String, String> headers) throws Exception {
        return request("POST", url, body, headers);
    }

    private static String request(String method, String url, String body,
                                  Map<String, String> headers) throws Exception {
        // 完整提交数据（链接 + 请求头 + 请求体）与完整响应（状态码 + 响应体）写入 debug，
        // 便于逐条核对
        Log.i("厂商请求 " + method + " " + url
                + "\n请求头: " + headers
                + "\n参数: " + body);
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        if (body != null && !body.isEmpty()) {
            conn.setDoOutput(true);
        }
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        if (body != null && !body.isEmpty()) {
            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(body);
            }
        }
        int code = conn.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 200 && code <= 299 ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();
        Log.i("厂商响应 " + method + " " + url + " HTTP " + code + "\n数据: " + sb);
        return sb.toString();
    }
}
