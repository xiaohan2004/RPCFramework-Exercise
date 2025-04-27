package com.rpc.core.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * RPC框架配置类
 */
@Slf4j
public class RpcConfig {
    private static final Properties properties = new Properties();
    private static final String DEFAULT_CONFIG_FILE = "rpc.properties";
    
    static {
        try (InputStream inputStream = RpcConfig.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG_FILE)) {
            if (inputStream != null) {
                properties.load(inputStream);
                log.info("成功加载RPC配置文件: {}", DEFAULT_CONFIG_FILE);
            } else {
                log.warn("未找到RPC配置文件: {}, 将使用默认配置", DEFAULT_CONFIG_FILE);
            }
        } catch (IOException e) {
            log.error("加载RPC配置文件时发生错误", e);
        }
    }
    
    /**
     * 获取字符串配置项
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    /**
     * 获取整数配置项
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public static int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("解析整数配置项失败: {}={}, 将使用默认值: {}", key, value, defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * 获取长整数配置项
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public static long getLong(String key, long defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("解析长整数配置项失败: {}={}, 将使用默认值: {}", key, value, defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * 获取布尔配置项
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
    
    /**
     * 获取服务器端口
     */
    public static int getServerPort() {
        return getInt("rpc.server.port", 9000);
    }
    
    /**
     * 获取注册中心地址
     */
    public static String getRegistryAddress() {
        return getProperty("rpc.registry.address", "127.0.0.1:8000");
    }
    
    /**
     * 获取序列化类型
     */
    public static String getSerializationType() {
        return getProperty("rpc.serialization.type", "json");
    }
    
    /**
     * 获取负载均衡策略
     */
    public static String getLoadBalanceStrategy() {
        return getProperty("rpc.loadbalance.strategy", "random");
    }
    
    private RpcConfig() {
        // 私有构造方法，防止实例化
    }
} 