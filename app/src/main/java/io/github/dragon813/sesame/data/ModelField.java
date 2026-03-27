package io.github.dragon813.sesame.data;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.Objects;

import io.github.dragon813.sesame.R;
import io.github.dragon813.sesame.util.JsonUtil;
import io.github.dragon813.sesame.util.ToastUtil;
import io.github.dragon813.sesame.util.TypeUtil;
import lombok.Data;

@Data
public class ModelField<T> implements Serializable {

    @JsonIgnore
    private final Type valueType;

    @JsonIgnore
    private String code;

    @JsonIgnore
    private String name;

    @JsonIgnore
    private String description;

    @JsonIgnore
    protected T defaultValue;

    protected volatile T value;

    public ModelField() {
        valueType = TypeUtil.getTypeArgument(this.getClass().getGenericSuperclass(), 0);
    }

    public ModelField(T value) {
        this(null, null, value);
    }

    public ModelField(String code, String name, T value) {
        this();
        this.code = code;
        this.name = name;
        this.defaultValue = value;
        this.description = null;
        setObjectValue(value);
    }

    public ModelField(String code, String name, T value, String description) {
        this();
        this.code = code;
        this.name = name;
        this.defaultValue = value;
        this.description = description;
        setObjectValue(value);
    }

    public void setObjectValue(Object objectValue) {
        if (objectValue == null) {
            reset();
            return;
        }
        value = JsonUtil.parseObject(objectValue, valueType);
    }

    @JsonIgnore
    public String getType() {
        return "DEFAULT";
    }

    @JsonIgnore
    public Object getExpandKey() {
        return null;
    }

    @JsonIgnore
    public Object getExpandValue() {
        return null;
    }

    public Object toConfigValue(T value) {
        return value;
    }

    public Object fromConfigValue(String value) {
        return value;
    }

    @JsonIgnore
    public String getConfigValue() {
        return JsonUtil.toJsonString(toConfigValue(value));
    }

    @JsonIgnore
    public void setConfigValue(String configValue) {
        if (configValue == null) {
            reset();
            return;
        }
        Object objectValue = fromConfigValue(configValue);
        if (Objects.equals(objectValue, configValue)) {
            value = JsonUtil.parseObject(configValue, valueType);
        } else {
            value = JsonUtil.parseObject(objectValue, valueType);
        }
    }

    public void reset() {
        value = defaultValue;
    }

    @JsonIgnore
    public View getView(Context context) {
        TextView btn = new TextView(context);
        btn.setText(getName());
        btn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btn.setTextColor(ContextCompat.getColor(context, R.color.button));
        btn.setBackground(ContextCompat.getDrawable(context, R.drawable.button));
        btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        btn.setMinHeight(150);
        btn.setMaxHeight(180);
        btn.setPaddingRelative(40, 0, 40, 0);
        btn.setAllCaps(false);
        btn.setOnClickListener(v -> ToastUtil.show(context, "无配置项"));
        return btn;
    }

}
