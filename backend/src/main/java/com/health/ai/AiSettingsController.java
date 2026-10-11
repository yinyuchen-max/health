package com.health.ai;
import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/ai/settings")
public class AiSettingsController {
    private final AiConfigService config;
    private final SecurityUtil security;
    public AiSettingsController(AiConfigService config, SecurityUtil security) { this.config=config; this.security=security; }
    @GetMapping public Result<AiConfigService.Settings> get() { return Result.success(config.settings(security.getCurrentUserId())); }
    @PutMapping public Result<?> save(@RequestBody AiConfigService.Update update) {
        config.save(security.getCurrentUserId(), update); return Result.success("AI 配置已保存");
    }
    @DeleteMapping public Result<?> delete() { config.delete(security.getCurrentUserId()); return Result.success("AI 配置已删除"); }
}
