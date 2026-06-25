package com.health.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SportRecordDTO {
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "运动类型不能为空")
    private String sportType;

    @NotNull(message = "运动时长不能为空")
    private Integer duration;

    private Double calories;

    @NotBlank(message = "强度不能为空")
    private String intensity;

    @NotBlank(message = "记录日期不能为空")
    private String recordDate;

    private String notes;
}