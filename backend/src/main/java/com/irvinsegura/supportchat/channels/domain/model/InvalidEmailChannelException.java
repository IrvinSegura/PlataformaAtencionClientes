package com.irvinsegura.supportchat.channels.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

/**
 * The mailbox settings cannot be saved as given.
 */
public class InvalidEmailChannelException extends DomainException {

    public InvalidEmailChannelException(String message) {
        super("INVALID_EMAIL_CHANNEL", message);
    }
}
