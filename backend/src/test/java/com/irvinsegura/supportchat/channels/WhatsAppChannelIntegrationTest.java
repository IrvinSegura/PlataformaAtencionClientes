package com.irvinsegura.supportchat.channels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.channels.domain.model.WebhookSignature;
import com.irvinsegura.supportchat.followups.application.FollowUpService;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * A customer writes to the business's WhatsApp number: Meta's signed delivery reaches the webhook, the assistant
 * answers from the knowledge base and the reply goes out through the Graph API, here a local stub.
 */
class WhatsAppChannelIntegrationTest extends WithExternalServices {

    private static final String APP_SECRET = "app-secret-123";
    private static final String CUSTOMER = "5215551111";
    private static final String SHIPPING = """
            # Envíos

            El envío nacional tarda de 3 a 5 días hábiles y es gratis en compras mayores a 999 pesos.
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FollowUpService followUpService;

    private ApiClient api;
    private Operator operator;
    private String phoneNumberId;
    private String webhookPath;
    private String verifyToken;

    @BeforeEach
    void setUp() throws Exception {
        ExternalServices.on("POST", "/graph/*", request -> Response.json(200,
                "{\"messages\":[{\"id\":\"wamid.out-" + UUID.randomUUID() + "\"}]}"));
        api = new ApiClient(mockMvc);
        operator = api.newOperator("Acme");
        // Each test gets its own number, so the replies it waits for are its own
        phoneNumberId = String.valueOf(1_000_000_000L + (long) (Math.random() * 8_000_000_000L));
        String body = api.putJsonAs(operator, "/api/v1/channels/whatsapp", ApiClient.json(
                        "{'phoneNumberId':'%s','displayPhoneNumber':'+52 55 0000 0000','accessToken':'token-123',"
                                + "'appSecret':'%s','enabled':true}", phoneNumberId, APP_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.appSecret").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        webhookPath = JsonPath.read(body, "$.webhookPath");
        verifyToken = JsonPath.read(body, "$.verifyToken");
    }

    @Test
    void metaVerifiesTheSubscriptionWithTheVerifyToken() throws Exception {
        mockMvc.perform(get(webhookPath).param("hub.mode", "subscribe").param("hub.verify_token", verifyToken)
                        .param("hub.challenge", "challenge-42"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge-42"));
        mockMvc.perform(get(webhookPath).param("hub.mode", "subscribe").param("hub.verify_token", "wrong")
                        .param("hub.challenge", "challenge-42"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aCustomersQuestionIsAnsweredOnWhatsApp() throws Exception {
        api.uploadAndWaitUntilReady(operator, "envios.md", SHIPPING);

        deliver(text("wamid.a1", "¿Cuánto tarda el envío nacional?")).andExpect(status().isOk());

        Request reply = awaitReply(body -> body.contains("3 a 5 días hábiles"));
        assertThat(reply.authorization()).isEqualTo("Bearer token-123");
        assertThat((String) JsonPath.read(reply.body(), "$.to")).isEqualTo(CUSTOMER);
        assertThat((String) JsonPath.read(reply.body(), "$.text.body")).contains("Fuentes: [1] envios");

        String conversations = api.getAs(operator, "/api/v1/conversations")
                .andExpect(jsonPath("$.total").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(conversations, "$.items[0].channel")).isEqualTo("WHATSAPP");
        assertThat((String) JsonPath.read(conversations, "$.items[0].contactId")).isEqualTo(CUSTOMER);
        assertThat((String) JsonPath.read(conversations, "$.items[0].contactName")).isEqualTo("Lucía");

        Eventually.assertEventually(Duration.ofSeconds(10), () -> {
            String usage = api.getAs(operator, "/api/v1/usage").andReturn().getResponse().getContentAsString();
            assertThat((List<Integer>) JsonPath.read(usage,
                    "$.lines[?(@.metric == 'WHATSAPP_MESSAGES' && @.dimension == 'inbound')].quantity"))
                    .containsExactly(1);
            assertThat((List<Integer>) JsonPath.read(usage,
                    "$.lines[?(@.metric == 'WHATSAPP_MESSAGES' && @.dimension == 'outbound')].quantity"))
                    .containsExactly(1);
            assertThat((List<Integer>) JsonPath.read(usage,
                    "$.lines[?(@.metric == 'ANSWERS' && @.dimension == 'WHATSAPP')].quantity")).containsExactly(1);
        });
    }

    @Test
    void followUpMessagesJoinTheSameConversationAndRepeatedDeliveriesAreIgnored() throws Exception {
        String first = text("wamid.b1", "Hola");
        deliver(first).andExpect(status().isOk());
        deliver(first).andExpect(status().isOk());
        deliver(text("wamid.b2", "¿Venden boletos de avión?")).andExpect(status().isOk());

        Eventually.assertEventually(Duration.ofSeconds(15), () -> api.getAs(operator, "/api/v1/conversations")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].questionCount").value(2)));
    }

    @Test
    void askingForAPersonHandsTheConversationToTheTeam() throws Exception {
        deliver(text("wamid.c1", "Quiero hablar con una persona")).andExpect(status().isOk());

        awaitReply(body -> body.contains("Te comunicaremos con una persona"));
        api.getAs(operator, "/api/v1/conversations?handling=WAITING").andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void aCustomerWritingInEnglishGetsTheFixedRepliesAndNoticesInEnglish() throws Exception {
        deliver(text("wamid.e1", "Do you sell plane tickets?")).andExpect(status().isOk());
        awaitReply(body -> body.contains("I don't have enough information to answer that."));

        deliver(text("wamid.e2", "I want to talk to a person")).andExpect(status().isOk());
        awaitReply(body -> body.contains("We'll put you in touch with someone from the team."));
        api.getAs(operator, "/api/v1/conversations").andExpect(jsonPath("$.items[0].language").value("en"));
    }

    @Test
    void aDeliveryWithoutMetasSignatureIsRefused() throws Exception {
        byte[] body = text("wamid.d1", "Hola").getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post(webhookPath).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(webhookPath).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-Hub-Signature-256", WebhookSignature.sign("not-the-secret", body)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/channels/whatsapp/unknown-key/webhook").contentType(MediaType.APPLICATION_JSON)
                        .content(body).header("X-Hub-Signature-256", WebhookSignature.sign(APP_SECRET, body)))
                .andExpect(status().isNotFound());
        api.getAs(operator, "/api/v1/conversations").andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void onlyOwnersAndAdministratorsSeeTheConnection() throws Exception {
        Operator agent = api.newMember(operator, "AGENT");

        api.getAs(agent, "/api/v1/channels/whatsapp").andExpect(status().isForbidden());
    }

    @Test
    void theNumberAnswersWithTheModelTheBusinessChose() throws Exception {
        api.getAs(operator, "/api/v1/channels/whatsapp").andExpect(jsonPath("$.model").value("AUTOMATIC"));

        api.putJsonAs(operator, "/api/v1/channels/whatsapp", ApiClient.json(
                        "{'phoneNumberId':'%s','enabled':true,'model':'PRECISE'}", phoneNumberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("PRECISE"));
        // A client that leaves the model out keeps the saved choice
        api.putJsonAs(operator, "/api/v1/channels/whatsapp", ApiClient.json(
                        "{'phoneNumberId':'%s','enabled':true}", phoneNumberId))
                .andExpect(jsonPath("$.model").value("PRECISE"));
        api.putJsonAs(operator, "/api/v1/channels/whatsapp", ApiClient.json(
                        "{'phoneNumberId':'%s','enabled':true,'model':'SMARTEST'}", phoneNumberId))
                .andExpect(status().isBadRequest());

        api.uploadAndWaitUntilReady(operator, "envios.md", SHIPPING);
        deliver(text("wamid.m1", "¿Cuánto tarda el envío nacional?")).andExpect(status().isOk());

        // A short, direct question would go to the fast tier if the platform picked
        awaitReply(body -> body.contains("3 a 5 días hábiles"));
        String conversations = api.getAs(operator, "/api/v1/conversations")
                .andReturn().getResponse().getContentAsString();
        String conversationId = JsonPath.read(conversations, "$.items[0].id");
        api.getAs(operator, "/api/v1/conversations/" + conversationId)
                .andExpect(jsonPath("$.messages[1].usage.tier").value("COMPLEX"))
                .andExpect(jsonPath("$.messages[1].usage.routingReason").value("chosen-precise"));
    }

    @Test
    void aReturningCustomerIsRecognizedAndTheTeamKeepsNotesOnThem() throws Exception {
        api.uploadAndWaitUntilReady(operator, "envios.md", SHIPPING);
        deliver(text("wamid.p1", "¿Cuánto tarda el envío nacional?")).andExpect(status().isOk());
        awaitReply(body -> body.contains("3 a 5 días hábiles"));
        String first = latestConversationId();
        api.postAs(operator, "/api/v1/conversations/" + first + "/closure").andExpect(status().isOk());

        // The same question again, in a new conversation: the answer kept for others is not given to someone the
        // assistant remembers, since theirs may draw on what they said before
        deliver(text("wamid.p2", "¿Cuánto tarda el envío nacional?")).andExpect(status().isOk());
        Eventually.assertEventually(Duration.ofSeconds(15), () -> api.getAs(operator, "/api/v1/conversations")
                .andExpect(jsonPath("$.total").value(2)));
        String second = latestConversationId();
        Eventually.assertEventually(Duration.ofSeconds(15), () -> api.getAs(operator,
                        "/api/v1/conversations/" + second)
                .andExpect(jsonPath("$.messages[1].author").value("ASSISTANT"))
                .andExpect(jsonPath("$.messages[1].usage.routingReason").value(
                        org.hamcrest.Matchers.not("reused"))));

        String customer = "/api/v1/conversations/" + second + "/customer";
        api.getAs(operator, customer).andExpect(status().isOk())
                .andExpect(jsonPath("$.contactId").value(CUSTOMER))
                .andExpect(jsonPath("$.contactName").value("Lucía"))
                .andExpect(jsonPath("$.conversations").value(2))
                .andExpect(jsonPath("$.remember").value(true))
                .andExpect(jsonPath("$.history[0].id").value(second))
                .andExpect(jsonPath("$.history[1].id").value(first));

        api.putJsonAs(operator, customer, ApiClient.json(
                        "{'displayName':'Lucía Pérez','notes':'Prefiere entregas por la tarde'}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Lucía Pérez"))
                .andExpect(jsonPath("$.notes").value("Prefiere entregas por la tarde"))
                .andExpect(jsonPath("$.updatedByName").value("Operator"));
        // Leaving the choice out keeps it; the customer can ask not to be remembered
        api.putJsonAs(operator, customer, ApiClient.json("{'notes':'x','remember':false}"))
                .andExpect(jsonPath("$.remember").value(false));
        api.putJsonAs(operator, customer, ApiClient.json("{'notes':'x'}"))
                .andExpect(jsonPath("$.remember").value(false))
                .andExpect(jsonPath("$.displayName").doesNotExist());
        api.putJsonAs(operator, customer, ApiClient.json("{'notes':'%s'}", "x".repeat(4001)))
                .andExpect(status().isBadRequest());

        api.getAs(operator, "/api/v1/customers?query=5551111")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].conversations").value(2))
                .andExpect(jsonPath("$.items[0].latestConversationId").value(second));
        api.getAs(operator, "/api/v1/customers?query=nadie").andExpect(jsonPath("$.total").value(0));
        // Another workspace sees none of it
        Operator stranger = api.newOperator("Otra");
        api.getAs(stranger, customer).andExpect(status().isNotFound());
        api.getAs(stranger, "/api/v1/customers").andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void aFollowUpFromATemplateReachesTheCustomerWhenItsMomentComes() throws Exception {
        Operator agent = api.newMember(operator, "AGENT");
        String template = ApiClient.json("{'name':'Pedido entregado','body':'Hola {nombre}, ¿te llegó tu pedido?',"
                + "'whatsappTemplate':'seguimiento_pedido','whatsappLanguage':'es_MX','whatsappNameParameter':true}");
        api.postJsonAs(agent, "/api/v1/follow-up-templates", template).andExpect(status().isForbidden());
        String templateId = JsonPath.read(api.postJsonAs(operator, "/api/v1/follow-up-templates", template)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.whatsapp.name").value("seguimiento_pedido"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        api.postJsonAs(operator, "/api/v1/follow-up-templates", template).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLLOW_UP_TEMPLATE_NAME_TAKEN"));
        api.getAs(agent, "/api/v1/follow-up-templates").andExpect(jsonPath("$[0].name").value("Pedido entregado"));

        deliver(text("wamid.f1", "Quiero hablar con una persona")).andExpect(status().isOk());
        awaitReply(body -> body.contains("Te comunicaremos con una persona"));
        String conversation = latestConversationId();
        String followUps = "/api/v1/conversations/" + conversation + "/follow-ups";
        String now = java.time.Instant.now().toString();
        String later = java.time.Instant.now().plus(Duration.ofDays(2)).toString();

        String scheduled = api.postJsonAs(agent, followUps, ApiClient.json("{'templateId':'%s','dueAt':'%s'}",
                        templateId, now))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.templateName").value("Pedido entregado"))
                .andExpect(jsonPath("$.createdByName").value("Laura"))
                .andReturn().getResponse().getContentAsString();
        String cancelled = JsonPath.read(api.postJsonAs(agent, followUps, ApiClient.json(
                        "{'text':'¿Algo más en que ayudarte?','dueAt':'%s'}", later))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        api.postAs(agent, "/api/v1/follow-ups/" + cancelled + "/cancellation").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        api.postAs(agent, "/api/v1/follow-ups/" + cancelled + "/cancellation").andExpect(status().isConflict());
        api.postJsonAs(agent, followUps, ApiClient.json("{'text':'Hola','dueAt':'2020-01-01T00:00:00Z'}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_FOLLOW_UP"));

        assertThat(followUpService.sendDue(operator.workspaceId())).isEqualTo(1);

        // The customer wrote a moment ago, so the text goes out as is, with their name, as the member's reply
        awaitReply(body -> body.contains("Hola Lucía, ¿te llegó tu pedido?"));
        api.getAs(agent, followUps)
                .andExpect(jsonPath("$[0].id").value((String) JsonPath.read(scheduled, "$.id")))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[0].sentConversationId").value(conversation))
                .andExpect(jsonPath("$[1].status").value("CANCELLED"));
        api.getAs(agent, "/api/v1/conversations/" + conversation)
                .andExpect(jsonPath("$.messages[-1:].author").value(org.hamcrest.Matchers.contains("AGENT")))
                .andExpect(jsonPath("$.messages[-1:].content").value(
                        org.hamcrest.Matchers.contains("Hola Lucía, ¿te llegó tu pedido?")));
        api.getAs(agent, "/api/v1/follow-ups?status=SENT").andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].contactId").value(CUSTOMER));
        // Nothing is sent twice
        assertThat(followUpService.sendDue(operator.workspaceId())).isZero();

        Operator stranger = api.newOperator("Otra");
        api.getAs(stranger, followUps).andExpect(status().isNotFound());
        api.getAs(stranger, "/api/v1/follow-ups").andExpect(jsonPath("$.total").value(0));
    }

    private String latestConversationId() throws Exception {
        return JsonPath.read(api.getAs(operator, "/api/v1/conversations").andReturn().getResponse()
                .getContentAsString(), "$.items[0].id");
    }

    private ResultActions deliver(String payload) throws Exception {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        return mockMvc.perform(post(webhookPath).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Hub-Signature-256", WebhookSignature.sign(APP_SECRET, body)));
    }

    private String text(String messageId, String text) {
        return ApiClient.json("""
                {'object':'whatsapp_business_account','entry':[{'id':'1','changes':[{'field':'messages','value':{
                  'messaging_product':'whatsapp',
                  'metadata':{'display_phone_number':'5215550000','phone_number_id':'%s'},
                  'contacts':[{'profile':{'name':'Lucía'},'wa_id':'%s'}],
                  'messages':[{'from':'%s','id':'%s','timestamp':'1','type':'text','text':{'body':'%s'}}]
                }}]}]}""", phoneNumberId, CUSTOMER, CUSTOMER, messageId, text);
    }

    private Request awaitReply(java.util.function.Predicate<String> matches) throws Exception {
        Request[] found = new Request[1];
        Eventually.assertEventually(Duration.ofSeconds(15), () -> {
            found[0] = ExternalServices.requests(sent -> sent.path().equals("/graph/" + phoneNumberId + "/messages"))
                    .stream()
                    .filter(sent -> matches.test(sent.body()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No such reply yet"));
        });
        return found[0];
    }
}
