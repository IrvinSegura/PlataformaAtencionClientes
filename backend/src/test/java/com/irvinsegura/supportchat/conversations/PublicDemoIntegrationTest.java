package com.irvinsegura.supportchat.conversations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import com.irvinsegura.supportchat.conversations.domain.port.ChatModel;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.ApiClient.Visitor;
import com.irvinsegura.supportchat.workspaces.DemoWorkspace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The public demo answers anyone on the platform's account: always with the economical model, and only so many times
 * a day before it hands the rest to the team, as a plan at its limit does.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PublicDemoIntegrationTest {

    private static final String SITE = "https://demo.example.com";

    @Autowired
    private MockMvc mockMvc;

    // The demo workspace is configured by id, which is only known once the test creates it
    @MockitoBean
    private DemoWorkspace demo;

    @MockitoSpyBean
    private ChatModel chatModel;

    @Test
    void theDemoAnswersWithTheEconomicalModelUntilItsDailyAnswersRunOut() throws Exception {
        ApiClient api = new ApiClient(mockMvc);
        Operator owner = api.newOperator("Café Aurora");
        when(demo.is(owner.workspaceId())).thenReturn(true);
        when(demo.dailyAnswers()).thenReturn(1);
        api.uploadAndWaitUntilReady(owner, "envios.md", """
                # Envíos
                Enviamos café en grano a todo México. El envío cuesta 99 pesos y es gratis en compras desde 600 pesos.
                Los pedidos llegan en 2 a 4 días hábiles.
                """);
        String widgetKey = api.enableWidget(owner, SITE);

        Visitor first = api.startConversation(widgetKey, SITE);
        api.ask(first, "¿Cuánto cuesta el envío y cuántos días tarda en llegar mi pedido?").andExpect(status().isOk());
        verify(chatModel, atLeastOnce()).stream(any(), eq(ModelTier.SIMPLE), any());
        verify(chatModel, never()).stream(any(), eq(ModelTier.COMPLEX), any());
        api.getAs(owner, "/api/v1/conversations?handling=WAITING").andExpect(jsonPath("$.total").value(0));

        // The day's answers are used up: the next visitor waits for the team instead
        Visitor second = api.startConversation(widgetKey, SITE);
        api.ask(second, "¿Hacen envíos a Monterrey?").andExpect(status().isOk());
        api.getAs(owner, "/api/v1/conversations?handling=WAITING")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(second.conversationId().toString()));
    }
}
