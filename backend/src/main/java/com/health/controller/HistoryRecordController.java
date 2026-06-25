package com.health.controller;

import com.health.common.utils.Result;
import com.health.domain.dto.HistoryRecordDTO;
import com.health.service.HistoryRecordService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/history")
public class HistoryRecordController {

    @Autowired
    private HistoryRecordService historyRecordService;

    @PostMapping("/record")
    public Result<Void> addHistoryRecord(@RequestBody @Valid HistoryRecordDTO historyRecordDTO) {
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
        Map<String, Object> records = historyRecordService.getHistoryRecordsByUserId(
            userId, pageNum, pageSize, type, startDate, endDate
        );
        return Result.success(records);
    }

    @DeleteMapping("/record/{id}")
    public Result<Void> deleteHistoryRecord(@PathVariable Long id) {
        historyRecordService.deleteHistoryRecord(id);
        return Result.success();
    }

    @PutMapping("/record/{id}")
    public Result<Void> updateHistoryRecord(
            @PathVariable Long id,
            @RequestBody @Valid HistoryRecordDTO historyRecordDTO) {
        historyRecordService.updateHistoryRecord(id, historyRecordDTO);
        return Result.success();
    }
}
