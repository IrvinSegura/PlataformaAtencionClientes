package com.irvinsegura.supportchat.channels.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * A message to send to a WhatsApp, Messenger or Instagram contact: plain text, or text with up to three reply
 * buttons. The text is kept whole; each sender fits it to its platform (WhatsApp cuts it, Meta splits it).
 *
 * @param to the contact's id on the platform (on WhatsApp, their number, digits only)
 */
public record OutboundMessage(String to, String text, List<Button> buttons) {

    // WhatsApp shows at most three reply buttons and Messenger 13 quick replies; both cut titles at 20 characters
    public static final int MAX_BUTTONS = 3;
    public static final int MAX_BUTTON_TITLE = 20;

    public OutboundMessage {
        Objects.requireNonNull(to);
        buttons = buttons == null ? List.of() : List.copyOf(buttons);
        if (buttons.size() > MAX_BUTTONS) {
            throw new IllegalArgumentException("A message carries at most three buttons");
        }
        text = text == null ? "" : text;
    }

    public static OutboundMessage text(String to, String text) {
        return new OutboundMessage(to, text, List.of());
    }

    /**
     * Cuts a text to at most {@code max} characters, marking the cut with an ellipsis.
     */
    public static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    /**
     * @param id what WhatsApp sends back when the contact presses it
     */
    public record Button(String id, String title) {

        public Button {
            Objects.requireNonNull(id);
            title = shorten(Objects.requireNonNull(title), MAX_BUTTON_TITLE);
        }
    }
}
