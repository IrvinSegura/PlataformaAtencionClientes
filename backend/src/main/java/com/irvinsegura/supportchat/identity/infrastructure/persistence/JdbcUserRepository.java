package com.irvinsegura.supportchat.identity.infrastructure.persistence;

import com.irvinsegura.supportchat.identity.domain.model.Email;
import com.irvinsegura.supportchat.identity.domain.model.EmailAlreadyRegisteredException;
import com.irvinsegura.supportchat.identity.domain.model.User;
import com.irvinsegura.supportchat.identity.domain.port.UserRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUserRepository implements UserRepository {

    private static final String COLUMNS =
            "id, email, display_name, password_hash, failed_login_attempts, locked_until, created_at";

    private final JdbcClient jdbc;

    JdbcUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(User user) {
        try {
            jdbc.sql("""
                            INSERT INTO users (id, email, display_name, password_hash, failed_login_attempts,
                                               locked_until, created_at)
                            VALUES (:id, :email, :displayName, :passwordHash, 0, NULL, :createdAt)
                            """)
                    .param("id", user.id())
                    .param("email", user.email().value())
                    .param("displayName", user.displayName())
                    .param("passwordHash", user.passwordHash())
                    .param("createdAt", Timestamp.from(user.createdAt()))
                    .update();
        } catch (DuplicateKeyException e) {
            // The unique constraint settles concurrent sign-ups with the same email, which a lookup could not
            throw new EmailAlreadyRegisteredException();
        }
    }

    @Override
    public void updateLoginState(User user) {
        jdbc.sql("UPDATE users SET failed_login_attempts = :attempts, locked_until = :lockedUntil WHERE id = :id")
                .param("attempts", user.failedLoginAttempts())
                .param("lockedUntil", user.lockedUntil() == null ? null : Timestamp.from(user.lockedUntil()))
                .param("id", user.id())
                .update();
    }

    @Override
    public Optional<User> findByEmailForUpdate(Email email) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE email = :email FOR UPDATE")
                .param("email", email.value())
                .query(JdbcUserRepository::map)
                .optional();
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE id = :id")
                .param("id", id)
                .query(JdbcUserRepository::map)
                .optional();
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM users WHERE email = :email)")
                .param("email", email.value())
                .query(Boolean.class)
                .single();
    }

    @Override
    public List<User> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE id IN (:ids)")
                .param("ids", ids)
                .query(JdbcUserRepository::map)
                .list();
    }

    private static User map(ResultSet rs, int row) throws SQLException {
        Timestamp lockedUntil = rs.getTimestamp("locked_until");
        return User.restore(
                rs.getObject("id", UUID.class),
                new Email(rs.getString("email")),
                rs.getString("display_name"),
                rs.getString("password_hash"),
                rs.getInt("failed_login_attempts"),
                lockedUntil == null ? null : lockedUntil.toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }
}
