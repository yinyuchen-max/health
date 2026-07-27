package com.health.service;

public interface AppointmentConversationService {
    String handleMessage(String userMessage, Long userId);

    String appendAppointmentOffer(String reply, Long userId);
}
