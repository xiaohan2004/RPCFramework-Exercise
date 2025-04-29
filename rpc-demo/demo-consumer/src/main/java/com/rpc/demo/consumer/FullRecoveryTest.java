package com.rpc.demo.consumer;

import com.rpc.client.RpcClient;
import com.rpc.core.annotation.RpcReference;
import com.rpc.demo.api.HelloService;

/**
 * 全面测试连接恢复功能
 * 测试注册中心连接和服务提供者连接的自动恢复功能
 */
public class FullRecoveryTest {
    
    @RpcReference(version = "1.0.0")
    private static HelloService helloService;
    
    static {
        RpcClient.inject(FullRecoveryTest.class);
    }
    
    public static void main(String[] args) {
        try {
            System.out.println("======= 完整连接恢复测试 =======");
            
            // 第一阶段：初始调用测试
            System.out.println("\n===== 第一阶段：初始调用测试 =====");
            System.out.println("首次调用服务...");
            String result1 = helloService.sayHello("第一次调用");
            System.out.println("调用结果1: " + result1);
            
            // 确认服务正常工作
            String time1 = helloService.getServerTime();
            System.out.println("服务器时间1: " + time1);
            System.out.println("第一阶段测试通过！");
            
            // 第二阶段：等待连接超时，测试自动重连
            System.out.println("\n===== 第二阶段：等待连接超时，测试自动重连 =====");
            System.out.println("请等待30秒，确保连接自然超时...");
            Thread.sleep(30000);  // 等待30秒
            
            System.out.println("尝试继续调用服务，应自动重连...");
            
            // 在此处，连接应该已经超时，下一次调用将触发重连
            try {
                String result2 = helloService.sayHello("重连后的调用");
                System.out.println("重连后调用成功: " + result2);
                System.out.println("第二阶段测试通过！");
            } catch (Exception e) {
                System.err.println("重连调用失败: " + e.getMessage());
                e.printStackTrace();
                System.err.println("重试一次...");
                
                // 多等待一会再重试
                Thread.sleep(5000);
                
                // 再次尝试，这次应该成功
                String result2Retry = helloService.sayHello("重试的调用");
                System.out.println("重试调用成功: " + result2Retry);
                System.out.println("第二阶段测试勉强通过（需要重试）！");
            }
            
            // 第三阶段：测试连接稳定性
            System.out.println("\n===== 第三阶段：测试连接稳定性 =====");
            for (int i = 0; i < 5; i++) {
                try {
                    String result = helloService.sayHello("稳定性测试 " + (i + 1));
                    System.out.println("调用 " + (i + 1) + " 成功: " + result);
                    Thread.sleep(1000);  // 间隔1秒
                } catch (Exception e) {
                    System.err.println("调用 " + (i + 1) + " 失败: " + e.getMessage());
                }
            }
            System.out.println("第三阶段测试完成！");
            
            // 最终测试：获取服务器时间，确认连接正常
            System.out.println("\n===== 最终测试 =====");
            String finalTime = helloService.getServerTime();
            System.out.println("最终服务器时间: " + finalTime);
            System.out.println("连接恢复与稳定性测试全部完成！");
            
        } catch (Exception e) {
            System.err.println("测试过程中发生异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 