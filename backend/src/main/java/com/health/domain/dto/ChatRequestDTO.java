package com.health.domain.dto;

import lombok.Data;

@Data
public class ChatRequestDTO {
    private String message;
    private Long userId; // 用于会话隔离
}
