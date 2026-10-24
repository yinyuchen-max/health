package com.health.memory;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SemanticMemoryService {
    private final CognitiveMemoryRepository cognitive;
    private final UserMemoryRepository facts;
    private final CognitiveMemorySettings settings;
    public SemanticMemoryService(CognitiveMemoryRepository cognitive,UserMemoryRepository facts,CognitiveMemorySettings settings) {
        this.cognitive=cognitive;this.facts=facts;this.settings=settings;
    }
    /** Replay grounded propositions in event order; never infer a diagnosis or causal relationship. */
    public void incorporate(CognitiveMemoryRepository.Episode e) {
        LocalDateTime now=LocalDateTime.now();
        if(e.expiresAt()!=null&&!e.expiresAt().isAfter(now)) { cognitive.markConsolidated(e.userId(),e.id());return; }
        var old=cognitive.fact(e.userId(),e.factKey());
        if(old!=null&&old.turn()>e.turn()) { cognitive.markConsolidated(e.userId(),e.id());return; }
        if(old!=null&&"NOOP".equals(e.operation())&&"ACTIVE".equals(old.status())&&old.expiresAt()==null) {
            // Only an actual user event adds support; replay and model repetition do not fabricate evidence.
            facts.touch(e.userId(),old.id(),Math.max(old.turn(),e.turn()));
            cognitive.evidence(e.userId(),old.id(),old.version(),e.id(),"SUPPORT");
            cognitive.exposure(e.userId(),old.id(),e.recordedAt());
        } else if(old==null) {
            if(!"CONFLICT".equals(e.operation())&&cognitive.semantics(e.userId(),true,settings.getMaxSemanticMemories()).size()<settings.getMaxSemanticMemories()) {
                facts.insert(e.userId(),e.factKey(),e.category(),e.content(),e.evidence(),e.turn(),e.expiresAt());
                var created=cognitive.fact(e.userId(),e.factKey());
                cognitive.metadata(e.userId(),created.id(),1,e.importance(),e.temporalType());
                versionAndEvidence(e,created.id(),"SUPPORT");
            }
        } else if(old.content().equals(e.content())&&!"CONFLICT".equals(old.status())) {
            facts.touch(e.userId(),old.id(),Math.max(old.turn(),e.turn()));
            if("ARCHIVED".equals(old.status())) cognitive.status(e.userId(),old.id(),"ACTIVE");
            if(old.expiresAt()!=null&&(e.expiresAt()==null||e.expiresAt().isAfter(old.expiresAt()))) {
                facts.replace(e.userId(),old.id(),e.content(),e.evidence(),e.turn(),e.expiresAt());
                cognitive.metadata(e.userId(),old.id(),old.version()+1,e.importance(),e.temporalType());
                cognitive.snapshotVersion(e.userId(),cognitive.semantic(e.userId(),old.id()));
            }
            cognitive.evidence(e.userId(),old.id(),cognitive.semantic(e.userId(),old.id()).version(),e.id(),"SUPPORT");
            cognitive.exposure(e.userId(),old.id(),e.recordedAt());
        } else {
            // A correction/explicit temporal change can supersede non-health facts; health disputes remain visible.
            boolean replace="UPDATE".equals(e.operation())&&e.explicitChange()&&!"health".equals(e.category());
            if(replace) facts.replace(e.userId(),old.id(),e.content(),e.evidence(),e.turn(),e.expiresAt());
            else facts.conflict(e.userId(),old.id(),e.content(),e.evidence(),e.turn());
            cognitive.metadata(e.userId(),old.id(),old.version()+1,Math.max(old.importance(),e.importance()),e.temporalType());
            cognitive.dropIndex(e.userId(),"SEMANTIC",old.id().toString());
            versionAndEvidence(e,old.id(),replace?"SUPPORT":"CONTRADICT");
        }
        var current=cognitive.fact(e.userId(),e.factKey());
        if(current!=null&&"ACTIVE".equals(current.status())) {
            for(String entity:MemoryTerms.entities(current.content())) cognitive.entity(e.userId(),entity,"SEMANTIC",current.id().toString());
            for(String entity:cognitive.entities(e.userId(),"EPISODE",e.id())) cognitive.entity(e.userId(),entity,"SEMANTIC",current.id().toString());
        }
        cognitive.markConsolidated(e.userId(),e.id());
    }
    private void versionAndEvidence(CognitiveMemoryRepository.Episode e,Long id,String relation) {
        var saved=cognitive.semantic(e.userId(),id);
        cognitive.snapshotVersion(e.userId(),saved);
        cognitive.evidence(e.userId(),id,saved.version(),e.id(),relation);
    }
    public void confirm(Long user,Long id,String content,long turn) {
        var old=cognitive.semantic(user,id);
        facts.replace(user,id,content,"用户在记忆管理中确认或修改",turn,old.expiresAt());
        cognitive.metadata(user,id,old.version()+1,old.importance(),old.temporalType());
        cognitive.dropIndex(user,"SEMANTIC",id.toString());
        cognitive.snapshotVersion(user,cognitive.semantic(user,id));
        for(String entity:MemoryTerms.entities(content)) cognitive.entity(user,entity,"SEMANTIC",id.toString());
    }
    /** A fact-level forget erases all its supporting/contradicting events, versions and derived indices. */
    public void forget(Long user,Long id) {
        var old=cognitive.semantic(user,id);
        if(old==null) return;
        for(var episode:cognitive.factEpisodes(user,old.factKey())) cognitive.deleteEpisode(user,episode.id());
        cognitive.removeFactDependencies(user,id);
        facts.delete(user,id); // FK cascade erases versions and evidence.
    }
    public void forgetKey(Long user,String key) {
        var old=cognitive.fact(user,key);
        if(old!=null) forget(user,old.id());
        else for(var episode:cognitive.factEpisodes(user,key)) cognitive.deleteEpisode(user,episode.id());
    }
    /** Remove dependent conclusions before replaying the remaining evidence in a later consolidation. */
    public void forgetEpisode(Long user,String id) {
        var event=cognitive.episode(user,id);
        if(event==null) return;
        for(Long dependent:cognitive.dependents(user,id)) {
            var fact=cognitive.semantic(user,dependent);
            if(fact!=null) {
                cognitive.removeFactDependencies(user,dependent);
                facts.delete(user,dependent);
                cognitive.reopenFactEpisodes(user,fact.factKey());
            }
        }
        cognitive.deleteEpisode(user,id);
    }
}
