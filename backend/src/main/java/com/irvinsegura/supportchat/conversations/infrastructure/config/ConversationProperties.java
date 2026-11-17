package com.irvinsegura.supportchat.conversations.infrastructure.config;

import com.irvinsegura.supportchat.shared.domain.Language;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("supportchat.conversations")
public record ConversationProperties(
        @Valid @NotNull ChatModelSettings chatModel,
        @Valid @NotNull Answer answer,
        @Valid @NotNull Routing routing,
        @Valid @NotNull AnswerReuse answerReuse,
        @Valid @NotNull DifficultQuestions difficultQuestions,
        @Valid @NotNull CustomerMemory customerMemory,
        @NotNull Language defaultLanguage,
        @NotNull Duration visitorTokenTtl,
        Map<String, @Valid ModelPrice> modelPrices) {

    /**
     * US dollars per million tokens, to estimate what an answer cost.
     */
    public record ModelPrice(@NotNull @DecimalMin("0") BigDecimal input, @NotNull @DecimalMin("0") BigDecimal output) {
    }

    public enum Provider {
        OFFLINE,
        ANTHROPIC
    }

    public record ChatModelSettings(@NotNull Provider provider, @Valid @NotNull Anthropic anthropic) {
    }

    /**
     * @param model         the capable model, for questions that need reasoning
     * @param simpleModel   the cheap model, for direct questions
     * @param simpleEffort  how much the cheap model thinks: low, medium or high
     * @param complexEffort how much the capable model thinks
     */
    public record Anthropic(String apiKey, @NotBlank String model, @NotBlank String simpleModel,
            @NotBlank @Pattern(regexp = "low|medium|high") String simpleEffort,
            @NotBlank @Pattern(regexp = "low|medium|high") String complexEffort,
            @NotBlank String baseUrl, @Min(256) @Max(16384) int maxOutputTokens, @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout) {
    }

    public record Answer(
            @Min(1) @Max(10) int maxSources,
            // Empty uses the value suited to the embedding model
            @DecimalMin("-1.0") @DecimalMax("1.0") Double minSimilarity,
            @Min(0) @Max(20) int historyMessages,
            @Min(1) @Max(2000) int maxMessageChars,
            @Min(1) int maxQuestionsPerConversation,
            @Min(0) @Max(5) int maxToolRounds) {
    }

    /**
     * @param enabled          off sends every question to the capable model
     * @param escalateNoAnswer ask the capable model again when the cheap one finds no answer in relevant sources
     * @param severalDocuments when an answer combines enough documents to need the capable model
     */
    public record Routing(boolean enabled, boolean escalateNoAnswer,
            @Valid @NotNull SeveralDocuments severalDocuments) {
    }

    /**
     * @param count           documents an answer must draw on to go to the capable model
     * @param closeness       how far behind the best passage another document may be and still count, as a share of
     *                        the best passage's lead over the minimum similarity
     * @param sameTextOverlap share of shared words from which two passages say the same thing
     */
    public record SeveralDocuments(@Min(2) int count, @DecimalMin("0") @DecimalMax("1") double closeness,
            @DecimalMin("0.1") @DecimalMax("1") double sameTextOverlap) {
    }

    /**
     * @param enabled      give the answer the model already wrote to the same question again, without a model call
     * @param timeToLive   how long an answer is given again, even when nothing it depended on changed
     */
    public record AnswerReuse(boolean enabled, @NotNull Duration timeToLive) {
    }

    /**
     * @param answeredWellToClose    answers in a row of the capable model, with no sign of difficulty, that close a
     *                               difficult question by itself
     * @param quietPeriod            how long a difficult question nobody found difficult again stays open
     * @param alertAfter             times found difficult from which the team is alerted by WhatsApp
     * @param sameQuestionSimilarity cosine similarity from which two questions are the same; empty uses the embedding
     *                               model's own value
     */
    public record DifficultQuestions(@Min(1) @Max(50) int answeredWellToClose, @NotNull Duration quietPeriod,
            @Min(2) @Max(1000) int alertAfter,
            @DecimalMin("0.0") @DecimalMax("1.0") Double sameQuestionSimilarity) {
    }

    /**
     * @param enabled  the assistant reads what a customer who writes through a channel said in earlier conversations
     * @param lookBack how far back it remembers
     * @param messages earlier messages it remembers at most
     */
    public record CustomerMemory(boolean enabled, @NotNull Duration lookBack, @Min(1) @Max(40) int messages) {
    }
}
