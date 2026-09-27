package com.health.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.health.domain.entity.DoctorAppointment;
import com.health.mapper.DoctorAppointmentMapper;
import com.health.service.DoctorAppointmentService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DoctorAppointmentServiceImpl extends ServiceImpl<DoctorAppointmentMapper, DoctorAppointment>
        implements DoctorAppointmentService {

    @Override
    public boolean createAppointment(DoctorAppointment appointment) {
        appointment.setCreatedAt(LocalDateTime.now());
        return save(appointment);
    }
}
