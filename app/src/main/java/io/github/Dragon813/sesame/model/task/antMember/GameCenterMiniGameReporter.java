package io.github.Dragon813.sesame.model.task.antMember;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import io.github.Dragon813.sesame.hook.ApplicationHook;
import io.github.Dragon813.sesame.hook.AuthCodeHelper;
import io.github.Dragon813.sesame.model.task.antGame.MiniGameVendorReporter;
import io.github.Dragon813.sesame.util.Log;
import io.github.Dragon813.sesame.util.TimeUtil;
import io.github.Dragon813.sesame.util.idMap.UserIdMap;

/**
 * 游戏中心「赚现金任务」里各小游戏厂商的进度上报适配。
 * <p>
 * 移植自 Sesame-AG 的同名实现：每个厂商一套 HTML 接口，拿支付宝动态授权的 authCode 换取该厂商的
 * openid / token，然后按任务要求次数循环上报「通关/消除/升级…」动作，支付宝侧再据此记账。
 */
public class GameCenterMiniGameReporter {

    private static final String TAG = "GameCenterMiniGameReporter";

    /** 支付宝游戏中心赚现金统一频道。 */
    public static final String CHANNEL = "P2E_GAME_TRAN_CHANNEL";

    /**
     * 一次性渠道覆盖：同一款游戏在不同任务体系下（游戏中心 / 芝麻炼金金豆夺宝 …）要填不同的
     * channel，调用方在 report 前后设置即可，平时为 null（用 Spec 自带频道）。
     */
    private static String channelOverride = null;

    public static void setChannelOverride(String channel) {
        channelOverride = (channel == null || channel.isEmpty()) ? null : channel;
    }

    public static String channelOf(Spec spec) {
        return channelOverride != null ? channelOverride : spec.channel;
    }

    public enum Vendor {
        DIANWANSHIDAI("dianwanshidai"),
        ASLK_GAME("gamesapi2"),
        DEVOURAD("devourad"),
        RAIYANTWAY("raiyantway"),
        MMHYGAME("mmhygame"),
        FLYSHEEP("flysheep"),
        MIHUANGAME("mihuangame"),
        IGAME58("igame58"),
        CPCHX("cpchxapi"),
        HALIGAME("haligame"),
        ASKB48("askb48"),
        JINGYOU("jingyou"),
        ULTRA("ultra");

        public final String label;

        Vendor(String label) {
            this.label = label;
        }
    }

    public static final class Spec {
        public final String appId;
        public final String title;
        public final Vendor vendor;
        public final String action;
        /** 备用 action 编码：部分游戏服务端白名单里的拼音写法与任务下发的不同（如 duju/duiju）。 */
        public final String altAction;
        public final String channel;
        public final String version;
        public final String gid;
        public final String bgid;
        public final String secret;
        public final String propertyKey;
        public final String umid;
        public final int gameVersion;
        /** 厂商主机名（部分游戏与同族不同域，如赵云与阿斗走 api01.mihuangame.com）。 */
        public final String host;
        /** 自定义上报路径（相对 /api/v2/ 之下，如 sys/alipay/submit）。 */
        public final String submitPath;
        /**
         * 部分厂商（mihuangame 系列）登录体必须带 channelAppId，值随游戏/平台不同，
         * 例：躺平发育在支付宝通道是 529 ���微信 536）。0 表示不发送该字段。
         */
        public final int channelAppId;
        /**
         * 厂商 SDK 的 packageType（仅 mmhygame 系列用）。取自包体 engine/game.js 的
         * GAME_INFO_DATA[CHANNEL_GAMETYPE.xxx].packageType，缺了服务端直接回 not found packageType:0。
         */
        public final int packageType;

        Spec(String appId, String title, Vendor vendor, String action) {
            this(appId, title, vendor, action, "", CHANNEL, "", "", "", "", "", "", 0);
        }

        Spec(String appId, String title, Vendor vendor, String action, String altAction,
             String channel, String version, String gid, String bgid, String secret,
             String propertyKey, String umid, int gameVersion) {
            this(appId, title, vendor, action, altAction, channel, version, gid, bgid, secret,
                    propertyKey, umid, gameVersion, "", "");
        }

        Spec(String appId, String title, Vendor vendor, String action, String altAction,
             String channel, String version, String gid, String bgid, String secret,
             String propertyKey, String umid, int gameVersion, String host, String submitPath) {
            this(appId, title, vendor, action, altAction, channel, version, gid, bgid, secret,
                    propertyKey, umid, gameVersion, host, submitPath, 0);
        }

        Spec(String appId, String title, Vendor vendor, String action, String altAction,
             String channel, String version, String gid, String bgid, String secret,
             String propertyKey, String umid, int gameVersion, String host, String submitPath,
             int channelAppId) {
            this(appId, title, vendor, action, altAction, channel, version, gid, bgid, secret,
                    propertyKey, umid, gameVersion, host, submitPath, channelAppId, 0);
        }

        Spec(String appId, String title, Vendor vendor, String action, String altAction,
             String channel, String version, String gid, String bgid, String secret,
             String propertyKey, String umid, int gameVersion, String host, String submitPath,
             int channelAppId, int packageType) {
            this.appId = appId;
            this.title = title;
            this.vendor = vendor;
            this.action = action;
            this.altAction = altAction;
            this.channel = channel;
            this.version = version;
            this.gid = gid;
            this.bgid = bgid;
            this.secret = secret;
            this.propertyKey = propertyKey;
            this.umid = umid;
            this.gameVersion = gameVersion;
            this.host = host == null ? "" : host;
            this.submitPath = submitPath == null ? "" : submitPath;
            this.channelAppId = channelAppId;
            this.packageType = packageType;
        }

        Spec withAction(String action) {
            if (action == null || action.isEmpty() || action.equals(this.action)) {
                return this;
            }
            return new Spec(appId, title, vendor, action, altAction, channel, version, gid, bgid,
                    secret, propertyKey, umid, gameVersion, host, submitPath, channelAppId, packageType);
        }
    }

    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

