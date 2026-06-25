package com.health.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class HealthRecordVO {
    private Long id;
    private Long userId;
    private Double bloodPressureSystolic;
    private Double bloodPressureDiastolic;
    private Integer heartRate;
    private BigDecimal bloodSugar;
    private Double weight;
    private String recordDate;
    private String notes;
    private LocalDateTime createTime;
}