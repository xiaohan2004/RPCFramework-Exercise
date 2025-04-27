package com.rpc.core.transport;

import com.rpc.core.protocol.RpcMessage;
import com.rpc.core.protocol.RpcProtocol;
import com.rpc.core.protocol.RpcRequest;
import com.rpc.core.protocol.RpcResponse;
import com.rpc.core.serialization.JacksonSerializer;
import com.rpc.core.serialization.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * RPC消息解码器
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
public class RpcMessageDecoder extends LengthFieldBasedFrameDecoder {
    private static final Serializer serializer = new JacksonSerializer();
    
    /**
     * 构造函数
     * <p>
     * lengthFieldOffset: 数据长度字段偏移量，从第12个字节开始
     * lengthFieldLength: 数据长度字段长度，占4个字节
     * lengthAdjustment: 数据长度调整值，实际数据长度 = 数据长度字段值 + lengthAdjustment
     * initialBytesToStrip: 需要跳过的字节数，这里不跳过，需要整个消息
     * </p>
     */
    public RpcMessageDecoder() {
        super(Integer.MAX_VALUE, 12, 4, 0, 0);
    }
    
    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }
        
        try {
            // 检查魔数
            byte[] magic = new byte[4];
            frame.readBytes(magic);
            if (!Arrays.equals(magic, RpcProtocol.MAGIC_NUMBER)) {
                log.error("魔数不匹配: {}", Arrays.toString(magic));
                throw new IllegalArgumentException("魔数不匹配");
            }
            
            // 读取版本号
            byte version = frame.readByte();
            if (version != RpcProtocol.VERSION) {
                log.error("版本不支持: {}", version);
                throw new IllegalArgumentException("版本不支持");
            }
            
            // 读取消息类型
            byte messageType = frame.readByte();
            // 读取状态
            byte status = frame.readByte();
            // 读取请求ID
            long requestId = frame.readLong();
            // 读取序列化类型
            byte serializationType = frame.readByte();
            // 读取压缩类型
            byte compressType = frame.readByte();
            // 读取数据长度
            int dataLength = frame.readInt();
            
            // 构建RPC消息对象
            RpcMessage rpcMessage = new RpcMessage();
            rpcMessage.setMessageType(messageType);
            rpcMessage.setStatus(status);
            rpcMessage.setRequestId(requestId);
            rpcMessage.setSerializationType(serializationType);
            rpcMessage.setCompressType(compressType);
            
            // 处理心跳消息
            if (messageType == RpcProtocol.HEARTBEAT_REQUEST_TYPE) {
                rpcMessage.setData("PING");
                return rpcMessage;
            }
            
            if (messageType == RpcProtocol.HEARTBEAT_RESPONSE_TYPE) {
                rpcMessage.setData("PONG");
                return rpcMessage;
            }
            
            // 读取数据内容
            if (dataLength > 0) {
                byte[] data = new byte[dataLength];
                frame.readBytes(data);
                
                // 反序列化数据
                if (messageType == RpcProtocol.REQUEST_TYPE) {
                    RpcRequest request = serializer.deserialize(data, RpcRequest.class);
                    rpcMessage.setData(request);
                } else if (messageType == RpcProtocol.RESPONSE_TYPE) {
                    RpcResponse response = serializer.deserialize(data, RpcResponse.class);
                    rpcMessage.setData(response);
                }
            }
            
            log.debug("解码RPC消息: {}", rpcMessage);
            return rpcMessage;
        } catch (Exception e) {
            log.error("解码RPC消息时发生错误", e);
            throw e;
        } finally {
            frame.release();
        }
    }
} 