package com.irvinsegura.supportchat.actions.domain.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The secret that signs the bound workspace's webhook calls. Implementations keep it encrypted at rest.
 */
public interface SigningSecretRepository {

    Optional<SigningSecret> find();

    void save(UUID workspaceId, SigningSecret secret);

    record SigningSecret(String value, Instant rotatedAt) {
    }
}