    static {
        put(new Spec("2021006131625225", "玩个倒水", Vendor.DIANWANSHIDAI,
                "wgds_game_pass_every_1", "", CHANNEL, "", "", "6798", "", "", "", 0));
        put(new Spec("2021005176645204", "玩解压俄罗斯方块", Vendor.JINGYOU,
                "jyelsfk_game_pass_every_1", "", CHANNEL, "7.2.5", "", "", "", "", "", 0));
        put(new Spec("2021005132680209", "这关我很行", Vendor.DIANWANSHIDAI,
                "zgwhx_game_level_every_1", "", CHANNEL, "0.2.2602130949.23", "", "6560", "", "", "", 0));
        put(new Spec("2021004128666655", "玩玩梗高手", Vendor.ASLK_GAME,
                "wggs_game_pass_every_1", "", CHANNEL, "1.0.4", "zfb_wggs", "", "", "", "", 0));
        put(new Spec("2021000111632448", "玩找不停", Vendor.ASLK_GAME,
                "zbt_game_pass_every_1", "", CHANNEL, "1.0.0", "zfb_zbt", "", "", "", "", 0));
        put(new Spec("2021006105651938", "玩智商乐消消", Vendor.DEVOURAD,
                "zslxx_game_pass_every_1", "zslxx_game_xiaochu_every_10", CHANNEL, "",
                "", "", "87e3d612cefbe59822d5c6cbde82a74f", "", "", 0));
        put(new Spec("2060170000399302", "玩占城大师", Vendor.RAIYANTWAY,
                "zcds_game_duju_every_1", "zcds_game_duiju_every_1", CHANNEL, "", "", "", "", "", "", 0));
        // 简直是天才：hbgwb SDK 的 JZSTC 条目 —— packageType=2、version=1.0.0，
        // 但 serverUrl 是 lgsg.xyx.mmhygame.com/lgsg（同族的合不过我吧走 sgxc），
        // 域名与 packageType 必须成对，否则服务端回 not found packageType:0。
        put(new Spec("2021006137600649", "简直是天才", Vendor.MMHYGAME,
                "jzstc_game_pass_every_1", "jzstc_game_xiaochu_every_10", CHANNEL, "1.0.0",
                "", "", "", "", "", 0, "lgsg.xyx.mmhygame.com", "lgsg", 0, 2));
        put(new Spec("2060170000375112", "玩猪了个猪", Vendor.FLYSHEEP,
                "zlgz_game_pass_every_1", "", CHANNEL, "", "", "", "", "", "", 0));
        put(new Spec("2021004123689742", "玩躺平发育", Vendor.MIHUANGAME,
                "tpfy_game_shengji_every_1", "", CHANNEL, "", "", "", "", "shengji_every", "", 0,
                "", "", 529));
        put(new Spec("2021004134649102", "玩货柜消消消", Vendor.IGAME58,
                "hgxxx_game_game_pass_every_1", "hgqxx_game_pass_every_1", CHANNEL, "25",
                "", "", "", "", "64dca6225488fe7b3af4f865", 25));
        // 点点消不停：umid/uuid 取自 2026-11-18 22:44 真机抓包（Reqable CA + mitmproxy 反代）
        // umid 是 igame58 的硬性请求头（服务端「Missing request header 'umid'」），且绑定「设备×游戏」
        put(new Spec("2060170000365575", "点点消不停", Vendor.IGAME58,
                "ddxbt_game_pass_every_1", "ddxbt_game_xiaochu_every_5", CHANNEL, "9",
                "", "", "", "", "qgaem_60de8c4e8a102159db85d599", 9));
        // 玩玩不过我呀：2026-11-19 20:5x 透明代理抓到 aa.igame58.com 的 getOpenId/login/adRecord，
        // 请求头带 umid=qgame_64361083ba6a5259c4360029、uuid=POxoMvPCIAcIrGOXX6zIqNPSBt6t12cK、gameVersion=14
        put(new Spec("2060170000363633", "玩玩不过我呀", Vendor.IGAME58,
                "wbgwy_game_pass_every_1", "", CHANNEL, "14",
                "", "", "", "", "qgame_64361083ba6a5259c4360029", 14));
        // 茶叶蛋大冒险：ultralisk 家族。2026-11-19 21:0x 透明代理抓到
        // POST https://common.ultralisk.cn/sdkserver/userlogin {token:<authCode>,channel:"h5alipay",gameId:1200}
        //   -> {"code":1,"openId":"081VNFbO…"}
        // POST https://common.ultralisk.cn/sdkserver/h5alipayuploadgamebehaviour
        //   {gameId,openId,actionCode,actionFinishChannel} -> {"code":1,"msg":"success"}
        put(new Spec("2021003193629997", "玩茶叶蛋大冒险", Vendor.ULTRA,
                "cyddmx_game_pass_level_1", "", CHANNEL, "", "1200", "", "", "", "", 0));
        put(new Spec("2060170000357475", "疯狂来解压", Vendor.CPCHX,
                "fkljy_game_pass_every_1", "", CHANNEL, "0.2.2604020945.51",
                "", "", "", "", "", 0));
        put(new Spec("2021005139650112", "打个螺丝", Vendor.HALIGAME,
                "dgls_game_level_every_1", "dgls_game_xiaochu_every_5", CHANNEL,
                "0.2.2602281817.3", "", "", "", "", "", 0));
        put(new Spec("2021006163641057", "玩遮天：帝路争锋", Vendor.ASKB48,
                "ztdlzf_game_zhaohuan_every_10", "", CHANNEL, "0.2.2507101829.6",
                "0G18JEWF0X5V3D3FE6", "", "", "", "", 0));
        put(new Spec("2060170000399014", "挪了下车", Vendor.DIANWANSHIDAI,
                "nlxc_game_pass_every_1", "", CHANNEL, "0.2.2608251627.21",
                "", "6969", "", "", "", 0));
        put(new Spec("2021004164603638", "拿下100关", Vendor.ASLK_GAME,
                "nxybg_game_pass_every_1", "", CHANNEL, "1.0.0",
                "zfb_nx100g", "", "", "", "", 0));
        put(new Spec("2021006148677438", "欢乐拼拼豆", Vendor.ASLK_GAME,
                "klppd_game_pass_every_1", "", CHANNEL, "1.2.0",
                "zfb_klppd", "", "", "", "", 0));
        // 赵云与阿斗：mihuangame 变体，登录与上报都在 api01.mihuangame.com，路径 sys/alipay/submit
        put(new Spec("2021006159622573", "玩赵云与阿斗", Vendor.MIHUANGAME,
                "zyyad_game_duiju_every_1", "", CHANNEL, "", "", "", "", "", "", 0,
                "api01.mihuangame.com", "sys/alipay/submit", 1229));
        // 芝麻炼金金豆夺宝里的小游戏（appId 与游戏中心同一个，频道由调用方覆盖）
        put(new Spec("2021005112626489", "合不过我吧", Vendor.MMHYGAME,
                "hbgwb_game_pass_every_1", "hbgwb_game_xiaochu_every_10", CHANNEL, "1.0.0",
                "", "", "", "", "", 0, "sgxc.xyx.mmhygame.com", "sgxc", 0, 2));
    }

    private static void put(Spec spec) {
        SPECS.put(spec.appId, spec);
    }

    /**
     * authCode 取不到时使用的本机缓存凭据（key 形如 appId:字段名）。
     * 这些值来自真实抓包，有效期有限（openid 一般一年、token 几天），过期后需要重新抓包补充。
     */
    private static final Map<String, String> FALLBACK = new HashMap<>();

    /**
     * 缓存凭据的归属账号（uid）。key 与 FALLBACK 相同。
     * 只有当前登录账号与这里的 uid 一致时才会使用对应凭据，避免换账号后把进度记到别人头上。
     * 未登记的条目一律不使用（日志会提示「未登记归属账号」）。
     */
    private static final Map<String, String> FALLBACK_UID = new HashMap<>();

    private static final java.util.Set<String> FALLBACK_SKIPPED = new java.util.HashSet<>();

    static {
        // 这关我很行（dianwanshidai，2021005132680209）的 openid 是主号 2088722733410814
        // 在 2026-11-18 12:25:27 自己打开该游戏时抓到的，只登记给主号。
        FALLBACK_UID.put("2021005132680209:openid", "2088722733410814");
        // 点点消不停（igame58，2060170000365575）的 openid 属主号
        FALLBACK_UID.put("2060170000365575:openid", "2088722733410814");
        FALLBACK_UID.put("2060170000365575:uuid", "2088722733410814");
    }

