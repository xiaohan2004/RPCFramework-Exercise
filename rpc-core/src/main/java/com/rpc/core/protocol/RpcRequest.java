package com.rpc.core.protocol;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.util.Arrays;

/**
 * RPC请求数据结构
 */
@Data
@ToString
public class RpcRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 请求的服务名称
     */
    private String serviceName;
    
    /**
     * 请求的方法名称
     */
    private String methodName;
    
    /**
     * 参数类型列表
     */
    private Class<?>[] parameterTypes;
    
    /**
     * 参数值列表
     */
    private Object[] parameters;
    
    /**
     * 服务版本
     */
    private String version;
    
    /**
     * 服务分组
     */
    private String group;
    
    /**
     * 获取服务的唯一标识
     */
    public String getRpcServiceKey() {
        return this.getServiceName() + 
               (this.getVersion() != null ? "_" + this.getVersion() : "") + 
               (this.getGroup() != null ? "_" + this.getGroup() : "");
    }
} 