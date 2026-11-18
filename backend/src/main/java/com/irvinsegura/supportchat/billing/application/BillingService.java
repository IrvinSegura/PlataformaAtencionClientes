package com.irvinsegura.supportchat.billing.application;

import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.billing.application.BillingViews.BillingOverview;
import com.irvinsegura.supportchat.billing.application.BillingViews.PlanChange;
import com.irvinsegura.supportchat.billing.application.BillingViews.PlanView;
import com.irvinsegura.supportchat.billing.application.BillingViews.PortalSession;
import com.irvinsegura.supportchat.billing.application.BillingViews.Quota;
import com.irvinsegura.supportchat.billing.application.BillingViews.TopUpPurchase;
import com.irvinsegura.supportchat.billing.application.BillingViews.TopUpView;
import com.irvinsegura.supportchat.billing.domain.model.Invoice;
import com.irvinsegura.supportchat.billing.domain.model.NoPaymentAccountException;
import com.irvinsegura.supportchat.billing.domain.model.PaymentsNotConfiguredException;
import com.irvinsegura.supportchat.billing.domain.model.Plan;
import com.irvinsegura.supportchat.billing.domain.model.PlanNotForSaleException;
import com.irvinsegura.supportchat.billing.domain.model.PlanNotFoundException;
import com.irvinsegura.supportchat.billing.domain.model.ProviderEvent;
import com.irvinsegura.supportchat.billing.domain.model.ProviderEvent.CheckoutCompleted;
import com.irvinsegura.supportchat.billing.domain.model.ProviderEvent.Other;
import com.irvinsegura.supportchat.billing.domain.model.ProviderEvent.SubscriptionReported;
import com.irvinsegura.supportchat.billing.domain.model.ProviderEvent.TopUpPaid;
import com.irvinsegura.supportchat.billing.domain.model.Subscription;
import com.irvinsegura.supportchat.billing.domain.model.TopUp;
import com.irvinsegura.supportchat.billing.domain.model.TopUpNotForSaleException;
import com.irvinsegura.supportchat.billing.domain.model.TopUpNotFoundException;
import com.irvinsegura.supportchat.billing.domain.port.PaymentProvider;
import com.irvinsegura.supportchat.billing.domain.port.PaymentProvider.Checkout;
import com.irvinsegura.supportchat.billing.domain.port.PaymentProvider.TopUpCheckout;
import com.irvinsegura.supportchat.billing.domain.port.SubscriptionRepository;
import com.irvinsegura.supportchat.billing.domain.port.UsageRepository;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Plans and payments of the workspace bound to the current code, and the payment provider's notifications. Calls to
 * the provider happen outside any transaction: a slow provider must not hold a database connection.
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final CurrentPlan currentPlan;
    private final SubscriptionRepository subscriptions;
    private final UsageRepository usage;
    private final PaymentProvider payments;
    private final BillingSettings settings;
    private final TransactionTemplate transactions;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    BillingService(CurrentPlan currentPlan, SubscriptionRepository subscriptions, UsageRepository usage,
            PaymentProvider payments, BillingSettings settings, PlatformTransactionManager transactionManager,
            Clock clock) {
        this.currentPlan = currentPlan;
        this.subscriptions = subscriptions;
        this.usage = usage;
        this.payments = payments;
        this.settings = settings;
        this.transactions = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    public BillingOverview overview() {
        Instant now = clock.instant();
        Snapshot snapshot = readOnly.execute(status -> {
            Subscription subscription = currentPlan.subscription();
            Plan plan = subscription.effectivePlan(settings.catalog(), now, settings.gracePeriod());
            Map<UsageMetric, Long> extra = currentPlan.topUps(currentPlan.currentMonth());
            Plan withExtra = plan.withExtra(extra);
            List<Quota> quotas = BillingViews.LIMITED.stream()
                    .map(metric -> Quota.of(withExtra, metric, currentPlan.usedThisMonth(metric), extra))
                    .toList();
            return new Snapshot(subscription, plan, quotas);
        });
        boolean paymentsEnabled = payments.configured();
        List<PlanView> plans = settings.catalog().all().stream()
                .map(plan -> PlanView.of(plan, paymentsEnabled && !plan.isFree() && sells(plan)))
                .toList();
        List<TopUpView> topUps = settings.topUps().stream()
                .map(topUp -> {
                    boolean available = topUp.fits(snapshot.plan());
                    return TopUpView.of(topUp, available, available && paymentsEnabled && sells(topUp));
                })
                .toList();
        Subscription subscription = snapshot.subscription();
        BigDecimal total = snapshot.quotas().stream()
                .map(Quota::overageCost)
                .reduce(snapshot.plan().monthlyPrice(), BigDecimal::add);
        return new BillingOverview(PlanView.of(snapshot.plan(), false), subscription.planCode(),
                subscription.status(), subscription.currentPeriodEnd(), subscription.cancelAtPeriodEnd(),
                subscription.graceEndsAt(settings.gracePeriod()).orElse(null), paymentsEnabled,
                subscription.customerId() != null, settings.currency(), snapshot.quotas(), total, plans, topUps);
    }

    /**
     * Buys a recharge on the provider's checkout page. Its units are added for the month once the provider confirms
     * the payment; only plans that stop at the metric's limit can buy one, since the others bill the extra.
     */
    public TopUpPurchase buyTopUp(String topUpCode) {
        TopUp topUp = settings.topUp(topUpCode).orElseThrow(TopUpNotFoundException::new);
        if (!payments.configured()) {
            throw new PaymentsNotConfiguredException();
        }
        Subscription subscription = readOnly.execute(status -> currentPlan.subscription());
        Plan plan = subscription.effectivePlan(settings.catalog(), clock.instant(), settings.gracePeriod());
        if (!topUp.fits(plan)) {
            throw new TopUpNotForSaleException("The plan " + plan.code() + " bills " + topUp.metric()
                    + " past its limit: a recharge is not needed");
        }
        if (!payments.sellsTopUp(topUp.code())) {
            throw new TopUpNotForSaleException("The payment provider has no price for recharge " + topUp.code());
        }
        String url = payments.buyTopUp(new TopUpCheckout(TenantContext.requireWorkspaceId(), topUp,
                subscription.customerId(), settings.returnUrl() + "?top-up=success",
                settings.returnUrl() + "?top-up=cancelled"));
        return new TopUpPurchase(url);
    }

    /**
     * Moves the workspace to a plan. A first paid plan is bought on the provider's checkout page; a live
     * subscription changes in place, and choosing the free plan ends it once the paid period is over. The new terms
     * apply when the provider confirms the change.
     */
    public PlanChange choosePlan(String planCode) {
        Plan plan = settings.catalog().find(planCode).orElseThrow(PlanNotFoundException::new);
        if (!payments.configured()) {
            throw new PaymentsNotConfiguredException();
        }
        Subscription subscription = readOnly.execute(status -> currentPlan.subscription());
        if (subscription.hasLiveSubscription()) {
            if (plan.isFree()) {
                payments.cancelAtPeriodEnd(subscription.subscriptionId());
                return new PlanChange(null);
            }
            if (plan.code().equals(subscription.planCode()) && !subscription.cancelAtPeriodEnd()) {
                throw new PlanNotForSaleException("The workspace is already on this plan");
            }
            requireSold(plan);
            payments.changePlan(subscription.subscriptionId(), plan.code());
            return new PlanChange(null);
        }
        if (plan.isFree()) {
            throw new PlanNotForSaleException("The workspace is already on the free plan");
        }
        requireSold(plan);
        Instant nextMonth = YearMonth.from(clock.instant().atZone(ZoneOffset.UTC)).plusMonths(1).atDay(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        String url = payments.checkout(new Checkout(TenantContext.requireWorkspaceId(), plan.code(),
                subscription.customerId(), nextMonth, settings.returnUrl() + "?checkout=success",
                settings.returnUrl() + "?checkout=cancelled"));
        return new PlanChange(url);
    }

    public PortalSession portal() {
        return new PortalSession(payments.portal(requireCustomer(), settings.returnUrl()));
    }

    public List<Invoice> invoices() {
        Subscription subscription = readOnly.execute(status -> currentPlan.subscription());
        return subscription.customerId() == null || !payments.configured() ? List.of()
                : payments.invoices(subscription.customerId());
    }

    /**
     * Applies a notification from the payment provider. Each one is applied once, in a single transaction with
     * the workspace it concerns bound, so a failure leaves it unapplied for the provider to deliver again.
     */
    public void receive(byte[] body, String signature) {
        Instant now = clock.instant();
        ProviderEvent event = payments.read(body, signature, now);
        switch (event) {
            case Other other -> log.debug("Ignored payment notification {} of type {}", other.id(), other.type());
            case CheckoutCompleted completed -> {
                if (completed.workspaceId() == null || completed.customerId() == null) {
                    log.warn("Payment notification {} names no workspace or customer", completed.id());
                    return;
                }
                apply(completed.workspaceId(), event, now, subscription -> {
                    subscriptions.linkCustomer(completed.workspaceId(), completed.customerId());
                    if (subscription.customerId() == null) {
                        subscription.linkCustomer(completed.customerId(), now);
                    }
                    return true;
                });
            }
            case TopUpPaid paid -> {
                if (paid.workspaceId() == null || paid.reference().isBlank()) {
                    log.warn("Payment notification {} names no workspace or purchase", paid.id());
                    return;
                }
                Instant paidAt = paid.createdAt() == null ? now : paid.createdAt();
                YearMonth month = YearMonth.from(paidAt.atZone(ZoneOffset.UTC));
                apply(paid.workspaceId(), event, now, subscription -> {
                    if (usage.addTopUp(paid.workspaceId(), month, paid.metric(), paid.quantity(), paid.topUpCode(),
                            paid.reference(), paidAt)) {
                        // The allowance grew: its shares are told again as usage reaches them
                        usage.forgetAlerts(month, paid.metric());
                    }
                    if (paid.customerId() == null) {
                        return false;
                    }
                    subscriptions.linkCustomer(paid.workspaceId(), paid.customerId());
                    if (subscription.customerId() == null) {
                        subscription.linkCustomer(paid.customerId(), now);
                        return true;
                    }
                    return false;
                });
            }
            case SubscriptionReported reported -> {
                Optional<UUID> workspace = Optional.ofNullable(reported.workspaceId())
                        .or(() -> readOnly.execute(status -> subscriptions.workspaceOfCustomer(
                                reported.subscription().customerId())));
                if (workspace.isEmpty()) {
                    log.warn("Payment notification {} is about a customer of no workspace", reported.id());
                    return;
                }
                apply(workspace.get(), event, now, subscription -> {
                    subscriptions.linkCustomer(workspace.get(), reported.subscription().customerId());
                    return subscription.apply(reported.subscription(), reported.createdAt(), now);
                });
            }
        }
    }

    private void apply(UUID workspaceId, ProviderEvent event, Instant now, Change change) {
        try {
            TenantContext.runAs(workspaceId, () -> transactions.executeWithoutResult(status -> {
                if (!subscriptions.firstEvent(event.id(), now)) {
                    return;
                }
                Subscription subscription = subscriptions.find().orElseGet(() -> Subscription.none(workspaceId, now));
                if (change.applyTo(subscription)) {
                    subscriptions.save(subscription);
                }
            }));
        } catch (DataIntegrityViolationException e) {
            // The workspace was deleted: nothing left to bill, and delivering it again would not help
            log.warn("Ignored payment notification {} for missing workspace {}", event.id(), workspaceId);
        }
    }

    private String requireCustomer() {
        if (!payments.configured()) {
            throw new PaymentsNotConfiguredException();
        }
        Subscription subscription = readOnly.execute(status -> currentPlan.subscription());
        if (subscription.customerId() == null) {
            throw new NoPaymentAccountException();
        }
        return subscription.customerId();
    }

    private void requireSold(Plan plan) {
        if (!payments.sells(plan.code())) {
            throw new PlanNotForSaleException("The payment provider has no prices for plan " + plan.code());
        }
    }

    private boolean sells(TopUp topUp) {
        try {
            return payments.sellsTopUp(topUp.code());
        } catch (RuntimeException e) {
            log.warn("Could not check the price of recharge {}: {}", topUp.code(), e.getMessage());
            return false;
        }
    }

    private boolean sells(Plan plan) {
        try {
            return payments.sells(plan.code());
        } catch (RuntimeException e) {
            // The overview still loads while the provider is down; buying waits until it is back
            log.warn("Could not check the prices of plan {}: {}", plan.code(), e.getMessage());
            return false;
        }
    }

    @FunctionalInterface
    private interface Change {

        /**
         * @return whether the subscription changed and must be saved
         */
        boolean applyTo(Subscription subscription);
    }

    private record Snapshot(Subscription subscription, Plan plan, List<Quota> quotas) {
    }
}
