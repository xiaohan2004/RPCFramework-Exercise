package com.rpc.registry;

import com.rpc.common.model.ServiceInfo;
import com.rpc.core.protocol.*;
import com.rpc.core.transport.SimpleJsonDecoder;
import com.rpc.core.transport.SimpleJsonEncoder;
import io.netty.bootstrap.ServerBootstrap;
import com.rpc.core.transport.RpcMessageDecoder;
import com.rpc.core.transport.RpcMessageEncoder;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Iterator;

/**
 * 远程注册中心服务器
 */
@Slf4j
public class RemoteRegistryServer {
    /**
     * 服务注册表，key为serviceKey，value为服务信息列表
     */
    private final Map<String, List<ServiceInfo>> serviceMap = new ConcurrentHashMap<>();
    
    /**
     * 服务心跳表，key为服务地址，value为最后心跳时间
     */
    private final Map<String, Long> heartbeatMap = new ConcurrentHashMap<>();
    
    /**
     * 服务器端口
     */
    private final int port;
    
    /**
     * Netty相关成员
     */
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    
    /**
     * 心跳超时检查线程
     */
    private Thread heartbeatCheckThread;
    private volatile boolean running = false;
    
    /**
     * 心跳超时时间（毫秒）
     */
    private static final long HEARTBEAT_TIMEOUT = 30000; // 心跳超时时间30秒
    
    /**
     * 心跳检查间隔（毫秒）
     */
    private static final long HEARTBEAT_CHECK_INTERVAL = 10000; // 心跳检查间隔10秒
    
    /**
     * Debug模式
     */
    private final boolean debug;
    
    /**
     * 是否自动注册测试服务
     */
    private boolean registerTestService = false;
    
    public RemoteRegistryServer(int port) {
        this(port, false);
    }
    
    public RemoteRegistryServer(int port, boolean debug) {
        this.port = port;
        this.debug = debug;
        log.info("初始化远程注册中心服务器，端口: {}, 调试模式: {}", port, debug);
    }
    
    /**
     * 设置是否自动注册测试服务
     */
    public void setRegisterTestService(boolean registerTestService) {
        this.registerTestService = registerTestService;
    }
    
