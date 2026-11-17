package com.irvinsegura.supportchat.channels.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

/**
 * No WhatsApp number connected, or none behind a webhook URL.
 */
public class WhatsAppChannelNotFoundException extends NotFoundException {

    public WhatsAppChannelNotFoundException() {
        super("WHATSAPP_CHANNEL_NOT_FOUND", "No WhatsApp number is connected");
    }
}
