package io.github.lazyimmortal.sesame.util.idMap;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import io.github.lazyimmortal.sesame.entity.CustomOption;
import io.github.lazyimmortal.sesame.util.FileUtil;
import io.github.lazyimmortal.sesame.util.JsonUtil;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.TaskIconUtil;

/**
 * 游戏任务收集表（所有账号共用）：key=任务名[描述]，value=出现过的任务列表类（listTitle）集合。
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

    /** 任务图标：key=任务名[描述]（与 idMap 同键），value=图标地址，空串表示无图标 */
    private static final Map<String, String> iconMap = new ConcurrentHashMap<>();

    private static final Map<String, List<String>> readOnlyIdMap = Collections.unmodifiableMap(idMap);

    private static volatile boolean loaded = false;

    /**
     * 游戏任务标题总白名单：所有任务列表共用，命中任一即视为游戏任务（走提交接口完成）。
     * 仅当任务标题未命中分项黑名单时，才会再查本白名单。
     */
    //public static final String[] COMMON_GAME_TASK_WHITELIST = {"玩游戏得", "去玩"};
    public static final String[] COMMON_GAME_TASK_WHITELIST = {};

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

    /** 带括号的进度，如「(50/8000)」「（3/5）」，只留分母（→「8000」） */
    private static final Pattern BRACKET_PROGRESS_PATTERN = Pattern.compile("[(（]\\s*\\d+\\s*/\\s*(\\d+)\\s*[)）]");

    /** 不带括号的进度，如「做任务已攒2430/3000」，只留分母（→「3000」） */
    private static final Pattern BARE_PROGRESS_PATTERN = Pattern.compile("\\d+\\s*/\\s*(\\d+)");

    /** 单独的括号数字，如「做任务攒(8000)」，去括号只留数字（→「8000」） */
    private static final Pattern BRACKET_NUMBER_PATTERN = Pattern.compile("[(（]\\s*(\\d+)\\s*[)）]");

    /** 任务显示名：{@code 任务名[描述]}；描述为空时只用任务名；描述中的动态进度只留目标值。 */
    public static String name(String taskTitle, String desc) {
        if (taskTitle == null) {
            return "";
        }
        String trimmed = normalize(desc == null ? "" : desc.trim());
        return trimmed.isEmpty() ? taskTitle : taskTitle + "[" + trimmed + "]";
    }

    /**
     * 归并描述里的动态进度，只保留目标值且不留括号：
     * 「做任务已攒2430/3000」→「做任务已攒3000」、「做任务攒(8000)」→「做任务攒8000」。
     * 避免同一任务按进度裂变出大量条目。
     */
    private static String normalize(String desc) {
        String result = readableDesc(desc);
        result = BRACKET_PROGRESS_PATTERN.matcher(result).replaceAll("$1");
        result = BARE_PROGRESS_PATTERN.matcher(result).replaceAll("$1");
        return BRACKET_NUMBER_PATTERN.matcher(result).replaceAll("$1");
    }

    /** 描述以 JSON 数组/对象下发时（如月卡的多段说明），取出其中的可读文案，避免整段 JSON 拼进任务名。 */
    private static String readableDesc(String desc) {
        String text = desc == null ? "" : desc.trim();
        if (!text.startsWith("{") && !text.startsWith("[")) {
            return text;
        }
        try {
            Object parsed = JsonUtil.parseObject(text, Object.class);
            Object node = parsed;
            if (node instanceof List && !((List<?>) node).isEmpty()) {
                node = ((List<?>) node).get(0);
            }
            if (node instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) node;
                for (String field : new String[]{"baseDesc", "desc", "description", "subTitle", "text", "content", "title"}) {
                    Object value = map.get(field);
                    if (value instanceof String && !((String) value).trim().isEmpty()) {
                        return cleanPlaceholder((String) value);
                    }
                }
                for (Object value : map.values()) {
                    if (value instanceof String && !((String) value).trim().isEmpty()) {
                        return cleanPlaceholder((String) value);
                    }
                }
            } else if (node instanceof String) {
                return cleanPlaceholder((String) node);
            }
        } catch (Throwable ignored) {
            // 不是合法 JSON，保持原文
        }
        return text;
    }

    /** 去掉描述模板里的占位符，如「剩余{remainingDays}天」→「剩余天」。 */
    private static String cleanPlaceholder(String text) {
        return text.replaceAll("\\{[^{}]*\\}", "").trim();
    }

    /** 「任务名[描述] + 任务列表类」组合键，用于每个账号独立的不允许执行列表。 */
    public static String key(String taskName, String listTitle) {
        return taskName + "||" + listTitle;
    }

    /** 选项列表：每个「任务名[描述] -> 任务列表类」一项，供设置界面勾选（带任务图标）。 */
    public static List<CustomOption> getOptions() {
        List<CustomOption> options = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : getMap().entrySet()) {
            for (String listTitle : entry.getValue()) {
                CustomOption option = new CustomOption(key(entry.getKey(), listTitle),
                        entry.getKey() + " | " + MessageUtil.getTaskListName(listTitle));
                option.icon = backfillIcon(entry.getKey(), listTitle);
                options.add(option);
            }
        }
        return options;
    }

    /** 取任务图标；历史收集条目无图标时从任务所在清单反查补录（key 带[描述]的剥掉再查）。 */
    private static String backfillIcon(String taskName, String listTitle) {
        String icon = getIcon(taskName);
        if (TaskIconUtil.isIconUrl(icon)) {
            return icon;
        }
        icon = resolveIcon(listTitle, taskName);
        if (!TaskIconUtil.isIconUrl(icon)) {
            int idx = taskName.lastIndexOf('[');
            if (idx > 0 && taskName.endsWith("]")) {
                icon = resolveIcon(listTitle, taskName.substring(0, idx));
            }
        }
        if (TaskIconUtil.isIconUrl(icon)) {
            iconMap.put(taskName, icon);
            saveIcons();
        }
        return TaskIconUtil.isIconUrl(icon) ? icon : "";
    }

    /** 任务图标地址（key=任务名[描述]），无图标返回空串。 */
    public static String getIcon(String taskName) {
        ensureLoaded();
        String icon = iconMap.get(taskName);
        return icon == null ? "" : icon;
    }

    /** 从任务所在清单（各 XxxTaskListMap，value 即图标地址）反查任务图标。 */
    private static String resolveIcon(String listTitle, String taskTitle) {
        if (listTitle == null || taskTitle == null) {
            return "";
        }
        String icon = null;
        switch (listTitle) {
            case "AntFarmDoFarmTaskList":
                icon = AntFarmDoFarmTaskListMap.get(taskTitle);
                break;
            case "AntFarmDrawMachineTaskList":
                icon = AntFarmDrawMachineTaskListMap.get(taskTitle);
                break;
            case "AntFarmChickenChefTaskList":
                icon = AntFarmChickenChefTaskListMap.get(taskTitle);
                break;
            case "AntOrchardTaskList":
                icon = AntOrchardTaskListMap.get(taskTitle);
                break;
            case "AntOrchardDrawTaskList":
                icon = AntOrchardDrawTaskListMap.get(taskTitle);
                break;
            case "AntForestVitalityTaskList":
                icon = AntForestVitalityTaskListMap.get(taskTitle);
                break;
            case "AntForestHuntTaskList":
                icon = AntForestHuntTaskListMap.get(taskTitle);
                break;
            case "MonopolyTaskList":
                icon = MonopolyTaskListMap.get(taskTitle);
                break;
            case "AntOceanAntiepTaskList":
                icon = AntOceanAntiepTaskListMap.get(taskTitle);
                break;
            case "AntOceanFishBlackList":
                icon = AntOceanFishBlackListMap.get(taskTitle);
                break;
            case "AntStallTaskList":
                icon = AntStallTaskListMap.get(taskTitle);
                break;
            case "AntDodoTaskList":
                icon = AntDodoTaskListMap.get(taskTitle);
                break;
            case "GoldenBeansTaskList":
                icon = GoldenBeansTaskListMap.get(taskTitle);
                break;
            case "MemberCreditSesameTaskList":
                icon = MemberCreditSesameTaskListMap.get(taskTitle);
                break;
            case "AntSportsTaskList":
                icon = AntSportsTaskListMap.get(taskTitle);
                break;
            case "AntMemberTaskList":
                icon = AntMemberTaskListMap.get(taskTitle);
                break;
        }
        return TaskIconUtil.isIconUrl(icon) ? icon : "";
    }

    public static Map<String, List<String>> getMap() {
        ensureLoaded();
        return readOnlyIdMap;
    }

    public static List<String> get(String key) {
        ensureLoaded();
        return idMap.get(key);
    }

    /** 记录一条「任务名[描述] -> 任务列表类」映射；已存在则忽略，新增后落盘。图标从任务所在清单反查补录。 */
    public synchronized static void add(String taskTitle, String desc, String listTitle) {
        String taskName = name(taskTitle, desc);
        ensureLoaded();
        //图标: 已有有效图标则保留, 否则从任务所在清单反查; 清单里暂无则留空, 等清单同步后再补
        String oldIcon = iconMap.get(taskName);
        if (!TaskIconUtil.isIconUrl(oldIcon)) {
            String icon = resolveIcon(listTitle, taskTitle);
            if (TaskIconUtil.isIconUrl(icon)) {
                iconMap.put(taskName, icon);
                saveIcons();
            }
        }
        add(taskName, listTitle);
    }

    /** 底层写入：key 为已组装好的任务名，直接落表。 */
    private synchronized static void add(String key, String value) {
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
            Log.record("游戏任务🔱收集[" + key + "]来源[" + MessageUtil.getTaskListName(value) + "]");
        }
    }

    public synchronized static void remove(String key) {
        ensureLoaded();
        idMap.remove(key);
        iconMap.remove(key);
        save();
    }

    public synchronized static void load() {
        idMap.clear();
        iconMap.clear();
        try {
            String body = FileUtil.readFromFile(FileUtil.getMiniGameTaskListMapFile());
            if (!body.isEmpty()) {
                Map<String, List<String>> newMap = JsonUtil.parseObject(body, new TypeReference<Map<String, List<String>>>() {
                });
                idMap.putAll(newMap);
            }
            String iconBody = FileUtil.readFromFile(FileUtil.getMiniGameTaskIconMapFile());
            if (!iconBody.isEmpty()) {
                Map<String, String> newIcons = JsonUtil.parseObject(iconBody, new TypeReference<Map<String, String>>() {
                });
                for (Map.Entry<String, String> entry : newIcons.entrySet()) {
                    String value = entry.getValue();
                    iconMap.put(entry.getKey(), TaskIconUtil.isIconUrl(value) ? value : "");
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    public synchronized static boolean save() {
        boolean success = FileUtil.write2File(JsonUtil.toJsonString(idMap), FileUtil.getMiniGameTaskListMapFile());
        saveIcons();
        return success;
    }

    /** 图标表落盘, 并预下载涉及到的图标（已缓存的跳过）。 */
    public synchronized static void saveIcons() {
        FileUtil.write2File(JsonUtil.toJsonString(iconMap), FileUtil.getMiniGameTaskIconMapFile());
        TaskIconUtil.predownload(iconMap.values());
    }

    public synchronized static void clear() {
        idMap.clear();
        iconMap.clear();
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