    static {
        FALLBACK.put("2021006131625225:openid", "081ptbYq2q4ioXSbhBRVdfv_37dvMhfwzjmhrZNV5w03uwe");
        FALLBACK.put("2021006105651938:openid", "081B6djq9BfWdSyW5j-Q2m0W_WHFf0oJ0L848q8Saz0TJUd");
        FALLBACK.put("2060170000399302:openid", "081sZVttVb8E_5qaXoGVflkUWuVhDNBAmgfPjgGPltgtFge");
        FALLBACK.put("2021006137600649:token", "eyJhbGciOiJIUzI1NiJ9.eyJpZCI6IjM5OTAwNzQ4NDM1ODAxMSIsImV4cCI6MTc5NDk3NDM5OX0.x");
        FALLBACK.put("2060170000375112:user_id", "982008103580");
        FALLBACK.put("2060170000375112:token", "composeBd8b33fba9e1d4576a3495d4d97afcX81");
        FALLBACK.put("2060170000375112:openid", "081nQf2SIY-Y2Dl2XZrgHpiWRMzJXD17Kx0MiGm_ioQPP0c");
        FALLBACK.put("2021004123689742:authentication",
                "3zQkZUBny+/iyuCAfb5vliKzhpXujzjDvsLG2tNzsGPBAQ/oJWqiOzyhITgj6WmM/IKZmtw7JrHJLhVjn9pVJOzmLUtmCQz9LktPRAdFyfk=");
        FALLBACK.put("2021004134649102:openid", "0815qOrxNSN-GP4NKl_UZapwu_87h3755zOvzlc4HMuKW0d");
        FALLBACK.put("2021004134649102:uuid", "FxQnxuyz8aF5kGkU8komcVDhgmqdGCWa");
        // 点点消不停：2026-11-18 22:42 真机抓包（主号），openid/uuid 随账号与安装变化，仅作登录失败兜底
        FALLBACK.put("2060170000365575:openid", "081Qbc4i6knCjFqGwcImCMKYIJI9wSbzFZR4uEC7AaFvQk9");
        FALLBACK.put("2060170000365575:uuid", "cgY0htl402bFStxEXr2Gf1GeFKcGlz10");
        FALLBACK.put("2060170000357475:token",
                "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiIyMTIyNjQ3ODI5Nzc1NTI3OTM3IiwiaWF0IjoxNzk0OTEzNjU0LCJzdWIiOiIwNTN2VjVVbVYzWERTc1B1dzZzX2VTQkJCZmVydGhqVy1ZaVZYbEV1cE43WUZvOCIsImV4cCI6MTc5NTUxODQ1NH0.2HbRIfn3I41km5jYMS2KRkVWakZEurh8GAG5oUbyVXg");
        FALLBACK.put("2021005139650112:open_id", "053qzI_B8cgyDhoTBmK90y6j3MI43kdSu--_Ni_SA4J69Y7");
        FALLBACK.put("2021006163641057:open_id", "053QLo4EUH2NhfpkBTmpPXV6Z8_NqyTMi0gdN7wNaOEfnge");
        FALLBACK.put("2060170000399014:openid", "053ZYVk0fHJOnkTDyyUtG-fUTXAYa_chkB6_8DbUni2hPQ8");
        FALLBACK.put("2021004164603638:token",
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhZGRfdGltZSI6MTc5MjI4NzkwMiwiZXhwaXJlX2F0IjoiMjAyNi0xMS0yNCAxOTozNzo0MiIsImdpZCI6InpmYl9ueDEwMGciLCJyZ3YiOiIwLjIuMjYwNjExMjAyMi4zOSIsInNlY3JldCI6Imc2eTJhcGhnOTA5M2E1N20iLCJ1aWQiOjM1ODk2MDEsInVuaWQiOiIifQ.WX2r0cvqlqVJv4iYRAQ0844Wyr3jwo0Mx0JCxF--XQI");
        FALLBACK.put("2021006148677438:token",
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhZGRfdGltZSI6MTc5MzE1MDMwMSwiZXhwaXJlX2F0IjoiMjAyNi0xMS0yNCAxOTozODowNyIsImdpZCI6InpmYl9rbHBwZCIsInJndiI6IjAuMi4yNjA3MjExNzQwLjE5Iiwic2VjcmV0Ijoicm5hYm91ODV3dnZoYjhzYiIsInVpZCI6MjgyOTI2OCwidW5pZCI6IiJ9.Kk9IZBpGUT7x1mwYMHKmb3scLtyCKpMhQJFMw3LMrdA");
    // 这关我很行（dianwanshidai，2021005132680209）：动态登录目前被服务端判 code-invalid，
        // 这里存的是主号 2088722733410814 自己打开该游戏时抓到的 openid（2026-11-18 12:25:27）。
        // 换账号使用时会记到主号头上，日志里会提示「使用本机缓存凭据」，届时需要用该账号自己的 openid 替换。
        FALLBACK.put("2021005132680209:openid", "08198CP8VVw1d7FCD7LOoia5cMTat8T7Jm8rBAyzMqBWC8a");
    }

    public static Spec specOf(String appId) {
        if (appId == null) {
            return null;
        }
        return SPECS.get(appId.trim());
    }

    public static boolean supports(String appId) {
        return specOf(appId) != null;
    }

    public static String supportedTitles() {
        StringBuilder sb = new StringBuilder();
        for (Spec spec : SPECS.values()) {
            if (sb.length() > 0) {
                sb.append('、');
            }
            sb.append(spec.title);
        }
        return sb.toString();
    }

    /**
     * 从任务描述里取需要完成的次数，如「通过5关」→ 5、「升级30次建筑」→ 30、「完成5次对局」→ 5。
     */
    public static int requiredTimes(String actionDesc) {
        if (actionDesc == null) {
            return 1;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[0-9]+").matcher(actionDesc);
        while (matcher.find()) {
            try {
                int value = Integer.parseInt(matcher.group());
                if (value >= 1 && value <= 500) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return 1;
    }

    /**
     * 循环上报进度。
     *
     * @return 成功上报的次数
     */
    public static int report(Spec spec, int times) {
        if (spec == null || times <= 0) {
            return 0;
        }
        try {
            switch (spec.vendor) {
                case DIANWANSHIDAI:
                    return reportDianwanshidai(spec, times);
                case ASLK_GAME:
                    return reportGamesapi2(spec, times);
                case DEVOURAD:
                    return reportDevourad(spec, times);
                case RAIYANTWAY:
                    return reportRaiyantway(spec, times);
                case MMHYGAME:
                    return reportMmhygame(spec, times);
                case FLYSHEEP:
                    return reportFlysheep(spec, times);
                case MIHUANGAME:
                    return reportMihuangame(spec, times);
                case IGAME58:
                    return reportIgame58(spec, times);
                case CPCHX:
                    return reportCpchx(spec, times);
                case HALIGAME:
                    return reportHaligame(spec, times);
                case ASKB48:
                    return reportAskb48(spec, times);
                case JINGYOU:
                    return reportJingyou(spec, times);
                case ULTRA:
                    return reportUltra(spec, times);
                default:
                    return 0;
            }
        } catch (Throwable t) {
            Log.i(TAG, "report " + spec.title + " err:");
            Log.printStackTrace(TAG, t);
            return 0;
        }
    }

    // ---------------------------------------------------------------- 各厂商实现

    private static int reportDianwanshidai(Spec spec, int times) {
        String base = "https://platformv2.dianwanshidai.com";
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("appid", spec.appId);
        headers.put("app-id", spec.appId);
        headers.put("bgid", spec.bgid);
        headers.put("x-release-type", "ONLINE");
        headers.put("referer", referer(spec.appId, spec.version));
        headers.put("User-Agent", userAgent());

        String openId = "";
        String lastLogin = "";
        // 授权码是一次性的：每个候选体都必须重新取码，否则第一个候选失败就把码烧掉，后面全回 code-invalid
        String[] loginBodyTemplates = new String[]{
                // 包体原文：send(login, {code: this.code, query: my.getLaunchOptionsSync()})
                "{\"code\":\"%s\",\"query\":{\"appId\":\"" + spec.appId + "\"}}",
                "{\"code\":\"%s\",\"openid\":\"\"}",
                "{\"code\":\"%s\"}"
        };
        for (String template : loginBodyTemplates) {
            String code = authCode(spec.appId);
            if (code.isEmpty()) {
                break;
            }
            String body = String.format(template, code);
            String resp = http(base + "/api_v2/v1/user/login", headers, body, "POST");
            lastLogin = (lastLogin.isEmpty() ? "" : lastLogin + " | ") + trim(resp);
            openId = valueByPath(resp, "data", "openid");
            if (openId.isEmpty()) {
                openId = valueByPath(resp, "data", "openId");
            }
            if (!openId.isEmpty()) {
                break;
            }
        }
        if (openId.isEmpty()) {
            openId = fallback(spec.appId, "openid");
            if (openId.isEmpty()) {
                Log.i("游戏中心🎮[" + spec.title + "]登录响应：" + trim(lastLogin));
                Log.i("游戏中心🎮[" + spec.title + "]登录未取到openid，跳过(bgid=" + spec.bgid + ",action=" + spec.action + ")");
                return 0;
            }
            Log.i("游戏中心🎮[" + spec.title + "]登录响应：" + trim(lastLogin));
            Log.i("游戏中心🎮[" + spec.title + "]登录未取到openid，使用本机缓存凭据");
        }

        Map<String, String> reportHeaders = new LinkedHashMap<>(headers);
        reportHeaders.put("Content-Type", "application/json");
        logLoginIdentity(spec, "openid", openId);
        int done = 0;
        for (int i = 0; i < times; i++) {
            String body = MiniGameVendorReporter.dianwanshidaiSubmitBody(openId, spec.action,
                    channelOf(spec));
            Log.i("游戏中心🎮[" + spec.title + "]上报请求：" + body
                    + " | headers=" + reportHeaders + " | base=" + base);
            String resp = http(base + "/api_v2/v1/ZfbSop/behavior", reportHeaders, body, "POST");
            Log.i("游戏中心🎮[" + spec.title + "]上报响应：" + clip2(resp));
            if (resp != null && resp.contains("\"code\":200")) {
                done++;
                progress(spec, done, times);
            } else {
                Log.i("游戏中心🎮[" + spec.title + "]上报失败：" + trim(resp));
                break;
            }
            TimeUtil.sleep(1200);
        }
        return done;
    }

    private static int reportGamesapi2(Spec spec, int times) {
        String code = authCode(spec.appId);
        if (code.isEmpty()) {
            Log.i("游戏中心🎮[" + spec.title + "]登录未取到authCode，跳过");
            return 0;
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("x-release-type", "ONLINE");
        headers.put("User-Agent", userAgent());
        String loginResp = http("https://gamesapi2.aslk2018.com/v2/game/login", headers,
                MiniGameVendorReporter.gamesapi2LoginBody(code, spec.gid, spec.version, spec.version,
                        System.currentTimeMillis() + "_" + (new Random().nextInt(90) + 10)), "POST");
        String token = valueByPath(loginResp, "data", "token");
        if (token.isEmpty()) {
            Log.i("游戏中心🎮[" + spec.title + "]登录失败：" + trim(loginResp));
            return 0;
        }

        headers.put("authorization", token);
        headers.put("referer", referer(spec.appId, spec.version));
        int done = 0;
        for (int i = 0; i < times; i++) {
            String body = MiniGameVendorReporter.gamesapi2SubmitBody(spec.action, channelOf(spec),
                    spec.gid, spec.version, spec.version,
                    System.currentTimeMillis() + "_" + (new Random().nextInt(90) + 10));
            String resp = http("https://gamesapi2.aslk2018.com/v2/zfb/taskReport", headers, body, "POST");
            if (resp != null && resp.contains("\"code\":1")) {
                done++;
                progress(spec, done, times);
            } else {
                Log.i("游戏中心🎮[" + spec.title + "]上报失败：" + trim(resp));
                break;
            }
            TimeUtil.sleep(1200);
        }
        return done;
    }

    private static int reportDevourad(Spec spec, int times) {
        String code = authCode(spec.appId);
        String secret = spec.secret;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", userAgent());

        // 协议实现与任务列表共用，见 MiniGameVendorReporter.devouradLogin / devouradSubmit
        String openId = "";
        if (!code.isEmpty()) {
            try {
                String resp = MiniGameVendorReporter.devouradLogin(spec.appId, secret, code, headers, true);
                openId = valueByPath(resp, "data", "open_id");
                if (openId.isEmpty()) {
                    openId = valueByPath(resp, "data", "openId");
                }
            } catch (Throwable t) {
                Log.i("游戏中心🎮[" + spec.title + "]devourad 登录异常: " + t);
            }
        }
        if (openId.isEmpty()) {
            openId = fallback(spec.appId, "openid");
            if (openId.isEmpty()) {
                Log.i("游戏中心🎮[" + spec.title + "]登录未取到openid，跳过");
                return 0;
            }
            Log.i("游戏中心🎮[" + spec.title + "]登录未取到openid，使用本机缓存凭据");
        }

        int done = 0;
        for (int i = 0; i < times; i++) {
            String resp;
            try {
                resp = MiniGameVendorReporter.devouradSubmit(spec.appId, secret, channelOf(spec),
                        spec.action, openId, headers);
            } catch (Throwable t) {
                Log.i("游戏中心🎮[" + spec.title + "]上报异常: " + t);
                break;
            }
            if (resp != null && resp.contains("\"code\":1")) {
                done++;
                progress(spec, done, times);
            } else {
                Log.i("游戏中心🎮[" + spec.title + "]上报失败：" + trim(resp));
                break;
            }
            TimeUtil.sleep(1200);
        }
        return done;
    }

    private static int reportRaiyantway(Spec spec, int times) {
        String code = authCode(spec.appId);
        String openId = "";
        if (!code.isEmpty()) {
            String url = "https://cbqta.rayiantway.com:8525/MiniGameServer/minigame/login.do?"
                    + MiniGameVendorReporter.raiyantwayLoginQuery(spec.appId, "31", code);
            String resp = http(url, null, null, "GET");
            openId = valueByPath(resp, "openId");
            if (openId.isEmpty()) {
                openId = valueByPath(resp, "data", "openId");
            }
        }
        if (openId.isEmpty()) {
            openId = fallback(spec.appId, "openid");
            if (openId.isEmpty()) {
                Log.i("游戏中心🎮[" + spec.title + "]登录未取到openid，跳过");
                return 0;
            }
            Log.i("游戏中心🎮[" + spec.title + "]登录未取到openid，使用本机缓存凭据");
        }

        String action = spec.action;
        boolean switched = false;
        int done = 0;
        int guard = 0;
        while (done < times && guard++ < times * 2 + 5) {
            String encoded = MiniGameVendorReporter.raiyantwayEncode(
                    MiniGameVendorReporter.raiyantwayPlainBody(spec.appId, openId, action,
                            channelOf(spec), UUID.randomUUID().toString().replace("-", "")));
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/x-www-form-urlencoded");
            headers.put("User-Agent", userAgent());
            String body = "ENCODE_DATA=" + urlEncode(encoded);
            String resp = http("https://cbqta.rayiantway.com:8525/MiniGameServer/alipay/reportGameAction",
                    headers, body, "POST");
            if (resp != null && resp.contains("\"code\":\"0\"")) {
                done++;
                progress(spec, done, times);
                TimeUtil.sleep(1200);
                continue;
            }
            if (resp != null && resp.contains("白名单") && !switched && !spec.altAction.isEmpty()) {
                Log.i("游戏中心🎮[" + spec.title + "]行动编码未在白名单，改用备用编码 " + spec.altAction);
                action = spec.altAction;
                switched = true;
                continue;
            }
            Log.i("游戏中心🎮[" + spec.title + "]上报失败：" + trim(resp));
            break;
        }
        return done;
    }

    private static int reportMmhygame(Spec spec, int times) {
        String code = authCode(spec.appId);
        String token = "";
        String lastLogin = "";
        // hbgwb SDK：serverUrl 取自包体 GAME_INFO_DATA[CHANNEL_GAMETYPE.xxx].serverUrl，
        // 这一族并不统一 —— 简直是天才（JZSTC）在 lgsg，合不过我吧（HBGWB）在 sgxc。
        // packageType 同理（两个都是 2），域名与 packageType 错配时服务端回 not found packageType:0。
        String host = spec.host == null || spec.host.isEmpty() ? "sgxc.xyx.mmhygame.com" : spec.host;
        String basePath = spec.submitPath == null || spec.submitPath.isEmpty() ? "sgxc" : spec.submitPath;
        String base = "https://" + host + "/" + basePath;
        int pkgType = spec.packageType;
        String ver = spec.version == null || spec.version.isEmpty() ? "1.0.0" : spec.version;
        if (!code.isEmpty()) {
            String[] bodies = new String[]{
                    // 包体原文（engine/game.js 的 zfb.login 分支）：
                    // h = {packageType:l, code:authCode, version:App.VERSION, type:ELoginType.ZFBLogin=7}
                    MiniGameVendorReporter.mmhygameLoginBody(code, ver, pkgType),
                    "{\"packageType\":" + pkgType + ",\"code\":\"" + code + "\",\"version\":\"" + ver + "\",\"type\":7,\"serverId\":0}",
                    "{\"code\":\"" + code + "\",\"appId\":\"" + spec.appId + "\"}",
                    "{\"code\":\"" + code + "\"}"
            };
            Map<String, String> formHeaders = new LinkedHashMap<>();
            Map<String, String> jsonHeaders = new LinkedHashMap<>();
            jsonHeaders.put("Content-Type", "application/json");
            for (String body : bodies) {
                // 真机抓包（09:50:08）确认：简直是天才自己登录打的就是
                // https://lgsg.xyx.mmhygame.com/lgsg/login，响应根级 token + player.openId="2:<uid>"。
                // JSON 编码可被解析；表单编码会落到 openId is empty 分支，所以先试 JSON。
                String resp = http(base + "/login", jsonHeaders, body, "POST");
                lastLogin = (lastLogin.isEmpty() ? "" : lastLogin + " | ") + "json:" + resp;
                token = valueByPath(resp, "token");
                if (!token.isEmpty()) {
                    break;
                }
                resp = http(base + "/login", formHeaders, body, "POST");
                lastLogin = lastLogin + " | form:" + resp;
                token = valueByPath(resp, "token");
                if (!token.isEmpty()) {
                    break;
                }
            }
        }
        if (token.isEmpty()) {
            token = fallback(spec.appId, "token");
            if (token.isEmpty()) {
                Log.i("游戏中心🎮[" + spec.title + "]登录响应：" + trim(lastLogin));
                Log.i("游戏中心🎮[" + spec.title + "]登录未取到token，跳过(action=" + spec.action + ")");
                return 0;
            }
            Log.i("游戏中心🎮[" + spec.title + "]登录响应：" + trim(lastLogin));
            Log.i("游戏中心🎮[" + spec.title + "]登录未取到token，使用本机缓存凭据");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + token);
        headers.put("User-Agent", userAgent());
        int done = 0;
        logLoginIdentity(spec, "token", token);
        for (int i = 0; i < times; i++) {
            String payload = MiniGameVendorReporter.mmhygameSubmitBody(spec.action, channelOf(spec));
            String url = mmhyLink(base + "/api/reqZfbGameEvent", payload, headers);
            String resp = http(url, headers, payload, "POST");
            if (done == 0) {
                Log.i("游戏中心🎮[" + spec.title + "]上报响应：" + trim(resp));
            }
            if (isMmhySuccess(resp)) {
                done++;
                progress(spec, done, times);
            } else {
                Log.i("游戏中心🎮[" + spec.title + "]上报失败：" + trim(resp));
                break;
            }
            TimeUtil.sleep(1200);
        }
        // 2026-11-22 实测（炼金金豆 ZHIMA 任务）：厂商事件上报后支付宝不落账，
        // 真机抓包确认游戏本体启动会再打 reqUpdateData / reqActivity2Info /
        // reqZfbGamingCommunityAward（支付宝社区奖励查询），这组「进游戏」会话
        // 才触发厂商向支付宝补报。这里按同一顺序补发。
        if (done > 0) {
            mmhyEnterCalls(spec, base, headers);
        }
        return done;
    }

    /** mmhygame 启动会话：游戏启动时的三个 api 调用（mmhyLink 签名 + Bearer token）。 */
    private static void mmhyEnterCalls(Spec spec, String base, Map<String, String> headers) {
        try {
            String[] enterPaths = {
                    "/api/reqUpdateData",
                    "/api/reqActivity2Info",
                    "/api/reqZfbGamingCommunityAward"
            };
            for (String path : enterPaths) {
                String url = mmhyLink(base + path, "{}", headers);
                String resp = http(url, headers, "{}", "POST");
                Log.i("游戏中心🎮[" + spec.title + "]mmhygame 启动会话 " + path + " → " + trim(resp));
                TimeUtil.sleep(400);
            }
        } catch (Throwable t) {
            Log.i("游戏中心🎮[" + spec.title + "]mmhygame 启动会话异常: " + t);
        }
    }

    private static Map<String, String> getHeaders(Spec spec) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", userAgent());
        headers.put("x-release-type", "ONLINE");
        headers.put("referer", referer(spec.appId, spec.version));
        return headers;
    }

    /**
     * askb48（爱思客 SDKS：sdk.askb48.com）：get_ali_openid_1.php 用 auth_code 换 open_id →
     * ali_action_finish_channel.php 绑定频道 → ali_submit_1.php 上报行为。
     * 该 SDK 的 action 形如 <短名>_game_<动作>_every_N，每次提交代表 N 次行为，所以提交次数 = ceil(需要的次数 / N)。
     */
    private static int reportAskb48(Spec spec, int times) {
        String openId = "";
        String code = authCode(spec.appId);
        String gameid = spec.gid;
        if (!code.isEmpty() && !gameid.isEmpty()) {
            String url = "https://sdk.askb48.com/game/pc/get_ali_openid_1.php?code=" + code
                    + "&pf=zfb&gameid=" + gameid;
            String resp = http(url, getHeaders(spec), null, "GET");
            openId = valueByPath(resp, "alipay_system_oauth_token_response", "open_id");
            if (openId.isEmpty()) {
                openId = valueByPath(resp, "open_id");
            }
            if (openId.isEmpty()) {
                Log.i("游戏中心🎮[" + spec.title + "]登录响应：" + trim(resp));
            } else {
                http("https://sdk.askb48.com/game/pc/ali_action_finish_channel.php?action_finish_channel="
                        + channelOf(spec) + "&gameid=" + gameid + "&open_id=" + openId,
                        getHeaders(spec), null, "GET");
            }
        }
        if (openId.isEmpty()) {
            openId = fallback(spec.appId, "open_id");
            if (openId.isEmpty()) {
                Log.i("游戏中心🎮[" + spec.title + "]登录未取到openid，跳过");
                return 0;
            }
            Log.i("游戏中心🎮[" + spec.title + "]登录未取到openid，使用本机缓存凭据");
        }

        int every = 1;
        java.util.regex.Matcher em = java.util.regex.Pattern.compile("_every_(\\d+)$").matcher(spec.action);
        if (em.find()) {
            try {
                every = Math.max(1, Integer.parseInt(em.group(1)));
            } catch (Throwable ignored) {
            }
        }
        int submits = Math.max(1, (times + every - 1) / every);

        int done = 0;
        for (int i = 0; i < submits; i++) {
            String url = "https://sdk.askb48.com/game/pc/ali_submit_1.php?app_id=" + spec.appId
                    + "&open_id=" + openId
                    + "&action_code=" + spec.action
                    + "&action_finish_date=" + System.currentTimeMillis()
                    + "&action_finish_channel=" + channelOf(spec)
                    + "&gameid=" + gameid;
            String resp = http(url, getHeaders(spec), null, "GET");
            if (resp != null && resp.contains("\"code\":\"10000\"")) {
                done++;
                progress(spec, done, submits);
            } else {
                Log.i("游戏中心🎮[" + spec.title + "]上报失败：" + trim(resp));
                break;
            }
            TimeUtil.sleep(1200);
        }
                // 一次提交代表 every 次行为，调用方拿返回值与「需要的行为次数」比，所以换算回行为数
        return Math.min(times, done * every);
    }

    /** haligame：get_openid.php 用 auth_code 换 open_id → user_action_submit.php（表单）上报。 */
    private static int reportHaligame(Spec spec, int times) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("User-Agent", userAgent());
        headers.put("referer", referer(spec.appId, spec.version));

        String openId = "";
        String code = authCode(spec.appId);
        if (!code.isEmpty()) {
            // 包体原文：my.request({url: curDomain + "ajax/get_openid.php", data: {auth_code: resCode}})
            // my.request 默认 GET，参数走 query，所以这里必须先 GET（POST 一律回「缺少参数」）
            String getUrl = "https://zf.haligame.com/ajax/get_openid.php?auth_code=" + urlEncode(code);
            String getResp = http(getUrl, headers, null, "GET");
            openId = valueByPath(getResp, "data", "open_id");
            if (openId.isEmpty()) {
                openId = valueByPath(getResp, "open_id");
            }
            if (openId.isEmpty()) {
                try {
                    JSONObject node = new JSONObject(getResp).optJSONObject("data");
                    if (node != null) {
                        openId = node.optString("open_id", node.optString("openId", ""));
                    }
                } catch (Throwable ignored) {
                }
            }
            String resp = getResp;
            if (openId.isEmpty()) {
                Map<String, String> jsonHeaders = new LinkedHashMap<>(headers);
                jsonHeaders.put("Content-Type", "application/json; charset=utf-8");
                String plain = http("https://zf.haligame.com/ajax/get_openid.php", jsonHeaders,
                        MiniGameVendorReporter.haligameLoginBody(code), "POST");
                openId = valueByPath(plain, "data", "open_id");
                if (openId.isEmpty()) {
                    openId = valueByPath(plain, "open_id");
                }
                if (openId.isEmpty()) {
                    Log.i("游戏中心🎮[" + spec.title + "]登录响应：" + trim(resp) + " | " + trim(plain));
                }
            }
        }
        if (openId.isEmpty()) {
            openId = fallback(spec.appId, "open_id");
            if (openId.isEmpty()) {
                Log.i("游戏中心🎮[" + spec.title + "]登录未取到openid，跳过");
                return 0;
            }
            Log.i("游戏中心🎮[" + spec.title + "]登录未取到openid，使用本机缓存凭据");
        }

        int done = 0;
        logLoginIdentity(spec, "open_id", openId);
        for (int i = 0; i < times; i++) {
            String body = MiniGameVendorReporter.haligameSubmitBody(spec.action, channelOf(spec),
                    openId, true);
            String resp = http("https://zf.haligame.com/ajax/user_action_submit.php",
                    headers, body, "POST");
            if (resp != null && resp.contains("\"code\":\"10000\"")) {
                done++;
                progress(spec, done, times);
            } else {
                Log.i("游戏中心🎮[" + spec.title + "]上报失败：" + trim(resp));
                break;
            }
            TimeUtil.sleep(1200);
        }
        // 2026-11-22 实测（炼金金豆 ZHIMA 任务）：只做 action_submit 时厂商收下动作但支付宝不落账
        // （页面一直「去完成」），玩家用手机点「去完成」拉起游戏本体后立刻 FINISHED。
        // 抓包显示游戏启动会发三个无签名 GET（玩家数据/玩家回流/支付宝礼包查询），
        // 正是这组「进游戏」事件让厂商服务端向支付宝补报进度。这里按同一顺序补发。
        if (done > 0) {
            haligameEnterCalls(spec, openId, headers);
        }
        return done;
    }

    /** haligame 启动会话：真实游戏启动时的三个 GET（uid 即 open_id），触发厂商向支付宝补报。 */
    private static void haligameEnterCalls(Spec spec, String openId, Map<String, String> headers) {
        try {
            String uid = urlEncode(openId);
            String[] enterUrls = {
                    "https://server8.haligame.com:39110/get_user_data?uid=" + uid,
                    "https://zfbu2.haligame.com:39003/player_return?uid=" + uid,
                    "https://zfbgift.haligame.com:39667/get_alipay_gift_data?uid=" + uid
            };
            for (String url : enterUrls) {
                String resp = http(url, headers, null, "GET");
                Log.i("游戏中心🎮[" + spec.title + "]haligame 启动会话 "
                        + url.substring(url.indexOf('/', 8)) + " → " + trim(resp));
                TimeUtil.sleep(400);
            }
        } catch (Throwable t) {
            Log.i("游戏中心🎮[" + spec.title + "]haligame 启动会话异常: " + t);
        }
    }

    /** cpchxapi：auth/login-ali 用 authCode 换 JWT → analytics/ali-report（Bearer 鉴权）。 */
    private static int reportCpchx(Spec spec, int times) {
        String base = "https://cpchxapi.dianwanshidai.com/app-api/game";
        Map<String, String> loginHeaders = new LinkedHashMap<>();
        loginHeaders.put("Content-Type", "application/json; charset=utf-8");
        loginHeaders.put("User-Agent", userAgent());
        loginHeaders.put("referer", referer(spec.appId, spec.version));

        String token = "";
        String code = authCode(spec.appId);
        if (!code.isEmpty()) {
            String body = MiniGameVendorReporter.cpchxLoginBody(spec.appId, code);
            String resp = http(base + "/auth/login-ali", loginHeaders, body, "POST");
            token = valueByPath(resp, "data", "token");
            if (token.isEmpty()) {
                Log.i("游戏中心🎮[" + spec.title + "]登录响应：" + trim(resp));
            }
        }
        if (token.isEmpty()) {
            token = fallback(spec.appId, "token");
            if (token.isEmpty()) {
                Log.i("游戏中心🎮[" + spec.title + "]登录未取到token，跳过");
                return 0;
            }
            Log.i("游戏中心🎮[" + spec.title + "]登录未取到token，使用本机缓存凭据");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("Authorization", "Bearer " + token);
        headers.put("User-Agent", userAgent());
        int done = 0;
        for (int i = 0; i < times; i++) {
            String body = MiniGameVendorReporter.cpchxSubmitBody(spec.action, channelOf(spec));
            String resp = http(base + "/analytics/ali-report", headers, body, "POST");
            if (resp != null && resp.contains("\"code\":0")) {
                done++;
                progress(spec, done, times);
            } else {
                Log.i("游戏中心🎮[" + spec.title + "]上报失败：" + trim(resp));
                break;
            }
            TimeUtil.sleep(1200);
        }
        return done;
    }

    private static boolean isMmhySuccess(String resp) {
        if (resp == null) {
            return false;
        }
        String text = resp.trim();
        if (text.startsWith("HTTP_ERROR")) {
            return false;
        }
        if (text.startsWith("{")) {
            try {
                JSONObject json = new JSONObject(text);
                if (json.length() == 0) {
                    return true;
                }
                if (json.has("code")) {
                    return json.optInt("code", -1) == 0;
                }
                if (json.has("errno")) {
                    return json.optInt("errno", -1) == 0;
                }
                return false;
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private static int reportFlysheep(Spec spec, int times) {
        String code = authCode(spec.appId);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", userAgent());

        String token = "";
        String openId = "";
        String userId = "";
        if (!code.isEmpty()) {
            JSONObject body = new JSONObject();
            try {
                body.put("appid", spec.appId);
                body.put("token_type", 1);
                body.put("code", code);
            } catch (Throwable ignored) {
            }
            String resp = http("https://api.flysheeep.com/api/tapaway/get_ali_token",
                    headers, body.toString(), "POST");
            token = firstValue(resp, "data", "access_token", "token");
            openId = firstValue(resp, "data", "open_id", "openId");
            userId = firstValue(resp, "data", "user_id", "userId");
        }
        if (token.isEmpty() || openId.isEmpty()) {
            String cachedToken = fallback(spec.appId, "token");
            String cachedOpenId = fallback(spec.appId, "openid");
            if (cachedToken.isEmpty() || cachedOpenId.isEmpty()) {
                Log.i("游戏中心🎮[" + spec.title + "]登录未取到凭据，跳过");
                return 0;
            }
            token = cachedToken;
            openId = cachedOpenId;
            userId = fallback(spec.appId, "user_id");
            Log.i("游戏中心🎮[" + spec.title + "]登录未取到凭据，使用本机缓存凭据");
        }

        int done = 0;
        for (int i = 0; i < times; i++) {
            JSONObject body = new JSONObject();
            try {
                body.put("appid", spec.appId);
                body.put("openid", openId);
                body.put("action_code", spec.action);
                body.put("action_finish_channel", channelOf(spec));
                if (!userId.isEmpty()) {
                    body.put("user_id", userId);
                }
                body.put("token", token);
                body.put("biz_no", openId + "_" + spec.action + "_" + System.currentTimeMillis());
            } catch (Throwable ignored) {
            }
            String resp = http("https://api.flysheeep.com/api/ali/ali_submit_v3",
                    headers, body.toString(), "POST");
            if (resp != null && resp.contains("\"code\":200")) {
                done++;
                progress(spec, done, times);
            } else {
                Log.i("游戏中心🎮[" + spec.title + "]上报失败：" + trim(resp));
                break;
            }
            TimeUtil.sleep(1200);
        }
        return done;
    }

    private static int reportMihuangame(Spec spec, int times) {
        String code = authCode(spec.appId);
        // AG 的 http() 在此处传空 header（默认表单编码），先码 appId 变体再裸 code
        Map<String, String> loginHeaders = new LinkedHashMap<>();
        loginHeaders.put("Content-Type", "application/json");
        String loginBase = "https://" + (spec.host.isEmpty() ? "tttp01.mihuangame.com" : spec.host);

        String authentication = "";
        String lastLogin = "";
        if (!code.isEmpty()) {
            String[] bodies = new String[]{
                    MiniGameVendorReporter.mihuangameLoginBody(code,
                            spec.channelAppId > 0 ? String.valueOf(spec.channelAppId) : ""),
                    "{\"channelAppId\":" + Math.max(spec.channelAppId, 0) + ",\"code\":\"" + code
                            + "\",\"appId\":\"" + spec.appId + "\"}",
                    "{\"code\":\"" + code + "\"}",
                    "{\"authCode\":\"" + code + "\"}"
            };
            for (String body : bodies) {
                String resp = http(loginBase + "/api/v2/sys/user/login", loginHeaders, body, "POST");
                lastLogin = resp;
                authentication = valueByPath(resp, "data", "authentication");
                if (!authentication.isEmpty()) {
                    break;
                }
            }
        }
        if (authentication.isEmpty()) {
            authentication = fallback(spec.appId, "authentication");
            if (authentication.isEmpty()) {
                Log.i("游戏中心🎮[" + spec.title + "]登录未取到authentication，跳过");
                return 0;
            }
            Log.i("游戏中心🎮[" + spec.title + "]登录响应：" + trim(lastLogin));
            Log.i("游戏中心🎮[" + spec.title + "]登录未取到authentication，使用本机缓存凭据");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("authentication", authentication);
        headers.put("User-Agent", userAgent());

        // 赵云与阿斗这类变体没有 tpfy 会话，直接打 sys/alipay/submit
        boolean custom = !spec.submitPath.isEmpty();
        if (!custom) {
            http(loginBase + "/api/v2/tpfy/game/start", headers, "{}", "POST");
        }

        int done = 0;
        for (int i = 0; i < times; i++) {
            String body;
            if (custom) {
                body = MiniGameVendorReporter.mihuangameSubmitBody(spec.action, channelOf(spec),
                        System.currentTimeMillis());
            } else if (spec.propertyKey.isEmpty()) {
                body = "{\"eventId\":\"" + spec.action + "\",\"eventFinishChannel\":\"" + channelOf(spec) + "\"}";
            } else {
                body = "{\"eventId\":\"" + spec.action + "\",\"eventFinishChannel\":\"" + channelOf(spec)
                        + "\",\"propertyMap\":[{\"key\":\"" + spec.propertyKey + "\",\"value\":\"1\"}]}";
            }
            String resp = http(loginBase + "/api/v2/" + (custom ? spec.submitPath : "sys/alipay/point"),
                    headers, body, "POST");
            if (resp != null && (resp.contains("\"code\":0") || resp.trim().equals("{}"))) {
                done++;
                progress(spec, done, times);
            } else {
                Log.i("游戏中心🎮[" + spec.title + "]上报失败：" + trim(resp));
                break;
            }
            TimeUtil.sleep(1200);
        }
        return done;
    }

    private static int reportIgame58(Spec spec, int times) {
        String code = authCode(spec.appId);
        String openId = "";
        String uuid = "";
        String umid = spec.umid;
        // 2026-11-18 22:42 抓包结论：getOpenId / login 都必须带 platform/channel/umid 头，
        // 否则服务端直接 500「Missing request header 'platform'」，以前一直是走缓存凭据的。
        Map<String, String> baseHeaders = new LinkedHashMap<>();
        baseHeaders.put("Content-Type", "application/json; charset=utf-8");
        baseHeaders.put("channel", "zhifubao");
        baseHeaders.put("platform", "1");
        baseHeaders.put("umid", umid);
        baseHeaders.put("x-release-type", "ONLINE");
        baseHeaders.put("User-Agent", userAgent());
        String uuidCache = fallback(spec.appId, "uuid");
        if (!uuidCache.isEmpty()) {
            baseHeaders.put("uuid", uuidCache);
        }
        if (!code.isEmpty()) {
            String resp = http("https://aa.igame58.com/mini/three/getOpenId?code=" + urlEncode(code),
                    baseHeaders, null, "GET");
            openId = valueByPath(resp, "data", "open_id");
            if (openId.isEmpty()) {
                openId = valueByPath(resp, "data", "openId");
            }
            if (openId.isEmpty()) {
                Log.i("游戏中心🎮[" + spec.title + "]getOpenId 响应：" + trim(resp));
            }
        }
        if (openId.isEmpty()) {
            openId = fallback(spec.appId, "openid");
            uuid = fallback(spec.appId, "uuid");
            if (openId.isEmpty()) {
                Log.i("游戏中心🎮[" + spec.title + "]登录未取到openid，跳过");
                return 0;
            }
            Log.i("游戏中心🎮[" + spec.title + "]登录未取到openid，使用本机缓存凭据");
        }

        int gameVersion = spec.gameVersion > 0 ? spec.gameVersion : 25;
        Map<String, String> loginHeaders = new LinkedHashMap<>(baseHeaders);
        if (!openId.isEmpty()) {
            loginHeaders.put("openId", openId);
            logLoginIdentity(spec, "openid", openId);
        }
        String loginUrl = "https://aa.igame58.com/mini/login/login?dataVersion=0&gameVersion=" + gameVersion
                + "&newArchiveVersion=1&time=" + System.currentTimeMillis();
        if (uuid.isEmpty() || !code.isEmpty()) {
            for (int attempt = 0; attempt < 2 && uuid.isEmpty(); attempt++) {
                if (attempt == 1) {
                    // 2026-11-18 23:15 分身实测：login 自身就要求 uuid 请求头（缺则 500
                    // "Missing request header 'uuid'"）。本账号从没登录过时没有历史 uuid，
                    // 这里生成一个 32 位随机串当「安装标识」，服务端会回真正的 uuid。
                    String gen = randomUuid();
                    loginHeaders.put("uuid", gen);
                    Log.i("游戏中心🎮[" + spec.title + "]login 缺 uuid，用随机安装标识重试：" + gen);
                }
                String resp = http(loginUrl, loginHeaders, null, "GET");
                String freshUuid = valueByPath(resp, "data", "uuid");
                if (freshUuid.isEmpty()) {
                    freshUuid = valueByPath(resp, "uuid");
                }
                if (freshUuid.isEmpty()) {
                    Log.i("游戏中心🎮[" + spec.title + "]login 响应：" + trim(resp));
                }
                if (!freshUuid.isEmpty()) {
                    uuid = freshUuid;
                }
            }
        }
        if (uuid.isEmpty()) {
            Log.i("游戏中心🎮[" + spec.title + "]未取到uuid，跳过");
            return 0;
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("Cache-Control", "no-cache");
        headers.put("appid", spec.appId);
        headers.put("openId", openId);
        headers.put("channel", "zhifubao");
        headers.put("platform", "1");
        headers.put("umid", umid);
        headers.put("uuid", uuid);
        headers.put("versionCode", String.valueOf(gameVersion));
        headers.put("h5Support", "1");
        headers.put("language", "zh-Hans");
        headers.put("device", deviceJson());
        headers.put("trackChannel", spec.channel);
        headers.put("adid", "ch_appcenter__chsub_9patch");
        headers.put("User-Agent", userAgent());

        int done = 0;
        for (int i = 0; i < times; i++) {
            String body = MiniGameVendorReporter.igame58SubmitBody(spec.action, channelOf(spec),
                    false, "");
            String resp = http("https://aa.igame58.com/mini/three/ali/action/submit",
                    headers, body, "POST");
            if (resp != null && resp.contains("\"10000\"")) {
                done++;
                progress(spec, done, times);
            } else {
                Log.i("游戏中心🎮[" + spec.title + "]上报失败：" + trim(resp));
                break;
            }
            TimeUtil.sleep(1200);
        }
        return done;
    }

    // ---------------------------------------------------------------- 工具方法

    /** 生成 32 位随机字母数字串，用作 igame58 首次登录时的 uuid「安装标识」 */
    private static String randomUuid() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        java.util.Random r = new java.util.Random();
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static void progress(Spec spec, int done, int times) {
        Log.i("游戏中心🎮[" + spec.title + "]进度 " + done + "/" + times);
    }

    private static String authCode(String appId) {
        try {
            String code = AuthCodeHelper.getAuthCode(appId);
            if (code == null) {
                Log.i("游戏中心🎮authCode 为空(appId=" + appId + ")，该小程序在本账号下可能还没授权过");
                return "";
            }
            if (code.trim().isEmpty()) {
                Log.i("游戏中心🎮authCode 为空(appId=" + appId + ")，该小程序在本账号下可能还没授权过");
            }
            return code.trim();
        } catch (Throwable t) {
            Log.i(TAG, "getAuthCode " + appId + " err: " + t);
            return "";
        }
    }

    private static void logLoginIdentity(Spec spec, String field, String value) {
        String uid = "";
        try {
            uid = UserIdMap.getCurrentUid();
        } catch (Throwable ignored) {
        }
        String masked = value == null ? "" : (value.length() <= 10 ? value
                : value.substring(0, 10) + "…(" + value.length() + ")");
        Log.i("游戏中心🎮[" + spec.title + "]" + field + "=" + masked + " uid=" + uid);
    }

    private static String fallback(String appId, String key) {
        String value = FALLBACK.get(appId + ":" + key);
        if (value == null || value.isEmpty()) {
            return "";
        }
        String owner = FALLBACK_UID.get(appId + ":" + key);
        String current = "";
        try {
            current = UserIdMap.getCurrentUid();
        } catch (Throwable ignored) {
        }
        if (owner == null || owner.isEmpty()) {
            if (FALLBACK_SKIPPED.add(appId + ":unregistered")) {
                Log.i("游戏中心🎮[" + appId + "]缓存凭据未登记归属账号，已跳过（避免记到别的账号）");
            }
            return "";
        }
        if (!owner.equals(current)) {
            if (FALLBACK_SKIPPED.add(appId + ":" + current)) {
                Log.i("游戏中心🎮[" + appId + "]缓存凭据属于账号[" + owner + "]，当前账号[" + current
                        + "]，已跳过（避免把进度记到别的账号）");
            }
            return "";
        }
        return value;
    }

    private static String referer(String appId, String version) {
        String ver = version == null || version.isEmpty() ? "" : version + "/";
        return "https://" + appId + ".hybrid.alipay-eco.com/" + appId + "/" + ver + "index.html";
    }

    private static String userAgent() {
        String ua = "Mozilla/5.0 (Linux; U; Android 15; zh-CN; PLR110 Build/AP3A.240905.015) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Version/4.0 Chrome/114.0.5735.196 UCBrowser/14.9.0.1 Mobile Safari/537.36";
        String version = "12.12.1.8000";
        try {
            String hookVersion = ApplicationHook.getAlipayVersion().getVersionString();
            if (hookVersion != null && !hookVersion.isEmpty()) {
                version = hookVersion;
            }
        } catch (Throwable ignored) {
        }
        return ua + " NebulaSDK/1.8.100112 Nebula AliApp(AP/" + version + ") AlipayClient/" + version;
    }

    private static String deviceJson() {
        JSONObject device = new JSONObject();
        try {
            device.put("sdkVersion", android.os.Build.VERSION.SDK_INT);
            device.put("platformVersion", android.os.Build.VERSION.RELEASE);
            device.put("androidVersion", android.os.Build.VERSION.RELEASE);
            device.put("model", android.os.Build.MODEL);
            device.put("networkType", "wifi");
        } catch (Throwable ignored) {
        }
        return device.toString();
    }

    /**
     * mmhygame（hbgwb SDK，engine/game.js 原文）的请求签名：
     * nonce = Math.random().toString(36).substring(2,10)（8 位）、timestamp = Date.now()（毫秒），
     * sign = md5Base64("nonce=<nonce>&timestamp=<ts>" + body + "35301dd88a274e269")，
     * URL 追加 ?nonce=&timestamp=，header 带 x-signature。
     */
    private static String mmhyLink(String base, String body, Map<String, String> headers) {
        try {
            String nonce = Long.toString(Math.abs(new java.util.Random().nextLong()), 36);
            if (nonce.length() > 8) {
                nonce = nonce.substring(0, 8);
            }
            String ts = String.valueOf(System.currentTimeMillis());
            String plain = "nonce=" + nonce + "&timestamp=" + ts + body + "35301dd88a274e269";
            byte[] digest = MessageDigest.getInstance("MD5").digest(plain.getBytes(StandardCharsets.UTF_8));
            headers.put("x-signature", android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP));
            return base + "?nonce=" + nonce + "&timestamp=" + ts;
        } catch (Throwable t) {
            return base;
        }
    }

    private static String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String urlEncode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8");
        } catch (Throwable t) {
            return text;
        }
    }

    // ---------------------------------------------------------------- JINGYOU（pt-sdk-api.jingyougz.com）
    // 「解压俄罗斯方块 jyelsfk」等悠悠(jingyou)系小游戏：客户端调用 JYSDK.submitEvent("action_report",
    // {action_code})，落到 https://pt-sdk-api.jingyougz.com/api/v1/platform/action_report。
    // 签名 = md5(白名单字段按 key 排序拼 k=v& … + app_key)，已用真机抓到的请求逐字节验证通过。

    private static final String JY_BASE = "https://pt-sdk-api.jingyougz.com";
    private static final String JY_APP_ID = "1750390480";
    private static final String JY_APP_KEY = "ofHCFJVjdl7Wr169G5uSOmXjKaMEIniT";
    private static final String JY_CHANNEL_ID = "1036";
    private static final String JY_SDK_TYPE = "minigame";
    private static final String JY_SDK_VER = "1.0.1";
    private static final String JY_CLIENT_VER = "7.2.5";
    private static final String JY_PREF = "sesame_jingyou_sdk";
    private static final String[] JY_SIGN_KEYS = {"app_id", "channel_id", "aid", "ad_group", "sdk_ver",
            "sdk_type", "client_ver", "t", "trace_id", "device_id", "os", "os_ver"};
    private static final String JY_NUMBER_KEYS = ",t,aid,ad_group,user_id,";

    private static int reportJingyou(Spec spec, int times) {
        String uid = UserIdMap.getCurrentUid();
        if (uid == null) {
            uid = "";
        }
        String openId = jyLoad(uid, "open_id");
        String userId = jyLoad(uid, "user_id");
        String token = "";
        if (openId.isEmpty()) {
            String authCode = AuthCodeHelper.getAuthCode(spec.appId);
            if (authCode == null || authCode.isEmpty()) {
                Log.i("游戏中心🎮[" + spec.title + "]未取到支付宝授权码，跳过(action=" + spec.action + ")");
                return 0;
            }
            Map<String, String> params = jyBaseParams(uid);
            params.put("pf_session", authCode);
            params.put("action_finish_channel", CHANNEL);
            String resp = jyPost(JY_BASE + "/api/v1/platform/login", params, "");
            JSONObject jo = jyJson(resp);
            JSONObject data = jo == null ? null : jo.optJSONObject("data");
            JSONObject user = data == null ? null : data.optJSONObject("user");
            if (user != null) {
                openId = user.optString("open_id", "");
                int id = user.optInt("user_id", 0);
                if (id > 0) {
                    userId = String.valueOf(id);
                }
            }
            if (data != null) {
                token = data.optString("token", "");
            }
            Log.i("游戏中心🎮[" + spec.title + "]JY platform/login 响应：" + jyClip(resp));
            if (openId.isEmpty()) {
                Log.i("游戏中心🎮[" + spec.title + "]JY 登录未取到openid，跳过");
                return 0;
            }
            jySave(uid, "open_id", openId);
            if (!userId.isEmpty()) {
                jySave(uid, "user_id", userId);
            }
        }
        if (token.isEmpty()) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("app_id", JY_APP_ID);
            params.put("channel_id", JY_CHANNEL_ID);
            params.put("client_ver", JY_CLIENT_VER);
            params.put("sdk_type", JY_SDK_TYPE);
            params.put("sdk_ver", JY_SDK_VER);
            params.put("t", String.valueOf(System.currentTimeMillis() / 1000));
            params.put("trace_id", jyTraceId());
            params.put("open_id", openId);
            params.put("pf_openid", uid);
            String resp = jyPost(JY_BASE + "/univ/api/v1/users/login", params, "");
            JSONObject jo = jyJson(resp);
            JSONObject data = jo == null ? null : jo.optJSONObject("data");
            if (data != null) {
                token = data.optString("token", "");
                JSONObject user = data.optJSONObject("user");
                if (user != null && user.optInt("user_id", 0) > 0 && userId.isEmpty()) {
                    userId = String.valueOf(user.optInt("user_id"));
                    jySave(uid, "user_id", userId);
                }
            }
            if (token.isEmpty()) {
                Log.i("游戏中心🎮[" + spec.title + "]JY 登录失败：" + jyClip(resp));
                return 0;
            }
        }
        int done = 0;
        for (int i = 0; i < times; i++) {
            Map<String, String> params = jyBaseParams(uid);
            params.put("action_finish_channel", CHANNEL);
            params.put("open_id", openId);
            params.put("pf_openid", uid);
            if (!userId.isEmpty()) {
                params.put("user_id", userId);
            }
            params.put("action_code", spec.action);
            String resp = jyPost(JY_BASE + "/api/v1/platform/action_report", params, token);
            JSONObject jo = jyJson(resp);
            boolean ok = jo != null && jo.optInt("code", -1) == 0;
            Log.i("游戏中心🎮[" + spec.title + "]JY 上报第" + (i + 1) + "/" + times + "次"
                    + (ok ? " 成功" : " 失败：" + jyClip(resp)));
            if (ok) {
                done++;
            } else if (i == 0) {
                break;
            }
            TimeUtil.sleep(700);
        }
        return done;
    }

    private static Map<String, String> jyBaseParams(String uid) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", JY_APP_ID);
        params.put("channel_id", JY_CHANNEL_ID);
        params.put("client_ver", JY_CLIENT_VER);
        params.put("sdk_type", JY_SDK_TYPE);
        params.put("sdk_ver", JY_SDK_VER);
        params.put("t", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("trace_id", jyTraceId());
        params.put("aid", "0");
        params.put("ad_group", "0");
        params.put("device_id", jyDeviceId(uid));
        params.put("device_uuid", jyDeviceId(uid));
        params.put("os", "android");
        params.put("os_ver", android.os.Build.VERSION.RELEASE);
        params.put("model", android.os.Build.MODEL);
        params.put("brand", android.os.Build.BRAND);
        params.put("scene", "unknown");
        params.put("referrer", "{}");
        params.put("query", "{\"channel\":\"" + CHANNEL + "\"}");
        return params;
    }

    private static String jyPost(String url, Map<String, String> params, String token) {
        Map<String, String> signed = new LinkedHashMap<>(params);
        signed.remove("sign");
        signed.put("sign", jySign(signed));
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "*/*");
        headers.put("x-release-type", "ONLINE");
        if (token != null && !token.isEmpty()) {
            headers.put("Authorization", "Bearer " + token);
        }
        return http(url, headers, jyBody(signed), "POST");
    }

    private static String jyBody(Map<String, String> params) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            String key = entry.getKey();
            String value = entry.getValue();
            sb.append(JSONObject.quote(key)).append(':');
            if (value != null && JY_NUMBER_KEYS.contains("," + key + ",") && value.matches("-?\\d+")) {
                sb.append(value);
            } else {
                sb.append(JSONObject.quote(value == null ? "" : value));
            }
        }
        return sb.append('}').toString();
    }

    private static String jySign(Map<String, String> params) {
        java.util.List<String> keys = new java.util.ArrayList<>(params.keySet());
        java.util.Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            boolean signed = false;
            for (String allow : JY_SIGN_KEYS) {
                if (allow.equals(key)) {
                    signed = true;
                    break;
                }
            }
            if (!signed) {
                continue;
            }
            String value = params.get(key);
            if (value == null || value.isEmpty()) {
                continue;
            }
            sb.append(key).append('=').append(value).append('&');
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return md5(sb.toString() + JY_APP_KEY);
    }

    private static String jyTraceId() {
        return System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "");
    }

    private static String jyDeviceId(String uid) {
        String seed = "jingyou|" + (uid == null ? "" : uid);
        return "1795" + md5(seed).substring(0, 28);
    }

    private static JSONObject jyJson(String text) {
        if (text == null || text.isEmpty() || text.startsWith("HTTP_ERROR")) {
            return null;
        }
        try {
            return new JSONObject(text);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String jyClip(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 240 ? text.substring(0, 240) + "…" : text;
    }

    private static String jyScope(String uid) {
        if (uid == null || uid.isEmpty()) {
            return "default";
        }
        return uid.replaceAll("[^0-9A-Za-z_-]", "_");
    }

    private static android.content.Context jyContext() {
        try {
            Object application = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            return (android.content.Context) application;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String jyLoad(String uid, String key) {
        try {
            android.content.Context ctx = jyContext();
            if (ctx == null) {
                return "";
            }
            String value = ctx.getSharedPreferences(JY_PREF, 0).getString(key + "_" + jyScope(uid), "");
            return value == null ? "" : value;
        } catch (Throwable t) {
            return "";
        }
    }

    private static void jySave(String uid, String key, String value) {
        try {
            android.content.Context ctx = jyContext();
            if (ctx == null) {
                return;
            }
            ctx.getSharedPreferences(JY_PREF, 0).edit().putString(key + "_" + jyScope(uid), value).apply();
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------------- ULTRALISK（common.ultralisk.cn）

    private static int reportUltra(Spec spec, int times) {
        int gameId = 0;
        try {
            gameId = Integer.parseInt(spec.gid);
        } catch (Throwable ignored) {
        }
        if (gameId <= 0) {
            Log.i("游戏中心🎮[" + spec.title + "]缺少 gameId，跳过(action=" + spec.action + ")");
            return 0;
        }
        String code = authCode(spec.appId);
        if (code.isEmpty()) {
            Log.i("游戏中心🎮[" + spec.title + "]未取到支付宝授权码，跳过");
            return 0;
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept-Encoding", "identity");
        headers.put("x-release-type", "ONLINE");
        headers.put("User-Agent", userAgent());
        String loginBody = "{\"token\":\"" + code + "\",\"channel\":\"h5alipay\",\"gameId\":" + gameId + "}";
        String resp = http("https://common.ultralisk.cn/sdkserver/userlogin", headers, loginBody, "POST");
        String openId = valueByPath(resp, "openId");
        if (openId.isEmpty()) {
            Log.i("游戏中心🎮[" + spec.title + "]userlogin 响应：" + trim(resp));
            return 0;
        }
        logLoginIdentity(spec, "openid", openId);
        int done = 0;
        for (int i = 0; i < times; i++) {
            String body = "{\"gameId\":" + gameId + ",\"openId\":\"" + openId
                    + "\",\"actionCode\":\"" + spec.action + "\",\"actionFinishChannel\":\""
                    + channelOf(spec) + "\"}";
            String r = http("https://common.ultralisk.cn/sdkserver/h5alipayuploadgamebehaviour",
                    headers, body, "POST");
            if (r != null && r.contains("\"code\":1")) {
                done++;
                progress(spec, done, times);
            } else {
                Log.i("游戏中心🎮[" + spec.title + "]上报失败：" + trim(r));
                break;
            }
            TimeUtil.sleep(900);
        }
        return done;
    }

    private static String http(String url, Map<String, String> headers, String body, String method) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestMethod(method == null ? "GET" : method);
            connection.setRequestProperty("Accept-Encoding", "identity");
            // 默认 JSON：mihuangame/mmhygame 这类服务端用表单编码解析会直接回 9999 System Error
            // 注意：这里不再给所有请求强行加 Content-Type: application/json。
            // 实测 haligame / dianwanshidai / mmhygame 的登录接口只认表单编码（AG 传空 header 就是这个原因），
            // 强行 JSON 会回「缺少参数」「登录未取到openid」。需要 JSON 的接口各自在 header 里显式声明。
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        connection.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (body != null) {
                connection.setDoOutput(true);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                OutputStream out = connection.getOutputStream();
                out.write(bytes);
                out.flush();
                out.close();
            }
            int code = connection.getResponseCode();
            InputStream in = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = readAll(in);
            if (code >= 400) {
                return "HTTP_ERROR:" + code + " " + text;
            }
            return text;
        } catch (Throwable t) {
            return "HTTP_ERROR:" + t;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readAll(InputStream in) {
        if (in == null) {
            return "";
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            in.close();
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String valueByPath(String text, String... path) {
        JSONObject json = toJson(text);
        if (json == null) {
            return "";
        }
        JSONObject current = json;
        for (int i = 0; i < path.length - 1; i++) {
            JSONObject next = current.optJSONObject(path[i]);
            if (next == null) {
                return "";
            }
            current = next;
        }
        String value = current.optString(path[path.length - 1], "");
        return "null".equals(value) ? "" : value;
    }

    private static String firstValue(String text, String parent, String... keys) {
        JSONObject json = toJson(text);
        if (json == null) {
            return "";
        }
        JSONObject node = json.optJSONObject(parent);
        if (node == null) {
            node = json;
        }
        for (String key : keys) {
            String value = node.optString(key, "");
            if (!value.isEmpty() && !"null".equals(value)) {
                return value;
            }
        }
        return "";
    }

    private static JSONObject toJson(String text) {
        if (text == null || text.isEmpty() || text.startsWith("HTTP_ERROR")) {
            return null;
        }
        try {
            return new JSONObject(text);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 排查用：保留前 600 字符，避免把回执截断到看不出内容 */
    private static String clip2(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() <= 600 ? s : s.substring(0, 600) + "…(len=" + s.length() + ")";
    }

    private static String trim(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}