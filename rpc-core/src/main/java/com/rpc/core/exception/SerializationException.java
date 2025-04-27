package com.rpc.core.exception;

/**
 * 序列化异常
 */
public class SerializationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SerializationException(String message) {
        super(message);
    }

    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
} 