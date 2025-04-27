package com.rpc.core.transport;

import com.rpc.core.protocol.RpcMessage;
import com.rpc.core.protocol.RpcProtocol;
import com.rpc.core.serialization.JacksonSerializer;
import com.rpc.core.serialization.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

/**
 * RPC消息编码器
 * <pre>
 * 协议格式:
 * +---------------------------------------------------------------+
 * | 魔数 4B | 版本号 1B | 消息类型 1B | 状态 1B | 请求ID 8B       |
 * +---------------------------------------------------------------+
 * | 序列化类型 1B | 压缩类型 1B | 数据长度 4B | 数据内容 (变长)   |
 * +---------------------------------------------------------------+
 * </pre>
 */
@Slf4j
public class RpcMessageEncoder extends MessageToByteEncoder<RpcMessage> {
    private static final Serializer serializer = new JacksonSerializer();

    @Override
    protected void encode(ChannelHandlerContext ctx, RpcMessage msg, ByteBuf out) throws Exception {
        try {
            // 1. 写入魔数
            out.writeBytes(RpcProtocol.MAGIC_NUMBER);
            // 2. 写入版本号
            out.writeByte(RpcProtocol.VERSION);
            // 3. 写入消息类型
            out.writeByte(msg.getMessageType());
            // 4. 写入状态
            out.writeByte(msg.getStatus());
            // 5. 写入请求ID
            out.writeLong(msg.getRequestId());
            // 6. 写入序列化类型，默认使用JSON
            out.writeByte(RpcProtocol.SERIALIZATION_JSON);
            // 7. 写入压缩类型，默认不压缩
            out.writeByte(RpcProtocol.COMPRESS_TYPE_NONE);
            
            // 8. 序列化数据
            byte[] data = new byte[0];
            if (msg.getData() != null) {
                data = serializer.serialize(msg.getData());
            }
            
            // 9. 写入数据长度
            out.writeInt(data.length);
            // 10. 写入数据内容
            if (data.length > 0) {
                out.writeBytes(data);
            }
            
            log.debug("编码RPC消息: {}", msg);
        } catch (Exception e) {
            log.error("编码RPC消息时发生错误", e);
            throw e;
        }
    }
} 