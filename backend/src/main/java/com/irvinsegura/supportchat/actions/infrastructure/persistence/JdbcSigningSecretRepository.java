package com.irvinsegura.supportchat.actions.infrastructure.persistence;

import com.irvinsegura.supportchat.actions.domain.port.SigningSecretRepository;
import com.irvinsegura.supportchat.shared.infrastructure.SecretCipher;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcSigningSecretRepository implements SigningSecretRepository {

    private final JdbcClient jdbc;
    private final SecretCipher cipher;

    JdbcSigningSecretRepository(JdbcClient jdbc, SecretCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SigningSecret> find() {
        return jdbc.sql("SELECT signing_secret, rotated_at FROM action_settings")
                .query((rs, row) -> new SigningSecret(cipher.decrypt(rs.getString("signing_secret")),
                        rs.getTimestamp("rotated_at").toInstant()))
                .optional();
    }

    @Override
    @Transactional
    public void save(UUID workspaceId, SigningSecret secret) {
        jdbc.sql("""
                        INSERT INTO action_settings (workspace_id, signing_secret, rotated_at)
                        VALUES (:workspaceId, :secret, :rotatedAt)
                        ON CONFLICT (workspace_id)
                        DO UPDATE SET signing_secret = EXCLUDED.signing_secret, rotated_at = EXCLUDED.rotated_at
                        """)
                .param("workspaceId", workspaceId)
                .param("secret", cipher.encrypt(secret.value()))
                .param("rotatedAt", Timestamp.from(secret.rotatedAt()))
                .update();
    }
}
