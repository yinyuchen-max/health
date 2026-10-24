package com.health.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Model checkpoint + contiguous recent tail; failed attempts never drop original input. */
@Component
public class ChatContextCompressor {
    public static final String CLARIFICATION="为准确理解您的情况，请分几次发送，并先告诉我本次最需要咨询的问题、目前的症状和正在使用的药物。";
    private final int configuredWindow,outputTokens,recentMessages;
    private final ModelFactory models;
    private final PromptService prompts;
    private final ObjectMapper json;
    public ChatContextCompressor(@Value("${ai.context.window-tokens:0}") int window,
            @Value("${ai.context.output-tokens:8192}") int output,
            @Value("${ai.context.recent-messages:8}") int recent,ModelFactory models,PromptService prompts,ObjectMapper json) {
        if(window<0 || window>2_000_000 || output<1 || output>32768 || recent<2 || recent>100
                || output+1024 >= (window>0?window:32768)*0.30) throw new IllegalArgumentException("AI 上下文预算配置无效");
        configuredWindow=window;outputTokens=output;recentMessages=recent;
        this.models=models;this.prompts=prompts;this.json=json;
    }
    public record Context(List<AiRequest.Message> history,boolean compressed,boolean requiresClarification,
            long beforeTokens,long afterTokens,int windowTokens,ConversationCheckpoint checkpoint,boolean compactionFailed,String summaryModel) {
        public Context(List<AiRequest.Message> history,boolean compressed,boolean clarify,long before,long after,int window,
                       ConversationCheckpoint checkpoint,boolean failed) {
            this(history,compressed,clarify,before,after,window,checkpoint,failed,null);
        }
        public Context(List<AiRequest.Message> history,boolean compressed,boolean clarify,long before,long after,int window) {
            this(history,compressed,clarify,before,after,window,ConversationCheckpoint.empty(),false);
        }
    }
    int window(AiRequest r) {
        if(configuredWindow>0) return configuredWindow;
        return "deepseek".equals(r.provider()) && Set.of("deepseek-flash","deepseek-v4-pro").contains(r.model())?1_000_000:32768;
    }
    static long textTokens(String value) { return value==null?0:value.getBytes(StandardCharsets.UTF_8).length; }
    static long messageTokens(AiRequest.Message m) {
        if(m==null || m.content()==null || m.content().isBlank() || !Set.of("user","assistant").contains(m.role()))
            throw new IllegalArgumentException("历史消息无效");
        return 96+textTokens(m.content())+textTokens(m.id())+textTokens(m.recordedAt());
    }
    public ConversationCheckpoint checkpoint(AiRequest r) {
        try {
            String value=r.variables().get("conversationCheckpoint");
            var cp=value==null?ConversationCheckpoint.empty():json.readValue(value,ConversationCheckpoint.class);
            if(!cp.isEmpty()) cp.validate(ConversationCheckpoint.sources(cp.evidence()),Set.of());
            return cp;
        } catch(Exception e) { throw new IllegalArgumentException("历史摘要损坏"); }
    }
    public String checkpointText(ConversationCheckpoint cp) {
        if(cp==null || cp.isEmpty()) return "";
        try { return "\n\n"+prompts.render("chat-checkpoint-context",Map.of("checkpoint",json.writeValueAsString(cp))); }
        catch(Exception e) { throw new IllegalArgumentException("无法渲染医疗摘要"); }
    }
    private long fixed(AiRequest r,String system) {
        if(r.message()==null || r.message().isBlank()) throw new IllegalArgumentException("聊天内容为空");
        return 96+textTokens(system)+textTokens(r.message())+outputTokens;
    }
    /** Dispatch guard only; never starts another summary call or mutates history. */
    public Context inspect(AiRequest r,String system) {
        var history=r.history()==null?List.<AiRequest.Message>of():List.copyOf(r.history());
        var cp=checkpoint(r);long total=fixed(r,system)+textTokens(checkpointText(cp));
        for(var m:history) total+=messageTokens(m);
        return new Context(history,false,total*100>=(long)window(r)*95,total,total,window(r),cp,false);
    }
    public Context compact(AiRequest r,String system) {
        var original=inspect(r,system);int window=original.windowTokens();
        if(original.beforeTokens()*100<(long)window*85) return original;
        if(original.requiresClarification() && fixed(r,system)*100>=(long)window*95) return original;
        long available=(long)window*30/100-fixed(r,system);
        if(available<512 || "false".equals(r.variables().get("allowCompaction"))) return failed(original);
        var history=original.history();int cut=history.size();long tailCost=0;
        while(cut>0 && history.size()-cut<recentMessages) {
            int start=cut-1;
            if("assistant".equals(history.get(start).role()) && start>0 && "user".equals(history.get(start-1).role())) start--;
            long unit=0;for(int i=start;i<cut;i++) unit+=messageTokens(history.get(i));
            if(tailCost+unit>available/2 || history.size()-start>recentMessages) break;
            tailCost+=unit;cut=start;
        }
        if(cut==0 && original.checkpoint().isEmpty()) return failed(original);
        var prefix=history.subList(0,cut);long summaryBudget=available-tailCost;
        try {
            Map<String,AiRequest.Message> allowed=new LinkedHashMap<>(ConversationCheckpoint.sources(original.checkpoint().evidence()));
            for(var entry:ConversationCheckpoint.sources(prefix).entrySet()) {
                var prior=allowed.putIfAbsent(entry.getKey(),entry.getValue());
                if(prior!=null && !prior.equals(entry.getValue())) throw new IllegalArgumentException("证据 ID 冲突");
            }
            Set<String> required=new HashSet<>();
            allowed.values().stream().filter(ConversationCheckpoint::critical).forEach(m->required.add(m.id()));
            String input=json.writeValueAsString(Map.of("previousCheckpoint",original.checkpoint(),"messages",prefix,
                    "maximumCheckpointBytes",summaryBudget,"requiredSourceIds",required,"currentQuestion",r.message()));
            int cap=(int)Math.min(8192,Math.max(128,summaryBudget/4));
            if(textTokens(input)+textTokens(prompts.render("chat-compaction-system",Map.of()))+cap>=(long)window*95/100)
                throw new IllegalArgumentException("摘要输入超过模型安全预算");
            ChatResponse response=models.summary(r,cap).chat(List.of(
                    SystemMessage.from(prompts.render("chat-compaction-system",Map.of())),UserMessage.from(input)));
            if(response==null || response.aiMessage()==null || response.metadata()!=null
                    && response.metadata().finishReason()!=null && response.metadata().finishReason()!=FinishReason.STOP)
                throw new IllegalArgumentException("摘要不完整");
            var draft=json.readValue(response.aiMessage().text(),ConversationCheckpoint.Draft.class);
            var cp=ConversationCheckpoint.ground(draft,allowed,required);
            long after=fixed(r,system)+textTokens(checkpointText(cp))+tailCost;
            if(after>(long)window*30/100 || after>=original.beforeTokens()) throw new IllegalArgumentException("摘要未达到预算目标");
            return new Context(List.copyOf(history.subList(cut,history.size())),true,false,
                    original.beforeTokens(),after,window,cp,false,models.summaryModelName(r));
        } catch(Exception e) {
            LoggerFactory.getLogger(ChatContextCompressor.class).warn("医疗上下文压缩未提交: type={}",e.getClass().getSimpleName());
            return failed(original);
        }
    }
    private Context failed(Context c) {
        return new Context(c.history(),false,c.requiresClarification(),c.beforeTokens(),c.beforeTokens(),c.windowTokens(),c.checkpoint(),true);
    }
}
