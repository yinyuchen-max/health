package com.health.ai;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Service
public class KnowledgeService {
    private final List<String> chunks;
    private final int maxSnippets;
    @Value("${ai.knowledge.min-score:0.1}") private double minScore=0.1;
    private final String document;
    private final HealthKnowledgeRedisRepository repository;
    private final String sourceHash;
    private volatile long nextBuildAttempt;
    public KnowledgeService(@Value("${ai.knowledge.document:classpath:rag/health-knowledge.md}") Resource resource,
                            @Value("${ai.knowledge.max-snippets:6}") int maxSnippets, ModelFactory models,
                            org.springframework.data.redis.core.StringRedisTemplate redis,
                            @Value("${ai.knowledge.embedding-api-key:}") String embeddingKey) throws java.io.IOException {
        this.document=resource.getContentAsString(StandardCharsets.UTF_8);
        this.sourceHash=ContentHash.sha256(document);
        RagRedisProperties props=new RagRedisProperties();
        // Public corpus only. Versioned keys allow rolling deployments to coexist.
        props.setKeyPrefix("ai:knowledge:public:v3:"+ContentHash.sha256(sourceHash+"\n"
                +ModelFactory.EMBEDDING_MODEL+"\n"+ModelFactory.EMBEDDING_BASE_URL));
        this.repository=embeddingKey.isBlank() ? null
                : new HealthKnowledgeRedisRepository(props,redis,models.embedding(embeddingKey));
        this.chunks=Arrays.stream(document.split("(?m)(?=^## )"))
                .filter(s->!s.isBlank()).toList();
        if(maxSnippets<1 || maxSnippets>100) throw new IllegalArgumentException("知识片段上限必须在 1 到 100 之间");
        this.maxSnippets=maxSnippets;
    }
    public String retrieve(AiRequest request,String query) {
        if (repository!=null) {
            try {
                initializeIndex();
                if(repository.hasActiveStore()) {
                    var found=repository.search(repository.embedQuery(query,request.userId()),maxSnippets,minScore);
                    if(!found.isEmpty()) return String.join("\n\n",found);
                }
            } catch(Exception ignored) { /* Fall back to local retrieval without exposing provider errors/keys. */ }
        }
        return retrieve(query);
    }
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public synchronized void initializeIndex() {
        if(repository==null || repository.hasActiveStore() || nextBuildAttempt!=0 && System.nanoTime()<nextBuildAttempt) return;
        // Retry failed builds at most once a minute per instance, even under traffic.
        nextBuildAttempt=System.nanoTime()+java.time.Duration.ofMinutes(1).toNanos();
        try {
            repository.buildAndActivate(
                    chunks.stream().map(dev.langchain4j.data.segment.TextSegment::from).toList(),sourceHash);
        } catch(Exception ignored) { /* Keyword retrieval remains available. */ }
    }
    @jakarta.annotation.PreDestroy public void close() { if(repository!=null) repository.close(); }
    public String retrieve(String query) {
        // Local retrieval has no embedding API bill and works with chat-only provider keys.
        var terms=List.of("血压","血糖","心率","体重","BMI","运动","睡眠","压力","营养","饮食","年龄","健康");
        record Hit(String text,int score) {}
        return chunks.stream().map(chunk -> new Hit(chunk,(int)terms.stream()
                        .filter(t->query.toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT))
                                && chunk.toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT))).count()))
                .filter(hit->hit.score()>0).sorted(Comparator.comparingInt(Hit::score).reversed())
                .limit(maxSnippets).map(Hit::text).reduce((a,b)->a+"\n\n"+b).orElse("暂无检索结果");
    }
}
