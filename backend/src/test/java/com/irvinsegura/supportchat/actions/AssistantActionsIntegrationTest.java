package com.irvinsegura.supportchat.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.ApiClient.SseEvent;
import com.irvinsegura.supportchat.support.ApiClient.Visitor;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The assistant runs the business's webhooks: lookups at once, changes only after the visitor confirms. The offline
 * model calls an action when the question names it and contains its values, so the journeys are predictable; the
 * business is a local HTTP server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AssistantActionsIntegrationTest {

    private static final String SITE = "https://shop.example.com";

    @Autowired
    private MockMvc mockMvc;

    private final Queue<Received> received = new ConcurrentLinkedQueue<>();
    private final AtomicReference<String> reply = new AtomicReference<>("{\"status\":\"Enviado ayer\"}");
    private HttpServer business;
    private ApiClient api;
    private Operator operator;
    private String widgetKey;

    @BeforeEach
    void setUp() throws Exception {
        business = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        business.createContext("/", exchange -> {
            received.add(new Received(exchange.getRequestURI().getPath(),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    exchange.getRequestHeaders().getFirst("X-SupportChat-Signature")));
            byte[] body = reply.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        business.start();
        api = new ApiClient(mockMvc);
        operator = api.newOperator("Acme");
        widgetKey = api.enableWidget(operator, SITE);
    }

    @AfterEach
    void stopBusiness() {
        business.stop(0);
    }

    @Test
    void aLookupRunsAtOnceWithASignedRequestAndIsBilled() throws Exception {
        createAction("order_status", "Consultar un pedido", "Consulta el estado de un pedido por su número", false);
        Visitor visitor = api.startConversation(widgetKey, SITE);

        List<SseEvent> events = ApiClient.events(api.ask(visitor, "¿Cuál es el estado de mi pedido 123456?")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(text(events)).contains("Enviado ayer");
        assertThat((String) JsonPath.read(events.getLast().data(), "$.outcome")).isEqualTo("ANSWERED");
        assertThat(received).hasSize(1);
        Received call = received.peek();
        assertThat(call.path()).isEqualTo("/order_status");
        assertThat((String) JsonPath.read(call.body(), "$.action")).isEqualTo("order_status");
        assertThat((String) JsonPath.read(call.body(), "$.arguments.order_number")).isEqualTo("123456");
        assertThat((String) JsonPath.read(call.body(), "$.channel")).isEqualTo("WEB");
        assertThat(validSignature(call)).isTrue();

        String detail = api.getAs(operator, "/api/v1/conversations/" + visitor.conversationId())
                .andExpect(jsonPath("$.actionRuns[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.messages[1].usage.tier").value("SIMPLE"))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(detail, "$.messages[1].actionRunId"))
                .isEqualTo(JsonPath.read(detail, "$.actionRuns[0].id"));

        String usage = api.getAs(operator, "/api/v1/usage").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((List<Integer>) JsonPath.read(usage,
                "$.lines[?(@.metric == 'ACTION_RUNS' && @.dimension == 'order_status')].quantity")).containsExactly(1);
        assertThat((List<Integer>) JsonPath.read(usage,
                "$.lines[?(@.metric == 'ANSWERS' && @.dimension == 'WEB')].quantity")).containsExactly(1);
    }

    @Test
    void aChangeWaitsForTheVisitorToConfirmIt() throws Exception {
        createAction("cancel_order", "Cancelar un pedido", "Cancela un pedido por su número", true);
        reply.set("{\"message\":\"Tu pedido 123456 fue cancelado.\"}");
        Visitor visitor = api.startConversation(widgetKey, SITE);

        List<SseEvent> events = ApiClient.events(api.ask(visitor, "Quiero cancelar mi pedido 123456")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        String done = events.getLast().data();
        String runId = JsonPath.read(done, "$.pendingAction.runId");
        assertThat((String) JsonPath.read(done, "$.pendingAction.title")).isEqualTo("Cancelar un pedido");
        assertThat((String) JsonPath.read(done, "$.pendingAction.arguments.order_number")).isEqualTo("123456");
        assertThat(received).isEmpty();
        // A reload draws the buttons again
        api.visitorHistory(visitor).andExpect(jsonPath("$[1].pendingAction.runId").value(runId));

        api.visitorPost(visitor, "/actions/" + runId + "/confirmation")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("ASSISTANT"))
                .andExpect(jsonPath("$.content").value("Tu pedido 123456 fue cancelado."));
        assertThat(received).hasSize(1);
        assertThat(validSignature(received.peek())).isTrue();

        api.visitorPost(visitor, "/actions/" + runId + "/confirmation")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTION_ALREADY_HANDLED"));
        api.visitorHistory(visitor).andExpect(jsonPath("$[1].pendingAction").doesNotExist());
    }

    @Test
    void aCancelledChangeIsNeverSent() throws Exception {
        createAction("cancel_order", "Cancelar un pedido", "Cancela un pedido por su número", true);
        Visitor visitor = api.startConversation(widgetKey, SITE);
        String done = ApiClient.events(api.ask(visitor, "Quiero cancelar mi pedido 123456")
                .andReturn().getResponse().getContentAsString()).getLast().data();
        String runId = JsonPath.read(done, "$.pendingAction.runId");

        api.visitorPost(visitor, "/actions/" + runId + "/cancellation")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("De acuerdo, no hice ningún cambio."));

        assertThat(received).isEmpty();
        api.getAs(operator, "/api/v1/conversations/" + visitor.conversationId())
                .andExpect(jsonPath("$.actionRuns[0].status").value("CANCELLED"));
    }

    @Test
    void anotherVisitorCannotConfirmTheChange() throws Exception {
        createAction("cancel_order", "Cancelar un pedido", "Cancela un pedido por su número", true);
        Visitor visitor = api.startConversation(widgetKey, SITE);
        String done = ApiClient.events(api.ask(visitor, "Quiero cancelar mi pedido 123456")
                .andReturn().getResponse().getContentAsString()).getLast().data();
        String runId = JsonPath.read(done, "$.pendingAction.runId");
        Visitor other = api.startConversation(widgetKey, SITE);

        api.visitorPost(other, "/actions/" + runId + "/confirmation").andExpect(status().isNotFound());

        assertThat(received).isEmpty();
    }

    @Test
    void onlyOwnersAndAdministratorsManageActions() throws Exception {
        Operator agent = api.newMember(operator, "AGENT");

        api.postJsonAs(agent, "/api/v1/actions", actionJson("order_status", "Consultar", "Consulta", false))
                .andExpect(status().isForbidden());
        api.getAs(agent, "/api/v1/actions/signing-secret").andExpect(status().isForbidden());
        api.getAs(agent, "/api/v1/actions").andExpect(status().isOk());
    }

    private void createAction(String name, String title, String description, boolean confirm) throws Exception {
        api.postJsonAs(operator, "/api/v1/actions", actionJson(name, title, description, confirm))
                .andExpect(status().isCreated());
    }

    private String actionJson(String name, String title, String description, boolean confirm) {
        return ApiClient.json("""
                {'name':'%s','title':'%s','description':'%s','endpointUrl':'http://127.0.0.1:%d/%s',
                 'parameters':[{'name':'order_number','type':'STRING','description':'Número de pedido',
                                'required':true,'pattern':'\\\\d{5,10}'}],
                 'requiresConfirmation':%s,'enabled':true}""",
                name, title, description, business.getAddress().getPort(), name, confirm);
    }

    private boolean validSignature(Received call) throws Exception {
        String secret = JsonPath.read(api.getAs(operator, "/api/v1/actions/signing-secret")
                .andReturn().getResponse().getContentAsString(), "$.value");
        String timestamp = call.signature().replaceAll("^t=([^,]+),.*$", "$1");
        String given = call.signature().replaceAll("^.*v1=([0-9a-f]+).*$", "$1");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + call.body())
                .getBytes(StandardCharsets.UTF_8)));
        return expected.equals(given);
    }

    private static String text(List<SseEvent> events) {
        return events.stream().filter(event -> event.name().equals("delta"))
                .map(event -> (String) JsonPath.read(event.data(), "$.text"))
                .reduce("", String::concat);
    }

    private record Received(String path, String body, String signature) {
    }
}
