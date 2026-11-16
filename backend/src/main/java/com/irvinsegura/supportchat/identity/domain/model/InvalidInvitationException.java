package com.irvinsegura.supportchat.identity.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

/**
 * Unknown, used, revoked and expired invitations all look the same, so a guessed token learns nothing.
 */
public class InvalidInvitationException extends NotFoundException {

    public InvalidInvitationException() {
        super("INVALID_INVITATION", "This invitation is not valid; ask for a new one");
    }
}
