package com.health.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("health_record")
public class HealthRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Double bloodPressureSystolic; // 收缩压
    private Double bloodPressureDiastolic; // 舒张压
    private Integer heartRate; // 心率
    private BigDecimal bloodSugar; // 血糖
    private Double weight; // 体重
    private String recordDate; // 记录日期 YYYY-MM-DD
    private String notes;
    private LocalDateTime createTime;
    @TableField(exist = false)
    private LocalDateTime updateTime;
    private Integer deleted;
}