package com.irvinsegura.supportchat.billing.application;

import com.irvinsegura.supportchat.billing.domain.model.PlanCatalog;
import com.irvinsegura.supportchat.billing.domain.model.TopUp;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @param gracePeriod how long a paid plan keeps applying after a payment fails, while the provider retries it
 * @param returnUrl   the panel page people come back to from the payment provider's pages
 * @param topUps      recharges on offer, cheapest first
 */
public record BillingSettings(String currency, PlanCatalog catalog, Duration gracePeriod, String returnUrl,
        List<TopUp> topUps) {

    public BillingSettings {
        Objects.requireNonNull(currency);
        Objects.requireNonNull(catalog);
        Objects.requireNonNull(gracePeriod);
        Objects.requireNonNull(returnUrl);
        topUps = topUps == null ? List.of() : List.copyOf(topUps);
    }

    public Optional<TopUp> topUp(String code) {
        return topUps.stream().filter(topUp -> topUp.code().equals(code)).findFirst();
    }
}
