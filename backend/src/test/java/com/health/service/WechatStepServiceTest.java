package com.health.service;

import com.health.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WechatStepServiceTest {
    JdbcTemplate jdbc;
    WechatStepService service;
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    @BeforeEach void setup() {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(data);
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO sys_user(id) VALUES (1),(2)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V13__wechat_web_login_and_steps.sql")).execute(data);
        service = new WechatStepService(jdbc);
    }
    Map<String, Object> payload(LocalDate date, int steps) {
        return Map.of("stepInfoList", List.of(Map.of("timestamp", date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond(), "step", steps)));
    }
    @Test void repeatedAndOutOfOrderUploadsKeepOneDailyRowAndSeparateUsers() {
        service.sync(1L, payload(today, 8000));
        service.sync(1L, payload(today, 3000));
        service.sync(1L, payload(today, 8500));
        service.sync(2L, payload(today, 12));
        assertEquals(8500, service.summary(1L).todaySteps());
        assertEquals(1, service.summary(1L).history().size());
        assertEquals(12, service.summary(2L).todaySteps());
        assertNotNull(service.summary(1L).lastSyncAt());
    }
    @Test void missingTodayIsNotReplacedByYesterdayAndZeroIsARealReading() {
        service.sync(1L, payload(today.minusDays(1), 9000));
        assertNull(service.summary(1L).todaySteps());
        service.sync(1L, payload(today, 0));
        assertEquals(0, service.summary(1L).todaySteps());
    }
    @Test void invalidHistoryCannotPartiallyWriteAndShanghaiMidnightKeepsTheCorrectDate() {
        long shanghaiMidnight = today.atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond();
        service.sync(1L, Map.of("stepInfoList", List.of(Map.of("timestamp", shanghaiMidnight, "step", 22))));
        assertEquals(today.toString(), service.summary(1L).history().get(0).date());
        assertThrows(BusinessException.class, () -> service.sync(2L, Map.of("stepInfoList", List.of(
                Map.of("timestamp", shanghaiMidnight, "step", 100), Map.of("timestamp", shanghaiMidnight, "step", -1)))));
        assertTrue(service.summary(2L).history().isEmpty());
        assertThrows(BusinessException.class, () -> service.sync(2L, payload(today.plusDays(1), 100)));
    }
}
