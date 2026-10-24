package com.health.memory;

import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class MemoryOutboxDispatcher {
    public record JobRef(String id) {}
    private final CognitiveMemoryRepository repository;
    private final RabbitTemplate rabbit;
    private final MemoryJobProcessor processor;
    private final CognitiveMemorySettings settings;
    public MemoryOutboxDispatcher(CognitiveMemoryRepository repository,RabbitTemplate rabbit,MemoryJobProcessor processor,CognitiveMemorySettings settings) {
        this.repository=repository;this.rabbit=rabbit;this.processor=processor;this.settings=settings;
    }
    @Scheduled(fixedDelayString="${health.memory.dispatch-delay-ms:3000}")
    public void publish() {
        LocalDateTime now=LocalDateTime.now();repository.expireJobs(now,settings.getMaxJobAttempts());
        for(var job:repository.due(now)) {
            if(!repository.reservePublish(job.id(),now)) continue;
            try {
                rabbit.convertAndSend(MemoryQueueConfig.EXCHANGE,MemoryQueueConfig.KEY,new JobRef(job.id()),message->{
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);return message;
                });
                // Sending is NOT completion: until a consumer commits DONE, the database will redispatch it.
            } catch(Exception ignored) { /* Broker failure is recovered from the durable outbox on the next due dispatch. */ }
        }
    }
    @RabbitListener(queues=MemoryQueueConfig.QUEUE,concurrency="1-2")
    public void consume(JobRef job) { if(job!=null&&job.id()!=null) processor.process(job.id()); }
}
