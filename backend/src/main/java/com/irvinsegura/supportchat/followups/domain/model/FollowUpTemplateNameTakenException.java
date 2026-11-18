package com.irvinsegura.supportchat.followups.domain.model;

import com.irvinsegura.supportchat.shared.domain.ConflictException;

public class FollowUpTemplateNameTakenException extends ConflictException {

    public FollowUpTemplateNameTakenException() {
        super("FOLLOW_UP_TEMPLATE_NAME_TAKEN", "Another template already has that name");
    }
}
