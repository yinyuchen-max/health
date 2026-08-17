package com.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.health.domain.entity.DoctorMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DoctorMessageMapper extends BaseMapper<DoctorMessage> {
}
