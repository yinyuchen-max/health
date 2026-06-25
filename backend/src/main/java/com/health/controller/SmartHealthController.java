package com.health.controller;

import com.health.common.utils.Result;
import com.health.domain.dto.SmartHealthOverviewDTO;
import com.health.service.SmartHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/smart-health")
public class SmartHealthController {

    private final SmartHealthService smartHealthService;

    public SmartHealthController(SmartHealthService smartHealthService) {
        this.smartHealthService = smartHealthService;
    }

    @GetMapping("/overview")
    public Result<SmartHealthOverviewDTO> getOverview(@RequestParam Long userId) {
        return Result.success(smartHealthService.generateOverview(userId));
    }
}
