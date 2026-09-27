package com.health.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.health.domain.dto.HistoryRecordDTO;
import com.health.domain.entity.HistoryRecord;
import com.health.domain.vo.HistoryRecordVO;
import com.health.mapper.HistoryRecordMapper;
import com.health.service.HistoryRecordService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class HistoryRecordServiceImpl extends ServiceImpl<HistoryRecordMapper, HistoryRecord> implements HistoryRecordService {

    @Override
    public void addHistoryRecord(HistoryRecordDTO historyRecordDTO) {
        HistoryRecord historyRecord = new HistoryRecord();
        BeanUtils.copyProperties(historyRecordDTO, historyRecord);
        historyRecord.setRecordDate(parseRecordDate(historyRecordDTO.getRecordDate()));
        historyRecord.setCreateTime(LocalDateTime.now());
        historyRecord.setDeleted(0);
        save(historyRecord);
    }

    @Override
    public Map<String, Object> getHistoryRecordsByUserId(Long userId, Integer pageNum, Integer pageSize, String type, String startDate, String endDate) {
        QueryWrapper<HistoryRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("deleted", 0);

        if (type != null && !type.isEmpty()) {
            queryWrapper.eq("type", type);
        }

        if (startDate != null && !startDate.isEmpty()) {
            queryWrapper.ge("record_date", LocalDate.parse(startDate).atStartOfDay());
        }
        if (endDate != null && !endDate.isEmpty()) {
            queryWrapper.le("record_date", LocalDate.parse(endDate).atTime(23, 59, 59));
        }

        queryWrapper.orderByDesc("create_time");

        Page<HistoryRecord> page = new Page<>(pageNum, pageSize);
        Page<HistoryRecord> resultPage = page(page, queryWrapper);

        List<HistoryRecordVO> records = resultPage.getRecords().stream()
                .map(record -> {
                    HistoryRecordVO vo = new HistoryRecordVO();
                    BeanUtils.copyProperties(record, vo);

                    if (record.getRecordDate() != null) {
                        vo.setDate(record.getRecordDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                    }
                    if (record.getCreateTime() != null) {
                        vo.setCreateTime(record.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    }

                    vo.setTypeName(getTypeName(record.getType()));
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
    public void deleteHistoryRecord(Long id) {
        HistoryRecord record = getById(id);
        if (record != null) {
            record.setDeleted(1);
            updateById(record);
        }
    }

    @Override
    public void updateHistoryRecord(Long id, HistoryRecordDTO historyRecordDTO) {
        HistoryRecord record = getById(id);
        if (record == null) {
            throw new RuntimeException("History record not found");
        }

        BeanUtils.copyProperties(historyRecordDTO, record, "id", "userId", "createTime", "updateTime");

        if (historyRecordDTO.getRecordDate() != null && !historyRecordDTO.getRecordDate().isEmpty()) {
            record.setRecordDate(parseRecordDate(historyRecordDTO.getRecordDate()));
        }

        updateById(record);
    }

    @Override
    public void syncSourceRecord(Long userId, String type, Long sourceRecordId, String title, String content, String recordDate) {
        if (userId == null || type == null || type.isBlank() || sourceRecordId == null) {
            return;
        }

        QueryWrapper<HistoryRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type", type)
                .eq("source_record_id", sourceRecordId)
                .last("LIMIT 1");

        HistoryRecord existingRecord = getOne(queryWrapper, false);
        HistoryRecord historyRecord = existingRecord != null ? existingRecord : new HistoryRecord();

        historyRecord.setUserId(userId);
        historyRecord.setType(type);
        historyRecord.setSourceRecordId(sourceRecordId);
        historyRecord.setTitle(title);
        historyRecord.setContent(content);
        historyRecord.setRecordDate(parseRecordDate(recordDate));
        historyRecord.setDeleted(0);

        if (existingRecord == null) {
            historyRecord.setCreateTime(LocalDateTime.now());
            save(historyRecord);
        } else {
            updateById(historyRecord);
        }
    }

    @Override
    public void deleteBySourceRecord(String type, Long sourceRecordId) {
        if (type == null || type.isBlank() || sourceRecordId == null) {
            return;
        }

        QueryWrapper<HistoryRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type", type)
                .eq("source_record_id", sourceRecordId)
                .eq("deleted", 0);

        List<HistoryRecord> records = list(queryWrapper);
        for (HistoryRecord record : records) {
            record.setDeleted(1);
            updateById(record);
        }
    }

    private LocalDateTime parseRecordDate(String recordDate) {
        if (recordDate != null && !recordDate.isEmpty()) {
            return LocalDateTime.parse(recordDate + "T00:00:00");
        }
        return LocalDateTime.now();
    }

    private String getTypeName(String type) {
        if (type == null) {
            return "";
        }

        return switch (type) {
            case "health" -> "Health Record";
            case "sport" -> "Sport Record";
            case "reminder" -> "Reminder Record";
            default -> type;
        };
    }
}
