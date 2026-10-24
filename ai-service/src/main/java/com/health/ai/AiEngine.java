package com.health.ai;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class AiEngine {
    private final PromptService prompts;
    private final KnowledgeService knowledge;
    private final ModelFactory models;
    private final ChatContextCompressor compressor;
    public AiEngine(PromptService prompts,KnowledgeService knowledge,ModelFactory models,ChatContextCompressor compressor) {
        this.prompts=prompts; this.knowledge=knowledge; this.models=models; this.compressor=compressor;
    }
    List<ChatMessage> messages(AiRequest r) {
        return messages(r,"chat".equals(r.task()) ? compressor.inspect(r,chatSystem(r)) : null);
    }
    private String chatSystem(AiRequest r) {
        Map<String,String> vars=new HashMap<>(r.variables());
        vars.putIfAbsent("longTermMemories", "暂无长期记忆");
        return prompts.render("chat-system",Map.of())+"\n\n"+prompts.render("chat-user-context",vars)
                +"\n\n"+prompts.render("memory-context",vars)
                +(vars.getOrDefault("archiveEvidence","").isBlank()?"":"\n\n"+prompts.render("chat-archive-context",vars));
    }
    public ChatContextCompressor.Context context(AiRequest r) {
        if (!"chat".equals(r.task())) throw new IllegalArgumentException("仅聊天支持上下文压缩");
        return compressor.compact(r,chatSystem(r));
    }
    private List<ChatMessage> messages(AiRequest r,ChatContextCompressor.Context context) {
        Map<String,String> vars=new HashMap<>(r.variables());
        List<ChatMessage> messages=new ArrayList<>();
        switch(r.task()) {
            case "chat" -> {
                messages.add(SystemMessage.from(chatSystem(r)+compressor.checkpointText(context.checkpoint())));
                for(var m:context.history()) {
                    String text=m.id()==null?m.content():"【历史消息 id="+m.id()+"；记录时间="
                            +(m.recordedAt()==null?"未知":m.recordedAt())+"】\n"+m.content();
                    if ("user".equals(m.role())) messages.add(UserMessage.from(text));
                    else if ("assistant".equals(m.role())) messages.add(AiMessage.from(text));
                    else throw new IllegalArgumentException("不支持的历史消息角色");
                }
                messages.add(UserMessage.from(r.message()));
            }
            case "report" -> {
                vars.put("retrievedKnowledge",knowledge.retrieve(r,String.join(" ",vars.values())));
                messages.add(SystemMessage.from(prompts.render("health-report-system",Map.of())));
                messages.add(UserMessage.from(prompts.render("health-report-user",vars)));
            }
            case "appointment" -> {
                messages.add(SystemMessage.from(prompts.render("appointment-extract-system",vars)));
                messages.add(UserMessage.from(r.message()));
            }
            case "memory-extract" -> {
                vars.putIfAbsent("currentDate",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString());
                messages.add(SystemMessage.from(prompts.render("memory-extract-system",vars)));
                messages.add(UserMessage.from(r.message()));
            }
            default -> throw new IllegalArgumentException("未知 AI 任务");
        }
        return messages;
    }
    public String complete(AiRequest r) {
        var context="chat".equals(r.task()) ? compressor.inspect(r,chatSystem(r)) : null;
        if(context!=null && context.requiresClarification()) return ChatContextCompressor.CLARIFICATION;
        return models.chat(r).chat(messages(r,context)).aiMessage().text();
    }
    public void stream(AiRequest r,StreamingChatResponseHandler handler) {
        var context=compressor.inspect(r,chatSystem(r));
        if(context.requiresClarification()) {
            handler.onPartialResponse(ChatContextCompressor.CLARIFICATION);
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from(ChatContextCompressor.CLARIFICATION)).build());
            return;
        }
        models.stream(r).chat(ChatRequest.builder().messages(messages(r,context)).build(),handler);
    }
}
