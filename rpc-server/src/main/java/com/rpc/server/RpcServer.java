package com.rpc.server;

import com.rpc.common.model.ServiceInfo;
import com.rpc.common.utils.NetUtil;
import com.rpc.core.annotation.RpcService;
import com.rpc.core.config.RpcConfig;
import com.rpc.core.transport.RpcMessageDecoder;
import com.rpc.core.transport.RpcMessageEncoder;
import com.rpc.registry.ServiceRegistry;
import com.rpc.registry.ServiceRegistryFactory;
import com.rpc.server.handler.RpcRequestHandler;
import com.rpc.server.handler.RpcServerHandler;
import io.netty.bootstrap.ServerBootstrap;
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
import java.util.concurrent.TimeUnit;

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
    
    /**
     * 构造函数
     */
    public RpcServer() {
        this(RpcConfig.getServerPort());
    }
    
    /**
     * 构造函数
     *
     * @param port 服务器端口
     */
    public RpcServer(int port) {
        this.port = port;
        this.serverAddress = NetUtil.getLocalIp() + ":" + port;
        this.rpcRequestHandler = new RpcRequestHandler();
        
        // 创建注册中心客户端
        String registryType = RpcConfig.getProperty("rpc.registry.type", ServiceRegistryFactory.LOCAL_REGISTRY);
        String registryAddr = RpcConfig.getRegistryAddress();
        this.serviceRegistry = ServiceRegistryFactory.getServiceRegistry(registryType, registryAddr);
        
        log.info("RPC服务器初始化完成，端口: {}, 注册中心: {} {}", port, registryType, registryAddr);
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
        
        log.info("服务注册成功: {}", serviceInfo);
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
                            ch.pipeline()
                                    // 空闲检测
                                    .addLast(new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS))
                                    // 消息编解码
                                    .addLast(new RpcMessageEncoder())
                                    .addLast(new RpcMessageDecoder())
                                    // 业务处理
                                    .addLast(new RpcServerHandler(rpcRequestHandler));
                        }
                    });
            
            // 绑定端口
            ChannelFuture future = bootstrap.bind(port).sync();
            log.info("RPC服务器启动成功，监听端口: {}", port);
            
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
        
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        
        // 注销所有服务
        if (serviceRegistry != null) {
            serviceRegistry.destroy();
        }
        
        log.info("RPC服务器已关闭");
    }
} 