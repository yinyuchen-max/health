package com.health.service;

import com.health.domain.dto.ReminderPreferenceDTO;
import com.health.domain.entity.ReminderPreference;
import com.health.domain.vo.ReminderPreferenceVO;
import com.health.mapper.ReminderPreferenceMapper;
import com.health.service.impl.ReminderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class ReminderServiceTest {

    @InjectMocks
    private ReminderServiceImpl reminderService;

    @Mock
    private SmartHealthService smartHealthService;

    @Mock
    private ReminderPreferenceMapper reminderPreferenceMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldMapPreferencesToVo() {
        ReminderPreference entity = new ReminderPreference();
        entity.setId(1L);
        entity.setUserId(1L);
        entity.setType("bloodPressure");
        entity.setTime("08:00");
        entity.setFrequency("daily");
        entity.setDeleted(0);

        doReturn(List.of(entity)).when(reminderService).list(any());

        List<ReminderPreferenceVO> result = reminderService.getPreferencesByUserId(1L);
        assertEquals(1, result.size());
        assertEquals("bloodPressure", result.get(0).getType());
    }

    @Test
    void shouldSavePreference() {
        ReminderPreferenceDTO dto = new ReminderPreferenceDTO();
        dto.setUserId(1L);
        dto.setType("bloodPressure");
        dto.setTime("08:00");
        dto.setFrequency("daily");

        doReturn(true).when(reminderService).save(any(ReminderPreference.class));

        assertTrue(reminderService.savePreference(dto));
    }
}
