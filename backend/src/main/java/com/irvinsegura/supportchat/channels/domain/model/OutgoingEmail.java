package com.irvinsegura.supportchat.channels.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * A plain-text reply to a customer, threaded under their last message.
 *
 * @param messageId  the Message-ID to send it with, angle brackets included
 * @param inReplyTo  the customer's message it answers, or null
 * @param references the thread's earlier messages, oldest first
 * @param automatic  written by the assistant or the platform rather than by a person, marked as such so other
 *                   automatic responders do not answer it
 */
public record OutgoingEmail(String to, String subject, String text, String messageId, String inReplyTo,
        List<String> references, boolean automatic) {

    public OutgoingEmail {
        Objects.requireNonNull(to);
        Objects.requireNonNull(subject);
        Objects.requireNonNull(text);
        Objects.requireNonNull(messageId);
        references = references == null ? List.of() : List.copyOf(references);
    }
}
