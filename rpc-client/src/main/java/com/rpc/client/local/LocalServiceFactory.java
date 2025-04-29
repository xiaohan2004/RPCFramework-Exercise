package com.rpc.client.local;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
     * 回退服务实现缓存，key为接口类名，value为回退服务实例
     */
    private static final Map<String, Object> fallbackServiceMap = new ConcurrentHashMap<>();
    
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
     * 注册回退服务实现
     * 当找不到特定的本地服务实现时，将使用回退服务作为最后手段
     *
     * @param serviceInterface 服务接口类
     * @param fallbackImpl 回退服务实现
     * @param <T> 服务接口类型
     */
    public static <T> void registerFallbackService(Class<T> serviceInterface, T fallbackImpl) {
        String interfaceName = serviceInterface.getName();
        fallbackServiceMap.put(interfaceName, fallbackImpl);
        log.info("注册回退服务：接口={}, 实现类={}", interfaceName, fallbackImpl.getClass().getName());
    }
    
    /**
     * 创建一个默认的回退服务实现，返回友好的错误消息
     *
     * @param serviceInterface 服务接口类
     * @param <T> 服务接口类型
     * @return 回退服务实现
     */
    @SuppressWarnings("unchecked")
    public static <T> T createDefaultFallbackService(Class<T> serviceInterface) {
        log.info("为接口 {} 创建默认回退服务", serviceInterface.getName());
        
        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class<?>[]{serviceInterface},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        log.warn("回退服务被调用: {}.{}", serviceInterface.getName(), method.getName());
                        
                        // 针对不同返回类型提供适当的默认值
                        Class<?> returnType = method.getReturnType();
                        
                        if (returnType == void.class || returnType == Void.class) {
                            return null;
                        } else if (returnType.isPrimitive()) {
                            if (returnType == boolean.class) {
                                return false;
                            } else if (returnType == char.class) {
                                return '\0';
                            } else {
                                return 0;
                            }
                        } else if (returnType == String.class) {
                            return "[回退服务] 服务不可用: " + serviceInterface.getName() + "." + method.getName();
                        } else if (returnType.isAssignableFrom(java.util.Collection.class)) {
                            // 返回空集合
                            if (returnType == java.util.List.class) {
                                return new java.util.ArrayList<>();
                            } else if (returnType == java.util.Set.class) {
                                return new java.util.HashSet<>();
                            } else {
                                return null;
                            }
                        } else {
                            // 其他对象类型返回null
                            return null;
                        }
                    }
                });
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
            return service;
        } 
        
        // 查找回退服务
        service = fallbackServiceMap.get(serviceName);
        if (service != null) {
            log.info("使用回退服务: {}, 实现类: {}", serviceName, service.getClass().getName());
            return service;
        }
        
        log.debug("未找到本地服务实现或回退服务: {}", serviceKey);
        return null;
    }
    
    /**
     * 获取本地服务实例，如果不存在则创建默认回退服务
     *
     * @param serviceName 服务名称（接口全限定名）
     * @param version 服务版本
     * @param group 服务分组
     * @param createFallback 是否创建默认回退服务（如果未找到服务）
     * @return 本地服务实例或回退服务实例，如果不存在且不创建回退服务则返回null
     */
    public static Object getLocalServiceWithFallback(String serviceName, String version, String group, boolean createFallback) {
        Object service = getLocalService(serviceName, version, group);
        
        if (service == null && createFallback) {
            try {
                // 动态加载接口类
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                Class<?> interfaceClass = classLoader.loadClass(serviceName);
                
                // 创建默认回退服务
                log.info("为服务 {} 创建默认回退服务", serviceName);
                service = createDefaultFallbackService(interfaceClass);
                
                // 注册到回退服务映射
                fallbackServiceMap.put(serviceName, service);
            } catch (ClassNotFoundException e) {
                log.error("无法加载服务接口类: {}", serviceName, e);
                return null;
            }
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
     * 移除回退服务
     *
     * @param serviceName 服务名称（接口全限定名）
     */
    public static void removeFallbackService(String serviceName) {
        Object removed = fallbackServiceMap.remove(serviceName);
        if (removed != null) {
            log.info("移除回退服务：接口={}", serviceName);
        } else {
            log.debug("尝试移除不存在的回退服务: {}", serviceName);
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
     * 获取已注册的回退服务数量
     * 
     * @return 已注册的回退服务数量
     */
    public static int getRegisteredFallbackCount() {
        return fallbackServiceMap.size();
    }
    
    /**
     * 清空所有注册的本地服务
     */
    public static void clearAllServices() {
        int count = localServiceMap.size();
        localServiceMap.clear();
        int fallbackCount = fallbackServiceMap.size();
        fallbackServiceMap.clear();
        log.info("已清空所有本地服务和回退服务, 本地服务{}个, 回退服务{}个", count, fallbackCount);
    }
} 