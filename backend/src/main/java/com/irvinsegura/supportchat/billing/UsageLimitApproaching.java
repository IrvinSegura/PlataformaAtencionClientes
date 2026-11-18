package com.irvinsegura.supportchat.billing;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A workspace used a share of what its plan includes this month, published once per month, metric and share after the
 * usage that reached it is saved. Past the limit, a plan with an overage price bills each extra unit; one without
 * stops, so the owner is told in time to buy a recharge or change plans.
 *
 * @param percent         the share reached, one of {@link #THRESHOLDS}
 * @param used            the month's usage so far
 * @param included        what the plan includes this month, recharges bought for it counted
 * @param extra           how many of the included units recharges added
 * @param billedPastLimit whether usage past the limit keeps working and is billed, instead of stopping
 */
public record UsageLimitApproaching(UUID workspaceId, UsageMetric metric, int percent, long used, long included,
        long extra, String planName, boolean billedPastLimit, Instant occurredAt) {

    /**
     * The shares of the allowance that are told, lowest first.
     */
    public static final List<Integer> THRESHOLDS = List.of(80, 95);

    public UsageLimitApproaching {
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(metric);
        Objects.requireNonNull(planName);
    }
}
