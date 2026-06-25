package com.health.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("reminder_preference")
public class ReminderPreference {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String type; // bloodPressure, bloodSugar, weight, exercise
    private String time; // HH:mm format
    private String frequency; // daily, weekly, custom
    private Boolean smartMode;
    private Boolean enabled;
    private Double effectivenessScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}