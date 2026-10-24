package com.health.memory;

import com.health.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LongTermMemoryServiceTest {
    @Test void successfulChatPersistsDurableWorkBeforeAnyPaidExtraction() throws Exception {
        var f=new CognitiveMemoryFixture();f.output(f.fact("ADD","diet.preference","我喜欢清淡饮食",false));
        var turn=f.service.beginTurn(1L,"饮食");var saved=new AtomicBoolean();
        f.service.completeTurn(turn,"我喜欢清淡饮食",()->saved.set(true));
        assertTrue(saved.get());verifyNoInteractions(f.ai);assertEquals(1,f.repository.countJobs(1L,"PENDING"));
        f.drain();
        assertTrue(f.service.context(f.service.beginTurn(1L,"饮食")).contains("清淡"));
        assertTrue(f.service.view(2L).memories().isEmpty());
        assertEquals(1,f.repository.episodes(1L,100,true).size());
        assertEquals(1,f.service.evidence(1L,f.id("diet.preference")).size());
    }
    @Test void repeatedFactsAddEvidenceWithoutCreatingSemanticDuplicatesAndUpdatesHaveHistory() throws Exception {
        var f=new CognitiveMemoryFixture();
        f.remember("ADD","goal.target_weight","目标65公斤",false);
        f.remember("NOOP","goal.target_weight","目标65公斤",false);
        assertEquals(1,f.service.view(1L).memories().size());
        assertEquals(2,f.service.evidence(1L,f.id("goal.target_weight")).size());
        f.remember("UPDATE","goal.target_weight","目标改为60公斤",true);
        var versions=f.service.versions(1L,f.id("goal.target_weight"));
        assertEquals(2,versions.size());assertNotNull(versions.get(1).validTo());
        assertEquals("目标改为60公斤",f.repository.fact(1L,"goal.target_weight").content());
        assertFalse(f.service.context(f.service.beginTurn(1L,"体重目标")).contains("65公斤"));
        assertTrue(f.service.context(f.service.beginTurn(1L,"以前的体重目标")).contains("65公斤"));
    }
    @Test void healthDisputesAreExcludedFromBothSemanticAndEpisodicRecallUntilConfirmed() throws Exception {
        var f=new CognitiveMemoryFixture();f.remember("ADD","health.allergy.peanut","我对花生过敏",false);
        f.remember("UPDATE","health.allergy.peanut","我对花生不过敏",true);
        var fact=f.repository.fact(1L,"health.allergy.peanut");assertEquals("CONFLICT",fact.status());
        assertFalse(f.service.context(f.service.beginTurn(1L,"花生")).contains("花生"));
        f.service.edit(1L,fact.id(),"我对花生不过敏");
        assertTrue(f.service.context(f.service.beginTurn(1L,"花生")).contains("花生不过敏"));
        assertEquals(3,f.service.versions(1L,fact.id()).size());
    }
    @Test void olderDelayedExtractionCannotReplaceLaterGoal() throws Exception {
        var f=new CognitiveMemoryFixture();
        var earlier=f.service.beginTurn(1L,"目标65公斤");f.service.completeTurn(earlier,"目标65公斤",()->{});
        var earlyJob=f.repository.due(LocalDateTime.now()).get(0);
        var later=f.service.beginTurn(1L,"目标60公斤");f.service.completeTurn(later,"目标60公斤",()->{});
        var lateJob=f.repository.due(LocalDateTime.now()).stream().filter(j->j.turn()==later.turn()).findFirst().orElseThrow();
        f.output(f.fact("ADD","goal.target_weight","目标60公斤",false));f.processor.process(lateJob.id());
        f.repository.due(LocalDateTime.now()).stream().filter(j->j.type().equals("CONSOLIDATE")).forEach(j->f.processor.process(j.id()));
        f.output(f.fact("UPDATE","goal.target_weight","目标65公斤",true));f.processor.process(earlyJob.id());f.drain();
        assertEquals("目标60公斤",f.repository.fact(1L,"goal.target_weight").content());
    }
    @Test void deletingFactClearsEventsVersionsVectorsAndCancelsOldTasks() throws Exception {
        var f=new CognitiveMemoryFixture();f.remember("ADD","diet.preference","偏好清淡",false);Long id=f.id("diet.preference");
        var old=f.service.beginTurn(1L,"偏好清淡");f.service.completeTurn(old,"偏好清淡",()->{});
        f.repository.vector(1L,new CognitiveMemoryRepository.Vector("SEMANTIC",id.toString(),1,"hash","model",2,"[1,0]"));
        f.service.delete(1L,id);f.drain();
        assertTrue(f.repository.episodes(1L,100,true).isEmpty());assertTrue(f.repository.vectors(1L).isEmpty());
        assertTrue(f.repository.versions(1L,id).isEmpty());assertTrue(f.repository.retrievals(1L).isEmpty());
        var saved=new AtomicBoolean();f.service.completeTurn(old,"旧请求",()->saved.set(true));assertFalse(saved.get());
        verify(f.recent).clearStrict(1L);verify(f.working).clearStrict(1L);
    }
    @Test void disableThenReenableDoesNotRunOldQueuedWorkAndDisabledChatsKeepRecentContext() throws Exception {
        var f=new CognitiveMemoryFixture();var turn=f.service.beginTurn(1L,"清淡");f.service.completeTurn(turn,"清淡",()->{});
        f.service.setEnabled(1L,false);var disabled=f.service.beginTurn(1L,"饮食");var saved=new AtomicBoolean();
        f.service.completeTurn(disabled,"饮食",()->saved.set(true));assertTrue(saved.get());
        f.service.setEnabled(1L,true);f.drain();verifyNoInteractions(f.ai);
        assertFalse(f.service.context(disabled).contains("清淡"));
    }
    @Test void allManagementOperationsAreScopedToOwner() throws Exception {
        var f=new CognitiveMemoryFixture();f.remember("ADD","diet.preference","清淡",false);Long id=f.id("diet.preference");
        assertThrows(BusinessException.class,()->f.service.delete(2L,id));
        assertThrows(BusinessException.class,()->f.service.edit(2L,id,"伪造"));
        assertThrows(BusinessException.class,()->f.service.versions(2L,id));
        assertThrows(BusinessException.class,()->f.service.evidence(2L,id));
        assertThrows(BusinessException.class,()->f.service.pin(2L,id,true));
        var episode=f.repository.episodes(1L,10,true).get(0);
        assertThrows(BusinessException.class,()->f.service.deleteEpisode(2L,episode.id()));
        f.service.clear(2L);assertEquals(1,f.service.view(1L).memories().size());
    }
    @Test void redisFailureRollsBackForgetInsteadOfPretendingSuccess() throws Exception {
        var f=new CognitiveMemoryFixture();f.remember("ADD","diet.preference","清淡",false);
        doThrow(new IllegalStateException()).when(f.recent).clearStrict(1L);
        assertThrows(IllegalStateException.class,()->f.service.clear(1L));assertNotNull(f.repository.fact(1L,"diet.preference"));
    }
    @Test void temporaryEventsExpireAsCurrentFactsButCanBeRecalledAsHistory() throws Exception {
        var f=new CognitiveMemoryFixture();
        var candidate=new MemoryExtraction.Candidate("ADD","health","health.symptom.headache","今天头疼","今天头疼","temporary",1,false,"self","asserted");
        f.output(candidate);var turn=f.service.beginTurn(1L,"头疼");f.service.completeTurn(turn,"今天头疼",()->{});f.drain();
        f.jdbc.update("UPDATE user_memory SET expires_at=?",LocalDateTime.now().minusDays(1));
        f.jdbc.update("UPDATE memory_episode SET expires_at=?",LocalDateTime.now().minusDays(1));
        assertFalse(f.service.context(f.service.beginTurn(1L,"头疼")).contains("头疼"));
        assertTrue(f.service.context(f.service.beginTurn(1L,"以前头疼过吗")).contains("头疼"));
    }
    @Test void deletingSourceRetractsDependentVersionsAndReplaysRemainingEvidence() throws Exception {
        var f=new CognitiveMemoryFixture();f.remember("ADD","diet.preference","清淡",false);f.remember("NOOP","diet.preference","清淡",false);
        var episodes=f.repository.episodes(1L,10,true);Long old=f.id("diet.preference");
        f.service.deleteEpisode(1L,episodes.get(0).id());assertNull(f.repository.semantic(1L,old));
        f.drain();assertNotNull(f.repository.fact(1L,"diet.preference"));
        assertEquals(1,f.service.evidence(1L,f.id("diet.preference")).size());
    }
    @Test void explicitNaturalForgetDeletesSourceGraph() throws Exception {
        var f=new CognitiveMemoryFixture();f.remember("ADD","diet.preference","清淡",false);
        f.output(new MemoryExtraction.Candidate("DELETE","diet","diet.preference",null,"忘掉我的饮食偏好",null,null,true,"self","asserted"));
        var turn=f.service.beginTurn(1L,"忘掉我的饮食偏好");f.service.completeTurn(turn,"忘掉我的饮食偏好",()->{});f.drain();
        assertNull(f.repository.fact(1L,"diet.preference"));assertTrue(f.repository.episodes(1L,10,true).isEmpty());
    }
    @Test void databaseLockOrdersForgetAfterInFlightHistoryWrite() throws Exception {
        var f=new CognitiveMemoryFixture();var snapshot=f.service.beginTurn(1L,"清淡");
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var cleared=new AtomicBoolean();var workers=Executors.newFixedThreadPool(2);
        doAnswer(inv->{cleared.set(true);return null;}).when(f.recent).clearStrict(1L);
        try {
            var completion=workers.submit(()->f.service.completeTurn(snapshot,"清淡",()->{
                entered.countDown();try { assertTrue(release.await(5,TimeUnit.SECONDS)); } catch(InterruptedException e) { throw new IllegalStateException(e); }
                assertFalse(cleared.get());
            }));
            assertTrue(entered.await(5,TimeUnit.SECONDS));var deletion=workers.submit(()->f.service.clear(1L));release.countDown();
            completion.get(5,TimeUnit.SECONDS);deletion.get(5,TimeUnit.SECONDS);f.drain();verifyNoInteractions(f.ai);assertTrue(cleared.get());
        } finally { release.countDown();workers.shutdownNow(); }
    }
}
