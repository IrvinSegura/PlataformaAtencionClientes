package com.irvinsegura.supportchat.shared.web;

import java.time.Duration;

public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(Duration retryAfter) {
        super("Rate limit exceeded");
        // Rounded up, so a client that waits exactly this long always finds a token
        this.retryAfterSeconds = Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
