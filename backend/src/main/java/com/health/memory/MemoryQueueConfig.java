package com.health.memory;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MemoryQueueConfig {
    public static final String EXCHANGE="memory.exchange";
    public static final String QUEUE="memory.jobs.queue";
    public static final String KEY="memory.job";
    @Bean public DirectExchange memoryExchange() { return new DirectExchange(EXCHANGE,true,false); }
    @Bean public Queue memoryQueue() { return QueueBuilder.durable(QUEUE).deadLetterExchange(EXCHANGE).deadLetterRoutingKey("memory.dead").build(); }
    @Bean public Queue memoryDeadQueue() { return QueueBuilder.durable("memory.jobs.dead.queue").build(); }
    @Bean public Binding memoryBinding() { return BindingBuilder.bind(memoryQueue()).to(memoryExchange()).with(KEY); }
    @Bean public Binding memoryDeadBinding() { return BindingBuilder.bind(memoryDeadQueue()).to(memoryExchange()).with("memory.dead"); }
}
