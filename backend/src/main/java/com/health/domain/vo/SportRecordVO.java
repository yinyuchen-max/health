package com.health.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SportRecordVO {
    private Long id;
    private Long userId;
    private String sportType;
    private Integer duration;
    private Double calories;
    private String intensity;
    private String recordDate;
    private String notes;
    private LocalDateTime createTime;
}