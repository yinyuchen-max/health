package com.health.domain.dto;

import lombok.Data;

@Data
public class WechatLoginResponseDTO {
    private String token;
    private Object userInfo;
}
