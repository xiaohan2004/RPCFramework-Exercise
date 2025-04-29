package com.rpc.client.processor;

import com.rpc.client.RpcClient;
import com.rpc.client.proxy.RpcClientProxy;
import com.rpc.core.annotation.RpcReference;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * RPC引用处理器，用于处理带有@RpcReference注解的字段
 */
@Slf4j
public class RpcReferenceProcessor {
    /**
     * RPC客户端
     */
    private final RpcClient rpcClient;
    
    /**
     * RPC客户端代理
     */
    private final RpcClientProxy rpcClientProxy;
    
    /**
     * 构造函数
     * 
     * @param rpcClient RPC客户端
     */
    public RpcReferenceProcessor(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
        this.rpcClientProxy = new RpcClientProxy(rpcClient);
    }
    
    /**
     * 处理对象中带有@RpcReference注解的字段
     * 
     * @param bean 目标对象
     * @throws IllegalAccessException 访问异常
     */
    public void processBean(Object bean) throws IllegalAccessException {
        if (bean == null) {
            return;
        }
        
        // 获取实际的类
        Class<?> targetClass;
        if (bean instanceof Class) {
            // 如果传入的是Class对象，表示要处理静态字段
            targetClass = (Class<?>) bean;
            processClassStaticFields(targetClass);
        } else {
            // 如果传入的是实例对象，处理实例字段
            targetClass = bean.getClass();
            processInstanceFields(bean, targetClass);
        }
    }
    
    /**
     * 处理类的静态字段
     * 
     * @param targetClass 目标类
     * @throws IllegalAccessException 访问异常
     */
    private void processClassStaticFields(Class<?> targetClass) throws IllegalAccessException {
        // 获取类的所有声明字段
        Field[] fields = targetClass.getDeclaredFields();
        
        for (Field field : fields) {
            // 只处理静态字段
            if (Modifier.isStatic(field.getModifiers())) {
                processField(null, field);
            }
        }
    }
    
    /**
     * 处理实例的非静态字段
     * 
     * @param instance 目标实例
     * @param targetClass 目标类
     * @throws IllegalAccessException 访问异常
     */
    private void processInstanceFields(Object instance, Class<?> targetClass) throws IllegalAccessException {
        // 获取类的所有声明字段
        Field[] fields = targetClass.getDeclaredFields();
        
        for (Field field : fields) {
            // 排除静态字段
            if (!Modifier.isStatic(field.getModifiers())) {
                processField(instance, field);
            }
        }
    }
    
    /**
     * 处理单个字段
     * 
     * @param instance 目标实例，如果处理静态字段则为null
     * @param field 字段
     * @throws IllegalAccessException 访问异常
     */
    private void processField(Object instance, Field field) throws IllegalAccessException {
        // 检查字段是否带有@RpcReference注解
        RpcReference reference = field.getAnnotation(RpcReference.class);
        if (reference != null) {
            // 添加详细日志，记录注解的所有相关属性
            log.info("处理RPC引用注解: 字段={}, 服务={}, 版本={}, 分组={}, 启用本地服务={}, 条件='{}'", 
                    field.getName(), 
                    field.getType().getName(),
                    reference.version(), 
                    reference.group(), 
                    reference.enableLocalService(),
                    reference.condition());
            
            // 获取字段类型作为服务接口
            Class<?> interfaceClass = field.getType();
            
            // 直接调试输出条件值
            String conditionValue = reference.condition();
            log.debug("引用注解条件原始值: '{}'", conditionValue);
            
            // 直接尝试获取属性的各个字面量值，跳过Java代理层
            try {
                // 使用反射获取注解的condition属性
                Method conditionMethod = reference.getClass().getMethod("condition");
                String reflectedCondition = (String) conditionMethod.invoke(reference);
                log.debug("通过反射获取的条件值: '{}'", reflectedCondition);
            } catch (Exception e) {
                log.warn("通过反射获取条件值失败: {}", e.getMessage());
            }
            
            // 创建代理对象
            Object serviceProxy = rpcClientProxy.getProxy(interfaceClass, reference);
            
            // 保存对象映射 - 便于调试
            int proxyHashCode = System.identityHashCode(serviceProxy);
            log.debug("创建的代理对象哈希码: {}", proxyHashCode);
            
            // 设置字段可访问
            field.setAccessible(true);
            
            // 将代理对象注入到字段
            field.set(instance, serviceProxy);
            
            log.info("已注入RPC服务: {}, 版本: {}, 分组: {}, 超时: {}ms, 静态字段: {}", 
                    interfaceClass.getName(), 
                    reference.version(), 
                    reference.group(), 
                    reference.timeout(),
                    instance == null);
        }
    }
} 