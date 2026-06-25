package com.health.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.health.domain.dto.HistoryRecordDTO;
import com.health.domain.entity.HistoryRecord;
import com.health.domain.vo.HistoryRecordVO;

import java.util.List;
import java.util.Map;

public interface HistoryRecordService extends IService<HistoryRecord> {

    void addHistoryRecord(HistoryRecordDTO historyRecordDTO);

    Map<String, Object> getHistoryRecordsByUserId(Long userId, Integer pageNum, Integer pageSize, String type, String startDate, String endDate);

    void deleteHistoryRecord(Long id);

    void updateHistoryRecord(Long id, HistoryRecordDTO historyRecordDTO);

    void syncSourceRecord(Long userId, String type, Long sourceRecordId, String title, String content, String recordDate);

    void deleteBySourceRecord(String type, Long sourceRecordId);
}
