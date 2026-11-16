package com.irvinsegura.supportchat.identity.application;

import com.irvinsegura.supportchat.identity.domain.model.Email;
import com.irvinsegura.supportchat.identity.domain.model.PasswordPolicy;
import com.irvinsegura.supportchat.identity.domain.model.User;
import com.irvinsegura.supportchat.identity.domain.port.PasswordHasher;
import com.irvinsegura.supportchat.identity.domain.port.UserRepository;
import com.irvinsegura.supportchat.shared.domain.Ids;
import com.irvinsegura.supportchat.workspaces.Membership;
import com.irvinsegura.supportchat.workspaces.Workspaces;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-up creates the operator and their workspace together, and signs them in. It needs the terms of service
 * accepted, and keeps that acceptance.
 */
@Service
public class RegistrationService {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final Workspaces workspaces;
    private final SessionIssuer sessions;
    private final TermsService terms;
    private final Clock clock;

    RegistrationService(UserRepository users, PasswordHasher passwordHasher, Workspaces workspaces,
            SessionIssuer sessions, TermsService terms, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.workspaces = workspaces;
        this.sessions = sessions;
        this.terms = terms;
        this.clock = clock;
    }

    @Transactional
    public AuthSession register(RegisterCommand command) {
        terms.requireAccepted(command.acceptedTerms());
        Email email = Email.of(command.email());
        PasswordPolicy.check(command.password(), email);
        User user = User.register(Ids.newId(), email, command.displayName(),
                passwordHasher.hash(command.password()), clock.instant());
        users.insert(user);
        terms.recordAccepted(user.id());
        Membership membership = workspaces.createWithOwner(command.workspaceName(), user.id());
        return sessions.startFamily(user.id(), membership);
    }

    /**
     * @param acceptedTerms whether the operator accepted the terms of service in force
     */
    public record RegisterCommand(String email, String password, String displayName, String workspaceName,
            boolean acceptedTerms) {

        // Keeps the password out of logs if the command is ever printed
        @Override
        public String toString() {
            return "RegisterCommand[email=%s, displayName=%s, workspaceName=%s]"
                    .formatted(email, displayName, workspaceName);
        }
    }
}
