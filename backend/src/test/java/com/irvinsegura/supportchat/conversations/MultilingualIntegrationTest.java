package com.irvinsegura.supportchat.conversations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.ApiClient.SseEvent;
import com.irvinsegura.supportchat.support.ApiClient.Visitor;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The model answers in whatever language the customer writes; the platform's own texts (fixed replies, the widget's
 * buttons and notices) follow the customer's language too: their browser's at first, then their messages'.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MultilingualIntegrationTest {

    private static final String SITE = "https://shop.example.com";

    @Autowired
    private MockMvc mockMvc;

    private ApiClient api;
    private Operator owner;
    private String widgetKey;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        owner = api.newOperator("Acme");
        widgetKey = api.enableWidget(owner, SITE);
    }

    @Test
    void aVisitorWhoseBrowserSpeaksEnglishIsAnsweredInEnglish() throws Exception {
        Visitor visitor = api.startConversation(widgetKey, SITE, "en-US,en;q=0.9,es;q=0.5");

        // Even before the visitor writes a word that tells
        assertThat(answer(visitor, "ok?")).startsWith("I don't have enough information to answer that.");
        api.getAs(owner, "/api/v1/conversations/" + visitor.conversationId())
                .andExpect(jsonPath("$.conversation.language").value("en"));
    }

    @Test
    void theLanguageFollowsWhatTheCustomerWrites() throws Exception {
        Visitor visitor = api.startConversation(widgetKey, SITE, "es-MX,es;q=0.9");

        assertThat(answer(visitor, "Vocês vendem passagens de avião?"))
                .startsWith("Não tenho informações suficientes para responder a isso.");
        // A message that tells nothing keeps the language
        assertThat(answer(visitor, "ok")).startsWith("Não tenho informações suficientes");
        assertThat(answer(visitor, "Combien coûte la livraison pour le Canada ?"))
                .startsWith("Je n'ai pas assez d'informations pour répondre à cela.");
        api.getAs(owner, "/api/v1/conversations/" + visitor.conversationId())
                .andExpect(jsonPath("$.conversation.language").value("fr"));
    }

    @Test
    void withoutAnyHintTheDefaultLanguageIsUsed() throws Exception {
        Visitor visitor = api.startConversation(widgetKey, SITE);

        assertThat(answer(visitor, "123")).startsWith("No tengo información suficiente para responder eso.");
        api.getAs(owner, "/api/v1/conversations/" + visitor.conversationId())
                .andExpect(jsonPath("$.conversation.language").doesNotExist());
    }

    @Test
    void theWidgetGetsItsOwnTextsInTheBrowsersLanguage() throws Exception {
        mockMvc.perform(get("/api/v1/widget/{key}/config", widgetKey).header(HttpHeaders.ORIGIN, SITE)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr-CA,fr;q=0.9,en;q=0.8"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeaders(HttpHeaders.VARY))
                        .anySatisfy(vary -> assertThat(vary).contains("Accept-Language")))
                .andExpect(jsonPath("$.texts.send").value("Envoyer"))
                .andExpect(jsonPath("$.texts['notices.AGENT_JOINED']").value("{} a rejoint la conversation."));
        mockMvc.perform(get("/api/v1/widget/{key}/config", widgetKey).header(HttpHeaders.ORIGIN, SITE)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "de-DE,es;q=0.5"))
                .andExpect(jsonPath("$.botName").exists())
                // The widget already speaks Spanish
                .andExpect(jsonPath("$.texts").doesNotExist());
        mockMvc.perform(get("/api/v1/widget/{key}/config", widgetKey).header(HttpHeaders.ORIGIN, SITE))
                .andExpect(jsonPath("$.texts").doesNotExist());
    }

    private String answer(Visitor visitor, String question) throws Exception {
        List<SseEvent> events = ApiClient.events(api.ask(visitor, question).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(events.getLast().name()).isEqualTo("done");
        return events.stream().filter(event -> event.name().equals("delta"))
                .map(event -> (String) JsonPath.read(event.data(), "$.text"))
                .reduce("", String::concat);
    }
}
