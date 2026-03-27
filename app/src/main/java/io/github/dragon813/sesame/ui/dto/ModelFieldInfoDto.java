package io.github.dragon813.sesame.ui.dto;

import lombok.Data;
import io.github.dragon813.sesame.data.ModelField;

import java.io.Serializable;

@Data
public class ModelFieldInfoDto implements Serializable {

    private String code;

    private String name;

    private String type;

    private Object expandKey;

    private Object expandValue;

    private String configValue;

    private String description;

    public ModelFieldInfoDto() {
    }

    public static ModelFieldInfoDto toInfoDto(ModelField<?> modelField) {
        ModelFieldInfoDto dto = new ModelFieldInfoDto();
        dto.setCode(modelField.getCode());
        dto.setName(modelField.getName());
        dto.setType(modelField.getType());
        dto.setExpandKey(modelField.getExpandKey());
        dto.setExpandValue(modelField.getExpandValue());
        dto.setConfigValue(modelField.getConfigValue());
        dto.setDescription(modelField.getDescription());
        return dto;
    }

}
