package com.irvinsegura.supportchat.identity.domain.port;

import java.time.Instant;
import java.util.UUID;

/**
 * The versions of the terms of service each operator accepted.
 */
public interface TermsAcceptanceRepository {

    /**
     * Notes that the operator accepted the version, unless they already had.
     */
    void record(UUID userId, String version, Instant acceptedAt);

    boolean accepted(UUID userId, String version);
}
