package com.health.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.common.config.TrafficProtectionProperties;
import com.health.common.utils.Result;
import com.health.service.GlobalTrafficLimiter;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** 在 JWT 校验/查库之前拒绝超载；同步和 SSE/异步请求共享实例并发额度。 */
public class TrafficProtectionFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(TrafficProtectionFilter.class);
    private final TrafficProtectionProperties properties;
    private final GlobalTrafficLimiter limiter;
    private final ObjectMapper mapper;
    private final Semaphore permits;

    public TrafficProtectionFilter(TrafficProtectionProperties properties, GlobalTrafficLimiter limiter, ObjectMapper mapper) {
        this.properties = properties;
        this.limiter = limiter;
        this.mapper = mapper;
        this.permits = new Semaphore(properties.getMaxConcurrentRequests());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 与 MVC 一致地解码路径、剥离矩阵参数，防止 /%61pi 或 /api;v=1 绕过入口保护。
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return !properties.isEnabled() || "OPTIONS".equals(request.getMethod())
                || !(path.equals("/api") || path.startsWith("/api/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 不排队等待连接池，也限制正在等待 Redis 的请求数。
        if (!permits.tryAcquire()) {
            reject(response, 429, "系统繁忙，请稍后再试", 1);
            return;
        }
        Permit permit = new Permit();
        try {
            long retryMillis;
            try {
                retryMillis = limiter.acquire();
            } catch (RuntimeException e) {
                // 全局保护不能像个人限流那样 fail-open，否则 Redis 故障时会冲击数据库。
                log.debug("全局流量保护暂时不可用", e);
                reject(response, 503, "系统暂时不可用，请稍后再试", 1);
                return;
            }
            if (retryMillis > 0) {
                reject(response, 429, "系统繁忙，请稍后再试", Math.max(1, (retryMillis + 999) / 1000));
                return;
            }
            // 在 startAsync 当时注册监听器，避免在 chain 返回后才注册造成完成事件丢失。
            HttpServletRequestWrapper wrapped = new HttpServletRequestWrapper(request) {
                @Override
                public AsyncContext startAsync() {
                    return observe(super.startAsync(this, response));
                }

                @Override
                public AsyncContext startAsync(ServletRequest asyncRequest, ServletResponse asyncResponse) {
                    return observe(super.startAsync(asyncRequest, asyncResponse));
                }

                private AsyncContext observe(AsyncContext context) {
                    if (permit.observed.compareAndSet(false, true)) {
                        context.addListener(permit);
                    }
                    return context;
                }
            };
            chain.doFilter(wrapped, response);
        } finally {
            // 异步请求在 onComplete 才释放，超时/错误的二次分发仍占用额度。
            if (!request.isAsyncStarted()) {
                permit.release();
            }
        }
    }

    private void reject(HttpServletResponse response, int status, String message, long retrySeconds) throws IOException {
        response.setStatus(status);
        response.setHeader("Retry-After", String.valueOf(retrySeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), Result.failed(message).setCode(status));
    }

    private final class Permit implements AsyncListener {
        private final AtomicBoolean released = new AtomicBoolean();
        private final AtomicBoolean observed = new AtomicBoolean();

        private void release() {
            if (released.compareAndSet(false, true)) permits.release();
        }

        @Override public void onComplete(AsyncEvent event) { release(); }
        @Override public void onTimeout(AsyncEvent event) { /* 等待最终完成，不能提前放入新请求。 */ }
        @Override public void onError(AsyncEvent event) { /* 容器完成错误分发后会触发 onComplete。 */ }
        @Override public void onStartAsync(AsyncEvent event) { event.getAsyncContext().addListener(this); }
    }
}
