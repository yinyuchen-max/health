package com.health.controller;

import com.health.common.utils.Result;
import com.health.domain.dto.ChatRequestDTO;
import com.health.domain.dto.ChatResponseDTO;
import com.health.service.ChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/send")
    public Result<ChatResponseDTO> send(@RequestBody ChatRequestDTO request) {
        return Result.success(chatService.chat(request.getMessage(), request.getUserId()));
    }
}
