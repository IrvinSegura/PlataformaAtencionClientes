package com.irvinsegura.supportchat.conversations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.ApiClient.SseEvent;
import com.irvinsegura.supportchat.support.ApiClient.Visitor;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The visitor's side: the widget starts a conversation and gets grounded answers streamed back. Uses the offline
 * model, which quotes the best source, so answers are predictable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class WidgetChatIntegrationTest {

    private static final String SITE = "https://shop.example.com";
    private static final String SHIPPING = """
            # Envíos

            El envío nacional tarda de 3 a 5 días hábiles y es gratis en compras mayores a 999 pesos.
            """;

    @Autowired
    private MockMvc mockMvc;

    private ApiClient api;
    private Operator operator;
    private String widgetKey;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        operator = api.newOperator("Acme");
        widgetKey = api.enableWidget(operator, SITE);
        api.uploadAndWaitUntilReady(operator, "envios.md", SHIPPING);
    }

    @Test
    void aQuestionTheKnowledgeBaseCoversIsAnsweredWithCitations() throws Exception {
        Visitor visitor = api.startConversation(widgetKey, SITE);

        String stream = api.ask(visitor, "¿Cuánto tarda el envío nacional?")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        org.hamcrest.Matchers.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, SITE))
                .andReturn().getResponse().getContentAsString();

        List<SseEvent> events = ApiClient.events(stream);
        assertThat(events).hasSizeGreaterThan(1);
        assertThat(events.getLast().name()).isEqualTo("done");
        String answer = events.stream().filter(event -> event.name().equals("delta"))
                .map(event -> (String) JsonPath.read(event.data(), "$.text"))
                .reduce("", String::concat);
        assertThat(answer).contains("3 a 5 días hábiles").endsWith("[1]");
        String done = events.getLast().data();
        assertThat((String) JsonPath.read(done, "$.outcome")).isEqualTo("ANSWERED");
        assertThat((String) JsonPath.read(done, "$.citations[0].documentTitle")).isEqualTo("envios");
    }

    @Test
    void aQuestionWithCharactersTheDatabaseRefusesIsStillAnswered() throws Exception {
        Visitor visitor = api.startConversation(widgetKey, SITE);

        // JSON's escape for NUL, which PostgreSQL refuses in text
        String stream = api.ask(visitor, "¿Venden boletos\\u0000 de avión a Europa?")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<SseEvent> events = ApiClient.events(stream);
        assertThat((String) JsonPath.read(events.getLast().data(), "$.outcome")).isEqualTo("NO_ANSWER");
        api.visitorHistory(visitor).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("¿Venden boletos de avión a Europa?"));
    }

    @Test
    void aQuestionOutsideTheKnowledgeBaseIsNotGuessed() throws Exception {
        Visitor visitor = api.startConversation(widgetKey, SITE);

        String stream = api.ask(visitor, "¿Venden boletos de avión a Europa?")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<SseEvent> events = ApiClient.events(stream);
        assertThat((String) JsonPath.read(events.getFirst().data(), "$.text")).contains("No tengo información");
        assertThat((String) JsonPath.read(events.getLast().data(), "$.outcome")).isEqualTo("NO_ANSWER");
    }

    @Test
    void theVisitorCanReloadTheConversationAndOperatorsSeeItWithItsCost() throws Exception {
        Visitor visitor = api.startConversation(widgetKey, SITE);
        api.ask(visitor, "¿Cuánto tarda el envío nacional?").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/widget/{key}/conversations/{id}/messages", widgetKey, visitor.conversationId())
                        .header(HttpHeaders.ORIGIN, SITE)
                        .header("X-Conversation-Token", visitor.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].author").value("VISITOR"))
                .andExpect(jsonPath("$[1].author").value("ASSISTANT"))
                // Visitors never see what an answer cost
                .andExpect(jsonPath("$[1].usage").doesNotExist());

        api.getAs(operator, "/api/v1/conversations")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].origin").value(SITE))
                .andExpect(jsonPath("$.items[0].questionCount").value(1));
        api.getAs(operator, "/api/v1/conversations/" + visitor.conversationId())
                .andExpect(jsonPath("$.messages[1].usage.model").value("offline-extractive"));
    }

    @Test
    void aConversationOnlyAnswersWhoeverHoldsItsToken() throws Exception {
        Visitor visitor = api.startConversation(widgetKey, SITE);
        Visitor impostor = new Visitor(widgetKey, SITE, visitor.conversationId(), "not-the-token");

        api.ask(impostor, "¿Cuánto tarda el envío nacional?")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CONVERSATION_TOKEN"));
        api.ask(new Visitor(widgetKey, SITE, UUID.randomUUID(), visitor.token()), "Hola")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anotherWorkspacesWidgetCannotReachTheConversation() throws Exception {
        Visitor visitor = api.startConversation(widgetKey, SITE);
        Operator rival = api.newOperator("Rival");
        String rivalKey = api.enableWidget(rival, SITE);

        // Right token, wrong workspace: row-level security hides the conversation
        api.ask(new Visitor(rivalKey, SITE, visitor.conversationId(), visitor.token()), "Hola")
                .andExpect(status().isUnauthorized());
        api.getAs(rival, "/api/v1/conversations/" + visitor.conversationId()).andExpect(status().isNotFound());
        api.getAs(rival, "/api/v1/conversations").andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void theWidgetOnlyWorksOnTheWorkspacesOwnSites() throws Exception {
        mockMvc.perform(post("/api/v1/widget/{key}/conversations", widgetKey)
                        .header(HttpHeaders.ORIGIN, "https://evil.example.net"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownWidgetKeyIsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/widget/{key}/conversations", "wk_unknownunknownunknownunknown12")
                        .header(HttpHeaders.ORIGIN, SITE))
                .andExpect(status().isNotFound());
    }

    @Test
    void anEmptyQuestionIsRejectedBeforeStreaming() throws Exception {
        Visitor visitor = api.startConversation(widgetKey, SITE);

        api.ask(visitor, "  ").andExpect(status().isBadRequest());
    }
}
