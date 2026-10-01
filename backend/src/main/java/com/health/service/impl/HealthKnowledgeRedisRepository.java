package com.health.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.common.config.RagRedisProperties;
import com.health.common.utils.ContentHash;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/** 完整向量和内容哈希持久化到 Redis；JVM 内存仅用于加速检索。 */
@Component
public class HealthKnowledgeRedisRepository {
    private static final Logger log = LoggerFactory.getLogger(HealthKnowledgeRedisRepository.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);
    private static final DefaultRedisScript<Long> SAVE_INDEX = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then redis.call('set', KEYS[2], ARGV[2]); return 1 else return 0 end",
            Long.class);

    private final RagRedisProperties properties;
    private final StringRedisTemplate redis;
    private final EmbeddingModel embeddingModel;
    private final Object[] queryLocks = IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();
    private volatile ActiveIndex activeIndex;

    @Value("${langchain4j.openai.embedding-model-name:text-embedding-3-small}")
    private String embeddingModelName = "text-embedding-3-small";
    @Value("${langchain4j.openai.embedding-base-url:https://api.openai.com/v1}")
    private String embeddingBaseUrl = "https://api.openai.com/v1";

    public HealthKnowledgeRedisRepository(RagRedisProperties properties, StringRedisTemplate redis,
                                          EmbeddingModel embeddingModel) {
        this.properties = properties;
        this.redis = redis;
        this.embeddingModel = embeddingModel;
    }

    public List<String> search(Embedding queryEmbedding, int maxResults, double minScore) {
        ActiveIndex index = activeIndex;
        if (index == null) {
            return List.of();
        }
        return index.store().search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding).maxResults(maxResults).minScore(minScore).build())
                .matches().stream().map(match -> match.embedded().text()).toList();
    }

    /** 内容未变时从 Redis 恢复完整索引，不因重启调用 embedding。 */
    public synchronized boolean needsRebuild(String sourceHash) {
        return !restoreIndex(indexHash(sourceHash));
    }

    public boolean isSourceChanged(String sourceHash) {
        return needsRebuild(sourceHash);
    }

    public boolean hasActiveStore() {
        return activeIndex != null;
    }

    public synchronized void buildAndActivate(List<TextSegment> segments, String sourceHash) {
        String hash = indexHash(sourceHash);
        if (restoreIndex(hash)) {
            return;
        }
        String lockKey = properties.key("rebuild-lock");
        String token = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, token, properties.getLockTtl()))) {
            // 等待者只恢复持久化索引，绝不绕过锁重新调用模型。
            long deadline = System.nanoTime() + properties.getLockWait().toNanos();
            do {
                if (restoreIndex(hash)) {
                    return;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } while (System.nanoTime() < deadline);
            log.info("向量索引正在由其他实例构建，本次检索使用关键词回退");
            return;
        }
        try {
            // 获取锁后复查，避免与已完成的构建重复。
            if (restoreIndex(hash)) {
                return;
            }
            InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
            if (!segments.isEmpty()) {
                List<Embedding> vectors = embeddingModel.embedAll(segments).content();
                if (vectors.size() != segments.size()) {
                    throw new IllegalStateException("Embedding result count does not match document count");
                }
                store.addAll(vectors, segments);
            }
            String snapshot = JSON.writeValueAsString(new IndexSnapshot(hash, store.serializeToJson()));
            Long saved = redis.execute(SAVE_INDEX, List.of(lockKey, properties.key("index-snapshot:v2")),
                    token, snapshot);
            if (!Long.valueOf(1).equals(saved)) {
                throw new IllegalStateException("Index rebuild lock expired before persistence");
            }
            activeIndex = new ActiveIndex(hash, store);
            log.info("知识库向量已持久化，包含 {} 个文档", segments.size());
        } catch (Exception e) {
            log.warn("构建向量索引失败，本次使用关键词回退: {}", e.getMessage());
        } finally {
            try {
                redis.execute(RELEASE_LOCK, List.of(lockKey), token);
            } catch (Exception e) {
                log.warn("释放向量索引锁失败: {}", e.getMessage());
            }
        }
    }

    private boolean restoreIndex(String hash) {
        if (activeIndex != null && hash.equals(activeIndex.hash())) {
            return true;
        }
        // 不允许新知识文档配上旧的向量索引。
        activeIndex = null;
        try {
            String json = redis.opsForValue().get(properties.key("index-snapshot:v2"));
            if (json == null) {
                return false;
            }
            IndexSnapshot snapshot = JSON.readValue(json, IndexSnapshot.class);
            if (!hash.equals(snapshot.hash())) {
                return false;
            }
            activeIndex = new ActiveIndex(hash, InMemoryEmbeddingStore.fromJson(snapshot.storeJson()));
            log.info("已从 Redis 恢复知识库向量，跳过向量模型调用");
            return true;
        } catch (Exception e) {
            log.warn("恢复向量索引失败: {}", e.getMessage());
            return false;
        }
    }

    /** 每位用户仅保留最新查询向量；查询及模型配置相同时永久复用。 */
    public Embedding embedQuery(String query, Long userId) {
        String hash = ContentHash.sha256(modelNamespace() + "\n" + query);
        String key = properties.key("query:v1:" + (userId == null ? hash : userId));
        synchronized (queryLocks[Math.floorMod(key.hashCode(), queryLocks.length)]) {
            try {
                String json = redis.opsForValue().get(key);
                if (json != null) {
                    QuerySnapshot snapshot = JSON.readValue(json, QuerySnapshot.class);
                    if (hash.equals(snapshot.hash()) && snapshot.vector() != null && snapshot.vector().length > 0) {
                        return Embedding.from(snapshot.vector());
                    }
                }
            } catch (Exception e) {
                log.warn("读取查询向量缓存失败: {}", e.getMessage());
            }
            Embedding embedding = embeddingModel.embed(query).content();
            try {
                redis.opsForValue().set(key, JSON.writeValueAsString(new QuerySnapshot(hash, embedding.vector())));
            } catch (Exception e) {
                log.warn("保存查询向量缓存失败: {}", e.getMessage());
            }
            return embedding;
        }
    }

    private String modelNamespace() {
        return embeddingBaseUrl + "\n" + embeddingModelName;
    }

    private String indexHash(String sourceHash) {
        return ContentHash.sha256("index-v2\n" + modelNamespace() + "\n" + sourceHash);
    }

    private record ActiveIndex(String hash, InMemoryEmbeddingStore<TextSegment> store) { }
    public record IndexSnapshot(String hash, String storeJson) { }
    public record QuerySnapshot(String hash, float[] vector) { }
}
