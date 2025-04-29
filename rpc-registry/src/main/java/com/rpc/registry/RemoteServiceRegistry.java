package com.rpc.registry;

import com.rpc.common.model.ServiceInfo;
import com.rpc.core.protocol.*;
import com.rpc.core.transport.SimpleJsonDecoder;
import com.rpc.core.transport.SimpleJsonEncoder;
import com.rpc.registry.ServiceRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 远程注册中心客户端
 */
@Slf4j
public class RemoteServiceRegistry implements ServiceRegistry {
    /**
     * 注册中心地址
     */
    private final String address;
    
    /**
     * 注册中心主机和端口
     */
    private final String host;
    private final int port;
    
    /**
     * Netty相关成员
     */
    private final EventLoopGroup eventLoopGroup;
    private final Bootstrap bootstrap;
    private Channel channel;
    
    /**
     * 请求ID生成器
     */
    private final AtomicLong requestIdGenerator = new AtomicLong(0);
    
    /**
     * 请求响应映射，key为请求ID，value为请求Future
     */
    private final ConcurrentHashMap<Long, CompletableFuture<Object>> pendingRequests = new ConcurrentHashMap<>();
    
    /**
     * 心跳线程
     */
    private Thread heartbeatThread;
    private volatile boolean running = false;
    
    /**
     * 默认超时时间（秒）
     */
    private static final int DEFAULT_TIMEOUT = 1000;
    
    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY_TIMES = 3;
    
    /**
     * 心跳频率（毫秒）
     */
    private static final long HEARTBEAT_INTERVAL = 5000; // 每5秒发送一次心跳
    
    /**
     * 连接重试次数
     */
    private static final int CONNECT_RETRY_TIMES = 10;
    
    /**
     * 连接重试间隔（毫秒）
     */
    private static final long CONNECT_RETRY_INTERVAL = 3000; // 3秒
    
    /**
     * 最后一次心跳发送时间
     */
    private final AtomicLong lastHeartbeatTime = new AtomicLong(0);
    
    /**
     * 心跳失败次数
     */
    private final AtomicLong heartbeatFailCount = new AtomicLong(0);
    
    /**
     * 已注册的服务列表
     */
    private final List<ServiceInfo> registeredServices = Collections.synchronizedList(new ArrayList<>());
    
    /**
     * 是否启用心跳
     */
    private final boolean enableHeartbeat;
    
    /**
     * 构造方法
     *
     * @param registryAddress 注册中心地址，格式为host:port，如果没有指定端口，则使用默认端口8000
     */
    public RemoteServiceRegistry(String registryAddress) {
        this(registryAddress, true);
    }
    
