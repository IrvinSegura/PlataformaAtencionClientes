package com.irvinsegura.supportchat.billing.infrastructure.stripe;

import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.billing.domain.model.InvalidPaymentSignatureException;
import com.irvinsegura.supportchat.billing.domain.model.Invoice;
import com.irvinsegura.supportchat.billing.domain.model.PaymentProviderUnavailableException;
import com.irvinsegura.supportchat.billing.domain.model.PaymentsNotConfiguredException;
import com.irvinsegura.supportchat.billing.domain.model.PlanCatalog;
import com.irvinsegura.supportchat.billing.domain.model.ProviderEvent;
import com.irvinsegura.supportchat.billing.domain.model.Subscription.ProviderSubscription;
import com.irvinsegura.supportchat.billing.domain.model.SubscriptionStatus;
import com.irvinsegura.supportchat.billing.domain.model.TopUp;
import com.irvinsegura.supportchat.billing.domain.port.PaymentProvider;
import com.irvinsegura.supportchat.billing.infrastructure.config.BillingProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stripe through its REST API (https://docs.stripe.com/api): Checkout for the first purchase, the customer portal
 * for payment methods and invoices, billing meters for usage past each plan's included quantities.
 * <p>
 * Prices are not configured here but found by lookup key, so the same configuration works in test and live mode:
 * {@code supportchat_starter} is the monthly price of plan STARTER and {@code supportchat_starter_answers} its
 * metered price for answers, which Stripe tiers so the included quantity costs nothing.
 */
public class StripePaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentProvider.class);
    // Stripe accepts at most ten lookup keys per price search
    private static final int LOOKUP_BATCH = 10;
    private static final int INVOICES = 12;
    private static final int MAX_ERROR_CHARS = 300;
    private static final String WORKSPACE_METADATA = "workspace_id";
    private static final String TOP_UP_METADATA = "top_up";
    private static final String TOP_UP_METRIC_METADATA = "top_up_metric";
    private static final String TOP_UP_QUANTITY_METADATA = "top_up_quantity";
    private static final String TOP_UP_ROLE = "topup";
    private static final String ASYNC_PAYMENT_SUCCEEDED = "checkout.session.async_payment_succeeded";

    private final HttpClient http;
    private final JsonMapper json;
    private final BillingProperties.Stripe settings;
    private final PlanCatalog catalog;
    private final List<TopUp> topUps;
    private final String baseUrl;
    private volatile PriceCache prices;

    public StripePaymentProvider(HttpClient http, JsonMapper json, BillingProperties.Stripe settings,
            PlanCatalog catalog, List<TopUp> topUps) {
        this.http = http;
        this.json = json;
        this.settings = settings;
        this.catalog = catalog;
        this.topUps = List.copyOf(topUps);
        this.baseUrl = settings.baseUrl().replaceAll("/+$", "");
    }

    @Override
    public boolean configured() {
        return settings.configured();
    }

    @Override
    public boolean sells(String planCode) {
        return configured() && prices().containsKey(baseKey(planCode));
    }

    @Override
    public String checkout(Checkout checkout) {
        Map<String, String> prices = prices();
        Form form = new Form()
                .add("mode", "subscription")
                .add("client_reference_id", checkout.workspaceId().toString())
                .add("metadata[" + WORKSPACE_METADATA + "]", checkout.workspaceId().toString())
                .add("subscription_data[metadata][" + WORKSPACE_METADATA + "]", checkout.workspaceId().toString())
                .add("subscription_data[billing_cycle_anchor]",
                        String.valueOf(checkout.billingCycleStart().getEpochSecond()))
                .add("success_url", checkout.successUrl())
                .add("cancel_url", checkout.cancelUrl())
                .add("line_items[0][price]", require(prices, baseKey(checkout.planCode())))
                .add("line_items[0][quantity]", "1");
        if (checkout.customerId() != null) {
            form.add("customer", checkout.customerId());
        }
        int item = 1;
        for (UsageMetric metric : UsageMetric.values()) {
            String price = prices.get(meteredKey(checkout.planCode(), metric));
            if (price != null) {
                // Metered prices take no quantity: the meter decides it
                form.add("line_items[" + item++ + "][price]", price);
            }
        }
        return post("/v1/checkout/sessions", form).path("url").asString("");
    }

    @Override
    public boolean sellsTopUp(String topUpCode) {
        return configured() && prices().containsKey(topUpKey(topUpCode));
    }

    @Override
    public String buyTopUp(TopUpCheckout checkout) {
        TopUp topUp = checkout.topUp();
        String workspace = checkout.workspaceId().toString();
        // What the recharge adds travels with the payment, so a later change of the offer does not change it
        Form form = new Form()
                .add("mode", "payment")
                .add("client_reference_id", workspace)
                .add("metadata[" + WORKSPACE_METADATA + "]", workspace)
                .add("metadata[" + TOP_UP_METADATA + "]", topUp.code())
                .add("metadata[" + TOP_UP_METRIC_METADATA + "]", topUp.metric().name())
                .add("metadata[" + TOP_UP_QUANTITY_METADATA + "]", String.valueOf(topUp.quantity()))
                .add("payment_intent_data[metadata][" + WORKSPACE_METADATA + "]", workspace)
                // A receipt among the customer's invoices, like the plan's
                .add("invoice_creation[enabled]", "true")
                .add("success_url", checkout.successUrl())
                .add("cancel_url", checkout.cancelUrl())
                .add("line_items[0][price]", require(prices(), topUpKey(topUp.code())))
                .add("line_items[0][quantity]", "1");
        if (checkout.customerId() != null) {
            form.add("customer", checkout.customerId());
        } else {
            form.add("customer_creation", "always");
        }
        return post("/v1/checkout/sessions", form).path("url").asString("");
    }

    @Override
    public String portal(String customerId, String returnUrl) {
        return post("/v1/billing_portal/sessions", new Form()
                .add("customer", customerId)
                .add("return_url", returnUrl))
                .path("url").asString("");
    }

    @Override
    public void changePlan(String subscriptionId, String planCode) {
        Map<String, String> prices = prices();
        JsonNode subscription = get("/v1/subscriptions/" + encode(subscriptionId));
        Form form = new Form()
                .add("proration_behavior", "create_prorations")
                .add("cancel_at_period_end", "false");
        int index = 0;
        List<String> kept = new ArrayList<>();
        for (JsonNode item : subscription.path("items").path("data")) {
            String role = role(item.path("price").path("lookup_key").asString(""));
            String replacement = role == null ? null : prices.get(key(planCode, role));
            form.add("items[" + index + "][id]", item.path("id").asString(""));
            if (replacement == null) {
                form.add("items[" + index + "][deleted]", "true");
            } else {
                form.add("items[" + index + "][price]", replacement);
                kept.add(role);
            }
            index++;
        }
        // Metered prices the new plan has and the old one did not
        for (UsageMetric metric : UsageMetric.values()) {
            String role = metric.name().toLowerCase(Locale.ROOT);
            String price = prices.get(key(planCode, role));
            if (price != null && !kept.contains(role)) {
                form.add("items[" + index++ + "][price]", price);
            }
        }
        if (!kept.contains("")) {
            form.add("items[" + index + "][price]", require(prices, baseKey(planCode)))
                    .add("items[" + index + "][quantity]", "1");
        }
        post("/v1/subscriptions/" + encode(subscriptionId), form);
    }

    @Override
    public void cancelAtPeriodEnd(String subscriptionId) {
        post("/v1/subscriptions/" + encode(subscriptionId), new Form().add("cancel_at_period_end", "true"));
    }

    @Override
    public List<Invoice> invoices(String customerId) {
        JsonNode list = get("/v1/invoices?customer=" + encode(customerId) + "&limit=" + INVOICES);
        List<Invoice> invoices = new ArrayList<>();
        for (JsonNode invoice : list.path("data")) {
            invoices.add(new Invoice(
                    invoice.path("id").asString(""),
                    text(invoice.path("number")),
                    invoice.path("status").asString(""),
                    // Amounts are in the currency's minor unit; every currency offered here has two decimals
                    BigDecimal.valueOf(invoice.path("total").asLong(0)).movePointLeft(2),
                    invoice.path("currency").asString("").toUpperCase(Locale.ROOT),
                    epoch(invoice.path("created")),
                    epoch(invoice.path("period_start")),
                    epoch(invoice.path("period_end")),
                    text(invoice.path("hosted_invoice_url")),
                    text(invoice.path("invoice_pdf"))));
        }
        return invoices;
    }

    @Override
    public void reportUsage(String customerId, UsageMetric metric, long quantity, String identifier, Instant at) {
        String event = settings.meterEvents().get(metric);
        if (event == null) {
            return;
        }
        post("/v1/billing/meter_events", new Form()
                .add("event_name", event)
                .add("identifier", identifier)
                .add("timestamp", String.valueOf(at.getEpochSecond()))
                .add("payload[stripe_customer_id]", customerId)
                .add("payload[value]", String.valueOf(quantity)));
    }

    @Override
    public ProviderEvent read(byte[] body, String signature, Instant now) {
        if (!StripeSignature.matches(settings.webhookSecret(), body, signature, now,
                settings.signatureTolerance())) {
            throw new InvalidPaymentSignatureException();
        }
        JsonNode event;
        try {
            event = json.readTree(body);
        } catch (JacksonException e) {
            throw new InvalidPaymentSignatureException();
        }
        String id = event.path("id").asString("");
        Instant createdAt = epoch(event.path("created"));
        String type = event.path("type").asString("");
        JsonNode object = event.path("data").path("object");
        if ("payment".equals(text(object.path("mode")))
                && (type.equals("checkout.session.completed") || type.equals(ASYNC_PAYMENT_SUCCEEDED))) {
            return topUpPaid(id, createdAt, type, object);
        }
        return switch (type) {
            case "checkout.session.completed" -> new ProviderEvent.CheckoutCompleted(id, createdAt,
                    uuid(text(object.path("client_reference_id"))), text(object.path("customer")));
            case "customer.subscription.created", "customer.subscription.updated", "customer.subscription.deleted" ->
                    new ProviderEvent.SubscriptionReported(id, createdAt,
                            uuid(text(object.path("metadata").path(WORKSPACE_METADATA))), subscription(object));
            default -> new ProviderEvent.Other(id, createdAt, type);
        };
    }

    /**
     * A one-time checkout: a recharge once its money arrived. A payment method that settles later completes the
     * checkout unpaid and reports the payment afterwards.
     */
    private ProviderEvent topUpPaid(String id, Instant createdAt, String type, JsonNode session) {
        JsonNode metadata = session.path("metadata");
        String code = text(metadata.path(TOP_UP_METADATA));
        UsageMetric metric = metric(text(metadata.path(TOP_UP_METRIC_METADATA)));
        long quantity = parseQuantity(text(metadata.path(TOP_UP_QUANTITY_METADATA)));
        boolean paid = type.equals(ASYNC_PAYMENT_SUCCEEDED) || "paid".equals(text(session.path("payment_status")));
        if (code == null || metric == null || quantity <= 0 || !paid) {
            return new ProviderEvent.Other(id, createdAt, paid ? type : type + " (unpaid)");
        }
        UUID workspace = Optional.ofNullable(uuid(text(metadata.path(WORKSPACE_METADATA))))
                .orElseGet(() -> uuid(text(session.path("client_reference_id"))));
        return new ProviderEvent.TopUpPaid(id, createdAt, workspace, text(session.path("customer")),
                session.path("id").asString(""), code, metric, quantity);
    }

    private static UsageMetric metric(String name) {
        if (name == null) {
            return null;
        }
        try {
            return UsageMetric.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static long parseQuantity(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private ProviderSubscription subscription(JsonNode object) {
        String planCode = null;
        Instant periodEnd = epoch(object.path("current_period_end"));
        for (JsonNode item : object.path("items").path("data")) {
            String role = role(item.path("price").path("lookup_key").asString(""));
            String plan = planOf(item.path("price").path("lookup_key").asString(""));
            if ("".equals(role) && plan != null) {
                planCode = plan;
            }
            // Newer API versions report the period on each item instead of the subscription
            if (periodEnd == null) {
                periodEnd = epoch(item.path("current_period_end"));
            }
        }
        return new ProviderSubscription(object.path("id").asString(""), object.path("customer").asString(""),
                planCode, SubscriptionStatus.fromProvider(text(object.path("status"))), periodEnd,
                object.path("cancel_at_period_end").asBoolean(false));
    }

    /**
     * What a price is for within its plan: "" for the monthly price, the metric in lower case for a metered one,
     * or null for a price of no plan on offer.
     */
    private String role(String lookupKey) {
        String plan = planOf(lookupKey);
        if (plan == null) {
            return null;
        }
        return lookupKey.substring(baseKey(plan).length()).replaceFirst("^_", "");
    }

    private String planOf(String lookupKey) {
        return catalog.all().stream()
                .map(plan -> plan.code())
                .filter(code -> lookupKey.equals(baseKey(code)) || lookupKey.startsWith(baseKey(code) + "_")
                        && isMetric(lookupKey.substring(baseKey(code).length() + 1)))
                .findFirst()
                .orElse(null);
    }

    private static boolean isMetric(String role) {
        try {
            UsageMetric.valueOf(role.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String baseKey(String planCode) {
        return settings.lookupKeyPrefix() + "_" + planCode.toLowerCase(Locale.ROOT);
    }

    private String topUpKey(String topUpCode) {
        return settings.lookupKeyPrefix() + "_" + TOP_UP_ROLE + "_" + topUpCode.toLowerCase(Locale.ROOT);
    }

    private String meteredKey(String planCode, UsageMetric metric) {
        return key(planCode, metric.name().toLowerCase(Locale.ROOT));
    }

    private String key(String planCode, String role) {
        return role.isEmpty() ? baseKey(planCode) : baseKey(planCode) + "_" + role;
    }

    /**
     * Price ids by lookup key, refreshed now and then so a price changed in Stripe is picked up without a restart.
     */
    private Map<String, String> prices() {
        PriceCache cached = prices;
        if (cached != null && cached.loadedAt().plus(settings.priceCacheTtl()).isAfter(Instant.now())) {
            return cached.ids();
        }
        List<String> keys = new ArrayList<>();
        catalog.all().forEach(plan -> {
            keys.add(baseKey(plan.code()));
            for (UsageMetric metric : UsageMetric.values()) {
                keys.add(meteredKey(plan.code(), metric));
            }
        });
        topUps.forEach(topUp -> keys.add(topUpKey(topUp.code())));
        Map<String, String> ids = new LinkedHashMap<>();
        for (int start = 0; start < keys.size(); start += LOOKUP_BATCH) {
            String query = keys.subList(start, Math.min(keys.size(), start + LOOKUP_BATCH)).stream()
                    .map(key -> "lookup_keys[]=" + encode(key))
                    .collect(Collectors.joining("&"));
            for (JsonNode price : get("/v1/prices?active=true&limit=100&" + query).path("data")) {
                ids.put(price.path("lookup_key").asString(""), price.path("id").asString(""));
            }
        }
        prices = new PriceCache(Map.copyOf(ids), Instant.now());
        return prices.ids();
    }

    private static String require(Map<String, String> prices, String key) {
        String id = prices.get(key);
        if (id == null) {
            throw new PaymentProviderUnavailableException("Stripe has no active price with lookup key " + key);
        }
        return id;
    }

    private JsonNode get(String path) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
    }

    private JsonNode post(String path, Form form) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.encoded(), StandardCharsets.UTF_8)));
    }

    private JsonNode send(HttpRequest.Builder builder) {
        if (!configured()) {
            throw new PaymentsNotConfiguredException();
        }
        HttpRequest request = builder
                .timeout(settings.requestTimeout())
                .header("Authorization", "Bearer " + settings.secretKey())
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new PaymentProviderUnavailableException("Stripe could not be reached");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentProviderUnavailableException("Interrupted while calling Stripe");
        }
        JsonNode body = parse(response.body());
        if (response.statusCode() / 100 != 2) {
            String message = body.path("error").path("message").asString("");
            String detail = message.substring(0, Math.min(message.length(), MAX_ERROR_CHARS));
            log.warn("Stripe answered {} to {} {}: {}", response.statusCode(), request.method(),
                    request.uri().getPath(), detail);
            throw new PaymentProviderUnavailableException("Stripe answered " + response.statusCode());
        }
        return body;
    }

    private JsonNode parse(String body) {
        try {
            return body == null || body.isBlank() ? json.createObjectNode() : json.readTree(body);
        } catch (JacksonException e) {
            return json.createObjectNode();
        }
    }

    private static String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asString(null);
    }

    private static Instant epoch(JsonNode node) {
        return node.isNumber() ? Instant.ofEpochSecond(node.asLong()) : null;
    }

    private static UUID uuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record PriceCache(Map<String, String> ids, Instant loadedAt) {
    }

    /**
     * Stripe's form encoding, with nested parameters written as {@code a[b][c]}.
     */
    private static final class Form {

        private final List<String> pairs = new ArrayList<>();

        Form add(String name, String value) {
            pairs.add(encode(name) + "=" + encode(Optional.ofNullable(value).orElse("")));
            return this;
        }

        String encoded() {
            return String.join("&", pairs);
        }
    }
}
