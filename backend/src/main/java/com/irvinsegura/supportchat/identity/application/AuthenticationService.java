package com.irvinsegura.supportchat.identity.application;

import com.irvinsegura.supportchat.identity.domain.model.AccountLockedException;
import com.irvinsegura.supportchat.identity.domain.model.Email;
import com.irvinsegura.supportchat.identity.domain.model.InvalidCredentialsException;
import com.irvinsegura.supportchat.identity.domain.model.InvalidEmailException;
import com.irvinsegura.supportchat.identity.domain.model.InvalidRefreshTokenException;
import com.irvinsegura.supportchat.identity.domain.model.LockoutPolicy;
import com.irvinsegura.supportchat.identity.domain.model.RefreshToken;
import com.irvinsegura.supportchat.identity.domain.model.User;
import com.irvinsegura.supportchat.identity.domain.model.WorkspaceAccessDeniedException;
import com.irvinsegura.supportchat.identity.domain.port.PasswordHasher;
import com.irvinsegura.supportchat.identity.domain.port.RefreshTokenRepository;
import com.irvinsegura.supportchat.identity.domain.port.UserRepository;
import com.irvinsegura.supportchat.shared.domain.SecretTokens;
import com.irvinsegura.supportchat.workspaces.Membership;
import com.irvinsegura.supportchat.workspaces.Workspaces;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher passwordHasher;
    private final Workspaces workspaces;
    private final SessionIssuer sessions;
    private final LockoutPolicy lockoutPolicy;
    private final Clock clock;
    // Compared against when the email is unknown, so the response takes as long as for a real account and timing
    // does not reveal which emails are registered
    private final String unknownUserHash;

    AuthenticationService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordHasher passwordHasher,
            Workspaces workspaces, SessionIssuer sessions, LockoutPolicy lockoutPolicy, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordHasher = passwordHasher;
        this.workspaces = workspaces;
        this.sessions = sessions;
        this.lockoutPolicy = lockoutPolicy;
        this.clock = clock;
        this.unknownUserHash = passwordHasher.hash(SecretTokens.generate());
    }

    // noRollbackFor: a failed attempt must still be counted when the exception leaves the method
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public AuthSession login(String rawEmail, String password) {
        User user = authenticate(rawEmail, password);
        // An operator removed from every workspace has nowhere to sign in to; same answer as a wrong password
        Membership membership = workspaces.defaultMembershipOf(user.id())
                .orElseThrow(InvalidCredentialsException::new);
        return sessions.startFamily(user.id(), membership);
    }

    /**
     * Checks an email and password with the lockout rules, counting failed attempts. Callers run it in a transaction
     * that does not roll back on {@link InvalidCredentialsException} or {@link AccountLockedException}.
     */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public User authenticate(String rawEmail, String password) {
        Optional<User> found = findUser(rawEmail);
        if (found.isEmpty()) {
            passwordHasher.matches(password, unknownUserHash);
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        Instant now = clock.instant();
        if (user.isLockedAt(now)) {
            throw new AccountLockedException();
        }
        if (!passwordHasher.matches(password, user.passwordHash())) {
            if (user.recordFailedLogin(lockoutPolicy, now)) {
                log.warn("Account {} locked after repeated failed sign-ins", user.id());
            }
            users.updateLoginState(user);
            throw new InvalidCredentialsException();
        }
        user.recordSuccessfulLogin();
        users.updateLoginState(user);
        return user;
    }

    /**
     * Moves the signed-in operator to another of their workspaces. The login presenting the cookie ends, and a new
     * one starts in the target workspace, so each refresh token family always belongs to one workspace.
     */
    @Transactional
    public AuthSession switchWorkspace(UUID userId, UUID workspaceId, String presentedToken) {
        Membership membership = workspaces.membershipOf(userId, workspaceId)
                .orElseThrow(WorkspaceAccessDeniedException::new);
        findToken(presentedToken)
                .filter(token -> token.userId().equals(userId))
                .ifPresent(token -> refreshTokens.revokeFamily(token.familyId(), clock.instant()));
        return sessions.startFamily(userId, membership);
    }

    // noRollbackFor: revoking a family on reuse must survive the exception that answers the request
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthSession refresh(String presentedToken) {
        RefreshToken token = findToken(presentedToken).orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();
        switch (token.stateAt(now)) {
            case REUSED_OR_REVOKED -> {
                log.warn("Refresh token reused; revoking login family {} of user {}", token.familyId(),
                        token.userId());
                refreshTokens.revokeFamily(token.familyId(), now);
                throw new InvalidRefreshTokenException();
            }
            case EXPIRED -> throw new InvalidRefreshTokenException();
            case ACTIVE -> {
                // Continues below
            }
        }
        refreshTokens.markUsed(token.id(), now);
        // The role may have changed, or the membership ended, since the last token was issued
        Optional<Membership> membership = workspaces.membershipOf(token.userId(), token.workspaceId());
        if (membership.isEmpty()) {
            refreshTokens.revokeFamily(token.familyId(), now);
            throw new InvalidRefreshTokenException();
        }
        return sessions.continueFamily(token.userId(), membership.get(), token.familyId());
    }

    /**
     * Ends the login the token belongs to. Unknown or already revoked tokens are ignored, so signing out always
     * succeeds.
     */
    @Transactional
    public void logout(String presentedToken) {
        findToken(presentedToken).ifPresent(token -> refreshTokens.revokeFamily(token.familyId(), clock.instant()));
    }

    private Optional<User> findUser(String rawEmail) {
        try {
            return users.findByEmailForUpdate(Email.of(rawEmail));
        } catch (InvalidEmailException e) {
            return Optional.empty();
        }
    }

    private Optional<RefreshToken> findToken(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank() || presentedToken.length() > 100) {
            return Optional.empty();
        }
        return refreshTokens.findByHashForUpdate(SecretTokens.hash(presentedToken));
    }
}
