package com.irvinsegura.supportchat.conversations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.ApiClient.SseEvent;
import com.irvinsegura.supportchat.support.ApiClient.Visitor;
import com.irvinsegura.supportchat.support.Eventually;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * Customers say whether answers helped and rate their conversations; the team sees how many conversations the
 * assistant solved alone, and which documents to write for what it could not answer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OutcomesIntegrationTest {

    private static final String SITE = "https://shop.example.com";
    private static final String SHIPPING = "# Envíos\n\nEl envío nacional tarda de 3 a 5 días hábiles.";

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
    void customersJudgeAnswersAndRateTheConversation() throws Exception {
        api.uploadAndWaitUntilReady(owner, "envios.md", SHIPPING);
        Visitor visitor = api.startConversation(widgetKey, SITE);
        String answerId = answer(visitor, "¿Cuánto tarda el envío nacional?");

        api.visitorPutJson(visitor, "/messages/" + answerId + "/feedback", "{\"helpful\":false}")
                .andExpect(status().isNoContent());
        // Changing one's mind replaces the verdict
        api.visitorPutJson(visitor, "/messages/" + answerId + "/feedback", "{\"helpful\":true}")
                .andExpect(status().isNoContent());
        api.visitorPutJson(visitor, "/rating", "{\"score\":5,\"comment\":\"  Muy rápido  \"}")
                .andExpect(status().isNoContent());

        api.visitorHistory(visitor)
                .andExpect(jsonPath("$[1].id").value(answerId))
                .andExpect(jsonPath("$[1].helpful").value(true))
                .andExpect(jsonPath("$[0].helpful").doesNotExist());
        api.getAs(owner, "/api/v1/conversations/" + visitor.conversationId())
                .andExpect(jsonPath("$.messages[1].helpful").value(true))
                .andExpect(jsonPath("$.rating.score").value(5))
                .andExpect(jsonPath("$.rating.comment").value("Muy rápido"));
    }

    @Test
    void onlyTheAssistantsAnswersOfTheVisitorsOwnConversationCanBeJudged() throws Exception {
        Visitor visitor = api.startConversation(widgetKey, SITE);
        answer(visitor, "Hola");
        String questionId = JsonPath.read(api.visitorHistory(visitor).andReturn().getResponse().getContentAsString(),
                "$[0].id");
        Visitor other = api.startConversation(widgetKey, SITE);
        String otherAnswer = answer(other, "Hola otra vez");

        api.visitorPutJson(visitor, "/messages/" + questionId + "/feedback", "{\"helpful\":true}")
                .andExpect(status().isNotFound());
        api.visitorPutJson(visitor, "/messages/" + otherAnswer + "/feedback", "{\"helpful\":true}")
                .andExpect(status().isNotFound());
        api.visitorPutJson(visitor, "/messages/" + UUID.randomUUID() + "/feedback", "{\"helpful\":true}")
                .andExpect(status().isNotFound());
        api.visitorPutJson(visitor, "/rating", "{\"score\":6}").andExpect(status().isBadRequest());
        api.visitorPutJson(visitor, "/rating", "{\"score\":4,\"comment\":\"" + "a".repeat(1001) + "\"}")
                .andExpect(status().isBadRequest());
        Visitor impostor = new Visitor(widgetKey, SITE, visitor.conversationId(), other.token());
        api.visitorPutJson(impostor, "/rating", "{\"score\":1}").andExpect(status().isUnauthorized());
    }

    @Test
    void theReportCountsWhatTheAssistantSolvedAloneAndWhatCustomersSaid() throws Exception {
        api.uploadAndWaitUntilReady(owner, "envios.md", SHIPPING);
        // Solved: answered, judged helpful, rated 4
        Visitor solved = api.startConversation(widgetKey, SITE);
        String solvedAnswer = answer(solved, "¿Cuánto tarda el envío nacional?");
        api.visitorPutJson(solved, "/messages/" + solvedAnswer + "/feedback", "{\"helpful\":true}")
                .andExpect(status().isNoContent());
        api.visitorPutJson(solved, "/rating", "{\"score\":4,\"comment\":\"Bien\"}").andExpect(status().isNoContent());
        // Answered, but the customer rated it 2
        Visitor disappointed = api.startConversation(widgetKey, SITE);
        answer(disappointed, "¿Cuánto tarda el envío nacional?");
        api.visitorPutJson(disappointed, "/rating", "{\"score\":2}").andExpect(status().isNoContent());
        // The assistant did not know
        answer(api.startConversation(widgetKey, SITE), "¿Venden boletos de avión?");
        // A person took part
        Visitor handedOff = api.startConversation(widgetKey, SITE);
        answer(handedOff, "¿Cuánto tarda el envío nacional?");
        api.visitorPost(handedOff, "/handoff").andExpect(status().isOk());
        // Started but nothing asked: not counted
        api.startConversation(widgetKey, SITE);

        String today = LocalDate.now(ZoneOffset.UTC).toString();
        api.getAs(owner, "/api/v1/insights/outcomes")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to").value(today))
                .andExpect(jsonPath("$.conversations").value(4))
                .andExpect(jsonPath("$.resolvedByAssistant").value(1))
                .andExpect(jsonPath("$.handedOff").value(1))
                .andExpect(jsonPath("$.unresolved").value(2))
                .andExpect(jsonPath("$.resolutionRate").value(closeTo(0.25, 0.001)))
                .andExpect(jsonPath("$.answered").value(3))
                .andExpect(jsonPath("$.notAnswered").value(1))
                .andExpect(jsonPath("$.helpful").value(1))
                .andExpect(jsonPath("$.helpfulRate").value(closeTo(1.0, 0.001)))
                .andExpect(jsonPath("$.ratings").value(2))
                .andExpect(jsonPath("$.averageRating").value(closeTo(3.0, 0.001)))
                .andExpect(jsonPath("$.satisfaction").value(closeTo(0.5, 0.001)))
                .andExpect(jsonPath("$.ratingCounts[1]").value(1))
                .andExpect(jsonPath("$.ratingCounts[3]").value(1))
                .andExpect(jsonPath("$.days", hasSize(1)))
                .andExpect(jsonPath("$.days[0].day").value(today))
                .andExpect(jsonPath("$.days[0].conversations").value(4))
                .andExpect(jsonPath("$.comments", hasSize(1)))
                .andExpect(jsonPath("$.comments[0].comment").value("Bien"));

        // Another workspace sees none of it
        api.getAs(api.newOperator("Other"), "/api/v1/insights/outcomes")
                .andExpect(jsonPath("$.conversations").value(0))
                .andExpect(jsonPath("$.resolutionRate").doesNotExist());
        // A period entirely in the past has nothing
        api.getAs(owner, "/api/v1/insights/outcomes?from=2020-01-01&to=2020-01-31")
                .andExpect(jsonPath("$.conversations").value(0));
    }

    @Test
    void onlyManagersSeeTheReportAndPeriodsAreChecked() throws Exception {
        api.getAs(api.newMember(owner, "AGENT"), "/api/v1/insights/outcomes").andExpect(status().isForbidden());
        api.getAs(api.newMember(owner, "ADMIN"), "/api/v1/insights/outcomes").andExpect(status().isOk());
        api.getAs(owner, "/api/v1/insights/outcomes?from=2026-02-01&to=2026-01-01")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
        api.getAs(owner, "/api/v1/insights/outcomes?from=2024-01-01&to=2026-01-01")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void unansweredQuestionsOnOneTopicBecomeOneSuggestedDocument() throws Exception {
        answer(api.startConversation(widgetKey, SITE), "¿Hacen envíos a Colombia?");
        answer(api.startConversation(widgetKey, SITE), "¿Hacen envíos a Colombia?");
        answer(api.startConversation(widgetKey, SITE), "¿Envían a Colombia?");
        answer(api.startConversation(widgetKey, SITE), "¿Aceptan pagos con PayPal?");
        Eventually.assertEventually(Duration.ofSeconds(10), () -> api.getAs(owner, "/api/v1/knowledge-gaps")
                .andExpect(jsonPath("$.total").value(3)));

        String body = api.getAs(owner, "/api/v1/knowledge-gaps/suggestions")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("¿Hacen envíos a Colombia?"))
                .andExpect(jsonPath("$[0].occurrences").value(3))
                .andExpect(jsonPath("$[1].title").value("¿Aceptan pagos con PayPal?"))
                .andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(body, "$[0].questions[*].question"))
                .containsExactly("¿Hacen envíos a Colombia?", "¿Envían a Colombia?");

        api.getAs(api.newOperator("Other"), "/api/v1/knowledge-gaps/suggestions").andExpect(jsonPath("$", hasSize(0)));
    }

    /**
     * Asks and returns the id of the assistant's answer.
     */
    private String answer(Visitor visitor, String question) throws Exception {
        List<SseEvent> events = ApiClient.events(api.ask(visitor, question).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(events.getLast().name()).isEqualTo("done");
        return JsonPath.read(events.getLast().data(), "$.messageId");
    }
}
