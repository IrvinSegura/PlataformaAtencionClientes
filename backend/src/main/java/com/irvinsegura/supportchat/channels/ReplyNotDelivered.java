package com.irvinsegura.supportchat.channels;

import java.util.Objects;
import java.util.UUID;

/**
 * A saved message for a customer that its channel did not deliver and will not try again. Published once the channel
 * knows, after the message was already saved and shown in the panel as sent.
 *
 * @param messageId the saved message that did not arrive
 */
public record ReplyNotDelivered(UUID workspaceId, UUID conversationId, UUID messageId, Reason reason) {

    public ReplyNotDelivered {
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(messageId);
        Objects.requireNonNull(reason);
    }

    public enum Reason {
        /**
         * The workspace has no connected and enabled account for the channel.
         */
        CHANNEL_UNAVAILABLE,
        /**
         * The channel refused it for good, e.g. a template WhatsApp does not know or a rejected address.
         */
        REFUSED
    }
}
