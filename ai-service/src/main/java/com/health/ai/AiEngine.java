package com.health.ai;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class AiEngine {
    private final PromptService prompts;
    private final KnowledgeService knowledge;
    private final ModelFactory models;
    public AiEngine(PromptService prompts,KnowledgeService knowledge,ModelFactory models) {
        this.prompts=prompts; this.knowledge=knowledge; this.models=models;
    }
    List<ChatMessage> messages(AiRequest r) {
        Map<String,String> vars=new HashMap<>(r.variables());
        List<ChatMessage> messages=new ArrayList<>();
        switch(r.task()) {
            case "chat" -> {
                messages.add(SystemMessage.from(prompts.render("chat-system",Map.of())+"\n\n"
                        +prompts.render("chat-user-context",vars)));
                if (r.history()!=null) for(var m:r.history()) {
                    if ("user".equals(m.role())) messages.add(UserMessage.from(m.content()));
                    else if ("assistant".equals(m.role())) messages.add(AiMessage.from(m.content()));
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
            default -> throw new IllegalArgumentException("未知 AI 任务");
        }
        return messages;
    }
    public String complete(AiRequest r) { return models.chat(r).chat(messages(r)).aiMessage().text(); }
    public void stream(AiRequest r,StreamingChatResponseHandler handler) {
        models.stream(r).chat(ChatRequest.builder().messages(messages(r)).build(),handler);
    }
}
