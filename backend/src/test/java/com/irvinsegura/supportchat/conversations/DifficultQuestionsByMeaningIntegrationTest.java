package com.irvinsegura.supportchat.conversations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Two wordings that share too few words are still one difficult question when they mean the same. The offline
 * embedding model of the tests sees only shared words and characters, so the bar is lowered to where its scores tell
 * these wordings apart: 0.625 for the two that ask the same, 0.596 for "¿El envío nacional es gratis?".
 */
@SpringBootTest(properties = "supportchat.conversations.difficult-questions.same-question-similarity=0.61")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DifficultQuestionsByMeaningIntegrationTest {

    private static final String SITE = "https://shop.example.com";
    // Three of their seven words in common: too few to be the same question by words alone
    private static final String QUESTION = "¿Cuánto tarda en llegar el envío nacional?";
    private static final String REWORDED = "¿En cuánto tiempo llega el envío nacional?";

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
        api.uploadAndWaitUntilReady(owner, "envios.md", """
                # Envíos

                El envío nacional tarda de 3 a 5 días hábiles y es gratis en compras mayores a 999 pesos.
                """);
    }

    @Test
    void theSameQuestionInOtherWordsGoesToTheCapableModelAndCountsOnTheSameQuestion() throws Exception {
        markUnhelpful(QUESTION);

        Visitor other = api.startConversation(widgetKey, SITE);
        String answerId = JsonPath.read(ask(other, REWORDED).getLast().data(), "$.messageId");
        assertThat(routingReason(other)).isEqualTo("learned-difficult");
        api.visitorPutJson(other, "/messages/" + answerId + "/feedback", "{\"helpful\":false}")
                .andExpect(status().isNoContent());

        api.getAs(owner, "/api/v1/difficult-questions")
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].occurrences").value(2))
                .andExpect(jsonPath("$.items[0].question").value(REWORDED));
    }

    @Test
    void aDifferentQuestionOnTheSameTopicStaysApart() throws Exception {
        markUnhelpful(QUESTION);
        markUnhelpful("¿El envío nacional es gratis?");

        api.getAs(owner, "/api/v1/difficult-questions").andExpect(jsonPath("$.items", hasSize(2)));
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

    private String routingReason(Visitor visitor) throws Exception {
        String body = api.getAs(owner, "/api/v1/conversations/" + visitor.conversationId())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.messages[1].usage.routingReason");
    }
}
