package com.health.memory;

import com.health.common.exception.BusinessException;
import com.health.service.impl.RedisChatMemoryStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.List;

/** Backward-compatible management facade; the orchestrator owns the online lifecycle. */
@Service
public class LongTermMemoryService {
    private final UserMemoryRepository repository;
    private final CognitiveMemoryRepository cognitive;
    private final SemanticMemoryService semantics;
    private final MemoryOrchestrator orchestrator;
    private final TransactionTemplate transactions;
    private final RedisChatMemoryStore chatMemory;
    private final WorkingMemoryStore working;
    private final AdaptiveMemoryController adaptive;
    private final ChatTranscriptRepository transcript;
    public LongTermMemoryService(UserMemoryRepository repository,CognitiveMemoryRepository cognitive,SemanticMemoryService semantics,
                                 MemoryOrchestrator orchestrator,TransactionTemplate transactions,RedisChatMemoryStore chatMemory,
                                 WorkingMemoryStore working,AdaptiveMemoryController adaptive,ChatTranscriptRepository transcript) {
        this.repository=repository;this.cognitive=cognitive;this.semantics=semantics;this.orchestrator=orchestrator;
        this.transactions=transactions;this.chatMemory=chatMemory;this.working=working;this.adaptive=adaptive;
        this.transcript=transcript;
    }
    public record Snapshot(Long userId,boolean enabled,long revision,long turn,List<UserMemoryRepository.Memory> memories,String recalledContext,String retrievalId,LocalDateTime observedAt) {
        public Snapshot(Long userId,boolean enabled,long revision,long turn,List<UserMemoryRepository.Memory> memories) {
            this(userId,enabled,revision,turn,memories,null,null,LocalDateTime.now());
        }
        public Snapshot(Long userId,boolean enabled,long revision,long turn,List<UserMemoryRepository.Memory> memories,String recalledContext,String retrievalId) {
            this(userId,enabled,revision,turn,memories,recalledContext,retrievalId,LocalDateTime.now());
        }
    }
    public record View(boolean enabled,List<UserMemoryRepository.Memory> memories) {}
    public record CognitiveView(CognitiveMemoryRepository.Profile policy,List<CognitiveMemoryRepository.Episode> episodes,
                                List<CognitiveMemoryRepository.Semantic> semantics,List<CognitiveMemoryRepository.Retrieval> retrievals,
                                long pendingJobs,long failedJobs) {}
    public Snapshot beginTurn(Long user) { return beginTurn(user,""); }
    public Snapshot beginTurn(Long user,String query) { return orchestrator.beforeReply(user,query); }
    public String context(Snapshot snapshot) {
        return snapshot==null||!snapshot.enabled()?"长期记忆未启用或暂不可用":snapshot.recalledContext()==null?"暂无相关长期记忆":snapshot.recalledContext();
    }
    public void completeTurn(Snapshot snapshot,String message,Runnable saveHistory) { orchestrator.afterReply(snapshot,message,saveHistory); }
    public View view(Long user) {
        return transactions.execute(tx->{var state=repository.lock(user);return new View(state.enabled(),repository.list(user,LocalDateTime.now()));});
    }
    public CognitiveView cognition(Long user) {
        return transactions.execute(tx->{repository.lock(user);return new CognitiveView(cognitive.profile(user),cognitive.episodes(user,100,true),
                cognitive.semantics(user,true,500),cognitive.retrievals(user),cognitive.countJobs(user,"PENDING")+cognitive.countJobs(user,"PROCESSING"),cognitive.countJobs(user,"FAILED"));});
    }
    public List<CognitiveMemoryRepository.Version> versions(Long user,Long id) {
        if(cognitive.semantic(user,id)==null) throw new BusinessException("记忆不存在");
        return cognitive.versions(user,id);
    }
    public List<CognitiveMemoryRepository.Episode> evidence(Long user,Long id) {
        if(cognitive.semantic(user,id)==null) throw new BusinessException("记忆不存在");
        return cognitive.evidenceIds(user,id).stream().map(e->cognitive.episode(user,e)).filter(java.util.Objects::nonNull).toList();
    }
    public void setEnabled(Long user,boolean enabled) {
        transactions.executeWithoutResult(tx->{repository.lock(user);invalidate(user,!enabled);repository.enabled(user,enabled);});
    }
    public void edit(Long user,Long id,String content) {
        if(content==null||content.isBlank()||content.length()>500) throw new BusinessException("记忆内容须为 1 到 500 个字符");
        transactions.executeWithoutResult(tx->{
            var state=repository.lock(user);requireFact(user,id);invalidate(user,true);
            semantics.confirm(user,id,content.trim(),state.nextTurn());
        });
    }
    public void pin(Long user,Long id,boolean pinned) {
        transactions.executeWithoutResult(tx->{repository.lock(user);requireFact(user,id);cognitive.pinned(user,id,pinned);
            var old=cognitive.semantic(user,id);
            if(pinned&&"ARCHIVED".equals(old.status())&&(old.expiresAt()==null||old.expiresAt().isAfter(LocalDateTime.now()))) cognitive.status(user,id,"ACTIVE");
        });
    }
    public void delete(Long user,Long id) {
        transactions.executeWithoutResult(tx->{repository.lock(user);requireFact(user,id);invalidate(user,true);semantics.forget(user,id);});
    }
    public void forgetKeys(Long user,List<String> keys) {
        transactions.executeWithoutResult(tx->{repository.lock(user);invalidate(user,true);for(String key:keys) semantics.forgetKey(user,key);});
    }
    public void deleteEpisode(Long user,String id) {
        transactions.executeWithoutResult(tx->{
            var state=repository.lock(user);
            if(cognitive.episode(user,id)==null) throw new BusinessException("事件不存在");
            invalidate(user,true);semantics.forgetEpisode(user,id);
            if(state.enabled()) {
                repository.advanceTurn(user);
                cognitive.enqueue(user,state.revision()+1,state.nextTurn()+1,"CONSOLIDATE","");
            }
        });
    }
    public void clear(Long user) {
        transactions.executeWithoutResult(tx->{repository.lock(user);invalidate(user,true);cognitive.clearDerived(user);repository.clear(user);});
    }
    public void feedback(Long user,String retrievalId,String feedback) {
        transactions.executeWithoutResult(tx->{repository.lock(user);adaptive.feedback(user,retrievalId,feedback);});
    }
    private void requireFact(Long user,Long id) { if(cognitive.semantic(user,id)==null) throw new BusinessException("记忆不存在"); }
    private void invalidate(Long user,boolean clearRecent) {
        if(clearRecent) { chatMemory.clearStrict(user);working.clearStrict(user);transcript.clear(user); }
        repository.invalidate(user);cognitive.cancelJobs(user);cognitive.clearRetrievals(user);
    }
}
