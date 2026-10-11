package com.health.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class AiControllerTest {
    @Test void verifiesSubjectAndSanitizesProviderErrors() throws Exception {
        var auth=mock(InternalAuthorization.class); var engine=mock(AiEngine.class); var json=new ObjectMapper();
        when(auth.verify(any(),anyString(),eq("complete"))).thenReturn("1");
        var mvc=MockMvcBuilders.standaloneSetup(new AiController(auth,json,engine,1)).build();
        var invocation=new AiRequest(2L,"deepseek","sk-secret","test","chat",null,Map.of(),List.of(),"hi");
        mvc.perform(post("/internal/ai/complete").contentType("application/json").content(json.writeValueAsString(invocation)))
                .andExpect(status().isBadRequest()); verifyNoInteractions(engine);
        invocation=new AiRequest(1L,"deepseek","sk-secret","test","chat",null,Map.of(),List.of(),"hi");
        when(engine.complete(any())).thenThrow(new RuntimeException("upstream echoed sk-secret"));
        var result=mvc.perform(post("/internal/ai/complete").contentType("application/json").content(json.writeValueAsString(invocation)))
                .andExpect(status().isUnprocessableEntity()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("sk-secret"));
    }
    @Test void streamPreservesTokenAndDoneEvents() throws Exception {
        var auth=mock(InternalAuthorization.class); var engine=mock(AiEngine.class); var json=new ObjectMapper();
        when(auth.verify(any(),anyString(),eq("stream"))).thenReturn("1");
        doAnswer(i->{ var h=i.getArgument(1,StreamingChatResponseHandler.class);
            h.onPartialResponse("回答\n换行"); h.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("完整回答")).build()); return null;
        }).when(engine).stream(any(),any());
        var mvc=MockMvcBuilders.standaloneSetup(new AiController(auth,json,engine,1)).build();
        var invocation=new AiRequest(1L,"deepseek","sk-secret","test","chat",null,Map.of(),List.of(),"hi");
        var result=mvc.perform(post("/internal/ai/stream").contentType("application/json").content(json.writeValueAsString(invocation)))
                .andExpect(request().asyncStarted()).andReturn();
        String body=result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains("event:token")); assertTrue(body.contains("event:done")); assertTrue(body.contains("\\n"));
    }
}
