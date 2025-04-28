package com.rpc.server;

import com.rpc.common.model.ServiceInfo;
import com.rpc.common.utils.NetUtil;
import com.rpc.core.annotation.RpcService;
import com.rpc.core.config.RpcConfig;
import com.rpc.core.protocol.RpcMessage;
import com.rpc.core.protocol.RpcProtocol;
import com.rpc.core.transport.RpcMessageDecoder;
import com.rpc.core.transport.RpcMessageEncoder;
import com.rpc.core.transport.SimpleJsonDecoder;
import com.rpc.core.transport.SimpleJsonEncoder;
import com.rpc.registry.ServiceRegistry;
import com.rpc.registry.ServiceRegistryFactory;
import com.rpc.server.handler.RpcRequestHandler;
import com.rpc.server.handler.RpcServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RPC服务器
 */
@Slf4j
public class RpcServer {
    /**
     * 服务器端口
     */
    private final int port;
    
    /**
     * 服务器IP
     */
    private final String ip;
    
    /**
     * 注册中心
     */
    private final ServiceRegistry serviceRegistry;
    
    /**
     * 请求处理器
     */
    private final RpcRequestHandler rpcRequestHandler;
    
    /**
     * 服务器地址
     */
    private final String serverAddress;
    
    /**
     * Netty相关成员
     */
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    
    /**
     * 已注册的服务列表
     */
    private final List<ServiceInfo> registeredServices = new ArrayList<>();
    
    /**
     * 心跳线程
     */
    private Thread heartbeatThread;
    private volatile boolean running = false;
    
    /**
     * 心跳间隔（毫秒）
     */
    private static final long HEARTBEAT_INTERVAL = 60000; // 每60秒发送一次心跳
    
    /**
     * 请求ID生成器
     */
    private final AtomicLong requestIdGenerator = new AtomicLong(0);
    
    /**
     * 是否使用简化JSON编解码器
     */
    private final boolean useSimpleJson;
    
    /**
     * 构造函数，使用自动探测的本地IP和配置文件中的端口
     */
    public RpcServer() {
        this(RpcConfig.getServerIp(),RpcConfig.getServerPort());
    }
    
    /**
     * 构造函数，使用自动探测的本地IP和指定端口
     *
     * @param port 服务器端口
     */
    public RpcServer(int port) {
        this.port = port;
        this.ip = NetUtil.getLocalIp();
        this.serverAddress = this.ip + ":" + port;
        this.rpcRequestHandler = new RpcRequestHandler();
        
        // 创建注册中心客户端
        String registryAddr = RpcConfig.getRegistryAddress();
        if (registryAddr == null || registryAddr.isEmpty()) {
            throw new IllegalArgumentException("注册中心地址不能为空，请在配置文件中设置rpc.registry.address");
        }
        
        this.serviceRegistry = ServiceRegistryFactory.getServiceRegistry(registryAddr);
        
        // 读取是否使用简化JSON编解码器的配置
        this.useSimpleJson = RpcConfig.getBoolean("rpc.server.use.simple.json", true);
        
        log.info("RPC服务器初始化完成，IP: {}, 端口: {}, 注册中心地址: {}, 使用简化JSON编解码器: {}", 
                this.ip, port, registryAddr, useSimpleJson);
    }
    
    /**
     * 构造函数，使用配置文件中的IP和端口
     *
     * @param useConfigIp 是否使用配置文件中的IP
     */
    public RpcServer(boolean useConfigIp) {
        this(useConfigIp ? RpcConfig.getServerIp() : NetUtil.getLocalIp(), RpcConfig.getServerPort());
    }
    
    /**
     * 构造函数，指定IP和端口
     *
     * @param ip 服务器IP地址
     * @param port 服务器端口
     */
    public RpcServer(String ip, int port) {
        this.ip = ip;
        this.port = port;
        this.serverAddress = ip + ":" + port;
        this.rpcRequestHandler = new RpcRequestHandler();
        
        // 创建注册中心客户端
        String registryAddr = RpcConfig.getRegistryAddress();
        if (registryAddr == null || registryAddr.isEmpty()) {
            throw new IllegalArgumentException("注册中心地址不能为空，请在配置文件中设置rpc.registry.address");
        }
        
        this.serviceRegistry = ServiceRegistryFactory.getServiceRegistry(registryAddr);
        
        // 读取是否使用简化JSON编解码器的配置
        this.useSimpleJson = RpcConfig.getBoolean("rpc.server.use.simple.json", true);
        
        log.info("RPC服务器初始化完成，IP: {}, 端口: {}, 注册中心地址: {}, 使用简化JSON编解码器: {}", 
                ip, port, registryAddr, useSimpleJson);
    }
    
