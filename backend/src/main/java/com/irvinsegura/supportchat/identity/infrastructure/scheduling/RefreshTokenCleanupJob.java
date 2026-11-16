package com.irvinsegura.supportchat.identity.infrastructure.scheduling;

import com.irvinsegura.supportchat.identity.application.RefreshTokenCleanupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every instance runs it; the delete is idempotent, so running it twice costs one extra query.
 */
@Component
class RefreshTokenCleanupJob {

    private final RefreshTokenCleanupService cleanup;

    RefreshTokenCleanupJob(RefreshTokenCleanupService cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(cron = "${supportchat.identity.refresh-token-cleanup-cron:0 15 3 * * *}")
    void run() {
        cleanup.deleteExpired();
    }
}
