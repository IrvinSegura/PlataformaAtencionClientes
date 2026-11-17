package com.irvinsegura.supportchat.knowledge.infrastructure.config;

import com.irvinsegura.supportchat.knowledge.application.IngestionSlots;
import com.irvinsegura.supportchat.knowledge.domain.model.TextChunker;
import com.irvinsegura.supportchat.knowledge.domain.port.EmbeddingModel;
import com.irvinsegura.supportchat.knowledge.domain.port.WebPageFetcher;
import com.irvinsegura.supportchat.knowledge.infrastructure.embedding.EmbeddingModels;
import com.irvinsegura.supportchat.knowledge.infrastructure.fetching.WebFetchers;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class KnowledgeConfiguration {

    @Bean
    TextChunker textChunker(KnowledgeProperties properties) {
        return new TextChunker(properties.chunking().maxChunkChars(), properties.chunking().overlapChars());
    }

    @Bean
    EmbeddingModel embeddingModel(KnowledgeProperties properties, JsonMapper json) {
        KnowledgeProperties.Embedding embedding = properties.embedding();
        return switch (embedding.provider()) {
            case HASHING -> EmbeddingModels.hashing();
            case VOYAGE -> EmbeddingModels.voyage(
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), json,
                    embedding.voyage().baseUrl(), embedding.voyage().apiKey(), embedding.voyage().model(),
                    embedding.voyage().timeout());
        };
    }

    @Bean
    IngestionSlots ingestionSlots(KnowledgeProperties properties) {
        return new IngestionSlots(properties.ingestion().maxConcurrent());
    }

    @Bean
    WebPageFetcher webPageFetcher(KnowledgeProperties properties) {
        return WebFetchers.http(properties.web().timeout(), properties.web().allowPrivateNetwork());
    }
}
