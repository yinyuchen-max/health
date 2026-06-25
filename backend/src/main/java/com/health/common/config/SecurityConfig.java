package com.health.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebSecurity
public class SecurityConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/{x:(?!api|assets|static|favicon)[\\w/]*}")
            .setViewName("forward:/index.html");
    }

    /**
     * 配置安全过滤链
     * 禁用 CSRF 保护，允许 API 接口访问
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // 禁用 CSRF 保护
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)  // 无状态会话
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/user/login", "/api/user/register").permitAll()  // 允许登录和注册
                .anyRequest().permitAll()  // 暂时允许所有请求，后续可根据需要配置权限
            );

        return http.build();
    }

    /**
     * 配置 HTTP 防火墙，放宽 URL 编码限制
     * 解决 RequestRejectedException 问题
     */
    @Bean
    public StrictHttpFirewall allowUrlEncodedSlashHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);  // 允许 URL 编码的斜杠
        firewall.setAllowSemicolon(true);  // 允许分号
        firewall.setAllowUrlEncodedPercent(true);  // 允许 URL 编码的百分号
        firewall.setAllowBackSlash(true);  // 允许反斜杠
        return firewall;
    }
}