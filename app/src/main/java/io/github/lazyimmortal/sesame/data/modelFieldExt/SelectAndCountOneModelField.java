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
import io.github.lazyimmortal.sesame.data.modelFieldExt.common.SelectModelFieldFunc;
import io.github.lazyimmortal.sesame.entity.IdAndName;
import io.github.lazyimmortal.sesame.entity.KVNode;
import io.github.lazyimmortal.sesame.ui.ListDialog;

import java.util.List;
import java.util.Objects;

public class SelectAndCountOneModelField extends ModelField<KVNode<String, Integer>> implements SelectModelFieldFunc {

    private SelectListFunc selectListFunc;

    private List<? extends IdAndName> expandValue;

    public SelectAndCountOneModelField(String code, String name, KVNode<String, Integer> value, List<? extends IdAndName> expandValue) {
        super(code, name, value);
        this.expandValue = expandValue;
    }

    public SelectAndCountOneModelField(String code, String name, KVNode<String, Integer> value, SelectListFunc selectListFunc) {
        super(code, name, value);
        this.selectListFunc = selectListFunc;
    }

    @Override
    public String getType() {
        return "SELECT_AND_COUNT_ONE";
    }

    public List<? extends IdAndName> getExpandValue() {
        return selectListFunc == null ? expandValue : selectListFunc.getList();
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
        btn.setOnClickListener(v -> ListDialog.show(v.getContext(), ((Button) v).getText(), this, ListDialog.ListType.RADIO));
        return btn;
    }

    @Override
    public void clear() {
        value = defaultValue;
    }

    @Override
    public Integer get(String id) {
        KVNode<String, Integer> kvNode = getValue();
        if (kvNode != null && Objects.equals(kvNode.getKey(), id)) {
            return kvNode.getValue();
        }
        return 0;
    }

    @Override
    public void add(String id, Integer count) {
        value = new KVNode<>(id, count);
    }

    @Override
    public void remove(String id) {
        KVNode<String, Integer> kvNode = getValue();
        if (kvNode != null && Objects.equals(kvNode.getKey(), id)) {
            value = defaultValue;
        }
    }

    @Override
    public Boolean contains(String id) {
        KVNode<String, Integer> kvNode = getValue();
        return kvNode != null && Objects.equals(kvNode.getKey(), id);
    }

    public interface SelectListFunc {
        List<? extends IdAndName> getList();
    }
}