package com.health.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.health.common.exception.BusinessException;
import com.health.common.exception.RecordConflictException;
import com.health.domain.dto.HealthRecordDTO;
import com.health.domain.entity.HealthRecord;
import com.health.domain.vo.HealthRecordVO;
import com.health.mapper.HealthRecordMapper;
import com.health.service.HealthRecordService;
import com.health.service.HistoryRecordService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HealthRecordServiceImpl extends ServiceImpl<HealthRecordMapper, HealthRecord> implements HealthRecordService {

    private final HistoryRecordService historyRecordService;

    public HealthRecordServiceImpl(HistoryRecordService historyRecordService) {
        this.historyRecordService = historyRecordService;
    }

    @Override
    @Transactional
    public void addHealthRecord(HealthRecordDTO healthRecordDTO) {
        HealthRecord healthRecord = new HealthRecord();
        BeanUtils.copyProperties(healthRecordDTO, healthRecord);
        healthRecord.setCreateTime(java.time.LocalDateTime.now());
        healthRecord.setDeleted(0);
        healthRecord.setVersion(0);

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
                .toList();
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", resultPage.getTotal());
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    @Override
    @Transactional
    public void deleteHealthRecord(Long id) {
        HealthRecord record = getById(id);
        if (record != null) {
            if (baseMapper.softDeleteWithVersion(id, record.getVersion()) != 1) {
                throw conflict();
            }
            historyRecordService.deleteBySourceRecord("health", record.getId());
        }
    }

    @Override
    @Transactional
    public void updateHealthRecord(Long id, HealthRecordDTO healthRecordDTO) {
        if (healthRecordDTO.getVersion() == null || healthRecordDTO.getVersion() < 0) {
            throw new BusinessException("修改健康记录必须携带有效版本号，请刷新记录后重新编辑");
        }
        HealthRecord record = getById(id);
        if (record == null) {
            throw conflict();
        }

        if (!healthRecordDTO.getVersion().equals(record.getVersion())) {
            throw conflict();
        }
        BeanUtils.copyProperties(healthRecordDTO, record, "id", "userId", "createTime", "updateTime");
        // 插件原子执行 WHERE id = ? AND version = ?，不能只依赖上面的内存比较。
        if (!updateById(record)) {
            throw conflict();
        }
        historyRecordService.syncSourceRecord(
                record.getUserId(),
                "health",
                record.getId(),
                buildHistoryTitle(record),
                buildHistoryContent(record),
                record.getRecordDate()
        );
    }

    private RecordConflictException conflict() {
        return new RecordConflictException("健康记录已被其他操作修改或删除，请刷新记录后重新编辑");
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
