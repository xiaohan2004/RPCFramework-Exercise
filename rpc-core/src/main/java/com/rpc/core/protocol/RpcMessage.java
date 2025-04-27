package com.rpc.core.protocol;

import lombok.Data;
import lombok.ToString;

/**
 * RPC消息格式定义
 */
@Data
@ToString
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
     */
    private Object data;
} 