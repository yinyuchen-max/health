package com.health.domain.dto;

import lombok.Data;
import com.health.domain.vo.UserVO;

@Data
public class WechatLoginResponseDTO {
    private String token;
    private String refreshToken;
    private long expiresIn;
    private long refreshExpiresAt;
    private UserVO userInfo;
    @Override public String toString() { return "WechatLoginResponseDTO[redacted]"; }
}
