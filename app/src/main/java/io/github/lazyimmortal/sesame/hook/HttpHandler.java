package io.github.lazyimmortal.sesame.hook;

import fi.iki.elonen.NanoHTTPD;

/**
 * HTTP处理器核心接口（对应原Kotlin的HttpHandler接口）
 * 定义所有HTTP处理器必须实现的请求处理方法
 */
public interface HttpHandler {
    
    /**
     * 处理HTTP请求的核心方法
     * Kotlin中的默认参数（body: String? = null）在Java中无需显式声明，
     * 调用方传null即可等效实现默认值逻辑
     *
     * @param session HTTP会话（NanoHTTPD的IHTTPSession）
     * @param body    请求体（可为null，对应Kotlin的String?）
     * @return HTTP响应（NanoHTTPD的Response）
     */
    NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session, String body);
}