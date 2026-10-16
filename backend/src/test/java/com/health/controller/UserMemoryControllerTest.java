package com.health.controller;

import com.health.common.utils.SecurityUtil;
import com.health.memory.LongTermMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserMemoryControllerTest {
    @Test void everyEndpointUsesAuthenticatedOwnerAndIgnoresForgedUserId() throws Exception {
        var service=mock(LongTermMemoryService.class);
        var security=mock(SecurityUtil.class);
        when(security.getCurrentUserId()).thenReturn(1L);
        when(service.view(1L)).thenReturn(new LongTermMemoryService.View(true,List.of()));
        var mvc=MockMvcBuilders.standaloneSetup(new UserMemoryController(service,security)).build();
        mvc.perform(get("/api/chat/memories?userId=2")).andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(true));
        mvc.perform(put("/api/chat/memories/settings").contentType("application/json").content("{\"enabled\":false,\"userId\":2}")).andExpect(status().isOk());
        mvc.perform(put("/api/chat/memories/10").contentType("application/json").content("{\"content\":\"清淡\",\"userId\":2}")).andExpect(status().isOk());
        mvc.perform(delete("/api/chat/memories/10?userId=2")).andExpect(status().isOk());
        mvc.perform(delete("/api/chat/memories?userId=2")).andExpect(status().isOk());
        verify(service).view(1L); verify(service).setEnabled(1L,false); verify(service).edit(1L,10L,"清淡");
        verify(service).delete(1L,10L); verify(service).clear(1L);
        mvc.perform(put("/api/chat/memories/settings").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/chat/memories/10").contentType("application/json").content("{\"content\":\" \"}")).andExpect(status().isBadRequest());
    }
}
