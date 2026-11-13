package io.github.lazyimmortal.sesame.model.task.antFarm;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectModelField;
import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.model.base.TaskAlternative;
import io.github.lazyimmortal.sesame.model.normal.base.BaseModel;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.RandomUtil;
import io.github.lazyimmortal.sesame.util.Status;
import io.github.lazyimmortal.sesame.util.TaskIconUtil;
import io.github.lazyimmortal.sesame.util.TimeUtil;
import io.github.lazyimmortal.sesame.util.idMap.AntFarmChickenChefTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.MiniGameTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

/**
 * 小鸡厨房 & 小鸡大厨(AI厨房) 业务实现
 * 从 {@link AntFarm} 中剥离的方法实现类，配置逻辑仍由 AntFarm 负责
 */
public class ChickenKitchen {

    private static final String TAG = AntFarm.class.getSimpleName();

    /* ===== 小鸡大厨(AI厨房) 常量 ===== */

    /** 每次烹饪消耗的食材数量 */
    private static final int AI_KITCHEN_INGREDIENT_COUNT = 3;
    /** 防御上限：正常由 canCook 控制，避免服务端异常时死循环 */
    private static final int AI_KITCHEN_MAX_COOK_TIMES = 20;
    /** 精通优先探索新组合时，手帐已有食材每种保留的份数，超出部分才用于探索 */
    private static final int AI_KITCHEN_EXPLORATION_RESERVE = 3;
    /** 各账号风味手帐中的菜品（配方 + 最高分），供精通/丰富策略选材 */
    private static final Map<String, List<AiKitchenDish>> AI_KITCHEN_DISH_CACHE = new HashMap<>();

    public interface ChickenChefCookStrategy {
        int MASTERY = 0;
        int RICHNESS = 1;
        int RANDOM = 2;
        String[] nickNames = {"精通优先", "丰富优先", "随机制作"};
    }

    /** 风味手帐中的一道菜 */
    private static final class AiKitchenDish {
        private final List<String> ingredientIds;
        private int highestScore;

        private AiKitchenDish(List<String> ingredientIds, int highestScore) {
            this.ingredientIds = ingredientIds;
            this.highestScore = highestScore;
        }
    }

    /* ===== 小鸡厨房基础功能 ===== */

    public static void collectDailyFoodMaterial(String userId) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.enterKitchen(userId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            boolean canCollectDailyFoodMaterial = jo.getBoolean("canCollectDailyFoodMaterial");
            int dailyFoodMaterialAmount = jo.getInt("dailyFoodMaterialAmount");
            int garbageAmount = jo.optInt("garbageAmount", 0);
            if (jo.has("orchardFoodMaterialStatus")) {
                JSONObject orchardFoodMaterialStatus = jo.getJSONObject("orchardFoodMaterialStatus");
                if ("FINISHED".equals(orchardFoodMaterialStatus.optString("foodStatus"))) {
                    jo = new JSONObject(AntFarmRpcCall.farmFoodMaterialCollect());
                    if ("100".equals(jo.getString("resultCode"))) {
                        Log.farm("小鸡厨房👨🏻‍🍳农场食材#领取[" + jo.getInt("foodMaterialAddCount") + "g食材]");
                    } else {
                        Log.i(TAG, jo.toString());
                    }
                }
            }
            if (canCollectDailyFoodMaterial) {
                jo = new JSONObject(AntFarmRpcCall.collectDailyFoodMaterial(dailyFoodMaterialAmount));
                if (MessageUtil.checkMemo(TAG, jo)) {
                    Log.farm("小鸡厨房👨🏻‍🍳今日食材#领取[" + dailyFoodMaterialAmount + "g食材]");
                }
            }
            if (garbageAmount > 0) {
                jo = new JSONObject(AntFarmRpcCall.collectKitchenGarbage());
                if (MessageUtil.checkMemo(TAG, jo)) {
                    Log.farm("小鸡厨房👨🏻‍🍳收集厨余#获得[" + jo.getInt("recievedKitchenGarbageAmount") + "g肥料]");
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "collectDailyFoodMaterial err:");
            Log.printStackTrace(TAG, t);
        }
    }

