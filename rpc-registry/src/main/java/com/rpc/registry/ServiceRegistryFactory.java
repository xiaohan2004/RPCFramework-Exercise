package com.rpc.registry;

import com.rpc.registry.local.LocalServiceRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务注册工厂，用于创建不同类型的注册中心实现
 */
@Slf4j
public class ServiceRegistryFactory {
    /**
     * 本地注册中心类型
     */
    public static final String LOCAL_REGISTRY = "local";
    
    /**
     * 文件注册中心类型
     */
    public static final String FILE_REGISTRY = "file";
    
    /**
     * 本地注册中心单例
     */
    private static final LocalServiceRegistry localServiceRegistry = new LocalServiceRegistry();
    
    /**
     * 创建服务注册实例
     *
     * @param registryType 注册中心类型
     * @param address 注册中心地址
     * @return 服务注册实例
     */
    public static ServiceRegistry getServiceRegistry(String registryType, String address) {
        switch (registryType.toLowerCase()) {
            case LOCAL_REGISTRY:
                log.info("使用本地内存注册中心");
                return localServiceRegistry; // 返回单例
            case FILE_REGISTRY:
                log.info("创建文件注册中心: {}", address);
                // TODO: 实现文件注册中心
                return localServiceRegistry;
            default:
                log.warn("未知的注册中心类型: {}, 使用默认的本地内存注册中心", registryType);
                return localServiceRegistry;
        }
    }
} 