package io.github.lazyimmortal.sesame.model.task.antFarm;

import org.json.JSONArray;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.UUID;

import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.util.RandomUtil;
import io.github.lazyimmortal.sesame.util.StringUtil;

public class AntFarmRpcCall {
    private static final String VERSION = "1.8.2302070202.46";
    
    public static String enterFarm(String userId) {
        String args = "[{\"queryLastRecordNum\":true,\"recall\":false,\"requestType\":\"NORMAL\"," + "\"sceneCode" + "\":\"ANTFARM\",\"source\":\"H5\",\"userId\":\"" + userId + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.enterFarm", args);
    }
    
    public static String enterFarm(String farmId, String userId) {
        return ApplicationHook.requestString("com.alipay.antfarm.enterFarm", "[{\"animalId\":\"\",\"farmId\":\"" + farmId + "\",\"gotoneScene\":\"\",\"gotoneTemplateId\":\"\"," + "\"masterFarmId\":\"\",\"queryLastRecordNum\":true,\"recall\":false," + "\"requestType\":\"NORMAL\"," + "\"sceneCode" +
                                                                             "\":\"ANTFARM\",\"source\":\"ANTFOREST\"," + "\"touchRecordId\":\"\"," + "\"userId\":\"" + userId + "\",\"version\":\"" + VERSION + "\"}]");
    }
    
