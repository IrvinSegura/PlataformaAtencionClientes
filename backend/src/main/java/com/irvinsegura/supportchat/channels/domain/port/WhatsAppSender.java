package com.irvinsegura.supportchat.channels.domain.port;

import com.irvinsegura.supportchat.channels.domain.model.OutboundMessage;
import com.irvinsegura.supportchat.channels.domain.model.TemplateMessage;
import com.irvinsegura.supportchat.channels.domain.model.WhatsAppChannel;

/**
 * Sends messages as the business, through the WhatsApp Cloud API.
 */
public interface WhatsAppSender {

    /**
     * @throws WhatsAppUnavailableException when sending may work later (network, rate limit, Meta's errors)
     * @throws WhatsAppRejectedException    when Meta refused the message for good (bad token, number outside the
     *                                      24-hour window), so retrying is pointless
     */
    void send(WhatsAppChannel channel, OutboundMessage message);

    /**
     * Sends an approved template, which reaches the number even outside the 24-hour window.
     *
     * @throws WhatsAppUnavailableException when sending may work later
     * @throws WhatsAppRejectedException    when Meta refused it, e.g. the template is unknown or not approved
     */
    void sendTemplate(WhatsAppChannel channel, TemplateMessage message);

    class WhatsAppUnavailableException extends RuntimeException {

        public WhatsAppUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    class WhatsAppRejectedException extends RuntimeException {

        public WhatsAppRejectedException(String message) {
            super(message);
        }
    }
}
