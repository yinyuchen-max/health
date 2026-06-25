package com.health.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReminderPreferenceVO {
    private Long id;
    private String type;
    private String time;
    private String frequency;
    private Boolean smartMode;
    private Boolean enabled;
    private Double effectivenessScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String typeName; // Human readable name
}