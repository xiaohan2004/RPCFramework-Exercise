package com.rpc.demo.consumer;

import com.rpc.client.RpcClient;
import com.rpc.core.annotation.RpcReference;
import com.rpc.demo.api.HelloService;

/**
 * 服务消费者启动类
 */
public class ConsumerApplication {
    
    // 使用@RpcReference注解声明RPC服务依赖
    @RpcReference(version = "1.0.0")
    private static HelloService helloService;
    
    static {
        // 在类加载时注入服务引用
        RpcClient.inject(ConsumerApplication.class);
    }
    
    public static void main(String[] args) {
        try {
            // 直接调用服务，就像调用本地方法一样
            String result1 = helloService.sayHello("张三");
            System.out.println(result1);
            
            String result2 = helloService.getServerTime();
            System.out.println(result2);
            
            // 暂停以便观察结果
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // RpcClient会在JVM关闭时自动关闭
    }
} 