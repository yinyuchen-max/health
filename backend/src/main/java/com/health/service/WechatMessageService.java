package com.health.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.health.domain.entity.SubscribeMessage;
import com.health.domain.entity.User;
import com.health.mapper.SubscribeMessageMapper;
import com.health.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信订阅消息发送服务
 */
@Service
public class WechatMessageService {

    private static final Logger log = LoggerFactory.getLogger(WechatMessageService.class);
    private static final String SEND_URL = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token={access_token}";

    @Value("${wechat.miniapp.subscribe-template-id:}")
    private String defaultTemplateId;

    private final WechatAccessTokenService accessTokenService;
    private final SubscribeMessageMapper subscribeMessageMapper;
    private final UserMapper userMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WechatMessageService(WechatAccessTokenService accessTokenService,
                                 SubscribeMessageMapper subscribeMessageMapper,
                                 UserMapper userMapper,
                                 ObjectMapper objectMapper) {
        this.accessTokenService = accessTokenService;
        this.subscribeMessageMapper = subscribeMessageMapper;
        this.userMapper = userMapper;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    /**
     * 发送订阅消息给指定用户
     *
     * @param userId 用户 ID
     * @param data   模板数据，如 {"thing1": {"value": "血压测量"}, "time2": {"value": "08:00"}}
     * @return 是否发送成功
     */
    public boolean sendSubscribeMessage(Long userId, Map<String, Map<String, String>> data) {
        return sendSubscribeMessage(userId, defaultTemplateId, data);
    }

    /**
     * 发送订阅消息给指定用户（指定模板 ID）
     */
    public boolean sendSubscribeMessage(Long userId, String templateId, Map<String, Map<String, String>> data) {
        // 1. 获取用户 openid
        User user = userMapper.selectById(userId);
        if (user == null || user.getWechatOpenid() == null || user.getWechatOpenid().isBlank()) {
            log.warn("用户 {} 未绑定微信 openid，无法发送订阅消息", userId);
            return false;
        }

        // 2. 查找有效的订阅记录（一次性订阅，用完即删）
        SubscribeMessage record = subscribeMessageMapper.selectOne(
                new LambdaQueryWrapper<SubscribeMessage>()
                        .eq(SubscribeMessage::getUserId, userId)
                        .eq(SubscribeMessage::getTemplateId, templateId)
                        .eq(SubscribeMessage::getStatus, 1)
                        .last("LIMIT 1")
        );

        if (record == null) {
            log.warn("用户 {} 无有效的订阅授权（templateId={}），无法发送", userId, templateId);
            return false;
        }

        // 3. 调用微信发送接口
        String accessToken = accessTokenService.getAccessToken();
        if (accessToken == null) {
            log.error("获取微信 access_token 失败，无法发送订阅消息");
            return false;
        }

        String url = SEND_URL.replace("{access_token}", accessToken);

        Map<String, Object> body = new HashMap<>();
        body.put("touser", user.getWechatOpenid());
        body.put("template_id", templateId);
        body.put("data", data);

        try {
            // 微信 API 返回 text/plain，先用 String 接收再解析
            String responseStr = restTemplate.postForObject(url, body, String.class);
            log.info("发送订阅消息响应：{}", responseStr);
            
            if (responseStr == null || responseStr.isBlank()) {
                log.error("发送订阅消息失败：响应为空");
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(responseStr, Map.class);

            Integer errcode = (Integer) result.get("errcode");
            if (errcode != null && errcode != 0) {
                log.error("发送订阅消息失败：errcode={}, errmsg={}", errcode, result.get("errmsg"));
                // 如果是 token 过期，清除缓存后重试一次
                if (errcode == 40001 || errcode == 42001) {
                    accessTokenService.clearAccessToken();
                    return sendSubscribeMessage(userId, templateId, data);
                }
                return false;
            }

            // 4. 标记订阅记录为已使用（一次性订阅）
            record.setStatus(0);
            subscribeMessageMapper.updateById(record);

            log.info("发送订阅消息成功：userId={}, openid={}", userId, user.getWechatOpenid());
            return true;
        } catch (Exception e) {
            log.error("发送订阅消息异常：{}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 批量发送订阅消息（用于提醒调度）
     */
    public int batchSendSubscribeMessage(List<Long> userIds, Map<String, Map<String, String>> data) {
        int successCount = 0;
        for (Long userId : userIds) {
            if (sendSubscribeMessage(userId, data)) {
                successCount++;
            }
        }
        return successCount;
    }
}
