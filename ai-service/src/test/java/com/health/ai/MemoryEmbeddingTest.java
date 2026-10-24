package com.health.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryEmbeddingTest {
    @Test void missingPlatformEmbeddingKeyDoesNotCallAnyProvider() {
        var models=mock(ModelFactory.class);var service=new MemoryEmbeddingService("",models);
        assertFalse(service.embed(List.of("花生过敏")).available());verifyNoInteractions(models);
        assertThrows(IllegalArgumentException.class,()->service.embed(List.of()));
        assertThrows(IllegalArgumentException.class,()->service.embed(List.of(" ")));
    }
    @Test void batchEmbedsOnlyRequestedUserTextsWithModelAndDimensionMetadata() {
        var models=mock(ModelFactory.class);var model=mock(EmbeddingModel.class);when(models.memoryEmbedding("platform-key")).thenReturn(model);
        when(model.embedAll(anyList())).thenReturn(Response.from(List.of(Embedding.from(new float[]{1,0}),Embedding.from(new float[]{0,1}))));
        var result=new MemoryEmbeddingService("platform-key",models).embed(List.of("花生过敏","喜欢游泳"));
        assertTrue(result.available());assertEquals(2,result.dimension());assertEquals(2,result.vectors().size());
        assertTrue(result.model().contains(ModelFactory.EMBEDDING_MODEL));
    }
    @Test void internalEndpointBindsOperationAndUserToSignedRequest() throws Exception {
        var auth=mock(InternalAuthorization.class);var service=mock(MemoryEmbeddingService.class);var json=new ObjectMapper();
        var controller=new MemoryEmbeddingController(auth,json,service);
        var request=new AiRequest(1L,"deepseek","private","model","memory-embed",null,Map.of("texts","[\"健康\"]"),List.of(),null);
        String body=json.writeValueAsString(request);when(auth.verify("token",body,"memory-embed")).thenReturn("1");
        when(service.embed(List.of("健康"))).thenReturn(new MemoryEmbeddingService.Batch(false,"model",0,List.of()));
        assertFalse(controller.embed("token",body).available());
        when(auth.verify("token",body,"memory-embed")).thenReturn("2");
        assertThrows(ResponseStatusException.class,()->controller.embed("token",body));
        verify(service,times(1)).embed(anyList());
    }
}
