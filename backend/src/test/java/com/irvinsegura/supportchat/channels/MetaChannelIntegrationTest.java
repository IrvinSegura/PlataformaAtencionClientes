package com.irvinsegura.supportchat.channels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.channels.domain.model.WebhookSignature;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.Eventually;
import com.irvinsegura.supportchat.support.ExternalServices;
import com.irvinsegura.supportchat.support.ExternalServices.Request;
import com.irvinsegura.supportchat.support.ExternalServices.Response;
import com.irvinsegura.supportchat.support.WithExternalServices;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * A customer writes to the business's Facebook Page or Instagram account: Meta's signed delivery reaches the webhook,
 * the assistant answers from the knowledge base and the reply goes out through the Send API, here a local stub.
 */
class MetaChannelIntegrationTest extends WithExternalServices {

    private static final String APP_SECRET = "meta-app-secret-123";
    private static final String SHIPPING = """
            # Envíos

            El envío nacional tarda de 3 a 5 días hábiles y es gratis en compras mayores a 999 pesos.
            """;

    @Autowired
    private MockMvc mockMvc;

    private ApiClient api;
    private Operator operator;
    // Each test gets its own Page and customer, so the replies it waits for are its own
    private String pageId;
    private String customer;
    private String webhookPath;
    private String verifyToken;

    @BeforeEach
    void setUp() throws Exception {
        ExternalServices.on("GET", "/meta/*", request -> Response.json(200, "{\"name\":\"Lucía Pérez\"}"));
        api = new ApiClient(mockMvc);
        operator = api.newOperator("Acme");
        pageId = randomId();
        customer = randomId();
        String body = connect("messenger", pageId);
        webhookPath = JsonPath.read(body, "$.webhookPath");
        verifyToken = JsonPath.read(body, "$.verifyToken");
    }

