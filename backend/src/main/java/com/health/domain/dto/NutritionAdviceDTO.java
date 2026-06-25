package com.health.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class NutritionAdviceDTO {
    private String title;
    private String summary;
    private Integer dailyCalories;
    private List<String> recommendations;
}
