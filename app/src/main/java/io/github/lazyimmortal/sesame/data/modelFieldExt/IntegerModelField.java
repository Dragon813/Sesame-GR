package io.github.lazyimmortal.sesame.data.modelFieldExt;


import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import lombok.Getter;
import io.github.lazyimmortal.sesame.R;
import io.github.lazyimmortal.sesame.data.ModelField;
import io.github.lazyimmortal.sesame.ui.StringDialog;
import io.github.lazyimmortal.sesame.util.Log;

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
        btn.setTextColor(ContextCompat.getColor(context, R.color.miuix_button_text_text));
        btn.setBackground(ContextCompat.getDrawable(context, R.drawable.miuix_button_text_bg));
        btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        btn.setMinHeight((int) context.getResources().getDimension(R.dimen.miuix_button_min_height));
        btn.setPaddingRelative((int) context.getResources().getDimension(R.dimen.miuix_spacing_16), 0,
                (int) context.getResources().getDimension(R.dimen.miuix_spacing_16), 0);
        btn.setAllCaps(false);
        btn.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(R.dimen.miuix_text_body1));
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
