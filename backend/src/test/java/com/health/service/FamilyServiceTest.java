package com.health.service;

import com.health.common.config.GlobalExceptionHandler;
import com.health.common.exception.BusinessException;
import com.health.common.exception.ForbiddenException;
import com.health.common.utils.SecurityUtil;
import com.health.controller.FamilyController;
import com.health.domain.dto.FamilyDtos.Invite;
import com.health.domain.dto.SmartHealthOverviewDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 实际执行 V7 迁移、SQL、Spring事务，使用独立内存库，不连接业务数据库。 */
class FamilyServiceTest {
    AnnotationConfigApplicationContext context;
    FamilyService family;
    SmartHealthService smart;
    JdbcTemplate jdbc;

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(DatabaseConfig.class);
        family = context.getBean(FamilyService.class);
        smart = context.getBean(SmartHealthService.class);
        jdbc = context.getBean(JdbcTemplate.class);
    }
    @AfterEach void close() { context.close(); }
    long invite() {
        family.invite(1, new Invite("13900000002", "爸爸"));
        return family.list(1).get(0).id();
    }
    long bind() { long id = invite(); family.decide(2, id, true); return id; }

    @Test void invitationRequiresConsentAndSharesOnlyTheBoundAccountReport() {
        long id = invite();
        var outgoing = family.list(1).get(0);
        var incoming = family.list(2).get(0);
        assertFalse(outgoing.incoming()); assertTrue(incoming.incoming());
        assertEquals("139****0002", outgoing.maskedPhone());
        assertThrows(ForbiddenException.class, () -> family.report(1, id));
        assertThrows(ForbiddenException.class, () -> family.remind(1, id, "bloodPressure"));
        assertThrows(ForbiddenException.class, () -> family.decide(1, id, true));
        assertThrows(ForbiddenException.class, () -> family.decide(3, id, true));
        verifyNoInteractions(smart);
        family.decide(2, id, true);
        var overview = new SmartHealthOverviewDTO(); overview.setUserId(2L);
        when(smart.generateOverview(2L)).thenReturn(overview);
        assertSame(overview, family.report(1, id));
        family.report(2, id);
        verify(smart).generateOverview(1L);
        assertThrows(ForbiddenException.class, () -> family.report(3, id));
        assertThrows(ForbiddenException.class, () -> family.remove(3, id));
    }

    @Test void duplicateReverseAndSelfInvitationsCannotGrantAccess() {
        long id = invite();
        assertThrows(BusinessException.class, () -> family.invite(1, new Invite("13900000002", "爸爸")));
        assertThrows(BusinessException.class, () -> family.invite(2, new Invite("13900000001", "孩子")));
        assertThrows(BusinessException.class, () -> family.invite(1, new Invite("13900000001", "自己")));
        assertEquals("PENDING", family.list(1).get(0).status());
        assertThrows(ForbiddenException.class, () -> family.report(1, id));
    }

    @Test void rejectedAndWithdrawnInvitationsNeedFreshConsent() {
        long id = invite(); family.decide(2, id, false);
        assertTrue(family.list(1).isEmpty());
        assertThrows(ForbiddenException.class, () -> family.report(1, id));
        assertThrows(BusinessException.class, () -> family.decide(2, id, true));
        family.invite(2, new Invite("13900000001", "孩子"));
        assertThrows(ForbiddenException.class, () -> family.decide(2, id, true));
        family.remove(2, id);
        assertThrows(BusinessException.class, () -> family.decide(1, id, true));
        assertTrue(family.list(1).isEmpty());
    }

    @Test void eitherMemberCanRevokeReportAndReminderAccess() {
        long id = bind(); family.remove(2, id);
        assertThrows(ForbiddenException.class, () -> family.report(1, id));
        assertThrows(ForbiddenException.class, () -> family.remind(1, id, "exercise"));
        verifyNoInteractions(smart);
        family.invite(1, new Invite("13900000002", "爸爸"));
        assertThrows(ForbiddenException.class, () -> family.report(1, id));
    }

    @Test void revokedDuringReportGenerationCannotReturnSensitiveData() {
        long id = bind();
        when(smart.generateOverview(2L)).thenAnswer(call -> {
            family.remove(2, id);
            return new SmartHealthOverviewDTO();
        });
        assertThrows(ForbiddenException.class, () -> family.report(1, id));
    }

    @Test void disabledDeletedMissingOrAmbiguousAccountsAreRejected() {
        assertThrows(BusinessException.class, () -> family.invite(1, new Invite("13900000009", "家人")));
        jdbc.update("INSERT INTO sys_user (id, username, phone) VALUES (4, 'same-phone', '13900000002')");
        assertThrows(BusinessException.class, () -> family.invite(1, new Invite("13900000002", "爸爸")));
        jdbc.update("DELETE FROM sys_user WHERE id = 4");
        long id = bind(); jdbc.update("UPDATE sys_user SET status = 0 WHERE id = 2");
        assertTrue(family.list(1).isEmpty());
        assertThrows(ForbiddenException.class, () -> family.report(1, id));
        assertThrows(ForbiddenException.class, () -> family.remind(1, id, "exercise"));
        jdbc.update("UPDATE sys_user SET status = 1, deleted = 1 WHERE id = 2");
        assertThrows(ForbiddenException.class, () -> family.report(1, id));
        verifyNoInteractions(smart);
    }

    @Test void remindersBelongToRecipientAndCooldownCannotBeBypassedByRebinding() {
        long id = bind(); family.remind(1, id, "bloodPressure");
        assertTrue(family.reminders(1).isEmpty()); assertTrue(family.reminders(3).isEmpty());
        var notification = family.reminders(2).get(0);
        assertEquals("child", notification.senderName()); assertFalse(notification.readStatus());
        assertThrows(ForbiddenException.class, () -> family.acknowledge(1, notification.id(), true));
        family.acknowledge(2, notification.id(), false);
        assertTrue(family.reminders(2).get(0).readStatus()); assertFalse(family.reminders(2).get(0).completed());
        family.acknowledge(2, notification.id(), true);
        assertTrue(family.reminders(2).get(0).completed());
        assertThrows(BusinessException.class, () -> family.remind(1, id, "bloodPressure"));
        family.remind(1, id, "exercise");
        assertThrows(BusinessException.class, () -> family.remind(1, id, "unsupported"));
        family.remove(1, id); family.invite(1, new Invite("13900000002", "爸爸")); family.decide(2, id, true);
        assertThrows(BusinessException.class, () -> family.remind(1, id, "bloodPressure"));
        jdbc.update("UPDATE family_reminder SET created_at = ?", java.time.LocalDateTime.now().minusMinutes(31));
        family.remind(1, id, "bloodPressure");
        assertEquals(3, family.reminders(2).size());
    }

    @Test void concurrentReminderRequestsHaveOneWinner() throws Exception {
        long id = bind(); var barrier = new CyclicBarrier(2); var workers = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> send = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                try { family.remind(1, id, "exercise"); return true; } catch (BusinessException e) { return false; }
            };
            var a = workers.submit(send); var b = workers.submit(send);
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(1, family.reminders(2).size());
        } finally { workers.shutdownNow(); }
    }

    @Test void simultaneousOppositeInvitationsCreateOnePendingRelationship() throws Exception {
        var barrier = new CyclicBarrier(2); var workers = Executors.newFixedThreadPool(2);
        try {
            var a = workers.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                try { family.invite(1, new Invite("13900000002", "爸爸")); return true; } catch (BusinessException e) { return false; }
            });
            var b = workers.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                try { family.invite(2, new Invite("13900000001", "孩子")); return true; } catch (BusinessException e) { return false; }
            });
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(1, family.list(1).size());
            assertEquals("PENDING", family.list(1).get(0).status());
        } finally { workers.shutdownNow(); }
    }

    @Test void apiUsesSessionOwnerValidatesInputAndRejectsUnrelatedAccount() throws Exception {
        var security = mock(SecurityUtil.class); when(security.getCurrentUserId()).thenReturn(1L);
        var mvc = MockMvcBuilders.standaloneSetup(new FamilyController(family, security))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/family/invitations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"abc\",\"relationship\":\"爸爸\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/family/invitations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"13900000002\",\"relationship\":\"爸爸\",\"userId\":3}"))
                .andExpect(status().isOk());
        long id = family.list(1).get(0).id();
        mvc.perform(put("/api/family/invitations/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        when(security.getCurrentUserId()).thenReturn(3L);
        mvc.perform(get("/api/family/members/" + id + "/report"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        mvc.perform(post("/api/family/members/" + id + "/reminders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"spam\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(smart);
    }

    @TestConfiguration @EnableTransactionManagement(proxyTargetClass = true)
    static class DatabaseConfig {
        @Bean DataSource dataSource() {
            var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE sys_user (id BIGINT PRIMARY KEY, username VARCHAR(40), phone VARCHAR(20), deleted INT DEFAULT 0, status INT DEFAULT 1)");
            jdbc.update("INSERT INTO sys_user (id, username, phone) VALUES (1, 'child', '13900000001'), (2, 'parent', '13900000002'), (3, 'stranger', '13900000003')");
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V7__add_family_members.sql")).execute(source);
            return source;
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean SmartHealthService smartHealthService() { return mock(SmartHealthService.class); }
        @Bean FamilyService familyService(JdbcTemplate jdbc, SmartHealthService smart) { return new FamilyService(jdbc, smart); }
    }
}
