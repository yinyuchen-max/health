package com.health.controller;

import com.health.common.annotation.RateLimit;
import com.health.common.annotation.RateLimit.LimitType;
import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import com.health.domain.dto.HistoryRecordDTO;
import com.health.service.HistoryRecordService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/history")
public class HistoryRecordController {

    @Autowired
    private HistoryRecordService historyRecordService;

    @Autowired
    private SecurityUtil securityUtil;

    @PostMapping("/record")
    @RateLimit(key = "history-record-write", maxRequests = 30, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Void> addHistoryRecord(@RequestBody @Valid HistoryRecordDTO historyRecordDTO) {
        Long currentUserId = securityUtil.getCurrentUserId();
        historyRecordDTO.setUserId(currentUserId);
        historyRecordService.addHistoryRecord(historyRecordDTO);
        return Result.success();
    }

    @GetMapping("/records/{userId}")
    public Result<Map<String, Object>> getHistoryRecords(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        securityUtil.requireOwnerOrAdmin(userId);
        Map<String, Object> records = historyRecordService.getHistoryRecordsByUserId(
            userId, pageNum, pageSize, type, startDate, endDate
        );
        return Result.success(records);
    }

    @DeleteMapping("/record/{id}")
    @RateLimit(key = "history-record-write", maxRequests = 30, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Void> deleteHistoryRecord(@PathVariable Long id) {
        var record = historyRecordService.getById(id);
        if (record == null) {
            return Result.failed("记录不存在");
        }
        securityUtil.requireRecordOwner(record.getUserId());
        historyRecordService.deleteHistoryRecord(id);
        return Result.success();
    }

    @PutMapping("/record/{id}")
    @RateLimit(key = "history-record-write", maxRequests = 30, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Void> updateHistoryRecord(
            @PathVariable Long id,
            @RequestBody @Valid HistoryRecordDTO historyRecordDTO) {
        var record = historyRecordService.getById(id);
        if (record == null) {
            return Result.failed("记录不存在");
        }
        securityUtil.requireRecordOwner(record.getUserId());
        historyRecordDTO.setUserId(record.getUserId());
        historyRecordService.updateHistoryRecord(id, historyRecordDTO);
        return Result.success();
    }
}
