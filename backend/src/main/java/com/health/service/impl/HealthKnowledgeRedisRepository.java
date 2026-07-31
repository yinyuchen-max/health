package com.health.service.impl;

import com.health.common.config.RagRedisProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 向量仓库
 * 管理健康知识的向量存储、版本切换和分布式锁
 * 使用 InMemoryEmbeddingStore 进行向量检索，Redis 存储元数据和分布式锁
 */
@Component
public class HealthKnowledgeRedisRepository {

    private static final Logger log = LoggerFactory.getLogger(HealthKnowledgeRedisRepository.class);

    private final RagRedisProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final EmbeddingModel embeddingModel;

    /** 当前活跃的向量存储实例 */
    private volatile InMemoryEmbeddingStore<TextSegment> activeStore;
    /** 当前活跃版本 */
    private volatile String activeVersion;

    public HealthKnowledgeRedisRepository(
            RagRedisProperties properties,
            StringRedisTemplate stringRedisTemplate,
            EmbeddingModel embeddingModel) {
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 搜索相似的知识片段
     */
    public List<String> search(Embedding queryEmbedding, int maxResults, double minScore) {
        try {
            InMemoryEmbeddingStore<TextSegment> store = getActiveStore();
            if (store == null) {
                return Collections.emptyList();
            }

            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();

            EmbeddingSearchResult<TextSegment> result = store.search(request);
            return result.matches().stream()
                    .map(match -> match.embedded().text())
                    .toList();
        } catch (Exception e) {
            log.warn("向量搜索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取当前活跃的向量存储
     */
    private InMemoryEmbeddingStore<TextSegment> getActiveStore() {
        if (activeStore != null) {
            return activeStore;
        }

        try {
            String currentVersion = stringRedisTemplate.opsForValue()
                    .get(properties.key("active-version"));

            if (currentVersion != null && !currentVersion.isEmpty()) {
                activeVersion = currentVersion;
                // 向量存储需要在构建时填充，这里返回 null 表示需要重建
                return null;
            }
        } catch (Exception e) {
            log.warn("获取活跃版本失败: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 构建并激活新的向量版本
     */
    public void buildAndActivate(List<TextSegment> segments, String sourceHash) {
        String newVersion = generateVersion(sourceHash);
        String lockKey = properties.key("rebuild-lock");
        String lockToken = UUID.randomUUID().toString();

        // 尝试获取分布式锁
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockToken, properties.getLockTtl());

        if (!Boolean.TRUE.equals(locked)) {
            log.info("另一个实例正在构建向量索引，等待完成...");
            waitForRebuild();
            return;
        }

        try {
            log.info("开始构建新的向量版本: {}", newVersion);

            // 创建内存向量存储
            InMemoryEmbeddingStore<TextSegment> newStore = new InMemoryEmbeddingStore<>();

            // 嵌入并存储所有文本段
            if (!segments.isEmpty()) {
                List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
                List<String> ids = new ArrayList<>();
                for (int i = 0; i < segments.size(); i++) {
                    ids.add("doc-" + i);
                }
                newStore.addAll(ids, embeddings, segments);
            }

            // 原子性地切换到新版本
            activateVersion(newVersion, sourceHash);

            // 更新本地缓存
            activeStore = newStore;
            activeVersion = newVersion;

            log.info("向量版本 {} 已激活，包含 {} 个文档", newVersion, segments.size());

        } catch (Exception e) {
            log.error("构建向量版本失败: {}", e.getMessage());
            // 清理失败的版本
            cleanupVersion(newVersion);
        } finally {
            // 释放锁
            releaseLock(lockKey, lockToken);
        }
    }

    /**
     * 检查源文件是否已更改
     */
    public boolean isSourceChanged(String currentHash) {
        try {
            String storedHash = stringRedisTemplate.opsForValue()
                    .get(properties.key("source-hash"));
            return storedHash == null || !storedHash.equals(currentHash);
        } catch (Exception e) {
            log.warn("检查源文件哈希失败: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 检查是否有活跃的向量存储
     */
    public boolean hasActiveStore() {
        return activeStore != null;
    }

    // ==================== 私有方法 ====================

    private void activateVersion(String version, String sourceHash) {
        String versionKey = properties.key("active-version");
        String hashKey = properties.key("source-hash");

        // 获取旧版本用于清理
        String oldVersion = stringRedisTemplate.opsForValue().get(versionKey);

        // 原子性地更新版本和哈希
        stringRedisTemplate.opsForValue().set(versionKey, version);
        stringRedisTemplate.opsForValue().set(hashKey, sourceHash);

        // 清理旧版本
        if (oldVersion != null && !oldVersion.equals(version)) {
            cleanupVersion(oldVersion);
        }
    }

    private void cleanupVersion(String version) {
        try {
            log.debug("清理旧版本: {}", version);
        } catch (Exception e) {
            log.warn("清理旧版本失败: {}", e.getMessage());
        }
    }

    private void waitForRebuild() {
        try {
            Thread.sleep(properties.getLockWait().toMillis());
            // 重新尝试获取活跃存储
            activeStore = null;
            getActiveStore();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待重建被中断");
        }
    }

    private void releaseLock(String lockKey, String lockToken) {
        try {
            String storedToken = stringRedisTemplate.opsForValue().get(lockKey);
            if (lockToken.equals(storedToken)) {
                stringRedisTemplate.delete(lockKey);
            }
        } catch (Exception e) {
            log.warn("释放锁失败: {}", e.getMessage());
        }
    }

    private String generateVersion(String sourceHash) {
        return sourceHash.substring(0, 8) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
