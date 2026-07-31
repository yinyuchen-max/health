package com.health.common.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 接口限流注解
 * <p>
 * 基于 Redis 的分布式限流，支持按 IP 或用户维度限流。
 * 使用方式：在 Controller 方法上添加 @RateLimit 注解即可。
 * </p>
 *
 * <pre>
 * // 每个 IP 每分钟最多 5 次登录请求
 * &#64;RateLimit(key = "login", maxRequests = 5, timeWindow = 1, timeUnit = TimeUnit.MINUTES)
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流 key 前缀，用于区分不同接口
     */
    String key();

    /**
     * 时间窗口内允许的最大请求数
     */
    int maxRequests() default 60;

    /**
     * 时间窗口大小
     */
    long timeWindow() default 1;

    /**
     * 时间单位，默认分钟
     */
    TimeUnit timeUnit() default TimeUnit.MINUTES;

    /**
     * 限流维度：按 IP 还是按用户
     */
    LimitType limitBy() default LimitType.IP;

    enum LimitType {
        /** 按客户端 IP 限流（适用于未登录接口） */
        IP,
        /** 按当前登录用户限流（适用于已登录接口） */
        USER
    }
}
