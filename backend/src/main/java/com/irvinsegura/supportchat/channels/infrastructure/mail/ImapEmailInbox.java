package com.irvinsegura.supportchat.channels.infrastructure.mail;

import com.irvinsegura.supportchat.channels.domain.model.EmailChannel;
import com.irvinsegura.supportchat.channels.domain.model.InboundEmail;
import com.irvinsegura.supportchat.channels.domain.model.InvalidEmailChannelException;
import com.irvinsegura.supportchat.channels.domain.model.MailboxCursor;
import com.irvinsegura.supportchat.channels.domain.port.EmailInbox;
import com.irvinsegura.supportchat.channels.domain.port.MailServerPolicy;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the inbox over IMAP, opened read-only so no message changes its read state.
 */
public class ImapEmailInbox implements EmailInbox {

    private static final Logger log = LoggerFactory.getLogger(ImapEmailInbox.class);

    private final MailServerPolicy servers;
    private final MailConnections connections;

    public ImapEmailInbox(MailServerPolicy servers, MailConnections connections) {
        this.servers = servers;
        this.connections = connections;
    }

    @Override
    public Batch read(EmailChannel channel, Optional<MailboxCursor> after, int max) {
        try {
            servers.check(channel.imap().host());
        } catch (InvalidEmailChannelException e) {
            throw new MailboxUnavailableException(e.getMessage(), e);
        }
        try (Store store = connections.imapSession().getStore(connections.imapProtocol())) {
            store.connect(channel.imap().host(), channel.imap().port(), channel.username(), channel.password());
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            try {
                return read(inbox, (UIDFolder) inbox, after, max);
            } finally {
                inbox.close(false);
            }
        } catch (AuthenticationFailedException e) {
            throw new MailboxUnavailableException("The IMAP server refused the username or password. With Gmail, "
                    + "use an app password and make sure IMAP is on", e);
        } catch (MessagingException e) {
            throw new MailboxUnavailableException("The IMAP server could not be read: " + reason(e), e);
        }
    }

    private static Batch read(Folder inbox, UIDFolder uids, Optional<MailboxCursor> after, int max)
            throws MessagingException {
        long validity = uids.getUIDValidity();
        if (after.isEmpty() || after.get().uidValidity() != validity) {
            // Mail that arrived before is not answered
            return new Batch(new MailboxCursor(validity, newestUid(inbox, uids)), List.of());
        }
        long last = after.get().lastUid();
        // "last+1:*" always includes the newest message, even when it is older than last+1
        Message[] found = uids.getMessagesByUID(last + 1, UIDFolder.MAXUID);
        FetchProfile profile = new FetchProfile();
        profile.add(UIDFolder.FetchProfileItem.UID);
        profile.add(FetchProfile.Item.ENVELOPE);
        inbox.fetch(found, profile);
        List<Message> newer = new ArrayList<>();
        for (Message message : found) {
            if (message != null && uids.getUID(message) > last) {
                newer.add(message);
            }
        }
        newer.sort(Comparator.comparingLong(message -> uid(uids, message)));
        List<InboundEmail> messages = new ArrayList<>();
        long readUpTo = last;
        for (Message message : newer.subList(0, Math.min(max, newer.size()))) {
            long uid = uids.getUID(message);
            try {
                MailMessages.read((MimeMessage) message, "<uid." + validity + "." + uid + "@mailbox>")
                        .ifPresent(messages::add);
            } catch (MessagingException | RuntimeException e) {
                // A message that cannot be read is skipped rather than blocking every message after it
                log.warn("Skipped an unreadable message in a mailbox: {}", e.getMessage());
            }
            readUpTo = uid;
        }
        return new Batch(new MailboxCursor(validity, readUpTo), messages);
    }

    private static long newestUid(Folder inbox, UIDFolder uids) throws MessagingException {
        int count = inbox.getMessageCount();
        return count == 0 ? 0 : uids.getUID(inbox.getMessage(count));
    }

    private static long uid(UIDFolder uids, Message message) {
        try {
            return uids.getUID(message);
        } catch (MessagingException e) {
            return Long.MAX_VALUE;
        }
    }

    // The innermost cause says what went wrong (unknown host, refused connection, certificate)
    static String reason(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return Arrays.stream(message.split("\\R")).findFirst().orElse(message).strip();
    }
}
