package com.rpc.client.local;

import com.rpc.core.annotation.RpcReference;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 条件评估器，用于评估是否应该使用本地服务
 */
@Slf4j
public class ConditionEvaluator {
    
    /**
     * 时间格式化器
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmm");
    
    /**
     * 时间条件正则表达式：time开头，后面跟着开始时间和结束时间，中间用-分隔
     * 例如：time0900-1800
     */
    private static final Pattern TIME_PATTERN = Pattern.compile("time(\\d{4})-(\\d{4})");
    
    /**
     * IP条件正则表达式：ip开头，后面跟着IP地址
     * 例如：ip192.168.1.1
     */
    private static final Pattern IP_PATTERN = Pattern.compile("ip([\\d.]+)");
    
    /**
     * 自定义条件处理器，key为条件前缀，value为条件处理函数
     */
    private static final Map<String, Predicate<String>> CUSTOM_CONDITION_HANDLERS = new ConcurrentHashMap<>();
    
    /**
     * 客户端IP地址缓存
     */
    private static final Map<String, Boolean> IP_CACHE = new HashMap<>();
    
    static {
        // 注册内置条件处理器
        registerBuiltInConditionHandlers();
    }
    
    /**
     * 评估是否应该使用本地服务
     * 当条件为假时使用本地服务，为真时使用远程服务
     *
     * @param reference RPC引用注解
     * @return 如果应该使用本地服务则返回true，否则返回false
     */
    public static boolean shouldUseLocalService(RpcReference reference) {
        // 验证reference对象是否为null
        if (reference == null) {
            log.error("RPC引用注解对象为null，无法评估条件，将使用远程服务");
            return false;
        }
        
        // 如果未启用本地服务，则直接返回false
        if (!reference.enableLocalService()) {
            log.debug("本地服务未启用(enableLocalService=false)，将使用远程服务");
            return false;
        }
        
        String condition = reference.condition();
        log.debug("获取到条件: '{}'", condition);
        
        // 如果条件为空，则默认使用远程服务
        if (condition == null || condition.isEmpty()) {
            log.debug("条件为空，将使用远程服务");
            return false;
        }
        
        // 移除条件字符串中可能存在的不可见字符或空格
        condition = condition.trim();
        if (condition.isEmpty()) {
            log.debug("条件去除空格后为空，将使用远程服务");
            return false;
        }
        
        // 评估条件
        log.info("开始评估条件: '{}'", condition);
        boolean conditionMet = evaluateCondition(condition);
        
        // 条件满足时使用远程服务，否则使用本地服务
        if (conditionMet) {
            log.info("条件满足('{}'=true)，将使用远程服务", condition);
            return false;
        } else {
            log.info("条件不满足('{}'=false)，将使用本地服务", condition);
            return true;
        }
    }
    
    /**
     * 评估条件
     *
     * @param condition 条件字符串
     * @return 如果条件满足返回true，否则返回false
     */
    public static boolean evaluateCondition(String condition) {
        // 如果条件为空，默认返回true（使用远程服务）
        if (condition == null || condition.isEmpty()) {
            log.debug("条件为空，默认返回true（使用远程服务）");
            return true;
        }
        
        // 去除两端空格
        condition = condition.trim();
        
        // 尝试使用内置条件处理器评估
        if (condition.startsWith("time")) {
            log.debug("检测到时间条件: '{}'", condition);
            boolean result = evaluateTimeCondition(condition);
            log.debug("时间条件评估结果: '{}'={}", condition, result);
            return result;
        } else if (condition.startsWith("ip")) {
            log.debug("检测到IP条件: '{}'", condition);
            boolean result = evaluateIpCondition(condition);
            log.debug("IP条件评估结果: '{}'={}", condition, result);
            return result;
        } else if (condition.equals("booltrue")) {
            log.debug("检测到布尔条件: '{}'", condition);
            return true; // booltrue始终返回true，表示使用远程服务
        } else if (condition.equals("boolfalse")) {
            log.debug("检测到布尔条件: '{}'", condition);
            return false; // boolfalse始终返回false，表示使用本地服务
        }
        
        // 尝试使用自定义条件处理器评估
        for (Map.Entry<String, Predicate<String>> entry : CUSTOM_CONDITION_HANDLERS.entrySet()) {
            String prefix = entry.getKey();
            if (condition.startsWith(prefix)) {
                log.debug("检测到自定义条件: '{}'，前缀: '{}'", condition, prefix);
                try {
                    boolean result = entry.getValue().test(condition);
                    log.debug("自定义条件评估结果: '{}'={}", condition, result);
                    return result;
                } catch (Exception e) {
                    log.error("评估自定义条件时发生异常: '{}'", condition, e);
                    return false;
                }
            }
        }
        
        log.warn("未知的条件格式: '{}'", condition);
        return false;
    }
    
