package com.health.controller;

import com.health.common.annotation.RateLimit;
import com.health.common.annotation.RateLimit.LimitType;
import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import com.health.service.LifestyleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/lifestyle")
public class LifestyleController {
    private final LifestyleService lifestyle;
    private final SecurityUtil security;

    public LifestyleController(LifestyleService lifestyle, SecurityUtil security) {
        this.lifestyle = lifestyle;
        this.security = security;
    }

    public record GoalsRequest(int steps, int exerciseMinutes, int waterMl, int sleepHours, int calories) {}
    public record SleepRequest(String date, String bedTime, String wakeTime, int sleepMinutes, String quality,
                               Integer deepMinutes, Integer lightMinutes, Integer remMinutes) {}

    @GetMapping("/goals")
    public Result<LifestyleService.Goals> goals() {
        return Result.success(lifestyle.goals(security.getCurrentUserId()));
    }

    @PutMapping("/goals")
    @RateLimit(key = "lifestyle-write", maxRequests = 20, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Void> saveGoals(@RequestBody GoalsRequest request) {
        lifestyle.saveGoals(security.getCurrentUserId(), request.steps(), request.exerciseMinutes(), request.waterMl(), request.sleepHours(), request.calories());
        return Result.success();
    }

    @GetMapping("/sleep")
    public Result<List<LifestyleService.SleepDay>> sleep(@RequestParam(defaultValue = "14") int limit) {
        return Result.success(lifestyle.recentSleep(security.getCurrentUserId(), limit));
    }

    @PutMapping("/sleep")
    @RateLimit(key = "lifestyle-write", maxRequests = 20, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Void> saveSleep(@RequestBody SleepRequest request) {
        lifestyle.saveSleep(security.getCurrentUserId(), request.date(), request.bedTime(), request.wakeTime(),
                request.sleepMinutes(), request.quality(), request.deepMinutes(), request.lightMinutes(), request.remMinutes());
        return Result.success();
    }
}
