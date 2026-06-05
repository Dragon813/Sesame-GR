package io.github.lazyimmortal.sesame.model.task.antDodo;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.lazyimmortal.sesame.data.ModelFields;
import io.github.lazyimmortal.sesame.data.ModelGroup;
import io.github.lazyimmortal.sesame.data.modelFieldExt.BooleanModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectModelField;
import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.entity.AlipayUser;
import io.github.lazyimmortal.sesame.entity.CustomOption;
import io.github.lazyimmortal.sesame.model.base.TaskCommon;
import io.github.lazyimmortal.sesame.model.task.antFarm.AntFarm.TaskStatus;
import io.github.lazyimmortal.sesame.model.task.antForest.AntForestV2;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.MessageUtil;
import io.github.lazyimmortal.sesame.util.Status;
import io.github.lazyimmortal.sesame.util.TimeUtil;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class AntDodo extends ModelTask {
    private static final String TAG = AntDodo.class.getSimpleName();
    
    @Override
    public String getName() {
        return "物种";
    }
    
    @Override
    public ModelGroup getGroup() {
        return ModelGroup.FOREST;
    }
    
    private BooleanModelField useProp;
    private BooleanModelField useUniversalCardBookCollectedhasCollected;
    private SelectModelField usePropList;
    private ChoiceModelField useCollectTimingType;
    private ChoiceModelField useUniversalCardBookStatusType;
    private ChoiceModelField useUniversalCardBookCollectedStatusType;
    private ChoiceModelField useUniversalCardMedalGenerationStatusType;
    private ChoiceModelField useUniversalCardFantasticLevelType;
    private BooleanModelField bookMedal;
    private SelectModelField bookMedalOptions;
    private ChoiceModelField collectToFriendType;
    private SelectModelField collectToFriendList;
    private BooleanModelField giftToFriend;
    private ChoiceModelField giftToFriendBookStatusType;
    private ChoiceModelField giftToFriendBookCollectedStatusType;
    private ChoiceModelField giftToFriendMedalGenerationStatusType;
    private ChoiceModelField giftToFriendFantasticLevelType;
    private SelectModelField giftToFriendList;
    
    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(useProp = new BooleanModelField("useProp", "使用道具 | 开启", false));
        modelFields.addField(usePropList = new SelectModelField("usePropList", "使用道具 | 道具列表", new LinkedHashSet<>(), CustomOption::getAntDodoPropList));
        modelFields.addField(useCollectTimingType = new ChoiceModelField("useCollectTimingType", "抽卡道具 | 使用时机", TimingType.EVERY_DAY, TimingType.nickNames));
        modelFields.addField(useUniversalCardBookStatusType = new ChoiceModelField("useUniversalCardBookStatusType", "万能卡片 | 图鉴状态类型", BookStatusType.END, BookStatusType.nickNames));
        modelFields.addField(useUniversalCardBookCollectedStatusType = new ChoiceModelField("useUniversalCardBookCollectedStatusType", "万能卡片 | 图鉴收集状态", BookCollectedStatusType.ALL, BookCollectedStatusType.nickNames));
        modelFields.addField(useUniversalCardBookCollectedhasCollected = new BooleanModelField("useUniversalCardBookCollectedhasCollected", "万能卡片 | 优先兑换未获得状态卡片", false));
        modelFields.addField(useUniversalCardMedalGenerationStatusType = new ChoiceModelField("useUniversalCardMedalGenerationStatusType", "万能卡片 | 勋章合成状态", MedalGenerationStatusType.ALL, MedalGenerationStatusType.nickNames));
        modelFields.addField(useUniversalCardFantasticLevelType = new ChoiceModelField("useUniversalCardFantasticLevelType", "万能卡片 | 最低等级", FantasticLevelType.MAGIC, FantasticLevelType.nickNames));
        modelFields.addField(bookMedal = new BooleanModelField("bookMedal", "图鉴勋章 | 开启", false));
        modelFields.addField(bookMedalOptions = new SelectModelField("bookMedalOptions", "图鉴勋章 | 选项", new LinkedHashSet<>(), CustomOption::getAntDodoBookMedalOptions));
        modelFields.addField(collectToFriendType = new ChoiceModelField("collectToFriendType", "帮抽卡片 | 动作", CollectToFriendType.NONE, CollectToFriendType.nickNames));
        modelFields.addField(collectToFriendList = new SelectModelField("collectToFriendList", "帮抽卡片 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(giftToFriend = new BooleanModelField("giftToFriend", "赠送卡片 | 开启", false));
        modelFields.addField(giftToFriendBookStatusType = new ChoiceModelField("giftToFriendBookStatusType", "赠送卡片 | " + "图鉴状态类型", BookStatusType.ALL, BookStatusType.nickNames));
        modelFields.addField(giftToFriendBookCollectedStatusType = new ChoiceModelField("giftToFriendBookCollectedStatusType", "赠送卡片 | 图鉴收集状态", BookCollectedStatusType.ALL, BookCollectedStatusType.nickNames));
        modelFields.addField(giftToFriendMedalGenerationStatusType = new ChoiceModelField("giftToFriendMedalGenerationStatusType", "赠送卡片 | 勋章合成状态", MedalGenerationStatusType.ALL, MedalGenerationStatusType.nickNames));
        modelFields.addField(giftToFriendFantasticLevelType = new ChoiceModelField("giftToFriendFantasticLevelType", "赠送卡片 | 最低等级", FantasticLevelType.COMMON, FantasticLevelType.nickNames));
        modelFields.addField(giftToFriendList = new SelectModelField("giftToFriendList", "赠送卡片 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList, "会赠送所有满足条件的卡片给已选择的好友"));
        return modelFields;
    }
    
    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.forest("任务暂停⏸️神奇物种:当前为仅收能量时间");
            return false;
        }
        return true;
    }
    
    @Override
    public void run() {
        try {
            collect();
            taskList();
            if (useProp.getValue()) {
                propList();
            }
            if (collectToFriendType.getValue() != CollectToFriendType.NONE) {
                collectToFriend();
            }
            if (bookMedal.getValue()) {
                generateBookMedal();
            }
            if (giftToFriend.getValue()) {
                giftToFriend();
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "AntoDodo.start.run err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /*
     * 神奇物种
     */
    private long getEndDateTime() {
        try {
            JSONObject jo = new JSONObject(AntDodoRpcCall.homePage());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return 0;
            }
            jo = jo.getJSONObject("data");
            jo = jo.getJSONObject("animalBook");
            String endDate = jo.getString("endDate") + " 23:59:59";
            return Log.timeToStamp(endDate);
        }
        catch (Throwable t) {
            Log.i(TAG, "getEndDateTime err:");
            Log.printStackTrace(TAG, t);
        }
        return 0;
    }
    
    private boolean isLastDay() {
        return getEndDateTime() - TimeUnit.DAYS.toMillis(1) < System.currentTimeMillis();
    }
    
    private void collect() {
        if (Status.hasFlagToday("dodo::collect")) {
            return;
        }
        try {
            JSONObject jo = new JSONObject(AntDodoRpcCall.queryAnimalStatus());
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONObject data = jo.getJSONObject("data");
                if (data.getBoolean("collect")) {
                    Log.record("神奇物种卡片今日收集完成！");
                }
                else {
                    collectAnimalCard();
                }
                Status.flagToday("dodo::collect");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "collect err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void collectAnimalCard() {
        try {
            JSONObject jo = new JSONObject(AntDodoRpcCall.homePage());
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONObject data = jo.getJSONObject("data");
                JSONArray ja = data.getJSONArray("limit");
                int index = -1;
                for (int i = 0; i < ja.length(); i++) {
                    jo = ja.getJSONObject(i);
                    if ("DAILY_COLLECT".equals(jo.getString("actionCode"))) {
                        index = i;
                        break;
                    }
                }
                if (index >= 0) {
                    int leftFreeQuota = jo.getInt("leftFreeQuota");
                    for (int j = 0; j < leftFreeQuota; j++) {
                        jo = new JSONObject(AntDodoRpcCall.collect());
                        if (MessageUtil.checkResultCode(TAG, jo)) {
                            data = jo.getJSONObject("data");
                            JSONObject animal = data.getJSONObject("animal");
                            Log.forest("神奇物种🦕每日抽卡" + getAnimalInfo(animal));
                            checkAnimalAndGiftToFriend(animal);
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "collectAnimalCard err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void taskList() {
        try {
            JSONObject jo = new JSONObject(AntDodoRpcCall.taskList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("data");
            JSONArray taskGroupInfoList = jo.optJSONArray("taskGroupInfoList");
            if (taskGroupInfoList == null) {
                return;
            }
            for (int i = 0; i < taskGroupInfoList.length(); i++) {
                JSONObject antDodoTask = taskGroupInfoList.getJSONObject(i);
                String taskGroupName = antDodoTask.getString("taskGroupName");
                JSONArray taskInfoList = antDodoTask.getJSONArray("taskInfoList");
                for (int j = 0; j < taskInfoList.length(); j++) {
                    JSONObject taskInfo = taskInfoList.getJSONObject(j);
                    JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo");
                    String taskStatus = taskBaseInfo.getString("taskStatus");
                    if (TaskStatus.RECEIVED.name().equals(taskStatus)) {
                        continue;
                    }
                    String sceneCode = taskBaseInfo.getString("sceneCode");
                    String taskType = taskBaseInfo.getString("taskType");
                    if (TaskStatus.TODO.name().equals(taskStatus)) {
                        if ("SEND_FRIEND_CARD".equals(taskType) || "AD_BIODIVERSITY_MASTERCARD".equals(taskType)) {
                            if (!finishTask(sceneCode, taskType, taskGroupName)) {
                                continue;
                            }
                        }
                        else {
                            continue;
                        }
                    }
                    receiveTaskAward(sceneCode, taskType, taskGroupName);
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "taskList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private Boolean finishTask(String sceneCode, String taskType, String taskTitle) {
        try {
            JSONObject jo = new JSONObject(AntDodoRpcCall.finishTask(sceneCode, taskType));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.forest("神奇物种🦕完成[" + taskTitle + "]");
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "finishTask err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private void receiveTaskAward(String sceneCode, String taskType, String taskTitle) {
        try {
            JSONObject jo = new JSONObject(AntDodoRpcCall.receiveTaskAward(sceneCode, taskType));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.forest("神奇物种🦕领取[" + taskTitle + "]奖励");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "receiveTaskAward err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void propList() {
        try {
            th:
            do {
                JSONObject jo = new JSONObject(AntDodoRpcCall.propList());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    break;
                }
                jo = jo.getJSONObject("data");
                JSONArray propList = jo.getJSONArray("propList");
                for (int i = 0; i < propList.length(); i++) {
                    JSONObject prop = propList.getJSONObject(i);
                    String propType = prop.getString("propType");
                    String propGroup = prop.getJSONObject("propConfig").getString("propGroup");
                    JSONArray propIdList = prop.getJSONArray("propIdList");
                    String propId = propIdList.getString(0);
                    long recentExpireTime = prop.getLong("recentExpireTime");
                    boolean willExpireSoon = recentExpireTime - TimeUnit.DAYS.toMillis(1) < System.currentTimeMillis();
                    boolean isUseProp = usePropList.getValue().contains(propType);
                    if (!isUseProp && !willExpireSoon) {
                        continue;
                    }
                    if (PropGroup.UNIVERSAL_CARD.name().equals(propGroup)) {
                        if (!usePropUniversalCard(propId, propType)) {
                            continue;
                        }
                    }
                    else {
                        if (PropGroup.COLLECT_ANIMAL.name().equals(propGroup) && !willExpireSoon && useCollectTimingType.getValue() == TimingType.LAST_DAY && !isLastDay()) {
                            continue;
                        }
                        if (!consumeProp(propId, propType)) {
                            continue;
                        }
                    }
                    if (prop.optInt("holdsNum", 1) > 1) {
                        continue th;
                    }
                }
                break;
            }
            while (true);
        }
        catch (Throwable th) {
            Log.i(TAG, "propList err:");
            Log.printStackTrace(TAG, th);
        }
    }
    
    // 使用万能卡
    private Boolean usePropUniversalCard(String propId, String propType) {
        try {
            boolean hasMore;
            int pageStart = 0;
            JSONObject animal = null;
            do {
                JSONObject jo = new JSONObject(AntDodoRpcCall.queryBookList(9, pageStart));
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    break;
                }
                jo = jo.getJSONObject("data");
                hasMore = jo.getBoolean("hasMore");
                pageStart += 9;
                JSONArray bookForUserList = jo.getJSONArray("bookForUserList");
                for (int i = 0; i < bookForUserList.length(); i++) {
                    jo = bookForUserList.getJSONObject(i);
                    if (isQueryBookInfo(jo, 0)) {
                        JSONObject animalBookResult = jo.getJSONObject("animalBookResult");
                        String bookId = animalBookResult.getString("bookId");
                        animal = queryUniversalAnimal(bookId, animal);
                    }
                }
            }
            while (hasMore);
            if (animal != null && consumeProp(propId, propType, animal.getString("animalId"))) {
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "usePropUniversalCard err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private Boolean isQueryBookInfo(JSONObject bookForUser, int type) {
        int statusType = type == 0 ? useUniversalCardBookStatusType.getValue() : giftToFriendBookStatusType.getValue();
        String bookStatus = bookForUser.optString("bookStatus");
        if (!BookStatus.valueOf(bookStatus).match(BookStatusType.types[statusType])) {
            return false;
        }
        
        int bookCollectedStatusType = type == 0 ? useUniversalCardBookCollectedStatusType.getValue() : giftToFriendBookCollectedStatusType.getValue();
        String bookCollectedStatus = bookForUser.optString("bookCollectedStatus");
        if (!BookCollectedStatus.valueOf(bookCollectedStatus).match(BookCollectedStatusType.types[bookCollectedStatusType])) {
            return false;
        }
        
        int medalGenerationStatusType = type == 0 ? useUniversalCardMedalGenerationStatusType.getValue() : giftToFriendMedalGenerationStatusType.getValue();
        String medalGenerationStatus = bookForUser.optString("medalGenerationStatus");
        return MedalGenerationStatus.valueOf(medalGenerationStatus).match(MedalGenerationStatusType.types[medalGenerationStatusType]);
    }
    
    private JSONObject queryUniversalAnimal(String bookId, JSONObject animal) {
        try {
            JSONObject jo = new JSONObject(AntDodoRpcCall.queryBookInfo(bookId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return animal;
            }
            // data: animalBookResult{}
            // data: animalForUserList[]
            JSONArray animalForUserList = jo.getJSONObject("data").getJSONArray("animalForUserList");
            for (int i = 0; i < animalForUserList.length(); i++) {
                jo = animalForUserList.getJSONObject(i);
                int star = jo.getInt("star");
                if (star < FantasticLevelType.stars[useUniversalCardFantasticLevelType.getValue()]) {
                    break;
                }
                JSONObject collectDetail = jo.getJSONObject("collectDetail");
                int count = collectDetail.optInt("count", 1 << 30);
                boolean hasCollected = collectDetail.optBoolean("hasCollected", false);
                //hasCollected=true(曾经获取);hasCollected=false(未获取)
                boolean isbetteranimal = false;
                //animal为空直接选该animal
                if (animal == null) {
                    isbetteranimal = true;
                }
                //开启优先搜集“未获取”
                else if (useUniversalCardBookCollectedhasCollected.getValue()) {
                    // 规则1: 如果之前的最优是“已收集”，而当前是“未收集”，则当前更好
                    if (animal.optBoolean("hasCollected", true) && !hasCollected) {
                        isbetteranimal = true;
                    }
                    // 规则2: 如果两者状态相同（都已收集或都未收集），则比较数量和星级
                    if (animal.optBoolean("hasCollected", true) == hasCollected) {
                        if (count < animal.getInt("count") || (count == animal.getInt("count") && star > animal.getInt("star"))) {
                            isbetteranimal = true;
                        }
                    }
                }
                //对比搜集数量和星级，优先选数量少的，数量相同选星级高的
                else {
                    if (count < animal.getInt("count") || (count == animal.getInt("count") && star > animal.getInt("star"))) {
                        isbetteranimal = true;
                    }
                }
                if (isbetteranimal) {
                    animal = jo.getJSONObject("animal");
                    animal.put("star", star);
                    animal.put("count", count);
                    animal.put("hasCollected", hasCollected);
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryUniversalAnimal err:");
            Log.printStackTrace(TAG, t);
        }
        return animal;
    }
    
    private Boolean consumeProp(String propId, String propType) {
        try {
            JSONObject jo = new JSONObject(AntDodoRpcCall.consumeProp(propId, propType));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            
            jo = jo.getJSONObject("data");
            String propName = jo.getJSONObject("propConfig").getString("propName");
            
            JSONObject animal = jo.getJSONObject("useResult").optJSONObject("animal");
            Log.forest("使用道具🎭[" + propName + "]" + getAnimalInfo(animal));
            checkAnimalAndGiftToFriend(animal);
            return true;
        }
        catch (Throwable t) {
            Log.i(TAG, "consumeProp err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private Boolean consumeProp(String propId, String propType, String animalId) {
        try {
            JSONObject jo = new JSONObject(AntDodoRpcCall.consumeProp(propId, propType, animalId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            jo = jo.getJSONObject("data");
            String propName = jo.getJSONObject("propConfig").getString("propName");
            JSONObject animal = jo.getJSONObject("useResult").optJSONObject("animal");
            Log.forest("使用道具🎭[" + propName + "]" + getAnimalInfo(animal));
            checkAnimalAndGiftToFriend(animal);
            return true;
        }
        catch (Throwable th) {
            Log.i(TAG, "consumeProp err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }
    
    private void collectToFriend() {
        try {
            JSONObject jo = new JSONObject(AntDodoRpcCall.queryFriend());
            if (MessageUtil.checkResultCode(TAG, jo)) {
                int count = 0;
                JSONArray limitList = jo.getJSONObject("data").getJSONObject("extend").getJSONArray("limit");
                for (int i = 0; i < limitList.length(); i++) {
                    JSONObject limit = limitList.getJSONObject(i);
                    if (limit.getString("actionCode").equals("COLLECT_TO_FRIEND")) {
                        if (limit.getLong("startTime") > System.currentTimeMillis()) {
                            return;
                        }
                        count = limit.getInt("leftLimit");
                        break;
                    }
                    
                }
                JSONArray friendList = jo.getJSONObject("data").getJSONArray("friends");
                for (int i = 0; i < friendList.length() && count > 0; i++) {
                    JSONObject friend = friendList.getJSONObject(i);
                    if (friend.getBoolean("dailyCollect")) {
                        continue;
                    }
                    String useId = friend.getString("userId");
                    boolean isCollectToFriend = collectToFriendList.getValue().contains(useId);
                    if (collectToFriendType.getValue() != CollectToFriendType.COLLECT) {
                        isCollectToFriend = !isCollectToFriend;
                    }
                    if (!isCollectToFriend) {
                        continue;
                    }
                    jo = new JSONObject(AntDodoRpcCall.collect(useId));
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        String userName = UserIdMap.getMaskName(useId);
                        JSONObject animal = jo.getJSONObject("data").optJSONObject("animal");
                        Log.forest("帮抽卡片🦕[" + userName + "]" + getAnimalInfo(animal));
                        count--;
                    }
                }
                
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "collectHelpFriend err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void generateBookMedal() {
        // 图鉴合成状态 合成 可以合成 不能合成
        // medalGenerationStatus: GENERATED CAN_GENERATE CAN_NOT_GENERATE
        
        // 卡片收集情况 完成 未完成
        // bookCollectedStatus: COMPLETED NOT_COMPLETED
        
        // 卡片收集进度
        // collectProgress 10/10 2/10
        try {
            boolean hasMore;
            int pageStart = 0;
            do {
                JSONObject jo = new JSONObject(AntDodoRpcCall.queryBookList(9, pageStart));
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    break;
                }
                jo = jo.getJSONObject("data");
                hasMore = jo.getBoolean("hasMore");
                pageStart += 9;
                JSONArray bookForUserList = jo.getJSONArray("bookForUserList");
                for (int i = 0; i < bookForUserList.length(); i++) {
                    jo = bookForUserList.getJSONObject(i);
                    MedalGenerationStatus medalGenerationStatus = MedalGenerationStatus.valueOf(jo.optString("medalGenerationStatus"));
                    if (medalGenerationStatus == MedalGenerationStatus.CAN_GENERATE) {
                        if (bookMedalOptions.getValue().contains("generateBookMedal")) {
                            JSONObject animalBookResult = jo.getJSONObject("animalBookResult");
                            String bookId = animalBookResult.getString("bookId");
                            String ecosystem = animalBookResult.getString("ecosystem");
                            jo = new JSONObject(AntDodoRpcCall.generateBookMedal(bookId));
                            if (!MessageUtil.checkResultCode(TAG, jo)) {
                                break;
                            }
                            Log.forest("神奇物种🦕合成勋章[" + ecosystem + "]");
                        }
                    }
                    else if (medalGenerationStatus == MedalGenerationStatus.CAN_NOT_GENERATE) {
                        if (bookMedalOptions.getValue().contains("collectHistoryAnimal") && Objects.equals("END", jo.optString("bookStatus")) && usePropList.getValue().contains("COLLECT_HISTORY_ANIMAL_7_DAYS") && useProp.getValue()) {
                            //if (Status.canVitalityExchangeBenefitToday("SK20230518000062", 1)) {
                                //AntForestV2.exchangeBenefit("SP20230518000022", "SK20230518000062", "神奇物种抽历史卡机会");
                            //}
                        }
                    }
                }
            }
            while (hasMore);
        }
        catch (Throwable t) {
            Log.i(TAG, "generateBookMedal err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private static String getAnimalInfo(JSONObject animal) {
        if (animal == null) {
            return "";
        }
        String ecosystem = animal.optString("ecosystem", "未知专辑");
        String name = animal.optString("name", "未知动物");
        String fantasticLevel = animal.optString("fantasticLevel", "Unknown");
        return "#[" + ecosystem + "]" + name + "[" + FantasticLevel.valueOf(fantasticLevel).nickName() + "]";
    }
    
    private void checkAnimalAndGiftToFriend(JSONObject animal) {
        if (animal == null || !giftToFriend.getValue() || useCollectTimingType.getValue() != TimingType.LAST_DAY) {
            return;
        }
        String targetUserId = getGiftToFriendTargetUserId();
        if (targetUserId == null) {
            return;
        }
        try {
            if (!FantasticLevel.MAGIC.name().equals(animal.getString("fantasticLevel"))) {
                return;
            }
            String bookId = animal.getString("bookId");
            JSONObject jo = new JSONObject(AntDodoRpcCall.homePage());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("data").getJSONObject("animalBook");
            if (!bookId.equals(jo.getString("bookId"))) {
                return;
            }
            giftToFriend(animal, targetUserId);
        }
        catch (Throwable t) {
            Log.i(TAG, "checkAnimalAndGiftToFriend err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private String getGiftToFriendTargetUserId() {
        Set<String> set = giftToFriendList.getValue();
        if (set.isEmpty()) {
            return null;
        }
        for (String userId : set) {
            if (UserIdMap.getCurrentUid() == null || Objects.equals(UserIdMap.getCurrentUid(), userId)) {
                continue;
            }
            return userId;
        }
        return null;
    }
    
    private void giftToFriend() {
        String targetUserId = getGiftToFriendTargetUserId();
        if (targetUserId == null) {
            return;
        }
        giftToFriend(targetUserId);
    }
    
    private void giftToFriend(String targetUserId) {
        try {
            boolean hasMore;
            int pageStart = 0;
            do {
                JSONObject jo = new JSONObject(AntDodoRpcCall.queryBookList(9, pageStart));
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    break;
                }
                jo = jo.getJSONObject("data");
                hasMore = jo.getBoolean("hasMore");
                pageStart += 9;
                JSONArray bookForUserList = jo.getJSONArray("bookForUserList");
                for (int i = 0; i < bookForUserList.length(); i++) {
                    jo = bookForUserList.getJSONObject(i);
                    String collectProgress = jo.getString("collectProgress");
                    if (collectProgress.startsWith("0/") || !isQueryBookInfo(jo, 1)) {
                        continue;
                    }
                    String bookId = jo.getJSONObject("animalBookResult").getString("bookId");
                    giftToFriend(bookId, targetUserId);
                }
            }
            while (hasMore);
        }
        catch (Throwable t) {
            Log.i(TAG, "giftToFriend err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void giftToFriend(String bookId, String targetUserId) {
        try {
            JSONObject jo = new JSONObject(AntDodoRpcCall.queryBookInfo(bookId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray animalForUserList = jo.getJSONObject("data").optJSONArray("animalForUserList");
            if (animalForUserList == null) {
                return;
            }
            int star = FantasticLevelType.stars[giftToFriendFantasticLevelType.getValue()];
            for (int i = 0; i < animalForUserList.length(); i++) {
                JSONObject animalForUser = animalForUserList.getJSONObject(i);
                if (animalForUser.optInt("star") < star) {
                    continue;
                }
                int count = animalForUser.getJSONObject("collectDetail").optInt("count");
                if (count <= 0) {
                    continue;
                }
                JSONObject animal = animalForUser.getJSONObject("animal");
                for (int j = 0; j < count; j++) {
                    if (!giftToFriend(animal, targetUserId)) {
                        return;
                    }
                    TimeUtil.sleep(500L);
                }
            }
        }
        catch (Throwable th) {
            Log.i(TAG, "giftToFriend err:");
            Log.printStackTrace(TAG, th);
        }
    }
    
    private Boolean giftToFriend(JSONObject animal, String targetUserId) {
        try {
            String animalId = animal.getString("animalId");
            if (targetUserId.equals(UserIdMap.getCurrentUid())) {
                return false;
            }
            ;
            JSONObject jo = new JSONObject(AntDodoRpcCall.social(animalId, targetUserId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.forest("赠送卡片🦕[" + UserIdMap.getMaskName(targetUserId) + "]" + getAnimalInfo(animal));
                return true;
            }
        }
        catch (Throwable th) {
            Log.i(TAG, "giftToFriend err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }
    
    public enum PropGroup {
        COLLECT_ANIMAL, COLLECT_HISTORY_ANIMAL, ADD_COLLECT_TO_FRIEND_LIMIT, UNIVERSAL_CARD;
        
        public final String[] nickNames = {"抽卡道具", "历史图鉴随机卡道具", "抽好友卡道具", "万能卡道具"};
        
        public String nickName() {
            return nickNames[ordinal()];
        }
    }
    
    public enum BookStatus {
        NOT_START, DOING, END;
        
        public final String[] nickNames = {"未开启", "进行中", "已结束"};
        
        public String nickName() {
            return nickNames[ordinal()];
        }
        
        public Boolean match(String status) {
            if (name().equals(NOT_START.name())) {
                return false;
            }
            return name().equals(status) || "ALL".equals(status);
        }
    }
    
    public enum BookCollectedStatus {
        NOT_COMPLETED, COMPLETED;
        
        public Boolean match(String status) {
            return name().equals(status) || "ALL".equals(status);
        }
    }
    
    public enum MedalGenerationStatus {
        CAN_NOT_GENERATE, CAN_GENERATE, GENERATED;
        
        public final String[] nickNames = {"收集中", "已集齐", "已合成"};
        
        public String nickName() {
            return nickNames[ordinal()];
        }
        
        public Boolean match(String status) {
            return name().equals(status) || "ALL".equals(status);
        }
    }
    
    public enum FantasticLevel {
        COMMON, RARE, MAGIC;
        
        public final String[] nickNames = {"普通", "稀有", "神奇"};
        
        public String nickName() {
            return nickNames[ordinal()];
        }
    }
    
    public interface TimingType {
        int EVERY_DAY = 0;
        int LAST_DAY = 1;
        
        String[] nickNames = {"每天使用", "专辑最后一天"};
    }
    
    public interface CollectToFriendType {
        
        int NONE = 0;
        int COLLECT = 1;
        int NOT_COLLECT = 2;
        
        String[] nickNames = {"不帮抽", "帮抽已选好友", "帮抽未选好友"};
        
    }
    
    public interface BookStatusType {
        int ALL = 0;
        int END = 1;
        int DOING = 2;
        
        String[] nickNames = {"全部图鉴", "往期图鉴", "本期图鉴"};
        String[] types = {"ALL", "END", "DOING"};
    }
    
    public interface BookCollectedStatusType {
        int ALL = 0;
        int NOT_COMPLETED = 1;
        int COMPLETED = 2;
        
        String[] nickNames = {"全部状态", "未完成收集", "已完成收集"};
        String[] types = {"ALL", "NOT_COMPLETED", "COMPLETED"};
    }
    
    public interface MedalGenerationStatusType {
        int ALL = 0;
        int CAN_NOT_GENERATE = 1;
        int CAN_GENERATE = 2;
        int GENERATED = 3;
        
        String[] nickNames = {"全部类型", "未能合成", "可以合成", "已经合成"};
        String[] types = {"ALL", "CAN_NOT_GENERATE", "CAN_GENERATE", "GENERATED"};
    }
    
    public interface FantasticLevelType {
        int COMMON = 0;
        int RARE = 1;
        int MAGIC = 2;
        
        String[] nickNames = {"普通", "稀有", "神奇"};
        int[] stars = {1, 2, 3};
    }
}