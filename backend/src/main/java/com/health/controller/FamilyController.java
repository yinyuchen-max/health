package com.health.controller;

import com.health.common.annotation.RateLimit;
import com.health.common.annotation.RateLimit.LimitType;
import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import com.health.domain.dto.FamilyDtos.*;
import com.health.domain.dto.SmartHealthOverviewDTO;
import com.health.service.FamilyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/family")
public class FamilyController {
    private final FamilyService family;
    private final SecurityUtil security;
    public FamilyController(FamilyService family, SecurityUtil security) {
        this.family = family;
        this.security = security;
    }
    @GetMapping("/members")
    public Result<List<Binding>> members() { return Result.success(family.list(security.getCurrentUserId())); }

    @PostMapping("/invitations")
    @RateLimit(key = "family-invite", maxRequests = 5, timeWindow = 1, limitBy = LimitType.USER)
    public Result<Void> invite(@Valid @RequestBody Invite invite) {
        family.invite(security.getCurrentUserId(), invite);
        return Result.success();
    }
    @PutMapping("/invitations/{id}")
    public Result<Void> decide(@PathVariable long id, @Valid @RequestBody Decision decision) {
        family.decide(security.getCurrentUserId(), id, decision.accept());
        return Result.success();
    }
    @DeleteMapping("/members/{id}")
    public Result<Void> remove(@PathVariable long id) {
        family.remove(security.getCurrentUserId(), id);
        return Result.success();
    }
    @GetMapping("/members/{id}/report")
    @RateLimit(key = "family-report", maxRequests = 10, timeWindow = 1, limitBy = LimitType.USER)
    public Result<SmartHealthOverviewDTO> report(@PathVariable long id) {
        return Result.success(family.report(security.getCurrentUserId(), id));
    }
    @PostMapping("/members/{id}/reminders")
    @RateLimit(key = "family-remind", maxRequests = 10, timeWindow = 1, limitBy = LimitType.USER)
    public Result<Void> remind(@PathVariable long id, @Valid @RequestBody SendReminder reminder) {
        family.remind(security.getCurrentUserId(), id, reminder.type());
        return Result.success();
    }
    @GetMapping("/reminders")
    public Result<List<Reminder>> reminders() { return Result.success(family.reminders(security.getCurrentUserId())); }

    @PutMapping("/reminders/{id}/read")
    public Result<Void> read(@PathVariable long id) {
        family.acknowledge(security.getCurrentUserId(), id, false);
        return Result.success();
    }
    @PutMapping("/reminders/{id}/complete")
    public Result<Void> complete(@PathVariable long id) {
        family.acknowledge(security.getCurrentUserId(), id, true);
        return Result.success();
    }
}
