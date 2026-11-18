package io.github.Dragon813.sesame.data;

import java.util.LinkedHashMap;

public final class ModelFields extends LinkedHashMap<String, ModelField<?>> {


    public void addField(ModelField<?> modelField) {
        put(modelField.getCode(), modelField);
    }

}