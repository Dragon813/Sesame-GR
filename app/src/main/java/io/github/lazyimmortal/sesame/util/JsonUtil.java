package io.github.lazyimmortal.sesame.util;

import android.annotation.SuppressLint;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

@SuppressLint("SimpleDateFormat")
public class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final TypeFactory TYPE_FACTORY = TypeFactory.defaultInstance();

    public static final JsonFactory JSON_FACTORY = new JsonFactory();

    static {
        //反序列化的时候如果多了其他属性,不抛出异常
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        //如果是空对象的时候,不抛异常
        MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        //属性为null不转换
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        MAPPER.setTimeZone(TimeZone.getDefault());
        MAPPER.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
    }

    public static ObjectMapper copyMapper() {
        return MAPPER.copy();
    }

    public static String toJsonString(Object object) {
        try {
            return MAPPER.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String toFormatJsonString(Object object) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 无损回写校验：rewritten 必须完整包含 source 的全部数据（只允许改格式，不允许丢键/改值）。
     * 用于配置加载时的格式化回写——防止内存残留值把磁盘数据覆盖丢失。
     * 判定不了就返回 false（调用方应保留磁盘原文）。
     */
    public static boolean isRewriteLossless(String sourceJson, String rewrittenJson) {
        try {
            return dataContainedIn(MAPPER.readTree(sourceJson), MAPPER.readTree(rewrittenJson));
        } catch (Throwable t) {
            Log.printStackTrace(t);
            return false;
        }
    }

    /** source 的每个键都必须在 rewritten 中存在且值被包含（键消失即不无损） */
    private static boolean dataContainedIn(JsonNode source, JsonNode rewritten) {
        if (source == null || source.isNull()) {
            return true;
        }
        if (source.isObject()) {
            if (rewritten == null || !rewritten.isObject()) {
                return false;
            }
            Iterator<Map.Entry<String, JsonNode>> it = source.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                JsonNode rewrittenValue = rewritten.get(entry.getKey());
                if (rewrittenValue == null) {
                    // 只允许改格式、不许改数据：键消失即判定不无损
                    return false;
                }
                if (!dataContainedIn(entry.getValue(), rewrittenValue)) {
                    return false;
                }
            }
            return true;
        }
        if (source.isArray()) {
            if (rewritten == null || !rewritten.isArray()) {
                return false;
            }
            if (isScalarArray(source)) {
                // 标量数组（Set 类名单）：顺序无意义，按重数比（minSdk21 不用 Map.merge/computeIfPresent，手动计数）
                if (!isScalarArray(rewritten)) {
                    return false;
                }
                Map<String, Integer> need = new HashMap<>();
                for (JsonNode node : source) {
                    String key = node.toString();
                    Integer count = need.get(key);
                    need.put(key, count == null ? 1 : count + 1);
                }
                for (JsonNode node : rewritten) {
                    String key = node.toString();
                    Integer count = need.get(key);
                    if (count != null) {
                        need.put(key, count - 1);
                    }
                }
                for (Integer left : need.values()) {
                    if (left != null && left > 0) {
                        return false;
                    }
                }
                return true;
            }
            // 对象数组：不许变短，逐位同值
            if (rewritten.size() < source.size()) {
                return false;
            }
            for (int i = 0; i < source.size(); i++) {
                if (!dataContainedIn(source.get(i), rewritten.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return source.equals(rewritten);
    }

    /** 元素全是标量（Set 的序列化形式） */
    private static boolean isScalarArray(JsonNode array) {
        for (JsonNode node : array) {
            if (node.isObject() || node.isArray()) {
                return false;
            }
        }
        return true;
    }

    public static JsonParser getJsonParser(String body) {
        try {
            return JSON_FACTORY.createParser(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseObject(String body, Type type) {
        try {
            return MAPPER.readValue(body, TYPE_FACTORY.constructType(type));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseObject(String body, JavaType javaType) {
        try {
            return MAPPER.readValue(body, javaType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseObject(String body, TypeReference<T> valueTypeRef) {
        try {
            return MAPPER.readValue(body, valueTypeRef);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseObject(String body, Class<T> clazz) {
        try {
            return MAPPER.readValue(body, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseObject(JsonParser jsonParser, Type type) {
        try {
            return MAPPER.readValue(jsonParser, TYPE_FACTORY.constructType(type));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseObject(JsonParser jsonParser, JavaType javaType) {
        try {
            return MAPPER.readValue(jsonParser, javaType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseObject(JsonParser jsonParser, TypeReference<T> valueTypeRef) {
        try {
            return MAPPER.readValue(jsonParser, valueTypeRef);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseObject(JsonParser jsonParser, Class<T> clazz) {
        try {
            return MAPPER.readValue(jsonParser, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseObject(Object bean, Type type) {
        try {
            return MAPPER.convertValue(bean, TYPE_FACTORY.constructType(type));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseObject(Object bean, JavaType javaType) {
        try {
            return MAPPER.convertValue(bean, javaType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseObject(Object bean, TypeReference<T> valueTypeRef) {
        try {
            return MAPPER.convertValue(bean, valueTypeRef);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseObject(Object bean, Class<T> clazz) {
        try {
            return MAPPER.convertValue(bean, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String parseString(String body, String field) {
        JsonNode node = null;
        try {
            node = MAPPER.readTree(body);
            JsonNode leaf = node.get(field);
            if (leaf != null) {
                return leaf.asText();
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Integer parseInteger(String body, String field) {
        JsonNode node = null;
        try {
            node = MAPPER.readTree(body);
            JsonNode leaf = node.get(field);
            if (leaf != null) {
                return leaf.asInt();
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Integer> parseIntegerList(String body, String field) {
        JsonNode node = null;
        try {
            node = MAPPER.readTree(body);
            JsonNode leaf = node.get(field);

            if (leaf != null) {
                return MAPPER.convertValue(leaf, new TypeReference<List<Integer>>() {
                });
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static Boolean parseBoolean(String body, String field) {
        JsonNode node = null;
        try {
            node = MAPPER.readTree(body);
            JsonNode leaf = node.get(field);
            if (leaf != null) {
                return leaf.asBoolean();
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Short parseShort(String body, String field) {
        JsonNode node = null;
        try {
            node = MAPPER.readTree(body);
            JsonNode leaf = node.get(field);
            if (leaf != null) {
                Integer value = leaf.asInt();
                return value.shortValue();
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Byte parseByte(String body, String field) {
        JsonNode node = null;
        try {
            node = MAPPER.readTree(body);
            JsonNode leaf = node.get(field);
            if (leaf != null) {
                Integer value = leaf.asInt();
                return value.byteValue();
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseObject(String body, String field, Class<T> clazz) {
        JsonNode node = null;
        try {
            node = MAPPER.readTree(body);
            node = node.get(field);
            return MAPPER.treeToValue(node, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> List<T> parseList(String body, Class<T> clazz) {
        try {
            return MAPPER.readValue(body, TYPE_FACTORY.constructCollectionType(ArrayList.class, clazz));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Object toNode(String json) {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 根据给定的点分隔符路径从JSONObject中获取值。
     *
     * @param jsonObject JSONObject对象
     * @param path       点分隔符或包含嵌套属性的形式的路径，例如 "taskExtProps.TASK_MORPHO_DETAIL.[0].title"
     * @return 找到值的话返回其String表现形式；如果任何层级键不存在或不是预期类型，则返回 null。
     */
    public static String getValueByPath(JSONObject jsonObject, String path) {
        Object object = getValueByPathObject(jsonObject, path);
        return object == null ? "" : String.valueOf(object);
    }

    public static Object getValueByPathObject(JSONObject jsonObject, String path) {
        // 使用正斜杠/作为Token分隔符号来直接跳过数组下标的解析逻辑部分并直接取嵌套属性
        // 使用正则表达式分割，但保留[]内的内容
        String[] parts = path.split("\\.");
        try {
            Object current = jsonObject;
            for (String part : parts) {
                if (current instanceof JSONObject) {
                    //对象取属性
                    current = ((JSONObject) current).get(part);
                } else if (current instanceof JSONArray) {
                    //数组取索引
                    JSONArray array = (JSONArray) current;
                    String p = part.replaceAll("\\D", "");
                    int index = Integer.parseInt(p);
                    current = array.get(index);
                } else if (part.contains("[")) {
                    //不是对象、数组，当成字符串重新解析，如果字符串是数组
                    JSONArray array = new JSONArray(current.toString());
                    String p = part.replaceAll("\\D", "");
                    int index = Integer.parseInt(p);
                    current = array.get(index);
                } else {
                    //不是对象、数组，当成字符串重新解析，再取属性
                    JSONObject object = new JSONObject(current.toString());
                    current = object.get(part);
                }
            }
            // 返回结果时检查是否确实找到了相应的值且非null，并转换成字符串形式返回
            return current;
        } catch (Exception e) {
            // JSONException、NumberFormatException等异常都被捕获，并默认行为是返回null.
            return null;
        }
    }

    public static List<String> jsonArrayToList(JSONArray jsonArray) {
        List<String> list = new ArrayList<>();
        for (int i = 0, len = jsonArray.length(); i < len; i++) {
            try {
                list.add(jsonArray.getString(i));
            } catch (Exception e) {
                Log.printStackTrace(e);
                list.add("");
            }
        }
        return list;
    }

}
