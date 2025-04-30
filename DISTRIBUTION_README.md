# RPC框架分发包使用指南

这是一个基于Java的简易RPC（远程过程调用）框架，使用Netty作为网络传输层，Jackson作为序列化工具。此分发包包含了框架的所有组件以及使用示例。

## 分发包内容

- `lib/` - 包含框架的所有JAR文件及其依赖
  - `rpc-common-1.0.0.jar` - 通用工具模块
  - `rpc-core-1.0.0.jar` - 核心模块
  - `rpc-server-1.0.0.jar` - 服务端模块
  - `rpc-client-1.0.0.jar` - 客户端模块
  - `rpc-registry-1.0.0.jar` - 注册中心模块
  - 其他依赖JAR文件
- `examples/` - 包含使用示例代码
- `rpc-registry-1.0.0-executable.jar` - 可直接运行的注册中心服务器
- `README.md` - 项目概述
- `RPCQuickStart.md` - 详细使用指南
- `DISTRIBUTION_README.md` - RPC框架分发包使用指南
- `LICENSE` - 许可证信息
- `install-to-maven.bat`/`install-to-maven.sh` - Maven安装脚本（如有）

## 快速入门

### 1. 启动注册中心

通过以下命令启动注册中心服务器：

```bash
java -jar rpc-registry-1.0.0-executable.jar [port] [debug|test|debugtest]
```

参数说明：
- `port`: 注册中心端口号，默认为8000
- `debug`: 启用调试模式
- `test`: 自动注册测试服务
- `debugtest`: 同时启用调试模式和测试服务

示例：
```bash
java -jar rpc-registry-1.0.0-executable.jar 8000 test
```

> **重要提示**：您可以直接使用此可执行JAR文件启动注册中心，无需安装框架即可使用注册服务。

### 2. 在项目中使用RPC框架

#### 方式一：使用Maven依赖（推荐）

首先，将框架的JAR文件安装到本地Maven仓库。**注意：**需要使用`lib`目录下的JAR文件，而不是`rpc-assembly-1.0.0.jar`。

如果提供了安装脚本，可以直接运行：
```bash
# Windows
install-to-maven.bat

# Linux/Mac
./install-to-maven.sh
```

或者手动安装每个JAR文件：
```bash
mvn install:install-file -Dfile=lib/rpc-common-1.0.0.jar -DgroupId=com.rpc -DartifactId=rpc-common -Dversion=1.0.0 -Dpackaging=jar
mvn install:install-file -Dfile=lib/rpc-core-1.0.0.jar -DgroupId=com.rpc -DartifactId=rpc-core -Dversion=1.0.0 -Dpackaging=jar
mvn install:install-file -Dfile=lib/rpc-server-1.0.0.jar -DgroupId=com.rpc -DartifactId=rpc-server -Dversion=1.0.0 -Dpackaging=jar
mvn install:install-file -Dfile=lib/rpc-client-1.0.0.jar -DgroupId=com.rpc -DartifactId=rpc-client -Dversion=1.0.0 -Dpackaging=jar
mvn install:install-file -Dfile=lib/rpc-registry-1.0.0.jar -DgroupId=com.rpc -DartifactId=rpc-registry -Dversion=1.0.0 -Dpackaging=jar
```

然后，在您的项目pom.xml中添加依赖：

```xml
<!-- 服务提供者依赖 -->
<dependency>
    <groupId>com.rpc</groupId>
    <artifactId>rpc-server</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 服务消费者依赖 -->
<dependency>
    <groupId>com.rpc</groupId>
    <artifactId>rpc-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### 方式二：直接添加JAR文件

如果您不使用Maven，可以直接将`lib`目录中的所有JAR文件添加到您项目的classpath中：

1. 在IDE中，右键点击项目 → 属性/设置 → 库/依赖项
2. 添加外部JAR文件 → 选择`lib`目录中的所有JAR文件
3. 确认并应用设置

## 使用示例

`examples/`目录包含完整的示例代码，展示了如何：
- 定义和实现服务接口
- 启动RPC服务器并注册服务
- 配置RPC客户端并调用远程服务

建议先查看以下文件：
- `examples/demo-api/src/main/java/com/rpc/demo/api/HelloService.java` - 服务接口定义
- `examples/demo-provider/src/main/java/com/rpc/demo/provider/HelloServiceImpl.java` - 服务实现
- `examples/demo-provider/src/main/java/com/rpc/demo/provider/ProviderApplication.java` - 服务器启动类
- `examples/demo-consumer/src/main/java/com/rpc/demo/consumer/ConsumerApplication.java` - 客户端调用示例

## 详细文档

有关框架的详细使用说明，请参考：
- `RPCQuickStart.md` - 详细使用教程
- `README.md` - 项目概述

## 常见问题

1. **找不到类或依赖项**
   确保所有JAR文件都已正确添加到classpath或Maven本地仓库。

2. **注册中心连接失败**
   检查注册中心是否已启动，以及地址和端口配置是否正确。

3. **服务无法注册或发现**
   检查服务名称、版本和组是否一致，以及网络连接是否正常。

## 支持

如有任何问题或建议，请提交到项目的GitHub仓库Issue区。

## 授权许可

此RPC框架基于LICENSE文件中指定的许可证发布。 