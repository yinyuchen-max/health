//package com.health.service.impl;
//
//import com.health.domain.entity.HealthRecord;
//import com.health.domain.entity.SportRecord;
//import com.health.domain.entity.User;
//import dev.langchain4j.data.embedding.Embedding;
//import dev.langchain4j.data.segment.TextSegment;
//import dev.langchain4j.model.embedding.EmbeddingModel;
//import dev.langchain4j.model.output.Response;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.io.TempDir;
//
//import java.math.BigDecimal;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.ArrayList;
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//class HealthKnowledgeRagServiceImplTest {
//
//    @TempDir
//    Path tempDir;
//
//    @Test
//    void shouldPersistMarkdownKnowledgeToVectorStoreAndRetrieveRelevantSegments() {
//        String markdown = """
//                ## BMI 分类与建议
//                BMI 达到 24 及以上属于超重风险，应控制总热量摄入。
//
//                ## 血压分类与生活方式建议
//                血压达到 140/90 mmHg 及以上时，应减少钠盐摄入并规律复测。
//
//                ## 血糖风险与饮食建议
//                空腹血糖达到 6.1 mmol/L 及以上时，应减少精制糖和高 GI 主食。
//
//                ## 运动建议原则
//                成人每周建议累计至少 150 分钟中等强度有氧运动。
//                """;
//        Path vectorStorePath = tempDir.resolve("health-knowledge-vector-store.json");
//        HealthKnowledgeRagServiceImpl service = new HealthKnowledgeRagServiceImpl(
//                markdown,
//                new DeterministicEmbeddingModel(),
//                vectorStorePath
//        );
//
//        User user = new User();
//        user.setAge(40);
//
//        HealthRecord record = new HealthRecord();
//        record.setBloodPressureSystolic(145.0);
//        record.setBloodPressureDiastolic(92.0);
//        record.setBloodSugar(new BigDecimal("6.5"));
//
//        SportRecord sportRecord = new SportRecord();
//        sportRecord.setSportType("快走");
//        sportRecord.setDuration(30);
//
//        List<String> result = service.retrieveRelevantKnowledge(
//                user,
//                List.of(record),
//                List.of(sportRecord),
//                26.0,
//                30
//        );
//
//        assertTrue(Files.exists(vectorStorePath));
//        assertTrue(Files.exists(vectorStorePath.resolveSibling(vectorStorePath.getFileName() + ".meta")));
//        assertTrue(result.size() <= 4);
//        assertTrue(String.join("\n", result).contains("BMI 达到 24"));
//        assertTrue(String.join("\n", result).contains("150 分钟"));
//    }
//
//    private static class DeterministicEmbeddingModel implements EmbeddingModel {
//
//        @Override
//        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
//            List<Embedding> embeddings = new ArrayList<>();
//            for (TextSegment segment : textSegments) {
//                embeddings.add(embedText(segment.text()));
//            }
//            return Response.from(embeddings);
//        }
//
//        private Embedding embedText(String text) {
//            String lower = text.toLowerCase();
//            return Embedding.from(new float[] {
//                    lower.contains("bmi") || lower.contains("体重") || lower.contains("超重") ? 1f : 0f,
//                    lower.contains("血压") || lower.contains("140/90") ? 1f : 0f,
//                    lower.contains("血糖") || lower.contains("6.1") ? 1f : 0f,
//                    lower.contains("运动") || lower.contains("150") ? 1f : 0f
//            });
//        }
//    }
//}
