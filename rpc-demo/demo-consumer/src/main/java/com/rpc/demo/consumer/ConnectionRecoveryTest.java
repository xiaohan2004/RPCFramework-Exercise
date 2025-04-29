package com.rpc.demo.consumer;

import com.rpc.client.RpcClient;
import com.rpc.core.annotation.RpcReference;
import com.rpc.demo.api.HelloService;

/**
 * 连接恢复测试类
 * 测试禁用心跳后，长时间不活动导致连接断开，然后在调用服务时自动恢复连接的功能
 */
public class ConnectionRecoveryTest {
    
    @RpcReference(version = "1.0.0")
    private static HelloService helloService;
    
    static {
        RpcClient.inject(ConnectionRecoveryTest.class);
    }
    
    public static void main(String[] args) {
        try {
            System.out.println("===== 连接恢复测试 =====");
            
            // 第一次调用服务
            System.out.println("第一次调用服务...");
            String result1 = helloService.sayHello("第一次调用");
            System.out.println("调用结果1: " + result1);
            
            // 等待足够长的时间，确保连接断开
            System.out.println("等待2分钟，确保连接断开...");
            Thread.sleep(120000);  // 等待2分钟
            
            // 再次调用服务，这时应该会自动重连
            System.out.println("第二次调用服务，连接应自动恢复...");
            String result2 = helloService.sayHello("第二次调用");
            System.out.println("调用结果2: " + result2);
            
            // 确认连接恢复正常
            System.out.println("再次调用验证连接稳定性...");
            String result3 = helloService.getServerTime();
            System.out.println("调用结果3: " + result3);
            
            System.out.println("测试完成！连接自动恢复功能正常工作！");
        } catch (Exception e) {
            System.err.println("测试异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 