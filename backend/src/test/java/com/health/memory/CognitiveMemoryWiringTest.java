package com.health.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.ai.AiClient;
import com.health.service.impl.RedisChatMemoryStore;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CognitiveMemoryWiringTest {
    @Configuration @ComponentScan(basePackageClasses=LongTermMemoryService.class) @EnableConfigurationProperties
    static class MemoryBeans {}
    @Test void productionComponentsWireWithoutCyclesOrStartupModelCalls() {
        var f=new CognitiveMemoryFixture();var ai=mock(AiClient.class);
        new ApplicationContextRunner().withUserConfiguration(MemoryBeans.class)
                .withBean(JdbcTemplate.class,()->f.jdbc).withBean(TransactionTemplate.class,()->f.transactions)
                .withBean(ObjectMapper.class,ObjectMapper::new).withBean(AiClient.class,()->ai)
                .withBean(RedisChatMemoryStore.class,()->mock(RedisChatMemoryStore.class))
                .withBean(RedisTemplate.class,()->mock(RedisTemplate.class)).withBean(RabbitTemplate.class,()->mock(RabbitTemplate.class))
                .run(context->{assertNull(context.getStartupFailure());assertNotNull(context.getBean(MemoryJobProcessor.class));assertNotNull(context.getBean(LongTermMemoryService.class));verifyNoInteractions(ai);});
    }
}
