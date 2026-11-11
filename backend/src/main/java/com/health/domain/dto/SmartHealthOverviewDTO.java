package com.health.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class SmartHealthOverviewDTO {
    private Long userId;
    /** AVAILABLE：有可分析记录；NO_DATA：需要先补充记录。 */
    private String dataStatus = "AVAILABLE";
    private String message;
    /** AI 或 RULES，供界面明确区分分析来源。 */
    private String analysisSource;
    private String generatedAt;
    private Double bmi;
    private String overallStatus;
    private List<HealthRiskAssessmentDTO> riskAssessments;
    private NutritionAdviceDTO nutritionAdvice;
    private ExercisePlanDTO exercisePlan;
    private SleepInsightDTO sleepInsight;
    private StressInsightDTO stressInsight;
    private List<String> quickTips;
    /** 这次报告用到的个人记录。 */
    private List<String> evidence;
    /** 没有睡眠记录时，睡眠分析不是实测评分。 */
    private boolean sleepInferred;
    /** 压力分来自心率、运动和血压估计。 */
    private boolean stressInferred;
    /** true 表示数据未变化，返回的是上次生成的报告。 */
    private boolean fromCache;
}
