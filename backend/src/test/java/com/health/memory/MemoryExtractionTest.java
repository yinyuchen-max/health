package com.health.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MemoryExtractionTest {
    private final ObjectMapper json = new ObjectMapper();
    private final MemoryExtraction parser = new MemoryExtraction(json);
    private String response(MemoryExtraction.Candidate c) throws Exception {
        return json.writeValueAsString(new MemoryExtraction.Result(List.of(c)));
    }
    private MemoryExtraction.Candidate candidate(String key, String evidence, String subject, String assertion) {
        return new MemoryExtraction.Candidate("ADD", "health", key, "用户自述花生过敏", evidence,
                "ongoing", null, false, subject, assertion);
    }
    @Test void requiresLiteralUserEvidenceAndDeclaredAssertedSelfFact() throws Exception {
        var good = candidate("health.allergy.peanut", "我对花生过敏", "self", "asserted");
        assertEquals(1, parser.parse("```json\n"+response(good)+"\n```", "我对花生过敏").size());
        assertTrue(parser.parse(response(good), "我喜欢游泳").isEmpty());
        assertTrue(parser.parse(response(candidate("health.condition.diabetes", "我是不是有糖尿病", "self", "question")), "我是不是有糖尿病").isEmpty());
        assertTrue(parser.parse(response(candidate("health.condition.diabetes", "假如我有糖尿病", "self", "hypothetical")), "假如我有糖尿病").isEmpty());
        assertTrue(parser.parse(response(candidate("health.condition.hypertension", "妈妈有高血压", "family", "asserted")), "妈妈有高血压").isEmpty());
    }
    @Test void rejectsUnknownFieldsTrailingJsonAndInvalidKeys() throws Exception {
        assertThrows(Exception.class, () -> parser.parse("{\"candidates\":[],\"userId\":2}", "你好"));
        assertThrows(Exception.class, () -> parser.parse("{\"candidates\":[]}{}", "你好"));
        assertTrue(parser.parse(response(candidate("diet.preference", "过敏", "self", "asserted")), "过敏").isEmpty());
        assertTrue(parser.parse(response(candidate("health.allergy;DROP TABLE", "过敏", "self", "asserted")), "过敏").isEmpty());
        assertThrows(Exception.class, () -> parser.parse("x".repeat(20001), "你好"));
    }
    @Test void requiresBoundedTemporaryExpiryAndExplicitForgetEvidence() throws Exception {
        for (Integer ttl : new Integer[]{null, 0, 31}) {
            var c = new MemoryExtraction.Candidate("ADD", "health", "health.symptom.headache", "头疼", "头疼", "temporary", ttl, false, "self", "asserted");
            assertTrue(parser.parse(response(c), "头疼").isEmpty());
        }
        var hallucinatedDelete = new MemoryExtraction.Candidate("DELETE", "diet", "diet.preference", null, "我喜欢清淡", null, null, true, "self", "asserted");
        assertTrue(parser.parse(response(hallucinatedDelete), "我喜欢清淡").isEmpty());
    }
}
