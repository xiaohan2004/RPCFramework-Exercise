package com.rpc.core.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rpc.core.exception.SerializationException;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON工具类，提供序列化和反序列化功能
 */
@Slf4j
public class JsonUtils {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            // 不因未知属性失败
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            // 不因空bean失败
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            // 启用类型信息包含，支持多态
            .enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL)
            // 排除null值
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /**
     * 将对象序列化为JSON字符串
     */
    public static String toJsonString(Object obj) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(obj);
            if (log.isDebugEnabled()) {
                log.debug("序列化对象: {} -> {}", obj.getClass().getName(), json);
            }
            return json;
        } catch (Exception e) {
            log.error("序列化对象为JSON字符串失败: {}", e.getMessage(), e);
            throw new SerializationException("序列化为JSON字符串失败", e);
        }
    }

    /**
     * 将对象序列化为字节数组
     */
    public static byte[] toJsonBytes(Object obj) {
        try {
            byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(obj);
            if (log.isDebugEnabled()) {
                log.debug("序列化对象: {} -> 字节数组，长度: {}", obj.getClass().getName(), bytes.length);
            }
            return bytes;
        } catch (Exception e) {
            log.error("序列化对象为JSON字节数组失败: {}", e.getMessage(), e);
            throw new SerializationException("序列化为JSON字节数组失败", e);
        }
    }

    /**
     * 将JSON字符串反序列化为对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            T obj = OBJECT_MAPPER.readValue(json, clazz);
            if (log.isDebugEnabled()) {
                log.debug("反序列化JSON字符串为对象: {} -> {}", json, obj);
            }
            return obj;
        } catch (Exception e) {
            log.error("从JSON字符串反序列化对象失败: {}, 类: {}, JSON: {}", e.getMessage(), clazz.getName(), json, e);
            throw new SerializationException("从JSON字符串反序列化失败", e);
        }
    }

    /**
     * 将字节数组反序列化为对象
     */
    public static <T> T fromJson(byte[] bytes, Class<T> clazz) {
        try {
            T obj = OBJECT_MAPPER.readValue(bytes, clazz);
            if (log.isDebugEnabled() && bytes.length < 1000) {
                log.debug("反序列化字节数组为对象: {} -> {}", new String(bytes), obj);
            } else if (log.isDebugEnabled()) {
                log.debug("反序列化字节数组为对象: 长度={} -> {}", bytes.length, obj);
            }
            return obj;
        } catch (Exception e) {
            String jsonPreview = bytes.length < 200 ? new String(bytes) : new String(bytes).substring(0, 200) + "...";
            log.error("从JSON字节数组反序列化对象失败: {}, 类: {}, JSON前200字节: {}", e.getMessage(), clazz.getName(), jsonPreview, e);
            throw new SerializationException("从JSON字节数组反序列化失败", e);
        }
    }

    /**
     * 获取ObjectMapper实例，用于高级操作
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
} 