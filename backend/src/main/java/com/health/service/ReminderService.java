package com.health.service;

import com.health.domain.dto.ReminderPreferenceDTO;
import com.health.domain.dto.SmartRecommendationDTO;
import com.health.domain.vo.ReminderPreferenceVO;

import java.util.List;

public interface ReminderService {

    /**
     * Get all reminder preferences for a user
     */
    List<ReminderPreferenceVO> getPreferencesByUserId(Long userId);

    /**
     * Save or update reminder preference
     */
    boolean savePreference(ReminderPreferenceDTO dto);

    /**
     * Delete reminder preference
     */
    boolean deletePreference(Long id);

    /**
     * Toggle reminder enabled status
     */
    boolean toggleReminder(Long id, Boolean enabled);

    /**
     * Update effectiveness score
     */
    boolean updateEffectivenessScore(Long id, Double score);

    /**
     * Generate smart recommendations
     */
    List<SmartRecommendationDTO> generateSmartRecommendations(Long userId);

    /**
     * Bulk update preferences
     */
    boolean bulkUpdatePreferences(List<ReminderPreferenceDTO> updates);

    /**
     * Export preferences as JSON
     */
    String exportPreferences(Long userId);

    /**
     * Import preferences from JSON
     */
    boolean importPreferences(Long userId, String jsonData);
}