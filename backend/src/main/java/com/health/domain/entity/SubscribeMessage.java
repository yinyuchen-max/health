package com.health.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("subscribe_message")
public class SubscribeMessage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String openid;
    private String templateId;
    private Integer status; // 1-有效，0-已使用
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
