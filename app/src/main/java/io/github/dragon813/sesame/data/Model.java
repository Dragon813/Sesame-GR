package io.github.dragon813.sesame.data;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.dragon813.sesame.data.modelFieldExt.BooleanModelField;
import io.github.dragon813.sesame.data.task.ModelTask;
import io.github.dragon813.sesame.model.base.ModelOrder;
import io.github.dragon813.sesame.util.Log;
import lombok.Getter;

public abstract class Model {

    private static final Map<String, ModelConfig> modelConfigMap = new LinkedHashMap<>();

    private static final Map<String, ModelConfig> readOnlyModelConfigMap = Collections.unmodifiableMap(modelConfigMap);

    private static final Map<ModelGroup, Map<String, ModelConfig>> groupModelConfigMap = new LinkedHashMap<>();

    private static final Map<Class<? extends Model>, Model> modelMap = new ConcurrentHashMap<>();

    private static final List<Class<? extends Model>> modelClazzList = ModelOrder.getClazzList();

    @Getter
    private static final Model[] modelArray = new Model[modelClazzList.size()];

    private static final List<Model> modelList = new LinkedList<>(Arrays.asList(modelArray));

    private static final List<Model> readOnlyModelList = Collections.unmodifiableList(modelList);

    private final BooleanModelField enableField;

    public final BooleanModelField getEnableField() {
        return enableField;
    }

    public Model() {
        this.enableField = new BooleanModelField("enable", getEnableFieldName(), false);
    }

    public String getEnableFieldName() {
        return "开启" + getName();
    }

    public final Boolean isEnable() {
        return enableField.getValue();
    }

    public ModelType getType() {
        return ModelType.NORMAL;
    }

    public abstract String getName();

    public abstract ModelGroup getGroup();

    public abstract ModelFields getFields();

    public void prepare() {}

    public void boot(ClassLoader classLoader) {}

    public void destroy() {}

    public static Map<String, ModelConfig> getModelConfigMap() {
        return readOnlyModelConfigMap;
    }

    public static Set<ModelGroup> getGroupModelConfigGroupSet() {
        return groupModelConfigMap.keySet();
    }

    public static List<Map<String, ModelConfig>> getGroupModelConfigMapList() {
        List<Map<String, ModelConfig>> list = new ArrayList<>();
        for (Map<String, ModelConfig> modelConfigMap : groupModelConfigMap.values()) {
            list.add(Collections.unmodifiableMap(modelConfigMap));
        }
        return list;
    }

    public static Map<String, ModelConfig> getGroupModelConfig(ModelGroup modelGroup) {
        Map<String, ModelConfig> map = groupModelConfigMap.get(modelGroup);
        if (map == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(map);
    }

    public static Boolean hasModel(Class<? extends Model> modelClazz) {
        return modelMap.containsKey(modelClazz);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Model> T getModel(Class<T> modelClazz) {
        return (T) modelMap.get(modelClazz);
    }

    public static List<Model> getModelList() {
        return readOnlyModelList;
    }

    public static synchronized void initAllModel() {
        destroyAllModel();
        for (int i = 0, len = modelClazzList.size(); i < len; i++) {
            Class<? extends Model> modelClazz = modelClazzList.get(i);
            try {
                Constructor<? extends Model> constructor = modelClazz.getConstructor();
                Model model = constructor.newInstance();
                ModelConfig modelConfig = new ModelConfig(model);
                modelArray[i] = model;
                modelMap.put(modelClazz, model);
                String modelCode = modelConfig.getCode();
                modelConfigMap.put(modelCode, modelConfig);
                ModelGroup group = modelConfig.getGroup();
                Map<String, ModelConfig> modelConfigMap = groupModelConfigMap.get(group);
                if (modelConfigMap == null) {
                    modelConfigMap = new LinkedHashMap<>();
                    groupModelConfigMap.put(group, modelConfigMap);
                }
                modelConfigMap.put(modelCode, modelConfig);
            } catch (ReflectiveOperationException e) {
                Log.printStackTrace(e);
            }
        }
    }

    public static synchronized void bootAllModel(ClassLoader classLoader) {
        for (Model model : modelArray) {
            try {
                model.prepare();
            } catch (Exception e) {
                Log.printStackTrace(e);
            }
            try {
                if (model.getEnableField().getValue()) {
                    model.boot(classLoader);
                }
            } catch (Exception e) {
                Log.printStackTrace(e);
            }
        }
    }

    public static synchronized void destroyAllModel() {
        for (int i = 0, len = modelArray.length; i < len; i++) {
            Model model = modelArray[i];
            if (model != null) {
                try {
                    if (ModelType.TASK == model.getType()) {
                        ((ModelTask) model).stopTask();
                    }
                    model.destroy();
                } catch (Exception e) {
                    Log.printStackTrace(e);
                }
                modelArray[i] = null;
            }
            modelMap.clear();
            modelConfigMap.clear();
        }
    }

}
