package com.health.common.config;

import com.health.common.filter.JwtAuthenticationFilter;
import com.health.common.filter.TrafficProtectionFilter;
import com.health.common.security.AuthCookies;
import com.health.common.utils.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Import(TrafficProtectionConfig.class)
public class SecurityConfig implements WebMvcConfigurer {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final AuthCookies authCookies;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:80}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper, AuthCookies authCookies) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.authCookies = authCookies;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/{x:(?!api|assets|static|favicon)[\\w/]*}")
            .setViewName("forward:/index.html");
    }

    /**
     * 配置安全过滤链
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, TrafficProtectionFilter trafficProtectionFilter) throws Exception {
        http
            // CORS 跨域配置
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Only these browser endpoints use automatically attached credentials.
            // Keep Spring's masked CSRF tokens; all business APIs still require explicit Bearer headers.
            .csrf(csrf -> csrf.csrfTokenRepository(authCookies).requireCsrfProtectionMatcher(request ->
                "POST".equals(request.getMethod()) && List.of("/api/user/login", "/api/user/refresh", "/api/user/web-logout")
                    .contains(request.getRequestURI())))

            // 无状态会话
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 授权规则
            .authorizeHttpRequests(auth -> auth
                // ASYNC 分发（SseEmitter/异步请求触发的二次分发）放行。
                // 该分发不会重新执行 JwtAuthenticationFilter，SecurityContext 为空，
                // 若走 /api/** 的 authenticated 规则会抛 AccessDeniedException。
                // 初始 REQUEST 已完成认证授权，放行 ASYNC 分发是安全的。
                .requestMatchers(new DispatcherTypeRequestMatcher(DispatcherType.ASYNC)).permitAll()
                // 公开接口：登录、注册、微信登录、医生列表、医生注册
                .requestMatchers("/api/user/csrf", "/api/user/refresh", "/api/user/native-refresh", "/api/user/web-logout", "/api/user/native-logout", "/api/user/native-login", "/api/user/login", "/api/user/register", "/api/user/wechat-login", "/api/doctor/list", "/api/doctor/departments", "/api/doctor/full-register").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/ai/payments/notify/wechat", "/api/ai/payments/notify/alipay").permitAll()
                // 管理员接口：需要 ADMIN 角色（URL 层第一道防线，方法内 requireAdmin 查库二次校验）
                .requestMatchers("/api/user/admin/**", "/api/doctor/admin/**").hasRole("ADMIN")
                // 其他所有 /api/** 接口需要认证
                .requestMatchers("/api/**").authenticated()
                // 静态资源和前端页面
                .requestMatchers("/", "/index.html", "/assets/**", "/static/**", "/favicon.ico", "/*.js", "/*.css").permitAll()
                // 其他请求放行（前端路由）
                .anyRequest().permitAll()
            )

            // 401/403 自定义响应（JSON 格式，而非 HTML 错误页）
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    Result<?> result = Result.failed("请先登录");
                    result.setCode(401);
                    response.getWriter().write(objectMapper.writeValueAsString(result));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    Result<?> result = accessDeniedException instanceof CsrfException
                            ? Result.failed("登录验证已失效，请重试").setData(java.util.Map.of("reason", "CSRF"))
                            : Result.failed("权限不足");
                    result.setCode(403);
                    response.getWriter().write(objectMapper.writeValueAsString(result));
                })
            )

            // HTTP 安全响应头
            .headers(headers -> headers
                // 防止点击劫持
                .frameOptions(frame -> frame.deny())
                // 防止 MIME 类型嗅探
                .contentTypeOptions(contentType -> {})
                // XSS 防护
                .xssProtection(xss -> {})
                // Referrer 策略
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                // 缓存控制（禁止缓存敏感接口响应）
                .cacheControl(cache -> {})
            )

            // 添加 JWT 过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(trafficProtectionFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 跨域配置
     * 通过 cors.allowed-origins 配置允许的源，多个用逗号分隔
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "X-CSRF-TOKEN"));
        config.setExposedHeaders(List.of("Authorization", "Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // 预检请求缓存 1 小时

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * 配置 HTTP 防火墙，放宽 URL 编码限制
     */
    @Bean
    public StrictHttpFirewall allowUrlEncodedSlashHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        firewall.setAllowSemicolon(true);
        firewall.setAllowUrlEncodedPercent(true);
        firewall.setAllowBackSlash(true);
        return firewall;
    }
}
