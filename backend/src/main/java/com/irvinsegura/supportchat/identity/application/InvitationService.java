package com.irvinsegura.supportchat.identity.application;

import com.irvinsegura.supportchat.identity.domain.model.AccountLockedException;
import com.irvinsegura.supportchat.identity.domain.model.Email;
import com.irvinsegura.supportchat.identity.domain.model.InvalidCredentialsException;
import com.irvinsegura.supportchat.identity.domain.model.InvalidInvitationException;
import com.irvinsegura.supportchat.identity.domain.model.Invitation;
import com.irvinsegura.supportchat.identity.domain.model.MemberPolicy;
import com.irvinsegura.supportchat.identity.domain.model.PasswordPolicy;
import com.irvinsegura.supportchat.identity.domain.model.User;
import com.irvinsegura.supportchat.identity.domain.port.InvitationRepository;
import com.irvinsegura.supportchat.identity.domain.port.PasswordHasher;
import com.irvinsegura.supportchat.identity.domain.port.UserRepository;
import com.irvinsegura.supportchat.shared.domain.Ids;
import com.irvinsegura.supportchat.shared.domain.SecretTokens;
import com.irvinsegura.supportchat.workspaces.Membership;
import com.irvinsegura.supportchat.workspaces.WorkspaceRole;
import com.irvinsegura.supportchat.workspaces.Workspaces;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Brings people into a workspace. There is no email delivery yet: the operator who invites gets a link to send
 * however they like, which keeps the platform free of a mail provider and of what such a provider would learn.
 */
@Service
public class InvitationService {

    private final InvitationRepository invitations;
    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final AuthenticationService authentication;
    private final Workspaces workspaces;
    private final SessionIssuer sessions;
    private final TermsService terms;
    private final Clock clock;
    private final Duration validity;

    InvitationService(InvitationRepository invitations, UserRepository users, PasswordHasher passwordHasher,
            AuthenticationService authentication, Workspaces workspaces, SessionIssuer sessions, TermsService terms,
            Clock clock, @Value("${supportchat.identity.invitation-ttl}") Duration validity) {
        this.terms = terms;
        this.invitations = invitations;
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.authentication = authentication;
        this.workspaces = workspaces;
        this.sessions = sessions;
        this.clock = clock;
        this.validity = validity;
    }

    /**
     * Creates an invitation; an earlier pending one for the same email stops working.
     *
     * @return the invitation with its token, which is shown this once
     */
    @Transactional
    public IssuedInvitation invite(UUID workspaceId, UUID inviterId, WorkspaceRole inviterRole, String rawEmail,
            WorkspaceRole role) {
        MemberPolicy.checkInvite(inviterRole, role);
        Email email = Email.of(rawEmail);
        Instant now = clock.instant();
        String token = SecretTokens.generate();
        invitations.revokePending(workspaceId, email, now);
        Invitation invitation = Invitation.issue(Ids.newId(), workspaceId, email, role, SecretTokens.hash(token),
                inviterId, validity, now);
        invitations.insert(invitation);
        return new IssuedInvitation(InvitationView.of(invitation), token);
    }

    @Transactional(readOnly = true)
    public List<InvitationView> pending(UUID workspaceId) {
        return invitations.pending(workspaceId, clock.instant()).stream().map(InvitationView::of).toList();
    }

    @Transactional
    public void revoke(UUID workspaceId, UUID invitationId) {
        Invitation invitation = invitations.findById(workspaceId, invitationId)
                .orElseThrow(InvalidInvitationException::new);
        invitation.revoke(clock.instant());
        invitations.update(invitation);
    }

    /**
     * What the person opening the link needs to see before accepting. Not read-only: the lookup locks the row, the
     * same query acceptance uses.
     */
    @Transactional
    public InvitationPreview preview(String token) {
        Invitation invitation = pendingByToken(token);
        return new InvitationPreview(workspaces.assistantProfile(invitation.workspaceId()).workspaceName(),
                invitation.email().value(), invitation.role(), users.existsByEmail(invitation.email()));
    }

    /**
     * Joins the invited workspace and signs in to it. With an existing account the password proves it is theirs
     * (failed attempts count towards the lockout); otherwise the account is created for the invited email, which
     * the link itself vouches for.
     *
     * @param displayName only used to create a new account
     */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public AuthSession accept(String token, String displayName, String password, boolean acceptedTerms) {
        Invitation invitation = pendingByToken(token);
        Instant now = clock.instant();
        User user;
        if (users.existsByEmail(invitation.email())) {
            user = authentication.authenticate(invitation.email().value(), password);
        } else {
            // A new account accepts the terms; an existing one is asked in the panel if it has not yet
            terms.requireAccepted(acceptedTerms);
            PasswordPolicy.check(password, invitation.email());
            user = User.register(Ids.newId(), invitation.email(), displayName, passwordHasher.hash(password), now);
            users.insert(user);
        }
        if (acceptedTerms) {
            terms.recordAccepted(user.id());
        }
        invitation.accept(now);
        invitations.update(invitation);
        Membership membership = workspaces.addMember(invitation.workspaceId(), user.id(), invitation.role());
        return sessions.startFamily(user.id(), membership);
    }

    private Invitation pendingByToken(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            throw new InvalidInvitationException();
        }
        return invitations.findByTokenHashForUpdate(SecretTokens.hash(token))
                .filter(invitation -> invitation.isPendingAt(clock.instant()))
                .orElseThrow(InvalidInvitationException::new);
    }

    public record InvitationView(UUID id, String email, WorkspaceRole role, Instant createdAt, Instant expiresAt) {

        static InvitationView of(Invitation invitation) {
            return new InvitationView(invitation.id(), invitation.email().value(), invitation.role(),
                    invitation.createdAt(), invitation.expiresAt());
        }
    }

    public record IssuedInvitation(InvitationView invitation, String token) {
    }

    /**
     * @param accountExists whether to ask for the existing password or for a name and a new password
     */
    public record InvitationPreview(String workspaceName, String email, WorkspaceRole role, boolean accountExists) {
    }
}
