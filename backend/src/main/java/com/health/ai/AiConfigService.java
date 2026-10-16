package com.health.ai;

import com.health.common.utils.ContentHash;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Set;

/** Platform-owned credentials. Historical user_ai_config records are never read. */
@Service
public class AiConfigService {
    private final String provider, model, apiKey, revision;
    public AiConfigService(@Value("${ai.platform.provider:deepseek}") String provider,
                           @Value("${ai.platform.model:deepseek-flash}") String model,
                           @Value("${ai.platform.api-key:}") String apiKey,
                           @Value("${ai.platform.revision:1}") String revision) {
        if(!Set.of("deepseek","dashscope").contains(provider)) throw new IllegalArgumentException("平台 AI 服务商不受支持");
        if(!model.matches("[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,99}")) throw new IllegalArgumentException("平台 AI 模型名称无效");
        this.provider=provider; this.model=model; this.apiKey=apiKey.trim();
        this.revision=ContentHash.sha256("platform-v1\n"+provider+"\n"+model+"\n"+revision);
    }
    public record Settings(boolean configured) {}
    public record Credentials(String provider,String model,String apiKey,String embeddingApiKey,String revision) {
        @Override public String toString() { return "Credentials[REDACTED]"; }
    }
    public Settings settings(Long userId) { return new Settings(!apiKey.isBlank()); }
    public String revision(Long userId) { return revision; }
    public Credentials credentials(Long userId) {
        if(apiKey.isBlank()) throw new AiServiceException("平台 AI 服务暂未配置，请联系管理员");
        return new Credentials(provider,model,apiKey,null,revision);
    }
}
