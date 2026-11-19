package com.irvinsegura.supportchat.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.billing.application.UsageReporter;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.ApiClient.SseEvent;
import com.irvinsegura.supportchat.support.ApiClient.Visitor;
import com.irvinsegura.supportchat.support.Eventually;
import com.irvinsegura.supportchat.support.ExternalServices;
import com.irvinsegura.supportchat.support.ExternalServices.Request;
import com.irvinsegura.supportchat.support.WithExternalServices;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A workspace buys a plan on Stripe's checkout, Stripe's signed notifications set what it pays for, and the plan's
 * limits decide what keeps working. Stripe is a local stub that knows the prices of every plan by lookup key.
 */
@RecordApplicationEvents
class BillingIntegrationTest extends WithExternalServices {

    private static final String SITE = "https://shop.example.com";
    private static final String WEBHOOK = "/api/v1/billing/stripe/webhook";

    static {
        ExternalServices.on("GET", "/stripe/v1/prices", """
                {"data":[
                  {"id":"price_starter","lookup_key":"supportchat_starter"},
                  {"id":"price_starter_answers","lookup_key":"supportchat_starter_answers"},
                  {"id":"price_starter_action_runs","lookup_key":"supportchat_starter_action_runs"},
                  {"id":"price_pro","lookup_key":"supportchat_pro"},
                  {"id":"price_pro_answers","lookup_key":"supportchat_pro_answers"},
                  {"id":"price_tiny","lookup_key":"supportchat_tiny"},
                  {"id":"price_topup_answers_500","lookup_key":"supportchat_topup_answers_500"}
                ]}""");
        ExternalServices.on("POST", "/stripe/v1/checkout/sessions",
                "{\"url\":\"https://checkout.stripe.test/c/pay_123\"}");
        ExternalServices.on("POST", "/stripe/v1/billing_portal/sessions",
                "{\"url\":\"https://billing.stripe.test/p/session_123\"}");
        ExternalServices.on("GET", "/stripe/v1/subscriptions/*", """
                {"items":{"data":[
                  {"id":"si_base","price":{"id":"price_starter","lookup_key":"supportchat_starter"}},
                  {"id":"si_answers","price":{"id":"price_starter_answers","lookup_key":"supportchat_starter_answers"}},
                  {"id":"si_runs","price":{"id":"price_starter_action_runs",
                    "lookup_key":"supportchat_starter_action_runs"}}
                ]}}""");
        ExternalServices.on("GET", "/stripe/v1/invoices", """
                {"data":[{"id":"in_1","number":"ACME-0001","status":"paid","total":2900,"currency":"usd",
                  "created":1772323200,"period_start":1769904000,"period_end":1772323200,
                  "hosted_invoice_url":"https://invoice.stripe.test/i/1","invoice_pdf":"https://invoice.stripe.test/i/1.pdf"}]}
                """);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsageReporter usageReporter;

    @Autowired
    private UsageMeter usageMeter;

    @Autowired
    private UsageAllowance allowance;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationEvents events;

    private ApiClient api;
    private Operator owner;
    private String customerId;
    private String subscriptionId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        owner = api.newOperator("Acme");
        customerId = "cus_" + UUID.randomUUID().toString().replace("-", "");
        subscriptionId = "sub_" + UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    void aNewWorkspaceIsOnTheFreePlanAndSeesThePlansOnSale() throws Exception {
        api.getAs(owner, "/api/v1/billing").andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.code").value("FREE"))
                .andExpect(jsonPath("$.status").value("NONE"))
                .andExpect(jsonPath("$.paymentsEnabled").value(true))
                .andExpect(jsonPath("$.hasPaymentAccount").value(false))
                .andExpect(jsonPath("$.quotas[?(@.metric == 'ANSWERS')].included").value(100))
                .andExpect(jsonPath("$.plans[?(@.code == 'STARTER')].purchasable").value(true))
                .andExpect(jsonPath("$.plans[?(@.code == 'FREE')].purchasable").value(false));
    }

    @Test
    void buyingAPlanGoesThroughStripesCheckoutAndApplyOnceStripeConfirms() throws Exception {
        api.postJsonAs(owner, "/api/v1/billing/plan", ApiClient.json("{'plan':'STARTER'}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.stripe.test/c/pay_123"));

        Request checkout = last(request -> request.path().equals("/stripe/v1/checkout/sessions")
                && owner.workspaceId().toString().equals(request.form("client_reference_id")));
        assertThat(checkout.authorization()).isEqualTo("Bearer sk_test_123");
        assertThat(checkout.form("mode")).isEqualTo("subscription");
        assertThat(checkout.form("line_items[0][price]")).isEqualTo("price_starter");
        assertThat(List.of(checkout.form("line_items[1][price]"), checkout.form("line_items[2][price]")))
                .containsExactlyInAnyOrder("price_starter_answers", "price_starter_action_runs");
        assertThat(checkout.form("subscription_data[metadata][workspace_id]"))
                .isEqualTo(owner.workspaceId().toString());
        assertThat(checkout.form("success_url")).endsWith("/billing?checkout=success");
        // Billing starts on the first of next month, in line with the monthly quotas
        Instant anchor = Instant.ofEpochSecond(Long.parseLong(checkout.form("subscription_data[billing_cycle_anchor]")));
        assertThat(anchor.toString()).endsWith("-01T00:00:00Z");
        // Nothing changes until Stripe confirms the payment
        api.getAs(owner, "/api/v1/billing").andExpect(jsonPath("$.plan.code").value("FREE"));

        deliver(checkoutCompleted("evt_" + UUID.randomUUID())).andExpect(status().isOk());
        deliver(subscriptionEvent("evt_" + UUID.randomUUID(), "STARTER", "active", Instant.now()))
                .andExpect(status().isOk());

        api.getAs(owner, "/api/v1/billing")
                .andExpect(jsonPath("$.plan.code").value("STARTER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.hasPaymentAccount").value(true))
                .andExpect(jsonPath("$.quotas[?(@.metric == 'ANSWERS')].included").value(2000));
    }

    @Test
    void eachNotificationIsAppliedOnceAndInTheOrderItHappened() throws Exception {
        String eventId = "evt_" + UUID.randomUUID();
        Instant now = Instant.now();
        deliver(subscriptionEvent(eventId, "STARTER", "active", now)).andExpect(status().isOk());

        // Stripe delivers the same notification again: acknowledged and ignored
        deliver(subscriptionEvent(eventId, "STARTER", "canceled", now.plusSeconds(5))).andExpect(status().isOk());
        // An older notification arrives late: ignored too
        deliver(subscriptionEvent("evt_" + UUID.randomUUID(), "STARTER", "past_due", now.minusSeconds(60)))
                .andExpect(status().isOk());

        api.getAs(owner, "/api/v1/billing")
                .andExpect(jsonPath("$.plan.code").value("STARTER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void aNotificationWithoutStripesSignatureIsRefused() throws Exception {
        byte[] body = subscriptionEvent("evt_" + UUID.randomUUID(), "PRO", "active", Instant.now())
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Stripe-Signature", sign("whsec_forged", Instant.now().getEpochSecond(), body)))
                .andExpect(status().isUnauthorized());
        // A genuine signature captured long ago cannot be replayed
        mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Stripe-Signature", sign(STRIPE_WEBHOOK_SECRET,
                                Instant.now().minus(Duration.ofHours(1)).getEpochSecond(), body)))
                .andExpect(status().isUnauthorized());

        api.getAs(owner, "/api/v1/billing").andExpect(jsonPath("$.plan.code").value("FREE"));
    }

