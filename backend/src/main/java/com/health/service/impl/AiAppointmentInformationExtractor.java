package com.health.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.domain.dto.AppointmentExtractionContext;
import com.health.domain.dto.AppointmentExtractionResult;
import com.health.service.AppointmentInformationExtractor;
import com.health.ai.AiClient;
import com.health.common.utils.SecurityUtil;
import com.health.service.DepartmentCatalog;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class AiAppointmentInformationExtractor implements AppointmentInformationExtractor {

    private final DepartmentCatalog departmentCatalog;
    private final AiClient ai;
    private final SecurityUtil security;
    private final ObjectMapper objectMapper;

    public AiAppointmentInformationExtractor(AiClient ai, ObjectMapper objectMapper, SecurityUtil security, DepartmentCatalog departmentCatalog) {
        this.departmentCatalog=departmentCatalog; this.ai=ai; this.security=security; this.objectMapper=objectMapper;
    }

    @Override
    public AppointmentExtractionResult extract(String userMessage, AppointmentExtractionContext context) {
        String currentDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        String contextJson = writeJson(context);
        String responseText=ai.complete(ai.prepare(security.getCurrentUserId(), "appointment", Map.of(
                "currentDate", currentDate, "context", contextJson,
                "departments", String.join("、", departmentCatalog.names())), List.of(), userMessage));
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
