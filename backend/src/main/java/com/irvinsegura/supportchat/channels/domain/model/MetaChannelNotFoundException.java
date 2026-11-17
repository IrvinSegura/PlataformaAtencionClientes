package com.irvinsegura.supportchat.channels.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

/**
 * No Messenger or Instagram account connected, or none behind a webhook URL.
 */
public class MetaChannelNotFoundException extends NotFoundException {

    public MetaChannelNotFoundException() {
        super("META_CHANNEL_NOT_FOUND", "No Messenger or Instagram account is connected");
    }
}
