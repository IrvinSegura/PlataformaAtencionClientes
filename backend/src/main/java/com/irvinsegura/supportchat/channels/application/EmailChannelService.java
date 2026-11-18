package com.irvinsegura.supportchat.channels.application;

import com.irvinsegura.supportchat.channels.domain.model.EmailActionReply;
import com.irvinsegura.supportchat.channels.domain.model.EmailChannel;
import com.irvinsegura.supportchat.channels.domain.model.EmailChannel.Settings;
import com.irvinsegura.supportchat.channels.domain.model.EmailChannelNotFoundException;
import com.irvinsegura.supportchat.channels.domain.model.EmailThread;
import com.irvinsegura.supportchat.channels.domain.model.InboundEmail;
import com.irvinsegura.supportchat.channels.domain.port.EmailChannelRepository;
import com.irvinsegura.supportchat.channels.domain.port.EmailInbox;
import com.irvinsegura.supportchat.channels.domain.port.EmailInbox.Batch;
import com.irvinsegura.supportchat.channels.domain.port.EmailInbox.MailboxUnavailableException;
import com.irvinsegura.supportchat.channels.domain.port.InboundMessageLog;
import com.irvinsegura.supportchat.channels.domain.port.MailServerPolicy;
import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Connects a workspace's support mailbox and reads it: each new customer mail becomes a question in that
 * customer's conversation, answered by mail.
 */
@Service
public class EmailChannelService {

    private static final Logger log = LoggerFactory.getLogger(EmailChannelService.class);
    private static final String CHANNEL = ConversationChannel.EMAIL.name();

    private final EmailChannelRepository channels;
    private final EmailInbox inbox;
    private final MailServerPolicy servers;
    private final InboundMessageLog received;
    private final ApplicationEventPublisher events;
    private final ChannelMetrics metrics;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final int maxPerRead;

    EmailChannelService(EmailChannelRepository channels, EmailInbox inbox, MailServerPolicy servers,
            InboundMessageLog received, ApplicationEventPublisher events, ChannelMetrics metrics,
            PlatformTransactionManager transactionManager, Clock clock, EmailReadSettings settings) {
        this.channels = channels;
        this.inbox = inbox;
        this.servers = servers;
        this.received = received;
        this.events = events;
        this.metrics = metrics;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.maxPerRead = settings.maxPerRead();
    }

    public Optional<EmailChannelView> view() {
        return transactions.execute(status -> channels.find().map(EmailChannelView::of));
    }

    public EmailChannelView configure(Settings settings) {
        // Name lookups happen before the transaction, which they could hold open for seconds
        servers.check(settings.imapHost() == null ? "" : settings.imapHost().strip());
        servers.check(settings.smtpHost() == null ? "" : settings.smtpHost().strip());
        return transactions.execute(status -> {
            Instant now = clock.instant();
            EmailChannel channel = channels.find()
                    .map(existing -> {
                        existing.change(settings, now);
                        return existing;
                    })
                    .orElseGet(() -> EmailChannel.connect(TenantContext.requireWorkspaceId(), settings, now));
            channels.save(channel);
            log.info("Mailbox saved for workspace {}, enabled: {}", channel.workspaceId(), channel.enabled());
            return EmailChannelView.of(channel);
        });
    }

    public void disconnect() {
        transactions.executeWithoutResult(status -> {
            channels.find().orElseThrow(EmailChannelNotFoundException::new);
            channels.delete(TenantContext.requireWorkspaceId());
        });
    }

    /**
     * Reads every connected mailbox once; one that fails does not hold back the others.
     *
     * @return the customer mails accepted
     */
    public int readAll() {
        List<UUID> workspaces = transactions.execute(status -> channels.workspacesWithMailbox());
        int accepted = 0;
        for (UUID workspaceId : workspaces) {
            try {
                accepted += TenantContext.callAs(workspaceId, this::read);
            } catch (RuntimeException e) {
                log.warn("Could not read the mailbox of workspace {}: {}", workspaceId, e.getMessage());
            }
        }
        return accepted;
    }

