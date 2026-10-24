package com.health.memory;

import com.health.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class AdaptiveMemoryController {
    private final CognitiveMemoryRepository repository;
    public AdaptiveMemoryController(CognitiveMemoryRepository repository) { this.repository=repository; }
    public void feedback(Long user,String retrievalId,String feedback) {
        if(!Set.of("USEFUL","IRRELEVANT","MISSING","FACT_ERROR").contains(feedback==null?"":feedback)) throw new BusinessException("无效的记忆反馈");
        var trace=repository.retrieval(user,retrievalId);
        if(trace==null) throw new BusinessException("记忆检索记录不存在");
        if(trace.feedback()!=null) throw new BusinessException("这次检索已经反馈过");
        var profile=repository.profile(user);
        double threshold=profile.encodingThreshold(),relevance=profile.relevanceWeight(),decay=profile.decayMultiplier();int budget=profile.contextBudget();
        // Collect at least three explicit feedback samples before changing policy; every update is bounded.
        if(profile.feedbackCount()>=2) switch(feedback) {
            case "IRRELEVANT" -> { relevance+=0.03;threshold+=0.01;budget-=100; }
            case "MISSING" -> { threshold-=0.03;decay+=0.05;budget+=100; }
            case "FACT_ERROR" -> threshold+=0.03;
            default -> { }
        }
        repository.adapt(user,clamp(threshold,0.25,0.75),clamp(relevance,0.4,0.8),clamp(decay,0.5,2),Math.max(600,Math.min(3000,budget)));
        repository.feedback(user,retrievalId,feedback);
        if("USEFUL".equals(feedback)) for(var item:trace.items()) if("SEMANTIC".equals(item.type()))
            repository.useful(user,Long.valueOf(item.id()),item.version(),LocalDateTime.now());
        // Error feedback alone cannot identify which proposition is wrong; edits explicitly correct facts.
    }
    private double clamp(double v,double min,double max) { return Math.max(min,Math.min(max,v)); }
}
