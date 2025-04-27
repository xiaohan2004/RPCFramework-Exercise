package com.rpc.demo.provider;

import com.rpc.server.RpcServer;

/**
 * 服务提供者启动类
 */
public class ProviderApplication {
    
    public static void main(String[] args) {
        // 创建RPC服务器
        RpcServer server = new RpcServer();
        
        // 注册服务
        server.registerService(new HelloServiceImpl());
        
        // 启动服务器
        System.out.println("RPC服务器启动中...");
        server.start();
    }
} 