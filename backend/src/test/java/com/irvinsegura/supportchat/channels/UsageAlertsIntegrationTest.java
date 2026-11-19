package com.irvinsegura.supportchat.channels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.billing.UsageLimitApproaching;
import com.irvinsegura.supportchat.billing.UsageMeter;
import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.Eventually;
import com.irvinsegura.supportchat.support.ExternalServices;
import com.irvinsegura.supportchat.support.ExternalServices.Request;
import com.irvinsegura.supportchat.support.ExternalServices.Response;
import com.irvinsegura.supportchat.support.WithExternalServices;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The owner hears by WhatsApp when the month's usage reaches 80 % and 95 % of what the plan includes, once each. A new
 * workspace is on the free plan, which includes 100 answers.
 */
@RecordApplicationEvents
class UsageAlertsIntegrationTest extends WithExternalServices {

    private static final String SETTINGS = "/api/v1/assistant/attention";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsageMeter usage;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationEvents events;

    private ApiClient api;
    private Operator owner;
    private String phoneNumberId;
    private String ownerPhone;

    @BeforeEach
    void setUp() throws Exception {
        ownerPhone = randomPhone();
        phoneNumberId = randomPhone();
        ExternalServices.on("POST", "/graph/*", request ->
                Response.json(200, "{\"messages\":[{\"id\":\"wamid.out-" + UUID.randomUUID() + "\"}]}"));
        api = new ApiClient(mockMvc);
        owner = api.newOperator("Acme");
        api.putJsonAs(owner, "/api/v1/channels/whatsapp", ApiClient.json(
                        "{'phoneNumberId':'%s','accessToken':'token-123','appSecret':'secret','enabled':true}",
                        phoneNumberId))
                .andExpect(status().isOk());
    }

    @Test
    void eachShareOfTheAllowanceIsToldOnceAMonth() {
        answers(79);
        assertThat(alerts()).isEmpty();

        answers(1);
        assertThat(alerts()).singleElement().satisfies(alert -> {
            assertThat(alert.percent()).isEqualTo(80);
            assertThat(alert.used()).isEqualTo(80);
            assertThat(alert.included()).isEqualTo(100);
            assertThat(alert.billedPastLimit()).isFalse();
        });

        answers(10);
        assertThat(alerts()).hasSize(1);
        answers(5);
        assertThat(alerts()).extracting(UsageLimitApproaching::percent).containsExactly(80, 95);
        answers(10);
        assertThat(alerts()).hasSize(2);
        // Tokens are not limited on their own: they never alert
        record(UsageMetric.INPUT_TOKENS, 1_000_000);
        assertThat(alerts()).hasSize(2);
    }

    @Test
    void crossingBothSharesAtOnceTellsOnlyTheHigher() {
        answers(96);

        assertThat(alerts()).extracting(UsageLimitApproaching::percent).containsExactly(95);
        answers(1);
        assertThat(alerts()).hasSize(1);
    }

    @Test
    void theOwnerGetsAWhatsAppWithWhatStopsAtTheLimit() throws Exception {
        api.putJsonAs(owner, SETTINGS, ApiClient.json("{'handOffFrustrated':true,'alertsEnabled':true,"
                        + "'alertPhones':['%s'],'alertOnRequest':true,'alertOnFrustration':true}", ownerPhone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertOnUsage").value(true));

        answers(80);

        Request alert = awaitMessageTo(ownerPhone, "Tu plan va en el 80 %");
        assertThat((String) JsonPath.read(alert.body(), "$.text.body")).contains(
                "llevas 80 de 100 respuestas del asistente", "el asistente deja de responder",
                "Compra una recarga o cambia de plan: http://localhost:5173/billing");
    }

    @Test
    void noWhatsAppWhenTheOwnerTurnedItOff() throws Exception {
        api.putJsonAs(owner, SETTINGS, ApiClient.json("{'handOffFrustrated':true,'alertsEnabled':true,"
                        + "'alertPhones':['%s'],'alertOnRequest':true,'alertOnFrustration':true,"
                        + "'alertOnUsage':false}", ownerPhone))
                .andExpect(status().isOk());

        answers(96);

        assertThat(alerts()).hasSize(1);
        Thread.sleep(1000);
        assertThat(ExternalServices.requests(sent -> sent.path().equals("/graph/" + phoneNumberId + "/messages")))
                .isEmpty();
    }

    private void answers(long quantity) {
        record(UsageMetric.ANSWERS, quantity);
    }

    private void record(UsageMetric metric, long quantity) {
        TenantContext.runAs(owner.workspaceId(), () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> usage.record(Instant.now(), metric, "WEB", quantity)));
    }

    private List<UsageLimitApproaching> alerts() {
        return events.stream(UsageLimitApproaching.class)
                .filter(alert -> alert.workspaceId().equals(owner.workspaceId()))
                .toList();
    }

    private Request awaitMessageTo(String phone, String containing) throws Exception {
        Request[] found = new Request[1];
        Eventually.assertEventually(Duration.ofSeconds(15), () -> {
            found[0] = ExternalServices.requests(sent -> sent.path().equals("/graph/" + phoneNumberId + "/messages")
                            && phone.equals(JsonPath.read(sent.body(), "$.to")) && sent.body().contains(containing))
                    .stream().findFirst().orElse(null);
            assertThat(found[0]).isNotNull();
        });
        return found[0];
    }

    private static String randomPhone() {
        return String.valueOf(1_000_000_000L + (long) (Math.random() * 8_000_000_000L));
    }
}
