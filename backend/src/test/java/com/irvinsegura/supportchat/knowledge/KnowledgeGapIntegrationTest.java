package com.irvinsegura.supportchat.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.Eventually;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Questions the assistant could not answer are grouped by wording so the team knows what to document next.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class KnowledgeGapIntegrationTest {

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
        api.uploadAndWaitUntilReady(owner, "envios.md", "# Envíos\n\nEl envío nacional tarda de 3 a 5 días hábiles.");
    }

    @Test
    void theSameQuestionInDifferentWordingIsOneGap() throws Exception {
        api.ask(api.startConversation(widgetKey, SITE), "¿Aceptan pagos con Bitcoin?").andExpect(status().isOk());
        api.ask(api.startConversation(widgetKey, SITE), "aceptan pagos con bitcoin").andExpect(status().isOk());

        Eventually.assertEventually(Duration.ofSeconds(10), () -> api.getAs(owner, "/api/v1/knowledge-gaps")
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].occurrences").value(2)));
    }

    @Test
    void aResolvedGapLeavesTheOpenListAndOtherWorkspacesNeverSeeIt() throws Exception {
        api.ask(api.startConversation(widgetKey, SITE), "¿Tienen tienda física en Monterrey?")
                .andExpect(status().isOk());
        Eventually.assertEventually(Duration.ofSeconds(10), () -> api.getAs(owner, "/api/v1/knowledge-gaps")
                .andExpect(jsonPath("$.items", hasSize(1))));
        String gapId = JsonPath.read(api.getAs(owner, "/api/v1/knowledge-gaps").andReturn().getResponse()
                .getContentAsString(), "$.items[0].id");

        api.getAs(api.newOperator("Other"), "/api/v1/knowledge-gaps").andExpect(jsonPath("$.items", hasSize(0)));
        api.putJsonAs(api.newMember(owner, "AGENT"), "/api/v1/knowledge-gaps/" + gapId + "/status",
                "{\"status\":\"RESOLVED\"}").andExpect(status().isForbidden());

        api.putJsonAs(owner, "/api/v1/knowledge-gaps/" + gapId + "/status", "{\"status\":\"RESOLVED\"}")
                .andExpect(status().isNoContent());
        api.getAs(owner, "/api/v1/knowledge-gaps").andExpect(jsonPath("$.items", hasSize(0)));
        String resolved = api.getAs(owner, "/api/v1/knowledge-gaps?status=RESOLVED").andReturn().getResponse()
                .getContentAsString();
        assertThat((String) JsonPath.read(resolved, "$.items[0].question")).contains("Monterrey");
    }
}
