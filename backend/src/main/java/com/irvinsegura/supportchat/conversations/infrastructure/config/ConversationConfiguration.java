package com.irvinsegura.supportchat.conversations.infrastructure.config;

import com.irvinsegura.supportchat.conversations.application.AnswerReuse;
import com.irvinsegura.supportchat.conversations.application.ConversationSettings;
import com.irvinsegura.supportchat.conversations.application.ConversationSettings.Replies;
import com.irvinsegura.supportchat.conversations.application.CustomerService;
import com.irvinsegura.supportchat.conversations.application.DifficultQuestionService;
import com.irvinsegura.supportchat.conversations.application.ModelPrices;
import com.irvinsegura.supportchat.conversations.domain.model.ModelRouter;
import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import com.irvinsegura.supportchat.conversations.domain.model.SourceSelection;
import com.irvinsegura.supportchat.conversations.domain.port.ChatModel;
import com.irvinsegura.supportchat.conversations.infrastructure.llm.ChatModels;
import com.irvinsegura.supportchat.conversations.infrastructure.llm.ChatModels.TierModel;
import com.irvinsegura.supportchat.knowledge.KnowledgeSearch;
import com.irvinsegura.supportchat.shared.domain.Language;
import com.irvinsegura.supportchat.shared.i18n.LocalizedTexts;
import java.net.http.HttpClient;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class ConversationConfiguration {

    @Bean
    ConversationSettings conversationSettings(ConversationProperties properties, KnowledgeSearch knowledge) {
        ConversationProperties.Answer answer = properties.answer();
        double minSimilarity = answer.minSimilarity() != null
                ? answer.minSimilarity()
                : knowledge.relatedSimilarity();
        LocalizedTexts texts = LocalizedTexts.load("replies");
        Map<Language, Replies> replies = new EnumMap<>(Language.class);
        for (Language language : texts.languages()) {
            replies.put(language, new Replies(texts.text(language, "no-answer"), texts.text(language, "failure"),
                    texts.text(language, "confirm-prompt"), texts.text(language, "done"),
                    texts.text(language, "failed"), texts.text(language, "cancelled"),
                    texts.text(language, "expired"), texts.text(language, "greeting"),
                    texts.text(language, "thanks"), texts.text(language, "goodbye"),
                    texts.text(language, "acknowledged")));
        }
        return new ConversationSettings(
                new SourceSelection(minSimilarity, answer.maxSources()),
                answer.historyMessages(),
                answer.maxMessageChars(),
                answer.maxQuestionsPerConversation(),
                properties.visitorTokenTtl(),
                new ModelRouter(properties.routing().enabled(), new ModelRouter.Thresholds(
                        properties.routing().severalDocuments().count(),
                        properties.routing().severalDocuments().closeness(),
                        properties.routing().severalDocuments().sameTextOverlap(), minSimilarity)),
                properties.routing().escalateNoAnswer(),
                answer.maxToolRounds(),
                properties.defaultLanguage(),
                replies);
    }

    @Bean
    AnswerReuse.Settings answerReuseSettings(ConversationProperties properties) {
        return new AnswerReuse.Settings(properties.answerReuse().enabled(), properties.answerReuse().timeToLive());
    }

    @Bean
    CustomerService.Settings customerMemorySettings(ConversationProperties properties) {
        ConversationProperties.CustomerMemory memory = properties.customerMemory();
        return new CustomerService.Settings(memory.enabled(), memory.lookBack(), memory.messages());
    }

    @Bean
    DifficultQuestionService.Settings difficultQuestionSettings(ConversationProperties properties) {
        ConversationProperties.DifficultQuestions difficult = properties.difficultQuestions();
        return new DifficultQuestionService.Settings(difficult.answeredWellToClose(), difficult.quietPeriod(),
                difficult.alertAfter(), difficult.sameQuestionSimilarity());
    }

    @Bean
    ChatModel chatModel(ConversationProperties properties, JsonMapper json) {
        ConversationProperties.ChatModelSettings settings = properties.chatModel();
        return switch (settings.provider()) {
            case OFFLINE -> ChatModels.offline();
            case ANTHROPIC -> {
                ConversationProperties.Anthropic anthropic = settings.anthropic();
                HttpClient http = HttpClient.newBuilder().connectTimeout(anthropic.connectTimeout()).build();
                yield ChatModels.anthropic(http, json, anthropic.baseUrl(), anthropic.apiKey(),
                        Map.of(ModelTier.SIMPLE, new TierModel(anthropic.simpleModel(), anthropic.simpleEffort()),
                                ModelTier.COMPLEX, new TierModel(anthropic.model(), anthropic.complexEffort())),
                        anthropic.maxOutputTokens(), anthropic.readTimeout());
            }
        };
    }

    @Bean
    ModelPrices modelPrices(ConversationProperties properties) {
        Map<String, ModelPrices.Price> prices = new HashMap<>();
        if (properties.modelPrices() != null) {
            properties.modelPrices().forEach((model, price) ->
                    prices.put(model, new ModelPrices.Price(price.input(), price.output())));
        }
        return new ModelPrices(prices);
    }
}
