package com.health.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sport_record")
public class SportRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String sportType; // 运动类型
    private Integer duration; // 运动时长(分钟)
    private Double calories; // 消耗卡路里
    private String intensity; // 强度: low, medium, high
    private String recordDate;
    private String notes;
    private LocalDateTime createTime;
    @TableField(exist = false)
    private LocalDateTime updateTime;
    private Integer deleted;
}