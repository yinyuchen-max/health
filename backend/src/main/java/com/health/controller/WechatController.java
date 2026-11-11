package com.health.controller;

import com.health.common.annotation.RateLimit;
import com.health.common.annotation.RateLimit.LimitType;
import com.health.common.exception.UnauthorizedException;
import com.health.common.security.AuthCookies;
import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import com.health.domain.dto.AuthTokens;
import com.health.domain.dto.WechatLoginDTO;
import com.health.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/user/wechat")
public class WechatController {
    private final WechatQrLoginService qr;
    private final AuthSessionService sessions;
    private final AuthCookies cookies;
    private final SecurityUtil security;
    private final WechatAuthService wechat;
    private final WechatStepService steps;
    private final WechatProfileService profiles;
    public WechatController(WechatQrLoginService qr, AuthSessionService sessions, AuthCookies cookies,
                            SecurityUtil security, WechatAuthService wechat, WechatStepService steps, WechatProfileService profiles) {
        this.qr = qr; this.sessions = sessions; this.cookies = cookies; this.security = security;
        this.wechat = wechat; this.steps = steps; this.profiles = profiles;
    }

    public record BrowserProof(@NotBlank @Pattern(regexp = "[a-f0-9]{32}") String ticket,
                               @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String proof) {
        @Override public String toString() { return "BrowserProof[redacted]"; }
    }
    public record Confirmation(@NotBlank @Pattern(regexp = "[a-f0-9]{32}") String ticket) {}
    public record Profile(@Size(min = 1, max = 64) String nickname, @Size(max = 1_400_000) String avatarBase64) {
        @Override public String toString() { return "Profile[redacted]"; }
    }

    @PostMapping("/web-session")
    @RateLimit(key = "wechat-qr-create", maxRequests = 10, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.IP)
    public Result<WechatQrLoginService.QrSession> create() { return Result.success(qr.create()); }

    @PostMapping("/web-session/status")
    @RateLimit(key = "wechat-qr-poll", maxRequests = 90, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.IP)
    public Result<Map<String, String>> status(@RequestBody @Valid BrowserProof proof) {
        return Result.success(Map.of("status", qr.status(proof.ticket(), proof.proof())));
    }

    @PostMapping("/web-session/consume")
    @RateLimit(key = "wechat-qr-consume", maxRequests = 10, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.IP)
    public Result<AuthTokens> consume(@RequestBody @Valid BrowserProof proof, HttpServletRequest request, HttpServletResponse response) {
        var user = qr.consume(proof.ticket(), proof.proof());
        String previous = cookies.read(request, AuthCookies.REFRESH);
        if (previous != null) {
            try { sessions.revokeRefresh(previous, AuthSessionService.WEB); }
            catch (UnauthorizedException ignored) { }
        }
        var tokens = sessions.issue(user, AuthSessionService.WEB);
        cookies.writeRefresh(response, tokens.refreshToken(), tokens.refreshExpiresAt());
        return Result.success(tokens.forBrowser());
    }

    @PostMapping("/web-confirm")
    @RateLimit(key = "wechat-qr-confirm", maxRequests = 10, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Void> confirm(@RequestBody @Valid Confirmation confirmation) {
        qr.confirm(confirmation.ticket(), security.getCurrentUserId());
        return Result.success();
    }

    @PostMapping("/session")
    @RateLimit(key = "wechat-session", maxRequests = 20, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Void> refreshWechatSession(@RequestBody @Valid WechatLoginDTO dto) {
        wechat.refreshSession(security.getCurrentUserId(), dto.getCode());
        return Result.success();
    }

    @GetMapping("/steps")
    public Result<WechatStepService.Summary> steps() { return Result.success(steps.summary(security.getCurrentUserId())); }

    @PutMapping("/profile")
    @RateLimit(key = "wechat-profile", maxRequests = 10, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Void> profile(@RequestBody @Valid Profile profile) {
        profiles.save(security.getCurrentUserId(), profile.nickname(), profile.avatarBase64());
        return Result.success();
    }

    @GetMapping("/avatar/{key}")
    public ResponseEntity<byte[]> avatar(@PathVariable String key) {
        byte[] data = profiles.avatar(key);
        return data == null ? ResponseEntity.notFound().build() : ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).body(data);
    }
}
