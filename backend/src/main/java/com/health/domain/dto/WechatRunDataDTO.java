package com.health.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class WechatRunDataDTO {
    @NotBlank @Size(max = 65536) private String encryptedData;
    @NotBlank @Size(max = 64) private String iv;

    public String getEncryptedData() { return encryptedData; }
    public void setEncryptedData(String encryptedData) { this.encryptedData = encryptedData; }
    public String getIv() { return iv; }
    public void setIv(String iv) { this.iv = iv; }
}
