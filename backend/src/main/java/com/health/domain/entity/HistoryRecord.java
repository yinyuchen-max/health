package com.health.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("history_record")
public class HistoryRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String type; // health, sport, reminder
    private Long sourceRecordId;
    private String title;
    private String content;
    private LocalDateTime recordDate;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
