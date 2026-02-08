package io.github.lazyimmortal.sesame.model.task.antStall;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.lazyimmortal.sesame.data.ConfigV2;
import io.github.lazyimmortal.sesame.data.ModelFields;
import io.github.lazyimmortal.sesame.data.ModelGroup;
import io.github.lazyimmortal.sesame.data.modelFieldExt.BooleanModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.IntegerModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectModelField;
import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.entity.AlipayAntStallTaskList;
import io.github.lazyimmortal.sesame.entity.AlipayUser;
import io.github.lazyimmortal.sesame.model.base.TaskCommon;
import io.github.lazyimmortal.sesame.model.task.antFarm.AntFarm.TaskStatus;
import io.github.lazyimmortal.sesame.model.task.readingDada.ReadingDada;
import io.github.lazyimmortal.sesame.util.*;
import io.github.lazyimmortal.sesame.util.idMap.AntFarmDoFarmTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.AntStallTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Constanline
 * @since 2023/08/22
 */
public class AntStall extends ModelTask {
    private static final String TAG = AntStall.class.getSimpleName();
    
    private static class Seat {
        public String userId;
        public int hot;
        
        public Seat(String userId, int hot) {
            this.userId = userId;
            this.hot = hot;
        }
    }
    
    private static final List<String> taskTypeList;
    
    static {
        taskTypeList = new ArrayList<>();
        // 开启收新村收益提醒
        taskTypeList.add("ANTSTALL_NORMAL_OPEN_NOTICE");
        // 添加首页
        taskTypeList.add("tianjiashouye");
        // 【木兰市集】逛精选好物
        //        taskTypeList.add("ANTSTALL_XLIGHT_VARIABLE_AWARD");
        // 去饿了么果园逛一逛
        taskTypeList.add("ANTSTALL_ELEME_VISIT");
        // 去点淘赚元宝提现
        taskTypeList.add("ANTSTALL_TASK_diantao202311");
        taskTypeList.add("ANTSTALL_TASK_nongchangleyuan");
    }
    
    @Override
    public String getName() {
        return "新村";
    }
    
    @Override
    public ModelGroup getGroup() {
        return ModelGroup.STALL;
    }
    
    private BooleanModelField AutoAntStallTaskList;
    private SelectModelField AntStallTaskList;
    private ChoiceModelField openShopType;
    private SelectModelField openShopList;
    private BooleanModelField closeShop;
    private IntegerModelField closeShopTime;
    private BooleanModelField sendBackShop;
    private IntegerModelField sendBackShopTime;
    private SelectModelField sendBackShopWhiteList;
    private SelectModelField sendBackShopBlackList;
    private ChoiceModelField inviteOpenShopType;
    private SelectModelField inviteOpenShopList;
    private ChoiceModelField pasteTicketType;
    private SelectModelField pasteTicketList;
    private ChoiceModelField throwManureType;
    private SelectModelField throwManureList;
    
