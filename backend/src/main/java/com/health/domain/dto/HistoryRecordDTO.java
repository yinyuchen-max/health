package com.health.domain.dto;

import lombok.Data;

@Data
public class HistoryRecordDTO {
    private Long id;
    private Long userId;
    private String type; // health, sport, reminder
    private String title;
    private String content;
    private String recordDate; // YYYY-MM-DD format
}
