package com.health.common.exception;

/**
 * 未认证异常
 * 当用户未登录或 Token 无效时抛出
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
