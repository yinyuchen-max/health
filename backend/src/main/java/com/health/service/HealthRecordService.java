package com.health.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.health.domain.dto.HealthRecordDTO;
import com.health.domain.entity.HealthRecord;
import com.health.domain.vo.HealthRecordVO;

import java.util.List;
import java.util.Map;

public interface HealthRecordService extends IService<HealthRecord> {

    void addHealthRecord(HealthRecordDTO healthRecordDTO);

    Map<String, Object> getHealthRecordsByUserId(Long userId, Integer pageNum, Integer pageSize);

    void deleteHealthRecord(Long id);

    void updateHealthRecord(Long id, HealthRecordDTO healthRecordDTO);
}