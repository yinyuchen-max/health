package com.health.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthTokens(String token, String refreshToken, long expiresIn, long refreshExpiresAt) {
    public AuthTokens forBrowser() { return new AuthTokens(token, null, expiresIn, refreshExpiresAt); }
    @Override public String toString() { return "AuthTokens[redacted]"; }
}
