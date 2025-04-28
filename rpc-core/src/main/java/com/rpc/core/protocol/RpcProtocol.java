package com.rpc.core.protocol;

/**
 * RPC协议相关常量
 */
public class RpcProtocol {
    /**
     * 魔数，用于标识RPC协议包
     */
    public static final byte[] MAGIC_NUMBER = {(byte) 'r', (byte) 'p', (byte) 'c'};
    
    /**
     * 协议版本号
     */
    public static final byte VERSION = 1;
    
    /**
     * 请求类型
     */
    public static final byte REQUEST_TYPE = 1;
    
    /**
     * 响应类型
     */
    public static final byte RESPONSE_TYPE = 2;
    
    /**
     * 心跳请求类型
     */
    public static final byte HEARTBEAT_REQUEST_TYPE = 3;
    
    /**
     * 心跳响应类型
     */
    public static final byte HEARTBEAT_RESPONSE_TYPE = 4;
    
    /**
     * 注册中心消息类型 - 注册服务
     */
    public static final byte REGISTRY_REGISTER_TYPE = 5;
    
    /**
     * 注册中心消息类型 - 注销服务
     */
    public static final byte REGISTRY_UNREGISTER_TYPE = 6;
    
    /**
     * 注册中心消息类型 - 查询服务
     */
    public static final byte REGISTRY_LOOKUP_TYPE = 7;
    
    /**
     * 注册中心消息类型 - 响应
     */
    public static final byte REGISTRY_RESPONSE_TYPE = 8;
    
    /**
     * JSON序列化类型
     */
    public static final byte SERIALIZATION_JSON = 1;
    
    /**
     * 无压缩
     */
    public static final byte COMPRESS_TYPE_NONE = 0;
    
    /**
     * 成功状态
     */
    public static final byte STATUS_SUCCESS = 0;
    
    /**
     * 失败状态
     */
    public static final byte STATUS_FAIL = 1;
    
    private RpcProtocol() {
        // 工具类，禁止实例化
    }
} 