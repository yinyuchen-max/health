package com.health.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.common.config.GlobalExceptionHandler;
import com.health.common.utils.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiConfigSecurityTest {
    private final AiConfigService service=new AiConfigService("deepseek","test","sk-platform-private","v1");
    @Test void platformCredentialsAreSharedAndNeverExposedBySettings() throws Exception {
        assertEquals(service.credentials(1L),service.credentials(2L));
        assertEquals("sk-platform-private",service.credentials(1L).apiKey());
        assertNull(service.credentials(1L).embeddingApiKey());
        assertFalse(service.credentials(1L).toString().contains("sk-platform-private"));
        assertFalse(new ObjectMapper().writeValueAsString(service.settings(1L)).contains("sk-platform-private"));
        assertEquals(service.revision(1L),service.revision(2L));
        assertNotEquals(service.revision(1L),new AiConfigService("deepseek","test2","sk-platform-private","v1").revision(1L));
        assertNotEquals(service.revision(1L),new AiConfigService("deepseek","test","sk-platform-private","v2").revision(1L));
    }
    @Test void statusUsesAuthenticatedUserAndLegacyKeyWritesAreForbidden() throws Exception {
        var security=mock(SecurityUtil.class); when(security.getCurrentUserId()).thenReturn(1L);
        var access=mock(AiAccessService.class); when(access.status(1L)).thenReturn(new AiAccessService.Access(false,false,null));
        var mvc=MockMvcBuilders.standaloneSetup(new AiSettingsController(service,access,security))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String body=mvc.perform(get("/api/ai/access?userId=2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowed").value(false))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("sk-platform-private")); assertFalse(body.contains("apiKey"));
        verify(access).status(1L); verify(access,never()).status(2L);
        mvc.perform(put("/api/ai/settings").contentType("application/json").content("{\"apiKey\":\"sk-bypass\",\"allowed\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/ai/settings")).andExpect(status().isForbidden());
    }
    @Test void noKeyDoesNotFallBackToHistoricalUserConfiguration() {
        assertThrows(AiServiceException.class,()->new AiConfigService("deepseek","test","","1").credentials(1L));
        assertThrows(IllegalArgumentException.class,()->new AiConfigService("http://evil","test","secret","1"));
    }
}
