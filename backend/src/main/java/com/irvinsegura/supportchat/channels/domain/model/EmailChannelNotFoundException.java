package com.irvinsegura.supportchat.channels.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

/**
 * No mailbox connected.
 */
public class EmailChannelNotFoundException extends NotFoundException {

    public EmailChannelNotFoundException() {
        super("EMAIL_CHANNEL_NOT_FOUND", "No mailbox is connected");
    }
}
