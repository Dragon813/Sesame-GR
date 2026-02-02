package io.github.lazyimmortal.sesame.hook;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * 精简版 ViewImage - 仅保留坐标获取和 XPath 查找功能
 */
public class SimpleViewImage {
    public static final String TEXT = "text";
    public static final String CONTENT_DESCRIPTION = "contentDescription";
    
    private final View originView;
    private SimpleViewImage parent;
    private int indexOfParent = -1;
    private SimpleViewImage[] children;
    
    public SimpleViewImage(View originView) {
        this.originView = originView;
    }
    
    /**
     * 获取文本内容
     */
    public String getText() {
        if (originView instanceof TextView) {
            CharSequence text = ((TextView) originView).getText();
            return text != null ? text.toString() : null;
        } else {
            CharSequence contentDesc = originView.getContentDescription();
            return contentDesc != null ? contentDesc.toString() : null;
        }
    }
    
    /**
     * 获取屏幕坐标
     */
    public int[] locationOnScreen() {
        int[] location = new int[2];
        originView.getLocationOnScreen(location);
        return location;
    }
    
    /**
     * 获取X坐标
     */
    public int X() {
        return locationOnScreen()[0];
    }
    
    /**
     * 获取Y坐标
     */
    public int Y() {
        return locationOnScreen()[1];
    }
    
    /**
     * 获取子节点数量
     */
    public int childCount() {
        if (!(originView instanceof ViewGroup)) {
            return 0;
        }
        return ((ViewGroup) originView).getChildCount();
    }
    
    /**
     * 获取指定索引的子节点
     */
    public SimpleViewImage childAt(int index) {
        if (childCount() < 0) {
            throw new IllegalStateException("can not parse child node for none ViewGroup object!!");
        }
        if (children == null) {
            children = new SimpleViewImage[childCount()];
        }
        SimpleViewImage viewImage = children[index];
        if (viewImage != null) {
            return viewImage;
        }
        ViewGroup viewGroup = (ViewGroup) originView;
        viewImage = new SimpleViewImage(viewGroup.getChildAt(index));
        viewImage.parent = this;
        viewImage.indexOfParent = index;
        children[index] = viewImage;
        return viewImage;
    }
    
    /**
     * 获取父节点
     */
    public SimpleViewImage getParentNode() {
        return parent;
    }
    
    /**
     * 获取指定层级的父节点
     */
    public SimpleViewImage getParentNode(int n) {
        if (n == 1) {
            return getParentNode();
        }
        SimpleViewImage parentNode = getParentNode();
        return parentNode != null ? parentNode.getParentNode(n - 1) : null;
    }
    
    /**
     * 获取所有子节点
     */
    public List<SimpleViewImage> getChildren() {
        int count = childCount();
        if (count <= 0) {
            return new ArrayList<>();
        }
        List<SimpleViewImage> ret = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ret.add(childAt(i));
        }
        return ret;
    }
    
    /**
     * 根据XPath查找单个元素
     */
    public SimpleViewImage xpath2One(String xpath) {
        List<SimpleViewImage> results = io.github.lazyimmortal.sesame.hook.SimpleXpathParser.evaluate(this, xpath);
        if (!results.isEmpty()) {
            return results.get(0);
        }
        return io.github.lazyimmortal.sesame.hook.SimplePageManager.tryGetTopView(xpath);
    }
    
    /**
     * 获取视图类型
     */
    public String getType() {
        return originView.getClass().getSimpleName();
    }
    
    /**
     * 获取属性值
     */
    public Object attribute(String key) {
        switch (key) {
            case TEXT:
                return getText();
            case CONTENT_DESCRIPTION:
                CharSequence contentDesc = originView.getContentDescription();
                return contentDesc != null ? contentDesc.toString() : null;
            default:
                return null;
        }
    }
    
    // Getter & Setter
    public View getOriginView() {
        return originView;
    }
    
    public int getIndexOfParent() {
        return indexOfParent;
    }
    
    public void setParent(SimpleViewImage parent) {
        this.parent = parent;
    }
    
    public void setIndexOfParent(int indexOfParent) {
        this.indexOfParent = indexOfParent;
    }
}