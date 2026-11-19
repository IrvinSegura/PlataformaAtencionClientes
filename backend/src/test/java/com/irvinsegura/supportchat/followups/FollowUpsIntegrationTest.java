package com.irvinsegura.supportchat.followups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Follow-ups to a customer who writes from WhatsApp, the channel with the most rules: the text goes out within 24 hours
 * of the customer's last message and only an approved template after that. The Graph API is a local stub.
 */
class FollowUpsIntegrationTest extends WithExternalServices {

    private static final String APP_SECRET = "app-secret-123";
    private static final String CUSTOMER = "5215552222";
    private static final String TEMPLATES = "/api/v1/follow-up-templates";
    private static final String TEMPLATE = ApiClient.json("{'name':'Pedido entregado',"
            + "'body':'Hola {nombre}, ¿te llegó tu pedido?','whatsappTemplate':'seguimiento_pedido',"
            + "'whatsappLanguage':'es_MX','whatsappNameParameter':true}");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FollowUpService followUpService;

    private ApiClient api;
    private Operator owner;
    private Operator agent;
    private String phoneNumberId;
    private String webhookPath;

    @BeforeEach
    void setUp() throws Exception {
        ExternalServices.on("POST", "/graph/*", request -> Response.json(200,
                "{\"messages\":[{\"id\":\"wamid.out-" + UUID.randomUUID() + "\"}]}"));
        api = new ApiClient(mockMvc);
        owner = api.newOperator("Acme");
        agent = api.newMember(owner, "AGENT");
        // Each test gets its own number, so the replies it waits for are its own
        phoneNumberId = String.valueOf(1_000_000_000L + (long) (Math.random() * 8_000_000_000L));
        String body = api.putJsonAs(owner, "/api/v1/channels/whatsapp", ApiClient.json(
                        "{'phoneNumberId':'%s','displayPhoneNumber':'+52 55 0000 0000','accessToken':'token-123',"
                                + "'appSecret':'%s','enabled':true}", phoneNumberId, APP_SECRET))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        webhookPath = JsonPath.read(body, "$.webhookPath");
    }

