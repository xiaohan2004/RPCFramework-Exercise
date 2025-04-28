package com.rpc.core.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册中心查询请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistryLookupRequest {
    /**
     * 服务名称
     */
    private String serviceName;
    
    /**
     * 版本号
     */
    private String version;
    
    /**
     * 分组
     */
    private String group;
} 