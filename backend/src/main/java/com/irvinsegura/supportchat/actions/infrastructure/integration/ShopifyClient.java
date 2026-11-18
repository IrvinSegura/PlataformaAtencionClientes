package com.irvinsegura.supportchat.actions.infrastructure.integration;

import com.irvinsegura.supportchat.actions.domain.model.Integration;
import com.irvinsegura.supportchat.actions.domain.model.IntegrationCall;
import com.irvinsegura.supportchat.actions.domain.model.WebhookResult;
import com.irvinsegura.supportchat.actions.infrastructure.integration.IntegrationHttp.Answer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * The store's orders through Shopify's GraphQL Admin API, with the access token of a custom app that has the
 * {@code read_orders} and {@code write_orders} scopes.
 * <p>
 * An order is only shown or cancelled when the email address the customer gives (or the one the mail channel
 * verified) is the order's: anyone could guess an order number. A wrong address and a missing order look the same,
 * so the assistant cannot be used to find out which orders exist.
 */
final class ShopifyClient {

    static final String PROVIDER = "Shopify";

    private static final String FIND_ORDER = """
            query FindOrder($query: String!) {
              orders(first: 1, query: $query) {
                nodes {
                  id name email createdAt cancelledAt
                  displayFinancialStatus displayFulfillmentStatus
                  totalPriceSet { shopMoney { amount currencyCode } }
                  fulfillments(first: 5) { status trackingInfo(first: 3) { company number url } }
                }
              }
            }""";
    private static final String CANCEL_ORDER = """
            mutation CancelOrder($orderId: ID!, $staffNote: String) {
              orderCancel(orderId: $orderId, reason: CUSTOMER, refund: true, restock: true, notifyCustomer: true,
                          staffNote: $staffNote) {
                job { id }
                orderCancelUserErrors { field message code }
              }
            }""";
    // Orders that left the warehouse, in whole or in part, are returned rather than cancelled
    private static final Set<String> SHIPPED = Set.of("FULFILLED", "PARTIALLY_FULFILLED", "IN_PROGRESS",
            "RESTOCKED");
    private static final int NOT_FOUND = 404;
    private static final int CONFLICT = 409;

    private final IntegrationHttp http;
    private final String urlTemplate;
    private final String apiVersion;

    /**
     * @param urlTemplate the store's address with {@code {store}} for its myshopify.com domain
     */
    ShopifyClient(IntegrationHttp http, String urlTemplate, String apiVersion) {
        this.http = http;
        this.urlTemplate = urlTemplate;
        this.apiVersion = apiVersion;
    }

    WebhookResult orderStatus(Integration store, IntegrationCall call) {
        long start = System.nanoTime();
        Lookup lookup = find(store, call);
        if (lookup.failure() != null) {
            return lookup.failure();
        }
        if (lookup.order().isEmpty()) {
            return result(200, Map.of("found", false, "reason", lookup.reason()), start);
        }
        return result(200, describe(lookup.order().get()), start);
    }

    WebhookResult cancelOrder(Integration store, IntegrationCall call) {
        long start = System.nanoTime();
        Lookup lookup = find(store, call);
        if (lookup.failure() != null) {
            return lookup.failure();
        }
        if (lookup.order().isEmpty()) {
            return refusal(NOT_FOUND, lookup.reason(), start);
        }
        JsonNode order = lookup.order().get();
        String name = order.path("name").asString("");
        if (!order.path("cancelledAt").isNull() && !order.path("cancelledAt").isMissingNode()) {
            return refusal(CONFLICT, "Order " + name + " was already cancelled", start);
        }
        if (SHIPPED.contains(order.path("displayFulfillmentStatus").asString(""))) {
            return refusal(CONFLICT, "Order " + name + " has already shipped; it can be returned instead", start);
        }
        Answer answer = graphql(store, CANCEL_ORDER, Map.of("orderId", order.path("id").asString(""),
                "staffNote", "Cancelled by the support assistant at the customer's request (run " + call.runId()
                        + ")"));
        if (!answer.ok()) {
            return answer.refused(PROVIDER);
        }
        Optional<String> errors = errors(answer.body());
        JsonNode userErrors = answer.body().path("data").path("orderCancel").path("orderCancelUserErrors");
        if (errors.isEmpty() && userErrors.isArray() && !userErrors.isEmpty()) {
            errors = Optional.of(userErrors.get(0).path("message").asString("The order cannot be cancelled"));
        }
        if (errors.isPresent()) {
            return new WebhookResult(IntegrationHttp.BAD_GATEWAY, answer.text(), PROVIDER + ": " + errors.get(),
                    null, IntegrationHttp.elapsed(start));
        }
        // Shopify cancels in the background; the job is accepted, and the customer gets Shopify's email
        return result(200, Map.of("cancelled", true, "order", name, "refunded", true), start);
    }

