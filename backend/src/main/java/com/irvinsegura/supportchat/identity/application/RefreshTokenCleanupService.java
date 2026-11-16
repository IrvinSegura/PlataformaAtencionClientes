package com.irvinsegura.supportchat.identity.application;

import com.irvinsegura.supportchat.identity.domain.port.RefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupService.class);

    // Expired tokens are kept a little longer, so a stolen one replayed just after expiry is still recognized
    private static final Duration GRACE = Duration.ofDays(1);

    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    RefreshTokenCleanupService(RefreshTokenRepository refreshTokens, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Transactional
    public int deleteExpired() {
        int deleted = refreshTokens.deleteExpiredBefore(clock.instant().minus(GRACE));
        log.info("Deleted {} expired refresh tokens", deleted);
        return deleted;
    }
}
