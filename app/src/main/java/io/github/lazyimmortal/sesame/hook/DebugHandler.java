package io.github.lazyimmortal.sesame.hook;

import fi.iki.elonen.NanoHTTPD;

import java.util.HashMap;
import java.util.Map;

/**
 * 调试接口处理器（继承BaseHandler，处理RPC调试请求）
 * 对应原Kotlin的DebugHandler
 */
public class DebugHandler extends io.github.lazyimmortal.sesame.hook.BaseHandler {
    
    /**
     * 构造方法（调用父类BaseHandler的构造器传入鉴权Token）
     * @param secretToken 鉴权秘钥令牌
     */
    public DebugHandler(String secretToken) {
        super(secretToken);
    }
    
    /**
     * 重写POST请求处理逻辑（核心业务逻辑）
     * 1. 校验请求体非空 2. 解析RPC请求 3. 校验参数 4. 调用RPC 5. 返回结果
     */
    @Override
    protected NanoHTTPD.Response onPost(NanoHTTPD.IHTTPSession session, String body) {
        // 1. 校验请求体是否为空/空白字符串
        if (body == null || body.trim().isEmpty()) {
            return badRequest("Empty body");
        }
        
        // 2. 解析请求体为RpcRequest对象
        RpcRequest request;
        try {
            request = mapper.readValue(body, RpcRequest.class);
        } catch (Exception e) {
            // 解析失败返回400，携带错误信息
            return badRequest("Invalid JSON: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
        
        // 3. 获取requestData的字符串形式，并校验核心字段非空
        String dataStr = request.getRequestDataString(mapper);
        if (request.getMethodName() == null || request.getMethodName().trim().isEmpty() ||
            dataStr == null || dataStr.trim().isEmpty()) {
            return badRequest("Fields cannot be empty");
        }
        
        // 4. 调用RPC并处理结果
        try {
            String result = ApplicationHook.requestString(request.getMethodName(), dataStr);
            
            // 5. 根据RPC返回结果构建响应
            if (result == null || result.trim().isEmpty()) {
                // 返回status=empty的JSON响应
                Map<String, String> emptyData = new HashMap<>();
                emptyData.put("status", "empty");
                return json(NanoHTTPD.Response.Status.OK, emptyData);
            } else {
                // 直接返回RPC返回的JSON字符串
                return NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.OK,
                        ServerCommon.MIME_JSON,
                        result
                );
            }
        } catch (Exception e) {
            // RPC调用异常返回400
            return badRequest("RPC Error: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
}