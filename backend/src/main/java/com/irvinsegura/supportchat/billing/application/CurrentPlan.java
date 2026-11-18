package com.irvinsegura.supportchat.billing.application;

import com.irvinsegura.supportchat.billing.UsageAllowance;
import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.billing.WorkspacePlan;
import com.irvinsegura.supportchat.billing.WorkspacePlans;
import com.irvinsegura.supportchat.billing.domain.model.Plan;
import com.irvinsegura.supportchat.billing.domain.model.Subscription;
import com.irvinsegura.supportchat.billing.domain.port.SubscriptionRepository;
import com.irvinsegura.supportchat.billing.domain.port.UsageRepository;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import com.irvinsegura.supportchat.workspaces.DemoWorkspace;
import com.irvinsegura.supportchat.workspaces.Workspaces;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The plan whose terms apply to the workspace bound to the current code, and what it still allows this month. A
 * workspace the platform suspended is allowed nothing, whatever its plan.
 */
@Service
public class CurrentPlan implements UsageAllowance, WorkspacePlans {

    private final SubscriptionRepository subscriptions;
    private final UsageRepository usage;
    private final BillingSettings settings;
    private final Workspaces workspaces;
    private final DemoWorkspace demo;
    private final Clock clock;

    CurrentPlan(SubscriptionRepository subscriptions, UsageRepository usage, BillingSettings settings,
            Workspaces workspaces, DemoWorkspace demo, Clock clock) {
        this.subscriptions = subscriptions;
        this.usage = usage;
        this.settings = settings;
        this.workspaces = workspaces;
        this.demo = demo;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Subscription subscription() {
        return subscriptions.find()
                .orElseGet(() -> Subscription.none(TenantContext.requireWorkspaceId(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public Plan plan() {
        return subscription().effectivePlan(settings.catalog(), clock.instant(), settings.gracePeriod());
    }

    /**
     * The plan that applies now, with the recharges bought for the month on top of what it includes.
     */
    @Transactional(readOnly = true)
    public Plan planIn(YearMonth month) {
        return plan().withExtra(usage.topUps(month));
    }

    /**
     * Extra units bought for the month, by metric.
     */
    @Transactional(readOnly = true)
    public Map<UsageMetric, Long> topUps(YearMonth month) {
        return usage.topUps(month);
    }

    // Joins the caller's transaction
    @Override
    @Transactional(readOnly = true)
    public boolean allows(UsageMetric metric) {
        UUID workspaceId = TenantContext.requireWorkspaceId();
        if (workspaces.suspension(workspaceId).isPresent()) {
            return false;
        }
        // The public demo's daily cap replaces its plan's monthly one: it runs on the platform's account
        if (metric == UsageMetric.ANSWERS && demo.is(workspaceId)) {
            LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
            return usage.total(metric, today, today) < demo.dailyAnswers();
        }
        Plan plan = plan();
        if (plan.limit(metric).isEmpty() || plan.overagePrice(metric).isPresent()) {
            // Nothing to count: it never stops
            return true;
        }
        YearMonth month = currentMonth();
        return plan.withExtra(usage.topUps(month)).allows(metric, usedIn(month, metric));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspacePlan current() {
        Subscription subscription = subscription();
        Plan plan = planIn(currentMonth());
        return new WorkspacePlan(plan.code(), plan.name(), plan.monthlyPrice(), subscription.status().name(),
                usedThisMonth(UsageMetric.ANSWERS), plan.limit(UsageMetric.ANSWERS).orElse(null));
    }

    @Transactional(readOnly = true)
    public long usedThisMonth(UsageMetric metric) {
        return usedIn(currentMonth(), metric);
    }

    public YearMonth currentMonth() {
        return YearMonth.from(Instant.now(clock).atZone(ZoneOffset.UTC));
    }

    private long usedIn(YearMonth month, UsageMetric metric) {
        return usage.total(metric, month.atDay(1), month.atEndOfMonth());
    }
}
