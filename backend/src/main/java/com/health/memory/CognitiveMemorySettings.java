package com.health.memory;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter @Setter @Component @Validated
@ConfigurationProperties(prefix="health.memory")
public class CognitiveMemorySettings {
    @Min(8) @Max(100) private int maxRecallCandidates=80;
    @Min(1) @Max(20) private int recallLimit=8;
    @Min(50) @Max(5000) private int maxSemanticMemories=500;
    @Min(100) @Max(20000) private int maxEpisodes=2000;
    @Min(1) @Max(100) private int consolidationBatchSize=32;
    @Min(1) @Max(10) private int maxJobAttempts=3;
    @Min(120) @Max(900) private int leaseSeconds=240;
    @Min(1) @Max(365) private int archivedRetentionDays=90;
    @NotBlank @Size(max=64) private String embeddingRevision="1";
    public String vectorHash(String content) { return com.health.common.utils.ContentHash.sha256(content+"\n"+embeddingRevision); }
}
