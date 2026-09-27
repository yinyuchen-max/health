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
     * 获取客户端真实 IP（防伪造）
     * <p>
     * 仅当直连方（remoteAddr）是可信代理（本机/内网，即 nginx、cloudflared 等）时才解析转发头；
     * 解析 X-Forwarded-For 时从右向左扫描，跳过可信代理追加的内网地址，
     * 取第一个"非内网"地址 —— 它是由可信代理追加的、客户端无法伪造的值。
     * 客户端自带的伪造 XFF 值只会出现在更左侧，永远不会被选中。
     * </p>
     */
    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";

        HttpServletRequest request = attrs.getRequest();
        String remoteAddr = request.getRemoteAddr();

        // 直连方不是可信代理：XFF/X-Real-IP 均可被客户端任意伪造，直接忽略
        if (remoteAddr == null || !isTrustedProxy(remoteAddr)) {
            return remoteAddr != null ? remoteAddr : "unknown";
        }

        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String candidate = normalizeIp(parts[i].trim());
                if (!candidate.isEmpty() && !"unknown".equalsIgnoreCase(candidate)
                        && !isTrustedProxy(candidate)) {
                    return candidate;
                }
            }
        }

        String realIp = normalizeIp(request.getHeader("X-Real-IP"));
        if (realIp != null && !realIp.isEmpty() && !"unknown".equalsIgnoreCase(realIp)
                && !isTrustedProxy(realIp)) {
            return realIp;
        }

        return remoteAddr;
    }

    /**
     * 判断是否为可信代理地址（回环、Docker/内网私有网段、链路本地）
     */
    private static boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        String normalized = normalizeIp(ip);
        if ("::1".equals(normalized) || "127.0.0.1".equals(normalized) || normalized.startsWith("127.")) {
            return true;
        }
        if (normalized.startsWith("10.") || normalized.startsWith("192.168.")
                || normalized.startsWith("169.254.") || normalized.startsWith("fc")
                || normalized.startsWith("fd") || normalized.startsWith("fe80")) {
            return true;
        }
        // 172.16.0.0 - 172.31.255.255（Docker 默认网段所在范围）
        if (normalized.startsWith("172.")) {
            String[] seg = normalized.split("\\.");
            if (seg.length == 4) {
                try {
                    int second = Integer.parseInt(seg[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * 去掉 IPv4-mapped IPv6 前缀（如 ::ffff:172.18.0.3）并 trim
     */
    private static String normalizeIp(String ip) {
        if (ip == null) return "";
        String result = ip.trim();
        if (result.regionMatches(true, 0, "::ffff:", 0, 7)) {
            result = result.substring(7);
        }
        return result;
    }

    private String formatDuration(long seconds) {
        if (seconds >= 3600) return (seconds / 3600) + "小时";
        if (seconds >= 60) return (seconds / 60) + "分钟";
        return seconds + "秒";
    }
}
