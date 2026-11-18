package com.irvinsegura.supportchat.billing.domain.model;

import java.util.Locale;

/**
 * Where a paid subscription stands, as the payment provider reports it.
 */
public enum SubscriptionStatus {
    // No paid subscription: the free plan
    NONE,
    ACTIVE,
    TRIALING,
    // A payment failed and is being retried: the plan keeps working for a grace period
    PAST_DUE,
    // Retries ran out
    UNPAID,
    CANCELED,
    // The first payment has not gone through yet
    INCOMPLETE,
    INCOMPLETE_EXPIRED,
    PAUSED;

    /**
     * Reads a Stripe status such as {@code past_due}; an unknown one counts as not paying.
     */
    public static SubscriptionStatus fromProvider(String status) {
        if (status == null) {
            return UNPAID;
        }
        try {
            return valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UNPAID;
        }
    }

    boolean paying() {
        return this == ACTIVE || this == TRIALING;
    }

    boolean ended() {
        return this == CANCELED || this == INCOMPLETE_EXPIRED;
    }
}
