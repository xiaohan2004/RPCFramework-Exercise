package com.rpc.core.transport;

import com.rpc.core.protocol.RpcMessage;
import com.rpc.core.serialization.JsonUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

/**
 * 简化的JSON解码器，直接将JSON字符串转为RpcMessage
 */
@Slf4j
public class SimpleJsonDecoder extends LengthFieldBasedFrameDecoder {
    
    /**
     * 构造函数
     * 
     * lengthFieldOffset: 长度字段的偏移量，从第0个字节开始
     * lengthFieldLength: 长度字段的长度，占4个字节
     * lengthAdjustment: 长度调整值，实际内容长度 = 长度字段的值 + lengthAdjustment
     * initialBytesToStrip: 需要跳过的字节数，这里设为4表示跳过长度字段
     */
    public SimpleJsonDecoder() {
        super(Integer.MAX_VALUE, 0, 4, 0, 4);
    }
    
    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }
        
        try {
            // 读取JSON字节
            byte[] jsonBytes = new byte[frame.readableBytes()];
            frame.readBytes(jsonBytes);
            
            // 使用JsonUtils进行反序列化
            RpcMessage rpcMessage = JsonUtils.fromJson(jsonBytes, RpcMessage.class);
            
            log.debug("消息解码完成: 类型={}, 请求ID={}", rpcMessage.getMessageType(), rpcMessage.getRequestId());
            
            return rpcMessage;
        } catch (Exception e) {
            log.error("消息解码失败: {}", e.getMessage(), e);
            throw e;
        } finally {
            // 释放ByteBuf
            frame.release();
        }
    }
} 