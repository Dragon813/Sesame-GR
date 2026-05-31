package io.github.lazyimmortal.sesame.model.task.protectEcology;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.lazyimmortal.sesame.data.ModelFields;
import io.github.lazyimmortal.sesame.data.ModelGroup;
import io.github.lazyimmortal.sesame.data.modelFieldExt.BooleanModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.IntegerModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectAndCountModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectModelField;
import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.entity.AlipayAnimal;
import io.github.lazyimmortal.sesame.entity.AlipayBeach;
import io.github.lazyimmortal.sesame.entity.AlipayMarathon;
import io.github.lazyimmortal.sesame.entity.AlipayNewAncientTree;
import io.github.lazyimmortal.sesame.entity.AlipayReserve;
import io.github.lazyimmortal.sesame.entity.AlipayTree;
import io.github.lazyimmortal.sesame.entity.CooperateUser;
import io.github.lazyimmortal.sesame.hook.Toast;
import io.github.lazyimmortal.sesame.model.base.TaskCommon;
import io.github.lazyimmortal.sesame.util.*;
import io.github.lazyimmortal.sesame.util.idMap.*;

public class ProtectEcology extends ModelTask {
    private static final String TAG = ProtectEcology.class.getSimpleName();
    
    @Override
    public String getName() {
        return "保护";
    }
    
    @Override
    public ModelGroup getGroup() {
        return ModelGroup.FOREST;
    }
    
