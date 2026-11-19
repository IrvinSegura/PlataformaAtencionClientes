package com.irvinsegura.supportchat.conversations;

import static com.irvinsegura.supportchat.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.ApiClient.SseEvent;
import com.irvinsegura.supportchat.support.ApiClient.Visitor;
import com.irvinsegura.supportchat.support.Eventually;
import java.time.Duration;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * A visitor asks for a person, a member of the team takes over and replies, then hands back to the assistant.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class HumanHandoffIntegrationTest {

    private static final String SITE = "https://shop.example.com";

    @Autowired
    private MockMvc mockMvc;

    private ApiClient api;
    private Operator owner;
    private Visitor visitor;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        owner = api.newOperator("Acme");
        String widgetKey = api.enableWidget(owner, SITE);
        api.uploadAndWaitUntilReady(owner, "envios.md", "# Envíos\n\nEl envío nacional tarda de 3 a 5 días hábiles.");
        visitor = api.startConversation(widgetKey, SITE);
    }

    @Test
    void aPersonTakesOverRepliesAndHandsBackToTheAssistant() throws Exception {
        api.visitorPost(visitor, "/handoff").andExpect(status().isOk())
                .andExpect(jsonPath("$.handling").value("WAITING"));
        api.getAs(owner, "/api/v1/conversations?handling=WAITING")
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(visitor.conversationId().toString()));

        // While waiting, messages are kept for the team instead of being answered
        List<SseEvent> waiting = ApiClient.events(api.ask(visitor, "¿Cuánto tarda el envío nacional?")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(waiting).extracting(SseEvent::name).containsExactly("forwarded");

        String conversation = "/api/v1/conversations/" + visitor.conversationId();
        api.postAs(owner, conversation + "/takeover").andExpect(status().isOk())
                .andExpect(jsonPath("$.handling").value("AGENT"))
                .andExpect(jsonPath("$.agentName").value("Operator"));
        api.postJsonAs(owner, conversation + "/messages", json("{'text':'Hola, soy del equipo. Tarda 3 días.'}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("AGENT"));

        api.visitorHistory(visitor).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].author").value(org.hamcrest.Matchers.contains(
                        "SYSTEM", "VISITOR", "SYSTEM", "AGENT")))
                .andExpect(jsonPath("$[0].content").value("HANDOFF_REQUESTED"))
                .andExpect(jsonPath("$[2].content").value("AGENT_JOINED"))
                .andExpect(jsonPath("$[3].authorName").value("Operator"));

        api.postAs(owner, conversation + "/release").andExpect(jsonPath("$.handling").value("ASSISTANT"));
        List<SseEvent> answered = ApiClient.events(api.ask(visitor, "¿Cuánto tarda el envío nacional?")
                .andReturn().getResponse().getContentAsString());
        assertThat(answered.getLast().name()).isEqualTo("done");
        // Only the question the assistant answered counts towards the visitor's limit
        api.getAs(owner, conversation).andExpect(jsonPath("$.conversation.questionCount").value(1));
    }

    @Test
    void onlyTheMemberWhoTookItOverReplies() throws Exception {
        Operator agent = api.newMember(owner, "AGENT");
        String conversation = "/api/v1/conversations/" + visitor.conversationId();

        api.postJsonAs(owner, conversation + "/messages", json("{'text':'Hola'}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_TAKEN"));
        api.postAs(agent, conversation + "/takeover").andExpect(status().isOk());
        api.postAs(owner, conversation + "/takeover").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONVERSATION_ASSIGNED"));
        api.postJsonAs(owner, conversation + "/messages", json("{'text':'Hola'}"))
                .andExpect(status().isConflict());
        // The owner may still free a conversation an agent left behind
        api.postAs(owner, conversation + "/release").andExpect(jsonPath("$.handling").value("ASSISTANT"));
    }

    @Test
    void aClosedConversationTakesNoMoreQuestions() throws Exception {
        api.postAs(owner, "/api/v1/conversations/" + visitor.conversationId() + "/closure")
                .andExpect(jsonPath("$.status").value("CLOSED"));

        api.ask(visitor, "¿Siguen ahí?").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CONVERSATION_CLOSED"));
        api.visitorPost(visitor, "/handoff").andExpect(status().isUnprocessableContent());
    }

    @Test
    void theVisitorSeesTheAgentJoinLive() throws Exception {
        MvcResult stream = mockMvc.perform(get("/api/v1/widget/{key}/conversations/{id}/events",
                        visitor.widgetKey(), visitor.conversationId())
                        .header(HttpHeaders.ORIGIN, SITE)
                        .header("X-Conversation-Token", visitor.token()))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertThat(stream.getResponse().getContentAsString()).contains("event:state").contains("\"ASSISTANT\"");

        api.postAs(owner, "/api/v1/conversations/" + visitor.conversationId() + "/takeover")
                .andExpect(status().isOk());

        Eventually.assertEventually(Duration.ofSeconds(5), () -> assertThat(stream.getResponse()
                .getContentAsString()).contains("AGENT_JOINED").contains("\"AGENT\""));
    }

    @Test
    void theEventsStreamNeedsTheConversationToken() throws Exception {
        mockMvc.perform(get("/api/v1/widget/{key}/conversations/{id}/events", visitor.widgetKey(),
                        visitor.conversationId())
                        .header(HttpHeaders.ORIGIN, SITE)
                        .header("X-Conversation-Token", "not-the-token"))
                .andExpect(status().isUnauthorized());
    }
}
