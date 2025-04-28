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
 * | 魔数 3B | 版本号 1B | 消息类型 1B | 状态 1B | 请求ID 8B       |
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
            byte[] magicNumber = RpcProtocol.MAGIC_NUMBER;
            out.writeBytes(magicNumber);
            
            // 记录编码前的消息信息
            StringBuilder debugInfo = new StringBuilder();
            debugInfo.append("编码消息 - 魔数: ");
            for (byte b : magicNumber) {
                debugInfo.append(String.format("%02x ", b));
            }
            
            // 2. 写入版本号
            byte version = RpcProtocol.VERSION;
            out.writeByte(version);
            debugInfo.append("版本: ").append(version).append(", ");
            
            // 3. 写入消息类型
            byte messageType = msg.getMessageType();
            out.writeByte(messageType);
            debugInfo.append("类型: ").append(messageType).append(", ");
            
            // 4. 写入状态
            byte status = msg.getStatus();
            out.writeByte(status);
            debugInfo.append("状态: ").append(status).append(", ");
            
            // 5. 写入请求ID
            long requestId = msg.getRequestId();
            out.writeLong(requestId);
            debugInfo.append("请求ID: ").append(requestId).append(", ");
            
            // 6. 写入序列化类型，默认使用JSON
            byte serializationType = RpcProtocol.SERIALIZATION_JSON;
            out.writeByte(serializationType);
            debugInfo.append("序列化: ").append(serializationType).append(", ");
            
            // 7. 写入压缩类型，默认不压缩
            byte compressType = RpcProtocol.COMPRESS_TYPE_NONE;
            out.writeByte(compressType);
            debugInfo.append("压缩: ").append(compressType).append(", ");
            
            // 8. 序列化数据
            byte[] data = new byte[0];
            if (msg.getData() != null) {
                data = serializer.serialize(msg.getData());
            }
            
            // 9. 写入数据长度
            int dataLength = data.length;
            out.writeInt(dataLength);
            debugInfo.append("数据长度: ").append(dataLength);
            
            // 10. 写入数据内容
            if (dataLength > 0) {
                out.writeBytes(data);
                // 添加数据内容部分的日志（太长则截断）
                if (dataLength <= 200) {
                    debugInfo.append(", 数据内容: ");
                    for (int i = 0; i < Math.min(50, dataLength); i++) {
                        debugInfo.append(String.format("%02x ", data[i]));
                    }
                    if (dataLength > 50) {
                        debugInfo.append("...(截断)");
                    }
                }
            }
            
            log.debug(debugInfo.toString());
            log.debug("编码RPC消息完成: {}", msg);
        } catch (Exception e) {
            log.error("编码RPC消息时发生错误: {}", e.getMessage(), e);
            throw e;
        }
    }
} 