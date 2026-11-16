package com.irvinsegura.supportchat.workspaces.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class InvalidSuspensionReasonException extends DomainException {

    public InvalidSuspensionReasonException(int maxLength) {
        super("INVALID_SUSPENSION_REASON", "A suspension needs a reason of up to %d characters".formatted(maxLength));
    }
}