    private Lookup find(Integration store, IntegrationCall call) {
        String email = call.customerEmail();
        if (email == null || email.isBlank()) {
            return Lookup.missing("Ask the customer for the email address the order was placed with");
        }
        String number = String.valueOf(call.arguments().get("order_number")).strip().replaceFirst("^#", "");
        Answer answer = graphql(store, FIND_ORDER, Map.of("query", "name:#" + number));
        if (!answer.ok()) {
            return Lookup.failed(answer.refused(PROVIDER));
        }
        Optional<String> errors = errors(answer.body());
        if (errors.isPresent()) {
            return Lookup.failed(new WebhookResult(IntegrationHttp.BAD_GATEWAY, answer.text(),
                    PROVIDER + ": " + errors.get(), null, answer.latencyMillis()));
        }
        JsonNode nodes = answer.body().path("data").path("orders").path("nodes");
        for (JsonNode order : nodes) {
            String orderEmail = order.path("email").asString("");
            if (order.path("name").asString("").replaceFirst("^#", "").equals(number)
                    && !orderEmail.isBlank() && orderEmail.strip().equalsIgnoreCase(email.strip())) {
                return new Lookup(Optional.of(order), null, null);
            }
        }
        return Lookup.missing("No order with that number was placed with that email address; ask the customer to "
                + "check both");
    }

    private Answer graphql(Integration store, String query, Map<String, Object> variables) {
        String url = urlTemplate.replace("{store}", store.account()) + "/admin/api/" + apiVersion + "/graphql.json";
        return http.post(PROVIDER, url, Map.of("X-Shopify-Access-Token", store.token()),
                Map.of("query", query, "variables", variables));
    }

    // GraphQL reports failures with a 200 and an "errors" list
    private static Optional<String> errors(JsonNode body) {
        JsonNode errors = body.path("errors");
        if (errors.isArray() && !errors.isEmpty()) {
            return Optional.of(errors.get(0).path("message").asString("Unknown error"));
        }
        return Optional.empty();
    }

    // What the assistant needs to tell the customer, and nothing else: no address, no items, no other customer data
    private static Map<String, Object> describe(JsonNode order) {
        Map<String, Object> described = new LinkedHashMap<>();
        described.put("found", true);
        described.put("order", order.path("name").asString(""));
        described.put("placedAt", order.path("createdAt").asString(""));
        boolean cancelled = !order.path("cancelledAt").isNull() && !order.path("cancelledAt").isMissingNode();
        described.put("cancelled", cancelled);
        described.put("payment", order.path("displayFinancialStatus").asString("").toLowerCase(Locale.ROOT));
        described.put("fulfillment", order.path("displayFulfillmentStatus").asString("").toLowerCase(Locale.ROOT));
        JsonNode money = order.path("totalPriceSet").path("shopMoney");
        described.put("total", money.path("amount").asString("") + " " + money.path("currencyCode").asString(""));
        List<Map<String, Object>> shipments = new ArrayList<>();
        for (JsonNode fulfillment : order.path("fulfillments")) {
            for (JsonNode tracking : fulfillment.path("trackingInfo")) {
                Map<String, Object> shipment = new LinkedHashMap<>();
                shipment.put("status", fulfillment.path("status").asString("").toLowerCase(Locale.ROOT));
                shipment.put("carrier", tracking.path("company").asString(""));
                shipment.put("trackingNumber", tracking.path("number").asString(""));
                shipment.put("trackingUrl", tracking.path("url").asString(""));
                shipments.add(shipment);
            }
        }
        described.put("shipments", shipments);
        return described;
    }

    private WebhookResult result(int status, Map<String, Object> body, long start) {
        return new WebhookResult(status, http.write(body), null, null, IntegrationHttp.elapsed(start));
    }

    private WebhookResult refusal(int status, String reason, long start) {
        return new WebhookResult(status, http.write(Map.of("reason", reason)), reason, null,
                IntegrationHttp.elapsed(start));
    }

    /**
     * @param reason why no order is given, for the model
     */
    private record Lookup(Optional<JsonNode> order, String reason, WebhookResult failure) {

        static Lookup missing(String reason) {
            return new Lookup(Optional.empty(), reason, null);
        }

        static Lookup failed(WebhookResult failure) {
            return new Lookup(Optional.empty(), null, failure);
        }
    }
}
