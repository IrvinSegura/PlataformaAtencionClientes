package com.irvinsegura.supportchat.support;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base of the integration tests that reach outside APIs: they all point to {@link ExternalServices} and, since the
 * configuration is declared once here, share a single application context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class WithExternalServices {

    public static final String STRIPE_WEBHOOK_SECRET = "whsec_test_123";

    /**
     * The application's clock, which a test may move ahead.
     */
    @Autowired
    protected TestClock clock;

    @DynamicPropertySource
    static void externalServices(DynamicPropertyRegistry registry) {
        registry.add("supportchat.channels.whatsapp.graph-base-url", () -> ExternalServices.baseUrl() + "/graph");
        registry.add("supportchat.channels.meta.graph-base-url", () -> ExternalServices.baseUrl() + "/meta");
        registry.add("supportchat.billing.stripe.base-url", () -> ExternalServices.baseUrl() + "/stripe");
        registry.add("supportchat.actions.integrations.shopify-url",
                () -> ExternalServices.baseUrl() + "/shopify/{store}");
        registry.add("supportchat.actions.integrations.zendesk-url",
                () -> ExternalServices.baseUrl() + "/zendesk/{subdomain}");
        registry.add("supportchat.billing.stripe.secret-key", () -> "sk_test_123");
        registry.add("supportchat.billing.stripe.webhook-secret", () -> STRIPE_WEBHOOK_SECRET);
        // Every trace is kept and sent at once, as the OpenTelemetry collector would receive it
        registry.add("management.opentelemetry.tracing.export.otlp.endpoint",
                () -> ExternalServices.baseUrl() + "/otlp/v1/traces");
        registry.add("management.opentelemetry.tracing.export.schedule-delay", () -> "200ms");
        registry.add("management.tracing.sampling.probability", () -> "1.0");
    }

}