    /**
     * 启动注册中心服务器
     */
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        running = true;
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .option(ChannelOption.SO_BACKLOG, 256)
                    // SO_KEEPALIVE不适用于ServerBootstrap，只适用于Bootstrap或ChildOption
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            log.debug("初始化客户端连接通道: {}", ch.remoteAddress());
                            ch.pipeline()
                                    // 添加日志处理器，记录所有进出的消息
                                    .addLast("logging", debug ? new LoggingHandler(LogLevel.DEBUG) : new LoggingHandler(LogLevel.INFO))
                                    // 空闲检测
                                    .addLast("idle", new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS))
                                    // 使用简化的JSON编解码器
                                    .addLast("encoder", new SimpleJsonEncoder())
                                    .addLast("decoder", new SimpleJsonDecoder())
                                    // 业务处理
                                    .addLast("handler", new RegistryServerHandler(RemoteRegistryServer.this));
                            log.debug("客户端连接通道初始化完成: {}", ch.pipeline().names());
                        }
                    });
            
            log.info("注册中心服务器配置完成，准备启动心跳检查线程");
            
            // 启动心跳检查线程
            startHeartbeatCheck();
            
            // 如果启用了测试服务注册，注册一个测试服务
            if (registerTestService) {
                registerTestServices();
            }
            
            // 绑定端口
            log.info("注册中心服务器准备绑定端口: {}", port);
            ChannelFuture future = bootstrap.bind(port).sync();
            log.info("注册中心服务器启动成功，监听端口: {}", port);
            
            // 创建关闭钩子，确保优雅关闭
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
            
            // 等待服务器关闭
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error("注册中心服务器启动失败: {}", e.getMessage(), e);
        } finally {
            shutdown();
        }
    }
    
    /**
     * 启动心跳检查线程
     */
    private void startHeartbeatCheck() {
        heartbeatCheckThread = new Thread(() -> {
            while (running) {
                try {
                    checkHeartbeats();
                    Thread.sleep(HEARTBEAT_CHECK_INTERVAL); // 使用配置的检查间隔
                } catch (InterruptedException e) {
                    log.warn("心跳检查线程被中断");
                    break;
                } catch (Exception e) {
                    log.error("心跳检查异常: {}", e.getMessage(), e);
                }
            }
        }, "HeartbeatCheckThread");
        
        heartbeatCheckThread.setDaemon(true);
        // 设置较高的优先级，确保心跳检查能正常执行
        heartbeatCheckThread.setPriority(Thread.MAX_PRIORITY - 1);
        heartbeatCheckThread.start();
        log.info("心跳检查线程已启动，检查间隔: {}秒，超时时间: {}秒", 
                HEARTBEAT_CHECK_INTERVAL/1000, HEARTBEAT_TIMEOUT/1000);
    }

    /**
     * 检查心跳超时的服务
     */
    private void checkHeartbeats() {
        long now = System.currentTimeMillis();
        int actuallyRemoved = 0;

        // 检查需要移除的服务
        List<String> ipsToRemove = new ArrayList<>();
        for (Map.Entry<String, Long> entry : heartbeatMap.entrySet()) {
            String ip = entry.getKey();
            long lastHeartbeat = entry.getValue();

            if (now - lastHeartbeat > HEARTBEAT_TIMEOUT) {
                ipsToRemove.add(ip);
                log.warn("服务IP心跳超时，立即移除: {}, 上次心跳: {}毫秒前", ip, now - lastHeartbeat);
            }
        }

        // 执行实际删除操作
        for (String ip : ipsToRemove) {
            removeService(ip);
            actuallyRemoved++;
        }

        if (actuallyRemoved > 0) {
            log.info("心跳检查完成: 实际移除服务IP: {}, 当前服务数: {}", actuallyRemoved, getServiceCount());
        } else {
            log.debug("心跳检查完成: 所有服务心跳正常, 当前服务数: {}", getServiceCount());
        }
    }
    
    /**
     * 移除指定地址的所有服务
     */
    private void removeService(String address) {
        // 提取IP部分
        String ip = extractIpFromAddress(address);
        if (ip == null || ip.isEmpty()) {
            log.warn("无法从地址中提取IP: {}, 无法移除服务", address);
            return;
        }
        
        log.warn("移除IP地址的所有服务: {}", ip);
        heartbeatMap.remove(ip);
        
        // 从服务注册表中移除该IP地址的所有服务
        int removed = 0;
        for (Map.Entry<String, List<ServiceInfo>> entry : serviceMap.entrySet()) {
            String serviceKey = entry.getKey();
            List<ServiceInfo> services = entry.getValue();
            int sizeBefore = services.size();
            
            services.removeIf(service -> {
                // 从服务地址中提取IP，并与当前IP比较
                String serviceIp = extractIpFromAddress(service.getAddress());
                return serviceIp != null && serviceIp.equals(ip);
            });
            
            int sizeAfter = services.size();
            removed += (sizeBefore - sizeAfter);
            
            if (sizeBefore != sizeAfter) {
                log.info("从服务[{}]中移除了{}个实例", serviceKey, sizeBefore - sizeAfter);
            }
        }
        
        // 移除空服务列表
        int emptyKeys = 0;
        for (Iterator<Map.Entry<String, List<ServiceInfo>>> it = serviceMap.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, List<ServiceInfo>> entry = it.next();
            if (entry.getValue().isEmpty()) {
                it.remove();
                emptyKeys++;
                log.info("移除空服务键: {}", entry.getKey());
            }
        }
        
        log.warn("服务移除完成: IP={}, 移除实例数={}, 移除空服务键={}", ip, removed, emptyKeys);
    }
    
    /**
     * 关闭注册中心服务器
     */
    public void shutdown() {
        log.info("关闭注册中心服务器...");
        running = false;
        
        if (heartbeatCheckThread != null) {
            heartbeatCheckThread.interrupt();
        }
        
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        
        log.info("注册中心服务器已关闭");
    }
    
    /**
     * 注册服务
     */
    public void registerService(ServiceInfo serviceInfo) {
        // 规范化ServiceInfo中的字段，确保不会有null值
        if (serviceInfo.getServiceName() == null) {
            serviceInfo.setServiceName("");
        }
        if (serviceInfo.getVersion() == null) {
            serviceInfo.setVersion("");
        }
        if (serviceInfo.getGroup() == null) {
            serviceInfo.setGroup("");
        }
        
        String serviceKey = serviceInfo.getServiceKey();
        log.info("注册服务: {}, 服务Key: {}, 服务名: {}, 版本: {}, 组: {}, 地址: {}", 
                serviceInfo, serviceKey, serviceInfo.getServiceName(), 
                serviceInfo.getVersion(), serviceInfo.getGroup(), serviceInfo.getAddress());
        
        // 如果服务名为空，创建一个默认的服务名
        if (serviceKey == null || serviceKey.isEmpty()) {
            log.warn("服务键为空，使用地址作为服务键: {}", serviceInfo.getAddress());
            serviceKey = "unknown_service_" + serviceInfo.getAddress();
        }
        
        // 获取服务列表，如果不存在则创建
        List<ServiceInfo> serviceList = serviceMap.computeIfAbsent(serviceKey, k -> new ArrayList<>());
        
        // 检查是否已存在相同地址的服务
        boolean exists = serviceList.stream()
                .anyMatch(svc -> svc.getAddress().equals(serviceInfo.getAddress()));
        
        if (!exists) {
            serviceList.add(serviceInfo);
            log.info("服务注册成功: {}, 当前服务键下实例数: {}", serviceInfo, serviceList.size());
        } else {
            log.info("服务已存在，无需重复注册: {}", serviceInfo);
        }
        
        // 更新心跳时间
        updateHeartbeat(serviceInfo.getAddress());
        
        // 打印当前注册表状态
        if (debug) {
            log.info("当前注册表: {}", serviceMap);
        } else {
            log.info("当前注册表服务数量: {}, 服务键列表: {}", serviceMap.size(), serviceMap.keySet());
        }
    }
    
    /**
     * 注销服务
     */
    public void unregisterService(ServiceInfo serviceInfo) {
        String serviceKey = serviceInfo.getServiceKey();
        log.info("注销服务: {}, 服务Key: {}", serviceInfo, serviceKey);
        
        List<ServiceInfo> serviceList = serviceMap.get(serviceKey);
        if (serviceList != null) {
            // 提取原始地址的 IP
            String serviceIp = extractIpFromAddress(serviceInfo.getAddress());
            if (serviceIp == null || serviceIp.isEmpty()) {
                log.warn("无法从服务地址中提取IP: {}, 无法注销服务", serviceInfo.getAddress());
                return;
            }
            
            // 使用IP而不是完整地址进行比较
            serviceList.removeIf(svc -> {
                String svcIp = extractIpFromAddress(svc.getAddress());
                return svcIp != null && svcIp.equals(serviceIp);
            });
            
            log.info("服务注销成功: {}", serviceInfo);
            
            // 如果列表为空，移除该服务条目
            if (serviceList.isEmpty()) {
                serviceMap.remove(serviceKey);
                log.info("服务{}的所有实例已注销，从注册表中移除", serviceKey);
            }
        } else {
            log.warn("注销服务失败，服务不存在: {}", serviceKey);
        }
        
        // 提取服务 IP
        String serviceIp = extractIpFromAddress(serviceInfo.getAddress());
        if (serviceIp == null || serviceIp.isEmpty()) {
            return;
        }
        
        // 检查是否还有其他服务使用相同IP
        boolean hasOtherServices = serviceMap.values().stream()
                .flatMap(List::stream)
                .anyMatch(svc -> {
                    String svcIp = extractIpFromAddress(svc.getAddress());
                    return svcIp != null && svcIp.equals(serviceIp);
                });
        
        // 如果没有其他服务使用此IP，则移除心跳记录
        if (!hasOtherServices) {
            heartbeatMap.remove(serviceIp);
            log.info("IP {}没有其他服务，移除心跳记录", serviceIp);
        }
        
        // 打印当前注册表状态
        if (debug) {
            log.info("当前注册表: {}", serviceMap);
        } else {
            log.info("当前注册表服务数量: {}", serviceMap.size());
        }
    }
    
    /**
     * 发现服务
     */
    public List<ServiceInfo> discoverService(String serviceName, String version, String group) {
        // 构建服务键
        String serviceKey = serviceName + 
                (version != null ? "_" + version : "") + 
                (group != null ? "_" + group : "");
        
        log.info("发现服务: {}, 服务名: {}, 版本: {}, 组: {}", serviceKey, serviceName, version, group);
        
        // 打印当前注册表内容以进行调试
        log.info("当前注册表内容: {}", serviceMap.keySet());
        if (serviceMap.size() <= 10) {
            // 如果服务数量少，打印详细内容
            log.info("当前注册表详细内容: {}", serviceMap);
        } else {
            // 服务数量多，只打印键和对应服务的数量
            serviceMap.forEach((key, list) -> {
                log.info("服务 {} 有 {} 个实例", key, list.size());
            });
        }
        
        List<ServiceInfo> serviceList = serviceMap.get(serviceKey);
        if (serviceList == null || serviceList.isEmpty()) {
            log.warn("未找到服务实例: {}", serviceKey);
            
            // 尝试查找可能匹配的服务（忽略版本和组）
            for (Map.Entry<String, List<ServiceInfo>> entry : serviceMap.entrySet()) {
                if (entry.getKey().startsWith(serviceName + "_") && !entry.getValue().isEmpty()) {
                    log.info("发现可能匹配的服务: {}, 实例数: {}", entry.getKey(), entry.getValue().size());
                }
            }
            
            return new ArrayList<>();
        }
        
        log.info("发现了{}个服务实例: {}", serviceList.size(), serviceKey);
        return new ArrayList<>(serviceList);
    }

    /**
     * 更新服务心跳
     */
    public void updateHeartbeat(String address) {
        if (address == null || address.isEmpty()) {
            log.warn("尝试更新心跳但地址为空");
            return;
        }

        // 从地址中提取IP部分（忽略端口号）
        String ip = extractIpFromAddress(address);
        if (ip == null || ip.isEmpty()) {
            log.warn("无法从地址中提取IP: {}", address);
            return;
        }

        long now = System.currentTimeMillis();
        heartbeatMap.put(ip, now);  // 使用IP作为key更新服务心跳

        // 输出更新后的 heartbeatMap
        log.debug("更新服务心跳: 原地址={}, IP={}, 当前时间: {}, 上次心跳时间: {}, 当前heartbeatMap: {}",
                address, ip, now, heartbeatMap.get(ip), heartbeatMap);
    }

    /**
     * 从地址中提取IP部分
     * 支持以下格式：
     * - "ip:port"
     * - "/ip:port"
     * - "ip"
     */
    private String extractIpFromAddress(String address) {
        if (address == null || address.isEmpty()) {
            return null;
        }

        // 移除开头的斜杠（如果有）
        if (address.startsWith("/")) {
            address = address.substring(1);
        }

        // 分离IP和端口
        int colonIndex = address.lastIndexOf(':');
        if (colonIndex > 0) {
            return address.substring(0, colonIndex);
        }

        // 如果没有冒号，则认为整个字符串就是IP
        return address;
    }

    /**
     * 获取所有已注册的服务
     */
    public Map<String, List<ServiceInfo>> getAllServices() {
        return new ConcurrentHashMap<>(serviceMap);
    }
    
    /**
     * 获取注册的服务总数
     */
    public int getServiceCount() {
        return serviceMap.values().stream()
                .mapToInt(List::size)
                .sum();
    }
    
    /**
     * 注册测试服务
     */
    private void registerTestServices() {
        log.info("注册测试服务...");
        
        // HelloService测试服务
        ServiceInfo helloService = new ServiceInfo();
        helloService.setServiceName("com.rpc.demo.api.HelloService");
        helloService.setVersion("1.0.0");
        helloService.setGroup("");
        helloService.setAddress("127.0.0.1:9000");
        helloService.setWeight(1);
        registerService(helloService);
        
        // 另一个带不同版本和组的HelloService
        ServiceInfo helloServiceV2 = new ServiceInfo();
        helloServiceV2.setServiceName("com.rpc.demo.api.HelloService");
        helloServiceV2.setVersion("2.0.0");
        helloServiceV2.setGroup("test");
        helloServiceV2.setAddress("127.0.0.1:9001");
        helloServiceV2.setWeight(1);
        registerService(helloServiceV2);
        
        log.info("测试服务注册完成");
    }
    
    /**
     * 启动注册中心服务器的主入口
     */
    public static void main(String[] args) {
        int port = 8000; // 默认端口
        boolean debug = false; // 默认非调试模式
        boolean testService = false; // 默认不注册测试服务
        
        // 从命令行参数或配置文件获取端口
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                log.warn("无效的端口参数: {}, 使用默认端口: {}", args[0], port);
            }
        }
        
        // 检查是否启用调试模式
        if (args.length > 1) {
            String arg1 = args[1].toLowerCase();
            if ("debug".equals(arg1)) {
                debug = true;
            } else if ("test".equals(arg1)) {
                testService = true;
            } else if ("debugtest".equals(arg1) || "testdebug".equals(arg1)) {
                debug = true;
                testService = true;
            }
        }
        
        // 检查是否注册测试服务
        if (args.length > 2 && "test".equals(args[2].toLowerCase())) {
            testService = true;
        }
        
        RemoteRegistryServer server = new RemoteRegistryServer(port, debug);
        
        // 如果指定了测试服务标志，启用测试服务注册
        if (testService) {
            log.info("启用测试服务自动注册");
            server.setRegisterTestService(true);
        }
        
        server.start();
    }
} 