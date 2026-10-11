package com.health.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** 可调运行策略；默认值保持原有行为，错误配置在启动时拒绝。 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "health.runtime")
public class RuntimeSettings {
    @Valid @NotNull private ChatMemory chatMemory = new ChatMemory();
    @Valid @NotNull private Async async = new Async();
    @Valid @NotNull private Login login = new Login();

    @Getter @Setter
    public static class ChatMemory {
        @Min(2) private int maxMessages = 20;
        @Min(1) private int contextRecordLimit = 5;
        @NotNull private Duration ttl = Duration.ofDays(7);
        @AssertTrue(message = "chat-memory.ttl 必须大于零且至少为 1 毫秒")
        public boolean isTtlValid() { return ttl != null && ttl.toMillis() > 0; }
    }

    @Getter @Setter
    public static class Async {
        /** 0 表示自动计算：核心 CPU * 2，最大 CPU * 4。 */
        @Min(0) private int corePoolSize = 0;
        @Min(0) private int maxPoolSize = 0;
        @Min(0) private int queueCapacity = 200;
        @NotNull private Duration keepAlive = Duration.ofSeconds(60);
        @NotNull private Duration shutdownWait = Duration.ofSeconds(60);
        public int effectiveCorePoolSize() {
            return corePoolSize == 0 ? Runtime.getRuntime().availableProcessors() * 2 : corePoolSize;
        }
        public int effectiveMaxPoolSize() {
            return maxPoolSize == 0 ? Math.max(effectiveCorePoolSize(), Runtime.getRuntime().availableProcessors() * 4) : maxPoolSize;
        }
        @AssertTrue(message = "async.max-pool-size 不得小于核心线程数；两个等待时间必须为正数整秒")
        public boolean isPoolValid() {
            return effectiveMaxPoolSize() >= effectiveCorePoolSize()
                    && validSeconds(keepAlive) && validSeconds(shutdownWait);
        }
        private boolean validSeconds(Duration value) {
            return value != null && value.getNano() == 0 && value.getSeconds() > 0
                    && value.getSeconds() <= Integer.MAX_VALUE;
        }
    }

    @Getter @Setter
    public static class Login {
        @Min(1) private int maxFailures = 5;
        @NotNull private Duration lockDuration = Duration.ofMinutes(15);
        @AssertTrue(message = "login.lock-duration 必须至少为 1 秒")
        public boolean isLockDurationValid() { return lockDuration != null && lockDuration.toSeconds() > 0; }
    }
}
