package io.github.lazyimmortal.sesame.model.task.antOrchard;

import android.content.Intent;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.hook.Toast;
import io.github.lazyimmortal.sesame.mtop.bridge.MtopCaller;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.TimeUtil;

/**
 * 芭芭农场自动收获+自动种植: 果树可兑换时静默拉取种成记录并兜底打开兑换页面, 随后读取种子列表默认种第一个有库存的种子
 */
public class AntOrchardAutoHarvest {
    private static final String TAG = "AntOrchardAutoHarvest";
    private static final String LOG_TAG = "农场收获🌳";

    // 固定兑换链接(静默拉取失败时兜底打开)
    private static final String FIXED_EXCHANGE_URL = "https://pages-fast.m.taobao.com/wow/z/hdwk/farm-ssr/tmfarm-harvest?disableNav=YES&spm=farm";

    private static final Random RANDOM = new Random();

    /* 随机延迟2-5秒, 模拟真人操作节奏 */
    private static int getRandomDelay() {
        return 2000 + RANDOM.nextInt(3001);
    }

    /**
     * 自动收获: 静默拉取种成记录并展示, 失败兜底打开兑换页面, 随后按开关尝试种植
     *
     * @param enableAutoPlant 是否开启自动种植
     */
    public static boolean harvest(boolean enableAutoPlant) {
        try {
            Log.record(LOG_TAG + "开始自动收获");

            TimeUtil.sleep(getRandomDelay());

            Log.record(LOG_TAG + "打开种成记录(执行收获)...");
            String exchangeResp = silentFetchExchange();
            boolean ok = false;
            if (exchangeResp != null && exchangeResp.length() > 0) {
                ok = reportExchange(exchangeResp);
            } else {
                Log.i(TAG, "获取数据为空");
            }

            if (!ok) {
                Log.record(LOG_TAG + "静默失败，跳转前台页面...");
                openUrl(FIXED_EXCHANGE_URL);
                TimeUtil.sleep(getRandomDelay());
            }

            if (enableAutoPlant) {
                autoPlant();
                Log.farm(LOG_TAG + "自动收获+种植完成");
            } else {
                Log.farm(LOG_TAG + "自动收获完成");
            }
            return true;
        } catch (Throwable t) {
            Log.i(TAG, "harvest err:");
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    /**
     * 自动种植: 读取种子列表, 默认选第一个有库存的种子种下
     */
    public static boolean autoPlant() {
        try {
            Log.record(LOG_TAG + "开始自动种植...");

            TimeUtil.sleep(getRandomDelay());

            String[] selected = selectFirstSeed();
            if (selected == null) {
                Log.record(LOG_TAG + "无可用种子库存，跳过种植");
                return false;
            }

            String seedCode = selected[0];
            String seedName = selected[1];
            Log.record(LOG_TAG + "默认选第一个种: " + seedName);

            TimeUtil.sleep(getRandomDelay());

            String result = AntOrchardRpcCall.orchardSelectSeed(seedCode);
            JSONObject jo = new JSONObject(result);

            if (MessageUtil.checkResultCode(TAG, jo) && jo.optBoolean("success", false)) {
                Log.farm(LOG_TAG + "种植" + seedName + "成功");
                Toast.show("芭芭农场已种植: " + seedName);
                return true;
            } else {
                Log.record(LOG_TAG + "种植" + seedName + "失败: " + jo.optString("resultDesc", "未知错误"));
                return false;
            }
        } catch (Throwable t) {
            Log.i(TAG, "autoPlant err:");
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    /**
     * 读取种子列表: 输出可选种子[种子1、种子2...], 默认选第一个有库存的
     *
     * @return {seedCode, seedName}, 无可用种子返回null
     */
    public static String[] selectFirstSeed() {
        try {
            String result = AntOrchardRpcCall.orchardSeedList();
            JSONObject jo = new JSONObject(result);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return null;
            }

            JSONObject taobaoData = new JSONObject(jo.getString("taobaoData"));
            JSONArray seedList = taobaoData.getJSONArray("seedList");

            List<String> options = new ArrayList<>();
            String[] first = null;
            for (int i = 0; i < seedList.length(); i++) {
                JSONObject seed = seedList.getJSONObject(i);
                if (!seed.optBoolean("hasInventory", false)) {
                    continue;
                }
                String seedCode = seed.optString("seedCode");
                String seedName = seed.optString("seedTitle");
                if (seedCode == null || seedCode.length() == 0) {
                    continue;
                }
                options.add(seedName);
                if (first == null) {
                    first = new String[]{seedCode, seedName};
                }
            }
            if (options.isEmpty()) {
                Log.record(LOG_TAG + "可选种子为空(均无库存)");
            } else {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < options.size(); i++) {
                    if (i > 0) {
                        sb.append("、");
                    }
                    sb.append(options.get(i));
                }
                sb.append("]");
                Log.record(LOG_TAG + "可选种子" + sb );
            }
            return first;
        } catch (Throwable t) {
            Log.i(TAG, "selectFirstSeed err:");
            Log.printStackTrace(TAG, t);
            return null;
        }
    }

    /**
     * 解析静默返回, 输出最新一条待兑换记录详情及累计种成/待兑换次数
     *
     * @return 拿到 grownUpRecords 返回 true
     */
    private static boolean reportExchange(String resp) {
        try {
            JSONObject jo = new JSONObject(resp);
            JSONObject data = jo.optJSONObject("data");
            if (data == null || !"OK".equals(data.optString("code"))) {
                Log.record(LOG_TAG + "兑换列表获取失败: code=" + (data == null ? "null" : data.optString("code"))
                        + " desc=" + (data == null ? "" : data.optString("resultDesc")));
                return false;
            }
            JSONObject model = data.optJSONObject("model");
            if (model == null) {
                Log.record(LOG_TAG + "兑换列表获取失败: 无 model");
                return false;
            }
            JSONObject acc = model.optJSONObject("accumulateTimes");
            JSONArray records = model.optJSONArray("grownUpRecords");
            if (acc != null) {
                String detail = "";
                if (records != null) {
                    for (int i = 0; i < records.length(); i++) {
                        JSONObject r = records.optJSONObject(i);
                        if (r != null && "UNEXCHANGED".equals(r.optString("status"))) {
                            detail = r.optString("title", "") + "已种成，用时"
                                    + r.optInt("grownUpConsumeDays", -1) + "天（"
                                    + r.optString("plantTime", "") + "）；";
                            break;
                        }
                    }
                }
                String summary = detail + "累计" + acc.optInt("grownUpTimes", -1)
                        + "次，待兑换" + acc.optInt("unExchangeTimes", -1) + "次";
                Log.farm(LOG_TAG + summary);
                Toast.show("芭芭农场已收获: " + summary);
            }
            if (records != null) {
                return true;
            }
            Log.record(LOG_TAG + "兑换列表获取失败: 无 grownUpRecords");
            return false;
        } catch (Throwable t) {
            Log.i(TAG, "reportExchange err: " + t);
            return false;
        }
    }

    /**
     * 静默拉取兑换列表(不走页面, 直接 mtop), 失败返回 null
     */
    private static String silentFetchExchange() {
        try {
            String data = "{\"bizParam\":\"{}\",\"bizType\":\"plantTree.showFruitList\"}";
            return MtopCaller.call(ApplicationHook.getClassLoader(), "mtop.tmall.farm.commonapi.get", data);
        } catch (Throwable t) {
            Log.i(TAG, "silentFetchExchange err: " + t);
            return null;
        }
    }

    /**
     * 打开兑换链接, 触发收获(兜底方案)
     */
    private static void openUrl(String url) {
        try {
            if (!url.startsWith("alipays://")) {
                url = "alipays://platformapi/startapp?appId=68687599&url=" + url;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ApplicationHook.getContext().startActivity(intent);
        } catch (Throwable t) {
            Log.i(TAG, "openUrl err: " + t);
        }
    }
}
