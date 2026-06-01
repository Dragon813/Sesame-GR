package io.github.lazyimmortal.sesame.data.modelFieldExt;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.lazyimmortal.sesame.R;
import io.github.lazyimmortal.sesame.data.ModelField;
import io.github.lazyimmortal.sesame.ui.StringDialog;

import java.util.ArrayList;
import java.util.List;

public class ListModelField extends ModelField<List<String>> {

    private static final TypeReference<List<String>> typeReference = new TypeReference<List<String>>() {
    };

    public ListModelField(String code, String name, List<String> value) {
        super(code, name, value);
    }

    @Override
    public String getType() {
        return "LIST";
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

    public static class ListJoinCommaToStringModelField extends ListModelField {

        public ListJoinCommaToStringModelField(String code, String name, List<String> value) {
            super(code, name, value);
        }

        @Override
        public void setConfigValue(String configValue) {
            if (configValue == null) {
                reset();
                return;
            }
            List<String> list = new ArrayList<>();
            String[] split = configValue.split(",");
            if (split.length == 1) {
                String str = split[0];
                if (!str.isEmpty()) {
                    list.add(str);
                }
            } else {
                for (String str : split) {
                    if (!str.isEmpty()) {
                        list.add(str);
                    }
                }
            }
            value = list;
        }

        @Override
        public String getConfigValue() {
            return String.join(",", value);
        }
    }

}
