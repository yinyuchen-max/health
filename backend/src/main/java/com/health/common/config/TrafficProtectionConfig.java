package com.health.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.common.filter.TrafficProtectionFilter;
import com.health.service.GlobalTrafficLimiter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TrafficProtectionProperties.class)
public class TrafficProtectionConfig {
    @Bean
    public GlobalTrafficLimiter globalTrafficLimiter(StringRedisTemplate redis, TrafficProtectionProperties properties) {
        return new GlobalTrafficLimiter(redis, properties);
    }

    @Bean
    public TrafficProtectionFilter trafficProtectionFilter(TrafficProtectionProperties properties,
                                                           GlobalTrafficLimiter limiter, ObjectMapper mapper) {
        return new TrafficProtectionFilter(properties, limiter, mapper);
    }

    @Bean
    public FilterRegistrationBean<TrafficProtectionFilter> trafficProtectionRegistration(TrafficProtectionFilter filter) {
        // 仅在 SecurityFilterChain 内执行，确保 CORS 响应头有效，并避免重复计数。
        FilterRegistrationBean<TrafficProtectionFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
