package com.rpc.core.protocol;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

/**
 * RPC响应数据结构
 */
@Data
@NoArgsConstructor
@ToString
public class RpcResponse<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 响应码
     */
    private Integer code;
    
    /**
     * 错误消息
     */
    private String message;
    
    /**
     * 响应数据
     */
    private T data;
    
    /**
     * 成功响应码
     */
    public static final Integer SUCCESS_CODE = 200;
    
    /**
     * 失败响应码
     */
    public static final Integer FAIL_CODE = 500;

    /**
     * 创建成功响应
     */
    public static <T> RpcResponse<T> success(T data) {
        RpcResponse<T> response = new RpcResponse<>();
        response.setCode(SUCCESS_CODE);
        response.setMessage("调用成功");
        response.setData(data);
        return response;
    }

    /**
     * 创建失败响应
     */
    public static <T> RpcResponse<T> fail(String message) {
        RpcResponse<T> response = new RpcResponse<>();
        response.setCode(FAIL_CODE);
        response.setMessage(message != null ? message : "未知错误");
        return response;
    }

    /**
     * 创建异常响应
     */
    public static <T> RpcResponse<T> fail(Exception e) {
        RpcResponse<T> response = new RpcResponse<>();
        response.setCode(FAIL_CODE);
        response.setMessage(e != null ? e.getMessage() : "未知异常");
        return response;
    }

    @Override
    public String toString() {
        return "RpcResponse{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
} 