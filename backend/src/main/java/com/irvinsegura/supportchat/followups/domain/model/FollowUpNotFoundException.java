package com.irvinsegura.supportchat.followups.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

public class FollowUpNotFoundException extends NotFoundException {

    public FollowUpNotFoundException() {
        super("FOLLOW_UP_NOT_FOUND", "Follow-up not found");
    }
}
