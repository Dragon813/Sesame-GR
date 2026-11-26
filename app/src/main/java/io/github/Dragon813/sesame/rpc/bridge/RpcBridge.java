package io.github.Dragon813.sesame.rpc.bridge;

import io.github.Dragon813.sesame.entity.RpcEntity;

public interface RpcBridge {

    RpcVersion getVersion();

    void load() throws Exception;

    void unload();

    String requestString(RpcEntity rpcEntity, int tryCount, int retryInterval);

    default String requestString(RpcEntity rpcEntity) {
        return requestString(rpcEntity, 3, -1);
    }

    default String requestString(String method, String data) {
        return requestString(method, data, 3, -1);
    }

    default String requestString(String method, String data, String relation) {
        return requestString(method, data, relation, 3, -1);
    }


    default String requestString(String method, String data, int tryCount, int retryInterval) {
        return requestString(new RpcEntity(method, data), tryCount, retryInterval);
    }

    default String requestString(String method, String data, String relation, int tryCount, int retryInterval) {
        return requestString(new RpcEntity(method, data, relation), tryCount, retryInterval);
    }


    RpcEntity requestObject(RpcEntity rpcEntity, int tryCount, int retryInterval);

    default RpcEntity requestObject(RpcEntity rpcEntity) {
        return requestObject(rpcEntity, 3, -1);
    }

    default RpcEntity requestObject(String method, String data) {
        return requestObject(method, data, 3, -1);
    }

    default RpcEntity requestObject(String method, String data, String relation) {
        return requestObject(method, data, relation, 3, -1);
    }


    default RpcEntity requestObject(String method, String data, int tryCount, int retryInterval) {
        return requestObject(new RpcEntity(method, data), tryCount, retryInterval);
    }

    default RpcEntity requestObject(String method, String data, String relation, int tryCount, int retryInterval) {
        return requestObject(new RpcEntity(method, data, relation), tryCount, retryInterval);
    }

    /**
     * 原样发送调用方拼好的信封 JSON，不做任何字段加工。
     * 用来逐字节复现抓包里的真实请求（例如手点的 platformTaskSignUp 信封只有 4 个字段）。
     * 不支持的桥接实现返回 null，调用方自行回落完整信封。
     *
     * @param envelopeJson 完整信封 JSON（对象）
     * @return 响应字符串，不支持时返回 null
     */
    default String requestRawEnvelope(String method, String envelopeJson) {
        return null;
    }


}
