package com.health.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppointmentExtractionResult {
    private Boolean appointmentIntent;
    private String patientName;
    private Integer age;
    private String appointmentTime;
    private String phone;
    private String department;
    private Boolean cancellation;
}
