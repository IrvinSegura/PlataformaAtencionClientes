package com.irvinsegura.supportchat.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The application's clock in integration tests: real time, which a test can move ahead to see what happens later (a
 * follow-up's moment, a closed 24-hour window) without touching the database. {@link WithExternalServices} puts it
 * back after every test, since all of them share one application context.
 * <p>
 * Only code that asks the application's {@link Clock} sees the jump; the database's {@code now()} and the signature
 * checks of access tokens keep real time, so the test's operators stay signed in.
 */
public class TestClock extends Clock {

    private final AtomicReference<Duration> ahead = new AtomicReference<>(Duration.ZERO);

    public void advance(Duration duration) {
        ahead.accumulateAndGet(duration, Duration::plus);
    }

    public void reset() {
        ahead.set(Duration.ZERO);
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(ahead.get());
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (!ZoneOffset.UTC.equals(zone.normalized())) {
            throw new UnsupportedOperationException("The application works in UTC");
        }
        return this;
    }
}
