package com.health.controller;

import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import com.health.common.annotation.RateLimit;
import com.health.common.annotation.RateLimit.LimitType;
import com.health.memory.LongTermMemoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/chat/memories")
public class UserMemoryController {
    private final LongTermMemoryService memories;
    private final SecurityUtil security;
    public UserMemoryController(LongTermMemoryService memories, SecurityUtil security) {
        this.memories = memories; this.security = security;
    }
    public record SettingsUpdate(@NotNull Boolean enabled) {}
    public record Edit(@NotBlank @Size(max=500) String content) {}
    @GetMapping public Result<LongTermMemoryService.View> list() {
        return Result.success(memories.view(security.getCurrentUserId()));
    }
    @PutMapping("/settings")
    public Result<Void> settings(@Valid @RequestBody SettingsUpdate update) {
        memories.setEnabled(security.getCurrentUserId(), update.enabled()); return Result.success();
    }
    @PutMapping("/{id}")
    public Result<Void> edit(@PathVariable Long id, @Valid @RequestBody Edit edit) {
        memories.edit(security.getCurrentUserId(), id, edit.content()); return Result.success();
    }
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        memories.delete(security.getCurrentUserId(), id); return Result.success();
    }
    @DeleteMapping
    @RateLimit(key="memory-clear", maxRequests=10, timeWindow=1, timeUnit=TimeUnit.MINUTES, limitBy=LimitType.USER)
    public Result<Void> clear() {
        memories.clear(security.getCurrentUserId()); return Result.success();
    }
}
