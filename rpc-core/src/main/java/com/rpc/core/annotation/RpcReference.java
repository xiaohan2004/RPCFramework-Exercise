package com.rpc.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RPC引用注解，标记一个字段为RPC服务消费者
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcReference {
    /**
     * 服务版本号
     */
    String version() default "1.0.0";
    
    /**
     * 服务分组
     */
    String group() default "";
    
    /**
     * 超时时间，单位毫秒
     */
    long timeout() default 5000;
    
    /**
     * 重试次数
     */
    int retries() default 2;
    
    /**
     * 是否异步调用
     */
    boolean async() default false;
    
    /**
     * 是否启用本地服务调用
     */
    boolean enableLocalService() default false;
    
    /**
     * 调用条件。满足条件时调用远程服务，不满足时调用本地服务。
     * 支持以下格式：
     * 1. 空字符串：默认使用远程服务
     * 2. time0900-1800：在9:00-18:00之间使用远程服务，其他时间使用本地服务
     * 3. ip192.168.1.1：当客户端IP为192.168.1.1时使用远程服务
     * 4. 其他自定义格式，需要在ConditionEvaluator中实现相应的解析和评估逻辑
     */
    String condition() default "";
} 