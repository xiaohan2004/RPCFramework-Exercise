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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RPC客户端
 */
@Slf4j
public class RpcClient {
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
     * 构造函数
     */
    public RpcClient() {
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
        
        log.info("RPC客户端初始化完成, 注册中心地址: {}, 使用简化JSON编解码器: {}", registryAddr, useSimpleJson);
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
        
        // 获取或创建Netty客户端
        NettyClient client = getOrCreateClient(address);
        
        // 发送请求
        return client.sendRequest(request, timeout);
    }
    
    /**
     * 获取或创建Netty客户端
     *
     * @param address 服务地址
     * @return Netty客户端
     */
    private NettyClient getOrCreateClient(String address) {
        return clientMap.computeIfAbsent(address, addr -> {
            String host = NetUtil.getHostFromAddress(addr);
            int port = NetUtil.getPortFromAddress(addr);
            
            try {
                NettyClient client = new NettyClient(host, port, useSimpleJson);
                client.connect().get();
                return client;
            } catch (Exception e) {
                throw new RuntimeException("连接RPC服务器失败: " + addr, e);
            }
        });
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
} 