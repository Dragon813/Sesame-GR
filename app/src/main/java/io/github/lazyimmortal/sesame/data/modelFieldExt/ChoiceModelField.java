package io.github.lazyimmortal.sesame.data.modelFieldExt;


import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import io.github.lazyimmortal.sesame.R;
import io.github.lazyimmortal.sesame.data.ModelField;
import io.github.lazyimmortal.sesame.ui.ChoiceDialog;

public class ChoiceModelField extends ModelField<Integer> {

    private String[] choiceArray;

    public ChoiceModelField(String code, String name, Integer value) {
        super(code, name, value);
    }

    public ChoiceModelField(String code, String name, Integer value, String[] choiceArray) {
        super(code, name, value);
        this.choiceArray = choiceArray;
    }

    @Override
    public String getType() {
        return "CHOICE";
    }

    public String[] getExpandKey() {
        return choiceArray;
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
        btn.setOnClickListener(v -> ChoiceDialog.show(v.getContext(), ((Button) v).getText(), this));
        return btn;
    }

}
