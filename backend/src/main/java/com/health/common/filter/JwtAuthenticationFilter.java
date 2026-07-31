package com.health.common.filter;

import com.health.common.utils.Result;
import com.health.common.utils.JwtUtil;
import com.health.service.impl.TokenBlacklistServiceImpl;
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
import java.util.Date;
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

    public JwtAuthenticationFilter(JwtUtil jwtUtil, TokenBlacklistServiceImpl tokenBlacklistService, ObjectMapper objectMapper) {
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
                // 1. 检查黑名单
                if (tokenBlacklistService.isBlacklisted(token)) {
                    log.debug("Token 已被加入黑名单");
                    sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Token 已失效，请重新登录");
                    return;
                }

                // 2. 解析并验证 Token
                String username = jwtUtil.extractUsername(token);
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                    // 3. 检查用户强制下线标记
                    Date issuedAt = jwtUtil.getClaimFromToken(token, Claims::getIssuedAt);
                    if (tokenBlacklistService.isTokenInvalidated(username, issuedAt)) {
                        log.debug("用户 {} 的 Token 已被强制失效", username);
                        sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "账号已被强制下线，请重新登录");
                        return;
                    }

                    // 4. 设置 Security Context
                    String role = jwtUtil.extractRole(token);
                    List<SimpleGrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + (role != null ? role.toUpperCase() : "USER"))
                    );

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                log.debug("JWT Token 验证失败: {}", e.getMessage());
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
        return path.startsWith("/static/") ||
               path.startsWith("/assets/") ||
               path.equals("/favicon.ico") ||
               path.endsWith(".html") ||
               path.endsWith(".js") ||
               path.endsWith(".css");
    }
}
