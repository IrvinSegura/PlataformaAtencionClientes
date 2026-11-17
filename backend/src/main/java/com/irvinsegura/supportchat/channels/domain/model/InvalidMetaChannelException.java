package com.irvinsegura.supportchat.channels.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

/**
 * The Messenger or Instagram settings cannot be saved as given.
 */
public class InvalidMetaChannelException extends DomainException {

    public InvalidMetaChannelException(String message) {
        super("INVALID_META_CHANNEL", message);
    }
}
