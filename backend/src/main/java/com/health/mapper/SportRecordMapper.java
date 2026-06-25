package com.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.health.domain.entity.SportRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SportRecordMapper extends BaseMapper<SportRecord> {
}