package com.irvinsegura.supportchat.followups.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class InvalidFollowUpTemplateException extends DomainException {

    public InvalidFollowUpTemplateException() {
        super("INVALID_FOLLOW_UP_TEMPLATE", "A template needs a name of up to %d characters and a text of up to %d; a "
                .formatted(FollowUpTemplate.MAX_NAME_LENGTH, FollowUpTemplate.MAX_BODY_LENGTH)
                + "WhatsApp template, a name in lowercase letters, digits and underscores and a language like es_MX");
    }
}
