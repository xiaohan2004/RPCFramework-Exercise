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
        responseMsg.setSerializationType(msg.getSerializationType());
        responseMsg.setCompressType(msg.getCompressType());
        
        if (messageType == RpcProtocol.REQUEST_TYPE) {
            // 处理RPC请求
            RpcRequest request = (RpcRequest) msg.getData();
            log.debug("处理RPC请求: service={}, method={}", request.getServiceName(), request.getMethodName());
            
            // 交给RpcRequestHandler处理请求
            RpcResponse<Object> response = rpcRequestHandler.handle(request);
            log.debug("RPC处理结果: code={}, message={}, data={}", 
                response.getCode(), response.getMessage(), response.getData());
            
            responseMsg.setMessageType(RpcProtocol.RESPONSE_TYPE);
            responseMsg.setData(response);
            
            // 确保响应码不为null
            if (response.getCode() == null) {
                log.warn("响应码为null，设置为失败状态");
                response.setCode(RpcResponse.FAIL_CODE);
                responseMsg.setStatus(RpcProtocol.STATUS_FAIL);
            } else {
                // 设置响应状态
                if (RpcResponse.SUCCESS_CODE.equals(response.getCode())) {
                    responseMsg.setStatus(RpcProtocol.STATUS_SUCCESS);
                } else {
                    responseMsg.setStatus(RpcProtocol.STATUS_FAIL);
                }
            }
            
            log.debug("发送RPC响应: requestId={}, status={}, code={}, message={}", 
                responseMsg.getRequestId(), responseMsg.getStatus(), 
                response.getCode(), response.getMessage());
            
            // 发送响应
            ctx.writeAndFlush(responseMsg);
        } else if (messageType == RpcProtocol.HEARTBEAT_REQUEST_TYPE) {
            // 处理心跳请求
            log.debug("收到心跳请求: {}", msg.getData());
            responseMsg.setMessageType(RpcProtocol.HEARTBEAT_RESPONSE_TYPE);
            responseMsg.setData("PONG");
            responseMsg.setStatus(RpcProtocol.STATUS_SUCCESS);
            ctx.writeAndFlush(responseMsg);
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("服务器处理请求时发生异常", cause);
        ctx.close();
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("客户端连接建立: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("客户端连接断开: {}", ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                log.warn("客户端 {} 长时间未发送数据，关闭连接", ctx.channel().remoteAddress());
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
} 