    private static BooleanModelField cooperateWater;
    private static SelectAndCountModelField cooperateWaterList;
    private static SelectAndCountModelField cooperateWaterTotalLimitList;
    private static ChoiceModelField protectMarathonType;
    private static SelectAndCountModelField protectMarathonList;
    private static ChoiceModelField protectNewAncientTreeType;
    private static SelectAndCountModelField protectNewAncientTreeList;
    private static BooleanModelField protectTree;
    private static SelectAndCountModelField protectTreeList;
    private static BooleanModelField protectReserve;
    private static SelectAndCountModelField protectReserveList;
    private static BooleanModelField protectReserveMinNum;
    private IntegerModelField protectReserveNum;
    private static BooleanModelField protectBeachMinNum;
    private IntegerModelField protectBeachNum;
    private static BooleanModelField protectBeach;
    private static SelectAndCountModelField protectBeachList;
    private static BooleanModelField protectAnimal;
    private static SelectModelField protectAnimalList;
    
    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(cooperateWater = new BooleanModelField("cooperateWater", "合种 | 浇水", false));
        modelFields.addField(cooperateWaterList = new SelectAndCountModelField("cooperateWaterList", "合种 | 日浇水量列表", new LinkedHashMap<>(), CooperateUser::getList, "请填写浇水克数(每日)"));
        modelFields.addField(cooperateWaterTotalLimitList = new SelectAndCountModelField("cooperateWaterTotalLimitList", "合种 | 总浇水量列表", new LinkedHashMap<>(), CooperateUser::getList, "请填写浇水克数(上限总量)"));
        modelFields.addField(protectMarathonType = new ChoiceModelField("protectMarathonType", "碳中和 | 马拉松", ProtectType.NONE, ProtectType.nickNames));
        modelFields.addField(protectMarathonList = new SelectAndCountModelField("protectMarathonList", "碳中和 | 马拉松列表", new LinkedHashMap<>(), AlipayMarathon::getList, "请填写助力能量克数(上限总量)"));
        modelFields.addField(protectNewAncientTreeType = new ChoiceModelField("protectNewAncientTreeType", "碳中和 | " + "古树医生", ProtectType.NONE, ProtectType.nickNames));
        modelFields.addField(protectNewAncientTreeList = new SelectAndCountModelField("protectNewAncientTreeList", "碳中和 | 古树医生列表", new LinkedHashMap<>(), AlipayNewAncientTree::getList, "请填写助力能量克数(上限总量)"));
        modelFields.addField(protectTree = new BooleanModelField("protectTree", "保护森林 | 植树", false));
        modelFields.addField(protectTreeList = new SelectAndCountModelField("protectTreeList", "保护森林 | 植树列表", new LinkedHashMap<>(), AlipayTree::getList, "请填写保护次数(上限总量)"));
        modelFields.addField(protectReserve = new BooleanModelField("protectReserve", "保护动物 | 保护地", false));
        modelFields.addField(protectReserveList = new SelectAndCountModelField("reserveList", "保护动物 | 保护地列表", new LinkedHashMap<>(), AlipayReserve::getList, "请填写保护次数(每日)"));
        modelFields.addField(protectReserveMinNum = new BooleanModelField("protectReserveMinNum", "保护地 | 最少保护", false));
        modelFields.addField(protectReserveNum = new IntegerModelField("protectReserveNum", "保护地 |最少保护下限", 1));
        modelFields.addField(protectAnimal = new BooleanModelField("protectAnimal", "保护动物 | 护林员", false));
        modelFields.addField(protectAnimalList = new SelectModelField("protectAnimalList", "保护动物 | 护林员列表", new HashSet<>(), AlipayAnimal::getList, "请选择需要点亮的护林员"));
        modelFields.addField(protectBeachMinNum = new BooleanModelField("protectBeachMinNum", "保护海洋 | 单个海滩保护", false));
        modelFields.addField(protectBeachNum = new IntegerModelField("protectBeachNum", "保护海洋 |海滩保护下限", 1));
        modelFields.addField(protectBeach = new BooleanModelField("protectBeach", "保护海洋 | 海滩", false));
        modelFields.addField(protectBeachList = new SelectAndCountModelField("protectOceanList", "保护海洋 | 海滩列表", new LinkedHashMap<>(), AlipayBeach::getList, "请填写保护次数(上限总量)"));
        return modelFields;
    }
    
    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.forest("任务暂停⏸️生态保护:当前为仅收能量时间");
            return false;
        }
        return true;
    }
    
    @Override
    public void run() {
        if (cooperateWater.getValue()) {
            cooperateWater();
        }
        if (protectMarathonType.getValue() != ProtectType.NONE || protectNewAncientTreeType.getValue() != ProtectType.NONE) {
            protectCarbon();
        }
        if (protectTree.getValue()) {
            protectTree();
        }
        if (protectReserve.getValue()) {
            protectReserve();
        }

        if (protectReserveMinNum.getValue()) {
            protectReserveMinNum(protectReserveNum.getValue());
        }

        if (protectAnimal.getValue()) {
            protectAnimal();
        }
        
        if (protectBeachMinNum.getValue()) {
            protectBeachMinNum(protectBeachNum.getValue());
        }
        
        if (protectBeach.getValue()) {
            protectBeach();
        }
    }
    
    public static void initForest() {
        try {
            JSONArray treeItems = queryTreeItemsForExchange("AVAILABLE", "project");
            if (treeItems == null) {
                return;
            }
            ReserveIdMap.load();
            for (int i = 0; i < treeItems.length(); i++) {
                JSONObject jo = treeItems.getJSONObject(i);
                String itemId = jo.getString("itemId");
                String itemName = jo.getString("itemName");
                if (Objects.equals("TREE", jo.getString("projectType"))) {
                    String organization = jo.getString("organization");
                    String region = jo.getString("region");
                    itemName = itemName + "[" + region + "|" + organization + "]";
                    TreeIdMap.add(itemId, itemName + "(" + jo.getInt("energy") + "g)");
                }
                else if (Objects.equals("RESERVE", jo.getString("projectType"))) {
                    ReserveIdMap.add(itemId, itemName + "(" + jo.getInt("energy") + "g)");
                }
                else if (Objects.equals("ANIMAL", jo.getString("projectType"))) {
                    AnimalIdMap.add(itemId, itemName + "(" + jo.getInt("energy") + "g)");
                }
            }
            TreeIdMap.save();
            ReserveIdMap.save();
            AnimalIdMap.save();
            
        }
        catch (Throwable t) {
            Log.i(TAG, "initForest err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    public static void initOcean() {
        try {
            JSONArray cultivationList = queryCultivationList();
            if (cultivationList == null) {
                return;
            }
            BeachIdMap.load();
            for (int i = 0; i < cultivationList.length(); i++) {
                JSONObject jo = cultivationList.getJSONObject(i);
                if (!Objects.equals("AVAILABLE", jo.getString("applyAction"))) {
                    continue;
                }
                if (Objects.equals("BEACH", jo.optString("templateSubType")) || Objects.equals("COOPERATE_PLANT", jo.getString("templateType")) || Objects.equals("PROTECT", jo.getString("templateType"))) {
                    BeachIdMap.add(jo.getString("templateCode"), jo.getString("cultivationName") + "(" + jo.getInt("energy") + "g)");
                }
            }
            BeachIdMap.save();
        }
        catch (Throwable t) {
            Log.i(TAG, "initOcean err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void cooperateWater() {
        try {
            JSONObject jo = new JSONObject(CooperateRpcCall.queryUserCooperatePlantList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            String userId = UserIdMap.getCurrentUid();
            JSONArray cooperatePlants = jo.getJSONArray("cooperatePlants");
            for (int i = 0; i < cooperatePlants.length(); i++) {
                jo = cooperatePlants.getJSONObject(i);
                String cooperationId = jo.getString("cooperationId");
                queryCooperatePlant(userId, cooperationId);
            }
            CooperationIdMap.save(userId);
        }
        catch (Throwable t) {
            Log.i(TAG, "cooperateWater err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void queryCooperatePlant(String userId, String cooperationId) {
        try {
            JSONObject jo = new JSONObject(CooperateRpcCall.queryCooperatePlant(cooperationId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            int userCurrentEnergy = jo.getInt("userCurrentEnergy");
            jo = jo.getJSONObject("cooperatePlant");
            String name = jo.getString("name");
            CooperationIdMap.add(cooperationId, name);
            int waterDayLimit = jo.getInt("waterDayLimit");
            int energyCount = getEnergyCount(userId, cooperationId, waterDayLimit);
            if (energyCount > 0 && energyCount <= userCurrentEnergy) {
                if (cooperateWater(userId, cooperationId, energyCount, name)) {
                    TimeUtil.sleep(300);
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryCooperatePlant err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static Boolean cooperateWater(String userId, String cooperationId, int energyCount, String name) {
        try {
            JSONObject jo = new JSONObject(CooperateRpcCall.cooperateWater(userId, cooperationId, energyCount));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.forest("合种浇水🚿[" + name + "]#" + jo.getString("barrageText"));
                Toast.show("合种浇水🚿[" + name + "]#" + jo.getString("barrageText"));
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "cooperateWater err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private static int getEnergyCount(String userId, String cooperationId, int waterDayLimit) {
        Integer waterNum = cooperateWaterList.getValue().get(cooperationId);
        if (waterNum == null) {
            return 0;
        }
        int dayWater = getEnergySummation("D", cooperationId, userId);
        int allWater = getEnergySummation("A", cooperationId, userId);
        int energyCount = Math.min(waterNum - dayWater, waterDayLimit);
        Integer limitNum = cooperateWaterTotalLimitList.getValue().get(cooperationId);
        if (limitNum != null) {
            energyCount = Math.min(waterNum, limitNum - allWater);
        }
        return energyCount < 10 ? 0 : energyCount;
    }
    
    private static int getEnergySummation(String bizType, String cooperationId, String userId) {
        try {
            JSONObject jo = new JSONObject(CooperateRpcCall.queryCooperateRank(bizType, cooperationId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONArray cooperateRankInfos = jo.getJSONArray("cooperateRankInfos");
                for (int i = 0; i < cooperateRankInfos.length(); i++) {
                    jo = cooperateRankInfos.getJSONObject(i);
                    if (Objects.equals(userId, jo.getString("userId"))) {
                        return jo.optInt("energySummation");
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "getEnergySummation err:");
            Log.printStackTrace(TAG, t);
        }
        return 0;
    }
    
    private static void protectTree() {
        Map<String, Integer> map = protectTreeList.getValue();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            Integer count = entry.getValue();
            if (count == null || count < 0) {
                continue;
            }
            int projectId = Integer.parseInt(entry.getKey());
            ExchangeableTree exchangeableTree = queryTreeForExchange(projectId);
            while (exchangeableTree.canExchange && exchangeableTree.certCount < count) {
                String projectName = exchangeableTree.projectName;
                int exchangeCount = exchangeableTree.certCount + 1;
                Log.forest("生态保护🏕️申请[" + projectName + "]#第" + exchangeCount + "次");
                if (!exchangeTree(projectId, projectName)) {
                    break;
                }
                TimeUtil.sleep(300);
                exchangeableTree = queryTreeForExchange(projectId);
            }
        }
    }
    
    private static void protectReserve() {
        Map<String, Integer> map = protectReserveList.getValue();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            Integer count = entry.getValue();
            if (count == null || count < 0) {
                continue;
            }
            int projectId = Integer.parseInt(entry.getKey());
            while (Status.canExchangeReserveToday(projectId, count)) {
                ExchangeableTree exchangeableTree = queryTreeForExchange(projectId);
                if (!exchangeableTree.canExchange) {
                    break;
                }
                String projectName = exchangeableTree.projectName;
                int exchangeCount = Status.getExchangeReserveCountToday(projectId) + 1;
                Log.forest("生态保护🏕️申请[" + projectName + "]#第" + exchangeCount + "次");
                if (!exchangeTree(projectId, projectName)) {
                    break;
                }
                Status.exchangeReserveToday(projectId);
                TimeUtil.sleep(300);
            }
        }
    }
    
    private static void protectAnimal() {
        Set<String> set = protectAnimalList.getValue();
        for (String s : set) {
            int projectId = Integer.parseInt(s);
            ExchangeableTree exchangeableTree = queryTreeForExchange(projectId);
            while (exchangeableTree.canExchange) {
                String projectName = exchangeableTree.projectName;
                if (!exchangeTree(projectId, projectName)) {
                    break;
                }
                exchangeableTree = queryTreeForExchange(projectId);
                TimeUtil.sleep(300);
            }
        }
    }
    
    public static JSONArray queryTreeItemsForExchange(String applyActions, String itemTypes) {
        try {
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.queryTreeItemsForExchange(applyActions, itemTypes));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                return jo.getJSONArray("treeItems");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryTreeItemsForExchange err:");
            Log.printStackTrace(TAG, t);
        }
        return null;
    }
    
    private static ExchangeableTree queryTreeForExchange(int projectId) {
        ExchangeableTree exchangeableTree = new ExchangeableTree(projectId);
        try {
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.queryTreeForExchange(projectId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return exchangeableTree;
            }
            String applyAction = jo.getString("applyAction");
            int currentEnergy = jo.getInt("currentEnergy");
            JSONArray subTreeVOs = jo.getJSONArray("subTreeVOs");
            jo = jo.getJSONObject("exchangeableTree");
            exchangeableTree.certCount = jo.getInt("certCount");
            exchangeableTree.projectName = jo.getString("projectName");
            if (!Objects.equals("AVAILABLE", applyAction)) {
                Log.record("生态保护🏕️保护[" + exchangeableTree.projectName + "]停止:数量不足");
                return exchangeableTree;
            }
            if (currentEnergy < jo.getInt("energy")) {
                Log.record("生态保护🏕️保护[" + exchangeableTree.projectName + "]停止:能量不足");
                return exchangeableTree;
            }
            if (Objects.equals("ANIMAL", jo.getString("type"))) {
                if (exchangeableTree.certCount == 0) {
                    for (int i = 0; i < subTreeVOs.length(); i++) {
                        jo = subTreeVOs.getJSONObject(i);
                        int certCountForAlias = jo.getInt("certCountForAlias");
                        if (certCountForAlias == 0) {
                            exchangeableTree.canExchange = true;
                            break;
                        }
                    }
                    if (!exchangeableTree.canExchange) {
                        applyGoldAnimalCert(projectId);
                    }
                }
            }
            else {
                exchangeableTree.canExchange = true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryTreeForExchange err:");
            Log.printStackTrace(TAG, t);
        }
        return exchangeableTree;
    }
    
    private static Boolean exchangeTree(int projectId, String projectName) {
        try {
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.exchangeTree(projectId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            int vitalityAmount = jo.optInt("vitalityAmount", 0);
            String str = "";
            if (vitalityAmount > 0) {
                str = "#获得[" + vitalityAmount + "活力值]";
            }
            jo = jo.getJSONObject("userCertificate");
            if (Objects.equals("ANIMAL", jo.getString("type"))) {
                str = "#获得[" + jo.getString("projectName") + "]";
            }
            Log.forest("生态保护🏕️保护[" + projectName + "]" + str);
            return true;
        }
        catch (Throwable t) {
            Log.i(TAG, "exchangeTree err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private static void applyGoldAnimalCert(int projectId) {
        try {
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.applyGoldAnimalCert(projectId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("goldAnimalCertVO");
            Log.record("生态保护🏕️点亮[" + jo.getString("name") + "]");
        }
        catch (Throwable t) {
            Log.i(TAG, "applyGoldAnimalCert err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void protectCarbon() {
        try {
            JSONArray treeItems = queryTreeItemsForExchange("AVAILABLE", "special");
            if (treeItems == null) {
                return;
            }
            for (int i = 0; i < treeItems.length(); i++) {
                JSONObject jo = treeItems.getJSONObject(i);
                jo = jo.getJSONObject("extendInfo");
                String activityName = jo.optString("activityName");
                if (Objects.equals("marathon", jo.optString("activityType"))) {
                    String activityId = StringUtil.getSubString(jo.getString("actionUrl"), "activityId%3D", "%26");
                    MarathonIdMap.add(activityId, activityName);
                    if (protectMarathonType.getValue() != ProtectType.NONE) {
                        marathonQueryActivity(activityId);
                    }
                }
                else if (activityName.contains("古树医生")) {
                    String activityId = StringUtil.getSubString(jo.getString("actionUrl"), "activityId%3D", "%26");
                    NewAncientTreeIdMap.add(activityId, activityName);
                    if (protectNewAncientTreeType.getValue() != ProtectType.NONE) {
                        carbonQueryActivity(activityId);
                    }
                }
            }
            MarathonIdMap.save();
            NewAncientTreeIdMap.save();
        }
        catch (Throwable t) {
            Log.i(TAG, "protectCarbon err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void marathonQueryActivity(String activityId) {
        try {
            JSONObject paramMap = new JSONObject();
            paramMap.put("donateQueryActionParam", "marathonWater");
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.doRubickActivity("marathonHome", activityId, paramMap));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("resultData");
            int currentEnergy = jo.getInt("currentEnergy");
            // 如果未曾助力:助力一次
            Integer donateNumber = protectMarathonList.getValue().get(activityId);
            if (!jo.optBoolean("certLockStatus", true)) {
                int donateNum = jo.getJSONObject("donateConfigVO").getInt("donateNum");
                if (protectMarathonType.getValue() == ProtectType.SELECT) {
                    if (donateNumber == null || donateNumber < donateNum) {
                        return;
                    }
                }
                if (currentEnergy >= donateNum && carbonCharityActivity("marathonWater", activityId, donateNum)) {
                    currentEnergy -= donateNum;
                }
            }
            if (protectMarathonType.getValue() == ProtectType.COLLECT) {
                // 集邮模式:不再助力
                return;
            }
            int energy = jo.getJSONObject("activityCertVO").getInt("energy");
            donateNumber = donateNumber == null ? 0 : donateNumber - energy;
            int secondDonateMinNum = jo.getJSONObject("donateConfigVO").getInt("secondDonateMinNum");
            if (donateNumber >= secondDonateMinNum && currentEnergy >= donateNumber) {
                carbonCharityActivity("marathonWater", activityId, donateNumber);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "marathonQueryActivity err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void carbonQueryActivity(String activityId) {
        try {
            JSONObject paramMap = new JSONObject();
            paramMap.put("donateQueryActionParam", "carbonWater");
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.doRubickActivity("carbonHome", activityId, paramMap));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("resultData");
            int currentEnergy = jo.getInt("currentEnergy");
            // 如果未曾助力:助力一次
            Integer donateNumber = protectNewAncientTreeList.getValue().get(activityId);
            if (!jo.optBoolean("certLockStatus", true)) {
                int donateNum = jo.getJSONObject("donateConfigVO").getInt("donateNum");
                if (protectNewAncientTreeType.getValue() == ProtectType.SELECT) {
                    if (donateNumber == null || donateNumber < donateNum) {
                        return;
                    }
                }
                if (currentEnergy >= donateNum && carbonCharityActivity("carbonWater", activityId, donateNum)) {
                    currentEnergy -= donateNum;
                }
            }
            if (protectNewAncientTreeType.getValue() == ProtectType.COLLECT) {
                // 集邮模式:不再助力
                return;
            }
            int energy = jo.getJSONObject("activityCertVO").getInt("energy");
            donateNumber = donateNumber == null ? 0 : donateNumber - energy;
            int secondDonateMinNum = jo.getJSONObject("donateConfigVO").getInt("secondDonateMinNum");
            if (donateNumber >= secondDonateMinNum && currentEnergy >= donateNumber) {
                carbonCharityActivity("carbonWater", activityId, donateNumber);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "carbonQueryActivity err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static Boolean carbonCharityActivity(String actionCode, String activityId, int donateNum) {
        try {
            JSONObject paramMap = new JSONObject();
            paramMap.put("donateNum", donateNum);
            paramMap.put("incrNum", donateNum);
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.doRubickActivity(actionCode, activityId, paramMap));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            jo = jo.getJSONObject("resultData").getJSONObject("activityCertVO");
            String name = jo.getString("name");
            int energy = jo.getInt("energy");
            Log.forest("生态保护🏕️助力[" + name + "]#累计[" + energy + "g能量]");
            return true;
        }
        catch (Throwable t) {
            Log.i(TAG, "carbonCharityActivity err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private static JSONArray queryCultivationList() {
        try {
            JSONObject jo = new JSONObject(ProtectOceanRpcCall.queryCultivationList());
            if (MessageUtil.checkResultCode(TAG, jo)) {
                return jo.getJSONArray("cultivationItemVOList");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryCultivationList err:");
            Log.printStackTrace(TAG, t);
        }
        return null;
    }
    private static void protectReserveMinNum(int protectReserveNum) {
        if (protectReserveNum > 0) {
            try {
                JSONArray treeItems = queryTreeItemsForExchange("AVAILABLE", "project");
                if (treeItems == null) {
                    return;
                }
                for (int i = 0; i < treeItems.length(); i++) {
                    JSONObject jo = treeItems.getJSONObject(i);
                    String itemId = jo.getString("itemId");
                    String itemName = jo.getString("itemName");
                    int certCountForAlias=jo.optInt("certCountForAlias");
                    int projectId=jo.optInt("projectId");
                    if (Objects.equals("RESERVE", jo.getString("projectType"))) {

                        if(certCountForAlias>=protectReserveNum)
                        {continue;}
                        while (certCountForAlias<protectReserveNum) {
                            ExchangeableTree exchangeableTree = queryTreeForExchange(projectId);
                            if (!exchangeableTree.canExchange) {
                                break;
                            }
                            String projectName = exchangeableTree.projectName;
                            certCountForAlias = certCountForAlias + 1;
                            Log.forest("生态保护🏕️申请[" + projectName + "]#第" + certCountForAlias + "次");
                            if (!exchangeTree(projectId, projectName)) {
                                break;
                            }
                            Status.exchangeReserveToday(projectId);
                            TimeUtil.sleep(300);
                        }
                    }
                }
            }
            catch (Throwable t) {
                Log.i(TAG, "protectReserveMinNum err:");
                Log.printStackTrace(TAG, t);
            }
        }
    }



    private static void protectBeachMinNum(int protectBeachNum) {
        if (protectBeachNum > 0) {
            try {
                JSONArray cultivationList = queryCultivationList();
                if (cultivationList == null) {
                    return;
                }
                for (int i = 0; i < cultivationList.length(); i++) {
                    JSONObject jo = cultivationList.getJSONObject(i);
                    if (!Objects.equals("AVAILABLE", jo.getString("applyAction"))) {
                        continue;
                    }
                    String cultivationCode = jo.getString("cultivationCode");
                    String projectCode = jo.getJSONObject("projectConfigVO").getString("code");
                    int certNum = jo.getInt("certNum");
                    int energy = jo.optInt("energy", 0);
                    if (energy > 1000) {
                        continue;
                    }
                    while (protectBeachNum > certNum && queryCultivationDetail(cultivationCode, projectCode)) {
                        certNum++;
                        TimeUtil.sleep(300);
                    }
                }
            }
            catch (Throwable t) {
                Log.i(TAG, "protectBeachMinNum err:");
                Log.printStackTrace(TAG, t);
            }
        }
        ;
    }
    
    private static void protectBeach() {
        Map<String, Integer> map = protectBeachList.getValue();
        try {
            JSONArray cultivationList = queryCultivationList();
            if (cultivationList == null) {
                return;
            }
            for (int i = 0; i < cultivationList.length(); i++) {
                JSONObject jo = cultivationList.getJSONObject(i);
                if (!Objects.equals("AVAILABLE", jo.getString("applyAction"))) {
                    continue;
                }
                String cultivationCode = jo.getString("cultivationCode");
                String projectCode = jo.getJSONObject("projectConfigVO").getString("code");
                int certNum = jo.getInt("certNum");
                Integer count = map.get(cultivationCode);
                if (count == null) {
                    continue;
                }
                while (count > certNum && queryCultivationDetail(cultivationCode, projectCode)) {
                    certNum++;
                    TimeUtil.sleep(300);
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "protectBeach err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static Boolean queryCultivationDetail(String cultivationCode, String projectCode) {
        try {
            JSONObject jo = new JSONObject(ProtectOceanRpcCall.queryCultivationDetail(cultivationCode, projectCode));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            int currentEnergy = jo.getJSONObject("userInfoVO").getInt("currentEnergy");
            jo = jo.getJSONObject("cultivationDetailVO");
            String cultivationName = jo.getString("cultivationName");
            if (!Objects.equals("AVAILABLE", jo.getString("applyAction"))) {
                Log.record("保护海洋🏖️保护[" + cultivationName + "]停止:数量不足");
                return false;
            }
            if (currentEnergy < jo.getInt("energy")) {
                Log.record("保护海洋🏖️保护[" + cultivationName + "]停止:能量不足");
                return false;
            }
            int count = jo.getInt("certNum") + 1;
            Log.forest("保护海洋🏖️申请[" + cultivationName + "]#第" + count + "次");
            return oceanExchangeTree(cultivationCode, projectCode, cultivationName);
        }
        catch (Throwable t) {
            Log.i(TAG, "queryCultivationDetail err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private static Boolean oceanExchangeTree(String cultivationCode, String projectCode, String cultivationName) {
        try {
            JSONObject jo = new JSONObject(ProtectOceanRpcCall.oceanExchangeTree(cultivationCode, projectCode));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray awardInfos = jo.getJSONArray("rewardItemVOs");
            StringBuilder award = new StringBuilder();
            for (int i = 0; i < awardInfos.length(); i++) {
                jo = awardInfos.getJSONObject(i);
                if (i > 0) {
                    award.append(";");
                }
                award.append(jo.getString("name")).append("*").append(jo.getInt("num"));
            }
            Log.forest("保护海洋🏖️保护[" + cultivationName + "]#获得[" + award + "]");
            return true;
        }
        catch (Throwable t) {
            Log.i(TAG, "oceanExchangeTree err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    public static class ExchangeableTree {
        boolean canExchange;
        int projectId;
        String projectName;
        int certCount;
        
        ExchangeableTree(int projectId) {
            canExchange = false;
            this.projectId = projectId;
        }
    }
    
    public interface ProtectType {
        int NONE = 0;
        int COLLECT = 1;
        int SELECT = 2;
        
        String[] nickNames = {"不保护", "集邮模式", "列表模式"};
    }
}
