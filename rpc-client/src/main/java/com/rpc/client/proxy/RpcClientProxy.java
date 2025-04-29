package com.rpc.client.proxy;

import com.rpc.client.RpcClient;
import com.rpc.client.local.ConditionEvaluator;
import com.rpc.client.local.LocalServiceFactory;
import com.rpc.client.transport.RpcFuture;
import com.rpc.core.annotation.RpcReference;
import com.rpc.core.protocol.RpcRequest;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * RPC客户端代理，用于创建远程服务代理对象
 */
@Slf4j
public class RpcClientProxy implements InvocationHandler {
    /**
     * 代理配置映射表，用于存储代理对象与其配置的关系
     * 键为代理对象的系统标识，值为代理配置
     */
    private static final Map<Integer, ProxyConfig> PROXY_CONFIG_MAP = new ConcurrentHashMap<>();
    
    /**
     * 代理配置类，存储代理相关的配置信息
     */
    private static class ProxyConfig {
        private final String version;
        private final String group;
        private final long timeout;
        private final int retries;
        private final boolean async;
        private final boolean enableLocalService;
        private final String condition;
        private final String serviceInterface; // 添加接口名称，便于调试
        
        public ProxyConfig(String version, String group, long timeout, int retries, 
                           boolean async, boolean enableLocalService, String condition,
                           String serviceInterface) {
            this.version = version;
            this.group = group;
            this.timeout = timeout;
            this.retries = retries;
            this.async = async;
            this.enableLocalService = enableLocalService;
            this.condition = condition;
            this.serviceInterface = serviceInterface;
        }
        
        @Override
        public String toString() {
            return String.format(
                "ProxyConfig[service=%s, version=%s, group=%s, enableLocalService=%s, condition='%s']",
                serviceInterface, version, group, enableLocalService, condition);
        }
    }
    
    /**
     * RPC客户端
     */
    private final RpcClient rpcClient;
    
    /**
     * 代理对象的标识符
     */
    private String proxyId;
    
    /**
     * 服务版本
     */
    private String version;
    
    /**
     * 服务分组
     */
    private String group;
    
    /**
     * 超时时间（毫秒）
     */
    private long timeout = 20000;
    
    /**
     * 重试次数
     */
    private int retries = 2;
    
    /**
     * 是否异步调用
     */
    private boolean async = false;
    
    /**
     * 是否启用本地服务
     */
    private boolean enableLocalService = false;
    
    /**
     * 本地服务条件
     */
    private String condition = "";
    
    /**
     * RPC引用注解
     */
    private RpcReference reference;
    
    /**
     * 构造函数
     *
     * @param rpcClient RPC客户端
     */
    public RpcClientProxy(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }
    
    /**
     * 创建接口代理
     *
     * @param interfaceClass 接口类
     * @param <T> 接口类型
     * @return 代理对象
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> interfaceClass) {
        // 创建代理对象
        T proxy = (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                this);
        
        // 使用代理对象的系统标识作为键
        int proxyHashCode = System.identityHashCode(proxy);
        
        // 保存代理配置
        ProxyConfig config = new ProxyConfig(
                version, group, timeout, retries, async, enableLocalService, condition,
                interfaceClass.getName());
        PROXY_CONFIG_MAP.put(proxyHashCode, config);
        
        log.debug("保存代理配置，Key: {}, 接口: {}, 条件: '{}'", 
                proxyHashCode, interfaceClass.getName(), condition);
        
        return proxy;
    }
    
    /**
     * 创建接口代理
     *
     * @param interfaceClass 接口类
     * @param version 版本号
     * @param <T> 接口类型
     * @return 代理对象
     */
    public <T> T getProxy(Class<T> interfaceClass, String version, String group) {
        this.version = version;
        this.group = group;
        return getProxy(interfaceClass);
    }
    
