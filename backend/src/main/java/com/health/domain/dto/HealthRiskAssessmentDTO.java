package com.health.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class HealthRiskAssessmentDTO {
    private String assessmentType;
    private Double riskScore;
    private String riskLevel;
    private String summary;
    private List<String> recommendations;
}
