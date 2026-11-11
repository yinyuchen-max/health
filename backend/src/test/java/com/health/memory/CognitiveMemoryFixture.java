package com.health.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.ai.*;
import com.health.service.impl.RedisChatMemoryStore;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CognitiveMemoryFixture {
    final ObjectMapper json=new ObjectMapper();
    final AiClient ai=mock(AiClient.class);
    final RedisChatMemoryStore recent=mock(RedisChatMemoryStore.class);
    final WorkingMemoryStore working=mock(WorkingMemoryStore.class);
    final JdbcTemplate jdbc;
    final UserMemoryRepository users;
    final CognitiveMemoryRepository repository;
    final TransactionTemplate transactions;
    final CognitiveMemorySettings settings=new CognitiveMemorySettings();
    final ForgettingPolicy forgetting=new ForgettingPolicy();
    final AssociativeRetriever retrieval;
    final SemanticMemoryService semantics;
    final MemoryOrchestrator orchestrator;
    final AdaptiveMemoryController adaptive;
    final MemoryConsolidator consolidator;
    final LongTermMemoryService service;
    final MemoryJobProcessor processor;
    CognitiveMemoryFixture() {
        JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:cognition-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");jdbc.update("INSERT INTO sys_user VALUES(1),(2)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V9__add_user_long_term_memory.sql"),new ClassPathResource("db/migration/V11__cognitive_memory.sql"),new ClassPathResource("db/migration/V12__chat_transcript_and_checkpoints.sql")).execute(ds);
        users=new UserMemoryRepository(jdbc);repository=new CognitiveMemoryRepository(jdbc);
        transactions=new TransactionTemplate(new DataSourceTransactionManager(ds));
        retrieval=new AssociativeRetriever(repository,forgetting,ai,json,settings);
        semantics=new SemanticMemoryService(repository,users,settings);
        consolidator=new MemoryConsolidator(repository,semantics,settings);
        adaptive=new AdaptiveMemoryController(repository);
        orchestrator=new MemoryOrchestrator(users,repository,retrieval,working,transactions);
        service=new LongTermMemoryService(users,repository,semantics,orchestrator,transactions,recent,working,adaptive,new ChatTranscriptRepository(jdbc));
        processor=new MemoryJobProcessor(repository,users,transactions,ai,json,new MemoryExtraction(json),new EpisodicMemoryService(repository,new AttentionEncoder(),working,settings),
                consolidator,new MemoryVectorService(repository,users,retrieval,ai,json,transactions,settings),service,settings);
        when(ai.prepare(anyLong(),anyString(),anyMap(),anyList(),any())).thenAnswer(inv->new AiRequest(inv.getArgument(0),"deepseek","private","model",inv.getArgument(1),null,inv.getArgument(2),inv.getArgument(3),inv.getArgument(4)));
    }
    MemoryExtraction.Candidate fact(String operation,String key,String content,boolean explicit) {
        String category=key.split("\\.")[0];
        return new MemoryExtraction.Candidate(operation,category,key,content,content,"ongoing",null,explicit,category.equals("family")?"family":"self","asserted");
    }
    void output(MemoryExtraction.Candidate... candidates) throws Exception {
        when(ai.complete(any())).thenReturn(json.writeValueAsString(new MemoryExtraction.Result(List.of(candidates))));
    }
    void drain() {
        for(int n=0;n<20;n++) {
            var jobs=repository.due(LocalDateTime.now());if(jobs.isEmpty()) return;
            jobs.forEach(j->processor.process(j.id()));
        }
        throw new IllegalStateException("Unbounded memory job loop");
    }
    void remember(String operation,String key,String content,boolean explicit) throws Exception {
        output(fact(operation,key,content,explicit));
        var turn=service.beginTurn(1L,content);service.completeTurn(turn,content,()->{});drain();
    }
    Long id(String key) { return repository.fact(1L,key).id(); }
}
