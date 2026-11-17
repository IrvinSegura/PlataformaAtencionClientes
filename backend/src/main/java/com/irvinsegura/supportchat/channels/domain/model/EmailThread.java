package com.irvinsegura.supportchat.channels.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * What replies to a customer need to land in the thread of their last message: its subject and its ids.
 *
 * @param subject       the subject without reply or forward prefixes, or null
 * @param lastMessageId the customer's last message
 * @param references    the thread's messages up to and including the last one, oldest first
 */
public record EmailThread(String contactAddress, String subject, String lastMessageId, List<String> references) {

    // Mail clients keep long threads to their newest ids; so does this
    static final int MAX_REFERENCES = 10;
    private static final Pattern PREFIXES = Pattern.compile("^\\s*((re|fw|fwd|rv|tr|enc|aw|wg)\\s*:\\s*)+",
            Pattern.CASE_INSENSITIVE);
    private static final int MAX_SUBJECT = 200;

    public EmailThread {
        Objects.requireNonNull(contactAddress);
        references = references == null ? List.of() : List.copyOf(references);
    }

    /**
     * The thread as of a message the customer just sent.
     */
    public static EmailThread of(InboundEmail email) {
        List<String> references = new ArrayList<>(email.references());
        if (email.inReplyTo() != null && !references.contains(email.inReplyTo())) {
            references.add(email.inReplyTo());
        }
        references.add(email.messageId());
        List<String> newest = references.subList(Math.max(0, references.size() - MAX_REFERENCES), references.size());
        return new EmailThread(email.from(), baseSubject(email.subject()), email.messageId(), newest);
    }

    /**
     * The subject without the prefixes mail clients add to replies and forwards; null when nothing is left.
     */
    public static String baseSubject(String subject) {
        if (subject == null) {
            return null;
        }
        String base = PREFIXES.matcher(subject).replaceFirst("").strip();
        return base.isEmpty() ? null : base.substring(0, Math.min(base.length(), MAX_SUBJECT));
    }

    /**
     * @param fallback the subject when the customer's mail had none
     */
    public String replySubject(String fallback) {
        return "Re: " + (subject == null ? fallback : subject);
    }
}
