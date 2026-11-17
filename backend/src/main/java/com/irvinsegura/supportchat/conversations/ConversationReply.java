package com.irvinsegura.supportchat.conversations;

import com.irvinsegura.supportchat.shared.domain.Language;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Something to send to a customer who talks from a channel other than the web widget, published once the message is
 * saved. The channel's module delivers it, e.g. as a WhatsApp message.
 *
 * @param contactId   the customer's identity on the channel, e.g. their phone number
 * @param text        the message; for notices, the {@code SystemNotice} code, which each channel words itself
 * @param authorName  agent replies and notices about an agent: the agent's name; otherwise null
 * @param citedSources the sources an assistant answer cites, as {@code [1] Document title}, in citation order
 * @param pendingAction an action the customer must confirm or cancel, or null
 * @param language     the language to word notices and labels in
 * @param template     WhatsApp only: the approved template to send instead of the text, or null
 */
public record ConversationReply(UUID workspaceId, UUID conversationId, ConversationChannel channel, String contactId,
        UUID messageId, Kind kind, String text, String authorName, List<String> citedSources,
        PendingAction pendingAction, Language language, Template template) {

    public ConversationReply {
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(channel);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(language);
        citedSources = citedSources == null ? List.of() : List.copyOf(citedSources);
    }

    public ConversationReply(UUID workspaceId, UUID conversationId, ConversationChannel channel, String contactId,
            UUID messageId, Kind kind, String text, String authorName, List<String> citedSources,
            PendingAction pendingAction, Language language) {
        this(workspaceId, conversationId, channel, contactId, messageId, kind, text, authorName, citedSources,
                pendingAction, language, null);
    }

    /**
     * An approved WhatsApp template to send instead of the text, which WhatsApp only delivers within 24 hours of the
     * customer's last message.
     *
     * @param language   its language code, e.g. {@code es_MX}
     * @param parameters the values of its variables {{1}}, {{2}}…, in order
     */
    public record Template(String name, String language, List<String> parameters) {

        public Template {
            Objects.requireNonNull(name);
            Objects.requireNonNull(language);
            parameters = List.copyOf(parameters);
        }
    }

    public enum Kind {
        ASSISTANT,
        AGENT,
        NOTICE
    }

    /**
     * @param arguments exactly what will be sent, so the customer confirms what they read
     */
    public record PendingAction(UUID runId, String title, Map<String, Object> arguments) {

        public PendingAction {
            arguments = arguments == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        }
    }
}
