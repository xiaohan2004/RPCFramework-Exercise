package com.rpc.demo.consumer;

import com.rpc.client.RpcClient;
import com.rpc.core.annotation.RpcReference;
import com.rpc.demo.api.HelloService;

/**
 * 微型RPC消费者示例 - 测试消费者不发送心跳
 */
public class MicroConsumerExample {
    
    @RpcReference(version = "1.0.0")
    private static HelloService helloService;
    
    // 静态代码块在类加载时执行注入
    static {
        RpcClient.inject(MicroConsumerExample.class);
    }
    
    public static void main(String[] args) {
        try {
            System.out.println("===== 测试消费者不发送心跳 =====");
            
            // 直接调用服务
            String result1 = helloService.sayHello("李四");
            System.out.println("调用结果1: " + result1);
            
            String result2 = helloService.getServerTime();
            System.out.println("调用结果2: " + result2);
            
            // 等待30秒观察是否有心跳日志
            System.out.println("请等待30秒，观察是否有心跳日志...");
            Thread.sleep(30000);
            
            // 再次调用验证服务可用性
            String result3 = helloService.sayHello("王五");
            System.out.println("调用结果3: " + result3);
            
            System.out.println("测试完成！");
        } catch (Exception e) {
            System.err.println("测试异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 