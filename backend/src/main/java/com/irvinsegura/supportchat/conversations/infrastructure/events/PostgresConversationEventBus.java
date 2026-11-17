package com.irvinsegura.supportchat.conversations.infrastructure.events;

import com.irvinsegura.supportchat.conversations.domain.model.ConversationEvent;
import com.irvinsegura.supportchat.conversations.domain.port.ConversationEventBus;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Conversation events over PostgreSQL LISTEN/NOTIFY, so every instance sharing the database gets them without
 * another piece of infrastructure.
 * <p>
 * Publishing runs {@code pg_notify} in the caller's transaction: PostgreSQL delivers the notification only when that
 * transaction commits, in commit order, and drops it on rollback. Each instance listens on a connection of its own,
 * outside the pool. Notifications sent while that connection is down are lost, so after reconnecting subscribers are
 * told to refresh.
 */
@Component
class PostgresConversationEventBus implements ConversationEventBus, SmartLifecycle {

    static final String CHANNEL = "conversation_events";
    static final String APPLICATION_NAME = "supportchat-live-updates";

    private static final Logger log = LoggerFactory.getLogger(PostgresConversationEventBus.class);
    // How long one wait for notifications lasts; also bounds how late a lost connection is noticed
    private static final int POLL_MILLIS = 10_000;
    private static final int VALIDATION_TIMEOUT_SECONDS = 5;
    private static final Duration STARTUP_WAIT = Duration.ofSeconds(10);
    private static final List<Duration> RECONNECT_DELAYS = List.of(Duration.ofMillis(500), Duration.ofSeconds(1),
            Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10));

    private final JdbcClient jdbc;
    private final JdbcConnectionDetails connectionDetails;
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final CountDownLatch listening = new CountDownLatch(1);
    private volatile boolean running;
    private volatile Connection connection;
    private Thread listener;

    PostgresConversationEventBus(JdbcClient jdbc, JdbcConnectionDetails connectionDetails) {
        this.jdbc = jdbc;
        this.connectionDetails = connectionDetails;
    }

    @Override
    public void publish(ConversationEvent event) {
        jdbc.sql("SELECT pg_notify(:channel, :payload)")
                .param("channel", CHANNEL)
                .param("payload", encode(event))
                .query()
                .listOfRows();
    }

    @Override
    public void subscribe(Subscriber subscriber) {
        subscribers.add(subscriber);
    }

    /**
     * Returns once this instance listens, so no change made right after startup is missed.
     */
    @Override
    public void start() {
        running = true;
        listener = Thread.ofPlatform().daemon().name("conversation-events").start(this::listen);
        try {
            if (!listening.await(STARTUP_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("Not listening for conversation events yet; live updates start once the database answers");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        running = false;
        closeQuietly(connection);
        if (listener != null) {
            listener.interrupt();
            try {
                listener.join(Duration.ofSeconds(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void listen() {
        boolean listenedBefore = false;
        int failures = 0;
        while (running) {
            try (Connection current = connect()) {
                connection = current;
                try (Statement statement = current.createStatement()) {
                    statement.execute("LISTEN " + CHANNEL);
                }
                if (listenedBefore) {
                    log.info("Listening for conversation events again");
                    subscribers.forEach(PostgresConversationEventBus::eventsMissed);
                }
                listenedBefore = true;
                failures = 0;
                listening.countDown();
                receive(current);
            } catch (SQLException | RuntimeException e) {
                if (!running) {
                    return;
                }
                log.warn("Lost the conversation events connection; reconnecting: {}", e.getMessage());
                if (!pause(RECONNECT_DELAYS.get(Math.min(failures++, RECONNECT_DELAYS.size() - 1)))) {
                    return;
                }
            } finally {
                connection = null;
            }
        }
    }

    private void receive(Connection current) throws SQLException {
        PGConnection postgres = current.unwrap(PGConnection.class);
        while (running) {
            PGNotification[] notifications = postgres.getNotifications(POLL_MILLIS);
            if (notifications != null && notifications.length > 0) {
                for (PGNotification notification : notifications) {
                    decode(notification.getParameter()).ifPresent(this::deliver);
                }
            } else if (!current.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                // A quiet channel and a silently dropped connection look the same until something is sent
                throw new SQLException("The conversation events connection stopped answering");
            }
        }
    }

    private void deliver(ConversationEvent event) {
        for (Subscriber subscriber : subscribers) {
            try {
                subscriber.deliver(event);
            } catch (RuntimeException e) {
                log.warn("A subscriber failed on {} of conversation {}", event.type(), event.conversationId(), e);
            }
        }
    }

    private static void eventsMissed(Subscriber subscriber) {
        try {
            subscriber.eventsMissed();
        } catch (RuntimeException e) {
            log.warn("A subscriber failed to handle missed conversation events", e);
        }
    }

    private Connection connect() throws SQLException {
        Properties properties = new Properties();
        if (connectionDetails.getUsername() != null) {
            properties.setProperty("user", connectionDetails.getUsername());
        }
        if (connectionDetails.getPassword() != null) {
            properties.setProperty("password", connectionDetails.getPassword());
        }
        properties.setProperty("ApplicationName", APPLICATION_NAME);
        properties.setProperty("tcpKeepAlive", "true");
        return DriverManager.getConnection(connectionDetails.getJdbcUrl(), properties);
    }

    // Type, workspace, conversation and, for a new message, the message: a few dozen bytes, far below the 8000 bytes
    // a notification may carry
    static String encode(ConversationEvent event) {
        String payload = event.type().name() + ":" + event.workspaceId() + ":" + event.conversationId();
        return event.messageId() == null ? payload : payload + ":" + event.messageId();
    }

    static Optional<ConversationEvent> decode(String payload) {
        try {
            String[] parts = payload.split(":");
            ConversationEvent.Type type = ConversationEvent.Type.valueOf(parts[0]);
            UUID messageId = parts.length > 3 ? UUID.fromString(parts[3]) : null;
            return Optional.of(new ConversationEvent(type, UUID.fromString(parts[1]), UUID.fromString(parts[2]),
                    messageId));
        } catch (RuntimeException e) {
            // Anyone who can connect may notify this channel; a payload this code did not write is ignored
            log.warn("Ignoring a malformed conversation event: {}", payload);
            return Optional.empty();
        }
    }

    private static boolean pause(Duration delay) {
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            // Shutting down; nothing left to do with it
        }
    }
}
