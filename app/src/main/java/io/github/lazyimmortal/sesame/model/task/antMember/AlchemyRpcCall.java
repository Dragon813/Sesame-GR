package io.github.lazyimmortal.sesame.model.task.antMember;

import io.github.lazyimmortal.sesame.hook.ApplicationHook;

/**
 * 支付宝-芝麻炼金 RPC 封装
 * <p>
 * 接口命名空间 com.antgroup.zmxy.zmmemberop.biz.rpc.*，通过抓包 RPC.txt(2026-10-19) 分析得到。
 * 炼金消耗芝麻粒(alchemyCostZml=5粒/次)产出金币(goldNum)，每日上限alchemyDailyCap(25次)，
 * 炼金同时攒炼金金豆(上限100，满后由queryGoldenBean收取换金豆夺宝收益)。
 */
public class AlchemyRpcCall {

    /**
     * 炼金主页
     * 响应data: alchemyNum今日剩余次数/alchemyDailyCap每日上限/alchemyCostZml每次消耗芝麻粒/
     * zmlBalance芝麻粒余额/goldBalance金币余额/currentLevel等级/capReached已达每日上限/
     * staminaCurrent体力值/staminaExhausted体力耗尽/finishAlchemyCount今日已炼次数
     */
    public static String queryHome() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.queryHome", "[{}]");
    }

    /**
     * 炼金一次
     * 响应data: goldNum本次产出金币/alchemyNum今日累计次数/staminaCurrent剩余体力/
     * levelUp是否升级/free是否免费炼金/stageRewardConfigs阶段奖励配置
     */
    public static String alchemy() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.alchemy", "[{\"outerNewHand\":false}]");
    }

    /**
     * 炼金主页入口列表
     * 响应entryList: CHECK_IN_TASK签到/TIME_LIMITED_TASK限时补贴/ALCHEMY_STAGE_REWARD阶段奖励/
     * GAME_CENTER_TASK游戏奖励，ALCHEMY_STAGE_REWARD.nextDayAwardDTO.awardAvailable标识阶段奖励可领
     */
    public static String queryEntryList() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.queryEntryList", "[{\"version\":\"2025-10-22\"}]");
    }

    /**
     * 领取炼金阶段奖励(炼金次数达标后的次日礼包)
     */
    public static String claimAward() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.claimAward", "[{}]");
    }

    /**
     * 查询限时补贴任务(早/中/晚饭)
     * 响应data.timeLimitedTaskVO: templateId任务模板/startHour~endHour有效时段/state任务状态/rewardAmount奖励数量
     */
    public static String queryTimeLimitedTask() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.TimeLimitedTaskRpcManager.queryTask", "[{}]");
    }

    /**
     * 完成限时补贴任务
     *
     * @param templateId 任务模板id，取自queryTask响应timeLimitedTaskVO.templateId
     */
    public static String completeTimeLimitedTask(String templateId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.TimeLimitedTaskRpcManager.completeTask", "[{\"templateId\":\"" + templateId + "\"}]");
    }

    /**
     * 查询炼金宝箱
     * 响应data: hasBox是否有待开启宝箱(开启动作RPC未抓包到，暂仅查询占位)
     */
    public static String queryTreasureBox() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyTreasureBoxRpcManager.query", "[{}]");
    }

    /**
     * 查询炼金攒金豆进度
     * 响应data: pendingBeans待收金豆/maxAccumulated上限(100)/status(MAX_REACHED为已满可收，SPROUT生长中)
     */
    public static String queryGoldenBean() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.queryGoldenBean", "[{}]");
    }

    /**
     * 查询炼金可用道具（如BOTTLE瓶子，用后获得免耗/体力）
     * 响应data.items: [{itemId,itemType,...}]
     */
    public static String queryAvailableItems() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.ItemRpcManager.queryAvailableItems", "[{\"bizScene\":\"ALCHEMY\"}]");
    }

    /**
     * 使用炼金道具
     *
     * @param itemId   道具id，取自queryAvailableItems响应items[].itemId
     * @param itemType 道具类型（如BOTTLE）
     */
    public static String useItem(String itemId, String itemType) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.ItemRpcManager.useItem",
                "[{\"itemId\":\"" + itemId + "\",\"itemType\":\"" + itemType + "\"}]");
    }

    /**
     * 芝麻积分主页宝箱查询
     * 响应data: hasBox是否有宝箱/status(WAIT_CLAIM可领取，HANGING倒计时中)/rewardAmount奖励/countdownSeconds
     */
    public static String queryPointTreasureBox() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.PointHomeRpcManager.queryTreasureBox", "[{}]");
    }

    /**
     * 开启芝麻积分主页宝箱
     * 响应data: status(HANGING进入倒计时)/rewardAmount奖励/countdownSeconds下次可开秒数
     */
    public static String openPointTreasureBox() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.PointHomeRpcManager.openTreasureBox", "[{}]");
    }

    /**
     * 攒芝麻分：一键领取未领取的行为反馈
     * 请求{collectAll:true,status:"UNCLAIMED"}
     */
    public static String collectCreditFeedback() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.collectCreditFeedback",
                "[{\"collectAll\":true,\"status\":\"UNCLAIMED\"}]");
    }
}
