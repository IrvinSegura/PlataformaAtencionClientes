package com.irvinsegura.supportchat.channels.infrastructure.scheduling;

import com.irvinsegura.supportchat.channels.application.EmailChannelService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every instance may run it: a mail read twice is recognized by its Message-ID and answered once.
 */
@Component
class EmailReadJob {

    private final EmailChannelService mailboxes;

    EmailReadJob(EmailChannelService mailboxes) {
        this.mailboxes = mailboxes;
    }

    @Scheduled(fixedDelayString = "${supportchat.channels.email.read-delay:PT1M}", initialDelayString = "PT30S")
    void run() {
        mailboxes.readAll();
    }
}
