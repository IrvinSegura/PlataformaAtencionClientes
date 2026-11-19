package com.irvinsegura.supportchat.channels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.channels.domain.model.WebhookSignature;
import com.irvinsegura.supportchat.shared.infrastructure.FailedEventRetries;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A reply WhatsApp could not take for a moment (it was down or limiting the business) reaches the customer on the
 * next retry, without waiting for the application to restart.
 */
class FailedReplyRetryIntegrationTest extends WithExternalServices {

    private static final String APP_SECRET = "app-secret-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FailedEventRetries retries;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void aReplyWhatsAppRefusedForAMomentGoesOutOnTheNextRetry() throws Exception {
        String phoneNumberId = String.valueOf(1_000_000_000L + (long) (Math.random() * 8_000_000_000L));
        AtomicInteger attempts = new AtomicInteger();
        ExternalServices.on("POST", "/graph/" + phoneNumberId + "/messages", request -> attempts.incrementAndGet() == 1
                ? Response.json(503, "{\"error\":{\"message\":\"Service unavailable\"}}")
                : Response.json(200, "{\"messages\":[{\"id\":\"wamid.out-" + UUID.randomUUID() + "\"}]}"));
        ApiClient api = new ApiClient(mockMvc);
        Operator owner = api.newOperator("Acme");
        String body = api.putJsonAs(owner, "/api/v1/channels/whatsapp", ApiClient.json(
                        "{'phoneNumberId':'%s','displayPhoneNumber':'+52 55 0000 0000','accessToken':'token-123',"
                                + "'appSecret':'%s','enabled':true}", phoneNumberId, APP_SECRET))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String webhookPath = JsonPath.read(body, "$.webhookPath");

        deliver(webhookPath, phoneNumberId, "Quiero hablar con una persona");
        Eventually.assertEventually(Duration.ofSeconds(15), () -> {
            assertThat(attempts).hasValue(1);
            assertThat(failedPublications()).isNotEmpty();
        });

        retries.retryFailedOlderThan(Duration.ZERO);

        Eventually.assertEventually(Duration.ofSeconds(15), () -> {
            List<Request> sent = ExternalServices.requests(request ->
                    request.path().equals("/graph/" + phoneNumberId + "/messages"));
            assertThat(sent).hasSize(2);
            assertThat(sent.get(1).body()).isEqualTo(sent.get(0).body());
        });
        Eventually.assertEventually(Duration.ofSeconds(15), () -> assertThat(failedPublications()).isEmpty());
    }

    private List<String> failedPublications() {
        return jdbc.sql("SELECT listener_id FROM event_publication WHERE status = 'FAILED'"
                        + " AND listener_id LIKE '%WhatsAppConversations%'")
                .query(String.class)
                .list();
    }

    private void deliver(String webhookPath, String phoneNumberId, String text) throws Exception {
        String customer = "52155" + (10_000_000 + (int) (Math.random() * 80_000_000));
        byte[] body = ApiClient.json("""
                {'object':'whatsapp_business_account','entry':[{'id':'1','changes':[{'field':'messages','value':{
                  'messaging_product':'whatsapp',
                  'metadata':{'display_phone_number':'5215550000','phone_number_id':'%s'},
                  'contacts':[{'profile':{'name':'Lucía'},'wa_id':'%s'}],
                  'messages':[{'from':'%s','id':'wamid.%s','timestamp':'1','type':'text','text':{'body':'%s'}}]
                }}]}]}""", phoneNumberId, customer, customer, UUID.randomUUID(), text)
                .getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(post(webhookPath).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-Hub-Signature-256", WebhookSignature.sign(APP_SECRET, body)))
                .andExpect(status().isOk());
    }
}
