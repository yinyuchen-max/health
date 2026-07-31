package com.health.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * RAG 健康知识的 Redis 向量存储配置
 */
@Component
@ConfigurationProperties(prefix = "rag.health.redis")
public class RagRedisProperties {

    /** Redis 主机地址 */
    private String host = "localhost";

    /** Redis 端口 */
    private int port = 6379;

    /** Redis 用户名（可选） */
    private String username = "";

    /** Redis 密码（可选） */
    private String password = "";

    /** 是否启用 TLS */
    private boolean tls = false;

    /** Redis 数据库编号 */
    private int database = 0;

    /** Key 前缀 */
    private String keyPrefix = "health:knowledge";

    /** 分布式锁 TTL */
    private Duration lockTtl = Duration.ofSeconds(120);

    /** 等待获取锁的时间 */
    private Duration lockWait = Duration.ofSeconds(2);

    // Getters and Setters

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isTls() {
        return tls;
    }

    public void setTls(boolean tls) {
        this.tls = tls;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getLockTtl() {
        return lockTtl;
    }

    public void setLockTtl(Duration lockTtl) {
        this.lockTtl = lockTtl;
    }

    public Duration getLockWait() {
        return lockWait;
    }

    public void setLockWait(Duration lockWait) {
        this.lockWait = lockWait;
    }

    /**
     * 生成完整的 key
     */
    public String key(String suffix) {
        return keyPrefix + ":" + suffix;
    }
}
