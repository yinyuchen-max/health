package com.health.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.ai.*;
import com.health.service.impl.RedisChatMemoryStore;
import dev.langchain4j.data.message.*;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/** SQL owns history and checkpoints. No database lock is held during a paid model request. */
@Service
public class ChatContextService {
    private final ChatTranscriptRepository transcript;
    private final UserMemoryRepository users;
    private final TransactionTemplate transactions;
    private final RedisChatMemoryStore legacy;
    private final AiClient ai;
    private final ObjectMapper json;
    public ChatContextService(ChatTranscriptRepository transcript,UserMemoryRepository users,TransactionTemplate transactions,
            RedisChatMemoryStore legacy,AiClient ai,ObjectMapper json) {
        this.transcript=transcript;this.users=users;this.transactions=transactions;this.legacy=legacy;this.ai=ai;this.json=json;
    }
    public record Turn(AiRequest request,String turnKey,long revision,boolean oversizedInput) {
        public Turn(AiRequest request,String turnKey,long revision) { this(request,turnKey,revision,false); }
    }
    private record Loaded(ChatTranscriptRepository.State state,List<ChatTranscriptRepository.Message> history,
                          String turnKey,String leaseToken,boolean leased,List<AiRequest.Message> recalled) {}

    public Turn prepare(Long user,String message,LongTermMemoryService.Snapshot snapshot,Map<String,String> variables) {
        Turn turn=prepareInternal(user,message,snapshot,variables);
        // Also covers failed summaries: forgetting during the auxiliary call must stop later dispatch.
        transactions.executeWithoutResult(tx->{
            if(users.lock(user).revision()!=turn.revision()) throw new AiServiceException("对话信息已更新，请重新发送");
        });
        return turn;
    }
    private Turn prepareInternal(Long user,String message,LongTermMemoryService.Snapshot snapshot,Map<String,String> variables) {
        if(snapshot==null || !Objects.equals(user,snapshot.userId())) throw new AiServiceException("AI 服务暂时不可用，请稍后重试");
        var loaded=transactions.execute(tx->{
            var current=users.lock(user);
            if(current.revision()!=snapshot.revision()) throw new AiServiceException("对话信息已更新，请重新发送");
            var state=transcript.state(user);
            if(state==null) {
                transcript.create(user,current.revision());
                // One-time import. Legacy dates were never stored, so they remain unknown.
                for(var m:legacy.getMessages(user)) {
                    String role,text;
                    if(m instanceof UserMessage u) { role="user";text=u.singleText(); }
                    else if(m instanceof AiMessage a) { role="assistant";text=a.text(); }
                    else continue;
                    transcript.append(user,current.revision(),UUID.randomUUID().toString(),role,text,null);
                }
                legacy.clearStrict(user);
                state=transcript.state(user);
            } else if(state.revision()!=current.revision()) {
                transcript.rebase(user,current.revision());state=transcript.state(user);
            }
            String turn=UUID.randomUUID().toString(),lease=UUID.randomUUID().toString();
            var input=transcript.append(user,current.revision(),turn,"user",message,snapshot.observedAt());
            var history=transcript.active(user,state.coveredThrough(),input.seq());
            List<AiRequest.Message> recalled=new ArrayList<>();int bytes=0;
            // Include matches from the soon-to-be-compacted prefix on this very request as well.
            for(var candidate:transcript.recall(user,input.seq()-1,message)) {
                int cost=candidate.value().content().getBytes(StandardCharsets.UTF_8).length+128;
                if(cost+bytes<=6000 && recalled.size()<8) { recalled.add(candidate.value());bytes+=cost; }
            }
            boolean acquired=transcript.acquire(user,current.revision(),lease,LocalDateTime.now());
            return new Loaded(state,history,turn,lease,acquired,List.copyOf(recalled));
        });
        try {
            var vars=new HashMap<>(variables);
            var previous=loaded.state().checkpoint()==null?ConversationCheckpoint.empty():readCheckpoint(loaded.state().checkpoint());
            vars.put("conversationCheckpoint",write(previous));
            vars.put("archiveEvidence",loaded.recalled().isEmpty()?"":write(loaded.recalled()));
            vars.put("allowCompaction",Boolean.toString(loaded.leased()));
            List<AiRequest.Message> history=loaded.history().stream().map(ChatTranscriptRepository.Message::value).toList();
            AiRequest request=ai.prepare(user,"chat",vars,history,message);
            AiClient.ChatContext context;
            try { context=ai.chatContext(request);AiClient.validateContext(request,context,json); }
            catch(AiClient.ContextProtocolException incompatible) { throw incompatible; }
            catch(Exception e) {
                if(Thread.currentThread().isInterrupted()) throw new AiServiceException("AI 请求已取消");
                // Keep the original checkpoint and raw messages. Dispatch still enforces a hard safety budget.
                LoggerFactory.getLogger(ChatContextService.class).warn("聊天摘要预检未提交: type={}",e.getClass().getSimpleName());
                return new Turn(request,loaded.turnKey(),snapshot.revision());
            }
            if(!context.compressed()) return new Turn(request,loaded.turnKey(),snapshot.revision(),context.requiresClarification()
                    && message.getBytes(StandardCharsets.UTF_8).length>=(long)context.windowTokens()*30/100);
            int cut=history.size()-context.history().size();
            long covered=cut==0?loaded.state().coveredThrough():loaded.history().get(cut-1).seq();
            String checkpoint=write(context.checkpoint());
            boolean committed;
            try {
                committed=Boolean.TRUE.equals(transactions.execute(tx->{
                    var current=users.lock(user);
                    if(current.revision()!=snapshot.revision()) throw new AiServiceException("对话信息已更新，请重新发送");
                    return transcript.checkpoint(loaded.state(),covered,checkpoint,context.beforeTokens(),context.afterTokens(),
                            context.summaryModel()==null?request.model():context.summaryModel(),loaded.leaseToken());
                }));
            } catch(AiServiceException changed) { throw changed; }
            catch(Exception failed) {
                LoggerFactory.getLogger(ChatContextService.class).warn("聊天检查点提交回滚: type={}",failed.getClass().getSimpleName());
                committed=false;
            }
            if(!committed) return new Turn(request,loaded.turnKey(),snapshot.revision());
            vars.put("conversationCheckpoint",checkpoint);
            var prepared=new AiRequest(request.userId(),request.provider(),request.apiKey(),request.model(),request.task(),
                    request.embeddingApiKey(),Map.copyOf(vars),context.history(),request.message());
            return new Turn(prepared,loaded.turnKey(),snapshot.revision());
        } finally {
            if(loaded.leased()) try { transcript.release(user,loaded.leaseToken()); }
            catch(Exception failed) { LoggerFactory.getLogger(ChatContextService.class).warn("聊天压缩租约等待到期: type={}",failed.getClass().getSimpleName()); }
        }
    }
    public void complete(Turn turn,String reply) {
        transactions.executeWithoutResult(tx->{
            var current=users.lock(turn.request().userId());
            if(current.revision()!=turn.revision()) return;
            transcript.append(turn.request().userId(),turn.revision(),turn.turnKey(),"assistant",reply,LocalDateTime.now());
            if(turn.oversizedInput()) transcript.excludeOversizedTurn(turn.request().userId(),turn.turnKey());
        });
    }
    private ConversationCheckpoint readCheckpoint(String value) {
        try {
            var cp=json.readValue(value,ConversationCheckpoint.class);
            cp.validate(ConversationCheckpoint.sources(cp.evidence()),Set.of());return cp;
        } catch(Exception e) { throw new IllegalStateException("聊天摘要损坏",e); }
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch(Exception e) { throw new IllegalStateException("无法序列化聊天上下文",e); }
    }
}
