package com.health.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.domain.dto.ReminderPreferenceDTO;
import com.health.domain.dto.SmartRecommendationDTO;
import com.health.domain.vo.ReminderPreferenceVO;
import com.health.service.ReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReminderController.class)
class ReminderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReminderService reminderService;

    @Autowired
    private ObjectMapper objectMapper;

    private ReminderPreferenceDTO testDto;

    @BeforeEach
    void setUp() {
        testDto = new ReminderPreferenceDTO();
        testDto.setUserId(1L);
        testDto.setType("bloodPressure");
        testDto.setTime("08:00");
        testDto.setFrequency("daily");
    }

    @Test
    void shouldGetPreferences() throws Exception {
        ReminderPreferenceVO vo = new ReminderPreferenceVO();
        vo.setId(1L);
        vo.setType("bloodPressure");
        vo.setTypeName("血压测量");

        when(reminderService.getPreferencesByUserId(1L)).thenReturn(List.of(vo));

        mockMvc.perform(get("/api/reminder/preferences").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].type").value("bloodPressure"));
    }

    @Test
    void shouldSavePreference() throws Exception {
        when(reminderService.savePreference(any(ReminderPreferenceDTO.class))).thenReturn(true);

        mockMvc.perform(post("/api/reminder/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void shouldGetSmartRecommendations() throws Exception {
        SmartRecommendationDTO dto = new SmartRecommendationDTO();
        dto.setType("health");
        dto.setContent("test");

        when(reminderService.generateSmartRecommendations(1L)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/reminder/smart-recommendations").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].type").value("health"));
    }
}
