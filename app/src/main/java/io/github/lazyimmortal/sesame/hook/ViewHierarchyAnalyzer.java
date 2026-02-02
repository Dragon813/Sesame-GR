package io.github.lazyimmortal.sesame.hook;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * 用于分析和遍历视图层次结构的实用工具对象。
 */
public class ViewHierarchyAnalyzer {
    private static final String TAG = "ViewHierarchyAnalyzer";
    
    private ViewHierarchyAnalyzer() {
        // 私有构造函数，防止实例化工具类
    }
    
    /**
     * 递归记录视图层次结构以供调试。
     * @param view 开始分析的根视图。
     * @param depth 当前递归深度，用于格式化。
     */
    public static void logViewHierarchy(View view, int depth) {
        StringBuilder indentBuilder = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indentBuilder.append("  ");
        }
        String indent = indentBuilder.toString();
        
        String className = view.getClass().getName();
        String resourceId;
        try {
            resourceId = "ID: " + view.getResources().getResourceEntryName(view.getId());
        } catch (Exception e) {
            resourceId = "ID: (无)";
        }
        
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        String info = String.format("loc=[%d,%d] size=[%dx%d] visible=%b enabled=%b",
                location[0], location[1], view.getWidth(), view.getHeight(),
                view.isShown(), view.isEnabled());
        
        StringBuilder textInfo = new StringBuilder();
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textInfo.append("text='").append(textView.getText()).append("' desc='")
                    .append(textView.getContentDescription()).append("'");
        }
        
        Log.d(TAG, String.format("%s- %s, %s, %s %s", indent, className, resourceId, info, textInfo));
        
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                logViewHierarchy(child, depth + 1);
            }
        }
    }
    
    /**
     * 通过从锚点文本视图遍历，查找实际的滑块按钮视图。
     * 它在第一次尝试时记录层次结构以供调试。
     * @param slideTextView "滑动验证"文本视图的 SimpleViewImage 包装器。
     * @return 找到的滑块视图，如果未找到则返回 null。
     */
    public static View findActualSliderView(io.github.lazyimmortal.sesame.hook.SimpleViewImage slideTextView) {
        View originView = slideTextView.getOriginView();
        if (!(originView.getParent() instanceof ViewGroup)) {
            return null;
        }
        ViewGroup parentView = (ViewGroup) originView.getParent();
        
        Log.d(TAG, "========= 分析滑块父视图层次结构 =========");
        logViewHierarchy(parentView, 0);
        Log.d(TAG, "========= 视图层次结构分析结束 =========");
        
        // 在父容器内开始递归搜索滑块视图。
        View slider = findSliderInGroup(parentView);
        if (slider != null) {
            int[] loc = new int[2];
            slider.getLocationOnScreen(loc);
            Log.d(TAG, String.format("找到可拖动滑块视图: %s 位置=[%d,%d]",
                    slider.getClass().getName(), loc[0], loc[1]));
        } else {
            Log.e(TAG, "无法找到实际的滑块视图。请检查上面的层次结构日志。");
        }
        return slider;
    }
    
    /**
     * 在 ViewGroup 中递归搜索候选滑块视图。
     * 策略是找到一个可见的 ImageView（图标）并返回其父视图（实际可拖动的视图）。
     * @param viewGroup 要搜索的组。
     * @return 找到的滑块视图，或 null。
     */
    private static View findSliderInGroup(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            
            // 可拖动部分是可见 ImageView 图标的父视图。
            if (child instanceof ImageView && child.isShown()) {
                Log.d(TAG, "找到滑块图标 (ImageView)。返回其父视图作为可拖动视图。");
                if (child.getParent() instanceof View) {
                    return (View) child.getParent();
                }
                return null;
            }
            
            // 如果未找到，递归到子 ViewGroup 中。
            if (child instanceof ViewGroup) {
                View foundInChild = findSliderInGroup((ViewGroup) child);
                if (foundInChild != null) {
                    return foundInChild;
                }
            }
        }
        return null;
    }
}