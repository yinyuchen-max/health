package com.health.controller;

import com.health.common.annotation.RateLimit;
import com.health.common.annotation.RateLimit.LimitType;
import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import com.health.domain.dto.ChatRequestDTO;
import com.health.domain.dto.ChatResponseDTO;
import com.health.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

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

    /**
     * AI 对话流式输出（SSE）。
     * 事件格式：
     * - event: thinking data: "<思考片段>"（推理模型的思考过程，可选，可能多次）
     * - event: token    data: "<文本片段>"（JSON 字符串，可能多次）
     * - event: done     data: "<完整回复>"（JSON 字符串，流结束）
     * - event: error    data: "<错误信息>"（JSON 字符串，出错结束）
     */
    @PostMapping(value = "/send/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(key = "chat-send", maxRequests = 10, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public SseEmitter sendStream(@RequestBody ChatRequestDTO request) {
        Long currentUserId = securityUtil.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(120_000L);
        AtomicBoolean finished = new AtomicBoolean(false);

        emitter.onTimeout(() -> {
            if (finished.compareAndSet(false, true)) {
                emitter.complete();
            }
        });
        emitter.onError(e -> finished.set(true));
        emitter.onCompletion(() -> finished.set(true));

        chatService.chatStream(request.getMessage(), currentUserId, new ChatService.StreamCallback() {

            private void sendEvent(String name, String data) {
                if (finished.get()) {
                    return;
                }
                try {
                    // JSON 编码避免 token 内容含换行时破坏 SSE 协议
                    emitter.send(SseEmitter.event().name(name).data(toJson(data)));
                } catch (Exception e) {
                    log.debug("SSE 发送失败（客户端可能已断开）: {}", e.getMessage());
                    if (finished.compareAndSet(false, true)) {
                        emitter.completeWithError(e);
                    }
                }
            }

            @Override
            public void onThinking(String token) {
                sendEvent("thinking", token);
            }

            @Override
            public void onToken(String token) {
                sendEvent("token", token);
            }

            @Override
            public void onComplete(String fullText) {
                sendEvent("done", fullText);
                if (finished.compareAndSet(false, true)) {
                    emitter.complete();
                }
            }

            @Override
            public void onError(Throwable error) {
                sendEvent("error", error.getMessage() == null ? "AI 服务出错" : error.getMessage());
                if (finished.compareAndSet(false, true)) {
                    emitter.complete();
                }
            }
        });
        return emitter;
    }

    private static String toJson(String text) {
        // 手写最小 JSON 字符串转义（只针对 token/完整回复中的控制字符）
        StringBuilder sb = new StringBuilder(text.length() + 2);
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
