package com.irvinsegura.supportchat.billing.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A workspace's plan and where its payments stand. The payment provider owns the truth: this keeps the last state it
 * reported, applying its notifications in the order they happened even when they arrive in another.
 * <p>
 * A paid plan applies while the subscription is active. When a payment fails it keeps applying for a grace period,
 * while the provider retries the charge; after that, or once the subscription ends, the workspace is on the free plan.
 */
public final class Subscription {

    private final UUID workspaceId;
    private String planCode;
    private SubscriptionStatus status;
    private String customerId;
    private String subscriptionId;
    private Instant currentPeriodEnd;
    private boolean cancelAtPeriodEnd;
    private Instant pastDueSince;
    private Instant lastEventAt;
    private Instant updatedAt;

    private Subscription(UUID workspaceId, String planCode, SubscriptionStatus status, String customerId,
            String subscriptionId, Instant currentPeriodEnd, boolean cancelAtPeriodEnd, Instant pastDueSince,
            Instant lastEventAt, Instant updatedAt) {
        this.workspaceId = Objects.requireNonNull(workspaceId);
        this.planCode = planCode;
        this.status = Objects.requireNonNull(status);
        this.customerId = customerId;
        this.subscriptionId = subscriptionId;
        this.currentPeriodEnd = currentPeriodEnd;
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
        this.pastDueSince = pastDueSince;
        this.lastEventAt = lastEventAt;
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    /**
     * A workspace that never paid: the free plan.
     */
    public static Subscription none(UUID workspaceId, Instant now) {
        return new Subscription(workspaceId, null, SubscriptionStatus.NONE, null, null, null, false, null, null, now);
    }

    public static Subscription restore(UUID workspaceId, String planCode, SubscriptionStatus status,
            String customerId, String subscriptionId, Instant currentPeriodEnd, boolean cancelAtPeriodEnd,
            Instant pastDueSince, Instant lastEventAt, Instant updatedAt) {
        return new Subscription(workspaceId, planCode, status, customerId, subscriptionId, currentPeriodEnd,
                cancelAtPeriodEnd, pastDueSince, lastEventAt, updatedAt);
    }

    /**
     * The plan whose terms apply right now.
     */
    public Plan effectivePlan(PlanCatalog catalog, Instant now, Duration gracePeriod) {
        if (status.paying() || inGrace(now, gracePeriod)) {
            return catalog.resolve(planCode);
        }
        return catalog.free();
    }

    /**
     * When a workspace whose payment failed drops to the free plan, if it does not pay before.
     */
    public Optional<Instant> graceEndsAt(Duration gracePeriod) {
        return status == SubscriptionStatus.PAST_DUE && pastDueSince != null
                ? Optional.of(pastDueSince.plus(gracePeriod))
                : Optional.empty();
    }

    /**
     * Whether a paid subscription exists that a plan change should modify, rather than a new checkout.
     */
    public boolean hasLiveSubscription() {
        return subscriptionId != null && status != SubscriptionStatus.NONE && status != SubscriptionStatus.CANCELED
                && status != SubscriptionStatus.INCOMPLETE_EXPIRED;
    }

    /**
     * Remembers the customer the provider created at checkout.
     */
    public void linkCustomer(String customerId, Instant now) {
        this.customerId = Objects.requireNonNull(customerId);
        this.updatedAt = now;
    }

    /**
     * Applies the provider's state of a subscription as of {@code eventAt}.
     *
     * @return false when it was ignored: older than what is known, or about a subscription that already ended while
     *         another one is live
     */
    public boolean apply(ProviderSubscription reported, Instant eventAt, Instant now) {
        if (lastEventAt != null && eventAt.isBefore(lastEventAt)) {
            return false;
        }
        boolean other = subscriptionId != null && !subscriptionId.equals(reported.subscriptionId());
        if (other && hasLiveSubscription() && reported.status().ended()) {
            // An old subscription's end must not cancel the one that replaced it
            return false;
        }
        boolean wasLate = status == SubscriptionStatus.PAST_DUE || status == SubscriptionStatus.UNPAID;
        boolean late = reported.status() == SubscriptionStatus.PAST_DUE
                || reported.status() == SubscriptionStatus.UNPAID;
        if (late && !wasLate) {
            pastDueSince = eventAt;
        } else if (!late) {
            pastDueSince = null;
        }
        if (reported.planCode() != null) {
            planCode = reported.planCode();
        }
        status = reported.status();
        customerId = reported.customerId();
        subscriptionId = reported.subscriptionId();
        currentPeriodEnd = reported.currentPeriodEnd();
        cancelAtPeriodEnd = reported.cancelAtPeriodEnd();
        lastEventAt = eventAt;
        updatedAt = now;
        return true;
    }

    private boolean inGrace(Instant now, Duration gracePeriod) {
        return graceEndsAt(gracePeriod).map(now::isBefore).orElse(false);
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    /**
     * The plan paid for, which may not apply right now; null when none was ever paid for.
     */
    public String planCode() {
        return planCode;
    }

    public SubscriptionStatus status() {
        return status;
    }

    public String customerId() {
        return customerId;
    }

    public String subscriptionId() {
        return subscriptionId;
    }

    public Instant currentPeriodEnd() {
        return currentPeriodEnd;
    }

    public boolean cancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public Instant pastDueSince() {
        return pastDueSince;
    }

    public Instant lastEventAt() {
        return lastEventAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * A subscription as the payment provider reports it.
     *
     * @param planCode null when its prices belong to no plan on offer
     */
    public record ProviderSubscription(String subscriptionId, String customerId, String planCode,
            SubscriptionStatus status, Instant currentPeriodEnd, boolean cancelAtPeriodEnd) {

        public ProviderSubscription {
            Objects.requireNonNull(subscriptionId);
            Objects.requireNonNull(customerId);
            Objects.requireNonNull(status);
        }
    }
}
