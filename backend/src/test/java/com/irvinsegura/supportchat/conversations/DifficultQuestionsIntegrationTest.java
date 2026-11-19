package com.irvinsegura.supportchat.conversations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.ApiClient.SseEvent;
import com.irvinsegura.supportchat.support.ApiClient.Visitor;
import com.irvinsegura.supportchat.support.Eventually;
import com.irvinsegura.supportchat.support.TestClock;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The assistant learns from the questions it struggled with: the next customer who asks the same goes straight to the
 * capable model, and the team can teach the right answer into the knowledge base.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DifficultQuestionsIntegrationTest {

    private static final String SITE = "https://shop.example.com";
    private static final String QUESTION = "¿Cuánto tarda el envío nacional?";
    private static final String SHIPPING = """
            # Envíos

            El envío nacional tarda de 3 a 5 días hábiles y es gratis en compras mayores a 999 pesos.
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestClock clock;

    private ApiClient api;
    private Operator owner;
    private String widgetKey;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        owner = api.newOperator("Acme");
        widgetKey = api.enableWidget(owner, SITE);
        api.uploadAndWaitUntilReady(owner, "envios.md", SHIPPING);
    }

    @Test
    void anAnswerThatDidNotHelpSendsTheQuestionToTheCapableModelNextTime() throws Exception {
        Visitor first = api.startConversation(widgetKey, SITE);
        String answerId = JsonPath.read(ask(first, QUESTION).getLast().data(), "$.messageId");
        assertThat(routingReason(first, 1)).isEqualTo("simple");

        api.visitorPutJson(first, "/messages/" + answerId + "/feedback", "{\"helpful\":false}")
                .andExpect(status().isNoContent());

        api.getAs(owner, "/api/v1/difficult-questions")
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].question").value(QUESTION))
                .andExpect(jsonPath("$.items[0].reason").value("UNHELPFUL"))
                .andExpect(jsonPath("$.items[0].lastAnswer").value(containsString("El envío nacional tarda")));

        // Asked in other words by another customer
        Visitor second = api.startConversation(widgetKey, SITE);
        ask(second, "cuanto tarda envio nacional");
        assertThat(routingReason(second, 1)).isEqualTo("learned-difficult");
    }

    @Test
    void askingTheSameAgainOrSayingItDidNotHelpRetriesWithTheCapableModel() throws Exception {
        Visitor visitor = api.startConversation(widgetKey, SITE);
        ask(visitor, QUESTION);
        // Without question marks: two or more would send it to the capable model for asking several things
        ask(visitor, "cuanto tarda el envio nacional");
        assertThat(routingReason(visitor, 3)).isEqualTo("customer-unsatisfied");

        Visitor other = api.startConversation(widgetKey, SITE);
        ask(other, "¿El envío nacional es gratis?");
        ask(other, "No entendí, ¿el envío nacional es gratis o no?");
        assertThat(routingReason(other, 3)).isEqualTo("customer-unsatisfied");

        String body = api.getAs(owner, "/api/v1/difficult-questions").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> reasons = JsonPath.read(body, "$.items[*].reason");
        assertThat(reasons).containsExactlyInAnyOrder("ASKED_AGAIN", "UNSATISFIED");
    }

    @Test
    void theTeamTeachesTheAnswerAndTheQuestionIsClosed() throws Exception {
        Visitor visitor = api.startConversation(widgetKey, SITE);
        String answerId = JsonPath.read(ask(visitor, QUESTION).getLast().data(), "$.messageId");
        api.visitorPutJson(visitor, "/messages/" + answerId + "/feedback", "{\"helpful\":false}");
        String questionId = JsonPath.read(api.getAs(owner, "/api/v1/difficult-questions").andReturn().getResponse()
                .getContentAsString(), "$.items[0].id");

        String answer = "{\"answer\":\"A Monterrey y Guadalajara llega en 2 días; al resto del país, en 3 a 5.\"}";
        api.postJsonAs(api.newMember(owner, "AGENT"), "/api/v1/difficult-questions/" + questionId + "/answer", answer)
                .andExpect(status().isForbidden());
        String taught = api.postJsonAs(owner, "/api/v1/difficult-questions/" + questionId + "/answer", answer)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String documentId = JsonPath.read(taught, "$.documentId");
        api.getAs(owner, "/api/v1/knowledge/documents/" + documentId)
                .andExpect(jsonPath("$.title").value("Respuesta del equipo: " + QUESTION));
        api.getAs(owner, "/api/v1/difficult-questions").andExpect(jsonPath("$.items", hasSize(0)));
        api.getAs(owner, "/api/v1/difficult-questions?status=TAUGHT").andExpect(jsonPath("$.items", hasSize(1)));
        // Taught: the knowledge base has the answer, so the fast model may answer it again
        Visitor next = api.startConversation(widgetKey, SITE);
        ask(next, "¿Cuánto tarda el envío nacional a Monterrey?");
        assertThat(routingReason(next, 1)).isNotEqualTo("learned-difficult");
    }

    @Test
    void anAnswerTaughtForAnUnansweredQuestionIsFoundByTheNextCustomer() throws Exception {
        ask(api.startConversation(widgetKey, SITE), "¿Venden boletos de avión a Europa?");
        Eventually.assertEventually(Duration.ofSeconds(10), () -> api.getAs(owner, "/api/v1/knowledge-gaps")
                .andExpect(jsonPath("$.items", hasSize(1))));
        String gapId = JsonPath.read(api.getAs(owner, "/api/v1/knowledge-gaps").andReturn().getResponse()
                .getContentAsString(), "$.items[0].id");

        String taught = api.postJsonAs(owner, "/api/v1/knowledge-gaps/" + gapId + "/answer",
                        "{\"answer\":\"No vendemos boletos de avión: solo enviamos paquetes dentro de México.\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String documentId = JsonPath.read(taught, "$.documentId");
        Eventually.assertEventually(Duration.ofSeconds(20), () ->
                api.getAs(owner, "/api/v1/knowledge/documents/" + documentId)
                        .andExpect(jsonPath("$.status").value("READY")));

        api.getAs(owner, "/api/v1/knowledge-gaps").andExpect(jsonPath("$.items", hasSize(0)));
        List<SseEvent> events = ask(api.startConversation(widgetKey, SITE), "¿Venden boletos de avión a Europa?");
        assertThat((String) JsonPath.read(events.getLast().data(), "$.outcome")).isEqualTo("ANSWERED");
        assertThat(text(events)).contains("No vendemos boletos de avión");
    }

    @Test
    void aQuestionTheCapableModelAnswersWellSeveralTimesInARowClosesByItself() throws Exception {
        markUnhelpful(QUESTION);
        // Other wordings, so no answer is simply given again: each one is a new answer of the capable model
        for (String wording : List.of("cuanto tarda envio nacional", "¿Cuánto tarda un envío nacional?",
                "envío nacional, ¿cuánto tarda?")) {
            Visitor visitor = api.startConversation(widgetKey, SITE);
            ask(visitor, wording);
            assertThat(routingReason(visitor, 1)).isEqualTo("learned-difficult");
        }

        api.getAs(owner, "/api/v1/difficult-questions").andExpect(jsonPath("$.items", hasSize(0)));
        api.getAs(owner, "/api/v1/difficult-questions?status=RESOLVED")
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].resolution").value("ANSWERED_WELL"));
        // Closed: the fast model tries it again, and a new sign of difficulty opens it again
        Visitor next = api.startConversation(widgetKey, SITE);
        String answerId = JsonPath.read(ask(next, "¿Cuánto tarda el envío nacional, por favor?").getLast().data(),
                "$.messageId");
        assertThat(routingReason(next, 1)).isEqualTo("simple");
        api.visitorPutJson(next, "/messages/" + answerId + "/feedback", "{\"helpful\":false}")
                .andExpect(status().isNoContent());
        api.getAs(owner, "/api/v1/difficult-questions")
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].occurrences").value(2))
                .andExpect(jsonPath("$.items[0].resolution").doesNotExist());
    }

    @Test
    void aQuestionNobodyFindsDifficultForThirtyDaysClosesByItself() throws Exception {
        markUnhelpful(QUESTION);
        String questionId = JsonPath.read(api.getAs(owner, "/api/v1/difficult-questions").andReturn().getResponse()
                .getContentAsString(), "$.items[0].id");
        clock.advance(Duration.ofDays(31));

        // Quiet for too long: it no longer costs the capable model, even before anybody opens the list
        Visitor visitor = api.startConversation(widgetKey, SITE);
        ask(visitor, "cuanto tarda envio nacional");
        assertThat(routingReason(visitor, 1)).isEqualTo("simple");
        api.getAs(owner, "/api/v1/difficult-questions").andExpect(jsonPath("$.items", hasSize(0)));
        api.getAs(owner, "/api/v1/difficult-questions?status=RESOLVED")
                .andExpect(jsonPath("$.items[0].resolution").value("QUIET"));

        // Opened again by the team, it is active again
        api.putJsonAs(owner, "/api/v1/difficult-questions/" + questionId + "/status", "{\"status\":\"OPEN\"}")
                .andExpect(status().isNoContent());
        api.getAs(owner, "/api/v1/difficult-questions").andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void theTeamGetsADraftOfTheAnswerFromTheDocuments() throws Exception {
        markUnhelpful(QUESTION);
        String questionId = JsonPath.read(api.getAs(owner, "/api/v1/difficult-questions").andReturn().getResponse()
                .getContentAsString(), "$.items[0].id");
        String path = "/api/v1/difficult-questions/" + questionId + "/draft";

        api.postJsonAs(api.newMember(owner, "AGENT"), path, "{}").andExpect(status().isForbidden());
        String draft = JsonPath.read(api.postJsonAs(owner, path, "{}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.answer");
        assertThat(draft).contains("3 a 5 días hábiles").doesNotContain("[1]");
        // Only a draft: the question stays open until the team teaches it
        api.getAs(owner, "/api/v1/difficult-questions").andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void theResultsShowWhatCustomersSaidOfEachModel() throws Exception {
        Visitor visitor = api.startConversation(widgetKey, SITE);
        String answerId = JsonPath.read(ask(visitor, QUESTION).getLast().data(), "$.messageId");
        api.visitorPutJson(visitor, "/messages/" + answerId + "/feedback", "{\"helpful\":false}");

        api.getAs(owner, "/api/v1/insights/outcomes")
                .andExpect(jsonPath("$.models[0].tier").value("SIMPLE"))
                .andExpect(jsonPath("$.models[0].reason").value("simple"))
                .andExpect(jsonPath("$.models[0].answers").value(1))
                .andExpect(jsonPath("$.models[0].unhelpful").value(1))
                .andExpect(jsonPath("$.models[0].unhelpfulRate").value(1.0))
                .andExpect(jsonPath("$.difficultQuestions").value(1));
    }

    private void markUnhelpful(String question) throws Exception {
        Visitor visitor = api.startConversation(widgetKey, SITE);
        String answerId = JsonPath.read(ask(visitor, question).getLast().data(), "$.messageId");
        api.visitorPutJson(visitor, "/messages/" + answerId + "/feedback", "{\"helpful\":false}")
                .andExpect(status().isNoContent());
    }

    private List<SseEvent> ask(Visitor visitor, String question) throws Exception {
        String stream = api.ask(visitor, question).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<SseEvent> events = ApiClient.events(stream);
        assertThat(events.getLast().name()).isEqualTo("done");
        return events;
    }

    private static String text(List<SseEvent> events) {
        return events.stream().filter(event -> event.name().equals("delta"))
                .map(event -> (String) JsonPath.read(event.data(), "$.text"))
                .reduce("", String::concat);
    }

    private String routingReason(Visitor visitor, int message) throws Exception {
        String body = api.getAs(owner, "/api/v1/conversations/" + visitor.conversationId())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.messages[" + message + "].usage.routingReason");
    }
}
