package com.irvinsegura.supportchat.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.billing.domain.model.Subscription.ProviderSubscription;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionTest {

    private static final Duration GRACE = Duration.ofDays(7);
    private static final Instant T0 = Instant.parse("2026-03-01T00:00:00Z");
    private static final Plan FREE = new Plan("FREE", "Gratis", BigDecimal.ZERO,
            Map.of(UsageMetric.ANSWERS, 100L), Map.of());
    private static final Plan PRO = new Plan("PRO", "Profesional", new BigDecimal("99"),
            Map.of(UsageMetric.ANSWERS, 10_000L), Map.of());
    private static final PlanCatalog CATALOG = new PlanCatalog(List.of(FREE, PRO), "FREE");

    private final Subscription subscription = Subscription.none(UUID.randomUUID(), T0);

    @Test
    void aWorkspaceThatNeverPaidIsOnTheFreePlan() {
        assertThat(subscription.effectivePlan(CATALOG, T0, GRACE)).isEqualTo(FREE);
        assertThat(subscription.hasLiveSubscription()).isFalse();
    }

    @Test
    void anActiveSubscriptionGivesItsPlan() {
        assertThat(subscription.apply(reported("sub_1", "PRO", SubscriptionStatus.ACTIVE), T0, T0)).isTrue();

        assertThat(subscription.effectivePlan(CATALOG, T0.plusSeconds(60), GRACE)).isEqualTo(PRO);
        assertThat(subscription.hasLiveSubscription()).isTrue();
        assertThat(subscription.customerId()).isEqualTo("cus_1");
    }

    @Test
    void aFailedPaymentKeepsThePlanDuringTheGracePeriodOnly() {
        subscription.apply(reported("sub_1", "PRO", SubscriptionStatus.ACTIVE), T0, T0);
        Instant failed = T0.plus(Duration.ofDays(30));
        subscription.apply(reported("sub_1", "PRO", SubscriptionStatus.PAST_DUE), failed, failed);
        // A retry that fails again does not restart the grace period
        subscription.apply(reported("sub_1", "PRO", SubscriptionStatus.PAST_DUE), failed.plus(Duration.ofDays(3)),
                failed.plus(Duration.ofDays(3)));

        assertThat(subscription.graceEndsAt(GRACE)).contains(failed.plus(GRACE));
        assertThat(subscription.effectivePlan(CATALOG, failed.plus(Duration.ofDays(6)), GRACE)).isEqualTo(PRO);
        assertThat(subscription.effectivePlan(CATALOG, failed.plus(Duration.ofDays(8)), GRACE)).isEqualTo(FREE);

        Instant paid = failed.plus(Duration.ofDays(9));
        subscription.apply(reported("sub_1", "PRO", SubscriptionStatus.ACTIVE), paid, paid);
        assertThat(subscription.pastDueSince()).isNull();
        assertThat(subscription.effectivePlan(CATALOG, paid, GRACE)).isEqualTo(PRO);
    }

    @Test
    void anUnpaidOrCanceledSubscriptionIsOnTheFreePlan() {
        subscription.apply(reported("sub_1", "PRO", SubscriptionStatus.UNPAID), T0, T0);
        assertThat(subscription.effectivePlan(CATALOG, T0, GRACE)).isEqualTo(FREE);

        subscription.apply(reported("sub_1", "PRO", SubscriptionStatus.CANCELED), T0.plusSeconds(1), T0);
        assertThat(subscription.effectivePlan(CATALOG, T0, GRACE)).isEqualTo(FREE);
        assertThat(subscription.hasLiveSubscription()).isFalse();
        assertThat(subscription.planCode()).isEqualTo("PRO");
    }

    @Test
    void anOlderNotificationArrivingLateIsIgnored() {
        subscription.apply(reported("sub_1", "PRO", SubscriptionStatus.CANCELED), T0.plusSeconds(10), T0);

        assertThat(subscription.apply(reported("sub_1", "PRO", SubscriptionStatus.ACTIVE), T0, T0)).isFalse();
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.CANCELED);
    }

    @Test
    void theEndOfAnOldSubscriptionDoesNotCancelItsReplacement() {
        subscription.apply(reported("sub_2", "PRO", SubscriptionStatus.ACTIVE), T0, T0);

        assertThat(subscription.apply(reported("sub_1", "PRO", SubscriptionStatus.CANCELED), T0.plusSeconds(5), T0))
                .isFalse();
        assertThat(subscription.subscriptionId()).isEqualTo("sub_2");
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void aSubscriptionWhosePricesBelongToNoPlanKeepsThePlanItHad() {
        subscription.apply(reported("sub_1", "PRO", SubscriptionStatus.ACTIVE), T0, T0);
        subscription.apply(reported("sub_1", null, SubscriptionStatus.ACTIVE), T0.plusSeconds(1), T0);

        assertThat(subscription.planCode()).isEqualTo("PRO");
    }

    @Test
    void unknownProviderStatusesCountAsUnpaid() {
        assertThat(SubscriptionStatus.fromProvider("something_new")).isEqualTo(SubscriptionStatus.UNPAID);
        assertThat(SubscriptionStatus.fromProvider(null)).isEqualTo(SubscriptionStatus.UNPAID);
        assertThat(SubscriptionStatus.fromProvider("past_due")).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    private static ProviderSubscription reported(String id, String plan, SubscriptionStatus status) {
        return new ProviderSubscription(id, "cus_1", plan, status, T0.plus(Duration.ofDays(30)), false);
    }
}