    @Test
    void aLiveSubscriptionChangesPlanInPlaceAndTheFreePlanEndsItAtTheEndOfThePeriod() throws Exception {
        deliver(subscriptionEvent("evt_" + UUID.randomUUID(), "STARTER", "active", Instant.now()))
                .andExpect(status().isOk());

        api.postJsonAs(owner, "/api/v1/billing/plan", ApiClient.json("{'plan':'STARTER'}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOR_SALE"));

        api.postJsonAs(owner, "/api/v1/billing/plan", ApiClient.json("{'plan':'PRO'}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").doesNotExist());
        Request change = last(request -> request.method().equals("POST")
                && request.path().equals("/stripe/v1/subscriptions/" + subscriptionId));
        assertThat(change.form("items[0][id]")).isEqualTo("si_base");
        assertThat(change.form("items[0][price]")).isEqualTo("price_pro");
        assertThat(change.form("items[1][price]")).isEqualTo("price_pro_answers");
        // PRO has no metered price for actions: that item goes
        assertThat(change.form("items[2][id]")).isEqualTo("si_runs");
        assertThat(change.form("items[2][deleted]")).isEqualTo("true");

        api.postJsonAs(owner, "/api/v1/billing/plan", ApiClient.json("{'plan':'FREE'}")).andExpect(status().isOk());
        Request cancel = last(request -> request.method().equals("POST")
                && request.path().equals("/stripe/v1/subscriptions/" + subscriptionId));
        assertThat(cancel.form("cancel_at_period_end")).isEqualTo("true");

        api.postJsonAs(owner, "/api/v1/billing/plan", ApiClient.json("{'plan':'GOLD'}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aFailedPaymentKeepsThePlanForTheGracePeriodThenFallsToTheFreePlan() throws Exception {
        Instant now = Instant.now();
        deliver(subscriptionEvent("evt_" + UUID.randomUUID(), "STARTER", "active", now.minus(Duration.ofDays(30))))
                .andExpect(status().isOk());
        deliver(subscriptionEvent("evt_" + UUID.randomUUID(), "STARTER", "past_due", now.minus(Duration.ofDays(2))))
                .andExpect(status().isOk());

        api.getAs(owner, "/api/v1/billing")
                .andExpect(jsonPath("$.plan.code").value("STARTER"))
                .andExpect(jsonPath("$.status").value("PAST_DUE"))
                .andExpect(jsonPath("$.graceEndsAt").exists());

        Operator late = api.newOperator("Late");
        String lateSubscription = "sub_" + UUID.randomUUID();
        String lateCustomer = "cus_" + UUID.randomUUID();
        deliver(subscriptionEvent("evt_" + UUID.randomUUID(), late.workspaceId(), lateCustomer, lateSubscription,
                "STARTER", "active", now.minus(Duration.ofDays(30)))).andExpect(status().isOk());
        deliver(subscriptionEvent("evt_" + UUID.randomUUID(), late.workspaceId(), lateCustomer, lateSubscription,
                "STARTER", "past_due", now.minus(Duration.ofDays(8)))).andExpect(status().isOk());

        api.getAs(late, "/api/v1/billing")
                .andExpect(jsonPath("$.plan.code").value("FREE"))
                .andExpect(jsonPath("$.paidPlanCode").value("STARTER"))
                .andExpect(jsonPath("$.status").value("PAST_DUE"));
    }

    @Test
    void pastThePlansAnswersTheAssistantPausesAndTheTeamTakesOver() throws Exception {
        deliver(subscriptionEvent("evt_" + UUID.randomUUID(), "TINY", "active", Instant.now()))
                .andExpect(status().isOk());
        String widgetKey = api.enableWidget(owner, SITE);
        api.uploadAndWaitUntilReady(owner, "envios.md", "# Envíos\n\nEl envío nacional tarda de 3 a 5 días hábiles.");
        Visitor visitor = api.startConversation(widgetKey, SITE);

        List<SseEvent> answered = ApiClient.events(api.ask(visitor, "¿Cuánto tarda el envío nacional?")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(answered.getLast().name()).isEqualTo("done");
        Eventually.assertEventually(Duration.ofSeconds(10), () -> api.getAs(owner, "/api/v1/billing")
                .andExpect(jsonPath("$.quotas[?(@.metric == 'ANSWERS')].used").value(1))
                .andExpect(jsonPath("$.quotas[?(@.metric == 'ANSWERS')].exhausted").value(true)));

        List<SseEvent> paused = ApiClient.events(api.ask(visitor, "¿Y el envío internacional?")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(paused).extracting(SseEvent::name).containsExactly("forwarded");

        api.visitorHistory(visitor)
                .andExpect(jsonPath("$[?(@.author == 'SYSTEM')].content").value("ASSISTANT_PAUSED"));
        api.getAs(owner, "/api/v1/conversations?handling=WAITING").andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void usageIsReportedToStripesMetersOnce() throws Exception {
        deliver(checkoutCompleted("evt_" + UUID.randomUUID())).andExpect(status().isOk());
        deliver(subscriptionEvent("evt_" + UUID.randomUUID(), "STARTER", "active", Instant.now()))
                .andExpect(status().isOk());
        String widgetKey = api.enableWidget(owner, SITE);
        api.uploadAndWaitUntilReady(owner, "envios.md", "# Envíos\n\nEl envío nacional tarda de 3 a 5 días hábiles.");
        Visitor visitor = api.startConversation(widgetKey, SITE);
        api.ask(visitor, "¿Cuánto tarda el envío nacional?").andExpect(status().isOk());
        Eventually.assertEventually(Duration.ofSeconds(10), () -> api.getAs(owner, "/api/v1/billing")
                .andExpect(jsonPath("$.quotas[?(@.metric == 'ANSWERS')].used").value(1)));

        usageReporter.reportAll();
        usageReporter.reportAll();

        List<Request> reports = ExternalServices.requests(request -> request.path().equals(
                "/stripe/v1/billing/meter_events") && customerId.equals(request.form("payload[stripe_customer_id]")));
        assertThat(reports).hasSize(1);
        assertThat(reports.getFirst().form("event_name")).isEqualTo("supportchat_answers");
        assertThat(reports.getFirst().form("payload[value]")).isEqualTo("1");
        assertThat(reports.getFirst().form("identifier")).startsWith(owner.workspaceId() + ":")
                .endsWith(":ANSWERS:1");
    }

    @Test
    void theOwnerManagesPaymentsAndAdministratorsOnlySeeThem() throws Exception {
        Operator admin = api.newMember(owner, "ADMIN");
        Operator agent = api.newMember(owner, "AGENT");

        api.getAs(agent, "/api/v1/billing").andExpect(status().isForbidden());
        api.getAs(admin, "/api/v1/billing").andExpect(status().isOk());
        api.postJsonAs(admin, "/api/v1/billing/plan", ApiClient.json("{'plan':'STARTER'}"))
                .andExpect(status().isForbidden());
        api.postAs(admin, "/api/v1/billing/portal").andExpect(status().isForbidden());

        // Without a purchase there is no Stripe customer: no portal, no invoices
        api.postAs(owner, "/api/v1/billing/portal").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NO_PAYMENT_ACCOUNT"));
        api.getAs(admin, "/api/v1/billing/invoices").andExpect(jsonPath("$.length()").value(0));

        deliver(checkoutCompleted("evt_" + UUID.randomUUID())).andExpect(status().isOk());
        api.postAs(owner, "/api/v1/billing/portal").andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://billing.stripe.test/p/session_123"));
        assertThat(last(request -> request.path().equals("/stripe/v1/billing_portal/sessions")
                && customerId.equals(request.form("customer"))).form("return_url")).endsWith("/billing");
        api.getAs(admin, "/api/v1/billing/invoices")
                .andExpect(jsonPath("$[0].number").value("ACME-0001"))
                .andExpect(jsonPath("$[0].total").value(29.0))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].pdfUrl").value("https://invoice.stripe.test/i/1.pdf"));
    }

    @Test
    void aPlanThatStopsAtItsLimitBuysARechargeThatAddsUnitsForTheMonth() throws Exception {
        answers(100);
        assertThat(allows(UsageMetric.ANSWERS)).isFalse();
        api.getAs(owner, "/api/v1/billing")
                .andExpect(jsonPath("$.topUps[?(@.code == 'ANSWERS_500')].available").value(true))
                .andExpect(jsonPath("$.topUps[?(@.code == 'ANSWERS_500')].purchasable").value(true))
                .andExpect(jsonPath("$.topUps[?(@.code == 'ANSWERS_500')].quantity").value(500))
                // On offer, but Stripe has no price for it
                .andExpect(jsonPath("$.topUps[?(@.code == 'ACTION_RUNS_200')].purchasable").value(false));

        api.postJsonAs(owner, "/api/v1/billing/top-ups", ApiClient.json("{'topUp':'ANSWERS_500'}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.stripe.test/c/pay_123"));
        Request checkout = last(request -> request.path().equals("/stripe/v1/checkout/sessions")
                && owner.workspaceId().toString().equals(request.form("client_reference_id")));
        assertThat(checkout.form("mode")).isEqualTo("payment");
        assertThat(checkout.form("line_items[0][price]")).isEqualTo("price_topup_answers_500");
        assertThat(checkout.form("metadata[top_up]")).isEqualTo("ANSWERS_500");
        assertThat(checkout.form("metadata[top_up_metric]")).isEqualTo("ANSWERS");
        assertThat(checkout.form("metadata[top_up_quantity]")).isEqualTo("500");
        // A workspace that never paid gets a Stripe customer with its first purchase
        assertThat(checkout.form("customer_creation")).isEqualTo("always");
        assertThat(checkout.form("success_url")).endsWith("/billing?top-up=success");
        api.getAs(owner, "/api/v1/billing").andExpect(jsonPath("$.quotas[?(@.metric == 'ANSWERS')].included")
                .value(100));

        String session = "cs_" + UUID.randomUUID();
        deliver(topUpPaid("evt_" + UUID.randomUUID(), "checkout.session.completed", session, "paid"))
                .andExpect(status().isOk());
        // Stripe reports the same purchase again under another notification: added once
        deliver(topUpPaid("evt_" + UUID.randomUUID(), "checkout.session.completed", session, "paid"))
                .andExpect(status().isOk());

        api.getAs(owner, "/api/v1/billing")
                .andExpect(jsonPath("$.plan.code").value("FREE"))
                .andExpect(jsonPath("$.hasPaymentAccount").value(true))
                .andExpect(jsonPath("$.quotas[?(@.metric == 'ANSWERS')].included").value(600))
                .andExpect(jsonPath("$.quotas[?(@.metric == 'ANSWERS')].extra").value(500))
                .andExpect(jsonPath("$.quotas[?(@.metric == 'ANSWERS')].exhausted").value(false))
                .andExpect(jsonPath("$.quotas[?(@.metric == 'ACTION_RUNS')].extra").value(0));
        api.getAs(owner, "/api/v1/usage").andExpect(jsonPath("$.quotas[?(@.metric == 'ANSWERS')].included")
                .value(600));
        assertThat(allows(UsageMetric.ANSWERS)).isTrue();
    }

    @Test
    void aPaymentThatSettlesLaterAddsTheRechargeOnceItArrives() throws Exception {
        String session = "cs_" + UUID.randomUUID();
        deliver(topUpPaid("evt_" + UUID.randomUUID(), "checkout.session.completed", session, "unpaid"))
                .andExpect(status().isOk());
        api.getAs(owner, "/api/v1/billing").andExpect(jsonPath("$.quotas[?(@.metric == 'ANSWERS')].included")
                .value(100));

        deliver(topUpPaid("evt_" + UUID.randomUUID(), "checkout.session.async_payment_succeeded", session, "paid"))
                .andExpect(status().isOk());

        api.getAs(owner, "/api/v1/billing").andExpect(jsonPath("$.quotas[?(@.metric == 'ANSWERS')].included")
                .value(600));
    }

    @Test
    void theSharesOfTheAllowanceAreToldAgainOnceARechargeRaisedIt() throws Exception {
        answers(96);
        assertThat(usageAlerts()).extracting(UsageLimitApproaching::percent).containsExactly(95);

        deliver(topUpPaid("evt_" + UUID.randomUUID(), "checkout.session.completed", "cs_" + UUID.randomUUID(),
                "paid")).andExpect(status().isOk());
        // 480 of 600
        answers(384);

        assertThat(usageAlerts()).hasSize(2).last().satisfies(alert -> {
            assertThat(alert.percent()).isEqualTo(80);
            assertThat(alert.included()).isEqualTo(600);
            assertThat(alert.extra()).isEqualTo(500);
        });
    }

    @Test
    void aPlanThatBillsPastItsLimitNeedsNoRecharge() throws Exception {
        deliver(subscriptionEvent("evt_" + UUID.randomUUID(), "STARTER", "active", Instant.now()))
                .andExpect(status().isOk());

        api.getAs(owner, "/api/v1/billing")
                .andExpect(jsonPath("$.topUps[?(@.code == 'ANSWERS_500')].available").value(false))
                .andExpect(jsonPath("$.topUps[?(@.code == 'ANSWERS_500')].purchasable").value(false));
        api.postJsonAs(owner, "/api/v1/billing/top-ups", ApiClient.json("{'topUp':'ANSWERS_500'}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TOP_UP_NOT_FOR_SALE"));
        api.postJsonAs(owner, "/api/v1/billing/top-ups", ApiClient.json("{'topUp':'GOLD_BARS'}"))
                .andExpect(status().isNotFound());
        Operator admin = api.newMember(owner, "ADMIN");
        api.postJsonAs(admin, "/api/v1/billing/top-ups", ApiClient.json("{'topUp':'ANSWERS_500'}"))
                .andExpect(status().isForbidden());
    }

    private void answers(long quantity) {
        TenantContext.runAs(owner.workspaceId(), () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> usageMeter.record(Instant.now(), UsageMetric.ANSWERS, "WEB",
                        quantity)));
    }

    private boolean allows(UsageMetric metric) {
        return TenantContext.callAs(owner.workspaceId(), () -> new TransactionTemplate(transactionManager)
                .execute(status -> allowance.allows(metric)));
    }

    private List<UsageLimitApproaching> usageAlerts() {
        return events.stream(UsageLimitApproaching.class)
                .filter(alert -> alert.workspaceId().equals(owner.workspaceId()))
                .toList();
    }

    private String topUpPaid(String eventId, String type, String session, String paymentStatus) {
        return ApiClient.json("""
                {'id':'%s','type':'%s','created':%d,'data':{'object':{
                  'id':'%s','client_reference_id':'%s','customer':'%s','mode':'payment','payment_status':'%s',
                  'metadata':{'workspace_id':'%s','top_up':'ANSWERS_500','top_up_metric':'ANSWERS',
                    'top_up_quantity':'500'}}}}""",
                eventId, type, Instant.now().getEpochSecond(), session, owner.workspaceId(), customerId,
                paymentStatus, owner.workspaceId());
    }

    private ResultActions deliver(String payload) throws Exception {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        return mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Stripe-Signature", sign(STRIPE_WEBHOOK_SECRET, Instant.now().getEpochSecond(), body)));
    }

    private String checkoutCompleted(String eventId) {
        return ApiClient.json("""
                {'id':'%s','type':'checkout.session.completed','created':%d,'data':{'object':{
                  'id':'cs_1','client_reference_id':'%s','customer':'%s','mode':'subscription'}}}""",
                eventId, Instant.now().getEpochSecond(), owner.workspaceId(), customerId);
    }

    private String subscriptionEvent(String eventId, String plan, String status, Instant at) {
        return subscriptionEvent(eventId, owner.workspaceId(), customerId, subscriptionId, plan, status, at);
    }

    private String subscriptionEvent(String eventId, UUID workspaceId, String customer, String subscription,
            String plan, String status, Instant at) {
        String key = "supportchat_" + plan.toLowerCase();
        return ApiClient.json("""
                {'id':'%s','type':'customer.subscription.updated','created':%d,'data':{'object':{
                  'id':'%s','customer':'%s','status':'%s','cancel_at_period_end':false,
                  'metadata':{'workspace_id':'%s'},
                  'items':{'data':[
                    {'id':'si_base','current_period_end':%d,'price':{'id':'price_x','lookup_key':'%s'}},
                    {'id':'si_answers','price':{'id':'price_y','lookup_key':'%s_answers'}}]}}}}""",
                eventId, at.getEpochSecond(), subscription, customer, status, workspaceId,
                at.plus(Duration.ofDays(30)).getEpochSecond(), key, key);
    }

    private static Request last(java.util.function.Predicate<Request> matching) {
        List<Request> found = ExternalServices.requests(matching);
        assertThat(found).as("requests to the Stripe stub").isNotEmpty();
        return found.getLast();
    }

    /**
     * Stripe's signature, computed here as Stripe does it rather than with the application's own code.
     */
    private static String sign(String secret, long timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
