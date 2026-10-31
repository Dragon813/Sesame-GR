package io.github.lazyimmortal.sesame.util.idMap;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.lazyimmortal.sesame.entity.CustomOption;
import io.github.lazyimmortal.sesame.util.FileUtil;
import io.github.lazyimmortal.sesame.util.JsonUtil;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;

/**
 * 游戏任务收集表（所有账号共用）：key=任务名称，value=出现过的任务列表（listTitle）集合。
 *
 * <p>记录来源：
 * <ul>
 *     <li>标题包含「玩游戏得」「去玩」的任务，由各任务列表的独立游戏任务流程直接记录；</li>
 *     <li>完成接口返回 400000040「不支持rpc调用」且非交易/履约类任务时，由
 *     MessageUtil.checkResultCodeAndMarkTaskBlackList 自动记录。</li>
 * </ul>
 */
public class MiniGameTaskListMap {

    private static final Map<String, List<String>> idMap = new ConcurrentHashMap<>();

    private static final Map<String, List<String>> readOnlyIdMap = Collections.unmodifiableMap(idMap);

    private static volatile boolean loaded = false;

    /**
     * 游戏任务标题总白名单：所有任务列表共用，命中任一即视为游戏任务（走备用接口完成）。
     * 仅当任务标题未命中分项黑名单时，才会再查本白名单。
     */
    public static final String[] COMMON_GAME_TASK_WHITELIST = {"玩游戏得", "去玩"};

    /**
     * 各任务列表专属的游戏任务标题黑名单，key=任务列表 listTitle。
     *
     * <p>判定顺序：<b>黑名单优先</b>——命中即整个任务直接跳过（不走游戏流程、也不走正常完成流程）；
     * 未命中黑名单的，再查 {@link #COMMON_GAME_TASK_WHITELIST} 总白名单，命中才作为游戏任务处理。
     *
     * <p>结构参照 {@link MessageUtil#checkResultCodeAndMarkTaskBlackList} 的分列表写法：
     * 每个列表都登记，数组为空表示该列表无单独黑名单、仅受总白名单约束。
     */
    private static final Map<String, String[]> GAME_TASK_BLACKLIST = new ConcurrentHashMap<>();

    static {
        //庄园饲料任务
        GAME_TASK_BLACKLIST.put("AntFarmDoFarmTaskList", new String[]{});
        //庄园装扮抽奖任务
        GAME_TASK_BLACKLIST.put("AntFarmDrawMachineTaskList", new String[]{});
        //芭芭农场肥料任务
        GAME_TASK_BLACKLIST.put("AntOrchardTaskList", new String[]{});
        //芭芭农场寻宝任务
        GAME_TASK_BLACKLIST.put("AntOrchardDrawTaskList", new String[]{});
        //森林活力任务
        //GAME_TASK_BLACKLIST.put("AntForestVitalityTaskList", new String[]{"玩一玩"});
        GAME_TASK_BLACKLIST.put("AntForestVitalityTaskList", new String[]{});
        //森林寻宝任务
        GAME_TASK_BLACKLIST.put("AntForestHuntTaskList", new String[]{});
        //新版保护地任务
        GAME_TASK_BLACKLIST.put("MonopolyTaskList", new String[]{});
        //神奇海洋任务
        GAME_TASK_BLACKLIST.put("AntOceanAntiepTaskList", new String[]{});
        //神奇海洋摸鱼任务
        GAME_TASK_BLACKLIST.put("AntOceanFishBlackList", new String[]{});
        //蚂蚁新村任务
        GAME_TASK_BLACKLIST.put("AntStallTaskList", new String[]{});
        //蚂蚁兔兔任务
        GAME_TASK_BLACKLIST.put("AntDodoTaskList", new String[]{});
        //金豆任务
        GAME_TASK_BLACKLIST.put("GoldenBeansTaskList", new String[]{});
        //芝麻信用任务
        GAME_TASK_BLACKLIST.put("MemberCreditSesameTaskList", new String[]{});
        //运动任务
        GAME_TASK_BLACKLIST.put("AntSportsTaskList", new String[]{});
    }

    /** 该任务列表里的标题是否命中分项黑名单（命中即整个任务跳过，不做）。 */
    public static boolean isBlacklisted(String listTitle, String title) {
        return containsKeyword(title, GAME_TASK_BLACKLIST.get(listTitle));
    }

    /** 该任务列表里的标题是否命中总白名单（命中才作为游戏任务走备用接口完成）。 */
    public static boolean isGameTask(String listTitle, String title) {
        return containsKeyword(title, COMMON_GAME_TASK_WHITELIST);
    }

    private static boolean containsKeyword(String title, String[] keywords) {
        if (title == null || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (title.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 「任务名称 + 任务列表」组合键，用于每个账号独立的不允许执行列表。 */
    public static String key(String taskTitle, String listTitle) {
        return taskTitle + "||" + listTitle;
    }

    /** 选项列表：每个「任务名称 -> 任务列表」一项，供设置界面勾选。 */
    public static List<CustomOption> getOptions() {
        List<CustomOption> options = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : getMap().entrySet()) {
            for (String listTitle : entry.getValue()) {
                options.add(new CustomOption(key(entry.getKey(), listTitle),
                        entry.getKey() + " | " + MessageUtil.getTaskListName(listTitle)));
            }
        }
        return options;
    }

    public static Map<String, List<String>> getMap() {
        ensureLoaded();
        return readOnlyIdMap;
    }

    public static List<String> get(String key) {
        ensureLoaded();
        return idMap.get(key);
    }

    /** 记录一条「任务名称 -> 任务列表」映射；已存在则忽略，新增后落盘。 */
    public synchronized static void add(String key, String value) {
        ensureLoaded();
        List<String> list = idMap.get(key);
        if (list == null) {
            list = new ArrayList<>();
            idMap.put(key, list);
        }
        if (list.contains(value)) {
            return;
        }
        list.add(value);
        if (save()) {
            Log.other("游戏任务🎮收集[" + key + "]来源[" + MessageUtil.getTaskListName(value) + "]");
        }
    }

    public synchronized static void remove(String key) {
        ensureLoaded();
        idMap.remove(key);
        save();
    }

    public synchronized static void load() {
        idMap.clear();
        try {
            String body = FileUtil.readFromFile(FileUtil.getMiniGameTaskListMapFile());
            if (!body.isEmpty()) {
                Map<String, List<String>> newMap = JsonUtil.parseObject(body, new TypeReference<Map<String, List<String>>>() {
                });
                idMap.putAll(newMap);
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    public synchronized static boolean save() {
        return FileUtil.write2File(JsonUtil.toJsonString(idMap), FileUtil.getMiniGameTaskListMapFile());
    }

    public synchronized static void clear() {
        idMap.clear();
    }

    private static void ensureLoaded() {
        if (!loaded) {
            synchronized (MiniGameTaskListMap.class) {
                if (!loaded) {
                    load();
                    loaded = true;
                }
            }
        }
    }

}
