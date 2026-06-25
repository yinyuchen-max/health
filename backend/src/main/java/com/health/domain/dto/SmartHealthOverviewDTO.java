package com.health.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class SmartHealthOverviewDTO {
    private Long userId;
    private String generatedAt;
    private Double bmi;
    private String overallStatus;
    private List<HealthRiskAssessmentDTO> riskAssessments;
    private NutritionAdviceDTO nutritionAdvice;
    private ExercisePlanDTO exercisePlan;
    private SleepInsightDTO sleepInsight;
    private StressInsightDTO stressInsight;
    private List<String> quickTips;
}
