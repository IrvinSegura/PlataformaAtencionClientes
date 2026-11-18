package com.irvinsegura.supportchat.billing.infrastructure.scheduling;

import com.irvinsegura.supportchat.billing.application.UsageReporter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every instance may run it: a report sent twice carries the same identifier and Stripe counts it once.
 */
@Component
class UsageReportJob {

    private final UsageReporter reporter;

    UsageReportJob(UsageReporter reporter) {
        this.reporter = reporter;
    }

    @Scheduled(cron = "${supportchat.billing.usage-report-cron:0 */15 * * * *}", zone = "UTC")
    void run() {
        reporter.reportAll();
    }
}
