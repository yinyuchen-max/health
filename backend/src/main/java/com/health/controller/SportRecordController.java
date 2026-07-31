package com.health.controller;

import com.health.common.annotation.RateLimit;
import com.health.common.annotation.RateLimit.LimitType;
import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import com.health.domain.dto.SportRecordDTO;
import com.health.service.SportRecordService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/sport")
public class SportRecordController {

    @Autowired
    private SportRecordService sportRecordService;

    @Autowired
    private SecurityUtil securityUtil;

    @PostMapping("/record")
    @RateLimit(key = "sport-record-write", maxRequests = 30, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Void> addSportRecord(@RequestBody @Valid SportRecordDTO sportRecordDTO) {
        Long currentUserId = securityUtil.getCurrentUserId();
        sportRecordDTO.setUserId(currentUserId);
        sportRecordService.addSportRecord(sportRecordDTO);
        return Result.success();
    }

    @GetMapping("/records/{userId}")
    public Result<Map<String, Object>> getSportRecords(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        securityUtil.requireOwnerOrAdmin(userId);
        Map<String, Object> records = sportRecordService.getSportRecordsByUserId(userId, pageNum, pageSize);
        return Result.success(records);
    }

    @DeleteMapping("/record/{id}")
    @RateLimit(key = "sport-record-write", maxRequests = 30, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Void> deleteSportRecord(@PathVariable Long id) {
        var record = sportRecordService.getById(id);
        if (record == null) {
            return Result.failed("记录不存在");
        }
        securityUtil.requireRecordOwner(record.getUserId());
        sportRecordService.deleteSportRecord(id);
        return Result.success();
    }

    @PutMapping("/record/{id}")
    @RateLimit(key = "sport-record-write", maxRequests = 30, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Void> updateSportRecord(
            @PathVariable Long id,
            @RequestBody @Valid SportRecordDTO sportRecordDTO) {
        var record = sportRecordService.getById(id);
        if (record == null) {
            return Result.failed("记录不存在");
        }
        securityUtil.requireRecordOwner(record.getUserId());
        sportRecordDTO.setUserId(record.getUserId());
        sportRecordService.updateSportRecord(id, sportRecordDTO);
        return Result.success();
    }
}