    public static void collectDailyLimitedFoodMaterial() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.queryFoodMaterialPack());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            boolean canCollectDailyLimitedFoodMaterial = jo.getBoolean("canCollectDailyLimitedFoodMaterial");
            if (canCollectDailyLimitedFoodMaterial) {
                int dailyLimitedFoodMaterialAmount = jo.getInt("dailyLimitedFoodMaterialAmount");
                jo = new JSONObject(AntFarmRpcCall.collectDailyLimitedFoodMaterial(dailyLimitedFoodMaterialAmount));
                if (MessageUtil.checkMemo(TAG, jo)) {
                    Log.farm("小鸡厨房👨🏻‍🍳领取[爱心食材店食材]#" + dailyLimitedFoodMaterialAmount + "g");
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "collectDailyLimitedFoodMaterial err:");
            Log.printStackTrace(TAG, t);
        }
    }

    public static void cook(String userId) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.enterKitchen(userId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            int cookTimesAllowed = jo.getInt("cookTimesAllowed");
            if (cookTimesAllowed > 0) {
                for (int i = 0; i < cookTimesAllowed; i++) {
                    jo = new JSONObject(AntFarmRpcCall.cook(userId));
                    if (MessageUtil.checkMemo(TAG, jo)) {
                        JSONObject cuisineVO = jo.getJSONObject("cuisineVO");
                        Log.farm("小鸡厨房👨🏻‍🍳制作[" + cuisineVO.getString("name") + "]");
                    }
                    TimeUtil.sleep(RandomUtil.delay());
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "cook err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static List<JSONObject> getSortedCuisineList(JSONArray cuisineList) {
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < cuisineList.length(); i++) {
            list.add(cuisineList.optJSONObject(i));
        }
        Collections.sort(list, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject jsonObject1, JSONObject jsonObject2) {
                int count1 = jsonObject1.optInt("count");
                int count2 = jsonObject2.optInt("count");
                return count2 - count1;
            }
        });
        return list;
    }

    public static void useFarmFood(JSONArray cuisineList) {
        try {
            List<JSONObject> list = getSortedCuisineList(cuisineList);
            for (int i = 0; i < list.size(); i++) {
                if (!useFarmFoodOne(list.get(i))) {
                    return;
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "useFarmFood err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static Boolean useFarmFoodOne(JSONObject cuisine) {
        if (!Status.canUseSpecialFoodToday()) {
            return false;
        }
        try {
            String cookbookId = cuisine.getString("cookbookId");
            String cuisineId = cuisine.getString("cuisineId");
            String name = cuisine.getString("name");
            int count = cuisine.getInt("count");
            for (int j = 0; j < count; j++) {
                JSONObject jo = new JSONObject(AntFarmRpcCall.useFarmFood(cookbookId, cuisineId));
                if (!MessageUtil.checkMemo(TAG, jo)) {
                    return false;
                }
                double deltaProduce = jo.getJSONObject("foodEffect").getDouble("deltaProduce");
                Log.farm("使用美食🍱[" + name + "]#加速" + deltaProduce + "颗爱心鸡蛋");
                Status.useSpecialFoodToday();
                if (!Status.canUseSpecialFoodToday()) {
                    break;
                }
            }
            return true;
        } catch (Throwable t) {
            Log.i(TAG, "useFarmFoodOne err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    /* ===== 小鸡大厨(AI厨房) 业务方法 ===== */

    /**
     * 自动做美食：enterAiKitchen 取食材库存，cookAiKitchen 烹饪
     */
    public static void chickenChefCook(int strategy) {
        try {
            boolean dishLoadTried = false;
            List<AiKitchenDish> dishes = null;
            Set<String> usedCombos = null;
            // 循环到服务端 canCook=false，手动做过的次数会自动跳过
            for (int cooked = 0; cooked < AI_KITCHEN_MAX_COOK_TIMES; cooked++) {
                JSONObject jo = new JSONObject(AntFarmRpcCall.enterAiKitchen());
                if (!MessageUtil.checkMemo(TAG, jo)) {
                    return;
                }
                if (!jo.optBoolean("canCook", false)) {
                    return;
                }
                String seasonId = jo.optString("seasonId", "AIKitchenSeasonOne");
                JSONArray ingredientList = jo.optJSONArray("ingredientList");
                if (ingredientList == null) {
                    return;
                }
                // 首轮加载风味手帐菜品和配方，供精通/丰富策略选材
                if (!dishLoadTried) {
                    dishLoadTried = true;
                    String uid = UserIdMap.getCurrentUid();
                    dishes = AI_KITCHEN_DISH_CACHE.get(uid);
                    if (dishes == null) {
                        dishes = loadAiKitchenDishes(seasonId);
                        if (dishes != null) {
                            AI_KITCHEN_DISH_CACHE.put(uid, dishes);
                        }
                    }
                    if (dishes != null) {
                        usedCombos = buildUsedCombos(dishes);
                    }
                }
                // 首轮输出食材库存，便于判断剩余可烹饪次数
                if (cooked == 0) {
                    int totalStock = 0;
                    StringBuilder stockLog = new StringBuilder();
                    for (int j = 0; j < ingredientList.length(); j++) {
                        JSONObject item = ingredientList.optJSONObject(j);
                        if (item == null || item.optInt("count") <= 0) {
                            continue;
                        }
                        totalStock += item.optInt("count");
                        if (stockLog.length() > 0) {
                            stockLog.append(' ');
                        }
                        stockLog.append(item.optString("ingredientName")).append(item.optInt("count"));
                    }
                    Log.record("小鸡大厨👨🏻‍🍳食材库存[" + stockLog + "]#可烹饪" + (totalStock / AI_KITCHEN_INGREDIENT_COUNT) + "次");
                }
                // 取库存最多的前 3 种食材，不足 3 种不烹饪
                List<JSONObject> stockList = new ArrayList<>();
                for (int j = 0; j < ingredientList.length(); j++) {
                    JSONObject item = ingredientList.optJSONObject(j);
                    if (item != null && item.optInt("count") > 0) {
                        stockList.add(item);
                    }
                }
                if (stockList.size() < AI_KITCHEN_INGREDIENT_COUNT) {
                    Log.record("小鸡大厨👨🏻‍🍳食材不足，跳过做美食");
                    return;
                }
                Collections.sort(stockList, new Comparator<JSONObject>() {
                    @Override
                    public int compare(JSONObject o1, JSONObject o2) {
                        return o2.optInt("count") - o1.optInt("count");
                    }
                });
                List<JSONObject> combo;
                AiKitchenDish target = null;
                if (strategy == ChickenChefCookStrategy.MASTERY) {
                    target = pickMasteryComboDish(dishes, stockList);
                    if (target != null) {
                        combo = buildComboFromDish(target, stockList);
                    } else if (allCombosUsed(stockList, usedCombos)) {
                        combo = chooseFullComboCombo(stockList, dishes);
                        if (combo == null) {
                            Log.record("小鸡大厨👨🏻‍🍳精通已完成，全组合下可消耗食材不足，跳过做美食");
                            return;
                        }
                    } else {
                        combo = chooseExplorationCombo(stockList, dishes, usedCombos);
                        if (combo == null) {
                            Log.record("小鸡大厨👨🏻‍🍳精通已完成，没有可用于探索的食材，跳过做美食");
                            return;
                        }
                    }
                } else if (strategy == ChickenChefCookStrategy.RICHNESS) {
                    combo = chooseNewAiKitchenCombo(stockList, usedCombos);
                    if (combo == null && allCombosUsed(stockList, usedCombos)) {
                        combo = chooseFullComboCombo(stockList, dishes);
                        if (combo == null) {
                            Log.record("小鸡大厨👨🏻‍🍳剩余食材不足，跳过做美食#策略["
                                    + ChickenChefCookStrategy.nickNames[strategy] + "]#原因[全组合下可消耗食材不足3种]");
                            return;
                        }
                    } else if (combo == null) {
                        StringBuilder reason = new StringBuilder("手帐已无可用新组合");
                        if (hasOverstockIngredient(stockList)) {
                            target = pickMasteryComboDish(dishes, stockList);
                            if (target == null) {
                                reason.append("、精通配方食材不足");
                            }
                        } else {
                            reason.append("、无单种食材超过3份");
                        }
                        combo = target != null ? buildComboFromDish(target, stockList) : null;
                        if (combo == null) {
                            combo = stockListTop3AboveReserve(stockList);
                            if (combo == null) {
                                reason.append("、超过3份的食材不足3种");
                                Log.record("小鸡大厨👨🏻‍🍳剩余食材不足，跳过做美食#策略["
                                        + ChickenChefCookStrategy.nickNames[strategy] + "]#原因[" + reason + "]");
                                return;
                            }
                        }
                    }
                } else {
                    if (allCombosUsed(stockList, usedCombos)) {
                        combo = chooseFullComboCombo(stockList, dishes);
                        if (combo == null) {
                            Log.record("小鸡大厨👨🏻‍🍳随机制作：全组合下可消耗食材不足，跳过做美食");
                            return;
                        }
                    } else {
                        combo = randomCombo(stockList, dishes);
                        if (combo == null) {
                            Log.record("小鸡大厨👨🏻‍🍳随机制作：剩余组合均为满分菜品，跳过做美食");
                            return;
                        }
                    }
                }
                StringBuilder ingredientIds = new StringBuilder();
                List<String> comboIds = new ArrayList<>();
                for (int j = 0; j < combo.size(); j++) {
                    String ingredientId = combo.get(j).optString("ingredientId");
                    comboIds.add(ingredientId);
                    if (j > 0) {
                        ingredientIds.append(',');
                    }
                    ingredientIds.append('"').append(ingredientId).append('"');
                }
                jo = new JSONObject(AntFarmRpcCall.cookAiKitchen(ingredientIds.toString(), seasonId));
                if (!MessageUtil.checkMemo(TAG, jo)) {
                    return;
                }
                if (usedCombos != null) {
                    usedCombos.add(aiKitchenComboKey(comboIds));
                }
                if (target != null) {
                    target.highestScore = Math.max(target.highestScore,
                            jo.optInt("highestScore", target.highestScore));
                }
                boolean firstUnlock = jo.optBoolean("firstUnlock");
                if (firstUnlock) {
                    AI_KITCHEN_DISH_CACHE.remove(UserIdMap.getCurrentUid());
                }
                String unlockTip = firstUnlock ? "#首次解锁新菜" : "";
                Log.farm("小鸡大厨👨🏻‍🍳制作[" + jo.optString("dishName") + "]#厨神分+" + jo.optInt("chefScoreDelta") + unlockTip);
                TimeUtil.sleep(RandomUtil.delay());
            }
        } catch (Throwable t) {
            Log.i(TAG, "chickenChefCook err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static List<AiKitchenDish> loadAiKitchenDishes(String seasonId) {
        try {
            JSONObject cookbook = new JSONObject(AntFarmRpcCall.getAiKitchenCookbookInfo(seasonId));
            if (!MessageUtil.checkMemo(TAG, cookbook)) {
                return null;
            }
            List<AiKitchenDish> dishes = new ArrayList<>();
            JSONArray dishList = cookbook.optJSONArray("dishList");
            if (dishList == null) {
                return dishes;
            }
            for (int i = 0; i < dishList.length(); i++) {
                JSONObject dish = dishList.optJSONObject(i);
                if (dish == null || dish.optInt("cookCount") <= 0) {
                    continue;
                }
                String dishId = dish.optString("dishId");
                if (dishId.isEmpty()) {
                    continue;
                }
                JSONObject detail = new JSONObject(AntFarmRpcCall.queryAiKitchenDishDetail(dishId, seasonId));
                if (!MessageUtil.checkMemo(TAG, detail)) {
                    continue;
                }
                JSONArray ingredients = detail.optJSONArray("ingredientList");
                if (ingredients == null || ingredients.length() != AI_KITCHEN_INGREDIENT_COUNT) {
                    continue;
                }
                List<String> ids = new ArrayList<>();
                for (int j = 0; j < ingredients.length(); j++) {
                    JSONObject ingredient = ingredients.optJSONObject(j);
                    if (ingredient != null) {
                        ids.add(ingredient.optString("ingredientId"));
                    }
                }
                if (ids.size() == AI_KITCHEN_INGREDIENT_COUNT) {
                    dishes.add(new AiKitchenDish(ids, dish.optInt("highestScore")));
                }
                TimeUtil.sleep(RandomUtil.delay());
            }
            Log.record("小鸡大厨👨🏻‍🍳风味手帐菜品" + dishes.size() + "道");
            return dishes;
        } catch (Throwable t) {
            Log.i(TAG, "loadAiKitchenDishes err:");
            Log.printStackTrace(TAG, t);
        }
        return null;
    }

    private static Set<String> buildUsedCombos(List<AiKitchenDish> dishes) {
        Set<String> usedCombos = new HashSet<>();
        for (AiKitchenDish dish : dishes) {
            usedCombos.add(aiKitchenComboKey(dish.ingredientIds));
        }
        return usedCombos;
    }

    private static AiKitchenDish pickMasteryComboDish(List<AiKitchenDish> dishes, List<JSONObject> stockList) {
        if (dishes == null || dishes.isEmpty()) {
            return null;
        }
        AiKitchenDish best = null;
        for (AiKitchenDish dish : dishes) {
            if (dish.highestScore >= 100 || !hasAllIngredients(dish, stockList)) {
                continue;
            }
            if (best == null || dish.highestScore > best.highestScore) {
                best = dish;
            }
        }
        return best;
    }

    private static boolean allMasteryFull(List<AiKitchenDish> dishes) {
        if (dishes == null || dishes.isEmpty()) {
            return false;
        }
        for (AiKitchenDish dish : dishes) {
            if (dish.highestScore < 100) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasOverstockIngredient(List<JSONObject> stockList) {
        for (JSONObject item : stockList) {
            if (item.optInt("count") > AI_KITCHEN_INGREDIENT_COUNT) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAllIngredients(AiKitchenDish dish, List<JSONObject> stockList) {
        for (String ingredientId : dish.ingredientIds) {
            boolean found = false;
            for (JSONObject item : stockList) {
                if (ingredientId.equals(item.optString("ingredientId")) && item.optInt("count") > 0) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static List<JSONObject> buildComboFromDish(AiKitchenDish dish, List<JSONObject> stockList) {
        if (dish == null) {
            return null;
        }
        List<JSONObject> combo = new ArrayList<>();
        for (String ingredientId : dish.ingredientIds) {
            JSONObject matched = null;
            for (JSONObject item : stockList) {
                if (ingredientId.equals(item.optString("ingredientId")) && item.optInt("count") > 0) {
                    matched = item;
                    break;
                }
            }
            if (matched == null) {
                return null;
            }
            combo.add(matched);
        }
        return combo.size() == AI_KITCHEN_INGREDIENT_COUNT ? combo : null;
    }

    private static List<JSONObject> stockListTop3AboveReserve(List<JSONObject> stockList) {
        List<JSONObject> candidates = new ArrayList<>();
        for (JSONObject item : stockList) {
            if (item.optInt("count") > AI_KITCHEN_INGREDIENT_COUNT) {
                candidates.add(item);
            }
        }
        if (candidates.size() < AI_KITCHEN_INGREDIENT_COUNT) {
            return null;
        }
        return new ArrayList<>(candidates.subList(0, AI_KITCHEN_INGREDIENT_COUNT));
    }

    private static List<JSONObject> randomCombo(List<JSONObject> stockList, List<AiKitchenDish> dishes) {
        boolean excludeFull = dishes != null && !dishes.isEmpty();
        Set<String> fullCombos = new HashSet<>();
        if (excludeFull) {
            for (AiKitchenDish dish : dishes) {
                if (dish.highestScore >= 100) {
                    fullCombos.add(aiKitchenComboKey(dish.ingredientIds));
                }
            }
        }
        List<List<JSONObject>> candidates = new ArrayList<>();
        int size = stockList.size();
        for (int i = 0; i < size - 2; i++) {
            for (int j = i + 1; j < size - 1; j++) {
                for (int k = j + 1; k < size; k++) {
                    List<JSONObject> combo = new ArrayList<>();
                    combo.add(stockList.get(i));
                    combo.add(stockList.get(j));
                    combo.add(stockList.get(k));
                    if (excludeFull) {
                        List<String> ids = new ArrayList<>();
                        for (JSONObject item : combo) {
                            ids.add(item.optString("ingredientId"));
                        }
                        if (fullCombos.contains(aiKitchenComboKey(ids))) {
                            continue;
                        }
                    }
                    candidates.add(combo);
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(RandomUtil.nextInt(0, candidates.size()));
    }

    private static List<JSONObject> chooseFullComboCombo(List<JSONObject> stockList, List<AiKitchenDish> dishes) {
        if (dishes == null || allMasteryFull(dishes)) {
            return randomAnyCombo(stockList);
        }
        Set<String> reserved = new HashSet<>();
        for (AiKitchenDish dish : dishes) {
            if (dish.highestScore < 100) {
                reserved.addAll(dish.ingredientIds);
            }
        }
        List<JSONObject> pool = new ArrayList<>();
        for (JSONObject item : stockList) {
            if (!reserved.contains(item.optString("ingredientId"))) {
                pool.add(item);
            }
        }
        if (pool.size() < AI_KITCHEN_INGREDIENT_COUNT) {
            return null;
        }
        return randomAnyCombo(pool);
    }

    private static List<JSONObject> randomAnyCombo(List<JSONObject> stockList) {
        List<JSONObject> randomList = new ArrayList<>(stockList);
        Collections.shuffle(randomList);
        return new ArrayList<>(randomList.subList(0, AI_KITCHEN_INGREDIENT_COUNT));
    }

    private static boolean allCombosUsed(List<JSONObject> stockList, Set<String> usedCombos) {
        if (usedCombos == null || usedCombos.isEmpty()) {
            return false;
        }
        int size = stockList.size();
        for (int i = 0; i < size - 2; i++) {
            for (int j = i + 1; j < size - 1; j++) {
                for (int k = j + 1; k < size; k++) {
                    List<String> ids = new ArrayList<>();
                    ids.add(stockList.get(i).optString("ingredientId"));
                    ids.add(stockList.get(j).optString("ingredientId"));
                    ids.add(stockList.get(k).optString("ingredientId"));
                    if (!usedCombos.contains(aiKitchenComboKey(ids))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static List<JSONObject> buildExplorationStock(List<JSONObject> stockList, List<AiKitchenDish> dishes) {
        Set<String> knownIngredients = new HashSet<>();
        if (dishes != null) {
            for (AiKitchenDish dish : dishes) {
                knownIngredients.addAll(dish.ingredientIds);
            }
        }
        List<JSONObject> pool = new ArrayList<>();
        for (JSONObject item : stockList) {
            String ingredientId = item.optString("ingredientId");
            if (!knownIngredients.contains(ingredientId)
                    || item.optInt("count") > AI_KITCHEN_EXPLORATION_RESERVE) {
                pool.add(item);
            }
        }
        return pool;
    }

    private static List<JSONObject> chooseExplorationCombo(List<JSONObject> stockList, List<AiKitchenDish> dishes,
                                                            Set<String> usedCombos) {
        List<JSONObject> pool = buildExplorationStock(stockList, dishes);
        if (pool.size() < AI_KITCHEN_INGREDIENT_COUNT) {
            return null;
        }
        List<JSONObject> combo = chooseNewAiKitchenCombo(pool, usedCombos);
        return combo != null ? combo : randomCombo(pool, dishes);
    }

    private static List<JSONObject> chooseNewAiKitchenCombo(List<JSONObject> stockList, Set<String> usedCombos) {
        int size = stockList.size();
        for (int i = 0; i < size - 2; i++) {
            for (int j = i + 1; j < size - 1; j++) {
                for (int k = j + 1; k < size; k++) {
                    List<String> ids = new ArrayList<>();
                    ids.add(stockList.get(i).optString("ingredientId"));
                    ids.add(stockList.get(j).optString("ingredientId"));
                    ids.add(stockList.get(k).optString("ingredientId"));
                    if (usedCombos == null || !usedCombos.contains(aiKitchenComboKey(ids))) {
                        List<JSONObject> combo = new ArrayList<>();
                        combo.add(stockList.get(i));
                        combo.add(stockList.get(j));
                        combo.add(stockList.get(k));
                        return combo;
                    }
                }
            }
        }
        return null;
    }

    private static String aiKitchenComboKey(List<String> ingredientIds) {
        List<String> sorted = new ArrayList<>(ingredientIds);
        Collections.sort(sorted);
        StringBuilder key = new StringBuilder();
        for (String id : sorted) {
            if (key.length() > 0) {
                key.append(',');
            }
            key.append(id);
        }
        return key.toString();
    }

    public static void chickenChefCookbookInfo() {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.getAiKitchenCookbookInfo("AIKitchenSeasonOne"));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            StringBuilder info = new StringBuilder();
            info.append("小鸡大厨👨🏻‍🍳[").append(UserIdMap.getShowName(UserIdMap.getCurrentUid())).append("]").append("[").append(jo.optString("seasonName")).append("]").append("厨神分").append(jo.optInt("chefScore")).append(";精通分").append(jo.optInt("masteryScore")).append(";丰富度").append(jo.optInt("richnessScore")).append(";图鉴").append(jo.optInt("totalUnlockedDishCount")).append("/").append(jo.optInt("totalNormalDishCount")).append("(黑暗").append(jo.optInt("unlockedDarkDishCount")).append("/").append(jo.optInt("totalDarkDishCount")).append(");");
            JSONArray dishList = jo.optJSONArray("dishList");
            if (dishList != null && dishList.length() > 0) {
                info.append("拿手菜").append(dishList.length()).append("道[");
                for (int i = 0; i < dishList.length(); i++) {
                    JSONObject dish = dishList.optJSONObject(i);
                    if (dish == null) {
                        continue;
                    }
                    if (i > 0) {
                        info.append(';');
                    }
                    info.append(dish.optString("dishName")).append('(').append(dish.optString("highestGrade")).append(' ').append(dish.optInt("highestScore")).append("分/").append(dish.optInt("cookCount")).append("次)");
                }
                info.append("];");
            }
            Log.info(info.toString());
        } catch (Throwable t) {
            Log.i(TAG, "chickenChefCookbookInfo err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 小鸡大厨任务列表
     * @param userId 当前用户ID
     * @param blackList 黑名单集合
     * @param giftFriendListField 赠送好友列表配置字段
     */
    public static void chickenChefTask(String userId, Set<String> blackList, SelectModelField giftFriendListField) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.listAiKitchenTask());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONArray ja = jo.getJSONArray("farmTaskList");
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                String title = jo.optString("title");
                String taskId = jo.optString("taskId");
                String bizKey = jo.optString("bizKey", taskId);
                String taskType = jo.optString("taskType", taskId);
                String taskStatus = jo.optString("taskStatus", "TODO");
                if (AntFarm.TaskStatus.FINISHED.name().equals(taskStatus)) {
                    chickenChefTaskAward(jo, title, taskType);
                    //还有剩余档位时继续往下推进，做到一次进入推满
                    if (jo.optInt("rightsTimesLimit", 0) <= jo.optInt("rightsTimes", 0)) {
                        continue;
                    }
                }
                if (blackList.contains(title)) {
                    String desc = jo.optString("desc");
                    boolean isGameTask = bizKey.contains("GAME") || (title + desc).contains("玩游戏得") || (title + desc).contains("去玩");
                    if (AntFarm.TaskStatus.TODO.name().equals(taskStatus) && isGameTask) {
                        MiniGameTaskListMap.add(title, desc, "AntFarmChickenChefTaskList");
                        if (BaseModel.isMiniGameTaskDisallowed(title, desc, "AntFarmChickenChefTaskList")) {
                            Log.i("游戏任务🎮跳过[" + MiniGameTaskListMap.name(title, desc) + "]场景[" + MessageUtil.getTaskListName("AntFarmChickenChefTaskList") + "]#已加入不允许执行列表");
                            continue;
                        }
                        int rightsTimesLimit = jo.optInt("rightsTimesLimit");
                        int rightsTimes = jo.optInt("rightsTimes");
                        int remaining = TaskAlternative.rightsTimesToDo(rightsTimes, rightsTimesLimit);
                        for (int j = 0; j < remaining; j++) {
                            if (!TaskAlternative.hasExecuteQuota("AntFarmChickenChefTaskList", title, desc)) {
                                break;
                            }
                            JSONObject pseudoJo = new JSONObject(doFarmTaskPseudo(bizKey, AntFarmRpcCall.AI_KITCHEN_TASK_SCENE));
                            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmChickenChefTaskList", title, pseudoJo);
                            if (MessageUtil.isSecurityVerify(pseudoJo)) {
                                break;
                            }
                            TaskAlternative.recordExecuteQuota("AntFarmChickenChefTaskList", title, desc);
                            if (!MessageUtil.checkSuccess(TAG, pseudoJo)) {
                                break;
                            }
                            Log.farm("小鸡大厨🧾申报[" + title + "]");
                            TimeUtil.sleep(1000);
                            chickenChefTaskAward(jo, title, taskType);
                        }
                    } else {
                        TaskAlternative.handleGameTask(new TaskAlternative.GameTask("AntFarmChickenChefTaskList", title, desc, jo.optString("targetUrl"), taskStatus).bizKey(bizKey).rights(jo.optInt("rightsTimes", 0), jo.optInt("rightsTimesLimit", 0)).taskRemain(Math.max(0, jo.optInt("taskRequire", 0) - jo.optInt("taskProgress", 0))).taskPlayType(jo.optString("taskPlayType", "OTHER")).taskMode(jo.optString("taskMode")).taskSceneCode(AntFarmRpcCall.AI_KITCHEN_TASK_SCENE).scene("ANTFARM", "H5").version(AntFarmRpcCall.VERSION));
                    }
                    continue;
                }
                if (AntFarm.TaskStatus.TODO.name().equals(taskStatus)) {
                    if ("给好友送食材".equals(title)) {
                        chickenChefShareIngredient(jo, giftFriendListField);
                        continue;
                    }
                    int rightsTimesLimit = jo.optInt("rightsTimesLimit");
                    int rightsTimes = jo.optInt("rightsTimes");
                    int remaining = TaskAlternative.rightsTimesToDo(rightsTimes, rightsTimesLimit);
                    for (int j = 0; j < remaining; j++) {
                        String outBizNo = userId + System.currentTimeMillis();
                        JSONObject finishJo = new JSONObject(AntFarmRpcCall.finishTaskantfarm(bizKey, outBizNo, taskType, userId));
                        MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmChickenChefTaskList", title, finishJo);
                        if (!MessageUtil.checkResultCode(TAG, finishJo)) {
                            break;
                        }
                        Log.farm("小鸡大厨🧾完成[" + title + "]"
                                + TaskAlternative.rightsProgressText(rightsTimes, rightsTimesLimit, j));
                        TimeUtil.sleep(1000);
                        chickenChefTaskAward(jo, title, taskType);
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "chickenChefTask err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void chickenChefTaskAward(JSONObject task, String title, String taskType) {
        try {
            int awardCount = task.optInt("canReceiveAwardCount", task.optInt("awardCount"));
            JSONObject jo = new JSONObject(AntFarmRpcCall.receiveTaskAwardantfarm(awardCount, AntFarmRpcCall.AI_KITCHEN_TASK_SCENE, taskType));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.farm("小鸡大厨🎖️领取[" + title + "]奖励");
            }
            TimeUtil.sleep(1000);
        } catch (Throwable t) {
            Log.i(TAG, "chickenChefTaskAward err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void chickenChefShareIngredient(JSONObject task, SelectModelField giftFriendListField) {
        try {
            int giftLimit = task.optInt("rightsTimesLimit") - task.optInt("rightsTimes");
            if (giftLimit <= 0) {
                return;
            }
            Set<String> friendSet = giftFriendListField != null ? giftFriendListField.getValue() : null;
            if (friendSet == null || friendSet.isEmpty()) {
                Log.record("小鸡大厨👨🏻‍🍳未配置可赠送好友列表，跳过给好友送食材任务");
                return;
            }
            List<String> friendList = new ArrayList<>();
            for (String userId : friendSet) {
                if (!userId.equals(UserIdMap.getCurrentUid())) {
                    friendList.add(userId);
                }
            }
            if (friendList.isEmpty()) {
                Log.record("小鸡大厨👨🏻‍🍳未配置可赠送好友列表，跳过给好友送食材任务");
                return;
            }
            JSONObject kitchenJo = new JSONObject(AntFarmRpcCall.enterAiKitchen());
            if (!MessageUtil.checkMemo(TAG, kitchenJo)) {
                return;
            }
            String seasonId = kitchenJo.optString("seasonId", "AIKitchenSeasonOne");
            JSONArray ingredientList = kitchenJo.optJSONArray("ingredientList");
            List<JSONObject> giftList = new ArrayList<>();
            if (ingredientList != null) {
                for (int i = 0; i < ingredientList.length() && giftList.size() < giftLimit; i++) {
                    JSONObject ingredient = ingredientList.optJSONObject(i);
                    if (ingredient == null) {
                        continue;
                    }
                    int count = ingredient.optInt("count");
                    for (int j = 0; j < count && giftList.size() < giftLimit; j++) {
                        giftList.add(ingredient);
                    }
                }
            }
            if (giftList.isEmpty()) {
                Log.record("小鸡大厨👨🏻‍🍳食材不足，跳过给好友送食材任务");
                return;
            }
            for (int i = 0; i < giftList.size(); i++) {
                String receiverId = friendList.get(i % friendList.size());
                JSONObject ingredient = giftList.get(i);
                JSONObject jo = new JSONObject(AntFarmRpcCall.shareAiKitchenIngredient(ingredient.optString("ingredientId"), receiverId, seasonId));
                if (!MessageUtil.checkMemo(TAG, jo)) {
                    break;
                }
                Log.farm("小鸡大厨👨🏻‍🍳赠送[" + UserIdMap.getMaskName(receiverId) + "]食材[" + ingredient.optString("ingredientName") + "]");
                TimeUtil.sleep(1000);
            }
        } catch (Throwable t) {
            Log.i(TAG, "chickenChefShareIngredient err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 小鸡大厨伪申报
     */
    private static String doFarmTaskPseudo(String bizKey, String taskSceneCode) {
        String args = "[{\"bizKey\":\"" + bizKey + "\",\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"taskSceneCode\":\"" + taskSceneCode + "\",\"version\":\"" + AntFarmRpcCall.VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.doFarmTask", args);
    }

    /* ===== 任务列表初始化 ===== */

    public static void initChickenChefTaskListMap(boolean autoBlackList, boolean chickenChef,
                                                  String modelConfigKey, String taskListFieldKey) {
        try {
            if (!chickenChef) {
                return;
            }
            AntFarmChickenChefTaskListMap.load();
            JSONObject jo = new JSONObject(AntFarmRpcCall.listAiKitchenTask());
            if (MessageUtil.checkMemo(TAG, jo)) {
                JSONArray farmTaskList = jo.getJSONArray("farmTaskList");
                for (int i = 0; i < farmTaskList.length(); i++) {
                    jo = farmTaskList.getJSONObject(i);
                    String title = jo.getString("title");
                    AntFarmChickenChefTaskListMap.add(title, TaskIconUtil.extractIcon(jo));
                }
            }
            AntFarmChickenChefTaskListMap.save();
            Log.record("同步任务🉑小鸡大厨任务列表");

            if (autoBlackList) {
                Set<String> chickenChefBlackList = new HashSet<>();
                io.github.lazyimmortal.sesame.data.ConfigV2 config = io.github.lazyimmortal.sesame.data.ConfigV2.INSTANCE;
                io.github.lazyimmortal.sesame.data.ModelFields fields = config.getModelFieldsMap().get(modelConfigKey);
                if (fields == null) {
                    return;
                }
                SelectModelField blackListField = (SelectModelField) fields.get(taskListFieldKey);
                if (blackListField != null) {
                    Set<String> currentValues = blackListField.getValue();
                    if (currentValues != null) {
                        for (String task : chickenChefBlackList) {
                            if (!currentValues.contains(task)) {
                                blackListField.add(task, 0);
                            }
                        }
                    }
                    if (io.github.lazyimmortal.sesame.data.ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
                        Log.record("黑白名单🈲小鸡大厨任务自动设置: " + blackListField.getValue());
                    } else {
                        Log.record("小鸡大厨任务黑白名单设置失败");
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "initChickenChefTaskListMap err:");
            Log.printStackTrace(TAG, t);
        }
    }
}
