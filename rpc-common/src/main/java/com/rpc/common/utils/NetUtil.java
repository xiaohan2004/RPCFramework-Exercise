package com.rpc.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

/**
 * 网络工具类
 */
@Slf4j
public class NetUtil {
    /**
     * 获取本机IP地址
     *
     * @return 本机IP地址
     */
    public static String getLocalIp() {
        try {
            // 优先获取非回环地址
            Enumeration<NetworkInterface> allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            while (allNetInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = allNetInterfaces.nextElement();
                // 跳过回环和禁用的接口
                if (netInterface.isLoopback() || !netInterface.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress ip = addresses.nextElement();
                    if (ip.getHostAddress().matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                        return ip.getHostAddress();
                    }
                }
            }
            
            // 如果没有找到合适的IP，则使用本地回环地址
            return InetAddress.getLocalHost().getHostAddress();
        } catch (SocketException | UnknownHostException e) {
            log.error("获取本机IP地址失败", e);
            return "127.0.0.1";
        }
    }
    
    /**
     * 检查端口是否可用
     *
     * @param port 端口号
     * @return 端口是否可用
     */
    public static boolean isPortAvailable(int port) {
        if (port < 1 || port > 65535) {
            return false;
        }
        
        try {
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress("localhost", port), 200);
            socket.close();
            return false;
        } catch (java.io.IOException e) {
            return true;
        }
    }
    
    /**
     * 构建地址字符串
     *
     * @param host 主机
     * @param port 端口
     * @return 地址字符串，格式为host:port
     */
    public static String buildAddress(String host, int port) {
        return host + ":" + port;
    }
    
    /**
     * 从地址字符串中提取主机
     *
     * @param address 地址字符串，格式为host:port
     * @return 主机
     */
    public static String getHostFromAddress(String address) {
        if (address == null || !address.contains(":")) {
            return "";
        }
        return address.split(":")[0];
    }
    
    /**
     * 从地址字符串中提取端口
     *
     * @param address 地址字符串，格式为host:port
     * @return 端口
     */
    public static int getPortFromAddress(String address) {
        if (address == null || !address.contains(":")) {
            return 0;
        }
        try {
            return Integer.parseInt(address.split(":")[1]);
        } catch (NumberFormatException e) {
            log.error("解析端口失败，地址格式不正确: {}", address);
            return 0;
        }
    }
} 