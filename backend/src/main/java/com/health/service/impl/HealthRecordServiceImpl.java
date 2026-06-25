package com.health.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.health.domain.dto.HealthRecordDTO;
import com.health.domain.entity.HealthRecord;
import com.health.domain.vo.HealthRecordVO;
import com.health.mapper.HealthRecordMapper;
import com.health.service.HealthRecordService;
import com.health.service.HistoryRecordService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class HealthRecordServiceImpl extends ServiceImpl<HealthRecordMapper, HealthRecord> implements HealthRecordService {

    private final HistoryRecordService historyRecordService;

    public HealthRecordServiceImpl(HistoryRecordService historyRecordService) {
        this.historyRecordService = historyRecordService;
    }

    @Override
    public void addHealthRecord(HealthRecordDTO healthRecordDTO) {
        HealthRecord healthRecord = new HealthRecord();
        BeanUtils.copyProperties(healthRecordDTO, healthRecord);
        healthRecord.setCreateTime(java.time.LocalDateTime.now());
        healthRecord.setDeleted(0);

        save(healthRecord);
        historyRecordService.syncSourceRecord(
                healthRecord.getUserId(),
                "health",
                healthRecord.getId(),
                buildHistoryTitle(healthRecord),
                buildHistoryContent(healthRecord),
                healthRecord.getRecordDate()
        );
    }

    @Override
    public Map<String, Object> getHealthRecordsByUserId(Long userId, Integer pageNum, Integer pageSize) {
        QueryWrapper<HealthRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("deleted", 0)
                .orderByDesc("create_time");
        Page<HealthRecord> page = new Page<>(pageNum, pageSize);
        Page<HealthRecord> resultPage = page(page, queryWrapper);
        List<HealthRecordVO> records = resultPage.getRecords().stream()
                .map(record -> {
                    HealthRecordVO vo = new HealthRecordVO();
                    BeanUtils.copyProperties(record, vo);
                    return vo;
                })
                .collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", resultPage.getTotal());
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    @Override
    public void deleteHealthRecord(Long id) {
        HealthRecord record = getById(id);
        if (record != null) {
            record.setDeleted(1);
            updateById(record);
            historyRecordService.deleteBySourceRecord("health", record.getId());
        }
    }

    @Override
    public void updateHealthRecord(Long id, HealthRecordDTO healthRecordDTO) {
        HealthRecord record = getById(id);
        if (record == null) {
            throw new RuntimeException("Health record not found");
        }

        BeanUtils.copyProperties(healthRecordDTO, record, "id", "userId", "createTime", "updateTime");
        updateById(record);
        historyRecordService.syncSourceRecord(
                record.getUserId(),
                "health",
                record.getId(),
                buildHistoryTitle(record),
                buildHistoryContent(record),
                record.getRecordDate()
        );
    }

    private String buildHistoryTitle(HealthRecord record) {
        return String.format("Health Record %s", record.getRecordDate());
    }

    private String buildHistoryContent(HealthRecord record) {
        String systolic = record.getBloodPressureSystolic() != null ? String.valueOf(record.getBloodPressureSystolic()) : "--";
        String diastolic = record.getBloodPressureDiastolic() != null ? String.valueOf(record.getBloodPressureDiastolic()) : "--";
        String heartRate = record.getHeartRate() != null ? String.valueOf(record.getHeartRate()) : "--";
        String bloodSugar = record.getBloodSugar() != null ? record.getBloodSugar().toPlainString() : "--";
        String weight = record.getWeight() != null ? String.valueOf(record.getWeight()) : "--";
        String notes = record.getNotes() != null && !record.getNotes().isBlank() ? "; Notes: " + record.getNotes() : "";

        return String.format("BP %s/%s mmHg, HR %s bpm, Blood Sugar %s mmol/L, Weight %s kg%s",
                systolic, diastolic, heartRate, bloodSugar, weight, notes);
    }
}
