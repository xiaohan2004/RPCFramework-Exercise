package com.rpc.demo.consumer;

import com.rpc.client.RpcClient;
import com.rpc.demo.api.HelloService;

/**
 * 服务消费者启动类
 */
public class ConsumerApplication {
    
    public static void main(String[] args) {
        // 创建RPC客户端
        RpcClient client = new RpcClient();
        
        try {
            // 获取远程服务代理
            HelloService helloService = client.getRemoteService(HelloService.class, "1.0.0", "");
            
            // 调用远程服务
            String result1 = helloService.sayHello("张三");
            System.out.println(result1);
            
            String result2 = helloService.getServerTime();
            System.out.println(result2);
            
            // 暂停以便观察结果
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 关闭客户端
            client.close();
        }
    }
} 