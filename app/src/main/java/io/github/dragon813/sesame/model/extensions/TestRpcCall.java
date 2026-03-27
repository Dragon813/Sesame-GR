package io.github.dragon813.sesame.model.extensions;

import io.github.dragon813.sesame.hook.ApplicationHook;

public class TestRpcCall {
        private static final String VERSION = "20240704";

        public static String queryEnvironmentCertDetailList(String alias, int pageNum, String targetUserID) {
                return ApplicationHook.requestString("alipay.antforest.forest.h5.queryEnvironmentCertDetailList",
                                "[{\"alias\":\"" + alias + "\",\"certId\":\"\",\"pageNum\":" + pageNum
                                                + ",\"shareId\":\"\",\"source\":\"chInfo_ch_appcenter__chsub_9patch\",\"targetUserID\":\""
                                                + targetUserID + "\",\"version\":\"20230701\"}]");
        }

        public static String sendTree(String certificateId, String friendUserId) {
                return ApplicationHook.requestString("alipay.antforest.forest.h5.sendTree",
                                "[{\"blessWords\":\"梭梭没有叶子，四季常青，从不掉发，祝你发量如梭。\",\"certificateId\":\"" + certificateId
                                                + "\",\"friendUserId\":\"" + friendUserId
                                                + "\",\"source\":\"chInfo_ch_appcenter__chsub_9patch\"}]");
        }

        public static String queryTreeItemsForExchange(String applyActions) {
                return ApplicationHook.requestString("alipay.antforest.forest.h5.queryTreeItemsForExchange",
                                "[{\"applyActions\":\"" + applyActions
                                                + "\",\"itemTypes\":\"\",\"source\":\"chInfo_ch_appcenter__chsub_9patch\",\"version\":\""
                                                + VERSION + "\"}]");
        }

        public static String queryTreeForExchange(String projectId) {
                return ApplicationHook.requestString("alipay.antforest.forest.h5.queryTreeForExchange",
                                "[{\"projectId\":\"" + projectId + "\",\"version\":\"" + VERSION
                                                + "\",\"source\":\"chInfo_ch_appcenter__chsub_9patch\"}]");
        }

        
        /* 查询地图树苗 */
        public static String queryAreaTrees() {
                return ApplicationHook.requestString("alipay.antmember.forest.h5.queryAreaTrees",
                                "[{\"source\":\"chInfo_ch_appcenter__chsub_9patch\",\"userId\":\"\",\"version\":\"20221215\"}]");
        }

}