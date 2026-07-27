package com.health.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("doctor_appointment")
public class DoctorAppointment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String patientName;
    private Integer age;
    private LocalDateTime appointmentTime;
    private String phone;
    private String department;
    private LocalDateTime createdAt;
}
