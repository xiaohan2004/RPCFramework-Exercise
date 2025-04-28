package com.rpc.server.handler;

import com.rpc.core.protocol.RpcMessage;
import com.rpc.core.protocol.RpcProtocol;
import com.rpc.core.protocol.RpcRequest;
import com.rpc.core.protocol.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * RPC服务器网络处理器
 */
@Slf4j
public class RpcServerHandler extends SimpleChannelInboundHandler<RpcMessage> {
    private final RpcRequestHandler rpcRequestHandler;
    
    public RpcServerHandler(RpcRequestHandler rpcRequestHandler) {
        this.rpcRequestHandler = rpcRequestHandler;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) throws Exception {
        log.debug("服务器收到消息: {}", msg);
        
        byte messageType = msg.getMessageType();
        // 创建响应消息
        RpcMessage responseMsg = new RpcMessage();
        responseMsg.setRequestId(msg.getRequestId());
        
        // 处理心跳请求
        if (messageType == RpcProtocol.HEARTBEAT_REQUEST_TYPE) {
            responseMsg.setMessageType(RpcProtocol.HEARTBEAT_RESPONSE_TYPE);
            responseMsg.setData("PONG");
        } else if (messageType == RpcProtocol.REQUEST_TYPE) {
            // 处理RPC请求
            RpcRequest request = (RpcRequest) msg.getData();
            
            // 交给RpcRequestHandler处理请求
            RpcResponse<Object> response = rpcRequestHandler.handle(request);
            
            responseMsg.setMessageType(RpcProtocol.RESPONSE_TYPE);
            responseMsg.setData(response);
            
            // 设置响应状态
            if (response.getCode() == RpcResponse.SUCCESS_CODE) {
                responseMsg.setStatus(RpcProtocol.STATUS_SUCCESS);
            } else {
                responseMsg.setStatus(RpcProtocol.STATUS_FAIL);
            }
        }
        
        // 发送响应
        ctx.writeAndFlush(responseMsg);
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
        log.error("服务器处理请求时发生异常", cause);
        ctx.close();
    }
} 