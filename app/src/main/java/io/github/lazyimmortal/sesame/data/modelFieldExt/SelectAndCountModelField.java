package io.github.lazyimmortal.sesame.data.modelFieldExt;


import android.content.Context;
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
import io.github.lazyimmortal.sesame.ui.ListDialog;

import java.util.List;
import java.util.Map;

/**
 * 数据结构说明
 * Map<String, Integer> 表示已选择的数据与已经设置的数量映射关系
 * List<? extends IdAndName> 需要选择的数据
 */
public class SelectAndCountModelField extends ModelField<Map<String, Integer>> implements SelectModelFieldFunc {

    private SelectListFunc selectListFunc;

    private List<? extends IdAndName> expandValue;

    public SelectAndCountModelField(String code, String name, Map<String, Integer> value, List<? extends IdAndName> expandValue) {
        super(code, name, value);
        this.expandValue = expandValue;
    }

    public SelectAndCountModelField(String code, String name, Map<String, Integer> value, SelectListFunc selectListFunc) {
        super(code, name, value);
        this.selectListFunc = selectListFunc;
    }

    public SelectAndCountModelField(String code, String name, Map<String, Integer> value, List<? extends IdAndName> expandValue, String description) {
        super(code, name, value, description);
        this.expandValue = expandValue;
    }

    public SelectAndCountModelField(String code, String name, Map<String, Integer> value, SelectListFunc selectListFunc, String description) {
        super(code, name, value, description);
        this.selectListFunc = selectListFunc;
    }

    @Override
    public String getType() {
        return "SELECT_AND_COUNT";
    }

    public List<? extends IdAndName> getExpandValue() {
        return selectListFunc == null ? expandValue : selectListFunc.getList();
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
        btn.setOnClickListener(v -> ListDialog.show(v.getContext(), ((Button) v).getText(), this));
        return btn;
    }

    @Override
    public void clear() {
        getValue().clear();
    }

    @Override
    public Integer get(String id) {
        return getValue().get(id);
    }

    @Override
    public void add(String id, Integer count) {
        getValue().put(id, count);
    }

    @Override
    public void remove(String id) {
        getValue().remove(id);
    }

    @Override
    public Boolean contains(String id) {
        return getValue().containsKey(id);
    }

        
        
        public interface SelectListFunc {
        List<? extends IdAndName> getList();
    }
}
