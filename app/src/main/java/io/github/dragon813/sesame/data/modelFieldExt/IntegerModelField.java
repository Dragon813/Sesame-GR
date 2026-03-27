package io.github.dragon813.sesame.data.modelFieldExt;


import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import lombok.Getter;
import io.github.dragon813.sesame.R;
import io.github.dragon813.sesame.data.ModelField;
import io.github.dragon813.sesame.ui.StringDialog;
import io.github.dragon813.sesame.util.Log;

@Getter
public class IntegerModelField extends ModelField<Integer> {

    protected final Integer minLimit;

    protected final Integer maxLimit;

    public IntegerModelField(String code, String name, Integer value) {
        super(code, name, value);
        this.minLimit = null;
        this.maxLimit = null;
    }

    public IntegerModelField(String code, String name, Integer value, Integer minLimit, Integer maxLimit) {
        super(code, name, value);
        this.minLimit = minLimit;
        this.maxLimit = maxLimit;
    }

    @Override
    public String getType() {
        return "INTEGER";
    }

    @Override
    public String getConfigValue() {
        return String.valueOf(value);
    }

    @Override
    public void setConfigValue(String configValue) {
        Integer newValue;
        if (configValue == null) {
            newValue = defaultValue;
        } else {
            try {
                newValue = Integer.parseInt(configValue);
            } catch (Exception e) {
                Log.printStackTrace(e);
                newValue = defaultValue;
            }
        }
        if (minLimit != null) {
            newValue = Math.max(minLimit, newValue);
        }
        if (maxLimit != null) {
            newValue = Math.min(maxLimit, newValue);
        }
        this.value = newValue;
    }

    @Override
    public View getView(Context context) {
        Button btn = new Button(context);
        btn.setText(getName());
        btn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btn.setTextColor(ContextCompat.getColor(context, R.color.button));
        btn.setBackground(ContextCompat.getDrawable(context, R.drawable.button));
        btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        btn.setMinHeight(150);
        btn.setMaxHeight(180);
        btn.setPaddingRelative(40, 0, 40, 0);
        btn.setAllCaps(false);
        btn.setOnClickListener(v -> StringDialog.showEditDialog(v.getContext(), ((Button) v).getText(), this));
        return btn;
    }

    @Getter
    public static class MultiplyIntegerModelField extends IntegerModelField {

        private final Integer multiple;

        public MultiplyIntegerModelField(String code, String name, Integer value, Integer minLimit, Integer maxLimit, Integer multiple) {
            super(code, name, value * multiple, minLimit, maxLimit);
            this.multiple = multiple;
        }

        @Override
        public String getType() {
            return "MULTIPLY_INTEGER";
        }

        @Override
        public void setConfigValue(String configValue) {
            if (configValue == null) {
                reset();
                return;
            }
            super.setConfigValue(configValue);
            try {
                value = value * multiple;
                return;
            } catch (Exception e) {
                Log.printStackTrace(e);
            }
            reset();
        }

        @Override
        public String getConfigValue() {
            return String.valueOf(value / multiple);
        }

    }

}
