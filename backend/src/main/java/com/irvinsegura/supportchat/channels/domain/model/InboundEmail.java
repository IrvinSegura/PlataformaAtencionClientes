package com.irvinsegura.supportchat.channels.domain.model;

import com.irvinsegura.supportchat.shared.domain.StoredText;
import java.util.List;
import java.util.Objects;

/**
 * One message read from the mailbox.
 *
 * @param messageId  the Message-ID header, used to ignore a message read twice and to thread the replies
 * @param from       the sender's address, in lower case
 * @param fromName   the sender's display name, or null
 * @param text       what they wrote above any quoted earlier message
 * @param inReplyTo  the message it answers, or null
 * @param references the earlier messages of the thread, oldest first
 * @param automated  sent by a program (auto-replies, bounces, mailing lists), which is never answered: two automatic
 *                   responders would answer each other forever
 * @param spoofed    the receiving server found that the sender's domain did not send it (DMARC failed): someone is
 *                   posing as the sender, and it is never answered
 */
public record InboundEmail(String messageId, String from, String fromName, String subject, String text,
        String inReplyTo, List<String> references, boolean automated, boolean spoofed) {

    public InboundEmail {
        Objects.requireNonNull(messageId);
        Objects.requireNonNull(from);
        fromName = StoredText.of(fromName);
        subject = StoredText.of(subject);
        text = text == null ? "" : StoredText.of(text);
        references = references == null ? List.of() : List.copyOf(references);
    }
}
