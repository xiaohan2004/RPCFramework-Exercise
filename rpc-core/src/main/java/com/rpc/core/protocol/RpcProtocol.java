package com.rpc.core.protocol;

/**
 * RPC协议定义
 */
public class RpcProtocol {
    // 魔数，用于快速识别RPC协议包
    public static final byte[] MAGIC_NUMBER = new byte[] {(byte) 'M', (byte) 'R', (byte) 'P', (byte) 'C'};
    public static final byte VERSION = 1;
    
    // 消息类型
    public static final byte REQUEST_TYPE = 1;
    public static final byte RESPONSE_TYPE = 2;
    public static final byte HEARTBEAT_REQUEST_TYPE = 3;
    public static final byte HEARTBEAT_RESPONSE_TYPE = 4;
    
    // 序列化类型
    public static final byte SERIALIZATION_JSON = 1;
    
    // 压缩类型
    public static final byte COMPRESS_TYPE_NONE = 0;
    
    // 状态
    public static final byte STATUS_SUCCESS = 0;
    public static final byte STATUS_FAIL = 1;
    
    private RpcProtocol() {
        // 工具类，禁止实例化
    }
} 