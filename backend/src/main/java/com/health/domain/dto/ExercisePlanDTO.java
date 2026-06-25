package com.health.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExercisePlanDTO {
    private String goal;
    private String intensity;
    private Integer weeklyMinutesTarget;
    private List<String> weeklyPlan;
}
