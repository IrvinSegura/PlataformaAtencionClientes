package com.irvinsegura.supportchat.identity.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class InvalidInvitationRoleException extends DomainException {

    public InvalidInvitationRoleException() {
        super("INVALID_INVITATION_ROLE", "Invite people as ADMIN or AGENT; a workspace has one owner");
    }
}
