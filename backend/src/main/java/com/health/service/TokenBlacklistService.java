package com.health.service;

import java.util.Date;

/**
 * Token 黑名单服务接口
 * 用于管理已注销或被禁用的 JWT Token
 */
public interface TokenBlacklistService {

    /**
     * 将 token 加入黑名单
     * @param token JWT token
     * @param expiration token 的过期时间
     */
    void addToBlacklist(String token, Date expiration);

    /**
     * 检查 token 是否在黑名单中
     * @param token JWT token
     * @return true 表示 token 已被禁用
     */
    boolean isBlacklisted(String token);

    /**
     * 将指定用户的所有 token 加入黑名单（用于强制下线）
     * @param username 用户名
     */
    void blacklistAllTokensForUser(String username);
}
