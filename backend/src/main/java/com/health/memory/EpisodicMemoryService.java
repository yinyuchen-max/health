package com.health.memory;

import com.health.common.utils.ContentHash;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class EpisodicMemoryService {
    private final CognitiveMemoryRepository repository;
    private final AttentionEncoder encoder;
    private final WorkingMemoryStore working;
    private final CognitiveMemorySettings settings;
    public EpisodicMemoryService(CognitiveMemoryRepository repository,AttentionEncoder encoder,WorkingMemoryStore working,CognitiveMemorySettings settings) {
        this.repository=repository;this.encoder=encoder;this.working=working;this.settings=settings;
    }
    /** Caller holds the user state lock and worker lease. */
    public int record(CognitiveMemoryRepository.Job job,List<MemoryExtraction.Candidate> facts) {
        var profile=repository.profile(job.userId());
        var known=repository.semantics(job.userId(),false,settings.getMaxSemanticMemories());
        String goal=known.stream().filter(m->"goal".equals(m.category())&&"ACTIVE".equals(m.status())).map(CognitiveMemoryRepository.Semantic::content).findFirst().orElse(null);
        List<String> contents=known.stream().map(CognitiveMemoryRepository.Semantic::content).toList();
        int count=0; Set<String> seen=new HashSet<>();
        for(var fact:facts) {
            if(!seen.add(fact.factKey())||"DELETE".equals(fact.operation())) continue;
            var encoding=encoder.encode(fact,contents,goal,profile.encodingThreshold());
            if(!encoding.selected()) { working.buffer(job.userId(),fact.evidence());continue; }
            if(repository.episodes(job.userId(),settings.getMaxEpisodes(),true).size()>=settings.getMaxEpisodes())
                throw new IllegalStateException("MEMORY_EPISODE_CAPACITY_REACHED");
            String id=UUID.randomUUID().toString();
            LocalDateTime expiry="temporary".equals(fact.temporalType())?job.createdAt().plusDays(fact.ttlDays()):null;
            var episode=new CognitiveMemoryRepository.Episode(id,job.userId(),job.id(),job.turn(),job.revision(),fact.factKey(),fact.category(),fact.content(),fact.evidence(),
                    ContentHash.sha256(fact.evidence()),fact.operation(),fact.subject(),fact.temporalType(),fact.explicitChange(),fact.goal(),encoding.importance(),encoding.score(),"ACTIVE",
                    MemoryTime.resolve(fact.occurredAt(),fact.timeEvidence(),job.createdAt()),fact.timeEvidence(),job.createdAt(),expiry,false);
            repository.insertEpisode(episode);
            Set<String> entities=new LinkedHashSet<>(MemoryTerms.entities(fact.content()+" "+fact.evidence()));
            if(fact.entities()!=null) entities.addAll(fact.entities());
            for(String entity:entities) repository.entity(job.userId(),entity,"EPISODE",id);
            count++;
        }
        return count;
    }
}
