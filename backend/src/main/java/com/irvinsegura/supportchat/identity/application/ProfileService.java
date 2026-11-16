package com.irvinsegura.supportchat.identity.application;

import com.irvinsegura.supportchat.identity.OperatorDirectory;
import com.irvinsegura.supportchat.identity.domain.model.InvalidCredentialsException;
import com.irvinsegura.supportchat.identity.domain.port.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService implements OperatorDirectory {

    private final UserRepository users;
    private final TermsService terms;

    ProfileService(UserRepository users, TermsService terms) {
        this.users = users;
        this.terms = terms;
    }

    @Transactional(readOnly = true)
    public OperatorProfile profile(UUID userId) {
        // A valid token for a deleted account is treated like any other invalid credential
        return users.findById(userId)
                .map(user -> new OperatorProfile(user.id(), user.email().value(), user.displayName(),
                        terms.version(), terms.acceptedCurrent(user.id())))
                .orElseThrow(InvalidCredentialsException::new);
    }

    @Override
    @Transactional(readOnly = true)
    public String displayNameOf(UUID userId) {
        return profile(userId).displayName();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> emailOf(UUID userId) {
        return users.findById(userId).map(user -> user.email().value());
    }

    /**
     * @param termsVersion  the terms of service in force
     * @param termsAccepted whether the operator accepted them; the panel asks until they do
     */
    public record OperatorProfile(UUID id, String email, String displayName, String termsVersion,
            boolean termsAccepted) {
    }
}
