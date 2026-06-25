package com.health.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class StressInsightDTO {
    private Double score;
    private String level;
    private String summary;
    private List<String> recommendations;
}
