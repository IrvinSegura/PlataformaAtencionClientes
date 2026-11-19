package com.irvinsegura.supportchat.channels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.channels.domain.model.WebhookSignature;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.ApiClient.SseEvent;
import com.irvinsegura.supportchat.support.ApiClient.Visitor;
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
 * Customers who ask for a person or seem upset: the conversation goes to the team and the owner gets a WhatsApp
 * from the business's number, here sent to a local stub of the Graph API.
 */
class AttentionAlertsIntegrationTest extends WithExternalServices {

    private static final String APP_SECRET = "app-secret-123";
    private static final String SETTINGS = "/api/v1/assistant/attention";

    @Autowired
    private MockMvc mockMvc;

    private ApiClient api;
    private Operator owner;
    private String phoneNumberId;
    private String webhookPath;
    private String ownerPhone;
    private String refusedPhone;

    @BeforeEach
    void setUp() throws Exception {
        // Each test gets its own numbers, so the messages it waits for are its own
        ownerPhone = randomPhone();
        refusedPhone = randomPhone();
        phoneNumberId = randomPhone();
        String refused = refusedPhone;
        ExternalServices.on("POST", "/graph/*", request -> request.body().contains("\"to\":\"" + refused + "\"")
                ? Response.json(400, "{\"error\":{\"message\":\"Re-engagement message\",\"code\":131047}}")
                : Response.json(200, "{\"messages\":[{\"id\":\"wamid.out-" + UUID.randomUUID() + "\"}]}"));
        api = new ApiClient(mockMvc);
        owner = api.newOperator("Acme");
        String body = api.putJsonAs(owner, "/api/v1/channels/whatsapp", ApiClient.json(
                        "{'phoneNumberId':'%s','accessToken':'token-123','appSecret':'%s','enabled':true}",
                        phoneNumberId, APP_SECRET))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        webhookPath = JsonPath.read(body, "$.webhookPath");
    }

