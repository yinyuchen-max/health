package com.health.controller;

import com.health.common.annotation.RateLimit;
import com.health.common.annotation.RateLimit.LimitType;
import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import com.health.domain.dto.ReminderPreferenceDTO;
import com.health.domain.dto.SmartRecommendationDTO;
import com.health.domain.vo.ReminderPreferenceVO;
import com.health.service.ReminderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/reminder")
public class ReminderController {

    private final ReminderService reminderService;
    private final SecurityUtil securityUtil;

    public ReminderController(ReminderService reminderService, SecurityUtil securityUtil) {
        this.reminderService = reminderService;
        this.securityUtil = securityUtil;
    }

    @GetMapping("/preferences")
    public Result<List<ReminderPreferenceVO>> getPreferences(@RequestParam Long userId) {
        securityUtil.requireOwnerOrAdmin(userId);
        return Result.success(reminderService.getPreferencesByUserId(userId));
    }

    @PostMapping("/preferences")
    @RateLimit(key = "reminder-write", maxRequests = 20, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Boolean> savePreference(@RequestBody @Valid ReminderPreferenceDTO dto) {
        Long currentUserId = securityUtil.getCurrentUserId();
        dto.setUserId(currentUserId);
        return reminderService.savePreference(dto) ? Result.success(true) : Result.failed("保存提醒偏好失败");
    }

    @PutMapping("/preferences/{id}")
    @RateLimit(key = "reminder-write", maxRequests = 20, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Boolean> updatePreference(@PathVariable Long id, @RequestBody @Valid ReminderPreferenceDTO dto) {
        Long ownerUserId = reminderService.getOwnerUserId(id);
        if (ownerUserId == null) {
            return Result.failed("提醒偏好不存在");
        }
        securityUtil.requireRecordOwner(ownerUserId);
        dto.setId(id);
        dto.setUserId(ownerUserId);
        return reminderService.savePreference(dto) ? Result.success(true) : Result.failed("更新提醒偏好失败");
    }

    @DeleteMapping("/preferences/{id}")
    @RateLimit(key = "reminder-write", maxRequests = 20, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Boolean> deletePreference(@PathVariable Long id) {
        Long ownerUserId = reminderService.getOwnerUserId(id);
        if (ownerUserId == null) {
            return Result.failed("提醒偏好不存在");
        }
        securityUtil.requireRecordOwner(ownerUserId);
        return reminderService.deletePreference(id) ? Result.success(true) : Result.failed("删除提醒偏好失败");
    }

    @PutMapping("/preferences/{id}/toggle")
    public Result<Boolean> toggleReminder(@PathVariable Long id, @RequestBody Boolean enabled) {
        Long ownerUserId = reminderService.getOwnerUserId(id);
        if (ownerUserId == null) {
            return Result.failed("提醒偏好不存在");
        }
        securityUtil.requireRecordOwner(ownerUserId);
        return reminderService.toggleReminder(id, enabled) ? Result.success(true) : Result.failed("切换提醒状态失败");
    }

    @PutMapping("/preferences/{id}/score")
    public Result<Boolean> updateEffectivenessScore(@PathVariable Long id, @RequestBody Double score) {
        Long ownerUserId = reminderService.getOwnerUserId(id);
        if (ownerUserId == null) {
            return Result.failed("提醒偏好不存在");
        }
        securityUtil.requireRecordOwner(ownerUserId);
        return reminderService.updateEffectivenessScore(id, score) ? Result.success(true) : Result.failed("更新效果评分失败");
    }

    @GetMapping("/smart-recommendations")
    @RateLimit(key = "smart-recommendations", maxRequests = 10, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<List<SmartRecommendationDTO>> getSmartRecommendations(@RequestParam Long userId) {
        securityUtil.requireOwnerOrAdmin(userId);
        return Result.success(reminderService.generateSmartRecommendations(userId));
    }

    @PostMapping("/preferences/bulk-update")
    @RateLimit(key = "reminder-bulk", maxRequests = 5, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Boolean> bulkUpdatePreferences(@RequestBody List<ReminderPreferenceDTO> updates) {
        Long currentUserId = securityUtil.getCurrentUserId();
        // 强制所有项使用当前用户ID
        for (ReminderPreferenceDTO dto : updates) {
            dto.setUserId(currentUserId);
        }
        return reminderService.bulkUpdatePreferences(updates) ? Result.success(true) : Result.failed("批量更新失败");
    }

    @GetMapping("/preferences/export")
    public Result<String> exportPreferences(@RequestParam Long userId) {
        securityUtil.requireOwnerOrAdmin(userId);
        return Result.success(reminderService.exportPreferences(userId));
    }

    @PostMapping("/preferences/import")
    public Result<Boolean> importPreferences(@RequestParam Long userId, @RequestBody String jsonData) {
        securityUtil.requireOwnerOrAdmin(userId);
        return reminderService.importPreferences(userId, jsonData) ? Result.success(true) : Result.failed("导入偏好设置失败");
    }
}
