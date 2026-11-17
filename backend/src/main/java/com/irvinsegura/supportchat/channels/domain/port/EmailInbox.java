package com.irvinsegura.supportchat.channels.domain.port;

import com.irvinsegura.supportchat.channels.domain.model.EmailChannel;
import com.irvinsegura.supportchat.channels.domain.model.InboundEmail;
import com.irvinsegura.supportchat.channels.domain.model.MailboxCursor;
import java.util.List;
import java.util.Optional;

/**
 * Reads a mailbox's inbox over IMAP, without changing it: messages keep their read or unread state for the people
 * who also use the mailbox.
 */
public interface EmailInbox {

    /**
     * The messages that arrived after the cursor, oldest first.
     *
     * @param after empty, or from another UIDVALIDITY: nothing is returned and the new cursor points to the newest
     *              message
     * @throws MailboxUnavailableException when the server cannot be reached or refuses the credentials
     */
    Batch read(EmailChannel channel, Optional<MailboxCursor> after, int max);

    /**
     * @param cursor where the next read resumes
     */
    record Batch(MailboxCursor cursor, List<InboundEmail> messages) {

        public Batch {
            messages = List.copyOf(messages);
        }
    }

    /**
     * Its message is shown to the operator, so it says what to fix without revealing the password.
     */
    class MailboxUnavailableException extends RuntimeException {

        public MailboxUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
