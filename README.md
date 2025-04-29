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
- **rpc-demo**: 示例模块，展示如何使用该框架
  - **demo-api**: 接口定义
  - **demo-provider**: 服务提供者
  - **demo-consumer**: 服务消费者
  - **demo-registry**: 注册中心

## 更多信息

有关使用说明和详细步骤，请参考[RPCQuickStart.md](RPCQuickStart.md)文件。

有关已知问题及解决方案，请参考[issues.md](issues.md)文件。

如需更多详细信息，请查看各模块代码和示例代码。 