package com.rpc.core.serialization;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rpc.core.exception.SerializationException;

/**
 * 基于Jackson的JSON序列化实现
 */
public class JacksonSerializer implements Serializer {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        // 不因未知属性失败
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        // 不因空bean失败
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    @Override
    public <T> byte[] serialize(T obj) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(obj);
        } catch (Exception e) {
            throw new SerializationException("序列化对象时发生错误: " + obj.getClass().getName(), e);
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(data, clazz);
        } catch (Exception e) {
            throw new SerializationException("反序列化对象时发生错误: " + clazz.getName(), e);
        }
    }
} 