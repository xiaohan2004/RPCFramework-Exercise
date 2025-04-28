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
  - **demo-api**: 接口定义
  - **demo-provider**: 服务提供者
  - **demo-consumer**: 服务消费者
  - **demo-registry**: 注册中心

## 快速开始

### 1. 启动注册中心

首先，您需要启动注册中心，它是服务提供者和消费者之间的桥梁：

```java
// RegistryServerStarter.java
public class RegistryServerStarter {
    public static void main(String[] args) {
        int port = 8000; // 默认端口
        
        // 创建并启动注册中心服务器
        RemoteRegistryServer registryServer = new RemoteRegistryServer(port);
        
        // 添加JVM关闭钩子，确保服务器正常关闭
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            registryServer.shutdown();
        }));
        
        // 启动服务器（这是一个阻塞调用，直到服务器关闭）
        registryServer.start();
    }
}
```

### 2. 定义服务接口

创建一个共享的接口模块，定义服务接口：

```java
// HelloService.java
package com.rpc.demo.api;

public interface HelloService {
    /**
     * 问候方法
     */
    String sayHello(String name);
    
    /**
     * 获取当前服务器时间
     */
    String getServerTime();
}
```

### 3. 实现服务接口

在服务提供者模块中实现该接口：

```java
// HelloServiceImpl.java
package com.rpc.demo.provider;

import com.rpc.core.annotation.RpcService;
import com.rpc.demo.api.HelloService;

import java.text.SimpleDateFormat;
import java.util.Date;

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

### 4. 启动服务提供者

在服务提供者模块中创建启动类：

```java
// ProviderApplication.java
package com.rpc.demo.provider;

import com.rpc.server.RpcServer;

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

### 5. 启动服务消费者

在服务消费者模块中创建启动类：

```java
// ConsumerApplication.java
package com.rpc.demo.consumer;

import com.rpc.client.RpcClient;
import com.rpc.demo.api.HelloService;

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

### 注册中心配置

注册中心默认使用8000端口，可以通过命令行参数指定其他端口：

```bash
java -jar rpc-registry.jar 9000
```

### 服务提供者配置 (rpc.properties)

```properties
# RPC服务端配置
rpc.server.ip=127.0.0.1
rpc.server.port=9000
rpc.registry.address=127.0.0.1:8000
rpc.server.use.simple.json=true
```

### 服务消费者配置 (rpc.properties)

```properties
# RPC客户端配置
rpc.registry.address=127.0.0.1:8000
rpc.client.timeout=5000
rpc.client.use.simple.json=true
```

### 配置说明

| 配置项 | 说明 | 默认值 |
| ----- | ----- | ----- |
| rpc.server.ip | 服务提供者IP地址 | 127.0.0.1 |
| rpc.server.port | 服务提供者端口 | 9000 |
| rpc.registry.address | 注册中心地址，格式为`host:port` | 127.0.0.1:8000 |
| rpc.client.timeout | 客户端请求超时时间(毫秒) | 5000 |
| rpc.server.use.simple.json | 服务端是否使用简化的JSON编解码器 | true |
| rpc.client.use.simple.json | 客户端是否使用简化的JSON编解码器 | true |

## 扩展性

框架设计考虑了扩展性，主要可扩展点包括：

1. 序列化方式：可以扩展实现不同的序列化方式
2. 注册中心：可以实现不同的注册中心
3. 负载均衡：可以实现不同的负载均衡策略
4. 传输协议：可以扩展通信协议

## 故障排除

### 已知问题及解决方案

#### 1. "RPC调用失败: 调用成功" 异常

**问题描述**：在某些情况下，即使RPC调用实际成功，客户端也可能抛出 `java.lang.RuntimeException: RPC调用失败: 调用成功` 的矛盾异常。

**原因**：这是由于响应处理过程中对Integer类型的响应码比较不当引起的。在Java中，使用`!=`比较Integer对象时，如果两个对象引用不同的Integer实例（尽管值相同），`!=`运算符可能返回true。

**解决方案**：在`RpcFuture.get()`方法中，我们修改了响应码的比较方式，使用`equals()`方法代替`!=`运算符，并添加了对空值的检查：

```java
// 修改前
if (response.getCode() != RpcResponse.SUCCESS_CODE) {
    throw new RuntimeException("RPC调用失败: " + response.getMessage());
}

// 修改后
if (response.getCode() == null) {
    log.error("RPC响应码为null，默认视为成功");
    response.setCode(RpcResponse.SUCCESS_CODE);
} else if (!RpcResponse.SUCCESS_CODE.equals(response.getCode())) {
    log.error("RPC调用失败: code={}, message={}", response.getCode(), response.getMessage());
    throw new RuntimeException("RPC调用失败: " + response.getMessage());
}
```

#### 2. 序列化问题

**问题描述**：在某些复杂对象的序列化和反序列化过程中，可能会丢失某些字段信息或类型信息。

**解决方案**：我们增强了`JsonUtils`类，添加了更详细的日志记录，便于排查问题。同时，确保Jackson的类型信息保留，通过`enableDefaultTyping`方法。

## 待完善

1. 服务治理功能
2. 集群容错策略
3. 更多负载均衡策略
4. 跨语言支持
5. 监控和统计

## 注册中心说明

本框架采用独立的注册中心服务，供服务端和客户端使用。注册中心负责服务注册、发现和健康检查。

### 心跳机制

心跳机制在**服务提供者**和**注册中心**之间实现：
1. 服务提供者定期向注册中心发送心跳
2. 注册中心检测服务提供者的心跳超时情况
3. 当服务提供者心跳超时，注册中心会将其从服务列表中移除

### 服务发现流程

1. 服务提供者启动时，向注册中心注册自己提供的服务
2. 服务消费者每次请求服务时，先从注册中心查询可用的服务提供者
3. 如果找到服务提供者，则建立连接并发起调用
4. 如果未找到服务或服务已下线，注册中心返回空结果，客户端会抛出异常

### 配置说明

在 `rpc.properties` 中配置注册中心：

```properties
# 注册中心地址（格式：host:port）
rpc.registry.address=127.0.0.1:9000
```

### 启动注册中心服务

注册中心作为独立服务运行：

```bash
java -jar rpc-registry.jar 9000
```

默认监听端口为 9000，可通过命令行参数指定其他端口。

## 日志配置

本框架使用SLF4J+Logback进行日志记录。通过添加以下配置文件自定义日志输出：

```xml
<!-- logback.xml -->
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- 配置RPC框架日志级别 -->
    <logger name="com.rpc" level="DEBUG"/>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

开启DEBUG级别日志可以帮助排查序列化、网络传输等问题。 