package com.health.service;

import com.health.domain.dto.SmartHealthOverviewDTO;

public interface SmartHealthService {

    SmartHealthOverviewDTO generateOverview(Long userId);

    SmartHealthOverviewDTO generateOverview(Long userId, boolean refresh);
}
