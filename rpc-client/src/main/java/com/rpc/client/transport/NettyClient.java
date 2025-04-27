package com.rpc.client.transport;

import com.rpc.client.handler.RpcResponseHandler;
import com.rpc.core.protocol.RpcMessage;
import com.rpc.core.protocol.RpcProtocol;
import com.rpc.core.protocol.RpcRequest;
import com.rpc.core.transport.RpcMessageDecoder;
import com.rpc.core.transport.RpcMessageEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Netty客户端，用于与RPC服务器通信
 */
@Slf4j
public class NettyClient {
    /**
     * 事件循环组
     */
    private final EventLoopGroup eventLoopGroup;
    
    /**
     * 引导器
     */
    private final Bootstrap bootstrap;
    
    /**
     * 远程地址
     */
    private final InetSocketAddress remotePeer;
    
    /**
     * 通道
     */
    private Channel channel;
    
    /**
     * 响应处理器
     */
    private final RpcResponseHandler responseHandler;
    
    /**
     * 请求ID生成器
     */
    private final AtomicLong requestIdGenerator = new AtomicLong(0);
    
    /**
     * 构造函数
     *
     * @param host 远程主机
     * @param port 远程端口
     */
    public NettyClient(String host, int port) {
        this.remotePeer = new InetSocketAddress(host, port);
        this.responseHandler = new RpcResponseHandler();
        
        // 初始化Netty客户端
        this.eventLoopGroup = new NioEventLoopGroup(4);
        this.bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                // 空闲检测处理器，15秒没有写操作就发送心跳
                                .addLast(new IdleStateHandler(0, 15, 0, TimeUnit.SECONDS))
                                // 消息编解码
                                .addLast(new RpcMessageEncoder())
                                .addLast(new RpcMessageDecoder())
                                // 响应处理器
                                .addLast(responseHandler);
                    }
                });
    }
    
    /**
     * 连接服务器
     */
    public CompletableFuture<Channel> connect() {
        CompletableFuture<Channel> future = new CompletableFuture<>();
        if (channel != null && channel.isActive()) {
            future.complete(channel);
            return future;
        }
        
        try {
            ChannelFuture channelFuture = bootstrap.connect(remotePeer);
            channelFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture cf) throws Exception {
                    if (cf.isSuccess()) {
                        log.info("连接到RPC服务器：{}", remotePeer);
                        channel = cf.channel();
                        future.complete(channel);
                    } else {
                        log.error("连接RPC服务器失败：{}", remotePeer);
                        future.completeExceptionally(cf.cause());
                    }
                }
            });
        } catch (Exception e) {
            log.error("连接RPC服务器时发生异常：{}", remotePeer, e);
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * 发送RPC请求
     *
     * @param request RPC请求
     * @param timeout 超时时间（毫秒）
     * @return 请求的Future
     */
    public RpcFuture sendRequest(RpcRequest request, long timeout) {
        long requestId = requestIdGenerator.incrementAndGet();
        
        RpcMessage rpcMessage = new RpcMessage();
        rpcMessage.setMessageType(RpcProtocol.REQUEST_TYPE);
        rpcMessage.setSerializationType(RpcProtocol.SERIALIZATION_JSON);
        rpcMessage.setCompressType(RpcProtocol.COMPRESS_TYPE_NONE);
        rpcMessage.setRequestId(requestId);
        rpcMessage.setData(request);
        
        RpcFuture future = new RpcFuture(requestId, timeout);
        // 添加到未完成请求映射
        responseHandler.addPendingRequest(requestId, future);
        
        try {
            // 发送请求
            channel.writeAndFlush(rpcMessage).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture cf) throws Exception {
                    if (!cf.isSuccess()) {
                        // 发送失败，完成异常
                        responseHandler.removePendingRequest(requestId);
                        future.completeExceptionally(cf.cause());
                        log.error("发送RPC请求失败", cf.cause());
                    }
                }
            });
        } catch (Exception e) {
            // 发送出现异常，完成异常
            responseHandler.removePendingRequest(requestId);
            future.completeExceptionally(e);
            log.error("发送RPC请求时发生异常", e);
        }
        
        return future;
    }
    
    /**
     * 关闭客户端
     */
    public void close() {
        if (channel != null) {
            channel.close();
        }
        eventLoopGroup.shutdownGracefully();
        log.info("RPC客户端已关闭");
    }
    
    /**
     * 客户端是否活跃
     */
    public boolean isActive() {
        return channel != null && channel.isActive();
    }
} 