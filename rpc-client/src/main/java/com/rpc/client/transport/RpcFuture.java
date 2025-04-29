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
    
    /**
     * 完成Future
     */
    private final CompletableFuture<RpcResponse> future;
    
    public RpcFuture(long requestId, long timeout) {
        this.requestId = requestId;
        this.startTime = System.currentTimeMillis();
        this.timeout = timeout;
        this.future = new CompletableFuture<>();
    }
    
    /**
     * 完成
     *
     * @param response RPC响应
     * @return 是否成功完成
     */
    public boolean complete(RpcResponse response) {
        return future.complete(response);
    }
    
    /**
     * 异常完成
     *
     * @param throwable 异常
     * @return 是否成功完成
     */
    public boolean completeExceptionally(Throwable throwable) {
        log.error("RPC请求异常完成: requestId={}, 异常: {}", requestId, throwable.getMessage());
        return future.completeExceptionally(throwable);
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
        
        log.debug("收到RPC响应: code={}, message={}, data={}", response.getCode(), response.getMessage(), response.getData());
        
        if (response.getCode() == null) {
            log.error("RPC响应码为null，默认视为成功");
            response.setCode(RpcResponse.SUCCESS_CODE);
            return response;
        }
        
        if (!RpcResponse.SUCCESS_CODE.equals(response.getCode())) {
            log.error("RPC调用失败: code={}, message={}", response.getCode(), response.getMessage());
            // 包含特定错误信息，以便RpcClientProxy可以识别
            String errorMessage = response.getMessage();
            if (errorMessage != null && (errorMessage.contains("服务不存在") || errorMessage.contains("未找到服务"))) {
                throw new RuntimeException("未找到服务提供者: " + errorMessage);
            } else {
                throw new RuntimeException("RPC调用失败: " + response.getMessage());
            }
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
        RpcResponse response = future.get();
        return processResponse(response);
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
        RpcResponse response = future.get(timeout, unit);
        return processResponse(response);
    }
    
    /**
     * 处理响应
     *
     * @param response RPC响应
     * @return 响应数据
     */
    private Object processResponse(RpcResponse response) {
        if (response == null) {
            log.error("RPC响应为null");
            throw new RuntimeException("RPC响应为null");
        }
        
        // 检查响应码
        if (response.getCode() == null) {
            log.error("RPC响应码为null，默认视为成功");
            response.setCode(RpcResponse.SUCCESS_CODE);
        } else if (!RpcResponse.SUCCESS_CODE.equals(response.getCode())) {
            log.error("RPC调用失败: code={}, message={}", response.getCode(), response.getMessage());
            // 包含特定错误信息，以便RpcClientProxy可以识别
            String errorMessage = response.getMessage();
            if (errorMessage != null && (errorMessage.contains("服务不存在") || errorMessage.contains("未找到服务"))) {
                throw new RuntimeException("未找到服务提供者: " + errorMessage);
            } else {
                throw new RuntimeException("RPC调用失败: " + response.getMessage());
            }
        }
        
        // 返回数据
        return response.getData();
    }
    
    /**
     * 是否已超时
     */
    public boolean isTimeout() {
        return System.currentTimeMillis() - startTime > timeout;
    }
    
    /**
     * 是否已完成
     *
     * @return 是否已完成
     */
    public boolean isDone() {
        return future.isDone();
    }
    
    /**
     * 获取已耗时（毫秒）
     *
     * @return 已耗时
     */
    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * 获取超时时间
     *
     * @return 超时时间
     */
    public long getTimeout() {
        return timeout;
    }
    
    /**
     * 取消
     *
     * @param mayInterruptIfRunning 是否中断正在运行的任务
     * @return 是否成功取消
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }
    
    /**
     * 是否已取消
     *
     * @return 是否已取消
     */
    public boolean isCancelled() {
        return future.isCancelled();
    }
} 