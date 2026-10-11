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
    private final ModelFactory models;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;
    public KnowledgeService(@Value("${ai.knowledge.document:classpath:rag/health-knowledge.md}") Resource resource,
                            @Value("${ai.knowledge.max-snippets:6}") int maxSnippets, ModelFactory models, org.springframework.data.redis.core.StringRedisTemplate redis) throws java.io.IOException {
        this.document=resource.getContentAsString(StandardCharsets.UTF_8);
        this.models=models; this.redis=redis;
        this.chunks=Arrays.stream(document.split("(?m)(?=^## )"))
                .filter(s->!s.isBlank()).toList();
        if(maxSnippets<1 || maxSnippets>100) throw new IllegalArgumentException("知识片段上限必须在 1 到 100 之间");
        this.maxSnippets=maxSnippets;
    }
    public String retrieve(AiRequest request,String query) {
        if (request.embeddingApiKey()!=null && !request.embeddingApiKey().isBlank()) {
            try {
                RagRedisProperties props=new RagRedisProperties();
                props.setKeyPrefix("ai:knowledge:"+request.userId());
                var repository=new HealthKnowledgeRedisRepository(props,redis,models.embedding(request.embeddingApiKey()));
                String hash=ContentHash.sha256(document);
                if(repository.needsRebuild(hash)) repository.buildAndActivate(
                        chunks.stream().map(dev.langchain4j.data.segment.TextSegment::from).toList(),hash);
                if(repository.hasActiveStore()) {
                    var found=repository.search(repository.embedQuery(query,request.userId()),maxSnippets,minScore);
                    if(!found.isEmpty()) return String.join("\n\n",found);
                }
            } catch(Exception ignored) { /* Fall back to local retrieval without exposing provider errors/keys. */ }
        }
        return retrieve(query);
    }
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
