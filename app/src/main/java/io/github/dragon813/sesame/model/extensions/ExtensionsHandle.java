package io.github.dragon813.sesame.model.extensions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Objects;

import io.github.dragon813.sesame.data.TokenConfig;
import io.github.dragon813.sesame.hook.Toast;
import io.github.dragon813.sesame.model.task.antSports.AntSportsRpcCall;
import io.github.dragon813.sesame.model.task.protectEcology.ProtectTreeRpcCall;
import io.github.dragon813.sesame.util.Log;
import io.github.dragon813.sesame.util.MessageUtil;
import io.github.dragon813.sesame.util.Status;
import io.github.dragon813.sesame.util.StringUtil;
import io.github.dragon813.sesame.util.TimeUtil;
import io.github.dragon813.sesame.util.idMap.WalkPathIdMap;

public class ExtensionsHandle {
    private static final String TAG = ExtensionsHandle.class.getSimpleName();

    public static void handleRequest(String type, String fun, Object data) {
        if (handleAlphaRequest(type, fun, data) != null) {
            return;
        }
        switch (type) {
            case "antForest":
                if (Objects.equals("getWateredItems", fun)) {
                    getWateredItems();
                }else if (Objects.equals("getWateringItems", fun)) {
                    getWateringItems();
                }else if (Objects.equals("getTreeItems", fun)) {
                    getTreeItems();
                } else if (Objects.equals("getNewTreeItems", fun)) {
                    getNewTreeItems();
                } else if (Objects.equals("queryAreaTrees", fun)) {
                    queryAreaTrees();
                } else if (Objects.equals("getUnlockTreeItems", fun)) {
                    getUnlockTreeItems();
                }
                break;
            case "setCustomWalkPathIdList":
                addCustomWalkPathIdList((String) data);
                break;
            case "setCustomWalkPathIdQueue":
                if (Objects.equals("addCustomWalkPathIdQueue", fun)) {
                    addCustomWalkPathIdQueue((String) data);
                } else if (Objects.equals("clearCustomWalkPathIdQueue", fun)) {
                    clearCustomWalkPathIdQueue();
                }
                break;
        }
    }

    public static Object handleAlphaRequest(String type, String fun, Object data) {
        try {
            return Class.forName("io.github.dragon813.sesame.model.extensions.ExtensionsHandleAlpha")
                    .getMethod("handleAlphaRequest", String.class, String.class, Object.class)
                    .invoke(null, type, fun, data);
        } catch (Exception e) {
            return null;
        }
    }
    private static void getWateredItems() {
        Status.getWateredFriendToday();
    }
    
    private static void getWateringItems() {
        Status.getWateringFriendToday();
    }
    
    private static void getNewTreeItems() {
        try {
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.queryTreeItemsForExchange("COMING", "project"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray ja = jo.getJSONArray("treeItems");
            if (ja.length() == 0) {
                Log.forest("新树上苗🌱[当前没有新树上苗信息!]");
                return;
            }
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                if (!jo.has("projectType"))
                    continue;
                if (!"TREE".equals(jo.getString("projectType")))
                    continue;
                if (!"COMING".equals(jo.getString("applyAction")))
                    continue;
                String projectId = jo.getString("itemId");
                queryTreeForExchange(projectId);
            }
        } catch (Throwable t) {
            Log.i(TAG, "getTreeItems err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void queryTreeForExchange(String projectId) {
        try {
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.queryTreeForExchange(projectId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject exchangeableTree = jo.getJSONObject("exchangeableTree");
            int currentBudget = exchangeableTree.getInt("currentBudget");
            String region = exchangeableTree.getString("region");
            String treeName = exchangeableTree.getString("treeName");
            String tips = "不可合种";
            if (exchangeableTree.optBoolean("canCoexchange", false)) {
                tips = "可以合种-合种类型："
                        + exchangeableTree.getJSONObject("extendInfo").getString("cooperate_template_id_list");
            }
            Log.forest("新树上苗🌱[" + region + "-" + treeName + "]#" + currentBudget + "株-" + tips);
        } catch (Throwable t) {
            Log.i(TAG, "queryTreeForExchange err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void getTreeItems() {
        try {
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.queryTreeItemsForExchange("AVAILABLE,ENERGY_LACK", "project"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray ja = jo.getJSONArray("treeItems");
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                if (!jo.has("projectType"))
                    continue;
                String projectId = jo.getString("itemId");
                String itemName = jo.getString("itemName");
                getTreeCurrentBudget(projectId, itemName);
                TimeUtil.sleep(100);
            }
        } catch (Throwable t) {
            Log.i(TAG, "getTreeItems err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void getTreeCurrentBudget(String projectId, String treeName) {
        try {
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.queryTreeForExchange(projectId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONObject exchangeableTree = jo.getJSONObject("exchangeableTree");
                int currentBudget = exchangeableTree.getInt("currentBudget");
                String region = exchangeableTree.getString("region");
                Log.forest("树苗查询🌱[" + region + "-" + treeName + "]#剩余:" + currentBudget);
            }
        } catch (Throwable t) {
            Log.i(TAG, "queryTreeForExchange err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void queryAreaTrees() {
        try {
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.queryAreaTrees());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject areaTrees = jo.getJSONObject("areaTrees");
            JSONObject regionConfig = jo.getJSONObject("regionConfig");
            Iterator<String> regionKeys = regionConfig.keys();
            while (regionKeys.hasNext()) {
                String regionKey = regionKeys.next();
                if (!areaTrees.has(regionKey)) {
                    JSONObject region = regionConfig.getJSONObject(regionKey);
                    String regionName = region.optString("regionName");
                    Log.forest("未解锁地区🗺️[" + regionName + "]");
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "queryAreaTrees err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void getUnlockTreeItems() {
        try {
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.queryTreeItemsForExchange("", "project"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray ja = jo.getJSONArray("treeItems");
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                if (!jo.has("projectType"))
                    continue;
                int certCountForAlias = jo.optInt("certCountForAlias", -1);
                if (certCountForAlias == 0) {
                    String itemName = jo.optString("itemName");
                    String region = jo.optString("region");
                    String organization = jo.optString("organization");
                    Log.forest("未解锁项目🐘[" + region + "-" + itemName + "]#" + organization);
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "getUnlockTreeItems err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void addCustomWalkPathIdList(String pathId) {
        if (!StringUtil.isEmpty(pathId)) {
            String pathName = AntSportsRpcCall.queryPathName(pathId);
            if (pathName == null) {
                Toast.show("添加自定义路线列表失败:找不到路线信息");
                return;
            }
            WalkPathIdMap.load();
            WalkPathIdMap.add(pathId, pathName);
            WalkPathIdMap.save();
            Toast.show("添加自定义路线列表成功:" + pathName);
        }
    }

    private static void addCustomWalkPathIdQueue(String pathId) {
        if (!StringUtil.isEmpty(pathId)) {
            String pathName = AntSportsRpcCall.queryPathName(pathId);
            if (pathName == null) {
                Toast.show("添加待行走路线队列失败:找不到路线信息");
                return;
            }
            if (TokenConfig.addCustomWalkPathIdQueue(pathId)) {
                Toast.show("添加待行走路线队列成功:" + pathName);
            }
        }
    }

    private static void clearCustomWalkPathIdQueue() {
        if (TokenConfig.clearCustomWalkPathIdQueue()) {
            Toast.show("清除待行走路线队列成功");
        }
    }
}
