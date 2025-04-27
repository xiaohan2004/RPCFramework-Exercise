package com.rpc.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RPC服务注解，标记一个类为RPC服务提供者
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcService {
    /**
     * 服务接口类型，默认为空，表示取实现类的第一个接口
     */
    Class<?> value() default void.class;
    
    /**
     * 服务版本号
     */
    String version() default "1.0.0";
    
    /**
     * 服务分组
     */
    String group() default "";
} 