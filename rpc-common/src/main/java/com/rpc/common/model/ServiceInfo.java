package com.rpc.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

/**
 * 服务信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ServiceInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 服务名称
     */
    private String serviceName;
    
    /**
     * 服务版本
     */
    private String version;
    
    /**
     * 服务分组
     */
    private String group;
    
    /**
     * 服务地址（IP:端口）
     */
    private String address;
    
    /**
     * 权重，可用于负载均衡
     */
    private int weight = 1;
    
    /**
     * 获取服务唯一标识
     */
    public String getServiceKey() {
        return this.serviceName + 
               (this.version != null ? "_" + this.version : "") + 
               (this.group != null ? "_" + this.group : "");
    }
} 