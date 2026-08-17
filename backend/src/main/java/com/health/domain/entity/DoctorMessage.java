package com.health.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("doctor_message")
public class DoctorMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long doctorId;
    private Long userId;
    private Long senderId;
    private String senderType;  // user, doctor
    private String content;
    private Integer isRead;     // 0-未读, 1-已读
    private LocalDateTime createdAt;
    private Integer deleted;
}
