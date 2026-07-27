package com.health.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.domain.dto.AppointmentExtractionContext;
import com.health.domain.dto.AppointmentExtractionResult;
import com.health.service.AppointmentInformationExtractor;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class AiAppointmentInformationExtractor implements AppointmentInformationExtractor {

    private static final String DEPARTMENTS =
            "内科、外科、儿科、妇科、皮肤科、眼科、耳鼻喉科、口腔科、骨科、神经内科、心血管内科、消化内科、呼吸内科、泌尿外科";

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public AiAppointmentInformationExtractor(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    @Override
    public AppointmentExtractionResult extract(String userMessage, AppointmentExtractionContext context) {
        String currentDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        String contextJson = writeJson(context);
        String systemPrompt = """
                你是医疗预约信息提取器。请从用户消息中提取预约字段，只返回一个合法 JSON 对象，不要使用 Markdown，不要解释。

                今天日期：%s，时区：Asia/Shanghai。
                当前已经收集的信息：%s
                支持的科室：%s。

                返回格式：
                {
                  "appointmentIntent": true或false,
                  "patientName": "姓名或null",
                  "age": 年龄整数或null,
                  "appointmentTime": "yyyy-MM-dd HH:mm或null",
                  "phone": "联系电话或null",
                  "department": "标准科室名称或null",
                  "cancellation": true或false
                }

                规则：
                1. 字段顺序可以任意，不得把“预约”“挂号”“确认”等操作词当作姓名。
                2. 只提取用户本次消息明确提供或可可靠推断的信息，不得编造缺失字段。
                3. 将“泌尿科”规范为“泌尿外科”、“心内科”规范为“心血管内科”、“牙科”规范为“口腔科”。
                4. 相对日期按照今天日期换算；无法确定具体日期或时间时 appointmentTime 返回 null。
                5. 用户表达取消、不约了时 cancellation 返回 true。
                """.formatted(currentDate, contextJson, DEPARTMENTS);

        var response = chatModel.chat(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userMessage)
        ));
        String responseText = response.aiMessage().text();
        return readResult(responseText);
    }

    private String writeJson(AppointmentExtractionContext context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化预约上下文", exception);
        }
    }

    private AppointmentExtractionResult readResult(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            throw new IllegalStateException("AI未返回预约提取结果");
        }
        int jsonStart = responseText.indexOf('{');
        int jsonEnd = responseText.lastIndexOf('}');
        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            throw new IllegalStateException("AI返回的预约信息不是合法JSON");
        }
        try {
            return objectMapper.readValue(
                    responseText.substring(jsonStart, jsonEnd + 1),
                    AppointmentExtractionResult.class
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法解析AI返回的预约信息", exception);
        }
    }
}
