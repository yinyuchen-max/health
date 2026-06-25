package com.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.health.domain.entity.HistoryRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HistoryRecordMapper extends BaseMapper<HistoryRecord> {
}
