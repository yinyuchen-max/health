package com.health.memory;

import com.health.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CognitiveAlgorithmsTest {
    @Test void healthAndExplicitGoalChangesRemainEligibleAtHighEncodingThreshold() {
        var f=new CognitiveMemoryFixture();var encoder=new AttentionEncoder();
        var health=new MemoryExtraction.Candidate("ADD","health","health.allergy.peanut","花生过敏","花生过敏","ongoing",null,false,"self","asserted",0.0,List.of("花生"),null,null,null);
        assertTrue(encoder.encode(health,List.of("花生过敏"),null,0.75).selected());
        assertTrue(encoder.encode(f.fact("UPDATE","goal.weight","60公斤",true),List.of("65公斤"),"减重",0.75).selected());
        assertFalse(encoder.encode(f.fact("NOOP","lifestyle.misc","一样的内容",false),List.of("一样的内容"),null,0.75).selected());
    }
    @Test void datesNeedEvidenceAndUnknownTimeRemainsUnknown() {
        LocalDateTime now=LocalDateTime.of(2026,10,18,12,0);
        assertEquals(now.toLocalDate().minusDays(1).atStartOfDay(),MemoryTime.resolve("2026-10-17","昨天跑步",now));
        assertNull(MemoryTime.resolve("2026-10-16","昨天跑步",now));
        assertNull(MemoryTime.resolve("2026-10-18",null,now));
        assertNull(MemoryTime.resolve("2026-10-18","以前跑步",now));
    }
    @Test void activationDecaysAndExplicitUsefulFeedbackReinforcesWithoutChangingEvidence() throws Exception {
        var f=new CognitiveMemoryFixture();var policy=new ForgettingPolicy();LocalDateTime now=LocalDateTime.now();
        double recent=policy.activation("diet",now.minusDays(1),0,1,now),old=policy.activation("diet",now.minusDays(180),0,1,now);
        assertTrue(recent>old);assertTrue(policy.activation("diet",now.minusDays(180),4,1,now)>old);
        assertFalse(policy.archive("health",false,1,0,false));assertFalse(policy.archive("diet",true,0.2,0,false));
        f.remember("ADD","diet.preference","清淡饮食",false);Long id=f.id("diet.preference");
        int sources=f.service.evidence(1L,id).size();
        var recall=f.service.beginTurn(1L,"饮食");f.service.completeTurn(recall,"饮食",()->{});
        f.service.feedback(1L,recall.retrievalId(),"USEFUL");
        assertEquals(1,f.repository.semantic(1L,id).usefulCount());assertEquals(sources,f.service.evidence(1L,id).size());
        assertThrows(BusinessException.class,()->f.service.feedback(1L,recall.retrievalId(),"USEFUL"));
        assertThrows(BusinessException.class,()->f.service.feedback(2L,recall.retrievalId(),"USEFUL"));
    }
    @Test void adaptivePolicyWaitsForFeedbackThenStaysBounded() {
        var f=new CognitiveMemoryFixture();
        for(int i=0;i<30;i++) {
            var turn=f.service.beginTurn(1L,"想起遗漏信息");f.service.completeTurn(turn,"想起遗漏信息",()->{});
            f.service.feedback(1L,turn.retrievalId(),"MISSING");
            var p=f.repository.profile(1L);
            if(i<2) assertEquals(0.45,p.encodingThreshold(),0.0001);
            assertTrue(p.encodingThreshold()>=0.25);assertTrue(p.contextBudget()<=3000);assertTrue(p.decayMultiplier()<=2);
        }
        assertTrue(f.repository.profile(1L).encodingThreshold()<0.45);
    }
    @Test void maintenanceArchivesWeakOldFactsButProtectsPinnedAndHealthFacts() throws Exception {
        var f=new CognitiveMemoryFixture();f.remember("ADD","diet.preference","清淡",false);f.remember("ADD","exercise.habit","游泳",false);f.remember("ADD","health.allergy.peanut","花生过敏",false);
        f.service.pin(1L,f.id("exercise.habit"),true);
        f.jdbc.update("UPDATE user_memory SET updated_at=?,importance=0.5",LocalDateTime.now().minusYears(2));
        new MemoryMaintenance(f.repository,f.users,f.forgetting,f.settings,f.transactions,f.ai).maintainUser(1L,LocalDateTime.now());
        assertEquals("ARCHIVED",f.repository.fact(1L,"diet.preference").status());assertEquals("ACTIVE",f.repository.fact(1L,"exercise.habit").status());
        assertEquals("ACTIVE",f.repository.fact(1L,"health.allergy.peanut").status());
    }
}
