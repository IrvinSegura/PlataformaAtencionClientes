package com.irvinsegura.supportchat.support;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

/**
 * Retries an assertion until it passes or time runs out, for work that finishes in the background.
 */
public final class Eventually {

    private static final Duration PAUSE = Duration.ofMillis(100);

    private Eventually() {
    }

    public static void assertEventually(Duration timeout, ThrowingRunnable assertion) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            try {
                assertion.run();
                return;
            } catch (AssertionError | Exception e) {
                if (System.nanoTime() > deadline) {
                    throw e;
                }
                LockSupport.parkNanos(PAUSE.toNanos());
            }
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {

        void run() throws Exception;
    }
}
