package com.rpc.client.handler;

import com.rpc.client.transport.RpcFuture;
import com.rpc.core.protocol.RpcMessage;
import com.rpc.core.protocol.RpcProtocol;
import com.rpc.core.protocol.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPC响应处理器
 */
@Slf4j
public class RpcResponseHandler extends SimpleChannelInboundHandler<RpcMessage> {
    /**
     * 未完成的请求映射，key为请求ID，value为请求Future
     */
    private final Map<Long, RpcFuture> pendingRequests = new ConcurrentHashMap<>();

    /**
     * 添加请求Future
     *
     * @param requestId 请求ID
     * @param future 请求Future
     */
    public void addPendingRequest(long requestId, RpcFuture future) {
        pendingRequests.put(requestId, future);
    }

    /**
     * 移除请求Future
     *
     * @param requestId 请求ID
     */
    public void removePendingRequest(long requestId) {
        pendingRequests.remove(requestId);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) throws Exception {
        log.debug("客户端收到消息: {}", msg);
        
        byte messageType = msg.getMessageType();
        // 处理心跳响应
        if (messageType == RpcProtocol.HEARTBEAT_RESPONSE_TYPE) {
            log.debug("收到心跳响应: {}", msg.getData());
            return;
        }
        
        // 处理RPC响应
        if (messageType == RpcProtocol.RESPONSE_TYPE) {
            long requestId = msg.getRequestId();
            RpcFuture future = pendingRequests.remove(requestId);
            
            if (future != null) {
                RpcResponse response = (RpcResponse) msg.getData();
                if (msg.getStatus() == RpcProtocol.STATUS_SUCCESS) {
                    future.complete(response);
                } else {
                    future.completeExceptionally(new RuntimeException(response.getMessage()));
                }
            } else {
                log.warn("收到未知请求ID的响应: {}", requestId);
            }
        }
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.WRITER_IDLE) {
                // 发送心跳
                log.debug("发送心跳");
                RpcMessage heartbeat = new RpcMessage();
                heartbeat.setMessageType(RpcProtocol.HEARTBEAT_REQUEST_TYPE);
                heartbeat.setData("PING");
                ctx.writeAndFlush(heartbeat);
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("RPC客户端异常", cause);
        // 处理所有未完成的请求
        pendingRequests.forEach((requestId, future) -> {
            future.completeExceptionally(cause);
            pendingRequests.remove(requestId);
        });
        ctx.close();
    }
} 