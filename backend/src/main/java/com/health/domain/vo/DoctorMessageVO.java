package com.health.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DoctorMessageVO {
    private Long id;
    private Long doctorId;
    private Long userId;
    private Long senderId;
    private String senderType;
    private String senderName;    // 发送者姓名（医生显示realName，用户显示username）
    private String content;
    private Integer isRead;
    private LocalDateTime createdAt;
}
