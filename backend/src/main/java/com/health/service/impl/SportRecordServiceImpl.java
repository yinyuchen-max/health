package com.health.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.health.domain.dto.SportRecordDTO;
import com.health.domain.entity.SportRecord;
import com.health.domain.vo.SportRecordVO;
import com.health.mapper.SportRecordMapper;
import com.health.service.HistoryRecordService;
import com.health.service.SportRecordService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SportRecordServiceImpl extends ServiceImpl<SportRecordMapper, SportRecord> implements SportRecordService {

    private final HistoryRecordService historyRecordService;

    public SportRecordServiceImpl(HistoryRecordService historyRecordService) {
        this.historyRecordService = historyRecordService;
    }

    @Override
    public void addSportRecord(SportRecordDTO sportRecordDTO) {
        SportRecord sportRecord = new SportRecord();
        BeanUtils.copyProperties(sportRecordDTO, sportRecord);
        sportRecord.setCreateTime(java.time.LocalDateTime.now());
        sportRecord.setDeleted(0);

        save(sportRecord);
        historyRecordService.syncSourceRecord(
                sportRecord.getUserId(),
                "sport",
                sportRecord.getId(),
                buildHistoryTitle(sportRecord),
                buildHistoryContent(sportRecord),
                sportRecord.getRecordDate()
        );
    }

    @Override
    public Map<String, Object> getSportRecordsByUserId(Long userId, Integer pageNum, Integer pageSize) {
        QueryWrapper<SportRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("deleted", 0)
                .orderByDesc("create_time");

        Page<SportRecord> page = new Page<>(pageNum, pageSize);
        Page<SportRecord> resultPage = page(page, queryWrapper);

        List<SportRecordVO> records = resultPage.getRecords().stream()
                .map(record -> {
                    SportRecordVO vo = new SportRecordVO();
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
    public void deleteSportRecord(Long id) {
        SportRecord record = getById(id);
        if (record != null) {
            record.setDeleted(1);
            updateById(record);
            historyRecordService.deleteBySourceRecord("sport", record.getId());
        }
    }

    @Override
    public void updateSportRecord(Long id, SportRecordDTO sportRecordDTO) {
        SportRecord record = getById(id);
        if (record == null) {
            throw new RuntimeException("Sport record not found");
        }

        BeanUtils.copyProperties(sportRecordDTO, record, "id", "userId", "createTime", "updateTime");
        updateById(record);
        historyRecordService.syncSourceRecord(
                record.getUserId(),
                "sport",
                record.getId(),
                buildHistoryTitle(record),
                buildHistoryContent(record),
                record.getRecordDate()
        );
    }

    private String buildHistoryTitle(SportRecord record) {
        return String.format("Sport Record %s", record.getRecordDate());
    }

    private String buildHistoryContent(SportRecord record) {
        String sportType = record.getSportType() != null ? record.getSportType() : "--";
        String duration = record.getDuration() != null ? String.valueOf(record.getDuration()) : "--";
        String calories = record.getCalories() != null ? String.valueOf(record.getCalories()) : "--";
        String intensity = mapIntensity(record.getIntensity());
        String notes = record.getNotes() != null && !record.getNotes().isBlank() ? "; Notes: " + record.getNotes() : "";

        return String.format("%s, Duration %s min, Intensity %s, Calories %s kcal%s",
                sportType, duration, intensity, calories, notes);
    }

    private String mapIntensity(String intensity) {
        if (intensity == null) {
            return "--";
        }

        return switch (intensity) {
            case "low" -> "Low";
            case "medium" -> "Medium";
            case "high" -> "High";
            default -> intensity;
        };
    }
}
