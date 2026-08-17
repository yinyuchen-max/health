package com.health.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DoctorRegisterDTO {
    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    @NotBlank(message = "所属医院不能为空")
    private String hospital;

    @NotBlank(message = "科室不能为空")
    private String department;

    private String title;          // 职称

    private String specialization; // 擅长领域

    @NotBlank(message = "执业证书编号不能为空")
    private String licenseNumber;

    private String introduction;   // 个人简介
}
