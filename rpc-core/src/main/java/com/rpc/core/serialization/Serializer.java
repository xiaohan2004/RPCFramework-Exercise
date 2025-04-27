package com.rpc.core.serialization;

/**
 * 序列化接口
 */
public interface Serializer {
    /**
     * 序列化
     *
     * @param obj 要序列化的对象
     * @return 序列化后的字节数组
     */
    <T> byte[] serialize(T obj);

    /**
     * 反序列化
     *
     * @param data 序列化后的字节数组
     * @param clazz 目标类型
     * @return 反序列化后的对象
     */
    <T> T deserialize(byte[] data, Class<T> clazz);
} 