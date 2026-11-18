package com.irvinsegura.supportchat.channels.application;

import com.irvinsegura.supportchat.channels.domain.model.MetaPlatform;
import java.util.UUID;

/**
 * A new Messenger or Instagram message was accepted from a webhook delivery; it is answered once the delivery is
 * acknowledged, so Meta gets its quick answer and does not deliver it again.
 *
 * @param text    what the customer wrote, or null when they pressed a button
 * @param payload the button they pressed, or null
 */
public record MetaMessageReceived(UUID workspaceId, MetaPlatform platform, String contactId, String text,
        String payload) {
}
