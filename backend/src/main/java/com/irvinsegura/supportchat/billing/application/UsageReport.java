package com.irvinsegura.supportchat.billing.application;

import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.billing.application.BillingViews.Quota;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * One month of a workspace's usage, measured against the plan that applies now.
 *
 * @param lines          every metric split by its dimension: channel, model, action or direction
 * @param quotas         the limited metrics against the plan's included quantities
 * @param estimatedTotal the plan's monthly price plus the overage of the month
 */
public record UsageReport(YearMonth month, String currency, String planCode, String planName,
        BigDecimal monthlyPrice, List<UsageLine> lines, List<Quota> quotas, BigDecimal estimatedTotal,
        List<UsageDay> days) {

    public record UsageLine(UsageMetric metric, String dimension, long quantity) {
    }

    public record UsageDay(LocalDate day, long answers, long inputTokens, long outputTokens, long actionRuns,
            long whatsappMessages) {

        static UsageDay of(LocalDate day, Map<UsageMetric, Long> totals) {
            return new UsageDay(day,
                    totals.getOrDefault(UsageMetric.ANSWERS, 0L),
                    totals.getOrDefault(UsageMetric.INPUT_TOKENS, 0L),
                    totals.getOrDefault(UsageMetric.OUTPUT_TOKENS, 0L),
                    totals.getOrDefault(UsageMetric.ACTION_RUNS, 0L),
                    totals.getOrDefault(UsageMetric.WHATSAPP_MESSAGES, 0L));
        }
    }
}
