package com.irvinsegura.supportchat.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.ApiClient.SseEvent;
import com.irvinsegura.supportchat.support.ApiClient.Visitor;
import com.irvinsegura.supportchat.support.ExternalServices;
import com.irvinsegura.supportchat.support.ExternalServices.Request;
import com.irvinsegura.supportchat.support.ExternalServices.Response;
import com.irvinsegura.supportchat.support.WithExternalServices;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The workspace connects Shopify and Zendesk with a token, and the assistant uses their actions like its own: Shopify's
 * GraphQL Admin API and Zendesk's ticket API are local stubs. The offline model calls an action when the question
 * shares words with its description and contains the values it needs.
 */
class IntegrationActionsIntegrationTest extends WithExternalServices {

    private static final String SITE = "https://shop.example.com";
    private static final String ORDER = """
            {"data":{"orders":{"nodes":[{"id":"gid://shopify/Order/1","name":"#%s","email":"Ana@Example.com",
              "createdAt":"2026-11-08T10:00:00Z","cancelledAt":null,"displayFinancialStatus":"PAID",
              "displayFulfillmentStatus":"%s","totalPriceSet":{"shopMoney":{"amount":"499.00","currencyCode":"MXN"}},
              "fulfillments":[{"status":"SUCCESS","trackingInfo":[{"company":"DHL","number":"JD0001",
              "url":"https://dhl.example/JD0001"}]}],"shippingAddress":{"address1":"Calle 1"}}]}}}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry registry;

    private ApiClient api;
    private Operator operator;
    private String widgetKey;
    // Each test gets its own store, so the calls it looks for are its own
    private String store;

    @BeforeEach
    void setUp() throws Exception {
        ExternalServices.on("POST", "/shopify/*", request -> request.body().contains("orderCancel")
                ? Response.json(200, "{\"data\":{\"orderCancel\":{\"job\":{\"id\":\"gid://shopify/Job/1\"},"
                        + "\"orderCancelUserErrors\":[]}}}")
                : request.body().contains("name:#2002") ? Response.json(200, ORDER.formatted("2002", "FULFILLED"))
                : Response.json(200, ORDER.formatted("1001", "UNFULFILLED")));
        ExternalServices.on("POST", "/zendesk/*", request -> Response.json(201, "{\"ticket\":{\"id\":35436}}"));
        api = new ApiClient(mockMvc);
        operator = api.newOperator("Acme");
        widgetKey = api.enableWidget(operator, SITE);
        store = "acme-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void anOrderIsShownToWhoeverGivesItsEmail() throws Exception {
        connectShopify();
        Visitor visitor = api.startConversation(widgetKey, SITE);

        String answer = text(api.ask(visitor, "What is the status of my order 1001 my email is ana@example.com"));

        assertThat(answer).contains("unfulfilled").contains("JD0001").doesNotContain("Calle 1");
        List<Request> calls = shopifyCalls();
        assertThat(calls).hasSize(1);
        assertThat((String) JsonPath.read(calls.getFirst().body(), "$.variables.query")).isEqualTo("name:#1001");
        api.getAs(operator, "/api/v1/conversations/" + visitor.conversationId())
                .andExpect(jsonPath("$.actionRuns[0].actionName").value("shopify_order_status"))
                .andExpect(jsonPath("$.actionRuns[0].status").value("SUCCEEDED"));
    }

    @Test
    void anotherEmailDoesNotRevealTheOrder() throws Exception {
        connectShopify();
        Visitor visitor = api.startConversation(widgetKey, SITE);

        String answer = text(api.ask(visitor, "What is the status of my order 1001 my email is eve@example.com"));

        assertThat(answer).doesNotContain("JD0001").contains("found");
    }

    @Test
    void cancellingAnOrderWaitsForTheCustomerToConfirm() throws Exception {
        connectShopify();
        Visitor visitor = api.startConversation(widgetKey, SITE);
        double pending = runs("PENDING_CONFIRMATION");
        double succeeded = runs("SUCCEEDED");
        long shopify = calls("shopify");

        String runId = pendingRun(api.ask(visitor, "Please cancel my order 1001 my email is ana@example.com"));
        assertThat(shopifyCalls()).isEmpty();

        api.visitorPost(visitor, "/actions/" + runId + "/confirmation").andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("ASSISTANT"));

        List<Request> calls = shopifyCalls();
        assertThat(calls).hasSize(2);
        assertThat((String) JsonPath.read(calls.getLast().body(), "$.variables.orderId"))
                .isEqualTo("gid://shopify/Order/1");
        api.getAs(operator, "/api/v1/conversations/" + visitor.conversationId())
                .andExpect(jsonPath("$.actionRuns[0].status").value("SUCCEEDED"));
        // The meters name the provider, never the workspace or the action
        assertThat(runs("PENDING_CONFIRMATION")).isEqualTo(pending + 1);
        assertThat(runs("SUCCEEDED")).isEqualTo(succeeded + 1);
        // One call to the integration, though it takes Shopify two requests (find, then cancel)
        assertThat(calls("shopify")).isEqualTo(shopify + 1);
    }

    @Test
    void aShippedOrderIsNotCancelled() throws Exception {
        connectShopify();
        Visitor visitor = api.startConversation(widgetKey, SITE);

        String runId = pendingRun(api.ask(visitor, "Please cancel my order 2002 my email is ana@example.com"));
        api.visitorPost(visitor, "/actions/" + runId + "/confirmation").andExpect(status().isOk());

        assertThat(shopifyCalls()).hasSize(1).noneMatch(call -> call.body().contains("orderCancel"));
        api.getAs(operator, "/api/v1/conversations/" + visitor.conversationId())
                .andExpect(jsonPath("$.actionRuns[0].status").value("FAILED"))
                .andExpect(jsonPath("$.actionRuns[0].httpStatus").value(409));
    }

    @Test
    void aConfirmedTicketIsCreatedInZendeskForTheCustomer() throws Exception {
        api.putJsonAs(operator, "/api/v1/actions/integrations/zendesk", ApiClient.json(
                        "{'account':'https://%s.zendesk.com','username':'Agente@Acme.test','token':'zd-token',"
                                + "'enabled':true}", store))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value(store))
                .andExpect(jsonPath("$.username").value("agente@acme.test"));
        Visitor visitor = api.startConversation(widgetKey, SITE);

        String runId = pendingRun(api.ask(visitor,
                "I need a support ticket for the team about a broken lamp my email is ana@example.com"));
        api.visitorPost(visitor, "/actions/" + runId + "/confirmation").andExpect(status().isOk());

        List<Request> calls = ExternalServices.requests(sent -> sent.path()
                .equals("/zendesk/" + store + "/api/v2/tickets.json"));
        assertThat(calls).hasSize(1);
        Request call = calls.getFirst();
        assertThat(call.authorization()).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("agente@acme.test/token:zd-token".getBytes(StandardCharsets.UTF_8)));
        assertThat((String) JsonPath.read(call.body(), "$.ticket.requester.email")).isEqualTo("ana@example.com");
        assertThat((List<String>) JsonPath.read(call.body(), "$.ticket.tags")).contains("supportchat_web");
        api.getAs(operator, "/api/v1/conversations/" + visitor.conversationId())
                .andExpect(jsonPath("$.actionRuns[0].status").value("SUCCEEDED"));
    }

    @Test
    void theAssistantStopsUsingAnIntegrationOnceDisconnected() throws Exception {
        connectShopify();
        api.deleteAs(operator, "/api/v1/actions/integrations/shopify").andExpect(status().isNoContent());
        Visitor visitor = api.startConversation(widgetKey, SITE);

        api.ask(visitor, "What is the status of my order 1001 my email is ana@example.com").andExpect(status().isOk());

        assertThat(shopifyCalls()).isEmpty();
        api.deleteAs(operator, "/api/v1/actions/integrations/shopify").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INTEGRATION_NOT_FOUND"));
    }

    @Test
    void onlyOwnersAndAdministratorsConnectIntegrationsAndTheirNamesAreReserved() throws Exception {
        connectShopify();
        Operator agent = api.newMember(operator, "AGENT");

        api.getAs(agent, "/api/v1/actions/integrations").andExpect(status().isForbidden());
        api.getAs(operator, "/api/v1/actions/integrations")
                .andExpect(jsonPath("$[0].provider").value("SHOPIFY"))
                .andExpect(jsonPath("$[0].connected").value(true))
                .andExpect(jsonPath("$[0].account").value(store + ".myshopify.com"))
                .andExpect(jsonPath("$[0].token").doesNotExist())
                .andExpect(jsonPath("$[0].actions[1].name").value("shopify_cancel_order"))
                .andExpect(jsonPath("$[1].provider").value("ZENDESK"))
                .andExpect(jsonPath("$[1].connected").value(false));
        api.putJsonAs(operator, "/api/v1/actions/integrations/shopify",
                        ApiClient.json("{'account':'acme.example.com','token':'','enabled':true}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_INTEGRATION"));
        api.postJsonAs(operator, "/api/v1/actions", ApiClient.json("""
                        {'name':'shopify_refund','title':'Reembolso','description':'Reembolsa',
                         'endpointUrl':'https://api.acme.test/refund','parameters':[],
                         'requiresConfirmation':true,'enabled':true}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_ACTION"));
    }

    private void connectShopify() throws Exception {
        api.putJsonAs(operator, "/api/v1/actions/integrations/shopify", ApiClient.json(
                        "{'account':'%s','token':'shpat_test_123','enabled':true}", store))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    private List<Request> shopifyCalls() {
        return ExternalServices.requests(sent -> sent.path()
                .equals("/shopify/" + store + ".myshopify.com/admin/api/2025-07/graphql.json"));
    }

    private static String text(ResultActions asked) throws Exception {
        List<SseEvent> events = ApiClient.events(asked.andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return events.stream().filter(event -> event.name().equals("delta"))
                .map(event -> (String) JsonPath.read(event.data(), "$.text"))
                .collect(Collectors.joining());
    }

    private static String pendingRun(ResultActions asked) throws Exception {
        List<SseEvent> events = ApiClient.events(asked.andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return JsonPath.read(events.getLast().data(), "$.pendingAction.runId");
    }

    private double runs(String status) {
        Counter counter = registry.find("supportchat.actions.runs").tag("status", status).counter();
        return counter == null ? 0 : counter.count();
    }

    private long calls(String target) {
        return registry.find("supportchat.actions.calls").tag("target", target).tag("outcome", "success").timers()
                .stream().mapToLong(Timer::count).sum();
    }
}
