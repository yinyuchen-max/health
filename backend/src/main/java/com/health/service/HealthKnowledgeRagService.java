package com.health.service;

import com.health.domain.entity.HealthRecord;
import com.health.domain.entity.SportRecord;
import com.health.domain.entity.User;

import java.util.List;

public interface HealthKnowledgeRagService {

    List<String> retrieveRelevantKnowledge(
            User user,
            List<HealthRecord> healthRecords,
            List<SportRecord> sportRecords,
            Double bmi,
            Integer weeklyExerciseMinutes
    );
}
