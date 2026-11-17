package com.irvinsegura.supportchat.channels.domain.port;

import java.time.Instant;
import java.util.UUID;

/**
 * The messages received on the email, Messenger and Instagram channels.
 */
public interface InboundMessageLog {

    /**
     * Remembers a received message.
     *
     * @param channel the conversation channel's name
     * @return false when it was received before and must be ignored
     */
    boolean firstDelivery(UUID workspaceId, String channel, String messageId, Instant receivedAt);
}
