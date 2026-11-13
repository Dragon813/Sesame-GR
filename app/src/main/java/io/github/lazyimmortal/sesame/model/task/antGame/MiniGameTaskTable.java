package io.github.lazyimmortal.sesame.model.task.antGame;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.lazyimmortal.sesame.util.FileUtil;
import io.github.lazyimmortal.sesame.util.JsonUtil;
import io.github.lazyimmortal.sesame.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GameTask 适配器全表。
 *
 * <p>内置默认表 {@code ROWS} 仍采用紧凑行格式 {@code appId|游戏名|版本|SDK|动作列表|协议参数}：
 * <ul>
 *     <li>动作列表：{@code actionCode^每N次^中文动作名}，用逗号分隔；动作名可用 {@code /}
 *         分隔多个同义关键词，供任务标题匹配；</li>
 *     <li>协议参数：{@code k=v} 用分号分隔，含 {@code bgid / appKey / oid / gcid / nebulaAppId /
 *         companyId / channelId / sdkVersion / gameVersion / gid / gameId / path /
 *         loginHost / reportHost / versionApp}。</li>
 * </ul>
 *
 * <p>本表只提供「元信息 + 厂商参数」，不含登录/上报的请求体与签名——协议实现见
 * {@link MiniGameVendorReporter}。表的用途：
 * <ol>
 *     <li>{@link #version(String)}：游戏中心首帧 {@code GAME_VERSION} 与 {@code alipayMiniMark}；</li>
 *     <li>{@link #actions(String)}：任务标题 → actionCode 关键词匹配；</li>
 *     <li>{@link #sdk(String)}：按厂商 SDK 族决定能否补上报；</li>
 *     <li>{@link #param(String, String)}：厂商协议的差异参数（bgid 等）。</li>
 * </ol>
 *
 * <p>数据持久化：首次加载时会把内置默认表写出到
 * {@code <数据目录>/MiniGameTaskTable.json}（与 {@code MiniGameTask.json} 同目录）。
 * 之后若该文件存在且 JSON 合法则优先加载磁盘版本；文件缺失或损坏时自动用默认表重建，
 * 保证编辑与回滚都不影响启动。文件内容为对象数组，每个对象结构如下：
 * <pre>{@code
 * {
 *   "appId":   "2021003184609526",
 *   "name":    "泡泡龙王国",
 *   "version": "0.2.2507071728.25",
 *   "sdk":     "d3games",
 *   "actions": [
 *     {"code": "pplwg_game_pass_level_every_1", "every": 1, "label": ["闯关","通关","通过关卡"]},
 *     {"code": "pplwg_game_daoju_every_1",        "every": 1, "label": ["道具"]}
 *   ],
 *   "params": {"appKey": "86b5b3815ddfa448eec459718d4c2aa3"}
 * }
 * }</pre>
 * 其中 {@code actions} 恒为数组（一个游戏可有多个动作码），{@code label} 也以数组表达同义关键词。
 * 旧版「管道分隔字符串数组」格式仍可被读取，落盘时会自动转换为对象数组。
 */
public final class MiniGameTaskTable {

    private static final String[] ROWS = {
            "2021000100675812|指尖幻影|0.2.2607102236.59||zjhy_game_boci_every_3^3^|",
            "2021004135635323|西游除妖|0.2.2607020611.9||xycy_game_pass_every_1^1^通关/通过/闯关,xycy_game_hulu_every_5^5^葫芦|sdkVersion=1.9.7;gameVersion=1.0",
            "2021004138691383|羊了个羊农场版|0.2.2607162135.58|||",
            "2021004189686520|守护世界|||shsj_game_hecheng_every_10^10^合成/合并,shsj_game_renwu_every_1^1^任务|channelId=hy1;sdkVersion=1.0.7",
            "2021005161697013|全境守卫|0.2.2608171810.43||qjsw_game_pass_every_1^1^通关/通过/闯关,qjsw_game_jianzhu_every_1^1^建造/建筑,qjsw_game_puzhaohuan_every_1^1^召唤,qjsw_game_baoxiang_every_1^1^宝箱|gameVersion=1.0.73.0",
            "2021005183651471|消消瓜果园||||",
            "2021006101635241|无尽探险队|0.2.2607161416.35||wjtxd_game_pass_every_1^1^通关/通过/闯关|",
            "2021006152653505|城主别慌|0.2.2610010110.0||czbhz_game_pass_every_1^1^通关/通过/闯关,czbhz_game_jineng_every_1^1^技能|",
            "2060170000358842|洪荒仙韵|0.2.2605141416.45||hhxy_game_guimenguan_every_1^1^鬼门关,hhxy_game_shiwang_every_5^5^魔王,hhxy_game_jingjichang_every_1^1^竞技场,hhxy_game_fuben_every_1^1^副本|",
            "2060170000400026|抓大鹅|0.2.2610231342.51||zde_game_xiaochu_every_15^15^消除/消消|",
            "2060170000363633|玩不过我呀|0.2.2605270925.9|aaIgame58|wbgwy_game_pass_every_1^1^通关/通过/闯关|",
            "2060170000365575|点点消不停|0.2.2604161533.29|aaIgame58|ddxbt_game_pass_every_1^1^通关/通过/闯关|",
            "2021000110660562|捞个菜|0.2.2606221937.35|aslk2018|lgc_game_pass_every_1^1^通关/通过/闯关|gid=zfb_lgc",
            "2021000111632448|找不停|0.2.2606181421.5|aslk2018|zbt_game_pass_every_1^1^通关/通过/闯关|gid=zfb_zbt;v=1.0.0",
            "2021004126650405|超级解压馆|0.2.2507301514.41|aslk2018|cjjyg_game_hecheng_every_5^5^合成/合并|gid=zfb_cjjyg",
            "2021004128666655|玩梗高手|0.2.2603171940.31|aslk2018|wggs_game_pass_every_1^1^通关/通过/闯关|gid=zfb_wggs;v=1.0.4",
            "2021004131628794|水滴排排序|0.2.2512171908.31|aslk2018|sdppx_game_pass_every_1^1^通关/通过/闯关|gid=zfb_sdppxx",
            "2021004135688960|火锅消不停|0.2.2407091636.11|aslk2018|hgxbt_game_xiaochu_every_5^5^消除/消消|gid=zfb_hgxbt",
            "2021004138632167|农场解压馆|0.2.2511281734.55|aslk2018|ncjyg_game_hecheng_every_5^5^合成/合并|gid=zfb_ncjyg",
            "2021004149679303|对对碰乐园|1.0.14|aslk2018|ddply_game_xiaochu_every_5^5^消除/消消|gid=zfb_ddply",
            "2021004150621264|螺丝庄园|0.2.2512051527.46|aslk2018|lszy_game_pass_every_1^1^通关/通过/闯关|gid=zfb_lszy",
            "2021004153698112|农场解螺丝|0.2.2601231520.27|aslk2018|ncjls_game_xiaochu_every_5^5^消除/消消|gid=zfb_ncjls",
            "2021004158640815|会员连不停|0.2.2509281117.51|aslk2018|qsgj_game_pass_every_1^1^通关/通过/闯关|gid=zfb_hylbt",
            "2021004159604141|玩个儿毛线|0.2.2512081218.33|aslk2018|wgemx_game_pass_every_1^1^通关/通过/闯关|gid=zfb_wgemx",
            "2021004164603638|拿下100关|0.2.2606112022.39|aslk2018|nxybg_game_pass_every_1^1^通关/通过/闯关,nxybg_game_yingzhang_every_1^1^印章|gid=zfb_nx100g;v=1.0.0",
            "2021004164632103|离谱的汉字|0.2.2507181129.29|aslk2018|lpdhz_game_pass_every_1^1^通关/通过/闯关,lpdhz_game_xiaochu_every_10^10^消除/消消|gid=zfb_lpdhz1",
            "2021004170628696|农场文字秀|0.2.2601192247.15|aslk2018|ncwzx_game_hecheng_every_10^10^合成/合并|gid=zfb_ncwzx",
            "2021004172624044|农场大聪明|0.2.2602101831.24|aslk2018|ncdcm_game_pass_every_1^1^通关/通过/闯关|gid=zfb_ncdcm",
            "2021004194616336|会员爱动脑|0.2.2506261635.47|aslk2018|hyadn_game_pass_every_1^1^通关/通过/闯关|gid=zfb_hyadn",
            "2021005112605978|森林萌萌消|0.2.2601281827.8|aslk2018|slmmx_game_xiaochu_every_10^10^消除/消消|gid=zfb_slmmxx",
            "2021005112647931|诗意绿洲|0.2.2606091857.22|aslk2018|sylz_game_pass_every_1^1^通关/通过/闯关,sylz_game_hecheng_every_10^10^合成/合并|gid=zfb_sylz",
            "2021005113622761|森林解压馆|0.2.2509301530.27|aslk2018|sljyg_game_hecheng_every_1^1^合成/合并|gid=zfb_sljyg",
            "2021005113684028|森林救援队|1.0.1|aslk2018|sljyd_game_xiaochu_every_10^10^消除/消消|gid=zfb_sljydx",
            "2021005172696439|找个喵|0.2.2601131551.50|aslk2018|zgm_game_pass_every_1^1^通关/通过/闯关|gid=zfb_zgm",
            "2021006113622705|挪了个箭|0.2.2605252023.30|aslk2018|nlgj_game_pass_every_1^1^通关/通过/闯关,nlgj_game_jiantou_every_50^50^箭头|gid=zfb_nlgj;v=1.0.5",
            "2021006117687842|玩水大作战|0.2.2602241832.15|aslk2018|wsdzz_game_hecheng_every_20^20^合成/合并|gid=zfb_wsdzz",
            "2021006148677438|欢乐拼拼豆|0.2.2607211740.19|aslk2018|klppd_game_pass_every_1^1^通关/通过/闯关|gid=zfb_klppd;v=1.2.0",
            "2021006152654592|一找一个准|0.2.2609141439.46|aslk2018|yzygz_game_pass_every_1^1^通关/通过/闯关|gid=zfb_yzygz;v=1.0.1",
            "2060170000354005|毛线消不停|0.2.2601091752.55|aslk2018|mxxbt_game_pass_every_1^1^通关/通过/闯关|gid=zfb_mxxbt",
            "2060170000355266|农场拆毛线|0.2.2602101754.41|aslk2018|nccmx_game_xiaochu_every_20^20^消除/消消|gid=zfb_nccmx",
            "2060170000356601|农场上车车|1.0.2|aslk2018|ncscc_game_kaiche_every_10^10^开车/车辆/辆车|gid=zfb_ncscc",
            "2060170000363691|森林小车车|1.0.1|aslk2018|slxcc_game_kaiche_every_10^10^开车/车辆/辆车|gid=zfb_slxcc",
            "2060170000366384|毛线解压馆|0.2.2601091624.55|aslk2018|ylzt_game_pass_every_1^1^通关/通过/闯关|gid=zfb_mxjyg",
            "2060170000373873|小鸡拆毛线|0.2.2601091612.54|aslk2018|xjcmx_game_xiaochu_every_10^10^消除/消消|gid=zfb_xjcmx",
            "2060170000376051|解压放松器|0.2.2601222010.25|aslk2018|jyfsq_game_pass_every_1^1^通关/通过/闯关|gid=zfb_jyfsq",
            "2021004115691527|农场萌小院|0.2.2607301618.6|beautifulgarden|ncmxy_game_dingdan_every_1^1^订单,ncmxy_game_synthetise_num_10^1^合成/合并|",
            "2021004157672803|躺平植物战僵尸|0.2.2512251750.58|cdwaterbear|tpzwjs_game_shengji_every_1^1^升级/升到|",
            "2021004159660680|超凡打螺丝|0.2.2603150002.25|cdwaterbear|cfdls_game_pass_every_1^1^通关/通过/闯关|",
            "2021004182639175|成语状元|0.2.2512051703.23|cdwaterbear|cyzzy_game_pass_every_1^1^通关/通过/闯关|",
            "2060170000357462|全民打螺丝|0.2.2602060943.11|cpchxapi|qmdls_game_pass_every_1^1^通关/通过/闯关|",
            "2060170000357475|疯狂来解压|0.2.2604020945.51|cpchxapi|fkljy_game_pass_every_1^1^通关/通过/闯关|",
            "2060170000363288|森林解压王|0.2.2602060952.45|cpchxapi|sljyw_game_xiaochu_every_10^10^消除/消消|",
            "2060170000363314|螺丝消不停|0.2.2602060945.10|cpchxapi|lsxbt_game_pass_every_1^1^通关/通过/闯关|",
            "2021003184609526|泡泡龙王国|0.2.2507071728.25|d3games|pplwg_game_pass_level_every_1^1^闯关/通关/通过关卡,pplwg_game_daoju_every_1^1^道具|appKey=86b5b3815ddfa448eec459718d4c2aa3",
            "2021004130619550|农场泡泡龙|0.2.2512101746.15|d3games|farmppl_game_pass_1_misson^1^通关/通过|appKey=4a9c9ae228d57007eb3af2808142e242",
            "2021004138641359|小鸡向前冲|0.2.2602041526.56|d3games|xjxqc_game_piaoyi_every_1^1^漂移|appKey=ca08c5ba0b86372f1028375f91ecdbf6",
            "2021004148696219|农场挪对对|0.2.2607201557.47|d3games|ncndd_game_pipei_every_10^10^配对/匹配|appKey=05b83455558bcd98f7beab079498df27",
            "2021004157647236|泡泡龙大挑战|0.2.2409041752.27|d3games|ppldtz_game_pass_every_1^1^通关/通过/闯关|appKey=e6a0631f86467b7a4a880574bbb78d7f",
            "2021004184696771|森林环游记|0.2.2607071824.23|d3games|slhyjiap_game_hecheng_every_10^10^合成/合并,slhyjiap_game_tili_every_10^10^体力,slhyjiap_game_dingdan_every_1^1^订单,slhyjiap_game_jianzao_every_1^1^建造/建筑|appKey=465d2fe840abb7bfdc2d69cf1f39be28;gameId=10182;reportHost=api-slhyj.d3games.com",
            "2021005109675436|美食奇遇记|0.2.2607092006.11|d3games|iapmsqyj_game_hecheng_every_10^10^合成/合并,iapmsqyj_game_tili_every_10^10^体力,iapmsqyj_game_dingdan_every_1^1^订单,iapmsqyj_game_jianzao_every_1^1^建造/建筑|appKey=192398bf853974a546a8e6c522d04b8a;gameId=10161;reportHost=msqyj.d3games.com",
            "2021005187627410|倒水杯|0.2.2603161110.56|devourad|dsb_game_pass_every_1^1^通关/通过/闯关|appKey=740f34d825e4ba3bc80b856fb6a99b8b",
            "2021006105651938|智商乐消消|0.2.2606241540.10|devourad|zslxx_game_xiaochu_every_10^10^消除/消消,zslxx_game_pass_every_1^1^通关/通过/闯关|appKey=87e3d612cefbe59822d5c6cbde82a74f",
            "2021004138613197|农场对对碰|0.2.2601041522.28|dianli100|ncddp_game_match_every_5^5^对局/对战/比赛|",
            "2021004170626565|消费金对对碰|0.2.2504291637.15|dianli100|xfjddp_game_pass_every_1^1^通关/通过/闯关,xfjddp_game_hecheng_every_10^10^合成/合并|",
            "2021005111689183|会员欢乐消|0.2.2601041522.28|dianli100|ttddp_game_pass_every_1^1^通关/通过/闯关|",
            "2021005132609824|汉字消消大师|0.2.2512261652.33|dianwanshidai|hzxxds_game_pass_every_1^1^通关/通过/闯关,hzxxds_game_xiaochu_every_10^10^消除/消消|bgid=6501",
            "2021005132680209|这关我很行|0.2.2602130949.23|dianwanshidai|zgwhx_game_level_every_1^1^关卡/通关/通过|bgid=6560",
            "2021005187620542|成语小福星|0.2.2512301639.48|dianwanshidai|cyxfx_game_pass_every_1^1^通关/通过/闯关,cyxfx_game_xiaochu_every_10^10^消除/消消|bgid=6617",
            "2021006101606146|分离吧小鸡|0.2.2601041709.52|dianwanshidai|flbxj_game_pass_every_1^1^通关/通过/闯关|bgid=6661",
            "2021006109602033|套住那只羊|0.2.2606301404.35|dianwanshidai|tznzy_game_pass_every_1^1^通关/通过/闯关,tznzy_game_daoju_every_1^1^道具|bgid=6680",
            "2021006119624486|农场分色瓶|0.2.2602271145.38|dianwanshidai|ncfsp_game_pass_every_1^1^通关/通过/闯关|bgid=6705",
            "2021006131625225|玩个倒水|0.2.2606181714.32|dianwanshidai|wgds_game_pass_every_1^1^通关/通过/闯关|bgid=6798",
            "2021006151633350|谁来救救鹅|0.2.2607151542.41|dianwanshidai|sljje_game_pass_every_1^1^通关/通过/闯关|bgid=6914",
            "2060170000378094|你是真厉害|0.2.2602031151.46|dianwanshidai|nszlh_game_pass_every_1^1^通关/通过/闯关,nszlh_game_xiaochu_every_20^20^消除/消消,nszlh_game_daoju_every_1^1^道具|bgid=6624",
            "2060170000385587|农场织织乐|0.2.2602261702.58|dianwanshidai|nczzl_game_xiaochu_every_20^20^消除/消消|bgid=6704",
            "2060170000399014|挪了下车|0.2.2608251627.21|dianwanshidai|nlxc_game_pass_every_1^1^通关/通过/闯关|bgid=6969",
            "2060170000399540|眼力拼图|0.2.2609161736.19|dianwanshidai|ylpt_game_pass_every_1^1^通关/通过/闯关|bgid=6968",
            "2060170000393854|斗破苍穹|0.2.2607061716.45|flourish|dpcq_game_pass_every_1^1^通关/通过/闯关,dpcq_game_shop_every_1^1^商铺,dpcq_game_douji_every_1^1^竞技场,dpcq_game_take_every_1^1^游历,dpcq_game_zhaomu_every_1^1^招募,dpcq_game_huoyue_every_5^5^活跃,dpcq_game_shengji_every_1^1^升级|appKey=a307ac9468c154a3db020597397b12be",
            "2021004148695291|打螺丝大师|0.2.2511181728.34|flysheeep|dlsds_game_pass_every_1^1^通关/通过/闯关|",
            "2060170000375112|猪了个猪|0.2.2511181728.34|flysheeep|zlgz_game_pass_every_1^1^通关/通过/闯关|",
            "2060170000377298|指尖王国|0.2.2609031704.54|h5i13yx|3^1^,2^1^|appKey=10002",
            "2021005139650112|打个螺丝|0.2.2602281817.3|haligame|dgls_game_level_every_1^1^通关/通过/闯关,dgls_game_daoju_every_1^1^道具,dgls_game_xiaochu_every_5^5^消除/消消|",
            "2021004163668677|我的花园世界|0.2.2607091304.58|hnycgames|wdhysj_game_dingdan_every_2^2^订单/居民订单,wdhysj_game_zhuxian_every_1^1^主线,wdhysj_game_gukedan_every_5^5^顾客订单/顾客单,wdhysj_game_richang_every_1^1^日常|",
            "2021004134649102|货柜消消消|0.2.2509031342.19|igame58|hgxxx_game_game_pass_every_1^1^通关/通过/闯关|",
            "2021004145605636|货柜趣消消|0.2.2510211823.14|igame58|hgqxx_game_pass_every_1^1^通关/通过/闯关|",
            "2021004148696299|闯关挪挪车|0.2.2510311929.40|igame58|cgnnc_game_pass_every_1^1^通关/通过/闯关|",
            "2021004199670767|小鸡货柜消除|0.2.2512171756.18|igame58|xjhgxc_game_game_pass_every_1^1^通关/通过/闯关|",
            "2021004199674631|小鸡挪车|0.2.2512171758.11|igame58|xjnc_game_pass_every_1^1^通关/通过/闯关|",
            "2021005161639016|会员挪小羊|0.2.2507211449.1|igame58|hynxy_game_pass_every_1^1^通关/通过/闯关|",
            "2021005181698249|消消来解压|0.2.2606041504.28|igame58|xxljy_game_pass_every_1^1^通关/通过/闯关|",
            "2021005176645204|解压俄罗斯方块|0.2.2607241635.14|jingyou|jyelsfk_game_pass_every_1^1^通关/通过/闯关|appKey=ofHCFJVjdl7Wr169G5uSOmXjKaMEIniT",
            "2021005197696986|小鸡方块拼图|0.2.2603171953.39|jingyou|xjfkpt_game_jingdian_every_500^500^经典,xjfkpt_game_pass_every_1^1^通关/通过/闯关|appKey=ofHCFJVjdl7Wr169G5uSOmXjKaMEIniT",
            "2060170000358569|会员爱解压|0.2.2509081602.46|landintheair|hyajy_game_pass_every_1^1^通关/通过/闯关|",
            "2060170000366791|疯狂爱消除|0.2.2512011739.5|landintheair|fkaxc_game_hecheng_every_10^10^合成/合并|",
            "2060170000373301|弓箭手对决|0.2.2509301438.22|lumosfun|gjsdj_game_win_every_1^1^胜利/获胜/挑战|loginHost=azory.lumosfun.com:8525;reportHost=gjdz-zfb-def.gusspro.com:15087",
            "2021004138694188|营救小鸡|0.2.2606051701.41|manyoukeji2024|yjxj_game_dingzi_every_10^10^钉子/道具|",
            "2021005190608132|密室出逃|0.2.2511141651.44|maoyumeng|msct_game_pass_every_1^1^通关/通过/闯关|",
            "2060170000365923|浪漫餐厅|0.2.2601281402.55|microfun|lmct_game_order_every_1^1^订单,lmct_game_merge_every_5^5^合成/合并|path=61b45d",
            "2021004122659378|猛鬼宿舍|0.2.2602281119.3|mihuangame|mgss_game_shengji_every_1^1^升级/升到|",
            "2021004123689742|躺平发育|0.2.2602281055.3|mihuangame|tpfy_game_shengji_every_1^1^升级/升到|",
            "2021005112626489|合不过我吧|0.2.2607011748.44|mmhygame|hbgwb_game_pass_every_1^1^通关/通过/闯关,hbgwb_game_xiaochu_every_10^10^消除/消消|",
            "2021006137600649|简直是天才|0.2.2608191623.26|mmhygame|jzstc_game_pass_every_1^1^通关/通过/闯关,jzstc_game_xiaochu_every_10^10^消除/消消|",
            "2021005112624997|动物叠高高|0.2.2603122222.47|modyoo|dwdgg_game_hecheng_every_1^1^合成/合并|",
            "2021004157677654|小鸡料理王|0.2.2608071935.8|moxigame|xjllw_game_hecheng_every_10^10^合成/合并|",
            "2021005113663065|森林改造计划|0.2.2601051147.45|moyangmoyang|slgzjh_game_hecheng_every_10^10^合成/合并|",
            "2060170000359285|一步两步|0.2.2607140922.20|nebula737|yblb_game_pass_every_1^1^通关/通过/闯关,yblb_game_jineng_every_1^1^技能,yblb_game_gaojixuyuan_every_1^1^高级许愿/许愿|nbAppKey=ac23221b56c6ab0d442094b67bf794c9;nebulaAppId=30488;companyId=69;channelId=100;sdkVersion=3.4.35;gameVersion=1.6.13",
            "2021004171692313|女孩与海|0.2.2507232209.2|nhyhtaobao||",
            "2021004143669557|会员小庭院|0.2.2607301647.41|oneMoxigame|iaphyxty_game_dingdan_every_5^5^订单,iaphyxty_game_hecheng_every_10^10^合成/合并|",
            "2021004173649916|四季物语|0.2.2607172159.19|qmhd87|sjwy_game_hecheng_every_20^20^合成/合并,sjwy_game_dingdan_every_1^1^订单,sjwy_game_jiaju_every_1^1^家具|reportHost=game2-sjwy.qmhd87.com;channelCode=PA0306;packageName=projectA;sdkVersion=6.9.7",
            "2021004130631819|农场纸牌塔|0.2.2606151503.30|qufang|farmzpt_game_pass_1_misson^1^通关/通过|versionApp=4.4.5;loginHost=login-01.qufang.top;reportHost=game-factory-jail-001.qufang.top",
            "2021004132661719|天天怼方块|0.2.2512101743.54|qufang|zydfk_game_hecheng_every_5^5^合成/合并|versionApp=4.4.5;loginHost=login-01.qufang.top;reportHost=game-factory-jail-001.qufang.top",
            "2021004148695256|庄园怼方块|0.2.2606041614.13|qufang|zydfk_game_hecheng_every_5^5^合成/合并|versionApp=4.4.5;loginHost=login-01.qufang.top;reportHost=game-factory-jail-001.qufang.top",
            "2021004129659235|疯狂旋转模拟器|0.2.2602031626.19|qxygame|fkxzmnq_game_jibai_every_1^1^击败/挑战|",
            "2021004160635662|萌萌闯关达人|0.2.2512121514.7|qxygame|mmcgdr_game_pass_every_1^1^通关/通过/闯关|",
            "2060170000399302|占城大师|0.2.2609172112.5|raiyantway|zcds_game_duiju_every_1^1^对局/对战/比赛,zcds_game_baoxiang_every_1^1^宝箱|loginHost=cbqta.rayiantway.com:8525;userType=31",
            "2021006118661999|果园消螺丝|0.2.2603041819.11|server8haligame|gyxls_game_xiaochu_every_5^5^消除/消消|",
            "2060170000358895|超元火柴人|0.2.2501072053.14|soulgame|cyqhcr_game_renyifuben_every_1^1^副本|appKey=sg1d1bf57c8def15ee",
            "2021004167642405|农场水滴排序|0.2.2507111655.8|tgpax|ncspx_game_pass_every_1^1^通关/通过/闯关|",
            "2021006139626077|时尚百货城|0.2.2607140329.43|tomato|ssbhc_game_dpsj_every_10^10^店铺升级/店铺,ssbhc_game_hbts_every_10^10^伙伴提升/伙伴,ssbhc_game_rw_every_1^1^任务,ssbhc_game_gcyf_every_10^10^工厂研发/研发,ssbhc_game_pass_every_1^1^通关/通过/闯关|sdkVersion=1.0.0;secret=24AB7463A3C613B430EDEBCEE1DFA190;gameId=1100313;platformKey=zfb_xyx_ssbhc;sdkChannel=zfbxyx_and_ssbhcwxand_34;cid=2200449",
            "2021003193629997|茶叶蛋大冒险|0.2.2606121444.57|ultralisk|cyddmx_game_pass_level_1^1^闯关/通关/通过|",
            "2021005107607717|茶叶蛋大解密|0.2.2602091812.10|ultralisk|cyddjm_game_pass_every_1^1^通关/通过/闯关|",
            "2021005130646326|废物幸存者|0.2.2601071550.0|ultralisk|fxxcz_game_pass_every_1^1^通关/通过/闯关|",
            "2021004150609014|农场叠叠叠|0.2.2504071609.32|vip888888|ncddd_game_pipei_every_5^5^配对/匹配|sdkVersion=v1.0.1;gameVersion=20250331V1.0.0",
            "2021005100698187|消消小能手|0.2.2512241437.43|yigomob|xxxns_game_pass_every_1^1^通关/通过/闯关|",
            "2060170000357463|我要下西洋|0.2.2501211752.30|yoqu|wyxxy_game_jianzao_every_1^1^建造/建筑|",
            "2060170000359949|我要下西洋农场版|0.2.2501222004.56|yoqu|wyxxyncb_game_jianzao_every_1^1^建造/建筑|",
            "2060170000359968|我要下西洋会员版|0.2.2501221829.7|yoqu|wyxxyhyb_game_jianzao_every_1^1^建造/建筑|",
            "2060170000352323|九梦仙域|0.2.2606111657.38|youyantech|jmxy_game_tianguan_every_1^1^天关/闯关,jmxy_game_mowang_every_1^1^魔王|",
            "2021004122696780|青云诀之伏魔|0.2.2606261000.30|yyxxgame|qyj_game_pass_level_every_3^3^通关/通过/闯关,qyj_game_mission_every_1^1^任务/挑战,qyj_game_level_every_1^1^关卡/升级,qyj_game_fengmoguta_every_1^1^伏魔塔,qyj_game_jiuyoufuben_every_1^1^九幽|apiKey=5t5s9a18bcfdfb8a4a4a10c546dg8r58;oid=rhkyx;gcid=Y09020301",
            "2021005120637256|源星战域|0.2.2607101555.51|yyxxgame|yxzy_game_pass_every_1^1^通关/通过/闯关,yxzy_game_mission_every_1^1^任务/挑战,yxzy_game_boss_every_1^1^BOSS/首领|apiKey=8f882d04f3d064c06ef40d76b77143a1;oid=zfb;gcid=Y011020301;reportHost=yxdasapi.yxzygame.com",
            "2021004184670590|农场火柴人|0.2.2504281703.57|zuiqiangyingyu|nchcr_game_pass_every_1^1^通关/通过/闯关|"
    };

    /** 单个动作。 */
    public static final class Action {
        public final String code;
        /** 每 N 次算 1 个进度（来自 actionCode 末尾 {@code _every_N}）。 */
        public final int every;
        /** 中文动作名，可能为空；多个同义关键词用 {@code /} 分隔。 */
        public final String label;

        Action(String code, int every, String label) {
            this.code = code;
            this.every = every;
            this.label = label;
        }

        /** 标题是否命中本动作的中文名（支持 {@code /} 分隔的多关键词）。 */
        public boolean matches(String title) {
            if (label.isEmpty() || title == null) {
                return false;
            }
            for (String keyword : label.split("/")) {
                String k = keyword.trim();
                if (!k.isEmpty() && title.contains(k)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** 单个游戏。 */
    public static final class Game {
        public final String appId;
        public final String name;
        public final String version;
        public final String sdk;
        public final List<Action> actions;
        final Map<String, String> params;

        Game(String appId, String name, String version, String sdk,
             List<Action> actions, Map<String, String> params) {
            this.appId = appId;
            this.name = name;
            this.version = version;
            this.sdk = sdk;
            this.actions = Collections.unmodifiableList(actions);
            this.params = Collections.unmodifiableMap(params);
        }
    }

    private static final Map<String, Game> TABLE = new HashMap<>();

    /** 把管道分隔的行字符串解析成 Game（仅用于内置默认表 {@code ROWS}）。 */
    private static Game parseRow(String row) {
        if (row == null) {
            return null;
        }
        String[] parts = row.split("\\|", 6);
        if (parts.length < 4) {
            return null;
        }
        List<Action> actions = new ArrayList<>();
        if (parts.length > 4 && !parts[4].isEmpty()) {
            for (String item : parts[4].split(",")) {
                String[] f = item.split("\\^", 3);
                if (f.length < 2) {
                    continue;
                }
                int every;
                try {
                    every = Integer.parseInt(f[1]);
                } catch (NumberFormatException e) {
                    every = 1;
                }
                actions.add(new Action(f[0], every, f.length > 2 ? f[2] : ""));
            }
        }
        Map<String, String> params = new LinkedHashMap<>();
        if (parts.length > 5 && !parts[5].isEmpty()) {
            for (String item : parts[5].split(";")) {
                String[] f = item.split("=", 2);
                if (f.length == 2) {
                    params.put(f[0], f[1]);
                }
            }
        }
        return new Game(parts[0], parts[1], parts[2], parts[3], actions, params);
    }

    /** Game 序列化为 JSON 友好的 Map：actions / params 都是结构化键值对。 */
    private static Map<String, Object> toRowMap(Game game) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("appId", game.appId);
        map.put("name", game.name);
        map.put("version", game.version);
        map.put("sdk", game.sdk);
        List<Map<String, Object>> actionList = new ArrayList<>();
        for (Action action : game.actions) {
            Map<String, Object> am = new LinkedHashMap<>();
            am.put("code", action.code);
            am.put("every", action.every);
            List<String> labels = new ArrayList<>();
            if (!action.label.isEmpty()) {
                for (String k : action.label.split("/")) {
                    String t = k.trim();
                    if (!t.isEmpty()) {
                        labels.add(t);
                    }
                }
            }
            am.put("label", labels);
            actionList.add(am);
        }
        map.put("actions", actionList);
        map.put("params", game.params);
        return map;
    }

    /** 从 JSON Map 反序列化回 Game；label 兼容数组与字符串两种写法。 */
    @SuppressWarnings("unchecked")
    private static Game fromRowMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        String appId = asString(map.get("appId"));
        if (appId.isEmpty()) {
            return null;
        }
        String name = asString(map.get("name"));
        String version = asString(map.get("version"));
        String sdk = asString(map.get("sdk"));
        List<Action> actions = new ArrayList<>();
        Object actionsObj = map.get("actions");
        if (actionsObj instanceof List) {
            for (Object item : (List<Object>) actionsObj) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<String, Object> am = (Map<String, Object>) item;
                String code = asString(am.get("code"));
                if (code.isEmpty()) {
                    continue;
                }
                int every = asInt(am.get("every"), 1);
                String label = asLabel(am.get("label"));
                actions.add(new Action(code, every, label));
            }
        }
        Map<String, String> params = new LinkedHashMap<>();
        Object paramsObj = map.get("params");
        if (paramsObj instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) paramsObj).entrySet()) {
                if (e.getValue() != null) {
                    params.put(e.getKey(), String.valueOf(e.getValue()));
                }
            }
        }
        return new Game(appId, name, version, sdk, actions, params);
    }

    private static String asString(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    /** label 在 JSON 里可能是字符串或数组，统一合并成 {@code /} 分隔串供内部匹配。 */
    @SuppressWarnings("unchecked")
    private static String asLabel(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Object item : (List<Object>) o) {
                if (item == null) {
                    continue;
                }
                String s = String.valueOf(item).trim();
                if (s.isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('/');
                }
                sb.append(s);
            }
            return sb.toString();
        }
        return String.valueOf(o);
    }

    static {
        // 内置默认表仅作为首次生成 JSON 文件的种子；TABLE 始终从磁盘 JSON 读取构建
        List<Game> defaultGames = new ArrayList<>();
        for (String row : ROWS) {
            Game g = parseRow(row);
            if (g != null) {
                defaultGames.add(g);
            }
        }
        List<Game> games = defaultGames;
        try {
            File file = FileUtil.getMiniGameTaskTableFile();
            if (file != null) {
                if (!file.exists() || FileUtil.readFromFile(file).trim().isEmpty()) {
                    // 文件不存在或为空：用内置默认表生成后，再从文件读回
                    writeDefaultTable(file, defaultGames);
                }
                List<Game> loaded = loadFromFile(file);
                if (loaded != null && !loaded.isEmpty()) {
                    games = loaded;
                } else {
                    // 解析失败：重建后再次回读；仍失败则保留默认表
                    writeDefaultTable(file, defaultGames);
                    List<Game> retry = loadFromFile(file);
                    if (retry != null && !retry.isEmpty()) {
                        games = retry;
                    }
                }
            }
        } catch (Throwable t) {
            // 任何异常都回退到内置默认表，保证类能加载
            Log.printStackTrace(t);
        }
        for (Game g : games) {
            TABLE.put(g.appId, g);
        }
    }

    private static List<Game> loadFromFile(File file) {
        return loadGames(FileUtil.readFromFile(file).trim());
    }

    private static void writeDefaultTable(File file, List<Game> games) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Game g : games) {
            rows.add(toRowMap(g));
        }
        FileUtil.write2File(JsonUtil.toFormatJsonString(rows), file);
    }

    /** 解析磁盘 JSON：优先对象数组，旧版字符串数组也兼容。 */
    @SuppressWarnings("unchecked")
    private static List<Game> loadGames(String body) {
        try {
            List<Object> raw = JsonUtil.parseObject(body, new TypeReference<List<Object>>() {
            });
            if (raw == null || raw.isEmpty()) {
                return null;
            }
            List<Game> games = new ArrayList<>();
            for (Object item : raw) {
                Game g = null;
                if (item instanceof Map) {
                    g = fromRowMap((Map<String, Object>) item);
                } else if (item instanceof String) {
                    g = parseRow((String) item);
                }
                if (g != null) {
                    games.add(g);
                }
            }
            return games.isEmpty() ? null : games;
        } catch (Throwable t) {
            Log.printStackTrace(t);
            return null;
        }
    }

    private MiniGameTaskTable() {
    }

    /** 取游戏元信息；未收录返回 null。 */
    public static Game get(String appId) {
        return appId == null ? null : TABLE.get(appId);
    }

    /** 游戏版本号；未收录返回空串。 */
    public static String version(String appId) {
        Game game = get(appId);
        return game == null ? "" : game.version;
    }

    /** 游戏名；未收录返回空串。 */
    public static String name(String appId) {
        Game game = get(appId);
        return game == null ? "" : game.name;
    }

    /** 所属厂商 SDK 名（如 {@code haligame}、{@code nebula737}）；未知返回空串。 */
    public static String sdk(String appId) {
        Game game = get(appId);
        return game == null ? "" : game.sdk;
    }

    /** 动作集合（可能为空，不会返回 null）。 */
    public static List<Action> actions(String appId) {
        Game game = get(appId);
        return game == null ? Collections.<Action>emptyList() : game.actions;
    }

    /**
     * 动作码的 {@code every_N}：每 N 次动作算 1 个任务进度。
     *
     * <p>优先取静态适配表里登记的值；表里没有该动作码时从后缀 {@code _every_N} 解析；
     * 都取不到按 1 处理。
     */
    public static int everyOf(String appId, String actionCode) {
        if (actionCode == null || actionCode.isEmpty()) {
            return 1;
        }
        for (Action action : actions(appId)) {
            if (actionCode.equals(action.code)) {
                return Math.max(1, action.every);
            }
        }
        int index = actionCode.lastIndexOf("_every_");
        if (index >= 0) {
            try {
                return Math.max(1, Integer.parseInt(actionCode.substring(index + 7)));
            } catch (NumberFormatException ignored) {
            }
        }
        return 1;
    }

    /** 取厂商参数（如 {@code bgid}）；缺失返回空串。 */
    public static String param(String appId, String key) {
        Game game = get(appId);
        if (game == null) {
            return "";
        }
        String value = game.params.get(key);
        return value == null ? "" : value;
    }

    /** 全量游戏（含无协议实现的 SDK），供统计/调试。 */
    public static Map<String, Game> all() {
        return Collections.unmodifiableMap(TABLE);
    }
}
