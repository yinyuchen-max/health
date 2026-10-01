package com.health.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class SmartHealthOverviewDTO {
    private Long userId;
    /** AVAILABLE：有可分析记录；NO_DATA：需要先补充记录。 */
    private String dataStatus = "AVAILABLE";
    private String message;
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
