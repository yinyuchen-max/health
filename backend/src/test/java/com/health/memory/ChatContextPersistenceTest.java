package com.health.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.ai.*;
import com.health.service.impl.RedisChatMemoryStore;
import dev.langchain4j.data.message.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChatContextPersistenceTest {
    final ObjectMapper json=new ObjectMapper();final AiClient ai=mock(AiClient.class);
    final RedisChatMemoryStore redis=mock(RedisChatMemoryStore.class);
    JdbcTemplate jdbc;UserMemoryRepository users;ChatTranscriptRepository transcript;TransactionTemplate tx;ChatContextService service;
    @BeforeEach void setup() {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:chat-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");jdbc.update("INSERT INTO sys_user VALUES(1),(2)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V9__add_user_long_term_memory.sql"),
                new ClassPathResource("db/migration/V12__chat_transcript_and_checkpoints.sql")).execute(ds);
        users=new UserMemoryRepository(jdbc);transcript=new ChatTranscriptRepository(jdbc);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        service=new ChatContextService(transcript,users,tx,redis,ai,json);
        when(ai.prepare(anyLong(),eq("chat"),anyMap(),anyList(),anyString())).thenAnswer(inv->new AiRequest(
                inv.getArgument(0),"deepseek","private","deepseek-flash","chat",null,inv.getArgument(2),inv.getArgument(3),inv.getArgument(4)));
        when(ai.chatContext(notNull())).thenAnswer(inv->unchanged(inv.getArgument(0)));
    }
    AiClient.ChatContext unchanged(AiRequest r) throws Exception {
        var cp=json.readValue(r.variables().get("conversationCheckpoint"),ConversationCheckpoint.class);
        return new AiClient.ChatContext(r.history(),false,false,100,100,10000,cp,false);
    }
    LongTermMemoryService.Snapshot snapshot(Long user) {
        return tx.execute(t->{var state=users.lock(user);users.advanceTurn(user);return new LongTermMemoryService.Snapshot(user,state.enabled(),state.revision(),state.nextTurn()+1,List.of());});
    }
    ChatContextService.Turn prepare(Long user,String text) {
        return service.prepare(user,text,snapshot(user),Map.of("healthRecords","暂无","sportRecords","暂无"));
    }
    void say(Long user,String text,String answer) { service.complete(prepare(user,text),answer); }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class); }
    AiClient.ChatContext compact(AiRequest r) throws Exception {
        int cut=Math.max(1,r.history().size()-2);
        var old=json.readValue(r.variables().get("conversationCheckpoint"),ConversationCheckpoint.class);
        var allowed=new LinkedHashMap<>(ConversationCheckpoint.sources(old.evidence()));
        allowed.putAll(ConversationCheckpoint.sources(r.history().subList(0,cut)));
        var ids=allowed.values().stream().filter(m->"user".equals(m.role())).map(AiRequest.Message::id).toList();
        var checkpoint=ConversationCheckpoint.ground(new ConversationCheckpoint.Draft(List.of(
                new ConversationCheckpoint.Item("history","历史自述，核对原文",ids))),allowed,new HashSet<>(ids));
        return new AiClient.ChatContext(r.history().subList(cut,r.history().size()),true,false,9000,2500,10000,checkpoint,false);
    }
    @Test void importsLegacyOnceArchivesEveryInputAndRestoresOriginalMessagesAfterRestart() {
        when(redis.getMessages(1L)).thenReturn(List.of(UserMessage.from("我说：\"不是我\"\n父亲用药0.25 mg"),AiMessage.from("请核实")));
        var first=prepare(1L,"现在呢？");assertEquals(2,first.request().history().size());
        assertNull(first.request().history().get(0).recordedAt());service.complete(first,"请补充症状");
        service=new ChatContextService(new ChatTranscriptRepository(jdbc),users,tx,redis,ai,json);
        var next=prepare(1L,"继续");assertEquals(4,next.request().history().size());
        assertEquals(first.request().history().get(0),next.request().history().get(0));
        assertEquals(5,count("chat_message_archive"));verify(redis,times(1)).getMessages(1L);verify(redis,times(1)).clearStrict(1L);
        service.complete(first,"请补充症状");assertEquals(5,count("chat_message_archive"));
    }
    @Test void checkpointHidesPrefixWithoutDeletingItAndArchivedMedicationCanBeRecalled() throws Exception {
        say(1L,"我对青霉素过敏，母亲用药0.25 mg","旧解释");say(1L,"普通提问","近期回答");
        doAnswer(inv->compact(inv.getArgument(0))).when(ai).chatContext(any());
        var compacted=prepare(1L,"请继续");assertEquals(2,compacted.request().history().size());
        assertEquals(1,transcript.state(1L).version());assertEquals(1,count("chat_compaction_checkpoint"));
        assertEquals(5,count("chat_message_archive"));
        doAnswer(inv->unchanged(inv.getArgument(0))).when(ai).chatContext(any());
        service=new ChatContextService(transcript,users,tx,redis,ai,json);
        var next=prepare(1L,"以前什么药过敏？");
        assertTrue(next.request().variables().get("archiveEvidence").contains("母亲用药0.25 mg"));
        assertTrue(next.request().variables().get("conversationCheckpoint").contains("我对青霉素过敏，母亲用药0.25 mg"));
        assertEquals(2,next.request().history().size());
        assertTrue(next.request().history().stream().noneMatch(m->m.content().equals("请继续")));
        assertTrue(prepare(2L,"以前什么药过敏？").request().variables().get("archiveEvidence").isBlank());
    }
    @Test void invalidOrFailedSummaryPreservesStateAndUserOriginalEvenIfProviderFails() throws Exception {
        say(1L,"我没有糖尿病，父亲有","旧回答");
        doAnswer(inv->{throw new AiServiceException("摘要超时");}).when(ai).chatContext(any());
        var failed=prepare(1L,"现在怎么办");assertEquals(2,failed.request().history().size());
        assertEquals(0,transcript.state(1L).version());assertEquals(0,count("chat_compaction_checkpoint"));assertEquals(3,count("chat_message_archive"));
        doAnswer(inv->{var valid=compact(inv.getArgument(0));return new AiClient.ChatContext(valid.history(),true,false,9000,3500,10000,valid.checkpoint(),false);}).when(ai).chatContext(any());
        var invalid=prepare(1L,"继续");assertEquals(2,invalid.request().history().size());assertEquals(0,transcript.state(1L).version());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM chat_message_archive WHERE include_in_context=TRUE AND content='现在怎么办'",Integer.class));
    }
    @Test void deletionWhileSummarizingInvalidatesLeaseAndCannotRestoreArchiveOrCheckpoint() throws Exception {
        say(1L,"我对青霉素过敏","旧回答");
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var executor=Executors.newSingleThreadExecutor();
        doAnswer(inv->{var result=compact(inv.getArgument(0));entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return result;}).when(ai).chatContext(any());
        try {
            var work=executor.submit(()->prepare(1L,"继续"));assertTrue(entered.await(5,TimeUnit.SECONDS));
            tx.executeWithoutResult(t->{users.lock(1L);transcript.clear(1L);users.invalidate(1L);});release.countDown();
            assertThrows(ExecutionException.class,()->work.get(5,TimeUnit.SECONDS));
            assertEquals(0,count("chat_message_archive"));assertEquals(0,count("chat_compaction_checkpoint"));assertNull(transcript.state(1L));
        } finally { release.countDown();executor.shutdownNow(); }
    }
    @Test void concurrentNewInputSurvivesCompactionAndOnlyLeaseOwnerMaySummarize() throws Exception {
        say(1L,"我对青霉素过敏","旧回答");say(1L,"普通提问","近期回答");
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var executor=Executors.newSingleThreadExecutor();
        doAnswer(inv->{AiRequest r=inv.getArgument(0);
            if("false".equals(r.variables().get("allowCompaction"))) return unchanged(r);
            var result=compact(r);entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return result;
        }).when(ai).chatContext(any());
        try {
            var first=executor.submit(()->prepare(1L,"第一条新输入"));assertTrue(entered.await(5,TimeUnit.SECONDS));
            var second=prepare(1L,"第二条新输入");assertEquals("false",second.request().variables().get("allowCompaction"));
            assertTrue(second.request().history().stream().noneMatch(m->m.content().equals("第一条新输入")));
            release.countDown();var completed=first.get(5,TimeUnit.SECONDS);service.complete(completed,"第一条回复");service.complete(second,"第二条回复");
            assertEquals(1,transcript.state(1L).version());assertEquals(8,count("chat_message_archive"));
            var active=transcript.active(1L,transcript.state(1L).coveredThrough(),Long.MAX_VALUE);
            assertTrue(active.stream().anyMatch(m->m.value().content().equals("第二条新输入")));
        } finally { release.countDown();executor.shutdownNow(); }
    }
    @Test void oversizedOriginalIsArchivedButDoesNotBlockEveryFutureTurn() throws Exception {
        doAnswer(inv->{AiRequest r=inv.getArgument(0);return new AiClient.ChatContext(r.history(),false,true,50000,50000,10000,ConversationCheckpoint.empty(),true);}).when(ai).chatContext(any());
        String original="药".repeat(20000);var large=prepare(1L,original);assertTrue(large.oversizedInput());service.complete(large,"请分几次发送");
        doAnswer(inv->unchanged(inv.getArgument(0))).when(ai).chatContext(any());
        assertTrue(prepare(1L,"这次只问饮食").request().history().isEmpty());
        assertEquals(original,jdbc.queryForObject("SELECT content FROM chat_message_archive WHERE role='user' ORDER BY seq LIMIT 1",String.class));
    }
    @Test void forgettingDuringFailedSummaryAlsoStopsLaterDispatch() throws Exception {
        say(1L,"我没有糖尿病","旧回答");
        doAnswer(inv->{tx.executeWithoutResult(t->{users.lock(1L);transcript.clear(1L);users.invalidate(1L);});throw new AiServiceException("摘要超时");})
                .when(ai).chatContext(any());
        assertThrows(AiServiceException.class,()->prepare(1L,"继续"));assertEquals(0,count("chat_message_archive"));
    }
    @Test void incompatibleAiVersionDoesNotSilentlyRunTheOldDestructiveCompressor() {
        doThrow(new AiClient.ContextProtocolException()).when(ai).chatContext(any());
        assertThrows(AiClient.ContextProtocolException.class,()->prepare(1L,"请继续"));
        assertEquals(1,count("chat_message_archive"));assertEquals(0,count("chat_compaction_checkpoint"));
        verify(ai,never()).complete(any());
    }
    @Test void failedCheckpointTransactionRollsBackAndExpiredLeaseCannotOverwriteNewerOwner() throws Exception {
        say(1L,"我对青霉素过敏","旧回答");
        jdbc.update("INSERT INTO chat_compaction_checkpoint(user_id,revision,version,covered_through,checkpoint_json,before_tokens,after_tokens,model) VALUES(1,0,1,0,'{}',9000,2500,'test')");
        doAnswer(inv->compact(inv.getArgument(0))).when(ai).chatContext(any());
        var unchanged=prepare(1L,"继续");assertEquals(2,unchanged.request().history().size());assertEquals(0,transcript.state(1L).version());
        assertTrue(transcript.acquire(1L,0,"old",LocalDateTime.now().minusSeconds(60)));
        assertTrue(transcript.acquire(1L,0,"new",LocalDateTime.now()));transcript.release(1L,"old");
        assertEquals("new",jdbc.queryForObject("SELECT lease_token FROM chat_context_state WHERE user_id=1",String.class));
        assertFalse(Boolean.TRUE.equals(tx.execute(t->transcript.checkpoint(transcript.state(1L),1,"{}",9000,2000,"model","old"))));
    }
}
