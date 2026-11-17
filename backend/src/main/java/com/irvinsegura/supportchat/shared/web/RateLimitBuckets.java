package com.irvinsegura.supportchat.shared.web;

import io.github.bucket4j.Bucket;
import java.time.Duration;

/**
 * Where the rate limiter keeps each client's tokens: in this instance's memory, or in Redis, shared by every instance.
 */
interface RateLimitBuckets {

    /**
     * The bucket of one client and rule, created full the first time. Greedy refill adds tokens continuously instead
     * of all at once, so a client cannot burst twice the capacity across a period boundary.
     *
     * @param key the same for the same client and rule on every instance
     */
    Bucket bucket(String key, long capacity, Duration period);
}
