package com.health.ai;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.*;
import dev.langchain4j.model.chat.response.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class AiEngineTest {
    private final ModelFactory models=mock(ModelFactory.class);
    private final PromptService prompts=new PromptService("");
    private KnowledgeService knowledge() throws Exception {
        return new KnowledgeService(new ClassPathResource("rag/health-knowledge.md"),2,models,mock(StringRedisTemplate.class));
    }
    @Test void startupLoadsKnowledgeWithoutCallingAnyPaidModelAndChatPreservesHistory() throws Exception {
        var engine=new AiEngine(prompts,knowledge(),models); verifyNoInteractions(models);
        var r=new AiRequest(1L,"deepseek","sk-private","test","chat",null,
                Map.of("healthRecords","心率75 {{departments}}","sportRecords","运动30分钟"),
                List.of(new AiRequest.Message("user","以前的问题"),new AiRequest.Message("assistant","以前的回答")),"今天呢");
        var messages=engine.messages(r);
        assertEquals(4,messages.size()); assertTrue(((SystemMessage)messages.get(0)).text().contains("心率75 {{departments}}"));
        assertEquals("以前的问题",((UserMessage)messages.get(1)).singleText());
        assertEquals("今天呢",((UserMessage)messages.get(3)).singleText());
        assertFalse(r.toString().contains("sk-private"));
    }
    @Test void reportInjectsKnowledgeOnAiSideWithNoEmbeddingKey() throws Exception {
        var engine=new AiEngine(prompts,knowledge(),models);
        var r=new AiRequest(1L,"deepseek","sk-private","test","report",null,Map.of(
                "userProfile","BMI30", "latestMetrics","血压140/90", "healthRecords","心率75",
                "sportRecords","运动30分钟","historyRecords","暂无"),List.of(),null);
        var messages=engine.messages(r);
        assertTrue(((UserMessage)messages.get(1)).singleText().contains("血压"));
        assertFalse(((UserMessage)messages.get(1)).singleText().contains("{{"));
        verifyNoInteractions(models);
    }
    @Test void everyCallBuildsModelWithThatRequestsKey() throws Exception {
        var engine=new AiEngine(prompts,knowledge(),models); ChatModel model=mock(ChatModel.class);
        when(model.chat(anyList())).thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());
        when(models.chat(any())).thenReturn(model);
        for(long id:List.of(1L,2L)) {
            var r=new AiRequest(id,"deepseek","key-for-"+id,"test","chat",null,
                    Map.of("healthRecords","暂无", "sportRecords","暂无"),List.of(),"hi");
            assertEquals("ok",engine.complete(r)); verify(models).chat(same(r));
        }
    }
}
