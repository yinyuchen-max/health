package com.health.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.common.config.GlobalExceptionHandler;
import com.health.common.utils.SecurityUtil;
import com.health.controller.ChatController;
import com.health.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiEntitlementBoundaryTest {
    @Test void directCompleteStreamPrepareAndCachedReportScopeCannotBypassPayment() throws Exception {
        var access=mock(AiAccessService.class); var config=mock(AiConfigService.class);
        doThrow(new AiPaymentRequiredException()).when(access).requireAccess(1L);
        try(var client=new AiClient(config,access,mock(SecurityUtil.class),new ObjectMapper(),"http://127.0.0.1:1",Base64.getEncoder().encodeToString(new byte[32]))) {
            assertThrows(AiPaymentRequiredException.class,()->client.prepare(1L,"chat",Map.of(),List.of(),"test"));
            assertThrows(AiPaymentRequiredException.class,()->client.cacheScope(1L));
            var forged=new AiRequest(1L,"deepseek","forged","test","chat",null,Map.of(),List.of(),"test");
            assertThrows(AiPaymentRequiredException.class,()->client.complete(forged));
            assertThrows(AiPaymentRequiredException.class,()->client.stream(forged,mock(ChatService.StreamCallback.class)));
            verifyNoInteractions(config);
        }
    }
    @Test void bothChatEndpointsReturn402BeforeStartingAStream() throws Exception {
        var service=mock(ChatService.class); var security=mock(SecurityUtil.class); when(security.getCurrentUserId()).thenReturn(1L);
        when(service.chat(anyString(),eq(1L))).thenThrow(new AiPaymentRequiredException());
        doThrow(new AiPaymentRequiredException()).when(service).chatStream(anyString(),eq(1L),any());
        var mvc=MockMvcBuilders.standaloneSetup(new ChatController(service,security)).setControllerAdvice(new GlobalExceptionHandler()).build();
        for(String path:List.of("/api/chat/send","/api/chat/send/stream"))
            mvc.perform(post(path).accept(path.endsWith("stream") ? "text/event-stream" : "application/json")
                            .contentType("application/json").content("{\"message\":\"hello\",\"userId\":2}"))
                    .andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.code").value(402));
    }
}
