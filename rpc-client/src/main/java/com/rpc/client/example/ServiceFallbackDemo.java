package com.rpc.client.example;

import com.rpc.client.RpcClient;
import com.rpc.client.local.LocalServiceFactory;
import com.rpc.core.annotation.RpcReference;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 服务回退演示类，展示如何注册和使用回退服务，以及如何优雅处理服务不可用的情况
 */
@Slf4j
public class ServiceFallbackDemo {
    
    /**
     * 示例用户服务接口
     */
    public interface UserService {
        /**
         * 获取用户名
         * 
         * @param userId 用户ID
         * @return 用户名
         */
        String getUserName(Long userId);
        
        /**
         * 获取用户年龄
         * 
         * @param userId 用户ID
         * @return 用户年龄
         */
        int getUserAge(Long userId);
        
        /**
         * 检查用户是否存在
         * 
         * @param userId 用户ID
         * @return 是否存在
         */
        boolean userExists(Long userId);
    }
    
    /**
     * 本地用户服务实现
     */
    public static class LocalUserService implements UserService {
        @Override
        public String getUserName(Long userId) {
            return "本地用户_" + userId;
        }
        
        @Override
        public int getUserAge(Long userId) {
            return 30; // 固定返回30岁
        }
        
        @Override
        public boolean userExists(Long userId) {
            return userId != null && userId > 0;
        }
    }
    
    /**
     * 用户服务回退实现
     */
    public static class FallbackUserService implements UserService {
        @Override
        public String getUserName(Long userId) {
            return "[回退服务] 无法获取用户名，服务不可用";
        }
        
        @Override
        public int getUserAge(Long userId) {
            return -1; // 表示未知年龄
        }
        
        @Override
        public boolean userExists(Long userId) {
            log.warn("回退服务：无法验证用户是否存在，默认返回false");
            return false;
        }
    }
    
    /**
     * 演示常规使用场景
     */
    public static void demonstrateNormalUsage() {
        log.info("===== 演示常规使用场景 =====");
        
        // 1. 注册本地服务实现
        LocalServiceFactory.registerLocalService(UserService.class, new LocalUserService());
        log.info("已注册本地UserService实现");
        
        // 2. 注册回退服务实现
        LocalServiceFactory.registerFallbackService(UserService.class, new FallbackUserService());
        log.info("已注册UserService回退实现");
        
        // 3. 创建RPC客户端
        RpcClient client = RpcClient.getInstance();
        
        // 4. 获取服务代理
        try {
            UserService userService = client.getRemoteService(UserService.class);
            
            // 5. 调用服务（可能会回退到本地服务或回退服务）
            String userName = userService.getUserName(123L);
            log.info("获取到用户名: {}", userName);
            
            int age = userService.getUserAge(123L);
            log.info("获取到用户年龄: {}", age);
            
            boolean exists = userService.userExists(123L);
            log.info("用户是否存在: {}", exists);
            
        } catch (Exception e) {
            log.error("服务调用异常", e);
        }
    }
    
    /**
     * 演示手动创建动态代理
     */
    public static void demonstrateManualProxy() {
        log.info("===== 演示手动创建动态代理 =====");
        
        // 1. 创建本地服务代理
        UserService localProxy = createServiceProxy(UserService.class, true);
        
        // 2. 创建远程服务代理（将回退到回退服务）
        UserService remoteProxy = createServiceProxy(UserService.class, false);
        
        // 3. 调用本地服务
        log.info("调用本地服务代理:");
        String localName = localProxy.getUserName(456L);
        log.info("本地服务返回用户名: {}", localName);
        
        // 4. 调用远程服务（将回退）
        log.info("调用远程服务代理（预期回退）:");
        String remoteName = remoteProxy.getUserName(456L);
        log.info("远程/回退服务返回用户名: {}", remoteName);
    }
    
    /**
     * 创建服务代理
     * 
     * @param serviceInterface 服务接口
     * @param useLocal 是否使用本地实现
     * @return 服务代理
     */
    @SuppressWarnings("unchecked")
    private static <T> T createServiceProxy(Class<T> serviceInterface, boolean useLocal) {
        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class<?>[]{serviceInterface},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        // 如果是Object类的方法，直接调用
                        if (Object.class.equals(method.getDeclaringClass())) {
                            return method.invoke(this, args);
                        }
                        
                        if (useLocal) {
                            // 获取本地服务
                            Object localService = LocalServiceFactory.getLocalService(
                                    serviceInterface.getName(), "1.0.0", "");
                            if (localService != null) {
                                log.info("使用本地服务: {}.{}", serviceInterface.getName(), method.getName());
                                return method.invoke(localService, args);
                            }
                        } else {
                            // 模拟远程服务不可用，尝试获取回退服务
                            log.info("模拟远程服务不可用，尝试获取回退服务");
                            Object fallbackService = LocalServiceFactory.getLocalServiceWithFallback(
                                    serviceInterface.getName(), "1.0.0", "", true);
                            if (fallbackService != null) {
                                log.info("使用回退服务: {}.{}", serviceInterface.getName(), method.getName());
                                return method.invoke(fallbackService, args);
                            }
                        }
                        
                        // 如果没有本地服务或回退服务，返回默认值
                        log.warn("无法找到服务实现，返回默认值");
                        Class<?> returnType = method.getReturnType();
                        
                        if (returnType == String.class) {
                            return "服务不可用";
                        } else if (returnType == boolean.class || returnType == Boolean.class) {
                            return false;
                        } else if (returnType.isPrimitive() || Number.class.isAssignableFrom(returnType)) {
                            return 0;
                        } else {
                            return null;
                        }
                    }
                });
    }
    
    /**
     * 主函数
     */
    public static void main(String[] args) {
        // 演示常规使用场景
        demonstrateNormalUsage();
        
        System.out.println("\n");
        
        // 演示手动创建动态代理
        demonstrateManualProxy();
        
        // 清理资源
        LocalServiceFactory.clearAllServices();
        RpcClient.getInstance().close();
    }
} 