    /**
     * 创建接口代理，带RpcReference注解配置
     *
     * @param interfaceClass 接口类
     * @param reference RPC引用注解
     * @param <T> 接口类型
     * @return 代理对象
     */
    public <T> T getProxy(Class<T> interfaceClass, RpcReference reference) {
        if (reference == null) {
            log.warn("传入的RpcReference注解为null，将使用默认参数");
            return getProxy(interfaceClass);
        }
        
        this.version = reference.version();
        this.group = reference.group();
        this.timeout = reference.timeout();
        this.retries = reference.retries();
        this.async = reference.async();
        
        // 直接保存条件相关的字段，而不仅仅依赖reference对象
        this.enableLocalService = reference.enableLocalService();
        this.condition = reference.condition();
        
        // 也保存reference对象以便完整性
        this.reference = reference;
        
        // 记录详细信息，确保所有参数都被正确保存
        log.info("创建RPC代理: 服务={}, 版本={}, 分组={}, 超时={}ms, 重试={}, 异步={}, 启用本地服务={}, 条件='{}'",
                interfaceClass.getName(),
                this.version,
                this.group,
                this.timeout,
                this.retries,
                this.async,
                this.enableLocalService,
                this.condition);
        
        return getProxy(interfaceClass);
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Object类的方法直接调用
        if (Object.class.equals(method.getDeclaringClass())) {
            return method.invoke(this, args);
        }
        
        // 使用代理对象本身的系统标识作为键
        int proxyHashCode = System.identityHashCode(proxy);
        
        // 从映射表中获取代理配置
        ProxyConfig config = PROXY_CONFIG_MAP.get(proxyHashCode);
        if (config == null) {
            log.warn("未找到代理配置，使用本地变量配置: proxyHashCode={}", proxyHashCode);
            // 显示映射表中所有的键，用于调试
            if (!PROXY_CONFIG_MAP.isEmpty()) {
                log.debug("当前映射表中的键: {}", PROXY_CONFIG_MAP.keySet());
            }
        } else {
            // 使用保存的配置更新本地变量
            this.version = config.version;
            this.group = config.group;
            this.timeout = config.timeout;
            this.retries = config.retries;
            this.async = config.async;
            this.enableLocalService = config.enableLocalService;
            this.condition = config.condition;
            
            log.debug("从映射表中恢复代理配置: key={}, config={}", 
                    proxyHashCode, config);
        }
        
        String serviceName = method.getDeclaringClass().getName();
        String methodName = method.getName();
        
        log.info("调用服务: {}.{}, 版本: {}, 分组: {}", serviceName, methodName, version, group);
        
        // 不再依赖reference对象，而是使用已保存的字段
        log.debug("RPC调用详情: 版本={}, 分组={}, 超时={}ms, 启用本地服务={}, 条件='{}'",
                version, group, timeout, enableLocalService, condition);
        
        // 标记是否已经尝试使用本地服务
        boolean useLocalService = false;
        
        // 检查是否应该使用本地服务
        if (enableLocalService) {
            log.debug("本地服务已启用(enableLocalService=true)，条件: '{}'", 
                    condition != null ? condition : "无");
            
            // 创建一个简单的RpcReference实现，用于评估条件
            RpcReference localReference = new RpcReference() {
                @Override
                public String version() { return version; }
                
                @Override
                public String group() { return group; }
                
                @Override
                public long timeout() { return timeout; }
                
                @Override
                public int retries() { return retries; }
                
                @Override
                public boolean async() { return async; }
                
                @Override
                public boolean enableLocalService() { return enableLocalService; }
                
                @Override
                public String condition() { return condition; }
                
                @Override
                public Class<? extends java.lang.annotation.Annotation> annotationType() {
                    return RpcReference.class;
                }
            };
            
            useLocalService = ConditionEvaluator.shouldUseLocalService(localReference);
            
            if (useLocalService) {
                // 获取本地服务实例
                Object localService = LocalServiceFactory.getLocalService(serviceName, version, group);
                
                if (localService != null) {
                    log.info("将使用本地服务调用: {}.{}, 条件: '{}'", 
                            serviceName, methodName, 
                            condition != null ? condition : "无");
                    
                    // 直接调用本地服务
                    Object result = method.invoke(localService, args);
                    log.debug("本地服务调用完成: {}.{}, 结果类型: {}", 
                            serviceName, methodName, 
                            result != null ? result.getClass().getName() : "null");
                    
                    return result;
                } else {
                    log.warn("未找到本地服务实现，将回退使用远程调用: {}.{}", serviceName, methodName);
                }
            } else {
                log.info("将使用远程服务调用: {}.{}, 条件: '{}'", 
                        serviceName, methodName, 
                        condition != null ? condition : "无");
            }
        } else {
            log.debug("本地服务未启用，将直接使用远程调用");
        }
        
        // 构建RPC请求
        RpcRequest request = createRequest(method, args);
        log.debug("创建RPC请求: {}", request);
        
        // 发送RPC请求
        RpcFuture future = null;
        Exception lastException = null;
        
        try {
            // 尝试发送请求，可能抛出服务发现失败异常
            for (int i = 0; i <= retries; i++) {
                try {
                    log.debug("发送RPC请求{}: {}.{}", i > 0 ? " (重试 " + i + ")" : "", serviceName, methodName);
                    future = rpcClient.sendRequest(request, timeout);
                    log.debug("RPC请求已发送，等待响应");
                    break;
                } catch (Exception e) {
                    lastException = e;
                    
                    // 检查是否是服务发现失败的异常
                    if (e instanceof RuntimeException) {
                        
                        log.warn("服务发现失败: {}", e.getMessage());
                        
                        // 如果允许使用本地服务
                        if (enableLocalService) {
                            log.info("尝试回退到本地服务: {}.{}", serviceName, methodName);
                            
                            // 尝试获取本地服务或回退服务
                            Object localService = LocalServiceFactory.getLocalServiceWithFallback(serviceName, version, group, true);
                            
                            if (localService != null) {
                                log.info("回退使用本地/回退服务: {}.{}", serviceName, methodName);
                                Object result = method.invoke(localService, args);
                                log.debug("本地/回退服务调用完成: {}.{}, 结果类型: {}", 
                                        serviceName, methodName, 
                                        result != null ? result.getClass().getName() : "null");
                                return result;
                            }
                        }
                        
                        // 如果没有本地服务或未启用，创建友好错误信息而不是抛出异常
                        log.error("无法找到服务实例，远程调用失败: {}.{}, 版本: {}, 分组: {}", 
                                serviceName, methodName, version, group);
                        
                        // 基于返回类型创建一个友好的错误响应
                        return createFriendlyErrorResponse(method.getReturnType(), 
                                "无法找到服务实例: " + serviceName);
                    }
                    
                    log.error("RPC调用失败，正在重试 {}/{}: {}", i + 1, retries, e.getMessage());
                    if (i == retries) {
                        log.error("RPC调用失败，已达到最大重试次数: {}", retries);
                        break;
                    }
                    
                    // 短暂等待后重试
                    try {
                        Thread.sleep(1000); // 等待1秒后重试
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("RPC调用被中断", ie);
                    }
                }
            }
            
            if (future == null) {
                if (lastException != null) {
                    // 创建友好的错误响应而不是抛出异常
                    return createFriendlyErrorResponse(method.getReturnType(), 
                            "RPC调用失败: " + lastException.getMessage());
                }
                return createFriendlyErrorResponse(method.getReturnType(), "RPC调用失败：未知错误");
            }
            
            // 如果是异步调用，直接返回Future
            if (async) {
                log.debug("异步调用模式，直接返回Future");
                return convertToReturnType(future, method.getReturnType());
            }
            
            // 同步调用，等待结果
            try {
                log.debug("同步调用模式，等待结果，超时时间: {}ms", timeout);
                Object result = future.getData(timeout, TimeUnit.MILLISECONDS);
                log.info("RPC调用成功完成: {}.{}, 结果类型: {}", 
                        serviceName, methodName, 
                        result != null ? result.getClass().getName() : "null");
                return result;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("RPC调用被中断: {}.{}", serviceName, methodName);
                return createFriendlyErrorResponse(method.getReturnType(), "RPC调用被中断");
            } catch (ExecutionException e) {
                log.error("RPC调用执行异常: {}.{}, 错误: {}", serviceName, methodName, e.getCause().getMessage());

                if (enableLocalService) {
                    log.info("检测到服务发现失败异常，尝试回退到本地服务: {}.{}", serviceName, methodName);
                    
                    // 尝试获取本地服务或回退服务，忽略条件评估，直接使用本地服务
                    Object localService = LocalServiceFactory.getLocalServiceWithFallback(serviceName, version, group, true);
                    
                    if (localService != null) {
                        log.info("执行异常后回退到本地/回退服务: {}.{}", serviceName, methodName);
                        try {
                            Object result = method.invoke(localService, args);
                            log.debug("本地/回退服务调用完成: {}.{}, 结果类型: {}", 
                                    serviceName, methodName, 
                                    result != null ? result.getClass().getName() : "null");
                            return result;
                        } catch (Exception localEx) {
                            log.error("本地/回退服务调用失败: {}", localEx.getMessage());
                            // 如果本地服务调用也失败，继续返回原来的错误响应
                        }
                    }
                }
                
                return createFriendlyErrorResponse(method.getReturnType(), "RPC调用执行异常: " + e.getCause().getMessage());
            } catch (TimeoutException e) {
                log.error("RPC调用超时: {}.{}, 超时时间: {}ms", serviceName, methodName, timeout);
                return createFriendlyErrorResponse(method.getReturnType(), "RPC调用超时，超时时间: " + timeout + "ms");
            }
        } catch (Exception unexpected) {
            // 完全意外的异常，记录日志
            log.error("RPC调用过程中发生意外异常: {}", unexpected.getMessage(), unexpected);
            return createFriendlyErrorResponse(method.getReturnType(), "RPC调用出现意外错误: " + unexpected.getMessage());
        }
    }
    
