package com.irvinsegura.supportchat.channels.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A workspace's support mailbox: customers' mail is read from it over IMAP and answered over SMTP, always with TLS.
 * With Gmail, the password is an app password of an account with two-step verification.
 * <p>
 * Reading resumes after the last message read. A mailbox read for the first time, or one whose server renumbered its
 * messages, starts from the newest message, so mail that arrived before the connection is never answered.
 */
public final class EmailChannel {

    private static final Pattern ADDRESS = Pattern.compile("[^@\\s<>\",;]+@[^@\\s<>\",;]+\\.[^@\\s<>\",;]+");
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?"
            + "(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*");
    private static final int MAX_ADDRESS = 254;
    private static final int MAX_HOST = 253;
    private static final int MAX_DISPLAY_NAME = 100;
    private static final int MAX_PASSWORD = 512;
    private static final int MAX_ERROR = 300;

    private final UUID workspaceId;
    private final Instant createdAt;
    private String address;
    private String displayName;
    private Server imap;
    private Server smtp;
    private String username;
    private String password;
    private boolean enabled;
    private MailboxCursor cursor;
    private Instant checkedAt;
    private String lastError;
    private Instant updatedAt;

    private EmailChannel(UUID workspaceId, String address, String displayName, Server imap, Server smtp,
            String username, String password, boolean enabled, MailboxCursor cursor, Instant checkedAt,
            String lastError, Instant createdAt, Instant updatedAt) {
        this.workspaceId = Objects.requireNonNull(workspaceId);
        this.address = address;
        this.displayName = displayName;
        this.imap = imap;
        this.smtp = smtp;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.cursor = cursor;
        this.checkedAt = checkedAt;
        this.lastError = lastError;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static EmailChannel connect(UUID workspaceId, Settings settings, Instant now) {
        if (blank(settings.password())) {
            throw new InvalidEmailChannelException("The password is required");
        }
        EmailChannel channel = new EmailChannel(workspaceId, null, null, null, null, null, null, false, null, null,
                null, now, now);
        channel.change(settings, now);
        return channel;
    }

    public static EmailChannel restore(UUID workspaceId, String address, String displayName, Server imap, Server smtp,
            String username, String password, boolean enabled, MailboxCursor cursor, Instant checkedAt,
            String lastError, Instant createdAt, Instant updatedAt) {
        return new EmailChannel(workspaceId, address, displayName, imap, smtp, username, password, enabled, cursor,
                checkedAt, lastError, createdAt, updatedAt);
    }

    /**
     * A blank password keeps the saved one. Pointing to another mailbox starts reading it afresh.
     */
    public void change(Settings settings, Instant now) {
        String newAddress = settings.address() == null ? "" : settings.address().strip().toLowerCase(Locale.ROOT);
        if (newAddress.length() > MAX_ADDRESS || !ADDRESS.matcher(newAddress).matches()) {
            throw new InvalidEmailChannelException("The email address is not valid");
        }
        String name = blank(settings.displayName()) ? null : settings.displayName().strip();
        if (name != null && name.length() > MAX_DISPLAY_NAME) {
            throw new InvalidEmailChannelException("The name is too long");
        }
        Server newImap = server(settings.imapHost(), settings.imapPort(), "IMAP");
        Server newSmtp = server(settings.smtpHost(), settings.smtpPort(), "SMTP");
        String newUsername = blank(settings.username()) ? newAddress : settings.username().strip();
        if (newUsername.length() > MAX_ADDRESS) {
            throw new InvalidEmailChannelException("The username is too long");
        }
        if (!blank(settings.password()) && settings.password().length() > MAX_PASSWORD) {
            throw new InvalidEmailChannelException("The password is too long");
        }
        if (!newImap.equals(imap) || !newUsername.equals(username)) {
            cursor = null;
        }
        address = newAddress;
        displayName = name;
        imap = newImap;
        smtp = newSmtp;
        username = newUsername;
        if (!blank(settings.password())) {
            // Spaces are kept: Gmail shows app passwords in groups of four, and accepts them either way
            password = settings.password();
            lastError = null;
        }
        enabled = settings.enabled();
        updatedAt = now;
    }

    private static Server server(String host, int port, String kind) {
        String name = host == null ? "" : host.strip().toLowerCase(Locale.ROOT);
        if (name.length() > MAX_HOST || !HOST.matcher(name).matches()) {
            throw new InvalidEmailChannelException("The " + kind + " server is not a valid host name");
        }
        if (port < 1 || port > 65_535) {
            throw new InvalidEmailChannelException("The " + kind + " port is not valid");
        }
        return new Server(name, port);
    }

    /**
     * Where to resume reading; empty to start from the newest message.
     */
    public Optional<MailboxCursor> cursor() {
        return Optional.ofNullable(cursor);
    }

    /**
     * The mailbox was read up to the cursor.
     */
    public void readUpTo(MailboxCursor cursor, Instant now) {
        this.cursor = Objects.requireNonNull(cursor);
        this.checkedAt = now;
        this.lastError = null;
    }

    /**
     * Reading the mailbox failed; the panel shows why until a read works.
     */
    public void failed(String error, Instant now) {
        String reason = error == null || error.isBlank() ? "Unknown error" : error.strip();
        this.lastError = reason.substring(0, Math.min(reason.length(), MAX_ERROR));
        this.checkedAt = now;
    }

    /**
     * Whether mail comes from the mailbox itself, e.g. a copy of a reply: it is never answered.
     */
    public boolean isOwnAddress(String address) {
        return this.address.equalsIgnoreCase(address);
    }

    /**
     * The domain for the ids of the messages sent from the mailbox.
     */
    public String domain() {
        return address.substring(address.indexOf('@') + 1);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public String address() {
        return address;
    }

    public String displayName() {
        return displayName;
    }

    public Server imap() {
        return imap;
    }

    public Server smtp() {
        return smtp;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public boolean enabled() {
        return enabled;
    }

    public Instant checkedAt() {
        return checkedAt;
    }

    public String lastError() {
        return lastError;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public record Server(String host, int port) {
    }

    /**
     * @param username blank uses the address, as Gmail and most providers expect
     * @param password blank keeps the saved one
     */
    public record Settings(String address, String displayName, String imapHost, int imapPort, String smtpHost,
            int smtpPort, String username, String password, boolean enabled) {

        @Override
        public String toString() {
            // The password stays out of logs
            return "Settings[address=" + address + ", imapHost=" + imapHost + ", smtpHost=" + smtpHost + ", enabled="
                    + enabled + "]";
        }
    }
}
