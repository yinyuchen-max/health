package com.health.service.impl;

import com.health.domain.dto.ChatResponseDTO;
import com.health.domain.vo.HealthRecordVO;
import com.health.domain.vo.SportRecordVO;
import com.health.service.ChatService;
import com.health.service.HealthRecordService;
import com.health.service.SportRecordService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    private static final String SYSTEM_PROMPT = """
            你是一位专业的健康管理顾问，具备以下能力：
            1. 解答用户关于健康、饮食、运动、睡眠、心理健康等方面的问题
            2. 根据用户描述的症状提供初步分析和建议（不代替医生诊断）
            3. 提供科学的运动计划和饮食建议
            4. 帮助用户解读常见的健康指标（BMI、血压、血糖、心率等）
            
            重要规则：
            - 如果用户描述紧急症状（胸痛、呼吸困难、严重出血等），请立即建议就医
            - 所有建议仅供参考，不构成医疗诊断
            - 回答简洁、专业、易懂，用中文回复
            - 如不确定，请诚实说明
            """;

    private final ChatModel chatModel;
    private final HealthRecordService healthRecordService;
    private final SportRecordService sportRecordService;
    // 使用 ConcurrentHashMap 缓存每个用户的对话记忆，确保线程安全
    private final ConcurrentHashMap<Object, MessageWindowChatMemory> userMemories = new ConcurrentHashMap<>();

    public ChatServiceImpl(ChatModel chatModel, HealthRecordService healthRecordService, SportRecordService sportRecordService) {
        this.chatModel = chatModel;
        this.healthRecordService = healthRecordService;
        this.sportRecordService = sportRecordService;
    }

    @Override
    public ChatResponseDTO chat(String userMessage, Long userId) {
        if (userMessage == null || userMessage.isBlank()) {
            ChatResponseDTO dto = new ChatResponseDTO();
            dto.setReply("请告诉我您想咨询的健康问题，我会尽力为您解答。");
            return dto;
        }

        try {
            // 获取当前用户的对话记忆（如果不存在则创建）
            Object memoryId = userId != null ? userId : "default";
            log.info("AI 对话请求 - userId: {}, memoryId: {}", userId, memoryId);
            
            var chatMemory = userMemories.computeIfAbsent(memoryId, id -> 
                MessageWindowChatMemory.builder()
                    .id(id)
                    .maxMessages(20)
                    .build()
            );
            
            log.info("当前用户历史消息数: {}", chatMemory.messages().size());
            
            // 构建完整的消息列表（系统提示 + 用户健康数据 + 历史对话 + 当前用户消息）
            var messages = new java.util.ArrayList<dev.langchain4j.data.message.ChatMessage>();
            
            // 添加系统提示
            String systemPromptWithUserData = buildSystemPromptWithUserData(userId);
            messages.add(SystemMessage.from(systemPromptWithUserData));
            
            // 添加历史对话
            messages.addAll(chatMemory.messages());
            
            // 添加当前用户消息
            messages.add(UserMessage.from(userMessage));
            
            log.info("发送给 AI 的总消息数: {}", messages.size());
            
            // 调用 AI 模型
            var response = chatModel.chat(messages);
            String aiReply = response.aiMessage().text();
            
            // 将用户消息和 AI 回复保存到记忆中
            chatMemory.add(UserMessage.from(userMessage));
            chatMemory.add(AiMessage.from(aiReply));
            
            log.info("保存后记忆中的消息数: {}", chatMemory.messages().size());

            ChatResponseDTO dto = new ChatResponseDTO();
            dto.setReply(aiReply != null ? aiReply : "抱歉，暂时无法生成回复，请稍后重试。");
            return dto;
        } catch (Exception e) {
            log.error("AI 对话失败: {}", e.getMessage(), e);
            ChatResponseDTO dto = new ChatResponseDTO();
            dto.setReply("抱歉，AI 服务暂时不可用，请稍后再试。您也可以查看「智能健康」页面获取系统评估。");
            return dto;
        }
    }
    
    /**
     * 构建包含用户健康数据的系统提示
     */
    private String buildSystemPromptWithUserData(Long userId) {
        StringBuilder prompt = new StringBuilder(SYSTEM_PROMPT);
        
        if (userId != null) {
            // 查询用户最近的健康记录
            Map<String, Object> healthData = healthRecordService.getHealthRecordsByUserId(userId, 1, 5);
            List<?> records = (List<?>) healthData.get("records");
            
            // 查询用户最近的运动记录
            Map<String, Object> sportData = sportRecordService.getSportRecordsByUserId(userId, 1, 5);
            List<?> sportRecords = (List<?>) sportData.get("records");
            
            // 如果有健康数据，添加到系统提示中
            if (records != null && !records.isEmpty()) {
                prompt.append("\n\n【用户最新健康数据】\n");
                for (Object record : records) {
                    if (record instanceof HealthRecordVO) {
                        HealthRecordVO hr = (HealthRecordVO) record;
                        prompt.append("- 日期: ").append(hr.getRecordDate()).append(", ");
                        if (hr.getBloodPressureSystolic() != null && hr.getBloodPressureDiastolic() != null) {
                            prompt.append("血压: ").append(hr.getBloodPressureSystolic())
                                .append("/").append(hr.getBloodPressureDiastolic()).append(" mmHg, ");
                        }
                        if (hr.getHeartRate() != null) {
                            prompt.append("心率: ").append(hr.getHeartRate()).append(" bpm, ");
                        }
                        if (hr.getBloodSugar() != null) {
                            prompt.append("血糖: ").append(hr.getBloodSugar()).append(" mmol/L, ");
                        }
                        if (hr.getWeight() != null) {
                            prompt.append("体重: ").append(hr.getWeight()).append(" kg, ");
                        }
                        prompt.append("\n");
                    }
                }
            }
            
            // 如果有运动数据，添加到系统提示中
            if (sportRecords != null && !sportRecords.isEmpty()) {
                prompt.append("\n【用户最近运动记录】\n");
                for (Object record : sportRecords) {
                    if (record instanceof SportRecordVO) {
                        SportRecordVO sr = (SportRecordVO) record;
                        prompt.append("- 日期: ").append(sr.getRecordDate())
                            .append(", 运动类型: ").append(sr.getSportType())
                            .append(", 时长: ").append(sr.getDuration()).append(" 分钟")
                            .append(", 消耗: ").append(sr.getCalories()).append(" kcal\n");
                    }
                }
            }
            
            prompt.append("\n重要说明：以上数据是用户的真实健康记录，请在回答问题时参考这些数据，提供更个性化、更准确的建议。\n");
        }
        
        return prompt.toString();
    }
}
