package com.health.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 微信 access_token 管理服务
 * access_token 有效期 2 小时，使用 Redis 缓存避免频繁请求
 */
@Service
public class WechatAccessTokenService {

    private static final Logger log = LoggerFactory.getLogger(WechatAccessTokenService.class);
    private static final String REDIS_KEY = "wechat:access_token";
    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid={appid}&secret={secret}";

    @Value("${wechat.miniapp.app-id:}")
    private String appId;

    @Value("${wechat.miniapp.app-secret:}")
    private String appSecret;

    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WechatAccessTokenService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    /**
     * 获取 access_token（优先从 Redis 缓存读取）
     */
    public String getAccessToken() {
        // 1. 尝试从 Redis 读取
        String cached = redisTemplate.opsForValue().get(REDIS_KEY);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }

        // 2. 调用微信接口获取
        String url = ACCESS_TOKEN_URL
                .replace("{appid}", appId)
                .replace("{secret}", appSecret);

        try {
            // 微信 API 返回 text/plain，先用 String 接收再解析
            String responseStr = restTemplate.getForObject(url, String.class);
            log.info("获取 access_token 响应：{}", responseStr);
            
            if (responseStr == null || responseStr.isBlank()) {
                log.error("获取微信 access_token 失败：响应为空");
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(responseStr, Map.class);

            if (result.containsKey("errcode") && !Integer.valueOf(0).equals(result.get("errcode"))) {
                log.error("获取微信 access_token 失败：errcode={}, errmsg={}", result.get("errcode"), result.get("errmsg"));
                return null;
            }

            String accessToken = (String) result.get("access_token");
            Integer expiresIn = (Integer) result.get("expires_in");

            if (accessToken != null && expiresIn != null) {
                // 缓存时间比实际有效期少 5 分钟，避免临界点过期
                long cacheSeconds = expiresIn - 300;
                redisTemplate.opsForValue().set(REDIS_KEY, accessToken, cacheSeconds, TimeUnit.SECONDS);
                log.info("获取微信 access_token 成功，已缓存到 Redis，有效期 {} 秒", cacheSeconds);
                return accessToken;
            }
        } catch (Exception e) {
            log.error("获取微信 access_token 异常：{}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * 清除缓存的 access_token（用于强制刷新）
     */
    public void clearAccessToken() {
        redisTemplate.delete(REDIS_KEY);
    }
}
