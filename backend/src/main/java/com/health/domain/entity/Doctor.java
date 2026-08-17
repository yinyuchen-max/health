package com.health.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("doctor")
public class Doctor {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String realName;
    private String hospital;
    private String department;
    private String title;          // 主任医师/副主任医师/主治医师/住院医师
    private String specialization; // 擅长领域
    private String licenseNumber;  // 执业证书编号
    private String introduction;   // 个人简介
    private String status;         // pending, approved, rejected
    private String rejectReason;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
