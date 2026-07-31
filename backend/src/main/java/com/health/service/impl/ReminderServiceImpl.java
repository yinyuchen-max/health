package com.health.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.health.domain.dto.ReminderPreferenceDTO;
import com.health.domain.dto.SmartRecommendationDTO;
import com.health.domain.entity.ReminderPreference;
import com.health.domain.vo.ReminderPreferenceVO;
import com.health.mapper.ReminderPreferenceMapper;
import com.health.service.ReminderService;
import com.health.service.SmartHealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReminderServiceImpl extends ServiceImpl<ReminderPreferenceMapper, ReminderPreference> implements ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderServiceImpl.class);

    private final SmartHealthService smartHealthService;

    public ReminderServiceImpl(SmartHealthService smartHealthService) {
        this.smartHealthService = smartHealthService;
    }

    @Override
    @Cacheable(value = "reminder:pref", key = "#userId")
    public List<ReminderPreferenceVO> getPreferencesByUserId(Long userId) {
        QueryWrapper<ReminderPreference> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("deleted", 0)
                .orderByDesc("created_at");

        return list(queryWrapper).stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @CacheEvict(value = "reminder:pref", allEntries = true)
    public boolean savePreference(ReminderPreferenceDTO dto) {
        try {
            ReminderPreference preference = convertToEntity(dto);
            if (dto.getId() != null) {
                preference.setUpdatedAt(LocalDateTime.now());
                updateById(preference);
            } else {
                preference.setCreatedAt(LocalDateTime.now());
                preference.setUpdatedAt(LocalDateTime.now());
                save(preference);
            }
            return true;
        } catch (Exception e) {
            log.error("Save reminder preference failed", e);
            return false;
        }
    }

    @Override
    @CacheEvict(value = "reminder:pref", allEntries = true)
    public boolean deletePreference(Long id) {
        try {
            ReminderPreference preference = getById(id);
            if (preference == null) {
                return false;
            }
            preference.setDeleted(1);
            preference.setUpdatedAt(LocalDateTime.now());
            return updateById(preference);
        } catch (Exception e) {
            log.error("Delete reminder preference failed", e);
            return false;
        }
    }

    @Override
    @CacheEvict(value = "reminder:pref", allEntries = true)
    public boolean toggleReminder(Long id, Boolean enabled) {
        try {
            ReminderPreference preference = getById(id);
            if (preference == null) {
                return false;
            }
            preference.setEnabled(enabled);
            preference.setUpdatedAt(LocalDateTime.now());
            return updateById(preference);
        } catch (Exception e) {
            log.error("Toggle reminder failed", e);
            return false;
        }
    }

    @Override
    public boolean updateEffectivenessScore(Long id, Double score) {
        try {
            ReminderPreference preference = getById(id);
            if (preference == null) {
                return false;
            }
            preference.setEffectivenessScore(score);
            preference.setUpdatedAt(LocalDateTime.now());
            return updateById(preference);
        } catch (Exception e) {
            log.error("Update effectiveness score failed", e);
            return false;
        }
    }

    @Override
    public List<SmartRecommendationDTO> generateSmartRecommendations(Long userId) {
        return smartHealthService.generateOverview(userId).getQuickTips().stream()
                .map(tip -> {
                    SmartRecommendationDTO dto = new SmartRecommendationDTO();
                    dto.setType("health");
                    dto.setContent(tip);
                    dto.setConfidenceScore(0.82);
                    dto.setReason("Generated from recent health records, sport records and user profile");
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public boolean bulkUpdatePreferences(List<ReminderPreferenceDTO> updates) {
        try {
            for (ReminderPreferenceDTO update : updates) {
                savePreference(update);
            }
            return true;
        } catch (Exception e) {
            log.error("Bulk update reminder preferences failed", e);
            return false;
        }
    }

    @Override
    public String exportPreferences(Long userId) {
        return "[]";
    }

    @Override
    public boolean importPreferences(Long userId, String jsonData) {
        return false;
    }

    @Override
    public Long getOwnerUserId(Long preferenceId) {
        ReminderPreference preference = getById(preferenceId);
        return preference != null ? preference.getUserId() : null;
    }

    private ReminderPreferenceVO convertToVO(ReminderPreference entity) {
        ReminderPreferenceVO vo = new ReminderPreferenceVO();
        vo.setId(entity.getId());
        vo.setType(entity.getType());
        vo.setTime(entity.getTime());
        vo.setFrequency(entity.getFrequency());
        vo.setSmartMode(entity.getSmartMode());
        vo.setEnabled(entity.getEnabled());
        vo.setEffectivenessScore(entity.getEffectivenessScore());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        vo.setTypeName(getTypeName(entity.getType()));
        return vo;
    }

    private ReminderPreference convertToEntity(ReminderPreferenceDTO dto) {
        ReminderPreference entity = new ReminderPreference();
        entity.setUserId(dto.getUserId());
        entity.setType(dto.getType());
        entity.setTime(dto.getTime());
        entity.setFrequency(dto.getFrequency());
        entity.setSmartMode(dto.getSmartMode());
        entity.setEnabled(dto.getEnabled());
        entity.setEffectivenessScore(dto.getEffectivenessScore());
        if (dto.getId() != null) {
            entity.setId(dto.getId());
        }
        return entity;
    }

    private String getTypeName(String type) {
        return switch (type) {
            case "bloodPressure" -> "血压测量";
            case "bloodSugar" -> "血糖检测";
            case "weight" -> "体重记录";
            case "exercise" -> "运动提醒";
            default -> type;
        };
    }
}
