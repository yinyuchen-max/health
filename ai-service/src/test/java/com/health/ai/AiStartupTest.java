package com.health.ai;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiStartupTest {
    @Test void independentApplicationStartsWithoutDatabaseOrAnyProviderKeyOrListeningPort() {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);
        new ApplicationContextRunner().withUserConfiguration(AiApplication.class)
                .withBean(StringRedisTemplate.class,()->redis)
                .withPropertyValues("ai.service-secret="+Base64.getEncoder().encodeToString(new byte[32]))
                .run(context->{
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(AiEngine.class)); assertNotNull(context.getBean(AiController.class));
                    assertEquals(0,context.getBeanNamesForType(javax.sql.DataSource.class).length);
                    assertEquals(0,context.getBeanNamesForType(dev.langchain4j.model.chat.ChatModel.class).length);
                    assertEquals(0,context.getBeanNamesForType(dev.langchain4j.model.embedding.EmbeddingModel.class).length);
                    verify(redis,never()).opsForValue();
                    verify(redis,never()).getConnectionFactory();
                });
    }
}
