package com.irvinsegura.supportchat.billing.domain.port;

import com.irvinsegura.supportchat.billing.UsageMetric;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Daily usage of the workspace bound to the current transaction.
 */
public interface UsageRepository {

    /**
     * Adds to the day's total, creating it if needed.
     */
    void add(UUID workspaceId, LocalDate day, UsageMetric metric, String dimension, long quantity);

    /**
     * Every total from {@code from} to {@code to}, both included, oldest day first.
     */
    List<DailyUsage> between(LocalDate from, LocalDate to);

    /**
     * The total of one metric over every dimension, from {@code from} to {@code to}, both included.
     */
    long total(UsageMetric metric, LocalDate from, LocalDate to);

    /**
     * How much of a month's metric was already reported to the payment provider.
     */
    long reported(YearMonth month, UsageMetric metric);

    /**
     * Notes that the workspace reached that share of a metric's allowance in the month, unless it was noted before.
     *
     * @return whether it was new
     */
    boolean noteAlertIfNew(UUID workspaceId, YearMonth month, UsageMetric metric, int percent, Instant at);

    /**
     * Adds a paid recharge to the month's allowance, unless the same purchase was added before.
     *
     * @param reference the payment provider's id of the purchase
     * @return whether it was new
     */
    boolean addTopUp(UUID workspaceId, YearMonth month, UsageMetric metric, long quantity, String topUpCode,
            String reference, Instant paidAt);

    /**
     * Extra units bought for the month, by metric; a metric without recharges is left out.
     */
    Map<UsageMetric, Long> topUps(YearMonth month);

    /**
     * Forgets which shares of a metric's allowance the month already reached, so they are told again once a
     * recharge raised it.
     */
    void forgetAlerts(YearMonth month, UsageMetric metric);

    void markReported(UUID workspaceId, YearMonth month, UsageMetric metric, long quantity, Instant at);

    record DailyUsage(LocalDate day, UsageMetric metric, String dimension, long quantity) {
    }
}
