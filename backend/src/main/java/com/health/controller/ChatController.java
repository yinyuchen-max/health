package com.health.controller;

import com.health.common.annotation.RateLimit;
import com.health.common.annotation.RateLimit.LimitType;
import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import com.health.domain.dto.ChatRequestDTO;
import com.health.domain.dto.ChatResponseDTO;
import com.health.service.ChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final SecurityUtil securityUtil;

    public ChatController(ChatService chatService, SecurityUtil securityUtil) {
        this.chatService = chatService;
        this.securityUtil = securityUtil;
    }

    @PostMapping("/send")
    @RateLimit(key = "chat-send", maxRequests = 10, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<ChatResponseDTO> send(@RequestBody ChatRequestDTO request) {
        // 强制使用当前登录用户ID，防止伪造userId访问他人会话
        Long currentUserId = securityUtil.getCurrentUserId();
        return Result.success(chatService.chat(request.getMessage(), currentUserId));
    }
}
