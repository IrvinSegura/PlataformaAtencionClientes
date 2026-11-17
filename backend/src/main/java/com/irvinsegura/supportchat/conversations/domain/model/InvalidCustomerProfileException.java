package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class InvalidCustomerProfileException extends DomainException {

    public InvalidCustomerProfileException(int maxNameLength, int maxNotesLength) {
        super("INVALID_CUSTOMER_PROFILE", "The name may have up to %d characters and the notes up to %d"
                .formatted(maxNameLength, maxNotesLength));
    }
}
