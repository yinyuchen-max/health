package com.health.ai;

import dev.langchain4j.data.segment.TextSegment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;

/** User text is never added to the shared public medical knowledge index. */
@Service
public class MemoryEmbeddingService {
    private final String key;
    private final ModelFactory models;
    public record Batch(boolean available,String model,int dimension,List<float[]> vectors) {}
    public MemoryEmbeddingService(@Value("${ai.knowledge.embedding-api-key:}") String key,ModelFactory models) {
        this.key=key;this.models=models;
    }
    public Batch embed(List<String> texts) {
        if(texts==null||texts.isEmpty()||texts.size()>16||texts.stream().anyMatch(s->s==null||s.isBlank()||s.length()>1000))
            throw new IllegalArgumentException("无效的记忆向量请求");
        String model=ModelFactory.EMBEDDING_BASE_URL+"|"+ModelFactory.EMBEDDING_MODEL;
        if(key.isBlank()) return new Batch(false,model,0,List.of());
        var vectors=models.memoryEmbedding(key).embedAll(texts.stream().map(TextSegment::from).toList()).content();
        if(vectors.size()!=texts.size()) throw new IllegalStateException("向量数量不匹配");
        int dimension=vectors.get(0).dimension();
        if(dimension<1||dimension>8192||vectors.stream().anyMatch(v->v.dimension()!=dimension||!finite(v.vector())))
            throw new IllegalStateException("向量维度不匹配");
        return new Batch(true,model,dimension,vectors.stream().map(v->v.vector()).toList());
    }
    private static boolean finite(float[] vector) { for(float value:vector) if(!Float.isFinite(value)) return false;return true; }
}
