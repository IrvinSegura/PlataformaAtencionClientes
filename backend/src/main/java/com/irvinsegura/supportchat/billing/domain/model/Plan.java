package com.irvinsegura.supportchat.billing.domain.model;

import com.irvinsegura.supportchat.billing.UsageMetric;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What a workspace pays each month and what that includes. A metric with no included quantity is unlimited. Past the
 * included quantity, a metric with an overage price keeps working and each extra unit is billed; one without stops.
 *
 * @param code           stable identifier, e.g. {@code STARTER}; Stripe prices are found by it
 * @param included       units of each limited metric included every month
 * @param overagePrices  price of each unit past the included ones
 */
public record Plan(String code, String name, BigDecimal monthlyPrice, Map<UsageMetric, Long> included,
        Map<UsageMetric, BigDecimal> overagePrices) {

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,31}");
    private static final int MONEY_SCALE = 4;

    public Plan {
        Objects.requireNonNull(code);
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("A plan code is upper case letters, digits and underscores: " + code);
        }
        Objects.requireNonNull(name);
        if (monthlyPrice == null || monthlyPrice.signum() < 0) {
            throw new IllegalArgumentException("A plan's monthly price cannot be negative");
        }
        included = copy(included);
        overagePrices = copy(overagePrices);
        included.values().forEach(quantity -> {
            if (quantity < 0) {
                throw new IllegalArgumentException("An included quantity cannot be negative");
            }
        });
        overagePrices.values().forEach(price -> {
            if (price.signum() < 0) {
                throw new IllegalArgumentException("An overage price cannot be negative");
            }
        });
    }

    public boolean isFree() {
        return monthlyPrice.signum() == 0 && overagePrices.isEmpty();
    }

    /**
     * Whether one more unit may be used after {@code used} this month.
     */
    public boolean allows(UsageMetric metric, long used) {
        Long limit = included.get(metric);
        return limit == null || used < limit || overagePrices.containsKey(metric);
    }

    /**
     * The same plan with extra units on top of the included ones, as recharges bought this month add. Extra units
     * of a metric the plan does not limit change nothing.
     */
    public Plan withExtra(Map<UsageMetric, Long> extra) {
        if (extra == null || extra.isEmpty()) {
            return this;
        }
        Map<UsageMetric, Long> raised = new EnumMap<>(UsageMetric.class);
        raised.putAll(included);
        extra.forEach((metric, quantity) -> raised.computeIfPresent(metric, (key, limit) -> limit + quantity));
        return new Plan(code, name, monthlyPrice, raised, overagePrices);
    }

    public Optional<Long> limit(UsageMetric metric) {
        return Optional.ofNullable(included.get(metric));
    }

    public Optional<BigDecimal> overagePrice(UsageMetric metric) {
        return Optional.ofNullable(overagePrices.get(metric));
    }

    public long overage(UsageMetric metric, long used) {
        Long limit = included.get(metric);
        return limit == null ? 0 : Math.max(0, used - limit);
    }

    public BigDecimal overageCost(UsageMetric metric, long used) {
        return overagePrice(metric)
                .map(price -> price.multiply(BigDecimal.valueOf(overage(metric, used))))
                .orElse(BigDecimal.ZERO)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static <V> Map<UsageMetric, V> copy(Map<UsageMetric, V> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new EnumMap<>(values));
    }
}
