package com.health.memory;

import com.health.ai.AiClient;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssociativeRetrieverTest {
    @Test void relatedOldFactsBeatUnrelatedRecentFactsAndContextStaysWithinBudget() throws Exception {
        var f=new CognitiveMemoryFixture();f.remember("ADD","health.allergy.peanut","我对花生过敏",false);f.remember("ADD","exercise.habit","我每天游泳",false);
        var recall=f.retrieval.retrieve(1L,"花生可以吃吗",f.repository.profile(1L),LocalDateTime.now());
        assertTrue(recall.context().contains("花生过敏"));assertFalse(recall.context().contains("每天游泳"));
        assertTrue(recall.estimatedTokens()<=f.repository.profile(1L).contextBudget());
        assertTrue(f.retrieval.retrieve(2L,"花生",new CognitiveMemoryRepository.Profile(.45,.55,1,1800,0,null),LocalDateTime.now()).hits().isEmpty());
    }
    @Test void sharedEntityCanRecallRelatedPreferenceWithoutLexicalOverlap() {
        var f=new CognitiveMemoryFixture();LocalDateTime now=LocalDateTime.now();
        var seed=new AssociativeRetriever.Node("EPISODE","knee",1,"health.knee","health","膝盖不舒服",.85,false,0,now,null,false,true);
        var linked=new AssociativeRetriever.Node("SEMANTIC","choice",1,"exercise.habit","exercise","偏好低冲击活动",.7,false,0,now,null,false,false);
        var profile=new CognitiveMemoryRepository.Profile(.45,.55,1,1800,0,null);
        var entities=Map.of(seed.key(),Set.of("knee"),linked.key(),Set.of("knee"));
        var full=f.retrieval.rank("膝盖不舒服",List.of(seed,linked),entities,Map.of(),profile,now,true,true,true);
        var noAssociations=f.retrieval.rank("膝盖不舒服",List.of(seed,linked),entities,Map.of(),profile,now,false,true,true);
        assertTrue(full.hits().stream().anyMatch(h->h.node().id().equals("choice")));
        assertFalse(noAssociations.hits().stream().anyMatch(h->h.node().id().equals("choice")));
    }
    @Test void vectorSearchChecksOwnershipVersionHashModelAndDimensionBeforeUse() throws Exception {
        var f=new CognitiveMemoryFixture();f.remember("ADD","diet.preference","偏好清淡饮食",false);Long id=f.id("diet.preference");
        var fact=f.repository.semantic(1L,id);
        when(f.ai.memoryVectors(eq(1L),anyList(),eq(true))).thenReturn(new AiClient.MemoryVectors(true,"model-v1",2,List.of(new float[]{1,0})));
        var valid=new CognitiveMemoryRepository.Vector("SEMANTIC",id.toString(),fact.version(),f.settings.vectorHash(fact.content()),"model-v1",2,"[1,0]");
        f.repository.vector(1L,valid);
        assertTrue(f.retrieval.retrieve(1L,"完全不同的线索",f.repository.profile(1L),LocalDateTime.now()).hits().stream().anyMatch(h->h.node().id().equals(id.toString())));
        for(var invalid:List.of(
                new CognitiveMemoryRepository.Vector("SEMANTIC",id.toString(),fact.version()+1,valid.hash(),valid.model(),2,"[1,0]"),
                new CognitiveMemoryRepository.Vector("SEMANTIC",id.toString(),fact.version(),"wrong-hash",valid.model(),2,"[1,0]"),
                new CognitiveMemoryRepository.Vector("SEMANTIC",id.toString(),fact.version(),valid.hash(),"different-model",2,"[1,0]"),
                new CognitiveMemoryRepository.Vector("SEMANTIC",id.toString(),fact.version(),valid.hash(),valid.model(),3,"[1,0,0]"))) {
            f.repository.vector(1L,invalid);
            assertTrue(f.retrieval.retrieve(1L,"完全不同的线索",f.repository.profile(1L),LocalDateTime.now()).hits().isEmpty());
        }
    }
    @Test void providerFailureFallsBackToLexicalRetrievalAndEmbeddingRevisionInvalidatesCachedVectors() throws Exception {
        var f=new CognitiveMemoryFixture();f.remember("ADD","diet.preference","清淡饮食",false);Long id=f.id("diet.preference");
        String oldHash=f.settings.vectorHash("清淡饮食");f.settings.setEmbeddingRevision("2");assertNotEquals(oldHash,f.settings.vectorHash("清淡饮食"));
        f.repository.vector(1L,new CognitiveMemoryRepository.Vector("SEMANTIC",id.toString(),1,oldHash,"model",2,"[1,0]"));
        when(f.ai.memoryVectors(eq(1L),anyList(),eq(true))).thenThrow(new RuntimeException("offline"));
        assertTrue(f.retrieval.retrieve(1L,"清淡饮食",f.repository.profile(1L),LocalDateTime.now()).context().contains("清淡饮食"));
        assertEquals(0,AssociativeRetriever.cosine(new float[]{Float.NaN},new float[]{1}));
        assertEquals(0,AssociativeRetriever.cosine(new float[]{1},new float[]{1,2}));
    }
}
