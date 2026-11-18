package com.irvinsegura.supportchat.billing.infrastructure.config;

import com.irvinsegura.supportchat.billing.application.BillingSettings;
import com.irvinsegura.supportchat.billing.domain.model.Plan;
import com.irvinsegura.supportchat.billing.domain.model.PlanCatalog;
import com.irvinsegura.supportchat.billing.domain.model.TopUp;
import com.irvinsegura.supportchat.billing.domain.port.PaymentProvider;
import com.irvinsegura.supportchat.billing.infrastructure.stripe.StripePaymentProvider;
import java.net.http.HttpClient;
import java.util.Comparator;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class BillingConfiguration {

    @Bean
    BillingSettings billingSettings(BillingProperties properties) {
        List<Plan> plans = properties.plans().entrySet().stream()
                .map(entry -> new Plan(entry.getKey(), entry.getValue().name(), entry.getValue().monthlyPrice(),
                        entry.getValue().included(), entry.getValue().overagePrices()))
                .toList();
        List<TopUp> topUps = properties.topUps().entrySet().stream()
                .map(entry -> new TopUp(entry.getKey(), entry.getValue().name(), entry.getValue().metric(),
                        entry.getValue().quantity(), entry.getValue().price()))
                .sorted(Comparator.comparing(TopUp::metric).thenComparing(TopUp::price))
                .toList();
        return new BillingSettings(properties.currency(), new PlanCatalog(plans, properties.freePlan()),
                properties.gracePeriod(), properties.returnUrl().replaceAll("/+$", ""), topUps);
    }

    @Bean
    PaymentProvider paymentProvider(BillingProperties properties, BillingSettings settings, JsonMapper json) {
        BillingProperties.Stripe stripe = properties.stripe();
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(stripe.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new StripePaymentProvider(http, json, stripe, settings.catalog(), settings.topUps());
    }
}
