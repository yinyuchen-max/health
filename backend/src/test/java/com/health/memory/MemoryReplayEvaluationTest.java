package com.health.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.ai.AiClient;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Reproducible synthetic component benchmark, not an evaluation of real model factual accuracy. */
class MemoryReplayEvaluationTest {
    record Fact(String key,String category,String content,double importance,int ageDays,List<String> entities) {}
    record Case(String id,String query,List<String> targets,List<Fact> facts) {}
    @Test void generatesBaselineAndAblationReportWithoutPaidModels() throws Exception {
        var json=new ObjectMapper();var ai=mock(AiClient.class);var settings=new CognitiveMemorySettings();
        var retriever=new AssociativeRetriever(mock(CognitiveMemoryRepository.class),new ForgettingPolicy(),ai,json,settings);
        List<Case> cases=json.readValue(new ClassPathResource("memory/replay-cases.json").getInputStream(),new TypeReference<>() {});
        LocalDateTime now=LocalDateTime.of(2026,10,18,12,0);
        Map<String,List<Map<String,Object>>> report=new LinkedHashMap<>();
        for(String mode:List.of("CURRENT_50_ALL","FIXED_VECTOR_ONLY","FULL","NO_ASSOCIATION","NO_DECAY","NO_IMPORTANCE","NO_CONSOLIDATION","NO_ATTENTION")) {
            List<Map<String,Object>> results=new ArrayList<>();
            for(var scenario:cases) {
                List<AssociativeRetriever.Node> nodes=new ArrayList<>();Map<String,Set<String>> entities=new HashMap<>();Map<String,Double> vectors=new HashMap<>();
                int i=0;
                for(var fact:scenario.facts()) {
                    var node=new AssociativeRetriever.Node(mode.equals("NO_CONSOLIDATION")?"EPISODE":"SEMANTIC",scenario.id()+"-"+i++,1,fact.key(),fact.category(),fact.content(),fact.importance(),false,0,now.minusDays(fact.ageDays()),null,false,mode.equals("NO_CONSOLIDATION"));
                    nodes.add(node);entities.put(node.key(),Set.copyOf(fact.entities()));
                    // Fixed synthetic cosine scores intentionally model a paraphrase miss in the association case.
                    vectors.put(node.key(),scenario.id().equals("association")&&fact.category().equals("exercise")?0.1:0.9);
                }
                int distractors=mode.equals("NO_ATTENTION")?120:60;
                for(int n=0;n<distractors;n++) nodes.add(new AssociativeRetriever.Node("EPISODE","noise-"+n,1,"lifestyle.noise_"+n,"lifestyle","无关的杯子颜色记录"+n,.1,false,0,now,null,false,true));
                if(!Set.of("CURRENT_50_ALL","FIXED_VECTOR_ONLY","NO_ATTENTION").contains(mode)) {
                    var encoder=new AttentionEncoder();
                    nodes=nodes.stream().filter(n->{
                        var candidate=new MemoryExtraction.Candidate("ADD",n.category(),n.factKey(),n.content(),n.content(),"ongoing",null,false,
                                n.category().equals("family")?"family":"self","asserted",n.importance(),List.of(),null,null,null);
                        return encoder.encode(candidate,List.of(),null,0.45).selected();
                    }).toList();
                }
                long start=System.nanoTime();List<AssociativeRetriever.Node> selected;int contextEstimate=0;
                if(mode.equals("CURRENT_50_ALL")) selected=nodes.subList(Math.max(0,nodes.size()-50),nodes.size());
                else if(mode.equals("FIXED_VECTOR_ONLY")) selected=nodes.stream().filter(n->vectors.getOrDefault(n.key(),0.0)>=0.45).sorted(Comparator.comparingDouble((AssociativeRetriever.Node n)->vectors.getOrDefault(n.key(),0.0)).reversed()).limit(8).toList();
                else {
                    var recall=retriever.rank(scenario.query(),nodes,entities,vectors,new CognitiveMemoryRepository.Profile(.45,.55,1,1800,0,null),now,
                            !mode.equals("NO_ASSOCIATION"),!mode.equals("NO_DECAY"),!mode.equals("NO_IMPORTANCE"));
                    selected=recall.hits().stream().map(AssociativeRetriever.Hit::node).toList();contextEstimate=recall.estimatedTokens();
                }
                Set<String> selectedKeys=new HashSet<>();selected.forEach(n->selectedKeys.add(n.factKey()));
                if(contextEstimate==0) contextEstimate=json.writeValueAsString(selected.stream().map(AssociativeRetriever.Node::content).toList()).length();
                long correct=scenario.targets().stream().filter(selectedKeys::contains).count();
                results.add(Map.of("case",scenario.id(),"precision",selected.isEmpty()?0:correct/(double)selected.size(),"recall",correct/(double)scenario.targets().size(),
                        "selectedCount",selected.size(),"storedNodes",nodes.size(),"contextEstimate",contextEstimate,"elapsedMicros",(System.nanoTime()-start)/1000));
            }
            report.put(mode,results);
        }
        double recall=report.get("FULL").stream().mapToDouble(r->((Number)r.get("recall")).doubleValue()).average().orElse(0);
        assertTrue(recall>=0.8,"Synthetic known-fact recall unexpectedly regressed");
        verifyNoInteractions(ai);
        var output=new LinkedHashMap<String,Object>();output.put("scope","Synthetic retrieval/ranking replay with fixed vector fixtures; does not measure model extraction, semantic entailment, answer quality or production latency.");
        output.put("seed",20261018);output.put("cases",cases.size());output.put("fullMeanRecall",recall);output.put("results",report);
        Files.createDirectories(Path.of("target"));json.writerWithDefaultPrettyPrinter().writeValue(Path.of("target/cognitive-memory-evaluation.json").toFile(),output);
    }
}