    /**
     * 评估时间条件
     *
     * @param condition 时间条件字符串，格式为time0900-1800
     * @return 如果当前时间在指定范围内返回true，否则返回false
     */
    private static boolean evaluateTimeCondition(String condition) {
        if (condition == null || condition.isEmpty()) {
            log.warn("时间条件为空");
            return false;
        }
        
        // 详细记录匹配过程
        log.debug("尝试匹配时间条件: '{}' 使用正则: '{}'", condition, TIME_PATTERN.pattern());
        
        Matcher matcher = TIME_PATTERN.matcher(condition);
        if (!matcher.matches()) {
            log.warn("时间条件格式不正确: '{}', 期望格式: 'time0900-1800'", condition);
            
            // 尝试解释不匹配的原因
            if (!condition.startsWith("time")) {
                log.warn("条件不是以'time'开头");
            } else if (condition.length() < 10) {
                log.warn("条件长度不足: {} (应该是10个字符)", condition.length());
            } else {
                String timePattern = condition.substring(4);
                log.warn("时间部分格式可能不正确: '{}'，应该是类似'0900-1800'", timePattern);
                
                // 检查是否包含分隔符
                if (!timePattern.contains("-")) {
                    log.warn("缺少时间分隔符'-'");
                } else {
                    String[] parts = timePattern.split("-");
                    if (parts.length != 2) {
                        log.warn("时间格式分隔不正确，应该只有一个'-'");
                    } else {
                        try {
                            Integer.parseInt(parts[0]);
                            Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            log.warn("时间部分包含非数字字符");
                        }
                    }
                }
            }
            
            return false;
        }
        
        try {
            String startTimeStr = matcher.group(1);
            String endTimeStr = matcher.group(2);
            
            log.debug("成功解析到时间范围: 开始={}, 结束={}", startTimeStr, endTimeStr);
            
            // 尝试手动解析时间
            int startHour = Integer.parseInt(startTimeStr.substring(0, 2));
            int startMinute = Integer.parseInt(startTimeStr.substring(2, 4));
            int endHour = Integer.parseInt(endTimeStr.substring(0, 2));
            int endMinute = Integer.parseInt(endTimeStr.substring(2, 4));
            
            log.debug("时间值: 开始={}:{}, 结束={}:{}", startHour, startMinute, endHour, endMinute);
            
            LocalTime startTime = LocalTime.of(startHour, startMinute);
            LocalTime endTime = LocalTime.of(endHour, endMinute);
            LocalTime now = LocalTime.now();
            
            log.debug("时间条件解析: 开始时间={}, 结束时间={}, 当前时间={}", 
                    startTime.format(DateTimeFormatter.ofPattern("HH:mm")), 
                    endTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            
            boolean result;
            // 处理跨天的情况（例如2200-0600）
            if (startTime.isAfter(endTime)) {
                result = !now.isAfter(endTime) || !now.isBefore(startTime);
                log.debug("跨天时间范围判断: {}", result ? "在范围内" : "不在范围内");
            } else {
                result = !now.isBefore(startTime) && !now.isAfter(endTime);
                log.debug("普通时间范围判断: {}", result ? "在范围内" : "不在范围内");
            }
            
            return result;
        } catch (DateTimeParseException e) {
            log.error("解析时间条件时发生异常: {}", condition, e);
            return false;
        } catch (Exception e) {
            log.error("评估时间条件时发生异常: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 评估IP条件
     *
     * @param condition IP条件字符串，格式为ip192.168.1.1
     * @return 如果当前主机IP与条件匹配返回true，否则返回false
     */
    private static boolean evaluateIpCondition(String condition) {
        Matcher matcher = IP_PATTERN.matcher(condition);
        if (!matcher.matches()) {
            log.warn("IP条件格式不正确: {}", condition);
            return false;
        }
        
        String targetIp = matcher.group(1);
        log.debug("IP条件解析: 目标IP={}", targetIp);
        
        // 尝试从缓存获取结果
        if (IP_CACHE.containsKey(targetIp)) {
            boolean result = IP_CACHE.get(targetIp);
            log.debug("从缓存获取IP匹配结果: {}", result);
            return result;
        }
        
        try {
            boolean result = hasMatchingIp(targetIp);
            // 缓存结果
            IP_CACHE.put(targetIp, result);
            log.debug("IP匹配结果: {}, 已缓存", result);
            return result;
        } catch (Exception e) {
            log.error("评估IP条件时发生异常: {}", condition, e);
            return false;
        }
    }
    
    /**
     * 检查主机是否有匹配的IP地址
     *
     * @param targetIp 目标IP地址
     * @return 如果有匹配的IP返回true，否则返回false
     */
    private static boolean hasMatchingIp(String targetIp) {
        try {
            log.debug("开始检查本机IP地址...");
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                
                log.debug("检查网络接口: {}", networkInterface.getDisplayName());
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    String hostAddress = addr.getHostAddress();
                    log.debug("发现IP地址: {}", hostAddress);
                    if (hostAddress.equals(targetIp)) {
                        log.debug("找到匹配的IP地址: {}", targetIp);
                        return true;
                    }
                }
            }
            log.debug("未找到匹配的IP地址: {}", targetIp);
        } catch (SocketException e) {
            log.error("获取网络接口信息失败", e);
        }
        
        return false;
    }
    
    /**
     * 注册自定义条件处理器
     *
     * @param prefix 条件前缀
     * @param handler 条件处理函数
     */
    public static void registerConditionHandler(String prefix, Predicate<String> handler) {
        CUSTOM_CONDITION_HANDLERS.put(prefix, handler);
        log.info("注册自定义条件处理器: {}", prefix);
    }
    
    /**
     * 移除自定义条件处理器
     *
     * @param prefix 条件前缀
     */
    public static void removeConditionHandler(String prefix) {
        CUSTOM_CONDITION_HANDLERS.remove(prefix);
        log.info("移除自定义条件处理器: {}", prefix);
    }
    
    /**
     * 注册内置条件处理器
     */
    private static void registerBuiltInConditionHandlers() {
        // 注册布尔条件处理器
        registerConditionHandler("bool", condition -> {
            if (condition.equals("booltrue")) {
                log.debug("布尔条件booltrue始终返回true，使用远程服务");
                return true;
            } else if (condition.equals("boolfalse")) {
                log.debug("布尔条件boolfalse始终返回false，使用本地服务");
                return false;
            }
            log.warn("未知的布尔条件格式: '{}'，期望: 'booltrue'或'boolfalse'", condition);
            return false;
        });
    }
    
    /**
     * 添加一个公共的测试方法，用于直接测试条件评估
     * 这个方法可以在需要调试条件评估逻辑时使用
     *
     * @param condition 要测试的条件字符串
     * @return 条件评估结果
     */
    public static boolean testCondition(String condition) {
        log.info("===== 开始测试条件评估 '{}'  =====", condition);
        boolean result = evaluateCondition(condition);
        log.info("===== 条件评估结果: '{}'={} =====", condition, result);
        return result;
    }
} 