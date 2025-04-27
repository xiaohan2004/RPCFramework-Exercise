package com.rpc.server.handler;

import com.rpc.core.protocol.RpcRequest;
import com.rpc.core.protocol.RpcResponse;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPC请求处理器
 */
@Slf4j
public class RpcRequestHandler {
    /**
     * 服务实例缓存，key为服务名称，value为服务实例
     */
    private final Map<String, Object> serviceMap = new ConcurrentHashMap<>();
    
    /**
     * 注册服务
     *
     * @param serviceKey 服务键
     * @param serviceBean 服务实例
     */
    public void registerService(String serviceKey, Object serviceBean) {
        log.info("注册服务：serviceKey={}, beanClass={}", serviceKey, serviceBean.getClass().getName());
        serviceMap.put(serviceKey, serviceBean);
    }
    
    /**
     * 查找服务实例
     *
     * @param serviceKey 服务键
     * @return 服务实例
     */
    public Object getServiceBean(String serviceKey) {
        return serviceMap.get(serviceKey);
    }
    
    /**
     * 处理RPC请求
     *
     * @param rpcRequest RPC请求
     * @return RPC响应
     */
    public RpcResponse<Object> handle(RpcRequest rpcRequest) {
        String serviceKey = rpcRequest.getRpcServiceKey();
        Object serviceBean = serviceMap.get(serviceKey);
        
        if (serviceBean == null) {
            log.error("未找到服务: {}", serviceKey);
            return RpcResponse.fail("未找到服务: " + serviceKey);
        }
        
        try {
            // 获取方法和参数
            String methodName = rpcRequest.getMethodName();
            Class<?>[] parameterTypes = rpcRequest.getParameterTypes();
            Object[] parameters = rpcRequest.getParameters();
            
            log.debug("调用服务: {}, 方法: {}", serviceKey, methodName);
            
            // 反射调用方法
            Method method = serviceBean.getClass().getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            Object result = method.invoke(serviceBean, parameters);
            
            log.debug("服务调用成功: {}, 方法: {}, 结果: {}", serviceKey, methodName, result);
            
            return RpcResponse.success(result);
        } catch (NoSuchMethodException e) {
            log.error("未找到方法: {}.{}", serviceKey, rpcRequest.getMethodName());
            return RpcResponse.fail("未找到方法: " + rpcRequest.getMethodName());
        } catch (Exception e) {
            log.error("调用服务时发生异常: {}", serviceKey, e);
            return RpcResponse.fail(e);
        }
    }
} 