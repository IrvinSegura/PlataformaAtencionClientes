package com.irvinsegura.supportchat.workspaces.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A tenant of the platform and the settings of its chat widget.
 */
public final class Workspace {

    public static final int MAX_NAME_LENGTH = 80;
    public static final int MAX_BOT_NAME_LENGTH = 40;
    public static final int MAX_WELCOME_LENGTH = 300;
    public static final int MAX_ALLOWED_ORIGINS = 20;

    private static final String DEFAULT_BOT_NAME = "Asistente";
    private static final String DEFAULT_WELCOME = "¡Hola! ¿En qué puedo ayudarte?";

    private final UUID id;
    private final String name;
    private WidgetKey widgetKey;
    private List<AllowedOrigin> allowedOrigins;
    private String botName;
    private String welcomeMessage;
    private final Instant createdAt;
    private Instant updatedAt;

    private Workspace(UUID id, String name, WidgetKey widgetKey, List<AllowedOrigin> allowedOrigins, String botName,
            String welcomeMessage, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.name = name;
        this.widgetKey = Objects.requireNonNull(widgetKey);
        this.allowedOrigins = List.copyOf(allowedOrigins);
        this.botName = botName;
        this.welcomeMessage = welcomeMessage;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    /**
     * A new workspace whose widget works nowhere until its owner allows at least one origin.
     */
    public static Workspace create(UUID id, String name, Instant now) {
        return new Workspace(id, requireText(name, "name", MAX_NAME_LENGTH), WidgetKey.generate(), List.of(),
                DEFAULT_BOT_NAME, DEFAULT_WELCOME, now, now);
    }

    public static Workspace restore(UUID id, String name, WidgetKey widgetKey, List<AllowedOrigin> allowedOrigins,
            String botName, String welcomeMessage, Instant createdAt, Instant updatedAt) {
        return new Workspace(id, name, widgetKey, allowedOrigins, botName, welcomeMessage, createdAt, updatedAt);
    }

    public void changeWidgetSettings(String botName, String welcomeMessage, List<AllowedOrigin> allowedOrigins,
            Instant now) {
        if (allowedOrigins.size() > MAX_ALLOWED_ORIGINS) {
            throw new InvalidWorkspaceSettingsException(
                    "At most %d allowed origins".formatted(MAX_ALLOWED_ORIGINS));
        }
        this.botName = requireText(botName, "botName", MAX_BOT_NAME_LENGTH);
        this.welcomeMessage = requireText(welcomeMessage, "welcomeMessage", MAX_WELCOME_LENGTH);
        this.allowedOrigins = allowedOrigins.stream().distinct().toList();
        this.updatedAt = now;
    }

    /**
     * Replaces the public key, e.g. after someone embedded the widget where they should not. Sites still using the
     * old key stop working at once.
     */
    public WidgetKey rotateWidgetKey(Instant now) {
        WidgetKey previous = widgetKey;
        this.widgetKey = WidgetKey.generate();
        this.updatedAt = now;
        return previous;
    }

    private static String requireText(String value, String field, int maxLength) {
        String stripped = value == null ? "" : value.strip();
        if (stripped.isEmpty() || stripped.length() > maxLength) {
            throw new InvalidWorkspaceSettingsException(
                    "%s must have between 1 and %d characters".formatted(field, maxLength));
        }
        return stripped;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public WidgetKey widgetKey() {
        return widgetKey;
    }

    public List<AllowedOrigin> allowedOrigins() {
        return allowedOrigins;
    }

    public String botName() {
        return botName;
    }

    public String welcomeMessage() {
        return welcomeMessage;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
