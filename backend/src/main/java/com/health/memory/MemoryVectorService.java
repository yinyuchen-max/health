package com.health.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.ai.AiClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MemoryVectorService {
    private final CognitiveMemoryRepository repository;
    private final UserMemoryRepository users;
    private final AssociativeRetriever retrieval;
    private final AiClient ai;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final CognitiveMemorySettings settings;
    public MemoryVectorService(CognitiveMemoryRepository repository,UserMemoryRepository users,AssociativeRetriever retrieval,AiClient ai,ObjectMapper json,TransactionTemplate transactions,CognitiveMemorySettings settings) {
        this.repository=repository;this.users=users;this.retrieval=retrieval;this.ai=ai;this.json=json;this.transactions=transactions;this.settings=settings;
    }
    public void index(CognitiveMemoryRepository.Job job,String token) throws Exception {
        var nodes=retrieval.nodes(job.userId(),"历史",LocalDateTime.now());
        Map<String,CognitiveMemoryRepository.Vector> existing=new HashMap<>();repository.vectors(job.userId()).forEach(v->existing.put(v.type()+":"+v.id(),v));
        var batch=nodes.stream().filter(n->!n.archived()).filter(n->{var v=existing.get(n.key());return v==null||v.version()!=n.version()||!v.hash().equals(settings.vectorHash(n.content()));}).limit(16).toList();
        if(batch.isEmpty()) return;
        var result=ai.memoryVectors(job.userId(),batch.stream().map(AssociativeRetriever.Node::content).toList(),false);
        if(result==null||!result.available()) return;
        List<CognitiveMemoryRepository.Vector> vectors=new ArrayList<>();
        for(int i=0;i<batch.size();i++) {
            var node=batch.get(i);
            vectors.add(new CognitiveMemoryRepository.Vector(node.type(),node.id(),node.version(),settings.vectorHash(node.content()),result.model(),result.dimension(),json.writeValueAsString(result.vectors().get(i))));
        }
        transactions.executeWithoutResult(tx->{
            var state=users.lock(job.userId());
            if(!state.enabled()||state.revision()!=job.revision()||!repository.owns(job.id(),token)) return;
            Map<String,AssociativeRetriever.Node> current=new HashMap<>();retrieval.nodes(job.userId(),"历史",LocalDateTime.now()).forEach(n->current.put(n.key(),n));
            for(var vector:vectors) {
                var node=current.get(vector.type()+":"+vector.id());
                if(node!=null&&node.version()==vector.version()&&settings.vectorHash(node.content()).equals(vector.hash())) repository.vector(job.userId(),vector);
            }
        });
    }
}
