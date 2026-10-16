package com.health.ai;

import com.health.common.exception.ForbiddenException;
import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/ai")
public class AiSettingsController {
    private final AiConfigService config;
    private final AiAccessService access;
    private final SecurityUtil security;
    public AiSettingsController(AiConfigService config,AiAccessService access,SecurityUtil security) {
        this.config=config; this.access=access; this.security=security;
    }
    public record Status(boolean configured,boolean allowed,boolean admin,LocalDateTime expiresAt) {}
    @GetMapping({"/settings","/access"}) public Result<Status> get() {
        Long userId=security.getCurrentUserId();
        var status=access.status(userId);
        return Result.success(new Status(config.settings(userId).configured(),status.allowed(),status.admin(),
                status.expiresAt()));
    }
    @RequestMapping(value="/settings",method={RequestMethod.PUT,RequestMethod.DELETE})
    public Result<?> retiredUserKeys() {
        throw new ForbiddenException("AI 模型由平台统一提供，不再接受用户 API Key");
    }
}
