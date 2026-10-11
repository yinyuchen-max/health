package com.health.domain.dto;

import lombok.Data;

@Data
public class WechatLoginResponseDTO {
    private String token;
    private String refreshToken;
    private long expiresIn;
    private long refreshExpiresAt;
    private Object userInfo;
    @Override public String toString() { return "WechatLoginResponseDTO[redacted]"; }
}
