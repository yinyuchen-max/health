package com.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.health.domain.entity.DoctorAppointment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DoctorAppointmentMapper extends BaseMapper<DoctorAppointment> {
}
