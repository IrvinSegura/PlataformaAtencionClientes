package com.irvinsegura.supportchat.billing.application;

import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.billing.domain.model.Plan;
import com.irvinsegura.supportchat.billing.domain.model.SubscriptionStatus;
import com.irvinsegura.supportchat.billing.domain.model.TopUp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What the panel shows about plans and payments.
 */
public final class BillingViews {

    /**
     * Metrics a plan limits; tokens follow from answers and are not limited on their own.
     */
    public static final List<UsageMetric> LIMITED = List.of(UsageMetric.ANSWERS, UsageMetric.ACTION_RUNS,
            UsageMetric.WHATSAPP_MESSAGES);

    private BillingViews() {
    }

    /**
     * @param purchasable whether it can be bought here: it costs something and the payment provider sells it
     */
    public record PlanView(String code, String name, BigDecimal monthlyPrice, Map<UsageMetric, Long> included,
            Map<UsageMetric, BigDecimal> overagePrices, boolean purchasable) {

        static PlanView of(Plan plan, boolean purchasable) {
            return new PlanView(plan.code(), plan.name(), plan.monthlyPrice(), plan.included(), plan.overagePrices(),
                    purchasable);
        }
    }

    /**
     * One limited metric this month.
     *
     * @param included     null when unlimited; it counts the recharges bought for the month
     * @param extra        how many of the included units recharges added
     * @param overagePrice null when the plan stops at its limit instead
     * @param exhausted    the limit is reached and nothing more is allowed until next month
     */
    public record Quota(UsageMetric metric, long used, @Nullable Long included, long extra,
            @Nullable BigDecimal overagePrice, long overage, BigDecimal overageCost, boolean exhausted) {

        /**
         * @param plan  the plan with the month's recharges already added
         * @param extra the recharges of the month, by metric
         */
        static Quota of(Plan plan, UsageMetric metric, long used, Map<UsageMetric, Long> extra) {
            Long included = plan.limit(metric).orElse(null);
            return new Quota(metric, used, included, included == null ? 0 : extra.getOrDefault(metric, 0L),
                    plan.overagePrice(metric).orElse(null), plan.overage(metric, used), plan.overageCost(metric, used),
                    !plan.allows(metric, used));
        }
    }

    /**
     * A recharge on offer.
     *
     * @param available   the plan that applies now stops at this metric's limit, so the recharge would help
     * @param purchasable available, and the payment provider sells it
     */
    public record TopUpView(String code, String name, UsageMetric metric, long quantity, BigDecimal price,
            boolean available, boolean purchasable) {

        static TopUpView of(TopUp topUp, boolean available, boolean purchasable) {
            return new TopUpView(topUp.code(), topUp.name(), topUp.metric(), topUp.quantity(), topUp.price(),
                    available, purchasable);
        }
    }

    /**
     * @param plan            the plan whose terms apply now
     * @param paidPlanCode    the plan paid for, which differs from {@code plan} once payments stopped
     * @param graceEndsAt     when the workspace drops to the free plan if the failed payment is not settled
     * @param paymentsEnabled whether plans can be bought on this platform
     * @param hasPaymentAccount whether there are invoices and payment details to manage
     * @param estimatedTotal  the plan's price plus this month's overage so far; recharges are paid apart
     * @param topUps          the recharges on offer
     */
    public record BillingOverview(PlanView plan, @Nullable String paidPlanCode, SubscriptionStatus status,
            @Nullable Instant currentPeriodEnd, boolean cancelAtPeriodEnd, @Nullable Instant graceEndsAt,
            boolean paymentsEnabled, boolean hasPaymentAccount, String currency, List<Quota> quotas,
            BigDecimal estimatedTotal, List<PlanView> plans, List<TopUpView> topUps) {
    }

    /**
     * @param checkoutUrl where to pay for the new plan; null when the existing subscription was changed in place
     */
    public record PlanChange(@Nullable String checkoutUrl) {
    }

    /**
     * @param checkoutUrl where to pay for the recharge
     */
    public record TopUpPurchase(String checkoutUrl) {
    }

    public record PortalSession(String url) {
    }
}
