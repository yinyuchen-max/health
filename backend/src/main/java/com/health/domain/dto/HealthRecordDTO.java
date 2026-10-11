package com.health.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class HealthRecordDTO {
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    // 修改时必须携带读取记录时的版本；新增时由服务端初始化。
    @PositiveOrZero(message = "版本号不能为负数")
    private Integer version;

    private Double bloodPressureSystolic;
    private Double bloodPressureDiastolic;
    private Integer heartRate;
    private BigDecimal bloodSugar;
    private Double weight;

    @NotBlank(message = "记录日期不能为空")
    private String recordDate;

    private String notes;
}
