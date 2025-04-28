package com.rpc.core.transport;

import com.rpc.common.model.ServiceInfo;
import com.rpc.core.protocol.RpcMessage;
import com.rpc.core.protocol.RpcProtocol;
import com.rpc.core.protocol.RpcRequest;
import com.rpc.core.protocol.RpcResponse;
import com.rpc.core.protocol.RegistryLookupRequest;
import com.rpc.core.protocol.RegistryLookupResponse;
import com.rpc.core.serialization.JacksonSerializer;
import com.rpc.core.serialization.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

/**
 * RPC消息解码器
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
public class RpcMessageDecoder extends LengthFieldBasedFrameDecoder {
    private static final Serializer serializer = new JacksonSerializer();
    
    /**
     * 构造函数
     * <p>
     * lengthFieldOffset: 数据长度字段偏移量，从第11个字节开始
     * lengthFieldLength: 数据长度字段长度，占4个字节
     * lengthAdjustment: 数据长度调整值，实际数据长度 = 数据长度字段值 + lengthAdjustment
     * initialBytesToStrip: 需要跳过的字节数，这里不跳过，需要整个消息
     * </p>
     */
    public RpcMessageDecoder() {
        super(Integer.MAX_VALUE, 11, 4, 0, 0);
    }
    
    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }
        
        // 首先记录完整的消息内容
        String completeMessage = byteBufToHexString(frame.duplicate().readerIndex(0), Math.min(frame.readableBytes(), 200));
        log.debug("收到完整消息内容: {}", completeMessage);
        
        try {
            // 检查魔数 - 修正为3字节
            byte[] magic = new byte[3];
            frame.readBytes(magic);
            
            // 接收到的魔数
            String receivedMagic = bytesToHexString(magic);
            // 期望的魔数
            String expectedMagic = bytesToHexString(RpcProtocol.MAGIC_NUMBER);
            
            if (!Arrays.equals(magic, RpcProtocol.MAGIC_NUMBER)) {
                log.error("魔数不匹配: {}, 期望: {}, 收到的值(十六进制): {}, 期望值(十六进制): {}", 
                    Arrays.toString(magic), Arrays.toString(RpcProtocol.MAGIC_NUMBER),
                    receivedMagic, expectedMagic);
                
                // 尝试特殊情况处理 - 如果接收到的是[5, 0, 0]
                if (magic[0] == 5 && magic[1] == 0 && magic[2] == 0) {
                    log.warn("检测到特殊魔数 [5, 0, 0]，尝试解析为注册请求...");
                    
                    // 如果是注册请求，尝试构建一个兼容的消息
                    RpcMessage rpcMessage = new RpcMessage();
                    rpcMessage.setMessageType(RpcProtocol.REGISTRY_REGISTER_TYPE);
                    rpcMessage.setStatus(RpcProtocol.STATUS_SUCCESS);
                    rpcMessage.setRequestId(0); // 默认请求ID
                    rpcMessage.setSerializationType(RpcProtocol.SERIALIZATION_JSON);
                    rpcMessage.setCompressType(RpcProtocol.COMPRESS_TYPE_NONE);
                    
                    // 重置读取索引，尝试读取整个消息内容
                    frame.readerIndex(0);
                    byte[] data = new byte[frame.readableBytes()];
                    frame.readBytes(data);
                    
                    // 尝试解析为JSON
                    try {
                        String jsonContent = new String(data, "UTF-8");
                        log.debug("尝试解析特殊魔数消息内容: {}", jsonContent);
                        
                        // 尝试解析为Map
                        Map<String, Object> map = serializer.deserialize(data, Map.class);
                        
                        // 如果包含serviceName字段，可能是ServiceInfo
                        if (map.containsKey("serviceName")) {
                            ServiceInfo serviceInfo = new ServiceInfo();
                            if (map.containsKey("serviceName")) serviceInfo.setServiceName((String)map.get("serviceName"));
                            if (map.containsKey("version")) serviceInfo.setVersion((String)map.get("version"));
                            if (map.containsKey("group")) serviceInfo.setGroup((String)map.get("group"));
                            if (map.containsKey("address")) serviceInfo.setAddress((String)map.get("address"));
                            if (map.containsKey("weight") && map.get("weight") instanceof Number) {
                                serviceInfo.setWeight(((Number)map.get("weight")).intValue());
                            }
                            
                            rpcMessage.setData(serviceInfo);
                            log.info("成功将特殊魔数消息解析为ServiceInfo: {}", serviceInfo);
                            return rpcMessage;
                        }
                    } catch (Exception e) {
                        log.error("尝试解析特殊魔数消息失败: {}", e.getMessage());
                    }
                }
                
                throw new IllegalArgumentException("魔数不匹配");
            }
            
            // 读取版本号
            byte version = frame.readByte();
            if (version != RpcProtocol.VERSION) {
                log.error("版本不支持: {}, 期望: {}", version, RpcProtocol.VERSION);
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
            
            // 记录消息头信息，便于调试
            log.debug("解码消息头 - 魔数: {}, 版本: {}, 类型: {}, 状态: {}, 请求ID: {}, 序列化: {}, 压缩: {}, 数据长度: {}",
                      receivedMagic, version, messageType, status, requestId, serializationType, compressType, dataLength);
            
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
                // 检查可读字节数是否足够
                if (frame.readableBytes() < dataLength) {
                    log.error("数据长度不足，期望: {}字节, 实际可读: {}字节", dataLength, frame.readableBytes());
                    rpcMessage.setStatus(RpcProtocol.STATUS_FAIL);
                    rpcMessage.setData("数据长度不足");
                    return rpcMessage;
                }
                
                byte[] data = new byte[dataLength];
                frame.readBytes(data);
                
                // 记录原始JSON字符串，用于调试
                String jsonString = null;
                try {
                    jsonString = new String(data, "UTF-8");
                } catch (Exception e) {
                    log.warn("无法将数据转换为字符串: {}", e.getMessage());
                }
                
                // 反序列化数据
                if (messageType == RpcProtocol.REQUEST_TYPE) {
                    try {
                        RpcRequest request = serializer.deserialize(data, RpcRequest.class);
                        rpcMessage.setData(request);
                    } catch (Exception e) {
                        log.error("反序列化RPC请求失败: {}, 原始JSON: {}", e.getMessage(), jsonString);
                        rpcMessage.setStatus(RpcProtocol.STATUS_FAIL);
                        rpcMessage.setData("反序列化RPC请求失败: " + e.getMessage());
                    }
                } else if (messageType == RpcProtocol.RESPONSE_TYPE) {
                    try {
                        RpcResponse response = serializer.deserialize(data, RpcResponse.class);
                        rpcMessage.setData(response);
                    } catch (Exception e) {
                        log.error("反序列化RPC响应失败: {}, 原始JSON: {}", e.getMessage(), jsonString);
                        rpcMessage.setStatus(RpcProtocol.STATUS_FAIL);
                        rpcMessage.setData("反序列化RPC响应失败: " + e.getMessage());
                    }
                } 
                // 添加对注册消息类型的处理
                else if (messageType == RpcProtocol.REGISTRY_REGISTER_TYPE) {
                    log.debug("收到服务注册请求，原始JSON: {}", jsonString);
                    try {
                        ServiceInfo serviceInfo = serializer.deserialize(data, ServiceInfo.class);
                        rpcMessage.setData(serviceInfo);
                        log.debug("解码注册服务请求成功: {}", serviceInfo);
                    } catch (Exception e) {
                        log.error("反序列化ServiceInfo失败: {}, 原始JSON: {}", e.getMessage(), jsonString);
                        // 尝试使用Map方式反序列化
                        try {
                            Map<String, Object> map = serializer.deserialize(data, Map.class);
                            log.debug("使用Map反序列化成功: {}", map);
                            
                            // 手动创建ServiceInfo对象
                            ServiceInfo serviceInfo = new ServiceInfo();
                            if (map.containsKey("serviceName")) serviceInfo.setServiceName((String)map.get("serviceName"));
                            if (map.containsKey("version")) serviceInfo.setVersion((String)map.get("version"));
                            if (map.containsKey("group")) serviceInfo.setGroup((String)map.get("group"));
                            if (map.containsKey("address")) serviceInfo.setAddress((String)map.get("address"));
                            if (map.containsKey("weight") && map.get("weight") instanceof Number) {
                                serviceInfo.setWeight(((Number)map.get("weight")).intValue());
                            }
                            
                            rpcMessage.setData(serviceInfo);
                            log.debug("通过Map方式创建ServiceInfo成功: {}", serviceInfo);
                        } catch (Exception ex) {
                            log.error("反序列化为Map也失败: {}", ex.getMessage());
                            rpcMessage.setStatus(RpcProtocol.STATUS_FAIL);
                            rpcMessage.setData("反序列化ServiceInfo失败: " + e.getMessage());
                        }
                    }
                } 
                else if (messageType == RpcProtocol.REGISTRY_UNREGISTER_TYPE) {
                    log.debug("收到服务注销请求，原始JSON: {}", jsonString);
                    try {
                        ServiceInfo serviceInfo = serializer.deserialize(data, ServiceInfo.class);
                        rpcMessage.setData(serviceInfo);
                        log.debug("解码注销服务请求成功: {}", serviceInfo);
                    } catch (Exception e) {
                        log.error("反序列化注销ServiceInfo失败: {}, 原始JSON: {}", e.getMessage(), jsonString);
                        // 尝试使用Map方式反序列化
                        try {
                            Map<String, Object> map = serializer.deserialize(data, Map.class);
                            
                            // 手动创建ServiceInfo对象
                            ServiceInfo serviceInfo = new ServiceInfo();
                            if (map.containsKey("serviceName")) serviceInfo.setServiceName((String)map.get("serviceName"));
                            if (map.containsKey("version")) serviceInfo.setVersion((String)map.get("version"));
                            if (map.containsKey("group")) serviceInfo.setGroup((String)map.get("group"));
                            if (map.containsKey("address")) serviceInfo.setAddress((String)map.get("address"));
                            if (map.containsKey("weight") && map.get("weight") instanceof Number) {
                                serviceInfo.setWeight(((Number)map.get("weight")).intValue());
                            }
                            
                            rpcMessage.setData(serviceInfo);
                            log.debug("通过Map方式创建ServiceInfo成功: {}", serviceInfo);
                        } catch (Exception ex) {
                            log.error("反序列化为Map也失败: {}", ex.getMessage());
                            rpcMessage.setStatus(RpcProtocol.STATUS_FAIL);
                            rpcMessage.setData("反序列化ServiceInfo失败: " + e.getMessage());
                        }
                    }
                }
                else if (messageType == RpcProtocol.REGISTRY_LOOKUP_TYPE) {
                    log.debug("收到服务查询请求，原始JSON: {}", jsonString);
                    try {
                        RegistryLookupRequest request = serializer.deserialize(data, RegistryLookupRequest.class);
                        rpcMessage.setData(request);
                        log.debug("解码查询服务请求成功: {}", request);
                    } catch (Exception e) {
                        log.error("反序列化RegistryLookupRequest失败: {}, 原始JSON: {}", e.getMessage(), jsonString);
                        try {
                            Map<String, Object> map = serializer.deserialize(data, Map.class);
                            
                            // 手动创建RegistryLookupRequest对象
                            RegistryLookupRequest request = new RegistryLookupRequest();
                            if (map.containsKey("serviceName")) request.setServiceName((String)map.get("serviceName"));
                            if (map.containsKey("version")) request.setVersion((String)map.get("version"));
                            if (map.containsKey("group")) request.setGroup((String)map.get("group"));
                            
                            rpcMessage.setData(request);
                            log.debug("通过Map方式创建RegistryLookupRequest成功: {}", request);
                        } catch (Exception ex) {
                            log.error("反序列化为Map也失败: {}", ex.getMessage());
                            rpcMessage.setStatus(RpcProtocol.STATUS_FAIL);
                            rpcMessage.setData("反序列化RegistryLookupRequest失败: " + e.getMessage());
                        }
                    }
                }
                else if (messageType == RpcProtocol.REGISTRY_RESPONSE_TYPE) {
                    log.debug("收到注册中心响应，原始JSON: {}", jsonString);
                    // 尝试反序列化为字符串，如果失败则尝试其他类型
                    try {
                        String response = serializer.deserialize(data, String.class);
                        rpcMessage.setData(response);
                        log.debug("解码注册中心响应(字符串)成功: {}", response);
                    } catch (Exception e) {
                        log.debug("反序列化为字符串失败，尝试作为RegistryLookupResponse解析");
                        try {
                            RegistryLookupResponse response = serializer.deserialize(data, RegistryLookupResponse.class);
                            rpcMessage.setData(response);
                            log.debug("解码注册中心响应(查询结果)成功: {}", response);
                        } catch (Exception ex) {
                            log.error("无法解析注册中心响应数据: {}, 尝试作为原始字符串", ex.getMessage());
                            rpcMessage.setData(jsonString);
                        }
                    }
                }
                else {
                    log.warn("未知的消息类型: {}, 尝试直接反序列化为字符串", messageType);
                    try {
                        String data_str = new String(data, "UTF-8");
                        rpcMessage.setData(data_str);
                    } catch (Exception e) {
                        log.warn("无法将数据转换为字符串，保留原始字节数组");
                        rpcMessage.setData(data);
                    }
                }
            }
            
            log.debug("解码RPC消息: {}", rpcMessage);
            return rpcMessage;
        } catch (Exception e) {
            log.error("解码RPC消息时发生错误: {}, 消息内容: {}", e.getMessage(), completeMessage);
            // 不抛出异常，让Netty继续处理
            return null;
        } finally {
            frame.release();
        }
    }
    
    /**
     * 将ByteBuf转为16进制字符串，用于调试
     */
    private String byteBufToHexString(ByteBuf buf, int maxLength) {
        StringBuilder sb = new StringBuilder();
        int length = Math.min(buf.readableBytes(), maxLength);
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02x ", buf.getByte(i)));
        }
        return sb.toString();
    }
    
    /**
     * 将ByteBuf转为16进制字符串，用于调试
     */
    private String byteBufToHexString(ByteBuf buf) {
        return byteBufToHexString(buf, 100); // 默认最多显示100字节
    }
    
    /**
     * 将字节数组转为16进制字符串
     */
    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x ", b));
        }
        return sb.toString();
    }
} 