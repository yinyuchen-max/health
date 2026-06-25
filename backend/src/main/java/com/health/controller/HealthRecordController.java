package com.health.controller;

import com.health.common.utils.Result;
import com.health.domain.dto.HealthRecordDTO;
import com.health.domain.vo.HealthRecordVO;
import com.health.service.HealthRecordService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthRecordController {

    @Autowired
    private HealthRecordService healthRecordService;

    @PostMapping("/record")
    public Result<Void> addHealthRecord(@RequestBody @Valid HealthRecordDTO healthRecordDTO) {
        healthRecordService.addHealthRecord(healthRecordDTO);
        return Result.success();
    }

    @GetMapping("/records/{userId}")
    public Result<Map<String, Object>> getHealthRecords(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Map<String, Object> records = healthRecordService.getHealthRecordsByUserId(userId, pageNum, pageSize);
        return Result.success(records);
    }

    @DeleteMapping("/record/{id}")
    public Result<Void> deleteHealthRecord(@PathVariable Long id) {
        healthRecordService.deleteHealthRecord(id);
        return Result.success();
    }

    @PutMapping("/record/{id}")
    public Result<Void> updateHealthRecord(
            @PathVariable Long id,
            @RequestBody @Valid HealthRecordDTO healthRecordDTO) {
        healthRecordService.updateHealthRecord(id, healthRecordDTO);
        return Result.success();
    }
}