package io.github.lazyimmortal.sesame.hook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.iki.elonen.NanoHTTPD;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP处理器基类（模板方法模式）
 * 统一处理鉴权、异常和请求方法分发（对应原Kotlin的BaseHandler）
 */
public abstract class BaseHandler implements io.github.lazyimmortal.sesame.hook.HttpHandler {
    private final String secretToken;
    protected final ObjectMapper mapper;
    
    /**
     * 构造方法（对应Kotlin的主构造器）
     * @param secretToken 鉴权秘钥令牌
     */
    public BaseHandler(String secretToken) {
        this.secretToken = secretToken;
        // 复用ServerCommon的全局ObjectMapper单例
        this.mapper = ServerCommon.jsonMapper;
    }
    
    /**
     * 模板方法：统一处理鉴权、异常和请求方法分发（final禁止子类重写）
     */
    @Override
    public final NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session, String body) {
        try {
            // 1. 鉴权验证，失败返回401
            if (!verifyToken(session)) {
                return unauthorized();
            }
            
            // 2. 根据请求方法分发处理
            NanoHTTPD.Method method = session.getMethod();
            if (method == NanoHTTPD.Method.GET) {
                return onGet(session);
            } else if (method == NanoHTTPD.Method.POST) {
                return onPost(session, body);
            } else {
                return methodNotAllowed();
            }
        } catch (Exception e) {
            // 3. 全局异常捕获，返回500响应
            e.printStackTrace(); // 打印堆栈到Logcat
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            Map<String, String> errorData = new HashMap<>();
            errorData.put("status", "error");
            errorData.put("message", errorMsg);
            return json(NanoHTTPD.Response.Status.INTERNAL_ERROR, errorData);
        }
    }
    
    /**
     * 验证鉴权Token
     * 1. 未设置secretToken则直接通过
     * 2. 支持Bearer Token格式（Bearer xxx）或直接传Token
     * @param session HTTP会话
     * @return 鉴权是否通过
     */
    private boolean verifyToken(NanoHTTPD.IHTTPSession session) {
        // 未设置Token，默认通过鉴权（与原Kotlin逻辑一致）
        if (secretToken == null || secretToken.trim().isEmpty()) {
            return true;
        }
        
        // 获取Authorization请求头
        String authHeader = session.getHeaders().get("authorization");
        if (authHeader == null) {
            return false;
        }
        
        // 解析Token（支持Bearer前缀，忽略大小写）
        String token;
        String bearerPrefix = "Bearer ";
        if (authHeader.toLowerCase().startsWith(bearerPrefix.toLowerCase())) {
            token = authHeader.substring(bearerPrefix.length()).trim();
        } else {
            token = authHeader.trim();
        }
        
        // 对比Token是否一致
        return token.equals(secretToken);
    }
    
    /**
     * 处理GET请求（子类可重写，默认返回405）
     * @param session HTTP会话
     * @return HTTP响应
     */
    protected NanoHTTPD.Response onGet(NanoHTTPD.IHTTPSession session) {
        return methodNotAllowed();
    }
    
    /**
     * 处理POST请求（子类可重写，默认返回405）
     * @param session HTTP会话
     * @param body POST请求体
     * @return HTTP响应
     */
    protected NanoHTTPD.Response onPost(NanoHTTPD.IHTTPSession session, String body) {
        return methodNotAllowed();
    }
    
    // --- 响应辅助方法（protected，子类可调用） ---
    
    /**
     * 构建JSON格式响应
     * @param status HTTP状态码
     * @param data 响应数据（String直接使用，其他对象序列化为JSON）
     * @return HTTP响应
     */
    protected NanoHTTPD.Response json(NanoHTTPD.Response.Status status, Object data) {
        String jsonText;
        if (data instanceof String) {
            // 若数据是字符串，直接使用（避免重复序列化）
            jsonText = (String) data;
        } else {
            // 其他类型序列化为JSON字符串
            try {
                jsonText = mapper.writeValueAsString(data);
            } catch (JsonProcessingException e) {
                // 序列化失败返回默认错误JSON
                jsonText = "{\"status\":\"error\",\"message\":\"Failed to serialize response\"}";
            }
        }
        return NanoHTTPD.newFixedLengthResponse(status, ServerCommon.MIME_JSON, jsonText);
    }
    
    /**
     * 构建200 OK的JSON响应
     * @param data 响应数据
     * @return HTTP响应
     */
    protected NanoHTTPD.Response ok(Object data) {
        return json(NanoHTTPD.Response.Status.OK, data);
    }
    
    /**
     * 构建400 Bad Request的JSON响应
     * @param message 错误信息
     * @return HTTP响应
     */
    protected NanoHTTPD.Response badRequest(String message) {
        Map<String, String> data = new HashMap<>();
        data.put("status", "error");
        data.put("message", message);
        return json(NanoHTTPD.Response.Status.BAD_REQUEST, data);
    }
    
    /**
     * 构建401 Unauthorized的JSON响应
     * @return HTTP响应
     */
    protected NanoHTTPD.Response unauthorized() {
        Map<String, String> data = new HashMap<>();
        data.put("status", "unauthorized");
        return json(NanoHTTPD.Response.Status.UNAUTHORIZED, data);
    }
    
    /**
     * 构建405 Method Not Allowed的JSON响应
     * @return HTTP响应
     */
    protected NanoHTTPD.Response methodNotAllowed() {
        Map<String, String> data = new HashMap<>();
        data.put("status", "method_not_allowed");
        return json(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, data);
    }
    
    /**
     * 构建404 Not Found的JSON响应
     * @return HTTP响应
     */
    protected NanoHTTPD.Response notFound() {
        Map<String, String> data = new HashMap<>();
        data.put("status", "not_found");
        return json(NanoHTTPD.Response.Status.NOT_FOUND, data);
    }
}