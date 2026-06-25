package com.health.domain.dto;

import lombok.Data;

@Data
public class SmartRecommendationDTO {
    private String type;
    private String content;
    private Double confidenceScore;
    private String reason;
}