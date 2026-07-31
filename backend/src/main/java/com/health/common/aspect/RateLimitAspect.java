package com.health.common.aspect;

import com.health.common.annotation.RateLimit;
import com.health.common.exception.RateLimitException;
import com.health.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * 限流切面
 * <p>
 * 拦截标注了 @RateLimit 的方法，根据配置进行限流检查。
 * Redis 异常时自动放行，不影响正常业务。
 * </p>
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    private static final String KEY_PREFIX = "rate:limit";

    private final RateLimitService rateLimitService;

    public RateLimitAspect(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String identifier = resolveIdentifier(rateLimit.limitBy());
        long windowSeconds = rateLimit.timeUnit().toSeconds(rateLimit.timeWindow());
        String key = String.join(":", KEY_PREFIX, rateLimit.key(), identifier);

        RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(
                key, rateLimit.maxRequests(), windowSeconds);

        if (!result.allowed()) {
            log.warn("限流触发: {} ({}), 已用 {}/{} 次/{}",
                    rateLimit.key(), identifier,
                    rateLimit.maxRequests() - result.remaining(),
                    rateLimit.maxRequests(),
                    formatDuration(windowSeconds));
            throw new RateLimitException(
                    "请求过于频繁，请稍后再试",
                    (int) windowSeconds);
        }

        return joinPoint.proceed();
    }

    /**
     * 根据限流维度获取标识符
     */
    private String resolveIdentifier(RateLimit.LimitType limitBy) {
        if (limitBy == RateLimit.LimitType.USER) {
            // 优先使用当前登录用户名
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
                    return "user:" + auth.getPrincipal().toString();
                }
            } catch (Exception ignored) {
            }
        }
        // 回退到 IP
        return "ip:" + getClientIp();
    }

    /**
     * 获取客户端真实 IP
     * 支持 Nginx/反向代理场景
     */
    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";

        HttpServletRequest request = attrs.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能包含多个 IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String formatDuration(long seconds) {
        if (seconds >= 3600) return (seconds / 3600) + "小时";
        if (seconds >= 60) return (seconds / 60) + "分钟";
        return seconds + "秒";
    }
}