    /**
     * 创建RPC请求
     *
     * @param method 方法
     * @param args 参数
     * @return RPC请求
     */
    private RpcRequest createRequest(Method method, Object[] args) {
        RpcRequest request = new RpcRequest();
        request.setServiceName(method.getDeclaringClass().getName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setParameters(args);
        request.setVersion(version);
        request.setGroup(group);
        return request;
    }
    
    /**
     * 将RpcFuture转换为方法返回类型
     *
     * @param future RPC Future
     * @param returnType 返回类型
     * @return 转换后的对象
     */
    private Object convertToReturnType(RpcFuture future, Class<?> returnType) {
        if (returnType == CompletableFuture.class) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return future.getData(timeout, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        
        // 其他异步返回类型可以在这里添加
        
        // 默认返回RpcFuture
        return future;
    }
    
    /**
     * 创建友好的错误响应对象，根据返回类型返回适当的默认值或错误信息
     * 
     * @param returnType 方法返回类型
     * @param errorMessage 错误信息
     * @return 适合返回类型的响应对象
     */
    private Object createFriendlyErrorResponse(Class<?> returnType, String errorMessage) {
        log.debug("为返回类型 {} 创建友好错误响应: {}", returnType.getName(), errorMessage);
        
        // 如果是void，直接返回null
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        
        // 如果是基本类型，返回对应的默认值
        if (returnType.isPrimitive()) {
            if (returnType == boolean.class) {
                return false;
            } else if (returnType == char.class) {
                return '\0';
            } else if (returnType == byte.class || returnType == short.class || 
                    returnType == int.class || returnType == long.class || 
                    returnType == float.class || returnType == double.class) {
                return 0;
            }
        }
        
        // 如果是Future类型，返回带有异常的CompletableFuture
        if (returnType == CompletableFuture.class) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException(errorMessage));
            return future;
        }
        
        // 如果是RpcFuture类型，返回带有异常的RpcFuture
        if (returnType == RpcFuture.class) {
            RpcFuture future = new RpcFuture(0, 0);
            future.completeExceptionally(new RuntimeException(errorMessage));
            return future;
        }
        
        // 对于String类型，返回错误消息
        if (returnType == String.class) {
            return "错误: " + errorMessage;
        }
        
        // 对于Optional类型，返回empty
        if (returnType == java.util.Optional.class) {
            return java.util.Optional.empty();
        }
        
        // 对于集合类型，返回空集合
        if (java.util.Collection.class.isAssignableFrom(returnType)) {
            if (returnType == java.util.List.class || returnType == java.util.ArrayList.class) {
                return new java.util.ArrayList<>();
            } else if (returnType == java.util.Set.class || returnType == java.util.HashSet.class) {
                return new java.util.HashSet<>();
            } else if (returnType == java.util.Map.class || returnType == java.util.HashMap.class) {
                return new java.util.HashMap<>();
            }
        }
        
        // 其他对象类型，返回null
        return null;
    }
} 