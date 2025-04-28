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
        // 服务名必须非空
        if (serviceName == null || serviceName.isEmpty()) {
            return "";
        }
        
        // 统一处理null值，确保一致性
        String ver = (version == null || version.isEmpty()) ? "" : version;
        String grp = (group == null || group.isEmpty()) ? "" : group;
        
        StringBuilder keyBuilder = new StringBuilder(serviceName);
        // 统一添加分隔符，即使版本或组为空
        keyBuilder.append("_").append(ver);
        keyBuilder.append("_").append(grp);
        
        return keyBuilder.toString();
    }
} 