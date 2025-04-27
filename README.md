# 简易RPC框架

一个基于Java的简易RPC（远程过程调用）框架实现，使用Netty作为网络传输层，Jackson作为序列化工具。

## 特性

- 使用Netty实现高性能网络传输
- 基于JSON的序列化和反序列化（使用Jackson库）
- 支持注解方式定义和引用服务
- 简单的服务注册与发现
- 支持配置文件方式配置RPC行为
- 支持同步和异步调用
- 心跳检测和连接管理
- 可扩展的架构设计

## 项目结构

- **rpc-core**: 核心模块，包含协议定义、序列化、传输等核心功能
- **rpc-server**: 服务端模块，用于服务的暴露和请求处理
- **rpc-client**: 客户端模块，用于服务的引用和远程调用
- **rpc-registry**: 注册中心模块，用于服务的注册和发现
- **rpc-common**: 通用工具类模块
- **rpc-demo**: 示例模块，展示如何使用该框架

## 快速开始

### 1. 定义服务接口

```java
public interface HelloService {
    String sayHello(String name);
    String getServerTime();
}
```

### 2. 实现服务接口

```java
@RpcService(version = "1.0.0")
public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) {
        return "你好, " + name + "! 来自RPC服务端的问候!";
    }
    
    @Override
    public String getServerTime() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return "服务器当前时间: " + format.format(new Date());
    }
}
```

### 3. 启动服务提供者

```java
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
```

### 4. 启动服务消费者

```java
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
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 关闭客户端
            client.close();
        }
    }
}
```

## 配置

服务端配置示例 (rpc.properties):

```properties
# RPC服务端配置
rpc.server.port=9000
rpc.registry.type=local
rpc.registry.address=127.0.0.1:8000
```

客户端配置示例 (rpc.properties):

```properties
# RPC客户端配置
rpc.registry.type=local
rpc.registry.address=127.0.0.1:8000
rpc.client.timeout=5000
```

## 扩展性

框架设计考虑了扩展性，主要可扩展点包括：

1. 序列化方式：可以扩展实现不同的序列化方式
2. 注册中心：可以实现不同的注册中心
3. 负载均衡：可以实现不同的负载均衡策略
4. 传输协议：可以扩展通信协议

## 待完善

1. 服务治理功能
2. 集群容错策略
3. 更多负载均衡策略
4. 跨语言支持
5. 监控和统计 