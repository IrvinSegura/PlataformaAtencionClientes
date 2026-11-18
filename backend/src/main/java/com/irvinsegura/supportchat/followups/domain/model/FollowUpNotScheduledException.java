package com.irvinsegura.supportchat.followups.domain.model;

import com.irvinsegura.supportchat.shared.domain.ConflictException;

public class FollowUpNotScheduledException extends ConflictException {

    public FollowUpNotScheduledException() {
        super("FOLLOW_UP_NOT_SCHEDULED", "The follow-up was already sent, failed or was cancelled");
    }
}
