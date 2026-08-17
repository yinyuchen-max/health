package com.health.controller;

import com.health.common.annotation.RateLimit;
import com.health.common.annotation.RateLimit.LimitType;
import com.health.common.exception.ForbiddenException;
import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import com.health.domain.dto.HealthRecordDTO;
import com.health.domain.vo.HealthRecordVO;
import com.health.service.DoctorService;
import com.health.service.HealthRecordService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/health")
public class HealthRecordController {

    @Autowired
    private HealthRecordService healthRecordService;

    @Autowired
    private SecurityUtil securityUtil;

    @Autowired
    private DoctorService doctorService;

    @PostMapping("/record")
    @RateLimit(key = "health-record-write", maxRequests = 30, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Void> addHealthRecord(@RequestBody @Valid HealthRecordDTO healthRecordDTO) {
        // 强制使用当前登录用户ID，防止伪造userId
        Long currentUserId = securityUtil.getCurrentUserId();
        healthRecordDTO.setUserId(currentUserId);
        healthRecordService.addHealthRecord(healthRecordDTO);
        return Result.success();
    }

    @GetMapping("/records/{userId}")
    public Result<Map<String, Object>> getHealthRecords(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        // 权限：本人、管理员、或存在预约关系的医生
        Long currentUserId = securityUtil.getCurrentUserId();
        boolean allowed = currentUserId.equals(userId) || isAdmin() || doctorService.canViewPatient(currentUserId, userId);
        if (!allowed) {
            throw new ForbiddenException("无权查看该用户的健康报告");
        }
        Map<String, Object> records = healthRecordService.getHealthRecordsByUserId(userId, pageNum, pageSize);
        return Result.success(records);
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @DeleteMapping("/record/{id}")
    @RateLimit(key = "health-record-write", maxRequests = 30, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Void> deleteHealthRecord(@PathVariable Long id) {
        // 先查询记录所属用户，再校验权限
        var record = healthRecordService.getById(id);
        if (record == null) {
            return Result.failed("记录不存在");
        }
        securityUtil.requireRecordOwner(record.getUserId());
        healthRecordService.deleteHealthRecord(id);
        return Result.success();
    }

    @PutMapping("/record/{id}")
    @RateLimit(key = "health-record-write", maxRequests = 30, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Void> updateHealthRecord(
            @PathVariable Long id,
            @RequestBody @Valid HealthRecordDTO healthRecordDTO) {
        // 校验记录所有权
        var record = healthRecordService.getById(id);
        if (record == null) {
            return Result.failed("记录不存在");
        }
        securityUtil.requireRecordOwner(record.getUserId());
        // 强制使用原记录所属用户ID，防止越权修改
        healthRecordDTO.setUserId(record.getUserId());
        healthRecordService.updateHealthRecord(id, healthRecordDTO);
        return Result.success();
    }
}
