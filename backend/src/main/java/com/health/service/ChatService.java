package com.health.service;

import com.health.domain.dto.ChatResponseDTO;

public interface ChatService {

    ChatResponseDTO chat(String userMessage, Long userId);
}
