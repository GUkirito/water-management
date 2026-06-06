package com.example.watermanagement.exception;

/**
 * 业务异常，用于 Service 层抛出，由 GlobalExceptionHandler 统一处理
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
