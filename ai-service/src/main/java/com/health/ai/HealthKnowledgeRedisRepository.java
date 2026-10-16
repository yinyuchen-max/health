package com.health.ai;

import com.fasterxml.jackson.databind.ObjectMapper;


import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/** 完整向量和内容哈希持久化到 Redis；JVM 内存仅用于加速检索。 */
public class HealthKnowledgeRedisRepository implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(HealthKnowledgeRedisRepository.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);
    private static final DefaultRedisScript<Long> SAVE_INDEX = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then redis.call('set', KEYS[2], ARGV[2]); return 1 else return 0 end",
            Long.class);
    private static final DefaultRedisScript<Long> RENEW_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> SAVE_QUERY = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then redis.call('set', KEYS[2], ARGV[2], 'EX', 86400); return 1 else return 0 end", Long.class);
    private final java.util.concurrent.ScheduledExecutorService renewals = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread=new Thread(r,"knowledge-lock-renewal"); thread.setDaemon(true); return thread;
    });

    private final RagRedisProperties properties;
    private final StringRedisTemplate redis;
    private final EmbeddingModel embeddingModel;
    private final Object[] queryLocks = IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();
    private volatile ActiveIndex activeIndex;

    private String embeddingModelName = ModelFactory.EMBEDDING_MODEL;
    private String embeddingBaseUrl = ModelFactory.EMBEDDING_BASE_URL;

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
        var renewal=renew(lockKey,token);
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
            log.warn("构建向量索引失败，本次使用关键词回退");
        } finally {
            renewal.cancel(false);
            try {
                redis.execute(RELEASE_LOCK, List.of(lockKey), token);
            } catch (Exception e) {
                log.warn("释放向量索引锁失败");
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
            log.warn("恢复向量索引失败");
            return false;
        }
    }

    /** 查询费用由平台承担；每用户仅缓存最新查询一天，不混用私有查询内容。 */
    public Embedding embedQuery(String query, Long userId) {
        String hash = ContentHash.sha256(modelNamespace() + "\n" + query);
        String key = properties.key("query:v2:" + (userId == null ? hash : userId));
        synchronized (queryLocks[Math.floorMod(key.hashCode(), queryLocks.length)]) {
            Embedding cached=readQuery(key,hash);
            if(cached!=null) return cached;
            String lockKey=key+":lock", token=UUID.randomUUID().toString();
            if(!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey,token,properties.getLockTtl()))) {
                long deadline=System.nanoTime()+properties.getLockWait().toNanos();
                do {
                    cached=readQuery(key,hash);
                    if(cached!=null) return cached;
                    try { Thread.sleep(50); }
                    catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("查询已取消"); }
                } while(System.nanoTime()<deadline);
                // Never call the paid model when another instance owns this user's query lock.
                throw new IllegalStateException("查询向量正在生成");
            }
            var renewal=renew(lockKey,token);
            try {
                cached=readQuery(key,hash);
                if(cached!=null) return cached;
                Embedding embedding=embeddingModel.embed(query).content();
                Long saved=redis.execute(SAVE_QUERY,List.of(lockKey,key),token,
                        JSON.writeValueAsString(new QuerySnapshot(hash,embedding.vector())));
                if(!Long.valueOf(1).equals(saved)) throw new IllegalStateException("查询锁已失效");
                return embedding;
            } catch(RuntimeException e) { throw e; }
            catch(Exception e) { throw new IllegalStateException("查询向量生成失败"); }
            finally {
                renewal.cancel(false);
                try { redis.execute(RELEASE_LOCK,List.of(lockKey),token); }
                catch(Exception ignored) { log.warn("释放查询向量锁失败"); }
            }
        }
    }

    private Embedding readQuery(String key,String hash) {
        try {
            String value=redis.opsForValue().get(key);
            if(value==null) return null;
            QuerySnapshot snapshot=JSON.readValue(value,QuerySnapshot.class);
            return hash.equals(snapshot.hash()) && snapshot.vector()!=null && snapshot.vector().length>0
                    ? Embedding.from(snapshot.vector()) : null;
        } catch(Exception e) { return null; }
    }

    private java.util.concurrent.ScheduledFuture<?> renew(String key,String token) {
        long interval=Math.max(1,properties.getLockTtl().toMillis()/3);
        return renewals.scheduleAtFixedRate(() -> {
            try { redis.execute(RENEW_LOCK,List.of(key),token,Long.toString(properties.getLockTtl().toMillis())); }
            catch(Exception ignored) { log.warn("续期知识库锁失败；写入时仍校验锁所有权"); }
        },interval,interval,java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    @Override public void close() { renewals.shutdownNow(); }

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
