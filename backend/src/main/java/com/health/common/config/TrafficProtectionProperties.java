package com.health.common.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 所有 API 共享的入口保护；修改配置后需重启。 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "health.traffic-protection")
public class TrafficProtectionProperties {
    private boolean enabled = true;
    @Min(1) private int requestsPerSecond = 50;
    @Min(1) private int burstCapacity = 50;
    /** 每个后端实例的上限；默认小于当前 Hikari 的 20 个连接。 */
    @Min(1) private int maxConcurrentRequests = 16;
    /** 同一数据库的所有后端实例必须共用 Redis 数据库和 key。 */
    @NotBlank private String redisKey = "rate:limit:global:api";
}
