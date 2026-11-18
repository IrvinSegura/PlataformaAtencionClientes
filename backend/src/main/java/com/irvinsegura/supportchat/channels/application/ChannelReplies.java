package com.irvinsegura.supportchat.channels.application;

import com.irvinsegura.supportchat.channels.domain.model.OutboundMessage;
import com.irvinsegura.supportchat.channels.domain.model.OutboundMessage.Button;
import com.irvinsegura.supportchat.conversations.ConversationReply;
import com.irvinsegura.supportchat.conversations.ConversationReply.PendingAction;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Words a conversation reply for a messaging channel. The widget shows citations as links and notices as status
 * lines; on the other channels both become plain text, and a proposed action becomes a message with confirm and
 * cancel buttons where the channel has them.
 */
final class ChannelReplies {

    static final String CONFIRM_PREFIX = "confirm:";
    static final String CANCEL_PREFIX = "cancel:";

    private ChannelReplies() {
    }

    /**
     * How a channel shows emphasis.
     */
    enum Format {
        // WhatsApp shows *text* in bold
        WHATSAPP,
        // Messenger, Instagram and plain-text mail would show the asterisks
        PLAIN
    }

    /**
     * @return empty for replies nothing is sent for
     */
    static Optional<OutboundMessage> compose(ConversationReply reply, Format format) {
        PendingAction pending = reply.pendingAction();
        return text(reply, format).map(text -> pending == null || reply.kind() != ConversationReply.Kind.ASSISTANT
                ? OutboundMessage.text(reply.contactId(), text)
                : new OutboundMessage(reply.contactId(), text, buttons(pending, ChannelTexts.in(reply.language()))));
    }

    /**
     * The reply as text; an action to confirm is described, without the buttons to answer it.
     *
     * @return empty for replies nothing is sent for
     */
    static Optional<String> text(ConversationReply reply, Format format) {
        ChannelTexts texts = ChannelTexts.in(reply.language());
        return switch (reply.kind()) {
            case ASSISTANT -> Optional.of(assistant(reply, texts, format));
            case AGENT -> Optional.of(reply.authorName() == null ? reply.text()
                    : bold(reply.authorName(), format) + ": " + reply.text());
            case NOTICE -> notice(reply.text(), reply.authorName(), texts);
        };
    }

    private static String assistant(ConversationReply reply, ChannelTexts texts, Format format) {
        StringBuilder text = new StringBuilder(reply.text());
        if (!reply.citedSources().isEmpty()) {
            text.append("\n\n").append(texts.sources()).append(": ").append(String.join(", ", reply.citedSources()));
        }
        PendingAction pending = reply.pendingAction();
        if (pending != null) {
            text.append("\n\n").append(bold(pending.title(), format));
            for (Map.Entry<String, Object> argument : pending.arguments().entrySet()) {
                text.append("\n• ").append(argument.getKey()).append(": ").append(argument.getValue());
            }
        }
        return text.toString();
    }

    private static String bold(String text, Format format) {
        return format == Format.WHATSAPP ? "*" + text + "*" : text;
    }

    private static List<Button> buttons(PendingAction pending, ChannelTexts texts) {
        return List.of(new Button(CONFIRM_PREFIX + pending.runId(), texts.confirm()),
                new Button(CANCEL_PREFIX + pending.runId(), texts.cancel()));
    }

    // Notices are stored as codes so each channel words them in the conversation's language
    private static Optional<String> notice(String code, String agentName, ChannelTexts texts) {
        String agent = agentName == null ? texts.someone() : agentName;
        return Optional.ofNullable(switch (code) {
            case "HANDOFF_REQUESTED" -> texts.handoffRequested();
            case "HANDOFF_CANCELLED" -> texts.handoffCancelled();
            case "AGENT_JOINED" -> texts.joined(agent);
            case "AGENT_LEFT" -> texts.left(agent);
            case "CONVERSATION_CLOSED" -> texts.closed();
            default -> null;
        });
    }

    /**
     * Reads the id of a button this class created.
     */
    static Optional<ButtonPress> button(String id) {
        if (id == null) {
            return Optional.empty();
        }
        boolean confirm = id.startsWith(CONFIRM_PREFIX);
        if (!confirm && !id.startsWith(CANCEL_PREFIX)) {
            return Optional.empty();
        }
        try {
            UUID runId = UUID.fromString(id.substring((confirm ? CONFIRM_PREFIX : CANCEL_PREFIX).length()));
            return Optional.of(new ButtonPress(runId, confirm));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    record ButtonPress(UUID runId, boolean confirm) {
    }
}
