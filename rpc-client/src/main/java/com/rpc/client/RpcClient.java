package com.rpc.client;

import com.rpc.client.proxy.RpcClientProxy;
import com.rpc.client.transport.NettyClient;
import com.rpc.client.transport.RpcFuture;
import com.rpc.common.model.ServiceInfo;
import com.rpc.common.utils.NetUtil;
import com.rpc.core.config.RpcConfig;
import com.rpc.core.protocol.RpcRequest;
import com.rpc.registry.ServiceRegistry;
import com.rpc.registry.ServiceRegistryFactory;
import lombok.extern.slf4j.Slf4j;
import com.rpc.client.processor.RpcReferenceProcessor;
import com.rpc.core.annotation.RpcReference;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * RPC客户端
 */
@Slf4j
public class RpcClient {
    /**
     * 单例实例
     */
    private static volatile RpcClient INSTANCE;
    
    /**
     * 注册中心
     */
    private final ServiceRegistry serviceRegistry;
    
    /**
     * 服务器连接Map，key为地址，value为Netty客户端
     */
    private final Map<String, NettyClient> clientMap = new ConcurrentHashMap<>();
    
    /**
     * 默认超时时间
     */
    private final long timeout;
    
    /**
     * 是否使用简化的JSON编解码器
     */
    private final boolean useSimpleJson;
    
    /**
     * RPC引用处理器
     */
    private final RpcReferenceProcessor referenceProcessor;
    
    /**
     * 私有构造函数
     */
    private RpcClient() {
        // 读取配置
        String registryAddr = RpcConfig.getRegistryAddress();
        if (registryAddr == null || registryAddr.isEmpty()) {
            throw new IllegalArgumentException("注册中心地址不能为空，请在配置文件中设置rpc.registry.address");
        }
        
        this.timeout = RpcConfig.getLong("rpc.client.timeout", 5000);
        
        // 创建注册中心客户端，消费者端不发送心跳
        this.serviceRegistry = ServiceRegistryFactory.getServiceRegistry(registryAddr, false);
        
        // 读取是否使用简化JSON编解码器的配置
        this.useSimpleJson = RpcConfig.getBoolean("rpc.client.use.simple.json", true);
        
        // 创建RPC引用处理器
        this.referenceProcessor = new RpcReferenceProcessor(this);
        
        log.info("RPC客户端初始化完成, 注册中心地址: {}, 使用简化JSON编解码器: {}", registryAddr, useSimpleJson);
        
        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }
    