    @Test
    void changingOrDeletingATemplateKeepsWhatWasAlreadyScheduled() throws Exception {
        String templateId = createTemplate();
        String conversation = startConversation("wamid.t1");
        String followUps = "/api/v1/conversations/" + conversation + "/follow-ups";
        api.postJsonAs(agent, followUps, ApiClient.json("{'templateId':'%s','dueAt':'%s'}", templateId,
                Instant.now().plus(Duration.ofDays(1)))).andExpect(status().isCreated());

        String changed = ApiClient.json("{'name':'Pedido en camino','body':'Tu pedido va en camino'}");
        api.putJsonAs(agent, TEMPLATES + "/" + templateId, changed).andExpect(status().isForbidden());
        api.putJsonAs(owner, TEMPLATES + "/" + UUID.randomUUID(), changed).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FOLLOW_UP_TEMPLATE_NOT_FOUND"));
        api.putJsonAs(owner, TEMPLATES + "/" + templateId, changed).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pedido en camino"))
                .andExpect(jsonPath("$.whatsapp").doesNotExist());
        api.getAs(agent, followUps)
                .andExpect(jsonPath("$[0].templateName").value("Pedido en camino"))
                .andExpect(jsonPath("$[0].text").value("Hola {nombre}, ¿te llegó tu pedido?"))
                .andExpect(jsonPath("$[0].whatsappTemplate").value("seguimiento_pedido"));

        api.deleteAs(agent, TEMPLATES + "/" + templateId).andExpect(status().isForbidden());
        api.deleteAs(owner, TEMPLATES + "/" + templateId).andExpect(status().isNoContent());
        api.deleteAs(owner, TEMPLATES + "/" + templateId).andExpect(status().isNotFound());
        api.getAs(agent, TEMPLATES).andExpect(jsonPath("$.length()").value(0));
        api.getAs(agent, followUps)
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].templateId").doesNotExist())
                .andExpect(jsonPath("$[0].text").value("Hola {nombre}, ¿te llegó tu pedido?"));
    }

    @Test
    void aFollowUpJoinsTheCustomersNewConversation() throws Exception {
        String first = startConversation("wamid.n1");
        api.postAs(owner, "/api/v1/conversations/" + first + "/closure").andExpect(status().isOk());
        String followUps = "/api/v1/conversations/" + first + "/follow-ups";
        api.postJsonAs(agent, followUps, ApiClient.json("{'text':'¿Pudiste revisar tu pedido?','dueAt':'%s'}",
                Instant.now())).andExpect(status().isCreated());
        String second = startConversation("wamid.n2");
        assertThat(second).isNotEqualTo(first);

        assertThat(followUpService.sendDue(owner.workspaceId())).isEqualTo(1);

        awaitReply(body -> body.contains("¿Pudiste revisar tu pedido?"));
        api.getAs(agent, followUps)
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[0].conversationId").value(first))
                .andExpect(jsonPath("$[0].sentConversationId").value(second));
        api.getAs(agent, "/api/v1/conversations/" + second)
                .andExpect(jsonPath("$.messages[-1:].content")
                        .value(org.hamcrest.Matchers.contains("¿Pudiste revisar tu pedido?")));
    }

    @Test
    void after24HoursOfSilenceOnlyTheApprovedTemplateGoesOut() throws Exception {
        String templateId = createTemplate();
        String conversation = startConversation("wamid.s1");
        String followUps = "/api/v1/conversations/" + conversation + "/follow-ups";
        String now = Instant.now().toString();
        String fromTemplate = id(api.postJsonAs(agent, followUps, ApiClient.json(
                "{'templateId':'%s','dueAt':'%s'}", templateId, now)));
        String plainText = id(api.postJsonAs(agent, followUps, ApiClient.json(
                "{'text':'¿Algo más en que ayudarte?','dueAt':'%s'}", now)));
        // Two days later, the customer has not written again
        clock.advance(Duration.ofDays(2));

        assertThat(followUpService.sendDue(owner.workspaceId())).isEqualTo(2);

        Request sent = awaitReply(body -> body.contains("seguimiento_pedido"));
        assertThat((String) JsonPath.read(sent.body(), "$.type")).isEqualTo("template");
        assertThat((String) JsonPath.read(sent.body(), "$.template.language.code")).isEqualTo("es_MX");
        assertThat(sent.body()).contains("Lucía");
        String list = api.getAs(agent, followUps).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(list, "$[?(@.id == '" + fromTemplate + "')].status"))
                .containsExactly("SENT");
        assertThat((List<String>) JsonPath.read(list, "$[?(@.id == '" + plainText + "')].status"))
                .containsExactly("FAILED");
        assertThat((List<String>) JsonPath.read(list, "$[?(@.id == '" + plainText + "')].failure"))
                .containsExactly("WINDOW_CLOSED_NO_TEMPLATE");
        api.getAs(agent, "/api/v1/follow-ups?status=FAILED").andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void aFollowUpWhatsAppRefusesShowsAsFailed() throws Exception {
        ExternalServices.on("POST", "/graph/" + phoneNumberId + "/messages", request ->
                request.body().contains("Número dado de baja")
                        ? Response.json(400, "{\"error\":{\"message\":\"Recipient not allowed\",\"code\":131030}}")
                        : Response.json(200, "{\"messages\":[{\"id\":\"wamid.out-" + UUID.randomUUID() + "\"}]}"));
        String conversation = startConversation("wamid.r1");
        String followUps = "/api/v1/conversations/" + conversation + "/follow-ups";
        api.postJsonAs(agent, followUps, ApiClient.json("{'text':'Número dado de baja','dueAt':'%s'}",
                Instant.now())).andExpect(status().isCreated());

        assertThat(followUpService.sendDue(owner.workspaceId())).isEqualTo(1);

        awaitReply(body -> body.contains("Número dado de baja"));
        Eventually.assertEventually(Duration.ofSeconds(15), () -> api.getAs(agent, followUps)
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].failure").value("DELIVERY_REFUSED"))
                .andExpect(jsonPath("$[0].sentConversationId").value(conversation)));
    }

    @Test
    void aConversationOfAnotherWorkspaceIsNotFound() throws Exception {
        String conversation = startConversation("wamid.o1");
        Operator stranger = api.newOperator("Otra");
        String followUps = "/api/v1/conversations/" + conversation + "/follow-ups";

        api.getAs(stranger, followUps).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
        api.postJsonAs(stranger, followUps, ApiClient.json("{'text':'Hola','dueAt':'%s'}", Instant.now()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
        String followUp = id(api.postJsonAs(agent, followUps, ApiClient.json("{'text':'Hola','dueAt':'%s'}",
                Instant.now().plus(Duration.ofDays(1)))));
        api.postAs(stranger, "/api/v1/follow-ups/" + followUp + "/cancellation").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FOLLOW_UP_NOT_FOUND"));
    }

    private String createTemplate() throws Exception {
        return id(api.postJsonAs(owner, TEMPLATES, TEMPLATE).andExpect(status().isCreated()));
    }

    /**
     * The customer asks for a person, which needs no language model, and the id of the conversation that holds it.
     */
    private String startConversation(String messageId) throws Exception {
        List<String> before = conversationIds();
        deliver(messageId, "Quiero hablar con una persona").andExpect(status().isOk());
        String[] started = new String[1];
        Eventually.assertEventually(Duration.ofSeconds(15), () -> {
            List<String> now = conversationIds();
            assertThat(now).hasSizeGreaterThan(before.size());
            started[0] = now.stream().filter(id -> !before.contains(id)).findFirst().orElseThrow();
        });
        return started[0];
    }

    private List<String> conversationIds() throws Exception {
        return JsonPath.read(api.getAs(owner, "/api/v1/conversations").andReturn().getResponse()
                .getContentAsString(), "$.items[*].id");
    }

    private static String id(ResultActions created) throws Exception {
        return JsonPath.read(created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.id");
    }

    private ResultActions deliver(String messageId, String text) throws Exception {
        byte[] body = ApiClient.json("""
                {'object':'whatsapp_business_account','entry':[{'id':'1','changes':[{'field':'messages','value':{
                  'messaging_product':'whatsapp',
                  'metadata':{'display_phone_number':'5215550000','phone_number_id':'%s'},
                  'contacts':[{'profile':{'name':'Lucía'},'wa_id':'%s'}],
                  'messages':[{'from':'%s','id':'%s','timestamp':'1','type':'text','text':{'body':'%s'}}]
                }}]}]}""", phoneNumberId, CUSTOMER, CUSTOMER, messageId, text).getBytes(StandardCharsets.UTF_8);
        return mockMvc.perform(post(webhookPath).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Hub-Signature-256", WebhookSignature.sign(APP_SECRET, body)));
    }

    private Request awaitReply(Predicate<String> matches) throws Exception {
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
