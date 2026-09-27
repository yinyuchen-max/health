package com.health.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class HealthRecordDTO {
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    private Double bloodPressureSystolic;
    private Double bloodPressureDiastolic;
    private Integer heartRate;
    private BigDecimal bloodSugar;
    private Double weight;

    @NotBlank(message = "记录日期不能为空")
    private String recordDate;

    private String notes;
}