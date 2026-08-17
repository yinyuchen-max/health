package com.health.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DoctorMessageDTO {
    private Long doctorId;   // 发送给医生时填
    private Long userId;     // 医生回复用户时填（后端强制设置）

    @NotBlank(message = "消息内容不能为空")
    private String content;
}
