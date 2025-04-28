package com.rpc.registry;

import com.rpc.common.model.ServiceInfo;
import com.rpc.core.protocol.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 注册中心服务器处理器
 */
@Slf4j
public class RegistryServerHandler extends SimpleChannelInboundHandler<RpcMessage> {
    
    private final RemoteRegistryServer registryServer;
    
    public RegistryServerHandler(RemoteRegistryServer registryServer) {
        this.registryServer = registryServer;
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("客户端已连接: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("客户端已断开连接: {}", ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) throws Exception {
        log.debug("注册中心收到消息: {}, 消息类型: {}, 请求ID: {}", msg, msg.getMessageType(), msg.getRequestId());
        
        try {
            byte messageType = msg.getMessageType();
            // 创建响应消息
            RpcMessage responseMsg = new RpcMessage();
            responseMsg.setRequestId(msg.getRequestId());
            responseMsg.setSerializationType(msg.getSerializationType());
            responseMsg.setCompressType(msg.getCompressType());
            
            // 检查消息状态，如果已经是失败状态，说明反序列化失败
            if (msg.getStatus() == RpcProtocol.STATUS_FAIL) {
                log.error("消息解码失败，无法处理请求: {}", msg.getData());
                responseMsg.setMessageType(RpcProtocol.REGISTRY_RESPONSE_TYPE);
                responseMsg.setStatus(RpcProtocol.STATUS_FAIL);
                responseMsg.setData("消息解码失败: " + msg.getData());
                ctx.writeAndFlush(responseMsg);
                return;
            }
            
            // 处理心跳请求
            if (messageType == RpcProtocol.HEARTBEAT_REQUEST_TYPE) {
                // 更新服务心跳
                String address = ctx.channel().remoteAddress().toString();
                if (address.startsWith("/")) {
                    address = address.substring(1);
                }
                registryServer.updateHeartbeat(address);
                
                responseMsg.setMessageType(RpcProtocol.HEARTBEAT_RESPONSE_TYPE);
                responseMsg.setData("PONG");
                responseMsg.setStatus(RpcProtocol.STATUS_SUCCESS);
                log.debug("已处理心跳请求，返回PONG响应");
            } 
            // 处理服务注册请求
            else if (messageType == RpcProtocol.REGISTRY_REGISTER_TYPE) {
                if (!(msg.getData() instanceof ServiceInfo)) {
                    log.error("注册请求数据类型错误: {}", msg.getData().getClass().getName());
                    responseMsg.setMessageType(RpcProtocol.REGISTRY_RESPONSE_TYPE);
                    responseMsg.setData("数据类型错误，期望ServiceInfo类型");
                    responseMsg.setStatus(RpcProtocol.STATUS_FAIL);
                } else {
                    ServiceInfo serviceInfo = (ServiceInfo) msg.getData();
                    log.info("收到服务注册请求: {}", serviceInfo);
                    
                    // 验证serviceInfo的必要字段是否完整
                    if (serviceInfo.getServiceName() == null || serviceInfo.getServiceName().isEmpty()) {
                        log.error("服务注册失败：服务名称为空");
                        responseMsg.setMessageType(RpcProtocol.REGISTRY_RESPONSE_TYPE);
                        responseMsg.setData("服务注册失败：服务名称为空");
                        responseMsg.setStatus(RpcProtocol.STATUS_FAIL);
                    } else if (serviceInfo.getAddress() == null || serviceInfo.getAddress().isEmpty()) {
                        log.error("服务注册失败：服务地址为空");
                        responseMsg.setMessageType(RpcProtocol.REGISTRY_RESPONSE_TYPE);
                        responseMsg.setData("服务注册失败：服务地址为空");
                        responseMsg.setStatus(RpcProtocol.STATUS_FAIL);
                    } else {
                        registryServer.registerService(serviceInfo);
                        
                        responseMsg.setMessageType(RpcProtocol.REGISTRY_RESPONSE_TYPE);
                        responseMsg.setData("Service registered successfully");
                        responseMsg.setStatus(RpcProtocol.STATUS_SUCCESS);
                    }
                }
            } 
            // 处理服务注销请求
            else if (messageType == RpcProtocol.REGISTRY_UNREGISTER_TYPE) {
                if (!(msg.getData() instanceof ServiceInfo)) {
                    log.error("注销请求数据类型错误: {}", msg.getData().getClass().getName());
                    responseMsg.setMessageType(RpcProtocol.REGISTRY_RESPONSE_TYPE);
                    responseMsg.setData("数据类型错误，期望ServiceInfo类型");
                    responseMsg.setStatus(RpcProtocol.STATUS_FAIL);
                } else {
                    ServiceInfo serviceInfo = (ServiceInfo) msg.getData();
                    log.info("收到服务注销请求: {}", serviceInfo);
                    
                    // 验证serviceInfo的必要字段是否完整
                    if (serviceInfo.getServiceName() == null || serviceInfo.getServiceName().isEmpty()) {
                        log.error("服务注销失败：服务名称为空");
                        responseMsg.setMessageType(RpcProtocol.REGISTRY_RESPONSE_TYPE);
                        responseMsg.setData("服务注销失败：服务名称为空");
                        responseMsg.setStatus(RpcProtocol.STATUS_FAIL);
                    } else if (serviceInfo.getAddress() == null || serviceInfo.getAddress().isEmpty()) {
                        log.error("服务注销失败：服务地址为空");
                        responseMsg.setMessageType(RpcProtocol.REGISTRY_RESPONSE_TYPE);
                        responseMsg.setData("服务注销失败：服务地址为空");
                        responseMsg.setStatus(RpcProtocol.STATUS_FAIL);
                    } else {
                        registryServer.unregisterService(serviceInfo);
                        
                        responseMsg.setMessageType(RpcProtocol.REGISTRY_RESPONSE_TYPE);
                        responseMsg.setData("Service unregistered successfully");
                        responseMsg.setStatus(RpcProtocol.STATUS_SUCCESS);
                    }
                }
            } 
            // 处理服务发现请求
            else if (messageType == RpcProtocol.REGISTRY_LOOKUP_TYPE) {
                if (!(msg.getData() instanceof RegistryLookupRequest)) {
                    log.error("查询请求数据类型错误: {}", msg.getData().getClass().getName());
                    responseMsg.setMessageType(RpcProtocol.REGISTRY_RESPONSE_TYPE);
                    responseMsg.setData("数据类型错误，期望RegistryLookupRequest类型");
                    responseMsg.setStatus(RpcProtocol.STATUS_FAIL);
                } else {
                    RegistryLookupRequest lookupRequest = (RegistryLookupRequest) msg.getData();
                    log.info("收到服务查询请求: {}", lookupRequest);
                    
                    // 验证lookupRequest的必要字段
                    if (lookupRequest.getServiceName() == null || lookupRequest.getServiceName().isEmpty()) {
                        log.error("服务查询失败：服务名称为空");
                        responseMsg.setMessageType(RpcProtocol.REGISTRY_RESPONSE_TYPE);
                        responseMsg.setData("服务查询失败：服务名称为空");
                        responseMsg.setStatus(RpcProtocol.STATUS_FAIL);
                    } else {
                        List<ServiceInfo> services = registryServer.discoverService(
                                lookupRequest.getServiceName(), 
                                lookupRequest.getVersion(), 
                                lookupRequest.getGroup());
                        
                        RegistryLookupResponse response = new RegistryLookupResponse();
                        response.setServices(services);
                        
                        responseMsg.setMessageType(RpcProtocol.REGISTRY_RESPONSE_TYPE);
                        responseMsg.setData(response);
                        responseMsg.setStatus(RpcProtocol.STATUS_SUCCESS);
                        log.info("服务查询返回{}个结果", services.size());
                    }
                }
            } else {
                log.warn("未知的消息类型: {}", messageType);
                responseMsg.setMessageType(RpcProtocol.REGISTRY_RESPONSE_TYPE);
                responseMsg.setData("Unknown message type: " + messageType);
                responseMsg.setStatus(RpcProtocol.STATUS_FAIL);
            }
            
            // 发送响应
            log.debug("发送响应消息: {}", responseMsg);
            ctx.writeAndFlush(responseMsg);
        } catch (Exception e) {
            log.error("处理消息时发生异常: {}", e.getMessage(), e);
            // 发送错误响应
            RpcMessage errorResponse = new RpcMessage();
            errorResponse.setRequestId(msg.getRequestId());
            errorResponse.setSerializationType(msg.getSerializationType());
            errorResponse.setCompressType(msg.getCompressType());
            errorResponse.setMessageType(RpcProtocol.REGISTRY_RESPONSE_TYPE);
            errorResponse.setStatus(RpcProtocol.STATUS_FAIL);
            errorResponse.setData("处理消息异常: " + e.getMessage());
            ctx.writeAndFlush(errorResponse);
        }
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                log.warn("长时间未收到客户端消息，关闭连接: {}", ctx.channel().remoteAddress());
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("注册中心处理器异常: {}", cause.getMessage(), cause);
        ctx.close();
    }
} 