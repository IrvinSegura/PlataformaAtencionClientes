package com.irvinsegura.supportchat.channels.domain.port;

import com.irvinsegura.supportchat.channels.domain.model.WhatsAppChannel;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The WhatsApp number of the workspace bound to the current transaction.
 */
public interface WhatsAppChannelRepository {

    Optional<WhatsAppChannel> find();

    void save(WhatsAppChannel channel);

    void delete(UUID workspaceId);

    /**
     * Which workspace a webhook URL belongs to, before any workspace is bound.
     */
    Optional<UUID> workspaceOfKey(String channelKey);

    /**
     * Remembers a delivered message.
     *
     * @return false when it was delivered before and must be ignored
     */
    boolean firstDelivery(UUID workspaceId, String messageId, Instant receivedAt);
}
