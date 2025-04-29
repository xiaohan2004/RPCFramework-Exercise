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

## 条件服务调用功能

框架提供了一个强大的条件服务调用功能，允许用户根据特定条件在远程服务和本地服务之间动态切换，提高系统的灵活性和可用性。

### 功能概述

条件服务调用功能允许根据条件选择服务调用方式：

1. 当条件为**真**时调用**远程服务**
2. 当条件为**假**时调用**本地服务**

使用简单的字符串格式定义条件：

1. `time0900-1800`：时间条件，在9:00-18:00期间使用远程服务，其他时间使用本地服务
2. `ip192.168.1.1`：IP条件，当客户端IP为192.168.1.1时使用远程服务
3. 空字符串("")：无条件，默认使用远程服务（但可以启用本地服务作为始终使用本地服务的选项）
4. 自定义条件：可以通过注册自定义条件处理器实现更复杂的逻辑

### 使用步骤

#### 1. 注册本地服务实现

首先，需要注册本地服务实现到`LocalServiceFactory`：

```java
import com.rpc.client.local.LocalServiceFactory;
import com.rpc.demo.api.HelloService;

// 创建本地服务实现
HelloService localHelloService = new HelloServiceLocalImpl();

// 注册到本地服务工厂
LocalServiceFactory.registerLocalService(HelloService.class, "1.0.0", "", localHelloService);
```

#### 2. 在RpcReference注解中配置条件

根据需要在`@RpcReference`注解中配置条件属性：

```java
import com.rpc.core.annotation.RpcReference;
import com.rpc.demo.api.HelloService;

public class MyConsumer {
    // 在每天9:00-18:00期间使用远程服务，其他时间使用本地服务
    @RpcReference(
        version = "1.0.0",
        enableLocalService = true,
        condition = "time0900-1800"
    )
    private HelloService helloService;
}
```

#### 3. 自动注入服务引用

使用`RpcClient.inject`方法注入服务引用：

```java
import com.rpc.client.RpcClient;

// 注入实例字段
RpcClient.inject(this);

// 或注入静态字段
RpcClient.inject(MyConsumer.class);
```

### 内置条件类型

#### 时间条件

使用`time`前缀，后面接开始时间和结束时间（24小时制，不带冒号），中间用`-`分隔：

```java
@RpcReference(
    version = "1.0.0",
    enableLocalService = true,
    condition = "time0900-1800" // 9:00-18:00之间使用远程服务
)
private HelloService timeBasedService;
```

#### IP条件

使用`ip`前缀，后面接IP地址：

```java
@RpcReference(
    version = "1.0.0",
    enableLocalService = true,
    condition = "ip192.168.1.100" // 当客户端IP为192.168.1.100时使用远程服务
)
private HelloService ipBasedService;
```

### 自定义条件类型

可以注册自定义条件处理器以支持更多条件类型：

```java
// 注册处理"count"开头的条件，表示计数条件
ConditionEvaluator.registerConditionHandler("count", condition -> {
    try {
        // 假设格式为"count3"，表示每3次调用使用1次远程服务
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
});
```

### 完整示例

```java
import com.rpc.client.RpcClient;
import com.rpc.client.local.ConditionEvaluator;
import com.rpc.client.local.LocalServiceFactory;
import com.rpc.core.annotation.RpcReference;
import com.rpc.demo.api.HelloService;
import java.util.function.Predicate;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleConditionDemo {
    private static final AtomicInteger counter = new AtomicInteger(0);
    
    // 在工作时间（9:00-18:00）使用远程服务，其他时间使用本地服务
    @RpcReference(
        version = "1.0.0",
        enableLocalService = true,
        condition = "time0900-1800"
    )
    private HelloService timeBasedService;
    
    // 每3次调用使用1次远程服务
    @RpcReference(
        version = "1.0.0",
        enableLocalService = true,
        condition = "count3"
    )
    private HelloService countBasedService;
    
    static {
        // 注册自定义条件处理器
        ConditionEvaluator.registerConditionHandler("count", condition -> {
            try {
                int n = Integer.parseInt(condition.substring(5));
                int current = counter.incrementAndGet();
                if (current >= n) {
                    counter.set(0);
                    return true; // 使用远程服务
                }
                return false; // 使用本地服务
            } catch (Exception e) {
                return false;
            }
        });
    }
    
    public SimpleConditionDemo() {
        // 注册本地服务实现
        LocalServiceFactory.registerLocalService(
            HelloService.class, 
            new HelloServiceLocalImpl()
        );
        
        // 注入服务引用
        RpcClient.inject(this);
    }
    
    // 本地服务实现示例
    private static class HelloServiceLocalImpl implements HelloService {
        @Override
        public String sayHello(String name) {
            return "Hello " + name + " (from local service)";
        }
        
        @Override
        public String getServerTime() {
            return "Local time: " + new java.util.Date();
        }
    }
    
    public static void main(String[] args) {
        SimpleConditionDemo demo = new SimpleConditionDemo();
        
        // 调用服务（框架会根据条件判断使用本地服务还是远程服务）
        System.out.println(demo.timeBasedService.sayHello("Alice"));
        
        for (int i = 0; i < 5; i++) {
            System.out.println("Count-based call " + (i + 1) + ": " + 
                demo.countBasedService.sayHello("Bob"));
        }
    }
}
```