    /**
     * 注册服务
     *
     * @param serviceBean 服务实例
     */
    public void registerService(Object serviceBean) {
        // 获取服务类型信息
        Class<?> serviceClass = serviceBean.getClass();
        Class<?>[] interfaces = serviceClass.getInterfaces();
        
        if (interfaces.length == 0) {
            throw new IllegalStateException("服务 " + serviceClass.getName() + " 未实现任何接口");
        }
        
        // 获取RpcService注解
        RpcService rpcService = serviceClass.getAnnotation(RpcService.class);
        String serviceName = null;
        String version = "1.0.0";
        String group = "";
        
        if (rpcService != null) {
            // 优先使用注解指定的服务名
            Class<?> serviceInterface = rpcService.value();
            if (serviceInterface != void.class) {
                serviceName = serviceInterface.getName();
            }
            version = rpcService.version();
            group = rpcService.group();
        }
        
        // 如果注解未指定服务名，则使用第一个接口名
        if (serviceName == null) {
            serviceName = interfaces[0].getName();
        }
        
        // 构建服务键
        String serviceKey = serviceName + (version != null ? "_" + version : "") + (group != null ? "_" + group : "");
        
        // 注册到本地服务映射
        rpcRequestHandler.registerService(serviceKey, serviceBean);
        
        // 注册到注册中心
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setServiceName(serviceName);
        serviceInfo.setVersion(version);
        serviceInfo.setGroup(group);
        serviceInfo.setAddress(serverAddress);
        
        serviceRegistry.register(serviceInfo);
        // 添加到已注册列表，用于服务停止时注销
        registeredServices.add(serviceInfo);
        
        log.info("服务注册成功: {}", serviceInfo);
    }
    
    /**
     * 启动心跳线程
     */
    private void startHeartbeatThread() {
        running = true;
        heartbeatThread = new Thread(() -> {
            while (running) {
                try {
                    // 发送心跳
                    sendHeartbeat();
                    
                    // 等待下一次心跳
                    Thread.sleep(HEARTBEAT_INTERVAL);
                } catch (InterruptedException e) {
                    log.warn("心跳线程被中断");
                    break;
                } catch (Exception e) {
                    log.error("发送心跳出错: {}", e.getMessage(), e);
                }
            }
        }, "HeartbeatThread");
        
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
        log.info("心跳线程已启动，间隔: {}秒", HEARTBEAT_INTERVAL / 1000);
    }
    
    /**
     * 发送心跳消息
     */
    private void sendHeartbeat() {
        // 确保注册中心客户端连接有效
        if (serviceRegistry != null) {
            try {
                // 调用注册中心的心跳方法
                ((com.rpc.registry.RemoteServiceRegistry) serviceRegistry).sendHeartbeatPublic();
                log.debug("已发送心跳");
            } catch (Exception e) {
                log.error("发送心跳失败: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 启动RPC服务器
     */
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .option(ChannelOption.SO_BACKLOG, 256)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            if (useSimpleJson) {
                                // 使用简化的JSON编解码器
                                ch.pipeline()
                                        // 添加空闲状态处理器，当30秒没有收到客户端数据时触发
                                        .addLast(new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS))
                                        // 简化的JSON编解码器
                                        .addLast(new SimpleJsonEncoder())
                                        .addLast(new SimpleJsonDecoder())
                                        // 业务处理
                                        .addLast(new RpcServerHandler(rpcRequestHandler));
                                log.info("使用简化的JSON编解码器");
                            } else {
                                // 使用标准的RPC消息编解码器
                                ch.pipeline()
                                        // 添加空闲状态处理器，当30秒没有收到客户端数据时触发
                                        .addLast(new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS))
                                        // 消息编解码
                                        .addLast(new RpcMessageEncoder())
                                        .addLast(new RpcMessageDecoder())
                                        // 业务处理
                                        .addLast(new RpcServerHandler(rpcRequestHandler));
                                log.info("使用标准的RPC消息编解码器");
                            }
                        }
                    });
            
            // 绑定IP和端口
            InetSocketAddress bindAddress = new InetSocketAddress(ip, port);
            ChannelFuture future = bootstrap.bind(bindAddress).sync();
            serverChannel = future.channel();
            log.info("RPC服务器启动成功，监听地址: {}:{}", ip, port);
            
            // 启动心跳线程
            startHeartbeatThread();
            
            // 注册JVM关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
            
            // 等待服务器关闭
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error("RPC服务器启动失败", e);
        } finally {
            shutdown();
        }
    }
    
    /**
     * 关闭RPC服务器
     */
    public void shutdown() {
        log.info("关闭RPC服务器...");
        
        // 停止心跳线程
        running = false;
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
            try {
                heartbeatThread.join(1000);
            } catch (InterruptedException e) {
                log.warn("等待心跳线程关闭时被中断");
            }
        }
        
        // 注销所有服务
        for (ServiceInfo serviceInfo : registeredServices) {
            try {
                serviceRegistry.unregister(serviceInfo);
                log.info("服务注销成功: {}", serviceInfo);
            } catch (Exception e) {
                log.error("服务注销失败: {}", serviceInfo, e);
            }
        }
        registeredServices.clear();
        
        if (serverChannel != null) {
            serverChannel.close();
        }
        
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        
        // 销毁注册中心客户端
        if (serviceRegistry != null) {
            serviceRegistry.destroy();
        }
        
        log.info("RPC服务器已关闭");
    }
} 