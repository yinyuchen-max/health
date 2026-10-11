package com.health.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.health.common.config.GlobalExceptionHandler;
import com.health.common.config.MyBatisPlusConfig;
import com.health.common.exception.BusinessException;
import com.health.common.exception.RecordConflictException;
import com.health.common.utils.SecurityUtil;
import com.health.controller.HealthRecordController;
import com.health.domain.dto.HealthRecordDTO;
import com.health.domain.entity.HealthRecord;
import com.health.domain.vo.HealthRecordVO;
import com.health.mapper.HealthRecordMapper;
import com.health.service.HistoryRecordService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用实际 MyBatis 插件、SQL 和 Spring 事务；数据库为独立 H2 内存库。 */
class HealthRecordOptimisticLockTest {
    private AnnotationConfigApplicationContext context;
    private HealthRecordServiceImpl service;
    private HealthRecordMapper mapper;
    private HistoryRecordService history;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(DatabaseConfiguration.class);
        service = context.getBean(HealthRecordServiceImpl.class);
        mapper = context.getBean(HealthRecordMapper.class);
        history = context.getBean(HistoryRecordService.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void migrationInitializesExistingRowsAndListsReturnTheirVersion() {
        assertEquals(0, mapper.selectById(1L).getVersion());
        @SuppressWarnings("unchecked")
        var records = (List<HealthRecordVO>) service.getHealthRecordsByUserId(42L, 1, 10).get("records");
        assertEquals(0, records.get(0).getVersion());
    }

    @Test
    void concurrentSqlUpdatesFromTheSameSnapshotHaveExactlyOneWinner() throws Exception {
        HealthRecord first = mapper.selectById(1L);
        HealthRecord second = mapper.selectById(1L);
        first.setNotes("first writer");
        second.setNotes("second writer");
        var barrier = new CyclicBarrier(2);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var firstWrite = workers.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return mapper.updateById(first);
            });
            var secondWrite = workers.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return mapper.updateById(second);
            });
            int firstCount = firstWrite.get(10, TimeUnit.SECONDS);
            int secondCount = secondWrite.get(10, TimeUnit.SECONDS);
            assertEquals(1, firstCount + secondCount);
            HealthRecord saved = mapper.selectById(1L);
            assertEquals(1, saved.getVersion());
            assertEquals(firstCount == 1 ? "first writer" : "second writer", saved.getNotes());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void staleBrowserEditDoesNotOverwriteRecordOrSynchronizeHistory() {
        service.updateHealthRecord(1L, edit(0, "winner"));
        clearInvocations(history);
        assertThrows(RecordConflictException.class, () -> service.updateHealthRecord(1L, edit(0, "stale")));
        assertEquals("winner", mapper.selectById(1L).getNotes());
        assertEquals(1, mapper.selectById(1L).getVersion());
        verifyNoInteractions(history);
    }

    @Test
    void missingOrNegativeVersionCannotBypassTheLock() {
        assertThrows(BusinessException.class, () -> service.updateHealthRecord(1L, edit(null, "missing")));
        assertThrows(BusinessException.class, () -> service.updateHealthRecord(1L, edit(-1, "negative")));
        assertEquals(0, mapper.selectById(1L).getVersion());
        assertEquals("original", mapper.selectById(1L).getNotes());
        verifyNoInteractions(history);
    }

    @Test
    void historyFailureRollsBackTheRecordAndVersionIncrement() {
        doThrow(new IllegalStateException("history unavailable")).when(history)
                .syncSourceRecord(anyLong(), anyString(), anyLong(), anyString(), anyString(), anyString());
        assertThrows(IllegalStateException.class, () -> service.updateHealthRecord(1L, edit(0, "uncommitted")));
        assertEquals("original", mapper.selectById(1L).getNotes());
        assertEquals(0, mapper.selectById(1L).getVersion());
    }

    @Test
    void newRecordVersionIsInitializedByServer() {
        service.addHealthRecord(edit(99, "new record"));
        assertEquals(0, jdbc.queryForObject("SELECT version FROM health_record WHERE notes = 'new record'", Integer.class));
    }

    @Test
    void deletionDoesNotRewriteSnapshotFieldsAndStaleUpdatesCannotRestoreIt() {
        HealthRecord snapshot = mapper.selectById(1L);
        service.deleteHealthRecord(1L);
        assertNull(mapper.selectById(1L));
        snapshot.setNotes("stale after delete");
        assertEquals(0, mapper.updateById(snapshot));
        assertEquals(1, jdbc.queryForObject("SELECT version FROM health_record WHERE id = 1", Integer.class));
        assertEquals("original", jdbc.queryForObject("SELECT notes FROM health_record WHERE id = 1", String.class));
        verify(history).deleteBySourceRecord("health", 1L);
    }

    @Test
    void staleDeleteSnapshotCannotDeleteAConcurrentUpdate() {
        service.updateHealthRecord(1L, edit(0, "updated"));
        assertEquals(0, mapper.softDeleteWithVersion(1L, 0));
        assertEquals("updated", mapper.selectById(1L).getNotes());
    }

    @Test
    void apiReturnsHttp409AndKeepsTheWinningData() throws Exception {
        service.updateHealthRecord(1L, edit(0, "winner"));
        clearInvocations(history);
        var controller = new HealthRecordController();
        ReflectionTestUtils.setField(controller, "healthRecordService", service);
        var security = mock(SecurityUtil.class);
        ReflectionTestUtils.setField(controller, "securityUtil", security);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(put("/api/health/record/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":42,\"version\":0,\"recordDate\":\"2026-10-03\",\"notes\":\"stale\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
        verify(security).requireRecordOwner(42L);
        assertEquals("winner", mapper.selectById(1L).getNotes());
        verifyNoInteractions(history);
    }

    private HealthRecordDTO edit(Integer version, String notes) {
        var dto = new HealthRecordDTO();
        dto.setUserId(42L);
        dto.setVersion(version);
        dto.setRecordDate("2026-10-03");
        dto.setNotes(notes);
        return dto;
    }

    @TestConfiguration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class DatabaseConfiguration {
        @Bean
        DataSource dataSource() {
            var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    CREATE TABLE health_record (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
                        blood_pressure_systolic DECIMAL(5,2), blood_pressure_diastolic DECIMAL(5,2),
                        heart_rate INT, blood_sugar DECIMAL(6,2), weight DECIMAL(5,2),
                        record_date DATE NOT NULL, notes TEXT, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        deleted INT DEFAULT 0)
                    """);
            jdbc.update("INSERT INTO health_record (user_id, record_date, notes) VALUES (42, '2026-10-03', 'original')");
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V6__add_health_record_version.sql"))
                    .execute(source);
            return source;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            var global = new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig().setLogicDeleteField("deleted"));
            GlobalConfigUtils.setGlobalConfig(configuration, global);
            configuration.addMapper(HealthRecordMapper.class);
            factory.setConfiguration(configuration);
            factory.setGlobalConfig(global);
            factory.setPlugins(new MyBatisPlusConfig().mybatisPlusInterceptor());
            return factory.getObject();
        }

        @Bean
        HealthRecordMapper healthRecordMapper(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(HealthRecordMapper.class);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        HistoryRecordService historyRecordService() {
            return mock(HistoryRecordService.class);
        }

        @Bean
        HealthRecordServiceImpl healthRecordService(HistoryRecordService history) {
            return new HealthRecordServiceImpl(history);
        }
    }
}
