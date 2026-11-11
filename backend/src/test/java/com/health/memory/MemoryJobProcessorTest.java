package com.health.memory;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryJobProcessorTest {
    @Test void duplicateCompletionDoesNotDuplicateHistoryAndPreservesOriginalMessageDate() {
        var f=new CognitiveMemoryFixture();var actual=f.service.beginTurn(1L,"今天头疼");
        var originalTime=LocalDateTime.of(2026,10,17,23,59,50);
        var turn=new LongTermMemoryService.Snapshot(actual.userId(),actual.enabled(),actual.revision(),actual.turn(),actual.memories(),actual.recalledContext(),actual.retrievalId(),originalTime);
        var writes=new java.util.concurrent.atomic.AtomicInteger();
        f.service.completeTurn(turn,"今天头疼",writes::incrementAndGet);f.service.completeTurn(turn,"今天头疼",writes::incrementAndGet);
        assertEquals(1,writes.get());assertEquals(originalTime,f.repository.due(LocalDateTime.now()).get(0).createdAt());
    }
    @Test void jobSurvivesRestartAndDuplicateDeliveryDoesNotCallModelAgain() throws Exception {
        var f=new CognitiveMemoryFixture();f.output(f.fact("ADD","diet.preference","清淡",false));
        var turn=f.service.beginTurn(1L,"清淡");f.service.completeTurn(turn,"清淡",()->{});
        var job=f.repository.due(LocalDateTime.now()).get(0);
        // A new repository/processor can recover work from the same database after a process loss.
        var restarted=new CognitiveMemoryRepository(f.jdbc);assertEquals("清淡",restarted.job(job.id()).input());
        f.processor.process(job.id());f.processor.process(job.id());f.drain();
        verify(f.ai,times(1)).complete(any());assertEquals("DONE",restarted.job(job.id()).state());assertEquals("",restarted.job(job.id()).input());
    }
    @Test void leasesFenceStaleWorkersAndExhaustedWorkIsRedacted() {
        var f=new CognitiveMemoryFixture();f.users.lock(1L);String id=f.repository.enqueue(1L,0,1,"ENCODE","private text");
        LocalDateTime now=LocalDateTime.now();String first=f.repository.claim(id,now,240,3);
        assertNull(f.repository.claim(id,now,240,3));assertTrue(f.repository.owns(id,first));
        String second=f.repository.claim(id,now.plusSeconds(241),240,3);
        assertNotNull(second);assertFalse(f.repository.owns(id,first));
        f.repository.done(id,first,"DONE");assertEquals("PROCESSING",f.repository.job(id).state());
        f.repository.claim(id,now.plusSeconds(482),240,3);
        f.repository.expireJobs(now.plusSeconds(723),3);
        assertEquals("FAILED",f.repository.job(id).state());assertEquals("",f.repository.job(id).input());
    }
    @Test void invalidModelOutputIsRetriedWithBackoffThenFailsWithoutPollutingMemory() {
        var f=new CognitiveMemoryFixture();when(f.ai.complete(any())).thenReturn("not json");
        var turn=f.service.beginTurn(1L,"清淡");f.service.completeTurn(turn,"清淡",()->{});var job=f.repository.due(LocalDateTime.now()).get(0);
        f.processor.process(job.id());assertEquals("PENDING",f.repository.job(job.id()).state());assertTrue(f.repository.due(LocalDateTime.now()).isEmpty());
        for(int i=0;i<2;i++) {
            f.jdbc.update("UPDATE memory_outbox SET available_at=?,next_publish_at=? WHERE id=?",LocalDateTime.now().minusMinutes(1),LocalDateTime.now().minusMinutes(1),job.id());
            f.processor.process(job.id());
        }
        assertEquals("FAILED",f.repository.job(job.id()).state());assertTrue(f.repository.episodes(1L,10,true).isEmpty());assertEquals("",f.repository.job(job.id()).input());
    }
    @Test void brokerSendIsNotCompletionAndLostDeliveryBecomesDueAgain() {
        var f=new CognitiveMemoryFixture();f.users.lock(1L);String id=f.repository.enqueue(1L,0,1,"ENCODE","清淡");
        var rabbit=mock(RabbitTemplate.class);var dispatcher=new MemoryOutboxDispatcher(f.repository,rabbit,f.processor,f.settings);
        dispatcher.publish();assertEquals("PENDING",f.repository.job(id).state());assertTrue(f.repository.due(LocalDateTime.now()).isEmpty());
        assertEquals(1,f.repository.due(LocalDateTime.now().plusSeconds(31)).size());
        verify(rabbit).convertAndSend(eq(MemoryQueueConfig.EXCHANGE),eq(MemoryQueueConfig.KEY),any(MemoryOutboxDispatcher.JobRef.class),any(org.springframework.amqp.core.MessagePostProcessor.class));
    }
    @Test void responseArrivingAfterDeletionCannotCommitEpisodes() throws Exception {
        var f=new CognitiveMemoryFixture();var turn=f.service.beginTurn(1L,"清淡");f.service.completeTurn(turn,"清淡",()->{});var job=f.repository.due(LocalDateTime.now()).get(0);
        when(f.ai.complete(any())).thenAnswer(inv->{f.service.clear(1L);return f.json.writeValueAsString(new MemoryExtraction.Result(List.of(f.fact("ADD","diet.preference","清淡",false))));});
        f.processor.process(job.id());assertTrue(f.repository.episodes(1L,10,true).isEmpty());assertEquals("CANCELLED",f.repository.job(job.id()).state());
    }
}
