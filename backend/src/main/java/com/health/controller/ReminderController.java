package com.health.controller;

import com.health.common.utils.Result;
import com.health.domain.dto.ReminderPreferenceDTO;
import com.health.domain.dto.SmartRecommendationDTO;
import com.health.domain.vo.ReminderPreferenceVO;
import com.health.service.ReminderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reminder")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping("/preferences")
    public Result<List<ReminderPreferenceVO>> getPreferences(@RequestParam Long userId) {
        return Result.success(reminderService.getPreferencesByUserId(userId));
    }

    @PostMapping("/preferences")
    public Result<Boolean> savePreference(@RequestBody @Valid ReminderPreferenceDTO dto) {
        return reminderService.savePreference(dto) ? Result.success(true) : Result.failed("保存提醒偏好失败");
    }

    @PutMapping("/preferences/{id}")
    public Result<Boolean> updatePreference(@PathVariable Long id, @RequestBody @Valid ReminderPreferenceDTO dto) {
        dto.setId(id);
        return reminderService.savePreference(dto) ? Result.success(true) : Result.failed("更新提醒偏好失败");
    }

    @DeleteMapping("/preferences/{id}")
    public Result<Boolean> deletePreference(@PathVariable Long id) {
        return reminderService.deletePreference(id) ? Result.success(true) : Result.failed("删除提醒偏好失败");
    }

    @PutMapping("/preferences/{id}/toggle")
    public Result<Boolean> toggleReminder(@PathVariable Long id, @RequestBody Boolean enabled) {
        return reminderService.toggleReminder(id, enabled) ? Result.success(true) : Result.failed("切换提醒状态失败");
    }

    @PutMapping("/preferences/{id}/score")
    public Result<Boolean> updateEffectivenessScore(@PathVariable Long id, @RequestBody Double score) {
        return reminderService.updateEffectivenessScore(id, score) ? Result.success(true) : Result.failed("更新效果评分失败");
    }

    @GetMapping("/smart-recommendations")
    public Result<List<SmartRecommendationDTO>> getSmartRecommendations(@RequestParam Long userId) {
        return Result.success(reminderService.generateSmartRecommendations(userId));
    }

    @PostMapping("/preferences/bulk-update")
    public Result<Boolean> bulkUpdatePreferences(@RequestBody List<ReminderPreferenceDTO> updates) {
        return reminderService.bulkUpdatePreferences(updates) ? Result.success(true) : Result.failed("批量更新失败");
    }

    @GetMapping("/preferences/export")
    public Result<String> exportPreferences(@RequestParam Long userId) {
        return Result.success(reminderService.exportPreferences(userId));
    }

    @PostMapping("/preferences/import")
    public Result<Boolean> importPreferences(@RequestParam Long userId, @RequestBody String jsonData) {
        return reminderService.importPreferences(userId, jsonData) ? Result.success(true) : Result.failed("导入偏好设置失败");
    }
}
