package com.irvinsegura.supportchat.identity.application;

import com.irvinsegura.supportchat.identity.domain.model.TermsNotAcceptedException;
import com.irvinsegura.supportchat.identity.domain.port.TermsAcceptanceRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which terms of service are in force and whether each operator accepted them. A new version, set in the
 * configuration, is accepted again by everyone.
 */
@Service
public class TermsService {

    private final TermsAcceptanceRepository acceptances;
    private final String version;
    private final Clock clock;

    TermsService(TermsAcceptanceRepository acceptances, Settings settings, Clock clock) {
        this.acceptances = acceptances;
        this.version = settings.version();
        this.clock = clock;
    }

    public String version() {
        return version;
    }

    @Transactional(readOnly = true)
    public boolean acceptedCurrent(UUID userId) {
        return acceptances.accepted(userId, version);
    }

    /**
     * Records the acceptance of the version the operator was shown, which must be the one in force: one changed in
     * the meantime has to be read first.
     */
    @Transactional
    public void accept(UUID userId, String shownVersion) {
        if (!version.equals(shownVersion)) {
            throw new TermsNotAcceptedException("The terms of service changed; read version " + version);
        }
        acceptances.record(userId, version, clock.instant());
    }

    /**
     * For a new account: it cannot be created without accepting the terms in force.
     */
    public void requireAccepted(boolean accepted) {
        if (!accepted) {
            throw new TermsNotAcceptedException("Accept the terms of service to create an account");
        }
    }

    // Joins the caller's transaction, so the acceptance is saved with the account
    public void recordAccepted(UUID userId) {
        acceptances.record(userId, version, clock.instant());
    }

    /**
     * @param version identifies the terms of service in force
     */
    public record Settings(String version) {

        public Settings {
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("The terms of service need a version");
            }
        }
    }
}
