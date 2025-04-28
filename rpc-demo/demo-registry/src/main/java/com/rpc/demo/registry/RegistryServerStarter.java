package com.rpc.demo.registry;

import com.rpc.registry.RemoteRegistryServer;
import lombok.extern.slf4j.Slf4j;

/**
 * 注册中心服务器启动类
 */
@Slf4j
public class RegistryServerStarter {
    
    /**
     * 默认端口
     */
    private static final int DEFAULT_PORT = 8000;
    
    public static void main(String[] args) {
        // 解析命令行参数，获取端口号
        int port = DEFAULT_PORT;
        boolean debug = false;
        
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                log.warn("无效的端口参数: {}, 使用默认端口: {}", args[0], DEFAULT_PORT);
            }
        }
        
        // 检查是否有调试参数
        if (args.length > 1 && "debug".equalsIgnoreCase(args[1])) {
            debug = true;
        }
        
        log.info("===== 开始启动RPC注册中心服务器 =====");
        log.info("端口: {}", port);
        log.info("调试模式: {}", debug ? "启用" : "禁用");
        
        // 创建并启动注册中心服务器
        RemoteRegistryServer registryServer = new RemoteRegistryServer(port, debug);
        
        // 添加JVM关闭钩子，确保服务器正常关闭
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("===== 正在关闭RPC注册中心服务器 =====");
            registryServer.shutdown();
            log.info("===== RPC注册中心服务器已关闭 =====");
        }));
        
        // 启动服务器（这是一个阻塞调用，直到服务器关闭）
        log.info("===== RPC注册中心服务器开始启动 =====");
        registryServer.start();
    }
} 