    @Test
    void metaVerifiesTheSubscriptionWithTheVerifyToken() throws Exception {
        assertThat(webhookPath).startsWith("/api/v1/channels/messenger/").endsWith("/webhook");
        mockMvc.perform(get(webhookPath).param("hub.mode", "subscribe").param("hub.verify_token", verifyToken)
                        .param("hub.challenge", "challenge-42"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge-42"));
        mockMvc.perform(get(webhookPath).param("hub.mode", "subscribe").param("hub.verify_token", "wrong")
                        .param("hub.challenge", "challenge-42"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aCustomersQuestionIsAnsweredOnMessenger() throws Exception {
        api.uploadAndWaitUntilReady(operator, "envios.md", SHIPPING);

        deliver(webhookPath, message("page", pageId, "m.a1", "¿Cuánto tarda el envío nacional?"))
                .andExpect(status().isOk());

        Request reply = awaitReply(body -> body.contains("3 a 5 días hábiles"));
        assertThat(reply.authorization()).isEqualTo("Bearer token-123");
        assertThat((String) JsonPath.read(reply.body(), "$.messaging_type")).isEqualTo("RESPONSE");
        assertThat((String) JsonPath.read(reply.body(), "$.message.text")).contains("Fuentes: [1] envios")
                .doesNotContain("*");

        String conversations = api.getAs(operator, "/api/v1/conversations")
                .andExpect(jsonPath("$.total").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(conversations, "$.items[0].channel")).isEqualTo("MESSENGER");
        assertThat((String) JsonPath.read(conversations, "$.items[0].contactId")).isEqualTo(customer);
        assertThat((String) JsonPath.read(conversations, "$.items[0].contactName")).isEqualTo("Lucía Pérez");
    }

    @Test
    void instagramHasItsOwnConnectionAndWebhook() throws Exception {
        String accountId = randomId();
        String body = connect("instagram", accountId);
        String instagramWebhook = JsonPath.read(body, "$.webhookPath");
        assertThat(instagramWebhook).startsWith("/api/v1/channels/instagram/");
        api.getAs(operator, "/api/v1/channels/instagram").andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.platform").value("INSTAGRAM"));

        // A Messenger delivery sent to the Instagram webhook is not Instagram's
        deliver(instagramWebhook, message("page", accountId, "m.i0", "Hola")).andExpect(status().isOk());
        deliver(instagramWebhook, message("instagram", accountId, "m.i1", "Quiero hablar con una persona"))
                .andExpect(status().isOk());

        awaitReply(text -> text.contains("Te comunicaremos con una persona"));
        String conversations = api.getAs(operator, "/api/v1/conversations?handling=WAITING")
                .andExpect(jsonPath("$.total").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(conversations, "$.items[0].channel")).isEqualTo("INSTAGRAM");
        api.getAs(operator, "/api/v1/conversations").andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void followUpMessagesJoinTheSameConversationAndRepeatedDeliveriesAreIgnored() throws Exception {
        String first = message("page", pageId, "m.b1", "Hola");
        deliver(webhookPath, first).andExpect(status().isOk());
        deliver(webhookPath, first).andExpect(status().isOk());
        deliver(webhookPath, message("page", pageId, "m.b2", "¿Venden boletos de avión?")).andExpect(status().isOk());

        Eventually.assertEventually(Duration.ofSeconds(15), () -> api.getAs(operator, "/api/v1/conversations")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].questionCount").value(2)));
    }

    @Test
    void messagesForAnotherPageOfTheSameAppAreIgnored() throws Exception {
        deliver(webhookPath, message("page", randomId(), "m.c1", "Hola")).andExpect(status().isOk());

        api.getAs(operator, "/api/v1/conversations").andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void aDeliveryWithoutMetasSignatureIsRefused() throws Exception {
        byte[] body = message("page", pageId, "m.d1", "Hola").getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post(webhookPath).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(webhookPath).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-Hub-Signature-256", WebhookSignature.sign("not-the-secret", body)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/channels/messenger/unknown-key/webhook").contentType(MediaType.APPLICATION_JSON)
                        .content(body).header("X-Hub-Signature-256", WebhookSignature.sign(APP_SECRET, body)))
                .andExpect(status().isNotFound());
        api.getAs(operator, "/api/v1/conversations").andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void onlyOwnersAndAdministratorsSeeTheConnection() throws Exception {
        Operator agent = api.newMember(operator, "AGENT");

        api.getAs(agent, "/api/v1/channels/messenger").andExpect(status().isForbidden());
        api.getAs(operator, "/api/v1/channels/messenger").andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        api.getAs(operator, "/api/v1/channels/instagram").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("META_CHANNEL_NOT_FOUND"));
        api.putJsonAs(operator, "/api/v1/channels/messenger", ApiClient.json(
                        "{'accountId':'not-a-page','accessToken':'','appSecret':'','enabled':true}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_META_CHANNEL"));
    }

    private String connect(String platform, String accountId) throws Exception {
        return api.putJsonAs(operator, "/api/v1/channels/" + platform, ApiClient.json(
                        "{'accountId':'%s','displayName':'Tienda Acme','accessToken':'token-123','appSecret':'%s',"
                                + "'enabled':true}", accountId, APP_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.appSecret").doesNotExist())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions deliver(String path, String payload) throws Exception {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Hub-Signature-256", WebhookSignature.sign(APP_SECRET, body)));
    }

    private String message(String object, String accountId, String messageId, String text) {
        return ApiClient.json("""
                {'object':'%s','entry':[{'id':'%s','time':1,'messaging':[
                  {'sender':{'id':'%s'},'recipient':{'id':'%s'},'timestamp':1,'message':{'mid':'%s','text':'%s'}}
                ]}]}""", object, accountId, customer, accountId, messageId, text);
    }

    private Request awaitReply(Predicate<String> matches) throws Exception {
        Request[] found = new Request[1];
        Eventually.assertEventually(Duration.ofSeconds(15), () -> {
            found[0] = ExternalServices.requests(sent -> sent.path().equals("/meta/me/messages")
                            && sent.body().contains("\"" + customer + "\""))
                    .stream()
                    .filter(sent -> matches.test(sent.body()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No such reply yet"));
        });
        return found[0];
    }

    private static String randomId() {
        return String.valueOf(100_000_000_000L + (long) (Math.random() * 800_000_000_000L));
    }
}
