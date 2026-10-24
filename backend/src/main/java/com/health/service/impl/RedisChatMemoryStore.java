package com.health.service.impl;

import com.health.common.config.RuntimeSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis 聊天记忆存储
 * 使用 Redis List 存储原文上下文；由 AI 服务按 token 预算压缩，不按条数提前丢弃。
 */
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemoryStore.class);

    /** Redis key 前缀 */
    private static final String CHAT_MEMORY_PREFIX = "chat:memory:";
    private static final DefaultRedisScript<Long> REPLACE = new DefaultRedisScript<>("""
            redis.call('DEL', KEYS[1])
            for i = 2, #ARGV do redis.call('RPUSH', KEYS[1], ARGV[i]) end
            if #ARGV > 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return 1
            """, Long.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RuntimeSettings settings;

    public RedisChatMemoryStore(RedisTemplate<String, Object> redisTemplate, RuntimeSettings settings) {
        this.settings = settings;
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
                    throw new IllegalStateException("聊天上下文损坏", e);
                }
            }
            return messages;
        } catch (Exception e) {
            // Do not silently present a partial or empty medical history as complete.
            throw new IllegalStateException("无法读取聊天上下文", e);
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = CHAT_MEMORY_PREFIX + memoryId;
        try {
            // Serialize everything before mutation; one Lua operation replaces data and TTL atomically.
            List<Object> args = new ArrayList<>();
            args.add(settings.getChatMemory().getTtl().toMillis());
            for (ChatMessage message : messages == null ? List.<ChatMessage>of() : messages) {
                ChatMessageData data = ChatMessageData.fromChatMessage(message);
                String json = objectMapper.writeValueAsString(data);
                args.add(json);
            }
            Long result=redisTemplate.execute(REPLACE,List.of(key),args.toArray());
            if (!Long.valueOf(1).equals(result)) throw new IllegalStateException("聊天上下文未保存");
        } catch (Exception e) {
            throw new IllegalStateException("无法保存聊天上下文", e);
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

    /** Forgetting must fail visibly if Redis cannot be cleared; callers roll back database changes. */
    public void clearStrict(Long userId) {
        redisTemplate.delete(CHAT_MEMORY_PREFIX + userId);
    }

    /**
     * 聊天消息数据传输对象
     */
    private static class ChatMessageData {
        private String type;
        private String content;
        private int formatVersion;

        public ChatMessageData() {}

        public ChatMessageData(String type, String content) {
            this.type = type;
            this.content = content;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public int getFormatVersion() { return formatVersion; }
        public void setFormatVersion(int formatVersion) { this.formatVersion = formatVersion; }

        public static ChatMessageData fromChatMessage(ChatMessage message) {
            String text;
            if(message instanceof UserMessage user) text=user.singleText();
            else if(message instanceof AiMessage ai) text=ai.text();
            else if(message instanceof SystemMessage system) text=system.text();
            else throw new IllegalArgumentException("不支持的聊天消息类型");
            var data=new ChatMessageData(message.type().name(),text);
            data.setFormatVersion(2);
            return data;
        }

        public ChatMessage toChatMessage() {
            ChatMessageType type = ChatMessageType.valueOf(this.type);
            String text=formatVersion==2 ? content : extractText(content);
            return switch (type) {
                case USER -> UserMessage.from(text);
                case AI -> AiMessage.from(text);
                case SYSTEM -> SystemMessage.from(text);
                default -> throw new IllegalArgumentException("不支持的聊天消息类型");
            };
        }

        /**
         * 从消息字符串中提取纯文本内容
         */
        private static String extractText(String content) {
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
