package com.rpc.core.transport;

import com.rpc.core.protocol.RpcMessage;
import com.rpc.core.serialization.JsonUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

/**
 * 简化的JSON编码器，直接将RpcMessage转为JSON字符串
 */
@Slf4j
public class SimpleJsonEncoder extends MessageToByteEncoder<RpcMessage> {
    
    @Override
    protected void encode(ChannelHandlerContext ctx, RpcMessage msg, ByteBuf out) throws Exception {
        try {
            log.debug("开始编码消息: 类型={}, 请求ID={}", msg.getMessageType(), msg.getRequestId());
            
            // 使用JsonUtils进行序列化
            byte[] jsonBytes = JsonUtils.toJsonBytes(msg);
            
            // 先写入长度
            out.writeInt(jsonBytes.length);
            
            // 再写入JSON内容
            out.writeBytes(jsonBytes);
            
            log.debug("消息编码完成，长度: {}字节", jsonBytes.length);
        } catch (Exception e) {
            log.error("消息编码失败: {}", e.getMessage(), e);
            throw e;
        }
    }
} 