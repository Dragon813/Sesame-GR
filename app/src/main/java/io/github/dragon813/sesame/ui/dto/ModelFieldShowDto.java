package io.github.dragon813.sesame.ui.dto;

import lombok.Data;
import io.github.dragon813.sesame.data.ModelField;

import java.io.Serializable;

@Data
public class ModelFieldShowDto implements Serializable {

    private String code;

    private String name;

    private String type;

    private Object expandKey;

    private String configValue;

    private String description;

    public ModelFieldShowDto() {
    }

    public static ModelFieldShowDto toShowDto(ModelField<?> modelField) {
        ModelFieldShowDto dto = new ModelFieldShowDto();
        dto.setCode(modelField.getCode());
        dto.setName(modelField.getName());
        dto.setType(modelField.getType());
        dto.setExpandKey(modelField.getExpandKey());
        dto.setConfigValue(modelField.getConfigValue());
        dto.setDescription(modelField.getDescription());
        return dto;
    }

}
