package com.irvinsegura.supportchat.support;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Puts the {@link TestClock} back to real time after every test, since tests with the same configuration share one
 * application context and so one clock. Registered for every test in {@code META-INF/spring.factories}.
 */
public class ResetTestClock implements TestExecutionListener {

    @Override
    public void afterTestMethod(TestContext testContext) {
        if (testContext.hasApplicationContext()) {
            testContext.getApplicationContext().getBeanProvider(TestClock.class).ifAvailable(TestClock::reset);
        }
    }
}
