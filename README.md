# 简易RPC框架

一个基于Java的简易RPC（远程过程调用）框架实现，使用Netty作为网络传输层，Jackson作为序列化工具。

## 框架特性

- 使用Netty实现高性能网络传输
- 基于JSON的序列化和反序列化（使用Jackson库）
- 支持注解方式定义和引用服务
- 简单的服务注册与发现
- 支持配置文件方式配置RPC行为
- 支持同步和异步调用
- 心跳检测和连接管理
- 条件服务调用（根据条件选择本地或远程服务）
- 服务回退与错误处理机制
- 使用SLF4J+Logback进行日志记录

## 项目结构

- **rpc-core**: 核心模块，包含协议定义、序列化、传输等核心功能
- **rpc-server**: 服务端模块，用于服务的暴露和请求处理
- **rpc-client**: 客户端模块，用于服务的引用和远程调用
- **rpc-registry**: 注册中心模块，用于服务的注册和发现
- **rpc-common**: 通用工具类模块
- **rpc-assembly**: 打包模块，用于创建可分发的RPC框架包
- **rpc-demo**: 示例模块，展示如何使用该框架
  - **demo-api**: 接口定义
  - **demo-provider**: 服务提供者
  - **demo-consumer**: 服务消费者
  - **demo-registry**: 注册中心
- **test**: 测试模块

## 构建与打包

本框架使用Maven进行构建和管理。可以通过以下步骤打包整个框架：

```bash
# 完整构建并打包
mvn clean package

# 构建分发包
cd rpc-assembly
mvn clean package
```

构建完成后，您可以在`rpc-assembly/target`目录下找到以下文件：
- `rpc-assembly-1.0.0-distribution.zip`: 完整的分发包，包含所有模块JAR文件、依赖和文档
- `rpc-registry-1.0.0-executable.jar`: 可独立运行的注册中心可执行JAR文件

## 分发与使用

框架构建后可以直接分发给其他开发者使用。使用者只需：

1. 解压分发包
2. 安装JAR文件到本地Maven仓库（使用install-to-maven脚本或手动安装）
3. 在项目中添加相应的依赖
4. 参考示例代码和文档使用框架

## 更多信息

这是一个演示该RPC框架功能的示例项目[RPC框架演示项目](https://github.com/xiaohan2004/RPCFramework-Exercise-Example)。

有关使用说明和详细步骤，请参考[RPCQuickStart.md](RPCQuickStart.md)文件。

有关详细的构建与打包说明，请参考[DISTRIBUTION_README.md](DISTRIBUTION_README.md)文件。

有关已知问题及解决方案，请参考[issues.md](issues.md)文件。

如需更多详细信息，请查看各模块代码和示例代码。 
