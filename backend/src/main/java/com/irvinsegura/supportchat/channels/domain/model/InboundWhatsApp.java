package com.irvinsegura.supportchat.channels.domain.model;

import java.util.Objects;

/**
 * One message a contact sent, as read from a webhook delivery.
 *
 * @param messageId   WhatsApp's id, used to ignore a delivery Meta repeats
 * @param from        the contact's WhatsApp id (their number)
 * @param contactName their profile name, or null
 * @param text        what they wrote, or null when they pressed a button
 * @param buttonId    the id of the reply button they pressed, or null
 */
public record InboundWhatsApp(String messageId, String from, String contactName, String text, String buttonId) {

    public InboundWhatsApp {
        Objects.requireNonNull(messageId);
        Objects.requireNonNull(from);
    }
}
