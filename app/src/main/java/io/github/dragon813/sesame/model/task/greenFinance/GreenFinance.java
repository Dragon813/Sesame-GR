package io.github.dragon813.sesame.model.task.greenFinance;

import org.json.JSONArray;
import org.json.JSONObject;
import io.github.dragon813.sesame.data.ModelFields;
import io.github.dragon813.sesame.data.ModelGroup;
import io.github.dragon813.sesame.data.modelFieldExt.BooleanModelField;
import io.github.dragon813.sesame.data.task.ModelTask;
import io.github.dragon813.sesame.model.base.TaskCommon;
import io.github.dragon813.sesame.util.JsonUtil;
import io.github.dragon813.sesame.util.Log;
import io.github.dragon813.sesame.util.Status;
import io.github.dragon813.sesame.util.TimeUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TreeMap;

/**
 * @author Constanline
 * @since 2023/09/08
 */
public class GreenFinance extends ModelTask {
    private static final String TAG = GreenFinance.class.getSimpleName();

    private BooleanModelField greenFinanceLsxd;
    private BooleanModelField greenFinanceLsbg;
    private BooleanModelField greenFinanceLscg;
    private BooleanModelField greenFinanceLswl;
    private BooleanModelField greenFinanceWdxd;
    private BooleanModelField greenFinanceDonation;
    /**
     * 是否收取好友金币
     */
    private BooleanModelField greenFinancePointFriend;

