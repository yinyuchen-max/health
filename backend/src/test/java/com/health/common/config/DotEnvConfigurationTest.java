package com.health.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import java.time.Duration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DotEnvConfigurationTest {
    @TempDir Path directory;

    private StandardEnvironment load(Path workingDirectory) throws Exception {
        String template;
        try (var input = new ClassPathResource("application.example.yml").getInputStream()) {
            template = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        // Anchor the real relative import paths to a synthetic working directory.
        template = template.replace("file:../.env", workingDirectory.resolve("../.env").normalize().toUri().toString())
                .replace("file:./.env", workingDirectory.resolve(".env").toUri().toString())
                .replace("file:./health/.env", workingDirectory.resolve("health/.env").toUri().toString());
        Path config = workingDirectory.resolve("application.yml");
        Files.writeString(config, template);
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("test-location",
                Map.of("spring.config.location", config.toUri().toString())));
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }

    private Path prepareProject() throws Exception {
        Path root = Files.createDirectory(directory.resolve("health"));
        Files.createDirectory(root.resolve("backend"));
        Files.writeString(root.resolve(".env"), """
                MYSQL_ROOT_PASSWORD=test-db-password
                JWT_SECRET=test-jwt-secret
                AI_SERVICE_SECRET=test-service-secret
                AI_PLATFORM_API_KEY=test-platform-key
                REDIS_PASSWORD=test-redis-password
                RABBITMQ_PASS=test-rabbit-password
                MAIL_PASSWORD=test-mail-password
                WECHAT_APP_SECRET=test-wechat-secret
                """);
        return root;
    }

    @Test
    void loadsAllCredentialsFromWorkspaceProjectRootOrBackendWithoutSystemEnvironment() throws Exception {
        Path root = prepareProject();
        Map<String, String> expected = Map.of(
                "spring.datasource.password", "test-db-password",
                "jwt.secret", "test-jwt-secret",
                "ai.service-secret", "test-service-secret",
                "ai.platform.api-key", "test-platform-key",
                "spring.data.redis.password", "test-redis-password",
                "spring.rabbitmq.password", "test-rabbit-password",
                "spring.mail.password", "test-mail-password",
                "wechat.miniapp.app-secret", "test-wechat-secret");
        for (Path cwd : new Path[]{root.getParent(), root, root.resolve("backend")}) {
            StandardEnvironment environment = load(cwd);
            expected.forEach((key, value) -> assertEquals(value, environment.getProperty(key), key));
        }
    }

    @Test
    void currentDirectoryOverridesParentForAiServiceConfig() throws Exception {
        Path root = prepareProject();
        Files.writeString(root.resolve("backend/.env"), """
                AI_SERVICE_SECRET=local-service-secret
                AI_SERVICE_URL=http://127.0.0.1:8089
                """);
        StandardEnvironment environment = load(root.resolve("backend"));
        assertEquals("local-service-secret", environment.getProperty("ai.service-secret"));
        assertEquals("http://127.0.0.1:8089", environment.getProperty("ai.service-url"));
        assertEquals("test-redis-password", environment.getProperty("spring.data.redis.password"));
    }

    @Test
    void workspaceDotEnvOverridesNestedProjectDotEnv() throws Exception {
        Path root = prepareProject();
        Files.writeString(root.getParent().resolve(".env"), "JWT_SECRET=workspace-jwt-secret\n");
        StandardEnvironment environment = load(root.getParent());
        assertEquals("workspace-jwt-secret", environment.getProperty("jwt.secret"));
        assertEquals("test-db-password", environment.getProperty("spring.datasource.password"));
    }

    @Test
    void missingRequiredSecretFailsInsteadOfUsingHardcodedFallback() throws Exception {
        Path root = Files.createDirectory(directory.resolve("empty-project"));
        StandardEnvironment environment = load(root);
        assertThrows(IllegalArgumentException.class, () -> environment.getProperty("jwt.secret"));
        assertThrows(IllegalArgumentException.class, () -> environment.getProperty("ai.service-secret"));
        assertEquals("", environment.getProperty("spring.data.redis.password"));
        assertNull(environment.getProperty("langchain4j.openai.chat-api-key"));
    }

    @Test
    void runtimePoliciesAndJwtLifetimeResolveFromDotEnv() throws Exception {
        Path root = prepareProject();
        Files.writeString(root.resolve("backend/.env"), """
                JWT_EXPIRATION_MS=172800000
                CHAT_MEMORY_MAX_MESSAGES=4
                CHAT_MEMORY_TTL=2h
                LOGIN_LOCK_DURATION=30m
                """);
        StandardEnvironment environment = load(root.resolve("backend"));
        assertEquals(172800000L, environment.getProperty("jwt.expiration", Long.class));
        RuntimeSettings settings = Binder.get(environment).bind("health.runtime", RuntimeSettings.class).get();
        assertEquals(4, settings.getChatMemory().getMaxMessages());
        assertEquals(Duration.ofHours(2), settings.getChatMemory().getTtl());
        assertEquals(Duration.ofMinutes(30), settings.getLogin().getLockDuration());
    }

    @Test
    void trafficProtectionCanBeTunedThroughDotEnv() throws Exception {
        Path root = prepareProject();
        Files.writeString(root.resolve("backend/.env"), """
                TRAFFIC_PROTECTION_ENABLED=true
                TRAFFIC_REQUESTS_PER_SECOND=8
                TRAFFIC_BURST_CAPACITY=3
                TRAFFIC_MAX_CONCURRENT_REQUESTS=2
                TRAFFIC_REDIS_KEY=test:shared:api
                """);
        StandardEnvironment environment = load(root.resolve("backend"));
        TrafficProtectionProperties properties = Binder.get(environment)
                .bind("health.traffic-protection", TrafficProtectionProperties.class).get();
        assertTrue(properties.isEnabled());
        assertEquals(8, properties.getRequestsPerSecond());
        assertEquals(3, properties.getBurstCapacity());
        assertEquals(2, properties.getMaxConcurrentRequests());
        assertEquals("test:shared:api", properties.getRedisKey());
    }

}
