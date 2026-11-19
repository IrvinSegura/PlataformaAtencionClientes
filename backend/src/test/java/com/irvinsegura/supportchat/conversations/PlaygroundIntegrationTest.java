package com.irvinsegura.supportchat.conversations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.ApiClient.SseEvent;
import com.irvinsegura.supportchat.support.Eventually;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * The team tries the assistant before customers do: the answer streams like a customer's, followed by what produced
 * it, and nothing is kept as a conversation. Uses the offline model, which quotes the best source.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PlaygroundIntegrationTest {

    private static final String SHIPPING = """
            # Envíos

            El envío nacional tarda de 3 a 5 días hábiles y es gratis en compras mayores a 999 pesos.
            """;

    @Autowired
    private MockMvc mockMvc;

    private ApiClient api;
    private Operator operator;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        operator = api.newOperator("Acme");
        api.uploadAndWaitUntilReady(operator, "envios.md", SHIPPING);
    }

    @Test
    void theAnswerStreamsAndTheInspectorShowsWhatProducedIt() throws Exception {
        String stream = ask(operator, "{\"question\":\"¿Cuánto tarda el envío nacional?\",\"model\":\"PRECISE\"}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<SseEvent> events = ApiClient.events(stream);
        String answer = events.stream().filter(event -> event.name().equals("delta"))
                .map(event -> (String) JsonPath.read(event.data(), "$.text"))
                .reduce("", String::concat);
        assertThat(answer).contains("3 a 5 días hábiles");
        assertThat(events.getLast().name()).isEqualTo("done");
        String done = events.getLast().data();
        assertThat((String) JsonPath.read(done, "$.outcome")).isEqualTo("ANSWERED");
        assertThat((String) JsonPath.read(done, "$.sources[0].documentTitle")).isEqualTo("envios");
        assertThat((Boolean) JsonPath.read(done, "$.sources[0].usedByModel")).isTrue();
        assertThat((String) JsonPath.read(done, "$.model.preference")).isEqualTo("PRECISE");
        assertThat((String) JsonPath.read(done, "$.model.tier")).isEqualTo("COMPLEX");
        assertThat((String) JsonPath.read(done, "$.model.reason")).isEqualTo("chosen-precise");
        assertThat((Integer) JsonPath.read(done, "$.model.modelCalls")).isEqualTo(1);
        assertThat((List<?>) JsonPath.read(done, "$.toolCalls")).isEmpty();
    }

    @Test
    void earlierTurnsOfTheTestConversationAreSentAlong() throws Exception {
        String stream = ask(operator, """
                {"question":"¿Y si compro más de 999 pesos?","model":"FAST",
                 "history":[{"fromCustomer":true,"text":"¿Cuánto tarda el envío nacional?"},
                            {"fromCustomer":false,"text":"De 3 a 5 días hábiles."}]}""")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String done = ApiClient.events(stream).getLast().data();
        assertThat((String) JsonPath.read(done, "$.model.reason")).isEqualTo("chosen-fast");
    }

    @Test
    void nothingIsSavedAsAConversationButTheAnswerCountsAgainstThePlan() throws Exception {
        ask(operator, "{\"question\":\"¿Cuánto tarda el envío nacional?\"}").andExpect(status().isOk());

        api.getAs(operator, "/api/v1/conversations").andExpect(jsonPath("$.total").value(0));
        Eventually.assertEventually(Duration.ofSeconds(10), () -> {
            String usage = api.getAs(operator, "/api/v1/usage").andReturn().getResponse().getContentAsString();
            assertThat((List<Integer>) JsonPath.read(usage,
                    "$.lines[?(@.metric == 'ANSWERS' && @.dimension == 'PLAYGROUND')].quantity"))
                    .containsExactly(1);
        });
    }

    @Test
    void aQuestionOutsideTheKnowledgeBaseIsNotGuessed() throws Exception {
        String stream = ask(operator, "{\"question\":\"¿Venden boletos de avión a Europa?\"}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String done = ApiClient.events(stream).getLast().data();
        assertThat((String) JsonPath.read(done, "$.outcome")).isEqualTo("NO_ANSWER");
    }

    @Test
    void anEmptyQuestionIsAnOrdinaryError() throws Exception {
        ask(operator, "{\"question\":\"\"}").andExpect(status().isBadRequest());
    }

    @Test
    void theModelChoicesListTheModelsBehindThem() throws Exception {
        api.getAs(operator, "/api/v1/assistant/models")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].preference").value("AUTOMATIC"))
                .andExpect(jsonPath("$[1].preference").value("FAST"))
                .andExpect(jsonPath("$[2].preference").value("PRECISE"))
                .andExpect(jsonPath("$[1].models[0]").isString());
    }

    @Test
    void onlyOwnersAndAdministratorsUseThePlayground() throws Exception {
        Operator agent = api.newMember(operator, "AGENT");

        ask(agent, "{\"question\":\"Hola\"}").andExpect(status().isForbidden());
        api.getAs(agent, "/api/v1/assistant/models").andExpect(status().isForbidden());
    }

    private ResultActions ask(Operator who, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/playground/answers")
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content(body));
    }
}
