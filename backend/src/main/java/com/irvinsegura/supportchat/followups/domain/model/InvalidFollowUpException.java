package com.irvinsegura.supportchat.followups.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class InvalidFollowUpException extends DomainException {

    private final Reason reason;

    public InvalidFollowUpException(Reason reason) {
        super("INVALID_FOLLOW_UP", switch (reason) {
            case WEB_VISITOR -> "Web visitors are anonymous: there is no way to write to them later";
            case DUE_AT -> "A follow-up is scheduled from now up to %d days ahead"
                    .formatted(FollowUp.MAX_DELAY.toDays());
            case TEXT -> "A follow-up needs a template or a text of up to %d characters"
                    .formatted(FollowUpTemplate.MAX_BODY_LENGTH);
        });
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        WEB_VISITOR,
        DUE_AT,
        TEXT
    }
}
