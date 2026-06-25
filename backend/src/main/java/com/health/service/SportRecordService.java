package com.health.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.health.domain.dto.SportRecordDTO;
import com.health.domain.entity.SportRecord;
import com.health.domain.vo.SportRecordVO;

import java.util.List;
import java.util.Map;

public interface SportRecordService extends IService<SportRecord> {

    void addSportRecord(SportRecordDTO sportRecordDTO);

    Map<String, Object> getSportRecordsByUserId(Long userId, Integer pageNum, Integer pageSize);

    void deleteSportRecord(Long id);

    void updateSportRecord(Long id, SportRecordDTO sportRecordDTO);
}