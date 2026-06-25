package com.health.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReminderPreferenceDTO {
    private Long id;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "提醒类型不能为空")
    private String type;

    @NotBlank(message = "提醒时间不能为空")
    private String time;

    @NotBlank(message = "重复频率不能为空")
    private String frequency;

    private Boolean smartMode;
    private Boolean enabled;
    private Double effectivenessScore;
}