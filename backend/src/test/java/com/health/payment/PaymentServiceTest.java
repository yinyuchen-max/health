package com.health.payment;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.health.ai.*;
import com.health.common.exception.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PaymentServiceTest {
    JdbcTemplate jdbc;
    PaymentMapper mapper;
    AiAccessMapper entitlements;
    PaymentService service;
    PaymentProperties properties;
    TransactionTemplate transactions;
    AiAccessService access;
    PaymentGateway gateway;
    final AiConfigService config=new AiConfigService("deepseek","test","platform-secret","1");
    public static LocalDateTime utcNow() { return LocalDateTime.now(ZoneOffset.UTC); }
    @BeforeEach void setup() throws Exception {
        var ds=new JdbcDataSource(); ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE ALIAS UTC_TIMESTAMP FOR 'com.health.payment.PaymentServiceTest.utcNow'");
        jdbc.execute("CREATE TABLE sys_user (id BIGINT PRIMARY KEY,status INT,deleted INT)");
        jdbc.execute("INSERT INTO sys_user VALUES(1,1,0),(2,1,0)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V10__add_ai_entitlement.sql")).execute(ds);
        var c=new MybatisConfiguration(); c.setMapUnderscoreToCamelCase(true); c.addMapper(PaymentMapper.class); c.addMapper(AiAccessMapper.class);
        var factory=new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(c);
        var session=new SqlSessionTemplate(factory.getObject()); mapper=session.getMapper(PaymentMapper.class); entitlements=session.getMapper(AiAccessMapper.class);
        transactions=new TransactionTemplate(new DataSourceTransactionManager(ds));
        properties=new PaymentProperties(); properties.setEnabled(true);
        gateway=mock(PaymentGateway.class); when(gateway.channel()).thenReturn("wechat"); when(gateway.enabled()).thenReturn(true);
        when(gateway.create(any())).thenReturn("weixin://wxpay/bizpayurl?pr=test");
        access=mock(AiAccessService.class); when(access.status(anyLong())).thenReturn(new AiAccessService.Access(false,false,null));
        service=new PaymentService(mapper,transactions,properties,access,config,List.of(gateway));
    }
    PaymentOrder create() { var view=service.create(1L,"plus","wechat",UUID.randomUUID().toString()); return mapper.find(view.id()); }
    PaymentGateway.PaymentResult paid(PaymentOrder order) {
        return new PaymentGateway.PaymentResult("wechat",order.id(),"transaction-"+order.id(),order.amountCents(),"CNY","PAID");
    }
    @Test void serverPricesAndIdempotentRequestsAndSinglePendingOrder() {
        assertEquals(List.of(990,1990,2990),service.plans().stream().map(PaymentService.Plan::amountCents).toList());
        String request=UUID.randomUUID().toString();
        var first=service.create(1L,"plus","wechat",request);
        assertEquals(first.id(),service.create(1L,"plus","wechat",request).id());
        assertEquals(first.id(),service.create(1L,"plus","wechat",UUID.randomUUID().toString()).id());
        assertEquals(990,first.amountCents()); verify(gateway,times(1)).create(any());
        assertThrows(BusinessException.class,()->service.create(1L,"pro","wechat",request));
        assertThrows(BusinessException.class,()->service.create(1L,"pro","wechat",UUID.randomUUID().toString()));
        assertNull(entitlements.find(1L));
    }
    @Test void concurrentDuplicateCallbacksGrantExactlyOneCalendarMonthAndRenewalExtends() throws Exception {
        var order=create(); var executor=Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> calls=new ArrayList<>();
            for(int i=0;i<8;i++) calls.add(()->{service.settle(paid(order));return null;});
            for(var result:executor.invokeAll(calls)) result.get();
        } finally { executor.shutdownNow(); }
        var saved=mapper.find(order.id()); var membership=mapper.membership(1L);
        assertEquals("PAID",saved.status()); assertEquals(saved.paidAt().plusMonths(1),membership.expiresAt());
        assertTrue(entitlements.find(1L).active());
        var renewal=create(); service.settle(paid(renewal));
        assertEquals(membership.expiresAt().plusMonths(1),mapper.membership(1L).expiresAt());
        assertThrows(BusinessException.class,()->service.create(1L,"pro","wechat",UUID.randomUUID().toString()));
    }
    @Test void wrongAmountChannelCurrencyAndForgedNotificationsNeverGrant() {
        var order=create();
        for(var result:List.of(
                new PaymentGateway.PaymentResult("wechat",order.id(),"x",1,"CNY","PAID"),
                new PaymentGateway.PaymentResult("alipay",order.id(),"x",990,"CNY","PAID"),
                new PaymentGateway.PaymentResult("wechat",order.id(),"x",990,"USD","PAID")))
            assertThrows(IllegalArgumentException.class,()->service.settle(result));
        when(gateway.notification(any(),anyMap(),anyMap())).thenThrow(new IllegalArgumentException("bad signature"));
        assertThrows(IllegalArgumentException.class,()->service.notification("wechat","forged",Map.of(),Map.of()));
        assertEquals("PENDING",mapper.find(order.id()).status()); assertNull(entitlements.find(1L));
    }
    @Test void failedGrantRollsBackPaidStatusAndCanBeRetried() {
        var order=create(); var failing=spy(mapper);
        doThrow(new IllegalStateException("database failed")).when(failing).grant(anyLong(),anyString(),any());
        var failedService=new PaymentService(failing,transactions,properties,access,config,List.of(gateway));
        assertThrows(IllegalStateException.class,()->failedService.settle(paid(order)));
        assertEquals("PENDING",mapper.find(order.id()).status()); assertNull(entitlements.find(1L));
        service.settle(paid(order)); assertTrue(entitlements.find(1L).active());
    }
    @Test void anotherUsersOrderCannotBeReadOrRefreshedAndAdminsCannotBeCharged() {
        var order=create();
        assertThrows(ForbiddenException.class,()->service.refresh(2L,order.id())); verify(gateway,never()).query(any());
        assertTrue(service.list(2L).isEmpty());
        when(access.status(2L)).thenReturn(new AiAccessService.Access(true,true,null));
        assertThrows(BusinessException.class,()->service.create(2L,"plus","wechat",UUID.randomUUID().toString()));
    }
    @Test void activeQueryRecoversLostCallbackAndRepeatedQueryDoesNotRenewAgain() {
        var order=create(); when(gateway.query(any())).thenReturn(paid(order));
        assertEquals("PAID",service.refresh(1L,order.id()).status());
        var expiry=mapper.membership(1L).expiresAt();
        service.refresh(1L,order.id()); assertEquals(expiry,mapper.membership(1L).expiresAt()); verify(gateway,times(1)).query(any());
    }
    @Test void aProviderTransactionCannotBeUsedToPayTwoOrders() {
        var first=create(); service.settle(paid(first)); var second=create();
        var replay=new PaymentGateway.PaymentResult("wechat",second.id(),paid(first).transactionId(),990,"CNY","PAID");
        var expiry=mapper.membership(1L).expiresAt();
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->service.settle(replay));
        assertEquals("PENDING",mapper.find(second.id()).status()); assertEquals(expiry,mapper.membership(1L).expiresAt());
    }
    @Test void unpaidExpiredOrderIsClosedBeforeCreatingAnotherOrder() {
        var first=create(); jdbc.update("UPDATE ai_payment_order SET expires_at=? WHERE id=?",utcNow().minusMinutes(1),first.id());
        when(gateway.query(any())).thenAnswer(i->{var order=i.getArgument(0,PaymentOrder.class);return new PaymentGateway.PaymentResult("wechat",order.id(),null,990,"CNY","PENDING");});
        var second=service.create(1L,"plus","wechat",UUID.randomUUID().toString());
        assertNotEquals(first.id(),second.id()); assertEquals("CLOSED",mapper.find(first.id()).status()); verify(gateway).close(any());
    }
    @Test void disablingNewPurchasesStillHonorsPreviouslyPaidOrders() {
        var order=create(); properties.setEnabled(false);
        assertFalse(service.catalog(1L).available());
        assertThrows(BusinessException.class,()->service.create(1L,"plus","wechat",UUID.randomUUID().toString()));
        when(gateway.notification(anyString(),anyMap(),anyMap())).thenReturn(paid(order));
        service.notification("wechat","signed-result",Map.of(),Map.of());
        assertTrue(entitlements.find(1L).active());
    }
    @Test void lostCheckoutResponseCanBeRetriedWithoutCreatingAnotherOrder() {
        when(gateway.create(any())).thenThrow(new IllegalStateException("lost response"));
        assertThrows(BusinessException.class,()->service.create(1L,"plus","wechat",UUID.randomUUID().toString()));
        var order=mapper.pending(1L);
        when(gateway.query(any())).thenReturn(new PaymentGateway.PaymentResult("wechat",order.id(),null,990,"CNY","NOT_FOUND"));
        doReturn("weixin://wxpay/bizpayurl?pr=recovered").when(gateway).create(any());
        var recovered=service.resumeCheckout(1L,order.id());
        assertEquals(order.id(),recovered.id()); assertNotNull(recovered.checkoutUrl());
        assertEquals(1,service.list(1L).size()); assertNull(entitlements.find(1L));
        assertThrows(ForbiddenException.class,()->service.resumeCheckout(2L,order.id()));
    }
    @Test void reconciliationRecoversPaymentAfterTransientProviderFailure() {
        var order=create();
        when(gateway.query(any())).thenThrow(new IllegalStateException("provider unavailable"));
        service.reconcile();
        assertNull(entitlements.find(1L));
        service.reconcile(); verify(gateway,times(1)).query(any());
        jdbc.update("UPDATE ai_payment_order SET next_check_at=? WHERE id=?",utcNow().minusMinutes(1),order.id());
        doReturn(paid(order)).when(gateway).query(any());
        service.reconcile(); assertTrue(entitlements.find(1L).active());
        service.reconcile(); verify(gateway,times(2)).query(any());
    }
}
