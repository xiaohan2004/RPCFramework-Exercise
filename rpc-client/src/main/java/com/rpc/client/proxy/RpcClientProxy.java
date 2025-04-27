package com.rpc.client.proxy;

import com.rpc.client.RpcClient;
import com.rpc.client.transport.RpcFuture;
import com.rpc.core.annotation.RpcReference;
import com.rpc.core.protocol.RpcRequest;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * RPC客户端代理，用于创建远程服务代理对象
 */
@Slf4j
public class RpcClientProxy implements InvocationHandler {
    /**
     * RPC客户端
     */
    private final RpcClient rpcClient;
    
    /**
     * 服务版本
     */
    private String version;
    
    /**
     * 服务分组
     */
    private String group;
    
    /**
     * 超时时间（毫秒）
     */
    private long timeout = 5000;
    
    /**
     * 重试次数
     */
    private int retries = 2;
    
    /**
     * 是否异步调用
     */
    private boolean async = false;
    
    /**
     * 构造函数
     *
     * @param rpcClient RPC客户端
     */
    public RpcClientProxy(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }
    
    /**
     * 创建接口代理
     *
     * @param interfaceClass 接口类
     * @param <T> 接口类型
     * @return 代理对象
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                this);
    }
    
    /**
     * 创建接口代理
     *
     * @param interfaceClass 接口类
     * @param version 版本号
     * @param <T> 接口类型
     * @return 代理对象
     */
    public <T> T getProxy(Class<T> interfaceClass, String version, String group) {
        this.version = version;
        this.group = group;
        return getProxy(interfaceClass);
    }
    
    /**
     * 创建接口代理，带RpcReference注解配置
     *
     * @param interfaceClass 接口类
     * @param reference RPC引用注解
     * @param <T> 接口类型
     * @return 代理对象
     */
    public <T> T getProxy(Class<T> interfaceClass, RpcReference reference) {
        this.version = reference.version();
        this.group = reference.group();
        this.timeout = reference.timeout();
        this.retries = reference.retries();
        this.async = reference.async();
        return getProxy(interfaceClass);
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Object类的方法直接调用
        if (Object.class.equals(method.getDeclaringClass())) {
            return method.invoke(this, args);
        }
        
        // 构建RPC请求
        RpcRequest request = createRequest(method, args);
        
        // 发送RPC请求
        RpcFuture future = null;
        Exception lastException = null;
        
        // 重试机制
        for (int i = 0; i <= retries; i++) {
            try {
                future = rpcClient.sendRequest(request, timeout);
                break;
            } catch (Exception e) {
                lastException = e;
                log.error("RPC调用失败，正在重试 {}/{}", i + 1, retries, e);
                if (i == retries) {
                    throw e;
                }
            }
        }
        
        if (future == null) {
            throw lastException != null ? lastException : new RuntimeException("RPC调用失败");
        }
        
        // 如果是异步调用，直接返回Future
        if (async) {
            return convertToReturnType(future, method.getReturnType());
        }
        
        // 同步调用，等待结果
        try {
            return future.getData(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("RPC调用被中断", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("RPC调用执行异常", e.getCause());
        } catch (TimeoutException e) {
            throw new RuntimeException("RPC调用超时", e);
        }
    }
    
    /**
     * 创建RPC请求
     *
     * @param method 方法
     * @param args 参数
     * @return RPC请求
     */
    private RpcRequest createRequest(Method method, Object[] args) {
        RpcRequest request = new RpcRequest();
        request.setServiceName(method.getDeclaringClass().getName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setParameters(args);
        request.setVersion(version);
        request.setGroup(group);
        return request;
    }
    
    /**
     * 将RpcFuture转换为方法返回类型
     *
     * @param future RPC Future
     * @param returnType 返回类型
     * @return 转换后的对象
     */
    private Object convertToReturnType(RpcFuture future, Class<?> returnType) {
        if (returnType == CompletableFuture.class) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return future.getData(timeout, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        
        // 其他异步返回类型可以在这里添加
        
        // 默认返回RpcFuture
        return future;
    }
} 