package com.health.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.common.config.GlobalExceptionHandler;
import com.health.common.exception.BusinessException;
import com.health.common.utils.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class AiConfigSecurityTest {
    private final AiKeyCipher cipher=new AiKeyCipher(Base64.getEncoder().encodeToString(new byte[32]));
    private final AiConfigMapper mapper=mock(AiConfigMapper.class);
    private final AiConfigService service=new AiConfigService(mapper,cipher);
    @Test void keysAreRandomizedAuthenticatedAndBoundToTheirUser() {
        String a=cipher.encrypt(1L,"sk-private-key"),b=cipher.encrypt(1L,"sk-private-key");
        assertNotEquals(a,b); assertFalse(a.contains("sk-private-key")); assertEquals("sk-private-key",cipher.decrypt(1L,a));
        assertThrows(IllegalStateException.class,()->cipher.decrypt(2L,a));
        String corrupt=a.substring(0,a.length()-5)+"AAAA=";
        assertThrows(IllegalStateException.class,()->cipher.decrypt(1L,corrupt));
        assertThrows(IllegalStateException.class,()->new AiKeyCipher("short"));
    }
    @Test void controllerIgnoresSuppliedUserIdAndNeverReturnsAnyKey() throws Exception {
        var security=mock(SecurityUtil.class); when(security.getCurrentUserId()).thenReturn(1L);
        Map<Long,AiConfigMapper.StoredConfig> db=new HashMap<>();
        when(mapper.find(anyLong())).thenAnswer(i->db.get(i.getArgument(0)));
        doAnswer(i->{ var c=i.getArgument(0,AiConfigMapper.StoredConfig.class); db.put(c.userId(),c); return null; }).when(mapper).save(any());
        var mvc=MockMvcBuilders.standaloneSetup(new AiSettingsController(service,security))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(put("/api/ai/settings").contentType("application/json")
                .content("{\"userId\":2,\"provider\":\"deepseek\",\"model\":\"test\",\"apiKey\":\"sk-only-user-one\"}"))
                .andExpect(status().isOk());
        assertTrue(db.containsKey(1L)); assertFalse(db.containsKey(2L));
        assertFalse(db.get(1L).encryptedApiKey().contains("sk-only-user-one"));
        String response=mvc.perform(get("/api/ai/settings")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(response.contains("\"configured\":true")); assertFalse(response.contains("sk-only-user-one"));
        assertFalse(response.contains("encryptedApiKey")); assertFalse(response.contains("apiKey"));
        when(security.getCurrentUserId()).thenReturn(2L);
        mvc.perform(get("/api/ai/settings")).andExpect(jsonPath("$.data.configured").value(false));
        mvc.perform(delete("/api/ai/settings")).andExpect(status().isOk()); verify(mapper).delete(2L);
        assertTrue(db.containsKey(1L));
    }
    @Test void blankKeepsOnlySameProviderAndEveryUpdateChangesCacheRevision() {
        service.save(1L,new AiConfigService.Update("deepseek","test","sk-private-key",null,false));
        var captured=org.mockito.ArgumentCaptor.forClass(AiConfigMapper.StoredConfig.class); verify(mapper).save(captured.capture());
        var previous=captured.getValue(); when(mapper.find(1L)).thenReturn(previous);
        service.save(1L,new AiConfigService.Update("deepseek","test2","",null,false));
        verify(mapper,times(2)).save(captured.capture()); var next=captured.getValue();
        assertEquals(previous.encryptedApiKey(),next.encryptedApiKey()); assertNotEquals(previous.revision(),next.revision());
        assertThrows(BusinessException.class,()->service.save(1L,new AiConfigService.Update("dashscope","qwen-plus","",null,false)));
        assertThrows(BusinessException.class,()->service.save(1L,new AiConfigService.Update("http://evil","test","sk-secret",null,false)));
        assertEquals("sk-private-key",service.credentials(1L).apiKey());
        assertFalse(new ObjectMapper().valueToTree(service.settings(1L)).toString().contains("sk-private-key"));
        assertFalse(service.credentials(1L).toString().contains("sk-private-key"));
    }
    @Test void unconfiguredAccountFailsWithoutAnySharedFallback() {
        assertThrows(AiServiceException.class,()->service.credentials(42L));
        assertEquals("unconfigured",service.revision(42L)); verifyNoMoreInteractionsAfterRead();
    }
    private void verifyNoMoreInteractionsAfterRead() { verify(mapper,times(2)).find(42L); verifyNoMoreInteractions(mapper); }
}
