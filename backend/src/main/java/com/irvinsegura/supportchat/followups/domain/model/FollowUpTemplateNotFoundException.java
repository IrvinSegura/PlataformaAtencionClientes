package com.irvinsegura.supportchat.followups.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

public class FollowUpTemplateNotFoundException extends NotFoundException {

    public FollowUpTemplateNotFoundException() {
        super("FOLLOW_UP_TEMPLATE_NOT_FOUND", "Follow-up template not found");
    }
}
