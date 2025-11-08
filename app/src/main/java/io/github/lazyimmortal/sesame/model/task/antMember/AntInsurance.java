package io.github.lazyimmortal.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Set;

import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.Status;

public class AntInsurance {
    private static final String TAG = AntInsurance.class.getSimpleName();

    public static void executeTask(Set<String> options) {
        if (options.contains("beanSignIn")) {
            beanSignIn();
        } if (options.contains("beanExchangeBubbleBoost")) {
            beanExchange("IT20230214000700069722");
        } if (options.contains("beanExchangeGoldenTicket")) {
            beanExchange("IT20240322000100086304");
        } if (options.contains("gainSumInsured")) {
            lotteryDraw(); gainSumInsured();
        }
    }

    // 保障金领取
    private static void gainSumInsured() {
        try {
            JSONObject jo = new JSONObject(AntInsuranceRpcCall.queryMultiSceneWaitToGainList());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            } jo = jo.getJSONObject("data"); Iterator<String> keys = jo.keys();
            while (keys.hasNext()) {
                String key = keys.next(); Object jsonDTO = jo.get(key);
                if (jsonDTO instanceof JSONArray) {
                    // 如eventToWaitDTOList、helpChildSumInsuredDTOList
                    JSONArray jsonArray = ((JSONArray) jsonDTO);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        gainMyAndFamilySumInsured(jsonArray.getJSONObject(i));
                    }
                } else if (jsonDTO instanceof JSONObject) {
                    // 如signInDTO、priorityChannelDTO
                    JSONObject jsonObject = ((JSONObject) jsonDTO); if (jsonObject.length() == 0) {
                        continue;
                    } gainMyAndFamilySumInsured(jsonObject);
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "gainSumInsured err:"); Log.printStackTrace(TAG, t);
        }
    }

    private static void gainMyAndFamilySumInsured(JSONObject giftData) {
        if (giftData == null || giftData.optInt("sendType", 2) != 1) {
            return;
        } try {
            giftData.put("entrance", "jkj_zhima_dairy66");
            JSONObject jo = new JSONObject(AntInsuranceRpcCall.gainMyAndFamilySumInsured(giftData));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            } jo = jo.getJSONObject("data").getJSONObject("gainSumInsuredDTO");
            Log.other("蚂蚁保障🛡️领取保障金#获得[" + jo.optString("gainSumInsuredYuan") + "元保额]");
        } catch (Throwable t) {
            Log.i(TAG, "gainMyAndFamilySumInsured err:"); Log.printStackTrace(TAG, t);
        }
    }

    // 天天领取保障福利
    private static void lotteryDraw() {
        if (Status.hasFlagToday("insurance::lotteryDraw")) {
            return;
        } try {
            JSONObject jo = new JSONObject(AntInsuranceRpcCall.queryAvailableNum());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            } jo = jo.getJSONObject("result"); if (jo.getInt("num") == 3) {
                jo = new JSONObject(AntInsuranceRpcCall.lotteryDraw());
                if (!MessageUtil.checkSuccess(TAG, jo)) {
                    return;
                } JSONArray ja = jo.getJSONArray("result"); for (int i = 0; i < ja.length(); i++) {
                    jo = ja.getJSONObject(i); String prizeName = jo.getString("prizeName");
                    Log.other("蚂蚁保障🛡️天天领取保障福利#获得[" + prizeName + "]");
                }
            } Status.flagToday("insurance::lotteryDraw");
        } catch (Throwable t) {
            Log.i(TAG, "lotteryDraw err:"); Log.printStackTrace(TAG, t);
        }
    }

    // 安心豆签到
    private static void beanSignIn() {
        try {
            JSONObject jo = new JSONObject(AntInsuranceRpcCall.beanQuerySignInProcess());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            } if (jo.getJSONObject("result").getBoolean("canPush")) {
                jo = new JSONObject(AntInsuranceRpcCall.beanSignInTrigger());
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    String prizeName = jo.getJSONObject("result").getJSONArray("prizeSendOrderDTOList").getJSONObject(0).getString("prizeName");
                    Log.other("蚂蚁保障🛡️安心豆签到#获得[" + prizeName + "]");
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "beanSignIn err:"); Log.printStackTrace(TAG, t);
        }
    }

    // 安心豆兑换
    private static void beanExchange(String itemId) {
        try {
            JSONObject jo = new JSONObject(AntInsuranceRpcCall.queryUserAccountInfo("INS_BLUE_BEAN"));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            } int userCurrentPoint = jo.getJSONObject("result").getInt("userCurrentPoint");
            jo = new JSONObject(AntInsuranceRpcCall.beanExchangeDetail(itemId));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("result").getJSONObject("rspContext").getJSONObject("params").getJSONObject("exchangeDetail");
            String itemName = jo.getString("itemName");
            jo = jo.getJSONObject("itemExchangeConsultDTO");
            int realConsumePointAmount = jo.getInt("realConsumePointAmount");
            if (!jo.getBoolean("canExchange") || realConsumePointAmount > userCurrentPoint) {
                return;
            } jo = new JSONObject(AntInsuranceRpcCall.beanExchange(itemId, realConsumePointAmount));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.other("蚂蚁保障🛡️安心豆兑换[" + itemName + "]#消耗[" + realConsumePointAmount + "安心豆]");
            }
        } catch (Throwable t) {
            Log.i(TAG, "beanExchange err:"); Log.printStackTrace(TAG, t);
        }
    }
}
