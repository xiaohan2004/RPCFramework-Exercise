package com.rpc.demo.api;

/**
 * Hello服务接口
 */
public interface HelloService {
    /**
     * 问候方法
     *
     * @param name 姓名
     * @return 问候语
     */
    String sayHello(String name);
    
    /**
     * 获取当前服务器时间
     *
     * @return 当前服务器时间的字符串表示
     */
    String getServerTime();
} 