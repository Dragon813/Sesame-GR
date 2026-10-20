package io.github.lazyimmortal.sesame.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.entity.KVNode;
import io.github.lazyimmortal.sesame.entity.UserEntity;
import io.github.lazyimmortal.sesame.util.*;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class ConfigV2 {

    private static final String TAG = ConfigV2.class.getSimpleName();

    public static final ConfigV2 INSTANCE = new ConfigV2();

    @JsonIgnore
    private boolean init;

    private final Map<String, ModelFields> modelFieldsMap = new ConcurrentHashMap<>();

    /**
     * 最近一次与磁盘同步（load 或 save）后的配置文本。
     * 模块 App 与注入进程各持一份 ConfigV2 快照、都会整份落盘，靠它识别"文件被别的进程改过"，
     * 防止任务运行中注入进程的整份保存把模块App刚改的配置（如关闭保护罩）覆盖回去。
     */
    @JsonIgnore
    private static String lastSyncedText;

    /** 上次同步时各字段的值快照（key = modelCode|fieldCode），用于挑出本进程真正改过的字段 */
    @JsonIgnore
    private static final Map<String, String> valueBaseline = new ConcurrentHashMap<>();

    /** 当前内存配置所属账号（load 时记录）。save 的目标账号与其不一致时拒绝写入，防止切换竞态把上一账号配置写进新账号文件 */
    @JsonIgnore
    private static String loadedUserId;

    public void setModelFieldsMap(Map<String, ModelFields> newModels) {
        modelFieldsMap.clear();
        Map<String, ModelConfig> modelConfigMap = ModelTask.getModelConfigMap();
        if (newModels == null) {
            newModels = new HashMap<>();
        }
        for (ModelConfig modelConfig : modelConfigMap.values()) {
            String modelCode = modelConfig.getCode();
            ModelFields newModelFields = new ModelFields();
            ModelFields configModelFields = modelConfig.getFields();
            ModelFields modelFields = newModels.get(modelCode);
            if (modelFields != null) {
                for (ModelField<?> configModelField : configModelFields.values()) {
                    ModelField<?> modelField = modelFields.get(configModelField.getCode());
                    try {
                        if (modelField != null) {
                            Object value = modelField.getValue();
                            if (value != null) {
                                configModelField.setObjectValue(value);
                            } else {
                                // 磁盘该键值为null：重置为代码默认值，不保留上一账号/上一轮的内存残留
                                configModelField.reset();
                            }
                        } else {
                            // 磁盘缺该键（如旧版本生成的配置文件）：重置为代码默认值。
                            // 不重置的话静态字段单例会保留上一个load账号的值，造成配置串号（如大号保护罩串到小号）
                            configModelField.reset();
                        }
                    } catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                    newModelFields.addField(configModelField);
                }
            } else {
                for (ModelField<?> configModelField : configModelFields.values()) {
                    // 磁盘缺整个模型（新模型或旧版本配置）：全部重置为代码默认值
                    configModelField.reset();
                    newModelFields.addField(configModelField);
                }
            }
            modelFieldsMap.put(modelCode, newModelFields);
        }
    }

    public Boolean hasModelFields(String modelCode) {
        return modelFieldsMap.containsKey(modelCode);
    }

    /*public ModelFields getModelFields(String modelCode) {
        return modelFieldsMap.get(modelCode);
    }*/

    /*public void removeModelFields(String modelCode) {
        modelFieldsMap.remove(modelCode);
    }*/

    /*public void addModelFields(String modelCode, ModelFields modelFields) {
        modelFieldsMap.put(modelCode, modelFields);
    }*/

    public Boolean hasModelField(String modelCode, String fieldCode) {
        ModelFields modelFields = modelFieldsMap.get(modelCode);
        if (modelFields == null) {
            return false;
        }
        return modelFields.containsKey(fieldCode);
    }

    /*public ModelField getModelField(String modelCode, String fieldCode) {
        ModelFields modelFields = modelFieldsMap.get(modelCode);
        if (modelFields == null) {
            return null;
        }
        return modelFields.get(fieldCode);
    }*/

    /*public void removeModelField(String modelCode, String fieldCode) {
        ModelFields modelFields = getModelFields(modelCode);
        if (modelFields == null) {
            return;
        }
        modelFields.remove(fieldCode);
    }*/

    /*public Boolean addModelField(String modelCode, ModelField modelField) {
        ModelFields modelFields = getModelFields(modelCode);
        if (modelFields == null) {
            return false;
        }
        modelFields.put(modelCode, modelField);
        return true;
    }*/

    /*@SuppressWarnings("unchecked")
    public <T extends ModelField> T getModelFieldExt(String modelCode, String fieldCode) {
        return (T) getModelField(modelCode, fieldCode);
    }*/

    public static Boolean isModify(String userId) {
        String json = null;
        File configV2File;
        if (StringUtil.isEmpty(userId)) {
            configV2File = FileUtil.getDefaultConfigV2File();
        } else {
            configV2File = FileUtil.getConfigV2File(userId);
        }
        if (configV2File.exists()) {
            json = FileUtil.readFromFile(configV2File);
        }
        if (json != null) {
            String formatted = INSTANCE.toSaveStr();
            return formatted == null || !formatted.equals(json);
        }
        return true;
    }

    public static synchronized Boolean save(String userId, Boolean force) {
        String targetUserId = StringUtil.isEmpty(userId) ? "" : userId;
        // 跨账号写守卫：内存配置属于另一账号时（initUser 与 load 之间的残留任务线程 save），拒绝写入，防止把上一账号配置整份写进新账号文件
        if (loadedUserId != null && !Objects.equals(targetUserId, loadedUserId)) {
            Log.record("配置保存跳过#内存配置属于[" + (loadedUserId.isEmpty() ? "默认" : loadedUserId) + "]#拒绝写入[" + (targetUserId.isEmpty() ? "默认" : targetUserId) + "]");
            return false;
        }
        // 本进程没有任何字段级改动时直接返回：写盘只会把磁盘上本进程不认识的键整份覆盖丢失
        if (!valueBaseline.isEmpty() && collectChangedFields().isEmpty()) {
            return true;
        }
        // 磁盘在本进程上次同步之后被别的进程（如模块App）改过时，先把磁盘内容合并进来再落盘
        if (mergeDiskChanges(targetUserId)) {
            // 本进程没有可写内容、磁盘上的是更新版本：跳过写入，别把别人的改动覆盖掉
            return true;
        }
        String json = INSTANCE.toSaveStr();
        boolean success;
        if (StringUtil.isEmpty(userId)) {
            userId = "默认";
            success = FileUtil.setDefaultConfigV2File(json);
        } else {
            success = FileUtil.setConfigV2File(userId, json);
        }

        // ========== 新增：保存成功后触发滚动备份 ==========
        if (success) {
            FileUtil.backupConfigV2WithRolling(userId);
            lastSyncedText = json;
            captureBaseline();
        }

        Log.record("保存配置: " + userId);
        return success;
    }
    
    public static synchronized ConfigV2 load(String userId) {
        Log.i(TAG, "开始加载配置");
        String userName = "";
        File configV2File = null;
        try {
            if (StringUtil.isEmpty(userId)) {
                configV2File = FileUtil.getDefaultConfigV2File();
                userName = "默认";
            } else {
                configV2File = FileUtil.getConfigV2File(userId);
                UserEntity userEntity = UserIdMap.get(userId);
                if (userEntity == null) {
                    userName = userId;
                } else {
                    userName = userEntity.getShowName();
                }
            }
            Log.record("加载配置: " + userName);
            if (configV2File.exists()) {
                String json = FileUtil.readFromFile(configV2File);
                JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                String formatted = INSTANCE.toSaveStr();
                if (formatted != null && !formatted.equals(json)) {
                    // 回写只允许改格式、不许改数据；判定不过就保留磁盘原文，防止内存残留值覆盖丢失
                    if (!JsonUtil.isRewriteLossless(json, formatted)) {
                        Log.record("配置加载结果与磁盘数据不一致，已跳过格式化回写以免覆盖丢失: " + userName);
                    } else {
                        Log.i(TAG, "格式化配置: " + userName);
                        FileUtil.write2File(formatted, configV2File);
                    }
                }
            } else {
                // 账号配置文件不存在：可能是存储故障/误删，先尝试从滚动备份恢复，避免直接重置为默认
                if (restoreFromBackup(userId, configV2File, userName)) {
                    Log.record("配置文件缺失恢复♻️已从备份恢复: " + userName);
                } else {
                    File defaultConfigV2File = FileUtil.getDefaultConfigV2File();
                    if (defaultConfigV2File.exists()) {
                        String json = FileUtil.readFromFile(defaultConfigV2File);
                        JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                        Log.i(TAG, "复制新配置: " + userName);
                        Log.system(TAG, "复制新配置: " + userName);
                        FileUtil.write2File(json, configV2File);
                    } else {
                        INSTANCE.setModelFieldsMap(null);
                        unload();
                        Log.i(TAG, "初始新配置: " + userName);
                        Log.system(TAG, "初始新配置: " + userName);
                        FileUtil.write2File(INSTANCE.toSaveStr(), configV2File);
                    }
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            // 解析失败(文件损坏/读取到空串)：先尝试从滚动备份恢复，恢复不了才重置为默认，
            // 防止偶发写入中断/存储抖动导致的损坏文件被默认配置覆盖造成用户配置永久丢失
            if (configV2File != null && restoreFromBackup(userId, configV2File, userName)) {
                Log.record("配置损坏恢复♻️已从备份恢复: " + userName);
            } else {
                Log.i(TAG, "重置配置: " + userName);
                Log.system(TAG, "重置配置: " + userName);
                INSTANCE.setModelFieldsMap(null);
                unload();
                if (configV2File != null) {
                    FileUtil.write2File(INSTANCE.toSaveStr(), configV2File);
                }
            }
        }
        INSTANCE.setInit(true);
        // 记录内存配置归属账号与同步文本基线，供 save() 的跨账号守卫与磁盘冲突合并使用
        loadedUserId = StringUtil.isEmpty(userId) ? "" : userId;
        lastSyncedText = configV2File != null && configV2File.exists() ? FileUtil.readFromFile(configV2File) : null;
        captureBaseline();
        Log.i(TAG, "加载配置结束");
        return INSTANCE;
    }

    public static synchronized void unload() {
        for (ModelFields modelFields : INSTANCE.modelFieldsMap.values()) {
            for (ModelField<?> modelField : modelFields.values()) {
                if (modelField != null) {
                    modelField.reset();
                }
            }
        }
    }

    /**
     * 从滚动备份恢复账号配置（取该账号最新的一份备份）。
     * 仅当备份内容能完整解析进内存时才认为恢复成功并写回配置文件；解析失败自动尝试下一份。
     *
     * @return true=已恢复并写回磁盘
     */
    private static boolean restoreFromBackup(String userId, File configV2File, String userName) {
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                File backupFile = FileUtil.findLatestBackupFile(userId);
                if (backupFile == null || !backupFile.exists()) {
                    return false;
                }
                String backupJson = FileUtil.readFromFile(backupFile);
                if (StringUtil.isEmpty(backupJson)) {
                    Log.i(TAG, "备份内容为空，跳过: " + backupFile.getName());
                    return false;
                }
                try {
                    JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(backupJson);
                    boolean success = FileUtil.write2File(backupJson, configV2File);
                    Log.i(TAG, "备份恢复成功: " + userName + "#备份文件: " + backupFile.getName());
                    return success;
                } catch (Throwable parseError) {
                    // 最新备份也损坏：删掉它让下次循环取到上一份备份
                    Log.printStackTrace(TAG, parseError);
                    Log.i(TAG, "备份损坏，尝试上一份: " + backupFile.getName());
                    backupFile.delete();
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    public String toSaveStr() {
        return JsonUtil.toFormatJsonString(this);
    }

    /**
     * 值的稳定文本形式：集合/映射先排序再拼接，避免顺序抖动把「没改」误判成「本进程改过」
     */
    private static String stableValueText(Object value) {
        if (value instanceof List) {
            // List 的顺序就是值本身，不能排序
            return String.valueOf(value);
        }
        if (value instanceof java.util.Collection) {
            java.util.List<String> parts = new ArrayList<>();
            for (Object item : (java.util.Collection<?>) value) {
                parts.add(String.valueOf(item));
            }
            java.util.Collections.sort(parts);
            return parts.toString();
        }
        if (value instanceof Map) {
            java.util.List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                parts.add(entry.getKey() + "=" + entry.getValue());
            }
            java.util.Collections.sort(parts);
            return parts.toString();
        }
        return String.valueOf(value);
    }

    /** 记录当前各字段的值快照，用于之后判断"本进程改过哪些字段" */
    private static void captureBaseline() {
        Map<String, String> baseline = new HashMap<>();
        for (Map.Entry<String, ModelFields> modelEntry : INSTANCE.modelFieldsMap.entrySet()) {
            for (ModelField<?> field : modelEntry.getValue().values()) {
                if (field != null && field.getCode() != null) {
                    baseline.put(fieldKey(modelEntry.getKey(), field.getCode()), stableValueText(field.getValue()));
                }
            }
        }
        valueBaseline.clear();
        valueBaseline.putAll(baseline);
    }

    /**
     * 收集本进程相对上次同步改过的字段（key = {@code modelCode|fieldCode}）。
     * 没有基线的字段一律不算本进程改动——宁可少合并，也不拿旧值覆盖新值。
     */
    private static Map<String, Object> collectChangedFields() {
        Map<String, Object> changed = new LinkedHashMap<>();
        for (Map.Entry<String, ModelFields> modelEntry : INSTANCE.modelFieldsMap.entrySet()) {
            for (ModelField<?> field : modelEntry.getValue().values()) {
                if (field == null || field.getCode() == null) {
                    continue;
                }
                String key = fieldKey(modelEntry.getKey(), field.getCode());
                String baseline = valueBaseline.get(key);
                if (baseline == null || baseline.equals(stableValueText(field.getValue()))) {
                    continue;
                }
                changed.put(key, copyFieldValue(field.getValue()));
            }
        }
        return changed;
    }

    /**
     * 落盘前把「磁盘上更新的内容」和「本进程自己的改动」合到一起：
     * 磁盘被别的进程改过时先重载，再把本进程改过的字段压回去，两边都不会丢。
     *
     * @return true 表示本进程没有可写内容（已采纳磁盘上的更新），调用方应跳过本次写入
     */
    private static boolean mergeDiskChanges(String userId) {
        try {
            File configV2File = StringUtil.isEmpty(userId) ? FileUtil.getDefaultConfigV2File() : FileUtil.getConfigV2File(userId);
            if (!configV2File.exists() || lastSyncedText == null) {
                return false;
            }
            String disk = FileUtil.readFromFile(configV2File);
            if (disk == null || disk.equals(lastSyncedText)) {
                return false;
            }
            Map<String, Object> changed = collectChangedFields();
            // 先重载，让本进程也看到别的进程刚写入的内容
            JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(disk);
            if (changed.isEmpty()) {
                // 本进程没有改动：磁盘上的才是最新状态，直接采纳，不能拿本进程的旧快照写回去
                lastSyncedText = disk;
                captureBaseline();
                Log.record("配置已被其他进程更新，本进程无改动，已重载并跳过写入");
                return true;
            }
            for (Map.Entry<String, Object> entry : changed.entrySet()) {
                int separator = entry.getKey().indexOf('|');
                ModelFields modelFields = INSTANCE.modelFieldsMap.get(entry.getKey().substring(0, separator));
                ModelField<?> field = modelFields == null ? null : modelFields.get(entry.getKey().substring(separator + 1));
                if (field != null) {
                    field.setObjectValue(entry.getValue());
                }
            }
            Log.record("配置已被其他进程更新，已合并本进程改动后再保存");
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private static String fieldKey(String modelCode, String fieldCode) {
        return modelCode + "|" + fieldCode;
    }

    /** 字段值的浅拷贝：集合/Map/KVNode 复制一份，避免重载时被就地改掉；其余按不可变类型处理 */
    private static Object copyFieldValue(Object value) {
        if (value instanceof Set) {
            return new LinkedHashSet<>((Set<?>) value);
        }
        if (value instanceof Map) {
            return new LinkedHashMap<>((Map<?, ?>) value);
        }
        if (value instanceof List) {
            return new ArrayList<>((List<?>) value);
        }
        if (value instanceof KVNode) {
            KVNode<?, ?> node = (KVNode<?, ?>) value;
            return new KVNode<>(node.getKey(), node.getValue());
        }
        return value;
    }

}
