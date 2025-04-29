# 快速上手RPC框架

本文档介绍如何在其他项目中集成和使用当前RPC框架。该框架基于Java实现，使用Netty作为网络传输层，Jackson作为序列化工具。

## 目录
- [使用步骤](#使用步骤)
- [配置详解](#配置详解)
- [注解详解](#注解详解)
- [高级功能](#高级功能)
- [日志配置](#日志配置)
- [注意事项](#注意事项)

## 使用步骤

### 1. 添加依赖

首先，在项目的Maven配置文件(pom.xml)中添加RPC框架的依赖。根据您的需求选择相应的依赖：

```xml
<!-- 添加RPC框架依赖 -->
<dependencies>
    <!-- 如果需要作为服务提供者 -->
    <dependency>
        <groupId>com.rpc</groupId>
        <artifactId>rpc-server</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    
    <!-- 如果需要作为服务消费者 -->
    <dependency>
        <groupId>com.rpc</groupId>
        <artifactId>rpc-client</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    
    <!-- 如果需要启动注册中心 -->
    <dependency>
        <groupId>com.rpc</groupId>
        <artifactId>rpc-registry</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

> 注意：rpc-server、rpc-client和rpc-registry已经依赖了rpc-core模块，所以不需要单独引入rpc-core。

### 2. 启动注册中心

在使用RPC框架之前，需要确保注册中心已经启动。可以创建一个单独的模块来启动注册中心：

```java
// RegistryServerStarter.java
package com.rpc.demo.registry;

import com.rpc.registry.RemoteRegistryServer;

public class RegistryServerStarter {
    public static void main(String[] args) {
        int port = 8000; // 默认端口
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                // 使用默认端口
            }
        }
        
        // 创建并启动注册中心服务器
        RemoteRegistryServer registryServer = new RemoteRegistryServer(port);
        
        // 添加JVM关闭钩子，确保服务器正常关闭
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            registryServer.shutdown();
        }));
        
        System.out.println("注册中心启动在端口: " + port);
        // 启动服务器（这是一个阻塞调用，直到服务器关闭）
        registryServer.start();
    }
}
```

也可以通过命令行启动，指定端口：

```bash
java -jar rpc-registry.jar 9000
```

### 3. 创建服务接口

创建一个独立的模块或包来定义服务接口，这样服务提供者和消费者都可以共享这些接口：

```java
// HelloService.java
package com.rpc.demo.api;

public interface HelloService {
    /**
     * 问候方法
     *
     * @param name 姓名
     * @return 问候语
     */
    String sayHello(String name);
    
    /**
     * 获取当前服务器时间
     *
     * @return 当前服务器时间的字符串表示
     */
    String getServerTime();
}
```

### 4. 实现服务接口

在服务提供者项目中实现这些接口，并使用`@RpcService`注解标记：

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

### 5. 配置并启动服务提供者

#### 5.1 创建配置文件

在服务提供者项目的resources目录下创建`rpc.properties`：

```properties
# 服务提供者配置
rpc.server.ip=127.0.0.1
rpc.server.port=9000
rpc.registry.address=127.0.0.1:8000
rpc.server.use.simple.json=true
```

#### 5.2 创建启动类

创建服务提供者启动类：

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

### 6. 配置并使用服务消费者

#### 6.1 创建配置文件

在服务消费者项目的resources目录下创建`rpc.properties`：

```properties
# 服务消费者配置
rpc.registry.address=127.0.0.1:8000
rpc.client.timeout=5000
rpc.client.use.simple.json=true
```

#### 6.2 使用服务

有两种方式使用远程服务：

##### 方式一：使用代码方式获取服务

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
            
            // 暂停以便观察结果
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // RpcClient会在JVM关闭时自动关闭
    }
}
```

##### 方式二：使用注解方式引用服务（推荐）

```java
// AnnotationConsumer.java
package com.rpc.demo.consumer;

import com.rpc.client.RpcClient;
import com.rpc.core.annotation.RpcReference;
import com.rpc.demo.api.HelloService;

public class AnnotationConsumer {
    
    // 使用@RpcReference注解声明RPC服务依赖
    @RpcReference(version = "1.0.0")
    private static HelloService helloService;
    
    static {
        // 在类加载时注入服务引用
        RpcClient.inject(AnnotationConsumer.class);
    }
    
    public static void main(String[] args) {
        try {
            // 直接调用服务，就像调用本地方法一样
            String result1 = helloService.sayHello("李四");
            System.out.println(result1);
            
            String result2 = helloService.getServerTime();
            System.out.println(result2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

另一种实现方式，在普通Java类中使用：

```java
package com.yourproject.consumer;

import com.rpc.client.RpcClient;
import com.rpc.core.annotation.RpcReference;
import com.yourproject.api.UserService;

public class UserController {
    
    @RpcReference(version = "1.0.0")
    private UserService userService;
    
    public UserController() {
        // 注入RPC服务引用
        RpcClient.inject(this);
    }
    
    public void showUserInfo(Long userId) {
        // 直接调用远程服务，就像调用本地方法一样
        User user = userService.getUserById(userId);
        System.out.println("获取到用户: " + user);
    }
}
```

## 配置详解

### 配置项说明

框架通过properties文件进行配置，支持以下配置项：

| 配置项 | 说明 | 默认值 |
| ----- | ----- | ----- |
| rpc.server.ip | 服务提供者IP地址 | 127.0.0.1 |
| rpc.server.port | 服务提供者端口 | 9000 |
| rpc.registry.address | 注册中心地址，格式为`host:port` | 127.0.0.1:8000 |
| rpc.client.timeout | 客户端请求超时时间(毫秒) | 5000 |
| rpc.server.use.simple.json | 服务端是否使用简化的JSON编解码器 | true |
| rpc.client.use.simple.json | 客户端是否使用简化的JSON编解码器 | true |

### 服务提供者配置示例 (rpc.properties)

```properties
# RPC服务端配置
rpc.server.ip=127.0.0.1
rpc.server.port=9000
rpc.registry.address=127.0.0.1:8000
rpc.server.use.simple.json=true
```

### 服务消费者配置示例 (rpc.properties)

```properties
# RPC客户端配置
rpc.registry.address=127.0.0.1:8000
rpc.client.timeout=5000
rpc.client.use.simple.json=true
```

## 注解详解

框架提供了两个关键注解，用于简化服务的定义和使用：

### @RpcService

用于标记服务实现类，声明该类为RPC服务提供者：

```java
@RpcService(version = "1.0.0", group = "default")
public class HelloServiceImpl implements HelloService {
    // 服务实现
}
```

参数说明：
- `version`: 服务版本号（必填）
- `group`: 服务分组（可选，默认为空字符串）

### @RpcReference

用于自动注入服务引用：

```java
@RpcReference(version = "1.0.0", enableLocalService = false, condition = "")
private HelloService helloService;
```

参数说明：
- `version`: 服务版本号（必填）
- `group`: 服务分组（可选，默认为空字符串）
- `enableLocalService`: 是否启用本地服务（默认false）
- `condition`: 条件表达式，用于决定使用本地服务还是远程服务

## 高级功能

### 条件服务调用

框架支持根据条件在远程服务和本地服务之间动态切换：

```java
// 在每天9:00-18:00期间使用远程服务，其他时间使用本地服务
@RpcReference(
    version = "1.0.0",
    enableLocalService = true,
    condition = "time0900-1800"
)
private HelloService helloService;
```

内置的条件类型包括：
- 时间条件: `time0900-1800`（9:00-18:00之间使用远程服务）
- IP条件: `ip192.168.1.100`（当客户端IP为192.168.1.100时使用远程服务）

### 服务回退机制

框架提供了服务回退机制，确保在服务不可用时系统仍能正常运行：

```java
// 注册本地服务实现
LocalServiceFactory.registerLocalService(UserService.class, new LocalUserServiceImpl());

// 注册回退服务实现
LocalServiceFactory.registerFallbackService(UserService.class, new FallbackUserServiceImpl());
```

回退顺序：远程服务 -> 本地服务 -> 回退服务 -> 默认值

### 异步调用

框架支持异步调用远程服务：

```java
// 获取异步服务代理
HelloService asyncHelloService = client.getAsyncRemoteService(HelloService.class, "1.0.0", "");

// 异步调用服务方法
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    return asyncHelloService.sayHello("王五");
});

// 添加回调
future.thenAccept(result -> {
    System.out.println("异步调用结果: " + result);
});

// 等待结果完成
future.join();
```

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
    
    <!-- 文件输出 -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/rpc.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/rpc.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- 配置RPC框架日志级别 -->
    <logger name="com.rpc" level="DEBUG"/>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

开启DEBUG级别日志可以帮助排查序列化、网络传输等问题。

## 注意事项

使用RPC框架时，请注意以下几点：

1. 确保服务接口的包路径在提供者和消费者项目中完全一致
2. 确保服务版本号匹配
3. 确保注册中心地址正确配置
4. 注意处理可能的远程调用异常
5. 对于复杂的对象传输，确保对象实现了Serializable接口
6. 启动顺序应该是：注册中心 -> 服务提供者 -> 服务消费者
7. 当服务提供者或注册中心宕机时，框架会自动尝试重连