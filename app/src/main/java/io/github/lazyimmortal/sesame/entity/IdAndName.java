package io.github.lazyimmortal.sesame.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import io.github.lazyimmortal.sesame.util.HanziToPinyin;

import java.util.ArrayList;
import java.util.List;

public abstract class IdAndName implements Comparable<IdAndName> {

    @Getter
    public String name;

    @Getter
    public String id;

    /**
     * 任务图标地址（仅任务清单类实体使用；null 表示无图标，Jackson NON_NULL 不参与序列化）
     */
    @Getter
    public String icon;

    private ArrayList<String> pinyin;

    @JsonIgnore
    public ArrayList<String> getPinyin() {
        if (pinyin != null) {
            return pinyin;
        }
        ArrayList<HanziToPinyin.Token> list = HanziToPinyin.getInstance().get(name);
        pinyin = new ArrayList<>(list.size());
        for (HanziToPinyin.Token token : list) {
            pinyin.add(token.target);
        }
        return pinyin;
    }

    /**
     * 无图标时的单字占位: 取标题中第一个数字/字母/中文原字符(跳过标点等符号);
     * 英文/数字统一大写, 中文直接显示原字; 整串都不是这些字符时才退回第一个字符。有图标时返回空串。
     */
    @JsonProperty("iconText")
    public String getIconText() {
        if (icon != null && !icon.isEmpty()) {
            return "";
        }
        return iconTextOf(name);
    }

    /**
     * 按统一规则从文本取单字占位, 供未继承 icon 语义的列表(如好友统计)复用
     */
    public static String iconTextOf(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                return c < 128 ? String.valueOf(Character.toUpperCase(c)) : String.valueOf(c);
            }
        }
        //全部都不是数字/字母/中文, 退回第一个字符
        return String.valueOf(text.charAt(0));
    }

    @Override
    public int compareTo(IdAndName o) {
        List<String> list1 = this.getPinyin();
        List<String> list2 = o.getPinyin();
        int i = 0;
        while (i < list1.size() && i < list2.size()) {
            if (list1.get(i).compareTo(list2.get(i)) != 0) {
                return list1.get(i).compareTo(list2.get(i));
            }
            i++;
        }
        return list1.size() - list2.size();
    }
}
