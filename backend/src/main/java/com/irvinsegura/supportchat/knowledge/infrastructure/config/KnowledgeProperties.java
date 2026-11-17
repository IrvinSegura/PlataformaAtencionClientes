package com.irvinsegura.supportchat.knowledge.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("supportchat.knowledge")
public record KnowledgeProperties(@Valid @NotNull Embedding embedding, @Valid @NotNull Chunking chunking,
        @Valid @NotNull Ingestion ingestion, @Valid @NotNull Web web) {

    public enum Provider {
        HASHING,
        VOYAGE
    }

    public record Embedding(@NotNull Provider provider, @Valid @NotNull Voyage voyage) {
    }

    public record Voyage(String apiKey, @NotBlank String model, @NotBlank String baseUrl,
            @NotNull Duration timeout) {
    }

    public record Chunking(@Min(100) int maxChunkChars, @Min(0) int overlapChars) {
    }

    /**
     * @param maxConcurrent documents one instance processes at the same time
     */
    public record Ingestion(@Min(1) int maxConcurrent) {
    }

    /**
     * @param timeout             longest wait for a page
     * @param allowPrivateNetwork fetch pages on private addresses and other ports; never in production
     */
    public record Web(@NotNull Duration timeout, boolean allowPrivateNetwork) {
    }
}
