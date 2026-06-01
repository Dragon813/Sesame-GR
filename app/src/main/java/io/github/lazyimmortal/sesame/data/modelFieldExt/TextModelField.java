package io.github.lazyimmortal.sesame.data.modelFieldExt;


import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.lazyimmortal.sesame.R;
import io.github.lazyimmortal.sesame.data.ModelField;
import io.github.lazyimmortal.sesame.ui.HtmlViewerActivity;
import io.github.lazyimmortal.sesame.ui.StringDialog;

public class TextModelField extends ModelField<String> {

    public TextModelField(String code, String name, String value) {
        super(code, name, value);
    }

    @Override
    public String getType() {
        return "TEXT";
    }

    @Override
    public String getConfigValue() {
        return value;
    }

    @Override
    public void setConfigValue(String configValue) {
        value = configValue;
    }

    @JsonIgnore
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
        btn.setOnClickListener(v -> StringDialog.showReadDialog(v.getContext(), ((Button) v).getText(), this));
        return btn;
    }

    public static class ReadOnlyTextModelField extends TextModelField {

        public ReadOnlyTextModelField(String code, String name, String value) {
            super(code, name, value);
        }

        @Override
        public String getType() {
            return "READ_TEXT";
        }

        @Override
        public String getValue() {
            return null;
        }

        @Override
        public void setConfigValue(String configValue) {
        }

    }

    public static class UrlTextModelField extends ReadOnlyTextModelField {

        public UrlTextModelField(String code, String name, String value) {
            super(code, name, value);
        }

        @Override
        public String getType() {
            return "URL_TEXT";
        }

        @JsonIgnore
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
            btn.setOnClickListener(v -> {
                Context innerContext = v.getContext();
                Intent it = new Intent(innerContext, HtmlViewerActivity.class);
                it.setData(Uri.parse(getConfigValue()));
                innerContext.startActivity(it);
            });
            return btn;
        }

    }

}
