package com.health.service.impl;

import com.health.domain.entity.HealthRecord;
import com.health.domain.entity.SportRecord;
import com.health.domain.entity.User;
import com.health.service.HealthKnowledgeRagService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class HealthKnowledgeRagServiceImpl implements HealthKnowledgeRagService {

    private static final Logger log = LoggerFactory.getLogger(HealthKnowledgeRagServiceImpl.class);
    private static final int MAX_SNIPPETS = 6;
    private static final double MIN_SCORE = 0.1;

    private final String markdown;
    private final List<String> chunks;
    private final EmbeddingModel embeddingModel;
    private final HealthKnowledgeRedisRepository redisRepository;

    @Autowired
    public HealthKnowledgeRagServiceImpl(
            EmbeddingModel embeddingModel,
            HealthKnowledgeRedisRepository redisRepository
    ) {
        this.markdown = loadKnowledgeDocument();
        this.chunks = splitMarkdown(this.markdown);
        this.embeddingModel = embeddingModel;
        this.redisRepository = redisRepository;
    }

    @Override
    public List<String> retrieveRelevantKnowledge(
            User user,
            List<HealthRecord> healthRecords,
            List<SportRecord> sportRecords,
            Double bmi,
            Integer weeklyExerciseMinutes
    ) {
        String query = buildQueryText(user, healthRecords, sportRecords, bmi, weeklyExerciseMinutes);
        if (query.isBlank() || chunks.isEmpty()) {
            return List.of();
        }

        // 尝试使用 Redis 向量检索
        try {
            List<String> results = vectorRetrieve(query);
            if (!results.isEmpty()) {
                return results;
            }
        } catch (Exception e) {
            log.warn("向量 RAG 检索失败，回退到关键词检索: {}", e.getMessage());
        }

        // 回退到关键词检索
        return keywordRetrieve(user, healthRecords, sportRecords, bmi, weeklyExerciseMinutes);
    }

    /**
     * 使用 Redis 向量存储进行检索
     */
    private List<String> vectorRetrieve(String query) {
        String currentHash = sha256(markdown);

        // 检查是否需要重建索引
        if (redisRepository.isSourceChanged(currentHash)) {
            List<TextSegment> segments = chunks.stream()
                    .map(TextSegment::from)
                    .toList();
            redisRepository.buildAndActivate(segments, currentHash);
        }

        // 执行向量搜索
        if (!redisRepository.hasActiveStore()) {
            return List.of();
        }

        Embedding queryEmbedding = embeddingModel.embed(query).content();
        return redisRepository.search(queryEmbedding, MAX_SNIPPETS, MIN_SCORE);
    }

    private static String loadKnowledgeDocument() {
        ClassPathResource resource = new ClassPathResource("rag/health-knowledge.md");
        if (!resource.exists()) {
            log.warn("RAG knowledge document not found: rag/health-knowledge.md");
            return "";
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to load RAG knowledge document: {}", e.getMessage());
            return "";
        }
    }

    private static List<String> splitMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        String currentTitle = "";
        StringBuilder currentBody = new StringBuilder();

        for (String line : markdown.split("\\R")) {
            if (line.startsWith("## ")) {
                addChunk(result, currentTitle, currentBody);
                currentTitle = line.substring(3).trim();
                currentBody = new StringBuilder();
            } else {
                currentBody.append(line).append('\n');
            }
        }
        addChunk(result, currentTitle, currentBody);
        return result;
    }

    private static void addChunk(List<String> chunks, String title, StringBuilder body) {
        String text = body.toString().trim();
        if (text.isBlank()) {
            return;
        }
        chunks.add(title == null || title.isBlank() ? text : "## " + title + "\n" + text);
    }

    private static String buildQueryText(
            User user,
            List<HealthRecord> healthRecords,
            List<SportRecord> sportRecords,
            Double bmi,
            Integer weeklyExerciseMinutes
    ) {
        StringBuilder query = new StringBuilder("健康建议 ");
        if (user != null) {
            appendIfPresent(query, "年龄", user.getAge());
            appendIfPresent(query, "身高", user.getHeight());
            appendIfPresent(query, "体重", user.getWeight());
        }
        appendIfPresent(query, "BMI", bmi);
        appendIfPresent(query, "周运动分钟", weeklyExerciseMinutes);

        HealthRecord latest = healthRecords == null || healthRecords.isEmpty() ? null : healthRecords.get(0);
        if (latest != null) {
            appendIfPresent(query, "收缩压", latest.getBloodPressureSystolic());
            appendIfPresent(query, "舒张压", latest.getBloodPressureDiastolic());
            appendIfPresent(query, "心率", latest.getHeartRate());
            appendIfPresent(query, "血糖", latest.getBloodSugar());
            appendIfPresent(query, "记录体重", latest.getWeight());
        }

        if (sportRecords != null) {
            sportRecords.stream().limit(3).forEach(record -> {
                appendIfPresent(query, "运动类型", record.getSportType());
                appendIfPresent(query, "运动时长", record.getDuration());
                appendIfPresent(query, "运动强度", record.getIntensity());
            });
        }
        return query.toString();
    }

    private static void appendIfPresent(StringBuilder query, String label, Object value) {
        if (value != null) {
            query.append(label).append(":").append(value).append(" ");
        }
    }

    private List<String> keywordRetrieve(
            User user,
            List<HealthRecord> healthRecords,
            List<SportRecord> sportRecords,
            Double bmi,
            Integer weeklyExerciseMinutes
    ) {
        Set<String> queryTerms = buildQueryTerms(user, healthRecords, sportRecords, bmi, weeklyExerciseMinutes);
        if (queryTerms.isEmpty() || chunks.isEmpty()) {
            return List.of();
        }

        return chunks.stream()
                .map(chunk -> new ScoredChunk(chunk, score(chunk, queryTerms)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(ScoredChunk::score).reversed())
                .limit(MAX_SNIPPETS)
                .map(ScoredChunk::chunk)
                .toList();
    }

    private static Set<String> buildQueryTerms(
            User user,
            List<HealthRecord> healthRecords,
            List<SportRecord> sportRecords,
            Double bmi,
            Integer weeklyExerciseMinutes
    ) {
        Set<String> terms = new LinkedHashSet<>();
        terms.add("健康");
        terms.add("建议");

        if (bmi != null) {
            terms.add("bmi");
            terms.add("体重");
            if (bmi >= 24.0) {
                terms.add("超重");
                terms.add("热量");
            } else if (bmi < 18.5) {
                terms.add("偏低");
                terms.add("营养");
            }
        }

        HealthRecord latest = healthRecords == null || healthRecords.isEmpty() ? null : healthRecords.get(0);
        if (latest != null) {
            if (latest.getBloodPressureSystolic() != null || latest.getBloodPressureDiastolic() != null) {
                terms.add("血压");
                terms.add("钠盐");
            }
            if (latest.getBloodSugar() != null) {
                terms.add("血糖");
                terms.add("gi");
                terms.add("精制糖");
            }
            if (latest.getHeartRate() != null) {
                terms.add("心率");
            }
        }

        int minutes = weeklyExerciseMinutes == null ? 0 : weeklyExerciseMinutes;
        if (minutes < 150) {
            terms.add("运动");
            terms.add("150");
            terms.add("有氧");
        }

        if (sportRecords != null && !sportRecords.isEmpty()) {
            terms.add("运动");
            for (SportRecord record : sportRecords.stream().limit(3).toList()) {
                if (record.getSportType() != null) {
                    terms.add(record.getSportType().toLowerCase(Locale.ROOT));
                }
            }
        }

        if (user != null && user.getAge() != null && user.getAge() >= 45) {
            terms.add("中老年");
            terms.add("慢病");
        }

        return terms;
    }

    private static int score(String chunk, Set<String> queryTerms) {
        String searchText = chunk.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : queryTerms) {
            if (searchText.contains(term.toLowerCase(Locale.ROOT))) {
                score++;
            }
        }
        return score;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }

    private record ScoredChunk(String chunk, int score) {
    }
}
