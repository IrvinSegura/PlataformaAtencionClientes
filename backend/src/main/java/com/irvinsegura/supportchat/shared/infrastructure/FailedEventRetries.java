package com.irvinsegura.supportchat.shared.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs again, every minute, the event listeners that failed, such as a reply WhatsApp could not take because it was
 * down or limiting the business. Each publication is claimed by one instance only. A publication is given up after a
 * few attempts or once it is a day old: a reply that late would confuse the customer more than it helps, and it stays
 * in {@code event_publication} for whoever looks into it.
 * <p>
 * Listeners an instance was running when it stopped are marked failed after a while (see
 * {@code spring.modulith.events.staleness}) and retried here too, so restarting does not run them again in bulk.
 */
@Component
public class FailedEventRetries {

    private final FailedEventPublications failed;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration maxAge;
    private final Duration minAge;

    FailedEventRetries(FailedEventPublications failed, Clock clock,
            @Value("${supportchat.events.retry.max-attempts:5}") int maxAttempts,
            @Value("${supportchat.events.retry.max-age:P1D}") Duration maxAge,
            @Value("${supportchat.events.retry.min-age:PT1M}") Duration minAge) {
        this.failed = failed;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.maxAge = maxAge;
        this.minAge = minAge;
    }

    @Scheduled(fixedDelayString = "${supportchat.events.retry.delay:PT1M}", initialDelayString = "PT1M")
    void run() {
        retryFailedOlderThan(minAge);
    }

    /**
     * @param minAge how long ago a publication must have been tried last, so a failing service gets a moment
     */
    public void retryFailedOlderThan(Duration minAge) {
        Instant oldest = clock.instant().minus(maxAge);
        failed.resubmit(ResubmissionOptions.defaults()
                .withMinAge(minAge)
                .withFilter(publication -> worthRetrying(publication, oldest)));
    }

    private boolean worthRetrying(EventPublication publication, Instant oldest) {
        return publication.getCompletionAttempts() < maxAttempts && publication.getPublicationDate().isAfter(oldest);
    }
}
