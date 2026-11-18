package com.irvinsegura.supportchat.billing.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * An invoice the payment provider issued, with links to see and download it there.
 *
 * @param status draft, open, paid, uncollectible or void
 */
public record Invoice(String id, @Nullable String number, String status, BigDecimal total, String currency,
        @Nullable Instant createdAt, @Nullable Instant periodStart, @Nullable Instant periodEnd,
        @Nullable String hostedUrl, @Nullable String pdfUrl) {
}
