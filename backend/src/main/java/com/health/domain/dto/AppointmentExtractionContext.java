package com.health.domain.dto;

import lombok.Data;

@Data
public class AppointmentExtractionContext {
    private String patientName;
    private Integer age;
    private String appointmentTime;
    private String phone;
    private String department;
}
