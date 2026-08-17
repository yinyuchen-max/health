package com.health.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 医生完整注册DTO（账号 + 医生信息一起提交）
 */
@Data
public class DoctorFullRegisterDTO {
    // === 账号信息 ===
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;

    // === 医生信息 ===
    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    @NotBlank(message = "所属医院不能为空")
    private String hospital;

    @NotBlank(message = "科室不能为空")
    private String department;

    private String title;           // 职称

    private String specialization;  // 擅长领域

    @NotBlank(message = "执业证书编号不能为空")
    private String licenseNumber;

    private String introduction;    // 个人简介
}
