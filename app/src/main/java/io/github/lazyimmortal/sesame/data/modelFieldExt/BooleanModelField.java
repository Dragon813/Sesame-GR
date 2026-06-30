package io.github.lazyimmortal.sesame.data.modelFieldExt;


import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;

import androidx.core.content.ContextCompat;

import io.github.lazyimmortal.sesame.R;
import io.github.lazyimmortal.sesame.data.ModelField;

public class BooleanModelField extends ModelField<Boolean> {

    public BooleanModelField(String code, String name, Boolean value) {
        super(code, name, value);
    }

    @Override
    public String getType() {
        return "BOOLEAN";
    }

    @Override
    public View getView(Context context) {
        Switch sw = new Switch(context);
        sw.setText(getName());
        sw.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sw.setMinHeight((int) context.getResources().getDimension(R.dimen.miuix_button_min_height));
        sw.setPaddingRelative((int) context.getResources().getDimension(R.dimen.miuix_spacing_16), 0,
                (int) context.getResources().getDimension(R.dimen.miuix_spacing_16), 0);
        sw.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.miuix_text_body1));
        sw.setTextColor(ContextCompat.getColor(context, R.color.miuix_on_background));
        sw.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        sw.setThumbDrawable(ContextCompat.getDrawable(context, R.drawable.miuix_switch_thumb));
        sw.setTrackDrawable(ContextCompat.getDrawable(context, R.drawable.miuix_switch_track));
        sw.setSwitchMinWidth((int) (40 * context.getResources().getDisplayMetrics().density));
        sw.setSwitchPadding((int) (2 * context.getResources().getDisplayMetrics().density));
        sw.setChecked(getValue());
        sw.setOnClickListener(v -> setObjectValue(((Switch) v).isChecked()));
        return sw;
    }

}
