package com.health.ai;
import com.health.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.UUID;
@Service
public class AiConfigService {
    private final AiConfigMapper mapper;
    private final AiKeyCipher cipher;
    public AiConfigService(AiConfigMapper mapper, AiKeyCipher cipher) { this.mapper = mapper; this.cipher = cipher; }
    public record Settings(boolean configured, String provider, String model, boolean embeddingConfigured) {}
    public record Update(String provider, String model, String apiKey, String embeddingApiKey, boolean clearEmbeddingKey) {
        @Override public String toString() { return "Update[REDACTED]"; }
    }
    public record Credentials(String provider, String model, String apiKey, String embeddingApiKey, String revision) {
        @Override public String toString() { return "Credentials[REDACTED]"; }
    }
    public Settings settings(Long userId) {
        var c = mapper.find(userId);
        return c == null ? new Settings(false, "deepseek", "deepseek-flash", false)
                : new Settings(true, c.provider(), c.model(), c.encryptedEmbeddingApiKey()!=null);
    }
    public void save(Long userId, Update update) {
        if (update == null || !Map.of("deepseek", true, "dashscope", true).containsKey(update.provider() == null ? "" : update.provider()))
            throw new BusinessException("请选择 DeepSeek 或阿里云百炼");
        String model = update.model() == null ? "" : update.model().trim();
        if (!model.matches("[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,99}")) throw new BusinessException("请填写有效的模型名称（最多 100 字符）");
        var previous = mapper.find(userId);
        String apiKey = update.apiKey() == null ? "" : update.apiKey().trim();
        String encrypted;
        if (apiKey.isEmpty()) {
            if (previous == null || !previous.provider().equals(update.provider())) throw new BusinessException("首次配置或切换服务商时必须填写 API Key");
            encrypted = previous.encryptedApiKey();
        } else {
            if (apiKey.length() < 8 || apiKey.length() > 512 || !apiKey.matches("[!-~]+")) throw new BusinessException("API Key 格式不正确");
            encrypted = cipher.encrypt(userId, apiKey);
        }
        String embedding = previous == null ? null : previous.encryptedEmbeddingApiKey();
        if (update.clearEmbeddingKey()) embedding = null;
        String embeddingKey = update.embeddingApiKey() == null ? "" : update.embeddingApiKey().trim();
        if (!embeddingKey.isEmpty()) {
            if (embeddingKey.length()<8 || embeddingKey.length()>512 || !embeddingKey.matches("[!-~]+")) throw new BusinessException("向量 API Key 格式不正确");
            embedding=cipher.encrypt(userId, embeddingKey);
        }
        mapper.save(new AiConfigMapper.StoredConfig(userId, update.provider(), model, encrypted, embedding, UUID.randomUUID().toString()));
    }
    public void delete(Long userId) { mapper.delete(userId); }
    public String revision(Long userId) { var c = mapper.find(userId); return c == null ? "unconfigured" : c.revision(); }
    public Credentials credentials(Long userId) {
        var c = mapper.find(userId);
        if (c == null) throw new AiServiceException("请先在「AI 设置」中填写自己的 API Key");
        return new Credentials(c.provider(), c.model(), cipher.decrypt(userId, c.encryptedApiKey()), c.encryptedEmbeddingApiKey()==null ? null : cipher.decrypt(userId,c.encryptedEmbeddingApiKey()), c.revision());
    }
}
