package com.irvinsegura.supportchat.shared.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import java.time.Duration;

/**
 * Buckets in this instance's memory: with several instances each one enforces the limits on its own share of the
 * traffic, so a client gets the limit once per instance.
 */
class MemoryRateLimitBuckets implements RateLimitBuckets {

    // Bounds memory when many clients (or spoofed IPv6 addresses) show up; evicting an idle bucket loses nothing,
    // because it would have refilled completely anyway
    private static final long MAX_TRACKED_CLIENTS = 100_000;

    private final Cache<String, Bucket> buckets;

    MemoryRateLimitBuckets(Duration longestPeriod) {
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_CLIENTS)
                .expireAfterAccess(longestPeriod)
                .build();
    }

    @Override
    public Bucket bucket(String key, long capacity, Duration period) {
        return buckets.get(key, ignored -> Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, period))
                .build());
    }
}
