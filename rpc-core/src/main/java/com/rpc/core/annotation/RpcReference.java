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
} 