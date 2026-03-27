package io.github.dragon813.sesame.data.modelFieldExt;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.dragon813.sesame.R;
import io.github.dragon813.sesame.data.ModelField;
import io.github.dragon813.sesame.ui.StringDialog;

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