    // 一起拿小鸡饲料
    public static String letsGetChickenFeedTogether() {
        String args1 = "[{\"needHasInviteUserByCycle\":\"true\",\"requestType\":\"RPC\"," + "\"sceneCode" + "\":\"ANTFARM_P2P\",\"source\":\"ANTFARM\",\"startIndex\":0," + "\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antiep.canInvitePersonListP2P", args1);
    }
    
    // 赠送饲料
    public static String giftOfFeed(String bizTraceId, String userId) {
        String args1 = "[{\"beInvitedUserId\":\"" + userId + "\",\"bizTraceId\":\"" + bizTraceId + "\",\"requestType" + "\":\"RPC\",\"sceneCode\":\"ANTFARM_P2P\"," + "\"source\":\"ANTFARM\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antiep.inviteP2P", args1);
    }
    
    public static String syncAnimalStatus(String farmId) {
        String args1 = "[{\"farmId\":\"" + farmId + "\",\"operType\":\"FEEDSYNC\",\"queryFoodStockInfo\":false," + "\"recall\":false,\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"," + "\"userId\":\"" + farmId2UserId(farmId) + "\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.syncAnimalStatus", args1);
    }
    
    public static String sleep() {
        String args1 = "[{\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"LOVECABIN\"," + "\"version" + "\":\"unknown\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.sleep", args1);
    }
    
    public static String wakeUp() {
        String args1 = "[{\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"LOVECABIN\"," + "\"version" + "\":\"unknown\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.wakeUp", args1);
    }
    
    public static String queryLoveCabin(String userId) {
        String args = "[{\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"LOVECABIN\",\"userId\":\"" + userId + "\"}]";
        //        String args1 = "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"ENTERFARM\",
        //        \"userId\":\"" + userId + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.queryLoveCabin", args);
    }
    
    public static String rewardFriend(String consistencyKey, String friendId, String productNum, String time) {
        String args1 =
                "[{\"canMock\":true,\"consistencyKey\":\"" + consistencyKey + "\",\"friendId\":\"" + friendId + "\"," + "\"operType\":\"1\",\"productNum\":" + productNum + ",\"requestType\":\"NORMAL\"," + "\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"time\":" + time + ",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.rewardFriend", args1);
    }
    
    public static String recallAnimal(String animalId, String currentFarmId, String masterFarmId) {
        String args1 = "[{\"animalId\":\"" + animalId + "\",\"currentFarmId\":\"" + currentFarmId + "\"," + "\"masterFarmId\":\"" + masterFarmId + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\"," + "\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.recallAnimal", args1);
    }
    
    public static String orchardRecallAnimal(String animalId, String userId) {
        String args1 = "[{\"animalId\":\"" + animalId + "\",\"orchardUserId\":\"" + userId + "\",\"requestType" + "\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"zhuangyuan_zhaohuixiaoji\",\"version\":\"0.1" + ".2403061630.6\"}]";
        return ApplicationHook.requestString("com.alipay.antorchard.recallAnimal", args1);
    }
    
    public static String sendBackAnimal(String sendType, String animalId, String currentFarmId, String masterFarmId) {
        String args1 = "[{\"animalId\":\"" + animalId + "\",\"currentFarmId\":\"" + currentFarmId + "\"," + "\"masterFarmId\":\"" + masterFarmId + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\"," + "\"sendType\":\"" + sendType + "\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.sendBackAnimal", args1);
    }
    
    public static String harvestProduce(String farmId) {
        String args1 = "[{\"canMock\":true,\"farmId\":\"" + farmId + "\",\"giftType\":\"\"," + "\"requestType" + "\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.harvestProduce", args1);
    }
    
    public static String getCharityAccount(String userId) {
        String args = "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"userId\":\"" + userId + "\",\"version\":\"unknown\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.getCharityAccount", args);
    }
    
    public static String listActivityInfo() {
        String args1 = "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.listActivityInfo", args1);
    }
    
    public static String getProjectInfo(String projectId) {
        String args = "[{\"activityId\":\"\",\"projectId\":\"" + projectId + "\",\"requestType\":\"NORMAL\"," + "\"sceneCode\":\"ANTFARM\",\"source\":\"ANTFARM\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.getProjectInfo", args);
    }
    
    public static String donation(String activityId, int donationAmount) {
        String args1 = "[{\"activityId\":\"" + activityId + "\",\"donationAmount\":" + donationAmount + "," + "\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.donation", args1);
    }
    
    public static String listFarmTask() {
        String args1 = "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.listFarmTask", args1);
    }
    
    public static String getAnswerInfo() {
        String args1 = "[{\"answerSource\":\"foodTask\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\"," + "\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.getAnswerInfo", args1);
    }
    
    public static String answerQuestion(String quesId, int answer) {
        String args1 = "[{\"answers\":\"[{\\\"questionId\\\":\\\"" + quesId + "\\\",\\\"answers\\\":[" + answer + "]}]\",\"bizkey\":\"ANSWER\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"," + "\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.doFarmTask", args1);
    }
    
    //String.valueOf(System.currentTimeMillis()) + RandomUtil.nextLong();
    //String outBizNo = taskType + "_" + RandomUtil.nextDouble();
    //{"bizKey":"IP_EXCHANGE_TASK","requestType":"RPC","sceneCode":"ANTFARM","source":"antfarm_villa","taskSceneCode":"ANTFARM_IP_DRAW_TASK"}
    
    public static String doFarmTask(String bizKey, String taskSceneCode) {
        String args1 = "[{\"bizKey\":\"" + bizKey + "\",\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"taskSceneCode\":\"" + taskSceneCode + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.doFarmTask", args1);
    }
    //{"bizKey":"SHH_liyunrui","requestType":"NORMAL","sceneCode":"ANTFARM","source":"H5","version":"1.8.2302070202.46"}]}
    public static String doFarmTask(String bizKey) {
        String args1 = "[{\"bizKey\":\"" + bizKey + "\",\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.doFarmTask", args1);
    }
    
    public static String finishTask(String taskType, String sceneCode) {
        StringBuilder randomStr = new StringBuilder(8);
        String RANDOM_CHAR_POOL = "0123456789abcdef";
        Random RANDOM = new Random();
        for (int i = 0; i < 8; i++) {
            randomStr.append(RANDOM_CHAR_POOL.charAt(RANDOM.nextInt(RANDOM_CHAR_POOL.length())));
        }
        String outBizNo = taskType + "_" + System.currentTimeMillis() +"_"+ randomStr;
        //{"outBizNo":"IP_SHANGYEHUA_TASK_1764696782007_f5c68d4d","requestType":"RPC","sceneCode":"ANTFARM_IP_DRAW_TASK","source":"ADBASICLIB","taskType":"IP_SHANGYEHUA_TASK"}
        String args1 = "[{\"outBizNo\":\"" + outBizNo + "\",\"requestType\":\"RPC\",\"sceneCode\":\"" + sceneCode + "\",\"source\":\"ADBASICLIB\",\"taskType\":\"" + taskType + "\"}]";
        return ApplicationHook.requestString("com.alipay.antiep.finishTask", args1);
    }
    
    public static String receiveFarmTaskAward(String taskId) {
        String args1 = "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"taskId\":\"" + taskId + "\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.receiveFarmTaskAward", args1);
    }
    
    public static String listToolTaskDetails() {
        String args1 = "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.listToolTaskDetails", args1);
    }
    
    public static String receiveToolTaskReward(String rewardType, int rewardCount, String taskType) {
        String args1 = "[{\"ignoreLimit\":false,\"requestType\":\"NORMAL\",\"rewardCount\":" + rewardCount + "," + "\"rewardType\":\"" + rewardType + "\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"taskType\":\"" + taskType + "\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.receiveToolTaskReward", args1);
    }
    
    public static String feedAnimal(String farmId) {
        String args1 = "[{\"animalType\":\"CHICK\",\"canMock\":true,\"farmId\":\"" + farmId + "\",\"requestType" + "\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.feedAnimal", args1);
    }
    
    public static String listFarmTool() {
        String args1 = "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.listFarmTool", args1);
    }
    
    public static String useFarmTool(String targetFarmId, String toolId, String toolType) {
        String args1 = "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"targetFarmId\":\"" + targetFarmId + "\",\"toolId\":\"" + toolId + "\",\"toolType\":\"" + toolType + "\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.useFarmTool", args1);
    }
    
    public static String rankingList(int pageStartSum) {
        String args1 = "[{\"pageSize\":20,\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"," + "\"startNum\":" + pageStartSum + ",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.rankingList", args1);
    }
    
    public static String notifyFriend(String animalId, String notifiedFarmId) {
        String args1 = "[{\"animalId\":\"" + animalId + "\",\"animalType\":\"CHICK\",\"canBeGuest\":true," + "\"notifiedFarmId\":\"" + notifiedFarmId + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\"," + "\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.notifyFriend", args1);
    }
    
    public static String feedFriendAnimal(String friendFarmId) {
        String args = "[{\"friendFarmId\":\"" + friendFarmId + "\",\"requestType\":\"NORMAL\"," + "\"sceneCode" + "\":\"ANTFARM\",\"source\":\"H5\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.feedFriendAnimal", args);
    }
    
    public static String feedFriendAnimal(String friendFarmId, String groupId) {
        if (StringUtil.isEmpty(groupId)) {
            return feedFriendAnimal(friendFarmId);
        }
        String args = "[{\"friendFarmId\":\"" + friendFarmId + "\",\"groupId\":\"" + groupId + "\",\"requestType" + "\":\"NORMAL\",\"sceneCode\":\"ChickFamily\",\"source\":\"H5\",\"spaceType\":\"ChickFamily\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.feedFriendAnimal", args);
    }
    
    public static String farmId2UserId(String farmId) {
        int l = farmId.length() / 2;
        return farmId.substring(l);
    }
    
    public static String collectManurePot(String manurePotNO) {
        return ApplicationHook.requestString("com.alipay.antfarm.collectManurePot", "[{\"manurePotNOs\":\"" + manurePotNO + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\"," + "\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String sign() {
        return ApplicationHook.requestString("com.alipay.antfarm.sign", "[{\"requestType\":\"NORMAL\"," + "\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String initFarmGame(String gameType) {
        if ("flyGame".equals(gameType)) {
            return ApplicationHook.requestString("com.alipay.antfarm.initFarmGame", "[{\"gameType\":\"flyGame\"," + "\"requestType\":\"RPC\",\"sceneCode\":\"FLAYGAME\"," + "\"source\":\"FARM_game_yundongfly\"," + "\"toolTypes\":\"ACCELERATETOOL,SHARETOOL,NONE\",\"version\":\"\"}]");
        }
        return ApplicationHook.requestString("com.alipay.antfarm.initFarmGame", "[{\"gameType\":\"" + gameType + "\"," + "\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"toolTypes\":\"STEALTOOL," + "ACCELERATETOOL,SHARETOOL\"}]");
    }
    
    public static int RandomScore(String str) {
        if ("starGame".equals(str)) {
            return RandomUtil.nextInt(300, 400);
        }
        else if ("jumpGame".equals(str)) {
            return RandomUtil.nextInt(250, 270) * 10;
        }
        else if ("flyGame".equals(str)) {
            return RandomUtil.nextInt(4000, 8000);
        }
        else if ("hitGame".equals(str)) {
            return RandomUtil.nextInt(80, 120);
        }
        else {
            return 210;
        }
    }
    
    public static String recordFarmGame(String gameType) {
        String uuid = getUuid();
        String md5String = getMD5(uuid);
        int score = RandomScore(gameType);
        if ("flyGame".equals(gameType)) {
            int foodCount = score / 50;
            return ApplicationHook.requestString("com.alipay.antfarm.recordFarmGame", "[{\"foodCount\":" + foodCount + ",\"gameType\":\"flyGame\",\"md5\":\"" + md5String + "\"," + "\"requestType\":\"RPC\",\"sceneCode\":\"FLAYGAME\",\"score\":" + score + ",\"source" + "\":\"ANTFARM\"," +
                                                                                      "\"toolTypes\":\"ACCELERATETOOL,SHARETOOL,NONE\",\"uuid\":\"" + uuid + "\",\"version\":\"\"}]");
        }
        return ApplicationHook.requestString("com.alipay.antfarm.recordFarmGame", "[{\"gameType\":\"" + gameType + "\",\"md5\":\"" + md5String + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"score\":" + score + ",\"source\":\"H5\",\"toolTypes\":\"STEALTOOL,ACCELERATETOOL,SHARETOOL\","
                                                                                  + "\"uuid\":\"" + uuid + "\"}]");
    }
    
    private static String getUuid() {
        StringBuilder sb = new StringBuilder();
        for (String str : UUID.randomUUID().toString().split("-")) {
            sb.append(str.substring(str.length() / 2));
        }
        return sb.toString();
    }
    
    public static String getMD5(String password) {
        try {
            // 得到一个信息摘要器
            MessageDigest digest = MessageDigest.getInstance("md5");
            byte[] result = digest.digest(password.getBytes());
            StringBuilder buffer = new StringBuilder();
            // 把没一个byte 做一个与运算 0xff;
            for (byte b : result) {
                // 与运算
                int number = b & 0xff;// 加盐
                String str = Integer.toHexString(number);
                if (str.length() == 1) {
                    buffer.append("0");
                }
                buffer.append(str);
            }
            
            // 标准的md5加密后的结果
            return buffer.toString();
        }
        catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "";
        }
    }
    
    /* 小鸡厨房 */
    
    public static String enterKitchen(String userId) {
        return ApplicationHook.requestString("com.alipay.antfarm.enterKitchen", "[{\"requestType\":\"RPC\"," + "\"sceneCode\":\"ANTFARM\",\"source\":\"antfarmzuofanrw\",\"userId\":\"" + userId + "\",\"version" + "\":\"unknown\"}]");
    }
    
    public static String collectDailyFoodMaterial(int dailyFoodMaterialAmount) {
        return ApplicationHook.requestString("com.alipay.antfarm.collectDailyFoodMaterial", "[{\"collectDailyFoodMaterialAmount\":" + dailyFoodMaterialAmount + ",\"requestType\":\"RPC\"," + "\"sceneCode\":\"ANTFARM\",\"source\":\"antfarmzuofanrw\",\"version\":\"unknown\"}]");
    }
    
    public static String queryFoodMaterialPack() {
        return ApplicationHook.requestString("com.alipay.antfarm.queryFoodMaterialPack", "[{\"requestType\":\"RPC\"," + "\"sceneCode\":\"ANTFARM\",\"source\":\"kitchen\"," + "\"version" + "\":\"unknown\"}]");
    }
    
    public static String collectDailyLimitedFoodMaterial(int dailyLimitedFoodMaterialAmount) {
        return ApplicationHook.requestString("com.alipay.antfarm.collectDailyLimitedFoodMaterial", "[{\"collectDailyLimitedFoodMaterialAmount\":" + dailyLimitedFoodMaterialAmount + ",\"requestType" + "\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"kitchen\",\"version\":\"unknown\"}]");
    }
    
    public static String farmFoodMaterialCollect() {
        return ApplicationHook.requestString("com.alipay.antorchard.farmFoodMaterialCollect", "[{\"collect\":true," + "\"requestType\":\"RPC\",\"sceneCode\":\"ORCHARD\",\"source\":\"VILLA\"," + "\"version\":\"unknown\"}]");
    }
    
    public static String cook(String userId) {
        return ApplicationHook.requestString("com.alipay.antfarm.cook", "[{\"requestType\":\"RPC\"," + "\"sceneCode" + "\":\"ANTFARM\",\"source\":\"antfarmzuofanrw\"," + "\"userId\":\"" + userId + "\",\"version" + "\":\"unknown\"}]");
    }
    
    public static String useFarmFood(String cookbookId, String cuisineId) {
        return ApplicationHook.requestString("com.alipay.antfarm.useFarmFood", "[{\"cookbookId\":\"" + cookbookId + "\",\"cuisineId\":\"" + cuisineId + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\"," + "\"source\":\"chInfo_ch_appcenter__chsub_9patch\",\"useCuisine\":true," + "\"version" +
                                                                               "\":\"" + VERSION + "\"}]");
    }
    
    public static String collectKitchenGarbage() {
        return ApplicationHook.requestString("com.alipay.antfarm.collectKitchenGarbage", "[{\"requestType\":\"RPC\"," + "\"sceneCode\":\"ANTFARM\",\"source\":\"VILLA\"," + "\"version" + "\":\"unknown\"}]");
    }
    
    /* 日常任务 */
    
    public static String queryTabVideoUrl() {
        return ApplicationHook.requestString("com.alipay.antfarm.queryTabVideoUrl", "[{\"requestType\":\"NORMAL\"," + "\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String videoDeliverModule(String bizId) {
        return ApplicationHook.requestString("alipay.content.reading.life.deliver.module", "[{\"bizId\":\"" + bizId + "\",\"bizType\":\"CONTENT\",\"chInfo\":\"ch_antFarm\"," + "\"refer" + "\":\"antFarm\",\"timestamp\":\"" + System.currentTimeMillis() + "\"}]");
    }
    
    public static String videoTrigger(String bizId) {
        return ApplicationHook.requestString("alipay.content.reading.life.prize.trigger", "[{\"bizId\":\"" + bizId + "\",\"bizType\":\"CONTENT\",\"prizeFlowNum\":\"VIDEO_TASK\",\"prizeType\":\"farmFeed\"}]");
    }
    
    /* 惊喜礼包 */
    public static String drawLotteryPlus() {
        return ApplicationHook.requestString("com.alipay.antfarm.drawLotteryPlus", "[{\"requestType\":\"NORMAL\"," + "\"sceneCode\":\"ANTFARM\",\"source\":\"H5 \",\"version\":\"\"}]");
    }
    
    /* 小麦 */
    
    public static String acceptGift() {
        return ApplicationHook.requestString("com.alipay.antfarm.acceptGift", "[{\"ignoreLimit\":false," + "\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String visitFriend(String friendFarmId) {
        return ApplicationHook.requestString("com.alipay.antfarm.visitFriend", "[{\"friendFarmId\":\"" + friendFarmId + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\"," + "\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]");
    }
    
    /* 小鸡日记 */
    public static String queryChickenDiaryList() {
        return ApplicationHook.requestString("com.alipay.antfarm.queryChickenDiaryList", "[{\"requestType\":\"NORMAL" + "\",\"sceneCode\":\"DIARY\",\"source\":\"antfarm_icon\"}]");
    }
    
    public static String queryChickenDiary(String queryDayStr) {
        return ApplicationHook.requestString("com.alipay.antfarm.queryChickenDiary", "[{\"queryDayStr\":\"" + queryDayStr + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"DIARY\"," + "\"source\":\"antfarm_icon\"}]");
    }
    
    public static String diaryTietie(String diaryDate, String roleId) {
        return ApplicationHook.requestString("com.alipay.antfarm.diaryTietie", "[{\"diaryDate\":\"" + diaryDate + "\",\"requestType\":\"NORMAL\",\"roleId\":\"" + roleId + "\",\"sceneCode\":\"DIARY\"," + "\"source" + "\":\"antfarm_icon\"}]");
    }
    
    public static String visitAnimal() {
        return ApplicationHook.requestString("com.alipay.antfarm.visitAnimal", "[{\"requestType\":\"NORMAL\"," + "\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String feedFriendAnimalVisit(String friendFarmId) {
        return ApplicationHook.requestString("com.alipay.antfarm.feedFriendAnimal", "[{\"friendFarmId\":\"" + friendFarmId + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\"," + "\"source\":\"visitChicken\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String visitAnimalSendPrize(String token) {
        return ApplicationHook.requestString("com.alipay.antfarm.visitAnimalSendPrize", "[{\"requestType\":\"NORMAL" + "\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"token\":\"" + token + "\",\"version\":\"" + VERSION + "\"}]");
    }
    
    /*抽抽乐*/
    public static String listFarmDrawTask(String taskSceneCode) {
        return ApplicationHook.requestString("com.alipay.antfarm.listFarmTask", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"signSceneCode\":\"\",\"source\":\"H5\",\"taskSceneCode\":\""+taskSceneCode+"\",\"topTask\":\"\"}]");
    }
    
    public static String receiveFarmDrawTimesTaskAward(String taskId, String awardType, String taskSceneCode) {
        return ApplicationHook.requestString("com.alipay.antfarm.receiveFarmTaskAward", "[{\"awardType\":\"" + awardType + "\",\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"taskId\":\"" + taskId + "\",\"taskSceneCode\":\"" + taskSceneCode + "\"}]");
    }
    public static String queryDrawMachineActivity(String otherScenes,String scene) {
        return ApplicationHook.requestString("com.alipay.antfarm.queryDrawMachineActivity", "[{\"otherScenes\":[\""+otherScenes+"\"],\"requestType\":\"RPC\",\"scene\":\""+scene+"\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]");
    }
    public static String drawMachine(String scene) {
        return ApplicationHook.requestString("com.alipay.antfarm.drawMachine", "[{\"requestType\":\"RPC\",\"scene\":\""+scene+"\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]");
    }
    /* 抽抽乐
    public static String enterDrawMachine() {
        return ApplicationHook.requestString("com.alipay.antfarm.enterDrawMachine", "[{\"requestType\":\"RPC\"," + "\"sceneCode\":\"ANTFARM\",\"source\":\"icon\"}]");
    }
    
    public static String queryDrawMachineActivity() {
        return ApplicationHook.requestString("com.alipay.antfarm.queryDrawMachineActivity", "[{\"otherScenes\":[\"dailyDrawMachine\"],\"requestType\":\"RPC\",\"scene\":\"ipDrawMachine\",\"sceneCode\":\"ANTFARM\",\"source\":\"antfarm_villa\"}]");
    }
    
    public static String drawPrize() {
        return ApplicationHook.requestString("com.alipay.antfarm.DrawPrize", "[{\"requestType\":\"RPC\"," + "\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]");
    }
    
    //
    public static String drawMachine() {
        return ApplicationHook.requestString("com.alipay.antfarm.drawMachine", "[{\"requestType\":\"RPC\",\"scene\":\"ipDrawMachine\",\"sceneCode\":\"ANTFARM\",\"source\":\"antfarm_villa\"}]");
    }
    
    public static String listFarmDrawTimesTask() {
        return ApplicationHook.requestString("com.alipay.antfarm.listFarmTask", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"signSceneCode\":\"\",\"source\":\"H5\",\"taskSceneCode\":\"ANTFARM_DRAW_TIMES_TASK\",\"topTask\":\"\"}]");
    }
    
    public static String listFarmDrawActivityTimeTask() {
        return ApplicationHook.requestString("com.alipay.antfarm.listFarmTask", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"signSceneCode\":\"\",\"source\":\"H5\",\"taskSceneCode\":\"ANTFARM_IP_DRAW_TASK\",\"topTask\":\"\"}]");
    }
    
    //{\"awardType\":\"DRAW_TIMES\",\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"taskId\":\"" + taskId + "\",\"taskSceneCode\":\"ANTFARM_DRAW_TIMES_TASK\"}
    //{"awardType":"IP_DRAW_MACHINE_DRAW_TIMES","requestType":"RPC","sceneCode":"ANTFARM","source":"antfarm_villa","taskId":"IP_SIGN_FREE","taskSceneCode":"ANTFARM_IP_DRAW_TASK"}
    public static String receiveFarmDrawTimesTaskAward(String taskId, String awardType, String taskSceneCode) {
        return ApplicationHook.requestString("com.alipay.antfarm.receiveFarmTaskAward", "[{\"awardType\":\"" + awardType + "\",\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"taskId\":\"" + taskId + "\",\"taskSceneCode\":\"" + taskSceneCode + "\"}]");
    }
    */
    public static String hireAnimal(String farmId, String animalId) {
        return ApplicationHook.requestString("com.alipay.antfarm.hireAnimal", "[{\"friendFarmId\":\"" + farmId + "\"," + "\"hireActionType\":\"HIRE_IN_FRIEND_FARM\",\"hireAnimalId\":\"" + animalId + "\",\"requestType" + "\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"sendCardChat\":false," + "\"source" +
                                                                              "\":\"H5\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String drawGameCenterAward() {
        return ApplicationHook.requestString("com.alipay.antfarm.drawGameCenterAward", "[{\"requestType\":\"NORMAL\"," + "\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String queryGameList() {
        return ApplicationHook.requestString("com.alipay.antfarm.queryGameList",
                "[{\"commonDegradeResult" + "\":{\"deviceLevel\":\"high\",\"resultReason\":0,\"resultType\":0},\"platform\":\"Android\"," + "\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]");
    }
    
    // 小鸡换装
    public static String listOrnaments() {
        return ApplicationHook.requestString("com.alipay.antfarm.listOrnaments", "[{\"pageNo\":\"1\"," + "\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"setsType\":\"ACHIEVEMENTSETS\"," + "\"source" + "\":\"H5\",\"subType\":\"sets\",\"type\":\"apparels\",\"version\":\"" + VERSION + "\"}]");
    }
    
    public static String saveOrnaments(String animalId, String farmId, String ornaments) {
        return ApplicationHook.requestString("com.alipay.antfarm.saveOrnaments", "[{\"animalId\":\"" + animalId + "\",\"farmId\":\"" + farmId + "\",\"ornaments\":\"" + ornaments + "\",\"requestType\":\"NORMAL\"," + "\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]");
    }
    
    // 亲密家庭
    public static String enterFamily() {
        String args = "[{\"fromAnn\":false,\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.enterFamily", args);
    }
    
    /**
     * 同步家庭状态
     *
     * @param groupId     家庭ID
     * @param operType    要同步的信息类型
     *                    INTIMACY_VALUE : 亲密度
     *                    FAMILY_INTERACT_ACTION : 进行的活动
     *                    ANIMAL_STATUS : 小鸡状态
     *                    SLEEP_INFO|ANIMAL_STATUS : 小鸡状态与睡眠信息
     * @param syncUserIds 要同步的用户ID
     * @return String
     */
    public static String syncFamilyStatus(String groupId, String operType, String syncUserIds) {
        // INTIMACY_VALUE
        // FAMILY_INTERACT_ACTION
        // SLEEP_INFO|ANIMAL_STATUS
        // ANIMAL_STATUS|SLEEP_INFO
        // ANIMAL_STATUS
        String args = "[{\"groupId\":\"" + groupId + "\",\"operType\":\"" + operType + "\",\"requestType\":\"NORMAL" + "\"," + "\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"syncUserIds\":[\"" + syncUserIds + "\"]}]";
        return ApplicationHook.requestString("com.alipay.antfarm.syncFamilyStatus", args);
    }
    
    //    public static String familyListFarmTask() {
    //        String args = "[{\"bizKey\":\"FAMILY_SIGN_TASK\",\"requestType\":\"NORMAL\",
    //        \"sceneCode\":\"familySign\",\"signSceneCode\":\"\",\"source\":\"H5\",
    //        \"taskSceneCode\":\"ANTFARM_FAMILY_TASK\"}]";
    //        return ApplicationHook.requestString("com.alipay.antfarm.listFarmTask", args);
    //    }
  
    public static String familyReceiveFarmTaskAward(String taskId) {
        String args = "[{\"awardType\":\"FAMILY_INTIMACY\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\"," + "\"source\":\"H5\",\"taskId\":\"" + taskId + "\",\"taskSceneCode\":\"ANTFARM_FAMILY_TASK\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.receiveFarmTaskAward", args);
    }
    
    public static String familyAwardList() {
        String args = "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.familyAwardList", args);
    }
    
    public static String receiveFamilyAward(String rightId) {
        String args = "[{\"requestType\":\"NORMAL\",\"rightId\":\"" + rightId + "\",\"sceneCode\":\"ANTFARM\"," + "\"source\":\"H5\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.receiveFamilyAward", args);
    }
    
    public static String familySleep(String groupId) {
        String args = "[{\"groupId\":\"" + groupId + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\"," + "\"source\":\"H5\",\"spaceType\":\"ChickFamily\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.sleep", args);
    }
    
    public static String familyWakeUp() {
        String args = "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"aixinxiaowutojiating\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.wakeUp", args);
    }
    
    public static String familyEatTogether(String groupId, JSONArray cuisines, JSONArray friendUserIdList) {
        // 确保JSON格式正确
        String args = "[{\"cuisines\":" + cuisines.toString() +
                      ",\"friendUserIds\":" + friendUserIdList.toString() +
                      ",\"groupId\":\"" + groupId +
                      "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\"," +
                      "\"source\":\"H5\",\"spaceType\":\"ChickFamily\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.familyEatTogether", args);
    }
    
    // 添加到 AntFarmRpcCall.java 中
    
    public static String familyTaskTips(JSONArray familyAnimalsExceptUser) {
        String args = "[{\"animals\":" + familyAnimalsExceptUser.toString() + ",\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"taskSceneCode\":\"ANTFARM_FAMILY_TASK\",\"timeZoneId\":\"Asia/Shanghai\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.familyTaskTips", args);
    }
    /*
    public static String OpenAIPrivatePolicy() {
        String args = "[{\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.OpenAIPrivatePolicy", args);
    }*/
    public static String OpenAIPrivatePolicy() {
        String args = "[{\"privatePolicyIdList\":[\"AI_CHICK_PRIVATE_POLICY\"],\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.OpenPrivatePolicy", args);
    }
    
    public static String deliverSubjectRecommend(JSONArray userIds) {
        String args = "[{\"friendUserIds\":" + userIds.toString() + ",\"requestType\":\"RPC\",\"sceneCode\":\"ChickFamily\",\"source\":\"H5\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.deliverSubjectRecommend", args);
    }
    
    public static String deliverContentExpand(String ariverRpcTraceId, String eventId, String eventName,
                                              String memo, String resultCode, String sceneId, String sceneName,
                                              boolean success, JSONArray userIds) {
        String args = "[{\"ariverRpcTraceId\":\"" + ariverRpcTraceId + "\",\"eventId\":\"" + eventId + "\",\"eventName\":\"" + eventName +
                      "\",\"friendUserIds\":" + userIds.toString() + ",\"memo\":\"" + memo + "\",\"requestType\":\"NORMAL\",\"resultCode\":\"" + resultCode +
                      "\",\"sceneCode\":\"ANTFARM\",\"sceneId\":\"" + sceneId + "\",\"sceneName\":\"" + sceneName +
                      "\",\"source\":\"H5\",\"success\":" + success + "}]";
        return ApplicationHook.requestString("com.alipay.antfarm.deliverContentExpand", args);
    }
    
    public static String QueryExpandContent(String deliverId) {
        String args = "[{\"deliverId\":\"" + deliverId + "\",\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.QueryExpandContent", args);
    }
    
    public static String deliverMsgSend(String groupId, JSONArray userIds, String content, String deliverId) {
        // 注意转义 content 中的特殊字符
        String escapedContent = content.replace("\"", "\\\"");
        String args = "[{\"content\":\"" + escapedContent + "\",\"deliverId\":\"" + deliverId + "\",\"groupId\":\"" + groupId +
                      "\",\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"userIds\":" + userIds.toString() + "}]";
        return ApplicationHook.requestString("com.alipay.antfarm.deliverMsgSend", args);
    }
    
    public static String inviteFriendVisitFamily(JSONArray inviteList) {
        String args = "[{\"inviteList\":" + inviteList.toString() + ",\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.inviteFriendVisitFamily", args);
    }
    
    public static String assignFamilyMember(String assignAction, String beAssignUser) {
        String args = "[{\"assignAction\":\"" + assignAction + "\",\"beAssignUser\":\"" + beAssignUser +
                      "\",\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.assignFamilyMember", args);
    }
    
    public static String clickForGiftV2(String foodType, int giftIndex) {
        String args = "[{\"foodType\":\""+foodType+"\",\"giftIndex\":"+giftIndex+",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\",\"version\":\""+VERSION+"\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.clickForGiftV2", args);
    }
}
