package com.irvinsegura.supportchat.billing.infrastructure.config;

import com.irvinsegura.supportchat.billing.UsageMetric;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The plans on offer and the payment provider that charges them.
 *
 * @param freePlan    the plan of workspaces that do not pay
 * @param gracePeriod how long a paid plan keeps applying after a payment fails
 * @param returnUrl   the panel's billing page, where the provider's pages send people back
 * @param topUps      recharges on offer, by code; none disables them
 */
@Validated
@ConfigurationProperties("supportchat.billing")
public record BillingProperties(
        @NotBlank String currency,
        @NotBlank String freePlan,
        @NotNull Duration gracePeriod,
        @NotBlank String returnUrl,
        @NotEmpty Map<String, @Valid PlanProperties> plans,
        Map<String, @Valid TopUpProperties> topUps,
        @NotNull @Valid Stripe stripe) {

    public BillingProperties {
        plans = plans == null ? Map.of() : Map.copyOf(plans);
        topUps = topUps == null ? Map.of() : Map.copyOf(topUps);
    }

    /**
     * @param quantity extra units of the metric, for the month the recharge is paid in
     * @param price    paid once
     */
    public record TopUpProperties(
            @NotBlank String name,
            @NotNull UsageMetric metric,
            @NotNull @Min(1) Long quantity,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal price) {
    }

    /**
     * @param included      units included each month; a metric left out is unlimited
     * @param overagePrices price of each unit past the included ones; a limited metric left out stops at its limit
     */
    public record PlanProperties(
            @NotBlank String name,
            @NotNull @DecimalMin("0") BigDecimal monthlyPrice,
            Map<UsageMetric, @NotNull @Min(0) Long> included,
            Map<UsageMetric, @NotNull @DecimalMin("0") BigDecimal> overagePrices) {

        public PlanProperties {
            included = included == null ? Map.of() : Map.copyOf(included);
            overagePrices = overagePrices == null ? Map.of() : Map.copyOf(overagePrices);
        }
    }

    /**
     * Prices are found by lookup key: {@code <prefix>_<plan>} for a plan's monthly price and
     * {@code <prefix>_<plan>_<metric>} for its metered overage, all in lower case.
     *
     * @param secretKey     empty disables payments: every workspace is on the free plan
     * @param meterEvents   the event name of the meter of each metric
     * @param signatureTolerance how old a signed notification may be
     */
    public record Stripe(
            String secretKey,
            String webhookSecret,
            @NotBlank String baseUrl,
            @NotNull Duration connectTimeout,
            @NotNull Duration requestTimeout,
            @NotBlank String lookupKeyPrefix,
            Map<UsageMetric, @NotBlank String> meterEvents,
            @NotNull Duration signatureTolerance,
            @NotNull Duration priceCacheTtl) {

        public Stripe {
            meterEvents = meterEvents == null ? Map.of() : Map.copyOf(meterEvents);
        }

        public boolean configured() {
            return secretKey != null && !secretKey.isBlank() && webhookSecret != null && !webhookSecret.isBlank();
        }
    }
}
