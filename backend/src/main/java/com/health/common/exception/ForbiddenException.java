package com.health.common.exception;

/**
 * 无权限异常
 * 当用户没有操作权限时抛出（如越权操作他人数据）
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
