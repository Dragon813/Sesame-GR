package io.github.lazyimmortal.sesame.hook;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 精简版 XPath 解析器 - 仅支持基本查询
 * 支持的语法：
 * - //android.widget.TextView[contains(@text,'xxx')]
 * - //android.widget.TextView[@text='xxx']
 * - //android.widget.TextView[@contentDescription='xxx']
 */
public class SimpleXpathParser {
    private static final Pattern XPATH_PATTERN = Pattern.compile(
            "//([\\w.]+)\\[contains\\(@(\\w+),'([^']*)'\\)]",
            Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern XPATH_ATTR_PATTERN = Pattern.compile(
            "//([\\w.]+)\\[@(\\w+)='([^']*)']",
            Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern TAG_PATTERN = Pattern.compile(
            "//([\\w.]+)",
            Pattern.CASE_INSENSITIVE
    );
    
    private SimpleXpathParser() {
        // 私有构造函数，防止实例化工具类
    }
    
    /**
     * 解析并执行 XPath 查询
     */
    public static List<io.github.lazyimmortal.sesame.hook.SimpleViewImage> evaluate(io.github.lazyimmortal.sesame.hook.SimpleViewImage root, String xpath) {
        List<io.github.lazyimmortal.sesame.hook.SimpleViewImage> results = new ArrayList<>();
        
        // 匹配 contains 语法
        java.util.regex.Matcher matcher = XPATH_PATTERN.matcher(xpath);
        if (matcher.find()) {
            String className = matcher.group(1);
            String attrName = matcher.group(2);
            String attrValue = matcher.group(3);
            return findElements(root, className, attrName, attrValue, true);
        }
        
        // 匹配精确属性语法
        java.util.regex.Matcher attrMatcher = XPATH_ATTR_PATTERN.matcher(xpath);
        if (attrMatcher.find()) {
            String className = attrMatcher.group(1);
            String attrName = attrMatcher.group(2);
            String attrValue = attrMatcher.group(3);
            return findElements(root, className, attrName, attrValue, false);
        }
        
        // 匹配仅标签语法
        java.util.regex.Matcher tagMatcher = TAG_PATTERN.matcher(xpath);
        if (tagMatcher.find()) {
            String className = tagMatcher.group(1);
            return findElementsByTag(root, className);
        }
        
        return results;
    }
    
    /**
     * 查找匹配指定类名和属性的元素
     */
    private static List<io.github.lazyimmortal.sesame.hook.SimpleViewImage> findElements(
            io.github.lazyimmortal.sesame.hook.SimpleViewImage root,
            String className,
            String attrName,
            String attrValue,
            boolean contains
    ) {
        List<io.github.lazyimmortal.sesame.hook.SimpleViewImage> results = new ArrayList<>();
        findElementsRecursive(root, className, attrName, attrValue, contains, results);
        return results;
    }
    
    /**
     * 递归查找元素
     */
    private static void findElementsRecursive(
            io.github.lazyimmortal.sesame.hook.SimpleViewImage node,
            String className,
            String attrName,
            String attrValue,
            boolean contains,
            List<io.github.lazyimmortal.sesame.hook.SimpleViewImage> results
    ) {
        String nodeType = node.getType();
        
        // 匹配类名（支持通配符 *）
        if (nodeType.equals(className) || "*".equals(className)) {
            Object attrValueActualObj = node.attribute(attrName);
            if (attrValueActualObj != null) {
                String attrValueActual = attrValueActualObj.toString();
                boolean matches;
                if (contains) {
                    matches = attrValueActual.contains(attrValue);
                } else {
                    matches = attrValueActual.equals(attrValue);
                }
                if (matches) {
                    results.add(node);
                }
            }
        }
        
        // 递归遍历子节点
        for (io.github.lazyimmortal.sesame.hook.SimpleViewImage child : node.getChildren()) {
            findElementsRecursive(child, className, attrName, attrValue, contains, results);
        }
    }
    
    /**
     * 查找指定类名的元素
     */
    private static List<io.github.lazyimmortal.sesame.hook.SimpleViewImage> findElementsByTag(io.github.lazyimmortal.sesame.hook.SimpleViewImage root, String className) {
        List<io.github.lazyimmortal.sesame.hook.SimpleViewImage> results = new ArrayList<>();
        findElementsByTagRecursive(root, className, results);
        return results;
    }
    
    /**
     * 递归查找指定类名的元素
     */
    private static void findElementsByTagRecursive(
            io.github.lazyimmortal.sesame.hook.SimpleViewImage node,
            String className,
            List<io.github.lazyimmortal.sesame.hook.SimpleViewImage> results
    ) {
        // 匹配类名（支持通配符 *）
        if (node.getType().equals(className) || "*".equals(className)) {
            results.add(node);
        }
        
        // 递归遍历子节点
        for (io.github.lazyimmortal.sesame.hook.SimpleViewImage child : node.getChildren()) {
            findElementsByTagRecursive(child, className, results);
        }
    }
}