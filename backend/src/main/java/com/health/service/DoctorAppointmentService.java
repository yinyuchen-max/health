package com.health.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.health.domain.entity.DoctorAppointment;

public interface DoctorAppointmentService extends IService<DoctorAppointment> {
    boolean createAppointment(DoctorAppointment appointment);
}
