package com.rpc.registry;

import com.rpc.core.protocol.RpcMessage;
import com.rpc.core.protocol.RpcProtocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * 注册中心客户端处理器
 */
@Slf4j
public class RegistryClientHandler extends SimpleChannelInboundHandler<RpcMessage> {
    
    private final RemoteServiceRegistry registry;
    
    public RegistryClientHandler(RemoteServiceRegistry registry) {
        this.registry = registry;
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("连接到注册中心服务器: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("与注册中心服务器断开连接: {}", ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) throws Exception {
        log.debug("收到注册中心响应: {}, 消息类型: {}, 请求ID: {}", msg, msg.getMessageType(), msg.getRequestId());
        
        try {
            byte messageType = msg.getMessageType();
            long requestId = msg.getRequestId();
            
            // 处理心跳响应
            if (messageType == RpcProtocol.HEARTBEAT_RESPONSE_TYPE) {
                log.debug("收到心跳响应: {}", msg.getData());
                return;
            }
            
            // 处理注册中心响应
            if (messageType == RpcProtocol.REGISTRY_RESPONSE_TYPE) {
                byte status = msg.getStatus();
                if (status == RpcProtocol.STATUS_SUCCESS) {
                    log.debug("注册中心请求成功: {}, 响应数据: {}", requestId, msg.getData());
                    registry.handleResponse(requestId, msg.getData());
                } else {
                    log.warn("注册中心请求失败: {}, 错误信息: {}", requestId, msg.getData());
                    registry.handleException(requestId, new RuntimeException("请求失败: " + msg.getData()));
                }
                return;
            }
            
            log.warn("收到未知类型的消息: {}", messageType);
        } catch (Exception e) {
            log.error("处理注册中心响应时发生异常: {}", e.getMessage(), e);
            // 注意：不要抛出异常，以防止连接关闭
        }
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.WRITER_IDLE) {
                log.debug("触发写空闲事件，发送心跳");
                // 由RemoteServiceRegistry的心跳线程处理，这里不需要额外操作
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("注册中心客户端处理器异常: {}", cause.getMessage(), cause);
        // 不要关闭连接，让RemoteServiceRegistry自己处理重连
    }
} 