    private BooleanModelField manualCollectManure;
    private BooleanModelField taskList;
    private BooleanModelField doTaskOnce;
    private BooleanModelField donate;
    private BooleanModelField nextVillage;
    private BooleanModelField inviteRegister;
    private SelectModelField inviteRegisterList;
    private BooleanModelField assistFriend;
    private SelectModelField assistFriendList;
    
    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(openShopType = new ChoiceModelField("openShopType", "摆摊 | 动作", OpenShopType.NONE, OpenShopType.nickNames));
        modelFields.addField(openShopList = new SelectModelField("openShopList", "摆摊 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(closeShop = new BooleanModelField("closeShop", "收摊 | 开启", false));
        modelFields.addField(closeShopTime = new IntegerModelField("closeShopTime", "收摊 | 摆摊时长(分钟)", 120));
        modelFields.addField(pasteTicketType = new ChoiceModelField("pasteTicketType", "贴罚单 | 动作", PasteTicketType.NONE, PasteTicketType.nickNames));
        modelFields.addField(pasteTicketList = new SelectModelField("pasteTicketList", "贴罚单 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(throwManureType = new ChoiceModelField("throwManureType", "丢肥料 | 动作", ThrowManureType.NONE, ThrowManureType.nickNames));
        modelFields.addField(throwManureList = new SelectModelField("throwManureList", "丢肥料 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(manualCollectManure = new BooleanModelField("manualCollectManure", "收肥料 | 手动收取", false));
        modelFields.addField(sendBackShop = new BooleanModelField("sendBackShop", "请走小摊 | 开启", false));
        modelFields.addField(sendBackShopTime = new IntegerModelField("sendBackShopTime", "请走小摊 | 允许摆摊时长(分钟)", 121));
        modelFields.addField(sendBackShopWhiteList = new SelectModelField("sendBackShopWhiteList", "请走小摊 | 白名单(超时也不赶)", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(sendBackShopBlackList = new SelectModelField("sendBackShopBlackList", "请走小摊 | 黑名单(不超时也赶)", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(inviteOpenShopType = new ChoiceModelField("inviteOpenShopType", "邀请摆摊 | 动作", InviteOpenShopType.NONE, InviteOpenShopType.nickNames));
        modelFields.addField(inviteOpenShopList = new SelectModelField("inviteOpenShopList", "邀请摆摊 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(taskList = new BooleanModelField("taskList", "新村任务 | 加速产币", false));
        modelFields.addField(AutoAntStallTaskList = new BooleanModelField("AutoAntStallTaskList", "新村任务 | 自动黑白名单", true));
        modelFields.addField(AntStallTaskList = new SelectModelField("AntStallTaskList", "新村任务 | 黑名单列表", new LinkedHashSet<>(), AlipayAntStallTaskList::getList));
        modelFields.addField(doTaskOnce = new BooleanModelField("doTaskOnce", "新村任务仅执行一次", false));
        modelFields.addField(donate = new BooleanModelField("donate", "助力就业岗位", false));
        modelFields.addField(nextVillage = new BooleanModelField("nextVillage", "解锁新村新店", false));
        modelFields.addField(inviteRegister = new BooleanModelField("inviteRegister", "邀请开通 | 开启", false));
        modelFields.addField(inviteRegisterList = new SelectModelField("inviteRegisterList", "邀请开通 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(assistFriend = new BooleanModelField("assistFriend", "分享助力 | 开启", false));
        modelFields.addField(assistFriendList = new SelectModelField("assistFriendList", "分享助力 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        return modelFields;
    }
    
    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.farm("任务暂停⏸️蚂蚁新村:当前为仅收能量时间");
            return false;
        }
        return true;
    }
    
    @Override
    public void run() {
        try {
            JSONObject selfHome = querySelfHome();
            if (selfHome == null) {
                return;
            }
            
            selfHomeHandler(selfHome);
            
            //初始任务列表
            if (!Status.hasFlagToday("BlackList::initAntStall")) {
                initAntStallTaskListMap(AutoAntStallTaskList.getValue(), taskList.getValue());
                Status.flagToday("BlackList::initAntStall");
            }
            
            if (throwManureType.getValue() != ThrowManureType.NONE) {
                throwManure();
            }
            if (!manualCollectManure.getValue()) {
                collectManure();
            }
            
            if (closeShop.getValue()) {
                closeShop();
            }
            if (openShopType.getValue() != OpenShopType.NONE) {
                openShop();
            }
            
            if (taskList.getValue()) {
                taskList();
                
            }
            if (assistFriend.getValue()) {
                assistFriend();
            }
            
            if (pasteTicketType.getValue() != PasteTicketType.NONE) {
                pasteTicket();
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "AntStall.start.run err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static JSONObject querySelfHome() {
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.selfHome(""));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return null;
            }
            if (!jo.getBoolean("hasRegister") || jo.getBoolean("hasQuit")) {
                Log.farm("蚂蚁新村⛪请先开启蚂蚁新村");
                return null;
            }
            String currentVillageType = jo.getJSONObject("userInfo").getString("currentVillageType");
            String villageType = jo.getJSONObject("currentVillage").getString("villageType");
            if (!Objects.equals(currentVillageType, villageType)) {
                TimeUtil.sleep(2000);
                jo = new JSONObject(AntStallRpcCall.selfHome(currentVillageType));
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return null;
                }
            }
            return jo;
        }
        catch (Throwable t) {
            Log.i(TAG, "querySelfHome err:");
            Log.printStackTrace(TAG, t);
        }
        return null;
    }
    
    private void selfHomeHandler(JSONObject selfHome) {
        try {
            JSONObject currentVillage = selfHome.getJSONObject("currentVillage");
            if (!canUnlockNewVillage(currentVillage)) {
                if (donate.getValue()) {
                    projectList();
                }
            }
            else {
                if (nextVillage.getValue()) {
                    unlockNewVillage();
                }
            }
            
            JSONObject astReceivableCoinVO = selfHome.getJSONObject("astReceivableCoinVO");
            settleReceivable(astReceivableCoinVO);
            
            JSONObject seatsMap = selfHome.getJSONObject("seatsMap");
            settle(seatsMap);
            sendBack(seatsMap);
        }
        catch (Throwable t) {
            Log.i(TAG, "selfHomeHandler err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    public static void initAntStallTaskListMap(boolean AutoAntStallTaskList, boolean taskList) {
        try {
            //初始化AntStallTaskListMap
            AntStallTaskListMap.load();
            Set<String> blackList = new HashSet<>();
            //blackList.add("到店付款");
            Set<String> whiteList = new HashSet<>();// 从黑名单中移除该任务
            //whiteList.add("逛一逛树");
            for (String task : blackList) {
                AntStallTaskListMap.add(task, task);
            }
            
            if (taskList) {
                String jostr = AntStallRpcCall.taskList();
                if (jostr == null) {
                    return;
                }
                JSONObject jo = new JSONObject(jostr);
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray taskModels = jo.getJSONArray("taskModels");
                    for (int i = 0; i < taskModels.length(); i++) {
                        JSONObject task = taskModels.getJSONObject(i);
                        JSONObject bizInfo = new JSONObject(task.getString("bizInfo"));
                        String title = bizInfo.getString("title");
                        AntStallTaskListMap.add(title, title);
                    }
                }
                //保存任务到配置文件
                AntStallTaskListMap.save();
                Log.record("同步任务🉑新村任务列表");
                
                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntStallTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields AntStall = config.getModelFieldsMap().get("AntStall");
                    SelectModelField AntStallTaskList = (SelectModelField) AntStall.get("AntStallTaskList");
                    if (AntStallTaskList == null) {
                        return;
                    }
                    // 2. 批量添加黑名单任务（确保存在）
                    Set<String> currentValues = AntStallTaskList.getValue();//该处直接返回列表地址
                    if (currentValues != null) {
                        for (String task : blackList) {
                            if (!currentValues.contains(task)) {
                                AntStallTaskList.add(task, 0);
                            }
                        }
                    }
                    currentValues = AntStallTaskList.getValue();//该处直接返回列表地址
                    if (currentValues != null) {
                        
                        // 3. 批量移除白名单任务（从现有列表中删除）
                        for (String task : whiteList) {
                            if (currentValues.contains(task)) {
                                currentValues.remove(task);
                            }
                        }
                    }
                    // 4. 保存配置
                    if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
                        Log.record("黑白名单🈲新村任务自动设置: " + AntStallTaskList.getValue());
                    }
                    else {
                        Log.record("新村任务黑白名单设置失败");
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "initAntStallTaskListMap err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void settleReceivable(JSONObject astReceivableCoinVO) {
        try {
            if (!astReceivableCoinVO.getBoolean("hasCoin")) {
                return;
            }
            double amount = astReceivableCoinVO.getJSONObject("receivableCoin").getDouble("amount");
            JSONObject jo = new JSONObject(AntStallRpcCall.settleReceivable());
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.farm("蚂蚁新村⛪收取小摊结余#获得[" + amount + "木兰币]");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "settleReceivable err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void sendBack(String billNo, String seatId, String shopId, String shopUserId) {
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.shopSendBackPre(billNo, seatId, shopId, shopUserId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject astPreviewShopSettleVO = jo.getJSONObject("astPreviewShopSettleVO");
            JSONObject income = astPreviewShopSettleVO.getJSONObject("income");
            double amount = income.getDouble("amount");
            jo = new JSONObject(AntStallRpcCall.shopSendBack(seatId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.farm("蚂蚁新村⛪请走[" + UserIdMap.getMaskName(shopUserId) + "]的小摊" + (amount > 0 ? "#获得[" + amount + "木兰币]" : ""));
            }
            inviteOpenShop(seatId);
        }
        catch (Throwable t) {
            Log.i(TAG, "sendBack err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private synchronized void inviteOpenShop(String seatId) {
        if (inviteOpenShopType.getValue() == InviteOpenShopType.NONE) {
            return;
        }
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.rankInviteOpen());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            
            JSONArray friendRankList = jo.getJSONArray("friendRankList");
            for (int i = 0; i < friendRankList.length(); i++) {
                JSONObject friend = friendRankList.getJSONObject(i);
                String friendUserId = friend.getString("userId");
                boolean isInviteShop = inviteOpenShopList.getValue().contains(friendUserId);
                if (inviteOpenShopType.getValue() != InviteOpenShopType.INVITE) {
                    isInviteShop = !isInviteShop;
                }
                if (!isInviteShop) {
                    continue;
                }
                if (friend.getBoolean("canOneKeyInviteOpenShop")) {
                    jo = new JSONObject(AntStallRpcCall.oneKeyInviteOpenShop(friendUserId, seatId));
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.farm("蚂蚁新村⛪邀请[" + UserIdMap.getMaskName(friendUserId) + "]来新村摆摊");
                        return;
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "inviteOpenShop err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void sendBack(JSONObject seatsMap) {
        try {
            for (int i = 1; i <= 2; i++) {
                JSONObject seat = seatsMap.getJSONObject("GUEST_0" + i);
                String seatId = seat.getString("seatId");
                if ("FREE".equals(seat.getString("status"))) {
                    inviteOpenShop(seatId);
                    continue;
                }
                // 请走小摊 未开启直接跳过
                if (!sendBackShop.getValue()) {
                    continue;
                }
                String rentLastUser = seat.optString("rentLastUser");
                if (StringUtil.isEmpty(rentLastUser)) {
                    continue;
                }
                // 白名单直接跳过
                if (sendBackShopWhiteList.getValue().contains(rentLastUser)) {
                    continue;
                }
                String rentLastBill = seat.getString("rentLastBill");
                String rentLastShop = seat.getString("rentLastShop");
                // 黑名单直接赶走
                if (sendBackShopBlackList.getValue().contains(rentLastUser)) {
                    sendBack(rentLastBill, seatId, rentLastShop, rentLastUser);
                    continue;
                }
                long bizStartTime = seat.getLong("bizStartTime");
                long endTime = bizStartTime + TimeUnit.MINUTES.toMillis(sendBackShopTime.getValue());
                if (System.currentTimeMillis() > endTime) {
                    sendBack(rentLastBill, seatId, rentLastShop, rentLastUser);
                }
                else {
                    String taskId = "SB|" + seatId;
                    if (!hasChildTask(taskId)) {
                        addChildTask(new ChildModelTask(taskId, "SB", () -> {
                            if (sendBackShop.getValue()) {
                                sendBack(rentLastBill, seatId, rentLastShop, rentLastUser);
                            }
                        }, endTime));
                        Log.record("添加蹲点请走⛪在[" + TimeUtil.getCommonDate(endTime) + "]执行");
                    } /*else {
                        addChildTask(new ChildModelTask(taskId, "SB", () -> {
                            if (stallAllowOpenReject.getValue()) {
                                sendBack(rentLastBill, seatId, rentLastShop, rentLastUser);
                            }
                        }, endTime));
                    }*/
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "sendBack err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void settle(JSONObject seatsMap) {
        try {
            JSONObject seat = seatsMap.getJSONObject("MASTER");
            if (seat.has("coinsMap")) {
                JSONObject coinsMap = seat.getJSONObject("coinsMap");
                JSONObject master = coinsMap.getJSONObject("MASTER");
                String assetId = master.getString("assetId");
                double settleCoin = master.getJSONObject("money").getDouble("amount");
                boolean fullShow = master.getBoolean("fullShow");
                if (fullShow || settleCoin > 100) {
                    JSONObject jo = new JSONObject(AntStallRpcCall.settle(assetId, settleCoin));
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.farm("蚂蚁新村⛪收取经营所得#获得[" + settleCoin + "木兰币]");
                    }
                }
            }
            
        }
        catch (Throwable t) {
            Log.i(TAG, "settle err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void closeShop() {
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.shopList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray astUserShopList = jo.getJSONArray("astUserShopList");
            for (int i = 0; i < astUserShopList.length(); i++) {
                JSONObject shop = astUserShopList.getJSONObject(i);
                if (Objects.equals("OPEN", shop.getString("status"))) {
                    JSONObject rentLastEnv = shop.getJSONObject("rentLastEnv");
                    long gmtLastRent = rentLastEnv.getLong("gmtLastRent");
                    long shopTime = gmtLastRent + TimeUnit.MINUTES.toMillis(closeShopTime.getValue());
                    String shopId = shop.getString("shopId");
                    String rentLastBill = shop.getString("rentLastBill");
                    String rentLastUser = shop.getString("rentLastUser");
                    if (System.currentTimeMillis() > shopTime) {
                        closeShop(shopId, rentLastBill, rentLastUser);
                    }
                    else {
                        String taskId = "SH|" + shopId;
                        if (!hasChildTask(taskId)) {
                            addChildTask(new ChildModelTask(taskId, "SH", () -> {
                                closeShop(shopId, rentLastBill, rentLastUser);
                                TimeUtil.sleep(300L);
                                if (openShopType.getValue() != OpenShopType.NONE) {
                                    openShop();
                                }
                            }, shopTime));
                            Log.record("添加蹲点收摊⛪在[" + TimeUtil.getCommonDate(shopTime) + "]执行");
                        } /*else {
                                addChildTask(new ChildModelTask(taskId, "SH", () -> {
                                    if (stallAutoClose.getValue()) {
                                        shopClose(shopId, rentLastBill, rentLastUser);
                                    }
                                }, shopTime));
                            }*/
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "closeShop err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private synchronized void openShop() {
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.shopList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray astUserShopList = jo.getJSONArray("astUserShopList");
            Queue<String> shopIds = new LinkedList<>();
            for (int i = 0; i < astUserShopList.length(); i++) {
                JSONObject astUserShop = astUserShopList.getJSONObject(i);
                if ("FREE".equals(astUserShop.getString("status"))) {
                    shopIds.add(astUserShop.getString("shopId"));
                }
            }
            rankCoinDonate(shopIds);
        }
        catch (Throwable t) {
            Log.i(TAG, "openShop err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void rankCoinDonate(Queue<String> shopIds) {
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.rankCoinDonate());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray friendRankList = jo.getJSONArray("friendRankList");
            List<Seat> seats = new ArrayList<>();
            for (int i = 0; i < friendRankList.length(); i++) {
                JSONObject friendRank = friendRankList.getJSONObject(i);
                if (friendRank.getBoolean("canOpenShop")) {
                    String userId = friendRank.getString("userId");
                    boolean isStallOpen = openShopList.getValue().contains(userId);
                    if (openShopType.getValue() != OpenShopType.OPEN) {
                        isStallOpen = !isStallOpen;
                    }
                    if (!isStallOpen) {
                        continue;
                    }
                    int hot = friendRank.getInt("hot");
                    seats.add(new Seat(userId, hot));
                }
            }
            friendHomeOpenShop(seats, shopIds);
        }
        catch (Throwable t) {
            Log.i(TAG, "rankCoinDonate err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private Boolean openShop(String seatId, String userId, String shopId) {
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.shopOpen(seatId, userId, shopId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.farm("蚂蚁新村⛪在[" + UserIdMap.getMaskName(userId) + "]的新村摆摊");
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "openShop err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private void friendHomeOpenShop(List<Seat> seats, Queue<String> shopIds) {
        Collections.sort(seats, (e1, e2) -> e2.hot - e1.hot);
        String shopId = null;
        String selfId = UserIdMap.getCurrentUid();
        for (Seat seat : seats) {
            if (shopId == null) {
                shopId = shopIds.poll();
                if (shopId == null) {
                    return;
                }
            }
            String userId = seat.userId;
            try {
                JSONObject jo = new JSONObject(AntStallRpcCall.friendHome(userId));
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                JSONObject seatsMap = jo.getJSONObject("seatsMap");
                String seatId = null;
                boolean canOpenShop = true;
                
                jo = seatsMap.getJSONObject("GUEST_02");
                if (jo.getBoolean("canOpenShop")) {
                    seatId = jo.getString("seatId");
                }
                else if (Objects.equals(selfId, jo.getString("rentLastUser"))) {
                    canOpenShop = false;
                }
                jo = seatsMap.getJSONObject("GUEST_01");
                if (jo.getBoolean("canOpenShop")) {
                    seatId = jo.getString("seatId");
                }
                else if (Objects.equals(selfId, jo.getString("rentLastUser"))) {
                    canOpenShop = false;
                }
                if (canOpenShop && seatId != null) {
                    if (openShop(seatId, userId, shopId)) {
                        shopId = null;
                    }
                }
            }
            catch (Throwable t) {
                Log.i(TAG, "friendHomeOpenShop err:");
                Log.printStackTrace(TAG, t);
            }
        }
    }
    
    private void closeShop(String shopId, String billNo, String userId) {
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.shopClosePre(shopId, billNo));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject income = jo.getJSONObject("astPreviewShopSettleVO").getJSONObject("income");
            jo = new JSONObject(AntStallRpcCall.shopClose(shopId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                double amount = income.getDouble("amount");
                Log.farm("蚂蚁新村⛪在[" + UserIdMap.getMaskName(userId) + "]的新村收摊#获得[" + amount + "木兰币]");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "closeShop err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void taskList() {
        try {
            String jostr = AntStallRpcCall.taskList();
            if (jostr == null) {
                return;
            }
            JSONObject jo = new JSONObject(jostr);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                if (jo.has("errorMessage")) {
                    String errorMessage = jo.optString("errorMessage");
                    //如果出错今天停止兑换
                    if (errorMessage.equals("系统繁忙，请稍后再试。")) {
                        Status.flagToday("antstall::blockTask");
                    }
                }
                return;
            }
            JSONObject signListModel = jo.getJSONObject("signListModel");
            if (!signListModel.getBoolean("currentKeySigned")) {
                signToday();
            }
            
            JSONArray taskModels = jo.getJSONArray("taskModels");
            for (int i = 0; i < taskModels.length(); i++) {
                JSONObject task = taskModels.getJSONObject(i);
                String taskStatus = task.getString("taskStatus");
                if (Objects.equals(TaskStatus.RECEIVED.name(), taskStatus)) {
                    continue;
                }
                String taskType = task.getString("taskType");
                JSONObject bizInfo = new JSONObject(task.getString("bizInfo"));
                String title = bizInfo.getString("title");
                //黑名单任务跳过
                if (AntStallTaskList.getValue().contains(title)) {
                    continue;
                }
                
                if (Objects.equals(TaskStatus.TODO.name(), taskStatus)) {
                    if (!doStallTask(task, title)) {
                        continue;
                    }
                    Log.farm("新村任务🧾完成[" + title + "]");
                    TimeUtil.sleep(1000);
                }
                receiveTaskAward(taskType, title);
            }
            //为了防止异常，新村任务只做一次
            if (doTaskOnce.getValue()) {
                Status.flagToday("antstall::blockTask");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "taskList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private Boolean doStallTask(JSONObject task, String title) {
        try {
            String taskType = task.getString("taskType");
            JSONObject bizInfo = new JSONObject(task.getString("bizInfo"));
            if (Objects.equals("VISIT_AUTO_FINISH", bizInfo.getString("actionType")) || taskTypeList.contains(taskType)) {
                return finishTask(taskType, title);
            }
            switch (taskType) {
                case "ANTSTALL_NORMAL_DAILY_QA":
                    return ReadingDada.answerQuestion(bizInfo);
                case "ANTSTALL_NORMAL_INVITE_REGISTER":
                    inviteRegister();
                    return false;
                case "ANTSTALL_P2P_DAILY_SHARER":
                    return false;
                case "ANTSTALL_TASK_taojinbihuanduan": {
                    // 进入淘宝芭芭农场
                    String sceneCode = JsonUtil.getValueByPath(task, "bizInfo.targetUrl").replaceAll(".*sceneCode%3D([^&]+).*", "$1");
                    if (sceneCode.isEmpty()) {
                        return false;
                    }
                    JSONObject jo = new JSONObject(AntStallRpcCall.queryCallAppSchema(sceneCode));
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        return false;
                    }
                    TimeUtil.sleep(5000);
                    querySelfHome();
                    AntStallRpcCall.taskList();
                    TimeUtil.sleep(5000);
                    return false;
                }
                case "ANTSTALL_XLIGHT_VARIABLE_AWARD": {
                    //【木兰市集】逛精选好物
                    JSONObject jo = new JSONObject(AntStallRpcCall.xlightPlugin());
                    if (!jo.has("playingResult")) {
                        Log.i(TAG, "taskList.xlightPlugin err:" + jo.optString("resultDesc"));
                        return false;
                    }
                    jo = jo.getJSONObject("playingResult");
                    String pid = jo.getString("playingBizId");
                    JSONArray jsonArray = (JSONArray) JsonUtil.getValueByPathObject(jo, "eventRewardDetail.eventRewardInfoList");
                    if (jsonArray == null || jsonArray.length() == 0) {
                        return false;
                    }
                    TimeUtil.sleep(5000);
                    for (int j = 0; j < jsonArray.length(); j++) {
                        try {
                            JSONObject jsonObject = jsonArray.getJSONObject(j);
                            TimeUtil.sleep(5000);
                            jo = new JSONObject(AntStallRpcCall.finish(pid, jsonObject));
                            if (!jo.optBoolean("success")) {
                                Log.i(TAG, "taskList.finish err:" + jo.optString("resultDesc"));
                            }
                        }
                        catch (Throwable t) {
                            Log.i(TAG, "taskList for err:");
                            Log.printStackTrace(TAG, t);
                        }
                    }
                    return true;
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "doStallTask err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private void signToday() {
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.signToday());
            if (MessageUtil.checkResultCode(TAG, jo)) {
                StringBuilder signReward = new StringBuilder();
                JSONArray signRewardModelList = jo.getJSONArray("signRewardModelList");
                for (int i = 0; i < signRewardModelList.length(); i++) {
                    jo = signRewardModelList.getJSONObject(i);
                    if (i > 0) {
                        signReward.append(";");
                    }
                    int count = jo.getInt("count");
                    String type = jo.getString("type");
                    if (Objects.equals("ANTSTALL_HOT", type)) {
                        signReward.append("产速增加").append(count).append("/小时");
                    }
                    else {
                        signReward.append(type).append("*").append(count);
                    }
                }
                Log.farm("新村任务📅签到#获得[" + signReward + "]");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "signToday err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static void receiveTaskAward(String taskType, String title) {
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.receiveTaskAward(taskType));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.farm("新村任务🎖️领取[" + title + "]奖励#获得[产速增加" + jo.getInt("incAwardCount") + "/小时]");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "receiveTaskAward err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static Boolean finishTask(String taskType, String title) {
        // String s = AntStallRpcCall.finishTask(FriendIdMap.currentUid + "_" + taskType, taskType);
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.finishTask(taskType));
            //检查并标记黑名单任务
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntStallTaskList", title, jo);
            return MessageUtil.checkSuccess(TAG, jo);
        }
        catch (Throwable t) {
            Log.i(TAG, "finishTask err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private void inviteRegister() {
        if (!inviteRegister.getValue()) {
            return;
        }
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.rankInviteRegister());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray friendRankList = jo.optJSONArray("friendRankList");
            if (friendRankList == null || friendRankList.length() <= 0) {
                return;
            }
            for (int i = 0; i < friendRankList.length(); i++) {
                JSONObject friend = friendRankList.getJSONObject(i);
                if (!friend.optBoolean("canInviteRegister", false) || !"UNREGISTER".equals(friend.getString("userStatus"))) {
                    continue;
                }
                /* 名单筛选 */
                String userId = friend.getString("userId");
                if (!inviteRegisterList.getValue().contains(userId)) {
                    continue;
                }
                jo = new JSONObject(AntStallRpcCall.friendInviteRegister(userId));
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    Log.farm("蚂蚁新村⛪邀请[" + UserIdMap.getMaskName(userId) + "]开通新村");
                    return;
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "inviteRegister err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private String shareP2P() {
        try {
            String s = AntStallRpcCall.shareP2P();
            JSONObject jo = new JSONObject(s);
            if (jo.optBoolean("success")) {
                String shareId = jo.getString("shareId");
                Log.record("蚂蚁新村⛪[分享助力]");
                return shareId;
            }
            else {
                Log.record("shareP2P err:" + " " + s);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "shareP2P err:");
            Log.printStackTrace(TAG, t);
        }
        return null;
    }
    
    /**
     * 助力好友
     */
    private void assistFriend() {
        try {
            if (Status.hasFlagToday("stall::shareP2PLimit")) {
                return;
            }
            Set<String> friendSet = assistFriendList.getValue();
            for (String friendUserId : friendSet) {
                if (!Status.canStallShareP2PToday(friendUserId)) {
                    continue;
                }
                JSONObject jo = new JSONObject(AntStallRpcCall.achieveBeShareP2P(friendUserId));
                TimeUtil.sleep(5000);
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    Log.farm("新村助力🎉助力[" + UserIdMap.getMaskName(friendUserId) + "]成功");
                    Status.stallShareP2PToday(friendUserId);
                }
                else if (Objects.equals("600000027", jo.getString("code"))) {
                    Status.flagToday("stall::shareP2PLimit");
                    return;
                }
                else {
                    Status.flagToday("stall::shareP2PLimit::" + friendUserId);
                }
                // 600000010 人传人邀请关系不存在
                // 600000015 人传人完成邀请，非法用户
                // 600000031 人传人完成邀请过于频繁
                // 600000027 今日助力他人次数上限
                // 600000028 被助力次数上限
                // 600000029 人传人分享一对一接受邀请达到限制
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "assistFriend err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    // 捐赠项目
    private void projectList() {
        if (!canDonateToday()) {
            return;
        }
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.projectList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            // 获取项目列表中的 astProjectVOS 数组
            JSONArray astProjectVOS = jo.getJSONArray("astProjectVOS");
            for (int i = 0; i < astProjectVOS.length(); i++) {
                jo = astProjectVOS.getJSONObject(i);
                // status: ONLINE FINISH
                if (!Objects.equals("ONLINE", jo.getString("status"))) {
                    break;
                }
                if (!projectDetail(jo.getString("projectId"))) {
                    break;
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "projectList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private Boolean projectDetail(String projectId) {
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.projectDetail(projectId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            int currentCoin = jo.getJSONObject("astUserInfoVO").getJSONObject("currentCoin").getInt("cent");
            int donateAmount = jo.getJSONObject("astProjectVO").getJSONObject("jobModel").getJSONObject("donateAmount").getInt("cent");
            if (currentCoin < donateAmount) {
                return false;
            }
            return projectDonate(projectId);
        }
        catch (Throwable t) {
            Log.i(TAG, "projectDetail err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private Boolean projectDonate(String projectId) {
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.projectDonate(projectId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONObject donateBillVO = jo.getJSONObject("donateBillVO");
                String projectTitle = donateBillVO.getString("projectTitle");
                int donateAmount = donateBillVO.getInt("donateAmount");
                Log.farm("公益捐赠❤️[捐木兰币:" + projectTitle + "]#捐赠[" + (donateAmount / 100) + "木兰币]");
                JSONObject astUserVillageVO = jo.getJSONObject("astUserVillageVO");
                if (canUnlockNewVillage(astUserVillageVO)) {
                    if (nextVillage.getValue()) {
                        return unlockNewVillage();
                    }
                    return false;
                }
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "projectDonate err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private Boolean canDonateToday() {
        if (Status.hasFlagToday("stall::donate")) {
            return false;
        }
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.letterList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray ja = jo.getJSONArray("letterList");
            if (ja.length() == 0) {
                return true;
            }
            jo = ja.getJSONObject(0);
            long gmtBiz = jo.getLong("gmtBiz");
            if (TimeUtil.isLessThanNowOfDays(gmtBiz)) {
                return true;
            }
            Status.flagToday("stall::donate");
        }
        catch (Throwable t) {
            Log.i(TAG, "canDonateToday err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    // 进入下一村
    private Boolean unlockNewVillage() {
        try {
            if (!nextVillage()) {
                return false;
            }
            JSONObject jo = querySelfHome();
            if (jo == null) {
                return false;
            }
            jo = jo.getJSONObject("currentVillage");
            String villageName = jo.getString("villageName");
            String villageDesc = jo.getJSONObject("properties").getString("villageDesc");
            Log.farm("蚂蚁新村⛪解锁[" + villageName + "]#" + villageDesc);
            return true;
        }
        catch (Throwable t) {
            Log.i(TAG, "unlockNewVillage err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private Boolean canUnlockNewVillage(JSONObject currentVillage) {
        try {
            int donateCount = currentVillage.getInt("donateCount");
            int donateLimit = currentVillage.getInt("donateLimit");
            return donateCount >= donateLimit;
        }
        catch (Throwable t) {
            Log.i(TAG, "canUnlockNewVillage err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private static Boolean nextVillage() {
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.nextVillage());
            return MessageUtil.checkResultCode(TAG, jo);
        }
        catch (Throwable t) {
            Log.i(TAG, "nextVillage err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private void collectManure() {
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.queryManureInfo());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject astManureInfoVO = jo.getJSONObject("astManureInfoVO");
            if (astManureInfoVO.optBoolean("hasManure")) {
                int manure = astManureInfoVO.getInt("manure");
                jo = new JSONObject(AntStallRpcCall.collectManure());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    Log.farm("蚂蚁新村⛪收取[" + manure + "g肥料]");
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "collectManure err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private Boolean throwManure(JSONArray dynamicList) {
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.throwManure(dynamicList));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                int income = jo.getInt("income");
                Log.farm("蚂蚁新村⛪一键丢肥料#讨回[" + income + "木兰币]");
                return true;
            }
            else if (Objects.equals("B_OVER_LIMIT_COUNT_OF_THROW_FROM", jo.optString("resultCode"))) {
                Status.flagToday("stall::throwManureLimit");
            }
        }
        catch (Throwable th) {
            Log.i(TAG, "throwManure err:");
            Log.printStackTrace(TAG, th);
        }
        finally {
            TimeUtil.sleep(1000);
        }
        return false;
    }
    
    private void throwManure() {
        if (Status.hasFlagToday("stall::throwManureLimit")) {
            return;
        }
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.dynamicLoss());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray astLossDynamicVOS = jo.getJSONArray("astLossDynamicVOS");
            JSONArray dynamicList = new JSONArray();
            for (int i = 0; i < astLossDynamicVOS.length(); i++) {
                JSONObject lossDynamic = astLossDynamicVOS.getJSONObject(i);
                if (lossDynamic.has("specialEmojiVO")) {
                    continue;
                }
                String objectId = lossDynamic.getString("objectId");
                boolean isThrowManure = throwManureList.getValue().contains(objectId);
                if (throwManureType.getValue() != ThrowManureType.THROW) {
                    isThrowManure = !isThrowManure;
                }
                if (!isThrowManure) {
                    continue;
                }
                JSONObject dynamic = new JSONObject();
                dynamic.put("bizId", lossDynamic.getString("bizId"));
                dynamic.put("bizType", lossDynamic.getString("bizType"));
                dynamicList.put(dynamic);
                if (dynamicList.length() == 5) {
                    if (!throwManure(dynamicList)) {
                        return;
                    }
                    dynamicList = new JSONArray();
                }
            }
            if (dynamicList.length() > 0) {
                throwManure(dynamicList);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "throwManure err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 贴罚单
     */
    private void pasteTicket() {
        if (Status.hasFlagToday("stall::pasteTicketLimit")) {
            return;
        }
        try {
            while (true) {
                JSONObject jo = new JSONObject(AntStallRpcCall.nextTicketFriend());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                if (jo.getInt("canPasteTicketCount") == 0) {
                    Log.record("蚂蚁新村👍今日罚单已贴完");
                    Status.flagToday("stall::pasteTicketLimit");
                    return;
                }
                if (!jo.has("friendUserId")) {
                    return;
                }
                pasteTicket(jo.getString("friendUserId"));
            }
        }
        catch (Throwable th) {
            Log.i(TAG, "pasteTicket err:");
            Log.printStackTrace(TAG, th);
        }
    }
    
    private void pasteTicket(String friendUserId) {
        boolean isStallTicket = pasteTicketList.getValue().contains(friendUserId);
        if (pasteTicketType.getValue() != PasteTicketType.TICKET) {
            isStallTicket = !isStallTicket;
        }
        if (!isStallTicket) {
            return;
        }
        try {
            JSONObject jo = new JSONObject(AntStallRpcCall.friendHome(friendUserId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject seatsMap = jo.getJSONObject("seatsMap");
            for (int i = 1; i <= 2; i++) {
                jo = seatsMap.getJSONObject("GUEST_0" + i);
                if (jo.getBoolean("canOpenShop") || !jo.getBoolean("overTicketProtection")) {
                    continue;
                }
                jo = new JSONObject(AntStallRpcCall.pasteTicket(jo.getString("rentLastBill"), jo.getString("seatId"), jo.getString("rentLastShop"), jo.getString("rentLastUser"), jo.getString("userId")));
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    double amount = jo.getJSONObject("pasteIncome").getDouble("amount");
                    Log.farm("蚂蚁新村🚫在[" + UserIdMap.getMaskName(friendUserId) + "]的新村贴罚单#获得[" + amount + "木兰币]");
                }
                TimeUtil.sleep(1000);
            }
        }
        catch (Throwable th) {
            Log.i(TAG, "pasteTicket err:");
            Log.printStackTrace(TAG, th);
        }
    }
    
    public interface OpenShopType {
        
        int NONE = 0;
        int OPEN = 1;
        int NOT_OPEN = 2;
        
        String[] nickNames = {"不摆摊", "摆摊已选好友", "摆摊未选好友"};
        
    }
    
    public interface PasteTicketType {
        
        int NONE = 0;
        int TICKET = 1;
        int NOT_TICKET = 2;
        
        String[] nickNames = {"不贴罚单", "贴已选好友", "贴未选好友"};
        
    }
    
    public interface ThrowManureType {
        
        int NONE = 0;
        int THROW = 1;
        int NOT_THROW = 2;
        
        String[] nickNames = {"不丢肥料", "丢已选好友", "丢未选好友"};
        
    }
    
    public interface InviteOpenShopType {
        
        int NONE = 0;
        int INVITE = 1;
        int NOT_INVITE = 2;
        
        String[] nickNames = {"不邀请摆摊", "邀请已选好友", "邀请未选好友"};
    }
    
}