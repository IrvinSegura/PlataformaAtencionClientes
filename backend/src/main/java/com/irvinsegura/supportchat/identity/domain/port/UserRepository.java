package com.irvinsegura.supportchat.identity.domain.port;

import com.irvinsegura.supportchat.identity.domain.model.Email;
import com.irvinsegura.supportchat.identity.domain.model.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    /**
     * @throws com.irvinsegura.supportchat.identity.domain.model.EmailAlreadyRegisteredException if the email is taken
     */
    void insert(User user);

    /**
     * Saves the sign-in counters (failed attempts, lockout).
     */
    void updateLoginState(User user);

    /**
     * Locks the row until the transaction ends, so concurrent sign-ins to one account are counted one at a time.
     */
    Optional<User> findByEmailForUpdate(Email email);

    Optional<User> findById(UUID id);

    boolean existsByEmail(Email email);

    List<User> findAllById(Collection<UUID> ids);
}
