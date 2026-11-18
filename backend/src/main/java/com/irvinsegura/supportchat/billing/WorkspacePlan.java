package com.irvinsegura.supportchat.billing;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * The plan of the workspace bound to the current code and how much of it the month used, as the platform
 * administration sees it.
 *
 * @param subscriptionStatus the status of the paid subscription, e.g. ACTIVE or PAST_DUE, or NONE on the free plan
 * @param answersIncluded    the answers the month includes, recharges counted, or null when unlimited
 */
public record WorkspacePlan(String planCode, String planName, BigDecimal monthlyPrice, String subscriptionStatus,
        long answersThisMonth, @Nullable Long answersIncluded) {
}
