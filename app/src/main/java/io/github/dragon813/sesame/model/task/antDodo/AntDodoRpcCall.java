package io.github.dragon813.sesame.model.task.antDodo;

import io.github.dragon813.sesame.hook.ApplicationHook;
import io.github.dragon813.sesame.util.RandomUtil;

public class AntDodoRpcCall {

    /* 神奇物种 */

    public static String queryAnimalStatus() {
        return ApplicationHook.requestString("alipay.antdodo.rpc.h5.queryAnimalStatus",
                "[{\"source\":\"chInfo_ch_appcenter__chsub_9patch\"}]");
    }

    public static String homePage() {
        return ApplicationHook.requestString("alipay.antdodo.rpc.h5.homePage",
                "[{}]");
    }

    public static String taskEntrance() {
        return ApplicationHook.requestString("alipay.antdodo.rpc.h5.taskEntrance",
                "[{\"statusList\":[\"TODO\",\"FINISHED\"]}]");
    }

    public static String collect() {
        return ApplicationHook.requestString("alipay.antdodo.rpc.h5.collect",
                "[{}]");
    }

    public static String taskList() {
        return ApplicationHook.requestString("alipay.antdodo.rpc.h5.taskList",
                "[{}]");
    }

    public static String finishTask(String sceneCode, String taskType) {
        String uniqueId = getUniqueId();
        return ApplicationHook.requestString("com.alipay.antiep.finishTask",
                "[{\"outBizNo\":\"" + uniqueId + "\",\"requestType\":\"rpc\",\"sceneCode\":\""
                        + sceneCode + "\",\"source\":\"af-biodiversity\",\"taskType\":\""
                        + taskType + "\",\"uniqueId\":\"" + uniqueId + "\"}]");
    }

    private static String getUniqueId() {
        return String.valueOf(System.currentTimeMillis()) + RandomUtil.nextLong();
    }

    public static String receiveTaskAward(String sceneCode, String taskType) {
        return ApplicationHook.requestString("com.alipay.antiep.receiveTaskAward",
                "[{\"ignoreLimit\":0,\"requestType\":\"rpc\",\"sceneCode\":\"" + sceneCode
                        + "\",\"source\":\"af-biodiversity\",\"taskType\":\"" + taskType
                        + "\"}]");
    }

    public static String propList() {
        return ApplicationHook.requestString("alipay.antdodo.rpc.h5.propList",
                "[{}]");
    }

    public static String consumeProp(String propId, String propType) {
        return ApplicationHook.requestString("alipay.antdodo.rpc.h5.consumeProp",
                "[{\"propId\":\"" + propId + "\",\"propType\":\"" + propType + "\"}]");
    }

    public static String consumeProp(String propId, String propType, String animalId) {
        String args = "[{\"extendInfo\":{\"animalId\":\"" + animalId + "\"},"
                + "\"propId\":\"" + propId + "\",\"propType\":\"" + propType + "\"}]";
        return ApplicationHook.requestString("alipay.antdodo.rpc.h5.consumeProp", args);
    }

    public static String queryBookList() {
        String args = "[{\"pageSize\":18,\"v2\":\"true\"}]";
        return ApplicationHook.requestString("alipay.antdodo.rpc.h5.queryBookList", args);
    }

    public static String queryBookList(int pageSize, int pageStart) {
        String args = "[{\"pageSize\":" + pageSize + ",\"pageStart\":\"" + pageStart + "\",\"v2\":\"true\"}]";
        return ApplicationHook.requestString("alipay.antdodo.rpc.h5.queryBookList", args);
    }

    public static String queryBookInfo(String bookId) {
        return ApplicationHook.requestString("alipay.antdodo.rpc.h5.queryBookInfo",
                "[{\"bookId\":\"" + bookId + "\"}]");
    }

    public static String generateBookMedal(String bookId) {
        String args = "[{\"bookId\":\"" + bookId + "\"}]";
        return ApplicationHook.requestString("alipay.antdodo.rpc.h5.generateBookMedal", args);
    }

    // 送卡片给好友
    public static String social(String targetAnimalId, String targetUserId) {
        return ApplicationHook.requestString("alipay.antdodo.rpc.h5.social",
                "[{\"actionCode\":\"GIFT_TO_FRIEND\",\"source\":\"GIFT_TO_FRIEND_FROM_CC\",\"targetAnimalId\":\""
                        + targetAnimalId + "\",\"targetUserId\":\"" + targetUserId
                        + "\",\"triggerTime\":\"" + System.currentTimeMillis() + "\"}]");
    }
    
    public static String queryFriend() {
        return ApplicationHook.requestString("alipay.antdodo.rpc.h5.queryFriend",
                "[{\"sceneCode\":\"EXCHANGE\"}]");
    }
    
    public static String collect(String targetUserId) {
        return ApplicationHook.requestString("alipay.antdodo.rpc.h5.collect",
                "[{\"targetUserId\":" + targetUserId + "}]");
    }
}