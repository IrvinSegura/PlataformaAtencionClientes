package com.irvinsegura.supportchat.billing.application;

import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.billing.domain.model.Subscription;
import com.irvinsegura.supportchat.billing.domain.port.PaymentProvider;
import com.irvinsegura.supportchat.billing.domain.port.SubscriptionRepository;
import com.irvinsegura.supportchat.billing.domain.port.UsageRepository;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reports the limited metrics of every paying workspace to the payment provider's meters, which bill what goes past
 * each plan's included quantity. Each run sends what was added since the last one. The report's identifier is the
 * month's running total, so a run that reported but failed to record it sends the same identifier again and the
 * provider counts it once.
 */
@Service
public class UsageReporter {

    private static final Logger log = LoggerFactory.getLogger(UsageReporter.class);

    private final SubscriptionRepository subscriptions;
    private final UsageRepository usage;
    private final PaymentProvider payments;
    private final TransactionTemplate transactions;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    UsageReporter(SubscriptionRepository subscriptions, UsageRepository usage, PaymentProvider payments,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.subscriptions = subscriptions;
        this.usage = usage;
        this.payments = payments;
        this.transactions = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    /**
     * @return how many reports were sent
     */
    public int reportAll() {
        if (!payments.configured()) {
            return 0;
        }
        int sent = 0;
        for (UUID workspaceId : readOnly.execute(status -> subscriptions.workspacesWithCustomer())) {
            try {
                sent += TenantContext.callAs(workspaceId, () -> report(workspaceId));
            } catch (RuntimeException e) {
                // One workspace must not hold back the others; its usage is sent on the next run
                log.warn("Could not report the usage of workspace {}: {}", workspaceId, e.getMessage());
            }
        }
        return sent;
    }

    private int report(UUID workspaceId) {
        Optional<Subscription> subscription = readOnly.execute(status -> subscriptions.find());
        if (subscription.isEmpty() || !subscription.get().hasLiveSubscription()) {
            return 0;
        }
        String customerId = subscription.get().customerId();
        Instant now = clock.instant();
        YearMonth current = YearMonth.from(now.atZone(ZoneOffset.UTC));
        List<YearMonth> months = new ArrayList<>();
        if (now.atZone(ZoneOffset.UTC).getDayOfMonth() == 1) {
            // What arrived after the last run of the month that just ended still belongs to it
            months.add(current.minusMonths(1));
        }
        months.add(current);
        int sent = 0;
        for (YearMonth month : months) {
            for (UsageMetric metric : BillingViews.LIMITED) {
                Delta delta = readOnly.execute(status -> new Delta(
                        usage.total(metric, month.atDay(1), month.atEndOfMonth()), usage.reported(month, metric)));
                long quantity = delta.total() - delta.reported();
                if (quantity <= 0) {
                    continue;
                }
                Instant at = month.equals(current) ? now : endOf(month);
                payments.reportUsage(customerId, metric, quantity,
                        "%s:%s:%s:%d".formatted(workspaceId, month, metric, delta.total()), at);
                transactions.executeWithoutResult(status ->
                        usage.markReported(workspaceId, month, metric, delta.total(), now));
                sent++;
            }
        }
        return sent;
    }

    private static Instant endOf(YearMonth month) {
        LocalDate next = month.plusMonths(1).atDay(1);
        return next.atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(1);
    }

    private record Delta(long total, long reported) {
    }
}