    @Test
    void settingsStartWithTheDefaultsAndOnlyManagersChangeThem() throws Exception {
        api.getAs(owner, SETTINGS).andExpect(status().isOk())
                .andExpect(jsonPath("$.handOffFrustrated").value(true))
                .andExpect(jsonPath("$.alertsEnabled").value(false))
                .andExpect(jsonPath("$.alertPhones").isEmpty())
                .andExpect(jsonPath("$.alertOnRequest").value(true))
                .andExpect(jsonPath("$.alertOnFrustration").value(true));

        api.putJsonAs(owner, SETTINGS, settings(true, true, "[]", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ATTENTION_SETTINGS"));
        api.putJsonAs(owner, SETTINGS, settings(false, true, "['+52 55 1234 5678']", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertPhones[0]").value("525512345678"));
        api.getAs(owner, SETTINGS).andExpect(jsonPath("$.handOffFrustrated").value(false));

        Operator agent = api.newMember(owner, "AGENT");
        api.getAs(agent, SETTINGS).andExpect(status().isForbidden());
        api.getAs(agent, SETTINGS + "/alerts").andExpect(status().isForbidden());
    }

    @Test
    void theOwnerIsAlertedWhenACustomerAsksForAPerson() throws Exception {
        api.putJsonAs(owner, SETTINGS, settings(true, true, "['" + ownerPhone + "']", null))
                .andExpect(status().isOk());

        deliver(text("wamid.p1", "5215552222", "Quiero hablar con una persona")).andExpect(status().isOk());

        Request alert = awaitMessageTo(ownerPhone, "pidió hablar con una persona");
        String text = JsonPath.read(alert.body(), "$.text.body");
        assertThat(text).contains("Lucía (WhatsApp +5215552222)", "“Quiero hablar con una persona”",
                "http://localhost:5173/conversations/");
        Eventually.assertEventually(Duration.ofSeconds(10), () -> api.getAs(owner, SETTINGS + "/alerts")
                .andExpect(jsonPath("$[0].reason").value("ASKED_FOR_PERSON"))
                .andExpect(jsonPath("$[0].channel").value("WHATSAPP"))
                .andExpect(jsonPath("$[0].contactName").value("Lucía"))
                .andExpect(jsonPath("$[0].deliveries[0].recipient").value(ownerPhone))
                .andExpect(jsonPath("$[0].deliveries[0].status").value("SENT")));
    }

    @Test
    void anUpsetCustomerGoesToTheTeamAndTheOwnerIsAlertedOnce() throws Exception {
        api.putJsonAs(owner, SETTINGS, settings(true, true, "['" + ownerPhone + "']", null))
                .andExpect(status().isOk());

        deliver(text("wamid.f1", "5215553333", "Esto no sirve para nada, pésimo servicio"))
                .andExpect(status().isOk());

        awaitMessageTo(ownerPhone, "parece molesto");
        awaitMessageTo("5215553333", "Te comunicaremos con una persona");
        String waiting = api.getAs(owner, "/api/v1/conversations?handling=WAITING")
                .andExpect(jsonPath("$.total").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(waiting, "$.items[0].frustratedAt")).isNotNull();
        String conversation = "/api/v1/conversations/" + JsonPath.read(waiting, "$.items[0].id");

        // Still upset: the team already knows, and nobody is woken twice
        deliver(text("wamid.f2", "5215553333", "En serio, son unos ladrones!!!")).andExpect(status().isOk());
        Eventually.assertEventually(Duration.ofSeconds(10), () -> {
            String detail = api.getAs(owner, conversation).andReturn().getResponse().getContentAsString();
            assertThat((List<Object>) JsonPath.read(detail, "$.messages[?(@.author == 'VISITOR')]")).hasSize(2);
        });
        Thread.sleep(1000);
        assertThat(messagesTo(ownerPhone)).hasSize(1);
        api.getAs(owner, SETTINGS + "/alerts").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reason").value("FRUSTRATED"))
                .andExpect(jsonPath("$[0].excerpt").value("Esto no sirve para nada, pésimo servicio"));
    }

    @Test
    void withoutHandingOffAnUpsetCustomerStaysWithTheAssistantButTheOwnerIsTold() throws Exception {
        api.putJsonAs(owner, SETTINGS, settings(false, true, "['" + ownerPhone + "']", null))
                .andExpect(status().isOk());

        deliver(text("wamid.g1", "5215554444", "Pésimo, mi pedido no llega")).andExpect(status().isOk());

        awaitMessageTo(ownerPhone, "parece molesto");
        // The assistant answers it, here with its honest "I don't know"
        awaitMessageTo("5215554444", "No tengo información suficiente");
        api.getAs(owner, "/api/v1/conversations?handling=WAITING").andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void aNumberOutsideTheWindowIsReportedAndATemplateIsSentInstead() throws Exception {
        api.putJsonAs(owner, SETTINGS, settings(true, true, "['" + ownerPhone + "','" + refusedPhone + "']", null))
                .andExpect(status().isOk());

        deliver(text("wamid.t1", "5215555555", "Quiero hablar con un asesor")).andExpect(status().isOk());

        Eventually.assertEventually(Duration.ofSeconds(15), () -> {
            String alerts = api.getAs(owner, SETTINGS + "/alerts").andReturn().getResponse().getContentAsString();
            assertThat((List<String>) JsonPath.read(alerts, "$[0].deliveries[?(@.recipient == '" + ownerPhone
                    + "')].status")).containsExactly("SENT");
            assertThat((List<String>) JsonPath.read(alerts, "$[0].deliveries[?(@.recipient == '" + refusedPhone
                    + "')].status")).containsExactly("REJECTED");
            assertThat((List<String>) JsonPath.read(alerts, "$[0].deliveries[?(@.recipient == '" + refusedPhone
                    + "')].detail")).singleElement().asString().contains("24 horas");
        });

        api.putJsonAs(owner, SETTINGS, settings(true, true, "['" + ownerPhone + "']", "alerta_cliente"))
                .andExpect(status().isOk());
        deliver(text("wamid.t2", "5215556666", "quiero hablar con una persona")).andExpect(status().isOk());

        Request template = awaitRequest(sent -> sent.body().contains("\"type\":\"template\""));
        assertThat((String) JsonPath.read(template.body(), "$.to")).isEqualTo(ownerPhone);
        assertThat((String) JsonPath.read(template.body(), "$.template.name")).isEqualTo("alerta_cliente");
        assertThat((String) JsonPath.read(template.body(), "$.template.language.code")).isEqualTo("es_MX");
        assertThat((List<String>) JsonPath.read(template.body(), "$.template.components[0].parameters[*].text"))
                .hasSize(3)
                .contains("Lucía (WhatsApp +5215556666)", "pidió hablar con una persona");
    }

    @Test
    void anUpsetWebVisitorIsForwardedAndPressingTheButtonAlertsToo() throws Exception {
        api.putJsonAs(owner, SETTINGS, settings(true, true, "['" + ownerPhone + "']", null))
                .andExpect(status().isOk());
        String widgetKey = api.enableWidget(owner, "https://acme.example");

        Visitor upset = api.startConversation(widgetKey, "https://acme.example");
        List<SseEvent> events = ApiClient.events(api.ask(upset, "ESTO ES UNA ESTAFA!!!")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(events).extracting(SseEvent::name).containsExactly("forwarded");
        awaitMessageTo(ownerPhone, "Un visitante del sitio web parece molesto");

        Visitor calm = api.startConversation(widgetKey, "https://acme.example");
        api.visitorPost(calm, "/handoff").andExpect(status().isOk());
        awaitMessageTo(ownerPhone, "Un visitante del sitio web pidió hablar con una persona");
    }

    @Test
    void theOwnerIsToldOnceWhenADifficultQuestionKeepsComingBack() throws Exception {
        api.putJsonAs(owner, SETTINGS, settings(true, true, "['" + ownerPhone + "']", null))
                .andExpect(status().isOk());
        String site = "https://acme.example";
        String widgetKey = api.enableWidget(owner, site);
        api.uploadAndWaitUntilReady(owner, "envios.md", """
                # Envíos

                El envío nacional tarda de 3 a 5 días hábiles.
                """);

        for (int customer = 1; customer <= 5; customer++) {
            markUnhelpful(widgetKey, site, "¿Cuánto tarda el envío nacional?");
        }

        Request alert = awaitMessageTo(ownerPhone, "Una pregunta difícil se repite");
        assertThat((String) JsonPath.read(alert.body(), "$.text.body")).contains("5 veces",
                "“¿Cuánto tarda el envío nacional?”", "http://localhost:5173/knowledge/difficult");
        api.getAs(owner, "/api/v1/difficult-questions").andExpect(jsonPath("$.items[0].alertedAt").isNotEmpty());

        // Once per question: the sixth customer wakes nobody
        markUnhelpful(widgetKey, site, "¿Cuánto tarda el envío nacional?");
        Thread.sleep(1000);
        assertThat(messagesTo(ownerPhone)).filteredOn(sent -> sent.body().contains("pregunta difícil")).hasSize(1);
    }

    @Test
    void noDifficultQuestionAlertWhenTheOwnerTurnedItOff() throws Exception {
        api.putJsonAs(owner, SETTINGS, ApiClient.json("{'handOffFrustrated':true,'alertsEnabled':true,"
                        + "'alertPhones':['%s'],'alertOnRequest':true,'alertOnFrustration':true,"
                        + "'alertOnDifficult':false}", ownerPhone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertOnDifficult").value(false));
        String site = "https://acme.example";
        String widgetKey = api.enableWidget(owner, site);
        api.uploadAndWaitUntilReady(owner, "envios.md", "# Envíos\n\nEl envío nacional tarda de 3 a 5 días.\n");

        for (int customer = 1; customer <= 5; customer++) {
            markUnhelpful(widgetKey, site, "¿Cuánto tarda el envío nacional?");
        }

        Thread.sleep(1000);
        assertThat(messagesTo(ownerPhone)).isEmpty();
        api.getAs(owner, "/api/v1/difficult-questions")
                .andExpect(jsonPath("$.items[0].occurrences").value(5))
                .andExpect(jsonPath("$.items[0].alertedAt").doesNotExist());
    }

    private void markUnhelpful(String widgetKey, String site, String question) throws Exception {
        Visitor visitor = api.startConversation(widgetKey, site);
        List<SseEvent> events = ApiClient.events(api.ask(visitor, question).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String answerId = JsonPath.read(events.getLast().data(), "$.messageId");
        api.visitorPutJson(visitor, "/messages/" + answerId + "/feedback", "{\"helpful\":false}")
                .andExpect(status().isNoContent());
    }

    private String settings(boolean handOff, boolean enabled, String phones, String template) {
        return ApiClient.json("{'handOffFrustrated':%s,'alertsEnabled':%s,'alertPhones':%s,'alertOnRequest':true,"
                        + "'alertOnFrustration':true,'template':%s,'templateLanguage':%s}", handOff, enabled, phones,
                template == null ? "null" : "'" + template + "'", template == null ? "null" : "'es_MX'");
    }

    private ResultActions deliver(String payload) throws Exception {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        return mockMvc.perform(post(webhookPath).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Hub-Signature-256", WebhookSignature.sign(APP_SECRET, body)));
    }

    private String text(String messageId, String customer, String text) {
        return ApiClient.json("""
                {'object':'whatsapp_business_account','entry':[{'id':'1','changes':[{'field':'messages','value':{
                  'messaging_product':'whatsapp',
                  'metadata':{'display_phone_number':'5215550000','phone_number_id':'%s'},
                  'contacts':[{'profile':{'name':'Lucía'},'wa_id':'%s'}],
                  'messages':[{'from':'%s','id':'%s-%s','timestamp':'1','type':'text','text':{'body':'%s'}}]
                }}]}]}""", phoneNumberId, customer, customer, messageId, phoneNumberId, text);
    }

    private List<Request> messagesTo(String phone) {
        return ExternalServices.requests(sent -> sent.path().equals("/graph/" + phoneNumberId + "/messages")
                && phone.equals(JsonPath.read(sent.body(), "$.to")));
    }

    private Request awaitMessageTo(String phone, String containing) throws Exception {
        return awaitRequest(sent -> phone.equals(JsonPath.read(sent.body(), "$.to"))
                && sent.body().contains(containing));
    }

    private Request awaitRequest(java.util.function.Predicate<Request> matches) throws Exception {
        Request[] found = new Request[1];
        Eventually.assertEventually(Duration.ofSeconds(15), () -> {
            found[0] = ExternalServices.requests(sent -> sent.path().equals("/graph/" + phoneNumberId + "/messages"))
                    .stream()
                    .filter(matches)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No such message yet"));
        });
        return found[0];
    }

    private static String randomPhone() {
        return String.valueOf(1_000_000_000L + (long) (Math.random() * 8_000_000_000L));
    }
}
