package com.rpc.core.protocol;

import com.rpc.common.model.ServiceInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 注册中心查询响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistryLookupResponse {
    /**
     * 服务列表
     */
    private List<ServiceInfo> services;
} 