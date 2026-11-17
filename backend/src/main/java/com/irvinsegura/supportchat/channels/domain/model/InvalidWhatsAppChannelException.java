package com.irvinsegura.supportchat.channels.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

/**
 * The WhatsApp settings cannot be saved as given.
 */
public class InvalidWhatsAppChannelException extends DomainException {

    public InvalidWhatsAppChannelException(String message) {
        super("INVALID_WHATSAPP_CHANNEL", message);
    }
}
