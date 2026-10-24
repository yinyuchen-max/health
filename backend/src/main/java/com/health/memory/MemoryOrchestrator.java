package com.health.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MemoryOrchestrator {
    private static final Logger log=LoggerFactory.getLogger(MemoryOrchestrator.class);
    private final UserMemoryRepository users;
    private final CognitiveMemoryRepository cognitive;
    private final AssociativeRetriever retrieval;
    private final WorkingMemoryStore working;
    private final TransactionTemplate transactions;
    public MemoryOrchestrator(UserMemoryRepository users,CognitiveMemoryRepository cognitive,AssociativeRetriever retrieval,WorkingMemoryStore working,TransactionTemplate transactions) {
        this.users=users;this.cognitive=cognitive;this.retrieval=retrieval;this.working=working;this.transactions=transactions;
    }
    public LongTermMemoryService.Snapshot beforeReply(Long user,String query) {
        try {
            var snapshot=transactions.execute(tx->{
                var state=users.lock(user);users.advanceTurn(user);
                return new LongTermMemoryService.Snapshot(user,state.enabled(),state.revision(),state.nextTurn()+1,
                        state.enabled()?users.list(user,LocalDateTime.now()):List.of(),null,null);
            });
            if(!snapshot.enabled()) return snapshot;
            var recall=retrieval.retrieve(user,query,cognitive.profile(user),LocalDateTime.now());
            return transactions.execute(tx->{
                var current=users.lock(user);
                if(!current.enabled()||current.revision()!=snapshot.revision()) return null;
                String trace=cognitive.retrieval(user,snapshot.revision(),snapshot.turn(),recall.items());
                working.working(user,recall.context());
                return new LongTermMemoryService.Snapshot(user,true,snapshot.revision(),snapshot.turn(),snapshot.memories(),recall.context(),trace,snapshot.observedAt());
            });
        } catch(Exception e) { failed("读取",user,e);return null; }
    }
    public void afterReply(LongTermMemoryService.Snapshot snapshot,String message,Runnable saveHistory) {
        if(snapshot==null) return;
        try {
            transactions.executeWithoutResult(tx->{
                var state=users.lock(snapshot.userId());
                if(state.revision()!=snapshot.revision()) return;
                if(snapshot.retrievalId()!=null&&!cognitive.completeRetrieval(snapshot.userId(),snapshot.turn())) return;
                saveHistory.run();
                if(state.enabled()&&snapshot.enabled()&&message.length()<=8000) {
                    cognitive.enqueue(snapshot.userId(),snapshot.revision(),snapshot.turn(),"ENCODE",message,snapshot.observedAt());
                    working.buffer(snapshot.userId(),message);
                }
            });
        } catch(Exception e) { failed("记录",snapshot.userId(),e); }
    }
    private void failed(String operation,Long user,Exception e) {
        log.warn("认知记忆{}失败: userId={}, type={}",operation,user,e.getClass().getSimpleName());
    }
}
