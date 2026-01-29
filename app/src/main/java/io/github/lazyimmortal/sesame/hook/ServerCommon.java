package io.github.lazyimmortal.sesame.hook;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 服务端通用工具类（纯Java版，无需Kotlin模块）
 */
public class ServerCommon {
    // 全局ObjectMapper单例（纯Java场景无需注册Kotlin模块）
    public static final ObjectMapper jsonMapper;
    
    public static final String MIME_JSON = "application/json";
    public static final String MIME_PLAINTEXT = "text/plain";
    
    static {
        // 去掉KotlinModule注册，直接初始化ObjectMapper
        jsonMapper = new ObjectMapper();
        // 可选：添加Java常用的配置（比如忽略未知字段）
        // jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    private ServerCommon() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}