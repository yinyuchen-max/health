# Redis Vector Store Design

## Goal

Replace the health knowledge RAG service's in-memory, JSON-persisted vector store with Redis Stack while preserving the existing LangChain4j embedding and retrieval flow. Redis stores only health knowledge vectors. MySQL data and chat history remain unchanged.

The integration must work with the Redis Stack service supplied by the local Docker Compose environment and with an externally managed production Redis deployment configured through environment variables. The production deployment must provide Redis Query Engine (RediSearch) vector indexing support.

## Scope

In scope:

- Store and search `rag/health-knowledge.md` embeddings in Redis.
- Detect knowledge changes by SHA-256 and rebuild lazily on the next retrieval.
- Support concurrent backend instances without duplicate rebuilds.
- Keep keyword retrieval as the failure fallback.
- Add local Redis Stack deployment, configuration, and focused automated tests.

Out of scope:

- Storing chat memory or user health records in Redis.
- User-uploaded knowledge bases.
- Migrating the existing JSON vector store. Redis can regenerate all data from the Markdown source.
- Adding management endpoints for manual indexing.

## Architecture

Use the LangChain4j Redis `EmbeddingStore<TextSegment>` integration so the existing embedding and search abstractions remain intact. Hide store construction and index lifecycle behind a small health-knowledge vector repository owned by the RAG module. `HealthKnowledgeRagServiceImpl` continues to build the health query and own keyword fallback, but delegates vector persistence, search, and version activation to this repository.

Redis uses these logical keys, with all names configurable through a common prefix:

- `health:knowledge:active-version`: the version currently used by readers.
- `health:knowledge:source-hash`: the SHA-256 associated with the active version.
- `health:knowledge:rebuild-lock`: a short-lived distributed rebuild lock.
- `health:knowledge:<version>:*`: vector documents for one immutable version.
- A version-specific RediSearch index, such as `health_knowledge_idx_<version>`.

Versions are derived from the source hash plus a short unique suffix. Readers resolve the active version before constructing or selecting the corresponding store. This keeps the old index available while a replacement is being populated.

## Retrieval And Rebuild Flow

For every RAG retrieval:

1. Load the bundled Markdown and calculate its SHA-256.
2. Read the active source hash and version from Redis.
3. If the hash matches, embed the query and search the active version with the existing limits: at most six matches and a minimum score of `0.1`.
4. If the hash differs or metadata is missing, attempt to acquire the rebuild lock using an atomic `SET NX` operation with an expiry.
5. The lock owner embeds every Markdown chunk, writes a new version-specific vector set, creates or initializes its vector index using the actual embedding dimension, and verifies that the expected document count is searchable.
6. After verification, switch the active version and source hash together. A Lua script or Redis transaction performs this metadata update atomically.
7. Release the lock only when its stored ownership token still matches the current process.
8. Delete the previous version after activation. Cleanup failure is logged and does not invalidate the new active version.

If another instance owns the lock, the caller waits for a short bounded interval and retries active metadata once. It never waits for the full embedding job indefinitely. If no usable active version becomes available, retrieval falls back to keyword matching.

The initial request may therefore pay the one-time embedding cost, matching the current lazy behavior. Application startup does not call the external embedding API.

## Configuration

Application configuration will expose environment-backed properties with local defaults:

```yaml
rag:
  health:
    redis:
      host: ${HEALTH_RAG_REDIS_HOST:localhost}
      port: ${HEALTH_RAG_REDIS_PORT:6379}
      username: ${HEALTH_RAG_REDIS_USERNAME:}
      password: ${HEALTH_RAG_REDIS_PASSWORD:}
      tls: ${HEALTH_RAG_REDIS_TLS:false}
      database: ${HEALTH_RAG_REDIS_DATABASE:0}
      key-prefix: ${HEALTH_RAG_REDIS_KEY_PREFIX:health:knowledge}
      lock-ttl: ${HEALTH_RAG_REDIS_LOCK_TTL:120s}
      lock-wait: ${HEALTH_RAG_REDIS_LOCK_WAIT:2s}
```

These values bind to a dedicated, validated Spring Boot configuration-properties class and are then mapped to the LangChain4j Redis store builder. Secrets are never given non-empty defaults or logged. Docker Compose adds a persistent Redis Stack service with a health check and makes the backend depend on that health check. The backend container receives `HEALTH_RAG_REDIS_HOST=redis`; external deployments override the same variables without code changes.

## Failure Handling

Redis connection, authentication, index creation, metadata, lock, write, and search failures are caught at the RAG boundary. The service logs a concise warning without credentials and immediately uses the existing keyword retriever. Smart-health API requests continue to return results rather than failing because vector retrieval is unavailable.

Embedding provider failures during rebuild also leave the current active version untouched. A partially written version is never activated and can be removed by best-effort cleanup. An empty or unreadable Markdown source returns no RAG snippets and does not replace a valid active version.

The lock TTL prevents abandoned locks. Ownership-token comparison prevents one instance from releasing another instance's renewed lock. The TTL must exceed the expected embedding duration; the implementation will either renew it during long rebuilds or fail the rebuild before ownership can become ambiguous.

## Testing

Unit tests use fakes for embedding generation and the vector repository to verify service behavior without network calls:

- Matching source hashes do not rebuild vectors.
- A changed Markdown hash requests one new version and activates it only after successful population.
- Search preserves maximum-result and minimum-score settings.
- Redis and embedding failures invoke keyword fallback.
- Empty knowledge content does not replace an active version.

Repository integration tests run against a pinned Redis Stack image managed by Testcontainers and cover:

- Index creation, vector insertion, and similarity search.
- Atomic active-version switching.
- Lock acquisition, ownership-safe release, and contention behavior.
- Failed rebuilds leaving the previous version searchable.
- Old-version cleanup after successful activation.

The existing RAG regression test remains, adjusted to assert repository interactions instead of JSON files. A Docker Compose validation confirms Redis health and backend connectivity. The backend Maven test suite is the release gate.

## Compatibility And Rollout

The implementation uses the LangChain4j Redis integration artifact at the same pinned `1.17.0` release as the project's other LangChain4j modules. Dependency resolution and the adapter's support for isolated index names are verified before production code is changed; an incompatibility blocks implementation and requires revising this design rather than silently introducing a second vector API.

The obsolete `rag.health.vector-store-path` setting and JSON persistence code are removed. Existing JSON files are ignored and may be deleted manually after rollout. Redis data uses a persistent Docker volume locally. Production persistence, replication, backups, memory limits, and eviction policy remain deployment responsibilities; production must avoid evicting active vector keys unexpectedly.

## Acceptance Criteria

- Health knowledge retrieval uses Redis vector search when Redis Stack is healthy.
- The first retrieval builds the index lazily; unchanged knowledge does not rebuild it.
- Editing `health-knowledge.md` causes a new version to be built and atomically activated on the next retrieval.
- Concurrent backend instances do not perform duplicate successful rebuilds.
- Redis or embedding failures preserve API behavior through keyword fallback.
- Local Docker Compose and externally configured Redis deployments use the same application build.
- No chat history, user record, or unrelated cache data is moved to Redis.
