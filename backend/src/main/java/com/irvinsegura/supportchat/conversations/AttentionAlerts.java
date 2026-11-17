package com.irvinsegura.supportchat.conversations;

import java.util.UUID;

/**
 * Where the module that sends an alert records how each delivery went, for the workspace bound to the current code,
 * so the panel can show whether the owner was reached.
 */
public interface AttentionAlerts {

    /**
     * @param detail why it was not delivered, in words the owner can act on; null when it was
     */
    void recordDelivery(UUID alertId, String recipient, Delivery delivery, String detail);

    enum Delivery {
        SENT,
        // WhatsApp refused it for good, e.g. the number has not written in 24 hours and there is no template
        REJECTED,
        // WhatsApp could not be reached
        FAILED,
        // The workspace has no WhatsApp number connected to send from
        NO_CHANNEL
    }
}
