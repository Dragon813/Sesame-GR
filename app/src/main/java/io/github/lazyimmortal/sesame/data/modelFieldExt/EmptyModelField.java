package io.github.lazyimmortal.sesame.data.modelFieldExt;

import android.app.AlertDialog;
import android.content.Context;
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
import io.github.lazyimmortal.sesame.util.ToastUtil;

public class EmptyModelField extends ModelField<Object> {

    private final Runnable clickRunner;

    public EmptyModelField(String code, String name) {
        super(code, name, null);
        this.clickRunner = null;
    }

    public EmptyModelField(String code, String name, Runnable clickRunner) {
        super(code, name, null);
        this.clickRunner = clickRunner;
    }

    @Override
    public String getType() {
        return "EMPTY";
    }

    @Override
    public void setObjectValue(Object value) {
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
        if (clickRunner != null) {
            btn.setOnClickListener(v -> new AlertDialog.Builder(context)
                    .setTitle("警告")
                    .setMessage("确认执行该操作？")
                    .setPositiveButton(R.string.ok, (dialog, id) -> clickRunner.run())
                    .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.dismiss())
                    .create()
                    .show());
        } else {
            btn.setOnClickListener(v -> ToastUtil.show(context, "无配置项"));
        }
        return btn;
    }

}
