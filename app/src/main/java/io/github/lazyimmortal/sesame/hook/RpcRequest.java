package io.github.lazyimmortal.sesame.hook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * RPC请求数据模型（对应原Kotlin的RpcRequest数据类）
 * 支持requestData为String/Object/null，提供安全转字符串方法
 */
@JsonIgnoreProperties(ignoreUnknown = true) // 等效Kotlin的注解
public class RpcRequest {
    // 成员变量（对应Kotlin的val，提供getter，无setter保持只读）
    private final String methodName;
    private final Object requestData;
    
    /**
     * 无参构造方法（Jackson反序列化必需）
     * 等效Kotlin数据类的默认值：methodName=""，requestData=null
     */
    public RpcRequest() {
        this.methodName = "";
        this.requestData = null;
    }
    
    /**
     * 全参构造方法（等效Kotlin数据类的主构造器）
     * @param methodName 方法名
     * @param requestData 请求数据（支持String/Object/null）
     */
    public RpcRequest(String methodName, Object requestData) {
        // 处理null值，保证和Kotlin默认值一致
        this.methodName = methodName == null ? "" : methodName;
        this.requestData = requestData;
    }
    
    /**
     * 将 requestData 安全转换为字符串
     * 逻辑完全等效Kotlin的when表达式：String直接返回/Null返回空串/其他转JSON
     * @param mapper ObjectMapper实例
     * @return 转换后的字符串
     */
    public String getRequestDataString(ObjectMapper mapper) {
        if (requestData instanceof String) {
            // 如果是字符串类型，直接返回
            return (String) requestData;
        } else if (requestData == null) {
            // null返回空字符串
            return "";
        } else {
            // 其他类型（Object/数组等）转为JSON字符串
            try {
                return mapper.writeValueAsString(requestData);
            } catch (JsonProcessingException e) {
                // 序列化失败时返回空串（保持和原逻辑一致的容错性）
                return "";
            }
        }
    }
    
    // Getter方法（Jackson序列化/外部访问必需，等效Kotlin的val自动生成的getter）
    public String getMethodName() {
        return methodName;
    }
    
    public Object getRequestData() {
        return requestData;
    }
    
    // 可选：重写toString方法，方便日志打印（等效Kotlin数据类自动生成的toString）
    @Override
    public String toString() {
        return "RpcRequest{" +
               "methodName='" + methodName + '\'' +
               ", requestData=" + requestData +
               '}';
    }
}