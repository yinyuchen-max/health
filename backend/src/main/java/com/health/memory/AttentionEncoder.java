package com.health.memory;

import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class AttentionEncoder {
    public record Encoding(double score,double importance,double novelty,double goalRelevance,boolean selected) {}
    public Encoding encode(MemoryExtraction.Candidate fact,List<String> known,String currentGoal,double threshold) {
        double novelty=1-known.stream().mapToDouble(s->MemoryTerms.similarity(fact.content(),s)).max().orElse(0);
        double relevance=currentGoal==null?0.3:MemoryTerms.similarity(fact.content(),currentGoal);
        double importance=fact.importance()!=null?fact.importance():switch(fact.category()) {
            case "health" -> fact.factKey().contains("allergy")?1:"temporary".equals(fact.temporalType())?0.65:0.85;
            case "goal" -> 0.85;
            case "diet","exercise","family" -> 0.7;
            default -> 0.5;
        };
        // Rule floors protect important self-reported health facts even if the model underscores them.
        if("health".equals(fact.category())) importance=Math.max(importance,"temporary".equals(fact.temporalType())?0.65:0.85);
        double emphasis=fact.explicitChange()?1:0.4;
        double score=0.30*novelty+0.35*importance+0.20*relevance+0.15*emphasis;
        return new Encoding(score,importance,novelty,relevance,
                (importance>=0.3&&score>=threshold)||fact.explicitChange()||"health".equals(fact.category())||"goal".equals(fact.category())||(novelty<0.15&&importance>=0.6));
    }
}
