package com.rpc.client.local;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地服务工厂，用于管理本地服务实现
 * 客户端可以在这里注册本地服务实现，当满足条件时将使用本地服务而不是远程调用
 */
@Slf4j
public class LocalServiceFactory {
    
    /**
     * 本地服务实例缓存，key为服务名称，value为服务实例
     */
    private static final Map<String, Object> localServiceMap = new ConcurrentHashMap<>();
    
    /**
     * 注册本地服务
     *
     * @param serviceInterface 服务接口类
     * @param version 服务版本
     * @param group 服务分组
     * @param serviceImpl 服务实现实例
     * @param <T> 服务接口类型
     */
    public static <T> void registerLocalService(Class<T> serviceInterface, String version, String group, T serviceImpl) {
        String serviceKey = generateServiceKey(serviceInterface.getName(), version, group);
        localServiceMap.put(serviceKey, serviceImpl);
        log.info("注册本地服务：serviceKey={}, implClass={}", serviceKey, serviceImpl.getClass().getName());
    }
    
    /**
     * 注册本地服务（使用默认版本和分组）
     *
     * @param serviceInterface 服务接口类
     * @param serviceImpl 服务实现实例
     * @param <T> 服务接口类型
     */
    public static <T> void registerLocalService(Class<T> serviceInterface, T serviceImpl) {
        registerLocalService(serviceInterface, "1.0.0", "", serviceImpl);
    }
    
    /**
     * 获取本地服务实例
     *
     * @param serviceName 服务名称（接口全限定名）
     * @param version 服务版本
     * @param group 服务分组
     * @return 本地服务实例，如果不存在则返回null
     */
    public static Object getLocalService(String serviceName, String version, String group) {
        String serviceKey = generateServiceKey(serviceName, version, group);
        log.debug("查找本地服务: {}", serviceKey);
        
        Object service = localServiceMap.get(serviceKey);
        if (service != null) {
            log.debug("找到本地服务实现: {}, 实现类: {}", serviceKey, service.getClass().getName());
        } else {
            log.debug("未找到本地服务实现: {}", serviceKey);
        }
        
        return service;
    }
    
    /**
     * 移除本地服务
     *
     * @param serviceName 服务名称（接口全限定名）
     * @param version 服务版本
     * @param group 服务分组
     */
    public static void removeLocalService(String serviceName, String version, String group) {
        String serviceKey = generateServiceKey(serviceName, version, group);
        Object removed = localServiceMap.remove(serviceKey);
        if (removed != null) {
            log.info("移除本地服务：serviceKey={}", serviceKey);
        } else {
            log.debug("尝试移除不存在的本地服务: {}", serviceKey);
        }
    }
    
    /**
     * 生成服务键
     *
     * @param serviceName 服务名称
     * @param version 版本号
     * @param group 服务分组
     * @return 服务键
     */
    private static String generateServiceKey(String serviceName, String version, String group) {
        return serviceName + "_" + (version != null ? version : "1.0.0") + "_" + (group != null ? group : "");
    }
    
    /**
     * 获取已注册的本地服务数量
     * 
     * @return 已注册的本地服务数量
     */
    public static int getRegisteredServiceCount() {
        return localServiceMap.size();
    }
    
    /**
     * 清空所有注册的本地服务
     */
    public static void clearAllServices() {
        int count = localServiceMap.size();
        localServiceMap.clear();
        log.info("已清空所有本地服务, 共{}个", count);
    }
} 