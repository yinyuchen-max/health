package com.health.service;

import com.health.domain.entity.DoctorAppointment;

public interface DoctorAppointmentService {
    boolean createAppointment(DoctorAppointment appointment);
}
