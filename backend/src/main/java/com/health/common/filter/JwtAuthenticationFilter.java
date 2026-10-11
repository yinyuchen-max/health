package com.health.common.filter;

import com.health.common.utils.Result;
import com.health.common.utils.JwtUtil;
import com.health.service.impl.TokenBlacklistServiceImpl;
import com.health.service.AuthSessionService;
import com.health.common.exception.AuthStoreUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器
 * 从请求头中提取 JWT Token 并验证，同时检查黑名单
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final TokenBlacklistServiceImpl tokenBlacklistService;
    private final ObjectMapper objectMapper;
    private final AuthSessionService sessions;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, TokenBlacklistServiceImpl tokenBlacklistService, ObjectMapper objectMapper, AuthSessionService sessions) {
        this.sessions = sessions;
        this.jwtUtil = jwtUtil;
        this.tokenBlacklistService = tokenBlacklistService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(AUTH_HEADER);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());

            try {
                Claims claims = jwtUtil.parseAccessToken(token);
                if (tokenBlacklistService.isBlacklisted(token)) {
                    sendJsonError(response, 401, "登录已失效，请重新登录"); return;
                }
                String role = sessions.validateAccess(claims);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(java.util.Locale.ROOT))));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (AuthStoreUnavailableException | org.springframework.dao.DataAccessException e) {
                SecurityContextHolder.clearContext();
                sendJsonError(response, 503, "认证服务暂时不可用，请稍后重试"); return;
            } catch (Exception e) {
                log.debug("JWT 认证失败");
                SecurityContextHolder.clearContext();
                sendJsonError(response, 401, "登录已失效，请重新登录"); return;
                // Token 无效时不设置认证信息，由后续权限控制处理
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 返回 JSON 格式的错误响应（而非 HTML 错误页）
     */
    private void sendJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Result<?> result = Result.failed(message);
        result.setCode(status);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    /**
     * 判断是否需要跳过过滤（静态资源等）
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Cookie/native refresh authenticate independently, including when the access token has expired.
        if (List.of("/api/user/csrf", "/api/user/login", "/api/user/native-login", "/api/user/wechat-login",
                "/api/user/refresh", "/api/user/native-refresh", "/api/user/web-logout", "/api/user/native-logout").contains(path)) return true;
        return path.startsWith("/static/") ||
               path.startsWith("/assets/") ||
               path.equals("/favicon.ico") ||
               path.endsWith(".html") ||
               path.endsWith(".js") ||
               path.endsWith(".css");
    }
}
