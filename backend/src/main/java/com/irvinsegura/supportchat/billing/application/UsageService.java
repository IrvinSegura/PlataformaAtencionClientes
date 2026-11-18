package com.irvinsegura.supportchat.billing.application;

import com.irvinsegura.supportchat.billing.UsageLimitApproaching;
import com.irvinsegura.supportchat.billing.UsageMeter;
import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.billing.application.BillingViews.Quota;
import com.irvinsegura.supportchat.billing.domain.model.Plan;
import com.irvinsegura.supportchat.billing.domain.port.UsageRepository;
import com.irvinsegura.supportchat.billing.domain.port.UsageRepository.DailyUsage;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records usage and tells, through {@link UsageLimitApproaching}, when a limited metric reaches a share of what the
 * plan includes this month.
 */
@Service
public class UsageService implements UsageMeter {

    private final UsageRepository usage;
    private final CurrentPlan currentPlan;
    private final BillingSettings settings;
    private final ApplicationEventPublisher events;

    UsageService(UsageRepository usage, CurrentPlan currentPlan, BillingSettings settings,
            ApplicationEventPublisher events) {
        this.usage = usage;
        this.currentPlan = currentPlan;
        this.settings = settings;
        this.events = events;
    }

    // Joins the caller's transaction, so usage commits or rolls back with the work it measures
    @Override
    @Transactional
    public void record(Instant at, UsageMetric metric, String dimension, long quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Usage cannot be negative");
        }
        if (quantity == 0) {
            return;
        }
        UUID workspaceId = TenantContext.requireWorkspaceId();
        LocalDate day = LocalDate.ofInstant(at, ZoneOffset.UTC);
        usage.add(workspaceId, day, metric, Objects.requireNonNullElse(dimension, ""), quantity);
        if (BillingViews.LIMITED.contains(metric)) {
            tellIfNearingLimit(workspaceId, YearMonth.from(day), metric, quantity, at);
        }
    }

    /**
     * Publishes the highest share of the allowance this usage reached for the first time this month. Delivered once
     * the caller's transaction commits, so usage that rolls back tells nobody.
     */
    private void tellIfNearingLimit(UUID workspaceId, YearMonth month, UsageMetric metric, long quantity,
            Instant at) {
        Map<UsageMetric, Long> extra = currentPlan.topUps(month);
        Plan plan = currentPlan.plan().withExtra(extra);
        long included = plan.limit(metric).orElse(0L);
        if (included <= 0) {
            // Unlimited, or nothing included at all: there is no share to approach
            return;
        }
        long after = usage.total(metric, month.atDay(1), month.atEndOfMonth());
        long before = after - quantity;
        Integer reached = null;
        for (int percent : UsageLimitApproaching.THRESHOLDS) {
            // Compared in whole units times 100, so 80 % of 5 is reached at 4 and not before
            boolean crossed = before * 100 < percent * included && after * 100 >= percent * included;
            // Every share crossed is noted, so a lower one is never told after a higher one
            if (crossed && usage.noteAlertIfNew(workspaceId, month, metric, percent, at)) {
                reached = percent;
            }
        }
        if (reached != null) {
            events.publishEvent(new UsageLimitApproaching(workspaceId, metric, reached, after, included,
                    extra.getOrDefault(metric, 0L), plan.name(), plan.overagePrice(metric).isPresent(), at));
        }
    }

    /**
     * The month's usage, measured against the plan that applies now. Days are UTC days.
     */
    @Transactional(readOnly = true)
    public UsageReport month(YearMonth month) {
        List<DailyUsage> rows = usage.between(month.atDay(1), month.atEndOfMonth());

        Map<LineKey, Long> totals = new TreeMap<>(Comparator.comparing(LineKey::metric)
                .thenComparing(LineKey::dimension));
        Map<UsageMetric, Long> byMetric = new EnumMap<>(UsageMetric.class);
        Map<LocalDate, EnumMap<UsageMetric, Long>> byDay = new LinkedHashMap<>();
        for (DailyUsage row : rows) {
            totals.merge(new LineKey(row.metric(), row.dimension()), row.quantity(), Long::sum);
            byMetric.merge(row.metric(), row.quantity(), Long::sum);
            byDay.computeIfAbsent(row.day(), day -> new EnumMap<>(UsageMetric.class))
                    .merge(row.metric(), row.quantity(), Long::sum);
        }

        Map<UsageMetric, Long> extra = currentPlan.topUps(month);
        Plan plan = currentPlan.plan().withExtra(extra);
        List<Quota> quotas = BillingViews.LIMITED.stream()
                .map(metric -> Quota.of(plan, metric, byMetric.getOrDefault(metric, 0L), extra))
                .toList();
        BigDecimal total = quotas.stream().map(Quota::overageCost).reduce(plan.monthlyPrice(), BigDecimal::add);
        List<UsageReport.UsageLine> lines = totals.entrySet().stream()
                .map(entry -> new UsageReport.UsageLine(entry.getKey().metric(), entry.getKey().dimension(),
                        entry.getValue()))
                .toList();
        List<UsageReport.UsageDay> days = byDay.entrySet().stream()
                .map(entry -> UsageReport.UsageDay.of(entry.getKey(), entry.getValue()))
                .toList();
        return new UsageReport(month, settings.currency(), plan.code(), plan.name(), plan.monthlyPrice(), lines,
                quotas, total, days);
    }

    private record LineKey(UsageMetric metric, String dimension) {
    }
}