    /**
     * 构造方法
     *
     * @param registryAddress 注册中心地址，格式为host:port，如果没有指定端口，则使用默认端口8000
     * @param enableHeartbeat 是否启用心跳线程
     */
    public RemoteServiceRegistry(String registryAddress, boolean enableHeartbeat) {
        this.address = registryAddress;
        this.enableHeartbeat = enableHeartbeat;
        
        // 解析地址
        this.host = getHostFromAddress(registryAddress);
        this.port = getPortFromAddress(registryAddress);
        
        log.info("初始化注册中心客户端 - 地址: {}:{}, 心跳: {}", host, port, enableHeartbeat ? "启用" : "禁用");
        
        // 初始化Netty客户端
        this.eventLoopGroup = new NioEventLoopGroup(1);
        this.bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        log.debug("初始化注册中心客户端连接通道");
                        ch.pipeline()
                                // 添加日志处理器，记录所有进出的消息
                                .addLast(new LoggingHandler(LogLevel.DEBUG));
                                
                        // 只有在启用心跳的情况下才添加IdleStateHandler
                        if (enableHeartbeat) {
                            // 空闲检测处理器，10秒没有写操作就发送心跳
                            ch.pipeline().addLast(new IdleStateHandler(0, 10, 0, TimeUnit.SECONDS));
                        }
                        
                        // 添加编解码器和处理器
                        ch.pipeline()
                                // 使用简化的JSON编解码器
                                .addLast(new SimpleJsonEncoder())
                                .addLast(new SimpleJsonDecoder())
                                // 响应处理器
                                .addLast(new RegistryClientHandler(RemoteServiceRegistry.this));
                        log.debug("注册中心客户端连接通道初始化完成");
                    }
                });
        
        // 连接注册中心
        connect(host, port);
        
        // 根据配置决定是否启动心跳线程
        if (enableHeartbeat) {
            startHeartbeat();
        } else {
            log.info("心跳线程已禁用");
        }
        
        log.info("远程注册中心客户端初始化完成: {}:{}", host, port);
    }
    
    /**
     * 连接注册中心
     */
    private void connect(String host, int port) {
        int retryCount = 0;
        boolean connected = false;
        Exception lastException = null;
        
        while (retryCount < CONNECT_RETRY_TIMES && !connected) {
            try {
                if (retryCount > 0) {
                    log.info("第{}次尝试连接注册中心: {}:{}", retryCount + 1, host, port);
                    Thread.sleep(CONNECT_RETRY_INTERVAL);
                } else {
                    log.info("开始连接注册中心服务器: {}:{}", host, port);
                }
                
                ChannelFuture future = bootstrap.connect(host, port).sync();
                this.channel = future.channel();
                connected = true;
                
                log.info("成功连接到注册中心服务器: {}:{}", host, port);
                
                // 连接成功后重置心跳失败计数
                heartbeatFailCount.set(0);
                
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                log.warn("连接注册中心失败 (尝试 {}/{}): {}:{}, 原因: {}", 
                        retryCount, CONNECT_RETRY_TIMES, host, port, e.getMessage());
            }
        }
        
        if (!connected) {
            log.error("连接注册中心服务器失败: {}:{}, 已重试{}次", host, port, retryCount, lastException);
            throw new RuntimeException("无法连接到注册中心: " + host + ":" + port, lastException);
        }
    }
    
    /**
     * 启动心跳线程
     */
    private void startHeartbeat() {
        this.running = true;
        this.heartbeatThread = new Thread(() -> {
            while (running) {
                try {
                    // 检查连接状态，如果断开则尝试重连
                    if (channel == null || !channel.isActive()) {
                        log.warn("检测到与注册中心的连接已断开，尝试重新连接...");
                        try {
                            connect(host, port);
                            // 重连成功后，重新注册所有服务
                            reregisterServices();
                        } catch (Exception e) {
                            log.error("重新连接注册中心失败: {}", e.getMessage());
                            // 重连失败，等待下次尝试
                            Thread.sleep(CONNECT_RETRY_INTERVAL);
                            continue;
                        }
                    }
                    
                    // 发送心跳
                    sendHeartbeat();
                    
                    // 心跳间隔时间
                    Thread.sleep(HEARTBEAT_INTERVAL);
                    
                } catch (InterruptedException e) {
                    log.warn("心跳线程被中断");
                    break;
                } catch (Exception e) {
                    log.error("发送心跳异常: {}", e.getMessage(), e);
                    // 出现异常时适当延迟，避免频繁重试
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "RegistryHeartbeatThread");
        
        // 设置为守护线程
        heartbeatThread.setDaemon(true);
        // 设置较高优先级
        heartbeatThread.setPriority(Thread.MAX_PRIORITY - 1);
        heartbeatThread.start();
        log.info("注册中心心跳线程已启动，心跳间隔: {}秒", HEARTBEAT_INTERVAL/1000);
    }
    
    /**
     * 重新注册所有服务
     */
    private void reregisterServices() {
        if (registeredServices.isEmpty()) {
            log.info("本地无缓存的服务信息，无需重新注册");
            return;
        }
        
        log.info("开始重新注册服务，共{}个服务", registeredServices.size());
        
        List<ServiceInfo> services = new ArrayList<>(registeredServices);
        for (ServiceInfo serviceInfo : services) {
            try {
                log.info("重新注册服务: {}", serviceInfo);
                // 调用注册方法，但不要重复添加到registeredServices
                doRegister(serviceInfo);
            } catch (Exception e) {
                log.error("重新注册服务失败: {}, 错误: {}", serviceInfo, e.getMessage(), e);
            }
        }
        
        log.info("服务重新注册完成，成功: {}/{}", services.size(), registeredServices.size());
    }
    
    /**
     * 实际执行注册的内部方法
     */
    private void doRegister(ServiceInfo serviceInfo) {
        log.info("执行注册操作: {}", serviceInfo);
        
        // 添加重试逻辑
        int retryTimes = 0;
        while (retryTimes <= MAX_RETRY_TIMES) {
            try {
                if (retryTimes > 0) {
                    log.info("第{}次重试注册服务: {}", retryTimes, serviceInfo);
                }
                
                // 检查连接状态
                if (!ensureConnection()) {
                    retryTimes++;
                    if (retryTimes <= MAX_RETRY_TIMES) {
                        log.info("连接失败，将在1秒后重试");
                        Thread.sleep(1000); // 等待1秒后重试
                        continue;
                    } else {
                        throw new RuntimeException("无法连接到注册中心");
                    }
                }
                
                RpcMessage message = new RpcMessage();
                message.setMessageType(RpcProtocol.REGISTRY_REGISTER_TYPE);
                message.setSerializationType(RpcProtocol.SERIALIZATION_JSON);
                message.setRequestId(requestIdGenerator.incrementAndGet());
                message.setData(serviceInfo);
                
                log.debug("发送注册服务请求: {}", message);
                
                CompletableFuture<Object> future = new CompletableFuture<>();
                pendingRequests.put(message.getRequestId(), future);
                
                channel.writeAndFlush(message).addListener((ChannelFutureListener) f -> {
                    if (!f.isSuccess()) {
                        pendingRequests.remove(message.getRequestId());
                        future.completeExceptionally(f.cause());
                        log.error("发送注册服务请求失败: {}", f.cause().getMessage());
                    } else {
                        log.debug("注册服务请求发送成功");
                    }
                });
                
                // 增加超时时间到100秒
                try {
                    Object result = future.get(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
                    log.info("服务注册成功: {}, 响应: {}", serviceInfo, result);
                    break; // 注册成功，退出重试循环
                } catch (TimeoutException e) {
                    pendingRequests.remove(message.getRequestId());
                    log.warn("注册服务请求超时，可能原因：注册中心未响应或网络延迟较大");
                    
                    // 如果超时，尝试重试
                    retryTimes++;
                    if (retryTimes <= MAX_RETRY_TIMES) {
                        log.info("将在1秒后重试注册");
                        Thread.sleep(1000);
                    } else {
                        throw e; // 重试次数用完，抛出异常
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("注册服务被中断", e);
            } catch (Exception e) {
                retryTimes++;
                if (retryTimes <= MAX_RETRY_TIMES) {
                    try {
                        log.warn("注册服务异常: {}，将在1秒后重试", e.getMessage());
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("注册服务被中断", ie);
                    }
                } else {
                    log.error("注册服务失败，重试{}次仍未成功: {}", MAX_RETRY_TIMES, serviceInfo, e);
                    throw new RuntimeException("注册服务失败", e);
                }
            }
        }
    }
    
    @Override
    public void register(ServiceInfo serviceInfo) {
        log.info("向注册中心 {}:{} 注册服务: {}", host, port, serviceInfo);
        
        // 添加到本地已注册服务列表中，用于重连时重新注册
        if (!registeredServices.contains(serviceInfo)) {
            registeredServices.add(serviceInfo);
            log.debug("服务已添加到本地缓存: {}", serviceInfo);
        }
        
        // 确保连接可用
        ensureConnection();
        
        // 执行注册操作
        doRegister(serviceInfo);
    }
    
    @Override
    public void unregister(ServiceInfo serviceInfo) {
        log.info("向注册中心注销服务: {}", serviceInfo);
        
        // 从本地已注册服务列表中移除
        registeredServices.remove(serviceInfo);
        log.debug("服务已从本地缓存移除: {}", serviceInfo);
        
        // 确保连接可用
        if (!ensureConnection()) {
            log.error("无法连接到注册中心，服务注销请求无法发送");
            return;
        }
        
        try {
            RpcMessage message = new RpcMessage();
            message.setMessageType(RpcProtocol.REGISTRY_UNREGISTER_TYPE);
            message.setSerializationType(RpcProtocol.SERIALIZATION_JSON);
            message.setRequestId(requestIdGenerator.incrementAndGet());
            message.setData(serviceInfo);
            
            log.debug("发送注销服务请求: {}", message);
            
            CompletableFuture<Object> future = new CompletableFuture<>();
            pendingRequests.put(message.getRequestId(), future);
            
            channel.writeAndFlush(message).addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    pendingRequests.remove(message.getRequestId());
                    future.completeExceptionally(f.cause());
                    log.error("发送注销服务请求失败: {}", f.cause().getMessage());
                } else {
                    log.debug("注销服务请求发送成功");
                }
            });
            
            // 等待响应，超时时间100秒
            Object result = future.get(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
            log.info("服务注销成功: {}, 响应: {}", serviceInfo, result);
        } catch (Exception e) {
            log.error("注销服务失败: {}, 原因: {}", serviceInfo, e.getMessage(), e);
            
            // 如果是连接问题，尝试重连并重试
            if (e.getMessage() != null && (e.getMessage().contains("closed") 
                    || e.getMessage().contains("Connection reset") 
                    || e.getMessage().contains("远程主机强迫关闭了一个现有的连接"))) {
                log.warn("检测到连接已关闭，尝试重连并重新注销");
                
                // 确保连接已重建
                if (ensureConnection()) {
                    // 重试注销，但直接调用方法，不再递归调用自身
                    try {
                        doUnregister(serviceInfo);
                    } catch (Exception retryE) {
                        log.error("重试注销服务失败: {}", retryE.getMessage());
                        throw new RuntimeException("注销服务失败", retryE);
                    }
                    return;
                }
            }
            
            throw new RuntimeException("注销服务失败", e);
        }
    }
    
    /**
     * 实际执行注销的内部方法
     */
    private void doUnregister(ServiceInfo serviceInfo) throws Exception {
        RpcMessage message = new RpcMessage();
        message.setMessageType(RpcProtocol.REGISTRY_UNREGISTER_TYPE);
        message.setSerializationType(RpcProtocol.SERIALIZATION_JSON);
        message.setRequestId(requestIdGenerator.incrementAndGet());
        message.setData(serviceInfo);
        
        log.debug("发送注销服务请求: {}", message);
        
        CompletableFuture<Object> future = new CompletableFuture<>();
        pendingRequests.put(message.getRequestId(), future);
        
        channel.writeAndFlush(message).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                pendingRequests.remove(message.getRequestId());
                future.completeExceptionally(f.cause());
                log.error("发送注销服务请求失败: {}", f.cause().getMessage());
            } else {
                log.debug("注销服务请求发送成功");
            }
        });
        
        // 等待响应，超时时间100秒
        Object result = future.get(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
        log.info("服务注销成功: {}, 响应: {}", serviceInfo, result);
    }
    
    /**
     * 确保连接状态，如果连接断开则重新连接
     * 
     * @return 是否连接可用
     */
    private boolean ensureConnection() {
        if (channel == null || !channel.isActive()) {
            log.warn("检测到与注册中心的连接已断开，尝试重新连接...");
            try {
                connect(host, port);
                // 如果有已注册的服务，重新注册
                if (!registeredServices.isEmpty() && enableHeartbeat) {
                    reregisterServices();
                }
                return true;
            } catch (Exception e) {
                log.error("重新连接注册中心失败: {}", e.getMessage());
                return false;
            }
        }
        return true;
    }

    @Override
    public List<ServiceInfo> discover(String serviceName, String version, String group) {
        log.info("向注册中心查询服务: {}_{}_{}", serviceName, version, group);
        
        try {
            // 确保连接可用
            if (!ensureConnection()) {
                log.error("无法连接到注册中心，服务查询失败");
                return new ArrayList<>();
            }
            
            RegistryLookupRequest lookupRequest = new RegistryLookupRequest(serviceName, version, group);
            
            RpcMessage message = new RpcMessage();
            message.setMessageType(RpcProtocol.REGISTRY_LOOKUP_TYPE);
            message.setSerializationType(RpcProtocol.SERIALIZATION_JSON);
            message.setRequestId(requestIdGenerator.incrementAndGet());
            message.setData(lookupRequest);
            
            log.debug("发送查询服务请求: {}", message);
            
            CompletableFuture<Object> future = new CompletableFuture<>();
            pendingRequests.put(message.getRequestId(), future);
            
            channel.writeAndFlush(message).addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    pendingRequests.remove(message.getRequestId());
                    future.completeExceptionally(f.cause());
                    log.error("发送查询服务请求失败: {}", f.cause().getMessage());
                } else {
                    log.debug("查询服务请求发送成功");
                }
            });
            
            // 等待响应，超时时间100秒
            Object result = future.get(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
            if (result instanceof RegistryLookupResponse) {
                RegistryLookupResponse response = (RegistryLookupResponse) result;
                List<ServiceInfo> services = response.getServices();
                log.info("服务查询成功，找到{}个服务实例", services.size());
                return services;
            } else {
                log.error("服务查询响应类型错误: {}", result);
                return new ArrayList<>();
            }
        } catch (TimeoutException e) {
            log.error("服务查询超时: {}_{}_{}", serviceName, version, group);
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("服务查询失败: {}_{}_{}，原因: {}", serviceName, version, group, e.getMessage(), e);
            
            // 如果是因为连接问题，尝试一次重连
            if (e.getMessage() != null && (e.getMessage().contains("closed") 
                    || e.getMessage().contains("Connection reset") 
                    || e.getMessage().contains("远程主机强迫关闭了一个现有的连接"))) {
                log.warn("检测到连接已关闭，尝试重连并重新查询");
                
                // 确保连接已重建
                if (ensureConnection()) {
                    // 递归调用自身，但最多重试一次，避免无限递归
                    try {
                        return discover(serviceName, version, group);
                    } catch (Exception retryE) {
                        log.error("重试查询服务失败: {}", retryE.getMessage());
                    }
                }
            }
            
            return new ArrayList<>();
        }
    }
    
    @Override
    public void destroy() {
        log.info("销毁注册中心客户端...");
        running = false;
        
        // 注销所有服务
        if (!registeredServices.isEmpty()) {
            log.info("注销所有注册的服务，共{}个", registeredServices.size());
            List<ServiceInfo> services = new ArrayList<>(registeredServices);
            for (ServiceInfo serviceInfo : services) {
                try {
                    unregister(serviceInfo);
                } catch (Exception e) {
                    log.warn("注销服务失败: {}, 错误: {}", serviceInfo, e.getMessage());
                }
            }
        }
        
        // 中断心跳线程
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }
        
        // 关闭连接
        if (channel != null) {
            channel.close();
            channel = null;
        }
        
        // 关闭事件循环组
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully();
        }
        
        // 清空请求和服务缓存
        pendingRequests.clear();
        registeredServices.clear();
        
        log.info("注册中心客户端已销毁");
    }
    
    /**
     * 处理请求响应
     *
     * @param requestId 请求ID
     * @param result 响应结果
     */
    public void handleResponse(long requestId, Object result) {
        CompletableFuture<Object> future = pendingRequests.remove(requestId);
        if (future != null) {
            log.debug("处理请求ID: {}的响应: {}", requestId, result);
            future.complete(result);
        } else {
            log.warn("收到未知请求ID的响应: {}", requestId);
        }
    }
    
    /**
     * 处理请求异常
     *
     * @param requestId 请求ID
     * @param cause 异常原因
     */
    public void handleException(long requestId, Throwable cause) {
        CompletableFuture<Object> future = pendingRequests.remove(requestId);
        if (future != null) {
            log.error("请求ID: {}发生异常: {}", requestId, cause.getMessage());
            future.completeExceptionally(cause);
        } else {
            log.warn("收到未知请求ID的异常: {}", requestId);
        }
    }
    
    /**
     * 从地址字符串中提取主机名
     */
    private String getHostFromAddress(String address) {
        int idx = address.lastIndexOf(':');
        return idx > 0 ? address.substring(0, idx) : address;
    }
    
    /**
     * 从地址字符串中提取端口
     */
    private int getPortFromAddress(String address) {
        int idx = address.lastIndexOf(':');
        if (idx > 0 && idx < address.length() - 1) {
            try {
                return Integer.parseInt(address.substring(idx + 1));
            } catch (NumberFormatException e) {
                log.warn("无效的端口号格式: {}，使用默认端口8000", address.substring(idx + 1));
                return 8000; // 默认端口
            }
        }
        return 8000; // 默认端口
    }
    
    /**
     * 发送心跳请求
     */
    private void sendHeartbeat() {
        if (channel == null || !channel.isActive()) {
            log.warn("注册中心连接不可用，无法发送心跳");
            heartbeatFailCount.incrementAndGet();
            return;
        }
        
        RpcMessage heartbeat = new RpcMessage();
        heartbeat.setMessageType(RpcProtocol.HEARTBEAT_REQUEST_TYPE);
        heartbeat.setRequestId(requestIdGenerator.incrementAndGet());
        heartbeat.setData("PING");
        
        lastHeartbeatTime.set(System.currentTimeMillis());
        
        try {
            ChannelFuture future = channel.writeAndFlush(heartbeat);
            future.addListener(f -> {
                if (!f.isSuccess()) {
                    long failCount = heartbeatFailCount.incrementAndGet();
                    log.error("发送心跳失败 ({}次连续失败): {}", failCount, f.cause().getMessage());
                    
                    // 如果连续多次心跳失败，尝试重新连接
                    if (failCount >= 3) {
                        log.warn("连续{}次心跳失败，将在下次心跳周期尝试重连", failCount);
                    }
                } else {
                    // 心跳成功，重置失败计数
                    heartbeatFailCount.set(0);
                    
                    if (log.isDebugEnabled()) {
                        log.debug("心跳请求发送成功，请求ID: {}", heartbeat.getRequestId());
                    }
                }
            });
        } catch (Exception e) {
            // 记录错误并增加失败计数
            long failCount = heartbeatFailCount.incrementAndGet();
            log.error("发送心跳异常 ({}次连续失败): {}", failCount, e.getMessage());
        }
    }
    
    /**
     * 发送心跳请求（公开方法）
     */
    public void sendHeartbeatPublic() {
        sendHeartbeat();
    }
    
    /**
     * 判断是否启用心跳
     * 
     * @return 是否启用心跳
     */
    public boolean isHeartbeatEnabled() {
        return enableHeartbeat;
    }
} 