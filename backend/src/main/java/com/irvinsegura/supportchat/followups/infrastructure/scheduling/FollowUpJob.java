package com.irvinsegura.supportchat.followups.infrastructure.scheduling;

import com.irvinsegura.supportchat.followups.application.FollowUpService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends the follow-ups whose moment came, every minute.
 */
@Component
class FollowUpJob {

    private final FollowUpService followUps;

    FollowUpJob(FollowUpService followUps) {
        this.followUps = followUps;
    }

    @Scheduled(fixedDelayString = "${supportchat.follow-ups.delay:PT1M}", initialDelayString = "PT45S")
    void run() {
        followUps.sendDue();
    }
}
