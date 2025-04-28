package com.rpc.core.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.ToString;

/**
 * RPC消息格式定义
 */
@Data
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class RpcMessage {
    /**
     * 消息类型
     */
    private byte messageType;
    
    /**
     * 序列化类型
     */
    private byte serializationType;
    
    /**
     * 压缩类型
     */
    private byte compressType;
    
    /**
     * 请求ID
     */
    private long requestId;
    
    /**
     * 消息状态
     */
    private byte status;
    
    /**
     * 消息体数据
     * 使用Jackson的@JsonTypeInfo注解来处理多态
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
    private Object data;
} 