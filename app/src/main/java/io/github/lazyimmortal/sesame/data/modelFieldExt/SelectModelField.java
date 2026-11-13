package io.github.lazyimmortal.sesame.data.modelFieldExt;


import android.content.Context;
import android.view.Gravity;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import io.github.lazyimmortal.sesame.R;
import io.github.lazyimmortal.sesame.data.ModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.common.SelectModelFieldFunc;
import io.github.lazyimmortal.sesame.entity.IdAndName;
import io.github.lazyimmortal.sesame.ui.ListDialog;

import java.util.List;
import java.util.Set;

/**
 * 数据结构说明
 * Set<String> 表示已选择的数据
 * List<? extends IdAndName> 需要选择的数据
 */
public class SelectModelField extends ModelField<Set<String>> implements SelectModelFieldFunc {

    private SelectListFunc selectListFunc;

    private List<? extends IdAndName> expandValue;

    public SelectModelField(String code, String name, Set<String> value, List<? extends IdAndName> expandValue) {
        super(code, name, value);
        this.expandValue = expandValue;
    }

    public SelectModelField(String code, String name, Set<String> value, SelectListFunc selectListFunc) {
        super(code, name, value);
        this.selectListFunc = selectListFunc;
    }

    public SelectModelField(String code, String name, Set<String> value, List<? extends IdAndName> expandValue, String description) {
        super(code, name, value, description);
        this.expandValue = expandValue;
    }

    public SelectModelField(String code, String name, Set<String> value, SelectListFunc selectListFunc, String description) {
        super(code, name, value, description);
        this.selectListFunc = selectListFunc;
    }

    @Override
    public String getType() {
        return "SELECT";
    }

    public List<? extends IdAndName> getExpandValue() {
        return selectListFunc == null ? expandValue : selectListFunc.getList();
    }

    @Override
    public View getView(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setBackground(ContextCompat.getDrawable(context, R.drawable.button));
        row.setMinimumHeight(150);
        row.setPaddingRelative(40, 0, 40, 0);
        row.setGravity(Gravity.CENTER_VERTICAL);
        
        TextView title = new TextView(context);
        title.setText(getName());
        title.setTextColor(ContextCompat.getColor(context, R.color.button));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        
        TextView count = new TextView(context);
        count.setTextColor(ContextCompat.getColor(context, R.color.button));
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        count.setText("已设置数：" + getValue().size());
        int gap = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, context.getResources().getDisplayMetrics());
        count.setPaddingRelative(gap, 0, 0, 0);
        
        row.addView(title);
        row.addView(count);
        row.setOnClickListener(v -> {
            ListDialog.setOnDismissCallback(() -> count.setText("已设置数：" + getValue().size()));
            ListDialog.show(v.getContext(), getName(), this);
        });
        return row;
    }

    @Override
    public void clear() {
        getValue().clear();
    }

    @Override
    public Integer get(String id) {
        return 0;
    }

    @Override
    public void add(String id, Integer count) {
        getValue().add(id);
    }

    @Override
    public void remove(String id) {
        getValue().remove(id);
    }

    @Override
    public Boolean contains(String id) {
        return getValue().contains(id);
    }

    public interface SelectListFunc {
        List<? extends IdAndName> getList();
    }
}
