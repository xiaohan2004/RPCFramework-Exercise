package com.rpc.demo.consumer;

import com.rpc.client.RpcClient;
import com.rpc.client.local.LocalServiceFactory;
import com.rpc.core.annotation.RpcReference;
import com.rpc.demo.api.HelloService;

/**
 * 布尔条件示例类
 * 演示booltrue和boolfalse条件的使用
 */
public class BoolConditionDemo {
    
    // 强制使用远程服务，不管本地服务是否可用
    @RpcReference(
        version = "1.0.0",
        enableLocalService = true,
        condition = "booltrue"
    )
    private HelloService alwaysRemoteService;
    
    // 强制使用本地服务，适用于开发测试或脱机运行场景
    @RpcReference(
        version = "1.0.0",
        enableLocalService = true,
        condition = "boolfalse"
    )
    private HelloService alwaysLocalService;
    
    public BoolConditionDemo() {
        // 注册本地服务实现
        LocalServiceFactory.registerLocalService(HelloService.class, new LocalHelloServiceImpl());
        
        // 注入服务引用
        RpcClient.inject(this);
    }
    
    /**
     * 本地HelloService实现
     */
    private static class LocalHelloServiceImpl implements HelloService {
        @Override
        public String sayHello(String name) {
            return "你好，" + name + "（来自本地服务）";
        }
        
        @Override
        public String getServerTime() {
            return "本地时间: " + System.currentTimeMillis();
        }
    }
    
    /**
     * 测试布尔条件
     */
    public void testBoolConditions() {
        System.out.println("===== 测试布尔条件 =====");
        
        try {
            // 测试booltrue条件（应该使用远程服务）
            System.out.println("[booltrue条件] 调用sayHello:");
            String result1 = alwaysRemoteService.sayHello("张三");
            System.out.println("   结果: " + result1);
            
            // 测试boolfalse条件（应该使用本地服务）
            System.out.println("[boolfalse条件] 调用sayHello:");
            String result2 = alwaysLocalService.sayHello("李四");
            System.out.println("   结果: " + result2);
            
        } catch (Exception e) {
            System.err.println("测试过程中发生异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("===== 测试完成 =====");
    }
    
    /**
     * 主方法
     */
    public static void main(String[] args) {
        BoolConditionDemo demo = new BoolConditionDemo();
        demo.testBoolConditions();
    }
}