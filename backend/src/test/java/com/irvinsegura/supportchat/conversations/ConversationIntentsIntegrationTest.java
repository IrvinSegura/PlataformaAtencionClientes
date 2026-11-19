package com.irvinsegura.supportchat.conversations;

import static com.irvinsegura.supportchat.support.ApiClient.json;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.ApiClient.Visitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Each conversation is tagged with what the customer writes about, the team filters by those topics and corrects them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ConversationIntentsIntegrationTest {

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
    void conversationsAreTaggedFilteredAndCorrectedByTheTeam() throws Exception {
        Visitor shipping = api.startConversation(widgetKey, SITE);
        api.ask(shipping, "¿Cuánto cuesta el envío a Monterrey?").andExpect(status().isOk());
        Visitor returns = api.startConversation(widgetKey, SITE);
        api.ask(returns, "Quiero devolver un producto").andExpect(status().isOk());
        Visitor greeting = api.startConversation(widgetKey, SITE);
        api.ask(greeting, "Hola").andExpect(status().isOk());

        String conversation = "/api/v1/conversations/" + shipping.conversationId();
        api.getAs(owner, conversation)
                .andExpect(jsonPath("$.conversation.intents", contains("SHIPPING", "PRICING")))
                .andExpect(jsonPath("$.conversation.intentsEdited").value(false));
        api.getAs(owner, "/api/v1/conversations?intent=SHIPPING")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(shipping.conversationId().toString()));
        api.getAs(owner, "/api/v1/conversations?intent=RETURNS")
                .andExpect(jsonPath("$.items[0].id").value(returns.conversationId().toString()));
        api.getAs(owner, "/api/v1/conversations?intent=ACCOUNT").andExpect(jsonPath("$.total").value(0));
        api.getAs(owner, "/api/v1/conversations?intent=WEATHER").andExpect(status().isBadRequest());

        // The team's correction stands: later messages add no topic
        Operator agent = api.newMember(owner, "AGENT");
        api.putJsonAs(agent, conversation + "/intents", json("{'intents':['SHIPPING']}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intents", contains("SHIPPING")))
                .andExpect(jsonPath("$.intentsEdited").value(true));
        api.ask(shipping, "¿Y aceptan pagos con tarjeta?").andExpect(status().isOk());
        api.getAs(owner, conversation).andExpect(jsonPath("$.conversation.intents", contains("SHIPPING")));
        api.putJsonAs(agent, conversation + "/intents", json("{'intents':[]}"))
                .andExpect(jsonPath("$.intents", empty()));
        api.putJsonAs(agent, conversation + "/intents", json("{'intents':['WEATHER']}"))
                .andExpect(status().isBadRequest());

        // Another workspace cannot touch them
        Operator stranger = api.newOperator("Otra");
        api.putJsonAs(stranger, conversation + "/intents", json("{'intents':['RETURNS']}"))
                .andExpect(status().isNotFound());
    }
}
