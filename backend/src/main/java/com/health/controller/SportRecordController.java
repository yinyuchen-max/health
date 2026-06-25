package com.health.controller;

import com.health.common.utils.Result;
import com.health.domain.dto.SportRecordDTO;
import com.health.service.SportRecordService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sport")
public class SportRecordController {

    @Autowired
    private SportRecordService sportRecordService;

    @PostMapping("/record")
    public Result<Void> addSportRecord(@RequestBody @Valid SportRecordDTO sportRecordDTO) {
        sportRecordService.addSportRecord(sportRecordDTO);
        return Result.success();
    }

    @GetMapping("/records/{userId}")
    public Result<Map<String, Object>> getSportRecords(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Map<String, Object> records = sportRecordService.getSportRecordsByUserId(userId, pageNum, pageSize);
        return Result.success(records);
    }

    @DeleteMapping("/record/{id}")
    public Result<Void> deleteSportRecord(@PathVariable Long id) {
        sportRecordService.deleteSportRecord(id);
        return Result.success();
    }

    @PutMapping("/record/{id}")
    public Result<Void> updateSportRecord(
            @PathVariable Long id,
            @RequestBody @Valid SportRecordDTO sportRecordDTO) {
        sportRecordService.updateSportRecord(id, sportRecordDTO);
        return Result.success();
    }
}