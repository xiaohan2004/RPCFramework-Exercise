package com.rpc.registry;

import com.rpc.common.model.ServiceInfo;

import java.util.List;

/**
 * 服务注册接口
 */
public interface ServiceRegistry {
    /**
     * 注册服务
     *
     * @param serviceInfo 服务信息
     */
    void register(ServiceInfo serviceInfo);
    
    /**
     * 注销服务
     *
     * @param serviceInfo 服务信息
     */
    void unregister(ServiceInfo serviceInfo);
    
    /**
     * 发现服务
     *
     * @param serviceName 服务名称
     * @param version 版本号
     * @param group 分组
     * @return 服务实例列表
     */
    List<ServiceInfo> discover(String serviceName, String version, String group);
    
    /**
     * 销毁注册中心客户端
     */
    void destroy();
} 