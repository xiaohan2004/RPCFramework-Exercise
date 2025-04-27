package com.rpc.demo.provider;

import com.rpc.core.annotation.RpcService;
import com.rpc.demo.api.HelloService;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Hello服务实现类
 */
@RpcService(version = "1.0.0")
public class HelloServiceImpl implements HelloService {
    
    @Override
    public String sayHello(String name) {
        return "你好, " + name + "! 来自RPC服务端的问候!";
    }
    
    @Override
    public String getServerTime() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return "服务器当前时间: " + format.format(new Date());
    }
} 