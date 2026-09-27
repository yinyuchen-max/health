package com.health.domain.dto;

public class WechatRunDataDTO {
    private String encryptedData;
    private String iv;

    public String getEncryptedData() { return encryptedData; }
    public void setEncryptedData(String encryptedData) { this.encryptedData = encryptedData; }
    public String getIv() { return iv; }
    public void setIv(String iv) { this.iv = iv; }
}
