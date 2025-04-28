package com.rpc.registry;

import lombok.extern.slf4j.Slf4j;

/**
 * 服务注册工厂，用于创建注册中心实现
 */
@Slf4j
public class ServiceRegistryFactory {
    /**
     * 创建服务注册实例
     *
     * @param address 注册中心地址
     * @return 服务注册实例
     */
    public static ServiceRegistry getServiceRegistry(String address) {
        return getServiceRegistry(address, true);
    }
    
    /**
     * 创建服务注册实例
     *
     * @param address 注册中心地址
     * @param enableHeartbeat 是否启用心跳
     * @return 服务注册实例
     */
    public static ServiceRegistry getServiceRegistry(String address, boolean enableHeartbeat) {
        if (address == null || address.isEmpty()) {
            throw new IllegalArgumentException("注册中心地址不能为空");
        }
        
        log.info("创建远程注册中心客户端: {}, 心跳: {}", address, enableHeartbeat ? "启用" : "禁用");
        return new RemoteServiceRegistry(address, enableHeartbeat);
    }
} 