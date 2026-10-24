package com.health.memory;

import com.health.ai.AiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;

@Component
public class MemoryMaintenance {
    private static final Logger log=LoggerFactory.getLogger(MemoryMaintenance.class);
    private final CognitiveMemoryRepository repository;
    private final UserMemoryRepository users;
    private final ForgettingPolicy forgetting;
    private final CognitiveMemorySettings settings;
    private final TransactionTemplate transactions;
    private final AiClient ai;
    public MemoryMaintenance(CognitiveMemoryRepository repository,UserMemoryRepository users,ForgettingPolicy forgetting,CognitiveMemorySettings settings,TransactionTemplate transactions,AiClient ai) {
        this.repository=repository;this.users=users;this.forgetting=forgetting;this.settings=settings;this.transactions=transactions;this.ai=ai;
    }
    @Scheduled(fixedDelayString="${health.memory.maintenance-delay-ms:600000}")
    public void maintain() {
        LocalDateTime now=LocalDateTime.now();repository.pruneTraces(now.minusDays(30));
        for(Long user:repository.users()) {
            try { maintainUser(user,now); }
            catch(Exception e) { log.warn("记忆维护失败: userId={}, type={}",user,e.getClass().getSimpleName()); }
        }
    }
    public void maintainUser(Long user,LocalDateTime now) {
        boolean allowed;
        try { ai.requireAccess(user);allowed=true; } catch(Exception ignored) { allowed=false; }
        boolean canEmbed=allowed;
        transactions.executeWithoutResult(tx->{
            var state=users.lock(user);
            if(!state.enabled()) return;
            var profile=repository.profile(user);
            for(var fact:repository.semantics(user,true,settings.getMaxSemanticMemories())) {
                if(!"ACTIVE".equals(fact.status())) continue;
                double activation=forgetting.activation(fact.category(),fact.lastAccessedAt()==null?fact.updatedAt():fact.lastAccessedAt(),fact.usefulCount(),profile.decayMultiplier(),now);
                if(forgetting.archive(fact.category(),fact.pinned(),fact.importance(),activation,fact.expiresAt()!=null&&!fact.expiresAt().isAfter(now)))
                    repository.status(user,fact.id(),"ARCHIVED");
            }
            for(var episode:repository.episodes(user,settings.getMaxEpisodes(),true)) {
                boolean protectedEvent=episode.importance()>=0.8||repository.dependents(user,episode.id()).stream()
                        .map(id->repository.semantic(user,id)).anyMatch(s->s!=null&&s.pinned());
                if(!protectedEvent&&episode.recordedAt().isBefore(now.minusDays(30))&&"ACTIVE".equals(episode.state())) repository.archiveEpisode(user,episode.id());
                if(!protectedEvent&&episode.recordedAt().isBefore(now.minusDays(settings.getArchivedRetentionDays()))&&repository.canPruneEpisode(user,episode.id()))
                    repository.deleteEpisode(user,episode.id());
            }
            // Recovery sweep for lost/stale jobs and indices enabled after memories already exist.
            if(!repository.hasPendingWork(user)) {
                users.advanceTurn(user);long turn=state.nextTurn()+1;
                if(!repository.unconsolidated(user,1).isEmpty()) repository.enqueue(user,state.revision(),turn,"CONSOLIDATE","");
                else if(canEmbed&&!repository.semantics(user,false,1).isEmpty()) repository.enqueue(user,state.revision(),turn,"INDEX","");
            }
        });
    }
}
