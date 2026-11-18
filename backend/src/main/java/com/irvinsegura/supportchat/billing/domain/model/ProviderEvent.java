package com.irvinsegura.supportchat.billing.domain.model;

import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.billing.domain.model.Subscription.ProviderSubscription;
import java.time.Instant;
import java.util.UUID;

/**
 * A notification from the payment provider, verified and read.
 */
public sealed interface ProviderEvent {

    /**
     * The provider's id, the same on every redelivery.
     */
    String id();

    Instant createdAt();

    /**
     * Someone of the workspace finished paying for a plan; the subscription itself is reported separately.
     */
    record CheckoutCompleted(String id, Instant createdAt, UUID workspaceId, String customerId)
            implements ProviderEvent {
    }

    /**
     * A subscription was created, changed or ended.
     *
     * @param workspaceId the workspace the subscription was bought for, when the provider still has it
     */
    record SubscriptionReported(String id, Instant createdAt, UUID workspaceId, ProviderSubscription subscription)
            implements ProviderEvent {
    }

    /**
     * Someone of the workspace paid for a recharge. Payment methods that settle later report it once they do.
     *
     * @param reference the provider's id of the purchase, the same however often it is reported
     * @param customerId the customer the provider charged, created for the purchase if the workspace had none
     */
    record TopUpPaid(String id, Instant createdAt, UUID workspaceId, String customerId, String reference,
            String topUpCode, UsageMetric metric, long quantity) implements ProviderEvent {
    }

    /**
     * Something this platform does not act on.
     */
    record Other(String id, Instant createdAt, String type) implements ProviderEvent {
    }
}
