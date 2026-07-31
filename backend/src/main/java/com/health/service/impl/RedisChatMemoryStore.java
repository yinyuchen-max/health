package com.health.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 聊天记忆存储
 * 使用 Redis List 存储用户对话历史，支持 7 天自动过期
 */
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemoryStore.class);

    /** Redis key 前缀 */
    private static final String CHAT_MEMORY_PREFIX = "chat:memory:";
    /** 默认最大消息数 */
    private static final long MAX_MESSAGES = 20;
    /** 默认过期时间：7 天 */
    private static final long EXPIRE_DAYS = 7;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisChatMemoryStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = CHAT_MEMORY_PREFIX + memoryId;
        try {
            List<Object> stored = redisTemplate.opsForList().range(key, 0, -1);
            if (stored == null || stored.isEmpty()) {
                return new ArrayList<>();
            }

            List<ChatMessage> messages = new ArrayList<>();
            for (Object obj : stored) {
                try {
                    String json = obj instanceof String ? (String) obj : objectMapper.writeValueAsString(obj);
                    ChatMessageData data = objectMapper.readValue(json, ChatMessageData.class);
                    messages.add(data.toChatMessage());
                } catch (Exception e) {
                    log.warn("解析聊天消息失败: {}", e.getMessage());
                }
            }
            return messages;
        } catch (Exception e) {
            log.warn("获取聊天记忆失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = CHAT_MEMORY_PREFIX + memoryId;
        try {
            // 删除旧数据
            redisTemplate.delete(key);

            if (messages == null || messages.isEmpty()) {
                return;
            }

            // 只保留最近 MAX_MESSAGES 条消息
            List<ChatMessage> trimmed = messages.size() > MAX_MESSAGES
                    ? messages.subList(messages.size() - (int) MAX_MESSAGES, messages.size())
                    : messages;

            // 批量写入
            for (ChatMessage message : trimmed) {
                ChatMessageData data = ChatMessageData.fromChatMessage(message);
                String json = objectMapper.writeValueAsString(data);
                redisTemplate.opsForList().rightPush(key, json);
            }

            // 设置过期时间
            redisTemplate.expire(key, EXPIRE_DAYS, TimeUnit.DAYS);

            log.debug("更新聊天记忆: memoryId={}, 消息数={}", memoryId, trimmed.size());
        } catch (Exception e) {
            log.warn("更新聊天记忆失败: {}", e.getMessage());
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String key = CHAT_MEMORY_PREFIX + memoryId;
        try {
            redisTemplate.delete(key);
            log.debug("删除聊天记忆: memoryId={}", memoryId);
        } catch (Exception e) {
            log.warn("删除聊天记忆失败: {}", e.getMessage());
        }
    }

    /**
     * 聊天消息数据传输对象
     */
    private static class ChatMessageData {
        private String type;
        private String content;

        public ChatMessageData() {}

        public ChatMessageData(String type, String content) {
            this.type = type;
            this.content = content;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public static ChatMessageData fromChatMessage(ChatMessage message) {
            return new ChatMessageData(
                    message.type().name(),
                    message.toString()
            );
        }

        public ChatMessage toChatMessage() {
            ChatMessageType type = ChatMessageType.valueOf(this.type);
            return switch (type) {
                case USER -> dev.langchain4j.data.message.UserMessage.from(extractText(this.content, "UserMessage"));
                case AI -> dev.langchain4j.data.message.AiMessage.from(extractText(this.content, "AiMessage"));
                case SYSTEM -> dev.langchain4j.data.message.SystemMessage.from(extractText(this.content, "SystemMessage"));
                default -> dev.langchain4j.data.message.UserMessage.from(this.content);
            };
        }

        /**
         * 从消息字符串中提取纯文本内容
         */
        private static String extractText(String content, String prefix) {
            if (content == null) return "";
            // LangChain4j 的 toString() 格式: "UserMessage { text = \"xxx\" }"
            int startIdx = content.indexOf("\"");
            int endIdx = content.lastIndexOf("\"");
            if (startIdx >= 0 && endIdx > startIdx) {
                return content.substring(startIdx + 1, endIdx);
            }
            return content;
        }
    }
}
