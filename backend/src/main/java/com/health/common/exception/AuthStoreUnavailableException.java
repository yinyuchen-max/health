package com.health.common.exception;

public class AuthStoreUnavailableException extends RuntimeException {
    public AuthStoreUnavailableException() { super("认证服务暂时不可用，请稍后重试"); }
}
