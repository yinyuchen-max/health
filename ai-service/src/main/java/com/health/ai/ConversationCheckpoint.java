package com.health.ai;

import java.util.*;
import java.util.regex.Pattern;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** Mirrored wire contract: generated notes are navigation; original messages are evidence. */
public record ConversationCheckpoint(List<Item> items, List<AiRequest.Message> evidence) {
    public record Item(String category,String note,List<String> sourceIds) {
        public Item { sourceIds=sourceIds==null?null:List.copyOf(sourceIds); }
    }
    public record Draft(List<Item> items) {}
    private static final Set<String> CATEGORIES=Set.of("symptoms","medications","allergies","diagnoses",
            "history","tests","family","goals","corrections","uncertainties","pending","assistant_advice");
    private static final Pattern CRITICAL=Pattern.compile(
            "过敏|药|剂量|毫克|微克|确诊|诊断|手术|怀孕|妊娠|哺乳|更正|纠正|否认|不是|没有|不再|胸痛|呼吸困难|晕厥|出血|抽搐|自杀|自伤|血压|血糖|症状|发热|疼痛|allerg|medicat|dose|diagnos|pregnan|chest|breath|bleed|seizur|suicid|insulin|\\b(?:mg|mcg|not|never|deny)\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER=Pattern.compile("[+-]?\\d+(?:[.,]\\d+)*");
    public ConversationCheckpoint {
        items=items==null?List.of():List.copyOf(items);
        evidence=evidence==null?List.of():List.copyOf(evidence);
    }
    public static ConversationCheckpoint empty() { return new ConversationCheckpoint(List.of(),List.of()); }
    @JsonIgnore public boolean isEmpty() { return items.isEmpty(); }
    public static boolean critical(AiRequest.Message m) {
        return "user".equals(m.role()) && CRITICAL.matcher(m.content()).find();
    }
    public static Map<String,AiRequest.Message> sources(Collection<AiRequest.Message> messages) {
        Map<String,AiRequest.Message> result=new LinkedHashMap<>();
        for(var m:messages) {
            if(m==null || m.id()==null || m.id().isBlank() || m.content()==null || m.content().isBlank()
                    || !Set.of("user","assistant").contains(m.role()) || result.putIfAbsent(m.id(),m)!=null)
                throw new IllegalArgumentException("无效或重复的聊天证据 ID");
        }
        return result;
    }
    public static ConversationCheckpoint ground(Draft draft,Map<String,AiRequest.Message> allowed,Set<String> required) {
        if(draft==null || draft.items()==null || draft.items().isEmpty() || draft.items().size()>128)
            throw new IllegalArgumentException("摘要结构为空或过大");
        var evidence=new LinkedHashMap<String,AiRequest.Message>();
        for(var item:draft.items()) {
            if(item==null || !CATEGORIES.contains(item.category()) || item.note()==null || item.note().isBlank()
                    || item.note().length()>500 || item.sourceIds()==null || item.sourceIds().isEmpty()
                    || item.sourceIds().size()>256 || new HashSet<>(item.sourceIds()).size()!=item.sourceIds().size())
                throw new IllegalArgumentException("摘要条目无效");
            StringBuilder original=new StringBuilder();
            for(String id:item.sourceIds()) {
                var source=allowed.get(id);
                if(source==null) throw new IllegalArgumentException("摘要引用不存在的证据");
                if(!Set.of("assistant_advice","pending").contains(item.category()) && !"user".equals(source.role()))
                    throw new IllegalArgumentException("AI 回复不能作为用户医疗事实证据");
                evidence.put(id,source);original.append(source.content()).append('\n');
            }
            Set<String> originalNumbers=new HashSet<>();
            NUMBER.matcher(original).results().forEach(m->originalNumbers.add(m.group()));
            var numbers=NUMBER.matcher(item.note());
            while(numbers.find()) if(!originalNumbers.contains(numbers.group()))
                throw new IllegalArgumentException("摘要引入无来源数值");
        }
        if(!evidence.keySet().containsAll(required)) throw new IllegalArgumentException("摘要遗漏关键医疗原文");
        return new ConversationCheckpoint(draft.items(),List.copyOf(evidence.values()));
    }
    public void validate(Map<String,AiRequest.Message> allowed,Set<String> required) {
        var grounded=ground(new Draft(items),allowed,required);
        if(!grounded.evidence().equals(evidence)) throw new IllegalArgumentException("摘要原文与来源不一致");
    }
}
