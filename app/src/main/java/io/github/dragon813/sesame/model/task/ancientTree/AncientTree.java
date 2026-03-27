package io.github.dragon813.sesame.model.task.ancientTree;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.dragon813.sesame.data.ModelFields;
import io.github.dragon813.sesame.data.ModelGroup;
import io.github.dragon813.sesame.data.modelFieldExt.BooleanModelField;
import io.github.dragon813.sesame.data.modelFieldExt.SelectModelField;
import io.github.dragon813.sesame.data.task.ModelTask;
import io.github.dragon813.sesame.entity.AreaCode;
import io.github.dragon813.sesame.model.base.TaskCommon;
import io.github.dragon813.sesame.util.Log;
import io.github.dragon813.sesame.util.MessageUtil;
import io.github.dragon813.sesame.util.Status;
import io.github.dragon813.sesame.util.TimeUtil;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;

public class AncientTree extends ModelTask {
    private static final String TAG = AncientTree.class.getSimpleName();

    @Override
    public String getName() {
        return "古树";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.FOREST;
    }

    private BooleanModelField ancientTreeOnlyWeek;
    private SelectModelField ancientTreeCityCodeList;

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(ancientTreeOnlyWeek = new BooleanModelField("ancientTreeOnlyWeek", "仅星期一、三、五运行保护古树",
                false));
        modelFields.addField(ancientTreeCityCodeList = new SelectModelField("ancientTreeCityCodeList", "古树区划代码列表",
                new LinkedHashSet<>(), AreaCode::getList));
        return modelFields;
    }

    @Override
    public Boolean check() {
        if (!TaskCommon.IS_ENERGY_TIME && TaskCommon.IS_AFTER_8AM) {
            if (!ancientTreeOnlyWeek.getValue()) {
                return true;
            } SimpleDateFormat sdf_week = new SimpleDateFormat("EEEE", Locale.getDefault());
            String week = sdf_week.format(new Date());
            return "星期一".equals(week) || "星期三".equals(week) || "星期五".equals(week);
        } return false;
    }

    @Override
    public void run() {
        try {
            Log.record("开始检测古树保护"); ancientTree(ancientTreeCityCodeList.getValue());
        } catch (Throwable t) {
            Log.i(TAG, "start.run err:"); Log.printStackTrace(TAG, t);
        }
    }

    private static void ancientTree(Collection<String> ancientTreeCityCodeList) {
        try {
            for (String cityCode : ancientTreeCityCodeList) {
                if (!Status.canAncientTreeToday(cityCode))
                    continue; ancientTreeProtect(cityCode); Thread.sleep(1000L);
            }
        } catch (Throwable th) {
            Log.i(TAG, "ancientTree err:"); Log.printStackTrace(TAG, th);
        }
    }

    private static void ancientTreeProtect(String cityCode) {
        try {
            JSONObject jo = new JSONObject(AncientTreeRpcCall.homePage(cityCode));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            } JSONObject data = jo.getJSONObject("data"); if (!data.has("districtBriefInfoList")) {
                return;
            } JSONArray districtBriefInfoList = data.getJSONArray("districtBriefInfoList");
            for (int i = 0; i < districtBriefInfoList.length(); i++) {
                JSONObject districtBriefInfo = districtBriefInfoList.getJSONObject(i);
                int userCanProtectTreeNum = districtBriefInfo.optInt("userCanProtectTreeNum", 0);
                if (userCanProtectTreeNum < 1)
                    continue; JSONObject districtInfo = districtBriefInfo.getJSONObject("districtInfo");
                String districtCode = districtInfo.getString("districtCode"); districtDetail(districtCode);
                TimeUtil.sleep(1000L);
            } Status.ancientTreeToday(cityCode);
        } catch (Throwable th) {
            Log.i(TAG, "ancientTreeProtect err:"); Log.printStackTrace(TAG, th);
        }
    }

    private static void districtDetail(String districtCode) {
        try {
            JSONObject jo = new JSONObject(AncientTreeRpcCall.districtDetail(districtCode));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            } JSONObject data = jo.getJSONObject("data"); if (!data.has("ancientTreeList")) {
                return;
            } JSONObject districtInfo = data.getJSONObject("districtInfo");
            String cityCode = districtInfo.getString("cityCode"); String cityName = districtInfo.getString("cityName");
            String districtName = districtInfo.getString("districtName");
            JSONArray ancientTreeList = data.getJSONArray("ancientTreeList");
            for (int i = 0; i < ancientTreeList.length(); i++) {
                JSONObject ancientTreeItem = ancientTreeList.getJSONObject(i);
                if (ancientTreeItem.getBoolean("hasProtected"))
                    continue;
                JSONObject ancientTreeControlInfo = ancientTreeItem.getJSONObject("ancientTreeControlInfo");
                int quota = ancientTreeControlInfo.optInt("quota", 0);
                int useQuota = ancientTreeControlInfo.optInt("useQuota", 0); if (quota <= useQuota)
                    continue; String itemId = ancientTreeItem.getString("projectId");
                JSONObject ancientTreeDetail = new JSONObject(AncientTreeRpcCall.projectDetail(itemId, cityCode));
                if (!MessageUtil.checkResultCode(TAG, ancientTreeDetail)) {
                    continue;
                } data = ancientTreeDetail.getJSONObject("data"); if (data.getBoolean("canProtect")) {
                    int currentEnergy = data.getInt("currentEnergy");
                    JSONObject ancientTree = data.getJSONObject("ancientTree");
                    String activityId = ancientTree.getString("activityId");
                    String projectId = ancientTree.getString("projectId");
                    JSONObject ancientTreeInfo = ancientTree.getJSONObject("ancientTreeInfo");
                    String name = ancientTreeInfo.getString("name"); int age = ancientTreeInfo.getInt("age");
                    int protectExpense = ancientTreeInfo.getInt("protectExpense");
                    cityCode = ancientTreeInfo.getString("cityCode"); if (currentEnergy < protectExpense)
                        break; TimeUtil.sleep(200);
                    jo = new JSONObject(AncientTreeRpcCall.protect(activityId, projectId, cityCode));
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.forest("保护古树🎐[" + cityName + "-" + districtName + "]#" + age + "年" + name + ",消耗能量" + protectExpense + "g");
                    }
                } TimeUtil.sleep(500L);
            }
        } catch (Throwable th) {
            Log.i(TAG, "districtDetail err:"); Log.printStackTrace(TAG, th);
        }
    }
}
