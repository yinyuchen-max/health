package com.health.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.ai.AiClient;
import com.health.common.exception.BusinessException;
import com.health.service.impl.RedisChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

@Service
public class LongTermMemoryService {
    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryService.class);
    private static final int MAX_MEMORIES = 50;
    private final UserMemoryRepository repository;
    private final TransactionTemplate transactions;
    private final AiClient ai;
    private final MemoryExtraction extraction;
    private final ObjectMapper json;
    private final Executor executor;
    private final RedisChatMemoryStore chatMemory;

    public LongTermMemoryService(UserMemoryRepository repository, TransactionTemplate transactions, AiClient ai,
                                MemoryExtraction extraction, ObjectMapper json,
                                @Qualifier("memoryExecutor") Executor executor, RedisChatMemoryStore chatMemory) {
        this.repository = repository; this.transactions = transactions; this.ai = ai;
        this.extraction = extraction; this.json = json; this.executor = executor; this.chatMemory = chatMemory;
    }
    public record Snapshot(Long userId, boolean enabled, long revision, long turn, List<UserMemoryRepository.Memory> memories) {}
    public record View(boolean enabled, List<UserMemoryRepository.Memory> memories) {}

    /** Capture before invoking the model, so a later delete/disable invalidates this turn's writes. */
    public Snapshot beginTurn(Long userId) {
        try {
            return transactions.execute(tx -> {
                var state = repository.lock(userId);
                repository.advanceTurn(userId);
                return new Snapshot(userId, state.enabled(), state.revision(), state.nextTurn()+1,
                        state.enabled() ? repository.list(userId, LocalDateTime.now()) : List.of());
            });
        } catch (Exception e) { failed("读取", userId, e); return null; }
    }

    public String context(Snapshot snapshot) {
        if (snapshot == null || !snapshot.enabled()) return "长期记忆未启用或暂不可用";
        try {
            var active = snapshot.memories().stream().filter(m -> "ACTIVE".equals(m.status()))
                    .limit(MAX_MEMORIES).map(m -> Map.of("factKey", m.factKey(), "content", m.content(),
                            "source", "用户自述，未经医学验证", "updatedAt", m.updatedAt().toString())).toList();
            return active.isEmpty() ? "暂无长期记忆" : json.writeValueAsString(active);
        } catch (Exception e) { failed("构建上下文", snapshot.userId(), e); return "长期记忆暂不可用"; }
    }

    /** The same DB lock serializes history writes against forgetting, across application instances. */
    public void completeTurn(Snapshot snapshot, String userMessage, Runnable saveHistory) {
        if (snapshot == null) return;
        try {
            boolean accepted = Boolean.TRUE.equals(transactions.execute(tx -> {
                var state = repository.lock(snapshot.userId());
                if (state.revision() != snapshot.revision()) return false;
                saveHistory.run();
                return state.enabled() && snapshot.enabled();
            }));
            if (accepted && userMessage.length() <= 8000)
                executor.execute(() -> extract(snapshot, userMessage));
        } catch (Exception e) { failed("提交", snapshot.userId(), e); }
    }

    private void extract(Snapshot snapshot, String message) {
        try {
            // Recheck before the paid call; check again under lock before persisting its response.
            boolean current = Boolean.TRUE.equals(transactions.execute(tx -> {
                var state = repository.lock(snapshot.userId());
                return state.enabled() && state.revision() == snapshot.revision();
            }));
            if (!current) return;
            var existing = snapshot.memories().stream().map(m -> Map.of(
                    "factKey", m.factKey(), "category", m.category(), "content", m.content(), "status", m.status())).toList();
            String response = ai.complete(ai.prepare(snapshot.userId(), "memory-extract",
                    Map.of("existingMemories", json.writeValueAsString(existing)), List.of(), message));
            apply(snapshot, extraction.parse(response, message));
        } catch (Exception e) { failed("提取", snapshot.userId(), e); }
    }

    void apply(Snapshot snapshot, List<MemoryExtraction.Candidate> candidates) {
        transactions.executeWithoutResult(tx -> {
            var state = repository.lock(snapshot.userId());
            if (!state.enabled() || state.revision() != snapshot.revision()) return;
            LocalDateTime now = LocalDateTime.now();
            repository.purgeExpired(snapshot.userId(), now);
            Set<String> processed = new HashSet<>();
            for (var c : candidates) {
                if (!processed.add(c.factKey())) continue;
                var entries = repository.list(snapshot.userId(), now);
                var old = entries.stream().filter(m -> m.factKey().equals(c.factKey())).findFirst().orElse(null);
                // A slower earlier turn must never overwrite the result of a later turn.
                if (old != null && old.lastTurn() >= snapshot.turn()) continue;
                if ("DELETE".equals(c.operation())) {
                    if (c.explicitChange()) {
                        chatMemory.clearStrict(snapshot.userId());
                        if (old != null) repository.delete(snapshot.userId(), old.id());
                        repository.invalidate(snapshot.userId());
                    }
                    continue;
                }
                if ("NOOP".equals(c.operation())) {
                    if (old != null) repository.touch(snapshot.userId(), old.id(), snapshot.turn());
                    continue;
                }
                LocalDateTime expiry = "temporary".equals(c.temporalType()) ? now.plusDays(c.ttlDays()) : null;
                if (old == null) {
                    if (!"CONFLICT".equals(c.operation()) && entries.size() < MAX_MEMORIES)
                        repository.insert(snapshot.userId(), c.factKey(), c.category(), c.content(), c.evidence(), snapshot.turn(), expiry);
                    continue;
                }
                if (old.content().equals(c.content()) && "ACTIVE".equals(old.status())) {
                    repository.touch(snapshot.userId(), old.id(), snapshot.turn());
                } else if ("UPDATE".equals(c.operation()) && c.explicitChange() && !"health".equals(c.category())) {
                    repository.replace(snapshot.userId(), old.id(), c.content(), c.evidence(), snapshot.turn(), expiry);
                } else {
                    // Changes to health facts, and ambiguous changes to other facts, require a person to confirm.
                    repository.conflict(snapshot.userId(), old.id(), c.content(), c.evidence(), snapshot.turn());
                }
            }
        });
    }

    public View view(Long userId) {
        return transactions.execute(tx -> {
            var state = repository.lock(userId);
            return new View(state.enabled(), repository.list(userId, LocalDateTime.now()));
        });
    }
    public void setEnabled(Long userId, boolean enabled) {
        transactions.executeWithoutResult(tx -> {
            repository.lock(userId);
            // Disabling also clears recent context so it cannot silently carry saved facts into future chats.
            if (!enabled) chatMemory.clearStrict(userId);
            repository.enabled(userId, enabled);
        });
    }
    public void edit(Long userId, Long id, String content) {
        if (content == null || content.isBlank() || content.length() > 500)
            throw new BusinessException("记忆内容须为 1 到 500 个字符");
        transactions.executeWithoutResult(tx -> {
            var state = repository.lock(userId);
            var old = repository.find(userId, id);
            if (old == null) throw new BusinessException("记忆不存在");
            chatMemory.clearStrict(userId);
            repository.replace(userId, id, content.trim(), "用户在记忆管理中确认或修改", state.nextTurn(), old.expiresAt());
            repository.invalidate(userId);
        });
    }
    public void delete(Long userId, Long id) {
        transactions.executeWithoutResult(tx -> {
            repository.lock(userId);
            if (repository.find(userId, id) == null) throw new BusinessException("记忆不存在");
            chatMemory.clearStrict(userId);
            repository.delete(userId, id);
            repository.invalidate(userId);
        });
    }
    public void clear(Long userId) {
        transactions.executeWithoutResult(tx -> {
            repository.lock(userId);
            chatMemory.clearStrict(userId);
            repository.clear(userId);
            repository.invalidate(userId);
        });
    }
    private void failed(String operation, Long userId, Exception e) {
        // No provider errors, user text, or candidate facts in logs.
        log.warn("长期记忆{}失败: userId={}, type={}", operation, userId, e.getClass().getSimpleName());
    }
}
