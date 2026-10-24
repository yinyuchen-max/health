package com.health.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.ai.AiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MemoryJobProcessor {
    private static final Logger log=LoggerFactory.getLogger(MemoryJobProcessor.class);
    private final CognitiveMemoryRepository repository;
    private final UserMemoryRepository users;
    private final TransactionTemplate transactions;
    private final AiClient ai;
    private final ObjectMapper json;
    private final MemoryExtraction extraction;
    private final EpisodicMemoryService episodes;
    private final MemoryConsolidator consolidator;
    private final MemoryVectorService vectors;
    private final LongTermMemoryService management;
    private final CognitiveMemorySettings settings;
    public MemoryJobProcessor(CognitiveMemoryRepository repository,UserMemoryRepository users,TransactionTemplate transactions,AiClient ai,
                              ObjectMapper json,MemoryExtraction extraction,EpisodicMemoryService episodes,MemoryConsolidator consolidator,
                              MemoryVectorService vectors,LongTermMemoryService management,CognitiveMemorySettings settings) {
        this.repository=repository;this.users=users;this.transactions=transactions;this.ai=ai;this.json=json;
        this.extraction=extraction;this.episodes=episodes;this.consolidator=consolidator;this.vectors=vectors;this.management=management;this.settings=settings;
    }
    public void process(String id) {
        String token=repository.claim(id,LocalDateTime.now(),settings.getLeaseSeconds(),settings.getMaxJobAttempts());
        if(token==null) return;
        var job=repository.job(id);
        try {
            boolean valid=Boolean.TRUE.equals(transactions.execute(tx->{
                var state=users.lock(job.userId());
                return state.enabled()&&state.revision()==job.revision()&&repository.owns(id,token);
            }));
            if(!valid) { repository.done(id,token,"CANCELLED");return; }
            List<MemoryExtraction.Candidate> candidates=List.of();
            if("ENCODE".equals(job.type())) {
                var existing=repository.semantics(job.userId(),false,settings.getMaxSemanticMemories()).stream().map(s->Map.of(
                        "factKey",s.factKey(),"category",s.category(),"content",s.content(),"status",s.status())).toList();
                String response=ai.complete(ai.prepare(job.userId(),"memory-extract",Map.of("existingMemories",json.writeValueAsString(existing),
                        "currentDate",job.createdAt().toLocalDate().toString()),List.of(),job.input()));
                candidates=extraction.parse(response,job.input());
            } else if("INDEX".equals(job.type())) vectors.index(job,token);
            var facts=candidates;
            transactions.executeWithoutResult(tx->{
                var state=users.lock(job.userId());
                if(!state.enabled()||state.revision()!=job.revision()||!repository.owns(id,token)) { repository.done(id,token,"CANCELLED");return; }
                switch(job.type()) {
                    case "ENCODE" -> {
                        var deletions=facts.stream().filter(c->"DELETE".equals(c.operation())).map(MemoryExtraction.Candidate::factKey)
                                .filter(key->{
                                    var current=repository.fact(job.userId(),key);
                                    return (current==null||current.turn()<=job.turn())&&repository.factEpisodes(job.userId(),key).stream().noneMatch(e->e.turn()>job.turn());
                                }).toList();
                        if(!deletions.isEmpty()) { management.forgetKeys(job.userId(),deletions);return; }
                        if(episodes.record(job,facts)>0) repository.enqueue(job.userId(),job.revision(),job.turn(),"CONSOLIDATE","");
                    }
                    case "CONSOLIDATE" -> {
                        consolidator.consolidate(job.userId());
                        repository.enqueue(job.userId(),job.revision(),job.turn(),"INDEX","");
                    }
                    case "INDEX" -> { }
                    default -> throw new IllegalArgumentException("未知记忆任务");
                }
                repository.done(id,token,"DONE");
            });
        } catch(Exception e) {
            repository.retry(id,token,e.getClass().getSimpleName(),settings.getMaxJobAttempts(),LocalDateTime.now());
            log.warn("记忆任务等待重试: jobId={}, type={}",id,e.getClass().getSimpleName());
        }
    }
}