    @Override
    public String getName() {
        return "经营";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(greenFinanceLsxd = new BooleanModelField("greenFinanceLsxd", "打卡 | 绿色行动", false));
        modelFields.addField(greenFinanceLscg = new BooleanModelField("greenFinanceLscg", "打卡 | 绿色采购", false));
        modelFields.addField(greenFinanceLsbg = new BooleanModelField("greenFinanceLsbg", "打卡 | 绿色办公", false));
        modelFields.addField(greenFinanceWdxd = new BooleanModelField("greenFinanceWdxd", "打卡 | 绿色销售", false));
        modelFields.addField(greenFinanceLswl = new BooleanModelField("greenFinanceLswl", "打卡 | 绿色物流", false));
        modelFields.addField(greenFinancePointFriend = new BooleanModelField("greenFinancePointFriend", "收取 | 好友金币", false));
        modelFields.addField(greenFinanceDonation = new BooleanModelField("greenFinanceDonation", "捐助 | 快过期金币", false));
        return modelFields;
    }

    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.other("任务暂停⏸️绿色经营:当前为仅收能量时间");
            return false;
        }
        return true;
    }

    @Override
    public void  run() {
        String s = GreenFinanceRpcCall.greenFinanceIndex();
        try {
            JSONObject jo = new JSONObject(s);
            if (!jo.optBoolean("success")) {
                Log.i(TAG, jo.optString("resultDesc"));
                return;
            }
            JSONObject result = jo.getJSONObject("result");
            if (!result.getBoolean("greenFinanceSigned")) {
                Log.other("绿色经营📊未开通");
                return;
            }
            JSONObject mcaGreenLeafResult = result.getJSONObject("mcaGreenLeafResult");
            JSONArray greenLeafList = mcaGreenLeafResult.getJSONArray("greenLeafList");
            String currentCode = "";
            JSONArray bsnIds = new JSONArray();
            for (int i = 0; i < greenLeafList.length(); i++) {
                JSONObject greenLeaf = greenLeafList.getJSONObject(i);
                String code = greenLeaf.getString("code");
                if (currentCode.equals(code) || bsnIds.length() == 0) {
                    bsnIds.put(greenLeaf.getString("bsnId"));
                } else {
                    batchSelfCollect(bsnIds);
                    bsnIds = new JSONArray();
                }
            }
            if (bsnIds.length() > 0) {
                batchSelfCollect(bsnIds);
            }
        } catch (Throwable th) {
            Log.i(TAG, "index err:");
            Log.printStackTrace(TAG, th);
        }

        signIn("PLAY102632271");
//            signIn("PLAY102932217");
        signIn("PLAY102232206");

        if(TimeUtil.isNowAfterOrCompareTimeStr("0836")) {
            //执行打卡
            behaviorTick();
            //收好友金币
            batchStealFriend();
        }
        //捐助
        donation();
        //评级奖品
        prizes();
        //绿色经营
        GreenFinanceRpcCall.doTask("AP13159535", TAG, "绿色经营📊");
        TimeUtil.sleep(500);
    }

    /**
     * 批量收取
     *
     * @param bsnIds Ids
     */
    private void batchSelfCollect(final JSONArray bsnIds) {
        String s = GreenFinanceRpcCall.batchSelfCollect(bsnIds);
        try {
            JSONObject joSelfCollect = new JSONObject(s);
            if (joSelfCollect.optBoolean("success")) {
                int totalCollectPoint = joSelfCollect.getJSONObject("result").getInt("totalCollectPoint");
                Log.other("绿色经营📊收集获得" + totalCollectPoint);
            } else {
                Log.i(TAG + ".batchSelfCollect", joSelfCollect.optString("resultDesc"));
            }
        } catch (Throwable th) {
            Log.i(TAG, "batchSelfCollect err:");
            Log.printStackTrace(TAG, th);
        }
    }

    /**
     * 签到
     *
     * @param sceneId sceneId
     */
    private void signIn(final String sceneId) {
        try {
            String s = GreenFinanceRpcCall.signInQuery(sceneId);
            JSONObject jo = new JSONObject(s);
            if (!jo.optBoolean("success")) {
                Log.i(TAG + ".signIn.signInQuery", jo.optString("resultDesc"));
                return;
            }
            JSONObject result = jo.getJSONObject("result");
            if (result.getBoolean("isTodaySignin")) {
                return;
            }
            s = GreenFinanceRpcCall.signInTrigger(sceneId);
            TimeUtil.sleep(300);
            jo = new JSONObject(s);
            if (jo.optBoolean("success")) {
                Log.other("绿色经营📊签到成功");
            } else {
                Log.i(TAG + ".signIn.signInTrigger", jo.optString("resultDesc"));
            }
        } catch (Throwable th) {
            Log.i(TAG, "signIn err:");
            Log.printStackTrace(TAG, th);
        }
    }

    /**
     * 打卡
     */
    private void behaviorTick() {
        //绿色行动
        if (greenFinanceLsxd.getValue()) {
            TimeUtil.sleep(1000);
            doTick("lsxd");
            TimeUtil.sleep(1500);
        }
        //绿色采购
        if (greenFinanceLscg.getValue()) {
            TimeUtil.sleep(1000);
            doTick("lscg");
            TimeUtil.sleep(1500);
        }
        //绿色物流
        if (greenFinanceLswl.getValue()) {
            TimeUtil.sleep(1000);
            doTick("lswl");
            TimeUtil.sleep(1500);
        }
        //绿色办公
        if (greenFinanceLsbg.getValue()) {
            TimeUtil.sleep(1000);
            doTick("lsbg");
            TimeUtil.sleep(1500);
        }
        //绿色销售
        if (greenFinanceWdxd.getValue()) {
            TimeUtil.sleep(1000);
            doTick("wdxd");
            TimeUtil.sleep(1500);
        }
    }

    /**
     * 打卡绿色行为
     *
     * @param type 打开类型
     */
    private void doTick(final String type) {
        try {
            String str = GreenFinanceRpcCall.queryUserTickItem(type);
            JSONObject jsonObject = new JSONObject(str);
            if (!jsonObject.optBoolean("success")) {
                Log.i(TAG + ".doTick.queryUserTickItem", jsonObject.optString("resultDesc"));
                return;
            }
            JSONArray jsonArray = jsonObject.getJSONArray("result");
            for (int i = 0; i < jsonArray.length(); i++) {
                jsonObject = jsonArray.getJSONObject(i);
                if ("Y".equals(jsonObject.getString("status"))) {
                    continue;
                }
                str = GreenFinanceRpcCall.submitTick(type, jsonObject.getString("behaviorCode"));
                TimeUtil.sleep(1500);
                JSONObject object = new JSONObject(str);
                if (!object.optBoolean("success")
                        || !String.valueOf(true).equals(JsonUtil.getValueByPath(object, "result.result"))) {
                    Log.other("绿色经营📊[" + jsonObject.getString("title") + "]打卡失败");
                    break;
                }
                Log.other("绿色经营📊[" + jsonObject.getString("title") + "]打卡成功");
//                Thread.sleep(executeIntervalInt);
            }
        } catch (Throwable th) {
            Log.i(TAG, "doTick err:");
            Log.printStackTrace(TAG, th);
        }
    }

    /**
     * 捐助
     */
    private void donation() {
        if (!greenFinanceDonation.getValue()) {
            return;
        }
        try {
            String str = GreenFinanceRpcCall.queryExpireMcaPoint(1);
            TimeUtil.sleep(300);
            JSONObject jsonObject = new JSONObject(str);
            if (!jsonObject.optBoolean("success")) {
                Log.i(TAG + ".donation.queryExpireMcaPoint", jsonObject.optString("resultDesc"));
                return;
            }
            String strAmount = JsonUtil.getValueByPath(jsonObject, "result.expirePoint.amount");
            if (strAmount.isEmpty() || !strAmount.matches("-?\\d+(\\.\\d+)?")) {
                return;
            }
            double amount = Double.parseDouble(strAmount);
            if (amount <= 0) {
                return;
            }
            //不管是否可以捐小于非100的倍数了，，第一次捐200，最后按amount-200*n
            Log.other("绿色经营📊1天内过期的金币[" + amount + "]");
            str = GreenFinanceRpcCall.queryAllDonationProjectNew();
            TimeUtil.sleep(300);
            jsonObject = new JSONObject(str);
            if (!jsonObject.optBoolean("success")) {
                Log.i(TAG + ".donation.queryAllDonationProjectNew", jsonObject.optString("resultDesc"));
                return;
            }
            JSONArray result = jsonObject.getJSONArray("result");
            TreeMap<String, String> dicId = new TreeMap<>();
            for (int i = 0; i < result.length(); i++) {
                jsonObject = (JSONObject) JsonUtil.getValueByPathObject(result.getJSONObject(i),
                        "mcaDonationProjectResult.[0]");
                if (jsonObject == null) {
                    continue;
                }
                String pId = jsonObject.optString("projectId");
                if (pId.isEmpty()) {
                    continue;
                }
                dicId.put(pId, jsonObject.optString("projectName"));
            }
            int[] r = calculateDeductions((int) amount, dicId.size());
            String am = "200";
            for (int i = 0; i < r[0]; i++) {
                String id = new ArrayList<>(dicId.keySet()).get(i);
                String name = dicId.get(id);
                if (i == r[0] - 1) {
                    am = String.valueOf(r[1]);
                }
                str = GreenFinanceRpcCall.donation(id, am);
                TimeUtil.sleep(1000);
                jsonObject = new JSONObject(str);
                if (!jsonObject.optBoolean("success")) {
                    Log.i(TAG + ".donation." + id, jsonObject.optString("resultDesc"));
                    return;
                }
                Log.other("绿色经营📊成功捐助[" + name + "]" + am + "金币");
            }
        } catch (Throwable th) {
            Log.i(TAG, "donation err:");
            Log.printStackTrace(TAG, th);
        }
    }

    /**
     * 评级奖品
     */
    private void prizes() {
        try {
            if (!Status.canGreenFinancePrizesMap()) {
                return;
            }
            String campId = "CP14664674";
            String str = GreenFinanceRpcCall.queryPrizes(campId);
            JSONObject jsonObject = new JSONObject(str);
            if (!jsonObject.optBoolean("success")) {
                Log.i(TAG + ".prizes.queryPrizes", jsonObject.optString("resultDesc"));
                return;
            }
            JSONArray prizes = (JSONArray) JsonUtil.getValueByPathObject(jsonObject, "result.prizes");
            if (prizes != null) {
                for (int i = 0; i < prizes.length(); i++) {
                    jsonObject = prizes.getJSONObject(i);
                    String bizTime = jsonObject.getString("bizTime");
                    // 使用 SimpleDateFormat 解析字符串
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH);
                    Date dateTime = formatter.parse(bizTime);
                    if (TimeUtil.getWeekNumber(dateTime) == TimeUtil.getWeekNumber(new Date())) {
                        //本周已完成
                        Status.greenFinancePrizesMap();
                        return;
                    }
                }
            }
            str = GreenFinanceRpcCall.campTrigger(campId);
            jsonObject = new JSONObject(str);
            if (!jsonObject.optBoolean("success")) {
                Log.i(TAG + ".prizes.campTrigger", jsonObject.optString("resultDesc"));
                return;
            }
            JSONObject object = (JSONObject) JsonUtil.getValueByPathObject(jsonObject, "result.prizes.[0]");
            if (object == null) {
                return;
            }
            Log.other("绿色经营🍬评级奖品[" + object.getString("prizeName") + "]" + object.getString("price"));
        } catch (Throwable th) {
            Log.i(TAG, "prizes err:");
            Log.printStackTrace(TAG, th);
        }
    }

    /**
     * 收好友金币
     */
    private void batchStealFriend() {
        try {
            if (!Status.canGreenFinancePointFriend() || !greenFinancePointFriend.getValue()) {
                return;
            }
            int n = 0;
            while (true) {
                try {
                    String str = GreenFinanceRpcCall.queryRankingList(n);
                    TimeUtil.sleep(1500);
                    JSONObject jsonObject = new JSONObject(str);
                    if (!jsonObject.optBoolean("success")) {
                        Log.other("绿色经营🙋，好友金币巡查失败");
                        break;
                    }
                    JSONObject result = jsonObject.getJSONObject("result");
                    if (result.getBoolean("lastPage")) {
                        Log.other("绿色经营🙋，好友金币巡查完成");
                        Status.greenFinancePointFriend();
                        return;
                    }
                    n = result.getInt("nextStartIndex");
                    JSONArray list = result.getJSONArray("rankingList");
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject object = list.getJSONObject(i);
                        if (!object.getBoolean("collectFlag")) {
                            continue;
                        }
                        String friendId = object.optString("uid");
                        if (friendId.isEmpty()) {
                            continue;
                        }
                        str = GreenFinanceRpcCall.queryGuestIndexPoints(friendId);
                        TimeUtil.sleep(1000);
                        jsonObject = new JSONObject(str);
                        if (!jsonObject.optBoolean("success")) {
                            Log.i(TAG + ".batchStealFriend.queryGuestIndexPoints", jsonObject.optString("resultDesc"));
                            continue;
                        }
                        JSONArray points = (JSONArray) JsonUtil.getValueByPathObject(jsonObject, "result.pointDetailList");
                        if (points == null) {
                            continue;
                        }
                        JSONArray jsonArray = new JSONArray();
                        for (int j = 0; j < points.length(); j++) {
                            jsonObject = points.getJSONObject(j);
                            if (!jsonObject.getBoolean("collectFlag")) {
                                jsonArray.put(jsonObject.getString("bsnId"));
                            }
                        }
                        if (jsonArray.length() == 0) {
                            continue;
                        }
                        str = GreenFinanceRpcCall.batchSteal(jsonArray, friendId);
                        TimeUtil.sleep(1000);
                        jsonObject = new JSONObject(str);
                        if (!jsonObject.optBoolean("success")) {
                            Log.i(TAG + ".batchStealFriend.batchSteal", jsonObject.optString("resultDesc"));
                            continue;
                        }
                        Log.other("绿色经营🤩收[" + object.optString("nickName") + "]" +
                                JsonUtil.getValueByPath(jsonObject, "result.totalCollectPoint") + "金币");
                    }
                } catch (Exception e) {
                    Log.printStackTrace(e);
                    break;
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "batchStealFriend err:");
            Log.printStackTrace(TAG, th);
        }
    }

    /**
     * 计算次数和金额
     *
     * @param amount        最小金额
     * @param maxDeductions 最大次数
     * @return [次数，最后一次的金额]
     */
    private int[] calculateDeductions(int amount, int maxDeductions) {
        if (amount < 200) {
            // 小于 200 时特殊处理
            return new int[]{1, 200};
        }
        // 实际扣款次数，不能超过最大次数
        int actualDeductions = Math.min(maxDeductions, (int) Math.ceil((double) (amount) / 200));
        // 剩余金额
        int remainingAmount = amount - actualDeductions * 200;
        // 调整剩余金额为 100 的倍数，且不小于 200
        if (remainingAmount % 100 != 0) {
            // 向上取整到最近的 100 倍数
            remainingAmount = ((remainingAmount + 99) / 100) * 100;
        }
        if (remainingAmount < 200) {
            remainingAmount = 200;
        }
        // 如果调整后的剩余金额需要扣除更多次数，则调整实际扣款次数
        if (remainingAmount < amount - actualDeductions * 200) {
            actualDeductions = (amount - remainingAmount) / 200;
        }
        return new int[]{actualDeductions, remainingAmount};
    }
}