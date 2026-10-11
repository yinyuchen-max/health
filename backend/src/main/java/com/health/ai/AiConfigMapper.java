package com.health.ai;
import org.apache.ibatis.annotations.*;
@Mapper
public interface AiConfigMapper {
    @Select("SELECT user_id, provider, model, encrypted_api_key, encrypted_embedding_api_key, revision FROM user_ai_config WHERE user_id=#{id}")
    StoredConfig find(Long id);
    @Insert("INSERT INTO user_ai_config(user_id,provider,model,encrypted_api_key,encrypted_embedding_api_key,revision) "
          + "VALUES(#{userId},#{provider},#{model},#{encryptedApiKey},#{encryptedEmbeddingApiKey},#{revision}) "
          + "ON DUPLICATE KEY UPDATE provider=VALUES(provider),model=VALUES(model),"
          + "encrypted_api_key=VALUES(encrypted_api_key),encrypted_embedding_api_key=VALUES(encrypted_embedding_api_key),revision=VALUES(revision)")
    void save(StoredConfig config);
    @Delete("DELETE FROM user_ai_config WHERE user_id=#{id}") void delete(Long id);
    record StoredConfig(Long userId, String provider, String model, String encryptedApiKey, String encryptedEmbeddingApiKey, String revision) {
        @Override public String toString() { return "StoredConfig[REDACTED]"; }
    }
}
