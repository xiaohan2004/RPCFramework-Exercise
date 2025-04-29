package com.rpc.demo.consumer;

import com.rpc.client.RpcClient;
import com.rpc.client.local.ConditionEvaluator;
import com.rpc.client.local.LocalServiceFactory;
import com.rpc.core.annotation.RpcReference;
import com.rpc.demo.api.HelloService;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * 简化条件格式示例
 */
public class SimpleConditionDemo {
    
    // 在工作时间（9:00-18:00）使用远程服务，其他时间使用本地服务
    @RpcReference(
        version = "1.0.0",
        enableLocalService = true,
        condition = "time0900-1800"
    )
    private HelloService timeBasedService;
    
    // 当客户端IP为127.0.0.1时使用远程服务
    @RpcReference(
        version = "1.0.0",
        enableLocalService = true,
        condition = "ip127.0.0.1"
    )
    private HelloService ipBasedService;
    
    // 自定义条件：每3次调用使用1次远程服务
    @RpcReference(
        version = "1.0.0",
        enableLocalService = true,
        condition = "count3"
    )
    private HelloService countBasedService;
    
    // 若有远程服务，始终使用远程服务
    @RpcReference(
        version = "1.0.0",
        enableLocalService = true,
        condition = ""
    )
    private HelloService alwaysRemoteService;
    
    static {
        // 注册自定义条件处理器，处理count开头的条件
        // 格式: count后面跟一个数字N，表示每N次调用使用1次远程服务
        ConditionEvaluator.registerConditionHandler("count", new Predicate<String>() {
            private final AtomicInteger counter = new AtomicInteger(0);
            
            @Override
            public boolean test(String condition) {
                try {
                    int n = Integer.parseInt(condition.substring(5));
                    int current = counter.incrementAndGet();
                    if (current >= n) {
                        counter.set(0);
                        return true; // 每n次调用返回1次true，使用远程服务
                    }
                    return false;
                } catch (Exception e) {
                    return false;
                }
            }
        });
    }
    
    public SimpleConditionDemo() {
        // 注册本地服务实现
        LocalServiceFactory.registerLocalService(HelloService.class, new HelloServiceLocalImpl());
        
        // 注入服务引用
        RpcClient.inject(this);
    }
    
    /**
     * 本地服务实现示例
     */
    private static class HelloServiceLocalImpl implements HelloService {
        @Override
        public String sayHello(String name) {
            return "Hello " + name + " (from local service)";
        }
        
        @Override
        public String getServerTime() {
            return "Local time: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }
    }
    
    /**
     * 测试各种条件服务
     */
    public void testServices() {
        System.out.println("=== 测试基于时间的条件服务 ===");
        System.out.println("当前时间: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        System.out.println("时间条件服务返回: " + timeBasedService.sayHello("Alice"));
        System.out.println("(在9:00-18:00之间使用远程服务，其他时间使用本地服务)");
        
        System.out.println("\n=== 测试基于IP的条件服务 ===");
        System.out.println("IP条件服务返回: " + ipBasedService.sayHello("Bob"));
        System.out.println("(当IP为127.0.0.1时使用远程服务，否则使用本地服务)");
        
        System.out.println("\n=== 测试基于计数的条件服务 ===");
        for (int i = 0; i < 5; i++) {
            System.out.println("计数条件服务调用 " + (i + 1) + ": " + countBasedService.sayHello("Charlie"));
        }
        System.out.println("(每3次调用使用1次远程服务，其余使用本地服务)");
        
        System.out.println("\n=== 测试始终远程服务 ===");
        System.out.println("始终远程服务返回: " + alwaysRemoteService.sayHello("David"));
        System.out.println("始终远程服务时间: " + alwaysRemoteService.getServerTime());
    }
    
    public static void main(String[] args) {
        SimpleConditionDemo demo = new SimpleConditionDemo();
        demo.testServices();
        
        // 退出程序
        System.exit(0);
    }
} 