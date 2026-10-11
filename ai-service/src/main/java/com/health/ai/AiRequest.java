package com.health.ai;
import java.util.List;
import java.util.Map;
public record AiRequest(Long userId, String provider, String apiKey, String model, String task, String embeddingApiKey,
                        Map<String, String> variables, List<Message> history, String message) {
    public record Message(String role, String content) {}
    @Override public String toString() { return "AiRequest[task=" + task + ", credentials=REDACTED]"; }
}
