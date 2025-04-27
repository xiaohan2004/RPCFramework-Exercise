package com.rpc.client.transport;

import com.rpc.core.protocol.RpcResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * RPC异步请求结果Future
 */
@Slf4j
public class RpcFuture extends CompletableFuture<RpcResponse> {
    /**
     * 请求ID
     */
    private final long requestId;
    
    /**
     * 请求发送时间
     */
    private final long startTime;
    
    /**
     * 超时时间，单位毫秒
     */
    private final long timeout;
    
    public RpcFuture(long requestId, long timeout) {
        this.requestId = requestId;
        this.startTime = System.currentTimeMillis();
        this.timeout = timeout;
    }
    
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        log.warn("RPC请求不支持取消操作");
        return false;
    }
    
    /**
     * 获取请求ID
     */
    public long getRequestId() {
        return requestId;
    }
    
    /**
     * 获取RPC调用结果
     *
     * @return 调用结果
     * @throws InterruptedException 如果线程被中断
     * @throws ExecutionException 如果执行出现异常
     */
    @Override
    public RpcResponse get() throws InterruptedException, ExecutionException {
        try {
            return this.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new ExecutionException("RPC调用超时", e);
        }
    }
    
    /**
     * 获取RPC调用结果，带超时时间
     *
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 调用结果
     * @throws InterruptedException 如果线程被中断
     * @throws ExecutionException 如果执行出现异常
     * @throws TimeoutException 如果调用超时
     */
    @Override
    public RpcResponse get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        RpcResponse response = super.get(timeout, unit);
        if (response == null) {
            throw new RuntimeException("RPC调用响应为空");
        }
        
        if (response.getCode() != RpcResponse.SUCCESS_CODE) {
            throw new RuntimeException("RPC调用失败: " + response.getMessage());
        }
        
        return response;
    }
    
    /**
     * 获取RPC调用结果数据
     *
     * @return 调用结果数据
     * @throws InterruptedException 如果线程被中断
     * @throws ExecutionException 如果执行出现异常
     */
    public Object getData() throws InterruptedException, ExecutionException {
        try {
            RpcResponse response = this.get(timeout, TimeUnit.MILLISECONDS);
            return response.getData();
        } catch (TimeoutException e) {
            throw new ExecutionException("RPC调用超时", e);
        }
    }
    
    /**
     * 获取RPC调用结果数据，带超时时间
     *
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 调用结果数据
     * @throws InterruptedException 如果线程被中断
     * @throws ExecutionException 如果执行出现异常
     * @throws TimeoutException 如果调用超时
     */
    public Object getData(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        RpcResponse response = this.get(timeout, unit);
        return response.getData();
    }
    
    /**
     * 是否已超时
     */
    public boolean isTimeout() {
        return System.currentTimeMillis() - startTime > timeout;
    }
} 