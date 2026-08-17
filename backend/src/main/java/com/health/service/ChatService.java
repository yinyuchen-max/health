package com.health.service;

import com.health.domain.dto.ChatResponseDTO;

public interface ChatService {

    ChatResponseDTO chat(String userMessage, Long userId);

    /**
     * 流式对话：AI 回复以 token 为单位通过回调逐步返回。
     *
     * @param userMessage 用户消息
     * @param userId      当前登录用户 ID
     * @param callback    流式回调（onToken 可能被调用多次，最终以 onComplete 或 onError 结束）
     */
    void chatStream(String userMessage, Long userId, StreamCallback callback);

    interface StreamCallback {

        /** 收到一个新的思考片段（推理模型的 reasoning token，可选） */
        default void onThinking(String token) {
        }

        /** 收到一个新的文本片段（token） */
        void onToken(String token);

        /** 流式生成结束，fullText 为完整回复文本 */
        void onComplete(String fullText);

        /** 生成失败（网络错误、超时等） */
        void onError(Throwable error);
    }
}
