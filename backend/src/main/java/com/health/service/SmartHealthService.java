package com.health.service;

import com.health.domain.dto.SmartHealthOverviewDTO;

public interface SmartHealthService {

    SmartHealthOverviewDTO generateOverview(Long userId);
}
