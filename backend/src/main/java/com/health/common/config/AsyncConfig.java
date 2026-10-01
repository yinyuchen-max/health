package com.health.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 *
 * 遵循阿里巴巴开发手册：
 * - 禁止使用 Executors 创建，通过 ThreadPoolExecutor 明确参数
 * - IO 密集型任务：核心线程数 = CPU 核数 * 2
 * - 线程池必须命名，便于排查问题
 * - 明确拒绝策略，防止任务丢失
 *
 * @see <a href="https://github.com/alibaba/p3c">阿里巴巴Java开发手册</a>
 */
@Configuration
// 启动事件监听方法位于实现类中，使用类代理以便 Spring 调用这些方法。
@EnableAsync(proxyTargetClass = true)
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    @Bean("asyncExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // IO 密集型：核心线程 = CPU * 2
        executor.setCorePoolSize(CPU_COUNT * 2);
        executor.setMaxPoolSize(CPU_COUNT * 4);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        // 线程命名格式：health-async-{序号}
        executor.setThreadNamePrefix("health-async-");
        // 拒绝策略：由调用线程执行，保证任务不丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭：等待任务完成
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("异步线程池初始化完成: corePoolSize={}, maxPoolSize={}, queueCapacity={}, cpuCount={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), 200, CPU_COUNT);
        return executor;
    }

    /**
     * 异步任务未捕获异常处理
     * 阿里手册：线程池中的任务异常需要被记录，避免静默失败
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) ->
                log.error("异步任务执行异常: method={}, params={}", method.getName(), params, ex);
    }
}
