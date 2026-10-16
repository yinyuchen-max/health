package com.health.ai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeServiceTest {
    @Test void publicIndexBuiltOnceAndRetainedAcrossUsersAndRestoredOnRestart() throws Exception {
        var cache=new RedisMemoryFixture(); var factory=mock(ModelFactory.class); var model=mock(EmbeddingModel.class);
        when(factory.embedding("platform-vector-key")).thenReturn(model);
        when(model.embedAll(anyList())).thenReturn(Response.from(List.of(Embedding.from(new float[]{1,0}))));
        when(model.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{1,0})));
        var resource=new ByteArrayResource("## 运动\n每天适量运动".getBytes(StandardCharsets.UTF_8));
        var service=new KnowledgeService(resource,2,factory,cache.redis,"platform-vector-key");
        try {
            service.initializeIndex();
            for(long user:List.of(1L,2L,1L)) {
                var request=new AiRequest(user,"deepseek","irrelevant","test","report","user-key-must-not-be-used",Map.of(),List.of(),null);
                assertTrue(service.retrieve(request,"运动建议").contains("每天适量运动"));
            }
            verify(factory,times(1)).embedding("platform-vector-key"); verify(factory,never()).embedding("user-key-must-not-be-used");
            verify(model,times(1)).embedAll(anyList()); verify(model,times(2)).embed("运动建议");
            String snapshot=cache.data.keySet().stream().filter(k->k.endsWith("index-snapshot:v2")).findFirst().orElseThrow();
            verify(cache.values,times(2)).get(snapshot); // Initial restore and post-lock recheck only, no per-request reads.
            var restarted=new KnowledgeService(resource,2,factory,cache.redis,"platform-vector-key");
            try { restarted.initializeIndex(); verify(model,times(1)).embedAll(anyList()); }
            finally { restarted.close(); }
        } finally { service.close(); }
    }
    @Test void noPlatformVectorKeyUsesKeywordsEvenIfUserSuppliesAKey() throws Exception {
        var factory=mock(ModelFactory.class); var cache=new RedisMemoryFixture();
        var service=new KnowledgeService(new ByteArrayResource("## 运动\n适量运动".getBytes(StandardCharsets.UTF_8)),2,factory,cache.redis,"");
        assertTrue(service.retrieve(new AiRequest(1L,"deepseek","private","test","report","user-key",Map.of(),List.of(),null),"运动").contains("适量运动"));
        verifyNoInteractions(factory); verify(cache.redis,never()).opsForValue();
    }
}
