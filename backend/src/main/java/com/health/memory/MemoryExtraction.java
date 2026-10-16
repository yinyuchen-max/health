package com.health.memory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** Untrusted model output: only bounded, typed operations with verbatim user evidence are accepted. */
@Component
public class MemoryExtraction {
    private final ObjectMapper json;
    private static final Set<String> CATEGORIES = Set.of("health", "diet", "exercise", "goal", "lifestyle", "family");
    private static final Set<String> OPERATIONS = Set.of("ADD", "UPDATE", "NOOP", "CONFLICT", "DELETE");
    public record Candidate(String operation, String category, String factKey, String content, String evidence,
                            String temporalType, Integer ttlDays, boolean explicitChange, String subject, String assertion) {}
    public record Result(List<Candidate> candidates) {}

    public MemoryExtraction(ObjectMapper json) {
        this.json = json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    public List<Candidate> parse(String response, String userMessage) throws Exception {
        if (response == null || response.length() > 20_000) throw new IllegalArgumentException("记忆提取响应过大或为空");
        String body = response.trim();
        if (body.startsWith("```json") && body.endsWith("```")) body = body.substring(7, body.length()-3).trim();
        else if (body.startsWith("```") && body.endsWith("```")) body = body.substring(3, body.length()-3).trim();
        Result result = json.readValue(body, Result.class);
        if (result == null || result.candidates() == null || result.candidates().size() > 8)
            throw new IllegalArgumentException("无效记忆提取结果");
        return result.candidates().stream().filter(c -> valid(c, userMessage)).toList();
    }
    private boolean valid(Candidate c, String message) {
        if (c == null || !OPERATIONS.contains(c.operation() == null ? "" : c.operation())
                || !CATEGORIES.contains(c.category() == null ? "" : c.category())
                || c.factKey() == null || !c.factKey().matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){1,5}")
                || c.factKey().length() > 100 || !c.factKey().startsWith(c.category()+".")
                || c.evidence() == null || c.evidence().isBlank() || c.evidence().length() > 1000
                || !message.contains(c.evidence())) return false;
        if ("DELETE".equals(c.operation())) return c.explicitChange()
                && c.evidence().matches("(?is).*(忘|删除|清除|不要记|别记|forget|delete|remove).*");
        return c.content() != null && !c.content().isBlank() && c.content().length() <= 500
                && "asserted".equals(c.assertion())
                && ("family".equals(c.category()) ? "family".equals(c.subject()) : "self".equals(c.subject()))
                && Set.of("ongoing", "until_changed", "temporary").contains(c.temporalType() == null ? "" : c.temporalType())
                && (!"temporary".equals(c.temporalType()) || c.ttlDays() != null && c.ttlDays() >= 1 && c.ttlDays() <= 30);
    }
}
