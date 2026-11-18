package com.irvinsegura.supportchat.billing.domain.model;

import com.irvinsegura.supportchat.billing.UsageMetric;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A one-time purchase of extra units of a limited metric, added to the plan's allowance for the calendar month it
 * was paid in. Offered to plans that stop at that metric's limit: one that bills the extra needs no recharge.
 *
 * @param code     stable identifier, e.g. {@code ANSWERS_500}; its Stripe price is found by it
 * @param quantity the extra units
 */
public record TopUp(String code, String name, UsageMetric metric, long quantity, BigDecimal price) {

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,31}");

    public TopUp {
        Objects.requireNonNull(code);
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("A recharge code is upper case letters, digits and underscores: " + code);
        }
        Objects.requireNonNull(name);
        Objects.requireNonNull(metric);
        if (quantity <= 0) {
            throw new IllegalArgumentException("A recharge adds at least one unit");
        }
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("A recharge must cost something");
        }
    }

    /**
     * Whether the plan needs it: it limits the metric and stops there instead of billing the extra.
     */
    public boolean fits(Plan plan) {
        return plan.limit(metric).isPresent() && plan.overagePrice(metric).isEmpty();
    }
}