    /**
     * Reads the mailbox of the workspace bound to the current code: the mail that arrived since the last read.
     */
    int read() {
        Optional<EmailChannel> found = transactions.execute(status -> channels.find()).filter(EmailChannel::enabled);
        if (found.isEmpty()) {
            return 0;
        }
        EmailChannel channel = found.get();
        Batch batch;
        try {
            batch = inbox.read(channel, channel.cursor(), maxPerRead);
        } catch (MailboxUnavailableException e) {
            metrics.mailboxRead(false);
            log.info("The mailbox of workspace {} could not be read: {}", channel.workspaceId(), e.getMessage());
            channel.failed(e.getMessage(), clock.instant());
            transactions.executeWithoutResult(status -> channels.saveProgress(channel));
            return 0;
        }
        metrics.mailboxRead(true);
        Instant now = clock.instant();
        int accepted = 0;
        // One transaction per mail: one that cannot be stored would otherwise roll back the whole read, and the same
        // batch would be read again every minute, holding back every later mail. A mail read again after a failure
        // is recognized by its Message-ID
        for (InboundEmail email : batch.messages()) {
            try {
                if (Boolean.TRUE.equals(transactions.execute(status -> accept(channel, email, now)))) {
                    accepted++;
                }
            } catch (TransientDataAccessException | DataAccessResourceFailureException
                    | RecoverableDataAccessException e) {
                // The database, not the mail: stop here and read the batch again next time
                throw e;
            } catch (RuntimeException e) {
                log.warn("Skipped a mail to workspace {} that could not be handled", channel.workspaceId(), e);
            }
        }
        transactions.executeWithoutResult(status -> {
            channel.readUpTo(batch.cursor(), now);
            channels.saveProgress(channel);
        });
        return accepted;
    }

    private boolean accept(EmailChannel channel, InboundEmail email, Instant now) {
        if (email.automated() || email.spoofed() || channel.isOwnAddress(email.from())) {
            log.info("Skipped a mail to workspace {}: automated {}, spoofed {}", channel.workspaceId(),
                    email.automated(), email.spoofed());
            return false;
        }
        String question = question(email);
        if (question.isEmpty() || !received.firstDelivery(channel.workspaceId(), CHANNEL, email.messageId(), now)) {
            return false;
        }
        channels.saveThread(channel.workspaceId(), EmailThread.of(email), now);
        events.publishEvent(new EmailReceived(channel.workspaceId(), email.from(), email.fromName(), question,
                EmailActionReply.read(email).orElse(null)));
        return true;
    }

    /**
     * A new mail's subject often holds the question ("Order 1234 has not arrived"), so it leads the text; replies
     * keep the subject of the thread, which says nothing new.
     */
    static String question(InboundEmail email) {
        String body = email.text().strip();
        String subject = EmailThread.baseSubject(email.subject());
        boolean reply = email.inReplyTo() != null || !email.references().isEmpty();
        if (reply || subject == null || body.toLowerCase(Locale.ROOT).contains(subject.toLowerCase(Locale.ROOT))) {
            return body;
        }
        return body.isEmpty() ? subject : subject + "\n\n" + body;
    }

    /**
     * The connection as the panel shows it; the password is never sent back.
     *
     * @param checkedAt when the mailbox was last read, or null before the first read
     * @param lastError why the last read failed, or null when it worked
     */
    public record EmailChannelView(String address, @Nullable String displayName, String imapHost, int imapPort,
            String smtpHost, int smtpPort, String username, boolean enabled, @Nullable Instant checkedAt,
            @Nullable String lastError, Instant updatedAt) {

        static EmailChannelView of(EmailChannel channel) {
            return new EmailChannelView(channel.address(), channel.displayName(), channel.imap().host(),
                    channel.imap().port(), channel.smtp().host(), channel.smtp().port(), channel.username(),
                    channel.enabled(), channel.checkedAt(), channel.lastError(), channel.updatedAt());
        }
    }

    /**
     * @param maxPerRead the most mails taken from a mailbox in one read; the rest wait for the next one
     */
    public record EmailReadSettings(int maxPerRead) {
    }
}
