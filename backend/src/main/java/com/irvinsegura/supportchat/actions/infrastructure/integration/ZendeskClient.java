package com.irvinsegura.supportchat.actions.infrastructure.integration;

import com.irvinsegura.supportchat.actions.domain.model.Integration;
import com.irvinsegura.supportchat.actions.domain.model.IntegrationCall;
import com.irvinsegura.supportchat.actions.domain.model.WebhookResult;
import com.irvinsegura.supportchat.actions.infrastructure.integration.IntegrationHttp.Answer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tickets in Zendesk Support, created as an agent with an API token
 * (https://developer.zendesk.com/api-reference/ticketing/tickets/tickets/#create-ticket). The customer is the
 * requester, so the team's answer reaches them by email.
 */
final class ZendeskClient {

    static final String PROVIDER = "Zendesk";

    private static final int UNPROCESSABLE = 422;

    private final IntegrationHttp http;
    private final String urlTemplate;

    /**
     * @param urlTemplate the account's address with {@code {subdomain}} for its subdomain
     */
    ZendeskClient(IntegrationHttp http, String urlTemplate) {
        this.http = http;
        this.urlTemplate = urlTemplate;
    }

    WebhookResult createTicket(Integration account, IntegrationCall call) {
        long start = System.nanoTime();
        String email = call.customerEmail();
        if (email == null || email.isBlank()) {
            String reason = "The ticket needs the customer's email address, so the team can answer them";
            return new WebhookResult(UNPROCESSABLE, http.write(Map.of("reason", reason)), reason, null,
                    IntegrationHttp.elapsed(start));
        }
        Object givenName = call.arguments().get("name");
        String name = givenName == null || givenName.toString().isBlank()
                ? email.substring(0, email.indexOf('@')) : givenName.toString().strip();
        Map<String, Object> ticket = new LinkedHashMap<>();
        ticket.put("subject", call.arguments().get("subject"));
        ticket.put("comment", Map.of("body", call.arguments().get("description")
                + "\n\n---\nCreated by the support assistant from a " + call.channel().toLowerCase(Locale.ROOT)
                + " conversation (" + call.conversationId() + ")."));
        ticket.put("requester", Map.of("name", name, "email", email));
        ticket.put("tags", List.of("supportchat", "supportchat_" + call.channel().toLowerCase(Locale.ROOT)));

        String credentials = account.username() + "/token:" + account.token();
        Answer answer = http.post(PROVIDER, urlTemplate.replace("{subdomain}", account.account())
                        + "/api/v2/tickets.json",
                Map.of("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString(credentials.getBytes(StandardCharsets.UTF_8))),
                Map.of("ticket", ticket));
        if (!answer.ok()) {
            return answer.refused(PROVIDER);
        }
        long id = answer.body().path("ticket").path("id").asLong(0);
        if (id == 0) {
            return new WebhookResult(IntegrationHttp.BAD_GATEWAY, answer.text(), PROVIDER + " gave no ticket number",
                    null, answer.latencyMillis());
        }
        return new WebhookResult(answer.status(), http.write(Map.of("created", true, "ticketId", id,
                "answerBy", "email to " + email)), null, null, IntegrationHttp.elapsed(start));
    }
}