### 注意事项

1. 必须启用`enableLocalService = true`才能使用条件服务调用功能
2. 在调用服务之前必须注册本地服务实现，否则当需要使用本地服务时会回退到远程服务
3. 自定义条件处理器应在使用前注册
4. 当条件字符串为空时，默认使用远程服务 

## 服务回退与错误处理机制

框架提供了完整的服务回退与友好错误处理机制，确保即使在服务不可用时，系统仍能正常运行。

### 特性

1. **服务发现失败的优雅处理**：
   - 当找不到远程服务实例时不再直接抛出异常
   - 对于开启了`enableLocalService`的情况，自动尝试本地服务
   - 提供默认回退服务机制，确保返回符合接口规范的值

2. **回退服务机制**：
   - 为接口添加指定的回退实现，作为服务不可用时的替代方案
   - 自动生成默认回退服务，根据方法返回类型提供合适的默认值
   - 多级回退策略：远程服务 -> 本地服务 -> 回退服务 -> 默认值

3. **友好的错误处理**：
   - 为不同的返回类型提供适当的默认值而非抛出异常
   - 对于String类型返回预设错误消息
   - 对于集合类型返回空集合
   - 对于Future类型返回已完成的异常Future

### 使用方式

#### 1. 注册本地服务和回退服务

```java
// 注册本地服务实现
LocalServiceFactory.registerLocalService(UserService.class, new LocalUserServiceImpl());

// 注册回退服务实现
LocalServiceFactory.registerFallbackService(UserService.class, new FallbackUserServiceImpl());
```

#### 2. 自动创建默认回退服务

```java
// 获取本地服务，如不存在则创建默认回退服务
Object service = LocalServiceFactory.getLocalServiceWithFallback(
    serviceName, version, group, true);
```

#### 3. 开启RPC引用的本地服务支持

```java
@RpcReference(
    version = "1.0.0",
    enableLocalService = true  // 开启本地服务支持
)
private UserService userService;
```

### 配置项说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| enableLocalService | 是否启用本地服务 | false |
| condition | 本地服务条件表达式 | "" |

### 工作流程

1. 客户端发起RPC调用
2. 框架尝试发现远程服务实例
3. 如服务发现失败：
   - 若`enableLocalService=true`：尝试使用本地服务
   - 若本地服务不存在：尝试使用回退服务
   - 若回退服务不存在：返回友好的错误响应（非异常）
4. 如服务调用出现其他错误：返回友好的错误响应

### 远程调用失败时回退本地服务

为提高系统可用性，框架现在支持在远程调用过程中失败时回退到本地服务：

1. **多层次回退机制**：
   - 条件评估初始决定是使用远程还是本地服务
   - 若选择远程服务但服务发现失败，自动回退到本地服务
   - 即使在远程调用执行过程中失败（如ExecutionException），也能正确回退到本地服务

2. **异常处理增强**：
   - 捕获并检查执行异常中的"未找到服务提供者"关键信息
   - 在检测到服务不可用时，自动切换到本地实现或回退服务
   - 保留原始异常信息以便更精确地诊断问题

3. **计数条件服务支持**：
   - 支持使用计数条件（如`count3`）交替使用远程和本地服务
   - 即使在计数条件决定使用远程服务但远程服务不可用时，也能回退到本地服务

```java
// 示例：使用计数条件服务（每3次调用使用1次远程服务）
@RpcReference(
    version = "1.0.0",
    enableLocalService = true,
    condition = "count3"  // 每3次调用时选择1次远程服务
)
private HelloService countService;

// 即使当计数器触发远程调用但远程服务不可用时，
// 系统也会自动回退到本地服务，确保调用不会失败
```

这个增强解决了在远程服务不可用、网络出现问题或远程执行异常时，系统能始终提供服务的能力，大大提高了系统的弹性和可用性。

### 示例代码

```java
// 服务接口
public interface UserService {
    String getUserName(Long userId);
    int getUserAge(Long userId);
    boolean userExists(Long userId);
}

// 回退服务实现
public class FallbackUserService implements UserService {
    @Override
    public String getUserName(Long userId) {
        return "[回退服务] 无法获取用户名，服务不可用";
    }
    
    @Override
    public int getUserAge(Long userId) {
        return -1; // 表示未知年龄
    }
    
    @Override
    public boolean userExists(Long userId) {
        log.warn("回退服务：无法验证用户是否存在，默认返回false");
        return false;
    }
}

// 使用示例
UserService userService = client.getRemoteService(UserService.class);
String userName = userService.getUserName(123L);  // 即使服务不可用也不会抛异常
```

更多示例请参考`ServiceFallbackDemo`类。 