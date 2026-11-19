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
 * Replies that cost no model call: a fixed courtesy reply to a greeting or a thanks, and the answer the model already
 * wrote to the same question, given again while nothing it depended on changed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FreeAnswersIntegrationTest {

    private static final String SITE = "https://shop.example.com";
    private static final String QUESTION = "¿Cuánto tarda el envío nacional?";
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
    void aGreetingGetsAFixedReplyWithoutAModelOrAKnowledgeGap() throws Exception {
        Visitor visitor = api.startConversation(widgetKey, SITE);

        List<SseEvent> events = ask(visitor, "¡Hola!");

        assertThat(text(events)).isEqualTo("¡Hola! 👋 ¿En qué te puedo ayudar?");
        assertThat((String) JsonPath.read(events.getLast().data(), "$.outcome")).isEqualTo("SMALL_TALK");
        assertThat(text(ask(visitor, "Muchas gracias 🙏"))).isEqualTo("¡Con gusto! Si necesitas algo más, aquí estoy.");
        api.getAs(operator, "/api/v1/conversations/" + visitor.conversationId())
                .andExpect(jsonPath("$.messages[1].outcome").value("SMALL_TALK"))
                .andExpect(jsonPath("$.messages[1].usage").doesNotExist());
        api.getAs(operator, "/api/v1/knowledge-gaps").andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void theSameQuestionIsAnsweredAgainWithoutAModelCall() throws Exception {
        Visitor first = api.startConversation(widgetKey, SITE);
        String written = text(ask(first, QUESTION));

        Visitor second = api.startConversation(widgetKey, SITE);
        // A greeting first still leaves the question standing on its own
        ask(second, "Buenas tardes");
        List<SseEvent> events = ask(second, "cuanto tarda el envio nacional");

        assertThat(text(events)).isEqualTo(written);
        assertThat((String) JsonPath.read(events.getLast().data(), "$.outcome")).isEqualTo("ANSWERED");
        assertThat((String) JsonPath.read(events.getLast().data(), "$.citations[0].documentTitle"))
                .isEqualTo("envios");
        api.getAs(operator, "/api/v1/conversations/" + second.conversationId())
                .andExpect(jsonPath("$.messages[3].usage.routingReason").value("reused"))
                .andExpect(jsonPath("$.messages[3].usage.inputTokens").value(0))
                .andExpect(jsonPath("$.messages[3].usage.outputTokens").value(0));
    }

    @Test
    void aChangeToTheDocumentsStopsTheOldAnswer() throws Exception {
        ask(api.startConversation(widgetKey, SITE), QUESTION);
        api.uploadAndWaitUntilReady(operator, "devoluciones.md", "# Devoluciones\n\nTienes 30 días para devolver.");

        Visitor visitor = api.startConversation(widgetKey, SITE);
        ask(visitor, QUESTION);

        assertThat(routingReason(visitor, 1)).isNotEqualTo("reused");
    }

    @Test
    void aFollowUpQuestionIsAnsweredForItsConversation() throws Exception {
        ask(api.startConversation(widgetKey, SITE), QUESTION);

        Visitor visitor = api.startConversation(widgetKey, SITE);
        ask(visitor, "¿El envío es gratis?");
        ask(visitor, QUESTION);

        assertThat(routingReason(visitor, 3)).isNotEqualTo("reused");
    }

    @Test
    void anAnswerACustomerSaidDidNotHelpIsNotGivenAgain() throws Exception {
        Visitor first = api.startConversation(widgetKey, SITE);
        String answerId = JsonPath.read(ask(first, QUESTION).getLast().data(), "$.messageId");
        api.visitorPutJson(first, "/messages/" + answerId + "/feedback", "{\"helpful\":false}")
                .andExpect(status().isNoContent());

        Visitor second = api.startConversation(widgetKey, SITE);
        ask(second, QUESTION);

        assertThat(routingReason(second, 1)).isNotEqualTo("reused");
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
        String body = api.getAs(operator, "/api/v1/conversations/" + visitor.conversationId())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.messages[" + message + "].usage.routingReason");
    }
}
