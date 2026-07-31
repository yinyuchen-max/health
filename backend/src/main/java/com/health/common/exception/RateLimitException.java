package com.health.common.exception;

/**
 * 请求过于频繁异常
 * 当触发限流规则时抛出
 */
public class RateLimitException extends RuntimeException {

    private final int retryAfterSeconds;

    public RateLimitException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
