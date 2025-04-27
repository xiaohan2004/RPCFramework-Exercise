package com.rpc.registry.local;

import com.rpc.common.model.ServiceInfo;
import com.rpc.registry.ServiceRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 本地内存服务注册实现
 */
@Slf4j
public class LocalServiceRegistry implements ServiceRegistry {
    /**
     * 服务注册表，key为serviceKey，value为服务信息列表
     */
    private final Map<String, List<ServiceInfo>> serviceMap = new ConcurrentHashMap<>();
    
    @Override
    public void register(ServiceInfo serviceInfo) {
        String serviceKey = serviceInfo.getServiceKey();
        log.info("注册服务: {}", serviceInfo);
        
        // 获取服务列表，如果不存在则创建
        List<ServiceInfo> serviceList = serviceMap.computeIfAbsent(serviceKey, k -> new ArrayList<>());
        
        // 检查是否已存在相同地址的服务
        boolean exists = serviceList.stream()
                .anyMatch(svc -> svc.getAddress().equals(serviceInfo.getAddress()));
        
        if (!exists) {
            serviceList.add(serviceInfo);
            log.info("服务注册成功: {}", serviceInfo);
        } else {
            log.info("服务已存在，无需重复注册: {}", serviceInfo);
        }
    }
    
    @Override
    public void unregister(ServiceInfo serviceInfo) {
        String serviceKey = serviceInfo.getServiceKey();
        log.info("注销服务: {}", serviceInfo);
        
        List<ServiceInfo> serviceList = serviceMap.get(serviceKey);
        if (serviceList != null) {
            serviceList.removeIf(svc -> svc.getAddress().equals(serviceInfo.getAddress()));
            log.info("服务注销成功: {}", serviceInfo);
            
            // 如果列表为空，移除该服务条目
            if (serviceList.isEmpty()) {
                serviceMap.remove(serviceKey);
            }
        }
    }
    
    @Override
    public List<ServiceInfo> discover(String serviceName, String version, String group) {
        // 构建服务键
        String serviceKey = serviceName + 
                (version != null ? "_" + version : "") + 
                (group != null ? "_" + group : "");
        
        log.info("发现服务: {}", serviceKey);
        
        List<ServiceInfo> serviceList = serviceMap.get(serviceKey);
        if (serviceList == null || serviceList.isEmpty()) {
            log.warn("未找到服务实例: {}", serviceKey);
            return new ArrayList<>();
        }
        
        log.info("发现了{}个服务实例: {}", serviceList.size(), serviceKey);
        return new ArrayList<>(serviceList);
    }
    
    @Override
    public void destroy() {
        serviceMap.clear();
        log.info("本地服务注册中心已销毁");
    }
} 