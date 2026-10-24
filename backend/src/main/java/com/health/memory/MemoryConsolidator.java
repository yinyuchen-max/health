package com.health.memory;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class MemoryConsolidator {
    private final CognitiveMemoryRepository repository;
    private final SemanticMemoryService semantics;
    private final CognitiveMemorySettings settings;
    public MemoryConsolidator(CognitiveMemoryRepository repository,SemanticMemoryService semantics,CognitiveMemorySettings settings) {
        this.repository=repository;this.semantics=semantics;this.settings=settings;
    }
    public int consolidate(Long user) {
        var batch=repository.unconsolidated(user,settings.getConsolidationBatchSize());
        for(var episode:batch) semantics.incorporate(episode);
        repository.consolidated(user,LocalDateTime.now());
        return batch.size();
    }
}