    /**
     * 获取RpcClient单例实例
     * 
     * @return RpcClient实例
     */
    public static RpcClient getInstance() {
        if (INSTANCE == null) {
            synchronized (RpcClient.class) {
                if (INSTANCE == null) {
                    INSTANCE = new RpcClient();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * 自动注入字段
     * 静态方法，自动使用单例客户端
     * 
     * @param bean 目标对象
     */
    public static void inject(Object bean) {
        try {
            getInstance().autoWireRpcReferences(bean);
        } catch (IllegalAccessException e) {
            log.error("注入RPC引用失败: {}", e.getMessage(), e);
            throw new RuntimeException("注入RPC引用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建服务代理
     *
     * @param interfaceClass 接口类
     * @param <T> 接口类型
     * @return 服务代理
     */
    public <T> T getRemoteService(Class<T> interfaceClass) {
        return this.getRemoteService(interfaceClass, "1.0.0", "");
    }
    
    /**
     * 创建服务代理
     *
     * @param interfaceClass 接口类
     * @param version 版本号
     * @param <T> 接口类型
     * @return 服务代理
     */
    public <T> T getRemoteService(Class<T> interfaceClass, String version) {
        return this.getRemoteService(interfaceClass, version, "");
    }
    
    /**
     * 创建服务代理
     *
     * @param interfaceClass 接口类
     * @param version 版本号
     * @param group 服务分组
     * @param <T> 接口类型
     * @return 服务代理
     */
    public <T> T getRemoteService(Class<T> interfaceClass, String version, String group) {
        return new RpcClientProxy(this).getProxy(interfaceClass, version, group);
    }
    
    /**
     * 发送RPC请求
     *
     * @param request RPC请求
     * @return RPC异步结果
     */
    public RpcFuture sendRequest(RpcRequest request) {
        return sendRequest(request, timeout);
    }
    
    /**
     * 发送RPC请求
     *
     * @param request RPC请求
     * @param timeout 超时时间
     * @return RPC异步结果
     */
    public RpcFuture sendRequest(RpcRequest request, long timeout) {
        // 从注册中心获取服务实例
        List<ServiceInfo> serviceInfos = serviceRegistry.discover(
                request.getServiceName(), request.getVersion(), request.getGroup());
        
        if (serviceInfos == null || serviceInfos.isEmpty()) {
            throw new RuntimeException("未找到服务提供者: " + request.getRpcServiceKey());
        }
        
        // 简单的随机负载均衡
        ServiceInfo serviceInfo = serviceInfos.get(ThreadLocalRandom.current().nextInt(serviceInfos.size()));
        String address = serviceInfo.getAddress();
        
        // 尝试发送请求，包含重试逻辑
        Exception lastException = null;
        for (int retryCount = 0; retryCount <= 1; retryCount++) { // 最多重试一次
            try {
                // 获取或创建Netty客户端（内部会处理连接状态）
                NettyClient client = getOrCreateClient(address);
                
                // 发送请求
                log.debug("发送RPC请求到 {}, 重试次数: {}", address, retryCount);
                return client.sendRequest(request, timeout);
            } catch (Exception e) {
                lastException = e;
                log.warn("发送RPC请求失败: {}, 重试次数: {}/{}, 错误: {}", 
                        address, retryCount, 1, e.getMessage());
                
                // 如果是连接问题，移除客户端实例，下次循环会重新创建
                if (e instanceof RuntimeException && 
                        e.getMessage() != null && e.getMessage().contains("连接RPC服务器失败")) {
                    log.info("检测到服务连接问题，移除客户端实例: {}", address);
                    clientMap.remove(address);
                }
                
                // 如果还可以重试，等待一小段时间后继续
                if (retryCount < 1) {
                    try {
                        Thread.sleep(1000); // 等待1秒后重试
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("发送请求被中断", ie);
                    }
                }
            }
        }
        
        // 如果所有重试都失败了，抛出最后的异常
        throw new RuntimeException("发送RPC请求失败，已重试1次", lastException);
    }
    
    /**
     * 获取或创建Netty客户端
     *
     * @param address 服务地址
     * @return Netty客户端
     */
    private NettyClient getOrCreateClient(String address) {
        // 先从缓存中获取
        NettyClient client = clientMap.get(address);
        
        // 如果客户端存在但不活跃，尝试重连
        if (client != null && !client.isActive()) {
            log.warn("检测到客户端连接不活跃: {}, 尝试重新连接", address);
            try {
                // 重新连接
                client.connect().get(5, TimeUnit.SECONDS);
                log.info("客户端重连成功: {}", address);
                return client;
            } catch (Exception e) {
                log.error("客户端重连失败: {}, 错误: {}", address, e.getMessage());
                
                // 连接失败，关闭旧连接并从映射中移除
                client.close();
                clientMap.remove(address);
                
                // 创建新的客户端对象
                log.info("创建新的客户端连接: {}", address);
                client = null;
            }
        }
        
        // 如果客户端不存在或已被移除，创建新的
        if (client == null) {
            String host = NetUtil.getHostFromAddress(address);
            int port = NetUtil.getPortFromAddress(address);
            
            try {
                client = new NettyClient(host, port, useSimpleJson);
                client.connect().get(5, TimeUnit.SECONDS);
                
                // 连接成功后放入映射表
                clientMap.put(address, client);
                log.info("成功创建并连接到服务: {}", address);
            } catch (Exception e) {
                // 创建或连接失败
                if (client != null) {
                    client.close();
                }
                throw new RuntimeException("连接RPC服务器失败: " + address, e);
            }
        }
        
        return client;
    }
    
    /**
     * 关闭客户端
     */
    public void close() {
        // 关闭所有Netty客户端
        for (NettyClient client : clientMap.values()) {
            client.close();
        }
        clientMap.clear();
        
        // 销毁注册中心客户端
        serviceRegistry.destroy();
        
        log.info("RPC客户端已关闭");
    }
    
    /**
     * 自动注入带有@RpcReference注解的服务引用
     * 
     * @param bean 目标对象
     * @throws IllegalAccessException 反射访问异常
     */
    public void autoWireRpcReferences(Object bean) throws IllegalAccessException {
        referenceProcessor.processBean(bean);
    }
} 