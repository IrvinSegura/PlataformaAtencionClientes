package com.irvinsegura.supportchat.identity.infrastructure.persistence;

import com.irvinsegura.supportchat.identity.domain.port.TermsAcceptanceRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcTermsAcceptanceRepository implements TermsAcceptanceRepository {

    private final JdbcClient jdbc;

    JdbcTermsAcceptanceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void record(UUID userId, String version, Instant acceptedAt) {
        jdbc.sql("""
                        INSERT INTO terms_acceptances (user_id, version, accepted_at)
                        VALUES (:userId, :version, :acceptedAt)
                        ON CONFLICT DO NOTHING
                        """)
                .param("userId", userId)
                .param("version", version)
                .param("acceptedAt", Timestamp.from(acceptedAt))
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean accepted(UUID userId, String version) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM terms_acceptances WHERE user_id = :userId AND version = :version)")
                .param("userId", userId)
                .param("version", version)
                .query(Boolean.class)
                .single();
    }
}
