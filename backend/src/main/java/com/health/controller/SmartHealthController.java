package com.health.controller;

import com.health.common.annotation.RateLimit;
import com.health.common.annotation.RateLimit.LimitType;
import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import com.health.domain.dto.SmartHealthOverviewDTO;
import com.health.service.SmartHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/smart-health")
public class SmartHealthController {

    private final SmartHealthService smartHealthService;
    private final SecurityUtil securityUtil;

    public SmartHealthController(SmartHealthService smartHealthService, SecurityUtil securityUtil) {
        this.smartHealthService = smartHealthService;
        this.securityUtil = securityUtil;
    }

    @GetMapping("/overview")
    @RateLimit(key = "smart-overview", maxRequests = 10, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<SmartHealthOverviewDTO> getOverview(@RequestParam Long userId) {
        // 校验是否有权查看该用户的智能分析数据
        securityUtil.requireOwnerOrAdmin(userId);
        return Result.success(smartHealthService.generateOverview(userId));
    }
}
