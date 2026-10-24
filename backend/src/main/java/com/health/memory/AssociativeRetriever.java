package com.health.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.ai.AiClient;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AssociativeRetriever {
    public record Node(String type,String id,int version,String factKey,String category,String content,double importance,
                       boolean pinned,int usefulCount,LocalDateTime exposure,LocalDateTime eventTime,boolean archived,boolean historical) {
        public String key() { return type+":"+id; }
    }
    public record Hit(Node node,double score,String reason) {}
    public record Recall(String context,List<CognitiveMemoryRepository.RecallItem> items,List<Hit> hits,int estimatedTokens) {}
    private final CognitiveMemoryRepository repository;
    private final ForgettingPolicy forgetting;
    private final AiClient ai;
    private final ObjectMapper json;
    private final CognitiveMemorySettings settings;
    public AssociativeRetriever(CognitiveMemoryRepository repository,ForgettingPolicy forgetting,AiClient ai,ObjectMapper json,CognitiveMemorySettings settings) {
        this.repository=repository;this.forgetting=forgetting;this.ai=ai;this.json=json;this.settings=settings;
    }
    public List<Node> nodes(Long user,String query,LocalDateTime now) {
        var semantics=repository.semantics(user,true,settings.getMaxSemanticMemories());
        Map<String,CognitiveMemoryRepository.Semantic> latest=new HashMap<>();
        List<Node> nodes=new ArrayList<>();
        for(var s:semantics) {
            latest.put(s.factKey(),s);
            if((!"ACTIVE".equals(s.status())&&!"ARCHIVED".equals(s.status()))||(s.expiresAt()!=null&&!s.expiresAt().isAfter(now))) continue;
            nodes.add(new Node("SEMANTIC",s.id().toString(),s.version(),s.factKey(),s.category(),s.content(),s.importance(),s.pinned(),s.usefulCount(),
                    s.lastAccessedAt()==null?s.updatedAt():s.lastAccessedAt(),null,"ARCHIVED".equals(s.status()),false));
        }
        boolean historicalQuery=query.matches("(?is).*(历史|以前|过去|上次|什么时候|之前|曾经|history|previous|when).*");
        for(var e:repository.episodes(user,settings.getMaxEpisodes(),true)) {
            var current=latest.get(e.factKey());
            if(current!=null&&"CONFLICT".equals(current.status())) continue;
            boolean superseded=current!=null&&current.turn()>e.turn()&&!current.content().equals(e.content());
            if(superseded&&!historicalQuery) continue;
            // Expired episodes remain historical events, but may not be used as present symptoms.
            boolean expired=e.expiresAt()!=null&&!e.expiresAt().isAfter(now);
            if(expired&&!historicalQuery) continue;
            nodes.add(new Node("EPISODE",e.id(),1,e.factKey(),e.category(),e.content(),e.importance(),false,0,e.recordedAt(),e.occurredAt(),"ARCHIVED".equals(e.state()),true));
        }
        return nodes;
    }
    public Recall retrieve(Long user,String query,CognitiveMemoryRepository.Profile profile,LocalDateTime now) {
        List<Node> nodes=nodes(user,query,now);
        var entityIndex=repository.entityIndex(user);
        Map<String,Double> vectorScores=new HashMap<>();
        var vectors=repository.vectors(user);
        if(!query.isBlank()&&!vectors.isEmpty()) {
            try {
                var batch=ai.memoryVectors(user,List.of(query.length()>1000?query.substring(0,1000):query),true);
                if(batch!=null&&batch.available()) {
                    Map<String,Node> byKey=new HashMap<>();nodes.forEach(n->byKey.put(n.key(),n));
                    for(var vector:vectors) {
                        var node=byKey.get(vector.type()+":"+vector.id());
                        if(node==null||node.version()!=vector.version()||!settings.vectorHash(node.content()).equals(vector.hash())
                                ||!batch.model().equals(vector.model())||vector.dimension()!=batch.dimension()) continue;
                        float[] values=json.readValue(vector.json(),float[].class);
                        vectorScores.put(node.key(),cosine(batch.vectors().get(0),values));
                    }
                }
            } catch(Exception ignored) { /* Lexical/entity retrieval remains available within a bounded query timeout. */ }
        }
        return rank(query,nodes,entityIndex,vectorScores,profile,now,true,true,true);
    }
    /** Shared deterministic ranking entry point used by the replay/ablation benchmark. */
    public Recall rank(String query,List<Node> nodes,Map<String,Set<String>> entityIndex,Map<String,Double> vectors,
                       CognitiveMemoryRepository.Profile profile,LocalDateTime now,boolean associative,boolean decay,boolean importance) {
        Map<String,Double> lexical=new HashMap<>();
        for(var node:nodes) lexical.put(node.key(),query.isBlank()?0.2:MemoryTerms.similarity(query,node.content()));
        Set<String> cues=new LinkedHashSet<>(MemoryTerms.entities(query));
        if(associative) nodes.stream().filter(n->lexical.get(n.key())>0.05)
                .sorted(Comparator.comparingDouble((Node n)->lexical.get(n.key())).reversed()).limit(3)
                .forEach(n->cues.addAll(entityIndex.getOrDefault(n.key(),Set.of())));
        List<Hit> hits=new ArrayList<>();
        for(var node:nodes) {
            double lex=lexical.get(node.key()),vec=Math.max(0,vectors.getOrDefault(node.key(),0.0));
            var entities=entityIndex.getOrDefault(node.key(),MemoryTerms.entities(node.content()));
            double association=associative&&!cues.isEmpty()?entities.stream().filter(cues::contains).count()/(double)Math.max(1,entities.size()):0;
            if(!query.isBlank()&&lex<0.025&&vec<0.45&&association==0) continue;
            if(node.archived()&&lex<0.15&&vec<0.7&&association<0.8) continue;
            double activation=decay?forgetting.activation(node.category(),node.exposure(),node.usefulCount(),profile.decayMultiplier(),now):0.5;
            double relevance=Math.max(lex,vec);
            double score=profile.relevanceWeight()*relevance+0.15*association+0.15*activation
                    +(importance?0.15*node.importance():0)+(node.pinned()?0.05:0)+(node.type().equals("SEMANTIC")?0.03:0);
            String reason="lex="+round(lex)+",vec="+round(vec)+",link="+round(association)+",activation="+round(activation);
            hits.add(new Hit(node,score,reason));
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed().thenComparing(h->h.node().key()));
        List<Hit> selected=new ArrayList<>(); List<CognitiveMemoryRepository.RecallItem> items=new ArrayList<>();
        List<Map<String,Object>> context=new ArrayList<>(); Set<String> represented=new HashSet<>();int used=2;
        for(var hit:hits.stream().limit(settings.getMaxRecallCandidates()).toList()) {
            if(selected.size()>=settings.getRecallLimit()) break;
            Node node=hit.node();
            // Different historical versions remain distinguishable; the same proposition does not consume two slots.
            String duplicateKey=node.factKey()+"|"+node.content();
            if(represented.contains(duplicateKey)) continue;
            Map<String,Object> entry=new LinkedHashMap<>();
            entry.put("type",node.type());entry.put("content",node.content());entry.put("source","用户自述，未经医学验证");
            entry.put("temporalStatus",node.historical()?"历史事件，不代表当前状态":"当前语义记忆");
            if(node.eventTime()!=null) entry.put("occurredAt",node.eventTime().toString());
            if(node.historical()&&node.eventTime()==null) entry.put("recordedAt",node.exposure().toString());
            try {
                String encoded=json.writeValueAsString(entry);
                // Conservative character-based estimate, exposed as an estimate rather than an exact model tokenizer count.
                int cost=encoded.codePointCount(0,encoded.length())+1;
                if(used+cost>profile.contextBudget()) continue;
                used+=cost;represented.add(duplicateKey);context.add(entry);selected.add(hit);
                items.add(new CognitiveMemoryRepository.RecallItem(node.type(),node.id(),node.version(),hit.score(),hit.reason()));
            } catch(Exception ignored) { /* Skip malformed derived context only. */ }
        }
        try { return new Recall(context.isEmpty()?"暂无相关长期记忆":json.writeValueAsString(context),List.copyOf(items),List.copyOf(selected),used); }
        catch(Exception e) { throw new IllegalStateException("无法构建记忆上下文"); }
    }
    private static String round(double value) { return String.format(Locale.ROOT,"%.2f",value); }
    public static double cosine(float[] a,float[] b) {
        if(a==null||b==null||a.length!=b.length||a.length==0) return 0;
        double dot=0,aa=0,bb=0;
        for(int i=0;i<a.length;i++) { if(!Float.isFinite(a[i])||!Float.isFinite(b[i])) return 0;dot+=a[i]*b[i];aa+=a[i]*a[i];bb+=b[i]*b[i]; }
        return aa==0||bb==0?0:Math.max(-1,Math.min(1,dot/Math.sqrt(aa*bb)));
    }
}
