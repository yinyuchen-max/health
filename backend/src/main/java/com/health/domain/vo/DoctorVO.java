package com.health.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DoctorVO {
    private Long id;
    private Long userId;
    private String username;      // 来自 sys_user
    private String realName;
    private String hospital;
    private String department;
    private String title;
    private String specialization;
    private String introduction;
    private String status;        // pending, approved, rejected
    private String rejectReason;
    private LocalDateTime createdAt;

    // 统计信息（列表展示用）
    private Integer appointmentCount;
    private Integer patientCount;
}
