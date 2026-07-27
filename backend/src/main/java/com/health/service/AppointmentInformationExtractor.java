package com.health.service;

import com.health.domain.dto.AppointmentExtractionContext;
import com.health.domain.dto.AppointmentExtractionResult;

public interface AppointmentInformationExtractor {
    AppointmentExtractionResult extract(String userMessage, AppointmentExtractionContext context);
}
