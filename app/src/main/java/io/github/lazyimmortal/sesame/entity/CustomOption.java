package io.github.lazyimmortal.sesame.entity;

import java.util.ArrayList;
import java.util.List;

public class CustomOption extends IdAndName {

    public CustomOption(String i, String n) {
        id = i;
        name = n;
    }

    public static List<CustomOption>
    getEcoLifeOptions() {
        List<CustomOption> list = new ArrayList<>();
        list.add(new CustomOption("tick", "绿色行动打卡"));
        list.add(new CustomOption("dish", "光盘行动打卡"));
        return list;
    }
    public static List<CustomOption>
    getContinuousUseCardOptions() {
        List<CustomOption> list = new ArrayList<>();
        list.add(new CustomOption("doubleClick", "使用限时双击卡和兑换使用31天双击卡"));
        list.add(new CustomOption("robExpandCard", "按现存最高倍使用收能量倍卡"));
        list.add(new CustomOption("shield", "兑换和使用限时保护罩卡"));
        list.add(new CustomOption("stealthCard", "兑换和使用隐身卡"));
        //list.add(new CustomOption("energyBombCard", "兑换和使用炸弹卡"));
        return list;
    }

    public static List<CustomOption> getUseAccelerateToolOptions() {
        List<CustomOption> list = new ArrayList<>();
        list.add(new CustomOption("useAccelerateToolContinue", "连续使用"));
        list.add(new CustomOption("useAccelerateToolWhenMaxEmotion", "仅在满状态时使用"));
        return list;
    }

    public static List<CustomOption> getAntFarmFamilyOptions() {
        List<CustomOption> list = new ArrayList<>();
        list.add(new CustomOption("familySign", "每日签到"));
        list.add(new CustomOption("familyFeed", "帮喂成员"));
        list.add(new CustomOption("familyEatTogether", "美食请客"));
        list.add(new CustomOption("familyClaimReward", "领取奖励"));
        //list.add(new CustomOption("deliverMsgSend", "道早安"));
        list.add(new CustomOption("assignRights", "顶梁柱"));
        return list;
    }

    public static List<CustomOption> getAntDodoPropList() {
        List<CustomOption> list = new ArrayList<>();
        list.add(new CustomOption("COLLECT_TIMES_7_DAYS", "抽卡道具"));
        list.add(new CustomOption("COLLECT_HISTORY_ANIMAL_7_DAYS", "历史图鉴随机卡道具"));
        list.add(new CustomOption("COLLECT_TO_FRIEND_TIMES_7_DAYS", "抽好友卡道具"));
        list.add(new CustomOption("UNIVERSAL_CARD_7_DAYS", "万能卡道具"));
        return list;
    }

    public static List<CustomOption> getAntDodoBookMedalOptions() {
        List<CustomOption> list = new ArrayList<>();
        list.add(new CustomOption("generateBookMedal", "自动合成图鉴勋章"));
        list.add(new CustomOption("collectHistoryAnimal", "自动收集历史物种"));
        return list;
    }

    public static List<CustomOption> getAntInsuranceOptions() {
        List<CustomOption> list = new ArrayList<>();
        list.add(new CustomOption("beanSignIn", "安心豆签到"));
        list.add(new CustomOption("beanExchangeGoldenTicket", "安心豆兑换黄金票"));
        list.add(new CustomOption("beanExchangeBubbleBoost", "安心豆兑换时光加速器"));
        list.add(new CustomOption("gainSumInsured", "保障金领取"));
        return list;
    }
}
