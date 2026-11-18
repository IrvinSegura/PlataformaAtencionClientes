package com.irvinsegura.supportchat.channels.infrastructure.mail;

import com.irvinsegura.supportchat.channels.domain.model.EmailText;
import com.irvinsegura.supportchat.channels.domain.model.InboundEmail;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what the platform needs from a received MIME message.
 */
final class MailMessages {

    // The start of the body is all the assistant needs; the rest is usually quoted thread
    private static final int MAX_BODY_CHARS = 100_000;
    private static final Pattern MESSAGE_ID = Pattern.compile("<[^<>\\s]+>");
    private static final Pattern PROGRAM_SENDERS = Pattern.compile(
            "(mailer-daemon|postmaster|no-?reply|do-?not-?reply|bounces?)([+._-].*)?");
    private static final Set<String> BULK_PRECEDENCE = Set.of("bulk", "junk", "list", "auto_reply");

    private MailMessages() {
    }

    /**
     * @param fallbackId the id for a message that has no Message-ID header
     * @return empty when the message has no usable sender
     */
    static Optional<InboundEmail> read(MimeMessage message, String fallbackId) throws MessagingException {
        Optional<InternetAddress> sender = sender(message);
        if (sender.isEmpty()) {
            return Optional.empty();
        }
        String address = sender.get().getAddress().toLowerCase(Locale.ROOT);
        String messageId = Optional.ofNullable(message.getMessageID()).map(String::strip)
                .filter(id -> !id.isEmpty()).orElse(fallbackId);
        String name = sender.get().getPersonal();
        List<String> inReplyTo = ids(message.getHeader("In-Reply-To", " "));
        return Optional.of(new InboundEmail(messageId, address, name == null || name.isBlank() ? null : name.strip(),
                message.getSubject(), EmailText.withoutQuotes(body(message)),
                inReplyTo.isEmpty() ? null : inReplyTo.getFirst(), ids(message.getHeader("References", " ")),
                automated(message, address), spoofed(message)));
    }

    private static Optional<InternetAddress> sender(MimeMessage message) throws MessagingException {
        Address[] from = message.getFrom();
        if (from == null || from.length == 0 || !(from[0] instanceof InternetAddress address)
                || address.getAddress() == null || !address.getAddress().contains("@")) {
            return Optional.empty();
        }
        return Optional.of(address);
    }

    /**
     * The plain-text version when there is one, else the HTML one as text.
     */
    static String body(Part message) throws MessagingException {
        String plain = find(message, "text/plain");
        if (plain != null) {
            return limit(plain);
        }
        String html = find(message, "text/html");
        return html == null ? "" : EmailText.fromHtml(limit(html));
    }

    private static String find(Part part, String type) throws MessagingException {
        if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
            return null;
        }
        try {
            if (part.isMimeType(type)) {
                return part.getContent() instanceof String text ? text : null;
            }
            if (part.isMimeType("multipart/*") && part.getContent() instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    String found = find(multipart.getBodyPart(i), type);
                    if (found != null) {
                        return found;
                    }
                }
            }
        } catch (IOException e) {
            // An unknown charset or a broken encoding: that part is skipped
            return null;
        }
        return null;
    }

    private static String limit(String text) {
        return text.length() <= MAX_BODY_CHARS ? text : text.substring(0, MAX_BODY_CHARS);
    }

    private static List<String> ids(String header) {
        List<String> ids = new ArrayList<>();
        if (header != null) {
            Matcher matcher = MESSAGE_ID.matcher(header);
            while (matcher.find()) {
                ids.add(matcher.group());
            }
        }
        return ids;
    }

    /**
     * Auto-replies, bounces and list mail mark themselves (RFC 3834 and common practice); answering them could start
     * two programs answering each other.
     */
    static boolean automated(MimeMessage message, String address) throws MessagingException {
        String autoSubmitted = first(message, "Auto-Submitted");
        if (autoSubmitted != null && !autoSubmitted.equalsIgnoreCase("no")) {
            return true;
        }
        String precedence = first(message, "Precedence");
        if (precedence != null && BULK_PRECEDENCE.contains(precedence.toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (first(message, "List-Id") != null || first(message, "List-Unsubscribe") != null
                || first(message, "X-Autoreply") != null || first(message, "X-Autorespond") != null) {
            return true;
        }
        if ("<>".equals(first(message, "Return-Path"))) {
            return true;
        }
        return PROGRAM_SENDERS.matcher(address.substring(0, address.indexOf('@'))).matches();
    }

    /**
     * The receiving server records its checks in the topmost Authentication-Results header; a failed DMARC check
     * means the sender's domain did not send the message.
     */
    static boolean spoofed(MimeMessage message) throws MessagingException {
        String results = first(message, "Authentication-Results");
        return results != null && results.toLowerCase(Locale.ROOT).contains("dmarc=fail");
    }

    private static String first(MimeMessage message, String header) throws MessagingException {
        String[] values = message.getHeader(header);
        return values == null || values.length == 0 ? null : values[0].strip();
    }
}
