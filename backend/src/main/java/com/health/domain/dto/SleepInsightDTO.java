package com.health.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class SleepInsightDTO {
    private Double score;
    private String summary;
    private List<String> recommendations;
}
