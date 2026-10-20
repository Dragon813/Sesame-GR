package io.github.lazyimmortal.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.github.lazyimmortal.sesame.data.ConfigV2;
import io.github.lazyimmortal.sesame.data.ModelFields;

import io.github.lazyimmortal.sesame.data.ModelGroup;
import io.github.lazyimmortal.sesame.data.modelFieldExt.BooleanModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.IntegerModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectModelField;
import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.entity.AlipayAntMemberTaskList;
import io.github.lazyimmortal.sesame.entity.AlipayMemberCreditSesameTaskList;
import io.github.lazyimmortal.sesame.entity.MemberBenefit;
import io.github.lazyimmortal.sesame.entity.PromiseSimpleTemplate;
import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.model.base.TaskCommon;
import io.github.lazyimmortal.sesame.model.extensions.ExtensionsHandle;
import io.github.lazyimmortal.sesame.model.task.antOrchard.AntOrchardRpcCall;
import io.github.lazyimmortal.sesame.util.*;
import io.github.lazyimmortal.sesame.util.idMap.AntFarmDoFarmTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.AntMemberTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.MemberBenefitIdMap;
import io.github.lazyimmortal.sesame.util.idMap.MemberCreditSesameTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.PromiseSimpleTemplateIdMap;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class AntMember extends ModelTask {
    private static final String TAG = AntMember.class.getSimpleName();
    
    @Override
    public String getName() {
        return "会员";
    }
    
    @Override
    public ModelGroup getGroup() {
        return ModelGroup.MEMBER;
    }
    
    private BooleanModelField AntMemberTask;
    private BooleanModelField AutoAntMemberTaskList;
    private SelectModelField AntMemberTaskList;
    private BooleanModelField memberSign;
    private BooleanModelField memberPointExchangeBenefit;
    private SelectModelField memberPointExchangeBenefitList;
    
    private BooleanModelField collectSesame;
    private BooleanModelField AutoMemberCreditSesameTaskList;
    private BooleanModelField sesameAlchemyGrainTask;
    private SelectModelField MemberCreditSesameTaskList;
    private BooleanModelField SesameGrowthBehavior;
    private BooleanModelField promise;
    private SelectModelField promiseList;
    private BooleanModelField KuaiDiFuLiJia;
    private BooleanModelField antInsurance;
    private SelectModelField antInsuranceOptions;
    private BooleanModelField signinCalendar;
    private BooleanModelField enableGoldTicket;
    private BooleanModelField enableGameCenter;
    private BooleanModelField merchantSignIn;
    private BooleanModelField merchantKMDK;
    private BooleanModelField sesameAlchemy;
    private BooleanModelField sesameAlchemySign;
    private BooleanModelField sesameAlchemyGoldenBean;
    private BooleanModelField sesameAlchemyBeanExchange;
    private IntegerModelField sesameAlchemyBeanLimit;
    private IntegerModelField sesameAlchemyReserveZml;
    private BooleanModelField pointTreasureBox;
    
    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(AntMemberTask = new BooleanModelField("AntMemberTask", "会员任务", false));
        modelFields.addField(AutoAntMemberTaskList = new BooleanModelField("AutoAntMemberTaskList", "会员任务 | 推荐黑白名单", true));
        modelFields.addField(AntMemberTaskList = new SelectModelField("AntMemberTaskList", "会员任务 | 黑名单列表", new LinkedHashSet<>(), AlipayAntMemberTaskList::getList));
        modelFields.addField(memberSign = new BooleanModelField("memberSign", "会员签到", false));
        modelFields.addField(memberPointExchangeBenefit = new BooleanModelField("memberPointExchangeBenefit", "会员积分 | 兑换权益", false));
        modelFields.addField(memberPointExchangeBenefitList = new SelectModelField("memberPointExchangeBenefitList", "会员积分 | 权益列表", new LinkedHashSet<>(), MemberBenefit::getList));
        modelFields.addField(collectSesame = new BooleanModelField("collectSesame", "芝麻粒 | 芝麻粒任务", false));
        modelFields.addField(sesameAlchemyGrainTask = new BooleanModelField("sesameAlchemyGrainTask", "芝麻粒 | 芝麻炼金任务", false));
        modelFields.addField(AutoMemberCreditSesameTaskList = new BooleanModelField("AutoMemberCreditSesameTaskList", "芝麻粒任务 | 推荐黑白名单", true));
        modelFields.addField(MemberCreditSesameTaskList = new SelectModelField("MemberCreditSesameTaskList", "芝麻粒任务 | 黑名单列表", new LinkedHashSet<>(), AlipayMemberCreditSesameTaskList::getList));
        modelFields.addField(SesameGrowthBehavior = new BooleanModelField("SesameGrowthBehavior", "攒芝麻分进度", false));
        modelFields.addField(sesameAlchemy = new BooleanModelField("sesameAlchemy", "芝麻炼金 | 消耗芝麻粒炼金", false));
        modelFields.addField(sesameAlchemySign = new BooleanModelField("sesameAlchemySign", "芝麻炼金 | 签到", false));
        modelFields.addField(sesameAlchemyGoldenBean = new BooleanModelField("sesameAlchemyGoldenBean", "炼金金豆 | 任务签到领奖", false));
        modelFields.addField(sesameAlchemyBeanExchange = new BooleanModelField("sesameAlchemyBeanExchange", "炼金金豆 | 芝麻粒换金豆", false));
        modelFields.addField(sesameAlchemyBeanLimit = new IntegerModelField("sesameAlchemyBeanLimit", "炼金金豆 | 当日兑换芝麻粒上限(0不限制)", 0, 0, null));
        modelFields.addField(sesameAlchemyReserveZml = new IntegerModelField("sesameAlchemyReserveZml", "炼金金豆 | 剩余芝麻粒下限(0不限制)", 0, 0, null));
        modelFields.addField(pointTreasureBox = new BooleanModelField("pointTreasureBox", "芝麻积分 | 宝箱", false));
        modelFields.addField(promise = new BooleanModelField("promise", "生活记录 | 坚持做", false));
        modelFields.addField(promiseList = new SelectModelField("promiseList", "生活记录 | 坚持做列表", new LinkedHashSet<>(), PromiseSimpleTemplate::getList));
        modelFields.addField(enableGameCenter = new BooleanModelField("enableGameCenter", "游戏中心 | 得乐园豆", false));
        modelFields.addField(KuaiDiFuLiJia = new BooleanModelField("KuaiDiFuLiJia", "我的快递 | 福利加", false));
        //modelFields.addField(antInsurance = new BooleanModelField("antInsurance", "蚂蚁保 | 开启", false));
        //modelFields.addField(antInsuranceOptions = new SelectModelField("antInsuranceOptions", "蚂蚁保 | 选项", new LinkedHashSet<>(), CustomOption::getAntInsuranceOptions));
        modelFields.addField(signinCalendar = new BooleanModelField("signinCalendar", "消费金 | 签到", false));
        modelFields.addField(enableGoldTicket = new BooleanModelField("enableGoldTicket", "黄金票 | 签到", false));
        modelFields.addField(merchantSignIn = new BooleanModelField("merchantSignIn", "商家服务 | 签到", false));
        modelFields.addField(merchantKMDK = new BooleanModelField("merchantKMDK", "商家服务 | 开门打卡", false));
        return modelFields;
    }
    
    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.other("任务暂停⏸️蚂蚁会员:当前为仅收能量时间");
            return false;
        }
        return true;
    }
    
    @Override
    public void run() {
        try {
            //初始任务列表
            if (!Status.hasFlagToday("BlackList::initMember")) {
                initMemberTaskListMap(AutoAntMemberTaskList.getValue(), AutoMemberCreditSesameTaskList.getValue(), AntMemberTask.getValue(), sesameAlchemyGrainTask.getValue());
                Status.flagToday("BlackList::initMember");
            }
            
            if (memberSign.getValue()) {
                memberSign();
            }
            
            if (AntMemberTask.getValue()) {
                queryPointCert(1, 8);
                //signPageTaskList();
                queryAllStatusTaskList();
            }
            
            if (memberPointExchangeBenefit.getValue()) {
                memberPointExchangeBenefit();
            }
            if (collectSesame.getValue()) {
                CheckInTaskRpcManager();
                collectSesame();
                // 芝麻信用信息记录(仅开启"芝麻粒|领取"时查询)
                sesameInfo();
            }
            // 攒粒任务（炼金页queryListV3 version=alchemy，独立开关）
            if (sesameAlchemyGrainTask.getValue()) {
                alchemySesameTasks();
            }
            
            //芝麻积攒进度
            if (SesameGrowthBehavior.getValue()) {
                // if (!Status.hasFlagToday("AntMember::SesameGrowthBehavior")) {
                //完成攒进度任务
                handleGrowthGuideTasks();
                //领取进度球
                queryAndCollect();
                //一键领取未领取的行为反馈
                collectCreditFeedback();
                //    Status.flagToday("AntMember::SesameGrowthBehavior");
                //}

            }
            // 生活记录
            if (promise.getValue()) {
                promise();
            }
            // 我的快递任务
            if (KuaiDiFuLiJia.getValue()) {
                RecommendTask();
                OrdinaryTask();
            }
            if (enableGoldTicket.getValue()) {
                goldTicket();
            }
            //if (antInsurance.getValue()) {
            //    AntInsurance.executeTask(antInsuranceOptions.getValue());
            //}
            // 消费金签到
            if (signinCalendar.getValue()) {
                signinCalendar();
            }
            if (enableGameCenter.getValue()) {
                //检查并执行签到
                checkAndDoSignIn();
                //查询并处理任务列表
                queryAndProcessTaskList();
                
                //查询玩乐豆小球列表，有则领取
                queryPointBallList();
                
            }
            if (merchantSignIn.getValue() || merchantKMDK.getValue()) {
                if (MerchantService.transcodeCheck()) {
                    if (merchantSignIn.getValue()) {
                        MerchantService.taskListQueryV2();
                    }
                    if (merchantKMDK.getValue()) {
                        MerchantService.merchantKMDK();
                    }
                }
            }
            //芝麻炼金 + 炼金金豆夺宝(任务黑名单与农场金豆夺宝共用GoldenBeansTaskList)
            if (sesameAlchemy.getValue() || sesameAlchemySign.getValue()
                    || sesameAlchemyGoldenBean.getValue() || sesameAlchemyBeanExchange.getValue() || pointTreasureBox.getValue()) {
                Set<String> goldenBeanList = null;
                ModelFields orchardFields = ConfigV2.INSTANCE.getModelFieldsMap().get("AntOrchard");
                SelectModelField goldenBeansTaskList = orchardFields == null ? null : (SelectModelField) orchardFields.get("GoldenBeansTaskList");
                if (goldenBeansTaskList != null) {
                    goldenBeanList = goldenBeansTaskList.getValue();
                }
                SesameAlchemy.run(sesameAlchemy.getValue(), sesameAlchemySign.getValue(),
                        sesameAlchemyGoldenBean.getValue(), goldenBeanList,
                        sesameAlchemyBeanExchange.getValue(), sesameAlchemyBeanLimit.getValue(), sesameAlchemyReserveZml.getValue(),
                        pointTreasureBox.getValue());
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }
    
    public static void initMemberTaskListMap(boolean AutoAntMemberTaskList, boolean AutoMemberCreditSesameTaskList, boolean AntMemberTask, boolean sesameAlchemyGrainTask) {
        try {
            //初始化AntMemberTaskListMap
            AntMemberTaskListMap.load();
            Set<String> blackList = new HashSet<>();
            //blackList.add("去淘金币逛一逛");
            // 可继续添加更多黑名单任务
            
            Set<String> whiteList = new HashSet<>();// 从黑名单中移除该任务
            //whiteList.add("逛一逛芝麻树");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                AntMemberTaskListMap.add(task, task);
            }
            
            JSONObject jo;
            if (AntMemberTask) {
                boolean hasNextPage = true;
                int page = 1;
                do {
                    jo = new JSONObject(AntMemberRpcCall.queryPointCert(page, 8));
                    TimeUtil.sleep(500);
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        break;
                    }
                    hasNextPage = jo.optBoolean("hasNextPage");
                    page++;
                    JSONArray jaCertList = jo.optJSONArray("certList");
                    if (jaCertList == null) {
                        break;
                    }
                    for (int i = 0; i < jaCertList.length(); i++) {
                        JSONObject cert = jaCertList.optJSONObject(i);
                        if (cert == null) {
                            continue;
                        }
                        String bizTitle = cert.optString("bizTitle");
                        if (!bizTitle.isEmpty()) {
                            AntMemberTaskListMap.add(bizTitle, bizTitle);
                        }
                    }
                }
                while (hasNextPage);
                
                jo = new JSONObject(AntMemberRpcCall.queryAllStatusTaskList());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray availableTaskList = jo.optJSONArray("availableTaskList");
                    if (availableTaskList != null) {
                        for (int i = 0; i < availableTaskList.length(); i++) {
                            JSONObject task = availableTaskList.optJSONObject(i);
                            JSONObject taskConfigInfo = task == null ? null : task.optJSONObject("taskConfigInfo");
                            String name = taskConfigInfo == null ? "" : taskConfigInfo.optString("name");
                            if (!name.isEmpty()) {
                                AntMemberTaskListMap.add(name, name);
                            }
                        }
                    }
                    JSONArray taskHistoryList = jo.optJSONArray("taskHistoryList");
                    if (taskHistoryList != null) {
                        for (int i = 0; i < taskHistoryList.length(); i++) {
                            JSONObject task = taskHistoryList.optJSONObject(i);
                            JSONObject taskConfigInfo = task == null ? null : task.optJSONObject("taskConfigInfo");
                            String name = taskConfigInfo == null ? "" : taskConfigInfo.optString("name");
                            if (!name.isEmpty()) {
                                AntMemberTaskListMap.add(name, name);
                            }
                        }
                    }
                }
                
                //保存任务到配置文件
                AntMemberTaskListMap.save();
                Log.record("同步任务🉑会员任务列表");
                
                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntMemberTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields antMember = config.getModelFieldsMap().get("AntMember");
                    SelectModelField AntMemberTaskList = (SelectModelField) antMember.get("AntMemberTaskList");
                    if (AntMemberTaskList == null) {
                        return;
                    }
                    
                    Set<String> currentValues = AntMemberTaskList.getValue();//该处直接返回列表地址
                    if (currentValues != null) {
                        for (String task : blackList) {
                            if (!currentValues.contains(task)) {
                                AntMemberTaskList.add(task, 0);
                            }
                        }
                        
                        // 3. 批量移除白名单任务（从现有列表中删除）
                        for (String task : whiteList) {
                            if (currentValues.contains(task)) {
                                currentValues.remove(task);
                            }
                        }
                    }
                    // 4. 保存配置
                    if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
                        Log.record("黑白名单🈲会员任务自动设置: " + AntMemberTaskList.getValue());
                    }
                    else {
                        Log.record("会员任务黑白名单设置失败");
                    }
                }
            }
            //初始化MemberCreditSesameTaskListMap
            MemberCreditSesameTaskListMap.load();
            blackList = new HashSet<>();
            blackList.add("去淘金币逛一逛");
            blackList.add("坚持逛裹酱领福利");
            blackList.add("坚持签到领奖励");
            blackList.add("坚持看直播领福利");
            blackList.add("去雇佣芝麻大表鸽");
            blackList.add("完成旧衣回收得现金");
            blackList.add("0.1元起租会员攒粒");
            blackList.add("每日施肥领水果");
            blackList.add("去玩小游戏");
            // 可继续添加更多黑名单任务
            
            whiteList = new HashSet<>();// 从黑名单中移除该任务
            whiteList.add("逛一逛芝麻树");
            whiteList.add("浏览15秒视频广告");
            whiteList.add("逛15秒商品橱窗");
            whiteList.add("逛一逛集汗滴找现金");
            whiteList.add("去体验先用后付");
            whiteList.add("去抛竿钓鱼");
            whiteList.add("去参与花呗活动");
            whiteList.add("坚持攒保障金");
            whiteList.add("去领支付宝积分");
            whiteList.add("去浏览租赁大促会场");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                MemberCreditSesameTaskListMap.add(task, task);
            }
            
            // 芝麻粒任务列表（会员页）标题同步，受「芝麻粒任务|推荐黑白名单」开关控制
            if (AutoMemberCreditSesameTaskList) {
                jo = new JSONObject(AntMemberRpcCall.queryHome());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONObject entrance = jo.getJSONObject("entrance");
                    if (entrance.optBoolean("openApp")) {
                        jo = new JSONObject(AntMemberRpcCall.CreditAccumulateStrategyRpcManager());
                        TimeUtil.sleep(300);
                        if (MessageUtil.checkResultCode(TAG, jo)) {
                            if (jo.has("data")) {
                                JSONObject data = jo.getJSONObject("data");
                                if (data.has("completeVOS")) {
                                    JSONArray completeVOS = data.getJSONArray("completeVOS");
                                    for (int i = 0; i < completeVOS.length(); i++) {
                                        JSONObject toCompleteVO = completeVOS.getJSONObject(i);
                                        String title = toCompleteVO.optString("title");
                                        if (title.isEmpty()) {
                                            continue;
                                        }
                                        MemberCreditSesameTaskListMap.add(title, title);
                                    }
                                }
                                if (data.has("toCompleteVOS")) {
                                    JSONArray toCompleteVOS = data.getJSONArray("toCompleteVOS");
                                    for (int i = 0; i < toCompleteVOS.length(); i++) {
                                        JSONObject toCompleteVO = toCompleteVOS.getJSONObject(i);
                                        String title = toCompleteVO.optString("title");
                                        if (title.isEmpty()) {
                                            continue;
                                        }
                                        MemberCreditSesameTaskListMap.add(title, title);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // 攒粒任务（炼金页）标题同步，受「芝麻粒|攒粒任务(炼金页)」功能开关控制
            if (sesameAlchemyGrainTask) {
                JSONObject alchemyJo = new JSONObject(AntMemberRpcCall.alchemyQueryTasks());
                TimeUtil.sleep(300);
                if (MessageUtil.checkResultCode(TAG, alchemyJo) && alchemyJo.has("data")) {
                    JSONObject alchemyData = alchemyJo.getJSONObject("data");
                    if (alchemyData.has("toCompleteVOS")) {
                        JSONArray alchemyTasks = alchemyData.getJSONArray("toCompleteVOS");
                        for (int i = 0; i < alchemyTasks.length(); i++) {
                            String grainTitle = alchemyTasks.getJSONObject(i).optString("title");
                            if (!grainTitle.isEmpty()) {
                                MemberCreditSesameTaskListMap.add(grainTitle, grainTitle);
                            }
                        }
                    }
                }
            }
            //保存任务到配置文件
            MemberCreditSesameTaskListMap.save();
            Log.record("同步任务🉑会员芝麻信用任务芝麻粒列表");

            //自动按推荐黑白名单调整（会员页与炼金页共用同一个黑名单）
            if (AutoMemberCreditSesameTaskList) {
                // 初始化黑白名单（使用集合统一操作）
                ConfigV2 config = ConfigV2.INSTANCE;
                ModelFields antMember = config.getModelFieldsMap().get("AntMember");
                SelectModelField MemberCreditSesameTaskList = (SelectModelField) antMember.get("MemberCreditSesameTaskList");
                if (MemberCreditSesameTaskList == null) {
                    return;
                }

                Set<String> currentValues = MemberCreditSesameTaskList.getValue();//该处直接返回列表地址
                if (currentValues != null) {
                    for (String task : blackList) {
                        if (!currentValues.contains(task)) {
                            MemberCreditSesameTaskList.add(task, 0);
                        }
                    }

                    // 3. 批量移除白名单任务（从现有列表中删除）
                    for (String task : whiteList) {
                        if (currentValues.contains(task)) {
                            currentValues.remove(task);
                        }
                    }
                }
                // 4. 保存配置
                if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
                    Log.record("黑白名单🈲会员芝麻信用任务芝麻粒自动设置: " + MemberCreditSesameTaskList.getValue());
                }
                else {
                    Log.record("会员芝麻信用任务芝麻粒黑白名单设置失败");
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "initMemberTaskListMap err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void memberSign() {
        try {
            if (!Status.hasFlagToday("member::sign")) {
                JSONObject jo = new JSONObject(AntMemberRpcCall.queryMemberSigninCalendar());
                TimeUtil.sleep(500);
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    if (jo.getBoolean("autoSignInSuccess")) {
                        Log.other("会员任务📅签到[坚持" + jo.getString("signinSumDay") + "天]#获得[" + jo.getString("signinPoint") + "积分]");
                    }
                    Status.flagToday("member::sign");
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "memberSign err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void queryPointCert(int page, int pageSize) {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryPointCert(page, pageSize));
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            boolean hasNextPage = jo.getBoolean("hasNextPage");
            JSONArray jaCertList = jo.getJSONArray("certList");
            for (int i = 0; i < jaCertList.length(); i++) {
                jo = jaCertList.getJSONObject(i);
                String bizTitle = jo.getString("bizTitle");
                //黑名单任务跳过
                if (AntMemberTaskList.getValue().contains(bizTitle)) {
                    continue;
                }
                String id = jo.getString("id");
                int pointAmount = jo.getInt("pointAmount");
                jo = new JSONObject(AntMemberRpcCall.receivePointByUser(id));
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    Log.other("会员任务🎖️领取[" + bizTitle + "]奖励#获得[" + pointAmount + "积分]");
                }
            }
            if (hasNextPage) {
                queryPointCert(page + 1, pageSize);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryPointCert err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 做任务赚积分
     */
    private void signPageTaskList() {
        try {
            do {
                JSONObject jo = new JSONObject(AntMemberRpcCall.signPageTaskList());
                TimeUtil.sleep(500);
                boolean doubleCheck = false;
                if (!MessageUtil.checkResultCode(TAG + " signPageTaskList", jo)) {
                    return;
                }
                if (!jo.has("categoryTaskList")) {
                    return;
                }
                JSONArray categoryTaskList = jo.getJSONArray("categoryTaskList");
                for (int i = 0; i < categoryTaskList.length(); i++) {
                    jo = categoryTaskList.getJSONObject(i);
                    JSONArray taskList = jo.getJSONArray("taskList");
                    String type = jo.getString("type");
                    if (Objects.equals("BROWSE", type)) {
                        doubleCheck = doBrowseTask(taskList);
                    }
                    else {
                        ExtensionsHandle.handleAlphaRequest("antMember", "doMoreTask", jo);
                    }
                }
                if (doubleCheck) {
                    continue;
                }
                break;
            }
            while (true);
        }
        catch (Throwable t) {
            Log.i(TAG, "signPageTaskList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 查询所有状态任务列表
     */
    private void queryAllStatusTaskList() {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryAllStatusTaskList());
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray availableTaskList = jo.getJSONArray("availableTaskList");
            if (doBrowseTask(availableTaskList)) {
                queryAllStatusTaskList();
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryAllStatusTaskList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    // 生活记录
    private void promise() {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.promiseQueryHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("data");
            JSONArray promiseSimpleTemplates = jo.getJSONArray("promiseSimpleTemplates");
            for (int i = 0; i < promiseSimpleTemplates.length(); i++) {
                jo = promiseSimpleTemplates.getJSONObject(i);
                String templateId = jo.getString("templateId");
                String promiseName = jo.getString("promiseName");
                String status = jo.getString("status");
                if ("un_join".equals(status) && promiseList.getValue().contains(templateId)) {
                    promiseJoin(querySingleTemplate(templateId));
                }
                PromiseSimpleTemplateIdMap.add(templateId, promiseName);
            }
            PromiseSimpleTemplateIdMap.save(UserIdMap.getCurrentUid());
        }
        catch (Throwable t) {
            Log.i(TAG, "promise err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private JSONObject querySingleTemplate(String templateId) {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.querySingleTemplate(templateId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return null;
            }
            jo = jo.getJSONObject("data");
            JSONObject result = new JSONObject();
            
            result.put("joinFromOuter", false);
            result.put("templateId", jo.getString("templateId"));
            result.put("autoRenewStatus", Boolean.valueOf(jo.getString("autoRenewStatus")));
            
            JSONObject joinGuarantyRule = jo.getJSONObject("joinGuarantyRule");
            joinGuarantyRule.put("selectValue", joinGuarantyRule.getJSONArray("canSelectValues").getString(0));
            joinGuarantyRule.remove("canSelectValues");
            result.put("joinGuarantyRule", joinGuarantyRule);
            
            JSONObject joinRule = jo.getJSONObject("joinRule");
            joinRule.put("selectValue", joinRule.getJSONArray("canSelectValues").getString(0));
            joinRule.remove("joinRule");
            result.put("joinRule", joinRule);
            
            JSONObject periodTargetRule = jo.getJSONObject("periodTargetRule");
            periodTargetRule.put("selectValue", periodTargetRule.getJSONArray("canSelectValues").getString(0));
            periodTargetRule.remove("canSelectValues");
            result.put("periodTargetRule", periodTargetRule);
            
            JSONObject dataSourceRule = jo.getJSONObject("dataSourceRule");
            dataSourceRule.put("selectValue", dataSourceRule.getJSONArray("canSelectValues").getJSONObject(0).getString("merchantId"));
            dataSourceRule.remove("canSelectValues");
            result.put("dataSourceRule", dataSourceRule);
            return result;
        }
        catch (Throwable t) {
            Log.i(TAG, "querySingleTemplate err:");
            Log.printStackTrace(TAG, t);
        }
        return null;
    }
    
    private void promiseJoin(JSONObject data) {
        if (data == null) {
            return;
        }
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.promiseJoin(data));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("data");
            String promiseName = jo.getString("promiseName");
            Log.other("生活记录📝加入[" + promiseName + "]");
            // 加入成功后推送打卡（抓包验证：joinActivity→pushActivity 即完成任务）
            String recordId = jo.optString("recordId");
            if (!recordId.isEmpty()) {
                JSONObject pushJo = new JSONObject(AntMemberRpcCall.promisePushActivity(recordId));
                if (MessageUtil.checkResultCode(TAG, pushJo)) {
                    Log.other("生活记录✅打卡[" + promiseName + "]");
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "promiseJoin err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    // 查询持续做明细任务
    private JSONObject promiseQueryDetail(String recordId) throws JSONException {
        JSONObject jo = new JSONObject(AntMemberRpcCall.promiseQueryDetail(recordId));
        if (!jo.optBoolean("success")) {
            return null;
        }
        return jo;
    }
    
    // 蚂蚁积分-做浏览任务
    private Boolean doBrowseTask(JSONArray taskList) {
        boolean doubleCheck = false;
        try {
            for (int i = 0; i < taskList.length(); i++) {
                try {
                    JSONObject task = taskList.getJSONObject(i);
                    if (task.getBoolean("hybrid")) {
                        int periodCurrentCount = Integer.parseInt(task.getJSONObject("extInfo").getString("PERIOD_CURRENT_COUNT"));
                        int periodTargetCount = Integer.parseInt(task.getJSONObject("extInfo").getString("PERIOD_TARGET_COUNT"));
                        int count = periodTargetCount > periodCurrentCount ? periodTargetCount - periodCurrentCount : 0;
                        if (count > 0) {
                            doubleCheck = doubleCheck || doBrowseTask(task, periodTargetCount, periodTargetCount);
                        }
                    }
                    else {
                        doubleCheck = doubleCheck || doBrowseTask(task, 1, 1);
                    }
                }
                catch (Throwable itemErr) {
                    // 单个任务字段异常(extInfo非数字等)不中断整批任务
                    Log.i(TAG, "doBrowseTask item err:");
                    Log.printStackTrace(TAG, itemErr);
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "doBrowseTask err:");
            Log.printStackTrace(TAG, t);
        }
        return doubleCheck;
    }
    
    private Boolean doBrowseTask(JSONObject task, int left, int right) {
        boolean doubleCheck = false;
        try {
            JSONObject taskConfigInfo = task.getJSONObject("taskConfigInfo");
            String name = taskConfigInfo.getString("name");
            //黑名单任务跳过
            if (AntMemberTaskList.getValue().contains(name)) {
                return false;
            }
            Long id = taskConfigInfo.getLong("id");
            String awardParamPoint = taskConfigInfo.getJSONObject("awardParam").getString("awardParamPoint");
            String targetBusiness = taskConfigInfo.getJSONArray("targetBusiness").getString(0);
            for (int i = left; i <= right; i++) {
                JSONObject jo = new JSONObject(AntMemberRpcCall.applyTask(name, id));
                TimeUtil.sleep(300);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    continue;
                }
                String[] targetBusinessArray = targetBusiness.split("#");
                String bizParam;
                String bizSubType;
                if (targetBusinessArray.length > 2) {
                    bizParam = targetBusinessArray[2];
                    bizSubType = targetBusinessArray[1];
                }
                else {
                    bizParam = targetBusinessArray[1];
                    bizSubType = targetBusinessArray[0];
                }
                jo = new JSONObject(AntMemberRpcCall.executeTask(bizParam, bizSubType));
                TimeUtil.sleep(300);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    continue;
                }
                String ex = left == right && left == 1 ? "" : "(" + (i + 1) + "/" + right + ")";
                Log.other("会员任务🎖️完成[" + name + ex + "]#获得[" + awardParamPoint + "积分]");
                doubleCheck = true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "doBrowseTask err:");
            Log.printStackTrace(TAG, t);
        }
        return doubleCheck;
    }
    
    private void goldTicket() {
        try {
            // 签到
            //已失效
            //goldBillCollect("\"campId\":\"CP1417744\",\"directModeDisableCollect\":true,\"from\":\"antfarm\",");
            // 收取其他
            //goldBillCollect("");
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 芝麻分任务处理（每日问答、公益任务、芭芭农场施肥等）
     */
    private void handleGrowthGuideTasks() {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject root = new JSONObject(AntMemberRpcCall.queryGrowthBehaviorToDoList());
            if (!MessageUtil.checkResultCode(TAG, root)) {
                return;
            }
            
            // 待处理任务列表
            JSONArray toDoList = root.optJSONArray("toDoList");
            int toDoCount = toDoList == null ? 0 : toDoList.length();
            if (toDoList == null || toDoCount == 0) {
                return;
            }
            
            for (int i = 0; i < toDoList.length(); i++) {
                JSONObject task = toDoList.optJSONObject(i);
                if (task == null) {
                    continue;
                }
                
                String behaviorId = task.optString("behaviorId", "");
                String title = task.optString("title", "");
                String status = task.optString("status", "");
                String subTitle = task.optString("subTitle", "");
                
                // 公益类任务（待领取）
                if ("wait_receive".equals(status)) {
                    String openResp = AntMemberRpcCall.openBehaviorCollect(behaviorId);
                    JSONObject openJo = new JSONObject(openResp);
                    if (MessageUtil.checkResultCode(TAG, openJo)) {
                        Log.other("攒芝麻分🧾任务领取：" + title);
                    }
                    continue;
                }
                
                // 每日问答
                if ("meiriwenda".equals(behaviorId) && "wait_doing".equals(status)) {
                    if (subTitle.contains("今日已参与")) {
                        Log.other("攒芝麻分🧾[每日问答] " + subTitle + "（跳过答题）");
                        continue;
                    }
                    
                    // 查询题目
                    JSONObject quizJo = new JSONObject(AntMemberRpcCall.queryDailyQuiz(behaviorId));
                    if (!MessageUtil.checkSuccess(TAG, quizJo)) {
                        continue;
                    }
                    JSONObject data = quizJo.optJSONObject("data");
                    if (data == null) {
                        continue;
                    }
                    
                    JSONObject qVo = data.optJSONObject("questionVo");
                    if (qVo == null) {
                        continue;
                    }
                    
                    JSONObject rightAnswer = qVo.optJSONObject("rightAnswer");
                    if (rightAnswer == null) {
                        continue;
                    }
                    
                    long bizDate = data.optLong("bizDate", 0L);
                    String questionId = qVo.optString("questionId", "");
                    String questionContent = qVo.optString("questionContent", "");
                    String answerId = rightAnswer.optString("answerId", "");
                    String answerContent = rightAnswer.optString("answerContent", "");
                    
                    if (bizDate <= 0 || questionId.isEmpty() || answerId.isEmpty()) {
                        continue;
                    }
                    
                    // 提交答案
                    JSONObject pushJo = new JSONObject(AntMemberRpcCall.pushDailyQuizAnswer(behaviorId, bizDate, answerId, questionId, "RIGHT"));
                    if (MessageUtil.checkResultCode(TAG, pushJo)) {
                        Log.other("攒芝麻分🎖️[每日答题成功] " + questionContent + " | 答案=" + answerContent + "(" + answerId + ")" + (subTitle.isEmpty() ? "" : " | " + subTitle));
                    }
                }
                
                // 视频问答
                if ("shipingwenda".equals(behaviorId) && "wait_doing".equals(status)) {
                    long bizDate = System.currentTimeMillis();
                    String questionId = "question3";
                    String answerId = "A";
                    String answerType = "RIGHT";
                    
                    jo = new JSONObject(AntMemberRpcCall.pushDailyQuizAnswer(behaviorId, bizDate, answerId, questionId, answerType));
                    
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.other("攒芝麻分🎖️[视频问答提交成功]");
                    }
                }
                
                // 芭芭农场施肥
                if ("babanongchang_7d".equals(behaviorId) && "wait_doing".equals(status)) {
                    
                    // 获取WUA
                    String wua = getWuaByReflection();
                    String source = "DNHZ_NC_zhimajingnangSF";
                    
                    JSONObject spreadManureData = new JSONObject(AntOrchardRpcCall.orchardSpreadManure(false, wua));
                    
                    if (!"100".equals(spreadManureData.optString("resultCode"))) {
                        continue;
                    }
                    
                    String taobaoDataStr = spreadManureData.optString("taobaoData", "");
                    if (taobaoDataStr.isEmpty()) {
                        continue;
                    }
                    
                    JSONObject spreadTaobaoData = new JSONObject(taobaoDataStr);
                    
                    JSONObject currentStage = spreadTaobaoData.optJSONObject("currentStage");
                    if (currentStage == null) {
                        Log.error(TAG + "GrowthGuideTasks" + "芭芭农场[缺少currentStage]");
                        continue;
                    }
                    
                    String stageText = currentStage.optString("stageText", "");
                    JSONObject statistics = spreadTaobaoData.optJSONObject("statistics");
                    int dailyAppWateringCount = statistics == null ? 0 : statistics.optInt("dailyAppWateringCount", 0);
                    
                    Log.farm("芭芭农场🌳施肥" + dailyAppWateringCount + "次[" + stageText + "]");
                    Log.other("攒芝麻分🎖️芭芭农场施肥[" + title + "]已施肥" + dailyAppWateringCount + "次");
                    
                }
            }
        }
        catch (Throwable e) {
            Log.printStackTrace(TAG + ".handleGrowthGuideTasks", e);
        }
    }
    
    // 在antMember任意类中添加反射调用方法
    private String getWuaByReflection() {
        try {
            // 1. 获取AntOrchard类
            Class<?> antOrchardClass = Class.forName("io.github.lazyimmortal.sesame.model.task.antOrchard.AntOrchard");
            // 2. 实例化类（若方法是静态的，无需实例化）
            Object antOrchardInstance = antOrchardClass.newInstance();
            // 3. 获取私有方法getWua()
            java.lang.reflect.Method getWuaMethod = antOrchardClass.getDeclaredMethod("getWua");
            // 4. 取消访问检查
            getWuaMethod.setAccessible(true);
            // 5. 调用方法并返回结果
            return (String) getWuaMethod.invoke(antOrchardInstance);
        }
        catch (ClassNotFoundException e) {
            Log.error("未找到AntOrchard类" + e);
        }
        catch (NoSuchMethodException e) {
            Log.error("未找到getWua方法" + e);
        }
        catch (IllegalAccessException | InstantiationException | java.lang.reflect.InvocationTargetException e) {
            Log.error("调用getWua方法失败" + e);
        }
        return "";
    }
    
    /**
     * 芝麻信用信息记录(信息记录): 查询芝麻分及行为/经济维度进度, 读不到以空格占位
     */
    private void sesameInfo() {
        Integer score = null;
        String socialName = "";
        Integer socialCurrent = null;
        Integer socialTarget = null;
        String economicName = "";
        Integer economicCurrent = null;
        Integer economicTarget = null;
        Integer collectProcess = null;
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryScoreProgress());
            if (MessageUtil.checkSuccess(TAG, jo)) {
                if (jo.has("score")) {
                    score = jo.optInt("score");
                }
                JSONObject social = jo.optJSONObject("socialDimension");
                if (social != null) {
                    socialName = social.optString("dimensionName", "");
                    if (social.has("currentProgress")) {
                        socialCurrent = social.optInt("currentProgress");
                    }
                    if (social.has("targetProgress")) {
                        socialTarget = social.optInt("targetProgress");
                    }
                }
                JSONObject economic = jo.optJSONObject("economicDimension");
                if (economic != null) {
                    economicName = economic.optString("dimensionName", "");
                    if (economic.has("currentProgress")) {
                        economicCurrent = economic.optInt("currentProgress");
                    }
                    if (economic.has("targetProgress")) {
                        economicTarget = economic.optInt("targetProgress");
                    }
                }
                JSONObject guide = jo.optJSONObject("newZmScoreGuideInfo");
                if (guide != null && guide.has("totalCollectProcess")) {
                    collectProcess = guide.optInt("totalCollectProcess");
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "sesameInfo 查询失败:");
            Log.printStackTrace(TAG, th);
        }
        Log.info("芝麻信用💯[" + UserIdMap.getShowName(UserIdMap.getCurrentUid()) + "]芝麻分" + Log.n(score) + ";" + socialName + Log.n(socialCurrent) + "/" + Log.n(socialTarget) + ";" + economicName + Log.n(economicCurrent) + "/" + Log.n(economicTarget) + ";待攒进度" + Log.n(collectProcess) + ";");
    }

    /**
     * 攒芝麻分：一键领取未领取的行为反馈（collectAll=true, status=UNCLAIMED）
     */
    private void collectCreditFeedback() {
        try {
            JSONObject jo = new JSONObject(AlchemyRpcCall.collectCreditFeedback());
            if (MessageUtil.checkResultCode(TAG, jo)) {
                //Log.other("攒芝麻分🧧一键领取行为反馈");
            }
        } catch (Throwable t) {
            Log.i(TAG, "collectCreditFeedback err:");
            Log.printStackTrace(TAG, t);
        }
    }

    public static void queryAndCollect() {
        try {
            // 1. 查询进度球状态
            String queryResp = AntMemberRpcCall.queryScoreProgress();
            if (queryResp == null || queryResp.isEmpty()) {
                return;
            }
            
            JSONObject json = new JSONObject(queryResp);
            
            // 检查 success
            if (!MessageUtil.checkSuccess(TAG, json)) {
                return;
            }
            
            JSONObject totalWait = json.optJSONObject("totalWaitProcessVO");
            if (totalWait == null) {
                return;
            }
            
            JSONArray idList = totalWait.optJSONArray("totalProgressIdList");
            if (idList == null || idList.length() == 0) {
                return;
            }
            
            // 直接传 JSONArray
            String collectResp = AntMemberRpcCall.collectProgressBall(idList);
            if (collectResp == null) {
                return;
            }
            
            JSONObject collectJson = new JSONObject(collectResp);
            int collectedAccelerateProgress = collectJson.optInt("collectedAccelerateProgress", -1);
            int currentAccelerateValue = collectJson.optInt("currentAccelerateValue", 0);
            int totalAccelerateProgress = collectJson.optInt("totalAccelerateProgress", 0);
            Log.other("攒芝麻分🎁领取#本次加速进度:" + collectedAccelerateProgress + "(总" + totalAccelerateProgress + "%)加速倍率:" + currentAccelerateValue);
        }
        catch (JSONException e) {
            Log.printStackTrace(TAG + "queryAndCollect JSON err", e);
        }
        catch (Exception e) {
            Log.printStackTrace(TAG + "queryAndCollect err", e);
        }
    }
    
    /**
     * 收取黄金票
     */
    private void goldBillCollect(String signInfo) {
        try {
            String str = AntMemberRpcCall.goldBillCollect(signInfo);
            JSONObject jsonObject = new JSONObject(str);
            if (!jsonObject.optBoolean("success")) {
                Log.i(TAG + ".goldBillCollect.goldBillCollect", jsonObject.optString("resultDesc"));
                return;
            }
            JSONObject object = jsonObject.getJSONObject("result");
            JSONArray jsonArray = object.getJSONArray("collectedList");
            int length = jsonArray.length();
            if (length == 0) {
                return;
            }
            for (int i = 0; i < length; i++) {
                Log.other("黄金票🙈[" + jsonArray.getString(i) + "]");
            }
            Log.other("黄金票🏦本次总共获得[" + JsonUtil.getValueByPath(object, "collectedCamp.amount") + "]");
        }
        catch (Throwable th) {
            Log.i(TAG, "signIn err:");
            Log.printStackTrace(TAG, th);
        }
    }
    //游戏中心任务
    
    /**
     * 批量领取玩乐豆
     */
    public static void batchReceivePointBall() {
        try {
            JSONObject jsonObject = new JSONObject(AntMemberRpcCall.batchReceivePointBall());
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONObject dataObj = jsonObject.getJSONObject("data");
                String totalAmount = dataObj.getString("totalAmount");
                Log.other("游戏中心🎮批量领取#获得[" + totalAmount + "玩乐豆]");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "batchReceivePointBall err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 每日签到
     *
     * @return 签到是否成功
     */
    public static boolean dailySignIn() {
        try {
            JSONObject jsonObject = new JSONObject(AntMemberRpcCall.continueSignIn());
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONObject toastModule = jsonObject.getJSONObject("data").getJSONObject("autoSignInToastModule");
                String desc = toastModule.getString("desc");
                String beanNum = desc.substring(desc.indexOf("玩乐豆+") + 4);
                Log.other("游戏中心🎮每日签到#获得[" + beanNum + "玩乐豆]");
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "continueSignIn err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    /**
     * 处理单个任务
     *
     * @param taskObj 任务JSON对象
     */
    public static void processTask(JSONObject taskObj) {
        try {
            if (!"VIEW".equals(taskObj.getString("actionType"))) {
                return;
            }
            
            String taskId = taskObj.getString("taskId");
            String subTitle = taskObj.getString("subTitle");
            String taskStatus = taskObj.getString("taskStatus");
            int prizeAmount = taskObj.getInt("prizeAmount");
            
            // 任务未完成且需要报名
            if ("NOT_DONE".equals(taskStatus) && taskObj.getBoolean("needSignUp")) {
                JSONObject jsonObject = new JSONObject(AntMemberRpcCall.doTaskSignup(taskId));
                if (!MessageUtil.checkSuccess(TAG, jsonObject)) {
                    return;
                }
            }
            
            // 执行任务
            JSONObject doTaskjo = new JSONObject(AntMemberRpcCall.doTaskSend(taskId));
            if (MessageUtil.checkSuccess(TAG, doTaskjo)) {
                Log.other("游戏中心🎮完成任务[" + subTitle + "]#待领[" + prizeAmount + "玩乐豆]");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "doTask err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 查询并处理任务列表
     */
    public static void queryAndProcessTaskList() {
        try {
            JSONObject jsonObject = new JSONObject(AntMemberRpcCall.queryModularTaskList());
            if (!MessageUtil.checkSuccess(TAG, jsonObject)) {
                return;
            }
            if (!jsonObject.has("data")) {
                return;
            }
            JSONArray taskModuleList = jsonObject.getJSONObject("data").getJSONArray("taskModuleList");
            for (int i = 0; i < taskModuleList.length(); i++) {
                JSONObject moduleObj = taskModuleList.getJSONObject(i);
                JSONArray taskList = moduleObj.getJSONArray("taskList");
                for (int j = 0; j < taskList.length(); j++) {
                    processTask(taskList.getJSONObject(j));
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryModularTaskList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    public static void queryTaskList() {
        try {
            JSONObject jsonObject = new JSONObject(AntMemberRpcCall.queryTaskList());
            if (!MessageUtil.checkSuccess(TAG, jsonObject)) {
                return;
            }
            if (!jsonObject.has("data")) {
                return;
            }
            JSONArray gameTaskList = jsonObject.getJSONObject("data").optJSONObject("gameTaskModule").optJSONArray("gameTaskList");
            for (int i = 0; i < gameTaskList.length(); i++) {
                processTask(gameTaskList.getJSONObject(i));
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryModularTaskList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 查询玩乐豆小球列表，有则领取
     */
    public static void queryPointBallList() {
        try {
            String response = ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.queryPointBallList", "[{}]");
            JSONObject jsonObject = new JSONObject(response);
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONArray pointBallList = jsonObject.getJSONObject("data").getJSONArray("pointBallList");
                if (pointBallList.length() > 0) {
                    batchReceivePointBall();
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryPointBallList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 检查并执行签到
     */
    public static void checkAndDoSignIn() {
        if (Status.hasFlagToday("gameCenterSignIn")) {
            return;
        }
        
        try {
            JSONObject jsonObject = new JSONObject(AntMemberRpcCall.queryPointBallList());
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONObject dataObj = jsonObject.getJSONObject("data");
                if (dataObj.has("signInBallModule")) {
                    JSONObject signInModule = dataObj.getJSONObject("signInBallModule");
                    if (!signInModule.getBoolean("signInStatus")) {
                        if (dailySignIn()) {
                            Status.flagToday("gameCenterSignIn");
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "querySignInBall err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /*
    private void enableGameCenter() {
        try {
            try {
                String str = AntMemberRpcCall.querySignInBall();
                JSONObject jsonObject = new JSONObject(str);
                if (!jsonObject.optBoolean("success")) {
                    Log.i(TAG + ".signIn.querySignInBall", jsonObject.optString("resultDesc"));
                    return;
                }
                str = JsonUtil.getValueByPath(jsonObject, "data.signInBallModule.signInStatus");
                if (String.valueOf(true).equals(str)) {
                    return;
                }
                str = AntMemberRpcCall.continueSignIn();
                TimeUtil.sleep(300);
                jsonObject = new JSONObject(str);
                if (!jsonObject.optBoolean("success")) {
                    Log.i(TAG + ".signIn.continueSignIn", jsonObject.optString("resultDesc"));
                    return;
                }
                Log.record("游戏中心🎮签到成功");
            }
            catch (Throwable th) {
                Log.i(TAG, "signIn err:");
                Log.printStackTrace(TAG, th);
            }
            try {
                String str = AntMemberRpcCall.queryPointBallList();
                JSONObject jsonObject = new JSONObject(str);
                if (!jsonObject.optBoolean("success")) {
                    Log.i(TAG + ".batchReceive.queryPointBallList", jsonObject.optString("resultDesc"));
                    return;
                }
                JSONArray jsonArray = (JSONArray) JsonUtil.getValueByPathObject(jsonObject, "data.pointBallList");
                if (jsonArray == null || jsonArray.length() == 0) {
                    return;
                }
                str = AntMemberRpcCall.batchReceivePointBall();
                TimeUtil.sleep(300);
                jsonObject = new JSONObject(str);
                if (jsonObject.optBoolean("success")) {
                    Log.other("游戏中心🎮全部领取成功[" + JsonUtil.getValueByPath(jsonObject, "data.totalAmount") + "]乐豆");
                }
                else {
                    Log.i(TAG + ".batchReceive.batchReceivePointBall", jsonObject.optString("resultDesc"));
                }
            }
            catch (Throwable th) {
                Log.i(TAG, "batchReceive err:");
                Log.printStackTrace(TAG, th);
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }
    */
    // 会员积分兑换
    private void memberPointExchangeBenefit() {
        try {
            String userId = UserIdMap.getCurrentUid();
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryDeliveryZoneDetail(userId, "94000SR2024011106752003"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("entityInfoList")) {
                Log.record("会员积分[未实名账号无可兑换权益]");
                return;
            }
            JSONArray entityInfoList = jo.getJSONArray("entityInfoList");
            for (int i = 0; i < entityInfoList.length(); i++) {
                JSONObject entityInfo = entityInfoList.getJSONObject(i);
                JSONObject benefitInfo = entityInfo.getJSONObject("benefitInfo");
                JSONObject pricePresentation = benefitInfo.getJSONObject("pricePresentation");
                if (!"POINT_PAY".equals(pricePresentation.optString("strategyType"))) {
                    continue;
                }
                String name = benefitInfo.getString("name");
                String benefitId = benefitInfo.getString("benefitId");
                MemberBenefitIdMap.add(benefitId, name);
                if (!Status.canMemberPointExchangeBenefitToday(benefitId) || !memberPointExchangeBenefitList.getValue().contains(benefitId)) {
                    continue;
                }
                String itemId = benefitInfo.getString("itemId");
                if (exchangeBenefit(benefitId, itemId)) {
                    String point = pricePresentation.getString("point");
                    Log.other("会员积分🎐兑换[" + name + "]#花费[" + point + "积分]");
                }
            }
            MemberBenefitIdMap.save(userId);
        }
        catch (Throwable t) {
            Log.i(TAG, "memberPointExchangeBenefit err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private Boolean exchangeBenefit(String benefitId, String itemId) {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.exchangeBenefit(benefitId, itemId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Status.memberPointExchangeBenefitToday(benefitId);
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "exchangeBenefit err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private void collectSesame() {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject entrance = jo.getJSONObject("entrance");
            if (!entrance.optBoolean("openApp")) {
                Log.other("芝麻信用💌未开通");
                return;
            }
            
            jo = new JSONObject(AntMemberRpcCall.CreditAccumulateStrategyRpcManager());
            TimeUtil.sleep(300);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("data")) {
                return;
            }
            JSONObject data = jo.getJSONObject("data");
            if (!data.has("toCompleteVOS")) {
                return;
            }
            JSONArray toCompleteVOS = data.getJSONArray("toCompleteVOS");
            for (int i = 0; i < toCompleteVOS.length(); i++) {
                JSONObject toCompleteVO = toCompleteVOS.getJSONObject(i);
                String taskTitle = toCompleteVO.has("title") ? toCompleteVO.getString("title") : "未知任务";
                //黑名单任务跳过
                if (MemberCreditSesameTaskList.getValue().contains(taskTitle)) {
                    continue;
                }
                
                boolean finishFlag = toCompleteVO.optBoolean("finishFlag", false);
                String actionText = toCompleteVO.optString("actionText", "");
                
                // 检查任务是否已完成
                if (finishFlag || "已完成".equals(actionText)) {
                    continue;
                }
                
                if (!toCompleteVO.has("templateId")) {
                    continue;
                }
                
                String taskTemplateId = toCompleteVO.getString("templateId");
                int needCompleteNum = toCompleteVO.has("needCompleteNum") ? toCompleteVO.getInt("needCompleteNum") : 1;
                int completedNum = toCompleteVO.optInt("completedNum", 0);
                String s = null;
                String recordId = null;
                JSONObject responseObj = null;
                
                if (!toCompleteVO.has("todayFinish")) {
                    // 领取任务
                    s = AntMemberRpcCall.joinSesameTask(taskTemplateId);
                    responseObj = new JSONObject(s);
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("MemberCreditSesameTaskList", taskTitle, responseObj);
                    TimeUtil.sleep(200);
                    if (!MessageUtil.checkResultCode(TAG, responseObj)) {
                        Log.error(TAG + "芝麻信用💳领取任务[" + taskTitle + "]失败#" + s);
                        continue;
                    }
                    recordId = responseObj.getJSONObject("data").getString("recordId");
                }
                else {
                    if (!toCompleteVO.has("recordId")) {
                        Log.error(TAG + "芝麻信用💳任务[" + taskTitle + "未获取到]recordId#" + toCompleteVO);
                        continue;
                    }
                    recordId = toCompleteVO.getString("recordId");
                }
                // 完成任务
                for (int j = completedNum; j < needCompleteNum; j++) {
                    s = AntMemberRpcCall.finishSesameTask(recordId);
                    TimeUtil.sleep(2000);
                    responseObj = new JSONObject(s);
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("MemberCreditSesameTaskList", taskTitle, responseObj);
                    
                    if (MessageUtil.checkResultCode(TAG, responseObj)) {
                        Log.other("芝麻信用💳完成任务[" + taskTitle + "]#(" + (j + 1) + "/" + needCompleteNum + "天)");
                    }
                    else {
                        Log.error("芝麻信用💳完成任务[" + taskTitle + "]失败#" + s);
                    }
                }
                
                jo = new JSONObject(AntMemberRpcCall.queryCreditFeedback());
                TimeUtil.sleep(300);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                JSONArray ja = jo.getJSONArray("creditFeedbackVOS");
                for (int j = 0; j < ja.length(); j++) {
                    jo = ja.getJSONObject(j);
                    if (!"UNCLAIMED".equals(jo.getString("status"))) {
                        continue;
                    }
                    //String title = jo.getString("title");
                    String creditFeedbackId = jo.getString("creditFeedbackId");
                    String potentialSize = jo.getString("potentialSize");
                    jo = new JSONObject(AntMemberRpcCall.collectCreditFeedback(creditFeedbackId));
                    TimeUtil.sleep(300);
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.other("收芝麻粒🙇🏻‍♂️领取[" + taskTitle + "]奖励[芝麻粒*" + potentialSize + "]");
                    }
                }
            }
            jo = new JSONObject(AntMemberRpcCall.queryCreditFeedback());
            TimeUtil.sleep(300);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray creditFeedbackVOS = jo.getJSONArray("creditFeedbackVOS");
            if (creditFeedbackVOS.length() != 0) {
                jo = new JSONObject(AntMemberRpcCall.collectAllCreditFeedback());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    String resultCode = jo.optString("resultCode");
                    Log.other("收芝麻粒🙇🏻‍♂️[一键收取]" + resultCode);
                }
            }

        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 攒粒任务（炼金页 queryListV3 version=alchemy）：
     * 复用会员页任务链路 joinActivity→pushActivity→collectCreditFeedback；
     * 订阅类/下单转化类需真实操作或授权，直接跳过；失败任务自动写入黑名单
     */
    private void alchemySesameTasks() {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.alchemyQueryTasks());
            TimeUtil.sleep(300);
            if (!MessageUtil.checkResultCode(TAG, jo) || !jo.has("data")) {
                return;
            }
            JSONObject data = jo.getJSONObject("data");
            if (!data.has("toCompleteVOS")) {
                return;
            }
            JSONArray toCompleteVOS = data.getJSONArray("toCompleteVOS");
            for (int i = 0; i < toCompleteVOS.length(); i++) {
                JSONObject toCompleteVO = toCompleteVOS.getJSONObject(i);
                String taskTitle = toCompleteVO.has("title") ? toCompleteVO.getString("title") : "";
                if (taskTitle.isEmpty() || !toCompleteVO.has("templateId")) {
                    continue;
                }
                // 黑名单任务跳过
                if (MemberCreditSesameTaskList.getValue().contains(taskTitle)) {
                    continue;
                }
                // 已完成任务跳过
                if (toCompleteVO.optBoolean("finishFlag", false) || "已完成".equals(toCompleteVO.optString("actionText", ""))) {
                    continue;
                }
                // 订阅类需订阅授权、下单转化类需真实购买行为，直接跳过
                if ("SUBSCRIBE_TASK".equals(toCompleteVO.optString("bizType"))
                        || "zhuanhua".equals(toCompleteVO.optString("gameTaskType"))) {
                    Log.record("芝麻炼金跳过攒粒任务[" + taskTitle + "]需真实操作或授权");
                    continue;
                }
                String taskTemplateId = toCompleteVO.getString("templateId");
                int needCompleteNum = toCompleteVO.optInt("needCompleteNum", 1);
                int completedNum = toCompleteVO.optInt("completedNum", 0);
                String recordId;
                JSONObject responseObj;
                if (!toCompleteVO.has("todayFinish")) {
                    // 领取任务
                    String s = AntMemberRpcCall.joinSesameGrainTask(taskTemplateId);
                    responseObj = new JSONObject(s);
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("MemberCreditSesameTaskList", taskTitle, responseObj);
                    TimeUtil.sleep(200);
                    if (!MessageUtil.checkResultCode(TAG, responseObj)) {
                        Log.error(TAG + "芝麻炼金💳领取攒粒任务[" + taskTitle + "]失败#" + s);
                        continue;
                    }
                    recordId = responseObj.getJSONObject("data").getString("recordId");
                    // 任务回调上报（TO_COMPLETE，与App抓包时序一致：join→taskFeedback→push）
                    AntMemberRpcCall.feedBackSesameGrainTask(taskTemplateId);
                    TimeUtil.sleep(200);
                }
                else {
                    if (!toCompleteVO.has("recordId")) {
                        Log.error(TAG + "芝麻炼金💳攒粒任务[" + taskTitle + "]未获取到recordId#" + toCompleteVO);
                        continue;
                    }
                    recordId = toCompleteVO.getString("recordId");
                }
                // 完成任务
                boolean finishAll = true;
                for (int j = completedNum; j < needCompleteNum; j++) {
                    String s = AntMemberRpcCall.finishSesameTask(recordId);
                    TimeUtil.sleep(2000);
                    responseObj = new JSONObject(s);
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("MemberCreditSesameTaskList", taskTitle, responseObj);
                    if (MessageUtil.checkResultCode(TAG, responseObj)) {
                        Log.other("芝麻炼金💳完成攒粒任务[" + taskTitle + "]#(" + (j + 1) + "/" + needCompleteNum + "天)");
                    }
                    else {
                        Log.error("芝麻炼金💳完成攒粒任务[" + taskTitle + "]失败#" + s);
                        finishAll = false;
                        break;
                    }
                }
                // 收取已完成任务的芝麻粒
                if (finishAll) {
                    jo = new JSONObject(AntMemberRpcCall.queryCreditFeedback());
                    TimeUtil.sleep(300);
                    if (MessageUtil.checkResultCode(TAG, jo) && jo.has("creditFeedbackVOS")) {
                        JSONArray feedbackVOS = jo.getJSONArray("creditFeedbackVOS");
                        for (int j = 0; j < feedbackVOS.length(); j++) {
                            JSONObject feedback = feedbackVOS.getJSONObject(j);
                            if (!"UNCLAIMED".equals(feedback.getString("status"))) {
                                continue;
                            }
                            String creditFeedbackId = feedback.getString("creditFeedbackId");
                            String potentialSize = feedback.optString("potentialSize");
                            JSONObject collectRes = new JSONObject(AntMemberRpcCall.collectCreditFeedback(creditFeedbackId));
                            TimeUtil.sleep(300);
                            if (MessageUtil.checkResultCode(TAG, collectRes)) {
                                Log.other("收芝麻粒🙇🏻‍♂️领取[" + taskTitle + "]奖励[芝麻粒*" + potentialSize + "]");
                            }
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    private void CheckInTaskRpcManager() {
        if (Status.hasFlagToday("AntMember::zmlCheckIn")) {
            return;
        }
        try {
            
            String checkInRes = AntMemberRpcCall.alchemyQueryCheckIn("zml");
            JSONObject checkInJo = new JSONObject(checkInRes);
            if (MessageUtil.checkResultCode(TAG, checkInJo)) {
                JSONObject data = checkInJo.optJSONObject("data");
                if (data != null) {
                    JSONObject currentDay = data.optJSONObject("currentDateCheckInTaskVO");
                    if (currentDay != null) {
                        String status = currentDay.optString("status");
                        String checkInDate = currentDay.optString("checkInDate");
                        if ("CAN_COMPLETE".equals(status) && !checkInDate.isEmpty()) {
                            String completeRes = AntMemberRpcCall.zmCheckInCompleteTask(checkInDate, "zml");
                            try {
                                JSONObject completeJo = new JSONObject(completeRes);
                                if (MessageUtil.checkResultCode(TAG, completeJo)) {
                                    JSONObject prize = completeJo.optJSONObject("data");
                                    int num = 0;
                                    if (prize != null) {
                                        num = prize.optInt("zmlNum", prize.optJSONObject("prize") != null ? prize.optJSONObject("prize").optInt("num", 0) : 0);
                                    }
                                    Log.other("收芝麻粒🙇🏻‍♂️领取[每日签到成功]#获得" + num + "粒");
                                }
                                else {
                                    Log.error(".doSesameAlchemy#" + "签到失败:" + completeRes);
                                }
                            }
                            catch (Throwable e) {
                                Log.printStackTrace(TAG + ".doSesameAlchemy.alchemyCheckInComplete", e);
                            }
                        }
                    }
                }
            }
            Status.flagToday("AntMember::zmlCheckIn");
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG + ".doSesameZmlCheckIn", t);
        }
    }
    
    // 我的快递任务
    private void RecommendTask() {
        try {
            // 调用 AntMemberRpcCall.queryRecommendTask() 获取 JSON 数据
            String response = AntMemberRpcCall.queryRecommendTask();
            JSONObject jsonResponse = new JSONObject(response);
            // 获取 taskDetailList 数组
            JSONArray taskDetailList = jsonResponse.getJSONArray("taskDetailList");
            // 遍历 taskDetailList
            for (int i = 0; i < taskDetailList.length(); i++) {
                JSONObject taskDetail = taskDetailList.getJSONObject(i);
                // 检查 "canAccess" 的值是否为 true
                boolean canAccess = taskDetail.optBoolean("canAccess", false);
                if (!canAccess) {
                    // 如果 "canAccess" 不为 true，跳过
                    continue;
                }
                // 获取 taskMaterial 对象
                JSONObject taskMaterial = taskDetail.optJSONObject("taskMaterial");
                // 获取 taskBaseInfo 对象
                JSONObject taskBaseInfo = taskDetail.optJSONObject("taskBaseInfo");
                // 获取 taskCode
                String taskCode = taskMaterial.optString("taskCode", "");
                // 根据 taskCode 执行不同的操作
                if ("WELFARE_PLUS_ANT_FOREST".equals(taskCode) || "WELFARE_PLUS_ANT_OCEAN".equals(taskCode)) {
                    if ("WELFARE_PLUS_ANT_FOREST".equals(taskCode)) {
                        //String forestHomePageResponse = AntMemberRpcCall.queryforestHomePage();
                        //TimeUtil.sleep(2000);
                        String forestTaskResponse = AntMemberRpcCall.forestTask();
                        TimeUtil.sleep(500);
                        String forestreceiveTaskAward = AntMemberRpcCall.forestreceiveTaskAward();
                    }
                    else if ("WELFARE_PLUS_ANT_OCEAN".equals(taskCode)) {
                        //String oceanHomePageResponse = AntMemberRpcCall.queryoceanHomePage();
                        //TimeUtil.sleep(2000);
                        String oceanTaskResponse = AntMemberRpcCall.oceanTask();
                        TimeUtil.sleep(500);
                        String oceanreceiveTaskAward = AntMemberRpcCall.oceanreceiveTaskAward();
                    }
                    if (taskBaseInfo != null) {
                        String appletName = taskBaseInfo.optString("appletName", "Unknown Applet");
                        Log.other("我的快递💌完成[" + appletName + "]");
                    }
                }
                if (taskMaterial == null || !taskMaterial.has("taskId")) {
                    // 如果 taskMaterial 为 null 或者不包含 taskId，跳过
                    continue;
                }
                // 获取 taskId
                String taskId = taskMaterial.getString("taskId");
                // 调用 trigger 方法
                String triggerResponse = AntMemberRpcCall.trigger(taskId);
                JSONObject triggerResult = new JSONObject(triggerResponse);
                // 检查 success 字段
                boolean success = triggerResult.getBoolean("success");
                if (success) {
                    // 从 triggerResponse 中获取 prizeSendInfo 数组
                    JSONArray prizeSendInfo = triggerResult.getJSONArray("prizeSendInfo");
                    if (prizeSendInfo.length() > 0) {
                        JSONObject prizeInfo = prizeSendInfo.getJSONObject(0);
                        JSONObject extInfo = prizeInfo.getJSONObject("extInfo");
                        // 获取 promoCampName
                        String promoCampName = extInfo.optString("promoCampName", "Unknown Promo Campaign");
                        // 输出日志信息
                        Log.other("我的快递💌完成[" + promoCampName + "]");
                    }
                }
            }
        }
        catch (Throwable th) {
            Log.i(TAG, "RecommendTask err:");
            Log.printStackTrace(TAG, th);
        }
    }
    
    private void OrdinaryTask() {
        try {
            // 调用 AntMemberRpcCall.queryOrdinaryTask() 获取 JSON 数据
            String response = AntMemberRpcCall.queryOrdinaryTask();
            JSONObject jsonResponse = new JSONObject(response);
            // 检查是否请求成功
            if (jsonResponse.getBoolean("success")) {
                // 获取任务详细列表
                JSONArray taskDetailList = jsonResponse.getJSONArray("taskDetailList");
                // 遍历任务详细列表
                for (int i = 0; i < taskDetailList.length(); i++) {
                    // 获取当前任务对象
                    JSONObject task = taskDetailList.getJSONObject(i);
                    // 提取任务 ID、处理状态和触发类型
                    String taskId = task.optString("taskId");
                    String taskProcessStatus = task.optString("taskProcessStatus");
                    String sendCampTriggerType = task.optString("sendCampTriggerType");
                    // 检查任务状态和触发类型，执行触发操作
                    if (!"RECEIVE_SUCCESS".equals(taskProcessStatus) && !"EVENT_TRIGGER".equals(sendCampTriggerType)) {
                        // 调用 signuptrigger 方法
                        String signuptriggerResponse = AntMemberRpcCall.signuptrigger(taskId);
                        // 调用 sendtrigger 方法
                        String sendtriggerResponse = AntMemberRpcCall.sendtrigger(taskId);
                        // 解析 sendtriggerResponse
                        JSONObject sendTriggerJson = new JSONObject(sendtriggerResponse);
                        // 判断任务是否成功
                        if (sendTriggerJson.getBoolean("success")) {
                            // 从 sendtriggerResponse 中获取 prizeSendInfo 数组
                            JSONArray prizeSendInfo = sendTriggerJson.getJSONArray("prizeSendInfo");
                            // 获取 prizeName
                            String prizeName = prizeSendInfo.getJSONObject(0).getString("prizeName");
                            Log.other("我的快递💌完成[" + prizeName + "]");
                        }
                        else {
                            Log.i(TAG, "sendtrigger failed for taskId: " + taskId);
                        }
                        TimeUtil.sleep(1000);
                    }
                }
            }
        }
        catch (Throwable th) {
            Log.i(TAG, "OrdinaryTask err:");
            Log.printStackTrace(TAG, th);
        }
    }
    
    // 消费金签到
    private void signinCalendar() {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.signinCalendar());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            boolean signed = jo.optBoolean("isSignInToday");
            if (!signed) {
                jo = new JSONObject(AntMemberRpcCall.openBoxAward());
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    int amount = jo.getInt("amount");
                    int consecutiveSignInDays = jo.getInt("consecutiveSignInDays");
                    Log.other("攒消费金💰签到[坚持" + consecutiveSignInDays + "天]#获得[" + amount + "消费金]");
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "signinCalendar err:");
            Log.printStackTrace(TAG, t);
        }
    }
}
