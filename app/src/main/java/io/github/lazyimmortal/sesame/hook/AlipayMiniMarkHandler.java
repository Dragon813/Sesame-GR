package io.github.lazyimmortal.sesame.hook;

import io.github.lazyimmortal.sesame.hook.AlipayMiniMarkHelper;
import fi.iki.elonen.NanoHTTPD;
import java.util.Map;

/**
 * 支付宝小程序标记处理器
 * 提供获取支付宝小程序标记的接口（对应原Kotlin的AlipayMiniMarkHandler）
 */
public class AlipayMiniMarkHandler implements io.github.lazyimmortal.sesame.hook.HttpHandler {
    
    /**
     * 处理HTTP请求
     * 仅支持GET请求，其他请求返回405 Method Not Allowed
     *
     * @param session HTTP会话
     * @param body    请求体（GET请求无body，未使用）
     * @return HTTP响应
     */
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session, String body) {
        // 匹配请求方法，仅处理GET
        if (session.getMethod() == NanoHTTPD.Method.GET) {
            return handleGetRequest(session);
        } else {
            return createResponse(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "{\"error\":\"Method not allowed\"}");
        }
    }
    
    /**
     * 处理GET请求
     * 解析appid和version参数，调用工具类获取小程序标记并返回
     *
     * @param session HTTP会话
     * @return HTTP响应
     */
    private NanoHTTPD.Response handleGetRequest(NanoHTTPD.IHTTPSession session) {
        // 获取GET请求参数（parms是NanoHTTPD解析后的参数Map）
        Map<String, String> params = session.getParms();
        String appid = params.get("appid");
        String version = params.get("version");
        
        // 参数验证：空值/空白字符串检查（等效Kotlin的isNullOrBlank）
        if (appid == null || appid.trim().isEmpty() || version == null || version.trim().isEmpty()) {
            return createResponse(
                    NanoHTTPD.Response.Status.BAD_REQUEST,
                    "{\"error\":\"参数缺失，请提供appid和version参数\"}"
            );
        }
        
        try {
            // 调用AlipayMiniMarkHelper获取小程序标记
            String miniMark = AlipayMiniMarkHelper.getAlipayMiniMark(appid, version);
            
            // 处理返回结果：非空则成功，否则失败
            if (miniMark != null && !miniMark.trim().isEmpty()) {
                // 拼接成功响应JSON（转义双引号，避免语法错误）
                String successJson = String.format("{\"success\":true,\"alipayMiniMark\":\"%s\"}", miniMark);
                return createResponse(NanoHTTPD.Response.Status.OK, successJson);
            } else {
                return createResponse(
                        NanoHTTPD.Response.Status.INTERNAL_ERROR,
                        "{\"error\":\"获取支付宝小程序标记失败\"}"
                );
            }
        } catch (Exception e) {
            // 捕获所有异常，返回带错误信息的500响应
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            String errorJson = String.format("{\"error\":\"服务器内部错误: %s\"}", errorMsg);
            return createResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, errorJson);
        }
    }
    
    /**
     * 创建JSON格式的HTTP响应
     * 统一设置Content-Type为application/json
     *
     * @param status   HTTP状态码
     * @param jsonBody JSON格式的响应体
     * @return NanoHTTPD Response对象
     */
    private NanoHTTPD.Response createResponse(NanoHTTPD.Response.Status status, String jsonBody) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", jsonBody);
    }
}